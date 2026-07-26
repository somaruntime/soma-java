package com.hgtech.soma.dataflow.generated;

/**
 * Generated cursor-borrow boundary. The callback object is statically typed by
 * the generated source method before it crosses this narrow runtime protocol.
 */
public interface CandidateBorrowAccess<B extends DataFlowBinding> {
    void borrow(B binding, int[] indexes, int count, Object consumer);

    String identity();
}
