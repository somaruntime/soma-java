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
import io.github.somaruntime.soma.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

            ComposedMetrics composed = BenchmarkSupport.composedWorkload()
                    ? runComposed(events, expected.composed)
                    : null;

            long removeKey = Math.max(1L, rows / 2L);
            RemoveResult remove = events.remove(removeKey);
            BenchmarkSupport.require(remove.removed(), 1L, "simulation remove result");
            BenchmarkSupport.require(!events.find(removeKey).isPresent(),
                    "simulation removed Key is still present");
            long composedRemoved = composed == null ? 0L : composed.removed;
            BenchmarkSupport.require(events.size(), rows - composedRemoved - 1L,
                    "simulation remove size");

            TableMetadata metadata = events._metadata();
            BenchmarkSupport.require(metadata.size(), rows - composedRemoved - 1L,
                    "simulation metadata size");
            long sharedFingerprint = BenchmarkSupport.fingerprint(rows, scan.value(), key.value());
            long fingerprint = BenchmarkSupport.fingerprint(
                    sharedFingerprint,
                    index.value(), top.value(), remove.removed(), metadata.size());

            BenchmarkResult result = new BenchmarkResult(
                    "simulation", "NARROW", implementation, rows)
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
                    .put("fingerprint", composed == null
                            ? fingerprint
                            : BenchmarkSupport.fingerprint(fingerprint, composed.fingerprint));
            if (composed != null) composed.appendTo(result);
            result.print();
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
        return new Expected(
                entities, selectedEntity, scan, key, indexCount, firstEvent,
                BenchmarkSupport.composedWorkload()
                        ? composedExpected(rows, entities, selectedEntity) : null);
    }

    private static ComposedExpected composedExpected(
            int rows,
            int entities,
            long selectedEntity) {
        boolean[] values = new boolean[97];
        PriorityQueue<ExpectedEvent> top = new PriorityQueue<ExpectedEvent>(
                1_024, Collections.reverseOrder(ExpectedEvent.ORDER));
        long updateCount = 0L;
        long updateAllSum = 0L;
        long removeCount = 0L;
        long removedSelectedEntity = 0L;
        for (int index = 0; index < rows; index++) {
            long eventId = index + 1L;
            long entityId = index % entities + 1L;
            int minute = index % 100_000;
            int priority = index % 8;
            long delta = index % 97;
            boolean add = (index & 1) == 0;
            if (add && priority >= 4 && delta % 3L == 0L) {
                values[(int) delta] = true;
            }
            ExpectedEvent candidate = new ExpectedEvent(eventId, minute, priority);
            if (top.size() < 1_024) top.add(candidate);
            else if (ExpectedEvent.ORDER.compare(candidate, top.peek()) < 0) {
                top.poll();
                top.add(candidate);
            }
            if (add && priority >= 6) {
                updateAllSum += delta;
                if (updateCount < 10_000L) updateCount++;
            }
            if (!add && priority <= 1 && removeCount < 10_000L) {
                removeCount++;
                if (entityId == selectedEntity) removedSelectedEntity++;
            }
        }

        long primitiveFingerprint = 0xcbf29ce484222325L;
        int emitted = 0;
        int available = 0;
        for (boolean present : values) if (present) available++;
        int selected = Math.max(0, Math.min(16, available - 1));
        primitiveFingerprint = BenchmarkSupport.mix(primitiveFingerprint, selected);
        for (int value = 0; value < values.length && emitted < 17; value++) {
            if (!values[value]) continue;
            if (emitted++ == 0) continue;
            if (emitted > 17) break;
            primitiveFingerprint = BenchmarkSupport.mix(primitiveFingerprint, value);
        }

        List<ExpectedEvent> orderedTop = new ArrayList<ExpectedEvent>(top);
        Collections.sort(orderedTop, ExpectedEvent.ORDER);
        long topFingerprint = 0xcbf29ce484222325L;
        topFingerprint = BenchmarkSupport.mix(topFingerprint, orderedTop.size());
        for (ExpectedEvent event : orderedTop) {
            topFingerprint = BenchmarkSupport.mix(topFingerprint, event.eventId);
            topFingerprint = BenchmarkSupport.mix(topFingerprint, event.minute);
            topFingerprint = BenchmarkSupport.mix(topFingerprint, event.priority);
        }
        return new ComposedExpected(
                primitiveFingerprint,
                topFingerprint,
                updateCount,
                updateAllSum + updateCount,
                removeCount,
                rows - removeCount,
                ((rows - 1L - (selectedEntity - 1L)) / entities + 1L)
                        - removedSelectedEntity,
                selectedEntity);
    }

    private static ComposedMetrics runComposed(
            EventTable events,
            ComposedExpected expected) {
        LongMeasurement primitive = BenchmarkSupport.measure(() -> {
            long[] values = events
                    .filter(events.eventType.eq(EventType.ADD))
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .filter(value -> value % 3L == 0L)
                    .distinct()
                    .sorted()
                    .skip(1L)
                    .limit(16L)
                    .toArray();
            long hash = 0xcbf29ce484222325L;
            hash = BenchmarkSupport.mix(hash, values.length);
            for (long value : values) hash = BenchmarkSupport.mix(hash, value);
            return hash;
        });
        BenchmarkSupport.require(primitive.value(), expected.primitiveFingerprint,
                "simulation composed primitive pipeline");

        LongMeasurement primitiveParallel = BenchmarkSupport.measure(() -> {
            long[] values = events.parallel()
                    .filter(events.eventType.eq(EventType.ADD))
                    .filter(events.priority.ge(4))
                    .mapToLong(events.delta)
                    .filter(value -> value % 3L == 0L)
                    .distinct()
                    .sorted()
                    .skip(1L)
                    .limit(16L)
                    .toArray();
            long hash = 0xcbf29ce484222325L;
            hash = BenchmarkSupport.mix(hash, values.length);
            for (long value : values) hash = BenchmarkSupport.mix(hash, value);
            return hash;
        });
        BenchmarkSupport.require(primitiveParallel.value(), expected.primitiveFingerprint,
                "simulation composed parallel primitive pipeline");

        LongMeasurement materializedTop = BenchmarkSupport.measure(() -> {
            Event[] values = events
                    .top(1_024L, events.eventMinute.asc()
                            .then(events.priority.desc())
                            .then(events.eventId.asc()))
                    .toArray();
            long hash = 0xcbf29ce484222325L;
            hash = BenchmarkSupport.mix(hash, values.length);
            for (Event value : values) {
                hash = BenchmarkSupport.mix(hash, value.eventId());
                hash = BenchmarkSupport.mix(hash, value.eventMinute());
                hash = BenchmarkSupport.mix(hash, value.priority());
            }
            return hash;
        });
        BenchmarkSupport.require(materializedTop.value(), expected.topFingerprint,
                "simulation composed top materialization");

        long updateStarted = System.nanoTime();
        UpdateResult update = events
                .filter(events.eventType.eq(EventType.ADD))
                .filter(events.priority.ge(6))
                .limit(10_000L)
                .update(editor -> editor.delta(Math.addExact(editor.delta(), 1L)));
        long updateNanos = System.nanoTime() - updateStarted;
        BenchmarkSupport.require(update.matched(), expected.updateCount,
                "simulation composed update matched");
        BenchmarkSupport.require(update.changed(), expected.updateCount,
                "simulation composed update changed");
        long updatedSum = events
                .filter(events.eventType.eq(EventType.ADD))
                .filter(events.priority.ge(6))
                .mapToLong(events.delta)
                .sum();
        BenchmarkSupport.require(updatedSum, expected.updatedSum,
                "simulation composed updated sum");

        long removeStarted = System.nanoTime();
        RemoveResult remove = events
                .filter(events.eventType.eq(EventType.SUBTRACT))
                .filter(events.priority.le(1))
                .limit(10_000L)
                .remove();
        long removeNanos = System.nanoTime() - removeStarted;
        BenchmarkSupport.require(remove.removed(), expected.removeCount,
                "simulation composed remove count");
        BenchmarkSupport.require(events.size(), expected.postMutationSize,
                "simulation composed post-mutation size");
        long postIndex = events.byEntityId(expected.selectedEntity).count();
        BenchmarkSupport.require(postIndex, expected.postMutationIndexCount,
                "simulation composed post-mutation Index");

        long mutationFingerprint = BenchmarkSupport.fingerprint(
                update.matched(), update.changed(), updatedSum,
                remove.removed(), events.size(), postIndex);
        return new ComposedMetrics(
                primitive,
                primitiveParallel,
                materializedTop,
                updateNanos,
                removeNanos,
                remove.removed(),
                mutationFingerprint);
    }

    private static final class Expected {
        final int entities;
        final long selectedEntity;
        final long scan;
        final long key;
        final long indexCount;
        final long firstEvent;
        final ComposedExpected composed;

        Expected(
                int entities,
                long selectedEntity,
                long scan,
                long key,
                long indexCount,
                long firstEvent,
                ComposedExpected composed) {
            this.entities = entities;
            this.selectedEntity = selectedEntity;
            this.scan = scan;
            this.key = key;
            this.indexCount = indexCount;
            this.firstEvent = firstEvent;
            this.composed = composed;
        }
    }

    private static final class ComposedExpected {
        final long primitiveFingerprint;
        final long topFingerprint;
        final long updateCount;
        final long updatedSum;
        final long removeCount;
        final long postMutationSize;
        final long postMutationIndexCount;
        final long selectedEntity;

        ComposedExpected(
                long primitiveFingerprint,
                long topFingerprint,
                long updateCount,
                long updatedSum,
                long removeCount,
                long postMutationSize,
                long postMutationIndexCount,
                long selectedEntity) {
            this.primitiveFingerprint = primitiveFingerprint;
            this.topFingerprint = topFingerprint;
            this.updateCount = updateCount;
            this.updatedSum = updatedSum;
            this.removeCount = removeCount;
            this.postMutationSize = postMutationSize;
            this.postMutationIndexCount = postMutationIndexCount;
            this.selectedEntity = selectedEntity;
        }
    }

    private static final class ComposedMetrics {
        final LongMeasurement primitive;
        final LongMeasurement primitiveParallel;
        final LongMeasurement materializedTop;
        final long updateNanos;
        final long removeNanos;
        final long removed;
        final long fingerprint;

        ComposedMetrics(
                LongMeasurement primitive,
                LongMeasurement primitiveParallel,
                LongMeasurement materializedTop,
                long updateNanos,
                long removeNanos,
                long removed,
                long mutationFingerprint) {
            this.primitive = primitive;
            this.primitiveParallel = primitiveParallel;
            this.materializedTop = materializedTop;
            this.updateNanos = updateNanos;
            this.removeNanos = removeNanos;
            this.removed = removed;
            this.fingerprint = BenchmarkSupport.fingerprint(
                    primitive.value(), primitiveParallel.value(),
                    materializedTop.value(), mutationFingerprint);
        }

        void appendTo(BenchmarkResult result) {
            result.put("composedPrimitive", primitive)
                    .put("composedPrimitiveParallel", primitiveParallel)
                    .put("composedMaterializedTop", materializedTop)
                    .put("composedUpdateNanos", updateNanos)
                    .put("composedRemoveNanos", removeNanos)
                    .put("composedRemoved", removed)
                    .put("composedFingerprint", fingerprint);
        }
    }

    private static final class ExpectedEvent {
        static final Comparator<ExpectedEvent> ORDER = new Comparator<ExpectedEvent>() {
            @Override public int compare(ExpectedEvent left, ExpectedEvent right) {
                int minute = Integer.compare(left.minute, right.minute);
                if (minute != 0) return minute;
                int priority = Integer.compare(right.priority, left.priority);
                return priority != 0 ? priority : Long.compare(left.eventId, right.eventId);
            }
        };

        final long eventId;
        final int minute;
        final int priority;

        ExpectedEvent(long eventId, int minute, int priority) {
            this.eventId = eventId;
            this.minute = minute;
            this.priority = priority;
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
