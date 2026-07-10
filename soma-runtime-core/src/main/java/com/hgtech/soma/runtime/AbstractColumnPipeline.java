package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeFailures;

/** Shared operation/lifecycle binding for primitive column traversal. */
abstract class AbstractColumnPipeline {
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
    protected final boolean visit(int rowIndex) { return presence == null || presence.isPresent(rowIndex); }
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
