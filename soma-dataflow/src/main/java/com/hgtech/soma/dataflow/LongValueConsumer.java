package com.hgtech.soma.dataflow;

/** Callback-scoped consumer for one projected primitive long value. */
public interface LongValueConsumer {
    void accept(long value);
}
