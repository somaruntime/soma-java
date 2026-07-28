package io.github.somaruntime.soma.examples.rtd.feed;

import io.github.somaruntime.soma.examples.rtd.config.DispatchConfig;

public interface DispatchScenarioFactory {
  DispatchScenario create(DispatchConfig config);
}
