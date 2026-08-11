package io.github.somaruntime.examples.scheduling.solver.api;

import java.util.Objects;

/** Immutable solve summary plus the operation-oriented result query API. */
public final class SchedSolveResult {
    private final SchedSolverStatus status;
    private final OperationResultQuery operations;
    private final long elapsedNanos;

    public SchedSolveResult(
            SchedSolverStatus status,
            OperationResultQuery operations,
            long elapsedNanos) {
        this.status = Objects.requireNonNull(status, "status");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.elapsedNanos = elapsedNanos;
    }

    public SchedSolverStatus status() { return status; }
    public OperationResultQuery operations() { return operations; }
    public long elapsedNanos() { return elapsedNanos; }
}
