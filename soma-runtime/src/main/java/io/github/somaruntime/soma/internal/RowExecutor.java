package io.github.somaruntime.soma.internal;

/** Selects sequential or bounded parallel physical row execution. */
final class RowExecutor {

    private RowExecutor() {
    }

    static long count(BoundRowPlan bound) {
        return OptimizedSequentialRowExecutor.count(prepare(bound));
    }

    static LongLocatorBuffer locators(BoundRowPlan bound) {
        return OptimizedSequentialRowExecutor.locators(prepare(bound));
    }

    static void visit(
            BoundRowPlan bound,
            OptimizedSequentialRowExecutor.LocatorVisitor visitor) {
        OptimizedSequentialRowExecutor.visit(prepare(bound), visitor);
    }

    private static BoundRowPlan prepare(BoundRowPlan bound) {
        return bound.logical.isParallel()
                ? ParallelRowScheduler.prepare(bound)
                : bound;
    }
}
