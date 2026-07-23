package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.result.ScheduleResult;

/** 已完成 runtime preparation 的一次性求解会话。 */
public interface SchedulingSession extends AutoCloseable {
  ScheduleResult solve();

  @Override
  void close();
}
