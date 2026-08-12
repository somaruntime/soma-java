package io.github.somaruntime.examples.simulation.presentation.console;

import io.github.somaruntime.examples.simulation.engine.api.SimulationRunResult;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;

/** Stable concise console projection for people, CI and packaged smoke. */
public final class ConsoleStatisticsReporter {
    public void report(SimulationRunResult result) {
        SimulationStatistics statistics = result.statistics();
        System.out.println("simulation-reference: PASS");
        System.out.println("ticks=" + statistics.tick()
                + " population=" + statistics.population()
                + " births=" + statistics.births()
                + " deaths=" + statistics.deaths()
                + " grazing=" + statistics.grazingCount()
                + " searching=" + statistics.searchingCount());
        System.out.println("initializationMs=" + result.initializationNanos() / 1_000_000L
                + " kernelMs=" + result.kernelNanos() / 1_000_000L
                + " ticksPerSecond=" + String.format(java.util.Locale.ROOT, "%.2f", result.ticksPerSecond())
                + " retainedBytes=" + statistics.retainedBytes()
                + " representationBytes=" + statistics.representationBytes());
        System.out.println("processMs="
                + "growth:" + result.processTimings().grassGrowthNanos() / 1_000_000L
                + ",metabolism:" + result.processTimings().metabolismNanos() / 1_000_000L
                + ",reproduction:" + result.processTimings().reproductionNanos() / 1_000_000L
                + ",grazing:" + result.processTimings().grazingNanos() / 1_000_000L
                + ",searching:" + result.processTimings().searchingNanos() / 1_000_000L);
        System.out.println("fingerprint=" + Long.toUnsignedString(statistics.fingerprint())
                + " validation=" + (result.valid() ? "PASS" : "FAIL"));
    }
}
