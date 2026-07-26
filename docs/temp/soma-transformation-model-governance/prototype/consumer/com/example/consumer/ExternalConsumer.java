package com.example.consumer;

import com.example.generated.WorkDataFlow;
import com.example.generated.WorkDataFlow.WorkBinding;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.CancellationToken;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.DataFlowContext;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.DataFlowDefinition;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.DataFlowInvocation;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.DataFlowTemplate;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.IntPredicate;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.SharedScratch;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.SourceSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Temporary 架构原型的 external-consumer executable。 */
public final class ExternalConsumer {
  private static final CancellationToken NOT_CANCELLED = new CancellationToken() {
    @Override
    public boolean isCancelled() {
      return false;
    }
  };

  private ExternalConsumer() {
  }

  public static void main(String[] args) {
    verifySequentialParallelIdentityAndOneShot();
    verifyAliasDedupAndCanonicalGuardOrder();
    verifyPartialAcquireCleanup();
    verifyCancellationAndWorkerFailureIdentity();
    verifyConsumerCountedSharedScratch();
    verifyContextOwnership();
    verifyCallbackRetentionBoundary();
    System.out.println("dataflow-architecture-prototype: ok");
  }

  private static void verifySequentialParallelIdentityAndOneShot() {
    int[] values = new int[10_000];
    int expected = 0;
    for (int index = 0; index < values.length; index++) {
      values[index] = index - 5_000;
      if ((values[index] & 3) == 0) {
        expected++;
      }
    }
    IntPredicate predicate = new IntPredicate() {
      @Override
      public boolean test(int value) {
        return (value & 3) == 0;
      }
    };
    DataFlowDefinition definition = DataFlowDefinition.define(
        WorkDataFlow.SOURCE, predicate, WorkDataFlow.SOURCE);
    DataFlowTemplate template = definition.compile();
    WorkBinding binding = new WorkBinding(10L, values, new ArrayList<Long>());

    DataFlowContext sequential = DataFlowContext.managed(1);
    DataFlowInvocation direct = template
        .newInvocation(sequential, false, NOT_CANCELLED)
        .bind(WorkDataFlow.SOURCE, binding);
    require(direct.executeCount() == expected, "direct count");
    boolean consumed = false;
    try {
      direct.executeCount();
    } catch (IllegalStateException expectedFailure) {
      consumed = expectedFailure.getMessage().contains("consumed");
    }
    require(consumed, "one-shot invocation");
    sequential.close();

    DataFlowContext parallel = DataFlowContext.managed(4);
    int parallelResult = template
        .newInvocation(parallel, true, NOT_CANCELLED)
        .bind(WorkDataFlow.SOURCE, binding)
        .executeCount();
    require(parallelResult == expected, "fixed-order parallel identity");
    parallel.close();
  }

  private static void verifyAliasDedupAndCanonicalGuardOrder() {
    final List<Long> events = new ArrayList<Long>();
    WorkBinding aggregateThree = new WorkBinding(3L, new int[] {1}, events);
    WorkBinding aggregateOne = new WorkBinding(1L, new int[] {1}, events);
    WorkBinding aggregateTwo = new WorkBinding(2L, new int[] {1}, events);
    SourceSlot<WorkBinding> sourceThree =
        new SourceSlot<WorkBinding>("three", WorkDataFlow.SCHEMA);
    SourceSlot<WorkBinding> sourceOne =
        new SourceSlot<WorkBinding>("one", WorkDataFlow.SCHEMA);
    SourceSlot<WorkBinding> sourceTwo =
        new SourceSlot<WorkBinding>("two", WorkDataFlow.SCHEMA);
    DataFlowDefinition ordered = DataFlowDefinition.define(
        sourceThree, alwaysTrue(), sourceThree, sourceOne, sourceTwo);
    DataFlowContext context = DataFlowContext.managed(1);
    ordered.compile().newInvocation(context, false, NOT_CANCELLED)
        .bind(sourceThree, aggregateThree)
        .bind(sourceOne, aggregateOne)
        .bind(sourceTwo, aggregateTwo)
        .executeCount();
    require(events.equals(Arrays.asList(1L, 2L, 3L, -3L, -2L, -1L)),
        "canonical acquire/reverse release order");

    events.clear();
    DataFlowDefinition aliased = DataFlowDefinition.define(
        WorkDataFlow.SOURCE, alwaysTrue(), WorkDataFlow.SOURCE, WorkDataFlow.ALIAS);
    aliased.compile().newInvocation(context, false, NOT_CANCELLED)
        .bind(WorkDataFlow.SOURCE, aggregateOne)
        .bind(WorkDataFlow.ALIAS, aggregateOne)
        .executeCount();
    require(aggregateOne.acquireCount() == 2, "alias aggregate acquired once per invocation");
    require(aggregateOne.releaseCount() == 2, "alias aggregate released once per invocation");
    context.close();
  }

  private static void verifyPartialAcquireCleanup() {
    List<Long> events = new ArrayList<Long>();
    WorkBinding first = new WorkBinding(1L, new int[] {1}, events);
    WorkBinding second = new WorkBinding(2L, new int[] {1}, events).failAcquire();
    SourceSlot<WorkBinding> firstSlot =
        new SourceSlot<WorkBinding>("first", WorkDataFlow.SCHEMA);
    SourceSlot<WorkBinding> secondSlot =
        new SourceSlot<WorkBinding>("second", WorkDataFlow.SCHEMA);
    DataFlowDefinition definition = DataFlowDefinition.define(
        firstSlot, alwaysTrue(), firstSlot, secondSlot);
    DataFlowContext context = DataFlowContext.managed(1);
    boolean failed = false;
    try {
      definition.compile().newInvocation(context, false, NOT_CANCELLED)
          .bind(firstSlot, first)
          .bind(secondSlot, second)
          .executeCount();
    } catch (IllegalStateException expected) {
      failed = expected.getMessage().contains("acquire failure");
    }
    require(failed, "partial acquire failure surfaced");
    require(events.equals(Arrays.asList(1L, -1L)), "partial acquire reverse cleanup");
    context.close();
  }

  private static void verifyContextOwnership() {
    ExecutorService borrowedExecutor = Executors.newFixedThreadPool(2);
    DataFlowContext borrowed = DataFlowContext.borrowed(borrowedExecutor, 2);
    require(!borrowed.ownsExecutor(), "borrowed ownership");
    borrowed.close();
    require(!borrowedExecutor.isShutdown(), "borrowed executor not shutdown");
    borrowedExecutor.shutdown();

    DataFlowContext managed = DataFlowContext.managed(1);
    managed.close();
    require(managed.isClosed(), "managed context closed");
  }

  private static void verifyConsumerCountedSharedScratch() {
    SharedScratch scratch = new SharedScratch(3);
    scratch.releaseConsumer();
    scratch.releaseConsumer();
    require(!scratch.isReleased(), "shared scratch retained for final consumer");
    scratch.releaseConsumer();
    require(scratch.isReleased(), "shared scratch released after final consumer");
  }

  private static void verifyCancellationAndWorkerFailureIdentity() {
    final List<Long> events = new ArrayList<Long>();
    WorkBinding cancelledBinding = new WorkBinding(7L, new int[] {1, 2, 3}, events);
    DataFlowDefinition cancelledDefinition = DataFlowDefinition.define(
        WorkDataFlow.SOURCE, alwaysTrue(), WorkDataFlow.SOURCE);
    DataFlowContext cancelledContext = DataFlowContext.managed(2);
    boolean cancelled = false;
    try {
      cancelledDefinition.compile()
          .newInvocation(cancelledContext, true, new CancellationToken() {
            @Override
            public boolean isCancelled() {
              return true;
            }
          })
          .bind(WorkDataFlow.SOURCE, cancelledBinding)
          .executeCount();
    } catch (IllegalStateException expected) {
      cancelled = "cancelled".equals(expected.getMessage());
    }
    require(cancelled, "cancellation failure");
    require(events.equals(Arrays.asList(7L, -7L)), "cancelled invocation cleanup");
    cancelledContext.close();

    int[] values = new int[600];
    values[10] = -100;
    values[300] = -200;
    WorkBinding failingBinding = new WorkBinding(8L, values, new ArrayList<Long>());
    DataFlowDefinition failingDefinition = DataFlowDefinition.define(
        WorkDataFlow.SOURCE, new IntPredicate() {
          @Override
          public boolean test(int value) {
            if (value == -100) {
              throw new IllegalStateException("partition-zero");
            }
            if (value == -200) {
              throw new IllegalStateException("partition-one");
            }
            return true;
          }
        }, WorkDataFlow.SOURCE);
    DataFlowContext failingContext = DataFlowContext.managed(4);
    boolean lowestPartitionWon = false;
    try {
      failingDefinition.compile().newInvocation(
          failingContext, true, NOT_CANCELLED)
          .bind(WorkDataFlow.SOURCE, failingBinding)
          .executeCount();
    } catch (IllegalStateException expected) {
      lowestPartitionWon = containsMessage(expected, "partition-zero");
    }
    require(lowestPartitionWon, "lowest logical partition failure identity");
    require(failingBinding.acquireCount() == 1
        && failingBinding.releaseCount() == 1, "worker failure cleanup");
    failingContext.close();
  }

  private static void verifyCallbackRetentionBoundary() {
    IntPredicate callback = alwaysTrue();
    DataFlowDefinition definition = DataFlowDefinition.define(
        WorkDataFlow.SOURCE, callback, WorkDataFlow.SOURCE);
    require(definition.retainedPredicate() == callback,
        "definition-instance callback identity");
    require(definition.compile() != definition.compile(),
        "no implicit global template cache");
  }

  private static IntPredicate alwaysTrue() {
    return new IntPredicate() {
      @Override
      public boolean test(int value) {
        return true;
      }
    };
  }

  private static boolean containsMessage(Throwable failure, String message) {
    Throwable current = failure;
    while (current != null) {
      if (message.equals(current.getMessage())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
