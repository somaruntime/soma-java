package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class IntColumnView extends AbstractColumnView implements AutoCloseable {
    private final IntColumn column;
    IntColumnView(DenseTableState state, IntColumn column, PresenceBitmap presence,
            String table, String field, String columnOperation,
            String presenceOperation, String valueOperation) {
        super(state, column, presence, table, field, columnOperation, presenceOperation, valueOperation); this.column = column;
    }
    public boolean isPresent(int index) { return present(checkedPresenceRow(index)); }
    public int getInt(int index) { int row = checkedValueRow(index); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
