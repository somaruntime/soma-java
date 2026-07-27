package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.GeneratedPlanToken;
import com.hgtech.soma.runtime.metadata.SomaMetadata;
import com.hgtech.soma.runtime.metadata.SomaSchemaMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable composition/resource plan for one explicit or generated implicit SomaGroup。
 *
 * <p>Slots are frozen at build time. Attaching a root binds exactly one declared slot;
 * no detach, reparent or dynamic slot addition exists.</p>
 */
public final class SomaGroupPlan {
    private static final String HASH_PREFIX = "soma-java:v1:soma-group-plan\n";

    private final String logicalGroupId;
    private final boolean explicit;
    private final long maximumStructuralBytes;
    private final long maximumTableInstances;
    private final List<SomaGroupMemberPlan> members;
    private final TreeMap<String, SomaGroupMemberPlan> membersById;
    private final String groupPlanHash;

    private SomaGroupPlan(
            String logicalGroupId,
            boolean explicit,
            long maximumStructuralBytes,
            long maximumTableInstances,
            TreeMap<String, SomaGroupMemberPlan> membersById) {
        this.logicalGroupId = logicalGroupId;
        this.explicit = explicit;
        this.maximumStructuralBytes = maximumStructuralBytes;
        this.maximumTableInstances = maximumTableInstances;
        this.membersById = new TreeMap<String, SomaGroupMemberPlan>(
                UnicodeCodePointOrder.INSTANCE);
        this.membersById.putAll(membersById);
        members = Collections.unmodifiableList(
                new ArrayList<SomaGroupMemberPlan>(this.membersById.values()));
        groupPlanHash = CanonicalSupport.sha256(HASH_PREFIX, toCanonicalJson());
    }

    public static Builder builder(String logicalGroupId) {
        return new Builder(logicalGroupId, true);
    }

    public static SomaGroupPlan generatedImplicit(
            GeneratedPlanToken token,
            String logicalGroupId,
            String memberId,
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            RuntimePlan runtimePlan) {
        GeneratedPlanToken.require(token);
        return new Builder(logicalGroupId, false)
                .member(memberId, metadata, rootTable, runtimePlan)
                .build();
    }

    public String logicalGroupId() { return logicalGroupId; }
    public boolean explicit() { return explicit; }
    public long maximumStructuralBytes() { return maximumStructuralBytes; }
    public long maximumTableInstances() { return maximumTableInstances; }
    public String groupPlanHash() { return groupPlanHash; }
    public List<SomaGroupMemberPlan> members() { return members; }

    public SomaGroupMemberPlan requireMember(String memberId) {
        String required = CanonicalSupport.required(memberId, "memberId");
        SomaGroupMemberPlan member = membersById.get(required);
        if (member == null) {
            throw invalid("members." + required, "unknown member");
        }
        return member;
    }

    private String toCanonicalJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"explicit\":").append(explicit)
                .append(",\"logicalGroupId\":")
                .append(CanonicalSupport.quote(logicalGroupId))
                .append(",\"maximumStructuralBytes\":")
                .append(maximumStructuralBytes)
                .append(",\"maximumTableInstances\":")
                .append(maximumTableInstances)
                .append(",\"members\":[");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) json.append(',');
            json.append(members.get(i).toCanonicalJson());
        }
        return json.append("]}").toString();
    }

    static SomaRuntimeException invalid(String path, String reason) {
        Map<String, String> context = new TreeMap<String, String>(
                UnicodeCodePointOrder.INSTANCE);
        context.put("reason", reason);
        return SomaRuntimeException.create(
                SomaErrorCategory.INVALID_INPUT,
                "invalid_group_plan",
                "group.create",
                path,
                context,
                null);
    }

    /** One-shot composition builder。 */
    public static final class Builder {
        private final String logicalGroupId;
        private final boolean explicit;
        private final TreeMap<String, SomaGroupMemberPlan> members =
                new TreeMap<String, SomaGroupMemberPlan>(
                        UnicodeCodePointOrder.INSTANCE);
        private long maximumStructuralBytes = -1L;
        private long maximumTableInstances = -1L;
        private boolean open = true;

        private Builder(String logicalGroupId, boolean explicit) {
            this.logicalGroupId =
                    CanonicalSupport.required(logicalGroupId, "logicalGroupId");
            this.explicit = explicit;
        }

        public Builder maximumStructuralBytes(long value) {
            requireOpen();
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumStructuralBytes must be positive");
            }
            maximumStructuralBytes = value;
            return this;
        }

        public Builder maximumTableInstances(long value) {
            requireOpen();
            if (value <= 0L) {
                throw new IllegalArgumentException(
                        "maximumTableInstances must be positive");
            }
            maximumTableInstances = value;
            return this;
        }

        public Builder member(
                String memberId,
                SomaMetadata metadata,
                SomaTableMetadata rootTable,
                RuntimePlan runtimePlan) {
            requireOpen();
            String id = CanonicalSupport.required(memberId, "memberId");
            if (metadata == null) throw new NullPointerException("metadata");
            if (rootTable == null) throw new NullPointerException("rootTable");
            if (runtimePlan == null) throw new NullPointerException("runtimePlan");
            if (members.containsKey(id)) {
                throw invalid("members." + id, "duplicate member");
            }
            SomaSchemaMetadata schema = metadata.descriptor().schema();
            if (schema == null) {
                throw invalid("members." + id + ".metadata",
                        "missing schema descriptor");
            }
            SomaTableMetadata canonical =
                    schema.requireTable(rootTable.logicalName());
            if (canonical != rootTable) {
                throw invalid("members." + id + ".rootTable",
                        "root descriptor is not owned by metadata");
            }
            if (!schema.schemaHash().equals(runtimePlan.schemaHash())) {
                throw invalid("members." + id + ".schemaHash",
                        "metadata and RuntimePlan schema mismatch");
            }
            runtimePlan.requireTable(rootTable.logicalName());
            members.put(id, new SomaGroupMemberPlan(
                    id, metadata, rootTable, runtimePlan));
            return this;
        }

        public SomaGroupPlan build() {
            requireOpen();
            open = false;
            if (members.isEmpty()) {
                throw invalid("members", "at least one member is required");
            }
            long requiredBytes = 0L;
            long requiredInstances = 0L;
            for (SomaGroupMemberPlan member : members.values()) {
                requiredBytes = checkedSum(
                        requiredBytes, member.maximumStructuralBytes(),
                        "maximumStructuralBytes");
                requiredInstances = checkedSum(
                        requiredInstances, member.maximumTableInstances(),
                        "maximumTableInstances");
            }
            long effectiveBytes = maximumStructuralBytes < 0L
                    ? requiredBytes : maximumStructuralBytes;
            long effectiveInstances = maximumTableInstances < 0L
                    ? requiredInstances : maximumTableInstances;
            if (effectiveBytes < requiredBytes) {
                throw invalid("maximumStructuralBytes",
                        "group envelope is smaller than member entitlements");
            }
            if (effectiveInstances < requiredInstances) {
                throw invalid("maximumTableInstances",
                        "group envelope is smaller than member entitlements");
            }
            return new SomaGroupPlan(
                    logicalGroupId,
                    explicit,
                    effectiveBytes,
                    effectiveInstances,
                    members);
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException(
                        "SomaGroupPlan.Builder is closed");
            }
        }

        private static long checkedSum(
                long left, long right, String path) {
            if (right <= 0L || Long.MAX_VALUE - left < right) {
                throw invalid(path, "member entitlement sum overflow");
            }
            return left + right;
        }
    }
}
