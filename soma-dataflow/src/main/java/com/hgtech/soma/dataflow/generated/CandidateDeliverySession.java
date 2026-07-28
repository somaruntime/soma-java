package com.hgtech.soma.dataflow.generated;

/**
 * 单次 Invocation 内有效的 generated cursor delivery session。
 *
 * <p>Session 不得逃逸 Invocation；实现必须复用 cursor，并在每次 callback 后关闭
 * cursor fence。</p>
 */
public interface CandidateDeliverySession extends AutoCloseable {
    /** 返回 {@code false} 表示消费当前 candidate 后 early stop。 */
    boolean visit(int index);

    @Override
    void close();
}
