package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

public final class PresenceBitmap extends GeneratedColumn {
    private long[] headWords = new long[0];
    private long[][] tailWords = new long[0][];
    private int capacity;
    private int presentCount;

    public PresenceBitmap() {
    }

    public boolean isPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        return (wordAt(rowIndex >>> 6)
                & (1L << (rowIndex & 63))) != 0L;
    }

    public void setPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        int wordIndex = rowIndex >>> 6;
        long mask = 1L << (rowIndex & 63);
        long before = wordAt(wordIndex);
        if ((before & mask) == 0L) {
            setWord(wordIndex, before | mask);
            presentCount++;
        }
    }

    public void clearPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        int wordIndex = rowIndex >>> 6;
        long mask = 1L << (rowIndex & 63);
        long before = wordAt(wordIndex);
        if ((before & mask) != 0L) {
            setWord(wordIndex, before & ~mask);
            presentCount--;
        }
    }

    public void copyFrom(
            PresenceBitmap source,
            int sourceIndex,
            int targetIndex,
            int length) {
        if (source == null) throw new NullPointerException("source");
        requireCopyRange(
                sourceIndex, targetIndex, length,
                source.capacity, capacity);
        if (length == 0
                || source == this && sourceIndex == targetIndex) {
            return;
        }
        if (source == this && targetIndex > sourceIndex
                && targetIndex < sourceIndex + length) {
            copyBackward(source, sourceIndex, targetIndex, length);
        } else {
            copyForward(source, sourceIndex, targetIndex, length);
        }
    }

    public int presentCount() {
        return presentCount;
    }

    /** 返回指定 packed word，供 optional-column word kernel 使用。 */
    public long wordAt(int wordIndex) {
        int wordCount = wordCount(capacity);
        if (wordIndex < 0 || wordIndex >= wordCount) {
            throw new IndexOutOfBoundsException(
                    "invalid presence word index");
        }
        int headWordCount = headWords.length;
        if (!segmented() || wordIndex < headWordCount) {
            return headWords[wordIndex];
        }
        int tailWordIndex = wordIndex - headWordCount;
        int wordsPerTail = segmentRows() >>> 6;
        return tailWords[tailWordIndex / wordsPerTail]
                [tailWordIndex % wordsPerTail];
    }

    @Override public Object stageCapacity(int newCapacity) {
        requireGrowth(capacity, newCapacity);
        int newHeadWords = wordCount(headCapacityFor(newCapacity));
        long[] stagedHead = newHeadWords == headWords.length
                ? headWords : Arrays.copyOf(headWords, newHeadWords);
        int newTailCount = tailCountFor(newCapacity);
        long[][] stagedTails = tailWords;
        if (newTailCount != tailWords.length) {
            stagedTails = Arrays.copyOf(tailWords, newTailCount);
            int wordsPerTail = segmentRows() >>> 6;
            for (int ordinal = tailWords.length;
                 ordinal < newTailCount;
                 ordinal++) {
                stagedTails[ordinal] = new long[wordsPerTail];
            }
        }
        return new Stage(stagedHead, stagedTails, newCapacity);
    }

    @Override public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof Stage)) {
            throw new IllegalStateException(
                    "staged capacity type mismatch");
        }
        Stage staged = (Stage) stagedCapacity;
        requireGrowth(capacity, staged.capacity);
        headWords = staged.headWords;
        tailWords = staged.tailWords;
        capacity = staged.capacity;
    }

    @Override public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, capacity);
        int position = fromInclusive;
        while (position < toExclusive) {
            int offset = position & 63;
            int count = Math.min(
                    toExclusive - position, 64 - offset);
            writeWordBits(position >>> 6, offset, count, 0L);
            position += count;
        }
    }

    @Override long estimatedBytes(int requestedCapacity) {
        requireCapacity(requestedCapacity);
        long bytes = 8L
                * (long) wordCount(headCapacityFor(requestedCapacity));
        int tails = tailCountFor(requestedCapacity);
        bytes = ColumnStorageSupport.checkedAdd(
                bytes, 8L * (long) tails);
        return ColumnStorageSupport.checkedAdd(
                bytes,
                8L * (long) tails
                        * (long) (segmentRows() >>> 6));
    }

    @Override long stagingAllocationBytes(int newCapacity) {
        requireGrowth(capacity, newCapacity);
        long bytes = wordCount(headCapacityFor(newCapacity))
                > headWords.length
                ? 8L * (long) wordCount(headCapacityFor(newCapacity))
                : 0L;
        int newTails = tailCountFor(newCapacity);
        if (newTails > tailWords.length) {
            bytes = ColumnStorageSupport.checkedAdd(
                    bytes, 8L * (long) newTails);
            bytes = ColumnStorageSupport.checkedAdd(
                    bytes,
                    8L * (long) (newTails - tailWords.length)
                            * (long) (segmentRows() >>> 6));
        }
        return bytes;
    }

    @Override long replacementTransientBytes(int newCapacity) {
        requireGrowth(capacity, newCapacity);
        long bytes = wordCount(headCapacityFor(newCapacity))
                > headWords.length
                ? 8L * (long) headWords.length : 0L;
        if (tailCountFor(newCapacity) > tailWords.length) {
            bytes = ColumnStorageSupport.checkedAdd(
                    bytes, 8L * (long) tailWords.length);
        }
        return bytes;
    }

    @Override long retainedBytes() {
        return estimatedBytes(capacity);
    }

    @Override void releaseStorage() {
        headWords = new long[0];
        tailWords = new long[0][];
        capacity = 0;
        presentCount = 0;
    }

    private void copyForward(
            PresenceBitmap source,
            int sourceIndex,
            int targetIndex,
            int length) {
        int copied = 0;
        while (copied < length) {
            int sourcePosition = sourceIndex + copied;
            int targetPosition = targetIndex + copied;
            int sourceOffset = sourcePosition & 63;
            int targetOffset = targetPosition & 63;
            int count = Math.min(
                    length - copied,
                    Math.min(64 - sourceOffset, 64 - targetOffset));
            long bits = source.readWordBits(
                    sourcePosition >>> 6, sourceOffset, count);
            writeWordBits(
                    targetPosition >>> 6, targetOffset, count, bits);
            copied += count;
        }
    }

    private void copyBackward(
            PresenceBitmap source,
            int sourceIndex,
            int targetIndex,
            int length) {
        int remaining = length;
        while (remaining > 0) {
            int sourceEnd = sourceIndex + remaining;
            int targetEnd = targetIndex + remaining;
            int sourceAvailable = sourceEnd & 63;
            int targetAvailable = targetEnd & 63;
            if (sourceAvailable == 0) sourceAvailable = 64;
            if (targetAvailable == 0) targetAvailable = 64;
            int count = Math.min(
                    remaining,
                    Math.min(sourceAvailable, targetAvailable));
            int sourcePosition = sourceEnd - count;
            int targetPosition = targetEnd - count;
            long bits = source.readWordBits(
                    sourcePosition >>> 6,
                    sourcePosition & 63,
                    count);
            writeWordBits(
                    targetPosition >>> 6,
                    targetPosition & 63,
                    count,
                    bits);
            remaining -= count;
        }
    }

    private long readWordBits(int wordIndex, int offset, int count) {
        return (wordAt(wordIndex) >>> offset) & lowMask(count);
    }

    private void writeWordBits(
            int wordIndex, int offset, int count, long bits) {
        long mask = lowMask(count) << offset;
        long before = wordAt(wordIndex);
        long after = (before & ~mask) | ((bits << offset) & mask);
        setWord(wordIndex, after);
        presentCount += Long.bitCount(after) - Long.bitCount(before);
    }

    private void setWord(int wordIndex, long value) {
        int headWordCount = headWords.length;
        if (!segmented() || wordIndex < headWordCount) {
            headWords[wordIndex] = value;
            return;
        }
        int tailWordIndex = wordIndex - headWordCount;
        int wordsPerTail = segmentRows() >>> 6;
        tailWords[tailWordIndex / wordsPerTail]
                [tailWordIndex % wordsPerTail] = value;
    }

    private void requireBitIndex(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= capacity) {
            throw new IndexOutOfBoundsException("invalid row index");
        }
    }

    private static int wordCount(int bitCapacity) {
        return (int) (((long) bitCapacity + 63L) >>> 6);
    }

    private static long lowMask(int count) {
        return count == 64 ? -1L : (1L << count) - 1L;
    }

    private static final class Stage {
        private final long[] headWords;
        private final long[][] tailWords;
        private final int capacity;

        private Stage(
                long[] headWords,
                long[][] tailWords,
                int capacity) {
            this.headWords = headWords;
            this.tailWords = tailWords;
            this.capacity = capacity;
        }
    }
}
