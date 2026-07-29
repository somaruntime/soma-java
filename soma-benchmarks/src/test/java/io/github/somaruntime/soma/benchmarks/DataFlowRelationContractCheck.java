package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.EntityKind;
import io.github.somaruntime.soma.benchmarks.schema.CandidateKey;
import io.github.somaruntime.soma.benchmarks.schema.CategoryId;
import io.github.somaruntime.soma.benchmarks.schema.GroupId;
import io.github.somaruntime.soma.benchmarks.schema.ItemId;
import io.github.somaruntime.soma.benchmarks.schema.NamespaceId;
import io.github.somaruntime.soma.benchmarks.schema.VariableKind;
import io.github.somaruntime.soma.benchmarks.schema.WorkKey;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.GroupCandidateTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactTable;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.GroupIndexResult;
import io.github.somaruntime.soma.dataflow.GroupedLongResult;
import io.github.somaruntime.soma.dataflow.ExpandedIndexResult;
import io.github.somaruntime.soma.dataflow.JoinedIndexResult;
import io.github.somaruntime.soma.dataflow.KeyExpression;
import io.github.somaruntime.soma.dataflow.LongColumnResult;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.PartialWindowPolicy;
import io.github.somaruntime.soma.dataflow.WindowIndexResult;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

/** Partition/Combine、GroupBy、Join 与 finite Window 关系能力契约。 */
public final class DataFlowRelationContractCheck {
    private DataFlowRelationContractCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");

        LongColumnResult combined = execute(
                source.candidates()
                        .partition(source.columns().entityKind()
                                .equalTo(EntityKind.PRIMARY))
                        .combine()
                        .project(source.columns().factIndex())
                        .toColumn(),
                source,
                table);
        int[] expectedCombined = new int[] {0, 2, 4, 6, 1, 3, 5, 7};
        require(combined.size() == expectedCombined.length,
                "partition combine cardinality");
        for (int index = 0; index < expectedCombined.length; index++) {
            require(combined.valueAt(index) == expectedCombined[index],
                    "partition combine declaration order");
        }

        KeyExpression<NumericFactDataFlow.Binding> kindKey =
                KeyExpression.of(source.columns().entityKind());
        GroupIndexResult groups = execute(
                source.candidates().groupBy(kindKey).indexSnapshot(),
                source,
                table);
        require(groups.groupCount() == 2
                        && groups.representativeIndex(0) == 0
                        && groups.representativeIndex(1) == 1,
                "group first-key order");
        require(groups.groupSize(0) == 4
                        && groups.indexAt(0, 0) == 0
                        && groups.indexAt(0, 1) == 2
                        && groups.indexAt(0, 2) == 4
                        && groups.indexAt(0, 3) == 6,
                "stable group member order");

        GroupedLongResult counts = execute(
                source.candidates().groupBy(kindKey).counts(),
                source,
                table);
        GroupedLongResult sums = execute(
                source.candidates().groupBy(kindKey)
                        .sum(source.columns().entityId()),
                source,
                table);
        require(counts.size() == 2
                        && counts.valueAt(0) == 4L
                        && counts.valueAt(1) == 4L,
                "group counts");
        require(sums.valueAt(0) == 412L && sums.valueAt(1) == 416L,
                "group long sum");

        WindowIndexResult windows = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .indexSnapshot(),
                source,
                table);
        require(windows.windowCount() == 4
                        && windows.windowSize(0) == 3
                        && windows.windowSize(3) == 2
                        && windows.indexAt(1, 0) == 2,
                "count window boundaries");
        LongColumnResult windowSums = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .sum(source.columns().factIndex()),
                source,
                table);
        require(windowSums.size() == 4
                        && windowSums.valueAt(0) == 3L
                        && windowSums.valueAt(1) == 9L
                        && windowSums.valueAt(2) == 15L
                        && windowSums.valueAt(3) == 13L,
                "count window aggregates");
        WindowIndexResult timeWindows = execute(
                source.candidates()
                        .windowByTime(
                                source.columns().factIndex(),
                                3L,
                                2L,
                                0L,
                                PartialWindowPolicy.INCLUDE_PARTIAL)
                        .indexSnapshot(),
                source,
                table);
        require(timeWindows.windowCount() == 4
                        && timeWindows.indexAt(2, 0) == 4,
                "time window half-open membership");

        NumericFactDataFlow.Source left =
                NumericFactDataFlow.source(0, "left");
        NumericFactDataFlow.Source right =
                NumericFactDataFlow.source(1, "right");
        LongScalarResult inner = execute(
                left.candidates()
                        .innerJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .count(),
                left,
                right,
                table);
        LongScalarResult outer = execute(
                left.candidates()
                        .leftOuterJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .count(),
                left,
                right,
                table);
        LongScalarResult semi = execute(
                left.candidates()
                        .leftSemiJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .count(),
                left,
                right,
                table);
        LongScalarResult anti = execute(
                left.candidates()
                        .leftAntiJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .count(),
                left,
                right,
                table);
        require(inner.value() == 4L
                        && outer.value() == 8L
                        && semi.value() == 4L
                        && anti.value() == 4L,
                "join preservation variants");

        JoinedIndexResult outerIndexes = execute(
                left.candidates()
                        .leftOuterJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .indexSnapshot(),
                left,
                right,
                table);
        require(outerIndexes.size() == 8
                        && outerIndexes.isRightPresent(3)
                        && !outerIndexes.isRightPresent(4),
                "outer join explicit side presence");

        JoinedIndexResult duplicates = execute(
                left.candidates()
                        .filter(left.columns().factIndex().lessThan(2L))
                        .innerJoin(right.candidates())
                        .on(left.columns().entityKind(),
                                right.columns().entityKind())
                        .indexSnapshot(),
                left,
                right,
                table);
        require(duplicates.size() == 8
                        && duplicates.leftIndexAt(0) == 0
                        && duplicates.rightIndexAt(0) == 0
                        && duplicates.rightIndexAt(1) == 2
                        && duplicates.rightIndexAt(2) == 4
                        && duplicates.rightIndexAt(3) == 6,
                "join multiplicity and stable right order");

        IndexSnapshot antiIndexes = execute(
                left.candidates()
                        .leftAntiJoin(right.candidates()
                                .filter(right.columns().factIndex().lessThan(4L)))
                        .on(left.columns().entityId(), right.columns().entityId())
                        .leftIndexSnapshot(),
                left,
                right,
                table);
        require(antiIndexes.size() == 4
                        && antiIndexes.indexAt(0) == 4
                        && antiIndexes.indexAt(3) == 7,
                "anti join candidate lineage");
        table.requireCurrent(antiIndexes);

        table.release();
        verifyTimeWindowCountOverflow();
        verifyExactSource();
        verifyOwnedChildExpand();
        System.out.println("dataflow-relation-contract: ok");
    }

    private static void verifyTimeWindowCountOverflow() {
        NumericFactBatch batch = new NumericFactBatch(2);
        batch.addValues(
                0,
                EntityKind.PRIMARY,
                0L,
                VariableKind.VALUE,
                0L,
                0.0d,
                1.0d);
        batch.addValues(
                1,
                EntityKind.PRIMARY,
                Long.MAX_VALUE,
                VariableKind.VALUE,
                1L,
                0.0d,
                1.0d);
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch);
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("time-overflow");
        boolean rejected = false;
        try {
            execute(
                    source.candidates()
                            .windowByTime(
                                    source.columns().entityId(),
                                    1L,
                                    1L,
                                    0L,
                                    PartialWindowPolicy.INCLUDE_PARTIAL)
                            .indexSnapshot(),
                    source,
                    table);
        } catch (SomaRuntimeException expected) {
            rejected = true;
            require("dataflow_window_count_overflow".equals(expected.code()),
                    "time window count overflow failure code");
        } finally {
            table.release();
        }
        require(rejected, "time window count overflow must fail closed");
    }

    private static void verifyExactSource() {
        GroupCandidateTable table = GroupCandidateTable.create();
        GroupCandidateBatch batch = new GroupCandidateBatch(6);
        CategoryId category = new CategoryId(1L);
        for (int index = 0; index < 6; index++) {
            batch.addValues(
                    new CandidateKey(
                            new WorkKey(
                                    new NamespaceId(3L),
                                    new ItemId(index / 2)),
                            new GroupId(index % 2)),
                    category,
                    index,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    false);
        }
        table.addBatch(batch);
        GroupCandidateDataFlow.Source source =
                GroupCandidateDataFlow.source("candidates");
        LongScalarResult groupCount = execute(
                source.candidatesByGroup(new GroupId(1L)).count(),
                source,
                table);
        LongColumnResult groupIndexes = execute(
                source.candidatesByGroup(new GroupId(1L))
                        .project(source.columns().metric0())
                        .toColumn(),
                source,
                table);
        LongScalarResult missing = execute(
                source.candidatesByGroup(new GroupId(99L)).count(),
                source,
                table);
        require(groupCount.value() == 3L
                        && groupIndexes.size() == 3
                        && groupIndexes.valueAt(0) == 5L
                        && groupIndexes.valueAt(1) == 3L
                        && groupIndexes.valueAt(2) == 1L
                        && missing.value() == 0L,
                "exact source uses maintained group order and empty semantics");
        table.release();
    }

    private static void verifyOwnedChildExpand() {
        OwnerFactBatch batch = new OwnerFactBatch(3);
        CategoryId category = new CategoryId(4L);
        for (int parent = 0; parent < 3; parent++) {
            OwnedOptionBatch options = new OwnedOptionBatch(parent + 1);
            for (int child = 0; child <= parent; child++) {
                options.addValues(
                        new GroupId(child % 2),
                        10L * parent + child);
            }
            batch.addValues(
                    new WorkKey(
                            new NamespaceId(7L),
                            new ItemId(parent)),
                    parent,
                    parent * 100L,
                    category,
                    options);
        }
        OwnerFactTable table = OwnerFactTable.create();
        table.addBatch(batch);
        OwnerFactDataFlow.Source owners =
                OwnerFactDataFlow.source("owners");
        OwnedOptionDataFlow.Source options =
                OwnedOptionDataFlow.source("options");
        LongScalarResult count = execute(
                owners.expandOptions(options).count(),
                owners,
                table);
        LongColumnResult costs = execute(
                owners.expandOptions(
                                owners.candidates()
                                        .filter(owners.columns().sequence()
                                                .greaterThan(0L)),
                                options)
                        .filterChild(options.columns().groupIdValue()
                                .equalTo(0L))
                        .projectChild(options.columns().cost())
                        .toColumn(),
                owners,
                table);
        ExpandedIndexResult indexes = execute(
                owners.expandOptions(options).indexes(),
                owners,
                table);
        require(count.value() == 6L
                        && costs.size() == 3
                        && costs.valueAt(0) == 10L
                        && costs.valueAt(1) == 20L
                        && costs.valueAt(2) == 22L,
                "owned child expand parent/child order and projection");
        require(indexes.size() == 6
                        && indexes.parentIndexAt(0) == 0
                        && indexes.childIndexAt(0) == 0
                        && indexes.parentIndexAt(5) == 2
                        && indexes.childIndexAt(5) == 2,
                "owned child expand current-index coordinates");
        table.release();
    }

    private static NumericFactBatch batch() {
        NumericFactBatch batch = new NumericFactBatch(8);
        for (int index = 0; index < 8; index++) {
            batch.addValues(
                    index,
                    index % 2 == 0 ? EntityKind.PRIMARY : EntityKind.SECONDARY,
                    100L + index,
                    index % 2 == 0 ? VariableKind.VALUE : VariableKind.RATE,
                    index,
                    index * 0.5d,
                    1.0d);
        }
        return batch;
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
                    .execute();
        } finally {
            context.close();
        }
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            OwnerFactDataFlow.Source source,
            OwnerFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, OwnerFactDataFlow.bind(table))
                    .execute();
        } finally {
            context.close();
        }
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            GroupCandidateDataFlow.Source source,
            GroupCandidateTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, GroupCandidateDataFlow.bind(table))
                    .execute();
        } finally {
            context.close();
        }
    }

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source left,
            NumericFactDataFlow.Source right,
            NumericFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(left, NumericFactDataFlow.bind(table))
                    .bind(right, NumericFactDataFlow.bind(table))
                    .execute();
        } finally {
            context.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
