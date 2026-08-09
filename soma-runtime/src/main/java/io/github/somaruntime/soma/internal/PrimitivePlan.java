package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable data-only primitive mapper plan using unboxed long/bits transport. */
final class PrimitivePlan {

    enum ValueKind { BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE }
    enum RootKind { ROW, MAPPED }
    enum StageKind { FILTER, MAP, CONVERT, DISTINCT, SORTED, SKIP, LIMIT }

    static final class Stage {
        final StageKind kind;
        final ValueKind input;
        final ValueKind output;
        final Object callback;
        final long count;
        Stage(StageKind kind, ValueKind input, ValueKind output, Object callback, long count) {
            this.kind = kind; this.input = input; this.output = output;
            this.callback = callback; this.count = count;
        }
    }

    final LogicalRowPlan rows;
    final RootKind rootKind;
    final MappedPlan<?> mapped;
    final Object rootMapper;
    final boolean rootApplicationCallback;
    final ValueKind rootValueKind;
    final ValueKind valueKind;
    final List<Stage> stages;

    private PrimitivePlan(
            LogicalRowPlan rows, RootKind rootKind, MappedPlan<?> mapped,
            Object rootMapper, boolean rootApplicationCallback,
            ValueKind rootValueKind, ValueKind valueKind,
            List<Stage> stages) {
        this.rows = rows; this.rootKind = rootKind; this.mapped = mapped;
        this.rootMapper = rootMapper; this.rootValueKind = rootValueKind;
        this.rootApplicationCallback = rootApplicationCallback;
        this.valueKind = valueKind; this.stages = stages;
    }

    static PrimitivePlan row(
            LogicalRowPlan rows,
            ValueKind kind,
            Object mapper,
            boolean applicationCallback) {
        return new PrimitivePlan(rows, RootKind.ROW, null, mapper,
                applicationCallback, kind, kind,
                Collections.<Stage>emptyList());
    }

    static PrimitivePlan mapped(MappedPlan<?> mapped, ValueKind kind, Object mapper) {
        return new PrimitivePlan(mapped.rows, RootKind.MAPPED, mapped, mapper,
                true, kind, kind,
                Collections.<Stage>emptyList());
    }

    PrimitivePlan filter(Object callback) {
        return append(new Stage(StageKind.FILTER, valueKind, valueKind, callback, 0L));
    }
    PrimitivePlan map(Object callback) {
        return append(new Stage(StageKind.MAP, valueKind, valueKind, callback, 0L));
    }
    PrimitivePlan convert(ValueKind target, Object callback) {
        return append(new Stage(StageKind.CONVERT, valueKind, target, callback, 0L));
    }
    PrimitivePlan distinct() {
        return append(new Stage(StageKind.DISTINCT, valueKind, valueKind, null, 0L));
    }
    PrimitivePlan sorted() {
        return append(new Stage(StageKind.SORTED, valueKind, valueKind, null, 0L));
    }
    PrimitivePlan skip(long count) {
        return append(new Stage(StageKind.SKIP, valueKind, valueKind, null, count));
    }
    PrimitivePlan limit(long count) {
        return append(new Stage(StageKind.LIMIT, valueKind, valueKind, null, count));
    }
    boolean hasStatefulStage() {
        if (rows.hasStatefulStage() || mapped != null && mapped.hasStatefulStage()) return true;
        return hasOwnStatefulStage();
    }

    boolean hasOwnStatefulStage() {
        for (Stage stage : stages) {
            if (stage.kind == StageKind.DISTINCT || stage.kind == StageKind.SORTED) return true;
        }
        return false;
    }

    long outputUpperBound(long sourceUpperBound) {
        long result = rootKind == RootKind.MAPPED
                ? mapped.outputUpperBound(sourceUpperBound)
                : rows.outputUpperBound(sourceUpperBound);
        for (Stage stage : stages) {
            if (stage.kind == StageKind.SKIP) {
                result = stage.count >= result ? 0L : result - stage.count;
            } else if (stage.kind == StageKind.LIMIT && stage.count < result) {
                result = stage.count;
            }
        }
        return result;
    }
    private PrimitivePlan append(Stage stage) {
        ArrayList<Stage> next = new ArrayList<Stage>(stages.size() + 1);
        next.addAll(stages); next.add(stage);
        return new PrimitivePlan(rows, rootKind, mapped, rootMapper,
                rootApplicationCallback, rootValueKind,
                stage.output, Collections.unmodifiableList(next));
    }
}
