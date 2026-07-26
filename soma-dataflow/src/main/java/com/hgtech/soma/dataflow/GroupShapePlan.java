package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

final class GroupShapePlan<B extends DataFlowBinding> {
    private static final int NONE = 0;
    private static final int ASCENDING = 1;
    private static final int DESCENDING = 2;

    private final long minimumCount;
    private final long maximumCount;
    private final int countOrder;
    private final String canonical;

    private GroupShapePlan(
            long minimumCount,
            long maximumCount,
            int countOrder) {
        this.minimumCount = minimumCount;
        this.maximumCount = maximumCount;
        this.countOrder = countOrder;
        canonical = "group-shape-v1|min=" + minimumCount
                + "|max=" + maximumCount
                + "|order=" + countOrder;
    }

    static <B extends DataFlowBinding> GroupShapePlan<B> empty() {
        return new GroupShapePlan<B>(
                0L, Long.MAX_VALUE, NONE);
    }

    GroupShapePlan<B> havingAtLeast(long count) {
        return new GroupShapePlan<B>(
                Math.max(minimumCount, count),
                maximumCount,
                countOrder);
    }

    GroupShapePlan<B> havingAtMost(long count) {
        return new GroupShapePlan<B>(
                minimumCount,
                Math.min(maximumCount, count),
                countOrder);
    }

    GroupShapePlan<B> orderByCount(boolean descending) {
        return new GroupShapePlan<B>(
                minimumCount,
                maximumCount,
                descending ? DESCENDING : ASCENDING);
    }

    String canonical() {
        return canonical;
    }

    boolean isIdentity() {
        return minimumCount == 0L
                && maximumCount == Long.MAX_VALUE
                && countOrder == NONE;
    }

    GroupPrepared<B> apply(
            ExecutionFrame frame, GroupPrepared<B> input) {
        if (isIdentity()) {
            return input;
        }
        int[] ordinals = frame.newScratchIndexes(
                input.groupCount, "dataflow.group.shape");
        int retained = 0;
        for (int group = 0; group < input.groupCount; group++) {
            long size = input.offsets[group + 1] - input.offsets[group];
            if (size >= minimumCount && size <= maximumCount) {
                ordinals[retained++] = group;
            }
        }
        if (countOrder != NONE) {
            int[] orderScratch = frame.newScratchIndexes(
                    retained, "dataflow.group.shape.sort");
            stableSortByCount(
                    ordinals,
                    orderScratch,
                    retained,
                    input.offsets,
                    countOrder == DESCENDING);
        }
        int[] representatives = frame.newScratchIndexes(
                retained, "dataflow.group.shape");
        int[] offsets = frame.newScratchIndexes(
                retained + 1, "dataflow.group.shape");
        int members = 0;
        for (int output = 0; output < retained; output++) {
            int source = ordinals[output];
            representatives[output] = input.representatives[source];
            members += input.offsets[source + 1] - input.offsets[source];
            offsets[output + 1] = members;
        }
        int[] selectedMembers = frame.newScratchIndexes(
                members, "dataflow.group.shape");
        int write = 0;
        for (int output = 0; output < retained; output++) {
            int source = ordinals[output];
            int start = input.offsets[source];
            int size = input.offsets[source + 1] - start;
            System.arraycopy(
                    input.members,
                    start,
                    selectedMembers,
                    write,
                    size);
            write += size;
        }
        return new GroupPrepared<B>(
                input.selected,
                input.binding,
                retained,
                representatives,
                offsets,
                selectedMembers,
                members);
    }

    private static void stableSortByCount(
            int[] ordinals,
            int[] scratch,
            int size,
            int[] offsets,
            boolean descending) {
        for (int width = 1; width < size; width = nextWidth(width, size)) {
            int block = width > Integer.MAX_VALUE / 2
                    ? Integer.MAX_VALUE : width * 2;
            for (int start = 0; start < size; ) {
                int middle = (long) start + width >= size
                        ? size : start + width;
                int end = (long) start + block >= size
                        ? size : start + block;
                int left = start;
                int right = middle;
                int output = start;
                while (left < middle || right < end) {
                    boolean takeLeft = right >= end
                            || (left < middle && compare(
                            ordinals[left],
                            ordinals[right],
                            offsets,
                            descending) <= 0);
                    scratch[output++] =
                            takeLeft ? ordinals[left++] : ordinals[right++];
                }
                if (end == size) {
                    break;
                }
                start = end;
            }
            System.arraycopy(scratch, 0, ordinals, 0, size);
        }
    }

    private static int compare(
            int left,
            int right,
            int[] offsets,
            boolean descending) {
        int leftSize = offsets[left + 1] - offsets[left];
        int rightSize = offsets[right + 1] - offsets[right];
        int compared = Integer.compare(leftSize, rightSize);
        return descending ? -compared : compared;
    }

    private static int nextWidth(int width, int size) {
        return width > size / 2 ? size : width * 2;
    }
}
