package com.hgtech.soma.dataflow.generated;

/**
 * Generated parent-to-owned-child binding bridge.
 *
 * <p>The returned child binding is read only and remains protected by the
 * parent ownership aggregate guard. A {@code null} binding denotes an absent
 * or not-yet-created empty child collection. This is not an application SPI.</p>
 */
public interface OwnedChildAccess<
        P extends DataFlowBinding, C extends DataFlowBinding> {
    C childBinding(P parentBinding, int parentIndex);

    /**
     * Validated finite upper bound for one owned child Table, or {@code -1}
     * when the generated binding cannot prove one.
     */
    int maximumChildRows(P parentBinding);

    String identity();
}
