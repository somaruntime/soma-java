package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaCharDoubleConsumer {
    void accept(char key, double value);
}
