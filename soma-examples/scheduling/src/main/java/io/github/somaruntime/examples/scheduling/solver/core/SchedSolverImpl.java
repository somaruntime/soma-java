package io.github.somaruntime.examples.scheduling.solver.core;

import io.github.somaruntime.examples.scheduling.modeling.JobModel;
import io.github.somaruntime.examples.scheduling.modeling.MachineModel;
import io.github.somaruntime.examples.scheduling.modeling.OperationModel;
import io.github.somaruntime.examples.scheduling.modeling.ProcessingOptionModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModelValidator;
import io.github.somaruntime.examples.scheduling.runtime.JobState;
import io.github.somaruntime.examples.scheduling.runtime.JobStateTable;
import io.github.somaruntime.examples.scheduling.runtime.MachineState;
import io.github.somaruntime.examples.scheduling.runtime.MachineStateTable;
import io.github.somaruntime.examples.scheduling.runtime.MachineWaitingOperation;
import io.github.somaruntime.examples.scheduling.runtime.MachineWaitingOperationTable;
import io.github.somaruntime.examples.scheduling.runtime.OperationState;
import io.github.somaruntime.examples.scheduling.runtime.OperationStateTable;
import io.github.somaruntime.examples.scheduling.runtime.Soma;
import io.github.somaruntime.examples.scheduling.runtime.SomaGroup;
import io.github.somaruntime.examples.scheduling.runtime.schema.JobStatus;
import io.github.somaruntime.examples.scheduling.runtime.schema.OperationStatus;
import io.github.somaruntime.examples.scheduling.solver.api.OperationResult;
import io.github.somaruntime.examples.scheduling.solver.api.OperationResultQuery;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolveResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolver;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverConfig;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverStatus;
import io.github.somaruntime.examples.scheduling.validation.SchedResultValidator;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Machine-first FCFS + SPT constructive solver backed by SOMA runtime state. */
public final class SchedSolverImpl implements SchedSolver {
    @Override
    public SchedSolveResult solve(SchedModel model, SchedSolverConfig config) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(config, "config");
        if (config.validateModel()) {
            SchedModelValidator.validate(model);
        }

        long startedAt = System.nanoTime();
        RuntimeTables tables = new RuntimeTables(Soma.createGroup());
        initialize(model, tables);

        long scheduled = 0L;
        long makespan = 0L;
        while (scheduled < model.operationCount()) {
            MachineState machine = selectMachine(tables.machines);
            MachineWaitingOperation selected = selectWaitingOperation(
                    tables.waiting, machine.machineId());
            require(selected.machineId() == machine.machineId(),
                    "waiting selection belongs to a different machine");
            OperationState operation = tables.operations.get(selected.operationId());
            require(operation.status() == OperationStatus.READY,
                    "waiting queue contains a non-READY operation");

            List<MachineWaitingOperation> waitingEntries = tables.waiting
                    .byOperationId(operation.operationId())
                    .toList();
            require(!waitingEntries.isEmpty(), "READY operation has no waiting entries");
            require(waitingEntries.size()
                            == model.operation(operation.operationId())
                                    .processingOptions().size(),
                    "waiting entries do not match processing options");

            long startTime = Math.max(machine.availableTime(), operation.readyTime());
            long completionTime = Math.addExact(startTime, selected.processingTime());

            removeWaitingEntries(tables, waitingEntries);
            publishMachineResult(
                    tables.machines,
                    selected.machineId(),
                    operation.operationId(),
                    completionTime);
            publishOperationResult(
                    tables.operations,
                    operation.operationId(),
                    selected.machineId(),
                    selected.processingTime(),
                    startTime,
                    completionTime);
            publishJobProgress(model, tables.jobs, operation, completionTime);
            if (operation.hasSuccessor()) {
                releaseSuccessor(
                        model,
                        tables,
                        operation.successorOperationId(),
                        completionTime);
            }

            scheduled = Math.addExact(scheduled, 1L);
            makespan = Math.max(makespan, completionTime);
        }

        require(tables.waiting.size() == 0, "waiting queue is not empty after solve");
        OperationResultQuery query = new OperationResultQueryImpl(tables.operations, makespan);
        SchedSolveResult result = new SchedSolveResult(
                SchedSolverStatus.FEASIBLE,
                query,
                System.nanoTime() - startedAt);
        if (config.validateResult()) {
            SchedResultValidator.validate(model, result);
        }
        return result;
    }

    private static MachineState selectMachine(MachineStateTable machines) {
        Optional<MachineState> selected = machines
                .filter(machines.waitingOperationCount.gt(0L))
                .top(1L, machines.availableTime.asc().then(machines.machineId.asc()))
                .findFirst();
        require(selected.isPresent(), "no machine has a waiting operation");
        return selected.get();
    }

    private static MachineWaitingOperation selectWaitingOperation(
            MachineWaitingOperationTable waiting,
            long machineId) {
        Optional<MachineWaitingOperation> selected = waiting.byMachineId(machineId)
                .top(1L, waiting.readyTime.asc()
                        .then(waiting.processingTime.asc())
                        .then(waiting.jobId.asc())
                        .then(waiting.operationId.asc())
                        .then(waiting.waitingEntryId.asc()))
                .findFirst();
        require(selected.isPresent(), "selected machine has no waiting operation");
        return selected.get();
    }

    private static void initialize(SchedModel model, RuntimeTables tables) {
        tables.machines.reserve(checkedStructureCount(model.machineCount(), "machine count"));
        tables.jobs.reserve(checkedStructureCount(model.jobCount(), "job count"));
        tables.operations.reserve(checkedStructureCount(
                model.operationCount(), "operation count"));
        tables.waiting.reserve(initialWaitingCount(model));

        Map<Long, Long> waitingByMachine = new HashMap<Long, Long>();
        for (MachineModel machine : model.machines()) {
            waitingByMachine.put(machine.machineId(), 0L);
        }

        for (JobModel job : model.jobs()) {
            tables.jobs.add(new JobState(job.jobId(), 0L, 0L, JobStatus.ACTIVE));
            for (int index = 0; index < job.operations().size(); index++) {
                OperationModel operation = job.operations().get(index);
                boolean first = index == 0;
                boolean hasSuccessor = index + 1 < job.operations().size();
                long successorId = hasSuccessor
                        ? job.operations().get(index + 1).operationId()
                        : 0L;
                tables.operations.add(new OperationState(
                        operation.operationId(),
                        operation.jobId(),
                        operation.sequence(),
                        successorId,
                        hasSuccessor,
                        0L,
                        first ? OperationStatus.READY : OperationStatus.BLOCKED,
                        0L,
                        0L,
                        0L,
                        0L));
            }

            OperationModel first = job.operations().get(0);
            for (ProcessingOptionModel option : first.processingOptions()) {
                addWaitingEntry(tables.waiting, first, option, 0L);
                waitingByMachine.put(
                        option.machineId(),
                        Math.addExact(waitingByMachine.get(option.machineId()), 1L));
            }
        }

        for (MachineModel machine : model.machines()) {
            tables.machines.add(new MachineState(
                    machine.machineId(),
                    0L,
                    0L,
                    0L,
                    waitingByMachine.get(machine.machineId())));
        }
    }

    private static int initialWaitingCount(SchedModel model) {
        long count = 0L;
        for (JobModel job : model.jobs()) {
            count = Math.addExact(
                    count,
                    job.operations().get(0).processingOptions().size());
        }
        return checkedStructureCount(count, "initial waiting count");
    }

    private static int checkedStructureCount(long value, String name) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds SOMA structural capacity");
        }
        return (int) value;
    }

    private static void removeWaitingEntries(
            RuntimeTables tables,
            List<MachineWaitingOperation> entries) {
        for (MachineWaitingOperation entry : entries) {
            RemoveResult removed = tables.waiting.remove(entry.waitingEntryId());
            require(removed.removed() == 1, "waiting entry is missing");
            UpdateResult updated = tables.machines.update(entry.machineId(), editor ->
                    editor.waitingOperationCount(Math.subtractExact(
                            editor.waitingOperationCount(), 1L)));
            require(updated.matched() == 1, "candidate machine is missing");
        }
    }

    private static void publishMachineResult(
            MachineStateTable machines,
            long machineId,
            long operationId,
            long completionTime) {
        UpdateResult result = machines.update(machineId, editor -> {
            editor.availableTime(completionTime);
            editor.lastOperationId(operationId);
            editor.scheduledOperationCount(
                    Math.addExact(editor.scheduledOperationCount(), 1L));
        });
        require(result.matched() == 1, "selected machine is missing");
    }

    private static void publishOperationResult(
            OperationStateTable operations,
            long operationId,
            long machineId,
            long processingTime,
            long startTime,
            long completionTime) {
        UpdateResult result = operations.update(operationId, editor -> {
            editor.status(OperationStatus.SCHEDULED);
            editor.assignedMachineId(machineId);
            editor.processingTime(processingTime);
            editor.startTime(startTime);
            editor.completionTime(completionTime);
        });
        require(result.matched() == 1, "operation state is missing");
    }

    private static void publishJobProgress(
            SchedModel model,
            JobStateTable jobs,
            OperationState operation,
            long completionTime) {
        long operationCount = model.job(operation.jobId()).operations().size();
        UpdateResult result = jobs.update(operation.jobId(), editor -> {
            long completed = Math.addExact(editor.completedOperationCount(), 1L);
            editor.completedOperationCount(completed);
            editor.completionTime(completionTime);
            if (completed == operationCount) {
                editor.status(JobStatus.COMPLETED);
            }
        });
        require(result.matched() == 1, "job state is missing");
    }

    private static void releaseSuccessor(
            SchedModel model,
            RuntimeTables tables,
            long successorOperationId,
            long readyTime) {
        OperationModel successor = model.operation(successorOperationId);
        UpdateResult result = tables.operations.update(successorOperationId, editor -> {
            editor.readyTime(readyTime);
            editor.status(OperationStatus.READY);
        });
        require(result.matched() == 1, "successor operation state is missing");

        for (ProcessingOptionModel option : successor.processingOptions()) {
            addWaitingEntry(tables.waiting, successor, option, readyTime);
            UpdateResult updated = tables.machines.update(option.machineId(), editor ->
                    editor.waitingOperationCount(Math.addExact(
                            editor.waitingOperationCount(), 1L)));
            require(updated.matched() == 1, "successor candidate machine is missing");
        }
    }

    private static void addWaitingEntry(
            MachineWaitingOperationTable waiting,
            OperationModel operation,
            ProcessingOptionModel option,
            long readyTime) {
        waiting.add(new MachineWaitingOperation(
                option.optionId(),
                option.machineId(),
                operation.operationId(),
                operation.jobId(),
                readyTime,
                option.processingTime()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class RuntimeTables {
        final MachineStateTable machines;
        final JobStateTable jobs;
        final OperationStateTable operations;
        final MachineWaitingOperationTable waiting;

        RuntimeTables(SomaGroup group) {
            machines = group.machineStateTable();
            jobs = group.jobStateTable();
            operations = group.operationStateTable();
            waiting = group.machineWaitingOperationTable();
        }
    }

    private static final class OperationResultQueryImpl implements OperationResultQuery {
        private static final Comparator<OperationState> BY_JOB_SEQUENCE =
                Comparator.comparingLong((OperationState state) -> state.sequence());
        private static final Comparator<OperationState> BY_MACHINE_TIME =
                Comparator.comparingLong((OperationState state) -> state.startTime())
                        .thenComparingLong(state -> state.operationId());

        private final OperationStateTable operations;
        private final long makespan;

        OperationResultQueryImpl(OperationStateTable operations, long makespan) {
            this.operations = operations;
            this.makespan = makespan;
        }

        @Override
        public Optional<OperationResult> find(long operationId) {
            Optional<OperationState> found = operations.find(operationId);
            return found.isPresent()
                    ? Optional.of(toResult(found.get()))
                    : Optional.empty();
        }

        @Override
        public List<OperationResult> byJob(long jobId) {
            List<OperationState> states = operations.byJobId(jobId).toList();
            Collections.sort(states, BY_JOB_SEQUENCE);
            return toResults(states);
        }

        @Override
        public List<OperationResult> byMachine(long machineId) {
            List<OperationState> states = operations
                    .filter(operations.assignedMachineId.eq(machineId))
                    .toList();
            Collections.sort(states, BY_MACHINE_TIME);
            return toResults(states);
        }

        @Override
        public long operationCount() {
            return operations.count();
        }

        @Override
        public long makespan() {
            return makespan;
        }

        private static List<OperationResult> toResults(List<OperationState> states) {
            List<OperationResult> results = new ArrayList<OperationResult>(states.size());
            for (OperationState state : states) {
                results.add(toResult(state));
            }
            return Collections.unmodifiableList(results);
        }

        private static OperationResult toResult(OperationState state) {
            return new OperationResult(
                    state.operationId(),
                    state.jobId(),
                    state.sequence(),
                    state.readyTime(),
                    state.assignedMachineId(),
                    state.processingTime(),
                    state.startTime(),
                    state.completionTime());
        }
    }
}
