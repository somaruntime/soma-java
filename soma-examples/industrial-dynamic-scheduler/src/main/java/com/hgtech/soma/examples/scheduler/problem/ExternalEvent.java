package com.hgtech.soma.examples.scheduler.problem;

import java.util.Comparator;

/** 按时间与稳定 identity 排序的不可变外部事件。 */
public final class ExternalEvent {
  public static final Comparator<ExternalEvent> ORDER =
      new Comparator<ExternalEvent>() {
        @Override
        public int compare(ExternalEvent left, ExternalEvent right) {
          int byTime = Long.compare(left.minute, right.minute);
          if (byTime != 0) return byTime;
          int byType = Integer.compare(left.type.code(), right.type.code());
          if (byType != 0) return byType;
          int bySubject = Long.compare(left.subjectId, right.subjectId);
          if (bySubject != 0) return bySubject;
          return Long.compare(left.value, right.value);
        }
      };

  public final long minute;
  public final ExternalEventType type;
  public final long subjectId;
  public final long value;

  public ExternalEvent(long minute, ExternalEventType type,
                       long subjectId, long value) {
    if (type == null) throw new NullPointerException("type");
    this.minute = minute;
    this.type = type;
    this.subjectId = subjectId;
    this.value = value;
  }
}
