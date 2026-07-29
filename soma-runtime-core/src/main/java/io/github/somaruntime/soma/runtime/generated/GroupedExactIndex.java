package io.github.somaruntime.soma.runtime.generated;

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
    private static final int[] EMPTY_INTS = new int[0];
    private static final long[] EMPTY_LONGS = new long[0];
    private static final byte[] EMPTY_BYTES = new byte[0];

    private int[] bucketHeads;
    private long[] groupHashes;
    private int[] nextHashGroups;
    private int[] groupHeadRows;
    private int[] groupSizes;
    private byte[] groupStates;
    private int[] rowGroups;
    private int[] rowPrevious;
    private int[] rowNext;
    private long[] groupBitmaps;
    private int bitmapWordsPerGroup;
    private final boolean bitmapEligible;
    private boolean bitmapLayout;

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
        this(expectedRows, expectedRows, false);
    }

    /**
     * 分别建立row-link storage与预期exact-group容量，避免把table capacity解释为一行一group。
     */
    public GroupedExactIndex(int expectedRows, int expectedGroups) {
        this(expectedRows, expectedGroups, false);
    }

    /**
     * 为符合公式约束的单字段primitive index启用内部link/bitmap物理选择。
     *
     * <p>eligibility只表示该selector允许参与选择；实际布局仍由row/group capacity
     * 与retained-byte公式确定，不是公开cardinality契约。</p>
     */
    public GroupedExactIndex(
            int expectedRows, int expectedGroups, boolean bitmapEligible) {
        if (expectedRows < 0 || expectedGroups < 0 || expectedGroups > expectedRows) {
            throw new IllegalArgumentException("invalid expected exact-index capacity");
        }
        this.bitmapEligible = bitmapEligible;
        int rowCapacity = expectedRows;
        int groupCapacity = expectedGroups == 0 ? 4 : expectedGroups;
        int bucketCapacity = bucketCapacityFor(expectedGroups);
        bitmapLayout = bitmapPreferred(
                bitmapEligible, rowCapacity, groupCapacity);
        bucketHeads = new int[bucketCapacity];
        groupHashes = new long[groupCapacity];
        nextHashGroups = new int[groupCapacity];
        groupHeadRows = new int[groupCapacity];
        groupSizes = new int[groupCapacity];
        groupStates = new byte[groupCapacity];
        rowGroups = new int[rowCapacity];
        if (bitmapLayout) {
            bitmapWordsPerGroup = wordsForRows(rowCapacity);
            groupBitmaps = new long[bitmapLongCount(
                    rowCapacity, groupCapacity)];
            rowPrevious = EMPTY_INTS;
            rowNext = EMPTY_INTS;
        } else {
            groupBitmaps = EMPTY_LONGS;
            rowPrevious = new int[rowCapacity];
            rowNext = new int[rowCapacity];
        }
        Arrays.fill(bucketHeads, NONE);
        Arrays.fill(nextHashGroups, NONE);
        Arrays.fill(groupHeadRows, NONE);
        Arrays.fill(rowGroups, NONE);
        if (!bitmapLayout) {
            Arrays.fill(rowPrevious, NONE);
            Arrays.fill(rowNext, NONE);
        }
        storageHighWaterBytes = retainedBytes();
    }

    public static long estimatedRetainedBytes(int rows, int groups) {
        return estimatedRetainedBytes(rows, groups, false);
    }

    public static long estimatedRetainedBytes(
            int rows, int groups, boolean bitmapEligible) {
        if (rows < 0 || groups < 0 || groups > rows) {
            throw new IllegalArgumentException("invalid exact-index estimate");
        }
        int groupCapacity = groups == 0 ? 4 : groups;
        return retainedBytes(
                rows,
                groupCapacity,
                bucketCapacityFor(groups),
                bitmapPreferred(bitmapEligible, rows, groupCapacity));
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
        return retainedBytes(
                rowGroups.length,
                groupHashes.length,
                bucketHeads.length,
                bitmapLayout);
    }

    public long storageHighWaterBytes() {
        return storageHighWaterBytes;
    }

    public long retainedBytesAfterEnsure(int requiredRows, int additionalGroups) {
        validateCapacityRequest(requiredRows, additionalGroups);
        int rows = targetRowCapacity(requiredRows);
        int groups = targetGroupCapacity(additionalGroups);
        return retainedBytes(
                rows,
                groups,
                targetBucketCapacity(additionalGroups),
                bitmapPreferred(bitmapEligible, rows, groups));
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
        boolean targetBitmap = bitmapPreferred(
                bitmapEligible, targetRows, targetGroups);
        boolean changeLayout = targetBitmap != bitmapLayout;
        if (!growRows && !growGroups && !growBuckets && !changeLayout) return;

        int[] newRowGroups = growRows
                ? Arrays.copyOf(rowGroups, targetRows) : rowGroups;
        if (growRows) {
            Arrays.fill(newRowGroups, rowGroups.length, targetRows, NONE);
        }

        long[] newGroupHashes = growGroups
                ? Arrays.copyOf(groupHashes, targetGroups) : groupHashes;
        int[] newGroupHeads = growGroups || targetBitmap || bitmapLayout
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

        int[] newRowPrevious;
        int[] newRowNext;
        long[] newGroupBitmaps;
        int newBitmapWords;
        if (targetBitmap) {
            newBitmapWords = wordsForRows(targetRows);
            newGroupBitmaps = new long[bitmapLongCount(
                    targetRows, targetGroups)];
            Arrays.fill(newGroupHeads, NONE);
            for (int row = 0; row < newRowGroups.length; row++) {
                int group = newRowGroups[row];
                if (group == NONE) continue;
                setBitmap(
                        newGroupBitmaps,
                        newBitmapWords,
                        group,
                        row);
                if (newGroupHeads[group] == NONE) {
                    newGroupHeads[group] = row;
                }
            }
            newRowPrevious = EMPTY_INTS;
            newRowNext = EMPTY_INTS;
        } else {
            newBitmapWords = 0;
            newGroupBitmaps = EMPTY_LONGS;
            if (!bitmapLayout) {
                newRowPrevious = growRows
                        ? Arrays.copyOf(rowPrevious, targetRows)
                        : rowPrevious;
                newRowNext = growRows
                        ? Arrays.copyOf(rowNext, targetRows)
                        : rowNext;
                if (growRows) {
                    Arrays.fill(
                            newRowPrevious,
                            rowPrevious.length,
                            targetRows,
                            NONE);
                    Arrays.fill(
                            newRowNext,
                            rowNext.length,
                            targetRows,
                            NONE);
                }
            } else {
                newRowPrevious = new int[targetRows];
                newRowNext = new int[targetRows];
                Arrays.fill(newRowPrevious, NONE);
                Arrays.fill(newRowNext, NONE);
                Arrays.fill(newGroupHeads, NONE);
                for (int row = targetRows - 1; row >= 0; row--) {
                    int group = newRowGroups[row];
                    if (group == NONE) continue;
                    int previousHead = newGroupHeads[group];
                    newRowNext[row] = previousHead;
                    if (previousHead != NONE) {
                        newRowPrevious[previousHead] = row;
                    }
                    newGroupHeads[group] = row;
                }
            }
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
        groupBitmaps = newGroupBitmaps;
        bitmapWordsPerGroup = newBitmapWords;
        bitmapLayout = targetBitmap;
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
        if (bitmapLayout) {
            return nextBitmapRow(rowGroups[row], row + 1);
        }
        return rowNext[row];
    }

    public boolean bitmapLayout() {
        return bitmapLayout;
    }

    public int bitmapWordCount() {
        return bitmapLayout ? bitmapWordsPerGroup : 0;
    }

    public long bitmapWord(int group, int word) {
        requireLiveGroup(group);
        if (!bitmapLayout) {
            throw new IllegalStateException("exact-index is not bitmap-backed");
        }
        if (word < 0 || word >= bitmapWordsPerGroup) {
            throw new IllegalArgumentException("bitmap word is outside exact-index capacity");
        }
        return groupBitmaps[group * bitmapWordsPerGroup + word];
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
        if (bitmapLayout) {
            setBitmap(groupBitmaps, bitmapWordsPerGroup, group, row);
            if (previousHead == NONE || row < previousHead) {
                groupHeadRows[group] = row;
            }
        } else {
            rowPrevious[row] = NONE;
            rowNext[row] = previousHead;
            if (previousHead != NONE) rowPrevious[previousHead] = row;
            groupHeadRows[group] = row;
        }
        groupSizes[group]++;
        entryCount++;
    }

    public void unlink(int row) {
        requireLinkedRow(row);
        int group = rowGroups[row];
        if (bitmapLayout) {
            clearBitmap(groupBitmaps, bitmapWordsPerGroup, group, row);
            if (groupHeadRows[group] == row) {
                groupHeadRows[group] = nextBitmapRow(group, row + 1);
            }
        } else {
            int previous = rowPrevious[row];
            int next = rowNext[row];
            if (previous == NONE) groupHeadRows[group] = next;
            else rowNext[previous] = next;
            if (next != NONE) rowPrevious[next] = previous;
        }
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
        if (bitmapLayout) {
            clearBitmap(groupBitmaps, bitmapWordsPerGroup, group, from);
            setBitmap(groupBitmaps, bitmapWordsPerGroup, group, to);
            rowGroups[to] = group;
            if (groupHeadRows[group] == from || to < groupHeadRows[group]) {
                groupHeadRows[group] = nextBitmapRow(group, 0);
            }
        } else {
            int previous = rowPrevious[from];
            int next = rowNext[from];
            rowGroups[to] = group;
            rowPrevious[to] = previous;
            rowNext[to] = next;
            if (previous == NONE) groupHeadRows[group] = to;
            else rowNext[previous] = to;
            if (next != NONE) rowPrevious[next] = to;
        }
        clearRowLink(from);
    }

    public void clear() {
        Arrays.fill(bucketHeads, NONE);
        Arrays.fill(groupStates, FREE);
        Arrays.fill(nextHashGroups, NONE);
        Arrays.fill(groupHeadRows, NONE);
        Arrays.fill(groupSizes, 0);
        Arrays.fill(rowGroups, NONE);
        if (bitmapLayout) {
            Arrays.fill(groupBitmaps, 0L);
        } else {
            Arrays.fill(rowPrevious, NONE);
            Arrays.fill(rowNext, NONE);
        }
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
        bucketHeads = EMPTY_INTS;
        groupHashes = EMPTY_LONGS;
        nextHashGroups = EMPTY_INTS;
        groupHeadRows = EMPTY_INTS;
        groupSizes = EMPTY_INTS;
        groupStates = EMPTY_BYTES;
        rowGroups = EMPTY_INTS;
        rowPrevious = EMPTY_INTS;
        rowNext = EMPTY_INTS;
        groupBitmaps = EMPTY_LONGS;
        bitmapWordsPerGroup = 0;
        bitmapLayout = false;
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
        if (nextGroupSlot == 0 && requiredGroupSlots > groupHashes.length) {
            return Math.max(4, (int) requiredGroupSlots);
        }
        return grownCapacity(groupHashes.length, (int) requiredGroupSlots, true);
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
        if (!bitmapLayout) {
            rowPrevious[row] = NONE;
            rowNext[row] = NONE;
        }
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

    private static long retainedBytes(
            int rows, int groups, int buckets, boolean bitmap) {
        long groupBytes = 21L * (long) groups;
        long rowBytes = 4L * (long) rows;
        if (bitmap) {
            long bitmapBytes = bitmapBytes(rows, groups);
            if (bitmapBytes == Long.MAX_VALUE
                    || Long.MAX_VALUE - rowBytes < bitmapBytes) {
                return Long.MAX_VALUE;
            }
            rowBytes += bitmapBytes;
        } else {
            rowBytes += 8L * (long) rows;
        }
        long bucketBytes = 4L * (long) buckets;
        if (Long.MAX_VALUE - groupBytes < rowBytes
                || Long.MAX_VALUE - groupBytes - rowBytes < bucketBytes) {
            return Long.MAX_VALUE;
        }
        return groupBytes + rowBytes + bucketBytes;
    }

    private int nextBitmapRow(int group, int fromInclusive) {
        if (fromInclusive < 0) fromInclusive = 0;
        if (fromInclusive >= rowGroups.length) return NONE;
        int word = fromInclusive >>> 6;
        long bits = groupBitmaps[group * bitmapWordsPerGroup + word]
                & (-1L << (fromInclusive & 63));
        while (true) {
            if (bits != 0L) {
                int row = (word << 6) + Long.numberOfTrailingZeros(bits);
                return row < rowGroups.length ? row : NONE;
            }
            word++;
            if (word >= bitmapWordsPerGroup) return NONE;
            bits = groupBitmaps[group * bitmapWordsPerGroup + word];
        }
    }

    private static boolean bitmapPreferred(
            boolean eligible, int rows, int groups) {
        if (!eligible || rows <= 0 || groups <= 0) return false;
        long words = (long) wordsForRows(rows);
        long bitmapLongs = words * (long) groups;
        if (bitmapLongs > Integer.MAX_VALUE - 8L) return false;
        long bitmapBytes = bitmapLongs * 8L;
        long linkMembershipBytes = 8L * (long) rows;
        long tailAllowance = 8L * (long) groups;
        return bitmapBytes <= linkMembershipBytes + tailAllowance;
    }

    private static long bitmapBytes(int rows, int groups) {
        long count = (long) wordsForRows(rows) * (long) groups;
        return count > Long.MAX_VALUE / 8L
                ? Long.MAX_VALUE : count * 8L;
    }

    private static int bitmapLongCount(int rows, int groups) {
        long count = (long) wordsForRows(rows) * (long) groups;
        if (count > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("exact-index bitmap capacity exhausted");
        }
        return (int) count;
    }

    private static int wordsForRows(int rows) {
        return (int) (((long) rows + 63L) >>> 6);
    }

    private static void setBitmap(
            long[] bitmap, int words, int group, int row) {
        int offset = group * words + (row >>> 6);
        bitmap[offset] |= 1L << (row & 63);
    }

    private static void clearBitmap(
            long[] bitmap, int words, int group, int row) {
        int offset = group * words + (row >>> 6);
        bitmap[offset] &= ~(1L << (row & 63));
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
