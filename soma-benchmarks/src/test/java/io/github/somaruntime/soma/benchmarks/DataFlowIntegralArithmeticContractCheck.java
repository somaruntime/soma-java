package io.github.somaruntime.soma.benchmarks;

import io.github.somaruntime.soma.benchmarks.schema.CategoryId;
import io.github.somaruntime.soma.benchmarks.schema.EntityKind;
import io.github.somaruntime.soma.benchmarks.schema.GroupId;
import io.github.somaruntime.soma.benchmarks.schema.ItemId;
import io.github.somaruntime.soma.benchmarks.schema.NamespaceId;
import io.github.somaruntime.soma.benchmarks.schema.VariableKind;
import io.github.somaruntime.soma.benchmarks.schema.WorkKey;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.NumericFactTable;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnedOptionDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactBatch;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactDataFlow;
import io.github.somaruntime.soma.benchmarks.schema.generated.OwnerFactTable;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.DataFlowInvocation;
import io.github.somaruntime.soma.dataflow.DataFlowStats;
import io.github.somaruntime.soma.dataflow.ExecutionBudget;
import io.github.somaruntime.soma.dataflow.ExecutionPolicy;
import io.github.somaruntime.soma.dataflow.GroupedLongResult;
import io.github.somaruntime.soma.dataflow.KeyExpression;
import io.github.somaruntime.soma.dataflow.LongColumnResult;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.OptionalDoubleResult;
import io.github.somaruntime.soma.dataflow.PartialWindowPolicy;
import io.github.somaruntime.soma.dataflow.StatsMode;
import io.github.somaruntime.soma.runtime.SomaErrorCategory;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

/** Fail-closed integral expression、reduction 与 shape contract。 */
public final class DataFlowIntegralArithmeticContractCheck {
    private static final int PARALLEL_ROWS = 8192;

    private DataFlowIntegralArithmeticContractCheck() {
    }

    public static void main(String[] args) {
        verifyExpressionFailures();
        verifyExactScalarAndParallelReduction();
        verifyPrefixGroupAndWindow();
        verifyExpandedSum();
        System.out.println("dataflow-integral-arithmetic-contract: ok");
    }

    private static void verifyExpressionFailures() {
        NumericFactTable maximum = table(Long.MAX_VALUE);
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("expression");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            expectFailure(
                    source.candidates()
                            .project(source.columns().entityId().plus(1L))
                            .toColumn(),
                    source,
                    maximum,
                    context,
                    "dataflow_integral_overflow",
                    "dataflow.expression.add",
                    "raw addition overflow");
            expectFailure(
                    source.candidates()
                            .filter(source.columns().entityId()
                                    .plus(1L)
                                    .greaterThan(0L))
                            .count(),
                    source,
                    maximum,
                    context,
                    "dataflow_integral_overflow",
                    "dataflow.expression.add",
                    "closed-kernel addition overflow");
            expectFailure(
                    source.candidates()
                            .project(source.columns().entityId()
                                    .multipliedBy(2L))
                            .toColumn(),
                    source,
                    maximum,
                    context,
                    "dataflow_integral_overflow",
                    "dataflow.expression.multiply",
                    "multiplication overflow");
            expectFailure(
                    source.candidates()
                            .project(source.columns().entityId()
                                    .dividedBy(0L))
                            .toColumn(),
                    source,
                    maximum,
                    context,
                    "dataflow_integral_division_by_zero",
                    "dataflow.expression.divide",
                    "division by zero");
            require(execute(
                            source.candidates().count(),
                            source,
                            maximum,
                            context).result.value() == 1L,
                    "expression failure releases invocation guards");
        } finally {
            context.close();
            maximum.release();
        }

        NumericFactTable minimum = table(Long.MIN_VALUE);
        NumericFactDataFlow.Source minimumSource =
                NumericFactDataFlow.source("minimum");
        DataFlowContext minimumContext = DataFlowContext.sequential();
        try {
            expectFailure(
                    minimumSource.candidates()
                            .project(minimumSource.columns().entityId()
                                    .minus(1L))
                            .toColumn(),
                    minimumSource,
                    minimum,
                    minimumContext,
                    "dataflow_integral_overflow",
                    "dataflow.expression.subtract",
                    "subtraction overflow");
            expectFailure(
                    minimumSource.candidates()
                            .project(minimumSource.columns().entityId()
                                    .dividedBy(-1L))
                            .toColumn(),
                    minimumSource,
                    minimum,
                    minimumContext,
                    "dataflow_integral_overflow",
                    "dataflow.expression.divide",
                    "signed division overflow");
        } finally {
            minimumContext.close();
            minimum.release();
        }

        NumericFactTable binary = table(0L, Long.MAX_VALUE);
        NumericFactDataFlow.Source binarySource =
                NumericFactDataFlow.source("binary");
        DataFlowContext binaryContext = DataFlowContext.sequential();
        try {
            expectFailure(
                    binarySource.candidates()
                            .filter(binarySource.columns().factIndex()
                                    .equalTo(1L))
                            .project(binarySource.columns().entityId()
                                    .plus(binarySource.columns().factIndex()))
                            .toColumn(),
                    binarySource,
                    binary,
                    binaryContext,
                    "dataflow_integral_overflow",
                    "dataflow.expression.add",
                    "binary addition overflow");
        } finally {
            binaryContext.close();
            binary.release();
        }
    }

    private static void verifyExactScalarAndParallelReduction() {
        NumericFactTable table = repeatedTable(
                PARALLEL_ROWS, Long.MAX_VALUE);
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("parallel");
        DataFlowContext sequential = DataFlowContext.sequential();
        DataFlowContext parallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1024),
                ExecutionBudget.defaults());
        try {
            DataFlowDefinition<OptionalDoubleResult> average =
                    source.candidates()
                            .project(source.columns().entityId())
                            .average();
            Run<OptionalDoubleResult> sequentialAverage =
                    execute(average, source, table, sequential);
            Run<OptionalDoubleResult> parallelAverage =
                    execute(average, source, table, parallel);
            long expected = Double.doubleToLongBits(
                    (double) Long.MAX_VALUE);
            require(sequentialAverage.result.isPresent()
                            && parallelAverage.result.isPresent()
                            && Double.doubleToLongBits(
                            sequentialAverage.result.value()) == expected
                            && Double.doubleToLongBits(
                            parallelAverage.result.value()) == expected
                            && parallelAverage.stats.tasks() > 1
                            && parallelAverage.stats.workers() > 1,
                    "average uses exact total in sequential and parallel paths");

            DataFlowDefinition<LongScalarResult> sum =
                    source.candidates()
                            .project(source.columns().entityId())
                            .sum();
            expectFailure(
                    sum,
                    source,
                    table,
                    sequential,
                    "dataflow_integral_overflow",
                    "dataflow.longReduce",
                    "sequential sum overflow");
            expectFailure(
                    sum,
                    source,
                    table,
                    parallel,
                    "dataflow_integral_overflow",
                    "dataflow.longReduce",
                    "parallel sum overflow");
        } finally {
            sequential.close();
            parallel.close();
            table.release();
        }

        NumericFactTable negative = repeatedTable(
                PARALLEL_ROWS, Long.MIN_VALUE);
        NumericFactDataFlow.Source negativeSource =
                NumericFactDataFlow.source("negative-parallel");
        DataFlowContext negativeSequential = DataFlowContext.sequential();
        DataFlowContext negativeParallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1024),
                ExecutionBudget.defaults());
        try {
            DataFlowDefinition<OptionalDoubleResult> average =
                    negativeSource.candidates()
                            .project(negativeSource.columns().entityId())
                            .average();
            Run<OptionalDoubleResult> sequentialAverage = execute(
                    average,
                    negativeSource,
                    negative,
                    negativeSequential);
            Run<OptionalDoubleResult> parallelAverage = execute(
                    average,
                    negativeSource,
                    negative,
                    negativeParallel);
            long expected = Double.doubleToLongBits(
                    (double) Long.MIN_VALUE);
            require(Double.doubleToLongBits(
                            sequentialAverage.result.value()) == expected
                            && Double.doubleToLongBits(
                            parallelAverage.result.value()) == expected
                            && parallelAverage.stats.tasks() > 1,
                    "negative average uses exact signed wide total");
        } finally {
            negativeSequential.close();
            negativeParallel.close();
            negative.release();
        }

        NumericFactTable cancellation =
                table(Long.MAX_VALUE, 1L, -1L);
        NumericFactDataFlow.Source cancellationSource =
                NumericFactDataFlow.source("cancellation");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            LongScalarResult sum = execute(
                    cancellationSource.candidates()
                            .project(cancellationSource.columns().entityId())
                            .sum(),
                    cancellationSource,
                    cancellation,
                    context).result;
            require(sum.value() == Long.MAX_VALUE,
                    "exact sum permits mathematically cancelling wide state");
        } finally {
            context.close();
            cancellation.release();
        }

        NumericFactTable parallelCancellation =
                balancedExtremeTable(PARALLEL_ROWS);
        NumericFactDataFlow.Source balancedSource =
                NumericFactDataFlow.source("parallel-cancellation");
        DataFlowContext balancedSequential = DataFlowContext.sequential();
        DataFlowContext balancedParallel = DataFlowContext.managedParallel(
                4,
                ExecutionPolicy.adaptiveParallel()
                        .withMinimumParallelCardinality(1024),
                ExecutionBudget.defaults());
        try {
            DataFlowDefinition<LongScalarResult> balancedSum =
                    balancedSource.candidates()
                            .project(balancedSource.columns().entityId())
                            .sum();
            Run<LongScalarResult> sequentialResult = execute(
                    balancedSum,
                    balancedSource,
                    parallelCancellation,
                    balancedSequential);
            Run<LongScalarResult> parallelResult = execute(
                    balancedSum,
                    balancedSource,
                    parallelCancellation,
                    balancedParallel);
            require(sequentialResult.result.value()
                            == -(PARALLEL_ROWS / 2L)
                            && parallelResult.result.value()
                            == sequentialResult.result.value()
                            && parallelResult.stats.tasks() > 1,
                    "parallel exact merge preserves cancelling wide partials");
        } finally {
            balancedSequential.close();
            balancedParallel.close();
            parallelCancellation.release();
        }
    }

    private static void verifyPrefixGroupAndWindow() {
        NumericFactTable cancellation =
                table(Long.MAX_VALUE, 1L, -1L);
        NumericFactDataFlow.Source source =
                NumericFactDataFlow.source("shape-cancellation");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            GroupedLongResult grouped = execute(
                    source.candidates()
                            .groupBy(KeyExpression.of(
                                    source.columns().entityKind()))
                            .sum(source.columns().entityId()),
                    source,
                    cancellation,
                    context).result;
            LongColumnResult windowed = execute(
                    source.candidates()
                            .windowByCount(
                                    3,
                                    3,
                                    PartialWindowPolicy.INCLUDE_PARTIAL)
                            .sum(source.columns().entityId()),
                    source,
                    cancellation,
                    context).result;
            require(grouped.size() == 1
                            && grouped.valueAt(0) == Long.MAX_VALUE
                            && windowed.size() == 1
                            && windowed.valueAt(0) == Long.MAX_VALUE,
                    "group and window preserve exact cancellation");
            expectFailure(
                    source.candidates()
                            .project(source.columns().entityId())
                            .inclusivePrefixSum(),
                    source,
                    cancellation,
                    context,
                    "dataflow_integral_overflow",
                    "dataflow.prefix",
                    "inclusive prefix overflow");
            expectFailure(
                    source.candidates()
                            .project(source.columns().entityId())
                            .exclusivePrefixSum(0L),
                    source,
                    cancellation,
                    context,
                    "dataflow_integral_overflow",
                    "dataflow.prefix",
                    "exclusive prefix overflow");
        } finally {
            context.close();
            cancellation.release();
        }

        NumericFactTable sliding =
                table(Long.MIN_VALUE, Long.MAX_VALUE, -1L);
        NumericFactDataFlow.Source slidingSource =
                NumericFactDataFlow.source("sliding-window");
        DataFlowContext slidingContext = DataFlowContext.sequential();
        try {
            LongColumnResult windows = execute(
                    slidingSource.candidates()
                            .windowByCount(
                                    2,
                                    1,
                                    PartialWindowPolicy.DROP_PARTIAL)
                            .sum(slidingSource.columns().entityId()),
                    slidingSource,
                    sliding,
                    slidingContext).result;
            require(windows.size() == 2
                            && windows.valueAt(0) == -1L
                            && windows.valueAt(1)
                            == Long.MAX_VALUE - 1L,
                    "sliding window subtracts Long.MIN_VALUE exactly");
        } finally {
            slidingContext.close();
            sliding.release();
        }

        NumericFactTable overflow = table(Long.MAX_VALUE, 1L);
        NumericFactDataFlow.Source overflowSource =
                NumericFactDataFlow.source("shape-overflow");
        DataFlowContext overflowContext = DataFlowContext.sequential();
        try {
            LongColumnResult exclusive = execute(
                    overflowSource.candidates()
                            .project(overflowSource.columns().entityId())
                            .exclusivePrefixSum(0L),
                    overflowSource,
                    overflow,
                    overflowContext).result;
            require(exclusive.size() == 2
                            && exclusive.valueAt(0) == 0L
                            && exclusive.valueAt(1) == Long.MAX_VALUE,
                    "exclusive unobserved tail does not invent overflow");
            expectFailure(
                    overflowSource.candidates()
                            .groupBy(KeyExpression.of(
                                    overflowSource.columns().entityKind()))
                            .sum(overflowSource.columns().entityId()),
                    overflowSource,
                    overflow,
                    overflowContext,
                    "dataflow_integral_overflow",
                    "dataflow.groupBy.aggregate",
                    "group sum overflow");
            expectFailure(
                    overflowSource.candidates()
                            .windowByCount(
                                    2,
                                    2,
                                    PartialWindowPolicy.INCLUDE_PARTIAL)
                            .sum(overflowSource.columns().entityId()),
                    overflowSource,
                    overflow,
                    overflowContext,
                    "dataflow_integral_overflow",
                    "dataflow.window.aggregate",
                    "window sum overflow");
        } finally {
            overflowContext.close();
            overflow.release();
        }
    }

    private static void verifyExpandedSum() {
        OwnerFactTable cancellation = ownerTable(
                Long.MAX_VALUE, 1L, -1L);
        OwnerFactDataFlow.Source owners =
                OwnerFactDataFlow.source("owners-cancellation");
        OwnedOptionDataFlow.Source options =
                OwnedOptionDataFlow.source("options-cancellation");
        DataFlowContext context = DataFlowContext.sequential();
        try {
            LongScalarResult sum = execute(
                    owners.expandOptions(options)
                            .projectChild(options.columns().cost())
                            .sum(),
                    owners,
                    cancellation,
                    context);
            require(sum.value() == Long.MAX_VALUE,
                    "expanded sum preserves exact cancellation");
        } finally {
            context.close();
            cancellation.release();
        }

        OwnerFactTable overflow = ownerTable(Long.MAX_VALUE, 1L);
        OwnerFactDataFlow.Source overflowOwners =
                OwnerFactDataFlow.source("owners-overflow");
        OwnedOptionDataFlow.Source overflowOptions =
                OwnedOptionDataFlow.source("options-overflow");
        DataFlowContext overflowContext = DataFlowContext.sequential();
        try {
            try {
                execute(
                        overflowOwners.expandOptions(overflowOptions)
                                .projectChild(
                                        overflowOptions.columns().cost())
                                .sum(),
                        overflowOwners,
                        overflow,
                        overflowContext);
                throw new AssertionError(
                        "expanded sum overflow must fail closed");
            } catch (SomaRuntimeException failure) {
                requireFailure(
                        failure,
                        "dataflow_integral_overflow",
                        "dataflow.expand.longSum",
                        "expanded sum overflow");
            }
        } finally {
            overflowContext.close();
            overflow.release();
        }
    }

    private static NumericFactTable table(long... values) {
        NumericFactBatch batch = new NumericFactBatch(values.length);
        for (int index = 0; index < values.length; index++) {
            batch.addValues(
                    index,
                    EntityKind.PRIMARY,
                    values[index],
                    VariableKind.VALUE,
                    0.0d,
                    0.0d,
                    1.0d);
        }
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch);
        return table;
    }

    private static NumericFactTable repeatedTable(
            int rows, long value) {
        NumericFactBatch batch = new NumericFactBatch(rows);
        for (int index = 0; index < rows; index++) {
            batch.addValues(
                    index,
                    EntityKind.PRIMARY,
                    value,
                    VariableKind.VALUE,
                    0.0d,
                    0.0d,
                    1.0d);
        }
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch);
        return table;
    }

    private static NumericFactTable balancedExtremeTable(int rows) {
        NumericFactBatch batch = new NumericFactBatch(rows);
        for (int index = 0; index < rows; index++) {
            batch.addValues(
                    index,
                    EntityKind.PRIMARY,
                    index < rows / 2
                            ? Long.MAX_VALUE : Long.MIN_VALUE,
                    VariableKind.VALUE,
                    0.0d,
                    0.0d,
                    1.0d);
        }
        NumericFactTable table = NumericFactTable.create();
        table.addBatch(batch);
        return table;
    }

    private static OwnerFactTable ownerTable(long... costs) {
        OwnedOptionBatch options = new OwnedOptionBatch(costs.length);
        for (int index = 0; index < costs.length; index++) {
            options.addValues(new GroupId(index), costs[index]);
        }
        OwnerFactBatch batch = new OwnerFactBatch(1);
        batch.addValues(
                new WorkKey(
                        new NamespaceId(1L),
                        new ItemId(1L)),
                0,
                0L,
                new CategoryId(1L),
                options);
        OwnerFactTable table = OwnerFactTable.create();
        table.addBatch(batch);
        return table;
    }

    private static <R> void expectFailure(
            DataFlowDefinition<R> definition,
            NumericFactDataFlow.Source source,
            NumericFactTable table,
            DataFlowContext context,
            String code,
            String operation,
            String message) {
        try {
            execute(definition, source, table, context);
            throw new AssertionError(message + " must fail closed");
        } catch (SomaRuntimeException failure) {
            requireFailure(failure, code, operation, message);
        }
    }

    private static void requireFailure(
            SomaRuntimeException failure,
            String code,
            String operation,
            String message) {
        require(failure.category() == SomaErrorCategory.INVALID_INPUT
                        && code.equals(failure.code())
                        && operation.equals(failure.operation())
                        && failure.path().length() > 0,
                message + " structured failure");
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

    private static <R> R execute(
            DataFlowDefinition<R> definition,
            OwnerFactDataFlow.Source source,
            OwnerFactTable table,
            DataFlowContext context) {
        return definition.compile()
                .newInvocation(context)
                .bind(source, OwnerFactDataFlow.bind(table))
                .execute();
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
