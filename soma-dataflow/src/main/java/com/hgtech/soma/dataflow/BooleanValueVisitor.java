package com.hgtech.soma.dataflow;

/** Callback-scoped visitor for one projected primitive boolean value。 */
public interface BooleanValueVisitor {
    /** 返回 {@code false} 表示消费当前 value 后 early stop。 */
    boolean visit(boolean value);
}
