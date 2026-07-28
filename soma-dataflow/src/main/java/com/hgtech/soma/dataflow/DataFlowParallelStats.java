package com.hgtech.soma.dataflow;

/** Immutable bounded-scheduler component of one Invocation. */
public final class DataFlowParallelStats {
    private final String schedulerFormulaIdentity;
    private final int tasks;
    private final int workers;
    private final int taskCurrent;
    private final int taskHighWater;
    private final int workerCurrent;
    private final int workerHighWater;

    DataFlowParallelStats(
            int tasks,
            int workers,
            int taskCurrent,
            int taskHighWater,
            int workerCurrent,
            int workerHighWater) {
        schedulerFormulaIdentity = MorselSchedulerFormula.IDENTITY;
        this.tasks = tasks;
        this.workers = workers;
        this.taskCurrent = taskCurrent;
        this.taskHighWater = taskHighWater;
        this.workerCurrent = workerCurrent;
        this.workerHighWater = workerHighWater;
    }

    public String schedulerFormulaIdentity() {
        return schedulerFormulaIdentity;
    }
    public int tasks() { return tasks; }
    public int workers() { return workers; }
    public int taskCurrent() { return taskCurrent; }
    public int taskHighWater() { return taskHighWater; }
    public int workerCurrent() { return workerCurrent; }
    public int workerHighWater() { return workerHighWater; }
}
