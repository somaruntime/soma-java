package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicBoolean;

/** Linked lazy one-shot row-selection pipeline used by the I2 minimum query carrier. */
public final class GeneratedPipeline {

    private final GeneratedTable owner;
    private final GeneratedExpression.Node predicate;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedPipeline(GeneratedTable owner, GeneratedExpression.Node predicate) {
        this.owner = owner;
        this.predicate = predicate;
    }

    public GeneratedPipeline filter(SomaExpression<?> expression) {
        GeneratedExpression.Node next = owner.requireOwnedExpression(expression);
        claim();
        final GeneratedExpression.Node left = predicate;
        final GeneratedExpression.Node right = next;
        return new GeneratedPipeline(owner, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return left.matches(root, locator) && right.matches(root, locator);
            }
        });
    }

    public long count() {
        claim();
        return owner.executeCount(predicate);
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    SomaOperation.QUERY,
                    "linked pipeline has already been consumed",
                    new Object());
        }
    }
}
