package io.github.somaruntime.soma.internal;

import java.util.List;
import java.util.Optional;

/** Thin Java-facade lowering adapter; lifecycle and execution are Canonical-owned. */
final class QueryOperation {

    private QueryOperation() {
    }

    static long optimizedCount(LogicalRowPlan frontend) {
        CanonicalRowOperation canonical = lower(
                frontend, CanonicalRowOperation.TerminalKind.COUNT, null);
        return CanonicalQueryOperation.optimizedCount(
                runtime(frontend), canonical);
    }

    static long referenceCountForTesting(LogicalRowPlan frontend) {
        CanonicalRowOperation canonical = lower(
                frontend, CanonicalRowOperation.TerminalKind.COUNT, null);
        return CanonicalQueryOperation.referenceCountForTesting(
                runtime(frontend), canonical);
    }

    static boolean anyMatch(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowPredicate predicate) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.ANY_MATCH,
                HostCallbackHandle.rowPredicate(
                        frontend.owner().logicalIdentity(), predicate));
        return CanonicalQueryOperation.anyMatch(runtime(frontend), canonical);
    }

    static boolean allMatch(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowPredicate predicate) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.ALL_MATCH,
                HostCallbackHandle.rowPredicate(
                        frontend.owner().logicalIdentity(), predicate));
        return CanonicalQueryOperation.allMatch(runtime(frontend), canonical);
    }

    static boolean noneMatch(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowPredicate predicate) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.NONE_MATCH,
                HostCallbackHandle.rowPredicate(
                        frontend.owner().logicalIdentity(), predicate));
        return !CanonicalQueryOperation.anyMatch(runtime(frontend), canonical);
    }

    static <R> Optional<R> findFirst(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowMapper<R> materializer) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.FIND_FIRST,
                HostCallbackHandle.rowMapper(
                        frontend.owner().logicalIdentity(), materializer));
        return CanonicalQueryOperation.findFirst(runtime(frontend), canonical);
    }

    static void forEach(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowAction action) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.FOR_EACH,
                HostCallbackHandle.rowAction(
                        frontend.owner().logicalIdentity(), action));
        CanonicalQueryOperation.forEach(runtime(frontend), canonical);
    }

    static <R> List<R> toList(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowMapper<R> materializer) {
        return toList(
                frontend,
                materializer,
                frontend.owner().layout().detachedRowEstimateBytes());
    }

    static <R> List<R> toList(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowMapper<R> materializer,
            long detachedElementEstimateBytes) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.TO_LIST,
                HostCallbackHandle.rowMapper(
                        frontend.owner().logicalIdentity(), materializer));
        return CanonicalQueryOperation.toList(
                runtime(frontend), canonical, detachedElementEstimateBytes);
    }

    static <R> R[] toArray(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowMapper<R> materializer,
            Class<R> componentType) {
        return toArray(
                frontend,
                materializer,
                componentType,
                frontend.owner().layout().detachedRowEstimateBytes());
    }

    static <R> R[] toArray(
            LogicalRowPlan frontend,
            GeneratedCallbacks.RowMapper<R> materializer,
            Class<R> componentType,
            long detachedElementEstimateBytes) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.TO_ARRAY,
                HostCallbackHandle.rowMapper(
                        frontend.owner().logicalIdentity(), materializer));
        return CanonicalQueryOperation.toArray(
                runtime(frontend), canonical, componentType,
                detachedElementEstimateBytes);
    }

    static String explain(LogicalRowPlan frontend) {
        CanonicalRowOperation canonical = lower(
                frontend, CanonicalRowOperation.TerminalKind.EXPLAIN, null);
        return CanonicalQueryOperation.explain(runtime(frontend), canonical);
    }

    static long[] optimizedLocatorsForTesting(LogicalRowPlan frontend) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.LOCATORS_TEST,
                null);
        return copy(CanonicalQueryOperation.optimizedLocators(
                runtime(frontend), canonical));
    }

    static long[] referenceLocatorsForTesting(LogicalRowPlan frontend) {
        CanonicalRowOperation canonical = lower(
                frontend,
                CanonicalRowOperation.TerminalKind.LOCATORS_TEST,
                null);
        return copy(CanonicalQueryOperation.referenceLocatorsForTesting(
                runtime(frontend), canonical));
    }

    private static CanonicalRowOperation lower(
            LogicalRowPlan frontend,
            CanonicalRowOperation.TerminalKind terminal,
            HostCallbackHandle callback) {
        CanonicalRowOperation result = CanonicalRowLowering.operation(
                frontend.owner(), frontend, terminal, callback);
        if (result == null) {
            throw new AssertionError("Java Row facade failed Canonical lowering");
        }
        return result;
    }

    private static CanonicalRowRuntimeSource runtime(LogicalRowPlan frontend) {
        return CanonicalRowRuntimeSource.frontend(frontend);
    }

    private static long[] copy(IntLocatorBuffer source) {
        long[] result = new long[source.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = source.get(index);
        }
        return result;
    }
}
