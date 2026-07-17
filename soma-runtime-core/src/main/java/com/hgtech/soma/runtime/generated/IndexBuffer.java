package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/** Table-local reusable primitive Index scratch。 */
public final class IndexBuffer {
    private int[] values = new int[0];
    private int length;

    public int length() {
        return length;
    }

    public int capacity() {
        return values.length;
    }

    public int get(int position) {
        if (position < 0 || position >= length) {
            throw new IndexOutOfBoundsException("index buffer position out of range");
        }
        return values[position];
    }

    public void set(int position, int value) {
        if (position < 0 || position >= length) {
            throw new IndexOutOfBoundsException("index buffer position out of range");
        }
        values[position] = value;
    }

    public void add(int value) {
        ensureCapacity(length + 1);
        values[length++] = value;
    }

    /** Resets logical content without clearing retained primitive storage。 */
    public void reset() {
        length = 0;
    }

    /** Sets a validated logical length and returns the retained generated-protocol array。 */
    public int[] prepare(int requiredLength) {
        if (requiredLength < 0) {
            throw new IllegalArgumentException("requiredLength must be non-negative");
        }
        ensureCapacity(requiredLength);
        length = requiredLength;
        return values;
    }

    /** Returns the generated-protocol backing array; callers must honor {@link #length()}。 */
    public int[] array() {
        return values;
    }

    public long retainedBytes() {
        return 4L * (long) values.length;
    }

    public long retainedBytesAfterEnsure(int requiredLength) {
        if (requiredLength < 0) {
            throw new IllegalArgumentException("requiredLength must be non-negative");
        }
        return 4L * (long) targetCapacity(values.length, requiredLength);
    }

    public void ensureCapacity(int requiredLength) {
        if (requiredLength < 0) {
            throw new IllegalArgumentException("requiredLength must be non-negative");
        }
        int target = targetCapacity(values.length, requiredLength);
        if (target != values.length) {
            values = Arrays.copyOf(values, target);
        }
    }

    public void release() {
        values = new int[0];
        length = 0;
    }

    private static int targetCapacity(int current, int required) {
        if (required <= current) return current;
        int target = current == 0 ? 4 : current;
        while (target < required) {
            int grown = target + (target >>> 1) + 1;
            if (grown <= target || grown > Integer.MAX_VALUE - 8) {
                target = Integer.MAX_VALUE - 8;
                break;
            }
            target = grown;
        }
        if (target < required) {
            throw new IllegalArgumentException("index buffer capacity exhausted");
        }
        return target;
    }
}
