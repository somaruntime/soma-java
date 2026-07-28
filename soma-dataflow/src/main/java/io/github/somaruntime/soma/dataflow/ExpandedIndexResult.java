package io.github.somaruntime.soma.dataflow;

import java.util.Arrays;

/**
 * Detached current-index coordinates produced by owned-child expansion.
 *
 * <p>Both coordinates are physical indexes, not stable identities. They are
 * invalid after mutation or lifecycle change of the source ownership
 * aggregate and are intended for immediate synchronous consumption.</p>
 */
public final class ExpandedIndexResult {
    private final int[] parentIndexes;
    private final int[] childIndexes;

    ExpandedIndexResult(
            int[] parentIndexes, int[] childIndexes, int size) {
        this.parentIndexes = size == parentIndexes.length
                ? parentIndexes : Arrays.copyOf(parentIndexes, size);
        this.childIndexes = size == childIndexes.length
                ? childIndexes : Arrays.copyOf(childIndexes, size);
    }

    public int size() {
        return parentIndexes.length;
    }

    public int parentIndexAt(int position) {
        return parentIndexes[check(position)];
    }

    public int childIndexAt(int position) {
        return childIndexes[check(position)];
    }

    private int check(int position) {
        if (position < 0 || position >= parentIndexes.length) {
            throw new IndexOutOfBoundsException(
                    "position=" + position + ", size=" + parentIndexes.length);
        }
        return position;
    }
}
