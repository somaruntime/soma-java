package com.hgtech.soma.examples.grassing.config;

import java.io.IOException;

/** 定位、解析并验证版本化仿真配置。 */
public final class SimulationConfigLoader {
  public SimulationConfig load(String selector, String... overrides)
      throws IOException {
    return SimulationConfig.load(selector, overrides);
  }
}
