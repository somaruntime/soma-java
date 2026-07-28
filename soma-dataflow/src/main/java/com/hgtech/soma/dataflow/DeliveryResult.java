package com.hgtech.soma.dataflow;

/**
 * Callback-scoped delivery 的 detached terminal result。
 *
 * <p>结果只在 visitor 正常返回且全部 guard、scratch 与 lease 完成清理后发布。
 * {@code completed=false} 表示 visitor 在消费当前元素后主动 early stop。</p>
 */
public final class DeliveryResult {
    private final long deliveredElements;
    private final boolean completed;

    DeliveryResult(long deliveredElements, boolean completed) {
        if (deliveredElements < 0L) {
            throw new IllegalArgumentException(
                    "deliveredElements must be non-negative");
        }
        this.deliveredElements = deliveredElements;
        this.completed = completed;
    }

    public long deliveredElements() {
        return deliveredElements;
    }

    public boolean completed() {
        return completed;
    }
}
