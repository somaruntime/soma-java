package com.example.soma.compositekeyed;

import com.example.soma.compositekeyed.generated.OperationStateBatch;
import com.example.soma.compositekeyed.generated.OperationStateTable;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.List;

public final class CompositeValueKeyedConsumer {
    private CompositeValueKeyedConsumer() {
    }

    public static void main(String[] args) {
        final OperationKey first = key(7000000001L, 11, -0.0d, "north", KeyKind.READY, -0.0f);
        final OperationKey second = key(-29L, 22, 2.5d, "south", KeyKind.RUNNING, 1.5f);
        final OperationKey movedByCompaction = key(41L, -7, 7.25d, "west", KeyKind.READY, 3.25f);
        final OperationKey collisionA = key(99L, 3, 4.0d, "Aa", KeyKind.READY, 2.0f);
        final OperationKey collisionB = key(99L, 3, 4.0d, "BB", KeyKind.READY, 2.0f);

        OperationStateBatch batch = new OperationStateBatch();
        batch.addValues(first, 100)
                .addValues(second, 200)
                .addValues(movedByCompaction, 300)
                .addValues(collisionA, 400)
                .addValues(collisionB, 500);

        final OperationStateTable table = OperationStateTable.create();
        table.addBatch(batch);

        OperationKey canonicalFirst = key(7000000001L, 11, 0.0d, "north", KeyKind.READY, 0.0f);
        require(table.containsKey(canonicalFirst), "composite contains");
        require(table.fetch(canonicalFirst).payload == 100, "composite fetch");
        require(!table.containsKey(key(7000000001L, 12, 0.0d, "north", KeyKind.READY, 0.0f)),
                "all composite leaves participate in equality");
        require(!table.containsKey(key(7000000001L, 11, 0.0d, "other", KeyKind.READY, 0.0f)),
                "string leaf participates in equality");
        require(!table.containsKey(key(7000000001L, 11, 0.0d, "north", KeyKind.RUNNING, 0.0f)),
                "enum leaf participates in equality");

        List<OperationKey> exported = table.keys().fetchAll();
        require(exported.size() == 5, "composite key export size");
        require(exported.get(0).equals(canonicalFirst), "composite key export value equality");
        require(Float.floatToIntBits(exported.get(0).lane) == Float.floatToIntBits(0.0f),
                "strict float leaf export canonicalization");
        require(Double.doubleToLongBits(exported.get(0).coordinate.position)
                == Double.doubleToLongBits(0.0d), "nested strict double export canonicalization");
        require("north".equals(exported.get(0).scope), "string key export");
        require(exported.get(1).equals(second), "composite key export order");
        require("Aa".hashCode() == "BB".hashCode(), "fixture requires a String hash collision");
        require(table.fetch(collisionA).payload == 400, "full equality collision A");
        require(table.fetch(collisionB).payload == 500, "full equality collision B");

        expectCode("duplicate_key", new Action() {
            @Override
            public void run() {
                table.addBatch(new OperationStateBatch()
                        .addValues(key(7000000001L, 11, 0.0d, "north", KeyKind.READY, 0.0f), 999));
            }
        });
        expectCode("missing_key", new Action() {
            @Override
            public void run() {
                table.fetch(key(7000000001L, 99, 0.0d, "north", KeyKind.READY, 0.0f));
            }
        });
        expectCode("invalid_floating_access_value", new Action() {
            @Override
            public void run() {
                new OperationStateBatch().addValues(
                        key(1L, 1, 1.0d, "invalid", KeyKind.READY, Float.NaN), 1);
            }
        });
        expectCode("invalid_floating_access_value", new Action() {
            @Override
            public void run() {
                table.containsKey(
                        key(1L, 1, 1.0d, "invalid", KeyKind.READY, Float.POSITIVE_INFINITY));
            }
        });

        table.delete(canonicalFirst);
        require(!table.containsKey(first), "composite delete");
        require(table.fetch(movedByCompaction).payload == 300, "composite compaction repair");
        require(table.fetch(second).payload == 200, "composite retained row after compaction");
        require(table.fetch(collisionA).payload == 400, "collision row survives compaction");
        require(table.fetch(collisionB).payload == 500, "collision row move repair");
    }

    private static OperationKey key(
            long machineId, int sequence, double position, String scope,
            KeyKind kind, float lane) {
        return new OperationKey(
                machineId, new Coordinate(sequence, position), scope, kind, lane);
    }

    private static void expectCode(String expected, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + expected);
        } catch (SomaRuntimeException failure) {
            require(expected.equals(failure.code()), failure.code());
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
