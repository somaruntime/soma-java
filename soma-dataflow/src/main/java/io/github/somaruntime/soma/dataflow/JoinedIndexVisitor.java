package io.github.somaruntime.soma.dataflow;

/**
 * Invocation-scoped low-materialization Join visitor。
 *
 * <p>Index 是 callback 内有效的 current physical position；outer absence 只由
 * {@code rightPresent=false} 表达。</p>
 */
public interface JoinedIndexVisitor {
    /** 返回 {@code false} 表示消费当前 joined value 后 early stop。 */
    boolean visit(int leftIndex, boolean rightPresent, int rightIndex);
}
