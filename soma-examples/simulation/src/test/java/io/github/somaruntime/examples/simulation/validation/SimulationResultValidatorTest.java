package io.github.somaruntime.examples.simulation.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.configuration.SimulationConfigLoader;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SimulationResultValidatorTest {
    @Test
    void validatesPopulationAndPhysicalRanges() throws Exception {
        SimulationConfig config = SimulationConfigLoader.load(defaultConfig());
        SimulationStatistics valid = new SimulationStatistics(
                2L, 1_005, 10L, 5L, 900L, 105L,
                OptionalDouble.of(0.8), OptionalDouble.of(0.1), OptionalDouble.of(1.2),
                0.6, 0.0, 1.0,
                700L, 1_000L, 800L, 1L);
        assertDoesNotThrow(() -> SimulationResultValidator.validate(config, valid));

        SimulationStatistics invalid = new SimulationStatistics(
                2L, 1_005, 10L, 5L, 900L, 104L,
                OptionalDouble.of(0.8), OptionalDouble.of(0.1), OptionalDouble.of(1.2),
                0.6, 0.0, 1.0,
                700L, 1_000L, 800L, 1L);
        assertThrows(IllegalStateException.class,
                () -> SimulationResultValidator.validate(config, invalid));
    }

    private static Path defaultConfig() {
        Path repository = Paths.get(
                "soma-examples", "simulation", "config", "grassing.properties");
        return Files.isRegularFile(repository)
                ? repository
                : Paths.get("config", "grassing.properties");
    }
}
