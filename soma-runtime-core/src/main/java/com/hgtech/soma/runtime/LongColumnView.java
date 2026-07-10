package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class LongColumnView extends AbstractColumnView implements AutoCloseable {
    private final LongColumn column;
    public LongColumnView(DenseTableState state, LongColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public long getLong(int rowIndex) { int row = checkedRow(rowIndex, "getLong"); requirePresent(row, "getLong"); return column.get(row); }
    @Override public void close() { super.close(); }
}
