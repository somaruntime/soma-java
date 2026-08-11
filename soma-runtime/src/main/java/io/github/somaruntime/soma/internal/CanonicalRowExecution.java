package io.github.somaruntime.soma.internal;

/** Admitted operation-local state and specialized S1 execution kernels. */
final class CanonicalRowExecution {

    private CanonicalRowExecution() {
    }

    static long count(CanonicalRowExecutionFrame frame) {
        CanonicalRowPhysicalPlan plan = frame.plan;
        BoundCanonicalRowOperation bound = plan.normalized.bound;
        switch (plan.accessPath) {
            case TABLE_SCAN:
                return scan(frame, 0, bound.root.size);
            case KEY_LOOKUP:
                int locator = bound.root.key == null
                        ? -1
                        : bound.root.key.findUnique(
                                bound.root.directory, plan.literal);
                return locator >= 0 && matches(frame, locator) ? 1L : 0L;
            case INDEX_SELECTION:
            case INDEX_LOOKUP:
                return lookup(frame);
            default:
                throw new AssertionError("unknown Canonical access path");
        }
    }

    private static long scan(
            CanonicalRowExecutionFrame frame,
            int start,
            int end) {
        long result = 0L;
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        for (int locator = start; locator < end; locator++) {
            if (matches(frame, locator)) {
                result = CheckedLong.increment(
                        result, bound.operation, bound.provenance);
            }
        }
        return result;
    }

    private static long lookup(CanonicalRowExecutionFrame frame) {
        CanonicalRowPhysicalPlan plan = frame.plan;
        BoundCanonicalRowOperation bound = plan.normalized.bound;
        IdentityHashIndex index = bound.root.indexes[plan.indexOrdinal];
        long result = 0L;
        for (int locator = index.first(
                bound.root.directory, plan.literal, frame.indexCursor);
             locator >= 0;
             locator = index.next(frame.indexCursor)) {
            if (matches(frame, locator)) {
                result = CheckedLong.increment(
                        result, bound.operation, bound.provenance);
            }
        }
        return result;
    }

    private static boolean matches(
            CanonicalRowExecutionFrame frame,
            int locator) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        for (PredicateIr filter : frame.plan.normalized.filters) {
            if (!OptimizedPredicateEvaluator.matches(
                    bound.layout,
                    filter,
                    bound.root,
                    locator,
                    frame.membership)) return false;
        }
        return true;
    }
}

final class CanonicalRowExecutionFrame {
    final CanonicalRowPhysicalPlan plan;
    final IdentityHashIndex.Cursor indexCursor;
    final PredicateMembership membership;

    CanonicalRowExecutionFrame(CanonicalRowPhysicalPlan plan) {
        if (plan == null) throw new AssertionError("physical plan is missing");
        this.plan = plan;
        this.indexCursor = plan.accessPath == CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN
                || plan.accessPath == CanonicalRowPhysicalPlan.AccessPath.KEY_LOOKUP
                ? null
                : new IdentityHashIndex.Cursor();
        this.membership = plan.normalized.bound.canonical.inLiteralCount() == 0L
                ? null
                : PredicateMembership.preparePredicates(
                        plan.normalized.bound.layout,
                        plan.normalized.filters,
                        plan.normalized.bound.operation,
                        plan.normalized.bound.provenance);
    }
}

final class ReferenceCanonicalRowInterpreter {

    private ReferenceCanonicalRowInterpreter() {
    }

    static long count(BoundCanonicalRowOperation bound) {
        long result = 0L;
        for (int locator = 0; locator < bound.root.size; locator++) {
            if (!sourceMatches(bound, locator)) continue;
            boolean matched = true;
            for (PredicateIr filter : bound.canonical.filters) {
                if (!PredicateEvaluator.matches(
                        bound.layout, filter, bound.root, locator)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                result = CheckedLong.increment(
                        result, bound.operation, bound.provenance);
            }
        }
        return result;
    }

    private static boolean sourceMatches(
            BoundCanonicalRowOperation bound,
            int locator) {
        if (bound.canonical.sourceKind == CanonicalRowOperation.SourceKind.TABLE) {
            return true;
        }
        int field = bound.layout.indexFieldIndex(bound.canonical.indexOrdinal);
        return bound.layout.fieldEquals(
                bound.root.directory,
                locator,
                bound.canonical.sourceLiteral,
                field);
    }
}
