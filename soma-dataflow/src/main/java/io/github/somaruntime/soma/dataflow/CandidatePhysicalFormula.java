package io.github.somaruntime.soma.dataflow;

/**
 * Candidate physical representation 的兼容性公式。
 *
 * <p>V1 闭集为 contiguous range、segment-aware range、exact single-pass 和
 * sparse indexes；新增形态必须更换 identity 并重新验真。</p>
 */
public final class CandidatePhysicalFormula {
    public static final String IDENTITY = "soma-candidate-physical-v2";

    private CandidatePhysicalFormula() {
    }
}
