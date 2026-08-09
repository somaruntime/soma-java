package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaCharLongSummaryConsumer {
    void accept(char key, SomaLongSummary value);
}
