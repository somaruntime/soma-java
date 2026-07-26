package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.List;

/** Invocation-local finite ordered Window shape. */
public final class WindowedFlow<B extends DataFlowBinding> {
    private final CandidateProgram<B> program;
    private final boolean time;
    private final LongExpression<B> orderKey;
    private final long width;
    private final long step;
    private final long origin;
    private final PartialWindowPolicy partialPolicy;
    private final WindowShapePlan<B> shape;

    private WindowedFlow(
            CandidateProgram<B> program,
            boolean time,
            LongExpression<B> orderKey,
            long width,
            long step,
            long origin,
            PartialWindowPolicy partialPolicy) {
        this(
                program,
                time,
                orderKey,
                width,
                step,
                origin,
                partialPolicy,
                WindowShapePlan.<B>empty());
    }

    private WindowedFlow(
            CandidateProgram<B> program,
            boolean time,
            LongExpression<B> orderKey,
            long width,
            long step,
            long origin,
            PartialWindowPolicy partialPolicy,
            WindowShapePlan<B> shape) {
        if (width <= 0L || step <= 0L) {
            throw new IllegalArgumentException("window width and step must be positive");
        }
        if (partialPolicy == null) {
            throw new NullPointerException("partialPolicy");
        }
        this.program = program;
        this.time = time;
        this.orderKey = orderKey;
        this.width = width;
        this.step = step;
        this.origin = origin;
        this.partialPolicy = partialPolicy;
        this.shape = shape;
    }

    static <B extends DataFlowBinding> WindowedFlow<B> count(
            CandidateProgram<B> program,
            int width,
            int step,
            PartialWindowPolicy partialPolicy) {
        return new WindowedFlow<B>(
                program, false, null, width, step, 0L, partialPolicy);
    }

    static <B extends DataFlowBinding> WindowedFlow<B> time(
            CandidateProgram<B> program,
            LongExpression<B> orderKey,
            long width,
            long step,
            long origin,
            PartialWindowPolicy partialPolicy) {
        return new WindowedFlow<B>(
                program, true, orderKey, width, step, origin, partialPolicy);
    }

    public DataFlowDefinition<WindowIndexResult> indexSnapshot() {
        return DataFlowDefinition.of(
                new WindowIndexOperation<B>(this));
    }

    public WindowedFlow<B> havingCountAtLeast(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return with(shape.havingAtLeast(count));
    }

    public WindowedFlow<B> havingCountAtMost(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException(
                    "count must be non-negative");
        }
        return with(shape.havingAtMost(count));
    }

    public DataFlowDefinition<LongScalarResult> borrow(WindowConsumer consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        return DataFlowDefinition.of(
                new WindowBorrowOperation<B>(this, consumer));
    }

    public DataFlowDefinition<LongColumnResult> sum(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                WindowLongAggregationOperation.sum(this, expression));
    }

    public DataFlowDefinition<LongColumnResult> counts() {
        return DataFlowDefinition.of(
                WindowLongAggregationOperation.<B>counts(this));
    }

    public DataFlowDefinition<LongColumnResult> min(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                WindowLongAggregationOperation.min(this, expression));
    }

    public DataFlowDefinition<LongColumnResult> max(
            LongExpression<B> expression) {
        requireSource(expression);
        return DataFlowDefinition.of(
                WindowLongAggregationOperation.max(this, expression));
    }

    CandidateProgram<B> program() {
        return program;
    }

    boolean time() {
        return time;
    }

    LongExpression<B> orderKey() {
        return orderKey;
    }

    List<ParameterSlot<?>> windowParameters() {
        return orderKey == null
                ? Collections.<ParameterSlot<?>>emptyList()
                : orderKey.parameters;
    }

    long width() {
        return width;
    }

    long step() {
        return step;
    }

    long origin() {
        return origin;
    }

    PartialWindowPolicy partialPolicy() {
        return partialPolicy;
    }

    WindowShapePlan<B> shape() {
        return shape;
    }

    private WindowedFlow<B> with(WindowShapePlan<B> next) {
        return new WindowedFlow<B>(
                program,
                time,
                orderKey,
                width,
                step,
                origin,
                partialPolicy,
                next);
    }

    private void requireSource(LongExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        if (expression.source != program.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_window_expression_source_mismatch",
                    program.source().alias(),
                    "dataflow.window");
        }
    }
}
