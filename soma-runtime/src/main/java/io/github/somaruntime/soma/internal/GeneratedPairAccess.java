package io.github.somaruntime.soma.internal;

/** Runtime scope contract behind one generated borrowed JoinPair. */
public interface GeneratedPairAccess {
    void register(Object pair);
    boolean hasLeft();
    boolean hasRight();
    void requireLeft();
    void requireRight();
}
