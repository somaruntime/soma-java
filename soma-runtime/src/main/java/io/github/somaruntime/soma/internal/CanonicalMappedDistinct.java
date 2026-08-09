package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.Objects;

/**
 * Canonical caller-thread hash/equality schedule for arbitrary mapped values.
 * This callback semantic kernel is shared; plan traversal remains independent.
 */
final class CanonicalMappedDistinct {

    private final Object[] values;
    private final byte[] occupied;
    private final int mask;
    private final BoundRowPlan bound;

    CanonicalMappedDistinct(int expected, BoundRowPlan bound) {
        int capacity = capacity(expected, bound.provenance);
        this.values = new Object[capacity];
        this.occupied = new byte[capacity];
        this.mask = capacity - 1;
        this.bound = bound;
    }

    boolean add(Object candidate) {
        int hash;
        try {
            hash = candidate == null ? 0 : candidate.hashCode();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, bound.provenance);
        }
        int slot = spread(hash) & mask;
        while (occupied[slot] != 0) {
            try {
                if (Objects.equals(values[slot], candidate)) return false;
            } catch (Exception failure) {
                throw SomaFailures.callbackFailure(
                        SomaOperation.QUERY, failure, bound.provenance);
            }
            slot = (slot + 1) & mask;
        }
        occupied[slot] = 1;
        values[slot] = candidate;
        return true;
    }

    private static int spread(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        return hash ^ (hash >>> 16);
    }

    private static int capacity(int expected, Object provenance) {
        if (expected <= 1) return 2;
        if (expected > (1 << 29)) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    SomaOperation.QUERY,
                    "mapped distinct hash table exceeds Java array boundary",
                    provenance);
        }
        int required = expected << 1;
        int capacity = 2;
        while (capacity < required) capacity <<= 1;
        return capacity;
    }
}
