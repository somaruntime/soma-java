package io.github.somaruntime.soma.examples.rtd.evidence;

import io.github.somaruntime.soma.dataflow.CancellationToken;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.ExecutionBudget;
import io.github.somaruntime.soma.dataflow.ExecutionPolicy;
import io.github.somaruntime.soma.dataflow.StatsMode;
import io.github.somaruntime.soma.examples.rtd.config.DispatchConfig;
import io.github.somaruntime.soma.examples.rtd.config.DispatchConfigLoader;
import io.github.somaruntime.soma.examples.rtd.dispatch.SomaDispatcher;
import io.github.somaruntime.soma.examples.rtd.dispatch.DispatchCommitterVerification;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.feed.SyntheticDispatchScenarioFactory;
import io.github.somaruntime.soma.examples.rtd.reference.ReferenceDispatcher;
import io.github.somaruntime.soma.examples.rtd.result.DispatchOutcome;
import io.github.somaruntime.soma.examples.rtd.result.DispatchResult;
import io.github.somaruntime.soma.examples.rtd.runtime.DispatchRuntimeBoundaryVerification;
import io.github.somaruntime.soma.examples.rtd.validation.DispatchResultAssertions;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

import java.util.concurrent.ForkJoinPool;

/** Canonical journey、reference、parallel、ownership 与 failure evidence。 */
public final class DispatchVerification {
  private DispatchVerification() {
  }

  public static void main(String[] args) throws Exception {
    String profile = args.length == 0 ? "correctness" : args[0];
    DispatchConfigLoader loader = new DispatchConfigLoader();
    DispatchConfig config = loader.load(profile);
    SyntheticDispatchScenarioFactory factory =
        new SyntheticDispatchScenarioFactory();
    DispatchScenario scenario = factory.create(config);
    DispatchScenario replay = factory.create(config);
    require(
        scenario.checksum().equals(replay.checksum()),
        "scenario replay identity");

    DispatchResult expected =
        new ReferenceDispatcher().dispatch(scenario);
    SomaDispatcher dispatcher = new SomaDispatcher();
    DispatchOutcome sequential = sequential(dispatcher, scenario);
    DispatchResultAssertions.equivalent(
        expected, sequential.result());

    DispatchOutcome managed = managed(dispatcher, scenario, config);
    DispatchResultAssertions.equivalent(expected, managed.result());
    require(
        managed.diagnostics().maximumWorkers() > 1,
        "managed execution did not exercise parallel work");

    DispatchOutcome borrowed = borrowed(dispatcher, scenario, config);
    DispatchResultAssertions.equivalent(expected, borrowed.result());
    require(
        sequential.diagnostics().definitionIdentity().equals(
            managed.diagnostics().definitionIdentity())
            && managed.diagnostics().definitionIdentity().equals(
                borrowed.diagnostics().definitionIdentity()),
        "definition identity differs by execution context");
    require(
        sequential.diagnostics().templateIdentity().equals(
            managed.diagnostics().templateIdentity())
            && managed.diagnostics().templateIdentity().equals(
                borrowed.diagnostics().templateIdentity()),
        "template identity differs by execution context");
    require(
        sequential.diagnostics().demandChecksum().equals(
            managed.diagnostics().demandChecksum())
            && managed.diagnostics().demandChecksum().equals(
                borrowed.diagnostics().demandChecksum()),
        "demand groups differ by execution context");

    if ("correctness".equals(profile)) {
      DispatchRuntimeBoundaryVerification.verify(scenario);
      DispatchCommitterVerification.verify(scenario);
      verifyCancellation(dispatcher, scenario);
      verifyBudget(dispatcher, config, factory);
      verifyInvalidConfig(loader);
    }
    System.out.println("rtd-dispatch-verification:"
        + " profile=" + profile
        + " cycles=" + sequential.result().cycles()
        + " work=" + sequential.result().totalWork()
        + " dispatched=" + sequential.result().dispatchedWork()
        + " inputChecksum=" + scenario.checksum()
        + " resultChecksum=" + sequential.result().checksum()
        + " oracle=" + expected.checksum()
        + " workers=" + managed.diagnostics().maximumWorkers()
        + " claimAllowed=false");
  }

  private static DispatchOutcome sequential(
      SomaDispatcher dispatcher, DispatchScenario scenario) {
    DataFlowContext context = DataFlowContext.sequential();
    try {
      return dispatcher.dispatch(scenario, context);
    } finally {
      context.close();
    }
  }

  private static DispatchOutcome managed(
      SomaDispatcher dispatcher,
      DispatchScenario scenario,
      DispatchConfig config) {
    DataFlowContext context = DataFlowContext.managedParallel(
        config.workers(),
        policy(config),
        upperBound(config));
    try {
      return dispatcher.dispatch(scenario, context);
    } finally {
      context.close();
      require(context.isClosed(), "managed context remained open");
    }
  }

  private static DispatchOutcome borrowed(
      SomaDispatcher dispatcher,
      DispatchScenario scenario,
      DispatchConfig config) {
    ForkJoinPool executor = new ForkJoinPool(config.workers());
    DataFlowContext context = DataFlowContext.borrowed(
        executor,
        config.workers(),
        policy(config),
        upperBound(config));
    try {
      return dispatcher.dispatch(scenario, context);
    } finally {
      context.close();
      require(
          !executor.isShutdown(),
          "borrowed executor was closed by SOMA");
      executor.shutdown();
    }
  }

  private static void verifyCancellation(
      SomaDispatcher dispatcher, DispatchScenario scenario) {
    DataFlowContext context = DataFlowContext.sequential();
    try {
      try {
        dispatcher.dispatch(
            scenario,
            context,
            new CancellationToken() {
              @Override
              public boolean isCancellationRequested() {
                return true;
              }
            });
        throw new AssertionError("cancellation expected");
      } catch (SomaRuntimeException expected) {
        require(
            "dataflow_cancelled".equals(expected.code()),
            "cancellation failure typing");
      }
      DispatchOutcome recovered = dispatcher.dispatch(scenario, context);
      require(
          recovered.result().totalWork()
              == scenario.config().totalWork(),
          "context not reusable after cancellation");
    } finally {
      context.close();
    }
  }

  private static void verifyBudget(
      SomaDispatcher dispatcher,
      DispatchConfig config,
      SyntheticDispatchScenarioFactory factory) {
    DispatchScenario constrained =
        factory.create(config.withMaximumOutputElements(1L));
    DataFlowContext context = DataFlowContext.sequential();
    try {
      try {
        dispatcher.dispatch(constrained, context);
        throw new AssertionError("budget failure expected");
      } catch (SomaRuntimeException expected) {
        require(
            "dataflow_output_budget_exceeded".equals(
                expected.code()),
            "budget failure typing");
      }
    } finally {
      context.close();
    }
  }

  private static void verifyInvalidConfig(
      DispatchConfigLoader loader) throws Exception {
    boolean rejected = false;
    try {
      loader.load("correctness", "resource.count=1");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    require(rejected, "invalid capability coverage accepted");
    rejected = false;
    try {
      loader.load("correctness", "unknown.key=1");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    require(rejected, "unknown config key accepted");
  }

  private static ExecutionPolicy policy(DispatchConfig config) {
    return ExecutionPolicy.adaptiveParallel()
        .withMinimumParallelCardinality(
            config.parallelMinimumCardinality())
        .withStatsMode(StatsMode.DETAILED);
  }

  private static ExecutionBudget upperBound(DispatchConfig config) {
    return ExecutionBudget.defaults()
        .toBuilder()
        .maximumWorkers(config.workers())
        .build();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
