package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicReference;

final class GroupOperationGuard {

    private final AtomicReference<Lease> current = new AtomicReference<Lease>();

    Lease acquire(SomaOperation operation) {
        Lease candidate = new Lease(this, operation, Thread.currentThread());
        if (current.compareAndSet(null, candidate)) {
            return candidate;
        }

        Lease active = current.get();
        boolean reentrant = active != null && active.thread == Thread.currentThread();
        throw SomaFailures.failure(
                reentrant
                        ? SomaFailureCode.REENTRANT_GROUP_OPERATION
                        : SomaFailureCode.CONCURRENT_GROUP_OPERATION,
                operation,
                reentrant
                        ? "same Group operation reentry"
                        : "same Group operation already active",
                reentrant ? active : candidate);
    }

    private void release(Lease lease) {
        if (!current.compareAndSet(lease, null)) {
            throw new AssertionError("SOMA Group guard ownership was lost");
        }
    }

    static final class Lease implements AutoCloseable {

        private final GroupOperationGuard owner;
        private final SomaOperation operation;
        private final Thread thread;
        private boolean closed;

        private Lease(
                GroupOperationGuard owner,
                SomaOperation operation,
                Thread thread) {
            this.owner = owner;
            this.operation = operation;
            this.thread = thread;
        }

        Object provenance() {
            return this;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    owner.release(this);
                } finally {
                    CallbackExecutionScope.clearIfInactive();
                }
            }
        }

        @Override
        public String toString() {
            return operation.name();
        }
    }
}
