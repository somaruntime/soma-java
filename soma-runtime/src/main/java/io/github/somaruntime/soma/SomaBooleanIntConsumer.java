package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaBooleanIntConsumer {
    void accept(boolean key, int value);
}
