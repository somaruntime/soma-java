package com.hgtech.soma.runtime.generated;

/**
 * 以 stage-all-then-commit 方式协调同一 table 的 column capacity。
 */
public final class ColumnGroup {

    private final GeneratedColumn[] columns;
    private int capacity;

    public ColumnGroup(int initialCapacity, GeneratedColumn... columns) {
        GeneratedColumn.requireCapacity(initialCapacity);
        if (columns == null) {
            throw new NullPointerException("columns");
        }
        this.columns = columns.clone();
        validateColumns(this.columns);
        stageAndCommit(initialCapacity);
        this.capacity = initialCapacity;
    }

    public int capacity() {
        return capacity;
    }

    public boolean ensureCapacity(int required, int growthNumerator, int growthDenominator) {
        GeneratedColumn.requireCapacity(required);
        if (growthDenominator < 1 || growthNumerator <= growthDenominator) {
            throw new IllegalArgumentException("growth ratio must satisfy numerator > denominator >= 1");
        }
        if (required <= capacity) {
            return false;
        }

        long grown = ((long) capacity * (long) growthNumerator
                + (long) growthDenominator - 1L) / (long) growthDenominator;
        long target = Math.max((long) required, grown);
        if (target > Integer.MAX_VALUE) {
            target = Integer.MAX_VALUE;
        }

        int newCapacity = (int) target;
        stageAndCommit(newCapacity);
        capacity = newCapacity;
        return true;
    }

    private static void validateColumns(GeneratedColumn[] columns) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] == null) {
                throw new NullPointerException("columns[" + i + "]");
            }
            for (int j = 0; j < i; j++) {
                if (columns[i] == columns[j]) {
                    throw new IllegalArgumentException("duplicate column");
                }
            }
        }
    }

    private void stageAndCommit(int newCapacity) {
        Object[] staged = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            staged[i] = columns[i].stageCapacity(newCapacity);
        }
        for (int i = 0; i < columns.length; i++) {
            columns[i].commitCapacity(staged[i]);
        }
    }
}
