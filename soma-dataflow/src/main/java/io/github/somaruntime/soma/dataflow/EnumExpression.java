package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/**
 * Immutable schema-bound enum expression backed by a primitive ordinal.
 *
 * <p>The ordinal carrier is an implementation detail. Public operations remain
 * tied to one concrete enum type and do not expose numeric arithmetic.</p>
 */
public final class EnumExpression<
        B extends DataFlowBinding, E extends Enum<E>> {
    private final LongExpression<B> carrier;
    private final Class<E> enumType;

    EnumExpression(LongExpression<B> carrier, Class<E> enumType) {
        if (carrier == null) throw new NullPointerException("carrier");
        if (enumType == null) throw new NullPointerException("enumType");
        this.carrier = carrier;
        this.enumType = enumType;
    }

    public BooleanExpression<B> isPresent() {
        return carrier.isPresent();
    }

    public BooleanExpression<B> isAbsent() {
        return carrier.isAbsent();
    }

    public EnumExpression<B, E> coalesce(E fallback) {
        return new EnumExpression<B, E>(
                carrier.coalesce(ordinal(fallback)), enumType);
    }

    public BooleanExpression<B> equalTo(E value) {
        return carrier.equalTo(ordinal(value));
    }

    public BooleanExpression<B> notEqualTo(E value) {
        return carrier.notEqualTo(ordinal(value));
    }

    public BooleanExpression<B> lessThan(E value) {
        return carrier.lessThan(ordinal(value));
    }

    public BooleanExpression<B> lessThanOrEqualTo(E value) {
        return carrier.lessThanOrEqualTo(ordinal(value));
    }

    public BooleanExpression<B> greaterThan(E value) {
        return carrier.greaterThan(ordinal(value));
    }

    public BooleanExpression<B> greaterThanOrEqualTo(E value) {
        return carrier.greaterThanOrEqualTo(ordinal(value));
    }

    public BooleanExpression<B> equalTo(EnumExpression<B, E> other) {
        requireType(other);
        return carrier.equalTo(other.carrier);
    }

    public BooleanExpression<B> notEqualTo(EnumExpression<B, E> other) {
        requireType(other);
        return carrier.notEqualTo(other.carrier);
    }

    public BooleanExpression<B> lessThan(EnumExpression<B, E> other) {
        requireType(other);
        return carrier.lessThan(other.carrier);
    }

    public BooleanExpression<B> lessThanOrEqualTo(
            EnumExpression<B, E> other) {
        requireType(other);
        return carrier.lessThanOrEqualTo(other.carrier);
    }

    public BooleanExpression<B> greaterThan(EnumExpression<B, E> other) {
        requireType(other);
        return carrier.greaterThan(other.carrier);
    }

    public BooleanExpression<B> greaterThanOrEqualTo(
            EnumExpression<B, E> other) {
        requireType(other);
        return carrier.greaterThanOrEqualTo(other.carrier);
    }

    public CandidateOrder<B> ascending() {
        return carrier.ascending();
    }

    public CandidateOrder<B> descending() {
        return carrier.descending();
    }

    public String identity() {
        return carrier.identity();
    }

    LongExpression<B> carrier() {
        return carrier;
    }

    String enumTypeName() {
        return enumType.getName();
    }

    private int ordinal(E value) {
        if (value == null) throw new NullPointerException("value");
        if (value.getDeclaringClass() != enumType) {
            throw new IllegalArgumentException("enum value type mismatch");
        }
        return value.ordinal();
    }

    private void requireType(EnumExpression<B, E> other) {
        if (other == null) throw new NullPointerException("other");
        if (enumType != other.enumType) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_enum_type_mismatch",
                    carrier.path,
                    "dataflow.expression");
        }
    }
}
