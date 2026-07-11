package com.hgtech.soma.runtime.generated;

import java.util.Objects;

/** Scoped controlled allocation admission used at the materialization publish boundary. */
public final class MaterializationAllocation {
    private static final ThreadLocal<Provider> CURRENT = new ThreadLocal<Provider>();

    private MaterializationAllocation() {
    }

    public interface Provider {
        boolean allow(String phase, long estimatedBytes, String path);
    }

    public static Scope installForCurrentThread(Provider provider) {
        Provider required = Objects.requireNonNull(provider, "provider");
        Provider previous = CURRENT.get();
        CURRENT.set(required);
        return new Scope(Thread.currentThread(), required, previous);
    }

    public static void preflight(String phase, long estimatedBytes, String path) {
        String requiredPhase = Objects.requireNonNull(phase, "phase");
        String requiredPath = Objects.requireNonNull(path, "path");
        if (estimatedBytes < 0L) {
            throw RuntimeFailures.internalInvariant(
                    "negative_materialization_allocation", requiredPath, requiredPhase);
        }
        Provider provider = CURRENT.get();
        if (provider == null) return;
        final boolean allowed;
        try {
            allowed = provider.allow(requiredPhase, estimatedBytes, requiredPath);
        } catch (RuntimeException failure) {
            throw RuntimeFailures.allocationFailure(
                    requiredPhase, estimatedBytes, requiredPath, failure);
        }
        if (!allowed) {
            throw RuntimeFailures.allocationFailure(
                    requiredPhase, estimatedBytes, requiredPath, null);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final Provider installed;
        private final Provider previous;
        private boolean closed;

        private Scope(Thread owner, Provider installed, Provider previous) {
            this.owner = owner;
            this.installed = installed;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != owner || CURRENT.get() != installed) {
                throw RuntimeFailures.internalInvariant(
                        "materialization_allocation_scope", "materialize", "scope.close");
            }
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
            closed = true;
        }
    }
}
