package io.github.somaruntime.examples.simulation.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.configuration.SimulationConfigLoader;
import io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;
import io.github.somaruntime.examples.simulation.runtime.Soma;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SomaSimulationEngineTest {
    @BeforeAll
    static void configureSomaOnce() {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(256L << 20)
                .compression(SomaCompression.AUTO)
                .build());
    }

    @Test
    void initializationAndTickMaintainCoreInvariants() throws Exception {
        SimulationConfig config = smallConfig(24, 16, 80, 7L);
        SomaSimulationEngine engine = new SomaSimulationEngine(config);
        SimulationStatistics initial = engine.statistics();
        assertEquals(0L, initial.tick());
        assertEquals(80, initial.population());
        assertTrue(initial.occupiedCells() > 0L);
        assertTrue(initial.occupiedCells() <= config.worldCells());
        double initialGrass = initial.meanGrass();

        for (int tick = 0; tick < 20; tick++) engine.step();
        SimulationStatistics result = engine.statistics();
        assertEquals(20L, result.tick());
        assertEquals(config.initialGrassers() + result.births() - result.deaths(),
                result.population());
        assertEquals(result.population(), result.grazingCount() + result.searchingCount());
        assertTrue(result.minGrass() >= 0.0);
        assertTrue(result.maxGrass() <= config.grassCapacity() + 1.0e-6);
        assertTrue(result.population() == 0
                || result.minEnergy().getAsDouble() > 0.0);
        assertTrue(result.births() > 0L);
        assertTrue(result.deaths() > 0L);
        assertTrue(result.searchingCount() > 0L);
        assertNotEquals(initialGrass, result.meanGrass());
        assertTrue(engine.processTimings().totalNanos() > 0L);
    }

    @Test
    void sameSeedAndScheduleProduceSameFingerprint() throws Exception {
        SimulationConfig config = smallConfig(20, 20, 100, 19L);
        SomaSimulationEngine first = new SomaSimulationEngine(config);
        SomaSimulationEngine second = new SomaSimulationEngine(config);
        for (int tick = 0; tick < 25; tick++) {
            first.step();
            second.step();
        }
        assertEquals(first.statistics().fingerprint(), second.statistics().fingerprint());
    }

    @Test
    void snapshotIsDetachedFromLaterTicks() throws Exception {
        SimulationConfig config = smallConfig(12, 10, 30, 3L);
        SomaSimulationEngine engine = new SomaSimulationEngine(config);
        SimulationSnapshot snapshot = engine.snapshot();
        float firstGrass = snapshot.grassAt(0);
        float[] callerCopy = snapshot.grass();
        callerCopy[0] = -1.0f;
        assertEquals(firstGrass, snapshot.grassAt(0));
        engine.step();
        assertEquals(firstGrass, snapshot.grassAt(0));
        assertNotEquals(snapshot.tick(), engine.tick());
    }

    @Test
    void emptyPopulationUsesExplicitAbsentEnergyStatistics() throws Exception {
        SomaSimulationEngine engine = new SomaSimulationEngine(
                smallConfig(8, 8, 0, 31L));
        engine.step();
        SimulationStatistics result = engine.statistics();
        assertEquals(0, result.population());
        assertTrue(!result.meanEnergy().isPresent());
        assertTrue(!result.minEnergy().isPresent());
        assertTrue(!result.maxEnergy().isPresent());
    }

    private static SimulationConfig smallConfig(
            int width, int height, int population, long seed) throws Exception {
        SimulationConfig base = SimulationConfigLoader.load(defaultConfig());
        return new SimulationConfig(
                width, height, population,
                base.initialGrass(), base.initialEnergy(), base.satiationEnergy(),
                base.grassCapacity(), base.grassGrowthRate(), base.metabolism(),
                base.reproductionProbability(), base.grazingConsumption(),
                base.minimumGrassAfterGrazing(), base.resumeGrazingAt(),
                base.walkSpeed(), base.turnStdDevRadians(), seed, 100L, 10L,
                base.memoryBudgetBytes(), base.compression(), false, 1L, 2, 0L);
    }

    private static Path defaultConfig() {
        Path repository = Paths.get(
                "soma-examples", "simulation", "config", "grassing.properties");
        return Files.isRegularFile(repository)
                ? repository
                : Paths.get("config", "grassing.properties");
    }
}
