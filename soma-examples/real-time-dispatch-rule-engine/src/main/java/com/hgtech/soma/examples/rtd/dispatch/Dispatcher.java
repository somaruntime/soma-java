package com.hgtech.soma.examples.rtd.dispatch;

import com.hgtech.soma.dataflow.CancellationToken;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.examples.rtd.feed.DispatchScenario;
import com.hgtech.soma.examples.rtd.result.DispatchOutcome;

public interface Dispatcher {
  DispatchOutcome dispatch(
      DispatchScenario scenario, DataFlowContext context);

  DispatchOutcome dispatch(
      DispatchScenario scenario,
      DataFlowContext context,
      CancellationToken cancellation);
}
