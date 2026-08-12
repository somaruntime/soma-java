package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.engine.api.SimulationEngine;
import io.github.somaruntime.examples.simulation.engine.api.SimulationRunResult;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;
import io.github.somaruntime.examples.simulation.engine.core.SomaSimulationEngine;
import io.github.somaruntime.examples.simulation.presentation.ui.SimulationWindow;
import io.github.somaruntime.examples.simulation.runtime.Soma;
import io.github.somaruntime.examples.simulation.validation.SimulationResultValidator;
import io.github.somaruntime.soma.SomaConfiguration;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Owns process-wide SOMA configuration, run lifecycle, thread and final validation. */
public final class SimulationApplication {
    public SimulationRunResult run(SimulationConfig config) throws Exception {
        if (config.visualizationEnabled() && GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("graphics environment is headless; use --headless");
        }
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(config.memoryBudgetBytes())
                .compression(config.compression())
                .build());
        long initializedAt = System.nanoTime();
        SimulationEngine engine = new SomaSimulationEngine(config);
        long initializationNanos = System.nanoTime() - initializedAt;
        if (!config.visualizationEnabled()) {
            return runHeadless(config, engine, initializationNanos);
        }
        return runUi(config, engine, initializationNanos);
    }

    private static SimulationRunResult runHeadless(
            SimulationConfig config,
            SimulationEngine engine,
            long initializationNanos) {
        long startedAt = System.nanoTime();
        while (engine.tick() < config.maxTicks()) {
            engine.step();
        }
        long kernelNanos = System.nanoTime() - startedAt;
        SimulationStatistics statistics = engine.statistics();
        SimulationResultValidator.validate(config, statistics);
        return new SimulationRunResult(
                statistics, engine.processTimings(),
                initializationNanos, kernelNanos, true);
    }

    private static SimulationRunResult runUi(
            SimulationConfig config,
            SimulationEngine engine,
            long initializationNanos) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<SimulationWindow> window = new AtomicReference<SimulationWindow>();
        SwingUtilities.invokeAndWait(() -> {
            SimulationWindow created = new SimulationWindow(
                    config.worldWidth(), config.worldHeight(), config.cellSize(), stop);
            window.set(created);
            created.show();
            created.publish(engine.snapshot());
        });
        AtomicReference<SimulationStatistics> statistics =
                new AtomicReference<SimulationStatistics>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        long[] kernelNanos = new long[1];
        Thread simulation = new Thread(() -> {
            try {
                while (!stop.get() && engine.tick() < config.maxTicks()) {
                    long startedAt = System.nanoTime();
                    engine.step();
                    kernelNanos[0] = Math.addExact(
                            kernelNanos[0], System.nanoTime() - startedAt);
                    if (engine.tick() % config.renderEveryTicks() == 0L) {
                        io.github.somaruntime.examples.simulation.engine.api.SimulationSnapshot snapshot =
                                engine.snapshot();
                        SwingUtilities.invokeLater(() -> {
                            SimulationWindow active = window.get();
                            if (!stop.get() && active != null) active.publish(snapshot);
                        });
                    }
                    if (config.frameDelayMillis() > 0L) {
                        Thread.sleep(config.frameDelayMillis());
                    }
                }
                statistics.set(engine.statistics());
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                stop.set(true);
            }
        }, "soma-grassing-simulation");
        simulation.start();
        simulation.join();
        SwingUtilities.invokeAndWait(() -> window.get().close());
        Throwable problem = failure.get();
        if (problem instanceof Exception) throw (Exception) problem;
        if (problem instanceof Error) throw (Error) problem;
        if (problem != null) throw new AssertionError(problem);
        SimulationStatistics finalStatistics = statistics.get();
        if (finalStatistics == null) {
            throw new IllegalStateException("simulation stopped without final statistics");
        }
        SimulationResultValidator.validate(config, finalStatistics);
        return new SimulationRunResult(
                finalStatistics, engine.processTimings(),
                initializationNanos, kernelNanos[0], true);
    }
}
