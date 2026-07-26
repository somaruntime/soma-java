package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.CandidateBorrowAccess;
import com.hgtech.soma.dataflow.generated.CandidateMaterializationAccess;
import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.MaterializationBudget;

import java.util.List;

final class PointExistsOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<BooleanScalarResult> {
    private final CandidateProgram<B> program;

    PointExistsOperation(CandidateProgram<B> program) {
        super(program);
        this.program = program;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->exists";
    }

    @Override
    public String logicalShape() {
        return "Point -> Probe<boolean>";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> Exists";
    }

    @Override
    public String physicalPlan() {
        return "point-probe";
    }

    @Override
    public ExecutionOutcome<BooleanScalarResult> execute(ExecutionFrame frame) {
        CandidateSelection selected =
                program.select(frame, "dataflow.point.exists");
        return new ExecutionOutcome<BooleanScalarResult>(
                new BooleanScalarResult(selected.size != 0),
                selected.scanned,
                selected.size,
                1L,
                1,
                1);
    }
}

final class CandidateBorrowOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<LongScalarResult> {
    private final CandidateProgram<B> program;
    private final CandidateBorrowAccess<B> access;
    private final Object consumer;

    CandidateBorrowOperation(
            CandidateProgram<B> program,
            CandidateBorrowAccess<B> access,
            Object consumer) {
        super(program);
        this.program = program;
        this.access = access;
        this.consumer = consumer;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->borrow("
                + access.identity() + ",opaque-instance)";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> BorrowedTraversal";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> Borrow(cursor-fence)";
    }

    @Override
    public String physicalPlan() {
        return "candidate-selection-vector[generated-cursor-borrow]";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        CandidateSelection selected =
                program.select(frame, "dataflow.borrow");
        @SuppressWarnings("unchecked")
        B binding = (B) frame.binding(source);
        access.borrow(binding, selected.indexes, selected.size, consumer);
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(selected.size),
                selected.scanned,
                selected.size,
                1L,
                1,
                1);
    }
}

final class CandidateMaterializeOperation<
        B extends DataFlowBinding, T>
        extends SingleSourceOperation<List<T>> {
    private final CandidateProgram<B> program;
    private final CandidateMaterializationAccess<B, T> access;
    private final MaterializationBudget budget;
    private final String shape;

    CandidateMaterializeOperation(
            CandidateProgram<B> program,
            CandidateMaterializationAccess<B, T> access,
            MaterializationBudget budget,
            String shape) {
        super(program);
        this.program = program;
        this.access = access;
        this.budget = budget;
        this.shape = shape;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->materialize("
                + access.identity() + "," + budget.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return shape + " -> MaterializedObject";
    }

    @Override
    public String logicalPlan() {
        return program.canonical() + " -> Materialize(explicit-budget)";
    }

    @Override
    public String physicalPlan() {
        return "candidate-selection-vector[two-pass-object-materialization]";
    }

    @Override
    public ExecutionOutcome<List<T>> execute(ExecutionFrame frame) {
        CandidateSelection selected =
                program.select(frame, "dataflow.materialize");
        frame.reserveOutput(
                selected.size,
                (long) selected.size * 8L,
                "dataflow.materialize");
        @SuppressWarnings("unchecked")
        B binding = (B) frame.binding(source);
        List<T> result = access.materialize(
                binding, selected.indexes, selected.size, budget);
        return new ExecutionOutcome<List<T>>(
                result,
                selected.scanned,
                selected.size,
                selected.size,
                1,
                1);
    }
}
