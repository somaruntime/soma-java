package com.hgtech.soma.examples.scheduler.runtime;

import java.util.Arrays;

/** application-owned、可由 assignment 重建的 secondary-resource lane calendar。 */
final class ResourceCalendar {
  private final long[] availableMinutes;

  ResourceCalendar(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    availableMinutes = new long[capacity];
  }

  int capacity() {
    return availableMinutes.length;
  }

  long earliestStart(int units) {
    requireUnits(units);
    return availableMinutes[units - 1];
  }

  void commit(long startMinute, long endMinute, int units) {
    requireUnits(units);
    if (startMinute < 0L || endMinute <= startMinute) {
      throw new IllegalArgumentException("invalid resource reservation");
    }
    for (int index = 0; index < units; index++) {
      if (availableMinutes[index] > startMinute) {
        throw new IllegalStateException(
            "resource lane is not available at selected start");
      }
    }
    for (int index = 0; index < units; index++) {
      availableMinutes[index] = endMinute;
    }
    Arrays.sort(availableMinutes);
  }

  long nextAvailableMinute() {
    return availableMinutes[0];
  }

  private void requireUnits(int units) {
    if (units <= 0 || units > availableMinutes.length) {
      throw new IllegalArgumentException("invalid resource unit demand");
    }
  }
}
