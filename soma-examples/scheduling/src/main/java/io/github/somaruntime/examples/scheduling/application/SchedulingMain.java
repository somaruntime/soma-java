package io.github.somaruntime.examples.scheduling.application;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import io.github.somaruntime.examples.scheduling.factory.StandardSchedModelFactory;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolveResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverConfig;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverStatus;
import io.github.somaruntime.examples.scheduling.solver.core.SchedSolverImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Runs the standard 100K-operation FJSP reference journey. */
public final class SchedulingMain {
    private SchedulingMain() {}

    public static void main(String[] args) throws Exception {
        Path configPath = args.length == 0 ? defaultConfigPath() : Paths.get(args[0]);
        SchedModelFactoryConfig factoryConfig = SchedModelFactoryConfig.load(configPath);
        SchedModel model = new StandardSchedModelFactory().create(factoryConfig);
        SchedSolveResult result = new SchedSolverImpl().solve(
                model, SchedSolverConfig.defaults());
        if (result.status() != SchedSolverStatus.FEASIBLE
                || result.operations().operationCount() != factoryConfig.operationCount()) {
            throw new AssertionError("standard scheduling journey is incomplete");
        }
        System.out.println("scheduling-reference: PASS");
        System.out.println("operations=" + result.operations().operationCount()
                + " makespan=" + result.operations().makespan()
                + " elapsedMs=" + result.elapsedNanos() / 1_000_000L);
    }

    private static Path defaultConfigPath() {
        Path repositoryPath = Paths.get(
                "soma-examples", "scheduling", "config", "fjsp-standard.properties");
        return Files.isRegularFile(repositoryPath)
                ? repositoryPath
                : Paths.get("config", "fjsp-standard.properties");
    }
}
