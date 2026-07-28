package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.CandidateKey;
import com.hgtech.soma.benchmarks.schema.CategoryId;
import com.hgtech.soma.benchmarks.schema.GroupCandidate;
import com.hgtech.soma.benchmarks.schema.GroupId;
import com.hgtech.soma.benchmarks.schema.ItemId;
import com.hgtech.soma.benchmarks.schema.NamespaceId;
import com.hgtech.soma.benchmarks.schema.OwnerFact;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.WorkKey;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateBatch;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateDelta;
import com.hgtech.soma.benchmarks.schema.generated.GroupCandidateTable;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.benchmarks.schema.generated.OwnedOptionBatch;
import com.hgtech.soma.benchmarks.schema.generated.OwnerFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.OwnerFactDelta;
import com.hgtech.soma.benchmarks.schema.generated.OwnerFactTable;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DeltaApplyResult;
import com.hgtech.soma.dataflow.DoubleColumnResult;
import com.hgtech.soma.runtime.DeltaStagingFormula;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

/** Slice C single-source MutationSet 与 safe-point Effect evidence。 */
public final class DataFlowSliceCCheck {
    private DataFlowSliceCCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");

        UpdateResult updated = execute(
                source.update(
                        source.candidates().filter(
                                source.columns().factIndex().lessThan(4L)),
                        new NumericFactScan.Updater() {
                            @Override
                            public void update(
                                    com.hgtech.soma.benchmarks.schema.generated
                                            .NumericFactUpdateCursor fact) {
                                fact.setValue(fact.value() + 100.0d);
                            }
                        }),
                source,
                table);
        require(updated.matched() == 4L && updated.changed() == 4L,
                "dataflow update result");
        DoubleColumnResult values = execute(
                source.candidates()
                        .project(source.columns().value())
                        .toColumn(),
                source,
                table);
        require(values.valueAt(0) == 100.0d
                        && values.valueAt(3) == 103.0d
                        && values.valueAt(4) == 4.0d,
                "dataflow update publication");

        long beforeFailure = table.structuralEpoch();
        try {
            execute(
                    source.update(
                            source.candidates().limit(3L),
                            new NumericFactScan.Updater() {
                                private int calls;

                                @Override
                                public void update(
                                        com.hgtech.soma.benchmarks.schema.generated
                                                .NumericFactUpdateCursor fact) {
                                    fact.setScale(99.0d);
                                    if (++calls == 2) {
                                        throw new IllegalStateException("expected");
                                    }
                                }
                            }),
                    source,
                    table);
            throw new AssertionError("update callback failure expected");
        } catch (SomaRuntimeException expected) {
            require("callback_failed".equals(expected.code()),
                    "typed update callback failure");
        }
        require(table.structuralEpoch() == beforeFailure,
                "failed update does not alter structural epoch");
        require(table.fetchAt(0).scale == 1.0d
                        && table.fetchAt(1).scale == 1.0d,
                "failed update publishes no partial row");

        RemoveResult removed = execute(
                source.remove(
                        source.candidates().filter(
                                source.columns().factIndex()
                                        .greaterThanOrEqualTo(6L))),
                source,
                table);
        require(removed.removed() == 2L && table.size() == 6,
                "dataflow remove result");
        require(table.structuralEpoch() == beforeFailure + 1L,
                "remove publishes one structural epoch");

        table.release();
        verifyKeyedDelta();
        verifyOwnedAggregateDelta();
        System.out.println("dataflow-slice-c-check: ok");
    }

    private static void verifyKeyedDelta() {
        GroupCandidateTable table = GroupCandidateTable.create();
        GroupCandidateBatch initial = new GroupCandidateBatch(3);
        initial.add(row(0L, 0L, 0L));
        initial.add(row(1L, 1L, 10L));
        initial.add(row(2L, 0L, 20L));
        table.addBatch(initial);

        long beforeEpoch = table.structuralEpoch();
        GroupCandidateDelta delta = new GroupCandidateDelta()
                .expectStructuralEpoch(beforeEpoch)
                .update(row(0L, 0L, 100L))
                .delete(key(1L, 1L))
                .insert(row(3L, 1L, 300L));
        DeltaApplyResult result = table.applyDelta(delta);
        require(result.inserted() == 1L
                        && result.updated() == 1L
                        && result.deleted() == 1L
                        && result.beforeSize() == 3
                        && result.afterSize() == 3,
                "delta apply summary");
        require(table.structuralEpoch() == beforeEpoch + 1L,
                "delta publishes one structural epoch");
        require(table.fetchAt(0).metric0 == 100L
                        && table.fetchAt(1).candidateKey.equals(key(2L, 0L))
                        && table.fetchAt(2).candidateKey.equals(key(3L, 1L)),
                "delta declaration order and swap-remove");
        require(table.findIndex(key(1L, 1L)) < 0
                        && table.findIndex(key(3L, 1L)) == 2,
                "delta key path publication");
        require(table.scanByGroup(new GroupId(1L)).count() == 1L,
                "delta exact index publication");

        long stableEpoch = table.structuralEpoch();
        DeltaApplyResult updateOnly = table.applyDelta(
                new GroupCandidateDelta()
                        .update(row(2L, 0L, 222L)));
        require(updateOnly.updated() == 1L
                        && table.fetch(key(2L, 0L)).metric0 == 222L
                        && table.structuralEpoch() == stableEpoch + 1L,
                "changed-row update stages only touched rows");
        stableEpoch = table.structuralEpoch();

        DeltaApplyResult insertOnly = table.applyDelta(
                new GroupCandidateDelta()
                        .insert(row(4L, 0L, 444L)));
        require(insertOnly.inserted() == 1L
                        && table.findIndex(key(4L, 0L)) >= 0
                        && table.structuralEpoch() == stableEpoch + 1L,
                "changed-row insert publishes once");
        stableEpoch = table.structuralEpoch();

        DeltaApplyResult deleteOnly = table.applyDelta(
                new GroupCandidateDelta().delete(key(3L, 1L)));
        require(deleteOnly.deleted() == 1L
                        && table.findIndex(key(3L, 1L)) < 0
                        && table.structuralEpoch() == stableEpoch + 1L,
                "changed-row delete publishes once");
        stableEpoch = table.structuralEpoch();

        require(DeltaStagingFormula.useChangedRows(1_000, 64)
                        && !DeltaStagingFormula.useChangedRows(8, 65)
                        && DeltaStagingFormula.IDENTITY
                        .equals("soma-delta-staging-v1"),
                "versioned deterministic Delta crossover");

        GroupCandidateDelta duplicate = new GroupCandidateDelta()
                .update(row(0L, 0L, 111L))
                .delete(key(0L, 0L));
        expectCode(table, duplicate, "duplicate_delta_target");
        require(table.structuralEpoch() == stableEpoch
                        && table.fetchAt(0).metric0 == 100L,
                "duplicate delta target is failure atomic");

        GroupCandidateDelta missing = new GroupCandidateDelta()
                .update(row(0L, 0L, 222L))
                .update(row(99L, 0L, 999L));
        expectCode(table, missing, "delta_target_absent");
        require(table.structuralEpoch() == stableEpoch
                        && table.fetchAt(0).metric0 == 100L,
                "delta presence preflight is failure atomic");

        GroupCandidateDelta stale = new GroupCandidateDelta()
                .expectStructuralEpoch(stableEpoch - 1L)
                .update(row(0L, 0L, 333L));
        expectCode(table, stale, "stale_delta");
        table.release();
    }

    private static void verifyOwnedAggregateDelta() {
        OwnerFactTable table = OwnerFactTable.create();
        OwnedOptionBatch options = new OwnedOptionBatch(2);
        options.addValues(new GroupId(1L), 10L);
        options.addValues(new GroupId(2L), 20L);
        OwnerFactBatch initial = new OwnerFactBatch(1);
        initial.addValues(
                new WorkKey(new NamespaceId(8L), new ItemId(1L)),
                1,
                10L,
                new CategoryId(1L),
                options);
        table.addBatch(initial);

        OwnerFact replacement = table.fetchAt(0);
        replacement.releaseValue = 77L;
        DeltaApplyResult result = table.applyDelta(
                new OwnerFactDelta().update(replacement));
        require(result.updated() == 1L
                        && table.fetchAt(0).releaseValue == 77L
                        && table.fetchAt(0).options.size() == 2,
                "delta preserves owned child aggregate");
        table.release();
    }

    private static void expectCode(
            GroupCandidateTable table,
            GroupCandidateDelta delta,
            String code) {
        try {
            table.applyDelta(delta);
            throw new AssertionError("delta failure expected: " + code);
        } catch (SomaRuntimeException expected) {
            require(code.equals(expected.code()), "typed delta failure: " + code);
        }
    }

    private static GroupCandidate row(long item, long group, long metric) {
        GroupCandidate row = new GroupCandidate();
        row.candidateKey = key(item, group);
        row.categoryId = new CategoryId(1L);
        row.metric0 = metric;
        return row;
    }

    private static CandidateKey key(long item, long group) {
        return new CandidateKey(
                new WorkKey(new NamespaceId(4L), new ItemId(item)),
                new GroupId(group));
    }

    private static NumericFactBatch batch() {
        NumericFactBatch batch = new NumericFactBatch(8);
        for (int index = 0; index < 8; index++) {
            batch.addValues(
                    index,
                    index % 2 == 0
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
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, NumericFactDataFlow.bind(table))
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
