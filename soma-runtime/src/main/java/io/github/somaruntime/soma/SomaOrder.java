package io.github.somaruntime.soma;

/** Generated lexicographic logical order carrier. This is not an application SPI. */
public interface SomaOrder<R> {
    SomaOrder<R> then(SomaOrder<R> next);
}
