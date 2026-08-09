package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaIntLongSummaryConsumer {
    void accept(int key, SomaLongSummary value);
}
