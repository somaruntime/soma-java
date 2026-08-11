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
import java.util.function.Consumer;

/** Machine-first FCFS + SPT constructive solver backed by SOMA runtime state. */
public final class SchedSolverImpl implements SchedSolver {
    @Override
    public SchedSolveResult solve(SchedModel model, SchedSolverConfig config) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(config, "config");
        if (config.validateModel()) {
            SchedModelValidator.validate(model);
        }

        long initializationStartedAt = System.nanoTime();
        RuntimeTables tables = new RuntimeTables(Soma.createGroup());
        initialize(model, tables);
        long initializationNanos = System.nanoTime() - initializationStartedAt;

        long dispatchStartedAt = System.nanoTime();
        MachineChoice machineChoice = new MachineChoice();
        WaitingChoice waitingChoice = new WaitingChoice();
        OperationCompletion completion = new OperationCompletion();
        long scheduled = 0L;
        long makespan = 0L;
        while (scheduled < model.operationCount()) {
            selectMachine(tables.machines, machineChoice);
            selectWaitingOperation(
                    tables.waiting, machineChoice.machineId, waitingChoice);
            OperationModel operationModel = model.operation(waitingChoice.operationId);
            require(!operationModel.processingOptions().isEmpty(),
                    "READY operation has no processing option");

            removeWaitingEntries(tables, operationModel);
            publishOperationResult(
                    tables.operations,
                    operationModel.operationId(),
                    machineChoice.machineId,
                    waitingChoice.processingTime,
                    machineChoice.availableTime,
                    completion);
            publishMachineResult(
                    tables.machines,
                    machineChoice.machineId,
                    operationModel.operationId(),
                    completion.completionTime);
            publishJobProgress(
                    model, tables.jobs, operationModel, completion.completionTime);
            OperationModel successor = successor(model, operationModel);
            if (successor != null) {
                releaseSuccessor(
                        tables,
                        successor,
                        completion.completionTime);
            }

            scheduled = Math.addExact(scheduled, 1L);
            makespan = Math.max(makespan, completion.completionTime);
        }

        require(tables.waiting.size() == 0, "waiting queue is not empty after solve");
        long dispatchNanos = System.nanoTime() - dispatchStartedAt;
        OperationResultQuery query = new OperationResultQueryImpl(tables.operations, makespan);
        SchedSolveResult result = new SchedSolveResult(
                SchedSolverStatus.FEASIBLE,
                query,
                initializationNanos,
                dispatchNanos);
        if (config.validateResult()) {
            SchedResultValidator.validate(model, result);
        }
        return result;
    }

    private static void selectMachine(
            MachineStateTable machines,
            MachineChoice choice) {
        choice.reset();
        machines.forEach(choice);
        require(choice.present, "no machine has a waiting operation");
    }

    private static void selectWaitingOperation(
            MachineWaitingOperationTable waiting,
            long machineId,
            WaitingChoice choice) {
        choice.reset();
        waiting.byMachineId(machineId).forEach(choice);
        require(choice.present, "selected machine has no waiting operation");
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
            OperationModel operation) {
        List<ProcessingOptionModel> options = operation.processingOptions();
        for (int index = 0; index < options.size(); index++) {
            ProcessingOptionModel option = options.get(index);
            RemoveResult removed = tables.waiting.remove(option.optionId());
            require(removed.removed() == 1, "waiting entry is missing");
            UpdateResult updated = tables.machines.update(option.machineId(), editor ->
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
            long machineAvailableTime,
            OperationCompletion completion) {
        UpdateResult result = operations.update(operationId, editor -> {
            require(editor.status() == OperationStatus.READY,
                    "waiting queue contains a non-READY operation");
            long startTime = Math.max(machineAvailableTime, editor.readyTime());
            long completionTime = Math.addExact(startTime, processingTime);
            editor.status(OperationStatus.SCHEDULED);
            editor.assignedMachineId(machineId);
            editor.processingTime(processingTime);
            editor.startTime(startTime);
            editor.completionTime(completionTime);
            completion.completionTime = completionTime;
        });
        require(result.matched() == 1, "operation state is missing");
    }

    private static void publishJobProgress(
            SchedModel model,
            JobStateTable jobs,
            OperationModel operation,
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
            RuntimeTables tables,
            OperationModel successor,
            long readyTime) {
        UpdateResult result = tables.operations.update(successor.operationId(), editor -> {
            editor.readyTime(readyTime);
            editor.status(OperationStatus.READY);
        });
        require(result.matched() == 1, "successor operation state is missing");

        List<ProcessingOptionModel> options = successor.processingOptions();
        for (int index = 0; index < options.size(); index++) {
            ProcessingOptionModel option = options.get(index);
            addWaitingEntry(tables.waiting, successor, option, readyTime);
            UpdateResult updated = tables.machines.update(option.machineId(), editor ->
                    editor.waitingOperationCount(Math.addExact(
                            editor.waitingOperationCount(), 1L)));
            require(updated.matched() == 1, "successor candidate machine is missing");
        }
    }

    private static OperationModel successor(SchedModel model, OperationModel operation) {
        List<OperationModel> operations = model.job(operation.jobId()).operations();
        int next = Math.toIntExact(operation.sequence()) + 1;
        return next < operations.size() ? operations.get(next) : null;
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

    /** Allocation-free machine minimum used in the hot dispatch loop. */
    private static final class MachineChoice implements Consumer<MachineStateTable.View> {
        boolean present;
        long machineId;
        long availableTime;

        void reset() {
            present = false;
        }

        @Override
        public void accept(MachineStateTable.View view) {
            if (view.waitingOperationCount() <= 0L) return;
            long candidateTime = view.availableTime();
            long candidateId = view.machineId();
            if (!present
                    || candidateTime < availableTime
                    || (candidateTime == availableTime && candidateId < machineId)) {
                present = true;
                machineId = candidateId;
                availableTime = candidateTime;
            }
        }
    }

    /** Allocation-free FCFS + SPT minimum over one machine's Index selection. */
    private static final class WaitingChoice
            implements Consumer<MachineWaitingOperationTable.View> {
        boolean present;
        long waitingEntryId;
        long readyTime;
        long processingTime;
        long jobId;
        long operationId;

        void reset() {
            present = false;
        }

        @Override
        public void accept(MachineWaitingOperationTable.View view) {
            long candidateReady = view.readyTime();
            long candidateProcessing = view.processingTime();
            long candidateJob = view.jobId();
            long candidateOperation = view.operationId();
            long candidateEntry = view.waitingEntryId();
            if (!present || compare(
                    candidateReady,
                    candidateProcessing,
                    candidateJob,
                    candidateOperation,
                    candidateEntry) < 0) {
                present = true;
                waitingEntryId = candidateEntry;
                readyTime = candidateReady;
                processingTime = candidateProcessing;
                jobId = candidateJob;
                operationId = candidateOperation;
            }
        }

        private int compare(
                long candidateReady,
                long candidateProcessing,
                long candidateJob,
                long candidateOperation,
                long candidateEntry) {
            int compared = Long.compare(candidateReady, readyTime);
            if (compared != 0) return compared;
            compared = Long.compare(candidateProcessing, processingTime);
            if (compared != 0) return compared;
            compared = Long.compare(candidateJob, jobId);
            if (compared != 0) return compared;
            compared = Long.compare(candidateOperation, operationId);
            if (compared != 0) return compared;
            return Long.compare(candidateEntry, waitingEntryId);
        }
    }

    private static final class OperationCompletion {
        long completionTime;
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
