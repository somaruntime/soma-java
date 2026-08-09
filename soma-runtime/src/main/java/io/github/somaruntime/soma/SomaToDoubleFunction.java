package io.github.somaruntime.soma;

@FunctionalInterface
public interface SomaToDoubleFunction<T> {
    double applyAsDouble(T value);
}
