package example.i3;

import io.github.somaruntime.soma.MappedStream;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaOperationException;
import example.i3.schema.Status;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class I3ConsumerMain {
    private I3ConsumerMain() {}

    public static void main(String[] args) {
        EventTable table = Soma.eventTable();
        Object firstPayload = new Object();
        table.add(new Event(1L, "a", true, (byte) 2, (short) 3, 'c',
                4, 10L, 1.5f, 4.0d, "x", firstPayload,
                new RouteKey(10L, 20L), Status.ACTIVE));
        table.add(new Event(2L, "b", false, (byte) 3, (short) 4, 'a',
                1, 20L, -0.0f, 2.0d, null, new Object(),
                new RouteKey(20L, 30L), Status.IDLE));
        table.add(new Event(3L, "a", true, (byte) 2, (short) 5, 'b',
                3, 30L, Float.NaN, Double.NaN, "x", new Object(),
                new RouteKey(10L, 40L), Status.ACTIVE));

        SomaExpression<EventTable.View> expression =
                table.kind.eq("a").and(table.enabled.eq(true));
        require(table.filter(expression).count() == 2L, "typed filter");
        require(table.filter(expression.not()).count() == 1L, "typed not");
        require(table.filter(table.id.between(1L, 2L)).count() == 2L,
                "inclusive between");
        require(table.filter(table.label.isNull()).count() == 1L
                        && table.filter(table.label.isNotNull()).count() == 2L
                        && table.filter(table.label.ne("x")).count() == 1L,
                "nullable predicate truth");
        require(table.filter(table.label.isNull()
                        .or(table.kind.eq("a").and(table.enabled.eq(true))))
                        .count() == 3L,
                "nested boolean expression");
        require(table.filter(table.id.in()).count() == 0L, "empty in");
        require(table.filter(table.id.in(1L, 1L, 3L)).count() == 2L,
                "set in");
        require(table.byKind("a").mapToLong(table.amount).sum() == 40L,
                "ordered Index substitution");

        long[] doubled = table.filter(table.kind.eq("a"))
                .mapToLong(table.amount)
                .map(value -> value * 2L)
                .toArray();
        require(Arrays.equals(doubled, new long[] {20L, 60L}),
                "typed projection");
        require(Arrays.equals(table.enabled.toArray(),
                new boolean[] {true, false, true}), "boolean array");
        require(Arrays.equals(table.code.toArray(),
                new byte[] {2, 3, 2}), "byte array");
        require(Arrays.equals(table.shortValue.toArray(),
                new short[] {3, 4, 5}), "short array");
        require(Arrays.equals(table.letter.toArray(),
                new char[] {'c', 'a', 'b'}), "char array");
        require(Arrays.equals(table.priority.toArray(),
                new int[] {4, 1, 3}), "int array");
        require(Arrays.equals(table.amount.toArray(),
                new long[] {10L, 20L, 30L}), "long array");
        float[] ratios = table.ratio.toArray();
        require(ratios.length == 3 && ratios[0] == 1.5f
                        && Float.floatToIntBits(ratios[1])
                        == Float.floatToIntBits(-0.0f)
                        && Float.isNaN(ratios[2]),
                "float array");
        double[] scores = table.score.toArray();
        require(scores.length == 3 && scores[0] == 4.0d
                && scores[1] == 2.0d && Double.isNaN(scores[2]),
                "double array");

        String[] labels = table.map(view -> view.label())
                .distinct()
                .sorted(Comparator.nullsFirst(String::compareTo))
                .toArray(String.class);
        require(labels.getClass() == String[].class
                && Arrays.equals(labels, new String[] {null, "x"}),
                "mapped typed array");
        CharSequence[] parentArray = table.map(view -> view.label())
                .toArray(CharSequence.class);
        require(parentArray.getClass() == CharSequence[].class,
                "mapped parent array");
        Object[] objectArray = table.map(view -> view.label())
                .toArray(Object.class);
        require(objectArray.getClass() == Object[].class,
                "mapped Object array");
        String[] allNull = table.map(view -> (String) null)
                .toArray(String.class);
        require(allNull.getClass() == String[].class && allNull.length == 3,
                "mapped all-null array");

        List<String> detached = table.label.map(
                value -> value == null ? "null" : value).toList();
        detached.add("application-owned");
        require(detached.size() == 4, "modifiable detached list");
        require(table.find(1L).get().payload() == firstPayload,
                "ordinary Object identity");
        require(Arrays.equals(table.route.from.toArray(),
                        new long[] {10L, 20L, 10L})
                        && table.route.toArray()[0].to() == 20L,
                "nested Value logical Field sources");
        Status[] statuses = table.status.distinct().toArray();
        require(statuses.length == 2
                        && statuses[0] == Status.ACTIVE
                        && statuses[1] == Status.IDLE,
                "Enum identity and encounter order");

        require(table.enabled.mapToInt(value -> value ? 1 : 0).count() == 3L
                        && table.enabled.mapToLong(value -> value ? 1L : 0L).count() == 3L
                        && table.enabled.mapToDouble(value -> value ? 1.0d : 0.0d).count() == 3L
                        && table.code.mapToInt(value -> value).count() == 3L
                        && table.code.mapToLong(value -> value).count() == 3L
                        && table.code.mapToDouble(value -> value).count() == 3L
                        && table.shortValue.mapToInt(value -> value).count() == 3L
                        && table.shortValue.mapToLong(value -> value).count() == 3L
                        && table.shortValue.mapToDouble(value -> value).count() == 3L
                        && table.letter.mapToInt(value -> value).count() == 3L
                        && table.letter.mapToLong(value -> value).count() == 3L
                        && table.letter.mapToDouble(value -> value).count() == 3L
                        && table.priority.mapToLong(value -> value).count() == 3L
                        && table.priority.mapToDouble(value -> value).count() == 3L
                        && table.amount.mapToInt(value -> (int) value).count() == 3L
                        && table.amount.mapToDouble(value -> value).count() == 3L
                        && table.ratio.mapToInt(value -> (int) value).count() == 3L
                        && table.ratio.mapToLong(value -> (long) value).count() == 3L
                        && table.ratio.mapToDouble(value -> value).count() == 3L
                        && table.score.mapToInt(value -> (int) value).count() == 3L
                        && table.score.mapToLong(value -> (long) value).count() == 3L,
                "complete primitive conversion surface");
        require(table.label.mapToInt(value -> value == null ? 0 : value.length()).count() == 3L
                        && table.label.mapToLong(value -> value == null ? 0L : value.length()).count() == 3L
                        && table.label.mapToDouble(value -> value == null ? 0.0d : value.length()).count() == 3L
                        && table.map(view -> view.label())
                                .mapToInt(value -> value == null ? 0 : value.length()).count() == 3L
                        && table.map(view -> view.label())
                                .mapToLong(value -> value == null ? 0L : value.length()).count() == 3L
                        && table.map(view -> view.label())
                                .mapToDouble(value -> value == null ? 0.0d : value.length()).count() == 3L,
                "reference and mapped primitive conversion surface");

        Event[] ordered = table.sortedBy(
                table.kind.asc().then(table.priority.desc())).toArray();
        require(ordered[0].id() == 1L && ordered[1].id() == 3L
                && ordered[2].id() == 2L, "stable lexicographic order");
        Event[] top = table.top(2L, table.priority.desc()).toArray();
        Event[] limited = table.sortedBy(table.priority.desc())
                .limit(2L).toArray();
        require(top[0].id() == limited[0].id()
                        && top[1].id() == limited[1].id(),
                "top equivalence");
        require(table.skip(0L).count() == 3L
                        && table.skip(99L).count() == 0L
                        && table.limit(0L).count() == 0L
                        && table.limit(99L).count() == 3L
                        && table.top(0L, table.priority.asc()).count() == 0L
                        && table.top(99L, table.priority.asc()).count() == 3L,
                "slice and top boundaries");

        EventTable empty = Soma.createGroup().eventTable();
        require(!empty.anyMatch(view -> true)
                        && empty.allMatch(view -> false)
                        && empty.noneMatch(view -> true),
                "empty match identities");
        SomaLongSummary emptySummary = empty.amount.summaryStatistics();
        require(emptySummary.count() == 0L && emptySummary.sum() == 0L,
                "empty summary");

        MappedStream<String> unclaimed = table.map(view -> view.label());
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> unclaimed.toArray(null));
        require(unclaimed.count() == 3L,
                "phase-one validation must not consume pipeline");
        EventTable.KindField.Stream unclaimedTop = table.kind.filter(value -> true);
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> unclaimedTop.top(-1L, String::compareTo));
        require(unclaimedTop.count() == 3L,
                "negative top must not consume Field Stream");
        EventTable foreign = Soma.createGroup().eventTable();
        EventTable.Selection unclaimedProjection = table.filter(table.id.gt(0L));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> unclaimedProjection.mapToLong(foreign.amount));
        require(unclaimedProjection.count() == 3L,
                "wrong-owner Field projection must not consume Selection");

        Comparator<String> noReverse = new Comparator<String>() {
            @Override public int compare(String left, String right) {
                return left.compareTo(right);
            }
            @Override public Comparator<String> reversed() {
                throw new IllegalStateException("must not be called");
            }
        };
        require("b".equals(table.kind.max(noReverse).get()),
                "max Comparator remains inside callback boundary");
        expect(SomaFailureCode.CALLBACK_FAILED,
                () -> table.kind.max((left, right) -> {
                    throw new IllegalStateException("application comparator");
                }));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> table.map(view -> view.label()).toArray(int.class));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> table.map(view -> view.label()).toArray(Integer.class));
        expect(SomaFailureCode.NULL_VALUE_UNSUPPORTED,
                () -> table.label.sorted().findFirst());

        final int[] callbackCalls = new int[1];
        require(table.filter(view -> {
            callbackCalls[0]++;
            return true;
        }).count() == 3L && callbackCalls[0] == 3,
                "full row callback traversal");
        callbackCalls[0] = 0;
        require(table.map(view -> {
            callbackCalls[0]++;
            return view.priority();
        }).count() == 3L && callbackCalls[0] == 3,
                "full mapped callback traversal");
        final List<Long> encounterTrace = new java.util.ArrayList<Long>();
        table.forEach(view -> encounterTrace.add(view.id()));
        require(encounterTrace.equals(Arrays.asList(1L, 2L, 3L)),
                "row forEach canonical order and exactly once");
        encounterTrace.clear();
        table.map(view -> view.id()).forEach(encounterTrace::add);
        require(encounterTrace.equals(Arrays.asList(1L, 2L, 3L)),
                "mapped forEach canonical order and exactly once");
        encounterTrace.clear();
        table.amount.map(value -> value).forEach(encounterTrace::add);
        require(encounterTrace.equals(Arrays.asList(10L, 20L, 30L)),
                "primitive forEach canonical order and exactly once");
        callbackCalls[0] = 0;
        String explain = table.filter(view -> {
            callbackCalls[0]++;
            return true;
        })._explain();
        require(callbackCalls[0] == 0
                        && explain.contains("callbackBarrier=true")
                        && explain.contains("physicalSource")
                        && explain.contains("estimatedTemporaryPeakBytes"),
                "logical explain without execution");
        table.filter(view -> {
            callbackCalls[0]++;
            return true;
        }).limit(1L).sortedBy(table.id.asc()).count();
        require(callbackCalls[0] == 1, "canonical short-circuit prefix");

        expect(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                () -> table.map(view -> view).count());
        expect(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                () -> table.map(view -> view.route()).count());
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> table.id.between(3L, 1L));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> table.kind.in("a", null));

        EventTable cancelling = Soma.createGroup().eventTable();
        cancelling.add(new Event(1L, null, true, (byte) 0, (short) 0,
                'a', 0, Long.MAX_VALUE, 0.0f, 0.0d, null, null,
                new RouteKey(0L, 0L), Status.ACTIVE));
        cancelling.add(new Event(2L, null, true, (byte) 0, (short) 0,
                'a', 0, Long.MAX_VALUE, 0.0f, 0.0d, null, null,
                new RouteKey(0L, 0L), Status.ACTIVE));
        cancelling.add(new Event(3L, null, true, (byte) 0, (short) 0,
                'a', 0, -Long.MAX_VALUE, 0.0f, 0.0d, null, null,
                new RouteKey(0L, 0L), Status.ACTIVE));
        cancelling.add(new Event(4L, null, true, (byte) 0, (short) 0,
                'a', 0, -Long.MAX_VALUE, 0.0f, 0.0d, null, null,
                new RouteKey(0L, 0L), Status.ACTIVE));
        require(cancelling.amount.sum() == 0L,
                "signed-128 final-range integer sum");
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
