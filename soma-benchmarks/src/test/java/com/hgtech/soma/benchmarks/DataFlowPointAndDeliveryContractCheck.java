package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.CandidateKey;
import com.hgtech.soma.benchmarks.schema.CategoryId;
import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.GroupCandidate;
import com.hgtech.soma.benchmarks.schema.GroupId;
import com.hgtech.soma.benchmarks.schema.ItemId;
import com.hgtech.soma.benchmarks.schema.NamespaceId;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.WorkKey;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateBatch;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateTable;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactCursor;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.BooleanScalarResult;
import com.hgtech.soma.dataflow.CallbackDeliveryDefinition;
import com.hgtech.soma.dataflow.CallbackDeliveryInvocation;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DeliveryResult;
import com.hgtech.soma.dataflow.ResultDeliveryMode;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.ParameterSlot;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.List;

/** Point、gather、callback delivery 与 materialize 能力契约。 */
public final class DataFlowPointAndDeliveryContractCheck {
    private DataFlowPointAndDeliveryContractCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(numericBatch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");

        BooleanScalarResult exists = execute(
                source.pointAt(3).exists(), source, table);
        List<com.hgtech.soma.benchmarks.schema.NumericFact> point =
                execute(
                        source.pointAt(3).materialize(
                                MaterializationBudget.defaults()),
                        source,
                        table);
        require(exists.value()
                        && point.size() == 1
                        && point.get(0).factIndex == 3,
                "current Index point shape");

        final long[] borrowed = new long[1];
        final CallbackDeliveryDefinition<NumericFactScan.Visitor> delivery =
                source.deliver(
                        source.candidates()
                                .filter(source.columns().factIndex()
                                        .greaterThanOrEqualTo(5L)));
        CallbackDeliveryDefinition<NumericFactScan.Visitor> equivalent =
                source.deliver(
                        source.candidates()
                                .filter(source.columns().factIndex()
                                        .greaterThanOrEqualTo(5L)));
        require(delivery.identity().equals(equivalent.identity()),
                "visitor instance is excluded from Definition identity");
        DeliveryResult borrowedCount = executeDelivery(
                delivery,
                source,
                table,
                new NumericFactScan.Visitor() {
                    @Override
                    public boolean visit(NumericFactCursor candidate) {
                        borrowed[0] += candidate.entityId();
                        return true;
                    }
                });
        require(borrowedCount.deliveredElements() == 3L
                        && borrowedCount.completed()
                        && borrowed[0] == 105L + 106L + 107L,
                "generated cursor callback delivery");

        DeliveryResult stopped = executeDelivery(
                delivery,
                source,
                table,
                new NumericFactScan.Visitor() {
                    @Override
                    public boolean visit(NumericFactCursor candidate) {
                        return false;
                    }
                });
        require(stopped.deliveredElements() == 1L && !stopped.completed(),
                "callback delivery early stop");
        expectDeliveryPreflight(delivery, source, table);
        expectMissingVisitor(delivery, source, table);
        expectDeliveryFailureCleanup(delivery, source, table);
        verifyStreamingLedger(delivery, source, table);
        require(table.size() == 8,
                "callback failure releases cursor, Table guard and Invocation");

        List<com.hgtech.soma.benchmarks.schema.NumericFact> materialized =
                execute(
                        source.materialize(
                                source.candidates()
                                        .filter(source.columns().factIndex()
                                                .lessThan(2L)),
                                MaterializationBudget.defaults()),
                        source,
                        table);
        require(materialized.size() == 2
                        && materialized.get(0).factIndex == 0
                        && materialized.get(1).factIndex == 1,
                "explicit candidate materialization");

        IndexSnapshot snapshot = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(6L))
                        .indexSnapshot(),
                source,
                table);
        ParameterSlot<IndexSnapshot> snapshotSlot =
                ParameterSlot.of(0, "candidates", IndexSnapshot.class);
        DataFlowDefinition<LongScalarResult> gather =
                source.gather(snapshotSlot)
                        .project(source.columns().entityId())
                        .sum();
        require(execute(gather, source, table, snapshotSlot, snapshot).value()
                        == 106L + 107L,
                "bound IndexSnapshot gather");
        expectMissingParameter(gather, source, table);

        table.clear();
        try {
            execute(gather, source, table, snapshotSlot, snapshot);
            throw new AssertionError("stale gather must fail");
        } catch (SomaRuntimeException expected) {
            require("stale_index_snapshot".equals(expected.code()),
                    "stale gather failure identity");
        }
        table.release();

        verifyPrimaryPoint();
        System.out.println("dataflow-point-and-delivery-contract: ok");
    }

    private static void verifyPrimaryPoint() {
        GroupCandidateTable table = GroupCandidateTable.create();
        GroupCandidateBatch batch = new GroupCandidateBatch(2);
        GroupCandidate first = row(1L, 10L);
        GroupCandidate second = row(2L, 20L);
        batch.add(first);
        batch.add(second);
        table.addBatch(batch);
        GroupCandidateDataFlow.Source source =
                GroupCandidateDataFlow.source("groups");

        DataFlowContext context = DataFlowContext.sequential();
        try {
            List<GroupCandidate> found = source
                    .pointByCandidateKey(first.candidateKey)
                    .materialize(MaterializationBudget.defaults())
                    .compile()
                    .newInvocation(context)
                    .bind(source, GroupCandidateDataFlow.bind(table))
                    .execute();
            BooleanScalarResult missing = source
                    .pointByCandidateKey(key(99L))
                    .exists()
                    .compile()
                    .newInvocation(context)
                    .bind(source, GroupCandidateDataFlow.bind(table))
                    .execute();
            require(found.size() == 1
                            && found.get(0).metric0 == 10L
                            && !missing.value(),
                    "primary-key point shape");
        } finally {
            context.close();
            table.release();
        }
    }

    private static void expectMissingParameter(
            DataFlowDefinition<LongScalarResult> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        try {
            execute(definition, source, table);
            throw new AssertionError("missing parameter must fail");
        } catch (SomaRuntimeException expected) {
            require("dataflow_parameter_cardinality".equals(expected.code()),
                    "missing parameter failure identity");
        }
    }

    private static NumericFactBatch numericBatch() {
        NumericFactBatch batch = new NumericFactBatch(8);
        for (int index = 0; index < 8; index++) {
            batch.addValues(
                    index,
                    EntityKind.PRIMARY,
                    100L + index,
                    VariableKind.VALUE,
                    index,
                    index,
                    1.0d);
        }
        return batch;
    }

    private static GroupCandidate row(long item, long metric) {
        GroupCandidate row = new GroupCandidate();
        row.candidateKey = key(item);
        row.categoryId = new CategoryId(1L);
        row.metric0 = metric;
        return row;
    }

    private static CandidateKey key(long item) {
        return new CandidateKey(
                new WorkKey(new NamespaceId(7L), new ItemId(item)),
                new GroupId(item % 2L));
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

    private static DeliveryResult executeDelivery(
            CallbackDeliveryDefinition<NumericFactScan.Visitor> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            NumericFactScan.Visitor visitor) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
                    .visitor(visitor)
                    .execute();
        } finally {
            context.close();
        }
    }

    private static void expectDeliveryPreflight(
            CallbackDeliveryDefinition<NumericFactScan.Visitor> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        final int[] callbacks = new int[1];
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<NumericFactScan.Visitor> invocation =
                    definition.compile().newInvocation(context)
                            .bind(source, NumericFactDataFlow.bind(table))
                            .visitor(new NumericFactScan.Visitor() {
                                @Override
                                public boolean visit(
                                        NumericFactCursor candidate) {
                                    callbacks[0]++;
                                    return true;
                                }
                            })
                            .budget(ExecutionBudget.defaults().toBuilder()
                                    .maximumOutputElements(2L)
                                    .build());
            try {
                invocation.execute();
                throw new AssertionError(
                        "delivery budget must fail before callback");
            } catch (SomaRuntimeException expected) {
                require("dataflow_output_budget_exceeded".equals(
                                expected.code())
                                && callbacks[0] == 0,
                        "delivery preflight precedes callback");
            }
        } finally {
            context.close();
        }
    }

    private static void expectMissingVisitor(
            CallbackDeliveryDefinition<NumericFactScan.Visitor> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            try {
                definition.compile().newInvocation(context)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .execute();
                throw new AssertionError("missing visitor must fail");
            } catch (SomaRuntimeException expected) {
                require("dataflow_parameter_cardinality".equals(
                                expected.code()),
                        "visitor is a required Invocation parameter");
            }
        } finally {
            context.close();
        }
    }

    private static void expectDeliveryFailureCleanup(
            CallbackDeliveryDefinition<NumericFactScan.Visitor> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            try {
                definition.compile().newInvocation(context)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .visitor(new NumericFactScan.Visitor() {
                            @Override
                            public boolean visit(
                                    NumericFactCursor candidate) {
                                throw new IllegalStateException("boom");
                            }
                        })
                        .execute();
                throw new AssertionError("callback failure must propagate");
            } catch (SomaRuntimeException expected) {
                require("callback_failed".equals(expected.code()),
                        "generated callback failure identity");
            }
        } finally {
            context.close();
        }
    }

    private static void verifyStreamingLedger(
            CallbackDeliveryDefinition<NumericFactScan.Visitor> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            CallbackDeliveryInvocation<NumericFactScan.Visitor> invocation =
                    definition.compile().newInvocation(context)
                            .bind(source, NumericFactDataFlow.bind(table))
                            .visitor(new NumericFactScan.Visitor() {
                                @Override
                                public boolean visit(
                                        NumericFactCursor candidate) {
                                    return true;
                                }
                            });
            invocation.execute();
            require(invocation.stats().sharedScratchHighWaterBytes() == 0L
                            && invocation.stats()
                                    .workerScratchHighWaterBytes() == 0L
                            && invocation.stats().outputHighWaterBytes() == 0L
                            && invocation.stats().taskCurrent() == 0
                            && invocation.stats().workerCurrent() == 0
                            && invocation.stats().delivery().mode()
                            == ResultDeliveryMode.CALLBACK_SCOPED
                            && invocation.stats().delivery()
                                    .deliveredElements() == 3L
                            && invocation.stats().delivery().completed()
                            && invocation.stats().resources()
                                    .invocationLedgerIdentity()
                                    .equals("soma-invocation-ledger-v1"),
                    "streaming Candidate delivery avoids universal vector");
        } finally {
            context.close();
        }
    }

    private static <R, T> R execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            ParameterSlot<T> slot,
            T value) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            DataFlowInvocation<R> invocation = definition.compile()
                    .newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
                    .parameter(slot, value);
            return invocation.execute();
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
