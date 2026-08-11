package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicReference;

final class GroupOperationGuard {

    private final Lease lease = new Lease(this);
    private final AtomicReference<Thread> current = new AtomicReference<Thread>();

    Lease acquire(SomaOperation operation) {
        Thread caller = Thread.currentThread();
        if (current.compareAndSet(null, caller)) {
            lease.open(operation);
            return lease;
        }

        boolean reentrant = current.get() == caller;
        throw SomaFailures.failure(
                reentrant
                        ? SomaFailureCode.REENTRANT_GROUP_OPERATION
                        : SomaFailureCode.CONCURRENT_GROUP_OPERATION,
                operation,
                reentrant
                        ? "same Group operation reentry"
                        : "same Group operation already active",
                lease);
    }

    private void release(Lease candidate) {
        if (candidate != lease || !current.compareAndSet(Thread.currentThread(), null)) {
            throw new AssertionError("SOMA Group guard ownership was lost");
        }
    }

    static final class Lease implements AutoCloseable {

        private final GroupOperationGuard owner;
        private SomaOperation operation;
        private boolean closed;

        private Lease(GroupOperationGuard owner) {
            this.owner = owner;
        }

        private void open(SomaOperation operation) {
            this.operation = operation;
            this.closed = false;
        }

        Object provenance() {
            return this;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    CallbackExecutionScope.clearIfInactive();
                } finally {
                    owner.release(this);
                }
            }
        }

        @Override
        public String toString() {
            SomaOperation active = operation;
            return active == null ? "GROUP_OPERATION" : active.name();
        }
    }
}
