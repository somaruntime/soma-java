package io.github.somaruntime.soma;

/** Borrowed callback-scoped view of one binary relation result. */
public interface JoinPair<L, R> {
    boolean hasLeft();
    boolean hasRight();
    L left();
    R right();
}
