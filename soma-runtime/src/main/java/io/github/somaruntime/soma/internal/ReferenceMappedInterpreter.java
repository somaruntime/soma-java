package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaPredicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Correctness-first mapped interpreter over the same Bound Canonical semantics. */
final class ReferenceMappedInterpreter {

    private ReferenceMappedInterpreter() {
    }

    static List<Object> toListForTesting(
            final MappedPipelineCapture<?> frontend) {
        final CanonicalMappedOperation operation =
                CanonicalMappedLowering.operation(
                        frontend,
                        CanonicalMappedOperation.TerminalKind.TEST,
                        null,
                        null);
        return CanonicalQueryOperation.executeReferenceFamily(
                frontend.rows, operation.source,
                new CanonicalQueryOperation.ReferenceExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return RowExecutionSupport.arrayBytes(
                        bound.root.size, 96L, bound.provenance);
            }
        }, new CanonicalQueryOperation.ReferenceSourceWork<List<Object>>() {
            @Override public List<Object> run(
                    BoundCanonicalRowOperation bound,
                    IntLocatorBuffer source) {
                return evaluate(bound, operation, source);
            }
        });
    }

    static List<Object> valuesBound(
            BoundCanonicalRowOperation bound,
            CanonicalMappedOperation operation,
            IntLocatorBuffer source) {
        return evaluate(bound, operation, source);
    }

    private static ArrayList<Object> evaluate(
            BoundCanonicalRowOperation bound,
            CanonicalMappedOperation operation,
            IntLocatorBuffer source) {
        ArrayList<Object> values = new ArrayList<Object>();
        int firstStateful = nextStateful(operation.stages, 0);
        collect(bound, source, operation, 0, firstStateful, values);
        int position = firstStateful;
        while (position < operation.stages.size()) {
            CanonicalMappedStage stage = operation.stages.get(position);
            if (stage.kind == CanonicalMappedStage.Kind.DISTINCT) {
                referenceDistinct(bound, values);
            } else if (stage.kind == CanonicalMappedStage.Kind.SORTED) {
                canonicalSort(bound, values, stage.callback);
            } else {
                throw new AssertionError("expected stateful mapped stage");
            }
            int next = nextStateful(operation.stages, position + 1);
            compact(bound, values, operation.stages, position + 1, next);
            position = next;
        }
        return values;
    }

    private static void collect(
            final BoundCanonicalRowOperation bound,
            final IntLocatorBuffer source,
            final CanonicalMappedOperation operation,
            final int from,
            final int to,
            final ArrayList<Object> output) {
        final long[] counters = new long[to - from];
        if (limitReached(operation.stages, from, to, counters)) return;
        ReferenceCanonicalRowInterpreter.visit(bound, source, locator -> {
            if (limitReached(operation.stages, from, to, counters)) {
                return false;
            }
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, operation.rootMapper, true);
            MappedQueryOperation.requireDetached(bound, value);
            Step step = applySegment(
                    bound, value, operation.stages,
                    from, to, counters);
            if (step.selected) output.add(step.value);
            return !step.stop
                    && !limitReached(operation.stages, from, to, counters);
        });
    }

    private static void compact(
            BoundCanonicalRowOperation bound,
            ArrayList<Object> values,
            List<CanonicalMappedStage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            if (limitReached(stages, from, to, counters)) break;
            Step step = applySegment(
                    bound, values.get(input), stages,
                    from, to, counters);
            if (step.selected) values.set(output++, step.value);
            if (step.stop) break;
        }
        trim(values, output);
    }

    private static Step applySegment(
            BoundCanonicalRowOperation bound,
            Object initial,
            List<CanonicalMappedStage> stages,
            int from,
            int to,
            long[] counters) {
        Object value = initial;
        for (int position = from; position < to; position++) {
            CanonicalMappedStage stage = stages.get(position);
            int counter = position - from;
            switch (stage.kind) {
                case FILTER:
                    if (!predicate(bound, stage.callback, value)) {
                        return new Step(value, false,
                                limitReached(stages, from, to, counters));
                    }
                    break;
                case MAP:
                    value = map(bound, stage.callback, value);
                    MappedQueryOperation.requireDetached(bound, value);
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
                    throw new AssertionError(
                            "stateful mapped stage inside segment");
            }
        }
        return new Step(value, true, false);
    }

    private static void referenceDistinct(
            BoundCanonicalRowOperation bound,
            ArrayList<Object> values) {
        CanonicalMappedDistinct seen = new CanonicalMappedDistinct(
                values.size(), bound);
        int output = 0;
        for (int input = 0; input < values.size(); input++) {
            Object candidate = values.get(input);
            if (seen.add(candidate)) values.set(output++, candidate);
        }
        trim(values, output);
    }

    private static void canonicalSort(
            BoundCanonicalRowOperation bound,
            ArrayList<Object> values,
            HostCallbackHandle comparator) {
        Object[] array = values.toArray();
        CanonicalMappedSort.sort(
                bound, array, array.length, comparator);
        for (int index = 0; index < array.length; index++) {
            values.set(index, array[index]);
        }
    }

    private static boolean predicate(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object value) {
        CallbackExecutionScope.enter();
        try {
            return castPredicate(callback).test(value);
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
        }
    }

    private static Object map(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object value) {
        CallbackExecutionScope.enter();
        try {
            return castMapper(callback).apply(value);
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
        }
    }

    private static boolean limitReached(
            List<CanonicalMappedStage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            CanonicalMappedStage stage = stages.get(position);
            if (stage.kind == CanonicalMappedStage.Kind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static int nextStateful(
            List<CanonicalMappedStage> stages,
            int from) {
        for (int position = from; position < stages.size(); position++) {
            if (stages.get(position).isStateful()) return position;
        }
        return stages.size();
    }

    private static void trim(ArrayList<Object> values, int size) {
        while (values.size() > size) values.remove(values.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private static SomaPredicate<Object> castPredicate(
            HostCallbackHandle callback) {
        return (SomaPredicate<Object>) callback.callback;
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> castMapper(
            HostCallbackHandle callback) {
        return (Function<Object, Object>) callback.callback;
    }

    private static final class Step {
        final Object value;
        final boolean selected;
        final boolean stop;

        Step(Object value, boolean selected, boolean stop) {
            this.value = value;
            this.selected = selected;
            this.stop = stop;
        }
    }
}
