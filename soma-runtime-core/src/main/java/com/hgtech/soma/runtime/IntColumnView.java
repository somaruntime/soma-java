package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class IntColumnView extends AbstractColumnView implements AutoCloseable {
    private final IntColumn column;
    IntColumnView(DenseTableState state, IntColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public int getInt(int rowIndex) { int row = checkedRow(rowIndex, "getInt"); requirePresent(row, "getInt"); return column.get(row); }
    @Override public void close() { super.close(); }
}
