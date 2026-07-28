package io.github.somaruntime.soma.dataflow;

import java.util.Arrays;

/**
 * Detached copy of invocation-time group membership expressed as current Index.
 *
 * <p>It is not a stable row snapshot and becomes stale after source mutation.</p>
 */
public final class GroupIndexResult {
    private final String source;
    private final long structuralEpoch;
    private final int[] representatives;
    private final int[] offsets;
    private final int[] indexes;

    GroupIndexResult(
            String source,
            long structuralEpoch,
            int[] representatives,
            int[] offsets,
            int[] indexes) {
        this.source = source;
        this.structuralEpoch = structuralEpoch;
        this.representatives = representatives;
        this.offsets = offsets;
        this.indexes = indexes;
    }

    public String source() {
        return source;
    }

    public long structuralEpoch() {
        return structuralEpoch;
    }

    public int groupCount() {
        return representatives.length;
    }

    public int representativeIndex(int group) {
        checkGroup(group);
        return representatives[group];
    }

    public int groupSize(int group) {
        checkGroup(group);
        return offsets[group + 1] - offsets[group];
    }

    public int indexAt(int group, int position) {
        checkGroup(group);
        int start = offsets[group];
        int size = offsets[group + 1] - start;
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("group position out of range");
        }
        return indexes[start + position];
    }

    public int[] representativeIndexes() {
        return Arrays.copyOf(representatives, representatives.length);
    }

    private void checkGroup(int group) {
        if (group < 0 || group >= representatives.length) {
            throw new IndexOutOfBoundsException("group ordinal out of range");
        }
    }
}
