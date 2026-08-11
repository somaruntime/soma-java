package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

/** Terminal lifecycle coordinator; owns neither semantics nor physical decisions. */
final class CanonicalQueryOperation {

    private CanonicalQueryOperation() {
    }

    static long optimizedCount(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return execute(table, canonical, new ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return 0L;
            }
        }, new FrameWork<Long>() {
            @Override public Long run(CanonicalRowExecutionFrame frame) {
                return CanonicalRowExecution.count(frame);
            }
        });
    }

    static long referenceCountForTesting(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return executeReference(table, canonical, new ReferenceWork<Long>() {
            @Override public Long run(BoundCanonicalRowOperation bound) {
                return ReferenceCanonicalRowInterpreter.count(bound);
            }
        });
    }

    static boolean anyMatch(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return execute(table, canonical, ZERO_SCRATCH, new FrameWork<Boolean>() {
            @Override public Boolean run(final CanonicalRowExecutionFrame frame) {
                final boolean[] matched = new boolean[1];
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                if (RowExecutionSupport.callbackTest(
                                        frame.plan.normalized.bound,
                                        locator,
                                        frame.plan.normalized.bound.canonical
                                                .terminalCallback)) {
                                    matched[0] = true;
                                    return false;
                                }
                                return true;
                            }
                        });
                return matched[0];
            }
        });
    }

    static boolean allMatch(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return execute(table, canonical, ZERO_SCRATCH, new FrameWork<Boolean>() {
            @Override public Boolean run(final CanonicalRowExecutionFrame frame) {
                final boolean[] all = new boolean[] {true};
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                if (!RowExecutionSupport.callbackTest(
                                        frame.plan.normalized.bound,
                                        locator,
                                        frame.plan.normalized.bound.canonical
                                                .terminalCallback)) {
                                    all[0] = false;
                                    return false;
                                }
                                return true;
                            }
                        });
                return all[0];
            }
        });
    }

    static <R> Optional<R> findFirst(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return execute(table, canonical, new ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return materializationScratch(
                        bound, 1L,
                        bound.layout.detachedRowEstimateBytes(), 32L);
            }
        }, new FrameWork<Optional<R>>() {
            @Override public Optional<R> run(final CanonicalRowExecutionFrame frame) {
                final Object[] first = new Object[1];
                final boolean[] present = new boolean[1];
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                first[0] = RowExecutionSupport.callbackMap(
                                        frame.plan.normalized.bound,
                                        locator,
                                        frame.plan.normalized.bound.canonical
                                                .terminalCallback,
                                        false);
                                present[0] = true;
                                return false;
                            }
                        });
                @SuppressWarnings("unchecked") R value = (R) first[0];
                if (present[0] && value == null) {
                    BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                    throw SomaFailures.failure(
                            SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                            SomaOperation.QUERY,
                            "selected null cannot be represented by Java Optional",
                            bound.provenance);
                }
                return present[0] ? Optional.of(value) : Optional.<R>empty();
            }
        });
    }

    static void forEach(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        execute(table, canonical, ZERO_SCRATCH, new FrameWork<Object>() {
            @Override public Object run(final CanonicalRowExecutionFrame frame) {
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                RowExecutionSupport.callbackAction(
                                        frame.plan.normalized.bound,
                                        locator,
                                        frame.plan.normalized.bound.canonical
                                                .terminalCallback);
                                return true;
                            }
                        });
                return null;
            }
        });
    }

    static <R> List<R> toList(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            final long detachedElementEstimateBytes) {
        return execute(table, canonical, new ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return materializationScratch(
                        bound,
                        bound.outputUpperBound(),
                        detachedElementEstimateBytes,
                        48L);
            }
        }, new FrameWork<List<R>>() {
            @Override public List<R> run(final CanonicalRowExecutionFrame frame) {
                final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                int upper = RowExecutionSupport.arrayLength(
                        bound.outputUpperBound(), bound.provenance);
                final ArrayList<R> result = new ArrayList<R>(upper);
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                result.add(RowExecutionSupport.<R>callbackMap(
                                        bound,
                                        locator,
                                        bound.canonical.terminalCallback,
                                        false));
                                return true;
                            }
                        });
                return result;
            }
        });
    }

    static <R> R[] toArray(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            final Class<R> componentType,
            final long detachedElementEstimateBytes) {
        return execute(table, canonical, new ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return materializationScratch(
                        bound,
                        bound.outputUpperBound(),
                        detachedElementEstimateBytes,
                        56L);
            }
        }, new FrameWork<R[]>() {
            @Override public R[] run(final CanonicalRowExecutionFrame frame) {
                final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                int upper = RowExecutionSupport.arrayLength(
                        bound.outputUpperBound(), bound.provenance);
                @SuppressWarnings("unchecked")
                final R[] staging = (R[]) Array.newInstance(componentType, upper);
                final int[] size = new int[1];
                CanonicalRowExecution.visit(
                        frame,
                        new CanonicalRowExecution.LocatorVisitor() {
                            @Override public boolean visit(int locator) {
                                staging[size[0]++] = RowExecutionSupport.<R>callbackMap(
                                        bound,
                                        locator,
                                        bound.canonical.terminalCallback,
                                        false);
                                return true;
                            }
                        });
                if (size[0] == upper) return staging;
                @SuppressWarnings("unchecked")
                R[] result = (R[]) Array.newInstance(componentType, size[0]);
                System.arraycopy(staging, 0, result, 0, size[0]);
                return result;
            }
        });
    }

    static IntLocatorBuffer optimizedLocators(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return execute(table, canonical, ZERO_SCRATCH,
                new FrameWork<IntLocatorBuffer>() {
                    @Override public IntLocatorBuffer run(
                            CanonicalRowExecutionFrame frame) {
                        return CanonicalRowExecution.locators(frame);
                    }
                });
    }

    static IntLocatorBuffer referenceLocatorsForTesting(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        return executeReference(
                table, canonical,
                new ReferenceWork<IntLocatorBuffer>() {
                    @Override public IntLocatorBuffer run(
                            BoundCanonicalRowOperation bound) {
                        return ReferenceCanonicalRowInterpreter.locators(bound);
                    }
                });
    }

    static String explain(
            GeneratedTable table,
            CanonicalRowOperation canonical) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(
                    table, canonical, operation, SomaOperation.QUERY);
            return explainBound(bound);
        }
    }

    static String explainBound(BoundCanonicalRowOperation bound) {
            CanonicalRowOperation canonical = bound.canonical;
            NormalizedCanonicalRow normalized = CanonicalRowPlanner.normalize(bound);
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(normalized);
            StringBuilder result = new StringBuilder(320);
            result.append("SOMA logicalSource=")
                    .append(canonical.sourceKind == CanonicalRowOperation.SourceKind.TABLE
                            ? "TABLE_SCAN" : "INDEX_SELECTION")
                    .append(" logicalStages=");
            appendStages(result, canonical.stages);
            result.append(" normalizedStages=");
            appendStages(result, normalized.stages);
            int logicalTyped = countTyped(canonical.stages);
            int normalizedTyped = countTyped(normalized.stages);
            result.append(" physicalSource=")
                    .append(physical.accessPath)
                    .append(" mode=")
                    .append(canonical.request.mode)
                    .append(" indexSubstitution=")
                    .append(physical.accessPath
                            == CanonicalRowPhysicalPlan.AccessPath.KEY_LOOKUP
                            || physical.accessPath
                            == CanonicalRowPhysicalPlan.AccessPath.INDEX_LOOKUP)
                    .append(" residualTyped=")
                    .append(normalizedTyped)
                    .append(" adjacentTypedNormalized=")
                    .append(logicalTyped > normalizedTyped)
                    .append(" callbackBarrier=")
                    .append(hasCallbackBarrier(canonical.stages))
                    .append(" statefulBarrier=")
                    .append(canonical.hasStatefulStage())
                    .append(" requiredLeaves=");
            int required = appendRequiredLeaves(
                    result, bound.layout, normalized.stages);
            result.append(" leafPruning=")
                    .append(required > 0 && required < bound.layout.leafCount())
                    .append(" statelessFusion=")
                    .append(hasStatelessFusion(normalized.stages))
                    .append(" boundedTop=")
                    .append(CanonicalRowExecution.usesBoundedTypedTop(physical))
                    .append(" inMembershipLiterals=")
                    .append(canonical.inLiteralCount())
                    .append(" order=canonical estimatedTemporaryPeakBytes=")
                    .append(physical.resources.temporaryBytes)
                    .append(' ')
                    .append(bound.table.compressionExplain(bound.root));
            return result.toString();
    }

    private static <T> T execute(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            ExtraScratch extra,
            FrameWork<T> work) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(
                    table, canonical, operation, SomaOperation.QUERY);
            NormalizedCanonicalRow normalized = CanonicalRowPlanner.normalize(bound);
            CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(normalized);
            long temporaryBytes = CheckedLong.add(
                    physical.resources.temporaryBytes,
                    extra.bytes(bound),
                    bound.operation,
                    bound.provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(
                                 temporaryBytes, bound.provenance)) {
                beginCursors(table, bound);
                try {
                    CanonicalRowExecutionFrame frame =
                            new CanonicalRowExecutionFrame(physical);
                    CanonicalParallelRowScheduler.prepare(frame);
                    return work.run(frame);
                } finally {
                    endCursors(table);
                }
            }
        }
    }

    /** Shared admitted frame seam for specialized mapped/primitive families. */
    static <T> T executeFamily(
            GeneratedTable table,
            CanonicalRowOperation source,
            ExtraScratch extra,
            FrameWork<T> work) {
        return execute(table, source, extra, work);
    }

    private static <T> T executeReference(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            ReferenceWork<T> work) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(
                    table, canonical, operation, SomaOperation.QUERY);
            long temporaryBytes = referenceTemporaryBytes(bound);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(
                                 temporaryBytes, bound.provenance)) {
                beginCursors(table, bound);
                try {
                    return work.run(bound);
                } finally {
                    endCursors(table);
                }
            }
        }
    }

    /** Independent bound seam for family-specific reference interpreters. */
    static <T> T executeReferenceFamily(
            GeneratedTable table,
            CanonicalRowOperation source,
            ReferenceExtraScratch extra,
            ReferenceWork<T> work) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundCanonicalRowOperation bound = bind(
                    table, source, operation, SomaOperation.QUERY);
            long temporaryBytes = CheckedLong.add(
                    referenceTemporaryBytes(bound),
                    extra.bytes(bound),
                    bound.operation,
                    bound.provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(
                                 temporaryBytes, bound.provenance)) {
                beginCursors(table, bound);
                try {
                    return work.run(bound);
                } finally {
                    endCursors(table);
                }
            }
        }
    }

    /** Read-only bound inspection; explain never creates a frame or submits work. */
    static <T> T inspectFamily(
            GeneratedTable table,
            CanonicalRowOperation source,
            ReferenceWork<T> work) {
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            return work.run(bind(
                    table, source, operation, SomaOperation.QUERY));
        }
    }

    private static long referenceTemporaryBytes(
            BoundCanonicalRowOperation bound) {
        long result = CheckedLong.multiply(
                bound.canonical.inLiteralCount(),
                256L,
                bound.operation,
                bound.provenance);
        if (bound.canonical.hasStatefulStage()) {
            result = CheckedLong.add(
                    result,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 96L, bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        if (bound.canonical.terminal
                == CanonicalRowOperation.TerminalKind.LOCATORS_TEST) {
            result = CheckedLong.add(
                    result,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 24L, bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        return result;
    }

    private static BoundCanonicalRowOperation bind(
            GeneratedTable table,
            CanonicalRowOperation canonical,
            GroupOperationGuard.Lease operation,
            SomaOperation kind) {
        if (!canonical.tableIdentity.sameTable(table.logicalIdentity())) {
            throw new AssertionError("Canonical operation bound to another Table");
        }
        return new BoundCanonicalRowOperation(
                canonical,
                table,
                table.layout(),
                table.currentRoot(),
                kind,
                operation.provenance());
    }

    private static void beginCursors(
            GeneratedTable table,
            BoundCanonicalRowOperation bound) {
        table.queryCursor().begin(bound.root, bound.operation, bound.provenance);
        try {
            table.secondaryQueryCursor().begin(
                    bound.root, bound.operation, bound.provenance);
        } catch (RuntimeException failure) {
            table.queryCursor().end();
            throw failure;
        }
    }

    private static void endCursors(GeneratedTable table) {
        table.secondaryQueryCursor().end();
        table.queryCursor().end();
    }

    private static long materializationScratch(
            BoundCanonicalRowOperation bound,
            long elements,
            long detachedElementEstimateBytes,
            long containerBytesPerElement) {
        long bytesPerElement = CheckedLong.add(
                detachedElementEstimateBytes,
                containerBytesPerElement,
                bound.operation,
                bound.provenance);
        return RowExecutionSupport.arrayBytes(
                elements, bytesPerElement, bound.provenance);
    }

    private static void appendStages(
            StringBuilder target,
            List<CanonicalRowStage> stages) {
        target.append('[');
        for (int index = 0; index < stages.size(); index++) {
            if (index != 0) target.append(',');
            target.append(stages.get(index).kind);
        }
        target.append(']');
    }

    private static int countTyped(List<CanonicalRowStage> stages) {
        int result = 0;
        for (CanonicalRowStage stage : stages) {
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER) result++;
        }
        return result;
    }

    private static boolean hasCallbackBarrier(List<CanonicalRowStage> stages) {
        for (CanonicalRowStage stage : stages) {
            if (stage.kind == CanonicalRowStage.Kind.CALLBACK_FILTER
                    || stage.kind == CanonicalRowStage.Kind.CALLBACK_ORDER) return true;
        }
        return false;
    }

    private static boolean hasStatelessFusion(List<CanonicalRowStage> stages) {
        int eligible = 0;
        for (CanonicalRowStage stage : stages) {
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER
                    || stage.kind == CanonicalRowStage.Kind.FIELD_PROJECT
                    || stage.kind == CanonicalRowStage.Kind.SKIP
                    || stage.kind == CanonicalRowStage.Kind.LIMIT) {
                if (++eligible >= 2) return true;
            } else {
                eligible = 0;
            }
        }
        return false;
    }

    private static int appendRequiredLeaves(
            StringBuilder target,
            GeneratedTableLayout layout,
            List<CanonicalRowStage> stages) {
        boolean[] required = new boolean[layout.leafCount()];
        boolean opaque = false;
        for (CanonicalRowStage stage : stages) {
            switch (stage.kind) {
                case TYPED_FILTER:
                    collectPredicateLeaves(layout, stage.predicate, required);
                    break;
                case TYPED_ORDER:
                    for (int index = 0; index < stage.order.size(); index++) {
                        markFieldLeaves(
                                layout, stage.order.fieldIndex(index), required);
                    }
                    break;
                case DISTINCT_FIELD:
                case FIELD_PROJECT:
                    markFieldLeaves(layout, stage.fieldIndex, required);
                    break;
                case CALLBACK_FILTER:
                case CALLBACK_ORDER:
                    opaque = true;
                    break;
                default:
                    break;
            }
        }
        if (opaque) Arrays.fill(required, true);
        int count = 0;
        target.append('[');
        for (int leaf = 0; leaf < required.length; leaf++) {
            if (!required[leaf]) continue;
            if (count++ != 0) target.append(',');
            target.append(leaf);
        }
        target.append(']');
        return count;
    }

    private static void collectPredicateLeaves(
            GeneratedTableLayout layout,
            PredicateIr predicate,
            boolean[] required) {
        switch (predicate.kind) {
            case AND:
            case OR:
                collectPredicateLeaves(layout, predicate.left, required);
                collectPredicateLeaves(layout, predicate.right, required);
                return;
            case NOT:
                collectPredicateLeaves(layout, predicate.left, required);
                return;
            case CONSTANT:
                return;
            default:
                markFieldLeaves(layout, predicate.fieldIndex, required);
        }
    }

    private static void markFieldLeaves(
            GeneratedTableLayout layout,
            int field,
            boolean[] required) {
        int start = layout.fieldStart(field);
        Arrays.fill(
                required, start, start + layout.fieldLeafCount(field), true);
    }

    private static final ExtraScratch ZERO_SCRATCH = new ExtraScratch() {
        @Override public long bytes(BoundCanonicalRowOperation bound) {
            return 0L;
        }
    };

    interface ExtraScratch {
        long bytes(BoundCanonicalRowOperation bound);
    }

    interface FrameWork<T> {
        T run(CanonicalRowExecutionFrame frame);
    }

    interface ReferenceWork<T> {
        T run(BoundCanonicalRowOperation bound);
    }

    interface ReferenceExtraScratch {
        long bytes(BoundCanonicalRowOperation bound);
    }
}
