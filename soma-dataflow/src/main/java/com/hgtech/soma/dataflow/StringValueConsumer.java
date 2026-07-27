package com.hgtech.soma.dataflow;

/** Callback-scoped consumer for one projected String value. */
public interface StringValueConsumer {
    void accept(String value);
}
