package io.github.somaruntime.benchmarks.simulation;

import io.github.somaruntime.benchmarks.BenchmarkResult;
import io.github.somaruntime.benchmarks.BenchmarkSupport;
import io.github.somaruntime.benchmarks.LongMeasurement;
import io.github.somaruntime.examples.simulation.EntityState;
import io.github.somaruntime.examples.simulation.EntityStateTable;
import io.github.somaruntime.examples.simulation.Event;
import io.github.somaruntime.examples.simulation.EventTable;
import io.github.somaruntime.examples.simulation.Soma;
import io.github.somaruntime.examples.simulation.SomaGroup;
import io.github.somaruntime.examples.simulation.schema.EventType;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.TableMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/** Narrow event-state benchmark and scenario correctness consumer. */
public final class SimulationBenchmarkMain {
    private SimulationBenchmarkMain() {}

    public static void main(String[] args) {
        int rows = BenchmarkSupport.rows(args);
        String implementation = BenchmarkSupport.implementation(args);
        Expected expected = expected(rows);
        if ("manual".equals(implementation)) runManual(rows, expected);
        else runSoma(rows, implementation, expected);
    }

    private static void runSoma(int rows, String implementation, Expected expected) {
        ForkJoinPool executor = new ForkJoinPool(BenchmarkSupport.parallelism());
        try {
            long budget = Long.getLong("soma.benchmark.memoryBudgetBytes", 6L << 30);
            BenchmarkSupport.require(budget > 0L && budget <= (32L << 30),
                    "memory budget outside benchmark envelope");
            SomaCompression compression = "soma-off".equals(implementation)
                    ? SomaCompression.OFF : SomaCompression.AUTO;
            Soma.configure(SomaConfiguration.builder()
                    .memoryBudgetBytes(budget)
                    .parallelExecutor(executor)
                    .compression(compression)
                    .build());

            SomaGroup group = Soma.createGroup();
            EventTable events = group.eventTable();
            EntityStateTable states = group.entityStateTable();
            states.reserve(expected.entities);
            events.reserve(rows);

            long ingestStarted = System.nanoTime();
            for (int index = 0; index < expected.entities; index++) {
                states.add(new EntityState(index + 1L, index, 0L));
            }
            for (int index = 0; index < rows; index++) {
                events.add(new Event(index + 1L, index % expected.entities + 1L,
                        index % 100_000, index % 8,
                        (index & 1) == 0 ? EventType.ADD : EventType.SUBTRACT,
                        index % 97));
            }
            long ingestNanos = System.nanoTime() - ingestStarted;
            BenchmarkSupport.require(events.size(), rows, "simulation ingest size");

            LongMeasurement scan = BenchmarkSupport.measure(() -> events
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .sum());
            BenchmarkSupport.require(scan.value(), expected.scan, "simulation scan");

            LongMeasurement parallelScan = BenchmarkSupport.measure(() -> events.parallel()
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .sum());
            BenchmarkSupport.require(parallelScan.value(), expected.scan,
                    "simulation parallel scan");

            LongMeasurement key = BenchmarkSupport.measure(() -> {
                long value = 0L;
                for (int index = 0; index < 10_000; index++) {
                    value += events.get((index * 7_919L) % rows + 1L).delta();
                }
                return value;
            });
            BenchmarkSupport.require(key.value(), expected.key, "simulation Key lookup");

            LongMeasurement index = BenchmarkSupport.measure(() ->
                    events.byEntityId(expected.selectedEntity).count());
            BenchmarkSupport.require(index.value(), expected.indexCount,
                    "simulation Index selection");

            LongMeasurement top = BenchmarkSupport.measure(() -> events
                    .top(1L, events.eventMinute.asc()
                            .then(events.priority.desc())
                            .then(events.eventId.asc()))
                    .findFirst().get().eventId());
            BenchmarkSupport.require(top.value(), expected.firstEvent, "simulation top");

            long removeKey = Math.max(1L, rows / 2L);
            RemoveResult remove = events.remove(removeKey);
            BenchmarkSupport.require(remove.removed(), 1L, "simulation remove result");
            BenchmarkSupport.require(!events.find(removeKey).isPresent(),
                    "simulation removed Key is still present");
            BenchmarkSupport.require(events.size(), rows - 1L, "simulation remove size");

            TableMetadata metadata = events._metadata();
            BenchmarkSupport.require(metadata.size(), rows - 1L, "simulation metadata size");
            long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
            long fingerprint = BenchmarkSupport.fingerprint(
                    sharedFingerprint,
                    index.value(), top.value(), remove.removed(), metadata.size());

            new BenchmarkResult("simulation", "NARROW", implementation, rows)
                    .put("correctness", true)
                    .put("compression", compression.name())
                    .put("ingestNanos", ingestNanos)
                    .put("scan", scan)
                    .put("parallelScan", parallelScan)
                    .put("key10k", key)
                    .put("index", index)
                    .put("top", top)
                    .put("retainedBytes", group._metadata().retainedBytes())
                    .put("representationBytes", metadata.representationBytes())
                    .put("plainEquivalentBytes", metadata.plainEquivalentBytes())
                    .put("sharedFingerprint", sharedFingerprint)
                    .put("fingerprint", fingerprint)
                    .print();
        } finally {
            executor.shutdown();
        }
    }

    private static void runManual(int rows, Expected expected) {
        long started = System.nanoTime();
        List<ManualEvent> list = new ArrayList<ManualEvent>(rows);
        Map<Long, ManualEvent> byId = new HashMap<Long, ManualEvent>(rows * 4 / 3 + 1);
        int[] priorities = new int[rows];
        long[] deltas = new long[rows];
        for (int index = 0; index < rows; index++) {
            ManualEvent event = new ManualEvent(index + 1L, index % 8, index % 97);
            list.add(event);
            byId.put(event.id, event);
            priorities[index] = event.priority;
            deltas[index] = event.delta;
        }
        long ingestNanos = System.nanoTime() - started;

        LongMeasurement scan = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (int index = 0; index < rows; index++) {
                if (priorities[index] >= 4) value += deltas[index];
            }
            return value;
        });
        BenchmarkSupport.require(scan.value(), expected.scan, "manual simulation scan");

        LongMeasurement key = BenchmarkSupport.measure(() -> {
            long value = 0L;
            for (int index = 0; index < 10_000; index++) {
                value += byId.get((index * 7_919L) % rows + 1L).delta;
            }
            return value;
        });
        BenchmarkSupport.require(key.value(), expected.key, "manual simulation Key lookup");
        BenchmarkSupport.require(list.size(), rows, "manual simulation ingest size");

        long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
        new BenchmarkResult("simulation", "NARROW", "manual", rows)
                .put("correctness", true)
                .put("ingestNanos", ingestNanos)
                .put("scan", scan)
                .put("key10k", key)
                .put("sharedFingerprint", sharedFingerprint)
                .put("fingerprint", sharedFingerprint)
                .print();
    }

    private static Expected expected(int rows) {
        int entities = Math.max(1, rows / 10);
        long selectedEntity = Math.min(42L, entities);
        long scan = 0L;
        long indexCount = 0L;
        long firstEvent = -1L;
        int firstMinute = Integer.MAX_VALUE;
        int firstPriority = Integer.MIN_VALUE;
        for (int index = 0; index < rows; index++) {
            long eventId = index + 1L;
            int priority = index % 8;
            int minute = index % 100_000;
            if (priority >= 4) scan += index % 97;
            if (index % entities + 1L == selectedEntity) indexCount++;
            if (minute < firstMinute
                    || (minute == firstMinute && priority > firstPriority)
                    || (minute == firstMinute && priority == firstPriority
                    && eventId < firstEvent)) {
                firstMinute = minute;
                firstPriority = priority;
                firstEvent = eventId;
            }
        }
        long key = 0L;
        for (int index = 0; index < 10_000; index++) {
            long id = (index * 7_919L) % rows + 1L;
            key += (id - 1L) % 97L;
        }
        return new Expected(entities, selectedEntity, scan, key, indexCount, firstEvent);
    }

    private static final class Expected {
        final int entities;
        final long selectedEntity;
        final long scan;
        final long key;
        final long indexCount;
        final long firstEvent;

        Expected(
                int entities,
                long selectedEntity,
                long scan,
                long key,
                long indexCount,
                long firstEvent) {
            this.entities = entities;
            this.selectedEntity = selectedEntity;
            this.scan = scan;
            this.key = key;
            this.indexCount = indexCount;
            this.firstEvent = firstEvent;
        }
    }

    private static final class ManualEvent {
        final long id;
        final int priority;
        final long delta;

        ManualEvent(long id, int priority, long delta) {
            this.id = id;
            this.priority = priority;
            this.delta = delta;
        }
    }
}
