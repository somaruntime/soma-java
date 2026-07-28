package io.github.somaruntime.soma.runtime.generated;

/** Generated owned-table lifecycle bridge；不进入 application public signature。 */
public interface OwnedChildTable {
    boolean hasPinnedSubtree();
    void preflightOwnedRelease(boolean aggregateRelease);
    void releaseOwnedSubtree(boolean aggregateRelease);
    long subtreeChildInstanceCount();
    long subtreeDescendantRowCount();
}
