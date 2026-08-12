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
import java.util.function.Function;

/** Specialized arbitrary-reference execution over one bound Canonical source. */
final class MappedQueryOperation {

    private MappedQueryOperation() {
    }

    static long count(final MappedPipelineCapture<?> frontend) {
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.COUNT,
                null, null, false, new MappedTerminal<Long>() {
            @Override public Long run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                final long[] count = new long[1];
                visit(frame, operation, value -> {
                    count[0] = CheckedLong.increment(
                            count[0], SomaOperation.QUERY,
                            frame.plan.normalized.bound.provenance);
                    return true;
                });
                return count[0];
            }
        });
    }

    static boolean anyMatch(
            final MappedPipelineCapture<?> frontend,
            SomaPredicate<Object> predicate) {
        HostCallbackHandle callback = mappedCallback(
                frontend, HostCallbackHandle.Kind.MAPPED_PREDICATE, predicate);
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.MATCH,
                callback, null, false, new MappedTerminal<Boolean>() {
            @Override public Boolean run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                final boolean[] found = new boolean[1];
                visit(frame, operation, value -> {
                    if (callbackPredicate(
                            frame.plan.normalized.bound,
                            operation.terminalCallback,
                            value)) {
                        found[0] = true;
                        return false;
                    }
                    return true;
                });
                return found[0];
            }
        });
    }

    static boolean allMatch(
            final MappedPipelineCapture<?> frontend,
            SomaPredicate<Object> predicate) {
        HostCallbackHandle callback = mappedCallback(
                frontend, HostCallbackHandle.Kind.MAPPED_PREDICATE, predicate);
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.MATCH,
                callback, null, false, new MappedTerminal<Boolean>() {
            @Override public Boolean run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                final boolean[] all = new boolean[] {true};
                visit(frame, operation, value -> {
                    if (!callbackPredicate(
                            frame.plan.normalized.bound,
                            operation.terminalCallback,
                            value)) {
                        all[0] = false;
                        return false;
                    }
                    return true;
                });
                return all[0];
            }
        });
    }

    static Optional<Object> findFirst(
            final MappedPipelineCapture<?> frontend) {
        return terminal(frontend,
                CanonicalMappedOperation.TerminalKind.FIND_FIRST,
                null, null, false,
                new MappedTerminal<Optional<Object>>() {
            @Override public Optional<Object> run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                final Object[] first = new Object[1];
                final boolean[] present = new boolean[1];
                visit(frame, operation, value -> {
                    first[0] = value;
                    present[0] = true;
                    return false;
                });
                if (!present[0]) return Optional.empty();
                if (first[0] == null) {
                    throw SomaFailures.failure(
                            SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                            SomaOperation.QUERY,
                            "selected null cannot be represented by Java Optional",
                            frame.plan.normalized.bound.provenance);
                }
                return Optional.of(first[0]);
            }
        });
    }

    static Optional<Object> extremum(
            final MappedPipelineCapture<?> frontend,
            Comparator<Object> comparator,
            final boolean maximum) {
        HostCallbackHandle callback = mappedCallback(
                frontend, HostCallbackHandle.Kind.MAPPED_COMPARATOR, comparator);
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.EXTREMUM,
                callback, null, false,
                new MappedTerminal<Optional<Object>>() {
            @Override public Optional<Object> run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                final Object[] result = new Object[1];
                final boolean[] present = new boolean[1];
                visit(frame, operation, value -> {
                    if (!present[0]) {
                        result[0] = value;
                        present[0] = true;
                    } else {
                        int compared = callbackCompare(
                                frame.plan.normalized.bound,
                                operation.terminalCallback,
                                value,
                                result[0]);
                        if (maximum ? compared > 0 : compared < 0) {
                            result[0] = value;
                        }
                    }
                    return true;
                });
                if (!present[0]) return Optional.empty();
                if (result[0] == null) {
                    throw SomaFailures.failure(
                            SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                            SomaOperation.QUERY,
                            "selected null cannot be represented by Java Optional",
                            frame.plan.normalized.bound.provenance);
                }
                return Optional.of(result[0]);
            }
        });
    }

    static void forEach(
            final MappedPipelineCapture<?> frontend,
            Consumer<Object> action) {
        HostCallbackHandle callback = mappedCallback(
                frontend, HostCallbackHandle.Kind.MAPPED_ACTION, action);
        terminal(frontend, CanonicalMappedOperation.TerminalKind.FOR_EACH,
                callback, null, false, new MappedTerminal<Object>() {
            @Override public Object run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                visit(frame, operation, value -> {
                    callbackAction(
                            frame.plan.normalized.bound,
                            operation.terminalCallback,
                            value);
                    return true;
                });
                return null;
            }
        });
    }

    static List<Object> toList(
            final MappedPipelineCapture<?> frontend) {
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.MATERIALIZE,
                null, null, true, new MappedTerminal<List<Object>>() {
            @Override public List<Object> run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                ArrayList<Object> result = new ArrayList<Object>(
                        RowExecutionSupport.arrayLength(
                                operation.outputUpperBound(bound),
                                bound.provenance));
                visit(frame, operation, value -> {
                    result.add(value);
                    return true;
                });
                return result;
            }
        });
    }

    static Object toArray(
            final MappedPipelineCapture<?> frontend,
            final Class<?> componentType) {
        return terminal(frontend, CanonicalMappedOperation.TerminalKind.MATERIALIZE,
                null, componentType, true, new MappedTerminal<Object>() {
            @Override public Object run(
                    CanonicalRowExecutionFrame frame,
                    CanonicalMappedOperation operation) {
                BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
                int upper = RowExecutionSupport.arrayLength(
                        operation.outputUpperBound(bound), bound.provenance);
                Object staging = Array.newInstance(
                        operation.arrayComponentType, upper);
                final int[] size = new int[1];
                visit(frame, operation, value -> {
                    if (value != null
                            && !operation.arrayComponentType.isInstance(value)) {
                        throw SomaFailures.failure(
                                SomaFailureCode.INVALID_ARGUMENT,
                                SomaOperation.QUERY,
                                "mapped value is incompatible with requested array component",
                                bound.provenance);
                    }
                    Array.set(staging, size[0]++, value);
                    return true;
                });
                Object result = Array.newInstance(
                        operation.arrayComponentType, size[0]);
                System.arraycopy(staging, 0, result, 0, size[0]);
                return result;
            }
        });
    }

    static String explain(final MappedPipelineCapture<?> frontend) {
        final CanonicalMappedOperation operation = CanonicalMappedLowering.operation(
                frontend,
                CanonicalMappedOperation.TerminalKind.EXPLAIN,
                null,
                null);
        return CanonicalQueryOperation.inspectFamily(
                frontend.rows.owner(), operation.source,
                new CanonicalQueryOperation.ReferenceWork<String>() {
            @Override public String run(BoundCanonicalRowOperation bound) {
                long mapped = estimatedExecutionScratch(
                        bound, operation, false);
                CanonicalRowPhysicalPlan physical =
                        CanonicalQueryOperation.planBound(
                                bound,
                                CanonicalRowPhysicalRequest.mapped(
                                        operation, mapped));
                StringBuilder result = new StringBuilder(
                        CanonicalQueryOperation.explainBound(
                                bound, physical));
                result.append(" mappedStages=");
                appendStages(result, operation.stages);
                return result.append(" mappedCallbackBarrier=true")
                        .append(" estimatedMappedTemporaryPeakBytes=")
                        .append(physical.resources.temporaryBytes)
                        .toString();
            }
        });
    }

    static void visit(
            CanonicalRowExecutionFrame frame,
            CanonicalMappedOperation operation,
            MappedVisitor visitor) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (operation.hasOwnStatefulStage()) {
            MappedValueBuffer values = values(frame, operation);
            for (int index = 0; index < values.size; index++) {
                if (!visitor.visit(values.values[index])) return;
            }
            return;
        }
        CanonicalPhysicalSegment segment = frame.plan.pipeline.lastSegment(
                CanonicalPhysicalSegment.Shape.MAPPED_REFERENCE);
        final int from = segment == null ? 0 : segment.fromStage;
        final int to = segment == null
                ? operation.stages.size() : segment.toStageExclusive;
        final long[] counters = new long[to - from];
        CanonicalRowExecution.visit(frame, locator -> {
            if (limitReached(operation.stages, from, to, counters)) return false;
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, operation.rootMapper, true);
            requireDetached(bound, value);
            Step step = applySegment(
                    bound, value, operation.stages,
                    from, to, counters);
            return (!step.selected || visitor.visit(step.value))
                    && !step.stop;
        });
    }

    static long mappedStatefulScratch(BoundCanonicalRowOperation bound) {
        return RowExecutionSupport.arrayBytes(
                bound.root.size, 64L, bound.provenance);
    }

    private static <T> T terminal(
            final MappedPipelineCapture<?> frontend,
            CanonicalMappedOperation.TerminalKind terminalKind,
            HostCallbackHandle terminalCallback,
            Class<?> componentType,
            final boolean materialized,
            final MappedTerminal<T> terminal) {
        final CanonicalMappedOperation operation =
                CanonicalMappedLowering.operation(
                        frontend,
                        terminalKind,
                        terminalCallback,
                        componentType);
        return CanonicalQueryOperation.executeMappedFamily(
                frontend.rows, operation.source,
                new CanonicalQueryOperation.ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return estimatedExecutionScratch(
                        bound, operation, materialized);
            }
        }, new CanonicalQueryOperation.FrameWork<T>() {
            @Override public T run(CanonicalRowExecutionFrame frame) {
                return terminal.run(frame, operation);
            }
        }, operation);
    }

    private static long estimatedExecutionScratch(
            BoundCanonicalRowOperation bound,
            CanonicalMappedOperation operation,
            boolean materialized) {
        long result = 0L;
        if (operation.hasOwnStatefulStage()) {
            result = mappedStatefulScratch(bound);
        }
        if (materialized) {
            result = CheckedLong.add(
                    result,
                    RowExecutionSupport.arrayBytes(
                            operation.outputUpperBound(bound),
                            64L,
                            bound.provenance),
                    bound.operation,
                    bound.provenance);
        }
        return result;
    }

    private static MappedValueBuffer values(
            CanonicalRowExecutionFrame frame,
            CanonicalMappedOperation operation) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        CanonicalPhysicalPipeline pipeline = frame.plan.pipeline;
        int breakerIndex = pipeline.firstBreaker(
                CanonicalPhysicalSegment.Shape.MAPPED_REFERENCE, 0);
        if (breakerIndex < 0) {
            throw new AssertionError("Mapped breaker topology is missing");
        }
        CanonicalPhysicalBreaker firstBreaker =
                pipeline.breakers[breakerIndex];
        CanonicalPhysicalSegment firstSegment =
                pipeline.segments[firstBreaker.inputSegmentOrdinal];
        MappedValueBuffer values = frame.allocateMappedBreakerState(
                firstBreaker.inputUpperBound, bound.provenance);
        collectSegment(
                frame, operation,
                firstSegment.fromStage,
                firstSegment.toStageExclusive,
                values);
        while (breakerIndex >= 0) {
            CanonicalPhysicalBreaker breaker = pipeline.breakers[breakerIndex];
            CanonicalMappedStage stage =
                    operation.stages.get(breaker.stageIndex);
            if (breaker.kind == CanonicalPhysicalBreaker.Kind.MEMBERSHIP) {
                distinct(bound, values);
            } else if (breaker.kind
                    == CanonicalPhysicalBreaker.Kind.STABLE_REORDER) {
                stableSort(bound, values, stage.callback);
            } else {
                throw new AssertionError("unexpected Mapped breaker");
            }
            CanonicalPhysicalSegment output =
                    pipeline.segments[breaker.outputSegmentOrdinal];
            compactSegment(
                    bound, values, operation.stages,
                    output.fromStage, output.toStageExclusive);
            breakerIndex = pipeline.firstBreaker(
                    CanonicalPhysicalSegment.Shape.MAPPED_REFERENCE,
                    breakerIndex + 1);
        }
        return values;
    }

    private static void collectSegment(
            CanonicalRowExecutionFrame frame,
            CanonicalMappedOperation operation,
            int from,
            int to,
            MappedValueBuffer output) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        long[] counters = new long[to - from];
        CanonicalRowExecution.visit(frame, locator -> {
            if (limitReached(operation.stages, from, to, counters)) {
                return false;
            }
            Object value = RowExecutionSupport.callbackMap(
                    bound, locator, operation.rootMapper, true);
            requireDetached(bound, value);
            Step step = applySegment(
                    bound, value, operation.stages, from, to, counters);
            if (step.selected) output.add(step.value);
            return !step.stop;
        });
    }

    private static void compactSegment(
            BoundCanonicalRowOperation bound,
            MappedValueBuffer values,
            List<CanonicalMappedStage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        for (int input = 0; input < values.size; input++) {
            if (limitReached(stages, from, to, counters)) break;
            Step step = applySegment(
                    bound, values.values[input], stages,
                    from, to, counters);
            if (step.selected) values.values[output++] = step.value;
            if (step.stop) break;
        }
        values.clearTail(output);
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
                    if (!callbackPredicate(bound, stage.callback, value)) {
                        return new Step(value, false,
                                limitReached(stages, from, to, counters));
                    }
                    break;
                case MAP:
                    value = callbackMap(bound, stage.callback, value);
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
                    throw new AssertionError(
                            "stateful mapped stage inside segment");
            }
        }
        return new Step(value, true, false);
    }

    private static void distinct(
            BoundCanonicalRowOperation bound,
            MappedValueBuffer values) {
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
            BoundCanonicalRowOperation bound,
            MappedValueBuffer values,
            HostCallbackHandle comparator) {
        CanonicalMappedSort.sort(
                bound,
                values.values,
                values.size,
                comparator);
    }

    private static boolean callbackPredicate(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object value) {
        if (callback == null
                || callback.kind != HostCallbackHandle.Kind.MAPPED_PREDICATE) {
            throw new AssertionError("mapped predicate callback kind drift");
        }
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

    private static Object callbackMap(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object value) {
        if (callback == null
                || callback.kind != HostCallbackHandle.Kind.MAPPED_MAPPER) {
            throw new AssertionError("mapped mapper callback kind drift");
        }
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

    static int callbackCompare(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object left,
            Object right) {
        if (callback == null
                || callback.kind != HostCallbackHandle.Kind.MAPPED_COMPARATOR) {
            throw new AssertionError("mapped comparator callback kind drift");
        }
        CallbackExecutionScope.enter();
        try {
            return castComparator(callback).compare(left, right);
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
        }
    }

    private static void callbackAction(
            BoundCanonicalRowOperation bound,
            HostCallbackHandle callback,
            Object value) {
        if (callback == null
                || callback.kind != HostCallbackHandle.Kind.MAPPED_ACTION) {
            throw new AssertionError("mapped action callback kind drift");
        }
        CallbackExecutionScope.enter();
        try {
            castConsumer(callback).accept(value);
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
        }
    }

    static void requireDetached(
            BoundCanonicalRowOperation bound,
            Object value) {
        if (bound.table.isBorrowedQueryView(value)) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.QUERY,
                    "mapper returned a borrowed SOMA view",
                    bound.provenance);
        }
    }

    private static HostCallbackHandle mappedCallback(
            MappedPipelineCapture<?> frontend,
            HostCallbackHandle.Kind kind,
            Object callback) {
        return HostCallbackHandle.host(
                frontend.rows.owner().logicalIdentity(), kind, callback);
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

    private static void appendStages(
            StringBuilder target,
            List<CanonicalMappedStage> stages) {
        target.append('[');
        for (int index = 0; index < stages.size(); index++) {
            if (index != 0) target.append(',');
            target.append(stages.get(index).kind);
        }
        target.append(']');
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

    @SuppressWarnings("unchecked")
    private static Comparator<Object> castComparator(
            HostCallbackHandle callback) {
        return (Comparator<Object>) callback.callback;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> castConsumer(
            HostCallbackHandle callback) {
        return (Consumer<Object>) callback.callback;
    }

    interface MappedVisitor {
        boolean visit(Object value);
    }

    private interface MappedTerminal<T> {
        T run(
                CanonicalRowExecutionFrame frame,
                CanonicalMappedOperation operation);
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

/** Typed host-reference breaker buffer allocated only by an admitted Frame. */
final class MappedValueBuffer {
    final Object[] values;
    int size;

    MappedValueBuffer(long upper, Object provenance) {
        values = new Object[RowExecutionSupport.arrayLength(
                upper, provenance)];
    }

    void add(Object value) {
        values[size++] = value;
    }

    void clearTail(int next) {
        Arrays.fill(values, next, size, null);
        size = next;
    }
}
