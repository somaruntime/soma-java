package io.github.somaruntime.soma.examples.scheduler.solver;

import java.util.Arrays;

/**
 * 求解器内部、固定容量的 primitive candidate pool。
 *
 * <p>candidate 是可由 SOMA authoritative state 重建的短命算法投影，
 * 因此不进入 runtime Table graph。本结构同时维护 candidate key 定位和
 * machine-local intrusive list，不创建逐 candidate 对象。</p>
 */
final class CandidatePool {
  private final int[] machineHeads;
  private final int[] previousByMachine;
  private final int[] nextByMachine;
  private final int[] nextFree;
  private final int[] machineIndexes;
  private final int[] resourceIndexes;
  private final long[] jobIds;
  private final long[] operationIds;
  private final long[] machineIds;
  private final long[] targetSetupFamilies;
  private final long[] resourceIds;
  private final int[] resourceUnits;
  private final long[] baseReadyMinutes;
  private final long[] processingMinutes;
  private final long[] setupMinutes;
  private final long[] transportMinutes;
  private final long[] effectiveStartMinutes;
  private final long[] completionMinutes;
  private final long[] dueMinutes;
  private final int[] priorities;
  private final long[] operationVersions;
  private final long[] machineVersions;
  private final long[] resourceVersions;

  private final byte[] keyStates;
  private final long[] keyJobIds;
  private final long[] keyOperationIds;
  private final long[] keyMachineIds;
  private final int[] keyCandidateSlots;
  private final int keyMask;
  private int freeHead;
  private int size;

  CandidatePool(int capacity, int machineCount) {
    if (capacity <= 0 || machineCount <= 0) {
      throw new IllegalArgumentException(
          "capacity and machineCount must be positive");
    }
    machineHeads = new int[machineCount];
    Arrays.fill(machineHeads, -1);
    previousByMachine = new int[capacity];
    nextByMachine = new int[capacity];
    nextFree = new int[capacity];
    machineIndexes = new int[capacity];
    resourceIndexes = new int[capacity];
    jobIds = new long[capacity];
    operationIds = new long[capacity];
    machineIds = new long[capacity];
    targetSetupFamilies = new long[capacity];
    resourceIds = new long[capacity];
    resourceUnits = new int[capacity];
    baseReadyMinutes = new long[capacity];
    processingMinutes = new long[capacity];
    setupMinutes = new long[capacity];
    transportMinutes = new long[capacity];
    effectiveStartMinutes = new long[capacity];
    completionMinutes = new long[capacity];
    dueMinutes = new long[capacity];
    priorities = new int[capacity];
    operationVersions = new long[capacity];
    machineVersions = new long[capacity];
    resourceVersions = new long[capacity];
    for (int index = 0; index < capacity - 1; index++) {
      nextFree[index] = index + 1;
    }
    nextFree[capacity - 1] = -1;

    int keyCapacity = 1;
    int requiredKeys = Math.multiplyExact(capacity, 2);
    while (keyCapacity < requiredKeys) {
      keyCapacity = Math.multiplyExact(keyCapacity, 2);
    }
    keyStates = new byte[keyCapacity];
    keyJobIds = new long[keyCapacity];
    keyOperationIds = new long[keyCapacity];
    keyMachineIds = new long[keyCapacity];
    keyCandidateSlots = new int[keyCapacity];
    keyMask = keyCapacity - 1;
  }

  int size() {
    return size;
  }

  boolean contains(
      long jobId, long operationId, long machineId) {
    return findKey(jobId, operationId, machineId) >= 0;
  }

  void add(
      SelectedCandidate candidate,
      int machineIndex,
      int resourceIndex) {
    requireMachine(machineIndex);
    if (!candidate.present || freeHead < 0) {
      throw new IllegalStateException(
          "candidate pool is absent or full");
    }
    if (findKey(candidate.jobId, candidate.operationId,
        candidate.machineId) >= 0) {
      throw new IllegalStateException("duplicate candidate key");
    }
    int slot = freeHead;
    freeHead = nextFree[slot];
    size++;
    machineIndexes[slot] = machineIndex;
    resourceIndexes[slot] = resourceIndex;
    copyFrom(slot, candidate);
    previousByMachine[slot] = -1;
    nextByMachine[slot] = machineHeads[machineIndex];
    if (nextByMachine[slot] >= 0) {
      previousByMachine[nextByMachine[slot]] = slot;
    }
    machineHeads[machineIndex] = slot;
    putKey(candidate.jobId, candidate.operationId,
        candidate.machineId, slot);
  }

  void remove(long jobId, long operationId, long machineId) {
    int key = findKey(jobId, operationId, machineId);
    if (key < 0) {
      throw new IllegalStateException(
          "candidate pool is missing a candidate");
    }
    int slot = keyCandidateSlots[key];
    removeKey(key);
    int machineIndex = machineIndexes[slot];
    int previous = previousByMachine[slot];
    int next = nextByMachine[slot];
    if (previous < 0) {
      machineHeads[machineIndex] = next;
    } else {
      nextByMachine[previous] = next;
    }
    if (next >= 0) {
      previousByMachine[next] = previous;
    }
    nextFree[slot] = freeHead;
    freeHead = slot;
    size--;
  }

  int firstByMachine(int machineIndex) {
    requireMachine(machineIndex);
    return machineHeads[machineIndex];
  }

  int nextByMachine(int slot) {
    requireSlot(slot);
    return nextByMachine[slot];
  }

  int resourceIndex(int slot) {
    requireSlot(slot);
    return resourceIndexes[slot];
  }

  void copyTo(int slot, SelectedCandidate target) {
    requireSlot(slot);
    target.present = true;
    target.jobId = jobIds[slot];
    target.operationId = operationIds[slot];
    target.machineId = machineIds[slot];
    target.targetSetupFamily = targetSetupFamilies[slot];
    target.resourceId = resourceIds[slot];
    target.resourceUnits = resourceUnits[slot];
    target.baseReadyMinute = baseReadyMinutes[slot];
    target.processingMinutes = processingMinutes[slot];
    target.setupMinutes = setupMinutes[slot];
    target.transportMinutes = transportMinutes[slot];
    target.effectiveStartMinute = effectiveStartMinutes[slot];
    target.completionMinute = completionMinutes[slot];
    target.dueMinute = dueMinutes[slot];
    target.priority = priorities[slot];
    target.operationVersion = operationVersions[slot];
    target.machineVersion = machineVersions[slot];
    target.resourceVersion = resourceVersions[slot];
  }

  void updateFrom(int slot, SelectedCandidate candidate) {
    requireSlot(slot);
    if (jobIds[slot] != candidate.jobId
        || operationIds[slot] != candidate.operationId
        || machineIds[slot] != candidate.machineId) {
      throw new IllegalStateException(
          "candidate identity changed during refresh");
    }
    setupMinutes[slot] = candidate.setupMinutes;
    effectiveStartMinutes[slot] =
        candidate.effectiveStartMinute;
    completionMinutes[slot] = candidate.completionMinute;
    machineVersions[slot] = candidate.machineVersion;
    resourceVersions[slot] = candidate.resourceVersion;
  }

  private void copyFrom(
      int slot, SelectedCandidate candidate) {
    jobIds[slot] = candidate.jobId;
    operationIds[slot] = candidate.operationId;
    machineIds[slot] = candidate.machineId;
    targetSetupFamilies[slot] = candidate.targetSetupFamily;
    resourceIds[slot] = candidate.resourceId;
    resourceUnits[slot] = candidate.resourceUnits;
    baseReadyMinutes[slot] = candidate.baseReadyMinute;
    processingMinutes[slot] = candidate.processingMinutes;
    setupMinutes[slot] = candidate.setupMinutes;
    transportMinutes[slot] = candidate.transportMinutes;
    effectiveStartMinutes[slot] =
        candidate.effectiveStartMinute;
    completionMinutes[slot] = candidate.completionMinute;
    dueMinutes[slot] = candidate.dueMinute;
    priorities[slot] = candidate.priority;
    operationVersions[slot] = candidate.operationVersion;
    machineVersions[slot] = candidate.machineVersion;
    resourceVersions[slot] = candidate.resourceVersion;
  }

  private int findKey(
      long jobId, long operationId, long machineId) {
    int key = hash(jobId, operationId, machineId) & keyMask;
    while (keyStates[key] != 0) {
      if (keyJobIds[key] == jobId
          && keyOperationIds[key] == operationId
          && keyMachineIds[key] == machineId) {
        return key;
      }
      key = (key + 1) & keyMask;
    }
    return -1;
  }

  private void putKey(
      long jobId, long operationId, long machineId, int slot) {
    int key = hash(jobId, operationId, machineId) & keyMask;
    while (keyStates[key] != 0) {
      key = (key + 1) & keyMask;
    }
    keyStates[key] = 1;
    keyJobIds[key] = jobId;
    keyOperationIds[key] = operationId;
    keyMachineIds[key] = machineId;
    keyCandidateSlots[key] = slot;
  }

  private void removeKey(int key) {
    keyStates[key] = 0;
    int next = (key + 1) & keyMask;
    while (keyStates[next] != 0) {
      long jobId = keyJobIds[next];
      long operationId = keyOperationIds[next];
      long machineId = keyMachineIds[next];
      int slot = keyCandidateSlots[next];
      keyStates[next] = 0;
      putKey(jobId, operationId, machineId, slot);
      next = (next + 1) & keyMask;
    }
  }

  private void requireMachine(int machineIndex) {
    if (machineIndex < 0 || machineIndex >= machineHeads.length) {
      throw new IllegalArgumentException("unknown machine Index");
    }
  }

  private void requireSlot(int slot) {
    if (slot < 0 || slot >= nextFree.length) {
      throw new IllegalArgumentException("unknown candidate slot");
    }
  }

  private static int hash(
      long jobId, long operationId, long machineId) {
    long value = jobId * 0x9E3779B97F4A7C15L;
    value ^= Long.rotateLeft(
        operationId * 0xC2B2AE3D27D4EB4FL, 21);
    value ^= Long.rotateLeft(
        machineId * 0x165667B19E3779F9L, 42);
    value ^= value >>> 33;
    value *= 0xFF51AFD7ED558CCDL;
    value ^= value >>> 33;
    return (int) value;
  }
}
