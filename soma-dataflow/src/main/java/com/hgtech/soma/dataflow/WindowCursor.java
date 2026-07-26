package com.hgtech.soma.dataflow;

/**
 * Invocation-scoped finite window cursor.
 *
 * <p>The cursor and its current indexes must not escape the callback.</p>
 */
public interface WindowCursor {
    int ordinal();

    int size();

    int indexAt(int position);
}
