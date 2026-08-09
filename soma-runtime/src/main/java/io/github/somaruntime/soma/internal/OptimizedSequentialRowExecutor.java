package io.github.somaruntime.soma.internal;

import java.util.List;

/** Optimized sequential row executor over normalized logical stages. */
final class OptimizedSequentialRowExecutor {

    private OptimizedSequentialRowExecutor() {
    }

    static long count(BoundRowPlan bound) {
        NormalizedRowPlan plan = RowOptimizer.optimize(bound);
        if (hasOrder(plan.stages)) return locators(bound, plan).size();
        final long[] count = new long[1];
        visitStateless(bound, plan, new LocatorVisitor() {
            @Override
            public boolean visit(long locator) {
                count[0]++;
                return true;
            }
        });
        return count[0];
    }

    static LongLocatorBuffer locators(BoundRowPlan bound) {
        return locators(bound, RowOptimizer.optimize(bound));
    }

    static void visit(
            BoundRowPlan bound,
            LocatorVisitor visitor) {
        NormalizedRowPlan plan = RowOptimizer.optimize(bound);
        if (!hasOrder(plan.stages)) {
            visitStateless(bound, plan, visitor);
            return;
        }
        LongLocatorBuffer values = locators(bound, plan);
        for (int index = 0; index < values.size(); index++) {
            if (!visitor.visit(values.get(index))) return;
        }
    }

    private static LongLocatorBuffer locators(
            BoundRowPlan bound,
            NormalizedRowPlan plan) {
        LongLocatorBuffer result = new LongLocatorBuffer(
                bound.root.size, bound.operation, bound.provenance);
        int firstStateful = nextStateful(plan.stages, 0);
        collectSourceSegment(bound, plan, 0, firstStateful, result);
        int position = firstStateful;
        while (position < plan.stages.size()) {
            LogicalRowPlan.Stage stage = plan.stages.get(position);
            switch (stage.kind) {
                case TYPED_ORDER:
                case CALLBACK_ORDER:
                    stableSort(bound, result, stage);
                    break;
                case DISTINCT_FIELD:
                    compactDistinct(bound, result, (int) stage.count);
                    break;
                default:
                    throw new AssertionError("expected stateful row stage");
            }
            int next = nextStateful(plan.stages, position + 1);
            compactSegment(
                    bound, result, plan.stages, position + 1, next,
                    plan.membership);
            position = next;
        }
        return result;
    }

    private static void collectSourceSegment(
            final BoundRowPlan bound,
            NormalizedRowPlan plan,
            final int from,
            final int to,
            final LongLocatorBuffer output) {
        final long[] counters = new long[to - from];
        visitSource(bound, plan, new LocatorVisitor() {
            @Override public boolean visit(long locator) {
                if (segmentLimitReached(plan.stages, from, to, counters)) return false;
                int decision = evaluateStateless(
                        bound, locator, plan.stages, from, to, counters,
                        plan.membership);
                if (decision > 0) output.add(locator);
                return decision >= 0 && !segmentLimitReached(
                        plan.stages, from, to, counters);
            }
        });
    }

    private static void compactSegment(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to,
            PredicateMembership membership) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (segmentLimitReached(stages, from, to, counters)) break;
            long locator = values.get(input);
            int decision = evaluateStateless(
                    bound, locator, stages, from, to, counters, membership);
            if (decision > 0) values.set(output++, locator);
            if (decision < 0) break;
        }
        values.size(output);
    }

    /** -1 stops the segment, 0 rejects the element, 1 selects it. */
    private static int evaluateStateless(
            BoundRowPlan bound,
            long locator,
            List<LogicalRowPlan.Stage> stages,
            int from,
            int to,
            long[] counters,
            PredicateMembership membership) {
        for (int position = from; position < to; position++) {
            LogicalRowPlan.Stage stage = stages.get(position);
            int counter = position - from;
            switch (stage.kind) {
                case TYPED_FILTER:
                    if (!OptimizedPredicateEvaluator.matches(
                            bound.logical.owner().layout(), stage.predicate,
                            bound.root, locator, membership)) return 0;
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
                    throw new AssertionError("stateful stage inside stateless segment");
            }
        }
        return 1;
    }

    private static boolean segmentLimitReached(
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

    private static int nextStateful(
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
            NormalizedRowPlan plan,
            LocatorVisitor visitor) {
        List<LogicalRowPlan.Stage> stages = plan.stages;
        long[] counters = new long[stages.size()];
        visitSource(bound, plan, new LocatorVisitor() {
            @Override
            public boolean visit(long locator) {
                if (limitReached(stages, counters)) return false;
                for (int index = 0; index < stages.size(); index++) {
                    LogicalRowPlan.Stage stage = stages.get(index);
                    switch (stage.kind) {
                        case TYPED_FILTER:
                            if (!OptimizedPredicateEvaluator.matches(
                                    bound.logical.owner().layout(),
                                    stage.predicate,
                                    bound.root,
                                    locator,
                                    plan.membership)) return true;
                            break;
                        case CALLBACK_FILTER:
                            if (!RowExecutionSupport.callbackTest(
                                    bound, locator, stage.callbackPredicate)) return true;
                            break;
                        case FIELD_PROJECT:
                            break;
                        case SKIP:
                            if (counters[index] < stage.count) {
                                counters[index]++;
                                return true;
                            }
                            break;
                        case LIMIT:
                            if (counters[index] >= stage.count) return false;
                            counters[index]++;
                            break;
                        default:
                            throw new AssertionError("stateful stage in streaming plan");
                    }
                }
                return visitor.visit(locator);
            }
        });
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

    private static void visitSource(
            BoundRowPlan bound,
            NormalizedRowPlan plan,
            LocatorVisitor visitor) {
        if (bound.parallelSource != null) {
            for (int index = 0; index < bound.parallelSource.size(); index++) {
                if (!visitor.visit(bound.parallelSource.get(index))) return;
            }
            return;
        }
        switch (plan.sourceKind) {
            case RELATION_LEFT:
                if (bound.relationSource == null) {
                    throw new AssertionError("relation row source is not bound");
                }
                for (int index = 0; index < bound.relationSource.size(); index++) {
                    if (!visitor.visit(bound.relationSource.get(index))) return;
                }
                return;
            case TABLE_SCAN:
                for (long locator = 0L; locator < bound.root.size; locator++) {
                    if (!visitor.visit(locator)) return;
                }
                return;
            case KEY_LOOKUP:
                long keyLocator = bound.root.key.findUnique(bound.root.directory, plan.probe);
                if (keyLocator >= 0L) visitor.visit(keyLocator);
                return;
            case INDEX_SELECTION:
            case INDEX_LOOKUP:
                IdentityHashIndex index = bound.root.indexes[plan.indexOrdinal];
                for (long locator = index.first(bound.root.directory, plan.probe);
                        locator >= 0L;
                        locator = index.next(locator)) {
                    if (!visitor.visit(locator)) return;
                }
                return;
            default:
                throw new AssertionError("unknown physical source");
        }
    }

    private static void compactDistinct(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            int fieldIndex) {
        GeneratedTableLayout layout = bound.logical.owner().layout();
        FieldLocatorSet seen = new FieldLocatorSet(
                values.size(), layout, bound.root.directory,
                fieldIndex, bound.operation, bound.provenance);
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            long candidate = values.get(input);
            if (seen.add(candidate)) values.set(output++, candidate);
        }
        values.size(output);
    }

    /**
     * Open-addressed set whose entries remain canonical row locators. Hash
     * collisions are resolved with the authoritative logical Field equality.
     */
    private static final class FieldLocatorSet {
        private final GeneratedTableLayout layout;
        private final TableChunkDirectory directory;
        private final int fieldIndex;
        private final long[] locators;
        private final byte[] occupied;
        private final int mask;

        FieldLocatorSet(
                int expected,
                GeneratedTableLayout layout,
                TableChunkDirectory directory,
                int fieldIndex,
                io.github.somaruntime.soma.SomaOperation operation,
                Object provenance) {
            int capacity = distinctCapacity(expected, operation, provenance);
            this.layout = layout;
            this.directory = directory;
            this.fieldIndex = fieldIndex;
            this.locators = new long[capacity];
            this.occupied = new byte[capacity];
            this.mask = capacity - 1;
        }

        boolean add(long locator) {
            long hash = layout.hashField(directory, locator, fieldIndex);
            int slot = ((int) (hash ^ (hash >>> 32))) & mask;
            while (occupied[slot] != 0) {
                if (layout.fieldEquals(
                        directory, locators[slot], locator, fieldIndex)) {
                    return false;
                }
                slot = (slot + 1) & mask;
            }
            occupied[slot] = 1;
            locators[slot] = locator;
            return true;
        }
    }

    private static int distinctCapacity(
            int expected,
            io.github.somaruntime.soma.SomaOperation operation,
            Object provenance) {
        if (expected <= 1) return 2;
        if (expected > (1 << 29)) {
            throw SomaFailures.failure(
                    io.github.somaruntime.soma.SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    operation,
                    "distinct hash table exceeds Java array boundary",
                    provenance);
        }
        int capacity = 2;
        int required = expected << 1;
        while (capacity < required) capacity <<= 1;
        return capacity;
    }

    private static void stableSort(
            BoundRowPlan bound,
            LongLocatorBuffer values,
            LogicalRowPlan.Stage stage) {
        if (stage.kind == LogicalRowPlan.StageKind.CALLBACK_ORDER) {
            RowExecutionSupport.stableCallbackSort(bound, values, stage);
            return;
        }
        int size = values.size();
        if (size < 2) return;
        long[] scratch = new long[size];
        stableMergeSort(bound, values.backing(), scratch, 0, size, stage);
    }

    private static void stableMergeSort(
            BoundRowPlan bound,
            long[] values,
            long[] scratch,
            int from,
            int to,
            LogicalRowPlan.Stage stage) {
        int length = to - from;
        if (length < 2) return;
        int middle = from + length / 2;
        stableMergeSort(bound, values, scratch, from, middle, stage);
        stableMergeSort(bound, values, scratch, middle, to, stage);
        int left = from;
        int right = middle;
        int output = from;
        while (left < middle && right < to) {
            if (RowExecutionSupport.compare(
                    bound, values[left], values[right], stage) <= 0) {
                scratch[output++] = values[left++];
            } else {
                scratch[output++] = values[right++];
            }
        }
        while (left < middle) scratch[output++] = values[left++];
        while (right < to) scratch[output++] = values[right++];
        System.arraycopy(scratch, from, values, from, length);
    }

    private static void skip(LongLocatorBuffer values, long count) {
        if (count <= 0L) return;
        if (count >= values.size()) {
            values.size(0);
            return;
        }
        int skipped = (int) count;
        int remaining = values.size() - skipped;
        System.arraycopy(values.backing(), skipped, values.backing(), 0, remaining);
        values.size(remaining);
    }

    private static void limit(LongLocatorBuffer values, long count) {
        if (count < values.size()) values.size((int) count);
    }

    private static boolean hasOrder(List<LogicalRowPlan.Stage> stages) {
        for (LogicalRowPlan.Stage stage : stages) {
            if (stage.kind == LogicalRowPlan.StageKind.TYPED_ORDER
                    || stage.kind == LogicalRowPlan.StageKind.CALLBACK_ORDER
                    || stage.kind == LogicalRowPlan.StageKind.DISTINCT_FIELD) return true;
        }
        return false;
    }

    interface LocatorVisitor {
        boolean visit(long locator);
    }
}
