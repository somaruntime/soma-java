package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot Java facade for a typed GroupBy operation. */
public final class GeneratedGrouping {

    public static final int KEY_BOOLEAN = 1;
    public static final int KEY_BYTE = 2;
    public static final int KEY_SHORT = 3;
    public static final int KEY_CHAR = 4;
    public static final int KEY_INT = 5;
    public static final int KEY_LONG = 6;
    public static final int KEY_REFERENCE = 7;

    public static final int VALUE_INT = 1;
    public static final int VALUE_LONG = 2;
    public static final int VALUE_DOUBLE = 3;
    public static final int VALUE_LONG_SUMMARY = 4;
    public static final int VALUE_DOUBLE_SUMMARY = 5;

    public static final int SUM = 1;
    public static final int MIN = 2;
    public static final int MAX = 3;
    public static final int AVERAGE = 4;
    public static final int SUMMARY = 5;

    private final LogicalRowPlan rows;
    private final int keyFieldIndex;
    private final int keyKind;
    private final GeneratedCallbacks.RowMapper<?> keyMaterializer;
    private final AtomicBoolean consumed = new AtomicBoolean();

    GeneratedGrouping(
            LogicalRowPlan rows,
            int keyFieldIndex,
            int keyKind,
            GeneratedCallbacks.RowMapper<?> keyMaterializer) {
        this.rows = rows;
        this.keyFieldIndex = keyFieldIndex;
        this.keyKind = keyKind;
        this.keyMaterializer = keyMaterializer;
    }

    public Object count() {
        claim();
        return CanonicalGroupingQueryOperation.execute(
                rows, keyFieldIndex, keyKind, keyMaterializer,
                VALUE_LONG, 0, -1, null, null, false);
    }

    Object countReferenceForTesting() {
        claim();
        return CanonicalGroupingQueryOperation.execute(
                rows, keyFieldIndex, keyKind, keyMaterializer,
                VALUE_LONG, 0, -1, null, null, true);
    }

    public Object aggregateLong(
            int aggregate,
            int valueKind,
            int fieldIndex,
            GeneratedCallbacks.RowToLongMapper mapper) {
        requireAggregate(aggregate, valueKind, fieldIndex, mapper);
        claim();
        return CanonicalGroupingQueryOperation.execute(
                rows, keyFieldIndex, keyKind, keyMaterializer,
                valueKind, aggregate, fieldIndex, mapper, null, false);
    }

    public Object aggregateDouble(
            int aggregate,
            int valueKind,
            int fieldIndex,
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        requireAggregate(aggregate, valueKind, fieldIndex, mapper);
        claim();
        return CanonicalGroupingQueryOperation.execute(
                rows, keyFieldIndex, keyKind, keyMaterializer,
                valueKind, aggregate, fieldIndex, null, mapper, false);
    }

    private static void requireAggregate(
            int aggregate,
            int valueKind,
            int fieldIndex,
            Object mapper) {
        if (aggregate < SUM || aggregate > SUMMARY
                || valueKind < VALUE_INT
                || valueKind > VALUE_DOUBLE_SUMMARY
                || fieldIndex < 0 || mapper == null) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "invalid GroupBy aggregate");
        }
    }

    private void claim() {
        if (!consumed.compareAndSet(false, true)) {
            throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                    SomaOperation.QUERY,
                    "GroupBy builder has already been consumed",
                    new Object());
        }
    }
}
