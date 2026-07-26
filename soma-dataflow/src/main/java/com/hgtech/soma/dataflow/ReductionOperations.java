package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.List;

final class LongReductionOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    private static final int SUM = 0;
    private static final int AVERAGE = 1;
    private static final int MIN = 2;
    private static final int MAX = 3;

    private final CandidateProgram<B> program;
    private final LongExpression<B> expression;
    private final int kind;

    private LongReductionOperation(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            int kind) {
        super(program, expression.parameters);
        this.program = program;
        this.expression = expression;
        this.kind = kind;
    }

    static <B extends DataFlowBinding>
    LongReductionOperation<B, LongScalarResult> sum(
            CandidateProgram<B> program, LongExpression<B> expression) {
        return new LongReductionOperation<B, LongScalarResult>(
                program, expression, SUM);
    }

    static <B extends DataFlowBinding>
    LongReductionOperation<B, OptionalDoubleResult> average(
            CandidateProgram<B> program, LongExpression<B> expression) {
        return new LongReductionOperation<B, OptionalDoubleResult>(
                program, expression, AVERAGE);
    }

    static <B extends DataFlowBinding>
    LongReductionOperation<B, OptionalLongResult> min(
            CandidateProgram<B> program, LongExpression<B> expression) {
        return new LongReductionOperation<B, OptionalLongResult>(
                program, expression, MIN);
    }

    static <B extends DataFlowBinding>
    LongReductionOperation<B, OptionalLongResult> max(
            CandidateProgram<B> program, LongExpression<B> expression) {
        return new LongReductionOperation<B, OptionalLongResult>(
                program, expression, MAX);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->long-reduce(" + kind + ","
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Projected<long> -> Scalar";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> LongReduce(" + kind + ")";
    }

    @Override
    public String physicalPlan() {
        if (program.supportsContiguousParallel()) {
            return "candidate-adaptive[contiguous-filter,fixed-tree-long-reduce]";
        }
        return program.requiresBarrier()
                ? "candidate-barrier[stable-sort,left-fold]"
                : "candidate-stream[fused-left-fold]";
    }

    @Override
    public boolean parallelBranchSafe() {
        return program.parallelBranchSafe()
                && expression.parallelSafe;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionOutcome<R> execute(ExecutionFrame frame) {
        ExecutionOutcome<R> parallel =
                ParallelCandidateExecution.longReduction(
                        program, expression, kind, frame);
        if (parallel != null) {
            return parallel;
        }
        final DataFlowBinding binding = frame.binding(source);
        final long[] accumulator = new long[1];
        final int[] count = new int[1];
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        long value = expression.evaluate(
                                frame, binding, index);
                        if (kind == SUM || kind == AVERAGE) {
                            accumulator[0] += value;
                        } else if (count[0] == 0
                                || (kind == MIN
                                ? value < accumulator[0]
                                : value > accumulator[0])) {
                            accumulator[0] = value;
                        }
                        count[0]++;
                        return true;
                    }
                },
                "dataflow.longReduce");
        Object result;
        if (kind == SUM) {
            result = new LongScalarResult(accumulator[0]);
        } else if (kind == AVERAGE) {
            result = count[0] == 0
                    ? OptionalDoubleResult.empty()
                    : OptionalDoubleResult.of(
                    (double) accumulator[0] / (double) count[0]);
        } else {
            result = count[0] == 0
                    ? OptionalLongResult.empty()
                    : OptionalLongResult.of(accumulator[0]);
        }
        frame.reserveOutput(1L, 8L, "dataflow.longReduce");
        return new ExecutionOutcome<R>(
                (R) result,
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class DoubleReductionOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    private static final int SUM = 0;
    private static final int AVERAGE = 1;
    private static final int MIN = 2;
    private static final int MAX = 3;

    private final CandidateProgram<B> program;
    private final DoubleExpression<B> expression;
    private final int kind;

    private DoubleReductionOperation(
            CandidateProgram<B> program,
            DoubleExpression<B> expression,
            int kind) {
        super(program, expression.parameters);
        this.program = program;
        this.expression = expression;
        this.kind = kind;
    }

    static <B extends DataFlowBinding>
    DoubleReductionOperation<B, DoubleScalarResult> sum(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        return new DoubleReductionOperation<B, DoubleScalarResult>(
                program, expression, SUM);
    }

    static <B extends DataFlowBinding>
    DoubleReductionOperation<B, OptionalDoubleResult> average(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        return new DoubleReductionOperation<B, OptionalDoubleResult>(
                program, expression, AVERAGE);
    }

    static <B extends DataFlowBinding>
    DoubleReductionOperation<B, OptionalDoubleResult> min(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        return new DoubleReductionOperation<B, OptionalDoubleResult>(
                program, expression, MIN);
    }

    static <B extends DataFlowBinding>
    DoubleReductionOperation<B, OptionalDoubleResult> max(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        return new DoubleReductionOperation<B, OptionalDoubleResult>(
                program, expression, MAX);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->double-reduce(" + kind + ","
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Projected<double> -> Scalar";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> DoubleReduce(" + kind + ")";
    }

    @Override
    public String physicalPlan() {
        return "candidate-stream[canonical-left-fold]";
    }

    @Override
    public boolean parallelBranchSafe() {
        return program.parallelBranchSafe()
                && expression.parallelSafe;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionOutcome<R> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        final double[] accumulator = new double[1];
        final int[] count = new int[1];
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        double value = expression.evaluate(
                                frame, binding, index);
                        if (kind == SUM || kind == AVERAGE) {
                            accumulator[0] += value;
                        } else if (count[0] == 0
                                || (kind == MIN
                                ? Double.compare(value, accumulator[0]) < 0
                                : Double.compare(value, accumulator[0]) > 0)) {
                            accumulator[0] = value;
                        }
                        count[0]++;
                        return true;
                    }
                },
                "dataflow.doubleReduce");
        Object result;
        if (kind == SUM) {
            result = new DoubleScalarResult(accumulator[0]);
        } else if (kind == AVERAGE) {
            result = count[0] == 0
                    ? OptionalDoubleResult.empty()
                    : OptionalDoubleResult.of(
                    accumulator[0] / (double) count[0]);
        } else {
            result = count[0] == 0
                    ? OptionalDoubleResult.empty()
                    : OptionalDoubleResult.of(accumulator[0]);
        }
        frame.reserveOutput(1L, 8L, "dataflow.doubleReduce");
        return new ExecutionOutcome<R>(
                (R) result,
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class LongPrefixOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<LongColumnResult> {
    private final CandidateProgram<B> program;
    private final LongExpression<B> expression;
    private final boolean inclusive;
    private final long seed;

    private LongPrefixOperation(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            boolean inclusive,
            long seed) {
        super(program, expression.parameters);
        this.program = program;
        this.expression = expression;
        this.inclusive = inclusive;
        this.seed = seed;
    }

    static <B extends DataFlowBinding> LongPrefixOperation<B> inclusive(
            CandidateProgram<B> program, LongExpression<B> expression) {
        return new LongPrefixOperation<B>(program, expression, true, 0L);
    }

    static <B extends DataFlowBinding> LongPrefixOperation<B> exclusive(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            long seed) {
        return new LongPrefixOperation<B>(program, expression, false, seed);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->prefix("
                + (inclusive ? "inclusive" : "exclusive") + ","
                + seed + "," + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Projected<long> -> Projected<prefix-long>";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> "
                + (inclusive ? "Inclusive" : "Exclusive") + "PrefixSum";
    }

    @Override
    public String physicalPlan() {
        return "candidate-stream[ordered-prefix-left-fold]";
    }

    @Override
    public ExecutionOutcome<LongColumnResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        int capacity = program.maximumCardinality(binding);
        final long[] values =
                frame.newOutputLongs(capacity, "dataflow.prefix");
        final long[] accumulator = new long[] {seed};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        long value = expression.evaluate(
                                frame, binding, index);
                        if (inclusive) {
                            accumulator[0] += value;
                            values[outputPosition] = accumulator[0];
                        } else {
                            values[outputPosition] = accumulator[0];
                            accumulator[0] += value;
                        }
                        return true;
                    }
                },
                "dataflow.prefix");
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(values, visit.matched),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class RegisteredLongReductionOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<LongScalarResult> {
    private final CandidateProgram<B> program;
    private final LongExpression<B> expression;
    private final RegisteredLongReducer reducer;
    private final String registeredIdentity;
    private final boolean parallelEligible;

    RegisteredLongReductionOperation(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            RegisteredLongReducer reducer) {
        super(program, expression.parameters);
        this.program = program;
        this.expression = expression;
        this.reducer = reducer;
        registeredIdentity = DataFlowSupport.registeredIdentity(
                "registered-long-reducer",
                reducer.semanticId(),
                reducer.version());
        parallelEligible = expression.parallelSafe
                && reducer.associative()
                && reducer.deterministic()
                && reducer.threadSafe();
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->" + registeredIdentity
                + "(" + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Projected<long> -> Scalar<long>";
    }

    @Override
    public String logicalPlan() {
        return program.canonical()
                + " -> RegisteredLongReduce(" + registeredIdentity + ")";
    }

    @Override
    public String physicalPlan() {
        return parallelEligible
                ? "candidate-adaptive[registered-fixed-tree-long-reduce]"
                : "candidate-stream[registered-left-fold]";
    }

    @Override
    public boolean parallelBranchSafe() {
        return program.parallelBranchSafe()
                && parallelEligible;
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(
            final ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        int cardinality = program.supportsContiguousParallel()
                ? program.contiguousCardinality(binding) : 0;
        ParallelPlan plan = ParallelExecution.plan(
                frame,
                cardinality,
                parallelEligible && program.supportsContiguousParallel(),
                16L,
                1,
                "dataflow.registeredLongReduce");
        if (plan.parallel()) {
            List<RegisteredLongPartial> partials = ParallelExecution.run(
                    frame,
                    plan,
                    new ParallelWork<RegisteredLongPartial>() {
                        @Override
                        public RegisteredLongPartial execute(
                                int partition,
                                int startInclusive,
                                int endExclusive) {
                            long state = seed();
                            int count = 0;
                            for (int index = startInclusive;
                                 index < endExclusive;
                                 index++) {
                                if ((index & 1023) == 0) {
                                    frame.checkBoundary(
                                            "dataflow.registeredLongReduce");
                                }
                                if (!program.parallelMatches(
                                        frame, binding, index)) {
                                    continue;
                                }
                                state = accumulate(
                                        state,
                                        expression.evaluate(
                                                frame, binding, index));
                                count++;
                            }
                            return new RegisteredLongPartial(state, count);
                        }
                    },
                    "dataflow.registeredLongReduce");
            long state = seed();
            long matched = 0L;
            for (RegisteredLongPartial partial : partials) {
                state = merge(state, partial.state);
                matched += partial.count;
            }
            long result = finish(state);
            frame.reserveOutput(
                    1L, 8L, "dataflow.registeredLongReduce");
            return new ExecutionOutcome<LongScalarResult>(
                    new LongScalarResult(result),
                    cardinality,
                    matched,
                    1L,
                    plan.tasks(),
                    plan.workers());
        }

        final long[] state = new long[] {seed()};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        state[0] = accumulate(
                                state[0],
                                expression.evaluate(frame, binding, index));
                        return true;
                    }
                },
                "dataflow.registeredLongReduce");
        long result = finish(state[0]);
        frame.reserveOutput(1L, 8L, "dataflow.registeredLongReduce");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(result),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }

    private long seed() {
        try {
            return reducer.seed();
        } catch (SomaRuntimeException failure) {
            throw failure;
        } catch (RuntimeException callback) {
            throw callback("seed", callback);
        }
    }

    private long accumulate(long state, long value) {
        try {
            return reducer.accumulate(state, value);
        } catch (SomaRuntimeException failure) {
            throw failure;
        } catch (RuntimeException callback) {
            throw callback("accumulate", callback);
        }
    }

    private long merge(long left, long right) {
        try {
            return reducer.merge(left, right);
        } catch (SomaRuntimeException failure) {
            throw failure;
        } catch (RuntimeException callback) {
            throw callback("merge", callback);
        }
    }

    private long finish(long state) {
        try {
            return reducer.finish(state);
        } catch (SomaRuntimeException failure) {
            throw failure;
        } catch (RuntimeException callback) {
            throw callback("finish", callback);
        }
    }

    private SomaRuntimeException callback(
            String phase, RuntimeException failure) {
        return DataFlowFailures.callback(
                "dataflow_registered_long_reducer_failed",
                registeredIdentity,
                "dataflow.reduce." + phase,
                failure);
    }
}

final class RegisteredLongPartial {
    final long state;
    final int count;

    RegisteredLongPartial(long state, int count) {
        this.state = state;
        this.count = count;
    }
}
