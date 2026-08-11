package io.github.somaruntime.examples.scheduling.solver.api;

/** Solver-level policy. Dispatch rules are deliberately fixed to FCFS + SPT. */
public final class SchedSolverConfig {
    private final boolean validateModel;
    private final boolean validateResult;

    public SchedSolverConfig(boolean validateModel, boolean validateResult) {
        this.validateModel = validateModel;
        this.validateResult = validateResult;
    }

    public static SchedSolverConfig defaults() {
        return new SchedSolverConfig(true, true);
    }

    public boolean validateModel() {
        return validateModel;
    }

    public boolean validateResult() {
        return validateResult;
    }
}
