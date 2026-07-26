package com.hgtech.soma.dataflow;

/** Invocation cooperative cancellation signal owned by the caller. */
public interface CancellationToken {
    CancellationToken NONE = new CancellationToken() {
        @Override
        public boolean isCancellationRequested() {
            return false;
        }
    };

    boolean isCancellationRequested();
}
