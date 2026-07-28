package com.example.soma.floatingvalue;

import com.example.soma.floatingvalue.generated.FloatingRowBatch;
import com.example.soma.floatingvalue.generated.FloatingRowTable;
import io.github.somaruntime.soma.runtime.DoubleColumnView;
import io.github.somaruntime.soma.runtime.FloatColumnView;

public final class FloatingValueConsumer {
    private FloatingValueConsumer() {
    }

    public static void main(String[] args) {
        FloatingRowTable table = FloatingRowTable.create();
        table.addBatch(new FloatingRowBatch().addValues(
                new FloatingPayload(Float.NaN, Double.NaN)));
        assertPayload(table, Float.NaN, Double.NaN, "batch/fetch NaN");
        assertColumns(table, Float.NaN, Double.NaN, "batch/column NaN");

        table.mutateAt(0).setPayload(new FloatingPayload(
                Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)).commit();
        assertPayload(table, Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                "mutator whole Value infinities");

        table.update(row -> {
            row.setPayloadSingleValue(Float.NEGATIVE_INFINITY);
            row.setPayloadWideValue(Double.POSITIVE_INFINITY);
        });
        assertPayload(table, Float.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                "rows.update Value leaves");

        table.update(row -> {
            row.setPayloadSingleValue(-0.0f);
            row.setPayloadWideValue(-0.0d);
        });
        FloatingPayload payload = table.fetchAt(0).payload;
        require(Float.floatToRawIntBits(payload.single)
                        == Float.floatToRawIntBits(-0.0f),
                "fetch must preserve float negative zero");
        require(Double.doubleToRawLongBits(payload.wide)
                        == Double.doubleToRawLongBits(-0.0d),
                "fetch must preserve double negative zero");
        assertColumns(table, -0.0f, -0.0d, "column negative zero");
        System.out.println("floating-value-storage-consumer: ok");
    }

    private static void assertPayload(
            FloatingRowTable table, float expectedFloat, double expectedDouble, String stage) {
        FloatingPayload value = table.fetchAt(0).payload;
        require(same(expectedFloat, value.single) && same(expectedDouble, value.wide), stage);
    }

    private static void assertColumns(
            FloatingRowTable table, float expectedFloat, double expectedDouble, String stage) {
        try (FloatColumnView single = table.payloadSingleValueColumn();
             DoubleColumnView wide = table.payloadWideValueColumn()) {
            require(same(expectedFloat, single.getFloat(0))
                            && same(expectedDouble, wide.getDouble(0)), stage);
        }
    }

    private static boolean same(float expected, float actual) {
        return Float.isNaN(expected) ? Float.isNaN(actual)
                : Float.floatToRawIntBits(expected) == Float.floatToRawIntBits(actual);
    }

    private static boolean same(double expected, double actual) {
        return Double.isNaN(expected) ? Double.isNaN(actual)
                : Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
