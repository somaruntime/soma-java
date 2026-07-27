package com.hgtech.soma.examples.rtd.runtime;

import com.hgtech.soma.examples.rtd.config.DispatchConfig;
import com.hgtech.soma.examples.rtd.feed.DispatchScenario;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateTable;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateTable;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** 为 detached Scenario 创建并完整投影 live RTD runtime。 */
public final class DispatchRuntimeFactory {
  public DispatchRuntime create(DispatchScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    RuntimePlan plan = plan(scenario.config());
    WorkStateTable work = null;
    ResourceStateTable resources = null;
    DispatchRuntime runtime = null;
    try {
      work = WorkStateTable.create(plan);
      resources = ResourceStateTable.create(plan);
      runtime = new DispatchRuntime(
          scenario.checksum(), work, resources);
      DispatchRuntimeProjector.project(scenario.initial(), runtime);
      return runtime;
    } catch (RuntimeException failure) {
      cleanup(runtime, resources, work, failure);
      throw failure;
    } catch (Error failure) {
      cleanup(runtime, resources, work, failure);
      throw failure;
    }
  }

  private static RuntimePlan plan(DispatchConfig config) {
    RuntimePlan base = WorkStateTable.defaultRuntimePlan();
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(),
            2L * 1024L * 1024L * 1024L));
    replaceCapacity(
        builder, base, "rtd_work_states", config.totalWork());
    replaceCapacity(
        builder, base, "rtd_resource_states", config.resourceCount());
    return builder.build();
  }

  private static void replaceCapacity(
      RuntimePlan.Builder builder,
      RuntimePlan base,
      String table,
      int capacity) {
    TablePlan replacement = base.requireTable(table).toBuilder()
        .initialCapacity(Math.max(1, capacity))
        .build();
    builder.replaceTable(replacement);
  }

  private static void cleanup(
      DispatchRuntime runtime,
      ResourceStateTable resources,
      WorkStateTable work,
      Throwable failure) {
    if (runtime != null) {
      runtime.fail(failure);
      return;
    }
    try {
      if (resources != null) resources.release();
    } catch (Throwable cleanup) {
      failure.addSuppressed(cleanup);
    }
    try {
      if (work != null) work.release();
    } catch (Throwable cleanup) {
      failure.addSuppressed(cleanup);
    }
  }
}
