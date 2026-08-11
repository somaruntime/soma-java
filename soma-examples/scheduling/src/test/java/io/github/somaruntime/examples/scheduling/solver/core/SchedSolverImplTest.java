package io.github.somaruntime.examples.scheduling.solver.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.somaruntime.examples.scheduling.modeling.JobModel;
import io.github.somaruntime.examples.scheduling.modeling.MachineModel;
import io.github.somaruntime.examples.scheduling.modeling.OperationModel;
import io.github.somaruntime.examples.scheduling.modeling.ProcessingOptionModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.solver.api.OperationResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolveResult;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverConfig;
import io.github.somaruntime.examples.scheduling.solver.api.SchedSolverStatus;
import io.github.somaruntime.examples.scheduling.validation.SchedResultValidator;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class SchedSolverImplTest {
    @Test
    void followsMachineFirstFcfsThenSpt() {
        OperationModel slowerOnMachineOne = new OperationModel(
                1L, 1L, 0L, Arrays.asList(
                        new ProcessingOptionModel(1L, 1L, 10L),
                        new ProcessingOptionModel(2L, 2L, 2L)));
        OperationModel fasterOnMachineOne = new OperationModel(
                2L, 2L, 0L, Arrays.asList(
                        new ProcessingOptionModel(3L, 1L, 3L),
                        new ProcessingOptionModel(4L, 2L, 7L)));
        SchedModel model = new SchedModel(
                Arrays.asList(new MachineModel(1L), new MachineModel(2L)),
                Arrays.asList(
                        new JobModel(1L, Collections.singletonList(slowerOnMachineOne)),
                        new JobModel(2L, Collections.singletonList(fasterOnMachineOne))));

        SchedSolveResult result = new SchedSolverImpl().solve(
                model, SchedSolverConfig.defaults());

        assertEquals(SchedSolverStatus.FEASIBLE, result.status());
        OperationResult first = result.operations().find(2L).get();
        OperationResult second = result.operations().find(1L).get();
        assertEquals(1L, first.machineId());
        assertEquals(0L, first.startTime());
        assertEquals(3L, first.completionTime());
        assertEquals(2L, second.machineId());
        assertEquals(0L, second.startTime());
        assertEquals(2L, second.completionTime());
        assertEquals(3L, result.operations().makespan());
        SchedResultValidator.validate(model, result);
    }

    @Test
    void releasesSuccessorAtPredecessorCompletion() {
        OperationModel first = new OperationModel(
                10L, 5L, 0L,
                Collections.singletonList(new ProcessingOptionModel(10L, 1L, 4L)));
        OperationModel second = new OperationModel(
                11L, 5L, 1L,
                Collections.singletonList(new ProcessingOptionModel(11L, 1L, 6L)));
        SchedModel model = new SchedModel(
                Collections.singletonList(new MachineModel(1L)),
                Collections.singletonList(new JobModel(5L, Arrays.asList(first, second))));

        SchedSolveResult result = new SchedSolverImpl().solve(
                model, SchedSolverConfig.defaults());

        OperationResult successor = result.operations().find(11L).get();
        assertEquals(4L, successor.readyTime());
        assertEquals(4L, successor.startTime());
        assertEquals(10L, successor.completionTime());
    }

    @Test
    void appliesFcfsBeforeSptWhenReadyTimesDiffer() {
        OperationModel machineOneBlocker = operation(1L, 1L, 0L, 1L, 20L);
        OperationModel earlyPredecessor = operation(2L, 2L, 0L, 2L, 5L);
        OperationModel earlyReadySlow = operation(3L, 2L, 1L, 1L, 10L);
        OperationModel latePredecessor = operation(4L, 3L, 0L, 3L, 10L);
        OperationModel lateReadyFast = operation(5L, 3L, 1L, 1L, 1L);
        SchedModel model = new SchedModel(
                Arrays.asList(
                        new MachineModel(1L),
                        new MachineModel(2L),
                        new MachineModel(3L)),
                Arrays.asList(
                        new JobModel(1L, Collections.singletonList(machineOneBlocker)),
                        new JobModel(2L, Arrays.asList(earlyPredecessor, earlyReadySlow)),
                        new JobModel(3L, Arrays.asList(latePredecessor, lateReadyFast))));

        SchedSolveResult result = new SchedSolverImpl().solve(
                model, SchedSolverConfig.defaults());

        OperationResult early = result.operations().find(3L).get();
        OperationResult late = result.operations().find(5L).get();
        assertEquals(5L, early.readyTime());
        assertEquals(10L, late.readyTime());
        assertEquals(20L, early.startTime());
        assertEquals(30L, late.startTime());
    }

    private static OperationModel operation(
            long operationId,
            long jobId,
            long sequence,
            long machineId,
            long processingTime) {
        return new OperationModel(
                operationId,
                jobId,
                sequence,
                Collections.singletonList(new ProcessingOptionModel(
                        operationId, machineId, processingTime)));
    }
}
