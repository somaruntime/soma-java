package io.github.somaruntime.soma;

/** Allocation-free consumer for a bounded int-key/long-value group result. */
@FunctionalInterface
public interface SomaIntLongConsumer {
    void accept(int key, long value);
}
