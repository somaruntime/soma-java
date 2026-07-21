package com.hgtech.soma.examples.vrp;

/** 尚有未分配 customer、但当前 hard constraints 下无可行 insertion。 */
public final class VrpInfeasibleException extends IllegalStateException {
  public VrpInfeasibleException(String message) {
    super(message);
  }
}
