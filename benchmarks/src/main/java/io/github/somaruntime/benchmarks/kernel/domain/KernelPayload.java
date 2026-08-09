package io.github.somaruntime.benchmarks.kernel.domain;

/** Opaque application object used to qualify ordinary-reference storage. */
public final class KernelPayload {
    private final int identity;

    public KernelPayload(int identity) {
        this.identity = identity;
    }

    public int identity() {
        return identity;
    }
}
