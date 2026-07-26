package com.hgtech.soma.dataflow;

/**
 * Invocation-scoped view of one logical group.
 *
 * <p>The cursor is valid only during its consumer callback. Its indexes are
 * current physical positions, not stable identity.</p>
 */
public interface GroupCursor {
    int ordinal();

    int representativeIndex();

    int size();

    int indexAt(int position);
}
