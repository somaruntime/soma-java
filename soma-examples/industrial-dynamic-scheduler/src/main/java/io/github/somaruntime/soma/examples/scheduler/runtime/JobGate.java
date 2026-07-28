package io.github.somaruntime.soma.examples.scheduler.runtime;

/** release/material 两类外部事件的 application-owned publish 状态。 */
final class JobGate {
  boolean released;
  boolean materialReady;
  boolean initialOperationPublished;

  boolean readyToPublish() {
    return released && materialReady && !initialOperationPublished;
  }
}
