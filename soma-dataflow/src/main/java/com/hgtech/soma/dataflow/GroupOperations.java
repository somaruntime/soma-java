package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Arrays;

final class GroupPrepared<B extends DataFlowBinding> {
    final CandidateSelection selected;
    final DataFlowBinding binding;
    final int groupCount;
    final int[] representatives;
    final int[] offsets;
    final int[] members;
    final int[] groupByPosition;

    GroupPrepared(
            CandidateSelection selected,
            DataFlowBinding binding,
            int groupCount,
            int[] representatives,
            int[] offsets,
            int[] members,
            int[] groupByPosition) {
        this.selected = selected;
        this.binding = binding;
        this.groupCount = groupCount;
        this.representatives = representatives;
        this.offsets = offsets;
        this.members = members;
        this.groupByPosition = groupByPosition;
    }
}

abstract class GroupOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    final CandidateProgram<B> program;
    final KeyExpression<B> key;

    GroupOperation(CandidateProgram<B> program, KeyExpression<B> key) {
        super(program.source());
        this.program = program;
        this.key = key;
    }

    @Override
    public final String canonicalForm() {
        return program.canonical() + "->groupBy(" + key.identity()
                + ")->" + terminal();
    }

    @Override
    public final String logicalShape() {
        return "Candidate -> Grouped";
    }

    @Override
    public final String logicalPlan() {
        return program.canonical() + " -> GroupBy(first-key-order) -> "
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
            int candidate = selected.indexes[position];
            long hash = key.hash(binding, candidate);
            int bucket = ((int) (hash ^ (hash >>> 32))) & mask;
            int group = buckets[bucket];
            while (group >= 0 && !key.equal(
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
            members[write[group]++] = selected.indexes[position];
        }
        return new GroupPrepared<B>(
                selected,
                binding,
                groups,
                representatives,
                offsets,
                members,
                groupByPosition);
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
            CandidateProgram<B> program, KeyExpression<B> key) {
        super(program, key);
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
                + (long) prepared.selected.size * 4L;
        frame.reserveOutput(
                prepared.selected.size, bytes, "dataflow.groupBy.indexSnapshot");
        int[] representatives = Arrays.copyOf(
                prepared.representatives, prepared.groupCount);
        int[] offsets = Arrays.copyOf(
                prepared.offsets, prepared.groupCount + 1);
        int[] members = Arrays.copyOf(
                prepared.members, prepared.selected.size);
        GroupIndexResult result = new GroupIndexResult(
                source.alias(),
                prepared.binding.structuralEpoch(),
                representatives,
                offsets,
                members);
        return new ExecutionOutcome<GroupIndexResult>(
                result,
                prepared.selected.scanned,
                prepared.selected.size,
                prepared.selected.size,
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

final class GroupBorrowOperation<B extends DataFlowBinding>
        extends GroupOperation<B, LongScalarResult> {
    private final GroupConsumer consumer;

    GroupBorrowOperation(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            GroupConsumer consumer) {
        super(program, key);
        this.consumer = consumer;
    }

    @Override
    String terminal() {
        return "borrow";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        GroupPrepared<B> prepared = prepare(frame);
        ActiveGroupCursor cursor = new ActiveGroupCursor();
        for (int group = 0; group < prepared.groupCount; group++) {
            cursor.open(
                    group,
                    prepared.representatives,
                    prepared.offsets,
                    prepared.members);
            try {
                consumer.accept(cursor);
            } catch (SomaRuntimeException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw DataFlowFailures.callback(
                        "dataflow_group_consumer_failed",
                        source.alias(),
                        "dataflow.group.borrow",
                        failure);
            } finally {
                cursor.close();
            }
        }
        frame.reserveOutput(1L, 8L, "dataflow.group.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(prepared.groupCount),
                prepared.selected.scanned,
                prepared.selected.size,
                1L,
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
            LongExpression<B> expression,
            int kind) {
        super(program, key);
        this.expression = expression;
        this.kind = kind;
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> counts(
            CandidateProgram<B> program, KeyExpression<B> key) {
        return new GroupLongAggregationOperation<B>(
                program, key, null, COUNT);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> sum(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, expression, SUM);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> min(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, expression, MIN);
    }

    static <B extends DataFlowBinding> GroupLongAggregationOperation<B> max(
            CandidateProgram<B> program,
            KeyExpression<B> key,
            LongExpression<B> expression) {
        return new GroupLongAggregationOperation<B>(
                program, key, expression, MAX);
    }

    @Override
    String terminal() {
        return "long-aggregate(" + kind + ")";
    }

    @Override
    public ExecutionOutcome<GroupedLongResult> execute(ExecutionFrame frame) {
        GroupPrepared<B> prepared = prepare(frame);
        frame.reserveOutput(
                prepared.groupCount,
                (long) prepared.groupCount * 12L,
                "dataflow.groupBy.aggregate");
        int[] representatives = Arrays.copyOf(
                prepared.representatives, prepared.groupCount);
        long[] values = new long[prepared.groupCount];
        if (kind == COUNT) {
            for (int group = 0; group < prepared.groupCount; group++) {
                values[group] = prepared.offsets[group + 1]
                        - prepared.offsets[group];
            }
        } else {
            for (int group = 0; group < prepared.groupCount; group++) {
                int start = prepared.offsets[group];
                int end = prepared.offsets[group + 1];
                long aggregate = expression.evaluate(
                        prepared.binding, prepared.members[start]);
                if (kind == SUM) {
                    aggregate = 0L;
                }
                for (int position = start; position < end; position++) {
                    long value = expression.evaluate(
                            prepared.binding, prepared.members[position]);
                    if (kind == SUM) {
                        aggregate += value;
                    } else if (kind == MIN && value < aggregate) {
                        aggregate = value;
                    } else if (kind == MAX && value > aggregate) {
                        aggregate = value;
                    }
                }
                values[group] = aggregate;
            }
        }
        GroupedLongResult result = new GroupedLongResult(
                source.alias(),
                prepared.binding.structuralEpoch(),
                representatives,
                values);
        return new ExecutionOutcome<GroupedLongResult>(
                result,
                prepared.selected.scanned,
                prepared.selected.size,
                prepared.groupCount,
                1,
                1);
    }
}
