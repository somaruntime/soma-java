package io.github.somaruntime.examples.scheduling.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.somaruntime.examples.scheduling.configuration.SchedModelFactoryConfig;
import io.github.somaruntime.examples.scheduling.modeling.OperationModel;
import io.github.somaruntime.examples.scheduling.modeling.SchedModel;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class StandardSchedModelFactoryTest {
    @Test
    void createsDeterministicValidatedFjspInput() {
        SchedModelFactoryConfig config = new SchedModelFactoryConfig(
                4, 5, 6, 3, 41L, 2L, 20L);

        SchedModel first = new StandardSchedModelFactory().create(config);
        SchedModel second = new StandardSchedModelFactory().create(config);

        assertEquals(4L, first.jobCount());
        assertEquals(20L, first.operationCount());
        assertEquals(60L, first.processingOptionCount());
        OperationModel firstOperation = first.jobs().get(0).operations().get(0);
        OperationModel repeated = second.jobs().get(0).operations().get(0);
        assertEquals(
                firstOperation.processingOptions().get(0).machineId(),
                repeated.processingOptions().get(0).machineId());
        assertEquals(
                firstOperation.processingOptions().get(0).processingTime(),
                repeated.processingOptions().get(0).processingTime());
        Set<Long> candidates = new HashSet<Long>();
        firstOperation.processingOptions().forEach(option -> candidates.add(option.machineId()));
        assertEquals(3, candidates.size());
        assertNotEquals(0L, firstOperation.operationId());
    }
}
