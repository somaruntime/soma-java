package io.github.somaruntime.soma.examples.rtd.dispatch;

import io.github.somaruntime.soma.dataflow.CancellationToken;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.result.DispatchOutcome;

public interface Dispatcher {
  DispatchOutcome dispatch(
      DispatchScenario scenario, DataFlowContext context);

  DispatchOutcome dispatch(
      DispatchScenario scenario,
      DataFlowContext context,
      CancellationToken cancellation);
}
