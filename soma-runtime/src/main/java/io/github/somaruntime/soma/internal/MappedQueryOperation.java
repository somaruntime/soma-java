package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaPredicate;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Canonical arbitrary-reference projection execution. */
final class MappedQueryOperation {

    private MappedQueryOperation() {
    }

    static long count(final MappedPlan<?> plan) {
        return terminal(plan, false, new MappedTerminal<Long>() {
            @Override public Long run(BoundRowPlan bound, MappedPlan<?> ignored) {
                final long[] count = new long[1];
                visitBound(bound, plan, value -> { count[0]++; return true; });
                return count[0];
            }
        });
    }

    static boolean anyMatch(final MappedPlan<?> plan, final SomaPredicate<Object> predicate) {
        return terminal(plan, false, new MappedTerminal<Boolean>() {
            @Override public Boolean run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                final boolean[] found = new boolean[1];
                visitBound(bound, plan, value -> {
                    if (callbackPredicate(bound, predicate, value)) {
                        found[0] = true; return false;
                    }
                    return true;
                });
                return found[0];
            }
        });
    }

    static boolean allMatch(final MappedPlan<?> plan, final SomaPredicate<Object> predicate) {
        return terminal(plan, false, new MappedTerminal<Boolean>() {
            @Override public Boolean run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                final boolean[] all = new boolean[] {true};
                visitBound(bound, plan, value -> {
                    if (!callbackPredicate(bound, predicate, value)) {
                        all[0] = false; return false;
                    }
                    return true;
                });
                return all[0];
            }
        });
    }

    static Optional<Object> findFirst(final MappedPlan<?> plan) {
        return terminal(plan, false, new MappedTerminal<Optional<Object>>() {
            @Override public Optional<Object> run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                final Object[] first = new Object[1]; final boolean[] present = new boolean[1];
                visitBound(bound, plan, value -> {
                    first[0] = value; present[0] = true; return false;
                });
                if (!present[0]) return Optional.empty();
                if (first[0] == null) {
                    throw SomaFailures.failure(
                            SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                            SomaOperation.QUERY,
                            "selected null cannot be represented by Java Optional",
                            bound.provenance);
                }
                return Optional.of(first[0]);
            }
        });
    }

    static Optional<Object> extremum(
            final MappedPlan<?> plan,
            final Comparator<Object> comparator,
            final boolean maximum) {
        return terminal(plan, false, new MappedTerminal<Optional<Object>>() {
            @Override public Optional<Object> run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                final Object[] result = new Object[1]; final boolean[] present = new boolean[1];
                visitBound(bound, plan, value -> {
                    if (!present[0]) { result[0] = value; present[0] = true; }
                    else {
                        int compared = callbackCompare(bound, comparator, value, result[0]);
                        if (maximum ? compared > 0 : compared < 0) result[0] = value;
                    }
                    return true;
                });
                if (!present[0]) return Optional.empty();
                if (result[0] == null) {
                    throw SomaFailures.failure(
                            SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                            SomaOperation.QUERY,
                            "selected null cannot be represented by Java Optional",
                            bound.provenance);
                }
                return Optional.of(result[0]);
            }
        });
    }

    static void forEach(
            final MappedPlan<?> plan,
            final Consumer<Object> action) {
        terminal(plan, false, new MappedTerminal<Object>() {
            @Override public Object run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                visitBound(bound, plan, value -> {
                    callbackAction(bound, action, value); return true;
                });
                return null;
            }
        });
    }

    static List<Object> toList(final MappedPlan<?> plan) {
        return terminal(plan, true, new MappedTerminal<List<Object>>() {
            @Override public List<Object> run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                final ArrayList<Object> result = new ArrayList<Object>(
                        RowExecutionSupport.arrayLength(
                                plan.outputUpperBound(bound.root.size),
                                bound.provenance));
                visitBound(bound, plan, value -> { result.add(value); return true; });
                return result;
            }
        });
    }

    static Object toArray(final MappedPlan<?> plan, final Class<?> componentType) {
        return terminal(plan, true, new MappedTerminal<Object>() {
            @Override public Object run(final BoundRowPlan bound, MappedPlan<?> ignored) {
                int upper = RowExecutionSupport.arrayLength(
                        plan.outputUpperBound(bound.root.size), bound.provenance);
                Object staging = Array.newInstance(componentType, upper);
                final int[] size = new int[1];
                visitBound(bound, plan, value -> {
                    if (value != null && !componentType.isInstance(value)) {
                        throw SomaFailures.failure(
                                SomaFailureCode.INVALID_ARGUMENT,
                                SomaOperation.QUERY,
                                "mapped value is incompatible with requested array component",
                                bound.provenance);
                    }
                    Array.set(staging, size[0]++, value);
                    return true;
                });
                Object result = Array.newInstance(componentType, size[0]);
                System.arraycopy(staging, 0, result, 0, size[0]);
                return result;
            }
        });
    }

    static String explain(final MappedPlan<?> plan) {
        return QueryOperation.execute(
                plan.rows, new QueryOperation.BoundWork<String>() {
            @Override public long scratchBytes(BoundRowPlan bound) {
                return 0L;
            }
            @Override public String run(BoundRowPlan bound) {
                StringBuilder result = new StringBuilder(
                        QueryOperation.explainBound(plan.rows, bound));
                result.append(" mappedStages=[");
                for (int index = 0; index < plan.stages.size(); index++) {
                    if (index != 0) result.append(',');
                    result.append(plan.stages.get(index).kind);
                }
                return result.append("] mappedCallbackBarrier=true")
                        .append(" estimatedMappedTemporaryPeakBytes=")
                        .append(estimatedExecutionScratch(bound, plan, false))
                        .toString();
            }
        });
    }

    private static long estimatedExecutionScratch(
            BoundRowPlan bound,
            MappedPlan<?> plan,
            boolean materialized) {
        long result = QueryOperation.rowExecutionScratch(bound);
        if (plan.hasOwnStatefulStage()) {
            result = QueryOperation.addScratch(
                    result, mappedStatefulScratch(bound), bound.provenance);
        }
        if (materialized) {
            result = QueryOperation.addScratch(
                    result,
                    RowExecutionSupport.arrayBytes(
                            plan.outputUpperBound(bound.root.size),
                            64L,
                            bound.provenance),
                    bound.provenance);
        }
        return result;
    }

    static <T> T terminal(
            final MappedPlan<?> plan,
            final boolean materialized,
            final MappedTerminal<T> terminal) {
        return QueryOperation.execute(plan.rows, new QueryOperation.BoundWork<T>() {
            @Override public long scratchBytes(BoundRowPlan bound) {
                return estimatedExecutionScratch(bound, plan, materialized);
            }
            @Override public T run(BoundRowPlan bound) {
                return terminal.run(bound, plan);
            }
        });
    }

    static void visitBound(
            BoundRowPlan bound,
            MappedPlan<?> plan,
            MappedVisitor visitor) {
        if (plan.hasOwnStatefulStage()) {
            ObjectBuffer values = values(bound, plan);
            for (int index = 0; index < values.size; index++) {
                if (!visitor.visit(values.values[index])) return;
            }
            return;
        }
        final long[] counters = new long[plan.stages.size()];
        RowExecutor.visit(bound, locator -> {
            if (mappedLimitReached(plan, counters)) return false;
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, plan.rootMapper, true);
            requireDetached(bound, value);
            for (int index = 0; index < plan.stages.size(); index++) {
                MappedPlan.Stage stage = plan.stages.get(index);
                switch (stage.kind) {
                    case FILTER:
                        if (!callbackPredicate(bound, stage.predicate, value)) {
                            return !mappedLimitReached(plan, counters);
                        }
                        break;
                    case MAP:
                        value = callbackMap(bound, stage.mapper, value);
                        requireDetached(bound, value);
                        break;
                    case SKIP:
                        if (counters[index] < stage.count) {
                            counters[index]++; return true;
                        }
                        break;
                    case LIMIT:
                        if (counters[index] >= stage.count) return false;
                        counters[index]++;
                        break;
                    default:
                        throw new AssertionError("stateful stage in mapped streaming path");
                }
            }
            return visitor.visit(value);
        });
    }

    static long mappedStatefulScratch(BoundRowPlan bound) {
        return RowExecutionSupport.arrayBytes(
                bound.root.size, 64L, bound.provenance);
    }

    private static boolean mappedLimitReached(
            MappedPlan<?> plan,
            long[] counters) {
        for (int index = 0; index < plan.stages.size(); index++) {
            MappedPlan.Stage stage = plan.stages.get(index);
            if (stage.kind == MappedPlan.Kind.LIMIT
                    && counters[index] >= stage.count) return true;
        }
        return false;
    }

    private static ObjectBuffer values(BoundRowPlan bound, MappedPlan<?> plan) {
        ObjectBuffer values = new ObjectBuffer(bound.root.size, bound.provenance);
        int firstStateful = mappedNextStateful(plan.stages, 0);
        collectMappedSegment(bound, plan, 0, firstStateful, values);
        int position = firstStateful;
        while (position < plan.stages.size()) {
            MappedPlan.Stage stage = plan.stages.get(position);
            switch (stage.kind) {
                case DISTINCT: distinct(bound, values); break;
                case SORTED: stableSort(bound, values, stage.comparator); break;
                default: throw new AssertionError("expected stateful mapped stage");
            }
            int next = mappedNextStateful(plan.stages, position + 1);
            compactMappedSegment(
                    bound, values, plan.stages, position + 1, next);
            position = next;
        }
        return values;
    }

    private static void collectMappedSegment(
            final BoundRowPlan bound,
            final MappedPlan<?> plan,
            final int from,
            final int to,
            final ObjectBuffer output) {
        final long[] counters = new long[to - from];
        RowExecutor.visit(bound, locator -> {
            if (mappedSegmentLimitReached(plan.stages, from, to, counters)) {
                return false;
            }
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, plan.rootMapper, true);
            requireDetached(bound, value);
            for (int position = from; position < to; position++) {
                MappedPlan.Stage stage = plan.stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!callbackPredicate(bound, stage.predicate, value)) {
                            return !mappedSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case MAP:
                        value = callbackMap(bound, stage.mapper, value);
                        requireDetached(bound, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            return !mappedSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) return false;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful mapped stage inside segment");
                }
            }
            output.add(value);
            return !mappedSegmentLimitReached(
                    plan.stages, from, to, counters);
        });
    }

    private static void compactMappedSegment(
            BoundRowPlan bound,
            ObjectBuffer values,
            java.util.List<MappedPlan.Stage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        inputLoop:
        for (int input = 0; input < values.size; input++) {
            if (mappedSegmentLimitReached(stages, from, to, counters)) break;
            Object value = values.values[input];
            for (int position = from; position < to; position++) {
                MappedPlan.Stage stage = stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!callbackPredicate(bound, stage.predicate, value)) {
                            continue inputLoop;
                        }
                        break;
                    case MAP:
                        value = callbackMap(bound, stage.mapper, value);
                        requireDetached(bound, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            continue inputLoop;
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) break inputLoop;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful mapped stage inside segment");
                }
            }
            values.values[output++] = value;
        }
        values.clearTail(output);
    }

    private static boolean mappedSegmentLimitReached(
            java.util.List<MappedPlan.Stage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            MappedPlan.Stage stage = stages.get(position);
            if (stage.kind == MappedPlan.Kind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static int mappedNextStateful(
            java.util.List<MappedPlan.Stage> stages,
            int from) {
        for (int position = from; position < stages.size(); position++) {
            MappedPlan.Kind kind = stages.get(position).kind;
            if (kind == MappedPlan.Kind.DISTINCT
                    || kind == MappedPlan.Kind.SORTED) return position;
        }
        return stages.size();
    }

    private static void compactFilter(
            BoundRowPlan bound, ObjectBuffer values, SomaPredicate<Object> predicate) {
        int output = 0;
        for (int input = 0; input < values.size; input++) {
            Object value = values.values[input];
            if (callbackPredicate(bound, predicate, value)) values.values[output++] = value;
        }
        values.clearTail(output);
    }

    private static void map(
            BoundRowPlan bound, ObjectBuffer values, java.util.function.Function<Object, Object> mapper) {
        for (int index = 0; index < values.size; index++) {
            Object value = callbackMap(bound, mapper, values.values[index]);
            requireDetached(bound, value); values.values[index] = value;
        }
    }

    private static void distinct(BoundRowPlan bound, ObjectBuffer values) {
        CanonicalMappedDistinct seen = new CanonicalMappedDistinct(
                values.size, bound);
        int output = 0;
        for (int input = 0; input < values.size; input++) {
            Object value = values.values[input];
            if (seen.add(value)) values.values[output++] = value;
        }
        values.clearTail(output);
    }

    private static void stableSort(
            BoundRowPlan bound, ObjectBuffer values, Comparator<Object> comparator) {
        CanonicalMappedSort.sort(
                bound, values.values, values.size, comparator);
    }

    private static boolean callbackPredicate(
            BoundRowPlan bound, SomaPredicate<Object> predicate, Object value) {
        CallbackExecutionScope.enter();
        try { return predicate.test(value); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance);
        } finally { CallbackExecutionScope.exit(); }
    }

    private static Object callbackMap(
            BoundRowPlan bound, java.util.function.Function<Object, Object> mapper, Object value) {
        CallbackExecutionScope.enter();
        try { return mapper.apply(value); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance);
        } finally { CallbackExecutionScope.exit(); }
    }

    static int callbackCompare(
            BoundRowPlan bound, Comparator<Object> comparator, Object left, Object right) {
        CallbackExecutionScope.enter();
        try { return comparator.compare(left, right); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance);
        } finally { CallbackExecutionScope.exit(); }
    }

    private static void callbackAction(
            BoundRowPlan bound, Consumer<Object> action, Object value) {
        CallbackExecutionScope.enter();
        try { action.accept(value); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance);
        } finally { CallbackExecutionScope.exit(); }
    }

    private static void requireDetached(BoundRowPlan bound, Object value) {
        if (bound.logical.owner().isBorrowedQueryView(value)) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.QUERY,
                    "mapper returned a borrowed SOMA view",
                    bound.provenance);
        }
    }

    interface MappedVisitor { boolean visit(Object value); }
    interface MappedTerminal<T> { T run(BoundRowPlan bound, MappedPlan<?> plan); }

    private static final class ObjectBuffer {
        final Object[] values; int size;
        ObjectBuffer(long upper, Object provenance) {
            values = new Object[RowExecutionSupport.arrayLength(upper, provenance)];
        }
        void add(Object value) { values[size++] = value; }
        void clearTail(int next) { Arrays.fill(values, next, size, null); size = next; }
        void skip(long count) {
            if (count >= size) { clearTail(0); return; }
            int offset = (int) count; int remaining = size - offset;
            System.arraycopy(values, offset, values, 0, remaining); clearTail(remaining);
        }
        void limit(long count) { if (count < size) clearTail((int) count); }
    }
}
