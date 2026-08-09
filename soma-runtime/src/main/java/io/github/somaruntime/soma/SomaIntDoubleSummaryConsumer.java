package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaIntDoubleSummaryConsumer {
    void accept(int key, SomaDoubleSummary value);
}
