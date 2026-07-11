package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.ShortColumn;

public final class ShortColumnView extends AbstractColumnView implements AutoCloseable {
    private final ShortColumn column;
    ShortColumnView(DenseTableState state, ShortColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public short getShort(int rowIndex) { int row = checkedRow(rowIndex, "getShort"); requirePresent(row, "getShort"); return column.get(row); }
    @Override public void close() { super.close(); }
}
