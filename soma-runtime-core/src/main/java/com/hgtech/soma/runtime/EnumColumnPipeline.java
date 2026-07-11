package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

import java.util.function.Consumer;

/** 基于 ordinal packed storage 的静态 enum 绑定；遍历不对 ordinal boxing。 */
public final class EnumColumnPipeline<E extends Enum<E>> extends AbstractColumnPipeline {
    private final IntColumn column;
    private final E[] members;

    EnumColumnPipeline(
            DenseTableState state,
            IntColumn column,
            PresenceBitmap presence,
            String table,
            String field,
            E[] members) {
        super(state, column, presence, table, field);
        if (members == null) {
            throw new NullPointerException("members");
        }
        this.column = column;
        this.members = members;
    }

    public void forEach(Consumer<? super E> consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        long scanned = 0L;
        long matched = 0L;
        begin();
        try {
            int limit = size();
            int lane = traversalLane(limit);
            if (lane == TRAVERSE_ALL) {
                for (int row = 0; row < limit; row++) {
                    scanned++;
                    matched++;
                    consumer.accept(members[column.get(row)]);
                }
            } else if (lane == TRAVERSE_MIXED) {
                for (int word = 0, count = presenceWordCount(limit); word < count; word++) {
                    long bits = presenceWord(word, limit);
                    while (bits != 0L) {
                        int row = (word << 6) + Long.numberOfTrailingZeros(bits);
                        scanned = row + 1L;
                        matched++;
                        consumer.accept(members[column.get(row)]);
                        bits &= bits - 1L;
                    }
                }
                scanned = limit;
            } else {
                scanned = limit;
            }
            success(scanned, matched);
        } catch (SomaRuntimeException failure) {
            failure(scanned, matched, failure.code());
            throw failure;
        } catch (RuntimeException failure) {
            SomaRuntimeException wrapped = callbackFailed(failure);
            failure(scanned, matched, wrapped.code());
            throw wrapped;
        } catch (Error failure) {
            failure(scanned, matched, "callback_failed");
            throw failure;
        }
    }
}
