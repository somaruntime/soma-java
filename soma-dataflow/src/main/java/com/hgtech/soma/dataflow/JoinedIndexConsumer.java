package com.hgtech.soma.dataflow;

/**
 * Invocation-scoped low-materialization Join consumer.
 *
 * <p>Indexes are current physical positions and must not escape the callback.
 * An absent right side is represented only by {@code rightPresent=false}.</p>
 */
public interface JoinedIndexConsumer {
    void accept(int leftIndex, boolean rightPresent, int rightIndex);
}
