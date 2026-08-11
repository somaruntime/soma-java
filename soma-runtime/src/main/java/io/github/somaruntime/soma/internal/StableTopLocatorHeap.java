package io.github.somaruntime.soma.internal;

/**
 * Per-terminal max heap for typed top. Comparator ties retain the canonical
 * source ordinal, so the finished buffer is identical to stable sort + limit.
 */
final class StableTopLocatorHeap {

    private final BoundRowPlan bound;
    private final LogicalRowPlan.Stage order;
    private final IntLocatorBuffer values;
    private final long[] ordinals;

    StableTopLocatorHeap(
            BoundRowPlan bound,
            LogicalRowPlan.Stage order,
            long capacity) {
        this.bound = bound;
        this.order = order;
        this.values = new IntLocatorBuffer(
                capacity, bound.operation, bound.provenance);
        this.ordinals = new long[values.backing().length];
    }

    boolean hasCapacity() {
        return ordinals.length != 0;
    }

    void offer(int locator, long ordinal) {
        if (values.size() < ordinals.length) {
            int position = values.size();
            values.add(locator);
            ordinals[position] = ordinal;
            siftUp(position);
            return;
        }
        if (compare(locator, ordinal, values.get(0), ordinals[0]) >= 0) return;
        values.set(0, locator);
        ordinals[0] = ordinal;
        siftDown(0, values.size());
    }

    IntLocatorBuffer finish() {
        for (int end = values.size() - 1; end > 0; end--) {
            swap(0, end);
            siftDown(0, end);
        }
        return values;
    }

    private void siftUp(int position) {
        while (position > 0) {
            int parent = (position - 1) >>> 1;
            if (compare(
                    values.get(parent), ordinals[parent],
                    values.get(position), ordinals[position]) >= 0) {
                return;
            }
            swap(parent, position);
            position = parent;
        }
    }

    private void siftDown(int position, int size) {
        while (true) {
            int left = position * 2 + 1;
            if (left >= size) return;
            int worst = left;
            int right = left + 1;
            if (right < size && compare(
                    values.get(left), ordinals[left],
                    values.get(right), ordinals[right]) < 0) {
                worst = right;
            }
            if (compare(
                    values.get(position), ordinals[position],
                    values.get(worst), ordinals[worst]) >= 0) {
                return;
            }
            swap(position, worst);
            position = worst;
        }
    }

    private int compare(
            int leftLocator,
            long leftOrdinal,
            int rightLocator,
            long rightOrdinal) {
        int compared = RowExecutionSupport.compare(
                bound, leftLocator, rightLocator, order);
        return compared != 0
                ? compared
                : Long.compare(leftOrdinal, rightOrdinal);
    }

    private void swap(int left, int right) {
        int locator = values.get(left);
        values.set(left, values.get(right));
        values.set(right, locator);
        long ordinal = ordinals[left];
        ordinals[left] = ordinals[right];
        ordinals[right] = ordinal;
    }
}
