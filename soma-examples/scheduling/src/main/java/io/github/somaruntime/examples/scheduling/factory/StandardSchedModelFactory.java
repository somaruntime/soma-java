package io.github.somaruntime.examples.scheduling.factory;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import io.github.somaruntime.examples.scheduling.modeling.JobModel;
import io.github.somaruntime.examples.scheduling.modeling.MachineModel;
import io.github.somaruntime.examples.scheduling.modeling.OperationModel;
import io.github.somaruntime.examples.scheduling.modeling.ProcessingOptionModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModelValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic factory for the standard 100K-operation FJSP shape. */
public final class StandardSchedModelFactory implements SchedModelFactory {
    @Override
    public SchedModel create(SchedModelFactoryConfig config) {
        Random random = new Random(config.randomSeed());
        List<MachineModel> machines = new ArrayList<MachineModel>(config.machineCount());
        for (int machine = 0; machine < config.machineCount(); machine++) {
            machines.add(new MachineModel(machine + 1L));
        }

        List<JobModel> jobs = new ArrayList<JobModel>(config.jobCount());
        long operationId = 1L;
        long optionId = 1L;
        long processingRange = Math.addExact(
                Math.subtractExact(
                        config.maximumProcessingTime(),
                        config.minimumProcessingTime()),
                1L);
        for (int jobIndex = 0; jobIndex < config.jobCount(); jobIndex++) {
            long jobId = jobIndex + 1L;
            List<OperationModel> operations =
                    new ArrayList<OperationModel>(config.operationsPerJob());
            for (int sequence = 0; sequence < config.operationsPerJob(); sequence++) {
                List<ProcessingOptionModel> options =
                        new ArrayList<ProcessingOptionModel>(config.candidatesPerOperation());
                boolean[] selectedMachines = new boolean[config.machineCount()];
                for (int candidate = 0;
                        candidate < config.candidatesPerOperation();
                        candidate++) {
                    int machineIndex;
                    do {
                        machineIndex = random.nextInt(config.machineCount());
                    } while (selectedMachines[machineIndex]);
                    selectedMachines[machineIndex] = true;
                    long processingTime = Math.addExact(
                            config.minimumProcessingTime(),
                            Math.floorMod(random.nextLong(), processingRange));
                    options.add(new ProcessingOptionModel(
                            optionId++, machineIndex + 1L, processingTime));
                }
                operations.add(new OperationModel(
                        operationId++, jobId, sequence, options));
            }
            jobs.add(new JobModel(jobId, operations));
        }

        SchedModel model = new SchedModel(machines, jobs);
        SchedModelValidator.validate(model, config);
        return model;
    }
}
