package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaGroup;
import com.hgtech.soma.runtime.SomaGroupPlan;
import com.hgtech.soma.runtime.metadata.SomaMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;

/** Narrow generated-code bridge for implicit Group creation and typed root attach。 */
public final class GeneratedSomaGroup {
    private static final String IMPLICIT_MEMBER_ID = "root";

    private GeneratedSomaGroup() {
    }

    public static <T> T createImplicit(
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            RuntimePlan runtimePlan,
            GeneratedRootFactory<T> factory) {
        if (metadata == null) throw new NullPointerException("metadata");
        if (rootTable == null) throw new NullPointerException("rootTable");
        if (runtimePlan == null) throw new NullPointerException("runtimePlan");
        String logicalId = "implicit/" + runtimePlan.schemaHash()
                + "/" + rootTable.logicalName();
        SomaGroupPlan groupPlan = SomaGroupPlan.generatedImplicit(
                GeneratedPlanToken.INSTANCE,
                logicalId,
                IMPLICIT_MEMBER_ID,
                metadata,
                rootTable,
                runtimePlan);
        SomaGroup group = SomaGroup.create(groupPlan);
        try {
            return group.generatedAttach(
                    GeneratedPlanToken.INSTANCE,
                    IMPLICIT_MEMBER_ID,
                    metadata,
                    rootTable,
                    factory);
        } catch (RuntimeException failure) {
            abortImplicit(group, failure);
            throw failure;
        } catch (Error failure) {
            abortImplicit(group, failure);
            throw failure;
        }
    }

    public static <T> T attach(
            SomaGroup group,
            String memberId,
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            GeneratedRootFactory<T> factory) {
        if (group == null) throw new NullPointerException("group");
        return group.generatedAttach(
                GeneratedPlanToken.INSTANCE,
                memberId,
                metadata,
                rootTable,
                factory);
    }

    private static void abortImplicit(SomaGroup group, Throwable primary) {
        try {
            group.release();
        } catch (Throwable cleanup) {
            if (cleanup != primary) primary.addSuppressed(cleanup);
        }
    }
}
