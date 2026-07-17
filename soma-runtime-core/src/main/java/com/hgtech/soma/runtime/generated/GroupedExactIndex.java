package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/**
 * Primitive exact-access substrate shared by generated {@code @SomaIndex} and
 * {@code @SomaUnique} bindings。
 *
 * <p>The runtime owns only hash buckets, same-hash groups and per-row links.
 * Generated code owns canonical selector hashing and full leaf equality.</p>
 */
public final class GroupedExactIndex {
    private static final int NONE = -1;
    private static final byte FREE = 0;
    private static final byte LIVE = 1;
    private static final int MAX_CAPACITY = 1 << 30;

    private int[] bucketHeads;
    private long[] groupHashes;
    private int[] nextHashGroups;
    private int[] groupHeadRows;
    private int[] groupSizes;
    private byte[] groupStates;
    private int[] rowGroups;
    private int[] rowPrevious;
    private int[] rowNext;

    private int groupCount;
    private int entryCount;
    private int nextGroupSlot;
    private int freeGroupHead = NONE;
    private int freeGroupCount;
    private long probeCount;
    private long collisionCount;
    private long rehashCount;
    private long storageHighWaterBytes;

    public GroupedExactIndex(int expectedRows) {
        if (expectedRows < 0) {
            throw new IllegalArgumentException("expectedRows must be non-negative");
        }
        int rowCapacity = expectedRows;
        int groupCapacity = expectedRows == 0 ? 4 : expectedRows;
        int bucketCapacity = bucketCapacityFor(expectedRows);
        bucketHeads = new int[bucketCapacity];
        groupHashes = new long[groupCapacity];
        nextHashGroups = new int[groupCapacity];
        groupHeadRows = new int[groupCapacity];
        groupSizes = new int[groupCapacity];
        groupStates = new byte[groupCapacity];
        rowGroups = new int[rowCapacity];
        rowPrevious = new int[rowCapacity];
        rowNext = new int[rowCapacity];
        Arrays.fill(bucketHeads, NONE);
        Arrays.fill(nextHashGroups, NONE);
        Arrays.fill(groupHeadRows, NONE);
        Arrays.fill(rowGroups, NONE);
        Arrays.fill(rowPrevious, NONE);
        Arrays.fill(rowNext, NONE);
        storageHighWaterBytes = retainedBytes();
    }

    public static long estimatedRetainedBytes(int rows, int groups) {
        if (rows < 0 || groups < 0 || groups > rows) {
            throw new IllegalArgumentException("invalid exact-index estimate");
        }
        int groupCapacity = groups == 0 ? 4 : groups;
        return retainedBytes(rows, groupCapacity, bucketCapacityFor(groups));
    }

    public int entryCount() {
        return entryCount;
    }

    public int groupCount() {
        return groupCount;
    }

    public long probeCount() {
        return probeCount;
    }

    public long collisionCount() {
        return collisionCount;
    }

    public long rehashCount() {
        return rehashCount;
    }

    public long retainedBytes() {
        return retainedBytes(rowGroups.length, groupHashes.length, bucketHeads.length);
    }

    public long storageHighWaterBytes() {
        return storageHighWaterBytes;
    }

    public long retainedBytesAfterEnsure(int requiredRows, int additionalGroups) {
        validateCapacityRequest(requiredRows, additionalGroups);
        return retainedBytes(
                targetRowCapacity(requiredRows),
                targetGroupCapacity(additionalGroups),
                targetBucketCapacity(additionalGroups));
    }

    /**
     * Grows row/group/bucket arrays before visible mutation. Publication is all-or-nothing.
     */
    public void ensureCapacity(int requiredRows, int additionalGroups) {
        validateCapacityRequest(requiredRows, additionalGroups);
        int targetRows = targetRowCapacity(requiredRows);
        int targetGroups = targetGroupCapacity(additionalGroups);
        int targetBuckets = targetBucketCapacity(additionalGroups);
        boolean growRows = targetRows != rowGroups.length;
        boolean growGroups = targetGroups != groupHashes.length;
        boolean growBuckets = targetBuckets != bucketHeads.length;
        if (!growRows && !growGroups && !growBuckets) return;

        int[] newRowGroups = growRows
                ? Arrays.copyOf(rowGroups, targetRows) : rowGroups;
        int[] newRowPrevious = growRows
                ? Arrays.copyOf(rowPrevious, targetRows) : rowPrevious;
        int[] newRowNext = growRows
                ? Arrays.copyOf(rowNext, targetRows) : rowNext;
        if (growRows) {
            Arrays.fill(newRowGroups, rowGroups.length, targetRows, NONE);
            Arrays.fill(newRowPrevious, rowPrevious.length, targetRows, NONE);
            Arrays.fill(newRowNext, rowNext.length, targetRows, NONE);
        }

        long[] newGroupHashes = growGroups
                ? Arrays.copyOf(groupHashes, targetGroups) : groupHashes;
        int[] newGroupHeads = growGroups
                ? Arrays.copyOf(groupHeadRows, targetGroups) : groupHeadRows;
        int[] newGroupSizes = growGroups
                ? Arrays.copyOf(groupSizes, targetGroups) : groupSizes;
        byte[] newGroupStates = growGroups
                ? Arrays.copyOf(groupStates, targetGroups) : groupStates;
        int[] newNextGroups = growGroups || growBuckets
                ? Arrays.copyOf(nextHashGroups, targetGroups) : nextHashGroups;
        if (growGroups) {
            Arrays.fill(newGroupHeads, groupHeadRows.length, targetGroups, NONE);
            Arrays.fill(newNextGroups, nextHashGroups.length, targetGroups, NONE);
        }

        int[] newBucketHeads = bucketHeads;
        if (growBuckets) {
            newBucketHeads = new int[targetBuckets];
            Arrays.fill(newBucketHeads, NONE);
            for (int group = 0; group < nextGroupSlot; group++) {
                if (newGroupStates[group] != LIVE) continue;
                int bucket = bucket(newGroupHashes[group], targetBuckets);
                newNextGroups[group] = newBucketHeads[bucket];
                newBucketHeads[bucket] = group;
            }
        }

        rowGroups = newRowGroups;
        rowPrevious = newRowPrevious;
        rowNext = newRowNext;
        groupHashes = newGroupHashes;
        groupHeadRows = newGroupHeads;
        groupSizes = newGroupSizes;
        groupStates = newGroupStates;
        nextHashGroups = newNextGroups;
        bucketHeads = newBucketHeads;
        if (growBuckets) {
            rehashCount = saturatedAdd(rehashCount, 1L);
            probeCount = saturatedAdd(probeCount, groupCount);
        }
        updateHighWater();
    }

    /** Returns the first live group with the requested hash, or {@code -1}。 */
    public int firstGroup(long hash) {
        int group = bucketHeads[bucket(hash, bucketHeads.length)];
        if (group == NONE) probeCount = saturatedAdd(probeCount, 1L);
        while (group != NONE) {
            requireLiveGroup(group);
            probeCount = saturatedAdd(probeCount, 1L);
            if (groupHashes[group] == hash) return group;
            collisionCount = saturatedAdd(collisionCount, 1L);
            group = nextHashGroups[group];
        }
        return NONE;
    }

    /** Continues along groups with the same hash after generated full-equality rejected one。 */
    public int nextHashGroup(int group) {
        requireLiveGroup(group);
        long hash = groupHashes[group];
        int candidate = nextHashGroups[group];
        while (candidate != NONE) {
            requireLiveGroup(candidate);
            probeCount = saturatedAdd(probeCount, 1L);
            if (groupHashes[candidate] == hash) return candidate;
            collisionCount = saturatedAdd(collisionCount, 1L);
            candidate = nextHashGroups[candidate];
        }
        return NONE;
    }

    /** Records a same-hash/full-equality collision observed by generated code。 */
    public void recordCollision() {
        collisionCount = saturatedAdd(collisionCount, 1L);
    }

    /** Creates an empty group; caller must have called {@link #ensureCapacity(int, int)}。 */
    public int createGroup(long hash) {
        if (2L * ((long) groupCount + 1L) >= (long) bucketHeads.length) {
            throw new IllegalStateException("exact-index bucket capacity not prepared");
        }
        int group;
        if (freeGroupHead != NONE) {
            group = freeGroupHead;
            freeGroupHead = nextHashGroups[group];
            freeGroupCount--;
        } else {
            if (nextGroupSlot >= groupHashes.length) {
                throw new IllegalStateException("exact-index group capacity not prepared");
            }
            group = nextGroupSlot++;
        }
        int bucket = bucket(hash, bucketHeads.length);
        groupStates[group] = LIVE;
        groupHashes[group] = hash;
        groupHeadRows[group] = NONE;
        groupSizes[group] = 0;
        nextHashGroups[group] = bucketHeads[bucket];
        bucketHeads[bucket] = group;
        groupCount++;
        return group;
    }

    public int representativeRow(int group) {
        requireLiveGroup(group);
        int row = groupHeadRows[group];
        if (row == NONE) throw new IllegalStateException("exact-index group is empty");
        return row;
    }

    public int groupSize(int group) {
        requireLiveGroup(group);
        return groupSizes[group];
    }

    public int firstRow(int group) {
        requireLiveGroup(group);
        return groupHeadRows[group];
    }

    public int nextRow(int row) {
        requireLinkedRow(row);
        return rowNext[row];
    }

    /** Returns whether the packed Index currently participates in this exact index. */
    public boolean isLinked(int row) {
        requireRowCapacity(row);
        return rowGroups[row] != NONE;
    }

    public void link(int group, int row) {
        requireLiveGroup(group);
        requireRowCapacity(row);
        if (rowGroups[row] != NONE) {
            throw new IllegalArgumentException("row is already linked");
        }
        int previousHead = groupHeadRows[group];
        rowGroups[row] = group;
        rowPrevious[row] = NONE;
        rowNext[row] = previousHead;
        if (previousHead != NONE) rowPrevious[previousHead] = row;
        groupHeadRows[group] = row;
        groupSizes[group]++;
        entryCount++;
    }

    public void unlink(int row) {
        requireLinkedRow(row);
        int group = rowGroups[row];
        int previous = rowPrevious[row];
        int next = rowNext[row];
        if (previous == NONE) groupHeadRows[group] = next;
        else rowNext[previous] = next;
        if (next != NONE) rowPrevious[next] = previous;
        clearRowLink(row);
        groupSizes[group]--;
        entryCount--;
        if (groupSizes[group] == 0) releaseGroup(group);
    }

    /** Moves linked metadata from one packed Index to an unlinked destination Index。 */
    public void relocate(int from, int to) {
        if (from == to) return;
        requireLinkedRow(from);
        requireRowCapacity(to);
        if (rowGroups[to] != NONE) {
            throw new IllegalArgumentException("relocation destination is linked");
        }
        int group = rowGroups[from];
        int previous = rowPrevious[from];
        int next = rowNext[from];
        rowGroups[to] = group;
        rowPrevious[to] = previous;
        rowNext[to] = next;
        if (previous == NONE) groupHeadRows[group] = to;
        else rowNext[previous] = to;
        if (next != NONE) rowPrevious[next] = to;
        clearRowLink(from);
    }

    public void clear() {
        Arrays.fill(bucketHeads, NONE);
        Arrays.fill(groupStates, FREE);
        Arrays.fill(nextHashGroups, NONE);
        Arrays.fill(groupHeadRows, NONE);
        Arrays.fill(groupSizes, 0);
        Arrays.fill(rowGroups, NONE);
        Arrays.fill(rowPrevious, NONE);
        Arrays.fill(rowNext, NONE);
        groupCount = 0;
        entryCount = 0;
        nextGroupSlot = 0;
        freeGroupHead = NONE;
        freeGroupCount = 0;
    }

    public void resetMetrics() {
        probeCount = 0L;
        collisionCount = 0L;
        rehashCount = 0L;
    }

    public void inheritMetrics(
            long probes,
            long collisions,
            long rehashes,
            long previousStorageHighWaterBytes) {
        if (probes < 0L || collisions < 0L || collisions > probes
                || rehashes < 0L || previousStorageHighWaterBytes < 0L) {
            throw new IllegalArgumentException("invalid exact-index metrics");
        }
        probeCount = saturatedAdd(probeCount, probes);
        collisionCount = saturatedAdd(collisionCount, collisions);
        rehashCount = saturatedAdd(rehashCount, rehashes);
        if (previousStorageHighWaterBytes > storageHighWaterBytes) {
            storageHighWaterBytes = previousStorageHighWaterBytes;
        }
    }

    public void release() {
        bucketHeads = new int[0];
        groupHashes = new long[0];
        nextHashGroups = new int[0];
        groupHeadRows = new int[0];
        groupSizes = new int[0];
        groupStates = new byte[0];
        rowGroups = new int[0];
        rowPrevious = new int[0];
        rowNext = new int[0];
        groupCount = 0;
        entryCount = 0;
        nextGroupSlot = 0;
        freeGroupHead = NONE;
        freeGroupCount = 0;
    }

    private void validateCapacityRequest(int requiredRows, int additionalGroups) {
        if (requiredRows < 0 || additionalGroups < 0) {
            throw new IllegalArgumentException("exact-index capacity must be non-negative");
        }
    }

    private int targetRowCapacity(int requiredRows) {
        return grownCapacity(rowGroups.length, requiredRows, false);
    }

    private int targetGroupCapacity(int additionalGroups) {
        int unavailableAdditional = Math.max(0, additionalGroups - freeGroupCount);
        long requiredGroupSlots = (long) nextGroupSlot + (long) unavailableAdditional;
        if (requiredGroupSlots > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("exact-index group capacity exhausted");
        }
        return grownCapacity(
                groupHashes.length, (int) requiredGroupSlots, true);
    }

    private int targetBucketCapacity(int additionalGroups) {
        long maximumGroups = (long) groupCount + (long) additionalGroups;
        if (maximumGroups > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("exact-index bucket capacity exhausted");
        }
        return Math.max(bucketHeads.length, bucketCapacityFor((int) maximumGroups));
    }

    private void releaseGroup(int group) {
        long hash = groupHashes[group];
        int bucket = bucket(hash, bucketHeads.length);
        int current = bucketHeads[bucket];
        int previous = NONE;
        while (current != group) {
            if (current == NONE) {
                throw new IllegalStateException("exact-index group bucket is corrupt");
            }
            previous = current;
            current = nextHashGroups[current];
        }
        int following = nextHashGroups[group];
        if (previous == NONE) bucketHeads[bucket] = following;
        else nextHashGroups[previous] = following;
        groupStates[group] = FREE;
        groupHeadRows[group] = NONE;
        groupSizes[group] = 0;
        nextHashGroups[group] = freeGroupHead;
        freeGroupHead = group;
        freeGroupCount++;
        groupCount--;
    }

    private void clearRowLink(int row) {
        rowGroups[row] = NONE;
        rowPrevious[row] = NONE;
        rowNext[row] = NONE;
    }

    private void requireLinkedRow(int row) {
        requireRowCapacity(row);
        if (rowGroups[row] == NONE) {
            throw new IllegalArgumentException("row is not linked");
        }
    }

    private void requireRowCapacity(int row) {
        if (row < 0 || row >= rowGroups.length) {
            throw new IllegalArgumentException("row is outside exact-index capacity");
        }
    }

    private void requireLiveGroup(int group) {
        if (group < 0 || group >= nextGroupSlot || groupStates[group] != LIVE) {
            throw new IllegalArgumentException("group is not live");
        }
    }

    private void updateHighWater() {
        long current = retainedBytes();
        if (current > storageHighWaterBytes) storageHighWaterBytes = current;
    }

    private static long retainedBytes(int rows, int groups, int buckets) {
        long groupBytes = 21L * (long) groups;
        long rowBytes = 12L * (long) rows;
        long bucketBytes = 4L * (long) buckets;
        if (Long.MAX_VALUE - groupBytes < rowBytes
                || Long.MAX_VALUE - groupBytes - rowBytes < bucketBytes) {
            return Long.MAX_VALUE;
        }
        return groupBytes + rowBytes + bucketBytes;
    }

    private static int grownCapacity(int current, int required, boolean minimumFour) {
        int target = current;
        if (minimumFour && target < 4) target = 4;
        if (required <= target) return target;
        if (target == 0) target = minimumFour ? 4 : 1;
        while (target < required) {
            int grown = target + (target >>> 1) + 1;
            if (grown <= target || grown > Integer.MAX_VALUE - 8) {
                target = Integer.MAX_VALUE - 8;
                break;
            }
            target = grown;
        }
        if (target < required) {
            throw new IllegalArgumentException("exact-index array capacity exhausted");
        }
        return target;
    }

    private static int bucketCapacityFor(int groups) {
        if (groups < 0) throw new IllegalArgumentException("groups must be non-negative");
        long required = Math.max(4L, 2L * (long) groups + 1L);
        int capacity = 4;
        while ((long) capacity < required) {
            if (capacity >= MAX_CAPACITY) {
                throw new IllegalArgumentException("exact-index bucket capacity exhausted");
            }
            capacity <<= 1;
        }
        return capacity;
    }

    private static int bucket(long hash, int capacity) {
        if (capacity == 0) throw new IllegalStateException("exact-index is released");
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

    private static long saturatedAdd(long current, long delta) {
        return delta < 0L || Long.MAX_VALUE - current < delta
                ? Long.MAX_VALUE : current + delta;
    }

}
