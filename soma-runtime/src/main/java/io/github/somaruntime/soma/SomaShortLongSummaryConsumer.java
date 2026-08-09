package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaShortLongSummaryConsumer {
    void accept(short key, SomaLongSummary value);
}
