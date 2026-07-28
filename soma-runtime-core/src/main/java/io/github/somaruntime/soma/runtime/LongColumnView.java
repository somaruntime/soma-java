package io.github.somaruntime.soma.runtime;

import io.github.somaruntime.soma.runtime.generated.DenseTableState;
import io.github.somaruntime.soma.runtime.generated.LongColumn;
import io.github.somaruntime.soma.runtime.generated.PresenceBitmap;

public final class LongColumnView extends AbstractColumnView implements AutoCloseable {
    private final LongColumn column;
    LongColumnView(DenseTableState state, LongColumn column, PresenceBitmap presence,
            String table, String field, String columnOperation,
            String presenceOperation, String valueOperation) {
        super(state, column, presence, table, field, columnOperation, presenceOperation, valueOperation); this.column = column;
    }
    public boolean isPresent(int index) { return present(checkedPresenceRow(index)); }
    public long getLong(int index) { int row = checkedValueRow(index); requirePresent(row); return column.get(row); }
    @Override public void close() { super.close(); }
}
