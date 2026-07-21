package com.hgtech.soma.examples.simulation;

/** 与 SOMA storage failure 分开的 application numeric invariant failure。 */
public final class SimulationNumericsException extends RuntimeException {
  public SimulationNumericsException(String message) {
    super(message);
  }
}
