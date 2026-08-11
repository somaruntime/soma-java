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
    final CanonicalTableIdentity tableIdentity;
    final boolean constant;
    final int fieldIndex;
    final TypedLiteral lower;
    final TypedLiteral upper;
    final TypedLiteral[] literals;
    final PredicateIr left;
    final PredicateIr right;

    private PredicateIr(
            Kind kind,
            CanonicalTableIdentity tableIdentity,
            boolean constant,
            int fieldIndex,
            TypedLiteral lower,
            TypedLiteral upper,
            TypedLiteral[] literals,
            PredicateIr left,
            PredicateIr right) {
        this.kind = kind;
        this.tableIdentity = tableIdentity;
        this.constant = constant;
        this.fieldIndex = fieldIndex;
        this.lower = lower;
        this.upper = upper;
        this.literals = literals == null ? null : literals.clone();
        this.left = left;
        this.right = right;
    }

    static PredicateIr constant(
            CanonicalTableIdentity tableIdentity,
            boolean value) {
        return new PredicateIr(
                Kind.CONSTANT, tableIdentity, value, -1,
                null, null, null, null, null);
    }

    static PredicateIr compare(
            Kind kind,
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedLiteral literal) {
        requireLiteral(tableIdentity, fieldIndex, literal);
        return new PredicateIr(
                kind, tableIdentity, false, fieldIndex,
                literal, null, null, null, null);
    }

    static PredicateIr between(
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedLiteral lower,
            TypedLiteral upper) {
        requireLiteral(tableIdentity, fieldIndex, lower);
        requireLiteral(tableIdentity, fieldIndex, upper);
        return new PredicateIr(
                Kind.BETWEEN,
                tableIdentity,
                false,
                fieldIndex,
                lower,
                upper,
                null,
                null,
                null);
    }

    static PredicateIr in(
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedLiteral[] literals) {
        if (literals == null) throw new AssertionError("IN literals are missing");
        for (TypedLiteral literal : literals) {
            requireLiteral(tableIdentity, fieldIndex, literal);
        }
        return new PredicateIr(
                Kind.IN, tableIdentity, false, fieldIndex,
                null, null, literals, null, null);
    }

    static PredicateIr nullTest(
            Kind kind,
            CanonicalTableIdentity tableIdentity,
            int fieldIndex) {
        return new PredicateIr(
                kind, tableIdentity, false, fieldIndex,
                null, null, null, null, null);
    }

    static PredicateIr binary(
            Kind kind,
            PredicateIr left,
            PredicateIr right) {
        if (left == null || right == null
                || !left.tableIdentity.sameTable(right.tableIdentity)) {
            throw new AssertionError("canonical predicate owner drift");
        }
        return new PredicateIr(
                kind, left.tableIdentity, false, -1,
                null, null, null, left, right);
    }

    static PredicateIr not(PredicateIr source) {
        if (source == null) throw new AssertionError("canonical predicate is missing");
        return new PredicateIr(
                Kind.NOT, source.tableIdentity, false, -1,
                null, null, null, source, null);
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

    private static void requireLiteral(
            CanonicalTableIdentity tableIdentity,
            int fieldIndex,
            TypedLiteral literal) {
        if (tableIdentity == null || literal == null
                || !literal.tableIdentity().sameTable(tableIdentity)
                || literal.fieldIndex() != fieldIndex) {
            throw new AssertionError("canonical typed literal owner drift");
        }
    }
}
