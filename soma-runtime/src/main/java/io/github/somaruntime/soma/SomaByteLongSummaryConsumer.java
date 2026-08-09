package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaByteLongSummaryConsumer {
    void accept(byte key, SomaLongSummary value);
}
