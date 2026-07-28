package com.hgtech.soma.benchmarks;

import com.hgtech.soma.benchmarks.schema.EntityKind;
import com.hgtech.soma.benchmarks.schema.VariableKind;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactBatch;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactDataFlow;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactScan;
import com.hgtech.soma.benchmarks.schema.generated.NumericFactTable;
import com.hgtech.soma.dataflow.BooleanColumnResult;
import com.hgtech.soma.dataflow.CancellationToken;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DataFlowStats;
import com.hgtech.soma.dataflow.DoubleColumnResult;
import com.hgtech.soma.dataflow.DoubleScalarResult;
import com.hgtech.soma.dataflow.ExecutionBudget;
import com.hgtech.soma.dataflow.ExecutionPolicy;
import com.hgtech.soma.dataflow.LongColumnResult;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OptionalDoubleResult;
import com.hgtech.soma.dataflow.OptionalLongResult;
import com.hgtech.soma.dataflow.StatsMode;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/** Slice D deterministic parallel execution and fallback evidence. */
public final class DataFlowSliceDCheck {
    private static final int ROWS = 8192;

    private DataFlowSliceDCheck() {
    }

    public static void main(String[] args) {
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch());
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("facts");
        DataFlowContext sequential = DataFlowContext.sequential();
        DataFlowContext defaultAdaptive =
                DataFlowContext.managedParallel(4);
        DataFlowContext managed = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1024),
                ExecutionBudget.defaults());
        try {
            require(executeDefaultAdaptive(
                            source.candidates().count(),
                            source,
                            table,
                            defaultAdaptive)
                            .stats.tasks() == 1,
                    "default adaptive policy avoids unsupported small-work crossover");
            verifyPureKernels(source, table, sequential, managed);
            verifyFallbacks(source, table, managed);
            verifyEffect(source, table, managed);
            verifyFailureDrain(source, table, managed);
            verifyBudgetFallback(source, table, managed);
            verifyBorrowedExecutors(source, table);
        } finally {
            sequential.close();
            defaultAdaptive.close();
            managed.close();
            table.release();
        }
        System.out.println("dataflow-slice-d-check: ok");
    }

    private static void verifyPureKernels(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext sequential,
            DataFlowContext parallel) {
        DataFlowDefinition<LongScalarResult> count =
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(100L)
                                .and(source.columns().factIndex()
                                        .lessThan(8100L)))
                        .count();
        Run<LongScalarResult> sequentialCount =
                execute(count, source, table, sequential);
        Run<LongScalarResult> parallelCount =
                execute(count, source, table, parallel);
        require(sequentialCount.result.value()
                        == parallelCount.result.value()
                        && parallelCount.result.value() == 8000L,
                "parallel count identity");
        require(sequentialCount.stats.tasks() == 1
                        && parallelCount.stats.tasks() == 8
                        && parallelCount.stats.workers() == 4,
                "single storage segment splits into bounded morsels");

        DataFlowDefinition<LongColumnResult> longProjection =
                source.candidates()
                        .filter(source.columns().factIndex()
                                .greaterThanOrEqualTo(100L))
                        .project(source.columns().factIndex()
                                .multipliedBy(3L))
                        .toColumn();
        LongColumnResult sequentialLong =
                execute(longProjection, source, table, sequential).result;
        Run<LongColumnResult> parallelLongRun =
                execute(longProjection, source, table, parallel);
        LongColumnResult parallelLong = parallelLongRun.result;
        require(sequentialLong.size() == parallelLong.size()
                        && parallelLongRun.stats.tasks() == 16
                        && parallelLongRun.stats.workers() == 4,
                "parallel long projection shape");
        for (int index = 0; index < sequentialLong.size(); index++) {
            require(sequentialLong.valueAt(index)
                            == parallelLong.valueAt(index),
                    "parallel stable long projection");
        }

        DataFlowDefinition<DoubleColumnResult> doubleProjection =
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(7000L))
                        .project(source.columns().value().plus(0.5d))
                        .toColumn();
        DoubleColumnResult sequentialDouble =
                execute(doubleProjection, source, table, sequential).result;
        DoubleColumnResult parallelDouble =
                execute(doubleProjection, source, table, parallel).result;
        require(sequentialDouble.size() == parallelDouble.size(),
                "parallel double projection shape");
        for (int index = 0; index < sequentialDouble.size(); index++) {
            require(Double.doubleToLongBits(sequentialDouble.valueAt(index))
                            == Double.doubleToLongBits(
                                    parallelDouble.valueAt(index)),
                    "parallel stable double projection");
        }

        DataFlowDefinition<BooleanColumnResult> booleanProjection =
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(4096L))
                        .project(source.columns().factIndex()
                                .greaterThanOrEqualTo(2048L))
                        .toColumn();
        BooleanColumnResult sequentialBoolean =
                execute(booleanProjection, source, table, sequential).result;
        BooleanColumnResult parallelBoolean =
                execute(booleanProjection, source, table, parallel).result;
        require(sequentialBoolean.size() == parallelBoolean.size(),
                "parallel boolean projection shape");
        for (int index = 0; index < sequentialBoolean.size(); index++) {
            require(sequentialBoolean.valueAt(index)
                            == parallelBoolean.valueAt(index),
                    "parallel stable boolean projection");
        }

        DataFlowDefinition<LongScalarResult> sum =
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(7000L))
                        .project(source.columns().factIndex())
                        .sum();
        DataFlowDefinition<OptionalLongResult> max =
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(7000L))
                        .project(source.columns().factIndex())
                        .max();
        DataFlowDefinition<OptionalDoubleResult> average =
                source.candidates()
                        .filter(source.columns().factIndex().lessThan(7000L))
                        .project(source.columns().factIndex())
                        .average();
        Run<LongScalarResult> parallelSum =
                execute(sum, source, table, parallel);
        require(execute(sum, source, table, sequential).result.value()
                        == parallelSum.result.value()
                        && parallelSum.stats.tasks() == 8
                        && parallelSum.stats.workers() == 4,
                "fixed-tree long sum identity");
        require(execute(max, source, table, sequential).result.value()
                        == execute(max, source, table, parallel).result.value(),
                "fixed-tree long max identity");
        require(Double.doubleToLongBits(
                        execute(average, source, table, sequential)
                                .result.value())
                        == Double.doubleToLongBits(
                                execute(average, source, table, parallel)
                                        .result.value()),
                "fixed-tree long average identity");
    }

    private static void verifyFallbacks(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext parallel) {
        Run<DoubleScalarResult> floating = execute(
                source.candidates()
                        .project(source.columns().value())
                        .sum(),
                source,
                table,
                parallel);
        require(floating.stats.tasks() == 1
                        && floating.stats.workers() == 1,
                "floating left-fold sequential fallback");

        Run<LongColumnResult> ordered = execute(
                source.candidates()
                        .skip(100L)
                        .limit(1000L)
                        .project(source.columns().factIndex())
                        .toColumn(),
                source,
                table,
                parallel);
        require(ordered.stats.tasks() == 1
                        && ordered.result.valueAt(0) == 100L,
                "skip-limit order sequential fallback");
    }

    private static void verifyEffect(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext parallel) {
        DataFlowDefinition<UpdateResult> update =
                source.update(
                        source.candidates()
                                .filter(source.columns().factIndex()
                                        .lessThan(2048L)),
                        new NumericFactScan.Updater() {
                            @Override
                            public void update(
                                    com.hgtech.soma.benchmarks.schema.generated
                                            .NumericFactUpdateCursor fact) {
                                fact.setScale(fact.scale() + 1.0d);
                            }
                        });
        Run<UpdateResult> run =
                execute(update, source, table, parallel);
        require(run.result.matched() == 2048L
                        && run.stats.tasks() == 16
                        && run.stats.workers() == 4
                        && table.fetchAt(0).scale == 2.0d
                        && table.fetchAt(2048).scale == 1.0d,
                "parallel freeze and deterministic effect commit");
    }

    private static void verifyFailureDrain(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext parallel) {
        try {
            execute(
                    source.candidates()
                            .project(source.columns().factIndex()
                                    .dividedBy(0L))
                            .toColumn(),
                    source,
                    table,
                    parallel);
            throw new AssertionError("parallel worker failure expected");
        } catch (SomaRuntimeException expected) {
            require("dataflow_unexpected_failure".equals(expected.code()),
                    "parallel worker failure typing");
        }
        require(execute(
                        source.candidates().count(),
                        source,
                        table,
                        parallel).result.value() == ROWS,
                "context and table guard reusable after worker failure");

        final AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return checks.incrementAndGet() > 8;
            }
        };
        DataFlowInvocation<LongColumnResult> invocation =
                source.candidates()
                        .project(source.columns().factIndex())
                        .toColumn()
                        .compile()
                        .newInvocation(parallel)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .cancellationToken(cancellation);
        try {
            invocation.execute();
            throw new AssertionError("parallel cancellation expected");
        } catch (SomaRuntimeException expected) {
            require("dataflow_cancelled".equals(expected.code()),
                    "parallel cancellation typing");
        }
        require(execute(
                        source.candidates().count(),
                        source,
                        table,
                        parallel).result.value() == ROWS,
                "context reusable after cancellation drain");
    }

    private static void verifyBudgetFallback(
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext parallel) {
        ExecutionBudget oneTask = ExecutionBudget.defaults()
                .toBuilder()
                .maximumTasks(1)
                .build();
        DataFlowInvocation<LongScalarResult> invocation =
                source.candidates().count()
                        .compile()
                        .newInvocation(parallel)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .policy(ExecutionPolicy.adaptiveParallel()
                                .withStatsMode(StatsMode.DETAILED))
                        .budget(oneTask);
        require(invocation.execute().value() == ROWS
                        && invocation.stats().tasks() == 1,
                "task budget causes deterministic sequential fallback");

        DataFlowInvocation<LongScalarResult> forcedSequential =
                source.candidates().count()
                        .compile()
                        .newInvocation(parallel)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .policy(ExecutionPolicy.sequential()
                                .withStatsMode(StatsMode.DETAILED));
        forcedSequential.execute();
        require(forcedSequential.stats().tasks() == 1,
                "invocation policy sequential fallback");
    }

    private static void verifyBorrowedExecutors(
            NumericFactDataFlow.Source source,
            NumericFactTable table) {
        ForkJoinPool custom = new ForkJoinPool(3);
        DataFlowContext borrowed =
                DataFlowContext.borrowed(custom, 3);
        try {
            Run<LongScalarResult> run = execute(
                    source.candidates().count(),
                    source,
                    table,
                    borrowed);
            require(run.result.value() == ROWS
                            && run.stats.workers() == 3,
                    "borrowed custom executor");
        } finally {
            borrowed.close();
        }
        require(!custom.isShutdown(),
                "borrowed executor ownership remains with caller");
        custom.shutdown();

        ForkJoinPool common = ForkJoinPool.commonPool();
        int workers = Math.max(1, common.getParallelism());
        DataFlowContext explicitCommon =
                DataFlowContext.borrowed(common, workers);
        try {
            require(execute(
                            source.candidates().count(),
                            source,
                            table,
                            explicitCommon).result.value() == ROWS,
                    "explicit common pool execution");
        } finally {
            explicitCommon.close();
        }
        require(!common.isShutdown(),
                "explicit common pool is never owned by SOMA");
    }

    private static NumericFactBatch batch() {
        NumericFactBatch batch = new NumericFactBatch(ROWS);
        for (int index = 0; index < ROWS; index++) {
            batch.addValues(
                    index,
                    (index & 1) == 0
                            ? EntityKind.PRIMARY : EntityKind.SECONDARY,
                    100L + index,
                    VariableKind.VALUE,
                    index,
                    index,
                    1.0d);
        }
        return batch;
    }

    private static <R> Run<R> execute(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context) {
        DataFlowInvocation<R> invocation =
                definition.compile()
                        .newInvocation(context)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .policy((context.workers() > 1
                                ? ExecutionPolicy.adaptiveParallel()
                                        .withMinimumParallelCardinality(1024)
                                : ExecutionPolicy.sequential())
                                .withStatsMode(StatsMode.DETAILED));
        R result = invocation.execute();
        return new Run<R>(result, invocation.stats());
    }

    private static <R> Run<R> executeDefaultAdaptive(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context) {
        DataFlowInvocation<R> invocation =
                definition.compile()
                        .newInvocation(context)
                        .bind(source, NumericFactDataFlow.bind(table))
                        .policy(ExecutionPolicy.adaptiveParallel()
                                .withStatsMode(StatsMode.DETAILED));
        R result = invocation.execute();
        return new Run<R>(result, invocation.stats());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Run<R> {
        final R result;
        final DataFlowStats stats;

        Run(R result, DataFlowStats stats) {
            this.result = result;
            this.stats = stats;
        }
    }
}
