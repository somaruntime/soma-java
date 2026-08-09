package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaIntDoubleConsumer {
    void accept(int key, double value);
}
