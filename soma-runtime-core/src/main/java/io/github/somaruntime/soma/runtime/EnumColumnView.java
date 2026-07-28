package io.github.somaruntime.soma.runtime;

import io.github.somaruntime.soma.runtime.generated.DenseTableState;
import io.github.somaruntime.soma.runtime.generated.IntColumn;
import io.github.somaruntime.soma.runtime.generated.PresenceBitmap;

/** 由 ordinal-packed IntColumn 支撑的显式 live enum view。 */
public final class EnumColumnView<E extends Enum<E>> extends AbstractColumnView implements AutoCloseable {
    private final IntColumn column;
    private final E[] members;

    EnumColumnView(
            DenseTableState state,
            IntColumn column,
            PresenceBitmap presence,
            String table,
            String field,
            String columnOperation,
            String presenceOperation,
            String valueOperation,
            E[] members) {
        super(state, column, presence, table, field, columnOperation, presenceOperation, valueOperation);
        if (members == null) {
            throw new NullPointerException("members");
        }
        this.column = column;
        this.members = members;
    }

    public boolean isPresent(int index) {
        return present(checkedPresenceRow(index));
    }

    public E get(int index) {
        int row = checkedValueRow(index);
        requirePresent(row);
        return members[column.get(row)];
    }

    @Override
    public void close() {
        super.close();
    }
}
