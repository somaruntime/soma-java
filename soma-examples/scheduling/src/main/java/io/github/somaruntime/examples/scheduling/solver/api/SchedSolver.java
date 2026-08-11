package io.github.somaruntime.examples.scheduling.solver.api;

import io.github.somaruntime.examples.scheduling.modeling.SchedModel;

/** Public entry point for one FJSP solve. */
public interface SchedSolver {
    SchedSolveResult solve(SchedModel model, SchedSolverConfig config);
}
