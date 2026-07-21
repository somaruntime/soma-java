package com.hgtech.soma.examples.fjsp;

/** 尚有 operation 未分配、但已无可调度 machine/candidate 时的明确结果。 */
public final class FjspInfeasibleException extends IllegalStateException {
  public FjspInfeasibleException(String message) {
    super(message);
  }
}
