package com.hgtech.soma.dataflow;

/** Opaque sequential consumer for one finite logical window. */
public interface WindowConsumer {
    void accept(WindowCursor window);
}
