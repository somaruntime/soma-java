package io.github.somaruntime.soma;

import io.github.somaruntime.soma.internal.SomaSharedSecrets;

/** Immutable detached two-field projection. */
public final class SomaTuple2<A, B> {
    static {
        SomaSharedSecrets.setTuple2Access(
                new SomaSharedSecrets.Tuple2Access() {
                    @Override public <X, Y> SomaTuple2<X, Y> create(X first, Y second) {
                        return new SomaTuple2<X, Y>(first, second);
                    }
                });
    }
    private final A first;
    private final B second;
    private SomaTuple2(A first, B second) {
        this.first = first;
        this.second = second;
    }
    public A first() { return first; }
    public B second() { return second; }
}
