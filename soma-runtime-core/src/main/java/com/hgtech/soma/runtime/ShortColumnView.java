package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.ShortColumn;

public final class ShortColumnView extends AbstractColumnView implements AutoCloseable {
    private final ShortColumn column;
    ShortColumnView(DenseTableState state, ShortColumn column, PresenceBitmap presence,
            String table, String field, String columnOperation,
            String presenceOperation, String valueOperation) {
        super(state, column, presence, table, field, columnOperation, presenceOperation, valueOperation); this.column = column;
    }
    public boolean isPresent(int index) { return present(checkedPresenceRow(index)); }
    public short getShort(int index) { int row = checkedValueRow(index); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
