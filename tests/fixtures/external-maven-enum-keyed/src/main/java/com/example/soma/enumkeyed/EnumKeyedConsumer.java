package com.example.soma.enumkeyed;

import com.example.soma.enumkeyed.generated.EnumKeyedJobBatch;
import com.example.soma.enumkeyed.generated.EnumKeyedJobDataFlow;
import com.example.soma.enumkeyed.generated.EnumKeyedJobTable;
import com.example.soma.enumkeyed.generated.DateKeyedDayBatch;
import com.example.soma.enumkeyed.generated.DateKeyedDayDataFlow;
import com.example.soma.enumkeyed.generated.DateKeyedDayTable;
import com.example.soma.enumkeyed.generated.TimeKeyedTickBatch;
import com.example.soma.enumkeyed.generated.TimeKeyedTickDataFlow;
import com.example.soma.enumkeyed.generated.TimeKeyedTickTable;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentBatch;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentDataFlow;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentTable;
import io.github.somaruntime.soma.dataflow.DataFlowContext;
import io.github.somaruntime.soma.dataflow.DataFlowDefinition;
import io.github.somaruntime.soma.dataflow.LongScalarResult;
import io.github.somaruntime.soma.dataflow.SourceSlot;
import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.EnumColumnView;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;

public final class EnumKeyedConsumer {
    private EnumKeyedConsumer() {
    }

    public static void main(String[] args) {
        EnumKeyedJobBatch batch = new EnumKeyedJobBatch();
        batch.addValues(LifecycleState.QUEUED, 10);
        batch.addValues(LifecycleState.RUNNING, 20);
        EnumKeyedJobTable table = EnumKeyedJobTable.create();
        table.addBatch(batch);

        require(table.containsKey(LifecycleState.QUEUED), "enum keyed contains");
        require(table.fetch(LifecycleState.RUNNING).payload == 20, "enum keyed fetch");
        require(table.keys().fetchAll().get(0) == LifecycleState.QUEUED,
                "enum key export preserves member");
        final int[] visited = new int[] {0};
        table.stateValues().forEach(new Consumer<LifecycleState>() {
            @Override
            public void accept(LifecycleState value) {
                visited[0] += value.ordinal() + 1;
            }
        });
        require(visited[0] == 3, "enum ordinal pipeline binding");
        EnumColumnView<LifecycleState> view = table.stateColumn();
        try {
            require(view.get(1) == LifecycleState.RUNNING, "enum column view binding");
        } finally {
            view.close();
        }

        expectCode("duplicate_key", new Action() {
            @Override
            public void run() {
                table.addBatch(new EnumKeyedJobBatch().addValues(LifecycleState.QUEUED, 99));
            }
        });
        expectCode("invalid_null_value", new Action() {
            @Override
            public void run() {
                table.containsKey(null);
            }
        });
        table.delete(LifecycleState.QUEUED);
        require(table.fetch(LifecycleState.RUNNING).payload == 20,
                "enum key compaction repair");
        EnumKeyedJobDataFlow.Source enumSource =
                EnumKeyedJobDataFlow.source("enum-job");
        require(execute(
                        enumSource.candidates()
                                .filter(enumSource.columns().state()
                                        .equalTo(LifecycleState.RUNNING))
                                .count(),
                        enumSource,
                        EnumKeyedJobDataFlow.bind(table)).value() == 1L,
                "enum logical expression binding");
        testSemanticKeyBindings();
        table.release();
    }

    private static void testSemanticKeyBindings() {
        DateKeyedDayBatch dayBatch = new DateKeyedDayBatch();
        dayBatch.addValues(20_000, 1);
        DateKeyedDayTable day = DateKeyedDayTable.create();
        day.addBatch(dayBatch);
        require(day.fetch(20_000).payload == 1, "DATE key binding");
        DateKeyedDayDataFlow.Source daySource =
                DateKeyedDayDataFlow.source("day");
        require(execute(
                        daySource.candidates()
                                .filter(daySource.columns().epochDay()
                                        .equalTo(LocalDate.ofEpochDay(20_000L)))
                                .count(),
                        daySource,
                        DateKeyedDayDataFlow.bind(day)).value() == 1L,
                "DATE logical expression binding");
        require(execute(
                        daySource.candidates()
                                .filter(daySource.columns().epochDay()
                                        .plusDays(2L)
                                        .minusDays(1L)
                                        .equalTo(LocalDate.ofEpochDay(20_001L)))
                                .count(),
                        daySource,
                        DateKeyedDayDataFlow.bind(day)).value() == 1L,
                "DATE type-specific arithmetic");

        TimeKeyedTickBatch timeBatch = new TimeKeyedTickBatch();
        timeBatch.addValues(12_345_678_901L, 2);
        TimeKeyedTickTable time = TimeKeyedTickTable.create();
        time.addBatch(timeBatch);
        require(time.fetch(12_345_678_901L).payload == 2, "TIME key binding");
        TimeKeyedTickDataFlow.Source timeSource =
                TimeKeyedTickDataFlow.source("time");
        require(execute(
                        timeSource.candidates()
                                .filter(timeSource.columns().nanosOfDay()
                                        .equalTo(LocalTime.ofNanoOfDay(
                                                12_345_678_901L)))
                                .count(),
                        timeSource,
                        TimeKeyedTickDataFlow.bind(time)).value() == 1L,
                "TIME logical expression binding");
        LocalTime baseTime = LocalTime.ofNanoOfDay(12_345_678_901L);
        require(execute(
                        timeSource.candidates()
                                .filter(timeSource.columns().nanosOfDay()
                                        .plusNanos(Long.MAX_VALUE)
                                        .minusNanos(Long.MIN_VALUE)
                                        .equalTo(baseTime
                                                .plusNanos(Long.MAX_VALUE)
                                                .minusNanos(Long.MIN_VALUE)))
                                .count(),
                        timeSource,
                        TimeKeyedTickDataFlow.bind(time)).value() == 1L,
                "TIME modular arithmetic");
        expectCode("invalid_time_value", new Action() {
            @Override
            public void run() {
                new TimeKeyedTickBatch().addValues(-1L, 9);
            }
        });
        expectCode("invalid_time_value", new Action() {
            @Override
            public void run() {
                new TimeKeyedTickBatch()
                        .addValues(86_400_000_000_000L, 9);
            }
        });

        DateTimeKeyedMomentBatch momentBatch = new DateTimeKeyedMomentBatch();
        momentBatch.addValues(1_700_000_000_000L, 3);
        momentBatch.addValues(Long.MIN_VALUE, 4);
        DateTimeKeyedMomentTable moment = DateTimeKeyedMomentTable.create();
        moment.addBatch(momentBatch);
        require(moment.fetch(1_700_000_000L * 1000L).payload == 3,
                "DATE_TIME key binding");
        DateTimeKeyedMomentDataFlow.Source momentSource =
                DateTimeKeyedMomentDataFlow.source("moment");
        require(execute(
                        momentSource.candidates()
                                .filter(momentSource.columns().epochMillis()
                                        .equalTo(Instant.ofEpochMilli(
                                                1_700_000_000_000L)))
                                .count(),
                        momentSource,
                        DateTimeKeyedMomentDataFlow.bind(moment)).value() == 1L,
                "DATE_TIME logical expression binding");
        require(execute(
                        momentSource.candidates()
                                .filter(momentSource.columns().epochMillis()
                                        .equalTo(Instant.ofEpochMilli(
                                                Long.MIN_VALUE)))
                                .filter(momentSource.columns().epochMillis()
                                        .minusMillis(Long.MIN_VALUE)
                                        .equalTo(Instant.EPOCH))
                                .count(),
                        momentSource,
                        DateTimeKeyedMomentDataFlow.bind(moment)).value() == 1L,
                "DATE_TIME checked carrier arithmetic");

        day.release();
        time.release();
        moment.release();
    }

    private static <B extends DataFlowBinding> LongScalarResult execute(
            DataFlowDefinition<LongScalarResult> definition,
            SourceSlot<B> source,
            B binding) {
        DataFlowContext context = DataFlowContext.sequential();
        try {
            return definition.compile()
                    .newInvocation(context)
                    .bind(source, binding)
                    .execute();
        } finally {
            context.close();
        }
    }

    private static void expectCode(String expected, Action action) {
        try {
            action.run();
            throw new AssertionError("expected failure " + expected);
        } catch (SomaRuntimeException failure) {
            require(expected.equals(failure.code()), "unexpected failure " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Action {
        void run();
    }
}
