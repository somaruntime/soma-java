package com.example.soma.breadth;

import com.example.soma.breadth.generated.FullRowBatch;
import com.example.soma.breadth.generated.FullRowMutator;
import com.example.soma.breadth.generated.FullRowTable;
import com.example.soma.breadth.generated.StringKeyRowBatch;
import com.example.soma.breadth.generated.StringKeyRowTable;
import com.example.soma.breadth.generated.StringParentBatch;
import com.example.soma.breadth.generated.StringParentTable;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.SomaRuntimeException;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.UpdateResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BreadthConsumer {
    private BreadthConsumer() { }

    public static void main(String[] args) {
        verifyDefaultsAndReferenceFields();
        verifyFailedUpdateDoesNotRetainReferences();
        verifyPresenceWordBoundaries();
        verifyStringKeyAndBudgets();
        verifyStringKeyedChild();
        System.out.println("breadth-phase5-consumer: ok");
    }

    private static void verifyDefaultsAndReferenceFields() {
        FullRowBatch batch = new FullRowBatch().addValues(row -> {
            row.setName("alpha");
            row.setPoint(new Point(1, 2));
            row.setScalar(new ScalarValue(10));
        });
        FullRowTable table = FullRowTable.create();
        table.reserve(96);
        check(table.size() == 0 && table.capacity() >= 96, "reserve");
        table.addBatch(batch);
        FullRow value = table.fetchAt(0);
        check(value.active && value.small == -7 && value.medium == 9
                        && value.priority == 7 && value.total == 1234567890123L
                        && Float.isNaN(value.payload)
                        && value.ratio == Double.POSITIVE_INFINITY
                        && Float.floatToRawIntBits(value.score)
                                == Float.floatToRawIntBits(-0.0f)
                        && "default".equals(value.label)
                        && value.state == State.READY,
                "writer scalar/String/enum defaults");
        check(value.day == (int) LocalDate.parse("2026-01-02").toEpochDay(),
                "semantic date default");
        check(value.time == LocalTime.parse("12:34:56.123456789").toNanoOfDay()
                        && value.instant == Instant.parse("2026-01-02T03:04:05Z")
                        .toEpochMilli(),
                "semantic time defaults");
        check("alpha".equals(value.name) && value.point.equals(new Point(1, 2))
                        && value.leafDefaults.equals(new LeafDefaults(3, "leaf"))
                        && value.scalar.equals(new ScalarValue(10)),
                "required String/value fields and recursive value defaults");
        check(value.note == null && value.optionalState == null
                        && value.optionalPoint == null && value.optionalScalar == null,
                "optional reference absent");

        FullRowMutator mutation = table.mutateAt(0).setNote("note")
                .setOptionalState(State.RUNNING).setOptionalPoint(new Point(8, 9))
                .setScalar(new ScalarValue(11))
                .setOptionalScalar(new ScalarValue(12));
        mutation.commit();
        value = table.fetchAt(0);
        check("note".equals(value.note) && value.optionalState == State.RUNNING
                        && value.optionalPoint.equals(new Point(8, 9))
                        && value.scalar.equals(new ScalarValue(11))
                        && value.optionalScalar.equals(new ScalarValue(12)),
                "optional String/enum/value and scalar-value mutation");
        UpdateResult updated = table.update(row -> {
            row.setScalar(new ScalarValue(20));
            row.setOptionalScalar(new ScalarValue(21));
        });
        check(updated.changed() == 1 && table.fetchAt(0).scalar.equals(new ScalarValue(20))
                        && table.fetchAt(0).optionalScalar.equals(new ScalarValue(21)),
                "scalar-value pipeline update");
        UpdateResult cleared = table.update(row -> row.clearOptionalScalar());
        check(cleared.changed() == 1 && table.fetchAt(0).optionalScalar == null,
                "optional scalar-value pipeline clear");
        TableStats operationStats = table.statsSnapshot();
        check(operationStats.operationScratchCurrentBytes() > 0L
                        && operationStats.operationScratchHighWaterBytes()
                        >= operationStats.operationScratchCurrentBytes(),
                "row-operation scratch stats");
        table.mutateAt(0).clearNote().clearOptionalState().clearOptionalPoint()
                .setScalar(new ScalarValue(22)).clearOptionalScalar().commit();
        value = table.fetchAt(0);
        check(value.note == null && value.optionalState == null
                        && value.optionalPoint == null && value.optionalScalar == null
                        && value.scalar.equals(new ScalarValue(22)),
                "optional String/enum/value clear and scalar-value materialization");
        expectNullPointer(() -> table.fetchAt(0, (MaterializationBudget) null),
                "null materialization budget");
        table.release();
        expectCode("table_released", table::size, "released table access");
    }

    private static void verifyPresenceWordBoundaries() {
        FullRowBatch batch = new FullRowBatch(65);
        for (int index = 0; index < 65; index++) {
            final int row = index;
            batch.addValues(writer -> {
                writer.setName("row-" + row);
                writer.setPoint(new Point(row, -row));
                writer.setLeafDefaults(new LeafDefaults(row, "leaf-" + row));
                writer.setScalar(new ScalarValue(row));
                if (row == 0) writer.setOptionalState(State.READY);
                if (row == 63) writer.setOptionalState(State.RUNNING);
                if (row == 64) writer.setOptionalState(State.DONE);
            });
        }
        FullRowTable table = FullRowTable.create();
        table.addBatch(batch);
        final State[] visited = new State[3];
        final int[] count = {0};
        table.optionalStateValues().forEach(value -> visited[count[0]++] = value);
        check(count[0] == 3 && visited[0] == State.READY
                        && visited[1] == State.RUNNING && visited[2] == State.DONE,
                "optional enum word traversal at rows 0/63/64");

        EnumColumnView<State> view = table.optionalStateColumn();
        try {
            check(view.isPresent(0) && !view.isPresent(1)
                            && view.isPresent(63) && view.isPresent(64),
                    "optional enum presence at 0/64/65 boundary");
            check(view.get(0) == State.READY && view.get(63) == State.RUNNING
                            && view.get(64) == State.DONE,
                    "optional enum view values");
            expectCode("optional_absent", () -> view.get(1),
                    "optional enum absent get");
        } finally {
            view.close();
        }
        table.release();
    }

    private static void verifyStringKeyAndBudgets() {
        StringKeyRowTable keyed = StringKeyRowTable.create();
        keyed.reserve(64);
        keyed.addBatch(new StringKeyRowBatch()
                .addValues("Aa", 1, false, null)
                .addValues("BB", 2, true, "present"));
        check(keyed.fetch("Aa").value == 1 && keyed.fetch("BB").value == 2,
                "String hash collision full equality");
        check(keyed.fetch("Aa").note == null
                        && "present".equals(keyed.fetch("BB").note),
                "String optional payload");
        check(!keyed.find("missing").isPresent(), "String missing find");
        expectCode("missing_key", () -> keyed.fetch("missing"),
                "String missing fetch");
        expectCode("invalid_null_value", () -> keyed.containsKey(null),
                "null String key");

        MaterializationBudget normal = MaterializationBudget.defaults();
        check(keyed.find("Aa", normal).get().value == 1,
                "explicit row materialization budget");
        List<String> keys = keyed.keys().fetchAll(normal);
        check(keys.size() == 2 && "Aa".equals(keys.get(0)) && "BB".equals(keys.get(1)),
                "explicit key fetchAll budget");
        check("Aa".equals(keyed.keys().findFirst(normal).get())
                        && "Aa".equals(keyed.keys().firstOrThrow(normal)),
                "explicit key first budget overloads");
        MaterializationBudget oneRow = MaterializationBudget.builder()
                .maximumRows(1).build();
        expectCode("materialization_budget_exceeded",
                () -> keyed.keys().fetchAll(oneRow), "key budget enforcement");

        TableStats keyStats = keyed.statsSnapshot();
        check("hash-composite-v2".equals(keyStats.keySpaceImplementation())
                        && keyStats.keySpaceCapacity() > 0
                        && keyStats.keySpaceUsed() >= keyed.size()
                        && keyStats.keySpaceProbeCount() > 0L
                        && keyStats.keySpaceCollisionCount() > 0L,
                "String KeySpace capacity/probe/collision stats");
        int keyCapacity = keyStats.keySpaceCapacity();
        int keyUsed = keyStats.keySpaceUsed();
        keyed.resetStats();
        TableStats resetKeyStats = keyed.statsSnapshot();
        check(resetKeyStats.keySpaceCapacity() == keyCapacity
                        && resetKeyStats.keySpaceUsed() == keyUsed
                        && resetKeyStats.keySpaceProbeCount() == 0L
                        && resetKeyStats.keySpaceCollisionCount() == 0L
                        && resetKeyStats.keySpaceRehashCount() == 0L,
                "KeySpace metric reset preserves locator state");
        check(keyed.containsKey("BB"), "KeySpace probe before staged append");
        long probesBeforeAppend = keyed.statsSnapshot().keySpaceProbeCount();
        keyed.addBatch(new StringKeyRowBatch()
                .addValues("Cc", 3, false, null));
        check(keyed.statsSnapshot().keySpaceProbeCount() > probesBeforeAppend,
                "singleton validation preserves since-reset KeySpace metrics");

        int beforeDuplicate = keyed.size();
        expectCode("duplicate_key",
                () -> keyed.addBatch(new StringKeyRowBatch()
                        .addValues("Aa", 99, false, null)),
                "duplicate String key");
        check(keyed.size() == beforeDuplicate && keyed.fetch("Aa").value == 1,
                "duplicate key atomicity");
        expectCode("duplicate_key",
                () -> keyed.addBatch(new StringKeyRowBatch()
                        .addValues("within-batch", 10, false, null)
                        .addValues("within-batch", 11, false, null)),
                "batch-internal duplicate String key");
        check(keyed.size() == beforeDuplicate,
                "batch-internal duplicate String append is atomic");
        keyed.delete("Aa");
        check(!keyed.containsKey("Aa") && keyed.containsKey("BB")
                        && keyed.containsKey("Cc") && keyed.fetch("BB").value == 2,
                "String key compaction");
        keyed.release();
    }

    private static void verifyStringKeyedChild() {
        StringParent parent = new StringParent();
        parent.id = 1;
        parent.children = new HashMap<String, StringKeyRow>();
        StringKeyRow child = new StringKeyRow();
        child.id = "child";
        child.value = 11;
        child.note = null;
        parent.children.put("child", child);

        StringParentTable parents = StringParentTable.create();
        parents.addBatch(new StringParentBatch().add(parent));
        check(parents.children(0).fetch("child").value == 11,
                "String keyed child live access");
        Map<String, StringKeyRow> materialized = parents
                .materialize(MaterializationBudget.defaults()).get(0).children;
        check(materialized.size() == 1 && materialized.get("child").value == 11,
                "String keyed child materialization");
        parents.release();
    }

    private static void verifyFailedUpdateDoesNotRetainReferences() {
        FullRowBatch batch = new FullRowBatch();
        for (int index = 0; index < 2; index++) {
            final int value = index;
            batch.addValues(row -> {
                row.setName("original-" + value);
                row.setPoint(new Point(value, value + 1));
                row.setScalar(new ScalarValue(value));
            });
        }
        FullRowTable table = FullRowTable.create();
        table.addBatch(batch);
        final String sentinel = new String("failed-update-retained-reference-sentinel");
        expectCode("callback_failed", () -> table.rows().update(row -> {
            row.setName(sentinel);
            throw new IllegalStateException("intentional update failure");
        }), "failed update callback");
        check("original-0".equals(table.fetchAt(0).name)
                        && "original-1".equals(table.fetchAt(1).name),
                "failed update must not publish detached references");
        assertGeneratedTableDoesNotRetain(table, sentinel);
        table.release();
    }

    private static void assertGeneratedTableDoesNotRetain(Object table, Object sentinel) {
        try {
            for (Field field : table.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(table);
                if (value instanceof String[]) {
                    assertArrayDoesNotRetain((Object[]) value, sentinel, field.getName());
                } else if (value != null
                        && value.getClass().getName().equals(
                        "com.hgtech.soma.runtime.generated.ObjectColumn")) {
                    Field values = value.getClass().getDeclaredField("values");
                    values.setAccessible(true);
                    assertArrayDoesNotRetain((Object[]) values.get(value), sentinel,
                            field.getName() + ".values");
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("retained-reference oracle could not inspect storage", failure);
        }
    }

    private static void assertArrayDoesNotRetain(
            Object[] values, Object sentinel, String path) {
        for (Object value : values) {
            check(value != sentinel, "failed reference retained at " + path);
        }
    }

    private static void expectCode(String code, Action action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": expected " + code);
        } catch (SomaRuntimeException expected) {
            check(code.equals(expected.code()), message + ": actual=" + expected.code());
        }
    }

    private static void expectNullPointer(Action action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected public null-contract failure.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action {
        void run();
    }
}
