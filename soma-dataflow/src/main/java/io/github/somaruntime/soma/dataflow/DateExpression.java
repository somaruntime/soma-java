package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.time.LocalDate;

/** Immutable logical date expression backed by an epoch-day carrier. */
public final class DateExpression<B extends DataFlowBinding> {
    private final LongExpression<B> carrier;

    DateExpression(LongExpression<B> carrier) {
        if (carrier == null) throw new NullPointerException("carrier");
        this.carrier = carrier;
    }

    public BooleanExpression<B> isPresent() { return carrier.isPresent(); }
    public BooleanExpression<B> isAbsent() { return carrier.isAbsent(); }

    public DateExpression<B> coalesce(LocalDate fallback) {
        return new DateExpression<B>(
                carrier.coalesce(epochDay(fallback)));
    }

    public DateExpression<B> plusDays(long days) {
        return new DateExpression<B>(
                LogicalLongExpressions.checkedAdd(
                        carrier, days, "date-plus-days"));
    }

    public DateExpression<B> minusDays(long days) {
        return new DateExpression<B>(
                LogicalLongExpressions.checkedSubtract(
                        carrier, days, "date-minus-days"));
    }

    public BooleanExpression<B> equalTo(LocalDate value) {
        return carrier.equalTo(epochDay(value));
    }

    public BooleanExpression<B> notEqualTo(LocalDate value) {
        return carrier.notEqualTo(epochDay(value));
    }

    public BooleanExpression<B> before(LocalDate value) {
        return carrier.lessThan(epochDay(value));
    }

    public BooleanExpression<B> beforeOrEqualTo(LocalDate value) {
        return carrier.lessThanOrEqualTo(epochDay(value));
    }

    public BooleanExpression<B> after(LocalDate value) {
        return carrier.greaterThan(epochDay(value));
    }

    public BooleanExpression<B> afterOrEqualTo(LocalDate value) {
        return carrier.greaterThanOrEqualTo(epochDay(value));
    }

    public BooleanExpression<B> equalTo(DateExpression<B> other) {
        return carrier.equalTo(required(other).carrier);
    }

    public BooleanExpression<B> notEqualTo(DateExpression<B> other) {
        return carrier.notEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> before(DateExpression<B> other) {
        return carrier.lessThan(required(other).carrier);
    }

    public BooleanExpression<B> beforeOrEqualTo(DateExpression<B> other) {
        return carrier.lessThanOrEqualTo(required(other).carrier);
    }

    public BooleanExpression<B> after(DateExpression<B> other) {
        return carrier.greaterThan(required(other).carrier);
    }

    public BooleanExpression<B> afterOrEqualTo(DateExpression<B> other) {
        return carrier.greaterThanOrEqualTo(required(other).carrier);
    }

    public CandidateOrder<B> ascending() { return carrier.ascending(); }
    public CandidateOrder<B> descending() { return carrier.descending(); }
    public String identity() { return carrier.identity(); }

    LongExpression<B> carrier() { return carrier; }

    private static long epochDay(LocalDate value) {
        if (value == null) throw new NullPointerException("value");
        return value.toEpochDay();
    }

    private static <B extends DataFlowBinding> DateExpression<B> required(
            DateExpression<B> value) {
        if (value == null) throw new NullPointerException("other");
        return value;
    }
}
