package io.github.somaruntime.soma.runtime;

import io.github.somaruntime.soma.runtime.generated.BooleanColumn;
import io.github.somaruntime.soma.runtime.generated.ByteColumn;
import io.github.somaruntime.soma.runtime.generated.DenseTableState;
import io.github.somaruntime.soma.runtime.generated.DoubleColumn;
import io.github.somaruntime.soma.runtime.generated.FloatColumn;
import io.github.somaruntime.soma.runtime.generated.IntColumn;
import io.github.somaruntime.soma.runtime.generated.LongColumn;
import io.github.somaruntime.soma.runtime.generated.PresenceBitmap;
import io.github.somaruntime.soma.runtime.generated.ShortColumn;

/**
 * 仅供 schema-specific generated facade 调用的 Java 8 column construction bridge。
 *
 * <p>Public signature故意不暴露generated-runtime binding；application只应使用
 * generated table的fieldValues/fieldColumn方法。</p>
 */
public final class GeneratedColumnAccess {
    private GeneratedColumnAccess() {
    }

    public static BooleanColumnTraversal booleanTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new BooleanColumnTraversal(state(state), column(column, BooleanColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static ByteColumnTraversal byteTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new ByteColumnTraversal(state(state), column(column, ByteColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static ShortColumnTraversal shortTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new ShortColumnTraversal(state(state), column(column, ShortColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static IntColumnTraversal intTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new IntColumnTraversal(state(state), column(column, IntColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static LongColumnTraversal longTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new LongColumnTraversal(state(state), column(column, LongColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static FloatColumnTraversal floatTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new FloatColumnTraversal(state(state), column(column, FloatColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static DoubleColumnTraversal doubleTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation) {
        return new DoubleColumnTraversal(state(state), column(column, DoubleColumn.class),
                presence(presence), table, operation, callbackOperation);
    }

    public static <E extends Enum<E>> EnumColumnTraversal<E> enumTraversal(
            Object state, Object column, Object presence, String table,
            String operation, String callbackOperation,
            E[] members) {
        return new EnumColumnTraversal<E>(state(state), column(column, IntColumn.class),
                presence(presence), table, operation, callbackOperation, requiredMembers(members));
    }

    public static BooleanColumnView booleanView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new BooleanColumnView(state(state), column(column, BooleanColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static ByteColumnView byteView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new ByteColumnView(state(state), column(column, ByteColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static ShortColumnView shortView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new ShortColumnView(state(state), column(column, ShortColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static IntColumnView intView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new IntColumnView(state(state), column(column, IntColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static LongColumnView longView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new LongColumnView(state(state), column(column, LongColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static FloatColumnView floatView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new FloatColumnView(state(state), column(column, FloatColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static DoubleColumnView doubleView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation) {
        return new DoubleColumnView(state(state), column(column, DoubleColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation, valueOperation);
    }

    public static <E extends Enum<E>> EnumColumnView<E> enumView(
            Object state, Object column, Object presence, String table, String field,
            String columnOperation, String presenceOperation, String valueOperation,
            E[] members) {
        return new EnumColumnView<E>(state(state), column(column, IntColumn.class),
                presence(presence), table, field, columnOperation, presenceOperation,
                valueOperation, requiredMembers(members));
    }

    private static DenseTableState state(Object value) {
        return column(value, DenseTableState.class);
    }

    private static PresenceBitmap presence(Object value) {
        return value == null ? null : column(value, PresenceBitmap.class);
    }

    private static <T> T column(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("invalid generated column binding: "
                    + type.getSimpleName());
        }
        return type.cast(value);
    }

    private static <E extends Enum<E>> E[] requiredMembers(E[] members) {
        if (members == null) {
            throw new NullPointerException("members");
        }
        return members;
    }
}
