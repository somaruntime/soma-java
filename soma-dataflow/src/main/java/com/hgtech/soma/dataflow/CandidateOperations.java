package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;

final class CandidateCountOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<LongScalarResult> {
    private static final CandidateVisitor NOOP = new CandidateVisitor() {
        @Override
        public boolean accept(int index, int outputPosition) {
            return true;
        }
    };

    private final CandidateProgram<B> program;

    CandidateCountOperation(CandidateProgram<B> program) {
        super(program.source());
        this.program = program;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->count";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Scalar<long>";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> Count";
    }

    @Override
    public String physicalPlan() {
        if (program.supportsContiguousParallel()) {
            return "candidate-adaptive[contiguous-filter,count]";
        }
        return program.requiresBarrier()
                ? "candidate-barrier[stable-sort,count]"
                : "candidate-stream[filter-skip-limit,count]";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        ExecutionOutcome<LongScalarResult> parallel =
                ParallelCandidateExecution.count(program, frame);
        if (parallel != null) {
            return parallel;
        }
        CandidateVisit visit =
                program.visit(frame, NOOP, "dataflow.count");
        frame.reserveOutput(1L, 8L, "dataflow.count");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class CandidateMatchOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<BooleanScalarResult> {
    static final int ANY = 0;
    static final int NONE = 1;
    static final int ALL = 2;

    private final CandidateProgram<B> program;
    private final BooleanExpression<B> predicate;
    private final int mode;

    CandidateMatchOperation(
            CandidateProgram<B> program,
            BooleanExpression<B> predicate,
            int mode) {
        super(program.source());
        this.program = program;
        this.predicate = predicate;
        this.mode = mode;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->match(" + mode + ","
                + predicate.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Scalar<boolean>";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> Match(" + mode + ")";
    }

    @Override
    public String physicalPlan() {
        return program.requiresBarrier()
                ? "candidate-barrier[stable-sort,short-circuit-match]"
                : "candidate-stream[short-circuit-match]";
    }

    @Override
    public ExecutionOutcome<BooleanScalarResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        final boolean[] result = new boolean[] {mode == ALL};
        final boolean[] done = new boolean[1];
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        if (done[0]) {
                            return false;
                        }
                        boolean value = predicate.evaluate(binding, index);
                        if (mode == ANY && value) {
                            result[0] = true;
                            done[0] = true;
                        } else if (mode == NONE && value) {
                            result[0] = false;
                            done[0] = true;
                        } else if (mode == ALL && !value) {
                            result[0] = false;
                            done[0] = true;
                        }
                        return !done[0];
                    }
                },
                "dataflow.match");
        frame.reserveOutput(1L, 1L, "dataflow.match");
        return new ExecutionOutcome<BooleanScalarResult>(
                new BooleanScalarResult(result[0]),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class CandidateIndexSnapshotOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<IndexSnapshot> {
    private final CandidateProgram<B> program;

    CandidateIndexSnapshotOperation(CandidateProgram<B> program) {
        super(program.source());
        this.program = program;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->index-snapshot";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> IndexSnapshot";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> IndexSnapshot";
    }

    @Override
    public String physicalPlan() {
        return "candidate-selection-vector[index-snapshot-copy]";
    }

    @Override
    public ExecutionOutcome<IndexSnapshot> execute(ExecutionFrame frame) {
        ParallelCandidateSelection parallel =
                ParallelCandidateExecution.select(
                        program, frame, "dataflow.indexSnapshot");
        CandidateSelection selected = parallel == null
                ? program.select(frame, "dataflow.indexSnapshot")
                : new CandidateSelection(
                        parallel.indexes,
                        parallel.matched,
                        parallel.scanned);
        frame.reserveOutput(
                selected.size,
                (long) selected.size * 4L,
                "dataflow.indexSnapshot");
        IndexSnapshot result = frame.binding(source)
                .indexSnapshot(selected.indexes, selected.size);
        return new ExecutionOutcome<IndexSnapshot>(
                result,
                selected.scanned,
                selected.size,
                selected.size,
                parallel == null ? 1 : parallel.tasks,
                parallel == null ? 1 : parallel.workers);
    }
}

abstract class CandidateProjectionOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    final CandidateProgram<B> program;

    CandidateProjectionOperation(CandidateProgram<B> program) {
        super(program.source());
        this.program = program;
    }

    @Override
    public final String logicalPlan() {
        return program.canonical() + " -> " + projectionName();
    }

    @Override
    public final String physicalPlan() {
        if (program.supportsContiguousParallel()) {
            return "candidate-adaptive[contiguous-filter,stable-project]";
        }
        return program.requiresBarrier()
                ? "candidate-selection-vector[stable-sort,project]"
                : "candidate-stream[fused-project]";
    }

    abstract String projectionName();
}

final class LongColumnOperation<B extends DataFlowBinding>
        extends CandidateProjectionOperation<B, LongColumnResult> {
    private final LongExpression<B> expression;

    LongColumnOperation(
            CandidateProgram<B> program, LongExpression<B> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->long-column("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Projected<long> -> DetachedColumn<long>";
    }

    @Override
    String projectionName() {
        return "DetachedColumn<long>";
    }

    @Override
    public ExecutionOutcome<LongColumnResult> execute(ExecutionFrame frame) {
        ExecutionOutcome<LongColumnResult> parallel =
                ParallelCandidateExecution.longColumn(
                        program, expression, frame);
        if (parallel != null) {
            return parallel;
        }
        final DataFlowBinding binding = frame.binding(source);
        if (program.hasSort()) {
            CandidateSelection selected =
                    program.select(frame, "dataflow.longColumn");
            long[] values =
                    frame.newOutputLongs(selected.size, "dataflow.longColumn");
            for (int index = 0; index < selected.size; index++) {
                values[index] = expression.evaluate(
                        binding, selected.indexes[index]);
            }
            return new ExecutionOutcome<LongColumnResult>(
                    new LongColumnResult(values, selected.size),
                    selected.scanned,
                    selected.size,
                    selected.size,
                    1,
                    1);
        }
        int capacity = program.maximumCardinality(binding);
        final long[] values =
                frame.newOutputLongs(capacity, "dataflow.longColumn");
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        values[outputPosition] =
                                expression.evaluate(binding, index);
                        return true;
                    }
                },
                "dataflow.longColumn");
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(values, visit.matched),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class DoubleColumnOperation<B extends DataFlowBinding>
        extends CandidateProjectionOperation<B, DoubleColumnResult> {
    private final DoubleExpression<B> expression;

    DoubleColumnOperation(
            CandidateProgram<B> program, DoubleExpression<B> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->double-column("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Projected<double> -> DetachedColumn<double>";
    }

    @Override
    String projectionName() {
        return "DetachedColumn<double>";
    }

    @Override
    public ExecutionOutcome<DoubleColumnResult> execute(ExecutionFrame frame) {
        ExecutionOutcome<DoubleColumnResult> parallel =
                ParallelCandidateExecution.doubleColumn(
                        program, expression, frame);
        if (parallel != null) {
            return parallel;
        }
        final DataFlowBinding binding = frame.binding(source);
        final CandidateSelection selected = program.hasSort()
                ? program.select(frame, "dataflow.doubleColumn") : null;
        int capacity = selected == null
                ? program.maximumCardinality(binding) : selected.size;
        final double[] values =
                frame.newOutputDoubles(capacity, "dataflow.doubleColumn");
        if (selected != null) {
            for (int index = 0; index < selected.size; index++) {
                values[index] = expression.evaluate(
                        binding, selected.indexes[index]);
            }
            return new ExecutionOutcome<DoubleColumnResult>(
                    new DoubleColumnResult(values, selected.size),
                    selected.scanned,
                    selected.size,
                    selected.size,
                    1,
                    1);
        }
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        values[outputPosition] =
                                expression.evaluate(binding, index);
                        return true;
                    }
                },
                "dataflow.doubleColumn");
        return new ExecutionOutcome<DoubleColumnResult>(
                new DoubleColumnResult(values, visit.matched),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class BooleanColumnOperation<B extends DataFlowBinding>
        extends CandidateProjectionOperation<B, BooleanColumnResult> {
    private final BooleanExpression<B> expression;

    BooleanColumnOperation(
            CandidateProgram<B> program, BooleanExpression<B> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->boolean-column("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Projected<boolean> -> DetachedColumn<boolean>";
    }

    @Override
    String projectionName() {
        return "DetachedColumn<boolean>";
    }

    @Override
    public ExecutionOutcome<BooleanColumnResult> execute(ExecutionFrame frame) {
        ExecutionOutcome<BooleanColumnResult> parallel =
                ParallelCandidateExecution.booleanColumn(
                        program, expression, frame);
        if (parallel != null) {
            return parallel;
        }
        final DataFlowBinding binding = frame.binding(source);
        int capacity = program.maximumCardinality(binding);
        final boolean[] values =
                frame.newOutputBooleans(capacity, "dataflow.booleanColumn");
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        values[outputPosition] =
                                expression.evaluate(binding, index);
                        return true;
                    }
                },
                "dataflow.booleanColumn");
        return new ExecutionOutcome<BooleanColumnResult>(
                new BooleanColumnResult(values, visit.matched),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class ObjectColumnOperation<B extends DataFlowBinding, T>
        extends CandidateProjectionOperation<B, ObjectColumnResult<T>> {
    private final ObjectExpression<B, T> expression;

    ObjectColumnOperation(
            CandidateProgram<B> program, ObjectExpression<B, T> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->object-column("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> Projected<object> -> DetachedColumn<object>";
    }

    @Override
    String projectionName() {
        return "DetachedColumn<object>";
    }

    @Override
    public ExecutionOutcome<ObjectColumnResult<T>> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        int capacity = program.maximumCardinality(binding);
        final Object[] values =
                frame.newOutputObjects(capacity, "dataflow.objectColumn");
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        values[outputPosition] =
                                expression.evaluate(binding, index);
                        return true;
                    }
                },
                "dataflow.objectColumn");
        return new ExecutionOutcome<ObjectColumnResult<T>>(
                new ObjectColumnResult<T>(values, visit.matched),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}
