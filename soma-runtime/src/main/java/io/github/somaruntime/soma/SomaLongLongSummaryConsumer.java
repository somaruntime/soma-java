package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaLongLongSummaryConsumer {
    void accept(long key, SomaLongSummary value);
}
