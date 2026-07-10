package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.ByteColumn;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class ByteColumnView extends AbstractColumnView implements AutoCloseable {
    private final ByteColumn column;
    public ByteColumnView(DenseTableState state, ByteColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedRow(rowIndex, "isPresent")); }
    public byte getByte(int rowIndex) { int row = checkedRow(rowIndex, "getByte"); requirePresent(row, "getByte"); return column.get(row); }
    @Override public void close() { super.close(); }
}
