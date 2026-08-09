package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaLongDoubleSummaryConsumer {
    void accept(long key, SomaDoubleSummary value);
}
