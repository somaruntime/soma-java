package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.List;

/**
 * Immutable logical transformation definition.
 *
 * <p>A definition retains semantics and declared callbacks, never live Table
 * state. Intermediate DSL calls remain lazy; execution starts only through a
 * one-shot {@link DataFlowInvocation}.</p>
 */
public final class DataFlowDefinition<R> {
    private final DataFlowOperation<R> operation;
    private final String identity;

    private DataFlowDefinition(DataFlowOperation<R> operation) {
        if (operation == null) {
            throw new NullPointerException("operation");
        }
        this.operation = operation;
        StringBuilder canonical =
                new StringBuilder("soma-definition-v1\n");
        for (SourceSlot<? extends DataFlowBinding> source
                : operation.requiredSources()) {
            canonical.append(source.ordinal()).append('\n')
                    .append(source.alias()).append('\n')
                    .append(source.schemaIdentity()).append('\n')
                    .append(source.tableIdentity()).append('\n');
        }
        canonical.append(operation.canonicalForm()).append('\n');
        identity = DataFlowSupport.identity(canonical.toString());
    }

    static <R> DataFlowDefinition<R> of(DataFlowOperation<R> operation) {
        return new DataFlowDefinition<R>(operation);
    }

    public String identity() {
        return identity;
    }

    public DataFlowTemplate<R> compile() {
        return new DataFlowTemplate<R>(this);
    }

    public DataFlowExplain explain() {
        return new DataFlowExplain(
                identity,
                "",
                operation.logicalShape(),
                operation.logicalPlan(),
                "unbound");
    }

    List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return operation.requiredSources();
    }

    DataFlowOperation<R> operation() {
        return operation;
    }
}
