package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/**
 * Narrow construction bridge used by generated schema companions.
 *
 * <p>Application code receives generated typed sources and does not implement this
 * protocol directly.</p>
 */
public final class GeneratedDataFlow {
    public static final String TRANSFORMATION_PROTOCOL = "soma-transformation-v1";
    public static final String KERNEL_PROTOCOL = "soma-kernel-v1";

    private GeneratedDataFlow() {
    }

    public static <B extends DataFlowBinding> CandidateFlow<B> candidates(
            SourceSlot<B> source) {
        return new CandidateFlow<B>(source);
    }
}
