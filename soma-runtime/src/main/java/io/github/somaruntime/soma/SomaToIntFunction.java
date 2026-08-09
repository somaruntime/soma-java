package io.github.somaruntime.soma;

@FunctionalInterface
public interface SomaToIntFunction<T> {
    int applyAsInt(T value);
}
