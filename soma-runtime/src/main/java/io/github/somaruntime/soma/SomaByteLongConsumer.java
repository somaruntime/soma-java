package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaByteLongConsumer {
    void accept(byte key, long value);
}
