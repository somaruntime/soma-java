package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaRelationExpression;

/** Internal immutable node for processor-issued expressions. */
public final class SomaExpressionNode<T> implements SomaExpression<T> {
    /** Evaluation callback owned by generated code, not application SPI. */
    public interface Evaluator<T> {
        boolean test(T value);
    }

    private final Evaluator<T> evaluator;
    private final Object owner;

    public SomaExpressionNode(Evaluator<T> evaluator) {
        this(evaluator, null);
    }

    public SomaExpressionNode(Evaluator<T> evaluator, Object owner) {
        if (evaluator == null) {
            throw new NullPointerException("evaluator");
        }
        this.evaluator = evaluator;
        this.owner = owner;
    }

    public boolean ownedBy(Object candidate) {
        return owner != null && owner == candidate;
    }

    public boolean evaluate(T value) {
        return evaluator.test(value);
    }

    @Override
    public SomaRelationExpression and(SomaRelationExpression other) {
        if (!(other instanceof SomaExpressionNode)) {
            throw SomaRuntimeAccess.invalidExpression();
        }
        @SuppressWarnings("unchecked")
        final SomaExpressionNode<T> right = (SomaExpressionNode<T>) other;
        if (owner == null || owner != right.owner) {
            throw SomaRuntimeAccess.invalidExpression();
        }
        return new SomaExpressionNode<T>(new Evaluator<T>() {
            @Override
            public boolean test(T value) {
                return evaluate(value) && right.evaluate(value);
            }
        }, owner);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SomaExpression<T> and(SomaExpression<T> other) {
        return (SomaExpression<T>) and((SomaRelationExpression) other);
    }

    @Override
    public SomaRelationExpression or(SomaRelationExpression other) {
        if (!(other instanceof SomaExpressionNode)) {
            throw SomaRuntimeAccess.invalidExpression();
        }
        @SuppressWarnings("unchecked")
        final SomaExpressionNode<T> right = (SomaExpressionNode<T>) other;
        if (owner == null || owner != right.owner) {
            throw SomaRuntimeAccess.invalidExpression();
        }
        return new SomaExpressionNode<T>(new Evaluator<T>() {
            @Override
            public boolean test(T value) {
                return evaluate(value) || right.evaluate(value);
            }
        }, owner);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SomaExpression<T> or(SomaExpression<T> other) {
        return (SomaExpression<T>) or((SomaRelationExpression) other);
    }

    @Override
    public SomaExpression<T> not() {
        return new SomaExpressionNode<T>(new Evaluator<T>() {
            @Override
            public boolean test(T value) {
                return !evaluate(value);
            }
        }, owner);
    }
}
