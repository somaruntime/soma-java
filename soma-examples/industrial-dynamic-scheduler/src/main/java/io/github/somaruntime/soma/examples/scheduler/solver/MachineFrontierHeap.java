package io.github.somaruntime.soma.examples.scheduler.solver;

import java.util.Arrays;

/**
 * 每台机器一个 candidate 代表项的全局最小堆。
 *
 * <p>dirty 项保存该机器当前最优 candidate 的保守下界；到达堆顶时，
 * CandidateFrontier 才扫描 primitive pool 中该机器的候选链并发布真实代表项。</p>
 */
final class MachineFrontierHeap {
  private final int[] positionsByMachine;
  private final int[] machineIndexes;
  private final boolean[] dirty;
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
  private final long[] setupStartMinutes;
  private final long[] effectiveStartMinutes;
  private final long[] completionMinutes;
  private final long[] dueMinutes;
  private final int[] priorities;
  private final long[] operationVersions;
  private final long[] machineVersions;
  private final long[] resourceVersions;
  private int size;

  MachineFrontierHeap(int machineCount) {
    if (machineCount <= 0) {
      throw new IllegalArgumentException("machineCount must be positive");
    }
    positionsByMachine = new int[machineCount];
    Arrays.fill(positionsByMachine, -1);
    machineIndexes = new int[machineCount];
    dirty = new boolean[machineCount];
    jobIds = new long[machineCount];
    operationIds = new long[machineCount];
    machineIds = new long[machineCount];
    targetSetupFamilies = new long[machineCount];
    resourceIds = new long[machineCount];
    resourceUnits = new int[machineCount];
    baseReadyMinutes = new long[machineCount];
    processingMinutes = new long[machineCount];
    setupMinutes = new long[machineCount];
    transportMinutes = new long[machineCount];
    setupStartMinutes = new long[machineCount];
    effectiveStartMinutes = new long[machineCount];
    completionMinutes = new long[machineCount];
    dueMinutes = new long[machineCount];
    priorities = new int[machineCount];
    operationVersions = new long[machineCount];
    machineVersions = new long[machineCount];
    resourceVersions = new long[machineCount];
  }

  boolean isEmpty() {
    return size == 0;
  }

  int bestMachineIndex() {
    requireNotEmpty();
    return machineIndexes[0];
  }

  long bestMachineId() {
    requireNotEmpty();
    return machineIds[0];
  }

  boolean bestIsDirty() {
    requireNotEmpty();
    return dirty[0];
  }

  void markDirty(int machineIndex) {
    requireMachine(machineIndex);
    int position = positionsByMachine[machineIndex];
    if (position >= 0) dirty[position] = true;
  }

  void consider(int machineIndex, SelectedCandidate candidate) {
    requireMachine(machineIndex);
    int position = positionsByMachine[machineIndex];
    if (position < 0 || better(candidate, position)) {
      publish(machineIndex, candidate);
    }
  }

  boolean represents(
      int machineIndex, long jobId, long operationId, long machineId) {
    requireMachine(machineIndex);
    int position = positionsByMachine[machineIndex];
    return position >= 0
        && !dirty[position]
        && jobIds[position] == jobId
        && operationIds[position] == operationId
        && machineIds[position] == machineId;
  }

  void publish(int machineIndex, SelectedCandidate candidate) {
    requireMachine(machineIndex);
    if (!candidate.present || candidate.machineId <= 0L) {
      throw new IllegalArgumentException(
          "candidate representative is absent");
    }
    int position = positionsByMachine[machineIndex];
    if (position < 0) {
      position = size++;
      positionsByMachine[machineIndex] = position;
      machineIndexes[position] = machineIndex;
    }
    dirty[position] = false;
    jobIds[position] = candidate.jobId;
    operationIds[position] = candidate.operationId;
    machineIds[position] = candidate.machineId;
    targetSetupFamilies[position] = candidate.targetSetupFamily;
    resourceIds[position] = candidate.resourceId;
    resourceUnits[position] = candidate.resourceUnits;
    baseReadyMinutes[position] = candidate.baseReadyMinute;
    processingMinutes[position] = candidate.processingMinutes;
    setupMinutes[position] = candidate.setupMinutes;
    transportMinutes[position] = candidate.transportMinutes;
    effectiveStartMinutes[position] =
        candidate.effectiveStartMinute;
    setupStartMinutes[position] = Math.subtractExact(
        candidate.effectiveStartMinute, candidate.setupMinutes);
    completionMinutes[position] = candidate.completionMinute;
    dueMinutes[position] = candidate.dueMinute;
    priorities[position] = candidate.priority;
    operationVersions[position] = candidate.operationVersion;
    machineVersions[position] = candidate.machineVersion;
    resourceVersions[position] = candidate.resourceVersion;
    repair(position);
  }

  void remove(int machineIndex) {
    requireMachine(machineIndex);
    int position = positionsByMachine[machineIndex];
    if (position < 0) return;
    positionsByMachine[machineIndex] = -1;
    int last = --size;
    if (position == last) return;
    copyPosition(last, position);
    positionsByMachine[machineIndexes[position]] = position;
    repair(position);
  }

  void copyBest(SelectedCandidate target) {
    requireNotEmpty();
    if (dirty[0]) {
      throw new IllegalStateException(
          "dirty machine representative cannot be selected");
    }
    copy(0, target);
  }

  void clear() {
    Arrays.fill(positionsByMachine, -1);
    size = 0;
  }

  private void repair(int position) {
    int current = siftUp(position);
    siftDown(current);
  }

  private int siftUp(int position) {
    int current = position;
    while (current > 0) {
      int parent = (current - 1) >>> 1;
      if (compare(parent, current) <= 0) break;
      swap(parent, current);
      current = parent;
    }
    return current;
  }

  private void siftDown(int position) {
    int current = position;
    while (true) {
      int left = (current << 1) + 1;
      if (left >= size) return;
      int right = left + 1;
      int smallest = right < size && compare(right, left) < 0
          ? right : left;
      if (compare(current, smallest) <= 0) return;
      swap(current, smallest);
      current = smallest;
    }
  }

  private int compare(int left, int right) {
    int result = Long.compare(
        setupStartMinutes[left], setupStartMinutes[right]);
    if (result != 0) return result;
    result = Long.compare(
        completionMinutes[left], completionMinutes[right]);
    if (result != 0) return result;
    result = Integer.compare(priorities[right], priorities[left]);
    if (result != 0) return result;
    result = Long.compare(dueMinutes[left], dueMinutes[right]);
    if (result != 0) return result;
    result = Long.compare(jobIds[left], jobIds[right]);
    if (result != 0) return result;
    result = Long.compare(operationIds[left], operationIds[right]);
    if (result != 0) return result;
    return Long.compare(machineIds[left], machineIds[right]);
  }

  private boolean better(
      SelectedCandidate candidate, int position) {
    long setupStart = Math.subtractExact(
        candidate.effectiveStartMinute, candidate.setupMinutes);
    int result = Long.compare(
        setupStart, setupStartMinutes[position]);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.completionMinute, completionMinutes[position]);
    if (result != 0) return result < 0;
    result = Integer.compare(
        priorities[position], candidate.priority);
    if (result != 0) return result < 0;
    result = Long.compare(candidate.dueMinute, dueMinutes[position]);
    if (result != 0) return result < 0;
    result = Long.compare(candidate.jobId, jobIds[position]);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.operationId, operationIds[position]);
    if (result != 0) return result < 0;
    return Long.compare(
        candidate.machineId, machineIds[position]) < 0;
  }

  private void swap(int left, int right) {
    swap(machineIndexes, left, right);
    swap(dirty, left, right);
    swap(jobIds, left, right);
    swap(operationIds, left, right);
    swap(machineIds, left, right);
    swap(targetSetupFamilies, left, right);
    swap(resourceIds, left, right);
    swap(resourceUnits, left, right);
    swap(baseReadyMinutes, left, right);
    swap(processingMinutes, left, right);
    swap(setupMinutes, left, right);
    swap(transportMinutes, left, right);
    swap(setupStartMinutes, left, right);
    swap(effectiveStartMinutes, left, right);
    swap(completionMinutes, left, right);
    swap(dueMinutes, left, right);
    swap(priorities, left, right);
    swap(operationVersions, left, right);
    swap(machineVersions, left, right);
    swap(resourceVersions, left, right);
    positionsByMachine[machineIndexes[left]] = left;
    positionsByMachine[machineIndexes[right]] = right;
  }

  private void copyPosition(int source, int target) {
    machineIndexes[target] = machineIndexes[source];
    dirty[target] = dirty[source];
    jobIds[target] = jobIds[source];
    operationIds[target] = operationIds[source];
    machineIds[target] = machineIds[source];
    targetSetupFamilies[target] = targetSetupFamilies[source];
    resourceIds[target] = resourceIds[source];
    resourceUnits[target] = resourceUnits[source];
    baseReadyMinutes[target] = baseReadyMinutes[source];
    processingMinutes[target] = processingMinutes[source];
    setupMinutes[target] = setupMinutes[source];
    transportMinutes[target] = transportMinutes[source];
    setupStartMinutes[target] = setupStartMinutes[source];
    effectiveStartMinutes[target] = effectiveStartMinutes[source];
    completionMinutes[target] = completionMinutes[source];
    dueMinutes[target] = dueMinutes[source];
    priorities[target] = priorities[source];
    operationVersions[target] = operationVersions[source];
    machineVersions[target] = machineVersions[source];
    resourceVersions[target] = resourceVersions[source];
  }

  private void copy(int position, SelectedCandidate target) {
    target.present = true;
    target.jobId = jobIds[position];
    target.operationId = operationIds[position];
    target.machineId = machineIds[position];
    target.targetSetupFamily = targetSetupFamilies[position];
    target.resourceId = resourceIds[position];
    target.resourceUnits = resourceUnits[position];
    target.baseReadyMinute = baseReadyMinutes[position];
    target.processingMinutes = processingMinutes[position];
    target.setupMinutes = setupMinutes[position];
    target.transportMinutes = transportMinutes[position];
    target.effectiveStartMinute = effectiveStartMinutes[position];
    target.completionMinute = completionMinutes[position];
    target.dueMinute = dueMinutes[position];
    target.priority = priorities[position];
    target.operationVersion = operationVersions[position];
    target.machineVersion = machineVersions[position];
    target.resourceVersion = resourceVersions[position];
  }

  private void requireMachine(int machineIndex) {
    if (machineIndex < 0
        || machineIndex >= positionsByMachine.length) {
      throw new IllegalArgumentException("unknown machine Index");
    }
  }

  private void requireNotEmpty() {
    if (size == 0) {
      throw new IllegalStateException(
          "machine frontier heap is empty");
    }
  }

  private static void swap(long[] values, int left, int right) {
    long value = values[left];
    values[left] = values[right];
    values[right] = value;
  }

  private static void swap(int[] values, int left, int right) {
    int value = values[left];
    values[left] = values[right];
    values[right] = value;
  }

  private static void swap(
      boolean[] values, int left, int right) {
    boolean value = values[left];
    values[left] = values[right];
    values[right] = value;
  }
}
