package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;

/** ClassLoader-wide checked accounting with reachability-owned Group release. */
final class GlobalMemoryManager {

    private final long budgetBytes;
    private final ReferenceQueue<GeneratedGroup> collectedGroups =
            new ReferenceQueue<GeneratedGroup>();
    private final Set<GroupReference> groupReferences =
            new HashSet<GroupReference>();
    private long retainedBytes;
    private long temporaryBytes;

    GlobalMemoryManager(long budgetBytes) {
        if (budgetBytes <= 0L) {
            throw new AssertionError("effective SOMA budget must be positive");
        }
        this.budgetBytes = budgetBytes;
    }

    synchronized GroupToken newGroupToken() {
        drainCollectedGroups();
        return new GroupToken();
    }

    synchronized void registerGroup(GeneratedGroup group, GroupToken token) {
        if (group == null || token == null || token.registered || token.released) {
            throw new AssertionError("invalid SOMA Group accounting registration");
        }
        drainCollectedGroups();
        token.registered = true;
        groupReferences.add(new GroupReference(group, collectedGroups, token));
    }

    synchronized RetainedReservation reserveRetained(
            GroupToken token,
            long bytes,
            SomaOperation operation,
            Object provenance) {
        requireLive(token);
        if (bytes < 0L) {
            throw new AssertionError("negative retained reservation");
        }
        if (bytes == 0L) return RetainedReservation.NOOP;
        admit(bytes, operation, provenance);
        retainedBytes += bytes;
        token.retainedBytes += bytes;
        return new RetainedReservation(this, token, bytes);
    }

    synchronized TemporaryLease leaseTemporary(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        if (bytes < 0L) {
            throw new AssertionError("negative temporary lease");
        }
        if (bytes == 0L) return TemporaryLease.NOOP;
        admit(bytes, operation, provenance);
        temporaryBytes += bytes;
        return new TemporaryLease(this, bytes);
    }

    synchronized long retainedBytes() {
        drainCollectedGroups();
        return retainedBytes;
    }

    synchronized long temporaryBytes() {
        drainCollectedGroups();
        return temporaryBytes;
    }

    synchronized long retainedBytes(GroupToken token) {
        requireLive(token);
        return token.retainedBytes;
    }

    long budgetBytes() {
        return budgetBytes;
    }

    synchronized void releasePublished(GroupToken token, long bytes) {
        requireLive(token);
        if (bytes < 0L || bytes > retainedBytes || bytes > token.retainedBytes) {
            throw new AssertionError("invalid published retained release");
        }
        retainedBytes -= bytes;
        token.retainedBytes -= bytes;
    }

    synchronized void drainCollectedGroups() {
        GroupReference reference;
        while ((reference = (GroupReference) collectedGroups.poll()) != null) {
            if (!groupReferences.remove(reference)) continue;
            GroupToken token = reference.token;
            if (token.released) {
                throw new AssertionError("SOMA Group accounting released twice");
            }
            retainedBytes -= token.retainedBytes;
            if (retainedBytes < 0L) {
                throw new AssertionError("SOMA retained accounting underflow");
            }
            token.retainedBytes = 0L;
            token.released = true;
            reference.clear();
        }
    }

    private void admit(long requested, SomaOperation operation, Object provenance) {
        drainCollectedGroups();
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

    private void requireLive(GroupToken token) {
        if (token == null || token.released) {
            throw new AssertionError("SOMA Group accounting token is not live");
        }
    }

    private synchronized void releaseRetained(GroupToken token, long bytes) {
        requireLive(token);
        retainedBytes -= bytes;
        token.retainedBytes -= bytes;
        if (retainedBytes < 0L || token.retainedBytes < 0L) {
            throw new AssertionError("SOMA retained accounting underflow");
        }
    }

    private synchronized void releaseTemporary(long bytes) {
        temporaryBytes -= bytes;
        if (temporaryBytes < 0L) {
            throw new AssertionError("SOMA temporary accounting underflow");
        }
    }

    static final class GroupToken {
        private long retainedBytes;
        private boolean registered;
        private boolean released;
    }

    static final class RetainedReservation implements AutoCloseable {

        private static final RetainedReservation NOOP = new RetainedReservation();

        private final GlobalMemoryManager owner;
        private final GroupToken token;
        private final long bytes;
        private boolean committed;
        private boolean closed;

        private RetainedReservation() {
            this.owner = null;
            this.token = null;
            this.bytes = 0L;
        }

        private RetainedReservation(
                GlobalMemoryManager owner,
                GroupToken token,
                long bytes) {
            this.owner = owner;
            this.token = token;
            this.bytes = bytes;
        }

        void commit() {
            if (owner == null) return;
            committed = true;
        }

        @Override
        public void close() {
            if (owner == null) return;
            if (!closed) {
                closed = true;
                if (!committed) owner.releaseRetained(token, bytes);
            }
        }
    }

    static final class TemporaryLease implements AutoCloseable {

        private static final TemporaryLease NOOP = new TemporaryLease();

        private final GlobalMemoryManager owner;
        private final long bytes;
        private boolean closed;

        private TemporaryLease() {
            this.owner = null;
            this.bytes = 0L;
        }

        private TemporaryLease(GlobalMemoryManager owner, long bytes) {
            this.owner = owner;
            this.bytes = bytes;
        }

        @Override
        public void close() {
            if (owner == null) return;
            if (!closed) {
                closed = true;
                owner.releaseTemporary(bytes);
            }
        }
    }

    private static final class GroupReference
            extends PhantomReference<GeneratedGroup> {

        private final GroupToken token;

        private GroupReference(
                GeneratedGroup referent,
                ReferenceQueue<? super GeneratedGroup> queue,
                GroupToken token) {
            super(referent, queue);
            this.token = token;
        }
    }
}
