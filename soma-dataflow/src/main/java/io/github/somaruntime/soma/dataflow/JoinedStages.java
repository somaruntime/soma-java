package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class JoinedStagePlan<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    private static final byte FILTER = 1;
    private static final byte SKIP = 2;
    private static final byte LIMIT = 3;
    private static final byte SORT = 4;

    private final byte[] kinds;
    private final Object[] operands;
    private final long[] arguments;
    private final List<ParameterSlot<?>> parameters;
    private final String canonical;

    private JoinedStagePlan(
            byte[] kinds,
            Object[] operands,
            long[] arguments,
            List<ParameterSlot<?>> parameters,
            String canonical) {
        this.kinds = kinds;
        this.operands = operands;
        this.arguments = arguments;
        this.parameters = parameters;
        this.canonical = canonical;
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinedStagePlan<L, R> empty() {
        return new JoinedStagePlan<L, R>(
                new byte[0],
                new Object[0],
                new long[0],
                Collections.<ParameterSlot<?>>emptyList(),
                "joined-stages-v1");
    }

    JoinedStagePlan<L, R> filter(JoinedPredicate<L, R> predicate) {
        return append(
                FILTER,
                predicate,
                0L,
                predicate.parameters(),
                "filter(" + predicate.identity() + ")");
    }

    JoinedStagePlan<L, R> skip(long count) {
        return append(
                SKIP,
                null,
                count,
                Collections.<ParameterSlot<?>>emptyList(),
                "skip(" + count + ")");
    }

    JoinedStagePlan<L, R> limit(long count) {
        return append(
                LIMIT,
                null,
                count,
                Collections.<ParameterSlot<?>>emptyList(),
                "limit(" + count + ")");
    }

    JoinedStagePlan<L, R> sort(JoinedOrder<L, R> order) {
        return append(
                SORT,
                order,
                0L,
                order.parameters(),
                "sort(" + order.identity() + ")");
    }

    boolean isEmpty() {
        return kinds.length == 0;
    }

    boolean hasSort() {
        for (byte kind : kinds) {
            if (kind == SORT) {
                return true;
            }
        }
        return false;
    }

    List<ParameterSlot<?>> parameters() {
        return parameters;
    }

    String canonical() {
        return canonical;
    }

    long upperBound(long cardinality) {
        long result = cardinality;
        for (int stage = 0; stage < kinds.length; stage++) {
            if (kinds[stage] == SKIP) {
                result = Math.max(0L, result - arguments[stage]);
            } else if (kinds[stage] == LIMIT) {
                result = Math.min(result, arguments[stage]);
            }
        }
        return result;
    }

    long enumerate(
            ExecutionFrame frame,
            JoinPrepared<L, R> prepared,
            JoinMatchConsumer consumer) {
        if (!hasSort()) {
            return enumerateStreaming(frame, prepared, consumer);
        }
        long cardinality = prepared.enumerateRaw(
                frame, JoinPrepared.COUNTER);
        if (cardinality > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    prepared.leftKey.source().alias(),
                    "dataflow.join.stages",
                    Long.toString(cardinality));
        }
        int size = (int) cardinality;
        int[] leftIndexes =
                frame.newScratchIndexes(size, "dataflow.join.stages");
        int[] rightIndexes =
                frame.newScratchIndexes(size, "dataflow.join.stages");
        boolean[] rightPresence =
                frame.newScratchBooleans(size, "dataflow.join.stages");
        final int[] write = new int[1];
        prepared.enumerateRaw(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        int position = write[0]++;
                        leftIndexes[position] = leftIndex;
                        rightIndexes[position] = rightIndex;
                        rightPresence[position] = rightPresent;
                        return true;
                    }
                });

        for (int stage = 0; stage < kinds.length; stage++) {
            byte kind = kinds[stage];
            if (kind == FILTER) {
                @SuppressWarnings("unchecked")
                JoinedPredicate<L, R> predicate =
                        (JoinedPredicate<L, R>) operands[stage];
                int output = 0;
                for (int position = 0; position < size; position++) {
                    if (predicate.evaluate(
                            frame,
                            prepared.leftBinding,
                            leftIndexes[position],
                            prepared.rightBinding,
                            rightPresence[position],
                            rightIndexes[position])) {
                        copy(
                                leftIndexes,
                                rightIndexes,
                                rightPresence,
                                position,
                                output++);
                    }
                }
                size = output;
            } else if (kind == SKIP) {
                int skipped = arguments[stage] >= size
                        ? size : (int) arguments[stage];
                compact(
                        leftIndexes,
                        rightIndexes,
                        rightPresence,
                        skipped,
                        size - skipped);
                size -= skipped;
            } else if (kind == LIMIT) {
                if (arguments[stage] < size) {
                    size = (int) arguments[stage];
                }
            } else {
                @SuppressWarnings("unchecked")
                JoinedOrder<L, R> order =
                        (JoinedOrder<L, R>) operands[stage];
                stableSort(
                        frame,
                        prepared,
                        leftIndexes,
                        rightIndexes,
                        rightPresence,
                        size,
                        order);
            }
        }

        for (int position = 0; position < size; position++) {
            if ((position & 1023) == 0) {
                frame.checkBoundary("dataflow.join.stages");
            }
            if (!consumer.accept(
                    leftIndexes[position],
                    rightPresence[position],
                    rightIndexes[position])) {
                return position + 1L;
            }
        }
        return size;
    }

    private long enumerateStreaming(
            final ExecutionFrame frame,
            final JoinPrepared<L, R> prepared,
            final JoinMatchConsumer consumer) {
        final long[] stageCounts = new long[kinds.length];
        final long[] delivered = new long[1];
        prepared.enumerateRaw(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        for (int stage = 0; stage < kinds.length; stage++) {
                            byte kind = kinds[stage];
                            if (kind == FILTER) {
                                @SuppressWarnings("unchecked")
                                JoinedPredicate<L, R> predicate =
                                        (JoinedPredicate<L, R>) operands[stage];
                                if (!predicate.evaluate(
                                        frame,
                                        prepared.leftBinding,
                                        leftIndex,
                                        prepared.rightBinding,
                                        rightPresent,
                                        rightIndex)) {
                                    return true;
                                }
                            } else if (kind == SKIP) {
                                if (stageCounts[stage] < arguments[stage]) {
                                    stageCounts[stage]++;
                                    return true;
                                }
                            } else if (kind == LIMIT) {
                                if (stageCounts[stage] >= arguments[stage]) {
                                    return false;
                                }
                                stageCounts[stage]++;
                            }
                        }
                        delivered[0]++;
                        return consumer.accept(
                                leftIndex, rightPresent, rightIndex);
                    }
                });
        return delivered[0];
    }

    private JoinedStagePlan<L, R> append(
            byte kind,
            Object operand,
            long argument,
            List<ParameterSlot<?>> addedParameters,
            String stageCanonical) {
        int length = kinds.length;
        byte[] nextKinds = Arrays.copyOf(kinds, length + 1);
        Object[] nextOperands = Arrays.copyOf(operands, length + 1);
        long[] nextArguments = Arrays.copyOf(arguments, length + 1);
        nextKinds[length] = kind;
        nextOperands[length] = operand;
        nextArguments[length] = argument;
        StringBuilder nextCanonical = new StringBuilder(canonical);
        DataFlowSupport.appendCanonical(
                nextCanonical, "stage", stageCanonical);
        return new JoinedStagePlan<L, R>(
                nextKinds,
                nextOperands,
                nextArguments,
                DataFlowSupport.unionParameters(
                        parameters, addedParameters),
                nextCanonical.toString());
    }

    private static void compact(
            int[] left,
            int[] right,
            boolean[] presence,
            int start,
            int length) {
        if (length == 0 || start == 0) {
            return;
        }
        System.arraycopy(left, start, left, 0, length);
        System.arraycopy(right, start, right, 0, length);
        System.arraycopy(presence, start, presence, 0, length);
    }

    private static void copy(
            int[] left,
            int[] right,
            boolean[] presence,
            int source,
            int target) {
        if (source == target) {
            return;
        }
        left[target] = left[source];
        right[target] = right[source];
        presence[target] = presence[source];
    }

    private static <L extends DataFlowBinding, R extends DataFlowBinding>
    void stableSort(
            ExecutionFrame frame,
            JoinPrepared<L, R> prepared,
            int[] left,
            int[] right,
            boolean[] presence,
            int size,
            JoinedOrder<L, R> order) {
        if (size < 2) {
            return;
        }
        int[] leftScratch =
                frame.newScratchIndexes(size, "dataflow.join.sort");
        int[] rightScratch =
                frame.newScratchIndexes(size, "dataflow.join.sort");
        boolean[] presenceScratch =
                frame.newScratchBooleans(size, "dataflow.join.sort");
        for (int width = 1; width < size; width = nextWidth(width, size)) {
            int block = width > Integer.MAX_VALUE / 2
                    ? Integer.MAX_VALUE : width * 2;
            for (int start = 0; start < size; ) {
                int middle = (long) start + width >= size
                        ? size : start + width;
                int end = (long) start + block >= size
                        ? size : start + block;
                merge(
                        frame,
                        prepared,
                        order,
                        left,
                        right,
                        presence,
                        leftScratch,
                        rightScratch,
                        presenceScratch,
                        start,
                        middle,
                        end);
                if (end == size) {
                    break;
                }
                start = end;
            }
            System.arraycopy(leftScratch, 0, left, 0, size);
            System.arraycopy(rightScratch, 0, right, 0, size);
            System.arraycopy(presenceScratch, 0, presence, 0, size);
        }
    }

    private static int nextWidth(int width, int size) {
        return width > size / 2 ? size : width * 2;
    }

    private static <L extends DataFlowBinding, R extends DataFlowBinding>
    void merge(
            ExecutionFrame frame,
            JoinPrepared<L, R> prepared,
            JoinedOrder<L, R> order,
            int[] left,
            int[] right,
            boolean[] presence,
            int[] leftScratch,
            int[] rightScratch,
            boolean[] presenceScratch,
            int start,
            int middle,
            int end) {
        int first = start;
        int second = middle;
        int output = start;
        while (first < middle || second < end) {
            boolean takeFirst = second >= end
                    || (first < middle && order.compare(
                    frame,
                    prepared.leftBinding,
                    prepared.rightBinding,
                    left[first],
                    presence[first],
                    right[first],
                    left[second],
                    presence[second],
                    right[second]) <= 0);
            int selected = takeFirst ? first++ : second++;
            leftScratch[output] = left[selected];
            rightScratch[output] = right[selected];
            presenceScratch[output] = presence[selected];
            output++;
        }
    }
}
