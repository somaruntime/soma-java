package example.i5;

import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public final class I6ConsumerMain {
    private I6ConsumerMain() {}

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool(4);
        Soma.configure(SomaConfiguration.builder()
                .parallelExecutor(pool)
                .memoryBudgetBytes(256L << 20)
                .build());

        SomaGroup group = Soma.createGroup();
        MachineEventTable events = group.machineEventTable();
        MachineStateTable states = group.machineStateTable();
        for (int index = 0; index < 12_000; index++) {
            events.add(new MachineEvent(
                    index + 1L,
                    index & 15,
                    "R" + (index & 7),
                    index,
                    (index & 1) == 0));
        }
        for (int machine = 0; machine < 16; machine++) {
            states.add(new MachineState(
                    machine + 1L,
                    machine,
                    "R" + (machine & 7),
                    machine * 10L,
                    true));
        }

        long[] sequential = events
                .filter(events.duration.ge(6_000L))
                .mapToLong(events.duration)
                .toArray();
        long[] parallel = events
                .parallel()
                .filter(events.duration.ge(6_000L))
                .mapToLong(events.duration)
                .toArray();
        require(Arrays.equals(sequential, parallel),
                "parallel result and encounter order");
        Thread caller = Thread.currentThread();
        AtomicInteger callbacks = new AtomicInteger();
        events.parallel()
                .filter(events.eventId.le(16L))
                .forEachOrdered(view -> {
                    require(Thread.currentThread() == caller,
                            "ordered callback caller thread");
                    callbacks.incrementAndGet();
                });
        require(callbacks.get() == 16, "ordered callback cardinality");

        long sequentialJoin = events.join(states)
                .on(events.machineId, states.machineId)
                .filter(states.enabled.eq(true))
                .count();
        long parallelJoin = events.join(states)
                .on(events.machineId, states.machineId)
                .parallel()
                .filter(states.enabled.eq(true))
                .count();
        require(sequentialJoin == parallelJoin,
                "parallel relation logical equivalence");

        int changed = events
                .filter(events.eventId.le(8L))
                .parallel()
                .update(editor -> editor.duration(editor.duration() + 1L))
                .changed();
        require(changed == 8, "parallel Selection atomic mutation");
        require(events.parallel()._explain().contains("mode=PARALLEL"),
                "parallel explain mode");

        pool.shutdownNow();
        try {
            events.parallel().count();
            throw new AssertionError("expected unavailable executor");
        } catch (SomaOperationException failure) {
            require(failure.code() == SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                    "shutdown pool failure code");
        }
        try {
            events.join(states)
                    .on(events.machineId, states.machineId)
                    .parallel()
                    .count();
            throw new AssertionError("expected unavailable relation executor");
        } catch (SomaOperationException failure) {
            require(failure.code() == SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                    "shutdown relation pool failure code");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
