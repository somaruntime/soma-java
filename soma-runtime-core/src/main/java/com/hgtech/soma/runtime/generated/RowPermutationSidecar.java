package com.hgtech.soma.runtime.generated;

/**
 * Index/order generated binding 使用的 primitive RowSlot permutation sidecar。
 *
 * <p>Rebuild 先在 detached staged array 完成；只有 generated comparator/unique
 * validation 全部成功后才发布，因此旧 current permutation 不会被 partial rebuild 污染。</p>
 */
public final class RowPermutationSidecar {
    private int[] rows = new int[0];
    private int[] scratch = new int[0];
    private int size;
    private boolean dirty = true;

    public RowPermutationSidecar() {
    }

    public boolean isDirty() {
        return dirty;
    }

    public int size() {
        return size;
    }

    public int rowAt(int index) {
        if (dirty) {
            throw new IllegalStateException("sidecar is dirty");
        }
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("sidecar index out of range");
        }
        return rows[index];
    }

    public int[] stage(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("required must be non-negative");
        }
        return rows.length < required ? new int[required] : rows;
    }

    public int[] scratch(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("required must be non-negative");
        }
        if (scratch.length < required) {
            scratch = new int[required];
        }
        return scratch;
    }

    public long retainedBytes() {
        return 4L * ((long) rows.length + (long) scratch.length);
    }

    public long retainedBytesAfterRebuild(int required) {
        if (required < 0) throw new IllegalArgumentException("required must be non-negative");
        return 4L * ((long) Math.max(rows.length, required)
                + (long) Math.max(scratch.length, required));
    }

    /** Peak bytes while growing detached permutation and merge scratch for one rebuild. */
    public long rebuildPeakBytes(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("required must be non-negative");
        }
        long peakInts = (long) rows.length + (long) scratch.length;
        if (rows.length < required) peakInts += required;
        if (scratch.length < required) peakInts += required;
        return 4L * peakInts;
    }

    public void commit(int[] staged, int committedSize) {
        if (staged == null) {
            throw new NullPointerException("staged");
        }
        if (committedSize < 0 || committedSize > staged.length) {
            throw new IllegalArgumentException("invalid committed size");
        }
        rows = staged;
        size = committedSize;
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clear() {
        size = 0;
        dirty = false;
    }

    public void release() {
        rows = new int[0];
        scratch = new int[0];
        size = 0;
        dirty = true;
    }
}
