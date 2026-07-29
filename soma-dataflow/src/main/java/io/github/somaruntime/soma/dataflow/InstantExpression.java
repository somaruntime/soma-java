package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.time.Instant;

/** Immutable logical instant expression backed by epoch milliseconds. */
public final class InstantExpression<B extends DataFlowBinding> {
    private final LongExpression<B> carrier;

    InstantExpression(LongExpression<B> carrier) {
        if (carrier == null) throw new NullPointerException("carrier");
        this.carrier = carrier;
    }

    public BooleanExpression<B> isPresent() { return carrier.isPresent(); }
    public BooleanExpression<B> isAbsent() { return carrier.isAbsent(); }

    public InstantExpression<B> coalesce(Instant fallback) {
        return new InstantExpression<B>(
                carrier.coalesce(epochMillis(fallback)));
    }

    public InstantExpression<B> plusMillis(long millis) {
        return new InstantExpression<B>(
                LogicalLongExpressions.checkedAdd(
                        carrier, millis, "instant-plus-millis"));
    }

    public InstantExpression<B> minusMillis(long millis) {
        return new InstantExpression<B>(
                LogicalLongExpressions.checkedSubtract(
                        carrier, millis, "instant-minus-millis"));
    }

    public BooleanExpression<B> equalTo(Instant value) {
        return carrier.equalTo(epochMillis(value));
    }

    public BooleanExpression<B> notEqualTo(Instant value) {
        return carrier.notEqualTo(epochMillis(value));
    }

    public BooleanExpression<B> before(Instant value) {
        return carrier.lessThan(epochMillis(value));
    }

    public BooleanExpression<B> beforeOrEqualTo(Instant value) {
        return carrier.lessThanOrEqualTo(epochMillis(value));
    }

    public BooleanExpression<B> after(Instant value) {
        return carrier.greaterThan(epochMillis(value));
    }

    public BooleanExpression<B> afterOrEqualTo(Instant value) {
        return carrier.greaterThanOrEqualTo(epochMillis(value));
    }

    public BooleanExpression<B> equalTo(InstantExpression<B> other) {
        return carrier.equalTo(required(other).carrier);
    }

    public BooleanExpression<B> notEqualTo(InstantExpression<B> other) {
        return carrier.notEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> before(InstantExpression<B> other) {
        return carrier.lessThan(required(other).carrier);
    }

    public BooleanExpression<B> beforeOrEqualTo(InstantExpression<B> other) {
        return carrier.lessThanOrEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> after(InstantExpression<B> other) {
        return carrier.greaterThan(required(other).carrier);
    }

    public BooleanExpression<B> afterOrEqualTo(InstantExpression<B> other) {
        return carrier.greaterThanOrEqualTo(required(other).carrier);
    }

    public CandidateOrder<B> ascending() { return carrier.ascending(); }
    public CandidateOrder<B> descending() { return carrier.descending(); }
    public String identity() { return carrier.identity(); }

    LongExpression<B> carrier() { return carrier; }

    private static long epochMillis(Instant value) {
        if (value == null) throw new NullPointerException("value");
        return value.toEpochMilli();
    }

    private static <B extends DataFlowBinding> InstantExpression<B> required(
            InstantExpression<B> value) {
        if (value == null) throw new NullPointerException("other");
        return value;
    }
}
