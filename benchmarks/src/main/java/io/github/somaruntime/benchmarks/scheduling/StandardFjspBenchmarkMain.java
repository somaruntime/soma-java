package io.github.somaruntime.benchmarks.scheduling;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import io.github.somaruntime.examples.scheduling.factory.StandardSchedModelFactory;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModelValidator;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolveResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverConfig;
import io.github.somaruntime.examples.scheduling.solver.core.SchedSolverImpl;
import io.github.somaruntime.examples.scheduling.validation.SchedResultValidator;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Fresh-Group warmup and measurement harness for the standard 100K FJSP journey. */
public final class StandardFjspBenchmarkMain {
    private StandardFjspBenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        Path config = args.length == 0
                ? Paths.get("soma-examples/scheduling/config/fjsp-standard.properties")
                : Paths.get(args[0]);
        int warmups = Integer.getInteger("soma.scheduling.warmups", 2);
        int samples = Integer.getInteger("soma.scheduling.samples", 5);
        require(warmups >= 0 && warmups <= 10, "warmups outside [0, 10]");
        require(samples > 0 && samples <= 11 && (samples & 1) == 1,
                "samples must be an odd value in [1, 11]");

        long factoryStartedAt = System.nanoTime();
        SchedModel model = new StandardSchedModelFactory().create(
                SchedModelFactoryConfig.load(config));
        long factoryNanos = System.nanoTime() - factoryStartedAt;
        SchedModelValidator.validate(model);

        SchedSolverImpl solver = new SchedSolverImpl();
        SchedSolverConfig solveConfig = new SchedSolverConfig(false, false);
        for (int index = 0; index < warmups; index++) {
            solver.solve(model, solveConfig);
        }

        long[] initialization = new long[samples];
        long[] dispatch = new long[samples];
        SchedSolveResult last = null;
        for (int index = 0; index < samples; index++) {
            last = solver.solve(model, solveConfig);
            initialization[index] = last.initializationNanos();
            dispatch[index] = last.dispatchNanos();
        }
        SchedResultValidator.validate(model, last);
        Arrays.sort(initialization);
        Arrays.sort(dispatch);

        int middle = samples >>> 1;
        System.out.println("SCHEDULING_FJSP"
                + " operations=" + last.operations().operationCount()
                + " makespan=" + last.operations().makespan()
                + " factoryNanos=" + factoryNanos
                + " initializationMedianNanos=" + initialization[middle]
                + " dispatchMedianNanos=" + dispatch[middle]
                + " warmups=" + warmups
                + " samples=" + samples
                + " correctness=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
