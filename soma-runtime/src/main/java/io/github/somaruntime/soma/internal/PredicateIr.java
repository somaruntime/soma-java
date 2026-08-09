package io.github.somaruntime.soma.internal;

/** Immutable, data-only typed predicate IR owned by one generated Table. */
final class PredicateIr {

    enum Kind {
        CONSTANT,
        EQ,
        NE,
        LT,
        LE,
        GT,
        GE,
        BETWEEN,
        IN,
        IS_NULL,
        IS_NOT_NULL,
        AND,
        OR,
        NOT
    }

    final Kind kind;
    final boolean constant;
    final int fieldIndex;
    final GeneratedProbe lower;
    final GeneratedProbe upper;
    final GeneratedProbe[] literals;
    final PredicateIr left;
    final PredicateIr right;

    private PredicateIr(
            Kind kind,
            boolean constant,
            int fieldIndex,
            GeneratedProbe lower,
            GeneratedProbe upper,
            GeneratedProbe[] literals,
            PredicateIr left,
            PredicateIr right) {
        this.kind = kind;
        this.constant = constant;
        this.fieldIndex = fieldIndex;
        this.lower = lower;
        this.upper = upper;
        this.literals = literals == null ? null : literals.clone();
        this.left = left;
        this.right = right;
    }

    static PredicateIr constant(boolean value) {
        return new PredicateIr(
                Kind.CONSTANT, value, -1, null, null, null, null, null);
    }

    static PredicateIr compare(
            Kind kind,
            int fieldIndex,
            GeneratedProbe literal) {
        return new PredicateIr(
                kind, false, fieldIndex, literal, null, null, null, null);
    }

    static PredicateIr between(
            int fieldIndex,
            GeneratedProbe lower,
            GeneratedProbe upper) {
        return new PredicateIr(
                Kind.BETWEEN,
                false,
                fieldIndex,
                lower,
                upper,
                null,
                null,
                null);
    }

    static PredicateIr in(int fieldIndex, GeneratedProbe[] literals) {
        return new PredicateIr(
                Kind.IN, false, fieldIndex, null, null, literals, null, null);
    }

    static PredicateIr nullTest(Kind kind, int fieldIndex) {
        return new PredicateIr(
                kind, false, fieldIndex, null, null, null, null, null);
    }

    static PredicateIr binary(
            Kind kind,
            PredicateIr left,
            PredicateIr right) {
        return new PredicateIr(
                kind, false, -1, null, null, null, left, right);
    }

    static PredicateIr not(PredicateIr source) {
        return new PredicateIr(
                Kind.NOT, false, -1, null, null, null, source, null);
    }

    long inLiteralCount() {
        switch (kind) {
            case IN:
                return literals.length;
            case AND:
            case OR:
                return Math.addExact(
                        left.inLiteralCount(), right.inLiteralCount());
            case NOT:
                return left.inLiteralCount();
            default:
                return 0L;
        }
    }
}
