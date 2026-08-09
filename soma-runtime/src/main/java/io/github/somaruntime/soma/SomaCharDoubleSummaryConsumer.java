package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaCharDoubleSummaryConsumer {
    void accept(char key, SomaDoubleSummary value);
}
