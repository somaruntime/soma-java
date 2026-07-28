package com.hgtech.soma.dataflow;

/** Opaque sequential visitor for one finite logical window。 */
public interface WindowVisitor {
    /** 返回 {@code false} 表示消费当前 window 后 early stop。 */
    boolean visit(WindowCursor window);
}
