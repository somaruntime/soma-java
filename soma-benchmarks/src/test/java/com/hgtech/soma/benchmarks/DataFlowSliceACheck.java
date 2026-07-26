package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.BooleanScalarResult;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DoubleColumnResult;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.LongColumnResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OptionalDoubleResult;
import com.hgtech.soma.dataflow.OptionalLongResult;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Slice A 的 lazy、shape、order、value、budget 与 current-Index differential。 */
public final class DataFlowSliceACheck {
    private static final double[] VALUES =
            new double[] {5.0d, 1.0d, 3.0d, 3.0d, 9.0d, 2.0d, 9.0d, 4.0d};

    private DataFlowSliceACheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");

        DataFlowDefinition<LongScalarResult> lazyCount = source.candidates()
                .filter(source.columns().entityId().greaterThanOrEqualTo(102L))
                .count();
        table.addBatch(batch());
        require(execute(lazyCount, source, table).value() == 6L,
                "lazy selection count");

        DoubleColumnResult projected = execute(
                source.candidates()
                        .filter(source.columns().entityId()
                                .greaterThanOrEqualTo(102L))
                        .sortedBy(source.columns().value().descending()
                                .then(source.columns().factIndex().ascending()))
                        .skip(1L)
                        .limit(3L)
                        .project(source.columns().value().multipliedBy(2.0d))
                        .toColumn(),
                source,
                table);
        require(projected.size() == 3
                        && projected.valueAt(0) == 18.0d
                        && projected.valueAt(1) == 8.0d
                        && projected.valueAt(2) == 6.0d,
                "stable sort and fused projection");

        BooleanScalarResult any = execute(
                source.candidates().anyMatch(
                        source.columns().value().greaterThan(8.0d)),
                source,
                table);
        BooleanScalarResult all = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(2L))
                        .allMatch(source.columns().entityId()
                                .greaterThanOrEqualTo(102L)),
                source,
                table);
        require(any.value() && all.value(), "match terminals");

        LongScalarResult sum = execute(
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(4L))
                        .project(source.columns().entityId())
                        .sum(),
                source,
                table);
        OptionalDoubleResult average = execute(
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(4L))
                        .project(source.columns().entityId())
                        .average(),
                source,
                table);
        OptionalLongResult minimum = execute(
                source.candidates()
                        .filter(source.columns().factIndex().greaterThan(100L))
                        .project(source.columns().entityId())
                        .min(),
                source,
                table);
        require(sum.value() == 406L, "integral sum");
        require(average.isPresent() && average.value() == 101.5d,
                "integral average");
        require(!minimum.isPresent(), "empty reduction absence");

        LongColumnResult prefix = execute(
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(2L))
                        .limit(3L)
                        .project(source.columns().factIndex())
                        .inclusivePrefixSum(),
                source,
                table);
        require(prefix.size() == 3
                        && prefix.valueAt(0) == 2L
                        && prefix.valueAt(1) == 5L
                        && prefix.valueAt(2) == 9L,
                "ordered prefix scan");

        IndexSnapshot best = execute(
                source.candidates().bestIndexSnapshot(
                        source.columns().value().descending()
                                .then(source.columns().factIndex().ascending())),
                source,
                table);
        require(best.size() == 1 && best.indexAt(0) == 4,
                "stable best current index");
        table.requireCurrent(best);

        require(lazyCount.explain().logicalPlan().contains("filter"),
                "logical explain");
        require(lazyCount.compile().explain().physicalPlan()
                        .contains("candidate-adaptive"),
                "physical explain");

        DataFlowContext context = DataFlowContext.sequential();
        try {
            DataFlowInvocation<DoubleColumnResult> invocation =
                    source.candidates()
                            .project(source.columns().value())
                            .toColumn()
                            .compile()
                            .newInvocation(context)
                            .bind(source, NumericFactDataFlow.bind(table))
                            .budget(ExecutionBudget.builder()
                                    .maximumOutputElements(2L)
                                    .build());
            try {
                invocation.execute();
                throw new AssertionError("output budget must fail closed");
            } catch (SomaRuntimeException expected) {
                require("dataflow_output_budget_exceeded".equals(expected.code()),
                        "output budget failure code");
            }
        } finally {
            context.close();
        }

        table.release();
        System.out.println("dataflow-slice-a-check: ok");
    }

    private static NumericFactBatch batch() {
        NumericFactBatch batch = new NumericFactBatch(VALUES.length);
        for (int index = 0; index < VALUES.length; index++) {
            batch.addValues(
                    index,
                    index % 2 == 0 ? EntityKind.PRIMARY : EntityKind.SECONDARY,
                    100L + index,
                    index % 2 == 0 ? VariableKind.VALUE : VariableKind.RATE,
                    VALUES[index],
                    index * 0.5d,
                    2.0d);
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
