package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

/**
 * Generated bulk边界使用的primitive detached distinct-group counter。
 * 每个group只保存一个代表性batch Index，不参与table read path。
 */
public final class ExactGroupCounter {
    private static final int NONE = -1;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final int[] EMPTY_INTS = new int[0];
    private static final long[] EMPTY_LONGS = new long[0];

    private int[] bucketHeads;
    private long[] groupHashes;
    private int[] nextHashGroups;
    private int[] representativeRows;
    private int groupCount;

    public ExactGroupCounter(int maximumGroups) {
        if (maximumGroups < 0) {
            throw new IllegalArgumentException("maximumGroups must be non-negative");
        }
        bucketHeads = new int[bucketCapacityFor(maximumGroups)];
        groupHashes = new long[maximumGroups];
        nextHashGroups = new int[maximumGroups];
        representativeRows = new int[maximumGroups];
        Arrays.fill(bucketHeads, NONE);
        Arrays.fill(nextHashGroups, NONE);
    }

    public static long estimatedRetainedBytes(int maximumGroups) {
        if (maximumGroups < 0) {
            throw new IllegalArgumentException("maximumGroups must be non-negative");
        }
        long groupBytes = 16L * (long) maximumGroups;
        long bucketBytes = 4L * (long) bucketCapacityFor(maximumGroups);
        return Long.MAX_VALUE - groupBytes < bucketBytes
                ? Long.MAX_VALUE : groupBytes + bucketBytes;
    }

    public int groupCount() {
        return groupCount;
    }

    public int firstGroup(long hash) {
        int group = bucketHeads[bucket(hash, bucketHeads.length)];
        while (group != NONE && groupHashes[group] != hash) {
            group = nextHashGroups[group];
        }
        return group;
    }

    public int nextHashGroup(int group) {
        requireGroup(group);
        long hash = groupHashes[group];
        int candidate = nextHashGroups[group];
        while (candidate != NONE && groupHashes[candidate] != hash) {
            candidate = nextHashGroups[candidate];
        }
        return candidate;
    }

    public int representativeRow(int group) {
        requireGroup(group);
        return representativeRows[group];
    }

    public int createGroup(long hash, int representativeRow) {
        if (representativeRow < 0) {
            throw new IllegalArgumentException("representativeRow must be non-negative");
        }
        if (groupCount >= groupHashes.length) {
            throw new IllegalStateException("exact-group counter capacity exhausted");
        }
        int bucket = bucket(hash, bucketHeads.length);
        int group = groupCount++;
        groupHashes[group] = hash;
        representativeRows[group] = representativeRow;
        nextHashGroups[group] = bucketHeads[bucket];
        bucketHeads[bucket] = group;
        return group;
    }

    public long retainedBytes() {
        return 16L * (long) groupHashes.length
                + 4L * (long) bucketHeads.length;
    }

    public void release() {
        bucketHeads = EMPTY_INTS;
        groupHashes = EMPTY_LONGS;
        nextHashGroups = EMPTY_INTS;
        representativeRows = EMPTY_INTS;
        groupCount = 0;
    }

    private void requireGroup(int group) {
        if (group < 0 || group >= groupCount) {
            throw new IllegalArgumentException("group is not live");
        }
    }

    private static int bucketCapacityFor(int groups) {
        long required = Math.max(4L, 2L * (long) groups + 1L);
        int capacity = 4;
        while ((long) capacity < required) {
            if (capacity >= MAX_CAPACITY) {
                throw new IllegalArgumentException("exact-group counter capacity exhausted");
            }
            capacity <<= 1;
        }
        return capacity;
    }

    private static int bucket(long hash, int capacity) {
        if (capacity == 0) throw new IllegalStateException("exact-group counter is released");
        return mix(hash) & (capacity - 1);
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int) (value ^ (value >>> 32));
    }
}
