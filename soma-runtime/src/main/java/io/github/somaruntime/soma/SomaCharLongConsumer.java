package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaCharLongConsumer {
    void accept(char key, long value);
}
