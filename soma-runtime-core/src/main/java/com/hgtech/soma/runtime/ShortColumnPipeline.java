package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.ShortColumn;

public final class ShortColumnPipeline extends AbstractColumnPipeline {
    private final ShortColumn column;
    ShortColumnPipeline(DenseTableState state, ShortColumn column, PresenceBitmap presence, String table, String field) { super(state, column, presence, table, field); this.column = column; }
    public void forEachShort(ShortConsumer consumer) {
        if (consumer == null) throw new NullPointerException("consumer"); long scanned = 0L, matched = 0L; begin();
        try {
            int limit = size();
            int lane = traversalLane(limit);
            if (lane == TRAVERSE_ALL) {
                for (int row = 0; row < limit; row++) { scanned++; matched++; consumer.accept(column.get(row)); }
            } else if (lane == TRAVERSE_MIXED) {
                for (int word = 0, count = presenceWordCount(limit); word < count; word++) {
                    long bits = presenceWord(word, limit);
                    while (bits != 0L) { int row = (word << 6) + Long.numberOfTrailingZeros(bits); scanned = row + 1L; matched++; consumer.accept(column.get(row)); bits &= bits - 1L; }
                }
                scanned = limit;
            } else { scanned = limit; }
            success(scanned, matched);
        }
        catch (SomaRuntimeException failure) { failure(scanned, matched, failure.code()); throw failure; }
        catch (RuntimeException failure) { SomaRuntimeException wrapped = callbackFailed(failure); failure(scanned, matched, wrapped.code()); throw wrapped; }
        catch (Error failure) { failure(scanned, matched, "callback_failed"); throw failure; }
    }
}
