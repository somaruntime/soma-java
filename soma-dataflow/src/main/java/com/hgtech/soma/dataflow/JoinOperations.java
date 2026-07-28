package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

interface JoinMatchConsumer {
    boolean accept(int leftIndex, boolean rightPresent, int rightIndex);
}

final class JoinPrepared<
        L extends DataFlowBinding, R extends DataFlowBinding> {
    static final JoinMatchConsumer COUNTER = new JoinMatchConsumer() {
        @Override
        public boolean accept(
                int leftIndex, boolean rightPresent, int rightIndex) {
            return true;
        }
    };

    final CandidateSelection left;
    final CandidateSelection right;
    final DataFlowBinding leftBinding;
    final DataFlowBinding rightBinding;
    final KeyExpression<L> leftKey;
    final KeyExpression<R> rightKey;
    final JoinType type;
    final int[] buckets;
    final int[] next;
    final JoinedStagePlan<L, R> stages;

    JoinPrepared(
            CandidateSelection left,
            CandidateSelection right,
            DataFlowBinding leftBinding,
            DataFlowBinding rightBinding,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            int[] buckets,
            int[] next,
            JoinedStagePlan<L, R> stages) {
        this.left = left;
        this.right = right;
        this.leftBinding = leftBinding;
        this.rightBinding = rightBinding;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
        this.buckets = buckets;
        this.next = next;
        this.stages = stages;
    }

    long scanned() {
        return left.scanned + right.scanned;
    }

    long enumerate(ExecutionFrame frame, JoinMatchConsumer consumer) {
        return stages.isEmpty()
                ? enumerateRaw(frame, consumer)
                : stages.enumerate(frame, this, consumer);
    }

    long enumerateRaw(ExecutionFrame frame, JoinMatchConsumer consumer) {
        long output = 0L;
        int mask = buckets.length - 1;
        for (int leftPosition = 0; leftPosition < left.size; leftPosition++) {
            if ((leftPosition & 1023) == 0) {
                frame.checkBoundary("dataflow.join");
            }
            int leftIndex = left.indexAt(leftPosition);
            int bucket = bucket(
                    leftKey.hash(frame, leftBinding, leftIndex), mask);
            boolean matched = false;
            for (int rightPosition = buckets[bucket];
                 rightPosition >= 0;
                 rightPosition = next[rightPosition]) {
                int rightIndex = right.indexAt(rightPosition);
                if (!leftKey.equal(
                        frame,
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
    final JoinedStagePlan<L, R> stages;

    JoinOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type) {
        this(
                left,
                right,
                leftKey,
                rightKey,
                type,
                JoinedStagePlan.<L, R>empty(),
                Collections.<ParameterSlot<?>>emptyList());
    }

    JoinOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            List<ParameterSlot<?>> additionalParameters) {
        this(
                left,
                right,
                leftKey,
                rightKey,
                type,
                JoinedStagePlan.<L, R>empty(),
                additionalParameters);
    }

    JoinOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            List<ParameterSlot<?>> additionalParameters) {
        super(
                left,
                right,
                DataFlowSupport.unionParameters(
                        DataFlowSupport.unionParameters(
                                DataFlowSupport.unionParameters(
                                        leftKey.parameters(),
                                        rightKey.parameters()),
                                stages.parameters()),
                        additionalParameters));
        this.left = left;
        this.right = right;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;
        this.stages = stages;
    }

    @Override
    public final String canonicalForm() {
        return "join(" + type + "," + left.canonical() + ","
                + right.canonical() + "," + leftKey.identity() + ","
                + rightKey.identity() + "," + stages.canonical()
                + ")->" + terminal();
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
                + (stages.isEmpty()
                ? "" : " -> JoinedStages")
                + " -> " + terminal();
    }

    @Override
    public final String physicalPlan() {
        return "left-driven-hash-join[stable-right-chain,"
                + (stages.hasSort()
                ? "sort-barrier" : "stage-fused-stream")
                + "," + terminal() + "]";
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
            int index = rightSelection.indexAt(position);
            long hash = rightKey.hash(frame, rightBinding, index);
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
                next,
                stages);
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

    JoinCountOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                Collections.<ParameterSlot<?>>emptyList());
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

    JoinIndexOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                Collections.<ParameterSlot<?>>emptyList());
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

    JoinLeftIndexOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                Collections.<ParameterSlot<?>>emptyList());
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

final class JoinDeliveryOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, DeliveryResult> {
    private final ParameterSlot<JoinedIndexVisitor> visitorSlot;

    JoinDeliveryOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            ParameterSlot<JoinedIndexVisitor> visitorSlot) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                Collections.<ParameterSlot<?>>singletonList(visitorSlot));
        this.visitorSlot = visitorSlot;
    }

    JoinDeliveryOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            ParameterSlot<JoinedIndexVisitor> visitorSlot) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                Collections.<ParameterSlot<?>>singletonList(visitorSlot));
        this.visitorSlot = visitorSlot;
    }

    @Override
    String terminal() {
        return "deliver(joined-index)";
    }

    @Override
    public ResultDeliveryMode resultDeliveryMode() {
        return ResultDeliveryMode.CALLBACK_SCOPED;
    }

    @Override
    public ExecutionOutcome<DeliveryResult> execute(ExecutionFrame frame) {
        long leftMaximum =
                left.maximumCardinality(frame.binding(left.source()));
        long rightMaximum =
                right.maximumCardinality(frame.binding(right.source()));
        long maximum = type == JoinType.LEFT_SEMI
                || type == JoinType.LEFT_ANTI
                ? leftMaximum
                : multiplySaturated(
                        leftMaximum,
                        type == JoinType.LEFT_OUTER
                                ? Math.max(1L, rightMaximum)
                                : rightMaximum);
        maximum = stages.upperBound(maximum);
        frame.preflightDelivery(
                maximum,
                multiplySaturated(maximum, 9L),
                "dataflow.join.deliver");
        JoinPrepared<L, R> prepared = prepare(frame);
        final JoinedIndexVisitor visitor = frame.parameter(visitorSlot);
        final boolean[] completed = new boolean[] {true};
        long count = prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        try {
                            boolean more = visitor.visit(
                                    leftIndex, rightPresent, rightIndex);
                            if (!more) completed[0] = false;
                            return more;
                        } catch (SomaRuntimeException failure) {
                            throw failure;
                        } catch (RuntimeException failure) {
                            throw DataFlowFailures.callback(
                                    "dataflow_join_delivery_callback",
                                    "joined",
                                    "dataflow.join.deliver",
                                    failure);
                        }
                    }
                });
        return new ExecutionOutcome<DeliveryResult>(
                new DeliveryResult(count, completed[0]),
                prepared.scanned(),
                count,
                count,
                1,
                1);
    }

    private static long multiplySaturated(long first, long second) {
        return first == 0L || second == 0L
                ? 0L
                : first > Long.MAX_VALUE / second
                ? Long.MAX_VALUE : first * second;
    }
}

final class JoinLongProjectionOperation<
        L extends DataFlowBinding,
        R extends DataFlowBinding,
        O> extends JoinOperation<L, R, O> {
    private final LongExpression<?> expression;
    private final boolean rightProjection;

    private JoinLongProjectionOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            LongExpression<?> expression,
            boolean rightProjection) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                expression.parameters);
        this.expression = expression;
        this.rightProjection = rightProjection;
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinLongProjectionOperation<L, R, LongColumnResult> left(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            LongExpression<L> expression) {
        return new JoinLongProjectionOperation<
                L, R, LongColumnResult>(
                left,
                right,
                leftKey,
                rightKey,
                type,
                JoinedStagePlan.<L, R>empty(),
                expression,
                false);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinLongProjectionOperation<L, R, OptionalLongColumnResult> right(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            LongExpression<R> expression) {
        return new JoinLongProjectionOperation<
                L, R, OptionalLongColumnResult>(
                left,
                right,
                leftKey,
                rightKey,
                type,
                JoinedStagePlan.<L, R>empty(),
                expression,
                true);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinLongProjectionOperation<L, R, LongColumnResult> left(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            LongExpression<L> expression) {
        return new JoinLongProjectionOperation<
                L, R, LongColumnResult>(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                expression,
                false);
    }

    static <L extends DataFlowBinding, R extends DataFlowBinding>
    JoinLongProjectionOperation<L, R, OptionalLongColumnResult> right(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            LongExpression<R> expression) {
        return new JoinLongProjectionOperation<
                L, R, OptionalLongColumnResult>(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                expression,
                true);
    }

    @Override
    String terminal() {
        return rightProjection
                ? "project-right-long" : "project-left-long";
    }

    @Override
    public ExecutionOutcome<O> execute(final ExecutionFrame frame) {
        final JoinPrepared<L, R> prepared = prepare(frame);
        long cardinality = prepared.enumerate(frame, new JoinMatchConsumer() {
            @Override
            public boolean accept(
                    int leftIndex,
                    boolean rightPresent,
                    int rightIndex) {
                return true;
            }
        });
        if (cardinality > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    left.source().alias(),
                    "dataflow.join.project",
                    Long.toString(cardinality));
        }
        final int size = (int) cardinality;
        final long[] values =
                frame.newOutputLongs(size, "dataflow.join.project");
        final boolean[] presence = rightProjection
                ? frame.newOutputBooleans(size, "dataflow.join.project")
                : null;
        final int[] write = new int[1];
        prepared.enumerate(frame, new JoinMatchConsumer() {
            @Override
            public boolean accept(
                    int leftIndex,
                    boolean rightPresent,
                    int rightIndex) {
                int position = write[0]++;
                if (rightProjection) {
                    presence[position] = rightPresent;
                    if (rightPresent) {
                        @SuppressWarnings("unchecked")
                        LongExpression<R> value =
                                (LongExpression<R>) expression;
                        values[position] = value.evaluate(
                                frame,
                                prepared.rightBinding,
                                rightIndex);
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    LongExpression<L> value =
                            (LongExpression<L>) expression;
                    values[position] = value.evaluate(
                            frame,
                            prepared.leftBinding,
                            leftIndex);
                }
                return true;
            }
        });
        Object result = rightProjection
                ? new OptionalLongColumnResult(values, presence)
                : new LongColumnResult(values, size);
        @SuppressWarnings("unchecked")
        O typed = (O) result;
        return new ExecutionOutcome<O>(
                typed,
                prepared.scanned(),
                size,
                size,
                1,
                1);
    }
}

final class JoinLeftGroupCountOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, GroupedLongResult> {
    private final KeyExpression<L> groupKey;

    JoinLeftGroupCountOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            KeyExpression<L> groupKey) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                groupKey.parameters());
        this.groupKey = groupKey;
    }

    @Override
    String terminal() {
        return "group-counts-by-left(" + groupKey.identity() + ")";
    }

    @Override
    public ExecutionOutcome<GroupedLongResult> execute(
            final ExecutionFrame frame) {
        final JoinPrepared<L, R> prepared = prepare(frame);
        int maximumGroups = prepared.left.size;
        int capacity = bucketCapacity(maximumGroups);
        final int[] buckets = frame.newScratchIndexes(
                capacity, "dataflow.join.groupByLeft");
        final int[] next = frame.newScratchIndexes(
                maximumGroups, "dataflow.join.groupByLeft");
        final int[] representatives = frame.newScratchIndexes(
                maximumGroups, "dataflow.join.groupByLeft");
        final long[] counts = frame.newScratchLongs(
                maximumGroups, "dataflow.join.groupByLeft");
        Arrays.fill(buckets, -1);
        final int mask = capacity - 1;
        final int[] groups = new int[1];
        long cardinality = prepared.enumerate(
                frame,
                new JoinMatchConsumer() {
                    @Override
                    public boolean accept(
                            int leftIndex,
                            boolean rightPresent,
                            int rightIndex) {
                        long hash = groupKey.hash(
                                frame, prepared.leftBinding, leftIndex);
                        int bucket =
                                ((int) (hash ^ (hash >>> 32))) & mask;
                        int group = buckets[bucket];
                        while (group >= 0 && !groupKey.equal(
                                frame,
                                prepared.leftBinding,
                                leftIndex,
                                groupKey,
                                prepared.leftBinding,
                                representatives[group])) {
                            group = next[group];
                        }
                        if (group < 0) {
                            group = groups[0]++;
                            representatives[group] = leftIndex;
                            next[group] = buckets[bucket];
                            buckets[bucket] = group;
                        }
                        counts[group]++;
                        return true;
                    }
                });
        frame.reserveOutput(
                groups[0],
                (long) groups[0] * 12L,
                "dataflow.join.groupByLeft");
        GroupedLongResult result = new GroupedLongResult(
                left.source().alias(),
                prepared.leftBinding.structuralEpoch(),
                Arrays.copyOf(representatives, groups[0]),
                Arrays.copyOf(counts, groups[0]));
        return new ExecutionOutcome<GroupedLongResult>(
                result,
                prepared.scanned(),
                cardinality,
                groups[0],
                1,
                1);
    }

    private static int bucketCapacity(int size) {
        int target = size >= (1 << 29)
                ? 1 << 30 : Math.max(4, size * 2);
        int capacity = 1;
        while (capacity < target) {
            capacity <<= 1;
        }
        return capacity;
    }
}

final class JoinCountWindowOperation<
        L extends DataFlowBinding, R extends DataFlowBinding>
        extends JoinOperation<L, R, LongColumnResult> {
    private final int width;
    private final int step;
    private final PartialWindowPolicy partialPolicy;

    JoinCountWindowOperation(
            CandidateProgram<L> left,
            CandidateProgram<R> right,
            KeyExpression<L> leftKey,
            KeyExpression<R> rightKey,
            JoinType type,
            JoinedStagePlan<L, R> stages,
            int width,
            int step,
            PartialWindowPolicy partialPolicy) {
        super(
                left,
                right,
                leftKey,
                rightKey,
                type,
                stages,
                Collections.<ParameterSlot<?>>emptyList());
        if (width <= 0 || step <= 0) {
            throw new IllegalArgumentException(
                    "window width and step must be positive");
        }
        if (partialPolicy == null) {
            throw new NullPointerException("partialPolicy");
        }
        this.width = width;
        this.step = step;
        this.partialPolicy = partialPolicy;
    }

    @Override
    String terminal() {
        return "count-window(" + width + "," + step + ","
                + partialPolicy + ")";
    }

    @Override
    public ExecutionOutcome<LongColumnResult> execute(
            ExecutionFrame frame) {
        JoinPrepared<L, R> prepared = prepare(frame);
        long cardinality = prepared.enumerate(
                frame, JoinPrepared.COUNTER);
        long estimated = cardinality == 0L
                ? 0L : (cardinality - 1L) / step + 1L;
        if (estimated > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_window_count_overflow",
                    left.source().alias(),
                    "dataflow.join.window",
                    Long.toString(estimated));
        }
        long[] counts = frame.newOutputLongs(
                (int) estimated, "dataflow.join.window");
        int windows = 0;
        for (long anchor = 0L;
             anchor < cardinality;
             anchor += step) {
            long end = Math.min(cardinality, anchor + width);
            long count = end - anchor;
            if (count < width
                    && partialPolicy
                    == PartialWindowPolicy.DROP_PARTIAL) {
                break;
            }
            counts[windows++] = count;
            if (anchor > Long.MAX_VALUE - step) {
                break;
            }
        }
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(counts, windows),
                prepared.scanned(),
                cardinality,
                windows,
                1,
                1);
    }
}
