package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaByteDoubleConsumer {
    void accept(byte key, double value);
}
