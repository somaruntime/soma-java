package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.BooleanColumn;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class BooleanColumnView extends AbstractColumnView implements AutoCloseable {
    private final BooleanColumn column;
    BooleanColumnView(DenseTableState state, BooleanColumn column, PresenceBitmap presence,
            String table, String field, String columnOperation,
            String presenceOperation, String valueOperation) {
        super(state, column, presence, table, field, columnOperation, presenceOperation, valueOperation); this.column = column;
    }
    public boolean isPresent(int index) { return present(checkedPresenceRow(index)); }
    public boolean getBoolean(int index) { int row = checkedValueRow(index); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
