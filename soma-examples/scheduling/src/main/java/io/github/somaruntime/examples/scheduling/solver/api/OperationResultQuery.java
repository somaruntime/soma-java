package io.github.somaruntime.examples.scheduling.solver.api;

import java.util.List;
import java.util.Optional;

/** Read-only query surface over the final operation schedule. */
public interface OperationResultQuery {
    Optional<OperationResult> find(long operationId);

    List<OperationResult> byJob(long jobId);

    List<OperationResult> byMachine(long machineId);

    long operationCount();

    long makespan();
}
