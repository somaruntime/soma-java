package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal all-long keyed Table runtime used by the I1 generated vertical slice.
 * Payload is paged primitive storage; generated code owns the public type shape.
 */
public final class GeneratedLongTable {

    private static final long PLAIN_TARGET_BYTES = 2L * 1024L * 1024L;
    private static final int MIN_CHUNK_ROWS = 4096;
    private static final int MAX_CHUNK_ROWS = 65536;
    private static final long ESTIMATED_CHUNK_HEADER_BYTES = 64L;
    private static final long ESTIMATED_ARRAY_HEADER_BYTES = 32L;
    private static final long ESTIMATED_ROOT_HEADER_BYTES = 128L;

    private final GeneratedGroup group;
    private final String logicalName;
    private final long defaultCapacity;
    private final int fieldCount;
    private final int keyFieldIndex;
    private final int chunkRows;
    private final MutationFaultInjector faultInjector;
    private final AtomicReference<LongStateRoot> current =
            new AtomicReference<LongStateRoot>(LongStateRoot.empty());

    GeneratedLongTable(
            GeneratedGroup group,
            String logicalName,
            long defaultCapacity,
            int fieldCount,
            int keyFieldIndex) {
        this(
                group,
                logicalName,
                defaultCapacity,
                fieldCount,
                keyFieldIndex,
                chooseChunkRows(fieldCount),
                MutationFaultInjector.NONE);
    }

    GeneratedLongTable(
            GeneratedGroup group,
            String logicalName,
            long defaultCapacity,
            int fieldCount,
            int keyFieldIndex,
            int chunkRows,
            MutationFaultInjector faultInjector) {
        if (group == null
                || logicalName == null
                || logicalName.isEmpty()
                || defaultCapacity < 0L
                || fieldCount < 2
                || keyFieldIndex < 0
                || keyFieldIndex >= fieldCount
                || chunkRows <= 0
                || (chunkRows & (chunkRows - 1)) != 0
                || faultInjector == null) {
            throw new AssertionError("invalid generated long Table shape");
        }
        this.group = group;
        this.logicalName = logicalName;
        this.defaultCapacity = defaultCapacity;
        this.fieldCount = fieldCount;
        this.keyFieldIndex = keyFieldIndex;
        this.chunkRows = chunkRows;
        this.faultInjector = faultInjector;
    }

    public long size() {
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.QUERY)) {
            return current.get().size;
        }
    }

    public long capacity() {
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.QUERY)) {
            return current.get().capacity;
        }
    }

    public void reserve(long expectedRows) {
        if (expectedRows < 0L) {
            throw SomaFailures.invalid(
                    SomaOperation.RESERVE,
                    logicalName + " expectedRows is negative");
        }
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.RESERVE)) {
            LongStateRoot root = current.get();
            if (expectedRows <= root.capacity) {
                return;
            }
            long target = roundedCapacity(
                    expectedRows, SomaOperation.RESERVE, operation.provenance());
            publishReserve(root, target, SomaOperation.RESERVE, operation.provenance());
        }
    }

    public void requireArgument(
            Object value,
            SomaOperation operation,
            String category) {
        if (value == null) {
            throw SomaFailures.invalid(operation, logicalName + " " + category + " is null");
        }
    }

    public void add(long[] values) {
        if (values == null || values.length != fieldCount) {
            throw SomaFailures.invalid(SomaOperation.ADD, logicalName + " carrier shape is invalid");
        }
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.ADD)) {
            Object provenance = operation.provenance();
            LongStateRoot root = current.get();
            long key = values[keyFieldIndex];
            if (root.keyIndex.find(key) >= 0L) {
                throw SomaFailures.failure(
                        SomaFailureCode.DUPLICATE_KEY,
                        SomaOperation.ADD,
                        logicalName + " duplicate Key",
                        provenance);
            }

            long newSize = CheckedLong.increment(root.size, SomaOperation.ADD, provenance);
            long newVersion = CheckedLong.increment(
                    root.stateVersion, SomaOperation.ADD, provenance);
            if (newSize > root.capacity) {
                long minimum = roundedCapacity(
                        newSize, SomaOperation.ADD, provenance);
                long preferred = preferredGrowth(root, newSize, minimum, provenance);
                try {
                    publishCandidateAdd(
                            root, preferred, values, newSize, newVersion, provenance);
                } catch (SomaOperationException failure) {
                    if (failure.code() != SomaFailureCode.RESOURCE_LIMIT_EXCEEDED
                            || preferred == minimum) {
                        throw failure;
                    }
                    publishCandidateAdd(
                            root, minimum, values, newSize, newVersion, provenance);
                }
                return;
            }

            if (!root.keyIndex.canInsertInPlace(key)) {
                publishCandidateAdd(
                        root,
                        root.capacity,
                        values,
                        newSize,
                        newVersion,
                        provenance);
            } else {
                publishInPlaceAdd(
                        root, values, newSize, newVersion, provenance);
            }
        }
    }

    public long[] find(long key) {
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.FIND)) {
            LongStateRoot root = current.get();
            long locator = root.keyIndex.find(key);
            return locator < 0L ? null : readRow(root, locator);
        }
    }

    public long[] get(long key) {
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.GET)) {
            LongStateRoot root = current.get();
            long locator = root.keyIndex.find(key);
            if (locator < 0L) {
                throw SomaFailures.failure(
                        SomaFailureCode.MISSING_KEY,
                        SomaOperation.GET,
                        logicalName + " required Key is missing",
                        operation.provenance());
            }
            return readRow(root, locator);
        }
    }

    public UpdateResult update(
            long key,
            Object applicationCallback,
            LongEditorCallback callback) {
        requireArgument(applicationCallback, SomaOperation.UPDATE, "updater");
        if (callback == null) {
            throw new AssertionError("generated Editor callback is missing");
        }
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.UPDATE)) {
            Object provenance = operation.provenance();
            LongStateRoot root = current.get();
            long locator = root.keyIndex.find(key);
            if (locator < 0L) {
                return updateResult(0L, 0L);
            }

            long stagingBytes = editorStagingBytes(SomaOperation.UPDATE, provenance);
            try (GlobalMemoryManager.TemporaryLease ignored =
                         group.memoryManager().leaseTemporary(
                                 stagingBytes, SomaOperation.UPDATE, provenance)) {
                long[] original = readRow(root, locator);
                LongEditorSession editor = new LongEditorSession(
                        original, keyFieldIndex, provenance);
                try {
                    callback.accept(editor);
                } catch (Exception failure) {
                    throw SomaFailures.callbackFailure(
                            SomaOperation.UPDATE, failure, provenance);
                } finally {
                    editor.close();
                }

                if (!editor.changed()) {
                    return updateResult(1L, 0L);
                }
                long newVersion = CheckedLong.increment(
                        root.stateVersion, SomaOperation.UPDATE, provenance);
                UpdateResult result = updateResult(1L, 1L);
                LongStateRoot published = new LongStateRoot(
                        root.size,
                        root.capacity,
                        newVersion,
                        root.managedBytes,
                        root.directory,
                        root.keyIndex);
                inject(MutationFaultPoint.BEFORE_FINAL_COMMIT, SomaOperation.UPDATE, provenance);
                writeNonKeyValues(root, locator, editor.stagedValues());
                current.set(published);
                return result;
            }
        }
    }

    public long count() {
        return executeCount(new LongExpression.ConstantNode(true));
    }

    public GeneratedLongPipeline selectAll() {
        return new GeneratedLongPipeline(this, new LongExpression.ConstantNode(true));
    }

    public GeneratedLongPipeline filter(SomaExpression<?> expression) {
        return new GeneratedLongPipeline(this, requireOwnedExpression(expression));
    }

    public <R> SomaExpression<R> eq(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.EQ, value);
    }

    public <R> SomaExpression<R> ne(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.NE, value);
    }

    public <R> SomaExpression<R> lt(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.LT, value);
    }

    public <R> SomaExpression<R> le(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.LE, value);
    }

    public <R> SomaExpression<R> gt(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.GT, value);
    }

    public <R> SomaExpression<R> ge(int fieldIndex, long value) {
        return comparison(fieldIndex, LongExpression.CompareNode.GE, value);
    }

    public <R> SomaExpression<R> between(
            int fieldIndex,
            long lowerInclusive,
            long upperInclusive) {
        requireFieldIndex(fieldIndex);
        if (lowerInclusive > upperInclusive) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    logicalName + " between lower bound exceeds upper bound");
        }
        return new LongExpression<R>(
                this,
                new LongExpression.BetweenNode(
                        fieldIndex, lowerInclusive, upperInclusive));
    }

    public <R> SomaExpression<R> in(int fieldIndex, long[] literals) {
        requireFieldIndex(fieldIndex);
        if (literals == null) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    logicalName + " in literals are null");
        }
        if (literals.length == 0) {
            return new LongExpression<R>(
                    this, new LongExpression.ConstantNode(false));
        }
        long[] values = literals.clone();
        Arrays.sort(values);
        int unique = 1;
        for (int index = 1; index < values.length; index++) {
            if (values[index] != values[unique - 1]) {
                values[unique++] = values[index];
            }
        }
        if (unique != values.length) {
            values = Arrays.copyOf(values, unique);
        }
        return new LongExpression<R>(this, new LongExpression.InNode(fieldIndex, values));
    }

    LongExpression.Node requireOwnedExpression(SomaExpression<?> expression) {
        if (!(expression instanceof LongExpression)) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    logicalName + " expression is not issued by SOMA");
        }
        LongExpression<?> internal = (LongExpression<?>) expression;
        if (internal.owner() != this) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    logicalName + " expression belongs to another Table");
        }
        return internal.node();
    }

    long executeCount(LongExpression.Node predicate) {
        try (GroupOperationGuard.Lease operation = group.acquire(SomaOperation.QUERY)) {
            LongStateRoot root = current.get();
            long remaining = root.size;
            long result = 0L;
            for (long ordinal = 0L; remaining > 0L; ordinal++) {
                PlainLongChunk chunk = root.directory.plainChunk(ordinal);
                long[][] columns = chunk.columns();
                int rows = (int) Math.min((long) chunkRows, remaining);
                for (int offset = 0; offset < rows; offset++) {
                    if (predicate.matches(columns, offset)) {
                        result++;
                    }
                }
                remaining -= rows;
            }
            return result;
        }
    }

    long stateVersionForTesting() {
        return current.get().stateVersion;
    }

    Object rootIdentityForTesting() {
        return current.get();
    }

    long managedBytesForTesting() {
        return current.get().managedBytes;
    }

    private <R> SomaExpression<R> comparison(
            int fieldIndex,
            int operator,
            long value) {
        requireFieldIndex(fieldIndex);
        return new LongExpression<R>(
                this,
                new LongExpression.CompareNode(fieldIndex, operator, value));
    }

    private void requireFieldIndex(int fieldIndex) {
        if (fieldIndex < 0 || fieldIndex >= fieldCount) {
            throw SomaFailures.invalid(
                    SomaOperation.QUERY,
                    logicalName + " Field endpoint is invalid");
        }
    }

    private void publishReserve(
            LongStateRoot root,
            long targetCapacity,
            SomaOperation operation,
            Object provenance) {
        long newVersion = CheckedLong.increment(root.stateVersion, operation, provenance);
        long finalManaged = managedBytes(
                targetCapacity, root.keyIndex.managedBytes(), operation, provenance);
        long retainedDelta = finalManaged - root.managedBytes;
        try (GlobalMemoryManager.RetainedReservation retained =
                     group.memoryManager().reserveRetained(
                             retainedDelta, operation, provenance);
             GlobalMemoryManager.TemporaryLease temporary =
                     group.memoryManager().leaseTemporary(
                             root.managedBytes, operation, provenance)) {
            long targetChunks = targetCapacity / chunkRows;
            PagedChunkDirectory candidate = PagedChunkDirectory.grow(
                    root.directory, targetChunks, fieldCount, chunkRows);
            inject(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH, operation, provenance);
            current.set(new LongStateRoot(
                    root.size,
                    targetCapacity,
                    newVersion,
                    finalManaged,
                    candidate,
                    root.keyIndex));
            retained.commit();
        }
    }

    private void publishInPlaceAdd(
            LongStateRoot root,
            long[] values,
            long newSize,
            long newVersion,
            Object provenance) {
        long key = values[keyFieldIndex];
        long predictedIndexBytes = root.keyIndex.estimatedManagedBytesAfterAdd(
                key, SomaOperation.ADD, provenance);
        long finalManaged = managedBytes(
                root.capacity,
                predictedIndexBytes,
                SomaOperation.ADD,
                provenance);
        long retainedDelta = finalManaged - root.managedBytes;
        try (GlobalMemoryManager.RetainedReservation retained =
                     group.memoryManager().reserveRetained(
                             retainedDelta, SomaOperation.ADD, provenance)) {
            LongKeyIndex.PreparedInsert prepared = root.keyIndex.prepareInPlace(
                    key, root.size, SomaOperation.ADD, provenance);
            if (prepared == null
                    || prepared.managedBytesAfterCommit() != predictedIndexBytes) {
                throw new AssertionError("SOMA in-place Key admission drift");
            }
            LongStateRoot published = new LongStateRoot(
                    newSize,
                    root.capacity,
                    newVersion,
                    finalManaged,
                    root.directory,
                    root.keyIndex);
            inject(MutationFaultPoint.BEFORE_FINAL_COMMIT, SomaOperation.ADD, provenance);
            writeRow(root.directory, root.size, values);
            prepared.commit();
            current.set(published);
            retained.commit();
        }
    }

    private void publishCandidateAdd(
            LongStateRoot root,
            long targetCapacity,
            long[] values,
            long newSize,
            long newVersion,
            Object provenance) {
        long key = values[keyFieldIndex];
        long predictedIndexBytes = root.keyIndex.estimatedManagedBytesAfterAdd(
                key, SomaOperation.ADD, provenance);
        long finalManaged = managedBytes(
                targetCapacity,
                predictedIndexBytes,
                SomaOperation.ADD,
                provenance);
        long retainedDelta = finalManaged - root.managedBytes;
        try (GlobalMemoryManager.RetainedReservation retained =
                     group.memoryManager().reserveRetained(
                             retainedDelta, SomaOperation.ADD, provenance);
             GlobalMemoryManager.TemporaryLease temporary =
                     group.memoryManager().leaseTemporary(
                             root.managedBytes, SomaOperation.ADD, provenance)) {
            PagedChunkDirectory candidateDirectory;
            if (targetCapacity > root.capacity) {
                candidateDirectory = PagedChunkDirectory.grow(
                        root.directory,
                        targetCapacity / chunkRows,
                        fieldCount,
                        chunkRows);
            } else {
                long ordinal = root.size / chunkRows;
                PlainLongChunk replacement =
                        root.directory.plainChunk(ordinal).copy();
                candidateDirectory = root.directory.copyReplacing(ordinal, replacement);
            }
            LongKeyIndex candidateIndex = root.keyIndex.copyAndAdd(
                    key, root.size, SomaOperation.ADD, provenance);
            if (candidateIndex.managedBytes() != predictedIndexBytes) {
                throw new AssertionError("SOMA Key accounting prediction drift");
            }
            writeRow(candidateDirectory, root.size, values);
            inject(
                    MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH,
                    SomaOperation.ADD,
                    provenance);
            current.set(new LongStateRoot(
                    newSize,
                    targetCapacity,
                    newVersion,
                    finalManaged,
                    candidateDirectory,
                    candidateIndex));
            retained.commit();
        }
    }

    private long preferredGrowth(
            LongStateRoot root,
            long requiredRows,
            long minimumCapacity,
            Object provenance) {
        long preferredRows = Math.max(requiredRows, defaultCapacity);
        if (root.capacity > 0L) {
            try {
                preferredRows = Math.max(
                        preferredRows,
                        CheckedLong.add(
                                root.capacity,
                                root.capacity / 2L,
                                SomaOperation.ADD,
                                provenance));
            } catch (SomaOperationException ignored) {
                return minimumCapacity;
            }
        }
        try {
            return roundedCapacity(preferredRows, SomaOperation.ADD, provenance);
        } catch (SomaOperationException ignored) {
            return minimumCapacity;
        }
    }

    private long roundedCapacity(
            long requiredRows,
            SomaOperation operation,
            Object provenance) {
        if (requiredRows == 0L) {
            return 0L;
        }
        long adjusted = CheckedLong.add(
                requiredRows, chunkRows - 1L, operation, provenance);
        long chunks = adjusted / chunkRows;
        return CheckedLong.multiply(chunks, chunkRows, operation, provenance);
    }

    private long managedBytes(
            long capacity,
            long keyBytes,
            SomaOperation operation,
            Object provenance) {
        if (capacity == 0L) {
            return keyBytes;
        }
        long rowWidth = CheckedLong.multiply(
                fieldCount, Long.BYTES, operation, provenance);
        long payload = CheckedLong.multiply(
                capacity, rowWidth, operation, provenance);
        long chunks = capacity / chunkRows;
        long perChunk = CheckedLong.add(
                ESTIMATED_CHUNK_HEADER_BYTES,
                CheckedLong.multiply(
                        fieldCount,
                        ESTIMATED_ARRAY_HEADER_BYTES,
                        operation,
                        provenance),
                operation,
                provenance);
        long chunkHeaders = CheckedLong.multiply(
                chunks, perChunk, operation, provenance);
        long directory = PagedChunkDirectory.estimatedDirectoryBytes(
                chunks, operation, provenance);
        return CheckedLong.add(
                CheckedLong.add(
                        CheckedLong.add(
                                CheckedLong.add(
                                        payload,
                                        chunkHeaders,
                                        operation,
                                        provenance),
                                ESTIMATED_ROOT_HEADER_BYTES,
                                operation,
                                provenance),
                        directory,
                        operation,
                        provenance),
                keyBytes,
                operation,
                provenance);
    }

    private long editorStagingBytes(
            SomaOperation operation,
            Object provenance) {
        return CheckedLong.add(
                128L,
                CheckedLong.multiply(
                        CheckedLong.multiply(
                                fieldCount, Long.BYTES, operation, provenance),
                        2L,
                        operation,
                        provenance),
                operation,
                provenance);
    }

    private long[] readRow(LongStateRoot root, long locator) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        long[][] columns = root.directory.plainChunk(ordinal).columns();
        long[] values = new long[fieldCount];
        for (int field = 0; field < fieldCount; field++) {
            values[field] = columns[field][offset];
        }
        return values;
    }

    private void writeRow(
            PagedChunkDirectory directory,
            long locator,
            long[] values) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        long[][] columns = directory.plainChunk(ordinal).columns();
        for (int field = 0; field < fieldCount; field++) {
            columns[field][offset] = values[field];
        }
    }

    private void writeNonKeyValues(
            LongStateRoot root,
            long locator,
            long[] values) {
        long ordinal = locator / chunkRows;
        int offset = (int) (locator % chunkRows);
        long[][] columns = root.directory.plainChunk(ordinal).columns();
        for (int field = 0; field < fieldCount; field++) {
            if (field != keyFieldIndex) {
                columns[field][offset] = values[field];
            }
        }
    }

    private UpdateResult updateResult(long matched, long changed) {
        return SomaSharedSecrets.updateResultAccess().create(matched, changed);
    }

    private void inject(
            MutationFaultPoint point,
            SomaOperation operation,
            Object provenance) {
        if (faultInjector.fail(point)) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    operation,
                    logicalName + " injected pre-publication failure",
                    provenance);
        }
    }

    private static int chooseChunkRows(int fieldCount) {
        long rowWidth = Math.max(1L, fieldCount * (long) Long.BYTES);
        long desired = PLAIN_TARGET_BYTES / rowWidth;
        int bounded = (int) Math.max(
                MIN_CHUNK_ROWS,
                Math.min((long) MAX_CHUNK_ROWS, desired));
        return Integer.highestOneBit(bounded);
    }
}
