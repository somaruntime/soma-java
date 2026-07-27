package com.hgtech.soma.examples.rtd;

import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.ExecutionPolicy;
import com.hgtech.soma.dataflow.StatsMode;
import com.hgtech.soma.examples.rtd.config.DispatchConfig;
import com.hgtech.soma.examples.rtd.config.DispatchConfigLoader;
import com.hgtech.soma.examples.rtd.dispatch.SomaDispatcher;
import com.hgtech.soma.examples.rtd.feed.DispatchScenario;
import com.hgtech.soma.examples.rtd.feed.SyntheticDispatchScenarioFactory;
import com.hgtech.soma.examples.rtd.result.DispatchOutcome;

/** 可直接运行的 canonical RTD reference journey。 */
public final class RealTimeDispatchApplication {
  private RealTimeDispatchApplication() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    String[] overrides = new String[Math.max(0, args.length - 1)];
    if (overrides.length > 0) {
      System.arraycopy(args, 1, overrides, 0, overrides.length);
    }
    DispatchConfig config =
        new DispatchConfigLoader().load(selector, overrides);
    DispatchScenario scenario =
        new SyntheticDispatchScenarioFactory().create(config);
    ExecutionBudget upperBound = ExecutionBudget.defaults()
        .toBuilder()
        .maximumWorkers(config.workers())
        .build();
    DataFlowContext context = DataFlowContext.managedParallel(
        config.workers(),
        ExecutionPolicy.adaptiveParallel()
            .withMinimumParallelCardinality(
                config.parallelMinimumCardinality())
            .withStatsMode(StatsMode.DETAILED),
        upperBound);
    DispatchOutcome outcome;
    try {
      outcome = new SomaDispatcher().dispatch(scenario, context);
    } finally {
      context.close();
    }
    System.out.println("rtd-dispatch-result:"
        + " cycles=" + outcome.result().cycles()
        + " work=" + outcome.result().totalWork()
        + " dispatched=" + outcome.result().dispatchedWork()
        + " pending=" + outcome.result().pendingWork()
        + " config.checksum=" + config.checksum()
        + " input.checksum=" + scenario.checksum()
        + " result.checksum=" + outcome.result().checksum()
        + " definition=" + outcome.diagnostics().definitionIdentity()
        + " workers=" + outcome.diagnostics().maximumWorkers()
        + " claimAllowed=false");
  }
}
