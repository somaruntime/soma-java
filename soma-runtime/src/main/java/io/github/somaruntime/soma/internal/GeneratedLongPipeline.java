package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal one-shot linked pipeline used by generated row streams. */
public final class GeneratedLongPipeline {

    private final GeneratedLongTable table;
    private final LongExpression.Node predicate;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedLongPipeline(
            GeneratedLongTable table,
            LongExpression.Node predicate) {
        this.table = table;
        this.predicate = predicate;
    }

    public GeneratedLongPipeline filter(SomaExpression<?> expression) {
        LongExpression.Node additional = table.requireOwnedExpression(expression);
        claim();
        return new GeneratedLongPipeline(
                table,
                new AndPredicate(predicate, additional));
    }

    public long count() {
        claim();
        return table.executeCount(predicate);
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    SomaOperation.QUERY,
                    "linked pipeline was already consumed",
                    new Object());
        }
    }

    private static final class AndPredicate implements LongExpression.Node {

        private final LongExpression.Node left;
        private final LongExpression.Node right;

        private AndPredicate(LongExpression.Node left, LongExpression.Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(long[][] columns, int offset) {
            return left.matches(columns, offset) && right.matches(columns, offset);
        }
    }
}
