package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaRelationExpression;

final class LongExpression<R> implements SomaExpression<R> {

    private final GeneratedLongTable owner;
    private final Node node;

    LongExpression(GeneratedLongTable owner, Node node) {
        this.owner = owner;
        this.node = node;
    }

    @Override
    public SomaExpression<R> and(SomaExpression<R> other) {
        return combine(other, true);
    }

    @Override
    public SomaRelationExpression and(SomaRelationExpression other) {
        return combine(other, true);
    }

    @Override
    public SomaExpression<R> or(SomaExpression<R> other) {
        return combine(other, false);
    }

    @Override
    public SomaRelationExpression or(SomaRelationExpression other) {
        return combine(other, false);
    }

    @Override
    public SomaExpression<R> not() {
        return new LongExpression<R>(owner, new NotNode(node));
    }

    GeneratedLongTable owner() {
        return owner;
    }

    Node node() {
        return node;
    }

    @SuppressWarnings("unchecked")
    private SomaExpression<R> combine(Object other, boolean conjunction) {
        if (!(other instanceof LongExpression)) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    "expression is not issued by SOMA");
        }
        LongExpression<?> expression = (LongExpression<?>) other;
        if (expression.owner != owner) {
            throw SomaFailures.invalid(
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    "expression belongs to another Table");
        }
        Node combined = conjunction
                ? new AndNode(node, expression.node)
                : new OrNode(node, expression.node);
        return new LongExpression<R>(owner, combined);
    }

    interface Node {
        boolean matches(long[][] columns, int offset);
    }

    static final class ConstantNode implements Node {

        private final boolean value;

        ConstantNode(boolean value) {
            this.value = value;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return value;
        }
    }

    static final class CompareNode implements Node {

        static final int EQ = 0;
        static final int NE = 1;
        static final int LT = 2;
        static final int LE = 3;
        static final int GT = 4;
        static final int GE = 5;

        private final int fieldIndex;
        private final int operator;
        private final long literal;

        CompareNode(int fieldIndex, int operator, long literal) {
            this.fieldIndex = fieldIndex;
            this.operator = operator;
            this.literal = literal;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            long value = columns[fieldIndex][offset];
            switch (operator) {
                case EQ:
                    return value == literal;
                case NE:
                    return value != literal;
                case LT:
                    return value < literal;
                case LE:
                    return value <= literal;
                case GT:
                    return value > literal;
                case GE:
                    return value >= literal;
                default:
                    throw new AssertionError("unknown SOMA comparison");
            }
        }
    }

    static final class BetweenNode implements Node {

        private final int fieldIndex;
        private final long lower;
        private final long upper;

        BetweenNode(int fieldIndex, long lower, long upper) {
            this.fieldIndex = fieldIndex;
            this.lower = lower;
            this.upper = upper;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            long value = columns[fieldIndex][offset];
            return value >= lower && value <= upper;
        }
    }

    static final class InNode implements Node {

        private final int fieldIndex;
        private final long[] values;

        InNode(int fieldIndex, long[] values) {
            this.fieldIndex = fieldIndex;
            this.values = values;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return java.util.Arrays.binarySearch(values, columns[fieldIndex][offset]) >= 0;
        }
    }

    private static final class AndNode implements Node {

        private final Node left;
        private final Node right;

        private AndNode(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return left.matches(columns, offset) && right.matches(columns, offset);
        }
    }

    private static final class OrNode implements Node {

        private final Node left;
        private final Node right;

        private OrNode(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return left.matches(columns, offset) || right.matches(columns, offset);
        }
    }

    private static final class NotNode implements Node {

        private final Node nested;

        private NotNode(Node nested) {
            this.nested = nested;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return !nested.matches(columns, offset);
        }
    }
}
