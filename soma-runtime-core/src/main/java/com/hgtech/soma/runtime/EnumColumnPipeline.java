package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;

import java.util.function.Consumer;

/** 基于 ordinal packed storage 的静态 enum 绑定；遍历不对 ordinal boxing。 */
public final class EnumColumnPipeline<E extends Enum<E>> extends AbstractColumnPipeline {
    private final IntColumn column;
    private final E[] members;

    public EnumColumnPipeline(
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
            for (int row = 0, limit = size(); row < limit; row++) {
                scanned++;
                if (visit(row)) {
                    matched++;
                    consumer.accept(members[column.get(row)]);
                }
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
