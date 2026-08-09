package io.github.somaruntime.soma;

/** Detached grouped result entry. */
public interface GroupedLongEntry<K> {
    K key();
    long value();
}
