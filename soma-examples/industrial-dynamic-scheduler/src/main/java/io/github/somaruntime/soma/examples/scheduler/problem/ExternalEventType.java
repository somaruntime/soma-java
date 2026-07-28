package io.github.somaruntime.soma.examples.scheduler.problem;

/** 调度运行期间可回放的外部事件类型。 */
public enum ExternalEventType {
  JOB_RELEASE(1),
  MATERIAL_READY(2),
  MACHINE_DELAY(3);

  private final int code;

  ExternalEventType(int code) {
    this.code = code;
  }

  int code() {
    return code;
  }
}
