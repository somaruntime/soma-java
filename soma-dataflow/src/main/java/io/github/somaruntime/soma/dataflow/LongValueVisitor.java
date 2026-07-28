package io.github.somaruntime.soma.dataflow;

/** Callback-scoped visitor for one projected primitive long value。 */
public interface LongValueVisitor {
    /** 返回 {@code false} 表示消费当前 value 后 early stop。 */
    boolean visit(long value);
}
