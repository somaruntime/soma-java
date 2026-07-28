package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaGroup;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.metadata.SomaMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableRuntimeMetadata;

import java.util.List;

/** Test-source generated-protocol harness; it is never packaged in production artifacts。 */
public final class GroupTestProtocol {
    private GroupTestProtocol() {
    }

    public static Root attach(
            SomaGroup group,
            String memberId,
            SomaMetadata metadata,
            SomaTableMetadata rootTable,
            String releaseMarker,
            List<String> releaseOrder) {
        return group.generatedAttach(
                GeneratedPlanToken.INSTANCE,
                memberId,
                metadata,
                rootTable,
                new Factory(releaseMarker, releaseOrder));
    }

    public static void fault(Root root) {
        root.ownership.markUnexpectedFailure("test.group.fault");
    }

    public static final class Root {
        private final ChildOwnershipRegistry ownership;
        private final TableLedger rootLedger;
        private final String releaseMarker;
        private final List<String> releaseOrder;
        private boolean blockPreflight;
        private boolean failReleaseOnce;

        private Root(
                ChildOwnershipRegistry ownership,
                TableLedger rootLedger,
                String releaseMarker,
                List<String> releaseOrder) {
            this.ownership = ownership;
            this.rootLedger = rootLedger;
            this.releaseMarker = releaseMarker;
            this.releaseOrder = releaseOrder;
        }

        public void blockPreflight(boolean value) {
            blockPreflight = value;
        }

        public void failReleaseOnce() {
            failReleaseOnce = true;
        }
    }

    private static final class Factory
            implements GeneratedRootFactory<Root> {
        private final String releaseMarker;
        private final List<String> releaseOrder;

        private Factory(
                String releaseMarker,
                List<String> releaseOrder) {
            this.releaseMarker = releaseMarker;
            this.releaseOrder = releaseOrder;
        }

        @Override
        public Root create(
                RuntimePlan runtimePlan,
                TablePlan tablePlan,
                ChildOwnershipRegistry ownership) {
            TableLedger rootLedger =
                    ownership.newTableLedgerInternal(
                            tablePlan.tableLogicalName());
            rootLedger.reserveTableInstance("test.group.root.create");
            return new Root(
                    ownership, rootLedger, releaseMarker, releaseOrder);
        }

        @Override
        public void preflightRelease(Root root) {
            if (root.blockPreflight) {
                throw RuntimeFailures.viewPinned(
                        root.releaseMarker, "release", 1);
            }
        }

        @Override
        public void release(Root root) {
            if (root.failReleaseOnce) {
                root.failReleaseOnce = false;
                throw new IllegalStateException(
                        "intentional group cleanup failure");
            }
            root.rootLedger.releaseTableInstance(
                    "test.group.root.release");
            root.releaseOrder.add(root.releaseMarker);
        }

        @Override
        public void preflightSafePoint(Root root, String operation) {
            root.ownership.preflightSafePoint(operation);
        }

        @Override
        public SomaTableRuntimeMetadata runtimeMetadata(Root root) {
            return null;
        }
    }
}
