package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaLongLongConsumer {
    void accept(long key, long value);
}
