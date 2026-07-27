package com.hgtech.soma.examples.rtd.feed;

import com.hgtech.soma.examples.rtd.config.DispatchConfig;

public interface DispatchScenarioFactory {
  DispatchScenario create(DispatchConfig config);
}
