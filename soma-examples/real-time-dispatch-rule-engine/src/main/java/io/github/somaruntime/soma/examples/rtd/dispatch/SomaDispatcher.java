package io.github.somaruntime.soma.examples.rtd.dispatch;

import io.github.somaruntime.soma.dataflow.CancellationToken;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowStats;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchCycle;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.result.DispatchCommand;
import io.github.somaruntime.soma.examples.rtd.result.DispatchDiagnostics;
import io.github.somaruntime.soma.examples.rtd.result.DispatchOutcome;
import io.github.somaruntime.soma.examples.rtd.result.DispatchResult;
import io.github.somaruntime.soma.examples.rtd.rule.DispatchRuleEvaluation;
import io.github.somaruntime.soma.examples.rtd.rule.DispatchRuleExecutor;
import io.github.somaruntime.soma.examples.rtd.rule.DispatchRuleExecutor.AdmittedLoad;
import io.github.somaruntime.soma.examples.rtd.rule.DispatchRulePlan;
import io.github.somaruntime.soma.examples.rtd.runtime.DispatchRuntime;
import io.github.somaruntime.soma.examples.rtd.runtime.DispatchRuntimeFactory;
import io.github.somaruntime.soma.examples.rtd.schema.WorkStatus;
import io.github.somaruntime.soma.examples.rtd.support.StableHash;

import java.util.ArrayList;

/** Reusable rule plan + repeated Invocation + application-owned commit。 */
public final class SomaDispatcher implements Dispatcher {
  private final DispatchRuntimeFactory runtimeFactory =
      new DispatchRuntimeFactory();
  private final DispatchRulePlan plan = new DispatchRulePlan();
  private final DispatchRuleExecutor rule =
      new DispatchRuleExecutor(plan);
  private final DispatchCommitter committer =
      new DispatchCommitter();

  @Override
  public DispatchOutcome dispatch(
      DispatchScenario scenario, DataFlowContext context) {
    return dispatch(scenario, context, CancellationToken.NONE);
  }

  @Override
  public DispatchOutcome dispatch(
      DispatchScenario scenario,
      DataFlowContext context,
      CancellationToken cancellation) {
    if (scenario == null) throw new NullPointerException("scenario");
    if (context == null) throw new NullPointerException("context");
    if (cancellation == null) {
      throw new NullPointerException("cancellation");
    }
    DispatchRuntime runtime = null;
    try {
      runtime = runtimeFactory.create(scenario);
      ArrayList<DispatchCommand> commands =
          new ArrayList<DispatchCommand>();
      StableHash demandHash = new StableHash()
          .addString("rtd-demand-horizon-v1");
      long readyCandidates = 0L;
      long joinedPairs = 0L;
      long demandGroups = 0L;
      long tasks = 0L;
      int maximumWorkers = 0;
      for (DispatchCycle cycle : scenario.cycles()) {
        runtime.beginCycle(cycle.currentMinute());
        runtime.apply(cycle.arrivals());
        DispatchRuleEvaluation evaluation =
            rule.evaluate(
                runtime, scenario.config(), context, cancellation);
        committer.commit(runtime, evaluation.commands());
        commands.addAll(evaluation.commands());
        readyCandidates = Math.addExact(
            readyCandidates, evaluation.readyCandidates());
        joinedPairs = Math.addExact(
            joinedPairs, evaluation.joinedPairs());
        demandGroups = Math.addExact(
            demandGroups, evaluation.demandGroups());
        demandHash.addString(evaluation.demandChecksum());
        DataFlowStats stats = evaluation.stats();
        tasks = Math.addExact(tasks, stats.tasks());
        maximumWorkers = Math.max(maximumWorkers, stats.workers());
      }
      AdmittedLoad admittedLoad = rule.measureAdmittedLoad(
          runtime, scenario.config(), context, cancellation);
      tasks = Math.addExact(tasks, admittedLoad.stats().tasks());
      maximumWorkers = Math.max(
          maximumWorkers, admittedLoad.stats().workers());

      int pending = Math.toIntExact(
          runtime.workStates()
              .scanByStatus(WorkStatus.PENDING).count());
      int dispatched = Math.toIntExact(
          runtime.workStates()
              .scanByStatus(WorkStatus.DISPATCHED).count());
      if (Math.addExact(pending, dispatched)
          != runtime.workStates().size()) {
        throw new IllegalStateException(
            "work status groups do not cover runtime");
      }
      String schemaHash =
          runtime.workStates().runtimePlan().schemaHash();
      String runtimePlanHash =
          runtime.workStates().runtimePlan().runtimePlanHash();
      DispatchResult result = new DispatchResult(
          scenario.cycleCount(),
          runtime.workStates().size(),
          dispatched,
          pending,
          commands,
          runtime.inputChecksum());
      DispatchDiagnostics diagnostics =
          new DispatchDiagnostics(
              plan.definitionIdentity(),
              plan.templateIdentity(),
              schemaHash,
              runtimePlanHash,
              Math.addExact(scenario.cycleCount(), 1),
              readyCandidates,
              joinedPairs,
              demandGroups,
              demandHash.finishHex(),
              admittedLoad.processingMinutes(),
              tasks,
              maximumWorkers);
      runtime.close();
      runtime = null;
      return new DispatchOutcome(result, diagnostics);
    } catch (RuntimeException failure) {
      if (runtime != null) throw runtime.fail(failure);
      throw failure;
    } catch (Error failure) {
      if (runtime != null) throw runtime.fail(failure);
      throw failure;
    }
  }
}
