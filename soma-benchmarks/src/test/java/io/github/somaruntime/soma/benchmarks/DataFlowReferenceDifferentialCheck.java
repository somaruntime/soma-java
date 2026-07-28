package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.EntityKind;
import io.github.somaruntime.soma.benchmarks.schema.VariableKind;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactTable;
import io.github.somaruntime.soma.dataflow.CandidateFlow;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.DataFlowInvocation;
import io.github.somaruntime.soma.dataflow.ExecutionBudget;
import io.github.somaruntime.soma.dataflow.ExecutionPolicy;
import io.github.somaruntime.soma.dataflow.GroupedLongResult;
import io.github.somaruntime.soma.dataflow.JoinedIndexResult;
import io.github.somaruntime.soma.dataflow.KeyExpression;
import io.github.somaruntime.soma.dataflow.LongColumnResult;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.PartialWindowPolicy;
import io.github.somaruntime.soma.dataflow.WindowIndexResult;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import java.util.Arrays;
import java.util.Random;

/**
 * Test-only reference differential for the admitted transformation algebra.
 *
 * <p>The oracle deliberately uses plain arrays and direct loops. It neither
 * depends on the production planner nor freezes a private physical layout.</p>
 */
public final class DataFlowReferenceDifferentialCheck {
    private static final long SEED = 0x534f4d4152454631L;
    private static final int TRIALS = 48;

    private DataFlowReferenceDifferentialCheck() {
    }

    public static void main(String[] args) {
        Random random = new Random(SEED);
        DataFlowContext sequential = DataFlowContext.sequential();
        try {
            for (int trial = 0; trial < TRIALS; trial++) {
                verifyTrial(random, sequential, trial);
            }
            verifySequentialParallelIdentity();
        } finally {
            sequential.close();
        }
        System.out.println(
                "dataflow-reference-differential-check: ok"
                        + " seed=" + Long.toHexString(SEED)
                        + " trials=" + TRIALS);
    }

    private static void verifyTrial(
            Random random,
            DataFlowContext context,
            int trial) {
        Fixture fixture = Fixture.random(random, random.nextInt(65));
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(fixture.batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts-" + trial);
        try {
            int lower = random.nextInt(fixture.size + 3);
            long upperEntityId = 1L + random.nextInt(14);
            int skip = random.nextInt(5);
            int limit = random.nextInt(10);

            CandidateFlow<NumericFactDataFlow.Binding> candidates =
                    source.candidates()
                            .filter(source.columns().factIndex()
                                    .greaterThanOrEqualTo(lower)
                                    .and(source.columns().entityId()
                                            .lessThan(upperEntityId)))
                            .sortedBy(source.columns().entityId().ascending()
                                    .then(source.columns().factIndex()
                                            .descending()))
                            .skip(skip)
                            .limit(limit);
            int[] expected = fixture.candidates(
                    lower, upperEntityId, skip, limit);

            verifyCandidateAndProjection(
                    candidates, source, table, context, fixture, expected);
            verifyPartition(source, table, context, fixture);
            verifyGroups(source, table, context, fixture, lower);
            verifyWindows(
                    candidates,
                    source,
                    table,
                    context,
                    fixture,
                    expected,
                    random);
            verifyJoins(source, table, context, fixture, random);
        } catch (AssertionError failure) {
            throw new AssertionError(
                    "reference differential trial " + trial
                            + " failed for size " + fixture.size,
                    failure);
        } finally {
            table.release();
        }
    }

    private static void verifyCandidateAndProjection(
            CandidateFlow<NumericFactDataFlow.Binding> candidates,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context,
            Fixture fixture,
            int[] expected) {
        IndexSnapshot indexes = execute(
                candidates.indexSnapshot(), source, table, context);
        require(indexes.size() == expected.length,
                "candidate cardinality");
        for (int position = 0; position < expected.length; position++) {
            require(indexes.indexAt(position) == expected[position],
                    "candidate order");
        }

        LongColumnResult projected = execute(
                candidates.project(source.columns().entityId()).toColumn(),
                source,
                table,
                context);
        LongScalarResult sum = execute(
                candidates.project(source.columns().entityId()).sum(),
                source,
                table,
                context);
        LongColumnResult prefix = execute(
                candidates.project(source.columns().entityId())
                        .inclusivePrefixSum(),
                source,
                table,
                context);
        long expectedSum = 0L;
        require(projected.size() == expected.length
                        && prefix.size() == expected.length,
                "projected cardinality");
        for (int position = 0; position < expected.length; position++) {
            expectedSum += fixture.entityIds[expected[position]];
            require(projected.valueAt(position)
                            == fixture.entityIds[expected[position]],
                    "projected value");
            require(prefix.valueAt(position) == expectedSum,
                    "prefix order");
        }
        require(sum.value() == expectedSum, "projected sum");
    }

    private static void verifyPartition(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context,
            Fixture fixture) {
        LongColumnResult actual = execute(
                source.candidates()
                        .partition(source.columns().entityKind()
                                .equalTo(EntityKind.PRIMARY.ordinal()))
                        .combine()
                        .project(source.columns().factIndex())
                        .toColumn(),
                source,
                table,
                context);
        int[] expected = fixture.partitionedIndexes();
        require(actual.size() == expected.length,
                "partition cardinality");
        for (int position = 0; position < expected.length; position++) {
            require(actual.valueAt(position) == expected[position],
                    "partition branch order");
        }
    }

    private static void verifyGroups(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context,
            Fixture fixture,
            int lower) {
        KeyExpression<NumericFactDataFlow.Binding> key =
                KeyExpression.of(source.columns().entityKind());
        GroupedLongResult counts = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(lower))
                        .groupBy(key)
                        .counts(),
                source,
                table,
                context);
        GroupedLongResult sums = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(lower))
                        .groupBy(key)
                        .sum(source.columns().entityId()),
                source,
                table,
                context);
        GroupReference expected = fixture.groups(lower);
        require(counts.size() == expected.size
                        && sums.size() == expected.size,
                "group cardinality");
        for (int group = 0; group < expected.size; group++) {
            require(counts.representativeIndexAt(group)
                            == expected.representatives[group]
                            && sums.representativeIndexAt(group)
                                    == expected.representatives[group],
                    "first-key group order");
            require(counts.valueAt(group) == expected.counts[group],
                    "group count");
            require(sums.valueAt(group) == expected.sums[group],
                    "group sum");
        }
    }

    private static void verifyWindows(
            CandidateFlow<NumericFactDataFlow.Binding> candidates,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context,
            Fixture fixture,
            int[] candidateIndexes,
            Random random) {
        int width = 1 + random.nextInt(7);
        int step = 1 + random.nextInt(6);
        PartialWindowPolicy policy = random.nextBoolean()
                ? PartialWindowPolicy.INCLUDE_PARTIAL
                : PartialWindowPolicy.DROP_PARTIAL;
        WindowReference expected = fixture.windows(
                candidateIndexes, width, step, policy);
        WindowIndexResult indexes = execute(
                candidates.windowByCount(width, step, policy)
                        .indexSnapshot(),
                source,
                table,
                context);
        LongColumnResult sums = execute(
                candidates.windowByCount(width, step, policy)
                        .sum(source.columns().entityId()),
                source,
                table,
                context);
        require(indexes.windowCount() == expected.count
                        && sums.size() == expected.count,
                "window cardinality");
        for (int window = 0; window < expected.count; window++) {
            require(indexes.windowSize(window)
                            == expected.indexes[window].length,
                    "window width");
            for (int member = 0;
                    member < expected.indexes[window].length;
                    member++) {
                require(indexes.indexAt(window, member)
                                == expected.indexes[window][member],
                        "window member order");
            }
            require(sums.valueAt(window) == expected.sums[window],
                    "window aggregate");
        }
    }

    private static void verifyJoins(
            NumericFactDataFlow.Source unused,
            NumericFactTable table,
            DataFlowContext context,
            Fixture fixture,
            Random random) {
        int leftLower = random.nextInt(fixture.size + 3);
        int rightUpper = random.nextInt(fixture.size + 3);
        NumericFactDataFlow.Source left =
                NumericFactDataFlow.source(0, "left");
        NumericFactDataFlow.Source right =
                NumericFactDataFlow.source(1, "right");
        CandidateFlow<NumericFactDataFlow.Binding> leftCandidates =
                left.candidates().filter(left.columns().factIndex()
                        .greaterThanOrEqualTo(leftLower));
        CandidateFlow<NumericFactDataFlow.Binding> rightCandidates =
                right.candidates().filter(right.columns().factIndex()
                        .lessThan(rightUpper));

        PairReference innerExpected = fixture.join(
                leftLower, rightUpper, false);
        PairReference outerExpected = fixture.join(
                leftLower, rightUpper, true);
        JoinedIndexResult inner = execute(
                leftCandidates.innerJoin(rightCandidates)
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .indexSnapshot(),
                left,
                right,
                table,
                context);
        JoinedIndexResult outer = execute(
                leftCandidates.leftOuterJoin(rightCandidates)
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .indexSnapshot(),
                left,
                right,
                table,
                context);
        requirePairs(inner, innerExpected, "inner join");
        requirePairs(outer, outerExpected, "left outer join");

        IndexSnapshot semi = execute(
                leftCandidates.leftSemiJoin(rightCandidates)
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .leftIndexSnapshot(),
                left,
                right,
                table,
                context);
        IndexSnapshot anti = execute(
                leftCandidates.leftAntiJoin(rightCandidates)
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .leftIndexSnapshot(),
                left,
                right,
                table,
                context);
        int[] expectedSemi = fixture.semiOrAnti(
                leftLower, rightUpper, true);
        int[] expectedAnti = fixture.semiOrAnti(
                leftLower, rightUpper, false);
        requireIndexes(semi, expectedSemi, "left semi join");
        requireIndexes(anti, expectedAnti, "left anti join");
    }

    private static void verifySequentialParallelIdentity() {
        Random random = new Random(SEED ^ 0x504152414c4c454cL);
        Fixture fixture = Fixture.random(random, 8192);
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(fixture.batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("parallel-facts");
        DataFlowContext sequential = DataFlowContext.sequential();
        DataFlowContext parallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(128),
                ExecutionBudget.defaults());
        try {
            DataFlowDefinition<LongColumnResult> projection =
                    source.candidates()
                            .filter(source.columns().entityId().lessThan(9L))
                            .project(source.columns().entityId()
                                    .multipliedBy(3L)
                                    .plus(source.columns().factIndex()))
                            .toColumn();
            DataFlowDefinition<LongScalarResult> reduction =
                    source.candidates()
                            .filter(source.columns().entityId().lessThan(9L))
                            .project(source.columns().entityId())
                            .sum();
            LongColumnResult sequentialProjection = execute(
                    projection, source, table, sequential);
            LongColumnResult parallelProjection = execute(
                    projection, source, table, parallel);
            require(sequentialProjection.size() == parallelProjection.size(),
                    "parallel projection cardinality");
            for (int index = 0;
                    index < sequentialProjection.size();
                    index++) {
                require(sequentialProjection.valueAt(index)
                                == parallelProjection.valueAt(index),
                        "parallel projection identity");
            }
            require(execute(reduction, source, table, sequential).value()
                            == execute(reduction, source, table, parallel).value(),
                    "parallel fixed-tree integral identity");
        } finally {
            parallel.close();
            sequential.close();
            table.release();
        }
    }

    private static void requirePairs(
            JoinedIndexResult actual,
            PairReference expected,
            String message) {
        require(actual.size() == expected.size, message + " cardinality");
        for (int position = 0; position < expected.size; position++) {
            require(actual.leftIndexAt(position) == expected.left[position],
                    message + " left order");
            require(actual.isRightPresent(position)
                            == expected.rightPresent[position],
                    message + " presence");
            if (expected.rightPresent[position]) {
                require(actual.rightIndexAt(position)
                                == expected.right[position],
                        message + " right order");
            }
        }
    }

    private static void requireIndexes(
            IndexSnapshot actual,
            int[] expected,
            String message) {
        require(actual.size() == expected.length,
                message + " cardinality");
        for (int position = 0; position < expected.length; position++) {
            require(actual.indexAt(position) == expected[position],
                    message + " order");
        }
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context) {
        DataFlowInvocation<R> invocation = definition.compile()
                .newInvocation(context)
                .bind(source, NumericFactDataFlow.bind(table));
        return invocation.execute();
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source left,
            NumericFactDataFlow.Source right,
            NumericFactTable table,
            DataFlowContext context) {
        DataFlowInvocation<R> invocation = definition.compile()
                .newInvocation(context)
                .bind(left, NumericFactDataFlow.bind(table))
                .bind(right, NumericFactDataFlow.bind(table));
        return invocation.execute();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {
        private final int size;
        private final EntityKind[] kinds;
        private final long[] entityIds;
        private final double[] values;

        private Fixture(
                EntityKind[] kinds,
                long[] entityIds,
                double[] values) {
            this.size = entityIds.length;
            this.kinds = kinds;
            this.entityIds = entityIds;
            this.values = values;
        }

        static Fixture random(Random random, int size) {
            EntityKind[] kinds = new EntityKind[size];
            long[] entityIds = new long[size];
            double[] values = new double[size];
            for (int index = 0; index < size; index++) {
                kinds[index] = random.nextBoolean()
                        ? EntityKind.PRIMARY : EntityKind.SECONDARY;
                entityIds[index] = random.nextInt(13);
                values[index] = random.nextInt(41) - 20;
            }
            return new Fixture(kinds, entityIds, values);
        }

        NumericFactBatch batch() {
            NumericFactBatch batch = new NumericFactBatch(size);
            for (int index = 0; index < size; index++) {
                batch.addValues(
                        index,
                        kinds[index],
                        entityIds[index],
                        index % 2 == 0
                                ? VariableKind.VALUE : VariableKind.RATE,
                        values[index],
                        values[index] * 0.5d,
                        1.0d);
            }
            return batch;
        }

        int[] candidates(
                int lower,
                long upperEntityId,
                int skip,
                int limit) {
            int[] selected = new int[size];
            int count = 0;
            for (int index = 0; index < size; index++) {
                if (index >= lower && entityIds[index] < upperEntityId) {
                    selected[count++] = index;
                }
            }
            stableSort(selected, count);
            int from = Math.min(skip, count);
            int to = Math.min(count, from + limit);
            return Arrays.copyOfRange(selected, from, to);
        }

        int[] partitionedIndexes() {
            int[] result = new int[size];
            int write = 0;
            for (int index = 0; index < size; index++) {
                if (kinds[index] == EntityKind.PRIMARY) {
                    result[write++] = index;
                }
            }
            for (int index = 0; index < size; index++) {
                if (kinds[index] != EntityKind.PRIMARY) {
                    result[write++] = index;
                }
            }
            return result;
        }

        GroupReference groups(int lower) {
            int[] representatives = new int[2];
            long[] counts = new long[2];
            long[] sums = new long[2];
            EntityKind[] order = new EntityKind[2];
            int groupCount = 0;
            for (int index = lower; index < size; index++) {
                int group = -1;
                for (int existing = 0;
                        existing < groupCount;
                        existing++) {
                    if (order[existing] == kinds[index]) {
                        group = existing;
                        break;
                    }
                }
                if (group < 0) {
                    group = groupCount++;
                    order[group] = kinds[index];
                    representatives[group] = index;
                }
                counts[group]++;
                sums[group] += entityIds[index];
            }
            return new GroupReference(
                    groupCount,
                    Arrays.copyOf(representatives, groupCount),
                    Arrays.copyOf(counts, groupCount),
                    Arrays.copyOf(sums, groupCount));
        }

        WindowReference windows(
                int[] candidates,
                int width,
                int step,
                PartialWindowPolicy policy) {
            int maximum = candidates.length == 0
                    ? 0 : (candidates.length + step - 1) / step;
            int[][] indexes = new int[maximum][];
            long[] sums = new long[maximum];
            int count = 0;
            for (int start = 0; start < candidates.length; start += step) {
                int end = Math.min(candidates.length, start + width);
                if (end - start < width
                        && policy == PartialWindowPolicy.DROP_PARTIAL) {
                    continue;
                }
                indexes[count] = Arrays.copyOfRange(
                        candidates, start, end);
                long sum = 0L;
                for (int member = start; member < end; member++) {
                    sum += entityIds[candidates[member]];
                }
                sums[count] = sum;
                count++;
            }
            return new WindowReference(
                    count,
                    Arrays.copyOf(indexes, count),
                    Arrays.copyOf(sums, count));
        }

        PairReference join(
                int leftLower,
                int rightUpper,
                boolean outer) {
            int capacity = Math.max(1, size);
            int[] left = new int[capacity];
            int[] right = new int[capacity];
            boolean[] present = new boolean[capacity];
            int count = 0;
            for (int leftIndex = leftLower;
                    leftIndex < size;
                    leftIndex++) {
                boolean matched = false;
                int boundedRight = Math.min(rightUpper, size);
                for (int rightIndex = 0;
                        rightIndex < boundedRight;
                        rightIndex++) {
                    if (entityIds[leftIndex] == entityIds[rightIndex]) {
                        if (count == left.length) {
                            int next = Math.max(1, count * 2);
                            left = Arrays.copyOf(left, next);
                            right = Arrays.copyOf(right, next);
                            present = Arrays.copyOf(present, next);
                        }
                        left[count] = leftIndex;
                        right[count] = rightIndex;
                        present[count] = true;
                        count++;
                        matched = true;
                    }
                }
                if (outer && !matched) {
                    if (count == left.length) {
                        int next = Math.max(1, count * 2);
                        left = Arrays.copyOf(left, next);
                        right = Arrays.copyOf(right, next);
                        present = Arrays.copyOf(present, next);
                    }
                    left[count] = leftIndex;
                    count++;
                }
            }
            return new PairReference(
                    count,
                    Arrays.copyOf(left, count),
                    Arrays.copyOf(right, count),
                    Arrays.copyOf(present, count));
        }

        int[] semiOrAnti(
                int leftLower,
                int rightUpper,
                boolean semi) {
            int[] result = new int[size];
            int count = 0;
            int boundedRight = Math.min(rightUpper, size);
            for (int leftIndex = leftLower;
                    leftIndex < size;
                    leftIndex++) {
                boolean matched = false;
                for (int rightIndex = 0;
                        rightIndex < boundedRight;
                        rightIndex++) {
                    if (entityIds[leftIndex] == entityIds[rightIndex]) {
                        matched = true;
                        break;
                    }
                }
                if (matched == semi) {
                    result[count++] = leftIndex;
                }
            }
            return Arrays.copyOf(result, count);
        }

        private void stableSort(int[] indexes, int count) {
            for (int current = 1; current < count; current++) {
                int value = indexes[current];
                int position = current;
                while (position > 0
                        && compare(value, indexes[position - 1]) < 0) {
                    indexes[position] = indexes[position - 1];
                    position--;
                }
                indexes[position] = value;
            }
        }

        private int compare(int left, int right) {
            int byEntity = Long.compare(
                    entityIds[left], entityIds[right]);
            if (byEntity != 0) {
                return byEntity;
            }
            return Integer.compare(right, left);
        }
    }

    private static final class GroupReference {
        private final int size;
        private final int[] representatives;
        private final long[] counts;
        private final long[] sums;

        private GroupReference(
                int size,
                int[] representatives,
                long[] counts,
                long[] sums) {
            this.size = size;
            this.representatives = representatives;
            this.counts = counts;
            this.sums = sums;
        }
    }

    private static final class WindowReference {
        private final int count;
        private final int[][] indexes;
        private final long[] sums;

        private WindowReference(
                int count,
                int[][] indexes,
                long[] sums) {
            this.count = count;
            this.indexes = indexes;
            this.sums = sums;
        }
    }

    private static final class PairReference {
        private final int size;
        private final int[] left;
        private final int[] right;
        private final boolean[] rightPresent;

        private PairReference(
                int size,
                int[] left,
                int[] right,
                boolean[] rightPresent) {
            this.size = size;
            this.left = left;
            this.right = right;
            this.rightPresent = rightPresent;
        }
    }
}
