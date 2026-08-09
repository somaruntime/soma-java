package io.github.somaruntime.soma;

/** Behavioral predicate callback. Typed SOMA expressions use {@link SomaExpression}. */
@FunctionalInterface
public interface SomaPredicate<T> {
    boolean test(T value);
}
