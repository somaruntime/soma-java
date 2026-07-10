package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.RuntimeFailures;

/** Shared lifecycle binding for handwritten primitive views. */
abstract class AbstractColumnView {
    private final DenseTableState state;
    private final PresenceBitmap presence;
    private final String table;
    private final String field;
    private final long capturedEpoch;
    private boolean closed;

    AbstractColumnView(
            DenseTableState state, Object column, PresenceBitmap presence, String table, String field) {
        if (state == null || column == null || table == null || field == null) {
            throw new NullPointerException("column view binding");
        }
        this.state = state;
        this.presence = presence;
        this.table = table;
        this.field = field;
        this.capturedEpoch = state.acquireView(field + ".column");
    }

    protected final int checkedRow(int rowIndex, String action) {
        String operation = field + ".column." + action;
        if (closed) {
            throw RuntimeFailures.releasedView(table, operation);
        }
        state.checkView(capturedEpoch, rowIndex, operation);
        return rowIndex;
    }

    protected final boolean present(int rowIndex) {
        return presence == null || presence.isPresent(rowIndex);
    }

    protected final void requirePresent(int rowIndex, String action) {
        if (!present(rowIndex)) {
            throw RuntimeFailures.optionalAbsent(table, field, field + ".column." + action);
        }
    }

    public void close() {
        if (!closed) {
            closed = true;
            state.releaseView();
        }
    }
}
