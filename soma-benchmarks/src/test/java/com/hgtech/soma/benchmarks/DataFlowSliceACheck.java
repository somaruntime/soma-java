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
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.DoubleColumnResult;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.GeneratedDataFlow;
import com.hgtech.soma.dataflow.LongColumnResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OptionalDoubleResult;
import com.hgtech.soma.dataflow.OptionalLongResult;
import com.hgtech.soma.dataflow.SourceSlot;
import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaErrorCategory;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.generated.RuntimeCompatibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        testProtocolMismatch();
        testPartialAcquireReleasesInReverseOrder();
        table.release();
        System.out.println("dataflow-slice-a-check: ok");
    }

    private static void testProtocolMismatch() {
        ProtocolSource source = new ProtocolSource();
        DataFlowDefinition<LongScalarResult> count =
                GeneratedDataFlow.candidates(source).count();
        DataFlowContext context = DataFlowContext.sequential();
        try {
            expectProtocolCode(
                    count,
                    source,
                    new ProtocolBinding(
                            "soma-generated-runtime-v5",
                            GeneratedDataFlow.TRANSFORMATION_PROTOCOL,
                            GeneratedDataFlow.KERNEL_PROTOCOL),
                    context,
                    "dataflow_generated_protocol_mismatch");
            expectProtocolCode(
                    count,
                    source,
                    new ProtocolBinding(
                            RuntimeCompatibility.GENERATED_PROTOCOL,
                            "soma-transformation-v1",
                            GeneratedDataFlow.KERNEL_PROTOCOL),
                    context,
                    "dataflow_transformation_protocol_mismatch");
        } finally {
            context.close();
        }
    }

    private static void expectProtocolCode(
            DataFlowDefinition<LongScalarResult> definition,
            ProtocolSource source,
            ProtocolBinding binding,
            DataFlowContext context,
            String code) {
        try {
            definition.compile().newInvocation(context)
                    .bind(source, binding)
                    .execute();
            throw new AssertionError(code + " must fail closed");
        } catch (SomaRuntimeException expected) {
            require(code.equals(expected.code()), code + " failure code");
        }
    }

    private static void testPartialAcquireReleasesInReverseOrder() {
        TrackingSource first = new TrackingSource(0, "first");
        TrackingSource second = new TrackingSource(1, "second");
        TrackingSource failing = new TrackingSource(2, "failing");
        DataFlowDefinition.Builder builder = DataFlowDefinition.builder();
        builder.output(
                "first", GeneratedDataFlow.candidates(first).count());
        builder.output(
                "second", GeneratedDataFlow.candidates(second).count());
        builder.output(
                "failing", GeneratedDataFlow.candidates(failing).count());
        DataFlowDefinition<DataFlowResults> graph = builder.build();
        List<String> events = new ArrayList<String>();
        DataFlowContext context = DataFlowContext.sequential();
        try {
            try {
                graph.compile().newInvocation(context)
                        .bind(first, new TrackingBinding(10L, false, events))
                        .bind(second, new TrackingBinding(20L, false, events))
                        .bind(failing, new TrackingBinding(30L, true, events))
                        .execute();
                throw new AssertionError(
                        "partial acquire must propagate failure");
            } catch (SomaRuntimeException expected) {
                require("test_acquire_failed".equals(expected.code()),
                        "partial acquire failure code");
            }
            require(events.size() == 5
                            && "acquire-10".equals(events.get(0))
                            && "acquire-20".equals(events.get(1))
                            && "acquire-30".equals(events.get(2))
                            && "release-20".equals(events.get(3))
                            && "release-10".equals(events.get(4)),
                    "partial acquire releases acquired aggregates in reverse order");
        } finally {
            context.close();
        }
    }

    private static final class ProtocolSource
            extends SourceSlot<ProtocolBinding> {
        private ProtocolSource() {
            super(0, "protocol", "protocol-schema", "protocol-table");
        }
    }

    private static final class ProtocolBinding implements DataFlowBinding {
        private final String generatedProtocol;
        private final String transformationProtocol;
        private final String kernelProtocol;
        private final Object physicalIdentity = new Object();

        private ProtocolBinding(
                String generatedProtocol,
                String transformationProtocol,
                String kernelProtocol) {
            this.generatedProtocol = generatedProtocol;
            this.transformationProtocol = transformationProtocol;
            this.kernelProtocol = kernelProtocol;
        }

        @Override public long aggregateInstanceId() { return 1L; }
        @Override public Object physicalIdentity() { return physicalIdentity; }
        @Override public String schemaIdentity() { return "protocol-schema"; }
        @Override public String tableIdentity() { return "protocol-table"; }
        @Override public String generatedProtocol() { return generatedProtocol; }
        @Override public String transformationProtocol() {
            return transformationProtocol;
        }
        @Override public String kernelProtocol() { return kernelProtocol; }
        @Override public long structuralEpoch() { return 0L; }
        @Override public int packedSize() { return 0; }
        @Override public boolean isPresent(int columnOrdinal, int index) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public boolean booleanValue(int columnOrdinal, int index) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public long longValue(int columnOrdinal, int index) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public double doubleValue(int columnOrdinal, int index) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public String stringValue(int columnOrdinal, int index) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public IndexSnapshot indexSnapshot(int[] indexes, int length) {
            throw new AssertionError("protocol test must not touch data");
        }
        @Override public void acquire(String operation) {
            throw new AssertionError("protocol mismatch must precede acquire");
        }
        @Override public void release(
                String operation,
                boolean success,
                long scanned,
                long matched,
                String failureCode) {
            throw new AssertionError("protocol mismatch must not release");
        }
    }

    private static final class TrackingSource
            extends SourceSlot<TrackingBinding> {
        private TrackingSource(int ordinal, String alias) {
            super(ordinal, alias, "tracking-schema", "tracking-table");
        }
    }

    private static final class TrackingBinding implements DataFlowBinding {
        private final long aggregateInstanceId;
        private final boolean failAcquire;
        private final List<String> events;
        private final Object physicalIdentity = new Object();

        private TrackingBinding(
                long aggregateInstanceId,
                boolean failAcquire,
                List<String> events) {
            this.aggregateInstanceId = aggregateInstanceId;
            this.failAcquire = failAcquire;
            this.events = events;
        }

        @Override public long aggregateInstanceId() {
            return aggregateInstanceId;
        }
        @Override public Object physicalIdentity() { return physicalIdentity; }
        @Override public String schemaIdentity() { return "tracking-schema"; }
        @Override public String tableIdentity() { return "tracking-table"; }
        @Override public String generatedProtocol() {
            return RuntimeCompatibility.GENERATED_PROTOCOL;
        }
        @Override public String transformationProtocol() {
            return GeneratedDataFlow.TRANSFORMATION_PROTOCOL;
        }
        @Override public String kernelProtocol() {
            return GeneratedDataFlow.KERNEL_PROTOCOL;
        }
        @Override public long structuralEpoch() { return 0L; }
        @Override public int packedSize() { return 0; }
        @Override public boolean isPresent(int columnOrdinal, int index) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public boolean booleanValue(int columnOrdinal, int index) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public long longValue(int columnOrdinal, int index) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public double doubleValue(int columnOrdinal, int index) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public String stringValue(int columnOrdinal, int index) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public IndexSnapshot indexSnapshot(int[] indexes, int length) {
            throw new AssertionError("acquire test must not touch data");
        }
        @Override public void acquire(String operation) {
            events.add("acquire-" + aggregateInstanceId);
            if (failAcquire) {
                throw SomaRuntimeException.create(
                        SomaErrorCategory.CONFLICT,
                        "test_acquire_failed",
                        operation,
                        "tracking",
                        Collections.<String, String>emptyMap(),
                        null);
            }
        }
        @Override public void release(
                String operation,
                boolean success,
                long scanned,
                long matched,
                String failureCode) {
            events.add("release-" + aggregateInstanceId);
        }
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
