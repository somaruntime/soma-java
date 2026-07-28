package com.example.soma.enumkeyed;

import com.example.soma.enumkeyed.generated.EnumKeyedJobBatch;
import com.example.soma.enumkeyed.generated.EnumKeyedJobTable;
import com.example.soma.enumkeyed.generated.DateKeyedDayBatch;
import com.example.soma.enumkeyed.generated.DateKeyedDayTable;
import com.example.soma.enumkeyed.generated.TimeKeyedTickBatch;
import com.example.soma.enumkeyed.generated.TimeKeyedTickTable;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentBatch;
import com.example.soma.enumkeyed.generated.DateTimeKeyedMomentTable;
import io.github.somaruntime.soma.runtime.EnumColumnView;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

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
        testSemanticKeyBindings();
    }

    private static void testSemanticKeyBindings() {
        DateKeyedDayBatch dayBatch = new DateKeyedDayBatch();
        dayBatch.addValues(20_000, 1);
        DateKeyedDayTable day = DateKeyedDayTable.create();
        day.addBatch(dayBatch);
        require(day.fetch(20_000).payload == 1, "DATE key binding");

        TimeKeyedTickBatch timeBatch = new TimeKeyedTickBatch();
        timeBatch.addValues(12_345_678_901L, 2);
        TimeKeyedTickTable time = TimeKeyedTickTable.create();
        time.addBatch(timeBatch);
        require(time.fetch(12_345_678_901L).payload == 2, "TIME key binding");

        DateTimeKeyedMomentBatch momentBatch = new DateTimeKeyedMomentBatch();
        momentBatch.addValues(1_700_000_000_000L, 3);
        DateTimeKeyedMomentTable moment = DateTimeKeyedMomentTable.create();
        moment.addBatch(momentBatch);
        require(moment.fetch(1_700_000_000L * 1000L).payload == 3,
                "DATE_TIME key binding");
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
