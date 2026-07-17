package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.ShortColumn;

public final class ShortColumnView extends AbstractColumnView implements AutoCloseable {
    private static final ColumnViewOperations.Cache OPERATIONS =
            new ColumnViewOperations.Cache("getShort");
    private final ShortColumn column;
    ShortColumnView(DenseTableState state, ShortColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field, OPERATIONS); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedPresenceRow(rowIndex)); }
    public short getShort(int rowIndex) { int row = checkedValueRow(rowIndex); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
