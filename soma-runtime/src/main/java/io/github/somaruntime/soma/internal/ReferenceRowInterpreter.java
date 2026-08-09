package io.github.somaruntime.soma.internal;

import java.util.List;

/** Correctness-first canonical sequential interpretation of original row Logical IR. */
final class ReferenceRowInterpreter {

    private ReferenceRowInterpreter() {
    }

    static long count(BoundRowPlan bound) {
        if (bound.logical.hasStatefulStage()) return locators(bound).size();
        final long[] result = new long[1];
        visitStateless(bound, new OptimizedSequentialRowExecutor.LocatorVisitor() {
            @Override
            public boolean visit(long locator) {
                result[0]++;
                return true;
            }
        });
        return result[0];
    }

    static void visit(
            BoundRowPlan bound,
            OptimizedSequentialRowExecutor.LocatorVisitor visitor) {
        if (!bound.logical.hasStatefulStage()) {
            visitStateless(bound, visitor);
            return;
        }
        LongLocatorBuffer values = locators(bound);
        for (int index = 0; index < values.size(); index++) {
            if (!visitor.visit(values.get(index))) return;
        }
    }

    static LongLocatorBuffer locators(BoundRowPlan bound) {
        LongLocatorBuffer result = new LongLocatorBuffer(
                bound.root.size, bound.operation, bound.provenance);
        List<LogicalRowPlan.Stage> stages = bound.logical.stages();
        int firstStateful = referenceNextStateful(stages, 0);
        referenceCollectSourceSegment(bound, stages, 0, firstStateful, result);
        int position = firstStateful;
        while (position < stages.size()) {
            LogicalRowPlan.Stage stage = stages.get(position);
            switch (stage.kind) {
                case TYPED_ORDER:
                case CALLBACK_ORDER:
                    insertionSort(bound, result, stage);
                    break;
                case DISTINCT_FIELD:
                    referenceDistinct(bound, result, (int) stage.count);
                    break;
                default:
                    throw new AssertionError("expected stateful reference row stage");
            }
            int next = referenceNextStateful(stages, position + 1);
            referenceCompactSegment(bound, result, stages, position + 1, next);
            position = next;
        }
        return result;
    }

    private static void referenceCollectSourceSegment(
            BoundRowPlan bound,
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to,
            LongLocatorBuffer output) {
        long[] counters = new long[to - from];
        for (long sourceIndex = 0L; sourceIndex < sourceSize(bound); sourceIndex++) {
            long locator = sourceLocator(bound, sourceIndex);
            if (referenceSegmentLimitReached(stages, from, to, counters)) return;
            if (!sourceContains(bound, locator)) continue;
            int decision = referenceEvaluateStateless(
                    bound, locator, stages, from, to, counters);
            if (decision > 0) output.add(locator);
            if (decision < 0) return;
            if (referenceSegmentLimitReached(stages, from, to, counters)) return;
        }
    }

    private static void referenceCompactSegment(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (referenceSegmentLimitReached(stages, from, to, counters)) break;
            long locator = values.get(input);
            int decision = referenceEvaluateStateless(
                    bound, locator, stages, from, to, counters);
            if (decision > 0) values.set(output++, locator);
            if (decision < 0) break;
        }
        values.size(output);
    }

    private static int referenceEvaluateStateless(
            BoundRowPlan bound,
            long locator,
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            LogicalRowPlan.Stage stage = stages.get(position);
            int counter = position - from;
            switch (stage.kind) {
                case TYPED_FILTER:
                    if (!PredicateEvaluator.matches(
                            bound.logical.owner().layout(), stage.predicate,
                            bound.root, locator)) return 0;
                    break;
                case CALLBACK_FILTER:
                    if (!RowExecutionSupport.callbackTest(
                            bound, locator, stage.callbackPredicate)) return 0;
                    break;
                case FIELD_PROJECT:
                    break;
                case SKIP:
                    if (counters[counter] < stage.count) {
                        counters[counter]++;
                        return 0;
                    }
                    break;
                case LIMIT:
                    if (counters[counter] >= stage.count) return -1;
                    counters[counter]++;
                    break;
                default:
                    throw new AssertionError("stateful reference stage inside segment");
            }
        }
        return 1;
    }

    private static boolean referenceSegmentLimitReached(
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            LogicalRowPlan.Stage stage = stages.get(position);
            if (stage.kind == LogicalRowPlan.StageKind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static int referenceNextStateful(
            List<LogicalRowPlan.Stage> stages,
            int from) {
        for (int position = from; position < stages.size(); position++) {
            LogicalRowPlan.StageKind kind = stages.get(position).kind;
            if (kind == LogicalRowPlan.StageKind.TYPED_ORDER
                    || kind == LogicalRowPlan.StageKind.CALLBACK_ORDER
                    || kind == LogicalRowPlan.StageKind.DISTINCT_FIELD) return position;
        }
        return stages.size();
    }

    private static void visitStateless(
            BoundRowPlan bound,
            OptimizedSequentialRowExecutor.LocatorVisitor visitor) {
        List<LogicalRowPlan.Stage> stages = bound.logical.stages();
        long[] counters = new long[stages.size()];
        for (long sourceIndex = 0L; sourceIndex < sourceSize(bound); sourceIndex++) {
            long locator = sourceLocator(bound, sourceIndex);
            if (limitReached(stages, counters)) return;
            if (!sourceContains(bound, locator)) continue;
            boolean selected = true;
            for (int index = 0; index < stages.size(); index++) {
                LogicalRowPlan.Stage stage = stages.get(index);
                switch (stage.kind) {
                    case TYPED_FILTER:
                        selected = PredicateEvaluator.matches(
                                bound.logical.owner().layout(),
                                stage.predicate,
                                bound.root,
                                locator);
                        break;
                    case CALLBACK_FILTER:
                        selected = RowExecutionSupport.callbackTest(
                                bound, locator, stage.callbackPredicate);
                        break;
                    case FIELD_PROJECT:
                        break;
                    case SKIP:
                        if (counters[index] < stage.count) {
                            counters[index]++;
                            selected = false;
                        }
                        break;
                    case LIMIT:
                        if (counters[index] >= stage.count) return;
                        else counters[index]++;
                        break;
                    default:
                        throw new AssertionError("stateful stage in reference streaming plan");
                }
                if (!selected) break;
            }
            if (selected && !visitor.visit(locator)) return;
        }
    }

    private static boolean limitReached(
            List<LogicalRowPlan.Stage> stages,
            long[] counters) {
        for (int index = 0; index < stages.size(); index++) {
            LogicalRowPlan.Stage stage = stages.get(index);
            if (stage.kind == LogicalRowPlan.StageKind.LIMIT
                    && counters[index] >= stage.count) return true;
        }
        return false;
    }

    private static boolean sourceContains(BoundRowPlan bound, long locator) {
        if (bound.logical.sourceKind() == LogicalRowPlan.SourceKind.TABLE_SCAN) return true;
        if (bound.logical.sourceKind() == LogicalRowPlan.SourceKind.RELATION_LEFT) return true;
        int ordinal = bound.logical.indexOrdinal();
        int field = bound.logical.owner().layout().indexFieldIndex(ordinal);
        return bound.logical.owner().layout().fieldEquals(
                bound.root.directory,
                locator,
                bound.logical.indexProbe(),
                field);
    }

    private static long sourceSize(BoundRowPlan bound) {
        if (bound.logical.sourceKind() != LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return bound.root.size;
        }
        if (bound.relationSource == null) {
            throw new AssertionError("relation row source is not bound");
        }
        return bound.relationSource.size();
    }

    private static long sourceLocator(BoundRowPlan bound, long sourceIndex) {
        if (bound.logical.sourceKind() != LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return sourceIndex;
        }
        return bound.relationSource.get((int) sourceIndex);
    }

    private static void compactFilter(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            LogicalRowPlan.Stage stage) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            long locator = values.get(input);
            boolean selected = stage.kind == LogicalRowPlan.StageKind.TYPED_FILTER
                    ? PredicateEvaluator.matches(
                            bound.logical.owner().layout(),
                            stage.predicate,
                            bound.root,
                            locator)
                    : RowExecutionSupport.callbackTest(
                            bound, locator, stage.callbackPredicate);
            if (selected) values.set(output++, locator);
        }
        values.size(output);
    }

    /** Intentionally simple and algorithmically independent stable reference sort. */
    private static void insertionSort(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            LogicalRowPlan.Stage stage) {
        if (stage.kind == LogicalRowPlan.StageKind.CALLBACK_ORDER) {
            RowExecutionSupport.stableCallbackSort(bound, values, stage);
            return;
        }
        for (int index = 1; index < values.size(); index++) {
            long candidate = values.get(index);
            int position = index;
            while (position > 0
                    && RowExecutionSupport.compare(
                    bound, values.get(position - 1), candidate, stage) > 0) {
                values.set(position, values.get(position - 1));
                position--;
            }
            values.set(position, candidate);
        }
    }

    private static void referenceDistinct(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            int fieldIndex) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            long candidate = values.get(input);
            boolean seen = false;
            for (int prior = 0; prior < output; prior++) {
                if (bound.logical.owner().layout().fieldEquals(
                        bound.root.directory,
                        candidate,
                        values.get(prior),
                        fieldIndex)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) values.set(output++, candidate);
        }
        values.size(output);
    }

    private static void referenceSkip(LongLocatorBuffer values, long count) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if ((long) input >= count) values.set(output++, values.get(input));
        }
        values.size(output);
    }

    private static void referenceLimit(LongLocatorBuffer values, long count) {
        if (count < values.size()) values.size((int) count);
    }
}
