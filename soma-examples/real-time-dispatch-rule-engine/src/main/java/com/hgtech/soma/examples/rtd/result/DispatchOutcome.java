package com.hgtech.soma.examples.rtd.result;

/** 成功调用的 detached domain Result 与 diagnostics。 */
public final class DispatchOutcome {
  private final DispatchResult result;
  private final DispatchDiagnostics diagnostics;

  public DispatchOutcome(
      DispatchResult result, DispatchDiagnostics diagnostics) {
    if (result == null) throw new NullPointerException("result");
    if (diagnostics == null) {
      throw new NullPointerException("diagnostics");
    }
    this.result = result;
    this.diagnostics = diagnostics;
  }

  public DispatchResult result() { return result; }
  public DispatchDiagnostics diagnostics() { return diagnostics; }
}
