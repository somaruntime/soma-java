package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.List;

/** Physical kernels for the admitted contiguous pure Candidate subset. */
final class ParallelCandidateExecution {
    private static final int LONG_SUM = 0;
    private static final int LONG_AVERAGE = 1;
    private static final int LONG_MIN = 2;
    private static final int LONG_MAX = 3;

    private ParallelCandidateExecution() {
    }

    static <B extends DataFlowBinding>
    ExecutionOutcome<LongScalarResult> count(
            final CandidateProgram<B> program,
            final ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(program.source());
        final int cardinality = parallelCardinality(program, binding);
        ParallelPlan plan = ParallelExecution.plan(
                frame,
                cardinality,
                program.supportsContiguousParallel(),
                8L,
                1,
                "dataflow.count");
        if (!plan.parallel()) {
            return null;
        }
        List<Integer> counts = ParallelExecution.run(
                frame,
                plan,
                new ParallelWork<Integer>() {
                    @Override
                    public Integer execute(
                            int partition, int start, int end) {
                        int count = 0;
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary("dataflow.count");
                            }
                            if (program.parallelMatches(binding, index)) {
                                count++;
                            }
                        }
                        return Integer.valueOf(count);
                    }
                },
                "dataflow.count");
        long matched = 0L;
        for (Integer count : counts) {
            matched += count.intValue();
        }
        frame.reserveOutput(1L, 8L, "dataflow.count");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(matched),
                cardinality,
                matched,
                1L,
                plan.tasks(),
                plan.workers());
    }

    static <B extends DataFlowBinding> ParallelCandidateSelection select(
            final CandidateProgram<B> program,
            final ExecutionFrame frame,
            final String operation) {
        final DataFlowBinding binding = frame.binding(program.source());
        final MatchLayout layout = layout(
                program, binding, frame, operation);
        if (layout == null) {
            return null;
        }
        final int[] indexes = frame.newScratchIndexes(
                layout.matched, operation);
        ParallelExecution.run(
                frame,
                layout.plan,
                new ParallelWork<Void>() {
                    @Override
                    public Void execute(
                            int partition, int start, int end) {
                        int write = layout.offsets[partition];
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary(operation);
                            }
                            if (program.parallelMatches(binding, index)) {
                                indexes[write++] = index;
                            }
                        }
                        if (write != layout.offsets[partition + 1]) {
                            throw DataFlowFailures.internal(
                                    "dataflow_parallel_match_instability",
                                    program.source().alias(),
                                    operation,
                                    Integer.toString(partition));
                        }
                        return null;
                    }
                },
                operation);
        return new ParallelCandidateSelection(
                indexes,
                layout.cardinality,
                layout.matched,
                layout.plan.tasks() * 2,
                layout.plan.workers());
    }

    static <B extends DataFlowBinding>
    ExecutionOutcome<LongColumnResult> longColumn(
            final CandidateProgram<B> program,
            final LongExpression<B> expression,
            final ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(program.source());
        MatchLayout layout = layout(
                program, binding, frame, "dataflow.longColumn");
        if (layout == null) {
            return null;
        }
        final long[] values = frame.newOutputLongs(
                layout.matched, "dataflow.longColumn");
        ParallelExecution.run(
                frame,
                layout.plan,
                new ParallelWork<Void>() {
                    @Override
                    public Void execute(
                            int partition, int start, int end) {
                        int write = layout.offsets[partition];
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary("dataflow.longColumn");
                            }
                            if (program.parallelMatches(binding, index)) {
                                values[write++] =
                                        expression.evaluate(binding, index);
                            }
                        }
                        if (write != layout.offsets[partition + 1]) {
                            throw DataFlowFailures.internal(
                                    "dataflow_parallel_match_instability",
                                    program.source().alias(),
                                    "dataflow.longColumn",
                                    Integer.toString(partition));
                        }
                        return null;
                    }
                },
                "dataflow.longColumn");
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(values, layout.matched),
                layout.cardinality,
                layout.matched,
                layout.matched,
                layout.plan.tasks() * 2,
                layout.plan.workers());
    }

    static <B extends DataFlowBinding>
    ExecutionOutcome<DoubleColumnResult> doubleColumn(
            final CandidateProgram<B> program,
            final DoubleExpression<B> expression,
            final ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(program.source());
        MatchLayout layout = layout(
                program, binding, frame, "dataflow.doubleColumn");
        if (layout == null) {
            return null;
        }
        final double[] values = frame.newOutputDoubles(
                layout.matched, "dataflow.doubleColumn");
        ParallelExecution.run(
                frame,
                layout.plan,
                new ParallelWork<Void>() {
                    @Override
                    public Void execute(
                            int partition, int start, int end) {
                        int write = layout.offsets[partition];
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary("dataflow.doubleColumn");
                            }
                            if (program.parallelMatches(binding, index)) {
                                values[write++] =
                                        expression.evaluate(binding, index);
                            }
                        }
                        if (write != layout.offsets[partition + 1]) {
                            throw DataFlowFailures.internal(
                                    "dataflow_parallel_match_instability",
                                    program.source().alias(),
                                    "dataflow.doubleColumn",
                                    Integer.toString(partition));
                        }
                        return null;
                    }
                },
                "dataflow.doubleColumn");
        return new ExecutionOutcome<DoubleColumnResult>(
                new DoubleColumnResult(values, layout.matched),
                layout.cardinality,
                layout.matched,
                layout.matched,
                layout.plan.tasks() * 2,
                layout.plan.workers());
    }

    static <B extends DataFlowBinding>
    ExecutionOutcome<BooleanColumnResult> booleanColumn(
            final CandidateProgram<B> program,
            final BooleanExpression<B> expression,
            final ExecutionFrame frame) {
        if (!expression.parallelSafe) {
            return null;
        }
        final DataFlowBinding binding = frame.binding(program.source());
        MatchLayout layout = layout(
                program, binding, frame, "dataflow.booleanColumn");
        if (layout == null) {
            return null;
        }
        final boolean[] values = frame.newOutputBooleans(
                layout.matched, "dataflow.booleanColumn");
        ParallelExecution.run(
                frame,
                layout.plan,
                new ParallelWork<Void>() {
                    @Override
                    public Void execute(
                            int partition, int start, int end) {
                        int write = layout.offsets[partition];
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary("dataflow.booleanColumn");
                            }
                            if (program.parallelMatches(binding, index)) {
                                values[write++] =
                                        expression.evaluate(binding, index);
                            }
                        }
                        if (write != layout.offsets[partition + 1]) {
                            throw DataFlowFailures.internal(
                                    "dataflow_parallel_match_instability",
                                    program.source().alias(),
                                    "dataflow.booleanColumn",
                                    Integer.toString(partition));
                        }
                        return null;
                    }
                },
                "dataflow.booleanColumn");
        return new ExecutionOutcome<BooleanColumnResult>(
                new BooleanColumnResult(values, layout.matched),
                layout.cardinality,
                layout.matched,
                layout.matched,
                layout.plan.tasks() * 2,
                layout.plan.workers());
    }

    static <B extends DataFlowBinding, R>
    ExecutionOutcome<R> longReduction(
            final CandidateProgram<B> program,
            final LongExpression<B> expression,
            final int kind,
            final ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(program.source());
        final int cardinality = parallelCardinality(program, binding);
        final ParallelPlan plan = ParallelExecution.plan(
                frame,
                cardinality,
                program.supportsContiguousParallel(),
                16L,
                1,
                "dataflow.longReduce");
        if (!plan.parallel()) {
            return null;
        }
        List<LongPartial> partials = ParallelExecution.run(
                frame,
                plan,
                new ParallelWork<LongPartial>() {
                    @Override
                    public LongPartial execute(
                            int partition, int start, int end) {
                        long value = 0L;
                        int count = 0;
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary("dataflow.longReduce");
                            }
                            if (!program.parallelMatches(binding, index)) {
                                continue;
                            }
                            long next = expression.evaluate(binding, index);
                            if (kind == LONG_SUM || kind == LONG_AVERAGE) {
                                value += next;
                            } else if (count == 0
                                    || (kind == LONG_MIN
                                    ? next < value : next > value)) {
                                value = next;
                            }
                            count++;
                        }
                        return new LongPartial(value, count);
                    }
                },
                "dataflow.longReduce");
        long[] values = frame.newScratchLongs(
                plan.tasks(), "dataflow.longReduce");
        int[] counts = frame.newScratchIndexes(
                plan.tasks(), "dataflow.longReduce");
        for (int partition = 0; partition < plan.tasks(); partition++) {
            LongPartial partial = partials.get(partition);
            values[partition] = partial.value;
            counts[partition] = partial.count;
        }
        for (int width = 1; width < plan.tasks(); width *= 2) {
            for (int left = 0; left < plan.tasks(); left += width * 2) {
                int right = left + width;
                if (right >= plan.tasks()) {
                    continue;
                }
                if (kind == LONG_SUM || kind == LONG_AVERAGE) {
                    values[left] += values[right];
                } else if (counts[right] != 0
                        && (counts[left] == 0
                        || (kind == LONG_MIN
                        ? values[right] < values[left]
                        : values[right] > values[left]))) {
                    values[left] = values[right];
                }
                counts[left] += counts[right];
            }
        }
        Object result;
        if (kind == LONG_SUM) {
            result = new LongScalarResult(values[0]);
        } else if (kind == LONG_AVERAGE) {
            result = counts[0] == 0
                    ? OptionalDoubleResult.empty()
                    : OptionalDoubleResult.of(
                            (double) values[0] / (double) counts[0]);
        } else {
            result = counts[0] == 0
                    ? OptionalLongResult.empty()
                    : OptionalLongResult.of(values[0]);
        }
        frame.reserveOutput(1L, 8L, "dataflow.longReduce");
        @SuppressWarnings("unchecked")
        R cast = (R) result;
        return new ExecutionOutcome<R>(
                cast,
                cardinality,
                counts[0],
                1L,
                plan.tasks(),
                plan.workers());
    }

    private static <B extends DataFlowBinding> MatchLayout layout(
            final CandidateProgram<B> program,
            final DataFlowBinding binding,
            final ExecutionFrame frame,
            final String operation) {
        final int cardinality = parallelCardinality(program, binding);
        final ParallelPlan plan = ParallelExecution.plan(
                frame,
                cardinality,
                program.supportsContiguousParallel(),
                8L,
                2,
                operation);
        if (!plan.parallel()) {
            return null;
        }
        List<Integer> counts = ParallelExecution.run(
                frame,
                plan,
                new ParallelWork<Integer>() {
                    @Override
                    public Integer execute(
                            int partition, int start, int end) {
                        int count = 0;
                        for (int index = start; index < end; index++) {
                            if ((index & 1023) == 0) {
                                frame.checkBoundary(operation);
                            }
                            if (program.parallelMatches(binding, index)) {
                                count++;
                            }
                        }
                        return Integer.valueOf(count);
                    }
                },
                operation);
        int[] offsets = frame.newScratchIndexes(
                plan.tasks() + 1, operation);
        for (int partition = 0; partition < plan.tasks(); partition++) {
            int count = counts.get(partition).intValue();
            if (offsets[partition] > Integer.MAX_VALUE - count) {
                throw DataFlowFailures.resource(
                        "dataflow_cardinality_overflow",
                        program.source().alias(),
                        operation,
                        Integer.toString(partition));
            }
            offsets[partition + 1] = offsets[partition] + count;
        }
        return new MatchLayout(
                plan, cardinality, offsets[offsets.length - 1], offsets);
    }

    private static int parallelCardinality(
            CandidateProgram<?> program, DataFlowBinding binding) {
        return program.supportsContiguousParallel()
                ? program.contiguousCardinality(binding) : 0;
    }

    private static final class MatchLayout {
        final ParallelPlan plan;
        final int cardinality;
        final int matched;
        final int[] offsets;

        MatchLayout(
                ParallelPlan plan,
                int cardinality,
                int matched,
                int[] offsets) {
            this.plan = plan;
            this.cardinality = cardinality;
            this.matched = matched;
            this.offsets = offsets;
        }
    }

    private static final class LongPartial {
        final long value;
        final int count;

        LongPartial(long value, int count) {
            this.value = value;
            this.count = count;
        }
    }
}

final class ParallelCandidateSelection {
    final int[] indexes;
    final long scanned;
    final int matched;
    final int tasks;
    final int workers;

    ParallelCandidateSelection(
            int[] indexes,
            long scanned,
            int matched,
            int tasks,
            int workers) {
        this.indexes = indexes;
        this.scanned = scanned;
        this.matched = matched;
        this.tasks = tasks;
        this.workers = workers;
    }
}
