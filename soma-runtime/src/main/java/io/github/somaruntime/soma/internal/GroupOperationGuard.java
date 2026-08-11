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
            try {
                lease.open(operation);
                return lease;
            } catch (RuntimeException failure) {
                current.compareAndSet(caller, null);
                throw failure;
            } catch (Error failure) {
                current.compareAndSet(caller, null);
                throw failure;
            }
        }

        boolean reentrant = current.get() == caller;
        Object provenance = reentrant ? lease.provenance() : new Object();
        throw SomaFailures.failure(
                reentrant
                        ? SomaFailureCode.REENTRANT_GROUP_OPERATION
                        : SomaFailureCode.CONCURRENT_GROUP_OPERATION,
                operation,
                reentrant
                        ? "same Group operation reentry"
                        : "same Group operation already active",
                provenance);
    }

    private void release(Lease candidate) {
        if (candidate != lease || !current.compareAndSet(Thread.currentThread(), null)) {
            throw new AssertionError("SOMA Group guard ownership was lost");
        }
    }

    static final class Lease
            implements AutoCloseable, SomaSharedSecrets.OperationProvenance {

        private final GroupOperationGuard owner;
        private SomaOperation operation;
        private long generation;
        private boolean closed;

        private Lease(GroupOperationGuard owner) {
            this.owner = owner;
        }

        private void open(SomaOperation operation) {
            if (generation == Long.MAX_VALUE) {
                throw SomaFailures.failure(
                        SomaFailureCode.ARITHMETIC_OVERFLOW,
                        operation,
                        "Group operation generation overflow",
                        new Object());
            }
            this.operation = operation;
            this.generation++;
            this.closed = false;
        }

        Object provenance() {
            if (closed) {
                throw new AssertionError("SOMA Group operation is not active");
            }
            return this;
        }

        @Override
        public Object owner() {
            return this;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    CallbackExecutionScope.clearIfInactive();
                } finally {
                    try {
                        owner.release(this);
                    } finally {
                        operation = null;
                    }
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
