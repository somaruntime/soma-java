package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.CandidateDeliveryAccess;
import io.github.somaruntime.soma.dataflow.generated.CandidateDeliverySession;
import io.github.somaruntime.soma.dataflow.generated.CandidateMaterializationAccess;
import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.MaterializationBudget;

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
    public boolean parallelBranchSafe() {
        return program.parallelBranchSafe();
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

final class CandidateDeliveryOperation<
        B extends DataFlowBinding, V>
        extends SingleSourceOperation<DeliveryResult> {
    private final CandidateProgram<B> program;
    private final CandidateDeliveryAccess<B, V> access;
    private final ParameterSlot<V> visitorSlot;

    CandidateDeliveryOperation(
            CandidateProgram<B> program,
            CandidateDeliveryAccess<B, V> access,
            ParameterSlot<V> visitorSlot) {
        super(
                program,
                java.util.Collections
                        .<ParameterSlot<?>>singletonList(visitorSlot));
        this.program = program;
        this.access = access;
        this.visitorSlot = visitorSlot;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->deliver("
                + access.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Candidate -> CallbackDelivery";
    }

    @Override
    public String logicalPlan() {
        return program.canonical()
                + " -> Deliver(generated-cursor-fence)";
    }

    @Override
    public String physicalPlan() {
        return program.requiresBarrier()
                ? "candidate-selection[" + program.physicalForm()
                        + ",generated-cursor-callback-delivery]"
                : "candidate-stream[" + program.physicalForm()
                        + ",generated-cursor-callback-delivery]";
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
                "dataflow.deliver");
        @SuppressWarnings("unchecked")
        B binding = (B) frame.binding(source);
        final CandidateDeliverySession session =
                access.open(binding, frame.parameter(visitorSlot));
        if (session == null) {
            throw DataFlowFailures.internal(
                    "dataflow_delivery_session_missing",
                    source.alias(),
                    "dataflow.deliver",
                    access.identity());
        }
        final boolean[] completed = new boolean[] {true};
        CandidateVisit visit;
        try {
            visit = program.visit(
                    frame,
                    new CandidateVisitor() {
                        @Override
                        public boolean accept(
                                int index, int outputPosition) {
                            boolean more = session.visit(index);
                            if (!more) completed[0] = false;
                            return more;
                        }
                    },
                    "dataflow.deliver");
        } finally {
            session.close();
        }
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(visit.matched, completed[0]),
                visit.scanned,
                visit.matched,
                visit.matched,
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
                binding,
                selected.materializedIndexes(
                        frame, "dataflow.materialize.indexes"),
                selected.size,
                budget);
        return new ExecutionOutcome<List<T>>(
                result,
                selected.scanned,
                selected.size,
                selected.size,
                1,
                1);
    }
}
