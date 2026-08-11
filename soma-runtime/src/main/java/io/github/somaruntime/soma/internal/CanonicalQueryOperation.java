package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** S1 terminal lifecycle coordinator; it owns no semantic or physical facts. */
final class CanonicalQueryOperation {

    private CanonicalQueryOperation() {
    }

    static long optimizedCount(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(table, canonical, operation);
            NormalizedCanonicalRow normalized = CanonicalRowPlanner.normalize(bound);
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(normalized);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(
                                 physical.resources.temporaryBytes,
                                 bound.provenance)) {
                CanonicalRowExecutionFrame frame =
                        new CanonicalRowExecutionFrame(physical);
                return CanonicalRowExecution.count(frame);
            }
        }
    }

    static long referenceCountForTesting(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(table, canonical, operation);
            long temporaryBytes = CheckedLong.multiply(
                    canonical.inLiteralCount(),
                    256L,
                    bound.operation,
                    bound.provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(
                                 temporaryBytes, bound.provenance)) {
                return ReferenceCanonicalRowInterpreter.count(bound);
            }
        }
    }

    private static BoundCanonicalRowOperation bind(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            GroupOperationGuard.Lease operation) {
        if (!canonical.tableIdentity.sameTable(table.logicalIdentity())) {
            throw new AssertionError("Canonical operation bound to another Table");
        }
        return new BoundCanonicalRowOperation(
                canonical,
                table.layout(),
                table.currentRoot(),
                SomaOperation.QUERY,
                operation.provenance());
    }
}
