package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaLongIntConsumer {
    void accept(long key, int value);
}
