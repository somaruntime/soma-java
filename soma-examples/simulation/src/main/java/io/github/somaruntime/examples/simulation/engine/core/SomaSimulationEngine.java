package io.github.somaruntime.examples.simulation.engine.core;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.engine.api.SimulationEngine;
import io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;
import io.github.somaruntime.examples.simulation.engine.api.SimulationProcessTimings;
import io.github.somaruntime.examples.simulation.engine.core.process.GrassGrowthProcess;
import io.github.somaruntime.examples.simulation.engine.core.process.GrazingProcess;
import io.github.somaruntime.examples.simulation.engine.core.process.MetabolismProcess;
import io.github.somaruntime.examples.simulation.engine.core.process.ReproductionProcess;
import io.github.somaruntime.examples.simulation.engine.core.process.SearchingProcess;
import io.github.somaruntime.examples.simulation.runtime.GrassCellState;
import io.github.somaruntime.examples.simulation.runtime.GrasserState;
import io.github.somaruntime.examples.simulation.runtime.Soma;
import io.github.somaruntime.soma.IntGroupedLongResult;
import io.github.somaruntime.soma.SomaDoubleSummary;
import java.util.OptionalDouble;

/** Complete deterministic Grassing engine backed by two SOMA Tables. */
public final class SomaSimulationEngine implements SimulationEngine {
    private final SimulationRuntime runtime;
    private final GrassGrowthProcess grassGrowth;
    private final MetabolismProcess metabolism;
    private final ReproductionProcess reproduction;
    private final GrazingProcess grazing;
    private final SearchingProcess searching;
    private long grassGrowthNanos;
    private long metabolismNanos;
    private long reproductionNanos;
    private long grazingNanos;
    private long searchingNanos;

    public SomaSimulationEngine(SimulationConfig config) {
        this.runtime = new SimulationRuntime(config, Soma.createGroup());
        new SimulationInitializer().initialize(runtime);
        this.grassGrowth = new GrassGrowthProcess(
                runtime.grassCells, config.grassGrowthRate(), config.grassCapacity());
        this.metabolism = new MetabolismProcess(runtime.grassers, config.metabolism());
        this.reproduction = new ReproductionProcess(
                runtime.grassers,
                runtime.random,
                config.reproductionProbability(),
                runtime::nextGrasserId);
        this.grazing = new GrazingProcess(
                runtime.grassCells,
                runtime.grassers,
                runtime.random,
                config.satiationEnergy(),
                config.grazingConsumption(),
                config.minimumGrassAfterGrazing());
        this.searching = new SearchingProcess(
                runtime.grassCells,
                runtime.grassers,
                runtime.random,
                config.worldWidth(),
                config.worldHeight(),
                config.resumeGrazingAt(),
                config.walkSpeed(),
                config.turnStdDevRadians());
    }

    @Override
    public void step() {
        if (runtime.failed) {
            throw new IllegalStateException("simulation engine is fail-stop");
        }
        try {
            long startedAt = System.nanoTime();
            grassGrowth.execute();
            grassGrowthNanos = addElapsed(grassGrowthNanos, startedAt);
            startedAt = System.nanoTime();
            runtime.deaths = Math.addExact(runtime.deaths, metabolism.execute());
            metabolismNanos = addElapsed(metabolismNanos, startedAt);
            startedAt = System.nanoTime();
            runtime.births = Math.addExact(runtime.births, reproduction.execute());
            reproductionNanos = addElapsed(reproductionNanos, startedAt);
            startedAt = System.nanoTime();
            grazing.execute();
            grazingNanos = addElapsed(grazingNanos, startedAt);
            startedAt = System.nanoTime();
            searching.execute();
            searchingNanos = addElapsed(searchingNanos, startedAt);
            runtime.tick = Math.addExact(runtime.tick, 1L);
        } catch (RuntimeException | Error failure) {
            runtime.failed = true;
            throw failure;
        }
    }

    @Override
    public long tick() {
        return runtime.tick;
    }

    @Override
    public SimulationStatistics statistics() {
        SomaDoubleSummary energy = runtime.grassers.energy.summaryStatistics();
        SomaDoubleSummary grass = runtime.grassCells.grass.summaryStatistics();
        long searchingCount = runtime.grassers
                .filter(runtime.grassers.searching.eq(true)).count();
        long grazingCount = runtime.grassers.size() - searchingCount;
        IntGroupedLongResult occupancy = runtime.grassers
                .groupBy(runtime.grassers.cellId).count();
        long joinCount = runtime.grassers.join(runtime.grassCells)
                .on(runtime.grassers.cellId, runtime.grassCells.cellId)
                .inner()
                .count();
        if (joinCount != runtime.grassers.size()) {
            throw new IllegalStateException("Grasser/Grass relation is inconsistent");
        }
        long representation = Math.addExact(
                runtime.grassCells._metadata().representationBytes(),
                runtime.grassers._metadata().representationBytes());
        return new SimulationStatistics(
                runtime.tick,
                runtime.grassers.size(),
                runtime.births,
                runtime.deaths,
                grazingCount,
                searchingCount,
                optional(energy.count(), energy.average()),
                optional(energy.count(), energy.min()),
                optional(energy.count(), energy.max()),
                grass.count() == 0L ? Double.NaN : grass.average(),
                grass.count() == 0L ? Double.NaN : grass.min(),
                grass.count() == 0L ? Double.NaN : grass.max(),
                occupancy.size(),
                runtime.group._metadata().retainedBytes(),
                representation,
                fingerprint());
    }

    @Override
    public SimulationSnapshot snapshot() {
        GrassCellState[] cells = runtime.grassCells
                .sortedBy(runtime.grassCells.cellId.asc()).toArray();
        GrasserState[] grassers = runtime.grassers
                .sortedBy(runtime.grassers.grasserId.asc()).toArray();
        float[] grass = new float[cells.length];
        for (int index = 0; index < cells.length; index++) {
            grass[index] = cells[index].grass();
        }
        float[] x = new float[grassers.length];
        float[] y = new float[grassers.length];
        float[] direction = new float[grassers.length];
        boolean[] searching = new boolean[grassers.length];
        for (int index = 0; index < grassers.length; index++) {
            GrasserState grasser = grassers[index];
            x[index] = grasser.x();
            y[index] = grasser.y();
            direction[index] = grasser.direction();
            searching[index] = grasser.searching();
        }
        return new SimulationSnapshot(
                runtime.tick,
                runtime.config.worldWidth(),
                runtime.config.worldHeight(),
                grass, x, y, direction, searching);
    }

    @Override
    public SimulationProcessTimings processTimings() {
        return new SimulationProcessTimings(
                grassGrowthNanos,
                metabolismNanos,
                reproductionNanos,
                grazingNanos,
                searchingNanos);
    }

    private long fingerprint() {
        long hash = 0xcbf29ce484222325L;
        GrassCellState[] cells = runtime.grassCells
                .sortedBy(runtime.grassCells.cellId.asc()).toArray();
        for (int index = 0; index < cells.length; index++) {
            hash = mix(hash, cells[index].cellId());
            hash = mix(hash, Float.floatToIntBits(cells[index].grass()));
        }
        GrasserState[] grassers = runtime.grassers
                .sortedBy(runtime.grassers.grasserId.asc()).toArray();
        for (int index = 0; index < grassers.length; index++) {
            GrasserState grasser = grassers[index];
            hash = mix(hash, grasser.grasserId());
            hash = mix(hash, grasser.cellId());
            hash = mix(hash, grasser.searching() ? 1 : 0);
            hash = mix(hash, Float.floatToIntBits(grasser.x()));
            hash = mix(hash, Float.floatToIntBits(grasser.y()));
            hash = mix(hash, Float.floatToIntBits(grasser.energy()));
            hash = mix(hash, Float.floatToIntBits(grasser.direction()));
        }
        return hash;
    }

    private static long mix(long hash, int value) {
        return (hash ^ (value & 0xffffffffL)) * 0x100000001b3L;
    }

    private static OptionalDouble optional(long count, double value) {
        return count == 0L ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    private static long addElapsed(long current, long startedAt) {
        return Math.addExact(current, System.nanoTime() - startedAt);
    }
}
