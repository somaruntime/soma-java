package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Closed arbitrary-reference semantic family lowered from a Java pipeline capture. */
final class CanonicalMappedOperation {

    enum TerminalKind {
        SOURCE, COUNT, MATCH, FIND_FIRST, EXTREMUM,
        FOR_EACH, MATERIALIZE, EXPLAIN, TEST
    }

    final CanonicalRowOperation source;
    final HostCallbackHandle rootMapper;
    final List<CanonicalMappedStage> stages;
    final TerminalKind terminal;
    final HostCallbackHandle terminalCallback;
    final Class<?> arrayComponentType;

    CanonicalMappedOperation(
            CanonicalRowOperation source,
            HostCallbackHandle rootMapper,
            List<CanonicalMappedStage> stages,
            TerminalKind terminal,
            HostCallbackHandle terminalCallback,
            Class<?> arrayComponentType) {
        if (source == null || rootMapper == null
                || rootMapper.kind != HostCallbackHandle.Kind.ROW_MAPPER
                || stages == null || terminal == null) {
            throw new AssertionError("invalid Canonical mapped operation");
        }
        ArrayList<CanonicalMappedStage> copy =
                new ArrayList<CanonicalMappedStage>(stages.size());
        for (CanonicalMappedStage stage : stages) {
            if (stage == null
                    || !stage.belongsTo(source.tableIdentity)) {
                throw new AssertionError("Canonical mapped stage owner drift");
            }
            copy.add(stage);
        }
        if (!rootMapper.tableIdentity.sameTable(source.tableIdentity)
                || terminalCallback != null
                && !terminalCallback.tableIdentity.sameTable(
                        source.tableIdentity)) {
            throw new AssertionError("Canonical mapped callback owner drift");
        }
        this.source = source;
        this.rootMapper = rootMapper;
        this.stages = Collections.unmodifiableList(copy);
        this.terminal = terminal;
        this.terminalCallback = terminalCallback;
        this.arrayComponentType = arrayComponentType;
    }

    boolean hasOwnStatefulStage() {
        for (CanonicalMappedStage stage : stages) {
            if (stage.isStateful()) return true;
        }
        return false;
    }

    long outputUpperBound(BoundCanonicalRowOperation bound) {
        long result = bound.outputUpperBound();
        for (CanonicalMappedStage stage : stages) {
            if (stage.kind == CanonicalMappedStage.Kind.SKIP) {
                result = stage.count >= result ? 0L : result - stage.count;
            } else if (stage.kind == CanonicalMappedStage.Kind.LIMIT
                    && stage.count < result) {
                result = stage.count;
            }
        }
        return result;
    }
}

final class CanonicalMappedStage {
    enum Kind { FILTER, MAP, DISTINCT, SORTED, SKIP, LIMIT }

    final Kind kind;
    final HostCallbackHandle callback;
    final long count;

    CanonicalMappedStage(
            Kind kind,
            HostCallbackHandle callback,
            long count) {
        if (kind == null) throw new AssertionError("mapped stage kind is missing");
        this.kind = kind;
        this.callback = callback;
        this.count = count;
    }

    boolean belongsTo(CanonicalTableIdentity identity) {
        return callback == null || callback.tableIdentity.sameTable(identity);
    }

    boolean isStateful() {
        return kind == Kind.DISTINCT || kind == Kind.SORTED;
    }
}

final class CanonicalMappedLowering {
    private CanonicalMappedLowering() {
    }

    static CanonicalMappedOperation source(MappedPipelineCapture<?> frontend) {
        return operation(
                frontend,
                CanonicalMappedOperation.TerminalKind.SOURCE,
                null,
                null);
    }

    static CanonicalMappedOperation operation(
            MappedPipelineCapture<?> frontend,
            CanonicalMappedOperation.TerminalKind terminal,
            HostCallbackHandle terminalCallback,
            Class<?> arrayComponentType) {
        GeneratedTable table = frontend.rows.owner();
        CanonicalRowOperation source = CanonicalRowLowering.source(
                table, frontend.rows);
        if (source == null) {
            throw new AssertionError("ordinary mapped source did not lower");
        }
        CanonicalTableIdentity identity = source.tableIdentity;
        ArrayList<CanonicalMappedStage> stages =
                new ArrayList<CanonicalMappedStage>(frontend.stages.size());
        for (MappedPipelineCapture.Stage stage : frontend.stages) {
            HostCallbackHandle callback = null;
            switch (stage.kind) {
                case FILTER:
                    callback = HostCallbackHandle.host(
                            identity,
                            HostCallbackHandle.Kind.MAPPED_PREDICATE,
                            stage.predicate);
                    break;
                case MAP:
                    callback = HostCallbackHandle.host(
                            identity,
                            HostCallbackHandle.Kind.MAPPED_MAPPER,
                            stage.mapper);
                    break;
                case SORTED:
                    callback = HostCallbackHandle.host(
                            identity,
                            HostCallbackHandle.Kind.MAPPED_COMPARATOR,
                            stage.comparator);
                    break;
                default:
                    break;
            }
            stages.add(new CanonicalMappedStage(
                    CanonicalMappedStage.Kind.valueOf(stage.kind.name()),
                    callback,
                    stage.count));
        }
        return new CanonicalMappedOperation(
                source,
                HostCallbackHandle.rowMapper(identity, frontend.rootMapper),
                stages,
                terminal,
                terminalCallback,
                arrayComponentType);
    }
}

/** Closed unboxed primitive semantic family. Physical values remain long/bits. */
final class CanonicalPrimitiveOperation {

    enum RootKind { ROW, MAPPED }
    enum TerminalKind {
        COUNT, MATCH, FIND_FIRST, EXTREMUM, SUM, AVERAGE,
        SUMMARY, MATERIALIZE, FOR_EACH, EXPLAIN, TEST
    }

    final CanonicalRowOperation source;
    final RootKind rootKind;
    final CanonicalMappedOperation mapped;
    final HostCallbackHandle rootMapper;
    final boolean rootApplicationCallback;
    final int rootFieldIndex;
    final PrimitiveValueKind rootValueKind;
    final PrimitiveValueKind valueKind;
    final List<CanonicalPrimitiveStage> stages;
    final TerminalKind terminal;
    final HostCallbackHandle terminalCallback;

    CanonicalPrimitiveOperation(
            CanonicalRowOperation source,
            RootKind rootKind,
            CanonicalMappedOperation mapped,
            HostCallbackHandle rootMapper,
            boolean rootApplicationCallback,
            int rootFieldIndex,
            PrimitiveValueKind rootValueKind,
            PrimitiveValueKind valueKind,
            List<CanonicalPrimitiveStage> stages,
            TerminalKind terminal,
            HostCallbackHandle terminalCallback) {
        if (source == null || rootKind == null || rootMapper == null
                || rootValueKind == null || valueKind == null
                || stages == null || terminal == null
                || rootKind == RootKind.ROW && mapped != null
                || rootKind == RootKind.MAPPED && mapped == null) {
            throw new AssertionError("invalid Canonical primitive operation");
        }
        this.source = source;
        this.rootKind = rootKind;
        this.mapped = mapped;
        this.rootMapper = rootMapper;
        this.rootApplicationCallback = rootApplicationCallback;
        this.rootFieldIndex = rootFieldIndex;
        this.rootValueKind = rootValueKind;
        this.valueKind = valueKind;
        this.stages = Collections.unmodifiableList(
                new ArrayList<CanonicalPrimitiveStage>(stages));
        this.terminal = terminal;
        this.terminalCallback = terminalCallback;
    }

    boolean hasOwnStatefulStage() {
        for (CanonicalPrimitiveStage stage : stages) {
            if (stage.isStateful()) return true;
        }
        return false;
    }

    long outputUpperBound(BoundCanonicalRowOperation bound) {
        long result = mapped == null
                ? bound.outputUpperBound()
                : mapped.outputUpperBound(bound);
        for (CanonicalPrimitiveStage stage : stages) {
            if (stage.kind == CanonicalPrimitiveStage.Kind.SKIP) {
                result = stage.count >= result ? 0L : result - stage.count;
            } else if (stage.kind == CanonicalPrimitiveStage.Kind.LIMIT
                    && stage.count < result) {
                result = stage.count;
            }
        }
        return result;
    }
}

final class CanonicalPrimitiveStage {
    enum Kind { FILTER, MAP, CONVERT, DISTINCT, SORTED, SKIP, LIMIT }

    final Kind kind;
    final PrimitiveValueKind input;
    final PrimitiveValueKind output;
    final HostCallbackHandle callback;
    final long count;

    CanonicalPrimitiveStage(
            Kind kind,
            PrimitiveValueKind input,
            PrimitiveValueKind output,
            HostCallbackHandle callback,
            long count) {
        this.kind = kind;
        this.input = input;
        this.output = output;
        this.callback = callback;
        this.count = count;
    }

    boolean isStateful() {
        return kind == Kind.DISTINCT || kind == Kind.SORTED;
    }
}

final class CanonicalPrimitiveLowering {
    private CanonicalPrimitiveLowering() {
    }

    static CanonicalPrimitiveOperation operation(
            PrimitivePipelineCapture frontend,
            CanonicalPrimitiveOperation.TerminalKind terminal,
            HostCallbackHandle terminalCallback) {
        GeneratedTable table = frontend.rows.owner();
        CanonicalRowOperation source = CanonicalRowLowering.source(
                table, frontend.rows);
        CanonicalTableIdentity identity = source.tableIdentity;
        CanonicalMappedOperation mapped = frontend.mapped == null
                ? null : CanonicalMappedLowering.source(frontend.mapped);
        ArrayList<CanonicalPrimitiveStage> stages =
                new ArrayList<CanonicalPrimitiveStage>(frontend.stages.size());
        for (PrimitivePipelineCapture.Stage stage : frontend.stages) {
            HostCallbackHandle callback = stage.callback == null ? null
                    : HostCallbackHandle.host(
                            identity,
                            stage.kind == PrimitivePipelineCapture.StageKind.FILTER
                                    ? HostCallbackHandle.Kind.PRIMITIVE_PREDICATE
                                    : HostCallbackHandle.Kind.PRIMITIVE_MAPPER,
                            stage.callback);
            stages.add(new CanonicalPrimitiveStage(
                    CanonicalPrimitiveStage.Kind.valueOf(stage.kind.name()),
                    stage.input,
                    stage.output,
                    callback,
                    stage.count));
        }
        return new CanonicalPrimitiveOperation(
                source,
                frontend.rootKind == PrimitivePipelineCapture.RootKind.ROW
                        ? CanonicalPrimitiveOperation.RootKind.ROW
                        : CanonicalPrimitiveOperation.RootKind.MAPPED,
                mapped,
                HostCallbackHandle.host(
                        identity,
                        HostCallbackHandle.Kind.PRIMITIVE_ROOT,
                        frontend.rootMapper),
                frontend.rootApplicationCallback,
                frontend.rootFieldIndex,
                frontend.rootValueKind,
                frontend.valueKind,
                stages,
                terminal,
                terminalCallback);
    }
}
