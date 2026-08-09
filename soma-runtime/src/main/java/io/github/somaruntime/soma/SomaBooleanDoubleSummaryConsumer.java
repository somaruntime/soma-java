package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaBooleanDoubleSummaryConsumer {
    void accept(boolean key, SomaDoubleSummary value);
}
