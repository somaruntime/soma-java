package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class WindowPrepared<B extends DataFlowBinding> {
    final CandidateSelection selected;
    final DataFlowBinding binding;
    final int[] starts;
    final int[] ends;
    final int windowCount;
    final long memberCount;

    WindowPrepared(
            CandidateSelection selected,
            DataFlowBinding binding,
            int[] starts,
            int[] ends,
            int windowCount,
            long memberCount) {
        this.selected = selected;
        this.binding = binding;
        this.starts = starts;
        this.ends = ends;
        this.windowCount = windowCount;
        this.memberCount = memberCount;
    }
}

abstract class WindowOperation<B extends DataFlowBinding, R>
        extends SingleSourceOperation<R> {
    final WindowedFlow<B> flow;

    WindowOperation(WindowedFlow<B> flow) {
        this(flow, Collections.<ParameterSlot<?>>emptyList());
    }

    WindowOperation(
            WindowedFlow<B> flow,
            List<ParameterSlot<?>> additionalParameters) {
        super(
                flow.program(),
                DataFlowSupport.unionParameters(
                        flow.windowParameters(), additionalParameters));
        this.flow = flow;
    }

    @Override
    public final String canonicalForm() {
        return flow.program().canonical() + "->"
                + (flow.time() ? "time" : "count") + "Window("
                + flow.width() + "," + flow.step() + ","
                + flow.origin() + "," + flow.partialPolicy() + ")->"
                + flow.shape().canonical() + "->" + terminal();
    }

    @Override
    public final String logicalShape() {
        return "Candidate -> Windowed";
    }

    @Override
    public final String logicalPlan() {
        return flow.program().canonical() + " -> "
                + (flow.time() ? "TimeWindow" : "CountWindow")
                + (flow.shape().isIdentity()
                ? "" : " -> WindowShape")
                + " -> " + terminal();
    }

    @Override
    public final String physicalPlan() {
        return "finite-window[offsets," + terminal() + "]";
    }

    abstract String terminal();

    final WindowPrepared<B> prepare(ExecutionFrame frame) {
        CandidateSelection selected =
                flow.program().select(frame, "dataflow.window");
        DataFlowBinding binding = frame.binding(source);
        WindowPrepared<B> prepared = flow.time()
                ? prepareTime(frame, selected, binding)
                : prepareCount(frame, selected, binding);
        return flow.shape().apply(prepared);
    }

    private WindowPrepared<B> prepareCount(
            ExecutionFrame frame,
            CandidateSelection selected,
            DataFlowBinding binding) {
        int cardinality = selected.size;
        long estimated = cardinality == 0
                ? 0L : ((long) cardinality - 1L) / flow.step() + 1L;
        if (estimated > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_window_count_overflow",
                    source.alias(),
                    "dataflow.window",
                    Long.toString(estimated));
        }
        int[] starts =
                frame.newScratchIndexes((int) estimated, "dataflow.window");
        int[] ends =
                frame.newScratchIndexes((int) estimated, "dataflow.window");
        int windows = 0;
        long members = 0L;
        for (long anchor = 0L; anchor < cardinality; anchor += flow.step()) {
            long end = Math.min((long) cardinality, anchor + flow.width());
            boolean partial = end - anchor < flow.width();
            if (partial
                    && flow.partialPolicy() == PartialWindowPolicy.DROP_PARTIAL) {
                break;
            }
            starts[windows] = (int) anchor;
            ends[windows] = (int) end;
            members = addMembers(members, end - anchor);
            windows++;
            if (anchor > Long.MAX_VALUE - flow.step()) {
                break;
            }
        }
        return new WindowPrepared<B>(
                selected, binding, starts, ends, windows, members);
    }

    private WindowPrepared<B> prepareTime(
            ExecutionFrame frame,
            CandidateSelection selected,
            DataFlowBinding binding) {
        int cardinality = selected.size;
        if (cardinality == 0) {
            return new WindowPrepared<B>(
                    selected, binding, new int[0], new int[0], 0, 0L);
        }
        LongExpression<B> order = flow.orderKey();
        long first = order.evaluate(
                frame, binding, selected.indexAt(0));
        if (flow.origin() > first) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_window_origin_after_first",
                    source.alias(),
                    "dataflow.window");
        }
        long previous = first;
        for (int position = 1; position < cardinality; position++) {
            long value = order.evaluate(
                    frame, binding, selected.indexAt(position));
            if (value < previous) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_window_order_not_monotonic",
                        source.alias(),
                        "dataflow.window");
            }
            previous = value;
        }
        long last = previous;
        long span = last - flow.origin();
        if (span < 0L) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_window_range_overflow",
                    source.alias(),
                    "dataflow.window");
        }
        long completedSteps = span / flow.step();
        if (completedSteps >= Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_window_count_overflow",
                    source.alias(),
                    "dataflow.window",
                    ">" + Integer.MAX_VALUE);
        }
        long estimated = completedSteps + 1L;
        int[] starts =
                frame.newScratchIndexes((int) estimated, "dataflow.window");
        int[] ends =
                frame.newScratchIndexes((int) estimated, "dataflow.window");
        int windows = 0;
        long members = 0L;
        int startPosition = 0;
        int endPosition = 0;
        long anchor = flow.origin();
        for (long ordinal = 0L; ordinal < estimated; ordinal++) {
            if (anchor > Long.MAX_VALUE - flow.width()) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_window_range_overflow",
                        source.alias(),
                        "dataflow.window");
            }
            long endKey = anchor + flow.width();
            while (startPosition < cardinality
                    && order.evaluate(
                    frame,
                    binding,
                    selected.indexAt(startPosition)) < anchor) {
                startPosition++;
            }
            if (endPosition < startPosition) {
                endPosition = startPosition;
            }
            while (endPosition < cardinality
                    && order.evaluate(
                    frame,
                    binding,
                    selected.indexAt(endPosition)) < endKey) {
                endPosition++;
            }
            boolean partial = endKey > last
                    && last != Long.MAX_VALUE;
            if (!(partial
                    && flow.partialPolicy() == PartialWindowPolicy.DROP_PARTIAL)
                    && startPosition < endPosition) {
                starts[windows] = startPosition;
                ends[windows] = endPosition;
                members = addMembers(
                        members, (long) endPosition - startPosition);
                windows++;
            }
            if (ordinal + 1L < estimated) {
                if (anchor > Long.MAX_VALUE - flow.step()) {
                    throw DataFlowFailures.invalidInput(
                            "dataflow_window_range_overflow",
                            source.alias(),
                            "dataflow.window");
                }
                anchor += flow.step();
            }
        }
        return new WindowPrepared<B>(
                selected, binding, starts, ends, windows, members);
    }

    private static long addMembers(long current, long additional) {
        if (additional < 0L || current > Long.MAX_VALUE - additional) {
            throw DataFlowFailures.resource(
                    "dataflow_window_members_overflow",
                    "window",
                    "dataflow.window",
                    "overflow");
        }
        return current + additional;
    }
}

final class WindowIndexOperation<B extends DataFlowBinding>
        extends WindowOperation<B, WindowIndexResult> {
    WindowIndexOperation(WindowedFlow<B> flow) {
        super(flow);
    }

    @Override
    String terminal() {
        return "window-index-snapshot";
    }

    @Override
    public ExecutionOutcome<WindowIndexResult> execute(ExecutionFrame frame) {
        WindowPrepared<B> prepared = prepare(frame);
        if (prepared.memberCount > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    source.alias(),
                    "dataflow.window.indexSnapshot",
                    Long.toString(prepared.memberCount));
        }
        long bytes = (long) (prepared.windowCount + 1) * 4L
                + prepared.memberCount * 4L;
        frame.reserveOutput(
                prepared.memberCount,
                bytes,
                "dataflow.window.indexSnapshot");
        int[] offsets = new int[prepared.windowCount + 1];
        int[] indexes = new int[(int) prepared.memberCount];
        int output = 0;
        for (int window = 0; window < prepared.windowCount; window++) {
            offsets[window] = output;
            for (int position = prepared.starts[window];
                 position < prepared.ends[window];
                 position++) {
                indexes[output++] = prepared.selected.indexAt(position);
            }
        }
        offsets[prepared.windowCount] = output;
        WindowIndexResult result = new WindowIndexResult(
                source.alias(),
                prepared.binding.structuralEpoch(),
                offsets,
                indexes);
        return new ExecutionOutcome<WindowIndexResult>(
                result,
                prepared.selected.scanned,
                prepared.selected.size,
                prepared.memberCount,
                1,
                1);
    }
}

final class ActiveWindowCursor implements WindowCursor {
    private int ordinal;
    private int start;
    private int end;
    private CandidateSelection selected;
    private boolean active;

    void open(
            int ordinal,
            int start,
            int end,
            CandidateSelection selected) {
        this.ordinal = ordinal;
        this.start = start;
        this.end = end;
        this.selected = selected;
        active = true;
    }

    void close() {
        active = false;
        selected = null;
    }

    @Override
    public int ordinal() {
        requireActive();
        return ordinal;
    }

    @Override
    public int size() {
        requireActive();
        return end - start;
    }

    @Override
    public int indexAt(int position) {
        requireActive();
        if (position < 0 || position >= end - start) {
            throw new IndexOutOfBoundsException("window position out of range");
        }
        return selected.indexAt(start + position);
    }

    private void requireActive() {
        if (!active) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_escaped_window_cursor",
                    "window",
                    "dataflow.window.cursor",
                    "CLOSED");
        }
    }
}

final class WindowDeliveryOperation<B extends DataFlowBinding>
        extends WindowOperation<B, DeliveryResult> {
    private final ParameterSlot<WindowVisitor> visitorSlot;

    WindowDeliveryOperation(
            WindowedFlow<B> flow,
            ParameterSlot<WindowVisitor> visitorSlot) {
        super(
                flow,
                Collections.<ParameterSlot<?>>singletonList(visitorSlot));
        this.visitorSlot = visitorSlot;
    }

    @Override
    String terminal() {
        return "deliver(window-cursor)";
    }

    @Override
    public ResultDeliveryMode resultDeliveryMode() {
        return ResultDeliveryMode.CALLBACK_SCOPED;
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        WindowPrepared<B> prepared = prepare(frame);
        frame.preflightDelivery(
                prepared.windowCount,
                (long) prepared.windowCount * 8L,
                "dataflow.window.deliver");
        WindowVisitor visitor = frame.parameter(visitorSlot);
        ActiveWindowCursor cursor = new ActiveWindowCursor();
        int delivered = 0;
        boolean completed = true;
        for (int window = 0; window < prepared.windowCount; window++) {
            cursor.open(
                    window,
                    prepared.starts[window],
                    prepared.ends[window],
                    prepared.selected);
            try {
                delivered++;
                if (!visitor.visit(cursor)) {
                    completed = false;
                    break;
                }
            } catch (SomaRuntimeException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw DataFlowFailures.callback(
                        "dataflow_window_delivery_callback",
                        source.alias(),
                        "dataflow.window.deliver",
                        failure);
            } finally {
                cursor.close();
            }
        }
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(delivered, completed),
                prepared.selected.scanned,
                prepared.selected.size,
                delivered,
                1,
                1);
    }
}

final class WindowLongAggregationOperation<B extends DataFlowBinding>
        extends WindowOperation<B, LongColumnResult> {
    private static final int COUNT = 0;
    private static final int SUM = 1;
    private static final int MIN = 2;
    private static final int MAX = 3;

    private final LongExpression<B> expression;
    private final int kind;

    private WindowLongAggregationOperation(
            WindowedFlow<B> flow,
            LongExpression<B> expression,
            int kind) {
        super(
                flow,
                expression == null
                        ? Collections.<ParameterSlot<?>>emptyList()
                        : expression.parameters);
        this.expression = expression;
        this.kind = kind;
    }

    static <B extends DataFlowBinding> WindowLongAggregationOperation<B> counts(
            WindowedFlow<B> flow) {
        return new WindowLongAggregationOperation<B>(
                flow, null, COUNT);
    }

    static <B extends DataFlowBinding> WindowLongAggregationOperation<B> sum(
            WindowedFlow<B> flow, LongExpression<B> expression) {
        return new WindowLongAggregationOperation<B>(
                flow, expression, SUM);
    }

    static <B extends DataFlowBinding> WindowLongAggregationOperation<B> min(
            WindowedFlow<B> flow, LongExpression<B> expression) {
        return new WindowLongAggregationOperation<B>(
                flow, expression, MIN);
    }

    static <B extends DataFlowBinding> WindowLongAggregationOperation<B> max(
            WindowedFlow<B> flow, LongExpression<B> expression) {
        return new WindowLongAggregationOperation<B>(
                flow, expression, MAX);
    }

    @Override
    String terminal() {
        return "long-aggregate-v2(" + kind + ")";
    }

    @Override
    public ExecutionOutcome<LongColumnResult> execute(ExecutionFrame frame) {
        WindowPrepared<B> prepared = prepare(frame);
        long[] values = frame.newOutputLongs(
                prepared.windowCount, "dataflow.window.aggregate");
        if (kind == COUNT) {
            for (int window = 0;
                 window < prepared.windowCount;
                 window++) {
                values[window] = prepared.ends[window]
                        - prepared.starts[window];
            }
        } else if (kind == SUM) {
            aggregateSums(frame, prepared, values);
        } else {
            aggregateExtrema(frame, prepared, values);
        }
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(values, prepared.windowCount),
                prepared.selected.scanned,
                prepared.selected.size,
                prepared.windowCount,
                1,
                1);
    }

    private void aggregateSums(
            ExecutionFrame frame,
            WindowPrepared<B> prepared,
            long[] values) {
        int currentStart = 0;
        int currentEnd = 0;
        IntegralArithmetic.ExactSum aggregate =
                new IntegralArithmetic.ExactSum();
        for (int window = 0; window < prepared.windowCount; window++) {
            int start = prepared.starts[window];
            int end = prepared.ends[window];
            if (start > currentEnd) {
                aggregate.reset();
                currentStart = start;
                currentEnd = start;
            } else {
                while (currentStart < start) {
                    aggregate.subtract(evaluate(
                            frame, prepared, currentStart++));
                }
            }
            while (currentEnd < end) {
                aggregate.add(evaluate(
                        frame, prepared, currentEnd++));
            }
            values[window] = aggregate.longValue(
                    expression.path, "dataflow.window.aggregate");
        }
    }

    private void aggregateExtrema(
            ExecutionFrame frame,
            WindowPrepared<B> prepared,
            long[] values) {
        int[] positions = frame.newScratchIndexes(
                prepared.selected.size, "dataflow.window.extrema");
        long[] candidates = frame.newScratchLongs(
                prepared.selected.size, "dataflow.window.extrema");
        int head = 0;
        int tail = 0;
        int currentEnd = 0;
        for (int window = 0; window < prepared.windowCount; window++) {
            int start = prepared.starts[window];
            int end = prepared.ends[window];
            if (start > currentEnd) {
                head = tail;
                currentEnd = start;
            }
            while (head < tail && positions[head] < start) {
                head++;
            }
            while (currentEnd < end) {
                long value = evaluate(frame, prepared, currentEnd);
                while (tail > head && dominated(
                        candidates[tail - 1], value)) {
                    tail--;
                }
                positions[tail] = currentEnd;
                candidates[tail] = value;
                tail++;
                currentEnd++;
            }
            values[window] = candidates[head];
        }
    }

    private long evaluate(
            ExecutionFrame frame,
            WindowPrepared<B> prepared,
            int position) {
        return expression.evaluate(
                frame,
                prepared.binding,
                prepared.selected.indexAt(position));
    }

    private boolean dominated(long existing, long next) {
        return kind == MIN ? existing >= next : existing <= next;
    }
}
