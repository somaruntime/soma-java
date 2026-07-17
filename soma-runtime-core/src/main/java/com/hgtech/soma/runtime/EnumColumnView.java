package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

/** 由 ordinal-packed IntColumn 支撑的显式 live enum view。 */
public final class EnumColumnView<E extends Enum<E>> extends AbstractColumnView implements AutoCloseable {
    private static final ColumnViewOperations.Cache OPERATIONS =
            new ColumnViewOperations.Cache("get");
    private final IntColumn column;
    private final E[] members;

    EnumColumnView(
            DenseTableState state,
            IntColumn column,
            PresenceBitmap presence,
            String table,
            String field,
            E[] members) {
        super(state, column, presence, table, field, OPERATIONS);
        if (members == null) {
            throw new NullPointerException("members");
        }
        this.column = column;
        this.members = members;
    }

    public boolean isPresent(int rowIndex) {
        return present(checkedPresenceRow(rowIndex));
    }

    public E get(int rowIndex) {
        int row = checkedValueRow(rowIndex);
        requirePresent(row);
        return members[column.get(row)];
    }

    @Override
    public void close() {
        super.close();
    }
}
