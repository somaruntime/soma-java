package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

import java.util.function.LongConsumer;

public final class LongColumnPipeline extends AbstractColumnPipeline {
    private final LongColumn column;
    public LongColumnPipeline(DenseTableState state, LongColumn column, PresenceBitmap presence, String table, String field) { super(state, column, presence, table, field); this.column = column; }
    public void forEachLong(LongConsumer consumer) {
        if (consumer == null) throw new NullPointerException("consumer"); long scanned = 0L, matched = 0L; begin();
        try { for (int row = 0, limit = size(); row < limit; row++) { scanned++; if (visit(row)) { matched++; consumer.accept(column.get(row)); } } success(scanned, matched); }
        catch (SomaRuntimeException failure) { failure(scanned, matched, failure.code()); throw failure; }
        catch (RuntimeException failure) { SomaRuntimeException wrapped = callbackFailed(failure); failure(scanned, matched, wrapped.code()); throw wrapped; }
        catch (Error failure) { failure(scanned, matched, "callback_failed"); throw failure; }
    }
}
