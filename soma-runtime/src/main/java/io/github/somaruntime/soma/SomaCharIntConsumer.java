package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaCharIntConsumer {
    void accept(char key, int value);
}
