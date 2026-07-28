package io.github.somaruntime.soma.benchmarks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/** 单线程分配量与 JVM GC 计数/暂停时间的诊断采集器。 */
final class JvmRuntimeMetrics {
  private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN =
    allocationBean();
  private static final String COLLECTOR_NAMES = collectorNames();

  private JvmRuntimeMetrics() {
  }

  static Snapshot snapshot() {
    long allocatedBytes = -1L;
    if (ALLOCATION_BEAN != null) {
      allocatedBytes = ALLOCATION_BEAN.getThreadAllocatedBytes(
        Thread.currentThread().getId());
    }

    long youngCount = 0L;
    long youngTimeMillis = 0L;
    long fullCount = 0L;
    long fullTimeMillis = 0L;
    long unknownCount = 0L;
    long unknownTimeMillis = 0L;
    List<GarbageCollectorMXBean> collectors =
      ManagementFactory.getGarbageCollectorMXBeans();
    for (GarbageCollectorMXBean collector : collectors) {
      long count = nonNegative(collector.getCollectionCount());
      long timeMillis = nonNegative(collector.getCollectionTime());
      int generation = generation(collector.getName());
      if (generation == 1) {
        youngCount += count;
        youngTimeMillis += timeMillis;
      } else if (generation == 2) {
        fullCount += count;
        fullTimeMillis += timeMillis;
      } else {
        unknownCount += count;
        unknownTimeMillis += timeMillis;
      }
    }
    return new Snapshot(allocatedBytes, youngCount, youngTimeMillis,
      fullCount, fullTimeMillis, unknownCount, unknownTimeMillis);
  }

  static String allocationMethod() {
    return ALLOCATION_BEAN == null
      ? "not-observed"
      : "thread-mxbean-current-thread-exact";
  }

  static long allLiveThreadAllocatedBytes() {
    if (ALLOCATION_BEAN == null) {
      return -1L;
    }
    long[] values = ALLOCATION_BEAN.getThreadAllocatedBytes(
      ALLOCATION_BEAN.getAllThreadIds());
    long total = 0L;
    for (long value : values) {
      if (value < 0L) {
        continue;
      }
      if (total > Long.MAX_VALUE - value) {
        throw new IllegalStateException(
          "live thread allocation observation overflow");
      }
      total += value;
    }
    return total;
  }

  static String allLiveThreadAllocationMethod() {
    return ALLOCATION_BEAN == null
      ? "not-observed"
      : "thread-mxbean-all-live-threads";
  }

  static String collectorNamesValue() {
    return COLLECTOR_NAMES;
  }

  private static com.sun.management.ThreadMXBean allocationBean() {
    java.lang.management.ThreadMXBean bean =
      ManagementFactory.getThreadMXBean();
    if (!(bean instanceof com.sun.management.ThreadMXBean)) {
      return null;
    }
    com.sun.management.ThreadMXBean allocationBean =
      (com.sun.management.ThreadMXBean) bean;
    if (!allocationBean.isThreadAllocatedMemorySupported()) {
      return null;
    }
    try {
      if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
        allocationBean.setThreadAllocatedMemoryEnabled(true);
      }
      return allocationBean.isThreadAllocatedMemoryEnabled()
        ? allocationBean : null;
    } catch (SecurityException exception) {
      return null;
    }
  }

  private static String collectorNames() {
    StringBuilder names = new StringBuilder();
    for (GarbageCollectorMXBean collector
        : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (names.length() != 0) {
        names.append(',');
      }
      names.append(collector.getName());
    }
    return names.toString();
  }

  private static int generation(String collectorName) {
    String normalized = collectorName.toLowerCase(Locale.ROOT);
    if (normalized.contains("young") || normalized.contains("scavenge")
        || normalized.contains("new") || normalized.contains("copy")
        || normalized.contains("parnew")) {
      return 1;
    }
    if (normalized.contains("old") || normalized.contains("mark")
        || normalized.contains("tenured") || normalized.contains("full")) {
      return 2;
    }
    return 0;
  }

  private static long nonNegative(long value) {
    return value < 0L ? 0L : value;
  }

  static final class Snapshot {
    private final long allocatedBytes;
    private final long youngGcCount;
    private final long youngGcTimeMillis;
    private final long fullGcCount;
    private final long fullGcTimeMillis;
    private final long unknownGcCount;
    private final long unknownGcTimeMillis;

    Snapshot(long allocatedBytes, long youngGcCount, long youngGcTimeMillis,
             long fullGcCount, long fullGcTimeMillis, long unknownGcCount,
             long unknownGcTimeMillis) {
      this.allocatedBytes = allocatedBytes;
      this.youngGcCount = youngGcCount;
      this.youngGcTimeMillis = youngGcTimeMillis;
      this.fullGcCount = fullGcCount;
      this.fullGcTimeMillis = fullGcTimeMillis;
      this.unknownGcCount = unknownGcCount;
      this.unknownGcTimeMillis = unknownGcTimeMillis;
    }

    Delta since(Snapshot start) {
      long allocationDelta = allocatedBytes < 0L || start.allocatedBytes < 0L
        ? -1L : Math.max(0L, allocatedBytes - start.allocatedBytes);
      return new Delta(allocationDelta,
        Math.max(0L, youngGcCount - start.youngGcCount),
        Math.max(0L, youngGcTimeMillis - start.youngGcTimeMillis),
        Math.max(0L, fullGcCount - start.fullGcCount),
        Math.max(0L, fullGcTimeMillis - start.fullGcTimeMillis),
        Math.max(0L, unknownGcCount - start.unknownGcCount),
        Math.max(0L, unknownGcTimeMillis - start.unknownGcTimeMillis));
    }
  }

  static final class Delta {
    final long allocatedBytes;
    final long youngGcCount;
    final long youngGcTimeMillis;
    final long fullGcCount;
    final long fullGcTimeMillis;
    final long unknownGcCount;
    final long unknownGcTimeMillis;

    Delta(long allocatedBytes, long youngGcCount, long youngGcTimeMillis,
          long fullGcCount, long fullGcTimeMillis, long unknownGcCount,
          long unknownGcTimeMillis) {
      this.allocatedBytes = allocatedBytes;
      this.youngGcCount = youngGcCount;
      this.youngGcTimeMillis = youngGcTimeMillis;
      this.fullGcCount = fullGcCount;
      this.fullGcTimeMillis = fullGcTimeMillis;
      this.unknownGcCount = unknownGcCount;
      this.unknownGcTimeMillis = unknownGcTimeMillis;
    }
  }
}
