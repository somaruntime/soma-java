package io.github.somaruntime.examples.scheduling.solver.api;

import java.util.Objects;

/** Immutable solve summary plus the operation-oriented result query API. */
public final class SchedSolveResult {
    private final SchedSolverStatus status;
    private final OperationResultQuery operations;
    private final long initializationNanos;
    private final long dispatchNanos;

    public SchedSolveResult(
            SchedSolverStatus status,
            OperationResultQuery operations,
            long initializationNanos,
            long dispatchNanos) {
        this.status = Objects.requireNonNull(status, "status");
        this.operations = Objects.requireNonNull(operations, "operations");
        if (initializationNanos < 0L || dispatchNanos < 0L) {
            throw new IllegalArgumentException("solve phase duration is negative");
        }
        this.initializationNanos = initializationNanos;
        this.dispatchNanos = dispatchNanos;
    }

    public SchedSolverStatus status() { return status; }
    public OperationResultQuery operations() { return operations; }
    public long initializationNanos() { return initializationNanos; }
    public long dispatchNanos() { return dispatchNanos; }
    public long elapsedNanos() { return Math.addExact(initializationNanos, dispatchNanos); }
}
