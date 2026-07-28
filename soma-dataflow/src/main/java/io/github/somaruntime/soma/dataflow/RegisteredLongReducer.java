package io.github.somaruntime.soma.dataflow;

/**
 * Caller-declared mergeable primitive long reduction.
 *
 * <p>The caller owns evidence that {@link #merge(long, long)} is associative
 * when {@link #associative()} returns true, and that all methods honor the
 * declared determinism and thread-safety traits.</p>
 */
public interface RegisteredLongReducer {
    String semanticId();

    String version();

    /** Returns the neutral identity state for both accumulate and merge. */
    long seed();

    long accumulate(long state, long value);

    long merge(long leftState, long rightState);

    long finish(long state);

    boolean associative();

    boolean deterministic();

    boolean threadSafe();
}
