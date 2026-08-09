package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaByteIntConsumer {
    void accept(byte key, int value);
}
