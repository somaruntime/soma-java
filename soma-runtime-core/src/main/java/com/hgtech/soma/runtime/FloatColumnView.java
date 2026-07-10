package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.FloatColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class FloatColumnView extends AbstractColumnView implements AutoCloseable {
    private final FloatColumn column;
    public FloatColumnView(DenseTableState state, FloatColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public float getFloat(int rowIndex) { int row = checkedRow(rowIndex, "getFloat"); requirePresent(row, "getFloat"); return column.get(row); }
    @Override public void close() { super.close(); }
}
