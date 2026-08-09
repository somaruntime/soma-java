package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaShortLongConsumer {
    void accept(short key, long value);
}
