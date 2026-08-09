package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaLongDoubleConsumer {
    void accept(long key, double value);
}
