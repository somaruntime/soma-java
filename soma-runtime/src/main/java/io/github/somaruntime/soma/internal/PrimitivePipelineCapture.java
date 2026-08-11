package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Java facade capture; terminal lowering creates CanonicalPrimitiveOperation. */
final class PrimitivePipelineCapture {

    enum RootKind { ROW, MAPPED }
    enum StageKind { FILTER, MAP, CONVERT, DISTINCT, SORTED, SKIP, LIMIT }

    static final class Stage {
        final StageKind kind;
        final PrimitiveValueKind input;
        final PrimitiveValueKind output;
        final Object callback;
        final long count;
        Stage(StageKind kind, PrimitiveValueKind input, PrimitiveValueKind output, Object callback, long count) {
            this.kind = kind; this.input = input; this.output = output;
            this.callback = callback; this.count = count;
        }
    }

    final LogicalRowPlan rows;
    final RootKind rootKind;
    final MappedPipelineCapture<?> mapped;
    final Object rootMapper;
    final boolean rootApplicationCallback;
    final int rootFieldIndex;
    final PrimitiveValueKind rootValueKind;
    final PrimitiveValueKind valueKind;
    final List<Stage> stages;

    private PrimitivePipelineCapture(
            LogicalRowPlan rows, RootKind rootKind, MappedPipelineCapture<?> mapped,
            Object rootMapper, boolean rootApplicationCallback,
            int rootFieldIndex,
            PrimitiveValueKind rootValueKind, PrimitiveValueKind valueKind,
            List<Stage> stages) {
        this.rows = rows; this.rootKind = rootKind; this.mapped = mapped;
        this.rootMapper = rootMapper; this.rootValueKind = rootValueKind;
        this.rootApplicationCallback = rootApplicationCallback;
        this.rootFieldIndex = rootFieldIndex;
        this.valueKind = valueKind; this.stages = stages;
    }

    static PrimitivePipelineCapture row(
            LogicalRowPlan rows,
            PrimitiveValueKind kind,
            Object mapper,
            boolean applicationCallback) {
        return row(rows, kind, mapper, applicationCallback, -1);
    }

    static PrimitivePipelineCapture row(
            LogicalRowPlan rows,
            PrimitiveValueKind kind,
            Object mapper,
            boolean applicationCallback,
            int fieldIndex) {
        return new PrimitivePipelineCapture(rows, RootKind.ROW, null, mapper,
                applicationCallback, fieldIndex, kind, kind,
                Collections.<Stage>emptyList());
    }

    static PrimitivePipelineCapture mapped(MappedPipelineCapture<?> mapped, PrimitiveValueKind kind, Object mapper) {
        return new PrimitivePipelineCapture(mapped.rows, RootKind.MAPPED, mapped, mapper,
                true, -1, kind, kind,
                Collections.<Stage>emptyList());
    }

    PrimitivePipelineCapture filter(Object callback) {
        return append(new Stage(StageKind.FILTER, valueKind, valueKind, callback, 0L));
    }
    PrimitivePipelineCapture map(Object callback) {
        return append(new Stage(StageKind.MAP, valueKind, valueKind, callback, 0L));
    }
    PrimitivePipelineCapture convert(PrimitiveValueKind target, Object callback) {
        return append(new Stage(StageKind.CONVERT, valueKind, target, callback, 0L));
    }
    PrimitivePipelineCapture distinct() {
        return append(new Stage(StageKind.DISTINCT, valueKind, valueKind, null, 0L));
    }
    PrimitivePipelineCapture sorted() {
        return append(new Stage(StageKind.SORTED, valueKind, valueKind, null, 0L));
    }
    PrimitivePipelineCapture skip(long count) {
        return append(new Stage(StageKind.SKIP, valueKind, valueKind, null, count));
    }
    PrimitivePipelineCapture limit(long count) {
        return append(new Stage(StageKind.LIMIT, valueKind, valueKind, null, count));
    }
    PrimitivePipelineCapture parallel() {
        if (rows.isParallel()) return this;
        MappedPipelineCapture<?> nextMapped = mapped == null ? null : mapped.parallel();
        return new PrimitivePipelineCapture(
                rows.parallel(), rootKind, nextMapped, rootMapper,
                rootApplicationCallback, rootFieldIndex,
                rootValueKind, valueKind, stages);
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
    private PrimitivePipelineCapture append(Stage stage) {
        ArrayList<Stage> next = new ArrayList<Stage>(stages.size() + 1);
        next.addAll(stages); next.add(stage);
        return new PrimitivePipelineCapture(rows, rootKind, mapped, rootMapper,
                rootApplicationCallback, rootFieldIndex, rootValueKind,
                stage.output, Collections.unmodifiableList(next));
    }
}

/** Closed primitive semantic kind shared by lowering and specialized kernels. */
enum PrimitiveValueKind { BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE }
