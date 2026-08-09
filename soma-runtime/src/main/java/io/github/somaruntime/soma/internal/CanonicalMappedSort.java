package io.github.somaruntime.soma.internal;

import java.util.Comparator;

/** Canonical stable comparison schedule for an opaque mapped Comparator. */
final class CanonicalMappedSort {

    private CanonicalMappedSort() {
    }

    static void sort(
            BoundRowPlan bound,
            Object[] values,
            int size,
            Comparator<Object> comparator) {
        if (size < 2) return;
        Object[] scratch = new Object[size];
        mergeSort(bound, values, scratch, 0, size, comparator);
    }

    private static void mergeSort(
            BoundRowPlan bound,
            Object[] values,
            Object[] scratch,
            int from,
            int to,
            Comparator<Object> comparator) {
        int length = to - from;
        if (length < 2) return;
        int middle = from + length / 2;
        mergeSort(bound, values, scratch, from, middle, comparator);
        mergeSort(bound, values, scratch, middle, to, comparator);
        int left = from;
        int right = middle;
        int output = from;
        while (left < middle && right < to) {
            if (MappedQueryOperation.callbackCompare(
                    bound, comparator, values[left], values[right]) <= 0) {
                scratch[output++] = values[left++];
            } else {
                scratch[output++] = values[right++];
            }
        }
        while (left < middle) scratch[output++] = values[left++];
        while (right < to) scratch[output++] = values[right++];
        System.arraycopy(scratch, from, values, from, length);
    }
}
