package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaShortIntConsumer {
    void accept(short key, int value);
}
