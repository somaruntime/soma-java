package com.hgtech.soma.dataflow;

/** Callback-scoped consumer for one projected primitive double value. */
public interface DoubleValueConsumer {
    void accept(double value);
}
