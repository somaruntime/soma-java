package io.github.somaruntime.examples.scheduling.validation;

import io.github.somaruntime.examples.scheduling.modeling.JobModel;
import io.github.somaruntime.examples.scheduling.modeling.MachineModel;
import io.github.somaruntime.examples.scheduling.modeling.OperationModel;
import io.github.somaruntime.examples.scheduling.modeling.ProcessingOptionModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.solver.api.OperationResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolveResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small, independent feasibility checker for the scheduling result. */
public final class SchedResultValidator {
    private SchedResultValidator() {}

    public static void validate(SchedModel model, SchedSolveResult result) {
        require(result.status() == SchedSolverStatus.FEASIBLE, "result is not feasible");
        require(result.operations().operationCount() == model.operationCount(),
                "operation count is incomplete");

        Set<Long> seen = new HashSet<Long>();
        long maximumCompletion = 0L;
        for (JobModel job : model.jobs()) {
            List<OperationResult> jobResult = result.operations().byJob(job.jobId());
            require(jobResult.size() == job.operations().size(), "job result is incomplete");
            long predecessorCompletion = 0L;
            for (int index = 0; index < jobResult.size(); index++) {
                OperationModel operation = job.operations().get(index);
                OperationResult scheduled = jobResult.get(index);
                require(scheduled.operationId() == operation.operationId(),
                        "operation order is incorrect");
                require(scheduled.sequence() == operation.sequence(),
                        "operation sequence is incorrect");
                require(seen.add(scheduled.operationId()), "operation is duplicated");
                require(scheduled.readyTime() == predecessorCompletion,
                        "ready time is not predecessor completion");
                require(scheduled.startTime() >= scheduled.readyTime(),
                        "operation starts before it is ready");
                require(scheduled.completionTime() == Math.addExact(
                                scheduled.startTime(), scheduled.processingTime()),
                        "completion time is inconsistent");
                require(matchesOption(operation, scheduled),
                        "machine or processing time is not a candidate option");
                require(result.operations().find(scheduled.operationId()).isPresent(),
                        "point query cannot find a scheduled operation");
                predecessorCompletion = scheduled.completionTime();
                maximumCompletion = Math.max(maximumCompletion, predecessorCompletion);
            }
        }

        for (MachineModel machine : model.machines()) {
            long previousCompletion = 0L;
            for (OperationResult operation : result.operations().byMachine(machine.machineId())) {
                require(operation.startTime() >= previousCompletion,
                        "machine processes overlapping operations");
                previousCompletion = operation.completionTime();
            }
        }
        require(seen.size() == model.operationCount(), "not all operations were validated");
        require(result.operations().makespan() == maximumCompletion,
                "makespan is inconsistent");
    }

    private static boolean matchesOption(
            OperationModel operation,
            OperationResult result) {
        for (ProcessingOptionModel option : operation.processingOptions()) {
            if (option.machineId() == result.machineId()
                    && option.processingTime() == result.processingTime()) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
