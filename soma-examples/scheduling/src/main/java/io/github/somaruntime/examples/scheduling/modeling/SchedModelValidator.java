package io.github.somaruntime.examples.scheduling.modeling;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Validates immutable problem definitions before runtime state is created. */
public final class SchedModelValidator {
    private SchedModelValidator() {}

    public static void validate(SchedModel model) {
        validate(model, null);
    }

    public static void validate(
            SchedModel model,
            SchedModelFactoryConfig expected) {
        Objects.requireNonNull(model, "model");
        Set<Long> machineIds = new HashSet<Long>();
        for (MachineModel machine : model.machines()) {
            require(machine.machineId() > 0L, "machineId must be positive");
            require(machineIds.add(machine.machineId()), "duplicate machineId");
        }
        require(!machineIds.isEmpty(), "at least one machine is required");

        Set<Long> jobIds = new HashSet<Long>();
        Set<Long> operationIds = new HashSet<Long>();
        Set<Long> optionIds = new HashSet<Long>();
        long operations = 0L;
        long options = 0L;
        for (JobModel job : model.jobs()) {
            require(job.jobId() > 0L, "jobId must be positive");
            require(jobIds.add(job.jobId()), "duplicate jobId");
            require(!job.operations().isEmpty(), "job must contain operations");
            long expectedSequence = 0L;
            for (OperationModel operation : job.operations()) {
                require(operation.operationId() > 0L, "operationId must be positive");
                require(operationIds.add(operation.operationId()), "duplicate operationId");
                require(operation.jobId() == job.jobId(), "operation belongs to wrong job");
                require(operation.sequence() == expectedSequence++, "operation sequence is not dense");
                require(!operation.processingOptions().isEmpty(),
                        "operation must contain processing options");
                Set<Long> candidateMachines = new HashSet<Long>();
                for (ProcessingOptionModel option : operation.processingOptions()) {
                    require(option.optionId() > 0L, "optionId must be positive");
                    require(optionIds.add(option.optionId()), "duplicate optionId");
                    require(machineIds.contains(option.machineId()), "unknown candidate machine");
                    require(candidateMachines.add(option.machineId()),
                            "duplicate candidate machine for operation");
                    require(option.processingTime() > 0L,
                            "processingTime must be positive");
                    options = Math.addExact(options, 1L);
                }
                operations = Math.addExact(operations, 1L);
            }
        }

        require(operations == model.operationCount(), "operation index is incomplete");
        require(options == model.processingOptionCount(), "option count is inconsistent");
        if (expected != null) {
            require(model.jobCount() == expected.jobCount(), "unexpected job count");
            require(model.machineCount() == expected.machineCount(), "unexpected machine count");
            require(model.operationCount() == expected.operationCount(),
                    "unexpected operation count");
            require(model.processingOptionCount() == expected.processingOptionCount(),
                    "unexpected processing-option count");
            for (JobModel job : model.jobs()) {
                require(job.operations().size() == expected.operationsPerJob(),
                        "unexpected operations per job");
                for (OperationModel operation : job.operations()) {
                    require(operation.processingOptions().size()
                                    == expected.candidatesPerOperation(),
                            "unexpected candidates per operation");
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
