package io.github.somaruntime.soma.dataflow;

/**
 * Detached, typed multi-output result of one finite DataFlow invocation.
 *
 * <p>Output values retain their own shape-specific contracts. This container
 * does not turn live Index or borrowed cursors into stable snapshots.</p>
 */
public final class DataFlowResults {
    private final Object owner;
    private final Object[] values;

    DataFlowResults(Object owner, Object[] values) {
        this.owner = owner;
        this.values = values;
    }

    public int size() {
        return values.length;
    }

    public <R> R get(OutputSlot<R> slot) {
        if (slot == null) {
            throw new NullPointerException("slot");
        }
        if (slot.owner() != owner
                || slot.ordinal() < 0
                || slot.ordinal() >= values.length) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_output_slot_mismatch",
                    slot.name(),
                    "dataflow.results");
        }
        @SuppressWarnings("unchecked")
        R value = (R) values[slot.ordinal()];
        return value;
    }
}
