package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicReference;

final class GroupOperationGuard {

    private final AtomicReference<OperationToken> current =
            new AtomicReference<OperationToken>();

    Lease acquire(SomaOperation operation) {
        OperationToken candidate = new OperationToken(operation, Thread.currentThread());
        Lease lease = new Lease(this, candidate);
        if (current.compareAndSet(null, candidate)) {
            return lease;
        }

        OperationToken active = current.get();
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

    private void release(OperationToken token) {
        if (!current.compareAndSet(token, null)) {
            throw new AssertionError("SOMA Group guard ownership was lost");
        }
    }

    static final class Lease implements AutoCloseable {

        private final GroupOperationGuard owner;
        private final OperationToken token;
        private boolean closed;

        private Lease(GroupOperationGuard owner, OperationToken token) {
            this.owner = owner;
            this.token = token;
        }

        Object provenance() {
            return token;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.release(token);
            }
        }
    }

    private static final class OperationToken {

        private final SomaOperation operation;
        private final Thread thread;

        private OperationToken(SomaOperation operation, Thread thread) {
            this.operation = operation;
            this.thread = thread;
        }

        @Override
        public String toString() {
            return operation.name();
        }
    }
}
