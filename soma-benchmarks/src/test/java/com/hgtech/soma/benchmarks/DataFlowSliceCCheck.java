package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DoubleColumnResult;
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
        System.out.println("dataflow-slice-c-check: ok");
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
