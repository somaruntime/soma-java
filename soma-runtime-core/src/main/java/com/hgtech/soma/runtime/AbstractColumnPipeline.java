package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeFailures;

/** Shared operation/lifecycle binding for primitive column traversal. */
abstract class AbstractColumnPipeline {
    protected static final int TRAVERSE_ALL = 0;
    protected static final int TRAVERSE_NONE = 1;
    protected static final int TRAVERSE_MIXED = 2;

    private final DenseTableState state;
    private final PresenceBitmap presence;
    private final String table;
    private final String operation;

    AbstractColumnPipeline(
            DenseTableState state, Object column, PresenceBitmap presence, String table, String field) {
        if (state == null || column == null || table == null || field == null) {
            throw new NullPointerException("column pipeline binding");
        }
        this.state = state;
        this.presence = presence;
        this.table = table;
        this.operation = field + ".values";
    }

    protected final void begin() { state.beginOperation(operation); }
    protected final int size() { return state.size(); }
    protected final int traversalLane(int limit) {
        if (presence == null) {
            return TRAVERSE_ALL;
        }
        int present = presence.presentCount();
        if (present == 0) {
            return TRAVERSE_NONE;
        }
        if (present >= limit && allPresent(limit)) {
            return TRAVERSE_ALL;
        }
        return TRAVERSE_MIXED;
    }
    private boolean allPresent(int limit) {
        int fullWords = limit >>> 6;
        for (int word = 0; word < fullWords; word++) {
            if (presence.wordAt(word) != -1L) {
                return false;
            }
        }
        int remaining = limit & 63;
        return remaining == 0
                || (presence.wordAt(fullWords) & ((1L << remaining) - 1L))
                == ((1L << remaining) - 1L);
    }
    protected final int presenceWordCount(int limit) {
        return (int) (((long) limit + 63L) >>> 6);
    }
    protected final long presenceWord(int wordIndex, int limit) {
        long bits = presence.wordAt(wordIndex);
        int remaining = limit - (wordIndex << 6);
        if (remaining < 64) {
            bits &= (1L << remaining) - 1L;
        }
        return bits;
    }
    protected final void success(long scanned, long matched) {
        state.endOperationSuccess(operation, scanned, matched, 0L);
    }
    protected final void failure(long scanned, long matched, String errorCode) {
        state.endOperationFailure(operation, scanned, matched, errorCode);
    }
    protected final SomaRuntimeException callbackFailed(RuntimeException cause) {
        return RuntimeFailures.callbackFailed(table, operation, "consumer", cause);
    }
}
