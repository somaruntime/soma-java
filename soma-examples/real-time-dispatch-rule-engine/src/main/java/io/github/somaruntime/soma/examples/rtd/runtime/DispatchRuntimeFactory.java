package io.github.somaruntime.soma.examples.rtd.runtime;

import io.github.somaruntime.soma.examples.rtd.config.DispatchConfig;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.schema.generated.ResourceStateTable;
import io.github.somaruntime.soma.examples.rtd.schema.generated.SchemaMetadata;
import io.github.somaruntime.soma.examples.rtd.schema.generated.WorkStateTable;
import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.SomaGroup;
import io.github.somaruntime.soma.runtime.SomaGroupPlan;

/** 为 detached Scenario 创建并完整投影 live RTD runtime。 */
public final class DispatchRuntimeFactory {
  public DispatchRuntime create(DispatchScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    RuntimePlan plan = plan(scenario.config());
    SomaGroup group = SomaGroup.create(groupPlan(plan));
    DispatchRuntime runtime = null;
    try {
      WorkStateTable work =
          WorkStateTable.attach(group, "work-states");
      ResourceStateTable resources =
          ResourceStateTable.attach(group, "resource-states");
      runtime = new DispatchRuntime(
          scenario.checksum(), group, work, resources);
      DispatchRuntimeProjector.project(scenario.initial(), runtime);
      return runtime;
    } catch (RuntimeException failure) {
      cleanup(runtime, group, failure);
      throw failure;
    } catch (Error failure) {
      cleanup(runtime, group, failure);
      throw failure;
    }
  }

  private static SomaGroupPlan groupPlan(RuntimePlan plan) {
    return SomaGroupPlan.builder("real-time-dispatch-horizon")
        .member("work-states", SchemaMetadata.metadata(),
            WorkStateTable.metadata(), plan)
        .member("resource-states", SchemaMetadata.metadata(),
            ResourceStateTable.metadata(), plan)
        .build();
  }

  private static RuntimePlan plan(DispatchConfig config) {
    RuntimePlan base = WorkStateTable.defaultRuntimePlan();
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(),
            2L * 1024L * 1024L * 1024L));
    setCapacity(
        builder, "rtd_work_states", config.totalWork());
    setCapacity(
        builder, "rtd_resource_states", config.resourceCount());
    return builder.build();
  }

  private static void setCapacity(
      RuntimePlan.Builder builder,
      String table,
      int capacity) {
    builder.table(table).initialCapacity(Math.max(1, capacity));
  }

  private static void cleanup(
      DispatchRuntime runtime,
      SomaGroup group,
      Throwable failure) {
    if (runtime != null) {
      runtime.fail(failure);
      return;
    }
    try {
      group.release();
    } catch (Throwable cleanup) {
      failure.addSuppressed(cleanup);
    }
  }
}
