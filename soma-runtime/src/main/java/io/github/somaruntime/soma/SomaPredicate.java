package io.github.somaruntime.soma;

/** Opaque application callback used as an optimizer barrier. */
@FunctionalInterface
public interface SomaPredicate<T> {
    boolean test(T value);
}
