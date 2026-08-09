package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Deliberately simple mapped-reference correctness oracle used only by tests. */
final class ReferenceMappedInterpreter {

    private ReferenceMappedInterpreter() {
    }

    static List<Object> toListForTesting(final MappedPlan<?> plan) {
        return QueryOperation.execute(plan.rows, new QueryOperation.BoundWork<List<Object>>() {
            @Override public long scratchBytes(BoundRowPlan bound) {
                return RowExecutionSupport.arrayBytes(
                        bound.root.size, 96L, bound.provenance);
            }

            @Override public List<Object> run(BoundRowPlan bound) {
                return evaluate(bound, plan);
            }
        });
    }

    static List<Object> valuesBound(BoundRowPlan bound, MappedPlan<?> plan) {
        return evaluate(bound, plan);
    }

    private static ArrayList<Object> evaluate(
            BoundRowPlan bound,
            MappedPlan<?> plan) {
        ArrayList<Object> values = new ArrayList<Object>();
        int firstStateful = nextStateful(plan.stages, 0);
        collect(bound, plan, 0, firstStateful, values);
        int position = firstStateful;
        while (position < plan.stages.size()) {
            MappedPlan.Stage stage = plan.stages.get(position);
            if (stage.kind == MappedPlan.Kind.DISTINCT) {
                referenceDistinct(bound, values);
            } else if (stage.kind == MappedPlan.Kind.SORTED) {
                canonicalSort(bound, values, stage.comparator);
            } else {
                throw new AssertionError("expected stateful mapped stage");
            }
            int next = nextStateful(plan.stages, position + 1);
            compact(bound, values, plan.stages, position + 1, next);
            position = next;
        }
        return values;
    }

    private static void collect(
            final BoundRowPlan bound,
            final MappedPlan<?> plan,
            final int from,
            final int to,
            final ArrayList<Object> output) {
        final long[] counters = new long[to - from];
        if (limitReached(plan.stages, from, to, counters)) return;
        ReferenceRowInterpreter.visit(bound, locator -> {
            if (limitReached(plan.stages, from, to, counters)) return false;
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, plan.rootMapper, true);
            requireDetached(bound, value);
            Step step = applySegment(
                    bound, value, plan.stages, from, to, counters);
            if (step.selected) output.add(step.value);
            return !step.stop && !limitReached(
                    plan.stages, from, to, counters);
        });
    }

    private static void compact(
            BoundRowPlan bound,
            ArrayList<Object> values,
            List<MappedPlan.Stage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (limitReached(stages, from, to, counters)) break;
            Step step = applySegment(
                    bound, values.get(input), stages, from, to, counters);
            if (step.selected) values.set(output++, step.value);
            if (step.stop) break;
        }
        while (values.size() > output) values.remove(values.size() - 1);
    }

    private static Step applySegment(
            BoundRowPlan bound,
            Object initial,
            List<MappedPlan.Stage> stages,
            int from,
            int to,
            long[] counters) {
        Object value = initial;
        for (int position = from; position < to; position++) {
            MappedPlan.Stage stage = stages.get(position);
            int counter = position - from;
            switch (stage.kind) {
                case FILTER:
                    if (!predicate(bound, stage, value)) {
                        return new Step(value, false,
                                limitReached(stages, from, to, counters));
                    }
                    break;
                case MAP:
                    value = map(bound, stage.mapper, value);
                    requireDetached(bound, value);
                    break;
                case SKIP:
                    if (counters[counter] < stage.count) {
                        counters[counter]++;
                        return new Step(value, false,
                                limitReached(stages, from, to, counters));
                    }
                    break;
                case LIMIT:
                    if (counters[counter] >= stage.count) {
                        return new Step(value, false, true);
                    }
                    counters[counter]++;
                    break;
                default:
                    throw new AssertionError("stateful mapped stage inside segment");
            }
        }
        return new Step(value, true, false);
    }

    private static void referenceDistinct(
            BoundRowPlan bound,
            ArrayList<Object> values) {
        CanonicalMappedDistinct seen = new CanonicalMappedDistinct(
                values.size(), bound);
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            Object candidate = values.get(input);
            if (seen.add(candidate)) values.set(output++, candidate);
        }
        while (values.size() > output) values.remove(values.size() - 1);
    }

    private static void canonicalSort(
            BoundRowPlan bound,
            ArrayList<Object> values,
            Comparator<Object> comparator) {
        Object[] array = values.toArray();
        CanonicalMappedSort.sort(bound, array, array.length, comparator);
        for (int index = 0; index < array.length; index++) {
            values.set(index, array[index]);
        }
    }

    private static boolean predicate(
            BoundRowPlan bound, MappedPlan.Stage stage, Object value) {
        try { return stage.predicate.test(value); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
    }

    private static Object map(
            BoundRowPlan bound, Function<Object, Object> mapper, Object value) {
        try { return mapper.apply(value); }
        catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
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

    private static boolean limitReached(
            List<MappedPlan.Stage> stages,
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

    private static int nextStateful(List<MappedPlan.Stage> stages, int from) {
        for (int position = from; position < stages.size(); position++) {
            MappedPlan.Kind kind = stages.get(position).kind;
            if (kind == MappedPlan.Kind.DISTINCT
                    || kind == MappedPlan.Kind.SORTED) return position;
        }
        return stages.size();
    }

    private static final class Step {
        final Object value;
        final boolean selected;
        final boolean stop;
        Step(Object value, boolean selected, boolean stop) {
            this.value = value; this.selected = selected; this.stop = stop;
        }
    }
}
