package io.github.somaruntime.soma.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, data-only Canonical operation for ordinary Row/Field lineages. */
final class CanonicalRowOperation {

    enum SourceKind { TABLE, INDEX_SELECTION }
    enum TerminalKind {
        COUNT,
        ANY_MATCH,
        ALL_MATCH,
        NONE_MATCH,
        FIND_FIRST,
        FOR_EACH,
        TO_LIST,
        TO_ARRAY,
        EXPLAIN,
        UPDATE,
        REMOVE,
        LOCATORS_TEST
    }

    final CanonicalTableIdentity tableIdentity;
    final SourceKind sourceKind;
    final int indexOrdinal;
    final TypedLiteral sourceLiteral;
    final List<CanonicalRowStage> stages;
    final List<PredicateIr> filters;
    final TerminalKind terminal;
    final HostCallbackHandle terminalCallback;
    final ExecutionRequest request;

    CanonicalRowOperation(
            CanonicalTableIdentity tableIdentity,
            SourceKind sourceKind,
            int indexOrdinal,
            TypedLiteral sourceLiteral,
            List<CanonicalRowStage> stages,
            TerminalKind terminal,
            HostCallbackHandle terminalCallback,
            ExecutionRequest request) {
        if (tableIdentity == null || sourceKind == null || stages == null
                || terminal == null || request == null) {
            throw new AssertionError("invalid canonical Row operation");
        }
        if (sourceKind == SourceKind.TABLE) {
            if (indexOrdinal != -1 || sourceLiteral != null) {
                throw new AssertionError("Table source contains physical lookup state");
            }
        } else if (indexOrdinal < 0
                || sourceLiteral == null
                || !sourceLiteral.tableIdentity().sameTable(tableIdentity)
                || tableIdentity.descriptor().indexFieldIndex(indexOrdinal)
                        != sourceLiteral.fieldIndex()) {
            throw new AssertionError("invalid canonical Index source");
        }
        ArrayList<CanonicalRowStage> stageCopy =
                new ArrayList<CanonicalRowStage>(stages.size());
        ArrayList<PredicateIr> predicateCopy = new ArrayList<PredicateIr>();
        for (CanonicalRowStage stage : stages) {
            if (stage == null || !stage.belongsTo(tableIdentity)) {
                throw new AssertionError("canonical Row stage owner drift");
            }
            stageCopy.add(stage);
            if (stage.kind == CanonicalRowStage.Kind.TYPED_FILTER) {
                predicateCopy.add(stage.predicate);
            }
        }
        if (terminalCallback != null
                && !terminalCallback.supports(terminal)) {
            throw new AssertionError("terminal callback kind drift");
        }
        this.tableIdentity = tableIdentity;
        this.sourceKind = sourceKind;
        this.indexOrdinal = indexOrdinal;
        this.sourceLiteral = sourceLiteral;
        this.stages = Collections.unmodifiableList(stageCopy);
        this.filters = Collections.unmodifiableList(predicateCopy);
        this.terminal = terminal;
        this.terminalCallback = terminalCallback;
        this.request = request;
    }

    long inLiteralCount() {
        long result = 0L;
        for (PredicateIr filter : filters) {
            result = Math.addExact(result, filter.inLiteralCount());
        }
        return result;
    }

    boolean hasStatefulStage() {
        for (CanonicalRowStage stage : stages) {
            if (stage.isStateful()) return true;
        }
        return false;
    }

    boolean beginsWithTypedFilter() {
        return !stages.isEmpty()
                && stages.get(0).kind == CanonicalRowStage.Kind.TYPED_FILTER;
    }
}

/** Closed Row stage family. Physical specialization is deliberately not stored here. */
final class CanonicalRowStage {

    enum Kind {
        TYPED_FILTER,
        CALLBACK_FILTER,
        TYPED_ORDER,
        CALLBACK_ORDER,
        DISTINCT_FIELD,
        FIELD_PROJECT,
        SKIP,
        LIMIT
    }

    final Kind kind;
    final PredicateIr predicate;
    final HostCallbackHandle callback;
    final CanonicalOrder order;
    final int fieldIndex;
    final long count;

    private CanonicalRowStage(
            Kind kind,
            PredicateIr predicate,
            HostCallbackHandle callback,
            CanonicalOrder order,
            int fieldIndex,
            long count) {
        this.kind = kind;
        this.predicate = predicate;
        this.callback = callback;
        this.order = order;
        this.fieldIndex = fieldIndex;
        this.count = count;
    }

    static CanonicalRowStage typedFilter(PredicateIr predicate) {
        return new CanonicalRowStage(
                Kind.TYPED_FILTER, predicate, null, null, -1, 0L);
    }

    static CanonicalRowStage callbackFilter(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowPredicate callback) {
        return new CanonicalRowStage(
                Kind.CALLBACK_FILTER,
                null,
                HostCallbackHandle.rowPredicate(identity, callback),
                null,
                -1,
                0L);
    }

    static CanonicalRowStage typedOrder(CanonicalOrder order) {
        return new CanonicalRowStage(
                Kind.TYPED_ORDER, null, null, order, -1, 0L);
    }

    static CanonicalRowStage callbackOrder(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowComparator comparator) {
        return new CanonicalRowStage(
                Kind.CALLBACK_ORDER,
                null,
                HostCallbackHandle.rowComparator(identity, comparator),
                null,
                -1,
                0L);
    }

    static CanonicalRowStage field(Kind kind, int fieldIndex) {
        return new CanonicalRowStage(
                kind, null, null, null, fieldIndex, 0L);
    }

    static CanonicalRowStage slice(Kind kind, long count) {
        return new CanonicalRowStage(kind, null, null, null, -1, count);
    }

    boolean belongsTo(CanonicalTableIdentity identity) {
        if (predicate != null) return predicate.tableIdentity.sameTable(identity);
        if (callback != null) return callback.tableIdentity.sameTable(identity);
        if (order != null) return order.tableIdentity.sameTable(identity);
        return fieldIndex < 0
                || fieldIndex < identity.descriptor().fieldCount();
    }

    boolean isStateful() {
        return kind == Kind.TYPED_ORDER
                || kind == Kind.CALLBACK_ORDER
                || kind == Kind.DISTINCT_FIELD;
    }
}

/** Compact logical Field order detached from the generated order carrier. */
final class CanonicalOrder {
    final CanonicalTableIdentity tableIdentity;
    private final int[] fields;
    private final boolean[] descending;

    CanonicalOrder(
            CanonicalTableIdentity tableIdentity,
            GeneratedOrder<?> source) {
        if (tableIdentity == null || source == null) {
            throw new AssertionError("canonical order is missing");
        }
        this.tableIdentity = tableIdentity;
        this.fields = new int[source.size()];
        this.descending = new boolean[source.size()];
        for (int index = 0; index < source.size(); index++) {
            int field = source.fieldIndex(index);
            tableIdentity.descriptor().fieldStart(field);
            fields[index] = field;
            descending[index] = source.descending(index);
        }
    }

    int size() { return fields.length; }
    int fieldIndex(int ordinal) { return fields[ordinal]; }
    boolean descending(int ordinal) { return descending[ordinal]; }
}

/** Minimal opaque Java callback handle; properties are derived from the closed kind. */
final class HostCallbackHandle {
    enum Kind { ROW_PREDICATE, ROW_COMPARATOR, ROW_ACTION, ROW_MAPPER, EDITOR_ACTION }

    final CanonicalTableIdentity tableIdentity;
    final Kind kind;
    final Object callback;

    private HostCallbackHandle(
            CanonicalTableIdentity tableIdentity,
            Kind kind,
            Object callback) {
        if (tableIdentity == null || kind == null || callback == null) {
            throw new AssertionError("invalid host callback handle");
        }
        this.tableIdentity = tableIdentity;
        this.kind = kind;
        this.callback = callback;
    }

    static HostCallbackHandle rowPredicate(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowPredicate callback) {
        return new HostCallbackHandle(identity, Kind.ROW_PREDICATE, callback);
    }

    static HostCallbackHandle rowComparator(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowComparator callback) {
        return new HostCallbackHandle(identity, Kind.ROW_COMPARATOR, callback);
    }

    static HostCallbackHandle rowAction(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowAction callback) {
        return new HostCallbackHandle(identity, Kind.ROW_ACTION, callback);
    }

    static HostCallbackHandle rowMapper(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.RowMapper<?> callback) {
        return new HostCallbackHandle(identity, Kind.ROW_MAPPER, callback);
    }

    static HostCallbackHandle editorAction(
            CanonicalTableIdentity identity,
            GeneratedCallbacks.EditorAction callback) {
        return new HostCallbackHandle(identity, Kind.EDITOR_ACTION, callback);
    }

    boolean supports(CanonicalRowOperation.TerminalKind terminal) {
        switch (terminal) {
            case ANY_MATCH:
            case ALL_MATCH:
            case NONE_MATCH:
                return kind == Kind.ROW_PREDICATE;
            case FIND_FIRST:
            case TO_LIST:
            case TO_ARRAY:
                return kind == Kind.ROW_MAPPER;
            case FOR_EACH:
                return kind == Kind.ROW_ACTION;
            case UPDATE:
                return kind == Kind.EDITOR_ACTION;
            default:
                return false;
        }
    }
}

/** Execution mode is operation input, never a semantic pipeline stage. */
final class ExecutionRequest {
    enum Mode { SEQUENTIAL, PARALLEL }
    static final ExecutionRequest SEQUENTIAL = new ExecutionRequest(Mode.SEQUENTIAL);
    static final ExecutionRequest PARALLEL = new ExecutionRequest(Mode.PARALLEL);

    final Mode mode;

    private ExecutionRequest(Mode mode) {
        this.mode = mode;
    }
}

/** Java facade lowering for ordinary, non-relation Row/Field operations. */
final class CanonicalRowLowering {

    private CanonicalRowLowering() {
    }

    static CanonicalRowOperation count(
            GeneratedTable table,
            LogicalRowPlan frontend) {
        if (frontend == null || frontend.isParallel()) return null;
        for (LogicalRowPlan.Stage stage : frontend.stages()) {
            if (stage.kind != LogicalRowPlan.StageKind.TYPED_FILTER) return null;
        }
        return operation(
                table,
                frontend,
                CanonicalRowOperation.TerminalKind.COUNT,
                null);
    }

    static CanonicalRowOperation operation(
            GeneratedTable table,
            LogicalRowPlan frontend,
            CanonicalRowOperation.TerminalKind terminal,
            HostCallbackHandle terminalCallback) {
        if (table == null || frontend == null || frontend.owner() != table
                || frontend.sourceKind() == LogicalRowPlan.SourceKind.RELATION_LEFT) {
            return null;
        }
        CanonicalTableIdentity identity = table.logicalIdentity();
        ArrayList<CanonicalRowStage> stages = new ArrayList<CanonicalRowStage>();
        for (LogicalRowPlan.Stage stage : frontend.stages()) {
            switch (stage.kind) {
                case TYPED_FILTER:
                    stages.add(CanonicalRowStage.typedFilter(stage.predicate));
                    break;
                case CALLBACK_FILTER:
                    stages.add(CanonicalRowStage.callbackFilter(
                            identity, stage.callbackPredicate));
                    break;
                case TYPED_ORDER:
                    stages.add(CanonicalRowStage.typedOrder(
                            new CanonicalOrder(identity, stage.order)));
                    break;
                case CALLBACK_ORDER:
                    stages.add(CanonicalRowStage.callbackOrder(
                            identity, stage.comparator));
                    break;
                case DISTINCT_FIELD:
                    stages.add(CanonicalRowStage.field(
                            CanonicalRowStage.Kind.DISTINCT_FIELD,
                            (int) stage.count));
                    break;
                case FIELD_PROJECT:
                    stages.add(CanonicalRowStage.field(
                            CanonicalRowStage.Kind.FIELD_PROJECT,
                            (int) stage.count));
                    break;
                case SKIP:
                    stages.add(CanonicalRowStage.slice(
                            CanonicalRowStage.Kind.SKIP, stage.count));
                    break;
                case LIMIT:
                    stages.add(CanonicalRowStage.slice(
                            CanonicalRowStage.Kind.LIMIT, stage.count));
                    break;
                default:
                    throw new AssertionError("unknown Java Row stage");
            }
        }
        CanonicalRowOperation.SourceKind sourceKind;
        int indexOrdinal;
        TypedLiteral sourceLiteral;
        if (frontend.sourceKind() == LogicalRowPlan.SourceKind.TABLE_SCAN) {
            sourceKind = CanonicalRowOperation.SourceKind.TABLE;
            indexOrdinal = -1;
            sourceLiteral = null;
        } else {
            sourceKind = CanonicalRowOperation.SourceKind.INDEX_SELECTION;
            indexOrdinal = frontend.indexOrdinal();
            sourceLiteral = frontend.indexProbe();
        }
        return new CanonicalRowOperation(
                identity,
                sourceKind,
                indexOrdinal,
                sourceLiteral,
                stages,
                terminal,
                terminalCallback,
                frontend.isParallel()
                        ? ExecutionRequest.PARALLEL
                        : ExecutionRequest.SEQUENTIAL);
    }
}
