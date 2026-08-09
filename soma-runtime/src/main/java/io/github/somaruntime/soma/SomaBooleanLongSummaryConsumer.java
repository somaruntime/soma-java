package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaBooleanLongSummaryConsumer {
    void accept(boolean key, SomaLongSummary value);
}
