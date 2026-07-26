package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowExplain;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.DataFlowStats;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.ExecutionPolicy;
import com.hgtech.soma.dataflow.AbsenceOrder;
import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.JoinedFlow;
import com.hgtech.soma.dataflow.KeyExpression;
import com.hgtech.soma.dataflow.LongColumnResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.LongValueConsumer;
import com.hgtech.soma.dataflow.OpaqueLongFunction;
import com.hgtech.soma.dataflow.OptionalLongColumnResult;
import com.hgtech.soma.dataflow.OutputSlot;
import com.hgtech.soma.dataflow.ParameterSlot;
import com.hgtech.soma.dataflow.PartialWindowPolicy;
import com.hgtech.soma.dataflow.RegisteredLongFunction;
import com.hgtech.soma.dataflow.RegisteredLongReducer;
import com.hgtech.soma.dataflow.StatsMode;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

/**
 * Slice F–I representative contract evidence.
 *
 * <p>The checks target shape closure, parameter/function ownership, finite graph
 * construction and diagnostics boundaries rather than private layouts or
 * repeated method-level validation.</p>
 */
public final class DataFlowSliceFCheck {
    private DataFlowSliceFCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");
        try {
            verifyShapeClosure(source, table);
            verifyParametersAndRegisteredContracts(source, table);
            verifyConstructionInvariants(source, table);
            verifyFiniteGraph(source, table);
            verifyDiagnostics(source, table);
        } finally {
            table.release();
        }
        System.out.println("dataflow-slice-f-check: ok");
    }

    private static void verifyShapeClosure(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        IndexSnapshot minimum = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(2L))
                        .argMinIndexSnapshot(
                                source.columns().entityId()),
                source,
                table);
        IndexSnapshot maximum = execute(
                source.candidates().argMaxIndexSnapshot(
                        source.columns().entityId()),
                source,
                table);
        require(minimum.size() == 1
                        && minimum.indexAt(0) == 2
                        && maximum.size() == 1
                        && maximum.indexAt(0) == 7,
                "arg-min/max preserve current Index lineage");

        final long[] borrowed = new long[1];
        LongScalarResult borrowCount = execute(
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(3L))
                        .project(source.columns().entityId())
                        .borrow(new LongValueConsumer() {
                            @Override
                            public void accept(long value) {
                                borrowed[0] += value;
                            }
                        }),
                source,
                table);
        require(borrowCount.value() == 3L
                        && borrowed[0] == 100L + 101L + 102L,
                "projected borrowed traversal");

        KeyExpression<NumericFactDataFlow.Binding> kind =
                KeyExpression.of(source.columns().entityKind());
        GroupedLongResult partitionCounts = execute(
                source.candidates().partitionBy(kind).groups().counts(),
                source,
                table);
        LongScalarResult recombined = execute(
                source.candidates().partitionBy(kind)
                        .combine().count(),
                source,
                table);
        require(partitionCounts.size() == 2
                        && partitionCounts.valueAt(0) == 4L
                        && partitionCounts.valueAt(1) == 4L
                        && recombined.value() == 8L,
                "key partition first-key groups and disjoint combine");

        GroupedLongResult selectedGroups = execute(
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(5L))
                        .groupBy(kind)
                        .havingCountAtLeast(2L)
                        .sortedByCountDescending()
                        .counts(),
                source,
                table);
        GroupedLongResult largestGroup = execute(
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(5L))
                        .groupBy(kind)
                        .havingCountAtLeast(3L)
                        .counts(),
                source,
                table);
        require(selectedGroups.size() == 2
                        && selectedGroups.valueAt(0) == 3L
                        && selectedGroups.valueAt(1) == 2L
                        && largestGroup.size() == 1
                        && largestGroup.valueAt(0) == 3L,
                "group having and stable aggregate ordering");

        GroupedLongResult booleanGroups = execute(
                source.candidates()
                        .groupBy(KeyExpression.of(
                                source.columns().factIndex()
                                        .lessThan(4L)))
                        .counts(),
                source,
                table);
        GroupedLongResult doubleGroups = execute(
                source.candidates()
                        .groupBy(KeyExpression.of(
                                source.columns().scale()))
                        .counts(),
                source,
                table);
        require(booleanGroups.size() == 2
                        && booleanGroups.valueAt(0) == 4L
                        && booleanGroups.valueAt(1) == 4L
                        && doubleGroups.size() == 1
                        && doubleGroups.valueAt(0) == 8L,
                "primitive key carriers preserve hash/equality semantics");

        LongColumnResult counts = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .counts(),
                source,
                table);
        LongColumnResult minima = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .min(source.columns().factIndex()),
                source,
                table);
        LongColumnResult maxima = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .max(source.columns().factIndex()),
                source,
                table);
        LongColumnResult fullWindows = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .havingCountAtLeast(3L)
                        .counts(),
                source,
                table);
        LongColumnResult tailWindow = execute(
                source.candidates()
                        .windowByCount(
                                3, 2, PartialWindowPolicy.INCLUDE_PARTIAL)
                        .havingCountAtMost(2L)
                        .counts(),
                source,
                table);
        require(counts.size() == 4
                        && counts.valueAt(0) == 3L
                        && counts.valueAt(3) == 2L
                        && minima.valueAt(2) == 4L
                        && maxima.valueAt(2) == 6L
                        && fullWindows.size() == 3
                        && tailWindow.size() == 1
                        && tailWindow.valueAt(0) == 2L,
                "window selection and aggregate closure");

        NumericFactDataFlow.Source left =
                NumericFactDataFlow.source(0, "left");
        NumericFactDataFlow.Source right =
                NumericFactDataFlow.source(1, "right");
        LongColumnResult projectedLeft = execute(
                left.candidates()
                        .leftOuterJoin(right.candidates()
                                .filter(right.columns().factIndex()
                                        .lessThan(4L)))
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .projectLeft(left.columns().entityId()),
                left,
                right,
                table);
        OptionalLongColumnResult projectedRight = execute(
                left.candidates()
                        .leftOuterJoin(right.candidates()
                                .filter(right.columns().factIndex()
                                        .lessThan(4L)))
                        .on(left.columns().entityId(),
                                right.columns().entityId())
                        .projectRight(right.columns().entityId()),
                left,
                right,
                table);
        require(projectedLeft.size() == 8
                        && projectedLeft.valueAt(7) == 107L
                        && projectedRight.size() == 8
                        && projectedRight.isPresent(3)
                        && !projectedRight.isPresent(4),
                "joined projection carries explicit outer-side absence");

        JoinedFlow<
                NumericFactDataFlow.Binding,
                NumericFactDataFlow.Binding> joined =
                left.candidates()
                        .leftOuterJoin(right.candidates()
                                .filter(right.columns().factIndex()
                                        .lessThan(4L)))
                        .on(left.columns().entityId(),
                                right.columns().entityId());
        LongScalarResult absentCount = execute(
                joined.filter(joined.rightAbsent()).count(),
                left,
                right,
                table);
        LongScalarResult filteredCount = execute(
                joined.filter(joined.rightPredicate(
                        right.columns().factIndex()
                                .greaterThanOrEqualTo(2L))).count(),
                left,
                right,
                table);
        OptionalLongColumnResult orderedTop = execute(
                joined.topK(
                                3L,
                                joined.rightOrder(
                                        right.columns().factIndex()
                                                .descending(),
                                        AbsenceOrder.LAST))
                        .projectRight(right.columns().entityId()),
                left,
                right,
                table);
        GroupedLongResult joinedGroups = execute(
                joined.groupCountsByLeft(
                        KeyExpression.of(
                                left.columns().entityKind())),
                left,
                right,
                table);
        LongColumnResult joinedWindows = execute(
                joined.topK(
                                3L,
                                joined.rightOrder(
                                        right.columns().factIndex()
                                                .descending(),
                                        AbsenceOrder.LAST))
                        .windowCounts(
                                2,
                                2,
                                PartialWindowPolicy.INCLUDE_PARTIAL),
                left,
                right,
                table);
        require(absentCount.value() == 4L
                        && filteredCount.value() == 2L
                        && orderedTop.size() == 3
                        && orderedTop.valueAt(0) == 103L
                        && orderedTop.valueAt(1) == 102L
                        && orderedTop.valueAt(2) == 101L
                        && joinedGroups.size() == 2
                        && joinedGroups.valueAt(0) == 4L
                        && joinedGroups.valueAt(1) == 4L
                        && joinedWindows.size() == 2
                        && joinedWindows.valueAt(0) == 2L
                        && joinedWindows.valueAt(1) == 1L,
                "joined select/sort/group/window closure");
    }

    private static void verifyParametersAndRegisteredContracts(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        ParameterSlot<Long> threshold =
                ParameterSlot.of(0, "threshold", Long.class);
        DataFlowDefinition<LongScalarResult> parameterized =
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(
                                        source.longParameter(threshold)))
                        .project(source.columns().entityId())
                        .sum();
        require(execute(
                        parameterized,
                        source,
                        table,
                        threshold,
                        Long.valueOf(5L)).value()
                        == 105L + 106L + 107L,
                "invocation parameter participates in generated expression");

        RegisteredLongFunction twiceV1 =
                function("example.twice", "1");
        RegisteredLongFunction equivalent =
                function("example.twice", "1");
        RegisteredLongFunction twiceV2 =
                function("example.twice", "2");
        DataFlowDefinition<LongScalarResult> first =
                source.candidates()
                        .project(source.columns().factIndex().map(twiceV1))
                        .sum();
        DataFlowDefinition<LongScalarResult> second =
                source.candidates()
                        .project(source.columns().factIndex().map(equivalent))
                        .sum();
        DataFlowDefinition<LongScalarResult> revised =
                source.candidates()
                        .project(source.columns().factIndex().map(twiceV2))
                        .sum();
        require(first.identity().equals(second.identity())
                        && !first.identity().equals(revised.identity())
                        && execute(first, source, table).value() == 56L,
                "registered semantic identity and execution");

        DataFlowDefinition<LongScalarResult> opaqueFirst =
                source.candidates()
                        .project(source.columns().factIndex().mapOpaque(
                                opaqueTwice()))
                        .sum();
        DataFlowDefinition<LongScalarResult> opaqueSecond =
                source.candidates()
                        .project(source.columns().factIndex().mapOpaque(
                                opaqueTwice()))
                        .sum();
        require(!opaqueFirst.identity().equals(opaqueSecond.identity()),
                "opaque callbacks use definition-instance identity");

        DataFlowDefinition<LongScalarResult> reduced =
                source.candidates()
                        .project(source.columns().factIndex())
                        .reduce(sumReducer());
        require(execute(reduced, source, table).value() == 28L,
                "registered mergeable primitive reducer");
    }

    private static void verifyFiniteGraph(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        DataFlowDefinition<LongColumnResult> shared =
                source.candidates()
                        .project(source.columns().factIndex())
                        .toColumn();
        DataFlowDefinition<LongScalarResult> count =
                source.candidates().count();
        DataFlowDefinition.Builder builder =
                DataFlowDefinition.builder();
        OutputSlot<LongColumnResult> values =
                builder.output("values", shared);
        OutputSlot<LongColumnResult> sharedAgain =
                builder.output("sharedAgain", shared);
        OutputSlot<LongScalarResult> cardinality =
                builder.output("cardinality", count);
        DataFlowDefinition<DataFlowResults> graph = builder.build();
        DataFlowResults results = execute(
                graph, source, table);
        require(results.size() == 3
                        && results.get(values) == results.get(sharedAgain)
                        && results.get(cardinality).value() == 8L,
                "finite graph executes one shared pure node once");

        DataFlowContext parallelContext = DataFlowContext.managedParallel(
                2,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1)
                        .withStatsMode(StatsMode.DETAILED),
                ExecutionBudget.defaults());
        try {
            DataFlowInvocation<DataFlowResults> parallel =
                    graph.compile().newInvocation(parallelContext)
                            .bind(source, NumericFactDataFlow.bind(table));
            DataFlowResults parallelResults = parallel.execute();
            require(parallelResults.get(values).size() == 8
                            && parallelResults.get(cardinality).value() == 8L
                            && parallel.stats().tasks() == 2
                            && parallel.stats().workers() == 2,
                    "independent pure graph branches execute in parallel");
        } finally {
            parallelContext.close();
        }

        DataFlowDefinition<UpdateResult> update = source.update(
                source.candidates().filter(
                        source.columns().factIndex().lessThan(2L)),
                new NumericFactScan.Updater() {
                    @Override
                    public void update(
                            com.hgtech.soma.benchmarks.schema.generated
                                    .NumericFactUpdateCursor fact) {
                        fact.setScale(fact.scale() + 1.0d);
                    }
                });
        DataFlowDefinition.Builder effectGraph =
                DataFlowDefinition.builder();
        OutputSlot<LongScalarResult> before =
                effectGraph.output("before", count);
        OutputSlot<UpdateResult> updated =
                effectGraph.output("updated", update);
        DataFlowResults effectResults = execute(
                effectGraph.build(), source, table);
        require(effectResults.get(before).value() == 8L
                        && effectResults.get(updated).matched() == 2L
                        && table.fetchAt(0).scale == 2.0d,
                "graph stages one terminal safe-point effect");

        DataFlowDefinition.Builder invalid =
                DataFlowDefinition.builder();
        invalid.output("first", update);
        invalid.output("second", update);
        try {
            invalid.build();
            throw new AssertionError("effect fan-out must fail");
        } catch (SomaRuntimeException expected) {
            require("dataflow_graph_effect_fan_out".equals(
                            expected.code())
                            && "FAILED".equals(invalid.state()),
                    "one-shot graph builder owns effect fan-out invariant");
        }
    }

    private static void verifyConstructionInvariants(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        LongScalarResult quotientSum = execute(
                source.candidates()
                        .project(source.columns().factIndex()
                                .plus(8L)
                                .dividedBy(source.columns().factIndex()
                                        .plus(1L)))
                        .sum(),
                source,
                table);
        require(quotientSum.value() == 24L,
                "long binary division owns arithmetic semantics");

        NumericFactDataFlow.Source left =
                NumericFactDataFlow.source(0, "left");
        NumericFactDataFlow.Source colliding =
                NumericFactDataFlow.source(0, "right");
        try {
            left.candidates()
                    .innerJoin(colliding.candidates())
                    .on(left.columns().entityId(),
                            colliding.columns().entityId())
                    .count();
            throw new AssertionError(
                    "source ordinal collision must fail at definition construction");
        } catch (SomaRuntimeException expected) {
            require("dataflow_source_identity_collision".equals(
                            expected.code()),
                    "Definition owns source identity uniqueness");
        }

        DataFlowDefinition<LongScalarResult> delimited =
                NumericFactDataFlow.source(0, "a|b=c\n")
                        .candidates().count();
        DataFlowDefinition<LongScalarResult> plain =
                NumericFactDataFlow.source(0, "a")
                        .candidates().count();
        require(!delimited.identity().equals(plain.identity()),
                "length-prefixed canonical identity accepts arbitrary aliases");

        DataFlowContext context = DataFlowContext.sequential();
        try {
            DataFlowInvocation<LongScalarResult> invocation =
                    source.candidates().count().compile()
                            .newInvocation(context)
                            .bind(source, NumericFactDataFlow.bind(table));
            try {
                invocation.bind(
                        source, NumericFactDataFlow.bind(table));
                throw new AssertionError(
                        "duplicate binding must fail");
            } catch (SomaRuntimeException expected) {
                require("dataflow_duplicate_binding".equals(
                                expected.code()),
                        "Invocation owns duplicate binding validation");
            }
            require(invocation.execute().value() == 8L,
                    "rejected binding cannot overwrite accepted state");
        } finally {
            context.close();
        }
    }

    private static void verifyDiagnostics(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        ParameterSlot<Long> threshold =
                ParameterSlot.of(0, "diagnosticThreshold", Long.class);
        DataFlowDefinition<LongScalarResult> definition =
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(
                                        source.longParameter(threshold)))
                        .count();
        DataFlowContext sequential = DataFlowContext.sequential();
        try {
            DataFlowInvocation<LongScalarResult> invocation =
                    definition.compile().newInvocation(sequential)
                            .bind(source, NumericFactDataFlow.bind(table))
                            .parameter(threshold, Long.valueOf(3L));
            DataFlowExplain explain = invocation.explain();
            require(explain.bound()
                            && explain.boundCardinality() == 8L
                            && explain.parameterSummary()
                                    .contains("<redacted>")
                            && !explain.parameterSummary().contains("=3"),
                    "bound explain is observed, detached and redacted");
            require(invocation.execute().value() == 5L,
                    "bound explain does not consume invocation");
            DataFlowStats basic = invocation.stats();
            require(basic.statsMode() == StatsMode.BASIC
                            && basic.tasks() == 0
                            && basic.maximumOutputElements() > 0L,
                    "BASIC stats expose cardinality and budget only");
        } finally {
            sequential.close();
        }

        DataFlowContext detailedContext = DataFlowContext.managedParallel(
                2,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1)
                        .withStatsMode(StatsMode.DETAILED),
                ExecutionBudget.defaults());
        try {
            DataFlowInvocation<LongScalarResult> detailed =
                    source.candidates().count().compile()
                            .newInvocation(detailedContext)
                            .bind(source, NumericFactDataFlow.bind(table));
            detailed.execute();
            require(detailed.stats().statsMode() == StatsMode.DETAILED
                            && detailed.stats().tasks() == 2
                            && detailed.stats().workers() == 2,
                    "DETAILED stats expose physical task counters");
        } finally {
            detailedContext.close();
        }

        DataFlowContext offContext = DataFlowContext.sequential();
        try {
            DataFlowInvocation<LongScalarResult> off =
                    source.candidates().count().compile()
                            .newInvocation(offContext)
                            .bind(source, NumericFactDataFlow.bind(table))
                            .policy(ExecutionPolicy.sequential()
                                    .withStatsMode(StatsMode.OFF));
            off.execute();
            try {
                off.stats();
                throw new AssertionError("OFF stats must be unavailable");
            } catch (SomaRuntimeException expected) {
                require("dataflow_stats_unavailable".equals(
                                expected.code()),
                        "OFF stats avoid diagnostics result");
            }
        } finally {
            offContext.close();
        }
    }

    private static RegisteredLongFunction function(
            final String semanticId, final String version) {
        return new RegisteredLongFunction() {
            @Override
            public String semanticId() {
                return semanticId;
            }

            @Override
            public String version() {
                return version;
            }

            @Override
            public long applyAsLong(long value) {
                return value * 2L;
            }

            @Override
            public boolean deterministic() {
                return true;
            }

            @Override
            public boolean threadSafe() {
                return true;
            }
        };
    }

    private static OpaqueLongFunction opaqueTwice() {
        return new OpaqueLongFunction() {
            @Override
            public long applyAsLong(long value) {
                return value * 2L;
            }
        };
    }

    private static RegisteredLongReducer sumReducer() {
        return new RegisteredLongReducer() {
            @Override
            public String semanticId() {
                return "example.sum";
            }

            @Override
            public String version() {
                return "1";
            }

            @Override
            public long seed() {
                return 0L;
            }

            @Override
            public long accumulate(long state, long value) {
                return state + value;
            }

            @Override
            public long merge(long leftState, long rightState) {
                return leftState + rightState;
            }

            @Override
            public long finish(long state) {
                return state;
            }

            @Override
            public boolean associative() {
                return true;
            }

            @Override
            public boolean deterministic() {
                return true;
            }

            @Override
            public boolean threadSafe() {
                return true;
            }
        };
    }

    private static NumericFactBatch batch() {
        NumericFactBatch batch = new NumericFactBatch(8);
        for (int index = 0; index < 8; index++) {
            batch.addValues(
                    index,
                    (index & 1) == 0
                            ? EntityKind.PRIMARY : EntityKind.SECONDARY,
                    100L + index,
                    VariableKind.VALUE,
                    index,
                    index,
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
            return definition.compile().newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
                    .execute();
        } finally {
            context.close();
        }
    }

    private static <R, T> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            ParameterSlot<T> parameter,
            T value) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile().newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
                    .parameter(parameter, value)
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
            return definition.compile().newInvocation(context)
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
