package io.github.somaruntime.soma.internal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Coordinates validation-complete query admission, binding, execution and release. */
final class QueryOperation {

    private QueryOperation() {
    }

    static long optimizedCount(final LogicalRowPlan logical) {
        CanonicalRowOperation canonical = CanonicalRowLowering.count(
                logical.owner(), logical);
        if (canonical != null) {
            return CanonicalQueryOperation.optimizedCount(
                    logical.owner(), canonical);
        }
        return execute(logical, new BoundWork<Long>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return rowExecutionScratch(bound);
            }

            @Override
            public Long run(BoundRowPlan bound) {
                return RowExecutor.count(bound);
            }
        });
    }

    static long referenceCountForTesting(final LogicalRowPlan logical) {
        CanonicalRowOperation canonical = CanonicalRowLowering.count(
                logical.owner(), logical);
        if (canonical != null) {
            return CanonicalQueryOperation.referenceCountForTesting(
                    logical.owner(), canonical);
        }
        return execute(logical, new BoundWork<Long>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return rowExecutionScratch(bound);
            }

            @Override
            public Long run(BoundRowPlan bound) {
                return ReferenceRowInterpreter.count(bound);
            }
        });
    }

    static boolean anyMatch(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowPredicate predicate) {
        return execute(logical, new BoundWork<Boolean>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return rowExecutionScratch(bound);
            }

            @Override
            public Boolean run(final BoundRowPlan bound) {
                final boolean[] matched = new boolean[1];
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                if (RowExecutionSupport.callbackTest(
                                        bound, locator, predicate)) {
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
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowPredicate predicate) {
        return execute(logical, new BoundWork<Boolean>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return rowExecutionScratch(bound);
            }

            @Override
            public Boolean run(final BoundRowPlan bound) {
                final boolean[] all = new boolean[] {true};
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                if (!RowExecutionSupport.callbackTest(
                                        bound, locator, predicate)) {
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

    static boolean noneMatch(
            LogicalRowPlan logical,
            GeneratedCallbacks.RowPredicate predicate) {
        return !anyMatch(logical, predicate);
    }

    static <R> Optional<R> findFirst(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowMapper<R> materializer) {
        return execute(logical, new BoundWork<Optional<R>>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return addScratch(
                        rowExecutionScratch(bound),
                        materializationScratch(bound, 1L, 32L),
                        bound.provenance);
            }

            @Override
            public Optional<R> run(final BoundRowPlan bound) {
                final Object[] first = new Object[1];
                final boolean[] present = new boolean[1];
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                first[0] = RowExecutionSupport.callbackMap(
                                        bound, locator, materializer, false);
                                present[0] = true;
                                return false;
                            }
                        });
                @SuppressWarnings("unchecked")
                R value = (R) first[0];
                if (present[0] && value == null) {
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
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowAction action) {
        execute(logical, new BoundWork<Object>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return rowExecutionScratch(bound);
            }

            @Override
            public Object run(final BoundRowPlan bound) {
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                RowExecutionSupport.callbackAction(bound, locator, action);
                                return true;
                            }
                        });
                return null;
            }
        });
    }

    static <R> List<R> toList(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowMapper<R> materializer) {
        return toList(
                logical,
                materializer,
                logical.owner().layout().detachedRowEstimateBytes());
    }

    static <R> List<R> toList(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowMapper<R> materializer,
            final long detachedElementEstimateBytes) {
        return execute(logical, new BoundWork<List<R>>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return addScratch(
                        rowExecutionScratch(bound),
                        materializationScratch(
                                bound,
                                bound.outputUpperBound(),
                                detachedElementEstimateBytes,
                                48L),
                        bound.provenance);
            }

            @Override
            public List<R> run(final BoundRowPlan bound) {
                int upper = RowExecutionSupport.arrayLength(
                        bound.outputUpperBound(), bound.provenance);
                final ArrayList<R> result = new ArrayList<R>(upper);
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                result.add(RowExecutionSupport.callbackMap(
                                        bound, locator, materializer, false));
                                return true;
                            }
                        });
                return result;
            }
        });
    }

    static <R> R[] toArray(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowMapper<R> materializer,
            final Class<R> componentType) {
        return toArray(
                logical,
                materializer,
                componentType,
                logical.owner().layout().detachedRowEstimateBytes());
    }

    static <R> R[] toArray(
            LogicalRowPlan logical,
            final GeneratedCallbacks.RowMapper<R> materializer,
            final Class<R> componentType,
            final long detachedElementEstimateBytes) {
        return execute(logical, new BoundWork<R[]>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return addScratch(
                        rowExecutionScratch(bound),
                        materializationScratch(
                                bound,
                                bound.outputUpperBound(),
                                detachedElementEstimateBytes,
                                56L),
                        bound.provenance);
            }

            @Override
            public R[] run(final BoundRowPlan bound) {
                int upper = RowExecutionSupport.arrayLength(
                        bound.outputUpperBound(), bound.provenance);
                @SuppressWarnings("unchecked")
                final R[] staging = (R[]) Array.newInstance(componentType, upper);
                final int[] size = new int[1];
                RowExecutor.visit(
                        bound,
                        new OptimizedSequentialRowExecutor.LocatorVisitor() {
                            @Override
                            public boolean visit(int locator) {
                                staging[size[0]++] = RowExecutionSupport.callbackMap(
                                        bound, locator, materializer, false);
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

    static String explain(final LogicalRowPlan logical) {
        return execute(logical, new BoundWork<String>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return 0L;
            }

            @Override
            public String run(BoundRowPlan bound) {
                return explainBound(logical, bound);
            }
        });
    }

    /** Builds diagnostics under an already-acquired query operation. */
    static String explainBound(LogicalRowPlan logical, BoundRowPlan bound) {
        NormalizedRowPlan optimized = RowOptimizer.describe(logical);
        StringBuilder result = new StringBuilder(320);
        result.append("SOMA logicalSource=")
                .append(logical.sourceKind())
                .append(" logicalStages=");
        appendStages(result, logical.stages());
        result.append(" normalizedStages=");
        appendStages(result, optimized.stages);
        int logicalTyped = countStages(
                logical.stages(), LogicalRowPlan.StageKind.TYPED_FILTER);
        int normalizedTyped = countStages(
                optimized.stages, LogicalRowPlan.StageKind.TYPED_FILTER);
        result.append(" physicalSource=")
                .append(optimized.sourceKind)
                .append(" mode=")
                .append(logical.isParallel() ? "PARALLEL" : "SEQUENTIAL")
                .append(" indexSubstitution=")
                .append(optimized.sourceKind == NormalizedRowPlan.SourceKind.KEY_LOOKUP
                        || optimized.sourceKind == NormalizedRowPlan.SourceKind.INDEX_LOOKUP)
                .append(" residualTyped=")
                .append(normalizedTyped)
                .append(" adjacentTypedNormalized=")
                .append(logicalTyped > normalizedTyped)
                .append(" callbackBarrier=")
                .append(hasCallbackBarrier(logical.stages()))
                .append(" statefulBarrier=")
                .append(logical.hasStatefulStage())
                .append(" requiredLeaves=");
        int requiredLeafCount = appendRequiredLeaves(
                result, logical.owner().layout(), optimized.stages);
        result.append(" leafPruning=")
                .append(requiredLeafCount > 0
                        && requiredLeafCount
                        < logical.owner().layout().leafCount())
                .append(" statelessFusion=")
                .append(hasStatelessFusion(optimized.stages))
                .append(" boundedTop=")
                .append(OptimizedSequentialRowExecutor.usesBoundedTypedTop(
                        bound, optimized))
                .append(" inMembershipLiterals=")
                .append(logical.inLiteralCount())
                .append(" order=canonical estimatedTemporaryPeakBytes=")
                .append(rowExecutionScratch(bound))
                .append(' ')
                .append(logical.owner().compressionExplain(bound.root));
        return result.toString();
    }

    private static int appendRequiredLeaves(
            StringBuilder target,
            GeneratedTableLayout layout,
            List<LogicalRowPlan.Stage> stages) {
        boolean[] required = new boolean[layout.leafCount()];
        boolean opaque = false;
        for (LogicalRowPlan.Stage stage : stages) {
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
                    markFieldLeaves(layout, (int) stage.count, required);
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
            if (count != 0) target.append(',');
            target.append(leaf);
            count++;
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
            int fieldIndex,
            boolean[] required) {
        int start = layout.fieldStart(fieldIndex);
        int count = layout.fieldLeafCount(fieldIndex);
        Arrays.fill(required, start, start + count, true);
    }

    private static boolean hasStatelessFusion(
            List<LogicalRowPlan.Stage> stages) {
        int eligible = 0;
        for (LogicalRowPlan.Stage stage : stages) {
            if (stage.kind == LogicalRowPlan.StageKind.TYPED_FILTER
                    || stage.kind == LogicalRowPlan.StageKind.FIELD_PROJECT
                    || stage.kind == LogicalRowPlan.StageKind.SKIP
                    || stage.kind == LogicalRowPlan.StageKind.LIMIT) {
                eligible++;
                if (eligible >= 2) return true;
            } else {
                eligible = 0;
            }
        }
        return false;
    }

    private static int countStages(
            List<LogicalRowPlan.Stage> stages,
            LogicalRowPlan.StageKind kind) {
        int result = 0;
        for (LogicalRowPlan.Stage stage : stages) {
            if (stage.kind == kind) result++;
        }
        return result;
    }

    private static boolean hasCallbackBarrier(
            List<LogicalRowPlan.Stage> stages) {
        for (LogicalRowPlan.Stage stage : stages) {
            if (stage.kind == LogicalRowPlan.StageKind.CALLBACK_FILTER
                    || stage.kind == LogicalRowPlan.StageKind.CALLBACK_ORDER) {
                return true;
            }
        }
        return false;
    }

    static long[] optimizedLocatorsForTesting(final LogicalRowPlan logical) {
        return execute(logical, new BoundWork<long[]>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return addScratch(
                        rowExecutionScratch(bound),
                        RowExecutionSupport.arrayBytes(
                                bound.root.size, 16L, bound.provenance),
                        bound.provenance);
            }

            @Override
            public long[] run(BoundRowPlan bound) {
                return copy(RowExecutor.locators(bound));
            }
        });
    }

    static long[] referenceLocatorsForTesting(final LogicalRowPlan logical) {
        return execute(logical, new BoundWork<long[]>() {
            @Override
            public long scratchBytes(BoundRowPlan bound) {
                return addScratch(
                        rowExecutionScratch(bound),
                        RowExecutionSupport.arrayBytes(
                                bound.root.size, 16L, bound.provenance),
                        bound.provenance);
            }

            @Override
            public long[] run(BoundRowPlan bound) {
                return copy(ReferenceRowInterpreter.locators(bound));
            }
        });
    }

    private static long[] copy(IntLocatorBuffer source) {
        long[] result = new long[source.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = source.get(index);
        }
        return result;
    }

    private static void appendStages(
            StringBuilder target,
            List<LogicalRowPlan.Stage> stages) {
        target.append('[');
        for (int index = 0; index < stages.size(); index++) {
            if (index != 0) target.append(',');
            target.append(stages.get(index).kind);
        }
        target.append(']');
    }

    static long rowExecutionScratch(BoundRowPlan bound) {
        long result = 0L;
        if (bound.logical.hasStatefulStage()) {
            result = RowExecutionSupport.arrayBytes(
                    bound.root.size, 96L, bound.provenance);
        }
        long literals = bound.logical.inLiteralCount();
        if (literals != 0L) {
            result = addScratch(
                    result,
                    RowExecutionSupport.arrayBytes(
                            literals, 256L, bound.provenance),
                    bound.provenance);
        }
        if (ParallelRowScheduler.requiresMembershipBuffer(bound)) {
            result = addScratch(
                    result,
                    RowExecutionSupport.arrayBytes(
                            bound.root.size, 24L, bound.provenance),
                    bound.provenance);
        }
        return result;
    }

    static long addScratch(long left, long right, Object provenance) {
        return CheckedLong.add(
                left, right, SomaOperation.QUERY, provenance);
    }

    private static long materializationScratch(
            BoundRowPlan bound,
            long elements,
            long containerBytesPerElement) {
        return materializationScratch(
                bound,
                elements,
                bound.logical.owner().layout().detachedRowEstimateBytes(),
                containerBytesPerElement);
    }

    private static long materializationScratch(
            BoundRowPlan bound,
            long elements,
            long detachedElementEstimateBytes,
            long containerBytesPerElement) {
        long bytesPerElement = addScratch(
                detachedElementEstimateBytes,
                containerBytesPerElement,
                bound.provenance);
        return RowExecutionSupport.arrayBytes(
                elements,
                bytesPerElement,
                bound.provenance);
    }

    static <T> T execute(
            LogicalRowPlan logical,
            BoundWork<T> work) {
        if (logical.sourceKind() == LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return logical.relation().executeLeft(logical, work);
        }
        GeneratedTable table = logical.owner();
        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            BoundRowPlan bound = new BoundRowPlan(
                    logical, table.currentRoot(),
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    operation.provenance());
            long scratch = work.scratchBytes(bound);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         table.leaseQueryTemporary(scratch, bound.provenance)) {
                beginCursors(table, bound.root, bound.operation, bound.provenance);
                try {
                    return work.run(bound);
                } finally {
                    endCursors(table);
                }
            }
        }
    }

    private static void beginCursors(
            GeneratedTable table,
            TableStateRoot root,
            io.github.somaruntime.soma.SomaOperation operation,
            Object provenance) {
        table.queryCursor().begin(root, operation, provenance);
        try {
            table.secondaryQueryCursor().begin(root, operation, provenance);
        } catch (RuntimeException failure) {
            table.queryCursor().end();
            throw failure;
        }
    }

    private static void endCursors(GeneratedTable table) {
        table.secondaryQueryCursor().end();
        table.queryCursor().end();
    }

    interface BoundWork<T> {
        long scratchBytes(BoundRowPlan bound);
        T run(BoundRowPlan bound);
    }
}
