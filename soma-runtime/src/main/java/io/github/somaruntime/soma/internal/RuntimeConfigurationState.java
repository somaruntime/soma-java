package io.github.somaruntime.soma.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class RuntimeConfigurationState {

    private final AtomicReference<Snapshot> current = new AtomicReference<Snapshot>();

    Observation observe() {
        Snapshot snapshot = current.get();
        return snapshot == null
                ? Observation.unfrozen()
                : Observation.frozen(snapshot);
    }

    Snapshot configure(Snapshot candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!current.compareAndSet(null, candidate)) {
            throw alreadyFrozen();
        }
        return candidate;
    }

    Snapshot freezeDefault(Supplier<Snapshot> policy) {
        Objects.requireNonNull(policy, "policy");
        Snapshot existing = current.get();
        if (existing != null) {
            return existing;
        }
        Snapshot candidate = Objects.requireNonNull(policy.get(), "policy result");
        if (current.compareAndSet(null, candidate)) {
            return candidate;
        }
        return current.get();
    }

    private static IllegalStateException alreadyFrozen() {
        return new IllegalStateException(
                "[SOMA-0003] Runtime configuration is already frozen.");
    }

    static final class Snapshot {

        private final long memoryBudgetBytes;
        private final String policyIdentity;

        Snapshot(long memoryBudgetBytes, String policyIdentity) {
            if (memoryBudgetBytes <= 0L) {
                throw new IllegalArgumentException("memoryBudgetBytes must be positive");
            }
            this.memoryBudgetBytes = memoryBudgetBytes;
            this.policyIdentity = Objects.requireNonNull(policyIdentity, "policyIdentity");
        }

        long memoryBudgetBytes() {
            return memoryBudgetBytes;
        }

        String policyIdentity() {
            return policyIdentity;
        }
    }

    static final class Observation {

        private static final Observation UNFROZEN = new Observation(false, null);

        private final boolean frozen;
        private final Snapshot snapshot;

        private Observation(boolean frozen, Snapshot snapshot) {
            this.frozen = frozen;
            this.snapshot = snapshot;
        }

        static Observation unfrozen() {
            return UNFROZEN;
        }

        static Observation frozen(Snapshot snapshot) {
            return new Observation(true, snapshot);
        }

        boolean isFrozen() {
            return frozen;
        }

        Snapshot snapshot() {
            return snapshot;
        }
    }
}
