package com.hgtech.soma.runtime.generated;

/** 严格 floating key 的 finite 校验与 canonical bit binding。 */
public final class KeyCanonicalization {
    private KeyCanonicalization() {
    }

    public static int strictFloatKeyBits(
            String table, String field, float value, String operation) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw RuntimeFailures.invalidFloatingAccessValue(
                    table, field, "float", operation);
        }
        return value == 0.0f ? 0 : Float.floatToIntBits(value);
    }

    public static long strictDoubleKeyBits(
            String table, String field, double value, String operation) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw RuntimeFailures.invalidFloatingAccessValue(
                    table, field, "double", operation);
        }
        return value == 0.0d ? 0L : Double.doubleToLongBits(value);
    }

    public static float strictFloatStorage(
            String table, String field, float value, String operation) {
        strictFloatKeyBits(table, field, value, operation);
        return value == 0.0f ? 0.0f : value;
    }

    public static double strictDoubleStorage(
            String table, String field, double value, String operation) {
        strictDoubleKeyBits(table, field, value, operation);
        return value == 0.0d ? 0.0d : value;
    }
}
