package io.github.somaruntime.soma.internal;

/**
 * Exact-leaf read contract used by generated borrowed Views.
 *
 * <p>Mutation staging and query traversal deliberately implement this contract through
 * different owners.  Generated application code can therefore share one View shape without
 * coupling query currentness to the point-mutation row lifecycle.</p>
 */
public interface GeneratedRowAccess {

    void registerBorrowedView(Object view);

    boolean viewBoolean(int leaf);
    byte viewByte(int leaf);
    short viewShort(int leaf);
    char viewChar(int leaf);
    int viewInt(int leaf);
    long viewLong(int leaf);
    float viewFloat(int leaf);
    double viewDouble(int leaf);
    Object viewReference(int leaf);
}
