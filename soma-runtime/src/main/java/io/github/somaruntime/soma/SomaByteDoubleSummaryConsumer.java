package io.github.somaruntime.soma;

/** Unboxed primitive-key grouped result consumer. */
@FunctionalInterface
public interface SomaByteDoubleSummaryConsumer {
    void accept(byte key, SomaDoubleSummary value);
}
