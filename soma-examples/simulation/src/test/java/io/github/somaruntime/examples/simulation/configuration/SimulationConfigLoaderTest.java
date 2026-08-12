package io.github.somaruntime.examples.simulation.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class SimulationConfigLoaderTest {
    @Test
    void loadsDocumentedConfiguration() throws Exception {
        SimulationConfig config = SimulationConfigLoader.load(defaultConfig());
        assertEquals(100, config.worldWidth());
        assertEquals(10_000, config.worldCells());
        assertEquals(1_000, config.initialGrassers());
        assertTrue(config.visualizationEnabled());
    }

    @Test
    void rejectsUnknownProperty() throws Exception {
        String input = new String(
                Files.readAllBytes(defaultConfig()), StandardCharsets.ISO_8859_1);
        Path temporary = Files.createTempFile("soma-simulation", ".properties");
        try {
            Files.write(temporary, (input + "\nmodel.typo=1\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> SimulationConfigLoader.load(temporary));
            assertTrue(failure.getMessage().contains("unknown property"));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path defaultConfig() {
        Path repository = Paths.get(
                "soma-examples", "simulation", "config", "grassing.properties");
        return Files.isRegularFile(repository)
                ? repository
                : Paths.get("config", "grassing.properties");
    }
}
