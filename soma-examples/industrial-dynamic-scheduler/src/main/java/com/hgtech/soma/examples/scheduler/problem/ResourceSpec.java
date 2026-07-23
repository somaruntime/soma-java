package com.hgtech.soma.examples.scheduler.problem;

/** 一种可再生次级资源的不可变输入定义。 */
public final class ResourceSpec {
  public final long id;
  public final int capacity;

  public ResourceSpec(long id, int capacity) {
    this.id = id;
    this.capacity = capacity;
  }
}
