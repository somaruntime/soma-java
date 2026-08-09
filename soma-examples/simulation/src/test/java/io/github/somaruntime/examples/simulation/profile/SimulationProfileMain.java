package io.github.somaruntime.examples.simulation.profile;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.examples.simulation.EntityState;
import io.github.somaruntime.examples.simulation.EntityStateTable;
import io.github.somaruntime.examples.simulation.Event;
import io.github.somaruntime.examples.simulation.EventTable;
import io.github.somaruntime.examples.simulation.Soma;
import io.github.somaruntime.examples.simulation.SomaGroup;
import io.github.somaruntime.examples.simulation.schema.EventType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.LongSupplier;

/** Narrow event-state million-row profile; not part of the published example artifact. */
public final class SimulationProfileMain {
    private static volatile long blackhole;

    private SimulationProfileMain() {}

    public static void main(String[] args) {
        int rows = rows(args);
        Baseline baseline = baseline(rows);
        blackhole ^= baseline.fingerprint;
        baseline = null;
        System.gc();

        ForkJoinPool executor = new ForkJoinPool(parallelism());
        try {
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(6L << 30)
                    .parallelExecutor(executor)
                    .compression(SomaCompression.AUTO)
                    .build());
            SomaGroup group = Soma.createGroup();
            EventTable events = group.eventTable();
            EntityStateTable states = group.entityStateTable();
            int entities = Math.max(1, rows / 10);
            states.reserve(entities);
            events.reserve(rows);
            long ingestStarted = System.nanoTime();
            for (int index = 0; index < entities; index++) {
                states.add(new EntityState(index + 1L, index, 0L));
            }
            for (int index = 0; index < rows; index++) {
                events.add(new Event(index + 1L, index % entities + 1L,
                        index % 100_000, index % 8,
                        (index & 1) == 0 ? EventType.ADD : EventType.SUBTRACT,
                        index % 97));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;

            LongSupplier scan = () -> events
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .sum();
            long somaScanNanos = median(scan, 5);
            long scanFingerprint = scan.getAsLong();
            LongSupplier parallelScan = () -> events.parallel()
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .sum();
            long parallelNanos = median(parallelScan, 5);
            long parallelFingerprint = parallelScan.getAsLong();
            require(scanFingerprint == parallelFingerprint,
                    "sequential/parallel simulation scan");

            long keyStarted = System.nanoTime();
            long keyFingerprint = 0L;
            for (int index = 0; index < 10_000; index++) {
                keyFingerprint += events.get((index * 7_919L) % rows + 1L).delta();
            }
            long keyNanos = System.nanoTime() - keyStarted;
            long entity = Math.min(42L, entities);
            long indexStarted = System.nanoTime();
            long entityEvents = events.byEntityId(entity).count();
            long indexNanos = System.nanoTime() - indexStarted;
            long topStarted = System.nanoTime();
            long firstEvent = events.top(1L,
                    events.eventMinute.asc()
                            .then(events.priority.desc())
                            .then(events.eventId.asc()))
                    .findFirst().get().eventId();
            long topNanos = System.nanoTime() - topStarted;
            events.remove(Math.max(1L, rows / 2L));

            TableMetadata metadata = events._metadata();
            long fingerprint = scanFingerprint ^ keyFingerprint ^ entityEvents
                    ^ firstEvent ^ metadata.size();
            blackhole ^= fingerprint;
            System.out.println("PROFILE scenario=simulation model=NARROW rows=" + rows
                    + " baselineIngestMs=" + ms(baselineIngestNanos)
                    + " baselineScanMs=" + ms(baselineScanNanos)
                    + " baselineKeyMs=" + ms(baselineKeyNanos)
                    + " somaIngestMs=" + ms(ingestNanos)
                    + " somaScanMs=" + ms(somaScanNanos)
                    + " parallelScanMs=" + ms(parallelNanos)
                    + " somaKeyMs=" + ms(keyNanos)
                    + " indexMs=" + ms(indexNanos)
                    + " topMs=" + ms(topNanos)
                    + " retainedBytes=" + group._metadata().retainedBytes()
                    + " representationBytes=" + metadata.representationBytes()
                    + " plainEquivalentBytes=" + metadata.plainEquivalentBytes()
                    + " fingerprint=" + fingerprint);
        } finally {
            executor.shutdown();
        }
    }

    private static long baselineIngestNanos;
    private static long baselineScanNanos;
    private static long baselineKeyNanos;

    private static Baseline baseline(int rows) {
        long started = System.nanoTime();
        List<BaselineEvent> list = new ArrayList<BaselineEvent>(rows);
        Map<Long, BaselineEvent> byId = new HashMap<Long, BaselineEvent>(rows * 4 / 3 + 1);
        int[] priorities = new int[rows];
        long[] deltas = new long[rows];
        for (int index = 0; index < rows; index++) {
            BaselineEvent event = new BaselineEvent(index + 1L, index % 8, index % 97);
            list.add(event);
            byId.put(event.id, event);
            priorities[index] = event.priority;
            deltas[index] = event.delta;
        }
        baselineIngestNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long scan = 0L;
        for (int index = 0; index < rows; index++) if (priorities[index] >= 4) scan += deltas[index];
        baselineScanNanos = System.nanoTime() - started;
        started = System.nanoTime();
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            key += byId.get((index * 7_919L) % rows + 1L).delta;
        }
        baselineKeyNanos = System.nanoTime() - started;
        return new Baseline(scan ^ key ^ list.size());
    }

    private static long median(LongSupplier supplier, int samples) {
        supplier.getAsLong(); supplier.getAsLong();
        long[] values = new long[samples];
        for (int index = 0; index < samples; index++) {
            long started = System.nanoTime();
            blackhole ^= supplier.getAsLong();
            values[index] = System.nanoTime() - started;
        }
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static int rows(String[] args) {
        int value = args.length == 0 ? 1_000_000 : Integer.parseInt(args[0]);
        if (value < 10_000 || value > 2_000_000) throw new IllegalArgumentException("rows out of profile range");
        return value;
    }

    private static int parallelism() {
        int value = Integer.getInteger("soma.profile.parallelism", 8);
        if (value < 1 || value > 16) throw new IllegalArgumentException("parallelism out of range");
        return value;
    }

    private static long ms(long nanos) { return nanos / 1_000_000L; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Baseline {
        final long fingerprint;
        Baseline(long fingerprint) { this.fingerprint = fingerprint; }
    }
    private static final class BaselineEvent {
        final long id;
        final int priority;
        final long delta;
        BaselineEvent(long id, int priority, long delta) {
            this.id = id; this.priority = priority; this.delta = delta;
        }
    }
}
