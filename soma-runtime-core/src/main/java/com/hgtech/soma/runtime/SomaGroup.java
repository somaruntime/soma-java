package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.ChildOwnershipRegistry;
import com.hgtech.soma.runtime.generated.GeneratedPlanToken;
import com.hgtech.soma.runtime.generated.GeneratedRootFactory;
import com.hgtech.soma.runtime.generated.GroupLedger;
import com.hgtech.soma.runtime.generated.GroupMembership;
import com.hgtech.soma.runtime.metadata.SomaGroupMemberMetadata;
import com.hgtech.soma.runtime.metadata.SomaGroupMetadata;
import com.hgtech.soma.runtime.metadata.SomaMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableRuntimeMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional stable composition/resource/release owner for root ownership aggregates。
 *
 * <p>SomaGroup is not a transaction, snapshot-isolation or DataFlow guard boundary.
 * Each attached root keeps an independent aggregate identity and trust state.</p>
 */
public final class SomaGroup {
    private static final AtomicLong NEXT_GROUP_INSTANCE_ID =
            new AtomicLong(1L);

    private final SomaGroupPlan plan;
    private final long groupInstanceId;
    private final GroupLedger ledger;
    private final MemberRuntime[] members;
    private final TreeMap<String, Integer> memberIndexes =
            new TreeMap<String, Integer>(UnicodeCodePointOrder.INSTANCE);
    private final int[] attachmentOrder;

    private SomaGroupState state = SomaGroupState.ACTIVE;
    private long membershipEpoch;
    private long nextAttachmentOrdinal = 1L;
    private int attachmentCount;
    private String dataVersion;
    private boolean releasing;

    private SomaGroup(SomaGroupPlan plan) {
        this.plan = plan;
        groupInstanceId = nextGroupInstanceId();
        List<SomaGroupMemberPlan> plannedMembers = plan.members();
        members = new MemberRuntime[plannedMembers.size()];
        attachmentOrder = new int[plannedMembers.size()];
        String[] ids = new String[plannedMembers.size()];
        long[] byteEntitlements = new long[plannedMembers.size()];
        long[] instanceEntitlements = new long[plannedMembers.size()];
        for (int index = 0; index < plannedMembers.size(); index++) {
            SomaGroupMemberPlan member = plannedMembers.get(index);
            members[index] = new MemberRuntime(member);
            memberIndexes.put(member.memberId(), Integer.valueOf(index));
            ids[index] = member.memberId();
            byteEntitlements[index] = member.maximumStructuralBytes();
            instanceEntitlements[index] = member.maximumTableInstances();
        }
        ledger = new GroupLedger(
                "group." + plan.logicalGroupId(),
                plan.maximumStructuralBytes(),
                plan.maximumTableInstances(),
                ids,
                byteEntitlements,
                instanceEntitlements);
    }

    public static SomaGroup create(SomaGroupPlan plan) {
        if (plan == null) throw new NullPointerException("plan");
        return new SomaGroup(plan);
    }

    public SomaGroupPlan plan() { return plan; }
    public long groupInstanceId() { return groupInstanceId; }
    public SomaGroupState state() { return state; }
    public boolean isReleased() { return state == SomaGroupState.RELEASED; }
    public long membershipEpoch() { return membershipEpoch; }
    public String dataVersion() { return dataVersion; }

    public void setDataVersion(String value) {
        if (value == null) throw new NullPointerException("value");
        preflightSafePoint("group.setDataVersion");
        dataVersion = value;
    }

    public void clearDataVersion() {
        preflightSafePoint("group.clearDataVersion");
        dataVersion = null;
    }

    public SomaGroupMetadata metadata() {
        return new MetadataSnapshot(this);
    }

    /**
     * Generated-runtime protocol entry. Ordinary applications cannot obtain the
     * required GeneratedPlanToken and attach through generated Table facades instead.
     */
    public <T> T generatedAttach(
            GeneratedPlanToken token,
            String memberId,
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            GeneratedRootFactory<T> factory) {
        GeneratedPlanToken.require(token);
        if (metadata == null) throw new NullPointerException("metadata");
        if (rootTable == null) throw new NullPointerException("rootTable");
        if (factory == null) throw new NullPointerException("factory");
        String id = CanonicalSupport.required(memberId, "memberId");
        int index = requireMemberIndex(id, "group.attach");
        requireAttachable(id);
        MemberRuntime member = members[index];
        verifyMemberBinding(member.plan, metadata, rootTable);
        requireMembershipEpochAvailable("group.attach");
        requireAttachmentOrdinalAvailable("group.attach");
        member.attaching = true;
        MemberContext context = new MemberContext(index);
        ChildOwnershipRegistry ownership = null;
        try {
            ownership = ChildOwnershipRegistry.generatedCreate(
                    token, ledger, index, context);
            member.aggregateInstanceId = ownership.aggregateInstanceId();
            RuntimePlan runtimePlan = member.plan.runtimePlan();
            TablePlan tablePlan =
                    runtimePlan.requireTable(rootTable.logicalName());
            T root = factory.create(runtimePlan, tablePlan, ownership);
            if (root == null) {
                throw groupInternal(
                        "group_attach_null_root", id, "group.attach");
            }
            member.binding = new Binding<T>(root, factory);
            member.ownership = ownership;
            member.attachmentOrdinal = nextAttachmentOrdinal++;
            member.state = SomaGroupMemberState.ATTACHED;
            attachmentOrder[attachmentCount++] = index;
            membershipEpoch++;
            member.attaching = false;
            return root;
        } catch (RuntimeException failure) {
            rollbackAttach(member, ownership, context, failure);
            throw failure;
        } catch (Error failure) {
            rollbackAttach(member, ownership, context, failure);
            throw failure;
        }
    }

    /**
     * Releases all attached roots after a complete preflight, in reverse attachment
     * order. Explicit member Tables delegate here only through group.release().
     */
    public void release() {
        if (state == SomaGroupState.RELEASED) return;
        if (releasing) {
            throw failure(
                    SomaErrorCategory.LIFECYCLE,
                    "reentrant_group_release",
                    "group.release",
                    plan.logicalGroupId(),
                    null);
        }
        for (MemberRuntime member : members) {
            if (member.attaching) {
                throw failure(
                        SomaErrorCategory.CONFLICT,
                        "group_attach_in_progress",
                        "group.release",
                        member.plan.memberId(),
                        null);
            }
        }
        requireMembershipEpochAvailable("group.release");
        for (int position = 0; position < attachmentCount; position++) {
            MemberRuntime member = members[attachmentOrder[position]];
            if (member.state == SomaGroupMemberState.RELEASED) continue;
            member.binding.preflightRelease();
        }
        releasing = true;
        boolean changed = false;
        try {
            for (int position = attachmentCount - 1; position >= 0; position--) {
                MemberRuntime member = members[attachmentOrder[position]];
                if (member.state == SomaGroupMemberState.RELEASED) continue;
                member.binding.release();
                member.ownership.releaseStorage();
                ledger.requireMemberDrained(
                        attachmentOrder[position],
                        member.ownership,
                        "group.release");
                member.state = SomaGroupMemberState.RELEASED;
                changed = true;
            }
            ledger.requireDrained("group.release");
            state = SomaGroupState.RELEASED;
            membershipEpoch++;
        } catch (RuntimeException failure) {
            if (changed) membershipEpoch++;
            state = SomaGroupState.FAULTED;
            throw failure;
        } catch (Error failure) {
            if (changed) membershipEpoch++;
            state = SomaGroupState.FAULTED;
            throw failure;
        } finally {
            releasing = false;
        }
    }

    private void preflightSafePoint(String operation) {
        requireUsableGroup(operation);
        if (releasing) {
            throw failure(
                    SomaErrorCategory.LIFECYCLE,
                    "reentrant_group_release",
                    operation,
                    plan.logicalGroupId(),
                    null);
        }
        for (int position = 0; position < attachmentCount; position++) {
            MemberRuntime member = members[attachmentOrder[position]];
            if (member.state == SomaGroupMemberState.ATTACHED
                    || member.state == SomaGroupMemberState.FAULTED) {
                member.binding.preflightSafePoint(operation);
            }
        }
    }

    private void requireAttachable(String memberId) {
        if (state == SomaGroupState.RELEASED) {
            throw failure(
                    SomaErrorCategory.LIFECYCLE,
                    "group_released",
                    "group.attach",
                    plan.logicalGroupId(),
                    null);
        }
        if (state == SomaGroupState.FAULTED || releasing) {
            throw failure(
                    SomaErrorCategory.INTERNAL,
                    "group_faulted",
                    "group.attach",
                    plan.logicalGroupId(),
                    null);
        }
        MemberRuntime member = members[
                requireMemberIndex(memberId, "group.attach")];
        if (member.attaching || member.state != SomaGroupMemberState.PLANNED) {
            throw failure(
                    SomaErrorCategory.CONFLICT,
                    "group_member_already_attached",
                    "group.attach",
                    memberId,
                    null);
        }
    }

    private void verifyMemberBinding(
            SomaGroupMemberPlan member,
            SomaMetadata metadata,
            SomaTableMetadata rootTable) {
        if (member.metadata() != metadata) {
            throw compatibility(
                    "group_member_metadata_mismatch",
                    member.memberId());
        }
        if (member.rootTable() != rootTable) {
            throw compatibility(
                    "group_member_root_mismatch",
                    member.memberId());
        }
    }

    private void rollbackAttach(
            MemberRuntime member,
            ChildOwnershipRegistry ownership,
            MemberContext context,
            Throwable primary) {
        Throwable cleanup = null;
        if (ownership != null) {
            try {
                ownership.abortUnpublishedConstruction();
                ledger.requireMemberDrained(
                        context.index, ownership, "group.attach.rollback");
            } catch (Throwable failure) {
                cleanup = failure;
            }
        }
        member.attaching = false;
        member.binding = null;
        member.ownership = null;
        member.aggregateInstanceId = 0L;
        member.attachmentOrdinal = 0L;
        member.state = SomaGroupMemberState.PLANNED;
        context.clear();
        if (cleanup != null && cleanup != primary) {
            primary.addSuppressed(cleanup);
        }
        if (cleanup != null || isInternal(primary)) {
            state = SomaGroupState.FAULTED;
        }
    }

    private void memberFaulted(
            int index,
            long aggregateInstanceId,
            String operation,
            String code) {
        MemberRuntime member = members[index];
        if (member.attaching) return;
        if (member.aggregateInstanceId != aggregateInstanceId
                || member.state == SomaGroupMemberState.PLANNED) {
            state = SomaGroupState.FAULTED;
            throw groupInternal(
                    "group_member_fault_identity",
                    member.plan.memberId(),
                    operation);
        }
        if (member.state == SomaGroupMemberState.RELEASED) return;
        member.state = SomaGroupMemberState.FAULTED;
        if (state == SomaGroupState.ACTIVE) {
            state = SomaGroupState.DEGRADED;
        }
    }

    private void preflightMemberAccess(
            int index, long aggregateInstanceId, String operation) {
        MemberRuntime member = members[index];
        if (member.attaching
                && member.aggregateInstanceId == aggregateInstanceId) {
            return;
        }
        if (member.aggregateInstanceId != aggregateInstanceId
                || member.state == SomaGroupMemberState.PLANNED) {
            throw groupInternal(
                    "group_member_access_identity",
                    member.plan.memberId(),
                    operation);
        }
        if (state == SomaGroupState.FAULTED
                && !isCleanupOrDiagnostic(operation)) {
            throw failure(
                    SomaErrorCategory.INTERNAL,
                    "group_faulted",
                    operation,
                    plan.logicalGroupId(),
                    null);
        }
        if (state == SomaGroupState.RELEASED
                && !isCleanupOrDiagnostic(operation)) {
            throw failure(
                    SomaErrorCategory.LIFECYCLE,
                    "group_released",
                    operation,
                    plan.logicalGroupId(),
                    null);
        }
    }

    private void releaseMemberRoot(
            int index, long aggregateInstanceId, String operation) {
        MemberRuntime member = members[index];
        if (member.aggregateInstanceId != aggregateInstanceId) {
            throw groupInternal(
                    "group_member_release_identity",
                    member.plan.memberId(),
                    operation);
        }
        if (plan.explicit()) {
            Map<String, String> context = new TreeMap<String, String>(
                    UnicodeCodePointOrder.INSTANCE);
            context.put("memberId", member.plan.memberId());
            throw failure(
                    SomaErrorCategory.CONFLICT,
                    "group_release_required",
                    operation,
                    plan.logicalGroupId(),
                    context);
        }
        release();
    }

    private void requireUsableGroup(String operation) {
        if (state == SomaGroupState.RELEASED) {
            throw failure(
                    SomaErrorCategory.LIFECYCLE,
                    "group_released",
                    operation,
                    plan.logicalGroupId(),
                    null);
        }
        if (state == SomaGroupState.FAULTED) {
            throw failure(
                    SomaErrorCategory.INTERNAL,
                    "group_faulted",
                    operation,
                    plan.logicalGroupId(),
                    null);
        }
    }

    private int requireMemberIndex(String memberId, String operation) {
        Integer index = memberIndexes.get(memberId);
        if (index == null) {
            throw failure(
                    SomaErrorCategory.INVALID_INPUT,
                    "unknown_group_member",
                    operation,
                    memberId,
                    null);
        }
        return index.intValue();
    }

    private void requireAttachmentOrdinalAvailable(String operation) {
        if (nextAttachmentOrdinal <= 0L
                || nextAttachmentOrdinal == Long.MAX_VALUE) {
            state = SomaGroupState.FAULTED;
            throw groupInternal(
                    "group_attachment_ordinal_exhausted",
                    plan.logicalGroupId(),
                    operation);
        }
    }

    private void requireMembershipEpochAvailable(String operation) {
        if (membershipEpoch == Long.MAX_VALUE) {
            state = SomaGroupState.FAULTED;
            throw groupInternal(
                    "group_membership_epoch_exhausted",
                    plan.logicalGroupId(),
                    operation);
        }
    }

    private static boolean isCleanupOrDiagnostic(String operation) {
        return "release".equals(operation)
                || "runtimePlan".equals(operation)
                || "runtimeMetadata".equals(operation)
                || "isReleased".equals(operation)
                || "statsSnapshot".equals(operation);
    }

    private static boolean isInternal(Throwable failure) {
        return failure instanceof SomaRuntimeException
                && ((SomaRuntimeException) failure).category()
                == SomaErrorCategory.INTERNAL;
    }

    private SomaRuntimeException compatibility(String code, String path) {
        return failure(
                SomaErrorCategory.COMPATIBILITY,
                code,
                "group.attach",
                path,
                null);
    }

    private SomaRuntimeException groupInternal(
            String invariant, String path, String operation) {
        Map<String, String> context = new TreeMap<String, String>(
                UnicodeCodePointOrder.INSTANCE);
        context.put("invariant", invariant);
        return failure(
                SomaErrorCategory.INTERNAL,
                "internal_invariant_violation",
                operation,
                path,
                context);
    }

    private static SomaRuntimeException failure(
            SomaErrorCategory category,
            String code,
            String operation,
            String path,
            Map<String, String> context) {
        return SomaRuntimeException.create(
                category,
                code,
                operation,
                path,
                context == null
                        ? new TreeMap<String, String>(
                                UnicodeCodePointOrder.INSTANCE)
                        : context,
                null);
    }

    private static long nextGroupInstanceId() {
        while (true) {
            long current = NEXT_GROUP_INSTANCE_ID.get();
            if (current <= 0L || current == Long.MAX_VALUE) {
                throw SomaRuntimeException.create(
                        SomaErrorCategory.INTERNAL,
                        "internal_invariant_violation",
                        "group.create",
                        "group",
                        Collections.singletonMap(
                                "invariant",
                                "group_instance_id_exhausted"),
                        null);
            }
            if (NEXT_GROUP_INSTANCE_ID.compareAndSet(
                    current, current + 1L)) {
                return current;
            }
        }
    }

    private static final class MemberRuntime {
        private final SomaGroupMemberPlan plan;
        private SomaGroupMemberState state = SomaGroupMemberState.PLANNED;
        private boolean attaching;
        private long aggregateInstanceId;
        private long attachmentOrdinal;
        private ChildOwnershipRegistry ownership;
        private Binding<?> binding;

        private MemberRuntime(SomaGroupMemberPlan plan) {
            this.plan = plan;
        }
    }

    private static final class Binding<T> {
        private final T root;
        private final GeneratedRootFactory<T> factory;

        private Binding(T root, GeneratedRootFactory<T> factory) {
            this.root = root;
            this.factory = factory;
        }

        private void preflightRelease() {
            factory.preflightRelease(root);
        }

        private void release() {
            factory.release(root);
        }

        private void preflightSafePoint(String operation) {
            factory.preflightSafePoint(root, operation);
        }

        private SomaTableRuntimeMetadata runtimeMetadata() {
            return factory.runtimeMetadata(root);
        }
    }

    private final class MemberContext implements GroupMembership {
        private final int index;
        private long aggregateInstanceId;

        private MemberContext(int index) {
            this.index = index;
        }

        @Override
        public void bindAggregate(long value) {
            if (value <= 0L || aggregateInstanceId != 0L) {
                throw groupInternal(
                        "group_member_aggregate_binding",
                        members[index].plan.memberId(),
                        "group.attach");
            }
            aggregateInstanceId = value;
        }

        @Override
        public void preflightAccess(long value, String operation) {
            preflightMemberAccess(index, value, operation);
        }

        @Override
        public void aggregateFaulted(
                long value, String operation, String code) {
            memberFaulted(index, value, operation, code);
        }

        @Override
        public void releaseRoot(long value, String operation) {
            releaseMemberRoot(index, value, operation);
        }

        private void clear() {
            aggregateInstanceId = 0L;
        }
    }

    private static final class MetadataSnapshot
            implements SomaGroupMetadata {
        private final String logicalGroupId;
        private final String groupPlanHash;
        private final long groupInstanceId;
        private final boolean explicit;
        private final SomaGroupState state;
        private final long membershipEpoch;
        private final String dataVersion;
        private final long maximumStructuralBytes;
        private final long retainedStructuralBytes;
        private final long transientStructuralBytes;
        private final long currentStructuralBytes;
        private final long structuralHighWaterBytes;
        private final long maximumTableInstances;
        private final long currentTableInstances;
        private final long tableInstanceHighWater;
        private final List<SomaGroupMemberMetadata> members;
        private final TreeMap<String, SomaGroupMemberMetadata> membersById =
                new TreeMap<String, SomaGroupMemberMetadata>(
                        UnicodeCodePointOrder.INSTANCE);

        private MetadataSnapshot(SomaGroup group) {
            logicalGroupId = group.plan.logicalGroupId();
            groupPlanHash = group.plan.groupPlanHash();
            groupInstanceId = group.groupInstanceId;
            explicit = group.plan.explicit();
            state = group.state;
            membershipEpoch = group.membershipEpoch;
            dataVersion = group.dataVersion;
            maximumStructuralBytes = group.ledger.maximumBytes();
            retainedStructuralBytes = group.ledger.retainedBytes();
            transientStructuralBytes = group.ledger.transientBytes();
            currentStructuralBytes = group.ledger.currentBytes();
            structuralHighWaterBytes = group.ledger.highWaterBytes();
            maximumTableInstances = group.ledger.maximumTableInstances();
            currentTableInstances = group.ledger.currentTableInstances();
            tableInstanceHighWater =
                    group.ledger.highWaterTableInstances();
            for (int index = 0; index < group.members.length; index++) {
                MemberMetadata snapshot = new MemberMetadata(
                        group.members[index], group.ledger, index);
                membersById.put(snapshot.memberId(), snapshot);
            }
            members = Collections.unmodifiableList(
                    new ArrayList<SomaGroupMemberMetadata>(
                            membersById.values()));
        }

        @Override public String logicalGroupId() { return logicalGroupId; }
        @Override public String groupPlanHash() { return groupPlanHash; }
        @Override public long groupInstanceId() { return groupInstanceId; }
        @Override public boolean explicit() { return explicit; }
        @Override public SomaGroupState state() { return state; }
        @Override public long membershipEpoch() { return membershipEpoch; }
        @Override public String dataVersion() { return dataVersion; }
        @Override public long maximumStructuralBytes() {
            return maximumStructuralBytes;
        }
        @Override public long retainedStructuralBytes() {
            return retainedStructuralBytes;
        }
        @Override public long transientStructuralBytes() {
            return transientStructuralBytes;
        }
        @Override public long currentStructuralBytes() {
            return currentStructuralBytes;
        }
        @Override public long structuralHighWaterBytes() {
            return structuralHighWaterBytes;
        }
        @Override public long maximumTableInstances() {
            return maximumTableInstances;
        }
        @Override public long currentTableInstances() {
            return currentTableInstances;
        }
        @Override public long tableInstanceHighWater() {
            return tableInstanceHighWater;
        }
        @Override public List<SomaGroupMemberMetadata> members() {
            return members;
        }
        @Override public SomaGroupMemberMetadata requireMember(
                String memberId) {
            String required = CanonicalSupport.required(
                    memberId, "memberId");
            SomaGroupMemberMetadata member = membersById.get(required);
            if (member == null) {
                throw failure(
                        SomaErrorCategory.INVALID_INPUT,
                        "unknown_group_member",
                        "group.metadata",
                        required,
                        null);
            }
            return member;
        }
    }

    private static final class MemberMetadata
            implements SomaGroupMemberMetadata {
        private final String memberId;
        private final String schemaHash;
        private final String rootTable;
        private final String runtimePlanHash;
        private final SomaGroupMemberState state;
        private final long aggregateInstanceId;
        private final long attachmentOrdinal;
        private final long maximumStructuralBytes;
        private final long retainedStructuralBytes;
        private final long transientStructuralBytes;
        private final long currentStructuralBytes;
        private final long structuralHighWaterBytes;
        private final long maximumTableInstances;
        private final long currentTableInstances;
        private final long tableInstanceHighWater;
        private final SomaTableRuntimeMetadata rootTableRuntimeMetadata;

        private MemberMetadata(
                MemberRuntime member,
                GroupLedger ledger,
                int memberIndex) {
            memberId = member.plan.memberId();
            schemaHash = member.plan.runtimePlan().schemaHash();
            rootTable = member.plan.rootTable().logicalName();
            runtimePlanHash =
                    member.plan.runtimePlan().runtimePlanHash();
            state = member.state;
            aggregateInstanceId = member.aggregateInstanceId;
            attachmentOrdinal = member.attachmentOrdinal;
            maximumStructuralBytes =
                    ledger.memberMaximumBytes(memberIndex);
            retainedStructuralBytes =
                    ledger.memberRetainedBytes(memberIndex);
            transientStructuralBytes =
                    ledger.memberTransientBytes(memberIndex);
            currentStructuralBytes =
                    ledger.memberCurrentBytes(memberIndex);
            structuralHighWaterBytes =
                    ledger.memberHighWaterBytes(memberIndex);
            maximumTableInstances =
                    ledger.memberMaximumTableInstances(memberIndex);
            currentTableInstances =
                    ledger.memberCurrentTableInstances(memberIndex);
            tableInstanceHighWater =
                    ledger.memberHighWaterTableInstances(memberIndex);
            rootTableRuntimeMetadata = member.binding == null
                    ? null : member.binding.runtimeMetadata();
        }

        @Override public String memberId() { return memberId; }
        @Override public String schemaHash() { return schemaHash; }
        @Override public String rootTable() { return rootTable; }
        @Override public String runtimePlanHash() { return runtimePlanHash; }
        @Override public SomaGroupMemberState state() { return state; }
        @Override public long aggregateInstanceId() {
            return aggregateInstanceId;
        }
        @Override public long attachmentOrdinal() {
            return attachmentOrdinal;
        }
        @Override public long maximumStructuralBytes() {
            return maximumStructuralBytes;
        }
        @Override public long retainedStructuralBytes() {
            return retainedStructuralBytes;
        }
        @Override public long transientStructuralBytes() {
            return transientStructuralBytes;
        }
        @Override public long currentStructuralBytes() {
            return currentStructuralBytes;
        }
        @Override public long structuralHighWaterBytes() {
            return structuralHighWaterBytes;
        }
        @Override public long maximumTableInstances() {
            return maximumTableInstances;
        }
        @Override public long currentTableInstances() {
            return currentTableInstances;
        }
        @Override public long tableInstanceHighWater() {
            return tableInstanceHighWater;
        }
        @Override public SomaTableRuntimeMetadata
                rootTableRuntimeMetadata() {
            return rootTableRuntimeMetadata;
        }
    }
}
