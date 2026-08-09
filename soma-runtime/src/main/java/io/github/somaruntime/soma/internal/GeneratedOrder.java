package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOrder;
import java.util.Arrays;

/** Immutable, data-only generated logical row order. */
public final class GeneratedOrder<R> implements SomaOrder<R> {

    private final GeneratedTable owner;
    private final int[] fieldIndexes;
    private final boolean[] descending;

    GeneratedOrder(GeneratedTable owner, int fieldIndex, boolean isDescending) {
        this(owner, new int[] {fieldIndex}, new boolean[] {isDescending});
    }

    private GeneratedOrder(
            GeneratedTable owner,
            int[] fieldIndexes,
            boolean[] descending) {
        this.owner = owner;
        this.fieldIndexes = fieldIndexes;
        this.descending = descending;
    }

    @Override
    public SomaOrder<R> then(SomaOrder<R> next) {
        GeneratedOrder<?> right = require(next);
        if (right.owner != owner) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    "order components belong to different Tables");
        }
        int size = fieldIndexes.length + right.fieldIndexes.length;
        int[] fields = Arrays.copyOf(fieldIndexes, size);
        boolean[] directions = Arrays.copyOf(descending, size);
        System.arraycopy(
                right.fieldIndexes, 0, fields, fieldIndexes.length, right.fieldIndexes.length);
        System.arraycopy(
                right.descending, 0, directions, descending.length, right.descending.length);
        return new GeneratedOrder<R>(owner, fields, directions);
    }

    GeneratedTable owner() {
        return owner;
    }

    int size() {
        return fieldIndexes.length;
    }

    int fieldIndex(int ordinal) {
        return fieldIndexes[ordinal];
    }

    boolean descending(int ordinal) {
        return descending[ordinal];
    }

    static GeneratedOrder<?> require(SomaOrder<?> order) {
        if (!(order instanceof GeneratedOrder)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    "order is not issued by SOMA");
        }
        return (GeneratedOrder<?>) order;
    }
}
