package io.github.somaruntime.soma.examples.rtd.benchmark;

import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.ExecutionBudget;
import io.github.somaruntime.soma.dataflow.ExecutionPolicy;
import io.github.somaruntime.soma.dataflow.StatsMode;
import io.github.somaruntime.soma.examples.rtd.config.DispatchConfig;
import io.github.somaruntime.soma.examples.rtd.config.DispatchConfigLoader;
import io.github.somaruntime.soma.examples.rtd.dispatch.SomaDispatcher;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.feed.SyntheticDispatchScenarioFactory;
import io.github.somaruntime.soma.examples.rtd.reference.ReferenceDispatcher;
import io.github.somaruntime.soma.examples.rtd.result.DispatchDiagnostics;
import io.github.somaruntime.soma.examples.rtd.result.DispatchOutcome;
import io.github.somaruntime.soma.examples.rtd.result.DispatchResult;
import io.github.somaruntime.soma.examples.rtd.validation.DispatchResultAssertions;

/** 单 JVM fork 的 correctness-guarded RTD integrated benchmark。 */
public final class DispatchBenchmark {
  private DispatchBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    DispatchConfig config =
        new DispatchConfigLoader().load(selector);
    BenchmarkOptions options = BenchmarkOptions.load(
        args.length < 2 ? "default" : args[1]);
    BenchmarkEnvironment environment =
        new BenchmarkEnvironment(options.forks());
    DispatchScenario scenario =
        new SyntheticDispatchScenarioFactory().create(config);
    DispatchResult expected =
        new ReferenceDispatcher().dispatch(scenario);
    SomaDispatcher dispatcher = new SomaDispatcher();
    DataFlowContext context = context(config);
    try {
      for (int warmup = 0;
           warmup < options.warmup(); warmup++) {
        DispatchResultAssertions.equivalent(
            expected,
            dispatcher.dispatch(scenario, context).result());
      }

      Aggregate aggregate = new Aggregate();
      for (int measurement = 0;
           measurement < options.measurements(); measurement++) {
        aggregate.add(measure(
            dispatcher, scenario, context, expected));
      }
      aggregate.print(
          selector, config, scenario, options, environment);
    } finally {
      context.close();
    }
  }

  private static DataFlowContext context(DispatchConfig config) {
    ExecutionBudget upperBound = ExecutionBudget.defaults()
        .toBuilder()
        .maximumWorkers(config.workers())
        .build();
    return DataFlowContext.managedParallel(
        config.workers(),
        ExecutionPolicy.adaptiveParallel()
            .withMinimumParallelCardinality(
                config.parallelMinimumCardinality())
            .withStatsMode(StatsMode.DETAILED),
        upperBound);
  }

  private static Measurement measure(
      SomaDispatcher dispatcher,
      DispatchScenario scenario,
      DataFlowContext context,
      DispatchResult expected) {
    long beforeAllocation = JvmMetrics.callerAllocatedBytes();
    JvmMetrics.GcSnapshot beforeGc = JvmMetrics.gcSnapshot();
    long start = System.nanoTime();
    DispatchOutcome outcome = dispatcher.dispatch(scenario, context);
    long nanos = System.nanoTime() - start;
    JvmMetrics.GcSnapshot afterGc = JvmMetrics.gcSnapshot();
    long afterAllocation = JvmMetrics.callerAllocatedBytes();
    DispatchResultAssertions.equivalent(expected, outcome.result());
    if (outcome.diagnostics().maximumWorkers() <= 1) {
      throw new IllegalStateException(
          "benchmark did not exercise controlled parallel work");
    }
    return new Measurement(
        nanos,
        Math.subtractExact(afterAllocation, beforeAllocation),
        delta(afterGc.youngCount, beforeGc.youngCount),
        delta(afterGc.youngMillis, beforeGc.youngMillis),
        delta(afterGc.fullCount, beforeGc.fullCount),
        delta(afterGc.fullMillis, beforeGc.fullMillis),
        outcome);
  }

  private static long delta(long after, long before) {
    return Math.max(0L, after - before);
  }

  private static long ceilingDivide(long value, long divisor) {
    if (value < 0L || divisor <= 0L) {
      throw new IllegalArgumentException(
          "ceiling division requires non-negative value "
              + "and positive divisor");
    }
    return value == 0L ? 0L : 1L + (value - 1L) / divisor;
  }

  private static final class Measurement {
    final long dispatchNanos;
    final long callerAllocatedBytes;
    final long youngGcCount;
    final long youngGcMillis;
    final long fullGcCount;
    final long fullGcMillis;
    final DispatchOutcome outcome;

    Measurement(
        long dispatchNanos,
        long callerAllocatedBytes,
        long youngGcCount,
        long youngGcMillis,
        long fullGcCount,
        long fullGcMillis,
        DispatchOutcome outcome) {
      this.dispatchNanos = dispatchNanos;
      this.callerAllocatedBytes = callerAllocatedBytes;
      this.youngGcCount = youngGcCount;
      this.youngGcMillis = youngGcMillis;
      this.fullGcCount = fullGcCount;
      this.fullGcMillis = fullGcMillis;
      this.outcome = outcome;
    }
  }

  private static final class Aggregate {
    private long dispatchNanos;
    private long minimumDispatchNanos = Long.MAX_VALUE;
    private long maximumDispatchNanos;
    private long callerAllocatedBytes;
    private long youngGcCount;
    private long youngGcMillis;
    private long fullGcCount;
    private long fullGcMillis;
    private long tasks;
    private int maximumWorkers;
    private DispatchOutcome identity;

    void add(Measurement value) {
      dispatchNanos = Math.addExact(
          dispatchNanos, value.dispatchNanos);
      minimumDispatchNanos = Math.min(
          minimumDispatchNanos, value.dispatchNanos);
      maximumDispatchNanos = Math.max(
          maximumDispatchNanos, value.dispatchNanos);
      callerAllocatedBytes = Math.addExact(
          callerAllocatedBytes, value.callerAllocatedBytes);
      youngGcCount = Math.addExact(
          youngGcCount, value.youngGcCount);
      youngGcMillis = Math.addExact(
          youngGcMillis, value.youngGcMillis);
      fullGcCount = Math.addExact(fullGcCount, value.fullGcCount);
      fullGcMillis = Math.addExact(
          fullGcMillis, value.fullGcMillis);
      tasks = Math.addExact(
          tasks, value.outcome.diagnostics().tasks());
      maximumWorkers = Math.max(
          maximumWorkers,
          value.outcome.diagnostics().maximumWorkers());
      if (identity == null) {
        identity = value.outcome;
      } else {
        requireSameIdentity(identity, value.outcome);
      }
    }

    void print(
        String selector,
        DispatchConfig config,
        DispatchScenario scenario,
        BenchmarkOptions options,
        BenchmarkEnvironment environment) {
      if (identity == null) {
        throw new IllegalStateException(
            "benchmark produced no measurement");
      }
      DispatchResult result = identity.result();
      DispatchDiagnostics diagnostics = identity.diagnostics();
      long workExecutions = Math.multiplyExact(
          (long) config.totalWork(),
          (long) options.measurements());
      System.out.println("{"
          + "\"schemaVersion\":"
          + "\"soma-reference-application-benchmark-v1\","
          + "\"artifactVersion\":\"rtd-dispatch-benchmark-v1\","
          + environment.jsonFields() + ","
          + "\"profile\":\"" + selector + "\","
          + "\"configChecksum\":\"" + config.checksum() + "\","
          + "\"generatorVersion\":" + config.generatorVersion() + ","
          + "\"seed\":" + config.seed() + ","
          + "\"inputChecksum\":\"" + scenario.checksum() + "\","
          + "\"resultChecksum\":\"" + result.checksum() + "\","
          + "\"schemaHash\":\"" + diagnostics.schemaHash() + "\","
          + "\"runtimePlanHash\":\""
          + diagnostics.runtimePlanHash() + "\","
          + "\"definitionIdentity\":\""
          + diagnostics.definitionIdentity() + "\","
          + "\"templateIdentity\":\""
          + diagnostics.templateIdentity() + "\","
          + "\"demandChecksum\":\""
          + diagnostics.demandChecksum() + "\","
          + "\"initialWork\":" + config.initialWork() + ","
          + "\"arrivalsPerCycle\":"
          + config.arrivalsPerCycle() + ","
          + "\"cycles\":" + config.cycles() + ","
          + "\"totalWork\":" + config.totalWork() + ","
          + "\"resources\":" + config.resourceCount() + ","
          + "\"capabilities\":" + config.capabilityCount() + ","
          + "\"configuredWorkers\":" + config.workers() + ","
          + "\"dispatchedWork\":" + result.dispatchedWork() + ","
          + "\"pendingWork\":" + result.pendingWork() + ","
          + "\"warmup\":" + options.warmup() + ","
          + "\"measurements\":" + options.measurements() + ","
          + "\"workExecutions\":" + workExecutions + ","
          + "\"dispatchNanos\":" + dispatchNanos + ","
          + "\"minimumDispatchNanos\":"
          + minimumDispatchNanos + ","
          + "\"maximumDispatchNanos\":"
          + maximumDispatchNanos + ","
          + "\"dispatchNanosPerWork\":"
          + ceilingDivide(dispatchNanos, workExecutions) + ","
          + "\"callerAllocatedBytes\":"
          + callerAllocatedBytes + ","
          + "\"callerAllocatedBytesPerWork\":"
          + ceilingDivide(callerAllocatedBytes, workExecutions) + ","
          + "\"youngGcCount\":" + youngGcCount + ","
          + "\"youngGcPauseMillis\":" + youngGcMillis + ","
          + "\"fullGcCount\":" + fullGcCount + ","
          + "\"fullGcPauseMillis\":" + fullGcMillis + ","
          + "\"tasks\":" + tasks + ","
          + "\"maximumWorkers\":" + maximumWorkers + ","
          + "\"admittedProcessingMinutes\":"
          + diagnostics.admittedProcessingMinutes() + ","
          + "\"claimAllowed\":false}");
    }

    private static void requireSameIdentity(
        DispatchOutcome expected, DispatchOutcome actual) {
      DispatchResult left = expected.result();
      DispatchResult right = actual.result();
      DispatchDiagnostics leftDiagnostics = expected.diagnostics();
      DispatchDiagnostics rightDiagnostics = actual.diagnostics();
      if (!left.checksum().equals(right.checksum())
          || !leftDiagnostics.schemaHash().equals(
              rightDiagnostics.schemaHash())
          || !leftDiagnostics.runtimePlanHash().equals(
              rightDiagnostics.runtimePlanHash())
          || !leftDiagnostics.definitionIdentity().equals(
              rightDiagnostics.definitionIdentity())
          || !leftDiagnostics.templateIdentity().equals(
              rightDiagnostics.templateIdentity())
          || !leftDiagnostics.demandChecksum().equals(
              rightDiagnostics.demandChecksum())
          || leftDiagnostics.admittedProcessingMinutes()
              != rightDiagnostics.admittedProcessingMinutes()) {
        throw new IllegalStateException(
            "benchmark measurement changed result or runtime identity");
      }
    }
  }
}
