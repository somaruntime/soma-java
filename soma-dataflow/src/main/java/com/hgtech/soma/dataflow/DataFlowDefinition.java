package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

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
        Map<Integer, ParameterSlot<?>> ordinals =
                new HashMap<Integer, ParameterSlot<?>>();
        Map<String, ParameterSlot<?>> names =
                new HashMap<String, ParameterSlot<?>>();
        for (ParameterSlot<?> parameter : operation.requiredParameters()) {
            ParameterSlot<?> byOrdinal = ordinals.put(
                    Integer.valueOf(parameter.ordinal()), parameter);
            ParameterSlot<?> byName = names.put(parameter.name(), parameter);
            if ((byOrdinal != null && byOrdinal != parameter)
                    || (byName != null && byName != parameter)) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_parameter_identity_collision",
                        parameter.name(),
                        "dataflow.definition");
            }
            canonical.append("parameter\n")
                    .append(parameter.canonical()).append('\n');
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

    List<ParameterSlot<?>> requiredParameters() {
        return operation.requiredParameters();
    }

    DataFlowOperation<R> operation() {
        return operation;
    }
}
