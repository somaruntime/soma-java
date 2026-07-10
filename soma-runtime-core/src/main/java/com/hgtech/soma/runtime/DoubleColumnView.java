package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.DoubleColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class DoubleColumnView extends AbstractColumnView implements AutoCloseable {
    private final DoubleColumn column;
    public DoubleColumnView(DenseTableState state, DoubleColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public double getDouble(int rowIndex) { int row = checkedRow(rowIndex, "getDouble"); requirePresent(row, "getDouble"); return column.get(row); }
    @Override public void close() { super.close(); }
}
