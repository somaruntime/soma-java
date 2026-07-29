package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.time.LocalTime;

/** Immutable logical local-time expression backed by nano-of-day. */
public final class TimeExpression<B extends DataFlowBinding> {
    private final LongExpression<B> carrier;

    TimeExpression(LongExpression<B> carrier) {
        if (carrier == null) throw new NullPointerException("carrier");
        this.carrier = carrier;
    }

    public BooleanExpression<B> isPresent() { return carrier.isPresent(); }
    public BooleanExpression<B> isAbsent() { return carrier.isAbsent(); }

    public TimeExpression<B> coalesce(LocalTime fallback) {
        return new TimeExpression<B>(
                carrier.coalesce(nanoOfDay(fallback)));
    }

    public TimeExpression<B> plusNanos(long nanos) {
        return new TimeExpression<B>(
                LogicalLongExpressions.timeAdd(carrier, nanos));
    }

    public TimeExpression<B> minusNanos(long nanos) {
        return new TimeExpression<B>(
                LogicalLongExpressions.timeSubtract(carrier, nanos));
    }

    public BooleanExpression<B> equalTo(LocalTime value) {
        return carrier.equalTo(nanoOfDay(value));
    }

    public BooleanExpression<B> notEqualTo(LocalTime value) {
        return carrier.notEqualTo(nanoOfDay(value));
    }

    public BooleanExpression<B> before(LocalTime value) {
        return carrier.lessThan(nanoOfDay(value));
    }

    public BooleanExpression<B> beforeOrEqualTo(LocalTime value) {
        return carrier.lessThanOrEqualTo(nanoOfDay(value));
    }

    public BooleanExpression<B> after(LocalTime value) {
        return carrier.greaterThan(nanoOfDay(value));
    }

    public BooleanExpression<B> afterOrEqualTo(LocalTime value) {
        return carrier.greaterThanOrEqualTo(nanoOfDay(value));
    }

    public BooleanExpression<B> equalTo(TimeExpression<B> other) {
        return carrier.equalTo(required(other).carrier);
    }

    public BooleanExpression<B> notEqualTo(TimeExpression<B> other) {
        return carrier.notEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> before(TimeExpression<B> other) {
        return carrier.lessThan(required(other).carrier);
    }

    public BooleanExpression<B> beforeOrEqualTo(TimeExpression<B> other) {
        return carrier.lessThanOrEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> after(TimeExpression<B> other) {
        return carrier.greaterThan(required(other).carrier);
    }

    public BooleanExpression<B> afterOrEqualTo(TimeExpression<B> other) {
        return carrier.greaterThanOrEqualTo(required(other).carrier);
    }

    public CandidateOrder<B> ascending() { return carrier.ascending(); }
    public CandidateOrder<B> descending() { return carrier.descending(); }
    public String identity() { return carrier.identity(); }

    LongExpression<B> carrier() { return carrier; }

    private static long nanoOfDay(LocalTime value) {
        if (value == null) throw new NullPointerException("value");
        return value.toNanoOfDay();
    }

    private static <B extends DataFlowBinding> TimeExpression<B> required(
            TimeExpression<B> value) {
        if (value == null) throw new NullPointerException("other");
        return value;
    }
}
