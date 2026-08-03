package io.github.somaruntime.examples.dispatch.domain;

/** Business lifecycle for a pending dispatch request. */
public enum DispatchStatus {
    READY,
    ASSIGNED,
    COMPLETED,
    REJECTED
}
