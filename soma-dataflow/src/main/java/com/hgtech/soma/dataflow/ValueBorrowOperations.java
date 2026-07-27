package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

abstract class ValueBorrowOperation<B extends DataFlowBinding>
        extends SingleSourceOperation<LongScalarResult> {
    final CandidateProgram<B> program;
    final long opaqueIdentity;

    ValueBorrowOperation(
            CandidateProgram<B> program,
            java.util.List<ParameterSlot<?>> parameters) {
        super(program, parameters);
        this.program = program;
        opaqueIdentity = DataFlowSupport.nextOpaqueIdentity();
    }

    @Override
    public final String logicalShape() {
        return "Projected -> BorrowedTraversal";
    }

    @Override
    public final String logicalPlan() {
        return program.canonical() + " -> Project -> Borrow(value-fence)";
    }

    @Override
    public final String physicalPlan() {
        return program.requiresBarrier()
                ? "candidate-selection-vector[project,borrow]"
                : "candidate-stream[fused-project-borrow]";
    }

    final SomaRuntimeException failure(RuntimeException callback) {
        return DataFlowFailures.callback(
                "dataflow_projected_borrow_callback",
                source.alias(),
                "dataflow.project.borrow",
                callback);
    }
}

final class LongValueBorrowOperation<B extends DataFlowBinding>
        extends ValueBorrowOperation<B> {
    private final LongExpression<B> expression;
    private final LongValueConsumer consumer;

    LongValueBorrowOperation(
            CandidateProgram<B> program,
            LongExpression<B> expression,
            LongValueConsumer consumer) {
        super(program, expression.parameters);
        this.expression = expression;
        this.consumer = consumer;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->borrow(opaque-instance-"
                + opaqueIdentity + ")";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            consumer.accept(expression.evaluate(
                                    frame, binding, index));
                            return true;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class DoubleValueBorrowOperation<B extends DataFlowBinding>
        extends ValueBorrowOperation<B> {
    private final DoubleExpression<B> expression;
    private final DoubleValueConsumer consumer;

    DoubleValueBorrowOperation(
            CandidateProgram<B> program,
            DoubleExpression<B> expression,
            DoubleValueConsumer consumer) {
        super(program, expression.parameters);
        this.expression = expression;
        this.consumer = consumer;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->borrow(opaque-instance-"
                + opaqueIdentity + ")";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            consumer.accept(expression.evaluate(
                                    frame, binding, index));
                            return true;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class BooleanValueBorrowOperation<B extends DataFlowBinding>
        extends ValueBorrowOperation<B> {
    private final BooleanExpression<B> expression;
    private final BooleanValueConsumer consumer;

    BooleanValueBorrowOperation(
            CandidateProgram<B> program,
            BooleanExpression<B> expression,
            BooleanValueConsumer consumer) {
        super(program, expression.parameters);
        this.expression = expression;
        this.consumer = consumer;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->borrow(opaque-instance-"
                + opaqueIdentity + ")";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            consumer.accept(expression.evaluate(
                                    frame, binding, index));
                            return true;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class StringValueBorrowOperation<
        B extends DataFlowBinding>
        extends ValueBorrowOperation<B> {
    private final StringExpression<B> expression;
    private final StringValueConsumer consumer;

    StringValueBorrowOperation(
            CandidateProgram<B> program,
            StringExpression<B> expression,
            StringValueConsumer consumer) {
        super(program, expression.parameters);
        this.expression = expression;
        this.consumer = consumer;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->project("
                + expression.identity() + ")->borrow(opaque-instance-"
                + opaqueIdentity + ")";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        final DataFlowBinding binding = frame.binding(source);
        CandidateVisit visit = program.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int outputPosition) {
                        try {
                            consumer.accept(expression.evaluate(
                                    frame, binding, index));
                            return true;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException callback) {
                            throw failure(callback);
                        }
                    }
                },
                "dataflow.project.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}
