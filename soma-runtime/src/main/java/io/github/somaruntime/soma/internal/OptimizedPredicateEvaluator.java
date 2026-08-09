package io.github.somaruntime.soma.internal;

/** Optimized typed predicate traversal with operation-local IN membership. */
final class OptimizedPredicateEvaluator {

    private OptimizedPredicateEvaluator() {
    }

    static boolean matches(
            GeneratedTableLayout layout,
            PredicateIr predicate,
            TableStateRoot root,
            long locator,
            PredicateMembership membership) {
        switch (predicate.kind) {
            case CONSTANT:
                return predicate.constant;
            case EQ:
                return layout.fieldEquals(
                        root.directory, locator, predicate.lower,
                        predicate.fieldIndex);
            case NE:
                return !layout.fieldEquals(
                        root.directory, locator, predicate.lower,
                        predicate.fieldIndex);
            case LT:
            case LE:
            case GT:
            case GE:
                if (layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex)) {
                    return false;
                }
                int compared = layout.compareStored(
                        root.directory, locator, predicate.lower,
                        predicate.fieldIndex);
                return predicate.kind == PredicateIr.Kind.LT ? compared < 0
                        : predicate.kind == PredicateIr.Kind.LE ? compared <= 0
                        : predicate.kind == PredicateIr.Kind.GT ? compared > 0
                        : compared >= 0;
            case BETWEEN:
                if (layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex)) {
                    return false;
                }
                return layout.compareStored(
                        root.directory, locator, predicate.lower,
                        predicate.fieldIndex) >= 0
                        && layout.compareStored(
                        root.directory, locator, predicate.upper,
                        predicate.fieldIndex) <= 0;
            case IN:
                if (membership == null) {
                    throw new AssertionError("missing optimized IN membership");
                }
                return membership.contains(
                        predicate, root.directory, locator);
            case IS_NULL:
                return layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex);
            case IS_NOT_NULL:
                return !layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex);
            case AND:
                return matches(layout, predicate.left, root, locator, membership)
                        && matches(layout, predicate.right, root, locator, membership);
            case OR:
                return matches(layout, predicate.left, root, locator, membership)
                        || matches(layout, predicate.right, root, locator, membership);
            case NOT:
                return !matches(layout, predicate.left, root, locator, membership);
            default:
                throw new AssertionError("unknown optimized predicate kind");
        }
    }
}
