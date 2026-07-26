package com.hgtech.soma.dataflow.prototype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * 仅供 Temporary 使用的 Java 8 架构原型。
 *
 * <p>候选 public role 被有意压缩为 nested type；这里只验证 lifecycle 和依赖可行性，
 * 不是 production API 或 runtime。</p>
 */
public final class PrototypeDataFlow {
  private PrototypeDataFlow() {
  }

  public interface IntPredicate {
    boolean test(int value);
  }

  public interface CancellationToken {
    boolean isCancelled();
  }

  public interface GeneratedBinding {
    long aggregateInstanceId();

    String schemaIdentity();

    int[] packedValues();

    void acquire();

    void release();
  }

  public static final class SharedScratch {
    private int remainingConsumers;
    private boolean released;

    public SharedScratch(int consumerCount) {
      if (consumerCount <= 0) {
        throw new IllegalArgumentException("consumerCount");
      }
      remainingConsumers = consumerCount;
    }

    public synchronized void releaseConsumer() {
      if (remainingConsumers <= 0) {
        throw new IllegalStateException("shared scratch already released");
      }
      remainingConsumers--;
      released = remainingConsumers == 0;
    }

    public synchronized boolean isReleased() {
      return released;
    }
  }

  public static final class SourceSlot<B extends GeneratedBinding> {
    private final String name;
    private final String schemaIdentity;

    public SourceSlot(String name, String schemaIdentity) {
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("name");
      }
      if (schemaIdentity == null || schemaIdentity.isEmpty()) {
        throw new IllegalArgumentException("schemaIdentity");
      }
      this.name = name;
      this.schemaIdentity = schemaIdentity;
    }

    public String name() {
      return name;
    }

    String schemaIdentity() {
      return schemaIdentity;
    }
  }

  public static final class DataFlowDefinition {
    private final SourceSlot<? extends GeneratedBinding> candidateSource;
    private final List<SourceSlot<? extends GeneratedBinding>> requiredSources;
    private final IntPredicate predicate;

    private DataFlowDefinition(
        SourceSlot<? extends GeneratedBinding> candidateSource,
        List<SourceSlot<? extends GeneratedBinding>> requiredSources,
        IntPredicate predicate) {
      this.candidateSource = candidateSource;
      this.requiredSources = requiredSources;
      this.predicate = predicate;
    }

    @SafeVarargs
    public static DataFlowDefinition define(
        SourceSlot<? extends GeneratedBinding> candidateSource,
        IntPredicate predicate,
        SourceSlot<? extends GeneratedBinding>... requiredSources) {
      if (candidateSource == null || predicate == null || requiredSources == null) {
        throw new NullPointerException();
      }
      List<SourceSlot<? extends GeneratedBinding>> sources =
          new ArrayList<SourceSlot<? extends GeneratedBinding>>(requiredSources.length);
      IdentityHashMap<SourceSlot<? extends GeneratedBinding>, Boolean> seen =
          new IdentityHashMap<SourceSlot<? extends GeneratedBinding>, Boolean>();
      for (SourceSlot<? extends GeneratedBinding> source : requiredSources) {
        if (source == null || seen.put(source, Boolean.TRUE) != null) {
          throw new IllegalArgumentException("required source must be non-null and unique");
        }
        sources.add(source);
      }
      if (!seen.containsKey(candidateSource)) {
        throw new IllegalArgumentException("candidate source must be required");
      }
      return new DataFlowDefinition(
          candidateSource, Collections.unmodifiableList(sources), predicate);
    }

    public DataFlowTemplate compile() {
      return new DataFlowTemplate(this);
    }

    public IntPredicate retainedPredicate() {
      return predicate;
    }
  }

  public static final class DataFlowTemplate {
    private final DataFlowDefinition definition;

    private DataFlowTemplate(DataFlowDefinition definition) {
      this.definition = definition;
    }

    public DataFlowInvocation newInvocation(
        DataFlowContext context, boolean adaptiveParallel, CancellationToken cancellation) {
      return new DataFlowInvocation(this, context, adaptiveParallel, cancellation);
    }
  }

  public static final class DataFlowContext implements AutoCloseable {
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final int workers;
    private boolean closed;
    private int activeInvocations;

    private DataFlowContext(ExecutorService executor, boolean ownsExecutor, int workers) {
      this.executor = executor;
      this.ownsExecutor = ownsExecutor;
      this.workers = workers;
    }

    public static DataFlowContext managed(int workers) {
      if (workers <= 0) {
        throw new IllegalArgumentException("workers");
      }
      return new DataFlowContext(new ForkJoinPool(workers), true, workers);
    }

    public static DataFlowContext borrowed(ExecutorService executor, int workers) {
      if (executor == null || workers <= 0) {
        throw new IllegalArgumentException("executor/workers");
      }
      return new DataFlowContext(executor, false, workers);
    }

    synchronized void begin() {
      if (closed) {
        throw new IllegalStateException("context closed");
      }
      activeInvocations++;
    }

    synchronized void end() {
      if (activeInvocations <= 0) {
        throw new IllegalStateException("active invocation underflow");
      }
      activeInvocations--;
    }

    ExecutorService executor() {
      return executor;
    }

    int workers() {
      return workers;
    }

    public synchronized boolean ownsExecutor() {
      return ownsExecutor;
    }

    public synchronized boolean isClosed() {
      return closed;
    }

    @Override
    public synchronized void close() {
      if (activeInvocations != 0) {
        throw new IllegalStateException("context busy");
      }
      if (!closed) {
        closed = true;
        if (ownsExecutor) {
          executor.shutdown();
        }
      }
    }
  }

  public static final class DataFlowInvocation {
    private enum State {
      NEW,
      RUNNING,
      COMPLETED,
      FAILED,
      CANCELLED
    }

    private static final int PARTITION_SIZE = 256;

    private final DataFlowTemplate template;
    private final DataFlowContext context;
    private final boolean adaptiveParallel;
    private final CancellationToken cancellation;
    private final IdentityHashMap<SourceSlot<?>, GeneratedBinding> bindings =
        new IdentityHashMap<SourceSlot<?>, GeneratedBinding>();
    private State state = State.NEW;

    private DataFlowInvocation(
        DataFlowTemplate template,
        DataFlowContext context,
        boolean adaptiveParallel,
        CancellationToken cancellation) {
      if (template == null || context == null || cancellation == null) {
        throw new NullPointerException();
      }
      this.template = template;
      this.context = context;
      this.adaptiveParallel = adaptiveParallel;
      this.cancellation = cancellation;
    }

    public <B extends GeneratedBinding> DataFlowInvocation bind(SourceSlot<B> slot, B binding) {
      ensureNew();
      if (slot == null || binding == null) {
        throw new NullPointerException();
      }
      if (bindings.put(slot, binding) != null) {
        throw new IllegalStateException("slot already bound: " + slot.name());
      }
      return this;
    }

    public int executeCount() {
      ensureNew();
      state = State.RUNNING;
      context.begin();
      List<GeneratedBinding> acquired = new ArrayList<GeneratedBinding>();
      try {
        Map<Long, GeneratedBinding> canonical = resolveAndValidate();
        for (GeneratedBinding binding : canonical.values()) {
          binding.acquire();
          acquired.add(binding);
        }
        if (cancellation.isCancelled()) {
          state = State.CANCELLED;
          throw new IllegalStateException("cancelled");
        }
        GeneratedBinding candidate = bindings.get(template.definition.candidateSource);
        int result = adaptiveParallel && context.workers() > 1
            ? executeParallel(candidate.packedValues(), template.definition.predicate)
            : executeDirect(candidate.packedValues(), template.definition.predicate);
        state = State.COMPLETED;
        return result;
      } catch (RuntimeException failure) {
        if (state != State.CANCELLED) {
          state = State.FAILED;
        }
        throw failure;
      } finally {
        for (int i = acquired.size() - 1; i >= 0; i--) {
          acquired.get(i).release();
        }
        context.end();
      }
    }

    private Map<Long, GeneratedBinding> resolveAndValidate() {
      TreeMap<Long, GeneratedBinding> canonical = new TreeMap<Long, GeneratedBinding>();
      for (SourceSlot<? extends GeneratedBinding> slot
          : template.definition.requiredSources) {
        GeneratedBinding binding = bindings.get(slot);
        if (binding == null) {
          throw new IllegalStateException("missing binding: " + slot.name());
        }
        if (!slot.schemaIdentity().equals(binding.schemaIdentity())) {
          throw new IllegalStateException("schema mismatch: " + slot.name());
        }
        GeneratedBinding previous =
            canonical.put(binding.aggregateInstanceId(), binding);
        if (previous != null && previous != binding) {
          throw new IllegalStateException("aggregate identity collision");
        }
      }
      if (bindings.size() != template.definition.requiredSources.size()) {
        throw new IllegalStateException("extra binding");
      }
      return canonical;
    }

    private int executeParallel(final int[] values, final IntPredicate predicate) {
      int partitionCount =
          Math.max(1, (values.length + PARTITION_SIZE - 1) / PARTITION_SIZE);
      List<Future<Integer>> futures = new ArrayList<Future<Integer>>(partitionCount);
      for (int partition = 0; partition < partitionCount; partition++) {
        final int start = partition * PARTITION_SIZE;
        final int end = Math.min(values.length, start + PARTITION_SIZE);
        futures.add(context.executor().submit(new Callable<Integer>() {
          @Override
          public Integer call() {
            if (cancellation.isCancelled()) {
              throw new IllegalStateException("cancelled");
            }
            return Integer.valueOf(executeRange(values, start, end, predicate));
          }
        }));
      }

      int result = 0;
      RuntimeException primary = null;
      for (int partition = 0; partition < futures.size(); partition++) {
        try {
          result += futures.get(partition).get().intValue();
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          if (primary == null) {
            primary = new IllegalStateException("interrupted", failure);
          }
        } catch (ExecutionException failure) {
          if (primary == null) {
            Throwable cause = failure.getCause();
            primary = cause instanceof RuntimeException
                ? (RuntimeException) cause
                : new IllegalStateException("worker failure", cause);
          }
        }
      }
      if (primary != null) {
        for (Future<Integer> future : futures) {
          future.cancel(false);
        }
        throw primary;
      }
      return result;
    }

    private static int executeDirect(int[] values, IntPredicate predicate) {
      return executeRange(values, 0, values.length, predicate);
    }

    private static int executeRange(
        int[] values, int start, int end, IntPredicate predicate) {
      int count = 0;
      for (int index = start; index < end; index++) {
        if (predicate.test(values[index])) {
          count++;
        }
      }
      return count;
    }

    private void ensureNew() {
      if (state != State.NEW) {
        throw new IllegalStateException("invocation consumed: " + state);
      }
    }
  }
}
