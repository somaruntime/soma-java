package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.UpdateResult;
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
            long finalManaged = managedBytes(
                    target,
                    sidecarBytes(root, SomaOperation.RESERVE, operation.provenance()),
                    SomaOperation.RESERVE,
                    operation.provenance());
            long delta = finalManaged - root.managedBytes;
            try (GlobalMemoryManager.RetainedReservation retained =
                         group.memoryManager().reserveRetained(
                                 delta, SomaOperation.RESERVE, operation.provenance());
                 GlobalMemoryManager.TemporaryLease temporary =
                         group.memoryManager().leaseTemporary(
                                 root.managedBytes,
                                 SomaOperation.RESERVE,
                                 operation.provenance())) {
                TableChunkDirectory candidate = TableChunkDirectory.grow(
                        root.directory, target / chunkRows, layout);
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

    public void requireArgument(Object value, SomaOperation operation, String category) {
        if (value == null) {
            throw SomaFailures.invalid(
                    operation, layout.logicalName() + " " + category + " is null");
        }
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
        return executeCount(new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return true;
            }
        });
    }

    public GeneratedPipeline selectAll() {
        return new GeneratedPipeline(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return true;
            }
        });
    }

    public GeneratedPipeline filter(SomaExpression<?> expression) {
        return new GeneratedPipeline(this, requireOwnedExpression(expression));
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
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                if (layout.storedFieldIsNull(root.directory, locator, field)) return false;
                return layout.compareStored(root.directory, locator, lower, field) >= 0
                        && layout.compareStored(root.directory, locator, upper, field) <= 0;
            }
        });
    }

    public <R> SomaExpression<R> in(final GeneratedProbe[] probes) {
        if (probes == null) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "in literals are null");
        }
        if (probes.length == 0) {
            return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
                @Override public boolean matches(TableStateRoot root, long locator) {
                    return false;
                }
            });
        }
        final int field = probes[0].fieldIndex();
        for (GeneratedProbe probe : probes) {
            if (probe == null) throw SomaFailures.invalid(SomaOperation.QUERY, "in literal is null");
            probe.requireSealed(this, field);
            if (layout.fieldValueIsNull(probe, field)) {
                throw SomaFailures.invalid(SomaOperation.QUERY, "in literal value is null");
            }
        }
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                for (GeneratedProbe probe : probes) {
                    if (layout.fieldEquals(root.directory, locator, probe, field)) return true;
                }
                return false;
            }
        });
    }

    public <R> SomaExpression<R> isNull(final int fieldIndex) {
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return layout.storedFieldIsNull(root.directory, locator, fieldIndex);
            }
        });
    }

    public <R> SomaExpression<R> isNotNull(final int fieldIndex) {
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                return !layout.storedFieldIsNull(root.directory, locator, fieldIndex);
            }
        });
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
        return group.memoryManager().leaseTemporary(
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
        if (!indexChanged) {
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
        IdentityHashIndex[] indexes = rebuildIndexes(
                candidate, root.size, SomaOperation.UPDATE, provenance);
        inject(
                MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                SomaOperation.UPDATE,
                provenance);
        long sidecars = sidecarBytes(root.key, indexes, SomaOperation.UPDATE, provenance);
        long finalManaged = managedBytes(
                root.capacity, sidecars, SomaOperation.UPDATE, provenance);
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
                     group.memoryManager().leaseTemporary(
                             root.managedBytes, SomaOperation.REMOVE, provenance)) {
            long newSize = root.size - 1L;
            TableChunkDirectory candidate = root.directory.copyForRemove(locator, root.size);
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
                    root.capacity, sidecars, SomaOperation.REMOVE, provenance);
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

    GeneratedExpression.Node requireOwnedExpression(SomaExpression<?> expression) {
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
        return internal.node();
    }

    long executeCount(GeneratedExpression.Node predicate) {
        try (GroupOperationGuard.Lease ignored = group.acquire(SomaOperation.QUERY)) {
            TableStateRoot root = current.get();
            long result = 0L;
            for (long locator = 0L; locator < root.size; locator++) {
                if (predicate.matches(root, locator)) result++;
            }
            return result;
        }
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
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                boolean equal = layout.fieldEquals(root.directory, locator, probe, field);
                return negate ? !equal : equal;
            }
        });
    }

    private <R> SomaExpression<R> order(
            final GeneratedProbe probe,
            final int operator) {
        final int field = probe.fieldIndex();
        probe.requireSealed(this, field);
        if (layout.fieldValueIsNull(probe, field)) {
            throw SomaFailures.invalid(SomaOperation.QUERY, "ordered literal is null");
        }
        return new GeneratedExpression<R>(this, new GeneratedExpression.Node() {
            @Override public boolean matches(TableStateRoot root, long locator) {
                if (layout.storedFieldIsNull(root.directory, locator, field)) return false;
                int compared = layout.compareStored(root.directory, locator, probe, field);
                return operator == -2 ? compared < 0
                        : operator == -1 ? compared <= 0
                        : operator == 2 ? compared > 0
                        : compared >= 0;
            }
        });
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
        IdentityHashIndex.PreparedAdd keyAdd = root.key == null
                ? null
                : root.key.prepareAdd(
                        root.directory,
                        row,
                        root.size,
                        SomaOperation.ADD,
                        provenance,
                        layout.logicalName());
        IdentityHashIndex.PreparedAdd[] indexAdds =
                new IdentityHashIndex.PreparedAdd[root.indexes.length];
        for (int ordinal = 0; ordinal < indexAdds.length; ordinal++) {
            indexAdds[ordinal] = root.indexes[ordinal].prepareAdd(
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
        long finalManaged = managedBytes(
                targetCapacity, sidecarBytes, SomaOperation.ADD, provenance);
        long delta = finalManaged - root.managedBytes;
        try (GlobalMemoryManager.RetainedReservation retained =
                     group.memoryManager().reserveRetained(
                             delta, SomaOperation.ADD, provenance);
             GlobalMemoryManager.TemporaryLease temporary =
                     group.memoryManager().leaseTemporary(
                             targetCapacity > root.capacity ? root.managedBytes : 0L,
                             SomaOperation.ADD,
                             provenance)) {
            TableChunkDirectory directory = targetCapacity > root.capacity
                    ? TableChunkDirectory.grow(
                            root.directory, targetCapacity / chunkRows, layout)
                    : root.directory;
            TableStateRoot committed = new TableStateRoot(
                    newSize,
                    targetCapacity,
                    newVersion,
                    finalManaged,
                    directory,
                    root.key,
                    root.indexes);
            inject(
                    targetCapacity > root.capacity
                            ? MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH
                            : MutationFaultPoint.BEFORE_FINAL_COMMIT,
                    SomaOperation.ADD,
                    provenance);
            directory.write(root.size, row);
            if (keyAdd != null) keyAdd.commit();
            for (IdentityHashIndex.PreparedAdd add : indexAdds) add.commit();
            current.set(committed);
            retained.commit();
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
                     group.memoryManager().reserveRetained(
                             positive, operation, provenance)) {
            inject(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH, operation, provenance);
            current.set(candidate);
            retained.commit();
        }
        if (delta < 0L) {
            group.memoryManager().releasePublished(-delta);
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
