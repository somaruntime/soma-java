package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaShortDoubleSummaryConsumer {
    void accept(short key, SomaDoubleSummary value);
}
