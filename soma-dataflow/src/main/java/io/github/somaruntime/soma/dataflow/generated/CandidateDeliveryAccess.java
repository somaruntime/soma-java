package io.github.somaruntime.soma.dataflow.generated;

/**
 * Generated callback delivery boundary。
 *
 * <p>Access 属于可复用 Definition；visitor 只用于打开单次 Invocation session，
 * 不得被 Access 保存。</p>
 */
public interface CandidateDeliveryAccess<
        B extends DataFlowBinding, V> {
    CandidateDeliverySession open(B binding, V visitor);

    String identity();
}
