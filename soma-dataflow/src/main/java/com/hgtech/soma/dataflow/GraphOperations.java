package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class GraphOutput<R> {
    final OutputSlot<R> slot;
    final DataFlowOperation<R> operation;

    GraphOutput(OutputSlot<R> slot, DataFlowOperation<R> operation) {
        this.slot = slot;
        this.operation = operation;
    }
}

final class GraphOperation implements DataFlowOperation<DataFlowResults> {
    private final Object resultOwner;
    private final List<GraphOutput<?>> outputs;
    private final List<SourceSlot<? extends DataFlowBinding>> sources;
    private final List<ParameterSlot<?>> parameters;
    private final List<DataFlowOperation<?>> uniqueOperations;
    private final boolean effectful;
    private final boolean branchParallelSafe;
    private final String canonical;
    private final String logical;
    private final String physical;

    GraphOperation(Object resultOwner, List<GraphOutput<?>> outputs) {
        this.resultOwner = resultOwner;
        this.outputs = Collections.unmodifiableList(
                new ArrayList<GraphOutput<?>>(outputs));

        ArrayList<SourceSlot<? extends DataFlowBinding>> sourceList =
                new ArrayList<SourceSlot<? extends DataFlowBinding>>();
        IdentityHashMap<SourceSlot<?>, Boolean> seenSources =
                new IdentityHashMap<SourceSlot<?>, Boolean>();
        Map<Integer, SourceSlot<?>> sourceOrdinals =
                new HashMap<Integer, SourceSlot<?>>();
        Map<String, SourceSlot<?>> sourceAliases =
                new HashMap<String, SourceSlot<?>>();
        List<ParameterSlot<?>> parameterList =
                Collections.emptyList();
        IdentityHashMap<DataFlowOperation<?>, Boolean> effectNodes =
                new IdentityHashMap<DataFlowOperation<?>, Boolean>();
        IdentityHashMap<DataFlowOperation<?>, Boolean> seenOperations =
                new IdentityHashMap<DataFlowOperation<?>, Boolean>();
        ArrayList<DataFlowOperation<?>> operationList =
                new ArrayList<DataFlowOperation<?>>();
        boolean hasEffect = false;
        boolean allBranchesSafe = true;
        StringBuilder canonicalText = new StringBuilder("finite-graph-v1");
        StringBuilder logicalText = new StringBuilder("FiniteGraph");
        StringBuilder physicalText =
                new StringBuilder("finite-dag[shared-pure-once");

        for (GraphOutput<?> output : outputs) {
            DataFlowOperation<?> operation = output.operation;
            if (seenOperations.put(operation, Boolean.TRUE) == null) {
                operationList.add(operation);
                allBranchesSafe = allBranchesSafe
                        && operation.parallelBranchSafe();
            }
            StringBuilder encodedOutput = new StringBuilder("output");
            DataFlowSupport.appendCanonical(
                    encodedOutput,
                    "ordinal",
                    Integer.toString(output.slot.ordinal()));
            DataFlowSupport.appendCanonical(
                    encodedOutput, "name", output.slot.name());
            DataFlowSupport.appendCanonical(
                    encodedOutput,
                    "operation",
                    operation.canonicalForm());
            DataFlowSupport.appendCanonical(
                    canonicalText, "output", encodedOutput.toString());
            logicalText.append("\n  ")
                    .append(output.slot.name()).append(" <- ")
                    .append(operation.logicalPlan());
            physicalText.append("\n  ")
                    .append(output.slot.name()).append(" <- ")
                    .append(operation.physicalPlan());

            for (SourceSlot<? extends DataFlowBinding> source
                    : operation.requiredSources()) {
                SourceSlot<?> ordinalCollision = sourceOrdinals.put(
                        Integer.valueOf(source.ordinal()), source);
                SourceSlot<?> aliasCollision =
                        sourceAliases.put(source.alias(), source);
                if ((ordinalCollision != null
                        && ordinalCollision != source)
                        || (aliasCollision != null
                        && aliasCollision != source)) {
                    throw DataFlowFailures.invalidInput(
                            "dataflow_graph_source_identity_collision",
                            source.alias(),
                            "dataflow.graph.build");
                }
                if (seenSources.put(source, Boolean.TRUE) == null) {
                    sourceList.add(source);
                }
            }
            parameterList = DataFlowSupport.unionParameters(
                    parameterList, operation.requiredParameters());
            if (operation.effectful()) {
                if (effectNodes.put(operation, Boolean.TRUE) != null) {
                    throw DataFlowFailures.invalidInput(
                            "dataflow_graph_effect_fan_out",
                            output.slot.name(),
                            "dataflow.graph.build");
                }
                if (hasEffect) {
                    throw DataFlowFailures.invalidInput(
                            "dataflow_graph_multiple_effects",
                            output.slot.name(),
                            "dataflow.graph.build");
                }
                hasEffect = true;
            }
        }
        physicalText.append(']');
        sources = Collections.unmodifiableList(sourceList);
        parameters = parameterList;
        uniqueOperations = Collections.unmodifiableList(operationList);
        effectful = hasEffect;
        branchParallelSafe = !hasEffect && allBranchesSafe;
        canonical = canonicalText.toString();
        logical = logicalText.toString();
        physical = physicalText.toString();
    }

    @Override
    public List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return sources;
    }

    @Override
    public List<ParameterSlot<?>> requiredParameters() {
        return parameters;
    }

    @Override
    public String canonicalForm() {
        return canonical;
    }

    @Override
    public String logicalShape() {
        return "FiniteGraph -> Results<" + outputs.size() + ">";
    }

    @Override
    public String logicalPlan() {
        return logical;
    }

    @Override
    public String physicalPlan() {
        return physical;
    }

    @Override
    public boolean effectful() {
        return effectful;
    }

    @Override
    public boolean parallelBranchSafe() {
        return branchParallelSafe;
    }

    @Override
    public ExecutionOutcome<DataFlowResults> execute(
            ExecutionFrame frame) {
        if (parallelEligible(frame)) {
            return executeParallel(frame);
        }
        return executeSequential(frame);
    }

    private ExecutionOutcome<DataFlowResults> executeSequential(
            ExecutionFrame frame) {
        final Object[] values = frame.newOutputObjects(
                outputs.size(), "dataflow.graph.results");
        IdentityHashMap<DataFlowOperation<?>, ExecutionOutcome<?>> executed =
                new IdentityHashMap<
                        DataFlowOperation<?>, ExecutionOutcome<?>>();
        DeferredEffect<?> effect = null;
        int effectOrdinal = -1;
        long scanned = 0L;
        long matched = 0L;
        long outputElements = outputs.size();
        int tasks = 0;
        int workers = 0;

        for (GraphOutput<?> output : outputs) {
            ExecutionOutcome<?> outcome = executed.get(output.operation);
            if (outcome == null) {
                outcome = output.operation.execute(frame);
                executed.put(output.operation, outcome);
                scanned = add(scanned, outcome.scanned, "scanned");
                matched = add(matched, outcome.matched, "matched");
                outputElements = add(
                        outputElements,
                        outcome.outputElements,
                        "output");
                tasks = add(tasks, outcome.tasks, "tasks");
                workers = Math.max(workers, outcome.workers);
            }
            if (outcome.effect != null) {
                effect = outcome.effect;
                effectOrdinal = output.slot.ordinal();
            } else {
                values[output.slot.ordinal()] = outcome.result;
            }
        }

        if (effect == null) {
            return new ExecutionOutcome<DataFlowResults>(
                    new DataFlowResults(resultOwner, values),
                    scanned,
                    matched,
                    outputElements,
                    tasks,
                    workers);
        }

        final DeferredEffect<?> pending = effect;
        final int pendingOrdinal = effectOrdinal;
        return ExecutionOutcome.effect(
                new DeferredEffect<DataFlowResults>() {
                    @Override
                    public DataFlowResults commit() {
                        values[pendingOrdinal] = pending.commit();
                        return new DataFlowResults(resultOwner, values);
                    }
                },
                scanned,
                matched,
                outputElements,
                tasks,
                workers);
    }

    private ExecutionOutcome<DataFlowResults> executeParallel(
            final ExecutionFrame frame) {
        final Object[] values = frame.newOutputObjects(
                outputs.size(), "dataflow.graph.results");
        int workers = Math.min(
                uniqueOperations.size(),
                Math.min(
                        frame.context().workers(),
                        frame.budget().maximumWorkers()));
        MorselPlan plan = MorselPlan.create(
                uniqueOperations.size(),
                uniqueOperations.size(),
                workers,
                0L);
        List<ExecutionOutcome<?>> outcomes = BoundedMorselScheduler.run(
                frame,
                plan,
                new MorselWork<ExecutionOutcome<?>>() {
                    @Override
                    public ExecutionOutcome<?> execute(
                            int partition,
                            int startInclusive,
                            int endExclusive) {
                        if (endExclusive - startInclusive != 1) {
                            throw DataFlowFailures.internal(
                                    "dataflow_graph_partition_shape",
                                    "graph",
                                    "dataflow.graph.parallel",
                                    startInclusive + "/" + endExclusive);
                        }
                        return uniqueOperations.get(startInclusive)
                                .execute(frame.sequentialChild());
                    }
                },
                "dataflow.graph.parallel");
        IdentityHashMap<DataFlowOperation<?>, ExecutionOutcome<?>> executed =
                new IdentityHashMap<
                        DataFlowOperation<?>, ExecutionOutcome<?>>();
        long scanned = 0L;
        long matched = 0L;
        long outputElements = outputs.size();
        int tasks = 0;
        for (int index = 0; index < uniqueOperations.size(); index++) {
            ExecutionOutcome<?> outcome = outcomes.get(index);
            if (outcome.effect != null) {
                throw DataFlowFailures.internal(
                        "dataflow_graph_parallel_effect",
                        "graph",
                        "dataflow.graph.parallel",
                        Integer.toString(index));
            }
            executed.put(uniqueOperations.get(index), outcome);
            scanned = add(scanned, outcome.scanned, "scanned");
            matched = add(matched, outcome.matched, "matched");
            outputElements = add(
                    outputElements,
                    outcome.outputElements,
                    "output");
            tasks = add(tasks, outcome.tasks, "tasks");
        }
        for (GraphOutput<?> output : outputs) {
            values[output.slot.ordinal()] =
                    executed.get(output.operation).result;
        }
        return new ExecutionOutcome<DataFlowResults>(
                new DataFlowResults(resultOwner, values),
                scanned,
                matched,
                outputElements,
                Math.max(tasks, uniqueOperations.size()),
                Math.min(
                        uniqueOperations.size(),
                        Math.min(
                                frame.context().workers(),
                                frame.budget().maximumWorkers())));
    }

    private boolean parallelEligible(ExecutionFrame frame) {
        if (!branchParallelSafe
                || uniqueOperations.size() < 2
                || frame.policy().mode()
                != ExecutionPolicy.Mode.ADAPTIVE_PARALLEL
                || frame.context().executor() == null
                || frame.context().workers() <= 1
                || uniqueOperations.size()
                > frame.budget().maximumTasks()
                || frame.budget().maximumWorkers() <= 1) {
            return false;
        }
        long cardinality = 0L;
        for (SourceSlot<? extends DataFlowBinding> source : sources) {
            int size = frame.binding(source).packedSize();
            if (size < 0 || cardinality > Long.MAX_VALUE - size) {
                return false;
            }
            cardinality += size;
        }
        return cardinality
                >= frame.policy().minimumParallelCardinality();
    }

    private static long add(long left, long right, String metric) {
        if (right < 0L || left > Long.MAX_VALUE - right) {
            throw DataFlowFailures.resource(
                    "dataflow_graph_metric_overflow",
                    metric,
                    "dataflow.graph.execute",
                    "overflow");
        }
        return left + right;
    }

    private static int add(int left, int right, String metric) {
        if (right < 0 || left > Integer.MAX_VALUE - right) {
            throw DataFlowFailures.resource(
                    "dataflow_graph_metric_overflow",
                    metric,
                    "dataflow.graph.execute",
                    "overflow");
        }
        return left + right;
    }
}
