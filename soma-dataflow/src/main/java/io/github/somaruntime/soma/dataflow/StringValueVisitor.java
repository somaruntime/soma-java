package io.github.somaruntime.soma.dataflow;

/** Callback-scoped visitor for one projected immutable String value。 */
public interface StringValueVisitor {
    /** 返回 {@code false} 表示消费当前 value 后 early stop。 */
    boolean visit(String value);
}
