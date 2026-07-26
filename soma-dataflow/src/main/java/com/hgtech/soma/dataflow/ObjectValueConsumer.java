package com.hgtech.soma.dataflow;

/** Callback-scoped consumer for one projected reference/value. */
public interface ObjectValueConsumer<T> {
    void accept(T value);
}
