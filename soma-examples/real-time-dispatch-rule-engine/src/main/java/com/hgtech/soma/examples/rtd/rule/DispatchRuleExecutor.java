package com.hgtech.soma.examples.rtd.rule;

import com.hgtech.soma.dataflow.CancellationToken;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.ExecutionPolicy;
import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.JoinedIndexResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.DataFlowStats;
import com.hgtech.soma.dataflow.StatsMode;
import com.hgtech.soma.examples.rtd.config.DispatchConfig;
import com.hgtech.soma.examples.rtd.runtime.DispatchRuntime;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateDataFlow;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateDataFlow;

/** 绑定一次 one-shot Invocation，并在 mutation 前完成 detached command 复制。 */
public final class DispatchRuleExecutor {
  private final DispatchRulePlan plan;
  private final DispatchCommandSelector selector =
      new DispatchCommandSelector();

  public DispatchRuleExecutor(DispatchRulePlan plan) {
    if (plan == null) throw new NullPointerException("plan");
    this.plan = plan;
  }

  public DispatchRuleEvaluation evaluate(
      DispatchRuntime runtime,
      DispatchConfig config,
      DataFlowContext context,
      CancellationToken cancellation) {
    if (runtime == null) throw new NullPointerException("runtime");
    if (config == null) throw new NullPointerException("config");
    if (context == null) throw new NullPointerException("context");
    DataFlowInvocation<DataFlowResults> invocation =
        plan.template.newInvocation(context)
            .bind(
                plan.work,
                WorkStateDataFlow.bind(runtime.workStates()))
            .bind(
                plan.resources,
                ResourceStateDataFlow.bind(runtime.resourceStates()))
            .parameter(
                DispatchRulePlan.NOW,
                Long.valueOf(runtime.currentMinute()))
            .policy(policy(config))
            .budget(budget(config))
            .cancellationToken(cancellation);
    DataFlowResults results = invocation.execute();
    LongScalarResult ready = results.get(plan.readyCount);
    GroupedLongResult demand =
        results.get(plan.demandByCapability);
    JoinedIndexResult pairs = results.get(plan.rankedPairs);
    requireGroupCardinality(ready.value(), demand);
    DispatchCommandSelector.Selection selected =
        selector.select(runtime, pairs, demand);
    return new DispatchRuleEvaluation(
        selected.commands,
        ready.value(),
        pairs.size(),
        demand.size(),
        selected.demandChecksum,
        invocation.stats());
  }

  public DispatchRulePlan plan() { return plan; }

  public AdmittedLoad measureAdmittedLoad(
      DispatchRuntime runtime,
      DispatchConfig config,
      DataFlowContext context,
      CancellationToken cancellation) {
    if (runtime == null) throw new NullPointerException("runtime");
    if (config == null) throw new NullPointerException("config");
    if (context == null) throw new NullPointerException("context");
    DataFlowInvocation<LongScalarResult> invocation =
        plan.admittedLoadTemplate.newInvocation(context)
            .bind(
                plan.work,
                WorkStateDataFlow.bind(runtime.workStates()))
            .policy(policy(config))
            .budget(budget(config))
            .cancellationToken(cancellation);
    long processingMinutes = invocation.execute().value();
    return new AdmittedLoad(processingMinutes, invocation.stats());
  }

  private static void requireGroupCardinality(
      long ready, GroupedLongResult demand) {
    long grouped = 0L;
    for (int group = 0; group < demand.size(); group++) {
      grouped = Math.addExact(grouped, demand.valueAt(group));
    }
    if (grouped != ready) {
      throw new IllegalStateException(
          "group counts do not cover ready candidates");
    }
  }

  private static ExecutionPolicy policy(DispatchConfig config) {
    return ExecutionPolicy.adaptiveParallel()
        .withMinimumParallelCardinality(
            config.parallelMinimumCardinality())
        .withStatsMode(StatsMode.DETAILED);
  }

  private static ExecutionBudget budget(DispatchConfig config) {
    return ExecutionBudget.defaults()
        .toBuilder()
        .maximumOutputElements(config.maximumOutputElements())
        .maximumTasks(config.maximumTasks())
        .maximumWorkers(config.workers())
        .build();
  }

  public static final class AdmittedLoad {
    private final long processingMinutes;
    private final DataFlowStats stats;

    AdmittedLoad(long processingMinutes, DataFlowStats stats) {
      this.processingMinutes = processingMinutes;
      this.stats = stats;
    }

    public long processingMinutes() { return processingMinutes; }
    public DataFlowStats stats() { return stats; }
  }
}
