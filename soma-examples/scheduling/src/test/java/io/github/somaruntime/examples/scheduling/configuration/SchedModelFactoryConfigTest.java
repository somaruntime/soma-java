package io.github.somaruntime.examples.scheduling.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class SchedModelFactoryConfigTest {
    @Test
    void loadsTheStandard100kShapeWithoutReleaseTime() throws Exception {
        SchedModelFactoryConfig config = SchedModelFactoryConfig.load(Paths.get(
                "config", "fjsp-standard.properties"));

        assertEquals(1_000, config.jobCount());
        assertEquals(100, config.operationsPerJob());
        assertEquals(100, config.machineCount());
        assertEquals(3, config.candidatesPerOperation());
        assertEquals(100_000L, config.operationCount());
        assertEquals(300_000L, config.processingOptionCount());
    }
}
