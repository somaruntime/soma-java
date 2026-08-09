package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaShortDoubleConsumer {
    void accept(short key, double value);
}
