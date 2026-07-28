package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Primitive/reference projected callback-delivery kernels。 */
abstract class ValueDeliveryOperation<
        B extends DataFlowBinding, V>
        extends SingleSourceOperation<DeliveryResult> {
    final CandidateProgram<B> program;
    final ParameterSlot<V> visitorSlot;

    ValueDeliveryOperation(
            CandidateProgram<B> program,
            java.util.List<ParameterSlot<?>> parameters,
            ParameterSlot<V> visitorSlot) {
        super(
                program,
                DataFlowSupport.unionParameters(
                        parameters,
                        java.util.Collections
                                .<ParameterSlot<?>>singletonList(visitorSlot)));
        this.program = program;
        this.visitorSlot = visitorSlot;
    }

    @Override
    public final String logicalShape() {
        return "Projected -> CallbackDelivery";
    }

    @Override
    public final String logicalPlan() {
        return program.canonical()
                + " -> Project -> Deliver(callback-fence)";
    }

    @Override
    public final String physicalPlan() {
        return program.requiresBarrier()
                ? "candidate-selection[" + program.physicalForm()
                        + ",project,callback-delivery]"
                : "candidate-stream[" + program.physicalForm()
                        + ",fused-project-callback-delivery]";
    }

    @Override
    public final ResultDeliveryMode resultDeliveryMode() {
        return ResultDeliveryMode.CALLBACK_SCOPED;
    }

    final void preflight(ExecutionFrame frame, long width) {
        int maximum = program.maximumCardinality(frame.binding(source));
        frame.preflightDelivery(
                maximum, multiply(maximum, width), "dataflow.project.deliver");
    }

    final SomaRuntimeException failure(RuntimeException callback) {
        return DataFlowFailures.callback(
                "dataflow_projected_delivery_callback",
                source.alias(),
                "dataflow.project.deliver",
                callback);
    }

    private static long multiply(int count, long width) {
        return (long) count * width;
    }
}

final class LongValueDeliveryOperation<B extends DataFlowBinding>
        extends ValueDeliveryOperation<B, LongValueVisitor> {
    private final LongExpression<B> expression;

    LongValueDeliveryOperation(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            ParameterSlot<LongValueVisitor> visitorSlot) {
        super(program, expression.parameters, visitorSlot);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->deliver(long)";
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        preflight(frame, 8L);
        final DataFlowBinding binding = frame.binding(source);
        final LongValueVisitor visitor = frame.parameter(visitorSlot);
        final boolean[] completed = new boolean[] {true};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            boolean more = visitor.visit(expression.evaluate(
                                    frame, binding, index));
                            if (!more) completed[0] = false;
                            return more;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.deliver");
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(visit.matched, completed[0]),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class DoubleValueDeliveryOperation<B extends DataFlowBinding>
        extends ValueDeliveryOperation<B, DoubleValueVisitor> {
    private final DoubleExpression<B> expression;

    DoubleValueDeliveryOperation(
            CandidateProgram<B> program,
            DoubleExpression<B> expression,
            ParameterSlot<DoubleValueVisitor> visitorSlot) {
        super(program, expression.parameters, visitorSlot);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->deliver(double)";
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        preflight(frame, 8L);
        final DataFlowBinding binding = frame.binding(source);
        final DoubleValueVisitor visitor = frame.parameter(visitorSlot);
        final boolean[] completed = new boolean[] {true};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            boolean more = visitor.visit(expression.evaluate(
                                    frame, binding, index));
                            if (!more) completed[0] = false;
                            return more;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.deliver");
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(visit.matched, completed[0]),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class BooleanValueDeliveryOperation<B extends DataFlowBinding>
        extends ValueDeliveryOperation<B, BooleanValueVisitor> {
    private final BooleanExpression<B> expression;

    BooleanValueDeliveryOperation(
            CandidateProgram<B> program,
            BooleanExpression<B> expression,
            ParameterSlot<BooleanValueVisitor> visitorSlot) {
        super(program, expression.parameters, visitorSlot);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->deliver(boolean)";
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        preflight(frame, 1L);
        final DataFlowBinding binding = frame.binding(source);
        final BooleanValueVisitor visitor = frame.parameter(visitorSlot);
        final boolean[] completed = new boolean[] {true};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            boolean more = visitor.visit(expression.evaluate(
                                    frame, binding, index));
                            if (!more) completed[0] = false;
                            return more;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.deliver");
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(visit.matched, completed[0]),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}

final class StringValueDeliveryOperation<
        B extends DataFlowBinding>
        extends ValueDeliveryOperation<B, StringValueVisitor> {
    private final StringExpression<B> expression;

    StringValueDeliveryOperation(
            CandidateProgram<B> program,
            StringExpression<B> expression,
            ParameterSlot<StringValueVisitor> visitorSlot) {
        super(program, expression.parameters, visitorSlot);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->deliver(string-reference)";
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        preflight(frame, 8L);
        final DataFlowBinding binding = frame.binding(source);
        final StringValueVisitor visitor = frame.parameter(visitorSlot);
        final boolean[] completed = new boolean[] {true};
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            boolean more = visitor.visit(expression.evaluate(
                                    frame, binding, index));
                            if (!more) completed[0] = false;
                            return more;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.deliver");
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(visit.matched, completed[0]),
                visit.scanned,
                visit.matched,
                visit.matched,
                1,
                1);
    }
}
