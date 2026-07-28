package io.github.somaruntime.soma.examples.rtd.benchmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** Zulu JDK 8 caller-thread allocation 与 process GC 快照。 */
final class JvmMetrics {
  private static final java.lang.management.ThreadMXBean THREAD_BEAN =
      ManagementFactory.getThreadMXBean();
  private static final Method ALLOCATION_SUPPORTED =
      method("isThreadAllocatedMemorySupported");
  private static final Method ALLOCATION_ENABLED =
      method("isThreadAllocatedMemoryEnabled");
  private static final Method ENABLE_ALLOCATION =
      method("setThreadAllocatedMemoryEnabled", boolean.class);
  private static final Method THREAD_ALLOCATED =
      method("getThreadAllocatedBytes", long.class);

  private JvmMetrics() {
  }

  static long callerAllocatedBytes() {
    try {
      if (!((Boolean) ALLOCATION_SUPPORTED.invoke(THREAD_BEAN))
          .booleanValue()) {
        throw new IllegalStateException(
            "thread allocation measurement is unsupported");
      }
      if (!((Boolean) ALLOCATION_ENABLED.invoke(THREAD_BEAN))
          .booleanValue()) {
        ENABLE_ALLOCATION.invoke(THREAD_BEAN, Boolean.TRUE);
      }
      long bytes = ((Long) THREAD_ALLOCATED.invoke(
          THREAD_BEAN,
          Long.valueOf(Thread.currentThread().getId()))).longValue();
      if (bytes < 0L) {
        throw new IllegalStateException(
            "thread allocation measurement is unavailable");
      }
      return bytes;
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException(
          "cannot access allocation counter", failure);
    } catch (InvocationTargetException failure) {
      throw new IllegalStateException(
          "allocation counter failed", failure.getCause());
    }
  }

  static GcSnapshot gcSnapshot() {
    long youngCount = 0L;
    long youngMillis = 0L;
    long fullCount = 0L;
    long fullMillis = 0L;
    List<GarbageCollectorMXBean> collectors =
        ManagementFactory.getGarbageCollectorMXBeans();
    for (GarbageCollectorMXBean collector : collectors) {
      long count = Math.max(0L, collector.getCollectionCount());
      long millis = Math.max(0L, collector.getCollectionTime());
      String name = collector.getName().toLowerCase(Locale.ROOT);
      if (name.contains("young") || name.contains("scavenge")
          || name.contains("new") || name.contains("copy")) {
        youngCount = Math.addExact(youngCount, count);
        youngMillis = Math.addExact(youngMillis, millis);
      } else {
        fullCount = Math.addExact(fullCount, count);
        fullMillis = Math.addExact(fullMillis, millis);
      }
    }
    return new GcSnapshot(
        youngCount, youngMillis, fullCount, fullMillis);
  }

  private static Method method(
      String name, Class<?>... parameterTypes) {
    try {
      Class<?> type =
          Class.forName("com.sun.management.ThreadMXBean");
      return type.getMethod(name, parameterTypes);
    } catch (Exception failure) {
      throw new IllegalStateException(
          "Zulu JDK allocation API is unavailable: " + name,
          failure);
    }
  }

  static final class GcSnapshot {
    final long youngCount;
    final long youngMillis;
    final long fullCount;
    final long fullMillis;

    GcSnapshot(
        long youngCount,
        long youngMillis,
        long fullCount,
        long fullMillis) {
      this.youngCount = youngCount;
      this.youngMillis = youngMillis;
      this.fullCount = fullCount;
      this.fullMillis = fullMillis;
    }
  }
}
