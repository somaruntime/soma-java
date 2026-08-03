package io.github.somaruntime.examples.scheduling.domain;

/** Business lifecycle owned by the scheduling application. */
public enum JobState {
    READY,
    RUNNING,
    COMPLETE,
    CANCELLED
}
