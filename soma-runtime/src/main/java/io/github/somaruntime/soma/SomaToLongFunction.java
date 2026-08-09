package io.github.somaruntime.soma;

@FunctionalInterface
public interface SomaToLongFunction<T> {
    long applyAsLong(T value);
}
