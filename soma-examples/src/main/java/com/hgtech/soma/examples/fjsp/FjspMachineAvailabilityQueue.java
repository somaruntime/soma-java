package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineTable;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * FJSP application拥有的indexed minimum heap，不是SOMA Table或generated API。
 *
 * <p>heap只保存由Machine事实派生的选择顺序；MachineTable仍是available time的
 * 权威Owner。slot由MachineId映射，不长期保存SOMA物理Index。</p>
 */
final class FjspMachineAvailabilityQueue {
  private static final int INACTIVE = -1;

  private final MachineId[] machineIds;
  private final long[] availableFromMinutes;
  private final int[] heap;
  private final int[] positions;
  private final Map<MachineId, Integer> slots;
  private int size;

  FjspMachineAvailabilityQueue(MachineTable machines) {
    if (machines == null) throw new NullPointerException("machines");
    int machineCount = machines.size();
    machineIds = new MachineId[machineCount];
    availableFromMinutes = new long[machineCount];
    heap = new int[machineCount];
    positions = new int[machineCount];
    Arrays.fill(positions, INACTIVE);
    slots = new HashMap<MachineId, Integer>(mapCapacity(machineCount));

    LongColumnView ids = machines.machineIdValueColumn();
    LongColumnView available = machines.availableFromMinuteColumn();
    try {
      // 同步只读批次内立即消费当前packed Index，不在queue中保存这些Index。
      for (int index = 0; index < machineCount; index++) {
        MachineId machineId = new MachineId(ids.getLong(index));
        machineIds[index] = machineId;
        availableFromMinutes[index] = available.getLong(index);
        if (slots.put(machineId, Integer.valueOf(index)) != null) {
          throw new IllegalStateException("duplicate machine identity in queue");
        }
      }
    } finally {
      available.close();
      ids.close();
    }
  }

  void activate(MachineId machineId) {
    int slot = slotOf(machineId);
    if (positions[slot] != INACTIVE) return;
    if (size >= heap.length) {
      throw new IllegalStateException("machine queue capacity exhausted");
    }
    int position = size++;
    heap[position] = slot;
    positions[slot] = position;
    siftUp(position);
  }

  int take() {
    if (size == 0) {
      throw new IllegalStateException("machine queue is empty");
    }
    int selected = heap[0];
    positions[selected] = INACTIVE;
    int remaining = --size;
    if (remaining != 0) {
      int replacement = heap[remaining];
      heap[0] = replacement;
      positions[replacement] = 0;
      siftDown(0);
    }
    return selected;
  }

  void updateInactive(MachineId machineId, long availableFromMinute) {
    int slot = slotOf(machineId);
    if (positions[slot] != INACTIVE) {
      throw new IllegalStateException("active machine availability cannot change");
    }
    availableFromMinutes[slot] = availableFromMinute;
  }

  boolean isEmpty() {
    return size == 0;
  }

  MachineId machineId(int slot) {
    requireSlot(slot);
    return machineIds[slot];
  }

  long availableFromMinute(int slot) {
    requireSlot(slot);
    return availableFromMinutes[slot];
  }

  private int slotOf(MachineId machineId) {
    if (machineId == null) throw new NullPointerException("machineId");
    Integer slot = slots.get(machineId);
    if (slot == null) {
      throw new IllegalArgumentException("unknown machine identity");
    }
    return slot.intValue();
  }

  private void siftUp(int position) {
    int slot = heap[position];
    while (position > 0) {
      int parent = (position - 1) >>> 1;
      int parentSlot = heap[parent];
      if (compare(parentSlot, slot) <= 0) break;
      heap[position] = parentSlot;
      positions[parentSlot] = position;
      position = parent;
    }
    heap[position] = slot;
    positions[slot] = position;
  }

  private void siftDown(int position) {
    int slot = heap[position];
    int half = size >>> 1;
    while (position < half) {
      int child = (position << 1) + 1;
      int childSlot = heap[child];
      int right = child + 1;
      if (right < size && compare(heap[right], childSlot) < 0) {
        child = right;
        childSlot = heap[right];
      }
      if (compare(slot, childSlot) <= 0) break;
      heap[position] = childSlot;
      positions[childSlot] = position;
      position = child;
    }
    heap[position] = slot;
    positions[slot] = position;
  }

  private int compare(int left, int right) {
    int byAvailable = Long.compare(
      availableFromMinutes[left], availableFromMinutes[right]);
    return byAvailable != 0 ? byAvailable
      : Long.compare(machineIds[left].value, machineIds[right].value);
  }

  private void requireSlot(int slot) {
    if (slot < 0 || slot >= machineIds.length) {
      throw new IllegalArgumentException("machine queue slot out of range");
    }
  }

  private static int mapCapacity(int size) {
    if (size < 3) return 4;
    long required = ((long) size * 4L + 2L) / 3L;
    return required >= (1L << 30) ? 1 << 30 : (int) required;
  }
}
