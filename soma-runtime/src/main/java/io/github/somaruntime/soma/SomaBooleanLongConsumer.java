package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaBooleanLongConsumer {
    void accept(boolean key, long value);
}
