package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaIntIntConsumer {
    void accept(int key, int value);
}
