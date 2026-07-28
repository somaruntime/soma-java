package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.IdentityHashMap;
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
                new StringBuilder("soma-definition-v1");
        Map<Integer, SourceSlot<?>> sourceOrdinals =
                new HashMap<Integer, SourceSlot<?>>();
        Map<String, SourceSlot<?>> sourceAliases =
                new HashMap<String, SourceSlot<?>>();
        IdentityHashMap<SourceSlot<?>, Boolean> seenSources =
                new IdentityHashMap<SourceSlot<?>, Boolean>();
        for (SourceSlot<? extends DataFlowBinding> source
                : operation.requiredSources()) {
            if (source == null) {
                throw DataFlowFailures.internal(
                        "dataflow_definition_null_source",
                        "definition",
                        "dataflow.definition",
                        operation.logicalShape());
            }
            SourceSlot<?> ordinalCollision = sourceOrdinals.put(
                    Integer.valueOf(source.ordinal()), source);
            SourceSlot<?> aliasCollision =
                    sourceAliases.put(source.alias(), source);
            if ((ordinalCollision != null
                    && ordinalCollision != source)
                    || (aliasCollision != null
                    && aliasCollision != source)) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_source_identity_collision",
                        source.alias(),
                        "dataflow.definition");
            }
            if (seenSources.put(source, Boolean.TRUE) != null) {
                continue;
            }
            StringBuilder encoded = new StringBuilder("source");
            DataFlowSupport.appendCanonical(
                    encoded, "ordinal", Integer.toString(source.ordinal()));
            DataFlowSupport.appendCanonical(
                    encoded, "alias", source.alias());
            DataFlowSupport.appendCanonical(
                    encoded, "schema", source.schemaIdentity());
            DataFlowSupport.appendCanonical(
                    encoded, "table", source.tableIdentity());
            DataFlowSupport.appendCanonical(
                    canonical, "source", encoded.toString());
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
            DataFlowSupport.appendCanonical(
                    canonical, "parameter", parameter.canonical());
        }
        DataFlowSupport.appendCanonical(
                canonical, "operation", operation.canonicalForm());
        identity = DataFlowSupport.identity(canonical.toString());
    }

    static <R> DataFlowDefinition<R> of(DataFlowOperation<R> operation) {
        return new DataFlowDefinition<R>(operation);
    }

    public static Builder builder() {
        return new Builder();
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

    /**
     * One-shot controlled authoring surface for a finite typed result graph.
     *
     * <p>Reusing the same definition instance for more than one output shares
     * its pure logical result within an invocation. At most one effect output
     * is legal and an effect cannot be fanned out.</p>
     */
    public static final class Builder {
        private enum State {
            OPEN,
            PUBLISHED,
            FAILED
        }

        private static final int MAXIMUM_OUTPUTS = 256;

        private final Object resultOwner = new Object();
        private final List<GraphOutput<?>> outputs =
                new java.util.ArrayList<GraphOutput<?>>();
        private final Map<String, OutputSlot<?>> names =
                new HashMap<String, OutputSlot<?>>();
        private State state = State.OPEN;

        private Builder() {
        }

        public <T> OutputSlot<T> output(
                String name, DataFlowDefinition<T> definition) {
            requireOpen("dataflow.graph.output");
            try {
                DataFlowSupport.required(name, "name");
                if (definition == null) {
                    throw new NullPointerException("definition");
                }
                if (outputs.size() >= MAXIMUM_OUTPUTS) {
                    throw DataFlowFailures.resource(
                            "dataflow_graph_output_limit",
                            name,
                            "dataflow.graph.output",
                            Integer.toString(MAXIMUM_OUTPUTS));
                }
                OutputSlot<T> slot = new OutputSlot<T>(
                        resultOwner, outputs.size(), name);
                if (names.put(name, slot) != null) {
                    throw DataFlowFailures.invalidInput(
                            "dataflow_graph_duplicate_output",
                            name,
                            "dataflow.graph.output");
                }
                outputs.add(new GraphOutput<T>(
                        slot, definition.operation()));
                return slot;
            } catch (RuntimeException failure) {
                state = State.FAILED;
                throw failure;
            }
        }

        public DataFlowDefinition<DataFlowResults> build() {
            requireOpen("dataflow.graph.build");
            state = State.FAILED;
            if (outputs.isEmpty()) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_graph_empty",
                        "graph",
                        "dataflow.graph.build");
            }
            DataFlowDefinition<DataFlowResults> definition =
                    DataFlowDefinition.of(
                            new GraphOperation(resultOwner, outputs));
            state = State.PUBLISHED;
            return definition;
        }

        public String state() {
            return state.name();
        }

        private void requireOpen(String operation) {
            if (state != State.OPEN) {
                throw DataFlowFailures.lifecycle(
                        "dataflow_graph_builder_consumed",
                        "graph",
                        operation,
                        state.name());
            }
        }
    }
}
