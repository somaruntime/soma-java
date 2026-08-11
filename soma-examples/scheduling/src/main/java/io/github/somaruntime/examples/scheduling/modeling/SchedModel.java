package io.github.somaruntime.examples.scheduling.modeling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable application model consumed by the scheduling algorithm. */
public final class SchedModel {
    private final List<MachineModel> machines;
    private final List<JobModel> jobs;
    private final Map<Long, MachineModel> machinesById;
    private final Map<Long, JobModel> jobsById;
    private final Map<Long, OperationModel> operationsById;
    private final long processingOptionCount;

    public SchedModel(List<MachineModel> machines, List<JobModel> jobs) {
        this.machines = Collections.unmodifiableList(
                new ArrayList<MachineModel>(Objects.requireNonNull(machines, "machines")));
        this.jobs = Collections.unmodifiableList(
                new ArrayList<JobModel>(Objects.requireNonNull(jobs, "jobs")));

        Map<Long, MachineModel> machineIndex = new HashMap<Long, MachineModel>();
        for (MachineModel machine : this.machines) {
            machineIndex.put(machine.machineId(), machine);
        }
        this.machinesById = Collections.unmodifiableMap(machineIndex);

        Map<Long, JobModel> jobIndex = new HashMap<Long, JobModel>();
        Map<Long, OperationModel> operationIndex = new HashMap<Long, OperationModel>();
        long optionCount = 0L;
        for (JobModel job : this.jobs) {
            jobIndex.put(job.jobId(), job);
            for (OperationModel operation : job.operations()) {
                operationIndex.put(operation.operationId(), operation);
                optionCount = Math.addExact(optionCount, operation.processingOptions().size());
            }
        }
        this.jobsById = Collections.unmodifiableMap(jobIndex);
        this.operationsById = Collections.unmodifiableMap(operationIndex);
        this.processingOptionCount = optionCount;
    }

    public List<MachineModel> machines() { return machines; }
    public List<JobModel> jobs() { return jobs; }
    public long machineCount() { return machines.size(); }
    public long jobCount() { return jobs.size(); }
    public long operationCount() { return operationsById.size(); }
    public long processingOptionCount() { return processingOptionCount; }

    public MachineModel machine(long machineId) {
        MachineModel value = machinesById.get(machineId);
        if (value == null) throw new IllegalArgumentException("unknown machineId: " + machineId);
        return value;
    }

    public JobModel job(long jobId) {
        JobModel value = jobsById.get(jobId);
        if (value == null) throw new IllegalArgumentException("unknown jobId: " + jobId);
        return value;
    }

    public OperationModel operation(long operationId) {
        OperationModel value = operationsById.get(operationId);
        if (value == null) {
            throw new IllegalArgumentException("unknown operationId: " + operationId);
        }
        return value;
    }
}
