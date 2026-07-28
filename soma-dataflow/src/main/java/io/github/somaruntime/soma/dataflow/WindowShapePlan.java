package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

final class WindowShapePlan<B extends DataFlowBinding> {
    private final long minimumCount;
    private final long maximumCount;
    private final String canonical;

    private WindowShapePlan(long minimumCount, long maximumCount) {
        this.minimumCount = minimumCount;
        this.maximumCount = maximumCount;
        canonical = "window-shape-v1|min=" + minimumCount
                + "|max=" + maximumCount;
    }

    static <B extends DataFlowBinding> WindowShapePlan<B> empty() {
        return new WindowShapePlan<B>(0L, Long.MAX_VALUE);
    }

    WindowShapePlan<B> havingAtLeast(long count) {
        return new WindowShapePlan<B>(
                Math.max(minimumCount, count), maximumCount);
    }

    WindowShapePlan<B> havingAtMost(long count) {
        return new WindowShapePlan<B>(
                minimumCount, Math.min(maximumCount, count));
    }

    String canonical() {
        return canonical;
    }

    boolean isIdentity() {
        return minimumCount == 0L
                && maximumCount == Long.MAX_VALUE;
    }

    WindowPrepared<B> apply(WindowPrepared<B> input) {
        if (isIdentity()) {
            return input;
        }
        int write = 0;
        long members = 0L;
        for (int window = 0; window < input.windowCount; window++) {
            long size = input.ends[window] - input.starts[window];
            if (size >= minimumCount && size <= maximumCount) {
                input.starts[write] = input.starts[window];
                input.ends[write] = input.ends[window];
                members += size;
                write++;
            }
        }
        return new WindowPrepared<B>(
                input.selected,
                input.binding,
                input.starts,
                input.ends,
                write,
                members);
    }
}
