package com.hgtech.soma.examples.fjsp;

/** 单次 operation release 产生的、solver-owned primitive machine scratch。 */
final class FjspReleasedMachines {
  private final long[] machineIds;
  private int size;

  FjspReleasedMachines(int maximumMachines) {
    if (maximumMachines <= 0) {
      throw new IllegalArgumentException("maximumMachines must be positive");
    }
    machineIds = new long[maximumMachines];
  }

  void reset() {
    size = 0;
  }

  void add(long machineId) {
    if (size == machineIds.length) {
      throw new IllegalStateException("released-machine scratch exhausted");
    }
    machineIds[size++] = machineId;
  }

  int size() {
    return size;
  }

  long machineIdValue(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("released machine index");
    }
    return machineIds[index];
  }

}
