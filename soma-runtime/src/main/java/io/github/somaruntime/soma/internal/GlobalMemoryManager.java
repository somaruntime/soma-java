package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

final class GlobalMemoryManager {

    private final long budgetBytes;
    private long retainedBytes;
    private long temporaryBytes;

    GlobalMemoryManager(long budgetBytes) {
        if (budgetBytes <= 0L) {
            throw new AssertionError("effective SOMA budget must be positive");
        }
        this.budgetBytes = budgetBytes;
    }

    synchronized RetainedReservation reserveRetained(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        if (bytes < 0L) {
            throw new AssertionError("negative retained reservation");
        }
        RetainedReservation reservation = new RetainedReservation(this, bytes);
        admit(bytes, operation, provenance);
        retainedBytes += bytes;
        return reservation;
    }

    synchronized TemporaryLease leaseTemporary(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        if (bytes < 0L) {
            throw new AssertionError("negative temporary lease");
        }
        TemporaryLease lease = new TemporaryLease(this, bytes);
        admit(bytes, operation, provenance);
        temporaryBytes += bytes;
        return lease;
    }

    synchronized long retainedBytes() {
        return retainedBytes;
    }

    synchronized long temporaryBytes() {
        return temporaryBytes;
    }

    long budgetBytes() {
        return budgetBytes;
    }

    synchronized void releasePublished(long bytes) {
        if (bytes < 0L || bytes > retainedBytes) {
            throw new AssertionError("invalid published retained release");
        }
        retainedBytes -= bytes;
    }

    private void admit(long requested, SomaOperation operation, Object provenance) {
        long used;
        try {
            used = Math.addExact(retainedBytes, temporaryBytes);
            long required = Math.addExact(used, requested);
            if (required > budgetBytes) {
                throw resource(operation, provenance, requested, budgetBytes - used);
            }
        } catch (ArithmeticException exception) {
            throw SomaFailures.failure(
                    SomaFailureCode.ARITHMETIC_OVERFLOW,
                    operation,
                    "managed-memory accounting overflow",
                    provenance);
        }
    }

    private static RuntimeException resource(
            SomaOperation operation,
            Object provenance,
            long requested,
            long available) {
        return SomaFailures.failure(
                SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                operation,
                "managed bytes required=" + requested
                        + ", available=" + Math.max(0L, available),
                provenance);
    }

    private synchronized void releaseRetained(long bytes) {
        retainedBytes -= bytes;
        if (retainedBytes < 0L) {
            throw new AssertionError("SOMA retained accounting underflow");
        }
    }

    private synchronized void releaseTemporary(long bytes) {
        temporaryBytes -= bytes;
        if (temporaryBytes < 0L) {
            throw new AssertionError("SOMA temporary accounting underflow");
        }
    }

    static final class RetainedReservation implements AutoCloseable {

        private final GlobalMemoryManager owner;
        private final long bytes;
        private boolean committed;
        private boolean closed;

        private RetainedReservation(GlobalMemoryManager owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        void commit() {
            committed = true;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (!committed) {
                    owner.releaseRetained(bytes);
                }
            }
        }
    }

    static final class TemporaryLease implements AutoCloseable {

        private final GlobalMemoryManager owner;
        private final long bytes;
        private boolean closed;

        private TemporaryLease(GlobalMemoryManager owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.releaseTemporary(bytes);
            }
        }
    }
}
