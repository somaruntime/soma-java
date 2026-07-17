package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.BooleanColumn;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

public final class BooleanColumnView extends AbstractColumnView implements AutoCloseable {
    private static final ColumnViewOperations.Cache OPERATIONS =
            new ColumnViewOperations.Cache("getBoolean");
    private final BooleanColumn column;
    BooleanColumnView(DenseTableState state, BooleanColumn column, PresenceBitmap presence, String table, String field) {
        super(state, column, presence, table, field, OPERATIONS); this.column = column;
    }
    public boolean isPresent(int rowIndex) { return present(checkedPresenceRow(rowIndex)); }
    public boolean getBoolean(int rowIndex) { int row = checkedValueRow(rowIndex); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
