package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaBooleanDoubleConsumer {
    void accept(boolean key, double value);
}
