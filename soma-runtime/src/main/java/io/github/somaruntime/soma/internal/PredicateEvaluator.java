package io.github.somaruntime.soma.internal;

/** Canonical sequential meaning of the data-only predicate IR. */
final class PredicateEvaluator {

    private PredicateEvaluator() {
    }

    static boolean matches(
            GeneratedTableLayout layout,
            PredicateIr predicate,
            TableStateRoot root,
            int locator) {
        switch (predicate.kind) {
            case CONSTANT:
                return predicate.constant;
            case EQ:
                return layout.fieldEquals(
                        root.directory, locator, predicate.lower, predicate.fieldIndex);
            case NE:
                return !layout.fieldEquals(
                        root.directory, locator, predicate.lower, predicate.fieldIndex);
            case LT:
            case LE:
            case GT:
            case GE:
                if (layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex)) {
                    return false;
                }
                int compared = layout.compareStored(
                        root.directory, locator, predicate.lower, predicate.fieldIndex);
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
                        root.directory, locator, predicate.lower, predicate.fieldIndex) >= 0
                        && layout.compareStored(
                        root.directory, locator, predicate.upper, predicate.fieldIndex) <= 0;
            case IN:
                for (GeneratedProbe literal : predicate.literals) {
                    if (layout.fieldEquals(
                            root.directory, locator, literal, predicate.fieldIndex)) {
                        return true;
                    }
                }
                return false;
            case IS_NULL:
                return layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex);
            case IS_NOT_NULL:
                return !layout.storedFieldIsNull(
                        root.directory, locator, predicate.fieldIndex);
            case AND:
                return matches(layout, predicate.left, root, locator)
                        && matches(layout, predicate.right, root, locator);
            case OR:
                return matches(layout, predicate.left, root, locator)
                        || matches(layout, predicate.right, root, locator);
            case NOT:
                return !matches(layout, predicate.left, root, locator);
            default:
                throw new AssertionError("unknown predicate IR kind");
        }
    }
}
