package io.github.somaruntime.soma.internal;

import java.util.List;

/** Specialized optimized Row/Field kernels over one admitted PhysicalPlan. */
final class CanonicalRowExecution {

    interface LocatorVisitor {
        boolean visit(int locator);
    }

    private CanonicalRowExecution() {
    }

    static long count(CanonicalRowExecutionFrame frame) {
        if (frame.plan.pipeline.hasBreaker(
                CanonicalPhysicalSegment.Shape.ROW_LOCATOR)) {
            return locators(frame).size();
        }
        final long[] result = new long[1];
        visitStreaming(frame, new LocatorVisitor() {
            @Override public boolean visit(int locator) {
                BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                result[0] = CheckedLong.increment(
                        result[0], bound.operation, bound.provenance);
                return true;
            }
        });
        return result[0];
    }

    static void visit(
            CanonicalRowExecutionFrame frame,
            LocatorVisitor visitor) {
        if (!frame.plan.pipeline.hasBreaker(
                CanonicalPhysicalSegment.Shape.ROW_LOCATOR)) {
            visitStreaming(frame, visitor);
            return;
        }
        IntLocatorBuffer values = locators(frame);
        for (int index = 0; index < values.size(); index++) {
            if (!visitor.visit(values.get(index))) return;
        }
    }

    static IntLocatorBuffer locators(CanonicalRowExecutionFrame frame) {
        List<CanonicalRowStage> stages = frame.plan.normalized.stages;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        CanonicalPhysicalPipeline pipeline = frame.plan.pipeline;
        int breakerIndex = pipeline.firstBreaker(
                CanonicalPhysicalSegment.Shape.ROW_LOCATOR, 0);
        if (breakerIndex < 0) {
            CanonicalPhysicalSegment segment = pipeline.firstSegment(
                    CanonicalPhysicalSegment.Shape.ROW_LOCATOR);
            IntLocatorBuffer result = frame.allocateRowBreakerState(
                    sourceUpperBound(frame), bound.operation, bound.provenance);
            collectSourceSegment(
                    frame, stages,
                    segment.fromStage, segment.toStageExclusive,
                    result);
            return result;
        }
        CanonicalPhysicalBreaker firstBreaker =
                pipeline.breakers[breakerIndex];
        CanonicalPhysicalSegment firstSegment =
                pipeline.segments[firstBreaker.inputSegmentOrdinal];
        IntLocatorBuffer result;
        if (firstBreaker.kind == CanonicalPhysicalBreaker.Kind.BOUNDED_TOP) {
            result = collectBoundedTypedTop(
                    frame,
                    firstSegment.fromStage,
                    firstSegment.toStageExclusive,
                    stages.get(firstBreaker.stageIndex),
                    stages.get(
                            firstBreaker.consumedToStageExclusive - 1).count);
            frame.rowBreakerState = result;
        } else {
            result = frame.allocateRowBreakerState(
                    sourceUpperBound(frame), bound.operation, bound.provenance);
            collectSourceSegment(
                    frame, stages,
                    firstSegment.fromStage,
                    firstSegment.toStageExclusive,
                    result);
        }
        while (breakerIndex >= 0) {
            CanonicalPhysicalBreaker breaker = pipeline.breakers[breakerIndex];
            CanonicalRowStage stage = stages.get(breaker.stageIndex);
            if (breaker != firstBreaker
                    || breaker.kind != CanonicalPhysicalBreaker.Kind.BOUNDED_TOP) {
                switch (breaker.kind) {
                case STABLE_REORDER:
                    stableSort(bound, result, stage);
                    break;
                case MEMBERSHIP:
                    compactDistinct(bound, result, stage.fieldIndex);
                    break;
                case BOUNDED_TOP:
                default:
                    throw new AssertionError("unexpected downstream Row breaker");
                }
            }
            CanonicalPhysicalSegment output =
                    pipeline.segments[breaker.outputSegmentOrdinal];
            compactSegment(frame, result, stages,
                    output.fromStage, output.toStageExclusive);
            breakerIndex = pipeline.firstBreaker(
                    CanonicalPhysicalSegment.Shape.ROW_LOCATOR,
                    breakerIndex + 1);
        }
        return result;
    }

    static boolean usesBoundedTypedTop(CanonicalRowPhysicalPlan plan) {
        for (CanonicalPhysicalBreaker breaker : plan.pipeline.breakers) {
            if (breaker.shape == CanonicalPhysicalSegment.Shape.ROW_LOCATOR
                    && breaker.kind
                            == CanonicalPhysicalBreaker.Kind.BOUNDED_TOP) {
                return true;
            }
        }
        return false;
    }

    private static IntLocatorBuffer collectBoundedTypedTop(
            final CanonicalRowExecutionFrame frame,
            final int from,
            final int to,
            final CanonicalRowStage order,
            long count) {
        final List<CanonicalRowStage> stages = frame.plan.normalized.stages;
        final long[] counters = new long[to - from];
        final CanonicalStableTopLocatorHeap heap =
                new CanonicalStableTopLocatorHeap(
                        frame.plan.normalized.bound, order, count);
        final long[] nextOrdinal = new long[1];
        visitSource(frame, new LocatorVisitor() {
            @Override public boolean visit(int locator) {
                if (segmentLimitReached(stages, from, to, counters)) return false;
                int decision = evaluateStateless(
                        frame, locator, stages, from, to, counters);
                if (decision > 0 && heap.hasCapacity()) {
                    heap.offer(locator, nextOrdinal[0]);
                    BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                    nextOrdinal[0] = CheckedLong.increment(
                            nextOrdinal[0], bound.operation, bound.provenance);
                }
                return decision >= 0
                        && !segmentLimitReached(stages, from, to, counters);
            }
        });
        return heap.finish();
    }

    private static int sourceUpperBound(CanonicalRowExecutionFrame frame) {
        if (frame.sourceOverride != null) return frame.sourceOverride.size();
        if (frame.parallelSource != null) return frame.parallelSource.size();
        return frame.plan.normalized.bound.root.size;
    }

    private static void collectSourceSegment(
            final CanonicalRowExecutionFrame frame,
            final List<CanonicalRowStage> stages,
            final int from,
            final int to,
            final IntLocatorBuffer output) {
        final long[] counters = new long[to - from];
        visitSource(frame, new LocatorVisitor() {
            @Override public boolean visit(int locator) {
                if (segmentLimitReached(stages, from, to, counters)) return false;
                int decision = evaluateStateless(
                        frame, locator, stages, from, to, counters);
                if (decision > 0) output.add(locator);
                return decision >= 0
                        && !segmentLimitReached(stages, from, to, counters);
            }
        });
    }

    private static void compactSegment(
            CanonicalRowExecutionFrame frame,
            IntLocatorBuffer values,
            List<CanonicalRowStage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (segmentLimitReached(stages, from, to, counters)) break;
            int locator = values.get(input);
            int decision = evaluateStateless(
                    frame, locator, stages, from, to, counters);
            if (decision > 0) values.set(output++, locator);
            if (decision < 0) break;
        }
        values.size(output);
    }

    /** -1 stops the segment, 0 rejects the element, 1 selects it. */
    private static int evaluateStateless(
            CanonicalRowExecutionFrame frame,
            int locator,
            List<CanonicalRowStage> stages,
            int from,
            int to,
            long[] counters) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        for (int position = from; position < to; position++) {
            CanonicalRowStage stage = stages.get(position);
            int counter = position - from;
            if (frame.parallelSource != null
                    && position < frame.plan.parallelPrefixStages) {
                continue;
            }
            switch (stage.kind) {
                case TYPED_FILTER:
                    if (!OptimizedPredicateEvaluator.matches(
                            bound.layout,
                            stage.predicate,
                            bound.root,
                            locator,
                            frame.membership)) return 0;
                    break;
                case CALLBACK_FILTER:
                    if (!RowExecutionSupport.callbackTest(
                            bound, locator, stage.callback)) return 0;
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
                    throw new AssertionError("stateful canonical stage in stateless segment");
            }
        }
        return 1;
    }

    private static void visitStreaming(
            final CanonicalRowExecutionFrame frame,
            final LocatorVisitor visitor) {
        final List<CanonicalRowStage> stages = frame.plan.normalized.stages;
        final long[] counters = new long[stages.size()];
        visitSource(frame, new LocatorVisitor() {
            @Override public boolean visit(int locator) {
                if (segmentLimitReached(stages, 0, stages.size(), counters)) {
                    return false;
                }
                int decision = evaluateStateless(
                        frame, locator, stages, 0, stages.size(), counters);
                return decision == 0 || decision > 0 && visitor.visit(locator);
            }
        });
    }

    private static void visitSource(
            CanonicalRowExecutionFrame frame,
            LocatorVisitor visitor) {
        if (frame.sourceOverride != null) {
            for (int index = 0; index < frame.sourceOverride.size(); index++) {
                if (!visitor.visit(frame.sourceOverride.get(index))) return;
            }
            return;
        }
        if (frame.parallelSource != null) {
            for (int index = 0; index < frame.parallelSource.size(); index++) {
                if (!visitor.visit(frame.parallelSource.get(index))) return;
            }
            return;
        }
        CanonicalRowPhysicalPlan plan = frame.plan;
        BoundCanonicalRowOperation bound = plan.normalized.bound;
        switch (plan.accessPath) {
            case TABLE_SCAN:
                for (int locator = 0; locator < bound.root.size; locator++) {
                    if (!visitor.visit(locator)) return;
                }
                return;
            case KEY_LOOKUP:
                int keyLocator = bound.root.key == null
                        ? -1
                        : bound.root.key.findUnique(
                                bound.root.directory, plan.literal);
                if (keyLocator >= 0) visitor.visit(keyLocator);
                return;
            case INDEX_SELECTION:
            case INDEX_LOOKUP:
                IdentityHashIndex index = bound.root.indexes[plan.indexOrdinal];
                for (int locator = index.first(
                                bound.root.directory,
                                plan.literal,
                                frame.indexCursor);
                     locator >= 0;
                     locator = index.next(frame.indexCursor)) {
                    if (!visitor.visit(locator)) return;
                }
                return;
            case RELATION_LEFT:
                throw new AssertionError(
                        "relation-left PhysicalPlan is missing admitted source");
            default:
                throw new AssertionError("unknown Canonical access path");
        }
    }

    private static boolean segmentLimitReached(
            List<CanonicalRowStage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            CanonicalRowStage stage = stages.get(position);
            if (stage.kind == CanonicalRowStage.Kind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static void compactDistinct(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer values,
            int fieldIndex) {
        CanonicalFieldLocatorSet seen = new CanonicalFieldLocatorSet(
                values.size(),
                bound.layout,
                bound.root.directory,
                fieldIndex,
                bound.operation,
                bound.provenance);
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            int candidate = values.get(input);
            if (seen.add(candidate)) values.set(output++, candidate);
        }
        values.size(output);
    }

    private static void stableSort(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer values,
            CanonicalRowStage stage) {
        if (stage.kind == CanonicalRowStage.Kind.CALLBACK_ORDER) {
            RowExecutionSupport.stableCallbackSort(bound, values, stage);
            return;
        }
        if (values.size() < 2) return;
        int[] scratch = new int[values.size()];
        stableMergeSort(
                bound, values.backing(), scratch, 0, values.size(), stage);
    }

    private static void stableMergeSort(
            BoundCanonicalRowOperation bound,
            int[] values,
            int[] scratch,
            int from,
            int to,
            CanonicalRowStage stage) {
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

    private static final class CanonicalFieldLocatorSet {
        private final GeneratedTableLayout layout;
        private final TableChunkDirectory directory;
        private final int fieldIndex;
        private final int[] locators;
        private final byte[] occupied;
        private final int mask;

        CanonicalFieldLocatorSet(
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
            this.locators = new int[capacity];
            this.occupied = new byte[capacity];
            this.mask = capacity - 1;
        }

        boolean add(int locator) {
            long hash = layout.hashField(directory, locator, fieldIndex);
            int slot = ((int) (hash ^ hash >>> 32)) & mask;
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

    private static final class CanonicalStableTopLocatorHeap {
        private final BoundCanonicalRowOperation bound;
        private final CanonicalRowStage order;
        private final IntLocatorBuffer values;
        private final long[] ordinals;

        CanonicalStableTopLocatorHeap(
                BoundCanonicalRowOperation bound,
                CanonicalRowStage order,
                long capacity) {
            this.bound = bound;
            this.order = order;
            this.values = new IntLocatorBuffer(
                    capacity, bound.operation, bound.provenance);
            this.ordinals = new long[values.backing().length];
        }

        boolean hasCapacity() { return ordinals.length != 0; }

        void offer(int locator, long ordinal) {
            if (values.size() < ordinals.length) {
                int position = values.size();
                values.add(locator);
                ordinals[position] = ordinal;
                siftUp(position);
                return;
            }
            if (compare(locator, ordinal, values.get(0), ordinals[0]) >= 0) return;
            values.set(0, locator);
            ordinals[0] = ordinal;
            siftDown(0, values.size());
        }

        IntLocatorBuffer finish() {
            for (int end = values.size() - 1; end > 0; end--) {
                swap(0, end);
                siftDown(0, end);
            }
            return values;
        }

        private void siftUp(int position) {
            while (position > 0) {
                int parent = (position - 1) >>> 1;
                if (compare(
                        values.get(parent), ordinals[parent],
                        values.get(position), ordinals[position]) >= 0) return;
                swap(parent, position);
                position = parent;
            }
        }

        private void siftDown(int position, int size) {
            while (true) {
                int left = position * 2 + 1;
                if (left >= size) return;
                int worst = left;
                int right = left + 1;
                if (right < size && compare(
                        values.get(left), ordinals[left],
                        values.get(right), ordinals[right]) < 0) worst = right;
                if (compare(
                        values.get(position), ordinals[position],
                        values.get(worst), ordinals[worst]) >= 0) return;
                swap(position, worst);
                position = worst;
            }
        }

        private int compare(
                int leftLocator,
                long leftOrdinal,
                int rightLocator,
                long rightOrdinal) {
            int compared = RowExecutionSupport.compare(
                    bound, leftLocator, rightLocator, order);
            return compared != 0
                    ? compared
                    : Long.compare(leftOrdinal, rightOrdinal);
        }

        private void swap(int left, int right) {
            int locator = values.get(left);
            values.set(left, values.get(right));
            values.set(right, locator);
            long ordinal = ordinals[left];
            ordinals[left] = ordinals[right];
            ordinals[right] = ordinal;
        }
    }
}

/** Lease-owned actual execution state. */
final class CanonicalRowExecutionFrame {
    final CanonicalRowPhysicalPlan plan;
    final IdentityHashIndex.Cursor indexCursor;
    final PredicateMembership membership;
    IntLocatorBuffer sourceOverride;
    IntLocatorBuffer parallelSource;
    IntLocatorBuffer rowBreakerState;
    MappedValueBuffer mappedBreakerState;
    LongValueBuffer primitiveBreakerState;
    CanonicalGroupingQueryOperation.GroupState groupBreakerState;

    CanonicalRowExecutionFrame(CanonicalRowPhysicalPlan plan) {
        if (plan == null) throw new AssertionError("physical plan is missing");
        this.plan = plan;
        this.indexCursor = plan.accessPath == CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || plan.accessPath == CanonicalRowPhysicalPlan.AccessPath.RELATION_LEFT
                || plan.accessPath == CanonicalRowPhysicalPlan.AccessPath.KEY_LOOKUP
                ? null
                : new IdentityHashIndex.Cursor();
        this.membership = plan.normalized.bound.canonical.inLiteralCount() == 0L
                ? null
                : PredicateMembership.preparePredicates(
                        plan.normalized.bound.layout,
                        plan.normalized.filters,
                        plan.normalized.bound.operation,
                        plan.normalized.bound.provenance);
    }

    IntLocatorBuffer allocateRowBreakerState(
            long upperBound,
            io.github.somaruntime.soma.SomaOperation operation,
            Object provenance) {
        if (rowBreakerState != null) {
            throw new AssertionError("Row breaker state already exists");
        }
        rowBreakerState = new IntLocatorBuffer(
                upperBound, operation, provenance);
        return rowBreakerState;
    }

    MappedValueBuffer allocateMappedBreakerState(
            long upperBound,
            Object provenance) {
        if (mappedBreakerState != null) {
            throw new AssertionError("Mapped breaker state already exists");
        }
        mappedBreakerState = new MappedValueBuffer(upperBound, provenance);
        return mappedBreakerState;
    }

    LongValueBuffer allocatePrimitiveBreakerState(
            long upperBound,
            Object provenance) {
        if (primitiveBreakerState != null) {
            throw new AssertionError("Primitive breaker state already exists");
        }
        primitiveBreakerState = new LongValueBuffer(
                upperBound, provenance);
        return primitiveBreakerState;
    }
}

/** Independent correctness-first interpreter over original Bound Canonical stages. */
final class ReferenceCanonicalRowInterpreter {

    private ReferenceCanonicalRowInterpreter() {
    }

    static long count(BoundCanonicalRowOperation bound) {
        return count(bound, null);
    }

    static long count(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer sourceOverride) {
        if (bound.canonical.hasStatefulStage()) {
            return locators(bound, sourceOverride).size();
        }
        final long[] result = new long[1];
        visitStreaming(bound, sourceOverride,
                new CanonicalRowExecution.LocatorVisitor() {
            @Override public boolean visit(int locator) {
                result[0] = CheckedLong.increment(
                        result[0], bound.operation, bound.provenance);
                return true;
            }
        });
        return result[0];
    }

    static void visit(
            BoundCanonicalRowOperation bound,
            CanonicalRowExecution.LocatorVisitor visitor) {
        visit(bound, null, visitor);
    }

    static void visit(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer sourceOverride,
            CanonicalRowExecution.LocatorVisitor visitor) {
        if (!bound.canonical.hasStatefulStage()) {
            visitStreaming(bound, sourceOverride, visitor);
            return;
        }
        IntLocatorBuffer values = locators(bound, sourceOverride);
        for (int index = 0; index < values.size(); index++) {
            if (!visitor.visit(values.get(index))) return;
        }
    }

    static IntLocatorBuffer locators(BoundCanonicalRowOperation bound) {
        return locators(bound, null);
    }

    static IntLocatorBuffer locators(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer sourceOverride) {
        IntLocatorBuffer result = new IntLocatorBuffer(
                sourceOverride == null ? bound.root.size : sourceOverride.size(),
                bound.operation,
                bound.provenance);
        List<CanonicalRowStage> stages = bound.canonical.stages;
        int firstStateful = nextStateful(stages, 0);
        collectSourceSegment(
                bound, sourceOverride, stages, 0, firstStateful, result);
        int position = firstStateful;
        while (position < stages.size()) {
            CanonicalRowStage stage = stages.get(position);
            if (stage.kind == CanonicalRowStage.Kind.DISTINCT_FIELD) {
                distinct(bound, result, stage.fieldIndex);
            } else if (stage.kind == CanonicalRowStage.Kind.TYPED_ORDER
                    || stage.kind == CanonicalRowStage.Kind.CALLBACK_ORDER) {
                insertionSort(bound, result, stage);
            } else {
                throw new AssertionError("expected stateful reference stage");
            }
            int next = nextStateful(stages, position + 1);
            compactSegment(bound, result, stages, position + 1, next);
            position = next;
        }
        return result;
    }

    private static void visitStreaming(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer sourceOverride,
            CanonicalRowExecution.LocatorVisitor visitor) {
        List<CanonicalRowStage> stages = bound.canonical.stages;
        long[] counters = new long[stages.size()];
        int sourceSize = sourceOverride == null
                ? bound.root.size : sourceOverride.size();
        for (int sourceIndex = 0; sourceIndex < sourceSize; sourceIndex++) {
            int locator = sourceOverride == null
                    ? sourceIndex : sourceOverride.get(sourceIndex);
            if (limitReached(stages, counters)) return;
            if (sourceOverride == null && !sourceMatches(bound, locator)) continue;
            int decision = evaluate(
                    bound, locator, stages, 0, stages.size(), counters);
            if (decision < 0) return;
            if (decision > 0 && !visitor.visit(locator)) return;
        }
    }

    private static void collectSourceSegment(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer sourceOverride,
            List<CanonicalRowStage> stages,
            int from,
            int to,
            IntLocatorBuffer output) {
        long[] counters = new long[to - from];
        int sourceSize = sourceOverride == null
                ? bound.root.size : sourceOverride.size();
        for (int sourceIndex = 0; sourceIndex < sourceSize; sourceIndex++) {
            int locator = sourceOverride == null
                    ? sourceIndex : sourceOverride.get(sourceIndex);
            if (segmentLimitReached(stages, from, to, counters)) return;
            if (sourceOverride == null && !sourceMatches(bound, locator)) continue;
            int decision = evaluate(bound, locator, stages, from, to, counters);
            if (decision > 0) output.add(locator);
            if (decision < 0) return;
        }
    }

    private static void compactSegment(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer values,
            List<CanonicalRowStage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (segmentLimitReached(stages, from, to, counters)) break;
            int decision = evaluate(
                    bound, values.get(input), stages, from, to, counters);
            if (decision > 0) values.set(output++, values.get(input));
            if (decision < 0) break;
        }
        values.size(output);
    }

    private static int evaluate(
            BoundCanonicalRowOperation bound,
            int locator,
            List<CanonicalRowStage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            CanonicalRowStage stage = stages.get(position);
            int counter = position - from;
            switch (stage.kind) {
                case TYPED_FILTER:
                    if (!PredicateEvaluator.matches(
                            bound.layout, stage.predicate, bound.root, locator)) return 0;
                    break;
                case CALLBACK_FILTER:
                    if (!RowExecutionSupport.callbackTest(
                            bound, locator, stage.callback)) return 0;
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

    private static boolean sourceMatches(
            BoundCanonicalRowOperation bound,
            int locator) {
        if (bound.canonical.sourceKind == CanonicalRowOperation.SourceKind.TABLE) {
            return true;
        }
        if (bound.canonical.sourceKind
                == CanonicalRowOperation.SourceKind.RELATION_LEFT) {
            throw new AssertionError(
                    "reference relation-left source override is missing");
        }
        int field = bound.layout.indexFieldIndex(bound.canonical.indexOrdinal);
        return bound.layout.fieldEquals(
                bound.root.directory,
                locator,
                bound.canonical.sourceLiteral,
                field);
    }

    private static void insertionSort(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer values,
            CanonicalRowStage stage) {
        if (stage.kind == CanonicalRowStage.Kind.CALLBACK_ORDER) {
            RowExecutionSupport.stableCallbackSort(bound, values, stage);
            return;
        }
        for (int index = 1; index < values.size(); index++) {
            int candidate = values.get(index);
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

    private static void distinct(
            BoundCanonicalRowOperation bound,
            IntLocatorBuffer values,
            int fieldIndex) {
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            int candidate = values.get(input);
            boolean seen = false;
            for (int previous = 0; previous < output; previous++) {
                if (bound.layout.fieldEquals(
                        bound.root.directory,
                        values.get(previous),
                        candidate,
                        fieldIndex)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) values.set(output++, candidate);
        }
        values.size(output);
    }

    private static int nextStateful(List<CanonicalRowStage> stages, int from) {
        for (int position = from; position < stages.size(); position++) {
            if (stages.get(position).isStateful()) return position;
        }
        return stages.size();
    }

    private static boolean limitReached(
            List<CanonicalRowStage> stages,
            long[] counters) {
        return segmentLimitReached(stages, 0, stages.size(), counters);
    }

    private static boolean segmentLimitReached(
            List<CanonicalRowStage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            CanonicalRowStage stage = stages.get(position);
            if (stage.kind == CanonicalRowStage.Kind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }
}
