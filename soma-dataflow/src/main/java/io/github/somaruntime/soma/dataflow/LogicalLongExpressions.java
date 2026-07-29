package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/** Internal primitive lowering shared by logical date/time/instant facades. */
final class LogicalLongExpressions {
    private static final long NANOS_PER_DAY = 86_400_000_000_000L;

    private LogicalLongExpressions() {
    }

    static <B extends DataFlowBinding> LongExpression<B> checkedAdd(
            LongExpression<B> source, final long delta, final String operation) {
        final LongNode upstream = source.node;
        return new LongExpression<B>(
                source.source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return Math.addExact(
                                upstream.evaluate(frame, binding, index),
                                delta);
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + upstream.canonical()
                                + "," + delta + ")";
                    }
                },
                source.presence,
                source.path + "." + operation,
                source.parameters,
                source.parallelSafe);
    }

    static <B extends DataFlowBinding> LongExpression<B> checkedSubtract(
            LongExpression<B> source, final long delta, final String operation) {
        final LongNode upstream = source.node;
        return new LongExpression<B>(
                source.source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        return Math.subtractExact(
                                upstream.evaluate(frame, binding, index),
                                delta);
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + upstream.canonical()
                                + "," + delta + ")";
                    }
                },
                source.presence,
                source.path + "." + operation,
                source.parameters,
                source.parallelSafe);
    }

    static <B extends DataFlowBinding> LongExpression<B> timeAdd(
            LongExpression<B> source, final long nanos) {
        return timeShift(
                source,
                Math.floorMod(nanos, NANOS_PER_DAY),
                nanos,
                "time-plus-nanos");
    }

    static <B extends DataFlowBinding> LongExpression<B> timeSubtract(
            LongExpression<B> source, final long nanos) {
        long normalized = Math.floorMod(nanos, NANOS_PER_DAY);
        long shift = normalized == 0L
                ? 0L : NANOS_PER_DAY - normalized;
        return timeShift(
                source, shift, nanos, "time-minus-nanos");
    }

    private static <B extends DataFlowBinding> LongExpression<B> timeShift(
            LongExpression<B> source,
            final long normalizedShift,
            final long declaredDelta,
            final String operation) {
        final LongNode upstream = source.node;
        return new LongExpression<B>(
                source.source,
                new LongNode() {
                    @Override
                    public long evaluate(
                            ExecutionFrame frame,
                            DataFlowBinding binding,
                            int index) {
                        long value =
                                upstream.evaluate(frame, binding, index);
                        long shifted = value + normalizedShift;
                        return shifted >= NANOS_PER_DAY
                                ? shifted - NANOS_PER_DAY : shifted;
                    }

                    @Override
                    public String canonical() {
                        return operation + "(" + upstream.canonical()
                                + "," + declaredDelta + ")";
                    }
                },
                source.presence,
                source.path + "." + operation,
                source.parameters,
                source.parallelSafe);
    }
}
