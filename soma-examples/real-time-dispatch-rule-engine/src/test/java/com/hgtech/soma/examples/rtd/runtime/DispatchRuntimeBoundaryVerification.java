package com.hgtech.soma.examples.rtd.runtime;

import com.hgtech.soma.examples.rtd.feed.DispatchCycle;
import com.hgtech.soma.examples.rtd.feed.DispatchScenario;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.runtime.SomaGroupState;

/** Snapshot/Delta projection与 release 的代表性边界证据。 */
public final class DispatchRuntimeBoundaryVerification {
  private DispatchRuntimeBoundaryVerification() {
  }

  public static void verify(DispatchScenario scenario) {
    DispatchRuntime runtime =
        new DispatchRuntimeFactory().create(scenario);
    require(runtime.runtimeMetadata().explicit()
            && runtime.runtimeMetadata().members().size() == 2
            && runtime.runtimeMetadata().currentTableInstances() == 2L,
        "dispatch explicit Group topology");
    require(
        runtime.workStates().size()
            == scenario.initial().workCount(),
        "initial work projection");
    require(
        runtime.resourceStates().size()
            == scenario.initial().resourceCount(),
        "initial resource projection");
    require(
        runtime.workStates()
            .scanByStatus(WorkStatus.PENDING).count()
            == scenario.initial().workCount(),
        "initial pending group");
    DispatchCycle first = scenario.cycleAt(0);
    runtime.beginCycle(first.currentMinute());
    runtime.apply(first.arrivals());
    require(
        runtime.workStates().size()
            == scenario.initial().workCount()
                + first.arrivals().size(),
        "delta projection");
    runtime.close();
    require(runtime.runtimeMetadata().state() == SomaGroupState.RELEASED
            && runtime.runtimeMetadata().currentStructuralBytes() == 0L
            && runtime.runtimeMetadata().currentTableInstances() == 0L,
        "dispatch Group terminal snapshot");
    boolean rejected = false;
    try {
      runtime.workStates();
    } catch (IllegalStateException expected) {
      rejected = true;
    }
    require(rejected, "released runtime accepted normal access");
    runtime.close();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
