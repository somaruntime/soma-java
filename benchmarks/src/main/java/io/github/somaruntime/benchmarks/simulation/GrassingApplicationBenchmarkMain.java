package io.github.somaruntime.benchmarks.simulation;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.examples.simulation.application.SimulationApplication;
import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.engine.api.SimulationRunResult;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;
import io.github.somaruntime.examples.simulation.engine.api.SimulationProcessTimings;
import io.github.somaruntime.soma.SomaCompression;

/** End-to-end headless Grassing journey; rows denotes world cell count. */
public final class GrassingApplicationBenchmarkMain {
    private GrassingApplicationBenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        int worldCells = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        BenchmarkSupport.require(
                "soma-auto".equals(implementation) || "soma-off".equals(implementation),
                "Grassing application is a SOMA downstream journey, not a parallel manual engine");
        int width = (int) Math.sqrt(worldCells);
        int height = Math.max(1, worldCells / width);
        int effectiveCells = Math.multiplyExact(width, height);
        int population = Math.max(1, Integer.getInteger(
                "soma.benchmark.simulation.population", effectiveCells / 10));
        long ticks = Long.getLong("soma.benchmark.simulation.ticks", 100L);
        long budget = Long.getLong("soma.benchmark.memoryBudgetBytes", 6L << 30);
        SomaCompression compression = "soma-off".equals(implementation)
                ? SomaCompression.OFF : SomaCompression.AUTO;
        SimulationConfig config = new SimulationConfig(
                width, height, population,
                0.8f, 1.0f, 1.0f, 1.0f, 0.01f, 0.02f, 0.05f,
                0.05f, 0.1f, 0.2f, 0.25f, (float) Math.toRadians(30.0),
                0L, ticks, Math.max(1L, ticks / 10L), budget, compression,
                false, 1L, 1, 0L);
        SimulationRunResult result = new SimulationApplication().run(config);
        SimulationStatistics statistics = result.statistics();
        SimulationProcessTimings timings = result.processTimings();
        new BenchmarkResult(
                "simulation-application", "GRASSING", implementation, effectiveCells)
                .put("correctness", result.valid())
                .put("compression", compression.name())
                .put("worldWidth", width)
                .put("worldHeight", height)
                .put("initialGrassers", population)
                .put("executedTicks", statistics.tick())
                .put("finalGrassers", statistics.population())
                .put("births", statistics.births())
                .put("deaths", statistics.deaths())
                .put("initializationNanos", result.initializationNanos())
                .put("kernelNanos", result.kernelNanos())
                .put("grassGrowthNanos", timings.grassGrowthNanos())
                .put("metabolismNanos", timings.metabolismNanos())
                .put("reproductionNanos", timings.reproductionNanos())
                .put("grazingNanos", timings.grazingNanos())
                .put("searchingNanos", timings.searchingNanos())
                .put("retainedBytes", statistics.retainedBytes())
                .put("representationBytes", statistics.representationBytes())
                .put("sharedFingerprint", statistics.fingerprint())
                .put("fingerprint", statistics.fingerprint())
                .print();
    }
}
