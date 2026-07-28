package io.github.somaruntime.soma.dataflow;

/**
 * Sequential expression escape hatch with definition-instance identity.
 *
 * <p>SOMA does not infer purity, determinism or thread safety from this
 * callback and never treats it as a registered reusable semantic function.</p>
 */
public interface OpaqueLongFunction {
    long applyAsLong(long value);
}
