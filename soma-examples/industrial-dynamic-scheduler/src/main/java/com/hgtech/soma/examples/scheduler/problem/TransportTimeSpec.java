package com.hgtech.soma.examples.scheduler.problem;

/** 两台机器之间的运输时长。 */
public final class TransportTimeSpec {
  public final long fromMachine;
  public final long toMachine;
  public final long minutes;

  public TransportTimeSpec(long fromMachine, long toMachine, long minutes) {
    this.fromMachine = fromMachine;
    this.toMachine = toMachine;
    this.minutes = minutes;
  }
}
