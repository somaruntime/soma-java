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
    private final String presenceOperation;
    private final String valueOperation;
    private final long capturedEpoch;
    private boolean closed;

    AbstractColumnView(
            DenseTableState state, Object column, PresenceBitmap presence,
            String table, String field, String columnOperation,
            String presenceOperation, String valueOperation) {
        if (state == null || column == null || table == null || field == null
                || columnOperation == null || presenceOperation == null || valueOperation == null) {
            throw new NullPointerException("column view binding");
        }
        this.state = state;
        this.presence = presence;
        this.table = table;
        this.field = field;
        this.presenceOperation = presenceOperation;
        this.valueOperation = valueOperation;
        this.capturedEpoch = state.acquireView(columnOperation);
    }

    protected final int checkedPresenceRow(int rowIndex) {
        return checkedRow(rowIndex, presenceOperation);
    }

    protected final int checkedValueRow(int rowIndex) {
        return checkedRow(rowIndex, valueOperation);
    }

    private int checkedRow(int rowIndex, String operation) {
        if (closed) {
            throw RuntimeFailures.releasedView(table, operation);
        }
        state.checkView(capturedEpoch, rowIndex, operation);
        return rowIndex;
    }

    protected final boolean present(int rowIndex) {
        return presence == null || presence.isPresent(rowIndex);
    }

    protected final void requirePresent(int rowIndex) {
        if (!present(rowIndex)) {
            throw RuntimeFailures.optionalAbsent(table, field, valueOperation);
        }
    }

    public void close() {
        if (!closed) {
            closed = true;
            state.releaseView();
        }
    }
}
