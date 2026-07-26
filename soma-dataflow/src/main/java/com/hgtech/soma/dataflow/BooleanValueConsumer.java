package com.hgtech.soma.dataflow;

/** Callback-scoped consumer for one projected primitive boolean value. */
public interface BooleanValueConsumer {
    void accept(boolean value);
}
