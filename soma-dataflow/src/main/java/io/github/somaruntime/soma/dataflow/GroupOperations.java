package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class GroupPrepared<B extends DataFlowBinding> {
    final CandidateSelection selected;
    final DataFlowBinding binding;
    final int groupCount;
    final int[] representatives;
    final int[] offsets;
    final int[] members;
    final int memberCount;

    GroupPrepared(
            CandidateSelection selected,
            DataFlowBinding binding,
            int groupCount,
            int[] representatives,
            int[] offsets,
            int[] members,
            int memberCount) {
        this.selected = selected;
        this.binding = binding;
        this.groupCount = groupCount;
        this.representatives = representatives;
        this.offsets = offsets;
        this.members = members;
        this.memberCount = memberCount;
    }
}

abstract class GroupOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    final CandidateProgram<B> program;
    final KeyExpression<B> key;
    final GroupShapePlan<B> shape;

    GroupOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape) {
        this(
                program,
                key,
                shape,
                Collections.<ParameterSlot<?>>emptyList());
    }

    GroupOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            List<ParameterSlot<?>> additionalParameters) {
        super(
                program,
                DataFlowSupport.unionParameters(
                        key.parameters(), additionalParameters));
        this.program = program;
        this.key = key;
        this.shape = shape;
    }

    @Override
    public final String canonicalForm() {
        return program.canonical() + "->groupBy(" + key.identity()
                + ")->" + shape.canonical() + "->" + terminal();
    }

    @Override
    public final String logicalShape() {
        return "Candidate -> Grouped";
    }

    @Override
    public final String logicalPlan() {
        return program.canonical() + " -> GroupBy(first-key-order) -> "
                + (shape.isIdentity() ? "" : "GroupShape -> ")
                + terminal();
    }

    @Override
    public final String physicalPlan() {
        return "hash-group[first-key-order,stable-members," + terminal() + "]";
    }

    abstract String terminal();

    final GroupPrepared<B> prepare(ExecutionFrame frame) {
        CandidateSelection selected =
                program.select(frame, "dataflow.groupBy");
        DataFlowBinding binding = frame.binding(source);
        int cardinality = selected.size;
        int bucketCapacity = bucketCapacity(cardinality);
        int[] buckets =
                frame.newScratchIndexes(bucketCapacity, "dataflow.groupBy.hash");
        int[] nextGroup =
                frame.newScratchIndexes(cardinality, "dataflow.groupBy.hash");
        int[] representatives =
                frame.newScratchIndexes(cardinality, "dataflow.groupBy");
        int[] sizes =
                frame.newScratchIndexes(cardinality, "dataflow.groupBy");
        int[] groupByPosition =
                frame.newScratchIndexes(cardinality, "dataflow.groupBy");
        Arrays.fill(buckets, -1);
        int mask = bucketCapacity - 1;
        int groups = 0;
        for (int position = 0; position < cardinality; position++) {
            if ((position & 1023) == 0) {
                frame.checkBoundary("dataflow.groupBy");
            }
            int candidate = selected.indexAt(position);
            long hash = key.hash(frame, binding, candidate);
            int bucket = ((int) (hash ^ (hash >>> 32))) & mask;
            int group = buckets[bucket];
            while (group >= 0 && !key.equal(
                    frame,
                    binding,
                    candidate,
                    key,
                    binding,
                    representatives[group])) {
                group = nextGroup[group];
            }
            if (group < 0) {
                group = groups++;
                representatives[group] = candidate;
                nextGroup[group] = buckets[bucket];
                buckets[bucket] = group;
            }
            groupByPosition[position] = group;
            sizes[group]++;
        }
        int[] offsets =
                frame.newScratchIndexes(groups + 1, "dataflow.groupBy");
        for (int group = 0; group < groups; group++) {
            offsets[group + 1] = offsets[group] + sizes[group];
        }
        int[] write =
                frame.newScratchIndexes(groups, "dataflow.groupBy");
        System.arraycopy(offsets, 0, write, 0, groups);
        int[] members =
                frame.newScratchIndexes(cardinality, "dataflow.groupBy");
        for (int position = 0; position < cardinality; position++) {
            int group = groupByPosition[position];
            members[write[group]++] = selected.indexAt(position);
        }
        GroupPrepared<B> prepared = new GroupPrepared<B>(
                selected,
                binding,
                groups,
                representatives,
                offsets,
                members,
                cardinality);
        return shape.apply(frame, prepared);
    }

    private static int bucketCapacity(int size) {
        int target = size >= (1 << 29)
                ? 1 << 30 : Math.max(4, size * 2);
        int capacity = 1;
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
    }
}

final class GroupIndexOperation<B extends DataFlowBinding>
        extends GroupOperation<B, GroupIndexResult> {
    GroupIndexOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape) {
        super(program, key, shape);
    }

    @Override
    String terminal() {
        return "group-index-snapshot";
    }

    @Override
    public ExecutionOutcome<GroupIndexResult> execute(ExecutionFrame frame) {
        GroupPrepared<B> prepared = prepare(frame);
        long bytes = (long) prepared.groupCount * 4L
                + (long) (prepared.groupCount + 1) * 4L
                + (long) prepared.memberCount * 4L;
        frame.reserveOutput(
                prepared.memberCount,
                bytes,
                "dataflow.groupBy.indexSnapshot");
        int[] representatives = Arrays.copyOf(
                prepared.representatives, prepared.groupCount);
        int[] offsets = Arrays.copyOf(
                prepared.offsets, prepared.groupCount + 1);
        int[] members = Arrays.copyOf(
                prepared.members, prepared.memberCount);
        GroupIndexResult result = new GroupIndexResult(
                source.alias(),
                prepared.binding.structuralEpoch(),
                representatives,
                offsets,
                members);
        return new ExecutionOutcome<GroupIndexResult>(
                result,
                prepared.selected.scanned,
                prepared.memberCount,
                prepared.memberCount,
                1,
                1);
    }
}

final class ActiveGroupCursor implements GroupCursor {
    private int ordinal;
    private int[] representatives;
    private int[] offsets;
    private int[] members;
    private boolean active;

    void open(
            int ordinal,
            int[] representatives,
            int[] offsets,
            int[] members) {
        this.ordinal = ordinal;
        this.representatives = representatives;
        this.offsets = offsets;
        this.members = members;
        active = true;
    }

    void close() {
        active = false;
        representatives = null;
        offsets = null;
        members = null;
    }

    @Override
    public int ordinal() {
        requireActive();
        return ordinal;
    }

    @Override
    public int representativeIndex() {
        requireActive();
        return representatives[ordinal];
    }

    @Override
    public int size() {
        requireActive();
        return offsets[ordinal + 1] - offsets[ordinal];
    }

    @Override
    public int indexAt(int position) {
        requireActive();
        int size = size();
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("group position out of range");
        }
        return members[offsets[ordinal] + position];
    }

    private void requireActive() {
        if (!active) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_escaped_group_cursor",
                    "group",
                    "dataflow.group.cursor",
                    "CLOSED");
        }
    }
}

final class GroupDeliveryOperation<B extends DataFlowBinding>
        extends GroupOperation<B, DeliveryResult> {
    private final ParameterSlot<GroupVisitor> visitorSlot;

    GroupDeliveryOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            ParameterSlot<GroupVisitor> visitorSlot) {
        super(
                program,
                key,
                shape,
                Collections.<ParameterSlot<?>>singletonList(visitorSlot));
        this.visitorSlot = visitorSlot;
    }

    @Override
    String terminal() {
        return "deliver(group-cursor)";
    }

    @Override
    public ResultDeliveryMode resultDeliveryMode() {
        return ResultDeliveryMode.CALLBACK_SCOPED;
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        int maximum = program.maximumCardinality(frame.binding(source));
        frame.preflightDelivery(
                maximum,
                (long) maximum * 8L,
                "dataflow.group.deliver");
        GroupPrepared<B> prepared = prepare(frame);
        GroupVisitor visitor = frame.parameter(visitorSlot);
        ActiveGroupCursor cursor = new ActiveGroupCursor();
        int delivered = 0;
        boolean completed = true;
        for (int group = 0; group < prepared.groupCount; group++) {
            cursor.open(
                    group,
                    prepared.representatives,
                    prepared.offsets,
                    prepared.members);
            try {
                delivered++;
                if (!visitor.visit(cursor)) {
                    completed = false;
                    break;
                }
            } catch (SomaRuntimeException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw DataFlowFailures.callback(
                        "dataflow_group_delivery_callback",
                        source.alias(),
                        "dataflow.group.deliver",
                        failure);
            } finally {
                cursor.close();
            }
        }
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(delivered, completed),
                prepared.selected.scanned,
                prepared.memberCount,
                delivered,
                1,
                1);
    }
}

final class GroupLongAggregationOperation<B extends DataFlowBinding>
        extends GroupOperation<B, GroupedLongResult> {
    private static final int COUNT = 0;
    private static final int SUM = 1;
    private static final int MIN = 2;
    private static final int MAX = 3;

    private final LongExpression<B> expression;
    private final int kind;

    private GroupLongAggregationOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            LongExpression<B> expression,
            int kind) {
        super(
                program,
                key,
                shape,
                expression == null
                        ? Collections.<ParameterSlot<?>>emptyList()
                        : expression.parameters);
        this.expression = expression;
        this.kind = kind;
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> counts(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape) {
        return new GroupLongAggregationOperation<B>(
                program, key, shape, null, COUNT);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> sum(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, shape, expression, SUM);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> min(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, shape, expression, MIN);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> max(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupShapePlan<B> shape,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, shape, expression, MAX);
    }

    @Override
    String terminal() {
        return "long-aggregate-v2(" + kind + ")";
    }

    @Override
    public ExecutionOutcome<GroupedLongResult> execute(ExecutionFrame frame) {
        CandidateSelection selected =
                program.select(frame, "dataflow.groupBy.aggregate");
        DataFlowBinding binding = frame.binding(source);
        int cardinality = selected.size;
        int capacity = bucketCapacity(cardinality);
        int[] buckets = frame.newScratchIndexes(
                capacity, "dataflow.groupBy.aggregate.hash");
        int[] next = frame.newScratchIndexes(
                cardinality, "dataflow.groupBy.aggregate.hash");
        int[] representatives = frame.newScratchIndexes(
                cardinality, "dataflow.groupBy.aggregate");
        int[] sizes = frame.newScratchIndexes(
                cardinality, "dataflow.groupBy.aggregate");
        long[] aggregates = frame.newScratchLongs(
                cardinality, "dataflow.groupBy.aggregate");
        long[] aggregateHighs = kind == SUM
                ? frame.newScratchLongs(
                        cardinality, "dataflow.groupBy.aggregate")
                : null;
        Arrays.fill(buckets, -1);
        int mask = capacity - 1;
        int groups = 0;
        for (int position = 0; position < cardinality; position++) {
            if ((position & 1023) == 0) {
                frame.checkBoundary("dataflow.groupBy.aggregate");
            }
            int candidate = selected.indexAt(position);
            long hash = key.hash(frame, binding, candidate);
            int bucket = ((int) (hash ^ (hash >>> 32))) & mask;
            int group = buckets[bucket];
            while (group >= 0 && !key.equal(
                    frame,
                    binding,
                    candidate,
                    key,
                    binding,
                    representatives[group])) {
                group = next[group];
            }
            if (group < 0) {
                group = groups++;
                representatives[group] = candidate;
                next[group] = buckets[bucket];
                buckets[bucket] = group;
            }
            long value = kind == COUNT
                    ? 0L
                    : expression.evaluate(frame, binding, candidate);
            boolean first = sizes[group] == 0;
            sizes[group]++;
            if (kind == COUNT) {
                aggregates[group]++;
            } else if (kind == SUM) {
                IntegralArithmetic.addTo(
                        aggregateHighs, aggregates, group, value);
            } else if (first) {
                aggregates[group] = value;
            } else if (kind == MIN && value < aggregates[group]) {
                aggregates[group] = value;
            } else if (kind == MAX && value > aggregates[group]) {
                aggregates[group] = value;
            }
        }
        GroupOrdinalSelection selection =
                shape.select(frame, sizes, groups);
        frame.reserveOutput(
                selection.size,
                (long) selection.size * 12L,
                "dataflow.groupBy.aggregate");
        int[] outputRepresentatives = new int[selection.size];
        long[] outputValues = new long[selection.size];
        for (int output = 0; output < selection.size; output++) {
            int group = selection.sourceOrdinal(output);
            outputRepresentatives[output] = representatives[group];
            outputValues[output] = kind == SUM
                    ? IntegralArithmetic.longValue(
                            aggregateHighs[group],
                            aggregates[group],
                            expression.path,
                            "dataflow.groupBy.aggregate")
                    : aggregates[group];
        }
        GroupedLongResult result = new GroupedLongResult(
                source.alias(),
                binding.structuralEpoch(),
                outputRepresentatives,
                outputValues);
        return new ExecutionOutcome<GroupedLongResult>(
                result,
                selected.scanned,
                cardinality,
                selection.size,
                1,
                1);
    }

    private static int bucketCapacity(int size) {
        int target = size >= (1 << 29)
                ? 1 << 30 : Math.max(4, size * 2);
        int capacity = 1;
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
    }
}
