package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class PresenceBitmap extends GeneratedColumn {

    private long[] words = new long[0];
    private int presentCount;

    public PresenceBitmap() {
    }

    public boolean isPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        return (words[rowIndex >>> 6] & (1L << (rowIndex & 63))) != 0L;
    }

    public void setPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        int wordIndex = rowIndex >>> 6;
        long mask = 1L << (rowIndex & 63);
        long before = words[wordIndex];
        if ((before & mask) == 0L) {
            words[wordIndex] = before | mask;
            presentCount++;
        }
    }

    public void clearPresent(int rowIndex) {
        requireBitIndex(rowIndex);
        int wordIndex = rowIndex >>> 6;
        long mask = 1L << (rowIndex & 63);
        long before = words[wordIndex];
        if ((before & mask) != 0L) {
            words[wordIndex] = before & ~mask;
            presentCount--;
        }
    }

    public void copyFrom(PresenceBitmap source, int sourceIndex, int targetIndex, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        requireCopyRange(sourceIndex, targetIndex, length,
                source.bitCapacity(), bitCapacity());
        if (length == 0 || source == this && sourceIndex == targetIndex) {
            return;
        }
        if (source == this && targetIndex > sourceIndex && targetIndex < sourceIndex + length) {
            copyBackward(source, sourceIndex, targetIndex, length);
        } else {
            copyForward(source, sourceIndex, targetIndex, length);
        }
    }

    public int presentCount() {
        return presentCount;
    }

    /**
     * 返回指定 packed word，供 runtime 的 optional-column word kernel 使用。
     */
    public long wordAt(int wordIndex) {
        if (wordIndex < 0 || wordIndex >= words.length) {
            throw new IndexOutOfBoundsException("invalid presence word index");
        }
        return words[wordIndex];
    }

    @Override
    public Object stageCapacity(int newCapacity) {
        requireCapacity(newCapacity);
        int newWordCount = (int) (((long) newCapacity + 63L) >>> 6);
        if (newWordCount < words.length) {
            throw new IllegalArgumentException("capacity shrink is not supported");
        }
        return Arrays.copyOf(words, newWordCount);
    }

    @Override
    public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof long[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        long[] staged = (long[]) stagedCapacity;
        if (staged.length < words.length) {
            throw new IllegalStateException("capacity shrink is not supported");
        }
        words = staged;
        presentCount = countPresent(staged);
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, bitCapacity());
        int position = fromInclusive;
        while (position < toExclusive) {
            int wordIndex = position >>> 6;
            int offset = position & 63;
            int count = Math.min(toExclusive - position, 64 - offset);
            writeWordBits(wordIndex, offset, count, 0L);
            position += count;
        }
    }

    private void copyForward(PresenceBitmap source, int sourceIndex,
                             int targetIndex, int length) {
        int copied = 0;
        while (copied < length) {
            int sourcePosition = sourceIndex + copied;
            int targetPosition = targetIndex + copied;
            int sourceOffset = sourcePosition & 63;
            int targetOffset = targetPosition & 63;
            int count = Math.min(length - copied,
                    Math.min(64 - sourceOffset, 64 - targetOffset));
            long bits = source.readWordBits(sourcePosition >>> 6, sourceOffset, count);
            writeWordBits(targetPosition >>> 6, targetOffset, count, bits);
            copied += count;
        }
    }

    private void copyBackward(PresenceBitmap source, int sourceIndex,
                              int targetIndex, int length) {
        int remaining = length;
        while (remaining > 0) {
            int sourceEnd = sourceIndex + remaining;
            int targetEnd = targetIndex + remaining;
            int sourceAvailable = sourceEnd & 63;
            int targetAvailable = targetEnd & 63;
            if (sourceAvailable == 0) {
                sourceAvailable = 64;
            }
            if (targetAvailable == 0) {
                targetAvailable = 64;
            }
            int count = Math.min(remaining, Math.min(sourceAvailable, targetAvailable));
            int sourcePosition = sourceEnd - count;
            int targetPosition = targetEnd - count;
            long bits = source.readWordBits(sourcePosition >>> 6,
                    sourcePosition & 63, count);
            writeWordBits(targetPosition >>> 6, targetPosition & 63, count, bits);
            remaining -= count;
        }
    }

    private long readWordBits(int wordIndex, int offset, int count) {
        return (words[wordIndex] >>> offset) & lowMask(count);
    }

    private void writeWordBits(int wordIndex, int offset, int count, long bits) {
        long mask = lowMask(count) << offset;
        long before = words[wordIndex];
        long after = (before & ~mask) | ((bits << offset) & mask);
        words[wordIndex] = after;
        presentCount += Long.bitCount(after) - Long.bitCount(before);
    }

    private void requireBitIndex(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= bitCapacity()) {
            throw new IndexOutOfBoundsException("invalid row index");
        }
    }

    private int bitCapacity() {
        long capacity = (long) words.length << 6;
        return capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private static long lowMask(int count) {
        return count == 64 ? -1L : (1L << count) - 1L;
    }

    private static int countPresent(long[] values) {
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            count += Long.bitCount(values[i]);
        }
        return count;
    }
}
