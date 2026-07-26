package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Arrays;

interface JoinMatchConsumer {
    boolean accept(int leftIndex, boolean rightPresent, int rightIndex);
}

final class JoinPrepared<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    final CandidateSelection left;
    final CandidateSelection right;
    final DataFlowBinding leftBinding;
    final DataFlowBinding rightBinding;
    final KeyExpression<L> leftKey;
    final KeyExpression<R> rightKey;
    final JoinType type;
    final int[] buckets;
    final int[] next;

    JoinPrepared(
            CandidateSelection left,
            CandidateSelection right,
            DataFlowBinding leftBinding,
            DataFlowBinding rightBinding,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            int[] buckets,
            int[] next) {
        this.left = left;
        this.right = right;
        this.leftBinding = leftBinding;
        this.rightBinding = rightBinding;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
        this.buckets = buckets;
        this.next = next;
    }

    long scanned() {
        return left.scanned + right.scanned;
    }

    long enumerate(ExecutionFrame frame, JoinMatchConsumer consumer) {
        long output = 0L;
        int mask = buckets.length - 1;
        for (int leftPosition = 0; leftPosition < left.size; leftPosition++) {
            if ((leftPosition & 1023) == 0) {
                frame.checkBoundary("dataflow.join");
            }
            int leftIndex = left.indexes[leftPosition];
            int bucket = bucket(leftKey.hash(leftBinding, leftIndex), mask);
            boolean matched = false;
            for (int rightPosition = buckets[bucket];
                 rightPosition >= 0;
                 rightPosition = next[rightPosition]) {
                int rightIndex = right.indexes[rightPosition];
                if (!leftKey.equal(
                        leftBinding,
                        leftIndex,
                        rightKey,
                        rightBinding,
                        rightIndex)) {
                    continue;
                }
                matched = true;
                if (type == JoinType.LEFT_SEMI) {
                    output++;
                    if (!consumer.accept(leftIndex, false, -1)) {
                        return output;
                    }
                    break;
                }
                if (type != JoinType.LEFT_ANTI) {
                    output++;
                    if (!consumer.accept(leftIndex, true, rightIndex)) {
                        return output;
                    }
                }
            }
            if (!matched && (type == JoinType.LEFT_OUTER
                    || type == JoinType.LEFT_ANTI)) {
                output++;
                if (!consumer.accept(leftIndex, false, -1)) {
                    return output;
                }
            }
        }
        return output;
    }

    private static int bucket(long hash, int mask) {
        return ((int) (hash ^ (hash >>> 32))) & mask;
    }
}

abstract class JoinOperation<
        L extends DataFlowBinding,
        R extends DataFlowBinding,
        O> extends MultiSourceOperation<O> {
    final CandidateProgram<L> left;
    final CandidateProgram<R> right;
    final KeyExpression<L> leftKey;
    final KeyExpression<R> rightKey;
    final JoinType type;

    JoinOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        super(left, right);
        this.left = left;
        this.right = right;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
    }

    @Override
    public final String canonicalForm() {
        return "join(" + type + "," + left.canonical() + ","
                + right.canonical() + "," + leftKey.identity() + ","
                + rightKey.identity() + ")->" + terminal();
    }

    @Override
    public final String logicalShape() {
        return type == JoinType.LEFT_SEMI || type == JoinType.LEFT_ANTI
                ? "Candidate<L> × Candidate<R> -> Candidate<L>"
                : "Candidate<L> × Candidate<R> -> Joined<L,R>";
    }

    @Override
    public final String logicalPlan() {
        return left.canonical() + " "
                + type + " EQUI JOIN " + right.canonical()
                + " -> " + terminal();
    }

    @Override
    public final String physicalPlan() {
        return "left-driven-hash-join[stable-right-chain," + terminal() + "]";
    }

    abstract String terminal();

    final JoinPrepared<L, R> prepare(ExecutionFrame frame) {
        CandidateSelection leftSelection =
                left.select(frame, "dataflow.join.left");
        CandidateSelection rightSelection =
                right.select(frame, "dataflow.join.right");
        DataFlowBinding leftBinding = frame.binding(left.source());
        DataFlowBinding rightBinding = frame.binding(right.source());
        int bucketCapacity = bucketCapacity(rightSelection.size);
        int[] buckets =
                frame.newScratchIndexes(bucketCapacity, "dataflow.join.hash");
        int[] next = frame.newScratchIndexes(
                rightSelection.size, "dataflow.join.hash");
        Arrays.fill(buckets, -1);
        int mask = bucketCapacity - 1;
        for (int position = rightSelection.size - 1; position >= 0; position--) {
            int index = rightSelection.indexes[position];
            long hash = rightKey.hash(rightBinding, index);
            int bucket = ((int) (hash ^ (hash >>> 32))) & mask;
            next[position] = buckets[bucket];
            buckets[bucket] = position;
        }
        return new JoinPrepared<L, R>(
                leftSelection,
                rightSelection,
                leftBinding,
                rightBinding,
                leftKey,
                rightKey,
                type,
                buckets,
                next);
    }

    private static int bucketCapacity(int size) {
        int target;
        if (size >= (1 << 29)) {
            target = 1 << 30;
        } else {
            target = Math.max(4, size * 2);
        }
        int capacity = 1;
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
    }
}

final class JoinCountOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, LongScalarResult> {
    private static final JoinMatchConsumer COUNTER = new JoinMatchConsumer() {
        @Override
        public boolean accept(
                int leftIndex, boolean rightPresent, int rightIndex) {
            return true;
        }
    };

    JoinCountOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        super(left, right, leftKey, rightKey, type);
    }

    @Override
    String terminal() {
        return "count";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        JoinPrepared<L, R> prepared = prepare(frame);
        long count = prepared.enumerate(frame, COUNTER);
        frame.reserveOutput(1L, 8L, "dataflow.join.count");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(count),
                prepared.scanned(),
                count,
                1L,
                1,
                1);
    }
}

final class JoinIndexOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, JoinedIndexResult> {
    JoinIndexOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        super(left, right, leftKey, rightKey, type);
    }

    @Override
    String terminal() {
        return "joined-index-snapshot";
    }

    @Override
    public ExecutionOutcome<JoinedIndexResult> execute(ExecutionFrame frame) {
        final JoinPrepared<L, R> prepared = prepare(frame);
        long count = prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        return true;
                    }
                });
        if (count > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    left.source().alias(),
                    "dataflow.join.indexSnapshot",
                    Long.toString(count));
        }
        frame.reserveOutput(
                count,
                count * 9L,
                "dataflow.join.indexSnapshot");
        final int[] leftIndexes = new int[(int) count];
        final int[] rightIndexes = new int[(int) count];
        final boolean[] rightPresence = new boolean[(int) count];
        final int[] position = new int[1];
        prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean present,
                            int rightIndex) {
                        int output = position[0]++;
                        leftIndexes[output] = leftIndex;
                        rightIndexes[output] = rightIndex;
                        rightPresence[output] = present;
                        return true;
                    }
                });
        JoinedIndexResult result = new JoinedIndexResult(
                left.source().alias(),
                right.source().alias(),
                prepared.leftBinding.structuralEpoch(),
                prepared.rightBinding.structuralEpoch(),
                leftIndexes,
                rightIndexes,
                rightPresence);
        return new ExecutionOutcome<JoinedIndexResult>(
                result,
                prepared.scanned(),
                count,
                count,
                1,
                1);
    }
}

final class JoinLeftIndexOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, IndexSnapshot> {
    JoinLeftIndexOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        super(left, right, leftKey, rightKey, type);
    }

    @Override
    String terminal() {
        return "left-index-snapshot";
    }

    @Override
    public ExecutionOutcome<IndexSnapshot> execute(ExecutionFrame frame) {
        final JoinPrepared<L, R> prepared = prepare(frame);
        long count = prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        return true;
                    }
                });
        if (count > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    left.source().alias(),
                    "dataflow.join.leftIndexSnapshot",
                    Long.toString(count));
        }
        frame.reserveOutput(
                count,
                count * 4L,
                "dataflow.join.leftIndexSnapshot");
        final int[] indexes = new int[(int) count];
        final int[] size = new int[1];
        prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        indexes[size[0]++] = leftIndex;
                        return true;
                    }
                });
        IndexSnapshot result = prepared.leftBinding.indexSnapshot(
                indexes, size[0]);
        return new ExecutionOutcome<IndexSnapshot>(
                result,
                prepared.scanned(),
                count,
                count,
                1,
                1);
    }
}

final class JoinBorrowOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, LongScalarResult> {
    private final JoinedIndexConsumer consumer;

    JoinBorrowOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedIndexConsumer consumer) {
        super(left, right, leftKey, rightKey, type);
        this.consumer = consumer;
    }

    @Override
    String terminal() {
        return "borrow";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(ExecutionFrame frame) {
        JoinPrepared<L, R> prepared = prepare(frame);
        long count = prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        try {
                            consumer.accept(
                                    leftIndex, rightPresent, rightIndex);
                            return true;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException failure) {
                            throw DataFlowFailures.callback(
                                    "dataflow_join_consumer_failed",
                                    "joined",
                                    "dataflow.join.borrow",
                                    failure);
                        }
                    }
                });
        frame.reserveOutput(1L, 8L, "dataflow.join.borrow");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(count),
                prepared.scanned(),
                count,
                1L,
                1,
                1);
    }
}
