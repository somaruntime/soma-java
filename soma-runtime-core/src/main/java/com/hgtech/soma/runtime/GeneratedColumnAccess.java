package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.generated.BooleanColumn;
import com.hgtech.soma.runtime.generated.ByteColumn;
import com.hgtech.soma.runtime.generated.DenseTableState;
import com.hgtech.soma.runtime.generated.DoubleColumn;
import com.hgtech.soma.runtime.generated.FloatColumn;
import com.hgtech.soma.runtime.generated.IntColumn;
import com.hgtech.soma.runtime.generated.LongColumn;
import com.hgtech.soma.runtime.generated.PresenceBitmap;
import com.hgtech.soma.runtime.generated.ShortColumn;

/**
 * 仅供 schema-specific generated facade 调用的 Java 8 column construction bridge。
 *
 * <p>Public signature故意不暴露generated-runtime binding；application只应使用
 * generated table的fieldValues/fieldColumn方法。</p>
 */
public final class GeneratedColumnAccess {
    private GeneratedColumnAccess() {
    }

    public static BooleanColumnPipeline booleanPipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new BooleanColumnPipeline(state(state), column(column, BooleanColumn.class),
                presence(presence), table, field);
    }

    public static ByteColumnPipeline bytePipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new ByteColumnPipeline(state(state), column(column, ByteColumn.class),
                presence(presence), table, field);
    }

    public static ShortColumnPipeline shortPipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new ShortColumnPipeline(state(state), column(column, ShortColumn.class),
                presence(presence), table, field);
    }

    public static IntColumnPipeline intPipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new IntColumnPipeline(state(state), column(column, IntColumn.class),
                presence(presence), table, field);
    }

    public static LongColumnPipeline longPipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new LongColumnPipeline(state(state), column(column, LongColumn.class),
                presence(presence), table, field);
    }

    public static FloatColumnPipeline floatPipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new FloatColumnPipeline(state(state), column(column, FloatColumn.class),
                presence(presence), table, field);
    }

    public static DoubleColumnPipeline doublePipeline(
            Object state, Object column, Object presence, String table, String field) {
        return new DoubleColumnPipeline(state(state), column(column, DoubleColumn.class),
                presence(presence), table, field);
    }

    public static <E extends Enum<E>> EnumColumnPipeline<E> enumPipeline(
            Object state, Object column, Object presence, String table, String field,
            E[] members) {
        return new EnumColumnPipeline<E>(state(state), column(column, IntColumn.class),
                presence(presence), table, field, requiredMembers(members));
    }

    public static BooleanColumnView booleanView(
            Object state, Object column, Object presence, String table, String field) {
        return new BooleanColumnView(state(state), column(column, BooleanColumn.class),
                presence(presence), table, field);
    }

    public static ByteColumnView byteView(
            Object state, Object column, Object presence, String table, String field) {
        return new ByteColumnView(state(state), column(column, ByteColumn.class),
                presence(presence), table, field);
    }

    public static ShortColumnView shortView(
            Object state, Object column, Object presence, String table, String field) {
        return new ShortColumnView(state(state), column(column, ShortColumn.class),
                presence(presence), table, field);
    }

    public static IntColumnView intView(
            Object state, Object column, Object presence, String table, String field) {
        return new IntColumnView(state(state), column(column, IntColumn.class),
                presence(presence), table, field);
    }

    public static LongColumnView longView(
            Object state, Object column, Object presence, String table, String field) {
        return new LongColumnView(state(state), column(column, LongColumn.class),
                presence(presence), table, field);
    }

    public static FloatColumnView floatView(
            Object state, Object column, Object presence, String table, String field) {
        return new FloatColumnView(state(state), column(column, FloatColumn.class),
                presence(presence), table, field);
    }

    public static DoubleColumnView doubleView(
            Object state, Object column, Object presence, String table, String field) {
        return new DoubleColumnView(state(state), column(column, DoubleColumn.class),
                presence(presence), table, field);
    }

    public static <E extends Enum<E>> EnumColumnView<E> enumView(
            Object state, Object column, Object presence, String table, String field,
            E[] members) {
        return new EnumColumnView<E>(state(state), column(column, IntColumn.class),
                presence(presence), table, field, requiredMembers(members));
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
