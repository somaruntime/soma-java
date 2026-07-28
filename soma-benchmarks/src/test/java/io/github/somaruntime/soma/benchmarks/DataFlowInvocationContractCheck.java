package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.EntityKind;
import io.github.somaruntime.soma.benchmarks.schema.VariableKind;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactTable;
import io.github.somaruntime.soma.dataflow.CancellationToken;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.DataFlowInvocation;
import io.github.somaruntime.soma.dataflow.DataFlowTemplate;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

/** Invocation binding、aggregate guard 与 one-shot lifecycle 的能力契约。 */
public final class DataFlowInvocationContractCheck {
    private DataFlowInvocationContractCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        NumericFactBatch batch = new NumericFactBatch(3);
        for (int index = 0; index < 3; index++) {
            batch.addValues(
                    index,
                    EntityKind.PRIMARY,
                    100L + index,
                    VariableKind.VALUE,
                    index,
                    0.0d,
                    1.0d);
        }
        table.addBatch(batch);

        NumericFactDataFlow.Source source = NumericFactDataFlow.source("facts");
        DataFlowDefinition<LongScalarResult> definition =
                source.candidates().count();
        DataFlowTemplate<LongScalarResult> template = definition.compile();
        DataFlowContext context = DataFlowContext.sequential();
        DataFlowInvocation<LongScalarResult> invocation =
                template.newInvocation(context)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .cancellationToken(new GuardProbe(table));

        LongScalarResult result = invocation.execute();
        require(result.value() == 3L, "packed count");
        require("COMPLETED".equals(invocation.state()), "completed state");
        require(invocation.stats().scanned() == 3L, "scanned stats");
        require(invocation.stats().outputElements() == 1L, "scalar output stats");

        try {
            invocation.execute();
            throw new AssertionError("one-shot invocation must be consumed");
        } catch (SomaRuntimeException expected) {
            require("dataflow_invocation_consumed".equals(expected.code()),
                    "one-shot failure code");
        }

        context.close();
        table.release();
        System.out.println("dataflow-invocation-contract: ok");
    }

    private static final class GuardProbe implements CancellationToken {
        private final NumericFactTable table;
        private int calls;

        private GuardProbe(NumericFactTable table) {
            this.table = table;
        }

        @Override
        public boolean isCancellationRequested() {
            calls++;
            if (calls == 2) {
                try {
                    table.size();
                    throw new AssertionError("Table access must be guarded");
                } catch (SomaRuntimeException expected) {
                    require("reentrant_access".equals(expected.code()),
                            "aggregate guard failure code");
                }
            }
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
