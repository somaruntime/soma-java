package example.i5;

import io.github.somaruntime.soma.GroupedLongEntry;
import io.github.somaruntime.soma.GroupedLongResult;
import io.github.somaruntime.soma.LongGroupedLongEntry;
import io.github.somaruntime.soma.LongGroupedLongResult;
import io.github.somaruntime.soma.LongGroupedDoubleResult;
import io.github.somaruntime.soma.LongGroupedLongSummaryResult;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaTuple2;
import java.util.Arrays;
import java.util.List;

public final class I5ConsumerMain {
    private I5ConsumerMain() {}

    public static void main(String[] args) {
        SomaGroup group = Soma.createGroup();
        MachineEventTable events = group.machineEventTable();
        MachineStateTable states = group.machineStateTable();

        events.add(new MachineEvent(1L, 10L, "A", 5L, true));
        events.add(new MachineEvent(2L, 10L, "A", 7L, true));
        events.add(new MachineEvent(3L, 20L, "B", 11L, false));
        events.add(new MachineEvent(4L, 30L, null, 13L, true));
        states.add(new MachineState(101L, 10L, "A", 100L, true));
        states.add(new MachineState(102L, 10L, "X", 110L, false));
        states.add(new MachineState(103L, 20L, "B", 120L, true));
        states.add(new MachineState(104L, 40L, null, 130L, true));

        LongGroupedLongResult counts = events.groupBy(events.machineId).count();
        LongGroupedLongEntry[] countEntries = counts.toArray();
        require(countEntries.length == 3
                        && countEntries[0].key() == 10L && countEntries[0].value() == 2L
                        && countEntries[1].key() == 20L && countEntries[1].value() == 1L
                        && countEntries[2].key() == 30L && countEntries[2].value() == 1L,
                "primitive GroupBy first-encounter order");

        LongGroupedLongResult enabledTotals = events
                .filter(events.enabled.eq(true))
                .groupBy(events.machineId)
                .sum(events.duration);
        require(enabledTotals.size() == 2L
                        && enabledTotals.toArray()[0].value() == 12L
                        && enabledTotals.toArray()[1].value() == 13L,
                "Selection GroupBy sum");

        require(events.groupBy(events.machineId)
                        .min(events.duration)
                        .toArray()[0].value() == 5L,
                "GroupBy minimum");
        require(events.groupBy(events.machineId)
                        .max(events.duration)
                        .toArray()[0].value() == 7L,
                "GroupBy maximum");
        LongGroupedDoubleResult averages = events
                .groupBy(events.machineId)
                .average(events.duration);
        require(averages.toArray()[0].value() == 6.0d,
                "GroupBy average");
        LongGroupedLongSummaryResult summaries = events
                .groupBy(events.machineId)
                .summaryStatistics(events.duration);
        SomaLongSummary firstSummary = summaries.toArray()[0].value();
        require(firstSummary.count() == 2L
                        && firstSummary.min() == 5L
                        && firstSummary.max() == 7L
                        && firstSummary.sum() == 12L
                        && firstSummary.average() == 6.0d,
                "GroupBy summary");

        GroupedLongResult<String> routeCounts = events.groupBy(events.route).count();
        GroupedLongEntry<String>[] routeEntries = routeCounts.toArray();
        require(routeEntries.length == 3
                        && "A".equals(routeEntries[0].key())
                        && "B".equals(routeEntries[1].key())
                        && routeEntries[2].key() == null,
                "nullable reference GroupBy");

        long inner = events.join(states)
                .on(events.machineId, states.machineId)
                .count();
        require(inner == 5L, "duplicate Equality Join Cartesian result");

        long[] order = events.join(states)
                .on(events.machineId, states.machineId)
                .mapToLong(pair -> pair.left().eventId() * 1000L
                        + pair.right().stateId())
                .toArray();
        require(Arrays.equals(order,
                        new long[] {1101L, 1102L, 2101L, 2102L, 3103L}),
                "Inner Join canonical left/right order");
        long joinedSum = events.join(states)
                .on(events.machineId, states.machineId)
                .mapToLong(pair -> pair.left().eventId() * 1000L
                        + pair.right().stateId())
                .sum();
        require(joinedSum == 9509L,
                "Inner Join fused integral aggregate");
        require(events.join(states)
                        .on(events.machineId, states.machineId)
                        .and(events.route, states.route)
                        .count() == 3L,
                "compound Equality Join");

        long left = events.join(states)
                .on(events.machineId, states.machineId)
                .left()
                .count();
        long full = events.join(states)
                .on(events.machineId, states.machineId)
                .full()
                .count();
        require(left == 6L && full == 7L, "Left and Full missing sides");

        final long[] missing = new long[2];
        events.join(states)
                .on(events.machineId, states.machineId)
                .full()
                .forEach(pair -> {
                    if (!pair.hasLeft()) missing[0]++;
                    if (!pair.hasRight()) missing[1]++;
                });
        require(missing[0] == 1L && missing[1] == 1L,
                "Outer Join explicit missing truth");

        require(events.join(states)
                        .on(events.machineId, states.machineId)
                        .semi()
                        .sortedBy(events.eventId.desc())
                        .mapToLong(events.eventId)
                        .toArray()[0] == 3L,
                "Semi Join re-enters left ReadStream");
        require(events.join(states)
                        .on(events.machineId, states.machineId)
                        .anti()
                        .count() == 1L,
                "Anti Join left membership");

        List<SomaTuple2<Long, Long>> selected = events.join(states)
                .on(events.machineId, states.machineId)
                .select(events.eventId, states.stateId)
                .toList();
        require(selected.size() == 5
                        && selected.get(0).first() == 1L
                        && selected.get(0).second() == 101L,
                "typed matched projection");

        require(events.join(states)
                        .on(events.machineId, states.machineId)
                        .filter(states.enabled.eq(true))
                        .count() == 3L,
                "typed relation filter");
        String explained = events.join(states)
                .on(events.machineId, states.machineId)
                .filter(states.enabled.eq(true))
                ._explain();
        require(explained.contains("physical=RIGHT_INDEX_LOOKUP")
                        && explained.contains("predicatePushdown=1"),
                "typed predicate pushdown and Index substitution explain");
        require(events.join(states)
                        .on(events.route, states.route)
                        .count() == 3L,
                "null never matches in Equality Join");

        require(events.crossJoin(states, 16L).count() == 16L,
                "bounded Cross Join");
        expect(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                () -> events.crossJoin(states, 15L).count());
    }

    private static void expect(SomaFailureCode code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaOperationException failure) {
            require(failure.code() == code,
                    "expected " + code + " but was " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
