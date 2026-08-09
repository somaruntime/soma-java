package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaOrder;
import io.github.somaruntime.soma.UpdateResult;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.soma.FieldMetadata;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/** Unified exact-leaf, long-domain runtime behind every generated Table facade. */
public final class GeneratedTable {

    private static final long PLAIN_TARGET_BYTES = 2L * 1024L * 1024L;
    private static final int MIN_CHUNK_ROWS = 4096;
    private static final int MAX_CHUNK_ROWS = 65536;
    private static final long CHUNK_HEADER_BYTES = 96L;
    private static final long ARRAY_HEADER_BYTES = 32L;
    private static final long ROOT_HEADER_BYTES = 160L;

    private final GeneratedGroup group;
    private final GeneratedTableLayout layout;
    private final int chunkRows;
    private final MutationFaultInjector faultInjector;
    private final GeneratedRow operationRow;
    private final GeneratedSelectionEditor selectionEditor;
    private final GeneratedQueryCursor primaryQueryCursor;
    private final GeneratedQueryCursor secondaryQueryCursor;
    private final IdentityHashIndex.PreparedAdd keyAddScratch;
    private final IdentityHashIndex.PreparedAdd[] indexAddScratch;
    private final AtomicReference<TableStateRoot> current;

    GeneratedTable(GeneratedGroup group, GeneratedTableLayout layout) {
        this(group, layout, chooseChunkRows(layout), MutationFaultInjector.NONE);
    }

    GeneratedTable(
            GeneratedGroup group,
            GeneratedTableLayout layout,
            int chunkRows,
            MutationFaultInjector faultInjector) {
        if (group == null
                || layout == null
                || chunkRows <= 0
                || (chunkRows & (chunkRows - 1)) != 0
                || faultInjector == null) {
            throw new AssertionError("invalid generated Table runtime");
        }
        this.group = group;
        this.layout = layout;
        this.chunkRows = chunkRows;
        this.faultInjector = faultInjector;
        this.operationRow = new GeneratedRow(this, layout);
        this.selectionEditor = new GeneratedSelectionEditor(layout);
        this.primaryQueryCursor = new GeneratedQueryCursor(layout);
        this.secondaryQueryCursor = new GeneratedQueryCursor(layout);
        this.keyAddScratch = layout.keyFieldIndex() < 0
                ? null : new IdentityHashIndex.PreparedAdd();
        this.indexAddScratch = new IdentityHashIndex.PreparedAdd[layout.indexCount()];
        for (int ordinal = 0; ordinal < indexAddScratch.length; ordinal++) {
            indexAddScratch[ordinal] = new IdentityHashIndex.PreparedAdd();
        }
        this.current = new AtomicReference<TableStateRoot>(
                TableStateRoot.empty(layout, chunkRows));
    }

    public long size() {
        try (GroupOperationGuard.Lease ignored = group.acquire(SomaOperation.QUERY)) {
            return current.get().size;
        }
    }

    public long capacity() {
        try (GroupOperationGuard.Lease ignored = group.acquire(SomaOperation.QUERY)) {
            return current.get().capacity;
        }
    }

    public TableMetadata metadata() {
        TableStateRoot root = current.get();
        Object provenance = root;
        long plain = root.directory.plainEquivalentBytes(
                SomaOperation.QUERY, provenance);
        long representation = root.directory.chunkManagedBytes(
                SomaOperation.QUERY, provenance);
        return SomaSharedSecrets.tableMetadataAccess().create(
                layout.logicalName(),
                root.size,
                root.capacity,
                root.managedBytes,
                plain,
                representation,
                root.directory.encodedChunkCount());
    }

    public FieldMetadata fieldMetadata(
            int fieldIndex,
            String logicalPath,
            String logicalType,
            boolean equalityComparable,
            boolean ordered) {
        layout.fieldStart(fieldIndex);
        if (logicalPath == null || logicalType == null) {
            throw new AssertionError("generated Field metadata identity is missing");
        }
        TableStateRoot root = current.get();
        Object provenance = root;
        long plain = root.directory.fieldPlainEquivalentBytes(
                fieldIndex, SomaOperation.QUERY, provenance);
        long representation = root.directory.fieldRepresentationBytes(
                fieldIndex, SomaOperation.QUERY, provenance);
        return SomaSharedSecrets.fieldMetadataAccess().create(
                logicalPath,
                logicalType,
                layout.fieldNullable(fieldIndex),
                layout.fieldKey(fieldIndex),
                layout.fieldIndexed(fieldIndex),
                equalityComparable,
                ordered,
                plain,
                representation,
                representation < plain);
    }

    String compressionExplain(TableStateRoot root) {
        return "compression=" + group.compression()
                + " encodedChunks=" + root.directory.encodedChunkCount()
                + " representationBytes="
                + root.directory.chunkManagedBytes(SomaOperation.QUERY, root)
                + " plainEquivalentBytes="
                + root.directory.plainEquivalentBytes(SomaOperation.QUERY, root);
    }

    public void reserve(long expectedRows) {
        if (expectedRows < 0L) {
            throw SomaFailures.invalid(
                    SomaOperation.RESERVE,
                    layout.logicalName() + " expectedRows is negative");
        }
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.RESERVE)) {
            TableStateRoot root = current.get();
            if (expectedRows <= root.capacity) return;
            long target = roundedCapacity(
                    expectedRows, SomaOperation.RESERVE, operation.provenance());
            long conservativeManaged = managedBytes(
                    target,
                    sidecarBytes(root, SomaOperation.RESERVE, operation.provenance()),
                    SomaOperation.RESERVE,
                    operation.provenance());
            long reservedDelta = Math.max(
                    0L, conservativeManaged - root.managedBytes);
            try (GlobalMemoryManager.RetainedReservation retained =
                         group.reserveRetained(
                                 reservedDelta,
                                 SomaOperation.RESERVE,
                                 operation.provenance());
                 GlobalMemoryManager.TemporaryLease temporary =
                         group.leaseTemporary(
                                 root.managedBytes,
                                 SomaOperation.RESERVE,
                                 operation.provenance())) {
                TableChunkDirectory candidate = TableChunkDirectory.grow(
                        root.directory, target / chunkRows, layout);
                long finalManaged = managedBytes(
                        candidate,
                        sidecarBytes(
                                root,
                                SomaOperation.RESERVE,
                                operation.provenance()),
                        SomaOperation.RESERVE,
                        operation.provenance());
                inject(
                        MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH,
                        SomaOperation.RESERVE,
                        operation.provenance());
                current.set(new TableStateRoot(
                        root.size,
                        target,
                        CheckedLong.increment(
                                root.stateVersion,
                                SomaOperation.RESERVE,
                                operation.provenance()),
                        finalManaged,
                        candidate,
                        root.key,
                        root.indexes));
                retained.commit();
                long surplus = CheckedLong.subtract(
                        CheckedLong.add(
                                root.managedBytes,
                                reservedDelta,
                                SomaOperation.RESERVE,
                                operation.provenance()),
                        finalManaged,
                        SomaOperation.RESERVE,
                        operation.provenance());
                if (surplus > 0L) group.releasePublished(surplus);
            }
        }
    }

    public GeneratedRow beginAdd() { return begin(GeneratedRow.ADD, SomaOperation.ADD); }
    public GeneratedRow beginFind() { return begin(GeneratedRow.FIND, SomaOperation.FIND); }
    public GeneratedRow beginGet() { return begin(GeneratedRow.GET, SomaOperation.GET); }
    public GeneratedRow beginUpdate() { return begin(GeneratedRow.UPDATE, SomaOperation.UPDATE); }
    public GeneratedRow beginRemove() { return begin(GeneratedRow.REMOVE, SomaOperation.REMOVE); }

    public GeneratedRow borrowedRow() {
        return operationRow;
    }

    public GeneratedEditorAccess borrowedSelectionEditor() {
        return selectionEditor;
    }

    public GeneratedQueryCursor queryCursor() {
        return primaryQueryCursor;
    }

    boolean isBorrowedQueryView(Object value) {
        return primaryQueryCursor.ownsBorrowedView(value)
                || secondaryQueryCursor.ownsBorrowedView(value);
    }

    public GeneratedQueryCursor secondaryQueryCursor() {
        return secondaryQueryCursor;
    }

    public void requireArgument(Object value, SomaOperation operation, String category) {
        if (value == null) {
            throw SomaFailures.invalid(
                    operation, layout.logicalName() + " " + category + " is null");
        }
    }

    public RuntimeException invalidQuery(String category) {
        return SomaFailures.invalid(SomaOperation.QUERY, category + " is null");
    }

    public UpdateResult missingUpdate() {
        return updateResult(0L, 0L);
    }

    public GeneratedProbe newProbe(int fieldIndex) {
        if (fieldIndex < 0 || fieldIndex >= layout.fieldCount()) {
            throw new AssertionError("invalid generated probe Field");
        }
        return new GeneratedProbe(this, layout, fieldIndex);
    }

    public GeneratedIndexSelection indexSelection(
            int indexOrdinal,
            GeneratedProbe probe) {
        int fieldIndex = layout.indexFieldIndex(indexOrdinal);
        probe.requireSealed(this, fieldIndex);
        return new GeneratedIndexSelection(this, indexOrdinal, probe);
    }

    public long count() {
        return QueryOperation.optimizedCount(LogicalRowPlan.tableScan(this));
    }

    public GeneratedPipeline selectAll() {
        return new GeneratedPipeline(this, LogicalRowPlan.tableScan(this));
    }

    public GeneratedPipeline parallel() {
        return new GeneratedPipeline(
                this, LogicalRowPlan.tableScan(this).parallel());
    }

    public GeneratedGrouping groupBy(
            int fieldIndex,
            int keyKind,
            GeneratedCallbacks.RowMapper<?> keyMaterializer) {
        layout.fieldStart(fieldIndex);
        requireQueryCallback(keyMaterializer, "group key materializer");
        return new GeneratedGrouping(
                LogicalRowPlan.tableScan(this),
                fieldIndex,
                keyKind,
                keyMaterializer);
    }

    public GeneratedFieldPipeline fieldSource(int fieldIndex) {
        layout.fieldStart(fieldIndex);
        return new GeneratedFieldPipeline(
                this, fieldIndex, new GeneratedPipeline(
                this, LogicalRowPlan.tableScan(this)
                        .fieldProjection(fieldIndex)));
    }

    public GeneratedPipeline filter(SomaExpression<?> expression) {
        return new GeneratedPipeline(
                this,
                LogicalRowPlan.tableScan(this).typedFilter(
                        requireOwnedExpression(expression)));
    }

    public GeneratedPipeline filter(GeneratedCallbacks.RowPredicate predicate) {
        requireQueryCallback(predicate, "predicate");
        return new GeneratedPipeline(
                this, LogicalRowPlan.tableScan(this).callbackFilter(predicate));
    }

    public GeneratedPipeline sorted(GeneratedCallbacks.RowComparator comparator) {
        requireQueryCallback(comparator, "comparator");
        return new GeneratedPipeline(
                this, LogicalRowPlan.tableScan(this).sorted(comparator));
    }

    public GeneratedPipeline sortedBy(SomaOrder<?> order) {
        return new GeneratedPipeline(
                this,
                LogicalRowPlan.tableScan(this).sortedBy(requireOwnedOrder(order)));
    }

    public GeneratedPipeline skip(long count) {
        requireQueryCount(count, "skip");
        return new GeneratedPipeline(this, LogicalRowPlan.tableScan(this).skip(count));
    }

    public GeneratedPipeline limit(long count) {
        requireQueryCount(count, "limit");
        return new GeneratedPipeline(this, LogicalRowPlan.tableScan(this).limit(count));
    }

    public GeneratedPipeline top(long count, SomaOrder<?> order) {
        requireQueryCount(count, "top");
        return new GeneratedPipeline(
                this,
                LogicalRowPlan.tableScan(this).top(count, requireOwnedOrder(order)));
    }

    public <R> SomaExpression<R> eq(GeneratedProbe probe) {
        return equality(probe, false);
    }

    public <R> SomaExpression<R> ne(GeneratedProbe probe) {
        return equality(probe, true);
    }

    public <R> SomaExpression<R> lt(GeneratedProbe probe) { return order(probe, -2); }
    public <R> SomaExpression<R> le(GeneratedProbe probe) { return order(probe, -1); }
    public <R> SomaExpression<R> gt(GeneratedProbe probe) { return order(probe, 2); }
    public <R> SomaExpression<R> ge(GeneratedProbe probe) { return order(probe, 1); }

    public <R> SomaExpression<R> between(
            final GeneratedProbe lower,
            final GeneratedProbe upper) {
        final int field = requireSameField(lower, upper);
        if (layout.compareValues(lower, upper, field) > 0) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    layout.logicalName() + " between lower bound exceeds upper bound");
        }
        return new GeneratedExpression<R>(
                this, PredicateIr.between(field, lower, upper));
    }

    public <R> SomaExpression<R> in(final GeneratedProbe[] probes) {
        if (probes == null) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "in literals are null");
        }
        requireInLiteralCapacity(probes.length);
        if (probes.length == 0) {
            return new GeneratedExpression<R>(this, PredicateIr.constant(false));
        }
        for (GeneratedProbe probe : probes) {
            if (probe == null) {
                throw SomaFailures.invalid(
                        SomaOperation.QUERY, "in literal is null");
            }
        }
        final int field = probes[0].fieldIndex();
        for (GeneratedProbe probe : probes) {
            probe.requireSealed(this, field);
            if (layout.fieldValueIsNull(probe, field)) {
                throw SomaFailures.invalid(SomaOperation.QUERY, "in literal value is null");
            }
        }
        GeneratedProbe[] unique = new GeneratedProbe[probes.length];
        int uniqueCount = 0;
        for (GeneratedProbe probe : probes) {
            boolean duplicate = false;
            for (int index = 0; index < uniqueCount; index++) {
                if (layout.fieldEquals(probe, unique[index], field)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) unique[uniqueCount++] = probe;
        }
        GeneratedProbe[] snapshot = Arrays.copyOf(unique, uniqueCount);
        return new GeneratedExpression<R>(this, PredicateIr.in(field, snapshot));
    }

    public int requireInLiteralCapacity(long length) {
        if (length < 0L) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY, "in literal length is negative");
        }
        long references = CheckedLong.multiply(
                length, 8L, SomaOperation.QUERY, this);
        CheckedLong.add(references, 32L, SomaOperation.QUERY, this);
        if (length > Integer.MAX_VALUE) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    SomaOperation.QUERY,
                    "in literal array exceeds Java array boundary",
                    this);
        }
        return (int) length;
    }

    public <R> SomaExpression<R> isNull(final int fieldIndex) {
        return new GeneratedExpression<R>(
                this, PredicateIr.nullTest(PredicateIr.Kind.IS_NULL, fieldIndex));
    }

    public <R> SomaExpression<R> isNotNull(final int fieldIndex) {
        return new GeneratedExpression<R>(
                this, PredicateIr.nullTest(PredicateIr.Kind.IS_NOT_NULL, fieldIndex));
    }

    public <R> SomaOrder<R> asc(int fieldIndex) {
        layout.fieldStart(fieldIndex);
        return new GeneratedOrder<R>(this, fieldIndex, false);
    }

    public <R> SomaOrder<R> desc(int fieldIndex) {
        layout.fieldStart(fieldIndex);
        return new GeneratedOrder<R>(this, fieldIndex, true);
    }

    GeneratedOrder<?> requireOwnedOrder(SomaOrder<?> order) {
        GeneratedOrder<?> internal = GeneratedOrder.require(order);
        if (internal.owner() != this) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    layout.logicalName() + " order belongs to another Table");
        }
        return internal;
    }

    void add(GeneratedRow row, Object provenance) {
        TableStateRoot root = current.get();
        long newSize = CheckedLong.increment(root.size, SomaOperation.ADD, provenance);
        long newVersion = CheckedLong.increment(
                root.stateVersion, SomaOperation.ADD, provenance);
        long minimum = root.capacity;
        if (newSize > root.capacity) {
            minimum = roundedCapacity(newSize, SomaOperation.ADD, provenance);
        }
        long preferred = preferredGrowth(root, newSize, minimum, provenance);
        try {
            publishAdd(root, row, preferred, newSize, newVersion, provenance);
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.RESOURCE_LIMIT_EXCEEDED
                    || preferred == minimum) {
                throw failure;
            }
            publishAdd(root, row, minimum, newSize, newVersion, provenance);
        }
    }

    boolean loadByKey(GeneratedRow probe, boolean required, Object provenance) {
        long locator = locateByKey(probe);
        if (locator < 0L) {
            if (required) {
                throw SomaFailures.failure(
                        SomaFailureCode.MISSING_KEY,
                        SomaOperation.GET,
                        layout.logicalName() + " required Key is missing",
                        provenance);
            }
            return false;
        }
        current.get().directory.read(locator, probe);
        return true;
    }

    long locateByKey(TypedValues probe) {
        TableStateRoot root = current.get();
        if (root.key == null) throw new AssertionError("keyless Table point operation");
        return root.key.findUnique(root.directory, probe);
    }

    void load(long locator, TypedValues destination) {
        current.get().directory.read(locator, destination);
    }

    GlobalMemoryManager.TemporaryLease admitPointUpdate(Object provenance) {
        TableStateRoot root = current.get();
        long candidate = root.indexes.length == 0
                ? CheckedLong.add(
                        256L,
                        CheckedLong.multiply(
                                layout.rowWidthBytes(), 2L, SomaOperation.UPDATE, provenance),
                        SomaOperation.UPDATE,
                        provenance)
                : CheckedLong.add(
                        sidecarBytes(root, SomaOperation.UPDATE, provenance),
                        chunkPayloadBytes(SomaOperation.UPDATE, provenance),
                        SomaOperation.UPDATE,
                        provenance);
        return group.leaseTemporary(
                candidate, SomaOperation.UPDATE, provenance);
    }

    UpdateResult update(
            long locator,
            TypedValues original,
            TypedValues staged,
            Object provenance) {
        TableStateRoot root = current.get();
        if (layout.keyFieldIndex() >= 0
                && !layout.fieldEquals(
                        original, staged, layout.keyFieldIndex())) {
            throw SomaFailures.invalid(SomaOperation.UPDATE, "Key Field is immutable");
        }
        if (layout.logicalRowEquals(original, staged)) {
            return updateResult(1L, 0L);
        }

        boolean indexChanged = false;
        for (int ordinal = 0; ordinal < layout.indexCount(); ordinal++) {
            if (!layout.fieldEquals(
                    original, staged, layout.indexFieldIndex(ordinal))) {
                indexChanged = true;
                break;
            }
        }
        long newVersion = CheckedLong.increment(
                root.stateVersion, SomaOperation.UPDATE, provenance);
        long chunkOrdinal = locator / chunkRows;
        long chunkStart = CheckedLong.multiply(
                chunkOrdinal, chunkRows, SomaOperation.UPDATE, provenance);
        boolean completeChunk = root.size - chunkStart >= chunkRows;
        if (!indexChanged && !completeChunk
                && !root.directory.chunk(chunkOrdinal).hasEncodedRepresentation()) {
            UpdateResult result = updateResult(1L, 1L);
            TableStateRoot committed = new TableStateRoot(
                    root.size,
                    root.capacity,
                    newVersion,
                    root.managedBytes,
                    root.directory,
                    root.key,
                    root.indexes);
            inject(MutationFaultPoint.BEFORE_FINAL_COMMIT, SomaOperation.UPDATE, provenance);
            root.directory.write(locator, staged);
            current.set(committed);
            return result;
        }

        TableChunkDirectory candidate = root.directory.copyForUpdate(locator, staged);
        candidate.finishTouched(
                root.size,
                group.compression(),
                SomaOperation.UPDATE,
                provenance);
        IdentityHashIndex[] indexes = indexChanged
                ? rebuildIndexes(
                        candidate, root.size, SomaOperation.UPDATE, provenance)
                : root.indexes;
        inject(
                MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                SomaOperation.UPDATE,
                provenance);
        long sidecars = sidecarBytes(root.key, indexes, SomaOperation.UPDATE, provenance);
        long finalManaged = managedBytes(
                candidate, sidecars, SomaOperation.UPDATE, provenance);
        UpdateResult result = updateResult(1L, 1L);
        publishCandidate(
                root,
                new TableStateRoot(
                        root.size,
                        root.capacity,
                        newVersion,
                        finalManaged,
                        candidate,
                        root.key,
                        indexes),
                SomaOperation.UPDATE,
                provenance);
        return result;
    }

    RemoveResult remove(TypedValues probe, Object provenance) {
        TableStateRoot root = current.get();
        if (root.key == null) throw new AssertionError("keyless Table point remove");
        long locator = root.key.findUnique(root.directory, probe);
        if (locator < 0L) return removeResult(0L);

        try (GlobalMemoryManager.TemporaryLease ignored =
                     group.leaseTemporary(
                             root.managedBytes, SomaOperation.REMOVE, provenance)) {
            long newSize = root.size - 1L;
            TableChunkDirectory candidate = root.directory.copyForRemove(locator, root.size);
            candidate.finishTouched(
                    newSize,
                    group.compression(),
                    SomaOperation.REMOVE,
                    provenance);
            inject(
                    MutationFaultPoint.BEFORE_KEY_REBUILD,
                    SomaOperation.REMOVE,
                    provenance);
            IdentityHashIndex key = IdentityHashIndex.rebuild(
                    layout,
                    layout.keyFieldIndex(),
                    true,
                    chunkRows,
                    candidate,
                    newSize,
                    SomaOperation.REMOVE,
                    provenance);
            IdentityHashIndex[] indexes = rebuildIndexes(
                    candidate, newSize, SomaOperation.REMOVE, provenance);
            inject(
                    MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                    SomaOperation.REMOVE,
                    provenance);
            long sidecars = sidecarBytes(key, indexes, SomaOperation.REMOVE, provenance);
            long finalManaged = managedBytes(
                    candidate, sidecars, SomaOperation.REMOVE, provenance);
            RemoveResult result = removeResult(1L);
            publishCandidate(
                    root,
                    new TableStateRoot(
                            newSize,
                            root.capacity,
                            CheckedLong.increment(
                                    root.stateVersion, SomaOperation.REMOVE, provenance),
                            finalManaged,
                            candidate,
                            key,
                            indexes),
                    SomaOperation.REMOVE,
                    provenance);
            return result;
        }
    }

    long indexCount(int indexOrdinal, GeneratedProbe probe) {
        int fieldIndex = layout.indexFieldIndex(indexOrdinal);
        probe.requireSealed(this, fieldIndex);
        try (GroupOperationGuard.Lease ignored = group.acquire(SomaOperation.QUERY)) {
            TableStateRoot root = current.get();
            return root.indexes[indexOrdinal].count(root.directory, probe);
        }
    }

    PredicateIr requireOwnedExpression(SomaExpression<?> expression) {
        if (!(expression instanceof GeneratedExpression)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    layout.logicalName() + " expression is not issued by SOMA");
        }
        GeneratedExpression<?> internal = (GeneratedExpression<?>) expression;
        if (internal.owner() != this) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    layout.logicalName() + " expression belongs to another Table");
        }
        return internal.predicate();
    }

    GeneratedTableLayout layout() { return layout; }
    boolean sharesGroup(GeneratedTable other) {
        return other != null && group == other.group;
    }
    TableStateRoot currentRoot() { return current.get(); }
    GroupOperationGuard.Lease acquireQuery() { return group.acquire(SomaOperation.QUERY); }
    ForkJoinPool parallelExecutor() { return group.parallelExecutor(); }
    GlobalMemoryManager.TemporaryLease leaseQueryTemporary(
            long bytes,
            Object provenance) {
        return group.leaseTemporary(bytes, SomaOperation.QUERY, provenance);
    }

    GroupOperationGuard.Lease acquireMutation(SomaOperation operation) {
        if (operation != SomaOperation.UPDATE && operation != SomaOperation.REMOVE) {
            throw new AssertionError("invalid Selection mutation operation");
        }
        return group.acquire(operation);
    }

    GlobalMemoryManager.TemporaryLease leaseMutationTemporary(
            long bytes,
            SomaOperation operation,
            Object provenance) {
        return group.leaseTemporary(bytes, operation, provenance);
    }

    GeneratedSelectionEditor selectionEditor() {
        return selectionEditor;
    }

    UpdateResult selectionUpdateResult(long matched, long changed) {
        return updateResult(matched, changed);
    }

    RemoveResult selectionRemoveResult(long removed) {
        return removeResult(removed);
    }

    UpdateResult publishSelectionUpdate(
            TableStateRoot oldRoot,
            TableChunkDirectory candidateDirectory,
            long matched,
            long changed,
            boolean indexedValueChanged,
            Object provenance) {
        candidateDirectory.finishTouched(
                oldRoot.size,
                group.compression(),
                SomaOperation.UPDATE,
                provenance);
        IdentityHashIndex[] indexes = indexedValueChanged
                ? rebuildIndexes(
                        candidateDirectory,
                        oldRoot.size,
                        SomaOperation.UPDATE,
                        provenance)
                : oldRoot.indexes;
        inject(
                MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                SomaOperation.UPDATE,
                provenance);
        long sidecars = sidecarBytes(
                oldRoot.key, indexes, SomaOperation.UPDATE, provenance);
        long finalManaged = managedBytes(
                candidateDirectory, sidecars, SomaOperation.UPDATE, provenance);
        publishCandidate(
                oldRoot,
                new TableStateRoot(
                        oldRoot.size,
                        oldRoot.capacity,
                        CheckedLong.increment(
                                oldRoot.stateVersion,
                                SomaOperation.UPDATE,
                                provenance),
                        finalManaged,
                        candidateDirectory,
                        oldRoot.key,
                        indexes),
                SomaOperation.UPDATE,
                provenance);
        return updateResult(matched, changed);
    }

    RemoveResult publishSelectionRemove(
            TableStateRoot oldRoot,
            TableChunkDirectory candidateDirectory,
            long removed,
            Object provenance) {
        long newSize = oldRoot.size - removed;
        candidateDirectory.finishTouched(
                newSize,
                group.compression(),
                SomaOperation.REMOVE,
                provenance);
        inject(
                MutationFaultPoint.BEFORE_KEY_REBUILD,
                SomaOperation.REMOVE,
                provenance);
        IdentityHashIndex key = layout.keyFieldIndex() < 0
                ? null
                : IdentityHashIndex.rebuild(
                        layout,
                        layout.keyFieldIndex(),
                        true,
                        chunkRows,
                        candidateDirectory,
                        newSize,
                        SomaOperation.REMOVE,
                        provenance);
        IdentityHashIndex[] indexes = rebuildIndexes(
                candidateDirectory, newSize, SomaOperation.REMOVE, provenance);
        inject(
                MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                SomaOperation.REMOVE,
                provenance);
        long sidecars = sidecarBytes(
                key, indexes, SomaOperation.REMOVE, provenance);
        long finalManaged = managedBytes(
                candidateDirectory, sidecars, SomaOperation.REMOVE, provenance);
        publishCandidate(
                oldRoot,
                new TableStateRoot(
                        newSize,
                        oldRoot.capacity,
                        CheckedLong.increment(
                                oldRoot.stateVersion,
                                SomaOperation.REMOVE,
                                provenance),
                        finalManaged,
                        candidateDirectory,
                        key,
                        indexes),
                SomaOperation.REMOVE,
                provenance);
        return removeResult(removed);
    }

    GeneratedPipeline indexPipeline(int indexOrdinal, GeneratedProbe probe) {
        int fieldIndex = layout.indexFieldIndex(indexOrdinal);
        probe.requireSealed(this, fieldIndex);
        return new GeneratedPipeline(
                this, LogicalRowPlan.indexSelection(this, indexOrdinal, probe));
    }

    long stateVersionForTesting() { return current.get().stateVersion; }
    Object rootIdentityForTesting() { return current.get(); }
    long managedBytesForTesting() { return current.get().managedBytes; }
    TableStateRoot rootForTesting() { return current.get(); }

    private GeneratedRow begin(int mode, SomaOperation operation) {
        GroupOperationGuard.Lease lease = group.acquire(operation);
        try {
            operationRow.begin(lease, mode);
            return operationRow;
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    private static void requireQueryCallback(Object callback, String category) {
        if (callback == null) throw SomaFailures.invalid(
                SomaOperation.QUERY, category + " is null");
    }

    private static void requireQueryCount(long count, String category) {
        if (count < 0L) throw SomaFailures.invalid(
                SomaOperation.QUERY, category + " is negative");
    }

    private <R> SomaExpression<R> equality(
            final GeneratedProbe probe,
            final boolean negate) {
        final int field = probe.fieldIndex();
        probe.requireSealed(this, field);
        if (layout.fieldValueIsNull(probe, field)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    "eq/ne does not accept null; use isNull/isNotNull");
        }
        return new GeneratedExpression<R>(this, PredicateIr.compare(
                negate ? PredicateIr.Kind.NE : PredicateIr.Kind.EQ, field, probe));
    }

    private <R> SomaExpression<R> order(
            final GeneratedProbe probe,
            final int operator) {
        final int field = probe.fieldIndex();
        probe.requireSealed(this, field);
        if (layout.fieldValueIsNull(probe, field)) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "ordered literal is null");
        }
        PredicateIr.Kind kind = operator == -2 ? PredicateIr.Kind.LT
                : operator == -1 ? PredicateIr.Kind.LE
                : operator == 2 ? PredicateIr.Kind.GT
                : PredicateIr.Kind.GE;
        return new GeneratedExpression<R>(this, PredicateIr.compare(kind, field, probe));
    }

    private int requireSameField(GeneratedProbe left, GeneratedProbe right) {
        if (left == null || right == null) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "between bound is null");
        }
        int field = left.fieldIndex();
        left.requireSealed(this, field);
        right.requireSealed(this, field);
        if (layout.fieldValueIsNull(left, field)
                || layout.fieldValueIsNull(right, field)) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "between bound value is null");
        }
        return field;
    }

    private void publishAdd(
            TableStateRoot root,
            TypedValues row,
            long targetCapacity,
            long newSize,
            long newVersion,
            Object provenance) {
        IdentityHashIndex.PreparedAdd keyAdd = root.key == null ? null : keyAddScratch;
        IdentityHashIndex.PreparedAdd[] indexAdds = indexAddScratch;
        try {
            if (keyAdd != null) {
                root.key.prepareAdd(
                        keyAdd,
                        root.directory,
                        row,
                        root.size,
                        SomaOperation.ADD,
                        provenance,
                        layout.logicalName());
            }
            for (int ordinal = 0; ordinal < indexAdds.length; ordinal++) {
                root.indexes[ordinal].prepareAdd(
                        indexAdds[ordinal],
                        root.directory,
                        row,
                        root.size,
                        SomaOperation.ADD,
                        provenance,
                        layout.logicalName());
            }
            long sidecarBytes = keyAdd == null ? 0L : keyAdd.managedBytesAfter();
            for (IdentityHashIndex.PreparedAdd add : indexAdds) {
                sidecarBytes = CheckedLong.add(
                        sidecarBytes,
                        add.managedBytesAfter(),
                        SomaOperation.ADD,
                        provenance);
            }
            long conservativeManaged = managedBytes(
                    targetCapacity, sidecarBytes, SomaOperation.ADD, provenance);
            long reservedDelta = Math.max(
                    0L, conservativeManaged - root.managedBytes);
            boolean growth = targetCapacity > root.capacity;
            boolean sealsChunk = newSize % chunkRows == 0L;
            boolean candidateRequired = growth || sealsChunk;
            try (GlobalMemoryManager.RetainedReservation retained =
                         group.reserveRetained(
                                 reservedDelta, SomaOperation.ADD, provenance);
                 GlobalMemoryManager.TemporaryLease temporary =
                         group.leaseTemporary(
                                 candidateRequired ? root.managedBytes : 0L,
                                 SomaOperation.ADD,
                                 provenance)) {
                TableChunkDirectory directory = growth
                        ? TableChunkDirectory.grow(
                                root.directory, targetCapacity / chunkRows, layout)
                        : root.directory;
                if (sealsChunk) {
                    directory = directory.copyForUpdate(root.size, row);
                    directory.finishTouched(
                            newSize,
                            group.compression(),
                            SomaOperation.ADD,
                            provenance);
                }
                long finalManaged = managedBytes(
                        directory, sidecarBytes, SomaOperation.ADD, provenance);
                TableStateRoot committed = new TableStateRoot(
                        newSize,
                        targetCapacity,
                        newVersion,
                        finalManaged,
                        directory,
                        root.key,
                        root.indexes);
                inject(
                        candidateRequired
                                ? MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH
                                : MutationFaultPoint.BEFORE_FINAL_COMMIT,
                        SomaOperation.ADD,
                        provenance);
                if (!sealsChunk) directory.write(root.size, row);
                if (keyAdd != null) keyAdd.commit();
                for (IdentityHashIndex.PreparedAdd add : indexAdds) add.commit();
                current.set(committed);
                retained.commit();
                long surplus = CheckedLong.subtract(
                        CheckedLong.add(
                                root.managedBytes,
                                reservedDelta,
                                SomaOperation.ADD,
                                provenance),
                        finalManaged,
                        SomaOperation.ADD,
                        provenance);
                if (surplus > 0L) group.releasePublished(surplus);
            }
        } finally {
            if (keyAdd != null) keyAdd.clear();
            for (IdentityHashIndex.PreparedAdd add : indexAdds) add.clear();
        }
    }

    private IdentityHashIndex[] rebuildIndexes(
            TableChunkDirectory directory,
            long size,
            SomaOperation operation,
            Object provenance) {
        IdentityHashIndex[] result = new IdentityHashIndex[layout.indexCount()];
        for (int ordinal = 0; ordinal < result.length; ordinal++) {
            inject(MutationFaultPoint.BEFORE_INDEX_REBUILD, operation, provenance);
            result[ordinal] = IdentityHashIndex.rebuild(
                    layout,
                    layout.indexFieldIndex(ordinal),
                    false,
                    chunkRows,
                    directory,
                    size,
                    operation,
                    provenance);
        }
        return result;
    }

    private void publishCandidate(
            TableStateRoot oldRoot,
            TableStateRoot candidate,
            SomaOperation operation,
            Object provenance) {
        long delta = candidate.managedBytes - oldRoot.managedBytes;
        long positive = Math.max(0L, delta);
        try (GlobalMemoryManager.RetainedReservation retained =
                     group.reserveRetained(
                             positive, operation, provenance)) {
            inject(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH, operation, provenance);
            current.set(candidate);
            retained.commit();
        }
        if (delta < 0L) {
            group.releasePublished(-delta);
        }
    }

    private long preferredGrowth(
            TableStateRoot root,
            long required,
            long minimum,
            Object provenance) {
        if (required <= root.capacity) return root.capacity;
        long preferred = Math.max(required, layout.defaultCapacity());
        if (root.capacity > 0L) {
            try {
                preferred = Math.max(
                        preferred,
                        CheckedLong.add(
                                root.capacity,
                                root.capacity / 2L,
                                SomaOperation.ADD,
                                provenance));
            } catch (SomaOperationException ignored) {
                return minimum;
            }
        }
        try {
            return roundedCapacity(preferred, SomaOperation.ADD, provenance);
        } catch (SomaOperationException ignored) {
            return minimum;
        }
    }

    private long roundedCapacity(
            long required,
            SomaOperation operation,
            Object provenance) {
        if (required == 0L) return 0L;
        long adjusted = CheckedLong.add(required, chunkRows - 1L, operation, provenance);
        return CheckedLong.multiply(
                adjusted / chunkRows, chunkRows, operation, provenance);
    }

    private long managedBytes(
            long capacity,
            long sidecars,
            SomaOperation operation,
            Object provenance) {
        if (capacity == 0L && sidecars == 0L) return 0L;
        long payload = CheckedLong.multiply(
                capacity, layout.rowWidthBytes(), operation, provenance);
        long chunks = capacity / chunkRows;
        long perChunk = CheckedLong.add(
                CHUNK_HEADER_BYTES,
                CheckedLong.multiply(
                        layout.leafCount(), ARRAY_HEADER_BYTES, operation, provenance),
                operation,
                provenance);
        long chunkHeaders = CheckedLong.multiply(chunks, perChunk, operation, provenance);
        long directory = TableChunkDirectory.estimatedDirectoryBytes(
                chunks, operation, provenance);
        return CheckedLong.add(
                CheckedLong.add(
                        CheckedLong.add(
                                CheckedLong.add(payload, chunkHeaders, operation, provenance),
                                directory,
                                operation,
                                provenance),
                        ROOT_HEADER_BYTES,
                        operation,
                        provenance),
                sidecars,
                operation,
                provenance);
    }

    private long managedBytes(
            TableChunkDirectory directory,
            long sidecars,
            SomaOperation operation,
            Object provenance) {
        if (directory.chunkCount() == 0L && sidecars == 0L) return 0L;
        long chunks = directory.chunkManagedBytes(operation, provenance);
        long directoryBytes = TableChunkDirectory.estimatedDirectoryBytes(
                directory.chunkCount(), operation, provenance);
        return CheckedLong.add(
                CheckedLong.add(
                        CheckedLong.add(
                                chunks,
                                directoryBytes,
                                operation,
                                provenance),
                        ROOT_HEADER_BYTES,
                        operation,
                        provenance),
                sidecars,
                operation,
                provenance);
    }

    private long chunkPayloadBytes(SomaOperation operation, Object provenance) {
        return CheckedLong.add(
                CheckedLong.multiply(
                        chunkRows, layout.rowWidthBytes(), operation, provenance),
                CheckedLong.add(
                        CHUNK_HEADER_BYTES,
                        CheckedLong.multiply(
                                layout.leafCount(), ARRAY_HEADER_BYTES, operation, provenance),
                        operation,
                        provenance),
                operation,
                provenance);
    }

    private long sidecarBytes(
            TableStateRoot root,
            SomaOperation operation,
            Object provenance) {
        long result = root.key == null ? 0L : root.key.managedBytes();
        for (IdentityHashIndex index : root.indexes) {
            result = CheckedLong.add(
                    result, index.managedBytes(), operation, provenance);
        }
        return result;
    }

    private long sidecarBytes(
            IdentityHashIndex key,
            IdentityHashIndex[] indexes,
            SomaOperation operation,
            Object provenance) {
        long result = key == null ? 0L : key.managedBytes();
        for (IdentityHashIndex index : indexes) {
            result = CheckedLong.add(
                    result, index.managedBytes(), operation, provenance);
        }
        return result;
    }

    private UpdateResult updateResult(long matched, long changed) {
        return SomaSharedSecrets.updateResultAccess().create(matched, changed);
    }

    private RemoveResult removeResult(long removed) {
        return SomaSharedSecrets.removeResultAccess().create(removed);
    }

    private void inject(
            MutationFaultPoint point,
            SomaOperation operation,
            Object provenance) {
        if (faultInjector.fail(point)) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    operation,
                    layout.logicalName() + " injected pre-publication failure",
                    provenance);
        }
    }

    private static int chooseChunkRows(GeneratedTableLayout layout) {
        long desired = PLAIN_TARGET_BYTES / Math.max(1L, layout.rowWidthBytes());
        int bounded = (int) Math.max(
                MIN_CHUNK_ROWS, Math.min((long) MAX_CHUNK_ROWS, desired));
        return Integer.highestOneBit(bounded);
    }
}
