package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Callback-scoped primitive staging session used by a generated Editor. */
public final class LongEditorSession {

    private final Thread participant;
    private final Object operationProvenance;
    private final int keyFieldIndex;
    private final long[] original;
    private final long[] staged;
    private boolean active = true;

    LongEditorSession(
            long[] original,
            int keyFieldIndex,
            Object operationProvenance) {
        this.participant = Thread.currentThread();
        this.operationProvenance = operationProvenance;
        this.keyFieldIndex = keyFieldIndex;
        this.original = original;
        this.staged = original.clone();
    }

    public long value(int fieldIndex) {
        check(fieldIndex);
        return staged[fieldIndex];
    }

    public void value(int fieldIndex, long value) {
        check(fieldIndex);
        if (fieldIndex == keyFieldIndex) {
            throw SomaFailures.failure(
                    SomaFailureCode.INVALID_ARGUMENT,
                    SomaOperation.UPDATE,
                    "Key Field is immutable after publication",
                    operationProvenance);
        }
        staged[fieldIndex] = value;
    }

    boolean changed() {
        for (int field = 0; field < staged.length; field++) {
            if (field != keyFieldIndex && original[field] != staged[field]) {
                return true;
            }
        }
        return false;
    }

    long[] stagedValues() {
        return staged;
    }

    void close() {
        active = false;
    }

    private void check(int fieldIndex) {
        if (!active
                || participant != Thread.currentThread()
                || fieldIndex < 0
                || fieldIndex >= staged.length) {
            throw SomaFailures.failure(
                    SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                    SomaOperation.UPDATE,
                    "Editor used outside its callback scope",
                    operationProvenance);
        }
    }
}
