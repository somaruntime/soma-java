package com.hgtech.soma.dataflow;

/**
 * Caller-declared pure long-to-long expression extension.
 *
 * <p>The semantic identity and version describe behavior, not an implementation
 * class name. The caller owns evidence that the implementation is pure and
 * honors its determinism and thread-safety declarations.</p>
 */
public interface RegisteredLongFunction {
    String semanticId();

    String version();

    long applyAsLong(long value);

    boolean deterministic();

    boolean threadSafe();
}
