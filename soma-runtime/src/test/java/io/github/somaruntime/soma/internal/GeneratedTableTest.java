package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.somaruntime.soma.GroupedLongEntry;
import io.github.somaruntime.soma.GroupedLongResult;
import io.github.somaruntime.soma.LongGroupedLongEntry;
import io.github.somaruntime.soma.LongGroupedLongResult;
import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaOrder;
import io.github.somaruntime.soma.TableMetadata;
import io.github.somaruntime.soma.UpdateResult;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeneratedTableTest {

    @Test
    void autoCompressionPreservesNormalQueryIndexAndMutationSemantics() {
        GeneratedTable table = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20)),
                testLayout(), 4096, MutationFaultInjector.NONE);
        Object shared = new Object();
        for (long key = 1L; key <= 4096L; key++) {
            add(table, key, "repeated", 7, shared);
        }

        TableMetadata compressed = table.metadata();
        assertTrue(compressed.encoded());
        assertTrue(compressed.representationBytes()
                < compressed.plainEquivalentBytes());
        assertEquals(4096L, table.count());
        assertEquals(4096L, indexCount(table, "repeated"));
        assertRow(table, 10L, "repeated", 7, shared);
        assertEquals(4096L * 7L, table.fieldSource(2)
                .primitiveInt(() -> table.queryCursor().viewInt(2))
                .sumIntegral());

        UpdateResult payload = update(table, 10L, "repeated", 12, shared);
        assertEquals(1L, payload.changed());
        assertRow(table, 10L, "repeated", 12, shared);
        assertTrue(table.metadata().encoded());
        assertEquals(4096L * 7L + 5L, table.fieldSource(2)
                .primitiveInt(() -> table.queryCursor().viewInt(2))
                .sumIntegral());

        UpdateResult indexed = update(table, 10L, "moved", 12, shared);
        assertEquals(1L, indexed.changed());
        assertEquals(4095L, indexCount(table, "repeated"));
        assertEquals(1L, indexCount(table, "moved"));

        assertEquals(1L, remove(table, 10L).removed());
        assertMissing(table, 10L);
        assertEquals(4095L, table.size());
        assertTrue(table.selectAll().explain().contains("compression=AUTO"));
    }

    @Test
    void compressionOffKeepsCompleteChunksPlain() {
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        SomaCompression.OFF),
                testLayout(), 4096, MutationFaultInjector.NONE);
        Object shared = new Object();
        for (long key = 1L; key <= 4096L; key++) {
            add(table, key, "repeated", 7, shared);
        }
        assertFalse(table.metadata().encoded());
        assertEquals(4096L, table.count());
        assertRow(table, 4096L, "repeated", 7, shared);
    }

    @Test
    void autoCompressionRejectsMarginalShortRunRleForRandomAccess() {
        GeneratedTable table = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20)),
                rleCostLayout(), 4096, MutationFaultInjector.NONE);
        for (long index = 0L; index < 4096L; index++) {
            try (GeneratedRow row = table.beginAdd()) {
                row.putLong(0, index + 1L);
                row.putLong(1, index / 2L);
                row.add();
            }
        }

        assertFalse(table.metadata().encoded());
        assertEquals(4096L, table.count());
    }

    @Test
    void repeatedPointMutationPromotesHotEncodedChunkToPlain() {
        GeneratedTable table = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20)),
                testLayout(), 4096, MutationFaultInjector.NONE);
        Object shared = new Object();
        for (long key = 1L; key <= 4096L; key++) {
            add(table, key, "repeated", 7, shared);
        }
        assertTrue(table.metadata().encoded());

        for (long key = 1L; key <= 65L; key++) {
            UpdateResult result = update(
                    table, key, "repeated", 7 + (int) key, shared);
            assertEquals(1L, result.changed());
        }

        assertFalse(table.metadata().encoded());
        assertRow(table, 1L, "repeated", 8, shared);
        assertRow(table, 65L, "repeated", 72, shared);
        assertEquals(4096L, indexCount(table, "repeated"));
    }

    @Test
    void parallelModeUsesBoundedCustomPoolAndPreservesCanonicalResults() {
        TrackingForkJoinPool pool = new TrackingForkJoinPool(4);
        try {
            GeneratedTable table = new GeneratedTable(
                    testGroup(new GlobalMemoryManager(64L << 20), pool),
                    testLayout(), 4, MutationFaultInjector.NONE);
            for (int index = 0; index < 256; index++) {
                add(table, index + 1L, "bucket-" + (index & 3), index, new Object());
            }
            GeneratedProbe minimum = table.newProbe(2);
            minimum.putInt(2, 128);
            SomaExpression<Object> expression = table.ge(minimum.seal());

            long sequential = table.filter(expression).count();
            long parallel = table.parallel().filter(expression).count();
            assertEquals(sequential, parallel);
            assertTrue(pool.submissions.get() > 0);
            assertTrue(table.parallel().explain().contains("mode=PARALLEL"));

            Thread caller = Thread.currentThread();
            AtomicReference<Thread> callbackThread = new AtomicReference<Thread>();
            table.parallel().filter(expression).forEach(() -> {
                callbackThread.compareAndSet(null, Thread.currentThread());
                assertSame(caller, Thread.currentThread());
            });
            assertSame(caller, callbackThread.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelModeFailsClosedWhenConfiguredPoolIsShutdown() {
        ForkJoinPool pool = new ForkJoinPool(2);
        GeneratedTable table = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20), pool),
                testLayout(), 4, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        pool.shutdownNow();

        SomaOperationException failure = assertThrows(
                SomaOperationException.class,
                () -> table.parallel().count());
        assertEquals(
                SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                failure.code());
    }

    @Test
    void selectionUpdatePublishesOneGenerationAndNoOpPublishesNothing() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        Object first = new Object();
        Object second = new Object();
        Object untouched = new Object();
        add(table, 1L, "selected", 10, first);
        add(table, 2L, "selected", 20, second);
        add(table, 3L, "other", 30, untouched);

        GeneratedProbe selected = table.newProbe(1);
        selected.putReference(1, "selected");
        long before = table.stateVersionForTesting();
        TableChunkDirectory directoryBeforePayloadUpdate =
                table.rootForTesting().directory;
        TableChunk chunkBeforePayloadUpdate =
                directoryBeforePayloadUpdate.chunk(0);
        IdentityHashIndex[] indexesBeforePayloadUpdate =
                table.rootForTesting().indexes;
        UpdateResult result = table.indexSelection(0, selected.seal()).update(() -> {
            GeneratedSelectionEditor editor = table.selectionEditor();
            editor.editInt(2, editor.viewInt(2) + 5);
        });

        assertEquals(2L, result.matched());
        assertEquals(2L, result.changed());
        assertEquals(before + 1L, table.stateVersionForTesting());
        assertRow(table, 1L, "selected", 15, first);
        assertRow(table, 2L, "selected", 25, second);
        assertRow(table, 3L, "other", 30, untouched);
        assertEquals(2L, indexCount(table, "selected"));
        assertSame(indexesBeforePayloadUpdate, table.rootForTesting().indexes);
        assertSame(directoryBeforePayloadUpdate, table.rootForTesting().directory);
        assertSame(chunkBeforePayloadUpdate, table.rootForTesting().directory.chunk(0));

        GeneratedProbe selectedForMove = table.newProbe(1);
        selectedForMove.putReference(1, "selected");
        IdentityHashIndex[] indexesBeforeIndexedUpdate =
                table.rootForTesting().indexes;
        TableChunkDirectory directoryBeforeIndexedUpdate =
                table.rootForTesting().directory;
        UpdateResult moved = table.indexSelection(0, selectedForMove.seal()).update(() ->
                table.selectionEditor().editReference(1, "moved"));
        assertEquals(2L, moved.matched());
        assertEquals(2L, moved.changed());
        assertNotSame(indexesBeforeIndexedUpdate, table.rootForTesting().indexes);
        assertNotSame(directoryBeforeIndexedUpdate, table.rootForTesting().directory);
        assertEquals(0L, indexCount(table, "selected"));
        assertEquals(2L, indexCount(table, "moved"));

        long published = table.stateVersionForTesting();
        UpdateResult noOp = table.selectAll().update(() -> {
            GeneratedSelectionEditor editor = table.selectionEditor();
            editor.editInt(2, editor.viewInt(2));
        });
        assertEquals(3L, noOp.matched());
        assertEquals(0L, noOp.changed());
        assertEquals(published, table.stateVersionForTesting());
    }

    @Test
    void selectionUpdateFailureAndResourceRejectionPublishNothing() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        Object first = new Object();
        Object second = new Object();
        add(table, 1L, "selected", 10, first);
        add(table, 2L, "selected", 20, second);

        Object root = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        final int[] editorCalls = new int[1];
        SomaOperationException failed = assertThrows(
                SomaOperationException.class,
                () -> table.selectAll().update(() -> {
                    GeneratedSelectionEditor editor = table.selectionEditor();
                    editor.editInt(2, editor.viewInt(2) + 1);
                    if (++editorCalls[0] == 2) {
                        throw new IllegalStateException("application failure");
                    }
                }));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, failed.code());
        assertEquals(io.github.somaruntime.soma.SomaOperation.UPDATE, failed.operation());
        assertSame(root, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertRow(table, 1L, "selected", 10, first);
        assertRow(table, 2L, "selected", 20, second);
        assertEquals(0L, memory.temporaryBytes());

        final int[] predicateCalls = new int[1];
        final int[] rejectedEditorCalls = new int[1];
        GeneratedPipeline rejectedPipeline = table.selectAll().filter(() -> {
            predicateCalls[0]++;
            return true;
        });
        try (GlobalMemoryManager.TemporaryLease pressure = memory.leaseTemporary(
                memory.budgetBytes() - memory.retainedBytes(),
                io.github.somaruntime.soma.SomaOperation.UPDATE,
                new Object())) {
            SomaOperationException rejected = assertThrows(
                    SomaOperationException.class,
                    () -> rejectedPipeline.update(() -> rejectedEditorCalls[0]++));
            assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, rejected.code());
            assertEquals(0, predicateCalls[0]);
            assertEquals(0, rejectedEditorCalls[0]);
        }
        assertSame(root, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void selectionInPlaceUpdateFaultsBeforePayloadCommit() {
        SwitchableFault fault = new SwitchableFault();
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        SomaCompression.OFF),
                testLayout(), 4, fault);
        Object first = new Object();
        Object second = new Object();
        add(table, 1L, "selected", 10, first);
        add(table, 2L, "selected", 20, second);

        for (MutationFaultPoint point : new MutationFaultPoint[] {
                MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH,
                MutationFaultPoint.BEFORE_FINAL_COMMIT}) {
            TableStateRoot root = table.rootForTesting();
            TableChunk chunk = root.directory.chunk(0);
            fault.point.set(point);
            assertThrows(SomaOperationException.class, () ->
                    table.selectAll().update(() -> {
                        GeneratedSelectionEditor editor = table.selectionEditor();
                        editor.editInt(2, editor.viewInt(2) + 5);
                    }));
            assertSame(root, table.rootForTesting());
            assertSame(chunk, table.rootForTesting().directory.chunk(0));
            assertRow(table, 1L, "selected", 10, first);
            assertRow(table, 2L, "selected", 20, second);
        }
        fault.point.set(null);
    }

    @Test
    void selectionRemoveUsesDeterministicDenseCompactionAndRebuildsSidecars() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        Object[] references = new Object[5];
        for (int index = 0; index < references.length; index++) {
            references[index] = new Object();
            add(table, index + 1L, "bucket-" + ((index + 1) & 1), index + 1,
                    references[index]);
        }

        int capacity = table.capacity();
        long version = table.stateVersionForTesting();
        TableChunkDirectory directory = table.rootForTesting().directory;
        TableChunk firstChunk = directory.chunk(0);
        TableChunk tailChunk = directory.chunk(1);
        RemoveResult result = table.selectAll()
                .filter(() -> (table.queryCursor().viewInt(2) & 1) == 0)
                .remove();

        assertEquals(2L, result.removed());
        assertEquals(3L, table.size());
        assertEquals(capacity, table.capacity());
        assertEquals(version + 1L, table.stateVersionForTesting());
        assertRow(table, 1L, "bucket-1", 1, references[0]);
        assertRow(table, 5L, "bucket-1", 5, references[4]);
        assertRow(table, 3L, "bucket-1", 3, references[2]);
        assertMissing(table, 2L);
        assertMissing(table, 4L);
        assertEquals(3L, indexCount(table, "bucket-1"));
        assertEquals(0L, indexCount(table, "bucket-0"));
        assertSame(directory, table.rootForTesting().directory);
        assertSame(firstChunk, table.rootForTesting().directory.chunk(0));
        assertSame(tailChunk, table.rootForTesting().directory.chunk(1));
        PlainChunk clearedTail = (PlainChunk) table.rootForTesting()
                .directory.chunk(0);
        assertNull(clearedTail.references(testLayout().leafSlot(1))[3]);
        assertNull(clearedTail.references(testLayout().leafSlot(3))[3]);
        PlainChunk clearedLastChunk = (PlainChunk) table.rootForTesting()
                .directory.chunk(1);
        assertNull(clearedLastChunk.references(testLayout().leafSlot(1))[0]);
        assertNull(clearedLastChunk.references(testLayout().leafSlot(3))[0]);
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
    }

    @Test
    void encodedSelectionMutationKeepsCandidateFallbackAndCanonicalState() {
        GlobalMemoryManager memory = new GlobalMemoryManager(128L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4096, MutationFaultInjector.NONE);
        Object shared = new Object();
        for (long key = 1L; key <= 4096L; key++) {
            add(table, key, "repeated", 7, shared);
        }
        assertTrue(table.metadata().encoded());
        TableChunkDirectory encodedDirectory = table.rootForTesting().directory;

        UpdateResult update = table.selectAll()
                .filter(() -> table.queryCursor().viewLong(0) <= 64L)
                .update(() -> table.selectionEditor().editInt(2, 9));
        assertEquals(64L, update.matched());
        assertEquals(64L, update.changed());
        assertNotSame(encodedDirectory, table.rootForTesting().directory);
        assertEquals(4096L * 7L + 64L * 2L, table.fieldSource(2)
                .primitiveInt(() -> table.queryCursor().viewInt(2))
                .sumIntegral());

        RemoveResult remove = table.selectAll()
                .filter(() -> table.queryCursor().viewLong(0) <= 64L)
                .remove();
        assertEquals(64L, remove.removed());
        assertEquals(4032L, table.size());
        assertMissing(table, 1L);
        assertEquals(4032L, indexCount(table, "repeated"));
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
    }

    @Test
    void selectionInPlaceRemoveFaultsBeforePayloadCommit() {
        SwitchableFault fault = new SwitchableFault();
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        SomaCompression.OFF),
                testLayout(), 4, fault);
        Object first = new Object();
        Object second = new Object();
        add(table, 1L, "one", 1, first);
        add(table, 2L, "two", 2, second);

        for (MutationFaultPoint point : new MutationFaultPoint[] {
                MutationFaultPoint.BEFORE_KEY_REBUILD,
                MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING,
                MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH,
                MutationFaultPoint.BEFORE_FINAL_COMMIT}) {
            TableStateRoot root = table.rootForTesting();
            TableChunk chunk = root.directory.chunk(0);
            fault.point.set(point);
            assertThrows(SomaOperationException.class, () ->
                    table.selectAll()
                            .filter(() -> table.queryCursor().viewLong(0) == 1L)
                            .remove());
            assertSame(root, table.rootForTesting());
            assertSame(chunk, table.rootForTesting().directory.chunk(0));
            assertRow(table, 1L, "one", 1, first);
            assertRow(table, 2L, "two", 2, second);
        }
        fault.point.set(null);
    }

    @Test
    void collectedExplicitGroupReleasesItsRetainedAccounting() throws Exception {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        WeakReference<GeneratedGroup> group = createCollectableGroup(memory);
        assertTrue(memory.retainedBytes() > 0L);

        for (int attempt = 0; attempt < 100 && group.get() != null; attempt++) {
            System.gc();
            System.runFinalization();
            memory.drainCollectedGroups();
            Thread.sleep(5L);
        }

        assertNull(group.get());
        assertEquals(0L, memory.retainedBytes());
    }

    @Test
    void exactStorageKeyAndMultipleIndexPathIsPayloadBacked() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        Object firstObject = new Object();
        Object secondObject = new Object();

        add(table, 0L, new String("FB"), 10, firstObject);
        add(table, 1L, new String("Ea"), 20, secondObject);
        add(table, 2L, null, 30, null);

        assertEquals(3L, table.size());
        assertEquals(3L, table.count());
        assertRow(table, 0L, "FB", 10, firstObject);
        assertRow(table, 1L, "Ea", 20, secondObject);
        assertEquals(1L, indexCount(table, "FB"));
        assertEquals(1L, indexCount(table, "Ea"));
        assertEquals(1L, indexCount(table, null));

        SomaOperationException duplicate = assertThrows(
                SomaOperationException.class,
                () -> add(table, 0L, "different", 99, new Object()));
        assertEquals(SomaFailureCode.DUPLICATE_KEY, duplicate.code());
        assertEquals(3L, table.size());
        Object fourthObject = new Object();
        add(table, 3L, "three", 40, fourthObject);
        assertRow(table, 3L, "three", 40, fourthObject);

        UpdateResult changed = update(table, 1L, null, 25, secondObject);
        assertEquals(1L, changed.matched());
        assertEquals(1L, changed.changed());
        assertEquals(0L, indexCount(table, "Ea"));
        assertEquals(2L, indexCount(table, null));
        assertRow(table, 1L, null, 25, secondObject);

        long version = table.stateVersionForTesting();
        UpdateResult noChange = update(table, 1L, null, 25, secondObject);
        assertEquals(1L, noChange.matched());
        assertEquals(0L, noChange.changed());
        assertEquals(version, table.stateVersionForTesting());
    }

    @Test
    void pointRemoveTailFillsAndClearsAllReferenceSlots() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        add(table, 1L, "same", 10, a);
        add(table, 2L, "same", 20, b);
        add(table, 3L, "same", 30, c);

        RemoveResult result = remove(table, 2L);
        assertEquals(1L, result.removed());
        assertEquals(2L, table.size());
        assertRow(table, 1L, "same", 10, a);
        assertRow(table, 3L, "same", 30, c);
        assertMissing(table, 2L);
        assertEquals(2L, indexCount(table, "same"));

        TableStateRoot root = table.rootForTesting();
        PlainChunk chunk = root.directory.plainChunk(0);
        int nameSlot = testLayout().leafSlot(1);
        int objectSlot = testLayout().leafSlot(3);
        assertNull(chunk.references(nameSlot)[2]);
        assertNull(chunk.references(objectSlot)[2]);

        IdentityHashIndex index = root.indexes[0];
        GeneratedProbe probe = table.newProbe(1);
        probe.putReference(1, "same");
        probe.seal();
        IdentityHashIndex.Cursor cursor = new IdentityHashIndex.Cursor();
        int first = index.first(root.directory, probe, cursor);
        assertEquals(0L, first);
        assertEquals(1L, index.next(cursor));
        assertEquals(-1L, index.next(cursor));

        assertEquals(0L, remove(table, 2L).removed());
    }

    @Test
    void indexUsesInlineSingletonAndOneOrderedArrayForHotBucket() {
        GeneratedTable singleton = table(64L << 20, MutationFaultInjector.NONE);
        for (int locator = 0; locator < 128; locator++) {
            add(singleton, locator + 1L, "value-" + locator, locator, new Object());
        }
        IdentityHashIndex singletonIndex = singleton.rootForTesting().indexes[0];
        singletonIndex.validateForTesting(
                singleton.rootForTesting().directory, singleton.size());
        assertEquals(128, singletonIndex.singletonBucketCountForTesting());
        assertEquals(0, singletonIndex.multiBucketCountForTesting());

        GeneratedTable hotspot = table(64L << 20, MutationFaultInjector.NONE);
        for (int locator = 0; locator < 128; locator++) {
            add(hotspot, locator + 1L, "hot", locator, new Object());
        }
        IdentityHashIndex hotspotIndex = hotspot.rootForTesting().indexes[0];
        hotspotIndex.validateForTesting(hotspot.rootForTesting().directory, hotspot.size());
        assertEquals(0, hotspotIndex.singletonBucketCountForTesting());
        assertEquals(1, hotspotIndex.multiBucketCountForTesting());
        assertEquals(128, hotspotIndex.maxBucketSizeForTesting());

        for (long key = 1L; key < 128L; key++) {
            assertEquals(1, remove(hotspot, key).removed());
        }
        hotspotIndex = hotspot.rootForTesting().indexes[0];
        hotspotIndex.validateForTesting(hotspot.rootForTesting().directory, hotspot.size());
        assertEquals(1, hotspotIndex.singletonBucketCountForTesting());
        assertEquals(0, hotspotIndex.multiBucketCountForTesting());
        assertEquals(1, hotspotIndex.maxBucketSizeForTesting());
    }

    @Test
    void tinyChunksGrowAcrossBoundariesAndRemoveKeepsCapacityMonotonic() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        Object[] references = new Object[10];
        for (int value = 0; value < references.length; value++) {
            references[value] = new Object();
            add(table, value, "bucket-" + (value & 1), value, references[value]);
        }

        assertEquals(10L, table.size());
        assertEquals(12L, table.capacity());
        assertEquals(3L, table.rootForTesting().directory.chunkCount());
        assertRow(table, 4L, "bucket-0", 4, references[4]);
        assertRow(table, 9L, "bucket-1", 9, references[9]);

        assertEquals(1L, remove(table, 1L).removed());
        assertEquals(9L, table.size());
        assertEquals(12L, table.capacity());
        assertMissing(table, 1L);
        assertRow(table, 9L, "bucket-1", 9, references[9]);
        assertEquals(5L, indexCount(table, "bucket-0"));
        assertEquals(4L, indexCount(table, "bucket-1"));

        TableStateRoot root = table.rootForTesting();
        PlainChunk formerTail = root.directory.plainChunk(2);
        assertNull(formerTail.references(testLayout().leafSlot(1))[1]);
        assertNull(formerTail.references(testLayout().leafSlot(3))[1]);
    }

    @Test
    void pagedDirectoryCrossesLeafBoundaryWithKeyIndexAndCompactionIntact() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        int rows = 257 * 4;
        table.reserve(rows);
        Object beforeBoundaryReference = null;
        Object atBoundaryReference = null;
        Object tailReference = null;
        for (int value = 0; value < rows; value++) {
            Object reference = new Object();
            if (value == 1023L) beforeBoundaryReference = reference;
            if (value == 1024L) atBoundaryReference = reference;
            if (value == rows - 1L) tailReference = reference;
            add(table, value, "bucket-" + (value & 1L), (int) value, reference);
        }

        assertEquals(rows, table.size());
        assertEquals(257L, table.rootForTesting().directory.chunkCount());
        assertRow(table, 1023L, "bucket-1", 1023, beforeBoundaryReference);
        assertRow(table, 1024L, "bucket-0", 1024, atBoundaryReference);
        assertEquals(514L, indexCount(table, "bucket-0"));
        assertEquals(514L, indexCount(table, "bucket-1"));

        assertEquals(1L, remove(table, 1021L).removed());
        assertMissing(table, 1021L);
        assertRow(table, rows - 1L, "bucket-1", (int) (rows - 1L), tailReference);
        assertEquals(514L, indexCount(table, "bucket-0"));
        assertEquals(513L, indexCount(table, "bucket-1"));

        PlainChunk formerTail = table.rootForTesting().directory.plainChunk(256);
        assertNull(formerTail.references(testLayout().leafSlot(1))[3]);
        assertNull(formerTail.references(testLayout().leafSlot(3))[3]);
    }

    @Test
    void canonicalFloatingEqualityAndReferenceIdentityDriveNoOpDetection() {
        GeneratedTable table = floatingTable();
        Object reference = new Object();
        addFloating(table, 7L, Float.intBitsToFloat(0x7f800001), -0.0d, reference);

        GeneratedProbe nan = table.newProbe(1);
        nan.putFloat(1, Float.intBitsToFloat(0x7fc00001));
        assertEquals(1L, table.filter(table.eq(nan.seal())).count());

        GeneratedProbe positiveZero = table.newProbe(2);
        positiveZero.putDouble(2, +0.0d);
        assertEquals(0L, table.filter(table.eq(positiveZero.seal())).count());

        long version = table.stateVersionForTesting();
        try (GeneratedRow row = table.beginUpdate()) {
            row.putLong(0, 7L);
            assertTrue(row.locateForUpdate());
            row.beginEditorCallback();
            row.editFloat(1, Float.NaN);
            row.editDouble(2, -0.0d);
            row.editReference(3, reference);
            row.endEditorCallback();
            UpdateResult result = row.finishUpdate();
            assertEquals(0L, result.changed());
        }
        assertEquals(version, table.stateVersionForTesting());

        try (GeneratedRow row = table.beginUpdate()) {
            row.putLong(0, 7L);
            assertTrue(row.locateForUpdate());
            row.beginEditorCallback();
            row.editReference(3, new Object());
            row.endEditorCallback();
            assertEquals(1L, row.finishUpdate().changed());
        }
    }

    @Test
    void logicalRowPlanBindsAtTerminalAndReferenceMatchesOptimized() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "odd", 10, new Object());

        GeneratedProbe odd = table.newProbe(1);
        odd.putReference(1, "odd");
        GeneratedProbe minimum = table.newProbe(2);
        minimum.putInt(2, 10);

        GeneratedPipeline optimized = table.selectAll()
                .filter(table.eq(odd.seal()))
                .filter(table.ge(minimum.seal()));
        GeneratedPipeline reference = table.selectAll()
                .filter(table.eq(odd))
                .filter(table.ge(minimum));

        add(table, 2L, "odd", 20, new Object());

        assertEquals(2L, optimized.count());
        assertEquals(2L, reference.referenceCountForTesting());
    }

    @Test
    void canonicalS1CountBindsTypedLiteralAndChoosesExactAccessPaths() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "odd", 10, new Object());
        add(table, 2L, "even", 20, new Object());
        add(table, 3L, "odd", 30, new Object());

        GeneratedProbe indexValue = table.newProbe(1);
        indexValue.putReference(1, "odd");
        PredicateIr indexPredicate = table.requireOwnedExpression(
                table.eq(indexValue.seal()));
        LogicalRowPlan indexLogical = LogicalRowPlan.tableScan(table)
                .typedFilter(indexPredicate);
        CanonicalRowOperation indexCanonical =
                CanonicalRowLowering.count(table, indexLogical);
        assertTrue(indexCanonical != null);
        assertSame(table.logicalIdentity(), indexCanonical.tableIdentity);
        assertTrue(indexPredicate.lower instanceof TypedLiteral);
        assertEquals(1, indexPredicate.lower.leafCountForTesting());
        assertEquals(2L, CanonicalQueryOperation.referenceCountForTesting(
                CanonicalRowRuntimeSource.table(table), indexCanonical));
        assertEquals(2L, CanonicalQueryOperation.optimizedCount(
                CanonicalRowRuntimeSource.table(table), indexCanonical));

        BoundCanonicalRowOperation indexBound = new BoundCanonicalRowOperation(
                indexCanonical,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        CanonicalRowPhysicalPlan indexPhysical = CanonicalRowPlanner.plan(
                CanonicalRowPlanner.normalize(indexBound));
        assertEquals(
                CanonicalRowPhysicalPlan.AccessPath.INDEX_LOOKUP,
                indexPhysical.accessPath);

        GeneratedProbe keyValue = table.newProbe(0);
        keyValue.putLong(0, 2L);
        CanonicalRowOperation keyCanonical = CanonicalRowLowering.count(
                table,
                LogicalRowPlan.tableScan(table).typedFilter(
                        table.requireOwnedExpression(table.eq(keyValue.seal()))));
        BoundCanonicalRowOperation keyBound = new BoundCanonicalRowOperation(
                keyCanonical,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        assertEquals(
                CanonicalRowPhysicalPlan.AccessPath.KEY_LOOKUP,
                CanonicalRowPlanner.plan(
                        CanonicalRowPlanner.normalize(keyBound)).accessPath);
        assertEquals(1L, CanonicalQueryOperation.optimizedCount(
                CanonicalRowRuntimeSource.table(table), keyCanonical));

        GeneratedProbe singleIn = table.newProbe(1);
        singleIn.putReference(1, "odd");
        CanonicalRowOperation inCanonical = CanonicalRowLowering.count(
                table,
                LogicalRowPlan.tableScan(table).typedFilter(
                        table.requireOwnedExpression(table.in(
                                new GeneratedProbe[] {singleIn.seal()}))));
        BoundCanonicalRowOperation inBound = new BoundCanonicalRowOperation(
                inCanonical,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        assertEquals(
                CanonicalRowPhysicalPlan.AccessPath.INDEX_LOOKUP,
                CanonicalRowPlanner.plan(
                        CanonicalRowPlanner.normalize(inBound)).accessPath);
        assertEquals(2L, CanonicalQueryOperation.optimizedCount(
                CanonicalRowRuntimeSource.table(table), inCanonical));

        CanonicalRowOperation impossibleNull = CanonicalRowLowering.count(
                table,
                LogicalRowPlan.tableScan(table).typedFilter(
                        table.requireOwnedExpression(table.isNull(0))));
        BoundCanonicalRowOperation nullBound = new BoundCanonicalRowOperation(
                impossibleNull,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        PredicateIr normalizedNull = CanonicalRowPlanner
                .normalize(nullBound).filters.get(0);
        assertEquals(PredicateIr.Kind.CONSTANT, normalizedNull.kind);
        assertFalse(normalizedNull.constant);
        assertEquals(0L, CanonicalQueryOperation.optimizedCount(
                CanonicalRowRuntimeSource.table(table), impossibleNull));
    }

    @Test
    void canonicalS1IndexSourceReferenceIgnoresPhysicalSidecarOrder() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 12L; key++) {
            add(table, key, key % 3L == 0L ? "hit" : "miss",
                    (int) key, new Object());
        }
        GeneratedProbe source = table.newProbe(1);
        source.putReference(1, "hit");
        LogicalRowPlan logical = LogicalRowPlan.indexSelection(
                table, 0, source.seal().snapshot(table, 1));
        CanonicalRowOperation canonical = CanonicalRowLowering.count(table, logical);
        assertTrue(canonical != null);
        assertEquals(4L, CanonicalQueryOperation.referenceCountForTesting(
                CanonicalRowRuntimeSource.table(table), canonical));
        assertEquals(4L, CanonicalQueryOperation.optimizedCount(
                CanonicalRowRuntimeSource.table(table), canonical));

        BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                canonical,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        assertEquals(
                CanonicalRowPhysicalPlan.AccessPath.INDEX_SELECTION,
                CanonicalRowPlanner.plan(
                        CanonicalRowPlanner.normalize(bound)).accessPath);
    }

    @Test
    void indexSourceDifferentialPreservesDuplicateLocatorOrderMembership() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 12L; key++) {
            add(table, key, key % 3L == 0L ? "hit" : "miss", (int) key, new Object());
        }
        GeneratedProbe indexProbe = table.newProbe(1);
        indexProbe.putReference(1, "hit");
        GeneratedProbe lower = table.newProbe(2);
        lower.putInt(2, 3);

        LogicalRowPlan optimized = LogicalRowPlan.indexSelection(
                table, 0, indexProbe.seal().snapshot(table, 1)).typedFilter(
                table.requireOwnedExpression(table.ge(lower.seal())));
        GeneratedProbe referenceProbe = table.newProbe(1);
        referenceProbe.putReference(1, "hit");
        LogicalRowPlan reference = LogicalRowPlan.indexSelection(
                table, 0, referenceProbe.seal().snapshot(table, 1)).typedFilter(
                table.requireOwnedExpression(table.ge(lower)));

        assertEquals(3L, QueryOperation.optimizedCount(optimized));
        assertEquals(3L, QueryOperation.referenceCountForTesting(reference));

        remove(table, 3L);
        update(table, 1L, "hit", 1, new Object());
        GeneratedProbe rebuiltProbe = table.newProbe(1);
        rebuiltProbe.putReference(1, "hit");
        LogicalRowPlan rebuilt = LogicalRowPlan.indexSelection(
                table, 0, rebuiltProbe.seal().snapshot(table, 1));
        assertTrue(Arrays.equals(
                new long[] {0L, 1L, 6L, 9L},
                QueryOperation.optimizedLocatorsForTesting(rebuilt)));
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(rebuilt),
                QueryOperation.optimizedLocatorsForTesting(rebuilt)));
    }

    @Test
    void randomizedPointMutationMaintainsKeyIndexesAndCanonicalOrderIncrementally() {
        GlobalMemoryManager memory = new GlobalMemoryManager(128L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory),
                dualIndexLayout(), 4, MutationFaultInjector.NONE);
        Map<Long, ExpectedRow> expected = new LinkedHashMap<Long, ExpectedRow>();
        long nextKey = 1L;
        for (; nextKey <= 96L; nextKey++) {
            ExpectedRow row = new ExpectedRow(
                    "bucket-" + (nextKey % 7L),
                    (int) (nextKey % 11L),
                    new Object());
            add(table, nextKey, row.name, row.value, row.reference);
            expected.put(nextKey, row);
        }

        Random random = new Random(0x5A17C0DEL);
        for (int step = 0; step < 600; step++) {
            int operation = random.nextInt(3);
            if (operation == 0 || expected.size() < 24) {
                long key = nextKey++;
                ExpectedRow row = new ExpectedRow(
                        "bucket-" + random.nextInt(9),
                        random.nextInt(13),
                        new Object());
                add(table, key, row.name, row.value, row.reference);
                expected.put(key, row);
            } else {
                long key = keyAt(expected, random.nextInt(expected.size()));
                if (operation == 1) {
                    ExpectedRow row = new ExpectedRow(
                            "bucket-" + random.nextInt(9),
                            random.nextInt(13),
                            new Object());
                    assertEquals(1L, update(
                            table, key, row.name, row.value, row.reference).changed());
                    expected.put(key, row);
                } else {
                    assertEquals(1L, remove(table, key).removed());
                    expected.remove(key);
                }
            }
            if ((step & 31) == 31) assertExpectedState(table, expected, memory);
        }
        assertExpectedState(table, expected, memory);
    }

    @Test
    void pointMutationUsesShardLocalRehashAndReusesTombstones() {
        GeneratedTableLayout layout = dualIndexLayout();
        int[] values = sameShardIntValues(layout, 28);
        int sourceValue = intValueOutsideShard(layout, values[0]);
        OccurrenceFault fault = new OccurrenceFault();
        GlobalMemoryManager memory = new GlobalMemoryManager(128L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), layout, 4, fault);
        Map<Long, ExpectedRow> expected = new LinkedHashMap<Long, ExpectedRow>();

        for (int index = 0; index < 10; index++) {
            long key = index + 1L;
            ExpectedRow row = new ExpectedRow(
                    "shared", values[index], new Object());
            add(table, key, row.name, row.value, row.reference);
            expected.put(Long.valueOf(key), row);
        }
        ExpectedRow source = new ExpectedRow("shared", sourceValue, new Object());
        add(table, 100L, source.name, source.value, source.reference);
        expected.put(Long.valueOf(100L), source);

        fault.arm(MutationFaultPoint.BEFORE_INDEX_REBUILD, 1);
        ExpectedRow moved = new ExpectedRow("shared", values[10], new Object());
        assertEquals(1L, update(
                table, 100L, moved.name, moved.value, moved.reference).changed());
        expected.put(Long.valueOf(100L), moved);
        assertEquals(0, fault.seen());

        for (long key = 1L; key <= 8L; key++) {
            assertEquals(1L, remove(table, key).removed());
            expected.remove(Long.valueOf(key));
        }
        for (int index = 11; index < values.length; index++) {
            long key = 1000L + index;
            ExpectedRow row = new ExpectedRow(
                    "shared", values[index], new Object());
            add(table, key, row.name, row.value, row.reference);
            expected.put(Long.valueOf(key), row);
        }

        assertExpectedState(table, expected, memory);
        assertEquals(0, fault.seen());
    }

    @Test
    void queryViewCursorRejectsEveryOutOfScopeAccess() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedQueryCursor cursor = table.queryCursor();
        SomaOperationException before = assertThrows(
                SomaOperationException.class, () -> cursor.viewLong(0));
        assertEquals(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, before.code());

        try (GroupOperationGuard.Lease operation = table.acquireQuery()) {
            cursor.begin(
                    table.currentRoot(),
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    operation.provenance());
            SomaOperationException inactive = assertThrows(
                    SomaOperationException.class, () -> cursor.viewLong(0));
            assertEquals(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, inactive.code());
            cursor.end();
        }
    }

    @Test
    void callbackBarrierOrderSliceAndShortCircuitHaveCanonicalSemantics() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 8L; key++) {
            add(table, key, key % 2L == 0L ? "even" : "odd", (int) (7L - key), new Object());
        }

        final int[] filterCalls = new int[1];
        GeneratedCallbacks.RowPredicate evenKey = () -> {
            filterCalls[0]++;
            return (table.queryCursor().viewLong(0) & 1L) == 0L;
        };
        LogicalRowPlan plan = LogicalRowPlan.tableScan(table)
                .callbackFilter(evenKey)
                .sortedBy((GeneratedOrder<?>) table.<Object>asc(2))
                .skip(1L)
                .limit(2L);

        long[] reference = QueryOperation.referenceLocatorsForTesting(plan);
        assertEquals(8, filterCalls[0]);
        filterCalls[0] = 0;
        long[] optimized = QueryOperation.optimizedLocatorsForTesting(plan);
        assertEquals(8, filterCalls[0]);
        assertTrue(Arrays.equals(reference, optimized));
        assertTrue(Arrays.equals(new long[] {4L, 2L}, optimized));

        final int[] matchCalls = new int[1];
        assertTrue(QueryOperation.anyMatch(
                LogicalRowPlan.tableScan(table),
                () -> {
                    matchCalls[0]++;
                    return table.queryCursor().viewLong(0) == 2L;
                }));
        assertEquals(3, matchCalls[0]);
    }

    @Test
    void callbackComparatorIsStableAndUsesTwoBorrowedViews() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 6L; key++) {
            add(table, key, "same", (int) (key % 2L), new Object());
        }
        List<String> optimizedTrace = new ArrayList<String>();
        GeneratedCallbacks.RowComparator optimizedComparator = () -> {
            int left = table.queryCursor().viewInt(2);
            int right = table.secondaryQueryCursor().viewInt(2);
            optimizedTrace.add(left + ":" + right);
            return Integer.compare(left, right);
        };
        List<String> referenceTrace = new ArrayList<String>();
        GeneratedCallbacks.RowComparator referenceComparator = () -> {
            int left = table.queryCursor().viewInt(2);
            int right = table.secondaryQueryCursor().viewInt(2);
            referenceTrace.add(left + ":" + right);
            return Integer.compare(left, right);
        };
        LogicalRowPlan optimized = LogicalRowPlan.tableScan(table)
                .sorted(optimizedComparator);
        LogicalRowPlan reference = LogicalRowPlan.tableScan(table)
                .sorted(referenceComparator);

        long[] actual = QueryOperation.optimizedLocatorsForTesting(optimized);
        long[] expected = QueryOperation.referenceLocatorsForTesting(reference);
        assertTrue(Arrays.equals(
                new long[] {0L, 2L, 4L, 1L, 3L, 5L}, actual));
        assertTrue(Arrays.equals(expected, actual));
        assertEquals(referenceTrace, optimizedTrace);
    }

    @Test
    void canonicalCallbackSortHasNLogNComparisonBound() {
        GeneratedTable table = table(512L << 20, MutationFaultInjector.NONE);
        int size = 4096;
        for (long key = 0L; key < size; key++) {
            add(table, key, "same", size - (int) key, new Object());
        }
        final long[] comparisons = new long[1];
        LogicalRowPlan plan = LogicalRowPlan.tableScan(table).sorted(() -> {
            comparisons[0]++;
            return Integer.compare(
                    table.queryCursor().viewInt(2),
                    table.secondaryQueryCursor().viewInt(2));
        });

        assertEquals(size, QueryOperation.optimizedCount(plan));
        assertTrue(comparisons[0] < 100_000L,
                "comparison count=" + comparisons[0]);
    }

    @Test
    void optimizerSubstitutesExactIndexAndRetainsResidual() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 12L; key++) {
            add(table, key, key % 3L == 0L ? "hit" : "miss", (int) key, new Object());
        }
        GeneratedProbe hit = table.newProbe(1);
        hit.putReference(1, "hit");
        GeneratedProbe minimum = table.newProbe(2);
        minimum.putInt(2, 5);
        LogicalRowPlan plan = LogicalRowPlan.tableScan(table)
                .typedFilter(table.requireOwnedExpression(table.eq(hit.seal())))
                .typedFilter(table.requireOwnedExpression(table.ge(minimum.seal())));

        String explain = QueryOperation.explain(plan);
        assertTrue(explain.contains("physicalSource=INDEX_LOOKUP"));
        assertTrue(explain.contains("indexSubstitution=true"));
        assertTrue(explain.contains("residualTyped=1"));
        assertTrue(explain.contains("callbackBarrier=false"));
        assertEquals(2L, QueryOperation.optimizedCount(plan));
        assertEquals(2L, QueryOperation.referenceCountForTesting(plan));
    }

    @Test
    void optimizerSubstitutesKeyAndNullIndexWithoutChangingOrder() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 10L, null, 1, new Object());
        add(table, 20L, "value", 2, new Object());
        add(table, 30L, null, 3, new Object());

        GeneratedProbe key = table.newProbe(0);
        key.putLong(0, 20L);
        LogicalRowPlan keyPlan = LogicalRowPlan.tableScan(table)
                .typedFilter(table.requireOwnedExpression(table.eq(key.seal())));
        assertTrue(QueryOperation.explain(keyPlan)
                .contains("physicalSource=KEY_LOOKUP"));
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(keyPlan),
                QueryOperation.optimizedLocatorsForTesting(keyPlan)));

        LogicalRowPlan nullPlan = LogicalRowPlan.tableScan(table)
                .typedFilter(table.requireOwnedExpression(table.isNull(1)));
        assertTrue(QueryOperation.explain(nullPlan)
                .contains("physicalSource=INDEX_LOOKUP"));
        assertTrue(Arrays.equals(new long[] {0L, 2L},
                QueryOperation.optimizedLocatorsForTesting(nullPlan)));
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(nullPlan),
                QueryOperation.optimizedLocatorsForTesting(nullPlan)));

        LogicalRowPlan barrier = LogicalRowPlan.tableScan(table)
                .callbackFilter(() -> true)
                .typedFilter(table.requireOwnedExpression(table.isNull(1)));
        String barrierExplain = QueryOperation.explain(barrier);
        assertTrue(barrierExplain.contains("physicalSource=TABLE_SCAN"));
        assertTrue(barrierExplain.contains("callbackBarrier=true"));
    }

    @Test
    void topBoundariesTiesAndCallbackFailureDifferentialAreEquivalent() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 6L; key++) {
            add(table, key, "same", (int) (key % 2L), new Object());
        }
        for (long count : new long[] {0L, 2L, 99L}) {
            LogicalRowPlan plan = LogicalRowPlan.tableScan(table)
                    .top(count, (GeneratedOrder<?>) table.<Object>asc(2));
            assertTrue(Arrays.equals(
                    QueryOperation.referenceLocatorsForTesting(plan),
                    QueryOperation.optimizedLocatorsForTesting(plan)));
        }
        assertTrue(Arrays.equals(new long[] {0L, 2L},
                QueryOperation.optimizedLocatorsForTesting(
                        LogicalRowPlan.tableScan(table)
                                .top(2L, (GeneratedOrder<?>) table.<Object>asc(2)))));
        LogicalRowPlan bounded = LogicalRowPlan.tableScan(table)
                .top(2L, (GeneratedOrder<?>) table.<Object>asc(2));
        assertTrue(QueryOperation.explain(bounded).contains("boundedTop=true"));
        LogicalRowPlan parallel = LogicalRowPlan.tableScan(table)
                .top(2L, (GeneratedOrder<?>) table.<Object>asc(2))
                .parallel();
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(parallel),
                QueryOperation.optimizedLocatorsForTesting(parallel)));

        LogicalRowPlan failing = LogicalRowPlan.tableScan(table)
                .callbackFilter(() -> {
                    throw new IllegalStateException("application failure");
                });
        SomaOperationException reference = assertThrows(
                SomaOperationException.class,
                () -> QueryOperation.referenceLocatorsForTesting(failing));
        SomaOperationException optimized = assertThrows(
                SomaOperationException.class,
                () -> QueryOperation.optimizedLocatorsForTesting(failing));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, reference.code());
        assertEquals(reference.code(), optimized.code());
        assertEquals(reference.operation(), optimized.operation());
    }

    @Test
    void boundedTypedTopMatchesStableOracleAcrossThresholdAndParallelPaths() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 257L; key++) {
            add(table, key, "bucket", (int) ((key * 37L) % 31L), new Object());
        }
        GeneratedProbe minimum = table.newProbe(2);
        minimum.putInt(2, 5);
        PredicateIr eligible = table.requireOwnedExpression(table.ge(minimum.seal()));
        for (long count : new long[] {0L, 1L, 2L, 17L, 64L, 65L, 512L}) {
            LogicalRowPlan plan = LogicalRowPlan.tableScan(table)
                    .typedFilter(eligible)
                    .top(count, (GeneratedOrder<?>) table.<Object>asc(2))
                    .skip(1L);
            assertTrue(Arrays.equals(
                    QueryOperation.referenceLocatorsForTesting(plan),
                    QueryOperation.optimizedLocatorsForTesting(plan)),
                    "top count=" + count);
        }

        LogicalRowPlan parallel = LogicalRowPlan.tableScan(table)
                .typedFilter(eligible)
                .top(17L, (GeneratedOrder<?>) table.<Object>desc(2))
                .parallel();
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(parallel),
                QueryOperation.optimizedLocatorsForTesting(parallel)));

        final int[] referenceCalls = new int[1];
        LogicalRowPlan reference = LogicalRowPlan.tableScan(table)
                .callbackFilter(() -> {
                    referenceCalls[0]++;
                    return table.queryCursor().viewLong(0) % 3L != 0L;
                })
                .top(17L, (GeneratedOrder<?>) table.<Object>asc(2));
        final int[] optimizedCalls = new int[1];
        LogicalRowPlan optimized = LogicalRowPlan.tableScan(table)
                .callbackFilter(() -> {
                    optimizedCalls[0]++;
                    return table.queryCursor().viewLong(0) % 3L != 0L;
                })
                .top(17L, (GeneratedOrder<?>) table.<Object>asc(2));
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(reference),
                QueryOperation.optimizedLocatorsForTesting(optimized)));
        assertEquals(257, referenceCalls[0]);
        assertEquals(referenceCalls[0], optimizedCalls[0]);
    }

    @Test
    void inMembershipIsDefensiveDifferentialAndAdmittedBeforeExecution() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4,
                MutationFaultInjector.NONE);
        for (long key = 0L; key < 128L; key++) {
            add(table, key, "bucket", (int) key, new Object());
        }

        GeneratedProbe[] literals = new GeneratedProbe[96];
        for (int index = 0; index < literals.length; index++) {
            GeneratedProbe probe = table.newProbe(2);
            probe.putInt(2, index % 64);
            literals[index] = probe.seal();
        }
        PredicateIr predicate = table.requireOwnedExpression(
                table.in(literals));
        for (int index = 0; index < literals.length; index++) {
            GeneratedProbe replacement = table.newProbe(2);
            replacement.putInt(2, 127);
            literals[index] = replacement.seal();
        }
        LogicalRowPlan plan = LogicalRowPlan.tableScan(table)
                .typedFilter(predicate);

        assertEquals(64L, QueryOperation.optimizedCount(plan));
        assertEquals(64L, QueryOperation.referenceCountForTesting(plan));
        assertTrue(Arrays.equals(
                QueryOperation.referenceLocatorsForTesting(plan),
                QueryOperation.optimizedLocatorsForTesting(plan)));

        String explain = QueryOperation.explain(plan);
        assertTrue(explain.contains("inMembershipLiterals=64"));
        assertTrue(explain.contains("estimatedTemporaryPeakBytes="));
        assertTrue(explain.contains("physicalSegments=1"));
        assertTrue(explain.contains("segmentKernel="));
        assertTrue(explain.contains("morsel="));
        assertTrue(explain.contains("physicalSink="));
        long requiredScratch = 64L * 256L;
        try (GlobalMemoryManager.TemporaryLease pressure = memory.leaseTemporary(
                memory.budgetBytes() - memory.retainedBytes()
                        - (requiredScratch - 1L),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object())) {
            SomaOperationException rejected = assertThrows(
                    SomaOperationException.class,
                    () -> QueryOperation.optimizedCount(plan));
            assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, rejected.code());
        }
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void inConstructionValidatesEveryLiteralAndCheckedArrayBoundary() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        Object root = table.rootIdentityForTesting();
        GeneratedProbe value = table.newProbe(2);
        value.putInt(2, 1);

        SomaOperationException firstNull = assertThrows(
                SomaOperationException.class,
                () -> table.in(new GeneratedProbe[] {null, value.seal()}));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, firstNull.code());
        assertEquals(io.github.somaruntime.soma.SomaOperation.QUERY,
                firstNull.operation());
        assertSame(root, table.rootIdentityForTesting());

        SomaOperationException arrayBoundary = assertThrows(
                SomaOperationException.class,
                () -> table.requireInLiteralCapacity(
                        (long) Integer.MAX_VALUE + 1L));
        assertEquals(
                SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                arrayBoundary.code());
        SomaOperationException arithmetic = assertThrows(
                SomaOperationException.class,
                () -> table.requireInLiteralCapacity(Long.MAX_VALUE));
        assertEquals(SomaFailureCode.ARITHMETIC_OVERFLOW, arithmetic.code());
        assertSame(root, table.rootIdentityForTesting());
    }

    @Test
    void mappedReferenceStagesDifferAgainstIndependentReferenceInterpreter() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 24L; key++) {
            add(table, key, "bucket-" + key % 3L, (int) (key % 7L), new Object());
        }
        GeneratedCallbacks.RowMapper<Integer> mapper =
                () -> table.queryCursor().viewInt(2);
        List<String> optimizedTrace = new ArrayList<String>();
        Comparator<Integer> optimizedDescending = (left, right) -> {
            optimizedTrace.add(left + ":" + right);
            return right.compareTo(left);
        };
        List<String> referenceTrace = new ArrayList<String>();
        Comparator<Integer> referenceDescending = (left, right) -> {
            referenceTrace.add(left + ":" + right);
            return right.compareTo(left);
        };

        MappedPipelineCapture<Integer> optimized = MappedPipelineCapture
                .root(LogicalRowPlan.tableScan(table), mapper)
                .filter(value -> (value & 1) == 0)
                .map(value -> value + 10)
                .distinct()
                .sorted(optimizedDescending)
                .skip(1L)
                .limit(2L);
        MappedPipelineCapture<Integer> reference = MappedPipelineCapture
                .root(LogicalRowPlan.tableScan(table), mapper)
                .filter(value -> (value & 1) == 0)
                .map(value -> value + 10)
                .distinct()
                .sorted(referenceDescending)
                .skip(1L)
                .limit(2L);

        List<Object> actual = MappedQueryOperation.toList(optimized);
        List<Object> expected = ReferenceMappedInterpreter.toListForTesting(reference);
        assertEquals(expected, actual);
        assertEquals(Arrays.<Object>asList(14, 12), actual);
        assertEquals(referenceTrace, optimizedTrace);
    }

    @Test
    void mappedDistinctUsesOneCanonicalHashFailureBoundary() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        ThrowingHash first = new ThrowingHash();
        ThrowingHash second = new ThrowingHash();
        add(table, 1L, "one", 1, first);
        add(table, 2L, "two", 2, second);
        GeneratedCallbacks.RowMapper<Object> mapper =
                () -> table.queryCursor().viewReference(3);

        MappedPipelineCapture<Object> optimized = MappedPipelineCapture
                .root(LogicalRowPlan.tableScan(table), mapper)
                .distinct();
        SomaOperationException optimizedFailure = assertThrows(
                SomaOperationException.class,
                () -> MappedQueryOperation.toList(optimized));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, optimizedFailure.code());

        MappedPipelineCapture<Object> reference = MappedPipelineCapture
                .root(LogicalRowPlan.tableScan(table), mapper)
                .distinct();
        SomaOperationException referenceFailure = assertThrows(
                SomaOperationException.class,
                () -> ReferenceMappedInterpreter.toListForTesting(reference));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, referenceFailure.code());
        assertEquals(2, first.calls + second.calls);
    }

    @Test
    void statelessMappedAndPrimitiveFamiliesConsumeTypedSegmentTopology() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedCallbacks.RowMapper<Integer> mappedRoot =
                () -> Integer.valueOf(table.queryCursor().viewInt(2));
        MappedPipelineCapture<Integer> mappedCapture = MappedPipelineCapture
                .root(LogicalRowPlan.tableScan(table), mappedRoot)
                .filter(value -> value >= 0)
                .map(value -> value + 1);
        CanonicalMappedOperation mapped = CanonicalMappedLowering.operation(
                mappedCapture,
                CanonicalMappedOperation.TerminalKind.COUNT,
                null,
                null);
        BoundCanonicalRowOperation mappedBound = new BoundCanonicalRowOperation(
                mapped.source,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        CanonicalRowPhysicalPlan mappedPlan = CanonicalRowPlanner.plan(
                CanonicalRowPlanner.normalize(mappedBound),
                CanonicalRowPhysicalRequest.mapped(mapped, 0L));
        assertEquals(2, mappedPlan.pipeline.segments.length);
        CanonicalPhysicalSegment mappedSegment =
                mappedPlan.pipeline.terminalSegment();
        assertEquals(CanonicalPhysicalSegment.Shape.MAPPED_REFERENCE,
                mappedSegment.shape);
        assertEquals(CanonicalPhysicalSegment.Kernel.MAPPED_SCALAR,
                mappedSegment.kernel);
        assertTrue(mappedSegment.callbackBarrier);
        assertEquals(0, mappedSegment.fromStage);
        assertEquals(2, mappedSegment.toStageExclusive);
        String mappedExplain = MappedQueryOperation.explain(
                MappedPipelineCapture
                        .root(LogicalRowPlan.tableScan(table), mappedRoot)
                        .filter(value -> value >= 0)
                        .map(value -> value + 1));
        assertTrue(mappedExplain.contains("physicalSegments=2"));
        assertTrue(mappedExplain.contains("segmentShape=MAPPED_REFERENCE"));
        assertTrue(mappedExplain.contains("segmentCallbackBarrier=true"));

        GeneratedCallbacks.RowToIntMapper primitiveRoot =
                () -> table.queryCursor().viewInt(2);
        PrimitivePipelineCapture primitiveCapture = PrimitivePipelineCapture
                .row(LogicalRowPlan.tableScan(table),
                        PrimitiveValueKind.INT, primitiveRoot, true)
                .filter((io.github.somaruntime.soma.SomaIntPredicate)
                        value -> value >= 0)
                .map((io.github.somaruntime.soma.SomaIntUnaryOperator)
                        value -> value + 1);
        CanonicalPrimitiveOperation primitive =
                CanonicalPrimitiveLowering.operation(
                        primitiveCapture,
                        CanonicalPrimitiveOperation.TerminalKind.COUNT,
                        null);
        BoundCanonicalRowOperation primitiveBound =
                new BoundCanonicalRowOperation(
                        primitive.source,
                        table,
                        table.layout(),
                        table.rootForTesting(),
                        io.github.somaruntime.soma.SomaOperation.QUERY,
                        new Object());
        CanonicalRowPhysicalPlan primitivePlan = CanonicalRowPlanner.plan(
                CanonicalRowPlanner.normalize(primitiveBound),
                CanonicalRowPhysicalRequest.primitive(primitive, 0L));
        assertEquals(2, primitivePlan.pipeline.segments.length);
        CanonicalPhysicalSegment primitiveSegment =
                primitivePlan.pipeline.terminalSegment();
        assertEquals(CanonicalPhysicalSegment.Shape.PRIMITIVE,
                primitiveSegment.shape);
        assertEquals(CanonicalPhysicalSegment.Kernel.PRIMITIVE_SCALAR,
                primitiveSegment.kernel);
        assertTrue(primitiveSegment.callbackBarrier);
        assertEquals(0, primitiveSegment.fromStage);
        assertEquals(2, primitiveSegment.toStageExclusive);
        String primitiveExplain = PrimitivePlanOperation.explain(
                PrimitivePipelineCapture
                        .row(LogicalRowPlan.tableScan(table),
                                PrimitiveValueKind.INT, primitiveRoot, true)
                        .filter((io.github.somaruntime.soma.SomaIntPredicate)
                                value -> value >= 0)
                        .map((io.github.somaruntime.soma.SomaIntUnaryOperator)
                                value -> value + 1));
        assertTrue(primitiveExplain.contains("physicalSegments=2"));
        assertTrue(primitiveExplain.contains("segmentShape=PRIMITIVE"));
        assertTrue(primitiveExplain.contains("segmentCallbackBarrier=true"));
    }

    @Test
    void randomizedLogicalPlansMatchOnIndependentImmutableStates() {
        GeneratedTable optimizedTable = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedTable referenceTable = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 128L; key++) {
            String name = key % 5L == 0L ? null : "bucket-" + key % 7L;
            int value = (int) ((key * 37L) % 31L);
            add(optimizedTable, key, name, value, new Object());
            add(referenceTable, key, name, value, new Object());
        }
        Random random = new Random(0x51A3D1FFL);
        for (int trial = 0; trial < 64; trial++) {
            int minimum = random.nextInt(31);
            long skip = random.nextInt(5);
            long limit = random.nextInt(16);
            int modulus = random.nextInt(4) + 2;

            GeneratedProbe optimizedMinimum = optimizedTable.newProbe(2);
            optimizedMinimum.putInt(2, minimum);
            GeneratedProbe referenceMinimum = referenceTable.newProbe(2);
            referenceMinimum.putInt(2, minimum);
            LogicalRowPlan optimized = LogicalRowPlan.tableScan(optimizedTable)
                    .typedFilter(optimizedTable.requireOwnedExpression(
                            optimizedTable.ge(optimizedMinimum.seal())))
                    .callbackFilter(() -> optimizedTable.queryCursor().viewLong(0)
                            % modulus != 0L)
                    .sortedBy((GeneratedOrder<?>) optimizedTable.<Object>asc(2)
                            .then(optimizedTable.<Object>desc(0)))
                    .skip(skip)
                    .limit(limit);
            LogicalRowPlan reference = LogicalRowPlan.tableScan(referenceTable)
                    .typedFilter(referenceTable.requireOwnedExpression(
                            referenceTable.ge(referenceMinimum.seal())))
                    .callbackFilter(() -> referenceTable.queryCursor().viewLong(0)
                            % modulus != 0L)
                    .sortedBy((GeneratedOrder<?>) referenceTable.<Object>asc(2)
                            .then(referenceTable.<Object>desc(0)))
                    .skip(skip)
                    .limit(limit);

            assertTrue(Arrays.equals(
                    QueryOperation.referenceLocatorsForTesting(reference),
                    QueryOperation.optimizedLocatorsForTesting(optimized)),
                    "differential trial=" + trial);
        }
    }

    @Test
    void groupAndRelationOptimizersMatchIndependentReferenceAlgorithms() {
        GeneratedGroup group = testGroup(new GlobalMemoryManager(64L << 20));
        GeneratedTable left = new GeneratedTable(
                group, testLayout(), 4, MutationFaultInjector.NONE);
        GeneratedTable right = new GeneratedTable(
                group, testLayout(), 4, MutationFaultInjector.NONE);
        add(left, 1L, "A", 10, new Object());
        add(left, 2L, "A", 20, new Object());
        add(left, 3L, "B", 30, new Object());
        add(left, 4L, null, 40, new Object());
        add(right, 101L, "A", 100, new Object());
        add(right, 102L, "A", 200, new Object());
        add(right, 103L, "C", 300, new Object());
        add(right, 104L, null, 400, new Object());

        GeneratedCallbacks.RowMapper<Object> key =
                () -> left.queryCursor().viewReference(1);
        @SuppressWarnings("unchecked")
        GroupedLongResult<Object> optimizedGroups =
                (GroupedLongResult<Object>) left.groupBy(
                        1, GeneratedGrouping.KEY_REFERENCE, key).count();
        @SuppressWarnings("unchecked")
        GroupedLongResult<Object> referenceGroups =
                (GroupedLongResult<Object>) left.groupBy(
                        1, GeneratedGrouping.KEY_REFERENCE, key)
                        .countReferenceForTesting();
        assertGroupedEquals(
                optimizedGroups.toArray(), referenceGroups.toArray());

        GeneratedCallbacks.RowMapper<Long> primitiveKey =
                () -> Long.valueOf(left.queryCursor().viewLong(0));
        LongGroupedLongResult optimizedPrimitiveGroups =
                (LongGroupedLongResult) left.groupBy(
                        0, GeneratedGrouping.KEY_LONG, primitiveKey).count();
        LongGroupedLongResult referencePrimitiveGroups =
                (LongGroupedLongResult) left.groupBy(
                        0, GeneratedGrouping.KEY_LONG, primitiveKey)
                        .countReferenceForTesting();
        assertPrimitiveGroupedEquals(
                optimizedPrimitiveGroups.toArray(),
                referencePrimitiveGroups.toArray());

        GeneratedRelation optimized = GeneratedRelation
                .equality(left, right)
                .on(1, 1)
                .kind(GeneratedRelation.FULL);
        GeneratedRelation reference = GeneratedRelation
                .equality(left, right)
                .on(1, 1)
                .kind(GeneratedRelation.FULL);
        assertTrue(Arrays.equals(
                relationLocators(optimized, false),
                relationLocators(reference, true)));

        GeneratedProbe minimum = right.newProbe(2);
        minimum.putInt(2, 200);
        SomaExpression<Object> rightFilter = right.ge(minimum.seal());
        GeneratedRelation optimizedFiltered = GeneratedRelation
                .equality(left, right)
                .on(1, 1)
                .filter(rightFilter);
        GeneratedRelation referenceFiltered = GeneratedRelation
                .equality(left, right)
                .on(1, 1)
                .filter(rightFilter);
        assertTrue(Arrays.equals(
                relationLocators(optimizedFiltered, false),
                relationLocators(referenceFiltered, true)));
    }

    @Test
    void relationLeftFeedsCanonicalMappedPrimitiveAndGroupingOracles() {
        GeneratedGroup group = testGroup(new GlobalMemoryManager(64L << 20));
        GeneratedTable left = new GeneratedTable(
                group, testLayout(), 4, MutationFaultInjector.NONE);
        GeneratedTable right = new GeneratedTable(
                group, testLayout(), 4, MutationFaultInjector.NONE);
        add(left, 1L, "A", 10, new Object());
        add(left, 2L, "A", 20, new Object());
        add(left, 3L, "B", 30, new Object());
        add(right, 101L, "A", 100, new Object());

        GeneratedCallbacks.RowMapper<Integer> mappedRoot =
                () -> Integer.valueOf(left.queryCursor().viewInt(2));
        MappedPipelineCapture<Integer> optimizedMapped = MappedPipelineCapture
                .root(LogicalRowPlan.relationLeft(
                        left, GeneratedRelation.equality(left, right)
                                .on(1, 1).kind(GeneratedRelation.SEMI)),
                        mappedRoot);
        MappedPipelineCapture<Integer> referenceMapped = MappedPipelineCapture
                .root(LogicalRowPlan.relationLeft(
                        left, GeneratedRelation.equality(left, right)
                                .on(1, 1).kind(GeneratedRelation.SEMI)),
                        mappedRoot);
        assertEquals(
                ReferenceMappedInterpreter.toListForTesting(referenceMapped),
                MappedQueryOperation.toList(optimizedMapped));

        GeneratedCallbacks.RowToIntMapper primitiveRoot =
                () -> left.queryCursor().viewInt(2);
        PrimitivePipelineCapture optimizedPrimitive = PrimitivePipelineCapture
                .row(LogicalRowPlan.relationLeft(
                        left, GeneratedRelation.equality(left, right)
                                .on(1, 1).kind(GeneratedRelation.SEMI)),
                        PrimitiveValueKind.INT, primitiveRoot, true);
        PrimitivePipelineCapture referencePrimitive = PrimitivePipelineCapture
                .row(LogicalRowPlan.relationLeft(
                        left, GeneratedRelation.equality(left, right)
                                .on(1, 1).kind(GeneratedRelation.SEMI)),
                        PrimitiveValueKind.INT, primitiveRoot, true);
        long[] referenceValues =
                ReferencePrimitiveInterpreter.valuesForTesting(referencePrimitive);
        int[] optimizedValues =
                PrimitivePlanOperation.toIntArray(optimizedPrimitive);
        assertEquals(referenceValues.length, optimizedValues.length);
        for (int index = 0; index < optimizedValues.length; index++) {
            assertEquals(referenceValues[index], optimizedValues[index]);
        }

        GeneratedCallbacks.RowMapper<Object> groupKey =
                () -> left.queryCursor().viewReference(1);
        @SuppressWarnings("unchecked")
        GroupedLongResult<Object> optimizedGroups =
                (GroupedLongResult<Object>) GeneratedRelation
                        .equality(left, right).on(1, 1)
                        .kind(GeneratedRelation.SEMI).leftPipeline()
                        .groupBy(1, GeneratedGrouping.KEY_REFERENCE, groupKey)
                        .count();
        @SuppressWarnings("unchecked")
        GroupedLongResult<Object> referenceGroups =
                (GroupedLongResult<Object>) GeneratedRelation
                        .equality(left, right).on(1, 1)
                        .kind(GeneratedRelation.SEMI).leftPipeline()
                        .groupBy(1, GeneratedGrouping.KEY_REFERENCE, groupKey)
                        .countReferenceForTesting();
        assertGroupedEquals(
                optimizedGroups.toArray(), referenceGroups.toArray());
    }

    @Test
    void equalityJoinCardinalityUsesKeyUniquenessForResourceAdmission() {
        GeneratedGroup group = testGroup(new GlobalMemoryManager(64L << 20));
        GeneratedTableLayout layout = joinBoundLayout();
        GeneratedTable left = new GeneratedTable(
                group, layout, 4, MutationFaultInjector.NONE);
        GeneratedTable right = new GeneratedTable(
                group, layout, 4, MutationFaultInjector.NONE);

        assertEquals(7L, GeneratedRelation.equality(left, right)
                .on(0, 0).kind(GeneratedRelation.INNER)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(10L, GeneratedRelation.equality(left, right)
                .on(1, 0).kind(GeneratedRelation.INNER)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(7L, GeneratedRelation.equality(left, right)
                .on(0, 1).kind(GeneratedRelation.INNER)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(70L, GeneratedRelation.equality(left, right)
                .on(1, 1).kind(GeneratedRelation.INNER)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(10L, GeneratedRelation.equality(left, right)
                .on(1, 0).kind(GeneratedRelation.LEFT)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(17L, GeneratedRelation.equality(left, right)
                .on(0, 1).kind(GeneratedRelation.LEFT)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(17L, GeneratedRelation.equality(left, right)
                .on(1, 0).kind(GeneratedRelation.FULL)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(87L, GeneratedRelation.equality(left, right)
                .on(1, 1).kind(GeneratedRelation.FULL)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(10L, GeneratedRelation.equality(left, right)
                .on(1, 1).kind(GeneratedRelation.SEMI)
                .outputUpperBoundForTesting(10L, 7L));
        assertEquals(70L, GeneratedRelation.cross(left, right, 70L)
                .outputUpperBoundForTesting(10L, 7L));
    }

    @Test
    void primitivePlansDifferAgainstIndependentBoxedReferenceAlgorithms() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 32L; key++) {
            add(table, key, "bucket", (int) (key % 9L), new Object());
        }
        GeneratedCallbacks.RowToIntMapper root =
                () -> table.queryCursor().viewInt(2);
        PrimitivePipelineCapture optimized = PrimitivePipelineCapture
                .row(LogicalRowPlan.tableScan(table), PrimitiveValueKind.INT, root, true)
                .filter((io.github.somaruntime.soma.SomaIntPredicate) value -> (value & 1) == 0)
                .map((io.github.somaruntime.soma.SomaIntUnaryOperator) value -> value + 10)
                .distinct()
                .sorted()
                .skip(1L)
                .limit(3L);
        PrimitivePipelineCapture reference = PrimitivePipelineCapture
                .row(LogicalRowPlan.tableScan(table), PrimitiveValueKind.INT, root, true)
                .filter((io.github.somaruntime.soma.SomaIntPredicate) value -> (value & 1) == 0)
                .map((io.github.somaruntime.soma.SomaIntUnaryOperator) value -> value + 10)
                .distinct()
                .sorted()
                .skip(1L)
                .limit(3L);

        assertTrue(Arrays.equals(
                ReferencePrimitiveInterpreter.valuesForTesting(reference),
                PrimitivePlanOperation.valuesForTesting(optimized)));
        assertTrue(Arrays.equals(
                new long[] {12L, 14L, 16L},
                PrimitivePlanOperation.valuesForTesting(PrimitivePipelineCapture
                        .row(LogicalRowPlan.tableScan(table), PrimitiveValueKind.INT, root, true)
                        .filter((io.github.somaruntime.soma.SomaIntPredicate) value -> (value & 1) == 0)
                        .map((io.github.somaruntime.soma.SomaIntUnaryOperator) value -> value + 10)
                        .distinct().sorted().skip(1L).limit(3L))));
    }

    @Test
    void vectorChunkKernelsMatchReferenceAcrossPlainEncodedAndTypedFilters() {
        for (SomaCompression compression : new SomaCompression[] {
                SomaCompression.OFF, SomaCompression.AUTO}) {
            GeneratedTable table = new GeneratedTable(
                    testGroup(new GlobalMemoryManager(64L << 20), compression),
                    testLayout(), 128, MutationFaultInjector.NONE);
            for (int index = 0; index < 4_096; index++) {
                add(table, index + 1L, "bucket", index % 1_000, null);
            }

            GeneratedProbe minimum = table.newProbe(2);
            minimum.putInt(2, 500);
            PredicateIr predicate = table.requireOwnedExpression(
                    table.ge(minimum.seal()));

            LogicalRowPlan countOptimized = LogicalRowPlan.tableScan(table)
                    .typedFilter(predicate);
            LogicalRowPlan countReference = LogicalRowPlan.tableScan(table)
                    .typedFilter(predicate);
            assertEquals(
                    QueryOperation.referenceCountForTesting(countReference),
                    QueryOperation.optimizedCount(countOptimized));

            PrimitivePipelineCapture sumOptimized = PrimitivePipelineCapture.row(
                    LogicalRowPlan.tableScan(table)
                            .typedFilter(predicate)
                            .fieldProjection(0),
                    PrimitiveValueKind.LONG,
                    (GeneratedCallbacks.RowToLongMapper)
                            () -> table.queryCursor().viewLong(0),
                    false,
                    0);
            PrimitivePipelineCapture sumReference = PrimitivePipelineCapture.row(
                    LogicalRowPlan.tableScan(table)
                            .typedFilter(predicate)
                            .fieldProjection(0),
                    PrimitiveValueKind.LONG,
                    (GeneratedCallbacks.RowToLongMapper)
                            () -> table.queryCursor().viewLong(0),
                    false,
                    0);
            assertEquals(
                    ReferencePrimitiveInterpreter
                            .sumIntegralForTesting(sumReference),
                    PrimitivePlanOperation.sumIntegral(sumOptimized));

            PrimitivePipelineCapture arrayOptimized = PrimitivePipelineCapture.row(
                    LogicalRowPlan.tableScan(table)
                            .typedFilter(predicate)
                            .fieldProjection(0),
                    PrimitiveValueKind.LONG,
                    (GeneratedCallbacks.RowToLongMapper)
                            () -> table.queryCursor().viewLong(0),
                    false,
                    0);
            PrimitivePipelineCapture arrayReference = PrimitivePipelineCapture.row(
                    LogicalRowPlan.tableScan(table)
                            .typedFilter(predicate)
                            .fieldProjection(0),
                    PrimitiveValueKind.LONG,
                    (GeneratedCallbacks.RowToLongMapper)
                            () -> table.queryCursor().viewLong(0),
                    false,
                    0);
            assertTrue(Arrays.equals(
                    ReferencePrimitiveInterpreter.valuesForTesting(arrayReference),
                    PrimitivePlanOperation.toLongArray(arrayOptimized)));
        }
    }

    @Test
    void vectorIntegralKernelHandlesRleMixedAndOverlayRepresentations() {
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        SomaCompression.AUTO),
                testLayout(), 128, MutationFaultInjector.NONE);
        for (int index = 0; index < 257; index++) {
            add(table, index + 1L, "bucket", 7, null);
        }
        TableChunk first = table.rootForTesting().directory.chunk(0);
        assertEquals(ChunkRepresentation.ENCODED, first.representation());
        int valueLeaf = table.layout().fieldStart(2);
        IntegralChunkAccess valueAccess = first.borrowIntegral(
                table.layout().leafKind(valueLeaf),
                table.layout().leafSlot(valueLeaf));
        assertNotNull(valueAccess);
        assertTrue(valueAccess.runEncoded());
        assertEquals(1, valueAccess.runCount());
        assertEquals(ChunkRepresentation.PLAIN,
                table.rootForTesting().directory.chunk(2).representation());

        assertEquals(
                QueryOperation.referenceCountForTesting(
                        LogicalRowPlan.tableScan(table)),
                QueryOperation.optimizedCount(
                        LogicalRowPlan.tableScan(table)));

        GeneratedProbe seven = table.newProbe(2);
        seven.putInt(2, 7);
        PredicateIr predicate = table.requireOwnedExpression(
                table.ge(seven.seal()));
        assertEquals(
                QueryOperation.referenceCountForTesting(
                        LogicalRowPlan.tableScan(table).typedFilter(predicate)),
                QueryOperation.optimizedCount(
                        LogicalRowPlan.tableScan(table).typedFilter(predicate)));

        PrimitivePipelineCapture sameLeafReference = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table)
                        .typedFilter(predicate).fieldProjection(2),
                PrimitiveValueKind.INT,
                (GeneratedCallbacks.RowToIntMapper)
                        () -> table.queryCursor().viewInt(2),
                false,
                2);
        PrimitivePipelineCapture sameLeafOptimized = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table)
                        .typedFilter(predicate).fieldProjection(2),
                PrimitiveValueKind.INT,
                (GeneratedCallbacks.RowToIntMapper)
                        () -> table.queryCursor().viewInt(2),
                false,
                2);
        CanonicalRowPhysicalPlan sameLeafPlan = primitivePhysicalPlan(
                table,
                PrimitivePipelineCapture.row(
                        LogicalRowPlan.tableScan(table)
                                .typedFilter(predicate).fieldProjection(2),
                        PrimitiveValueKind.INT,
                        (GeneratedCallbacks.RowToIntMapper)
                                () -> table.queryCursor().viewInt(2),
                        false,
                        2),
                CanonicalPrimitiveOperation.TerminalKind.SUM);
        assertEquals(
                CanonicalPrimitiveVectorKernel.RepresentationHandler
                        .ENCODED_NATIVE,
                sameLeafPlan.pipeline.terminalSegment().chunkKernel.handler(first));
        assertEquals(
                ReferencePrimitiveInterpreter.sumIntegralForTesting(
                        sameLeafReference),
                PrimitivePlanOperation.sumIntegral(sameLeafOptimized));

        // The projection and predicate use different leaves. RLE boundaries
        // are deliberately not zipped; this Chunk takes the scalar fallback.
        PrimitivePipelineCapture multiLeafReference = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table)
                        .typedFilter(predicate).fieldProjection(0),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(0),
                false,
                0);
        PrimitivePipelineCapture multiLeafOptimized = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table)
                        .typedFilter(predicate).fieldProjection(0),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(0),
                false,
                0);
        CanonicalRowPhysicalPlan multiLeafPlan = primitivePhysicalPlan(
                table,
                PrimitivePipelineCapture.row(
                        LogicalRowPlan.tableScan(table)
                                .typedFilter(predicate).fieldProjection(0),
                        PrimitiveValueKind.LONG,
                        (GeneratedCallbacks.RowToLongMapper)
                                () -> table.queryCursor().viewLong(0),
                        false,
                        0),
                CanonicalPrimitiveOperation.TerminalKind.SUM);
        assertEquals(
                CanonicalPrimitiveVectorKernel.RepresentationHandler
                        .ENCODED_SCALAR,
                multiLeafPlan.pipeline.terminalSegment().chunkKernel.handler(first));
        assertEquals(
                ReferencePrimitiveInterpreter.sumIntegralForTesting(
                        multiLeafReference),
                PrimitivePlanOperation.sumIntegral(multiLeafOptimized));

        assertEquals(1L, update(table, 1L, "bucket", 9, null).changed());
        assertEquals(ChunkRepresentation.ENCODED_WITH_OVERLAY,
                table.rootForTesting().directory.chunk(0).representation());
        PrimitivePipelineCapture overlayReference = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table).fieldProjection(2),
                PrimitiveValueKind.INT,
                (GeneratedCallbacks.RowToIntMapper)
                        () -> table.queryCursor().viewInt(2),
                false,
                2);
        PrimitivePipelineCapture overlayOptimized = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table).fieldProjection(2),
                PrimitiveValueKind.INT,
                (GeneratedCallbacks.RowToIntMapper)
                        () -> table.queryCursor().viewInt(2),
                false,
                2);
        CanonicalRowPhysicalPlan overlayPlan = primitivePhysicalPlan(
                table,
                PrimitivePipelineCapture.row(
                        LogicalRowPlan.tableScan(table).fieldProjection(2),
                        PrimitiveValueKind.INT,
                        (GeneratedCallbacks.RowToIntMapper)
                                () -> table.queryCursor().viewInt(2),
                        false,
                        2),
                CanonicalPrimitiveOperation.TerminalKind.SUM);
        assertEquals(
                CanonicalPrimitiveVectorKernel.RepresentationHandler
                        .OVERLAY_SCALAR,
                overlayPlan.pipeline.terminalSegment().chunkKernel.handler(
                        table.rootForTesting().directory.chunk(0)));
        assertEquals(
                ReferencePrimitiveInterpreter.sumIntegralForTesting(
                        overlayReference),
                PrimitivePlanOperation.sumIntegral(overlayOptimized));
    }

    @Test
    void vectorRleIntegralSumKeepsExactWideOverflowSemantics() {
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        SomaCompression.AUTO),
                rleCostLayout(), 128, MutationFaultInjector.NONE);
        for (int index = 0; index < 128; index++) {
            try (GeneratedRow row = table.beginAdd()) {
                row.putLong(0, index + 1L);
                row.putLong(1, Long.MAX_VALUE);
                row.add();
            }
        }
        TableChunk encoded = table.rootForTesting().directory.chunk(0);
        assertEquals(ChunkRepresentation.ENCODED, encoded.representation());
        IntegralChunkAccess access = encoded.borrowIntegral(
                GeneratedTableLayout.LONG, 1);
        assertNotNull(access);
        assertTrue(access.runEncoded());
        assertEquals(1, access.runCount());

        PrimitivePipelineCapture reference = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table).fieldProjection(1),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(1),
                false,
                1);
        PrimitivePipelineCapture optimized = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table).fieldProjection(1),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(1),
                false,
                1);
        SomaOperationException referenceFailure = assertThrows(
                SomaOperationException.class,
                () -> ReferencePrimitiveInterpreter
                        .sumIntegralForTesting(reference));
        SomaOperationException optimizedFailure = assertThrows(
                SomaOperationException.class,
                () -> PrimitivePlanOperation.sumIntegral(optimized));
        assertEquals(SomaFailureCode.ARITHMETIC_OVERFLOW,
                referenceFailure.code());
        assertEquals(referenceFailure.code(), optimizedFailure.code());
    }

    @Test
    void vectorMorselsUseBoundedPoolAndPreserveExactIntegralSum() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        TrackingForkJoinPool pool = new TrackingForkJoinPool(4, memory);
        try {
            GeneratedTable table = new GeneratedTable(
                    testGroup(memory, pool),
                    testLayout(), 128, MutationFaultInjector.NONE);
            long expected = 0L;
            for (int index = 0; index < 8_192; index++) {
                long value = index + 1L;
                add(table, value, "bucket", index % 1_000, null);
                expected += value;
            }

            PrimitivePipelineCapture parallel = PrimitivePipelineCapture.row(
                    LogicalRowPlan.tableScan(table).fieldProjection(0).parallel(),
                    PrimitiveValueKind.LONG,
                    (GeneratedCallbacks.RowToLongMapper)
                            () -> table.queryCursor().viewLong(0),
                    false,
                    0);
            assertEquals(expected, PrimitivePlanOperation.sumIntegral(parallel));
            assertTrue(pool.submissions.get() > 0);
            assertTrue(pool.peakTemporaryBytes.get() > 0L);
            assertTrue(pool.peakTemporaryBytes.get() < table.size() * 4L);
            assertEquals(0L, memory.temporaryBytes());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void vectorParallelPlanOwnsOneFinalDecisionAndChunkPartials() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory, new ForkJoinPool(4)),
                testLayout(), 128, MutationFaultInjector.NONE);
        for (int index = 0; index < 8_192; index++) {
            add(table, index + 1L, "bucket", index % 1_000, null);
        }
        GeneratedProbe minimum = table.newProbe(2);
        minimum.putInt(2, 500);
        LogicalRowPlan frontend = LogicalRowPlan.tableScan(table)
                .typedFilter(table.requireOwnedExpression(table.ge(minimum.seal())))
                .fieldProjection(0)
                .parallel();
        CanonicalRowOperation source = CanonicalRowLowering.source(table, frontend);
        BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                source,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        CanonicalPrimitiveOperation operation = CanonicalPrimitiveLowering.operation(
                PrimitivePipelineCapture.row(
                        frontend,
                        PrimitiveValueKind.LONG,
                        (GeneratedCallbacks.RowToLongMapper)
                                () -> table.queryCursor().viewLong(0),
                        false,
                        0),
                CanonicalPrimitiveOperation.TerminalKind.SUM,
                null);
        CanonicalRowPhysicalPlan physical = CanonicalRowPlanner.plan(
                CanonicalRowPlanner.normalize(bound),
                CanonicalRowPhysicalRequest.primitive(operation, 0L));
        CanonicalPrimitiveVectorKernel.Decision decision =
                physical.pipeline.terminalSegment().chunkKernel;
        assertNotNull(decision);
        assertEquals(CanonicalRowPhysicalPlan.AccessPath.TABLE_SCAN,
                physical.pipeline.source);
        assertEquals(CanonicalPhysicalSegment.Kernel.CHUNK_SPECIALIZED,
                physical.pipeline.terminalSegment().kernel);
        assertEquals(0, physical.pipeline.terminalSegment().fromStage);
        assertEquals(0, physical.pipeline.terminalSegment().toStageExclusive);
        assertEquals(CanonicalPhysicalMorsel.Kind.CHUNK_RANGE,
                physical.pipeline.terminalSegment().morsel.kind);
        assertEquals(4, physical.pipeline.terminalSegment().morsel.partitions);
        assertEquals(CanonicalPhysicalPipeline.Sink.INTEGRAL_SUM,
                physical.pipeline.sink);
        assertTrue(CanonicalPrimitiveVectorKernel.isIntegralSum(physical));
        long partialBytes = decision.temporaryBytes;
        assertEquals(0L, physical.resources.parallelPrefixTemporaryBytes);
        assertTrue(partialBytes > 0L);
        assertTrue(partialBytes < table.size() * 4L);
        assertEquals(partialBytes, physical.resources.temporaryBytes);
    }

    @Test
    void vectorParallelScratchIsAdmittedBeforePoolOrDataWork() {
        long budget = 64L << 20;
        GlobalMemoryManager memory = new GlobalMemoryManager(budget);
        TrackingForkJoinPool pool = new TrackingForkJoinPool(4, memory);
        try {
            GeneratedTable table = new GeneratedTable(
                    testGroup(memory, pool),
                    testLayout(), 128, MutationFaultInjector.NONE);
            for (int index = 0; index < 8_192; index++) {
                add(table, index + 1L, "bucket", index % 1_000, null);
            }
            LogicalRowPlan planningFrontend = LogicalRowPlan.tableScan(table)
                    .fieldProjection(0).parallel();
            CanonicalRowOperation source = CanonicalRowLowering.source(
                    table, planningFrontend);
            BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                    source,
                    table,
                    table.layout(),
                    table.rootForTesting(),
                    io.github.somaruntime.soma.SomaOperation.QUERY,
                    new Object());
            CanonicalPrimitiveOperation operation =
                    CanonicalPrimitiveLowering.operation(
                            PrimitivePipelineCapture.row(
                                    planningFrontend,
                                    PrimitiveValueKind.LONG,
                                    (GeneratedCallbacks.RowToLongMapper)
                                            () -> table.queryCursor().viewLong(0),
                                    false,
                                    0),
                            CanonicalPrimitiveOperation.TerminalKind.SUM,
                            null);
            long scratch = CanonicalRowPlanner.plan(
                    CanonicalRowPlanner.normalize(bound),
                    CanonicalRowPhysicalRequest.primitive(operation, 0L))
                    .resources.temporaryBytes;
            long available = budget - memory.retainedBytes();
            long blockerBytes = available - scratch + 1L;
            assertTrue(scratch > 0L);
            assertTrue(blockerBytes > 0L);
            int submissions = pool.submissions.get();
            try (GlobalMemoryManager.TemporaryLease ignored =
                         memory.leaseTemporary(
                                 blockerBytes,
                                 io.github.somaruntime.soma.SomaOperation.QUERY,
                                 new Object())) {
                PrimitivePipelineCapture rejected = PrimitivePipelineCapture.row(
                        LogicalRowPlan.tableScan(table)
                                .fieldProjection(0).parallel(),
                        PrimitiveValueKind.LONG,
                        (GeneratedCallbacks.RowToLongMapper)
                                () -> table.queryCursor().viewLong(0),
                        false,
                        0);
                SomaOperationException failure = assertThrows(
                        SomaOperationException.class,
                        () -> PrimitivePlanOperation.sumIntegral(rejected));
                assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        failure.code());
                assertEquals(submissions, pool.submissions.get());
            }
            assertEquals(0L, memory.temporaryBytes());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void vectorParallelFieldSumFailsClosedWhenPoolIsUnavailable() {
        ForkJoinPool pool = new ForkJoinPool(2);
        GeneratedTable table = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20), pool),
                testLayout(), 128, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, null);
        pool.shutdownNow();

        PrimitivePipelineCapture parallel = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(table).fieldProjection(0).parallel(),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(0),
                false,
                0);
        SomaOperationException failure = assertThrows(
                SomaOperationException.class,
                () -> PrimitivePlanOperation.sumIntegral(parallel));
        assertEquals(SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                failure.code());
    }

    @Test
    void vectorLongMaterializationPreservesOrderAcrossRleMixedAndOverlay() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        TrackingForkJoinPool pool = new TrackingForkJoinPool(4, memory);
        try {
            GeneratedTable table = new GeneratedTable(
                    testGroup(memory, pool, SomaCompression.AUTO),
                    rleCostLayout(), 128, MutationFaultInjector.NONE);
            for (int index = 0; index < 257; index++) {
                long value = index < 64 ? 7L : index < 192 ? 8L : 9L;
                addRleCost(table, index + 1L, value);
            }
            assertEquals(ChunkRepresentation.ENCODED,
                    table.rootForTesting().directory.chunk(0).representation());
            assertEquals(ChunkRepresentation.ENCODED,
                    table.rootForTesting().directory.chunk(1).representation());
            assertEquals(ChunkRepresentation.PLAIN,
                    table.rootForTesting().directory.chunk(2).representation());

            long[] detached = PrimitivePlanOperation.toLongArray(
                    longFieldCapture(table, null, false));
            assertTrue(Arrays.equals(
                    ReferencePrimitiveInterpreter.valuesForTesting(
                            longFieldCapture(table, null, false)),
                    detached));
            assertTrue(Arrays.equals(
                    detached,
                    PrimitivePlanOperation.toLongArray(
                            longFieldCapture(table, null, true))));

            GeneratedProbe minimum = table.newProbe(1);
            minimum.putLong(1, 8L);
            PredicateIr predicate = table.requireOwnedExpression(
                    table.ge(minimum.seal()));
            long[] expected = ReferencePrimitiveInterpreter.valuesForTesting(
                    longFieldCapture(table, predicate, false));
            assertTrue(Arrays.equals(
                    expected,
                    PrimitivePlanOperation.toLongArray(
                            longFieldCapture(table, predicate, false))));
            assertTrue(Arrays.equals(
                    expected,
                    PrimitivePlanOperation.toLongArray(
                            longFieldCapture(table, predicate, true))));

            try (GeneratedRow row = table.beginUpdate()) {
                row.putLong(0, 1L);
                assertTrue(row.locateForUpdate());
                row.beginEditorCallback();
                row.editLong(1, 11L);
                row.endEditorCallback();
                assertEquals(1L, row.finishUpdate().changed());
            }
            assertEquals(ChunkRepresentation.ENCODED_WITH_OVERLAY,
                    table.rootForTesting().directory.chunk(0).representation());
            long[] overlayExpected = ReferencePrimitiveInterpreter.valuesForTesting(
                    longFieldCapture(table, predicate, false));
            long[] overlayParallel = PrimitivePlanOperation.toLongArray(
                    longFieldCapture(table, predicate, true));
            assertTrue(Arrays.equals(overlayExpected, overlayParallel));
            assertEquals(7L, detached[0], "detached result changed with Table");
            assertEquals(0L, memory.temporaryBytes());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void vectorLongMaterializationHandlesEmptyAllAndNoMatch() {
        GeneratedTable empty = new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20)),
                rleCostLayout(), 128, MutationFaultInjector.NONE);
        assertEquals(0, PrimitivePlanOperation.toLongArray(
                longFieldCapture(empty, null, false)).length);

        for (int index = 0; index < 128; index++) {
            addRleCost(empty, index + 1L, 5L);
        }
        GeneratedProbe five = empty.newProbe(1);
        five.putLong(1, 5L);
        PredicateIr all = empty.requireOwnedExpression(empty.ge(five.seal()));
        assertEquals(128, PrimitivePlanOperation.toLongArray(
                longFieldCapture(empty, all, false)).length);

        GeneratedProbe six = empty.newProbe(1);
        six.putLong(1, 6L);
        PredicateIr none = empty.requireOwnedExpression(empty.ge(six.seal()));
        assertEquals(0, PrimitivePlanOperation.toLongArray(
                longFieldCapture(empty, none, false)).length);
    }

    @Test
    void vectorLongMaterializationUsesBoundedChunkStateForParallelismMatrix() {
        for (int parallelism : new int[] {1, 2, 4, 16}) {
            GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            try {
                GeneratedTable table = new GeneratedTable(
                        testGroup(memory, pool, SomaCompression.AUTO),
                        rleCostLayout(), 128, MutationFaultInjector.NONE);
                for (int index = 0; index < 1_024; index++) {
                    addRleCost(table, index + 1L, index % 17L);
                }
                GeneratedProbe minimum = table.newProbe(1);
                minimum.putLong(1, 8L);
                PredicateIr predicate = table.requireOwnedExpression(
                        table.ge(minimum.seal()));
                assertTrue(Arrays.equals(
                        ReferencePrimitiveInterpreter.valuesForTesting(
                                longFieldCapture(table, predicate, false)),
                        PrimitivePlanOperation.toLongArray(
                                longFieldCapture(table, predicate, true))),
                        "parallelism=" + parallelism);
                assertEquals(0L, memory.temporaryBytes());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void vectorLongMaterializationAdmitsOutputAndChunkStateBeforeWork() {
        long budget = 64L << 20;
        GlobalMemoryManager memory = new GlobalMemoryManager(budget);
        TrackingForkJoinPool pool = new TrackingForkJoinPool(4, memory);
        try {
            GeneratedTable table = new GeneratedTable(
                    testGroup(memory, pool, SomaCompression.AUTO),
                    rleCostLayout(), 128, MutationFaultInjector.NONE);
            for (int index = 0; index < 8_192; index++) {
                addRleCost(table, index + 1L, index % 17L);
            }
            GeneratedProbe minimum = table.newProbe(1);
            minimum.putLong(1, 8L);
            PredicateIr predicate = table.requireOwnedExpression(
                    table.ge(minimum.seal()));
            PrimitivePipelineCapture planning = longFieldCapture(
                    table, predicate, true);
            CanonicalRowPhysicalPlan physical = primitivePhysicalPlan(
                    table,
                    planning,
                    CanonicalPrimitiveOperation.TerminalKind.MATERIALIZE);
            int chunks = CheckedStructural.ceilChunks(
                    table.rootForTesting().size,
                    table.rootForTesting().directory.chunkRows());
            long expected = RowExecutionSupport.arrayBytes(
                    table.size(), 8L, physical.normalized.bound.provenance);
            expected = CheckedLong.add(
                    expected,
                    RowExecutionSupport.arrayBytes(
                            chunks, 4L, physical.normalized.bound.provenance),
                    physical.normalized.bound.operation,
                    physical.normalized.bound.provenance);
            expected = CheckedLong.add(
                    expected,
                    RowExecutionSupport.arrayBytes(
                            chunks, 4L, physical.normalized.bound.provenance),
                    physical.normalized.bound.operation,
                    physical.normalized.bound.provenance);
            expected = CheckedLong.add(
                    expected,
                    RowExecutionSupport.arrayBytes(
                            chunks, 16L, physical.normalized.bound.provenance),
                    physical.normalized.bound.operation,
                    physical.normalized.bound.provenance);
            assertTrue(physical.pipeline.terminalSegment().chunkKernel.ownsTerminalScratch);
            assertEquals(0L, physical.resources.parallelPrefixTemporaryBytes);
            assertEquals(expected, physical.resources.temporaryBytes);
            assertTrue(expected < RowExecutionSupport.arrayBytes(
                    table.size(), 16L, physical.normalized.bound.provenance));

            long available = budget - memory.retainedBytes();
            long blockerBytes = available - expected + 1L;
            assertTrue(blockerBytes > 0L);
            int submissions = pool.submissions.get();
            try (GlobalMemoryManager.TemporaryLease ignored =
                         memory.leaseTemporary(
                                 blockerBytes,
                                 io.github.somaruntime.soma.SomaOperation.QUERY,
                                 new Object())) {
                SomaOperationException failure = assertThrows(
                        SomaOperationException.class,
                        () -> PrimitivePlanOperation.toLongArray(
                                longFieldCapture(table, predicate, true)));
                assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        failure.code());
                assertEquals(submissions, pool.submissions.get());
            }
            assertEquals(0L, memory.temporaryBytes());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void vectorParallelLongMaterializationFailsClosedWhenPoolIsUnavailable() {
        ForkJoinPool pool = new ForkJoinPool(2);
        GeneratedTable table = new GeneratedTable(
                testGroup(
                        new GlobalMemoryManager(64L << 20),
                        pool,
                        SomaCompression.AUTO),
                rleCostLayout(), 128, MutationFaultInjector.NONE);
        addRleCost(table, 1L, 7L);
        pool.shutdownNow();

        SomaOperationException failure = assertThrows(
                SomaOperationException.class,
                () -> PrimitivePlanOperation.toLongArray(
                        longFieldCapture(table, null, true)));
        assertEquals(SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                failure.code());
    }


    @Test
    void primitiveIntegralNaturalSortPreservesSignedValueOrder() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        int[] input = new int[] {
                Integer.MAX_VALUE, -1, 0, Integer.MIN_VALUE, 7, -7
        };
        for (int index = 0; index < input.length; index++) {
            add(table, index, "bucket", input[index], new Object());
        }
        GeneratedCallbacks.RowToIntMapper root =
                () -> table.queryCursor().viewInt(2);
        PrimitivePipelineCapture optimized = PrimitivePipelineCapture
                .row(LogicalRowPlan.tableScan(table),
                        PrimitiveValueKind.INT, root, true, 2)
                .sorted();
        PrimitivePipelineCapture reference = PrimitivePipelineCapture
                .row(LogicalRowPlan.tableScan(table),
                        PrimitiveValueKind.INT, root, true)
                .sorted();

        assertTrue(Arrays.equals(
                new long[] {
                        Integer.MIN_VALUE, -7L, -1L, 0L, 7L,
                        Integer.MAX_VALUE
                },
                PrimitivePlanOperation.valuesForTesting(optimized)));
        assertTrue(Arrays.equals(
                ReferencePrimitiveInterpreter.valuesForTesting(reference),
                PrimitivePlanOperation.valuesForTesting(PrimitivePipelineCapture
                        .row(LogicalRowPlan.tableScan(table),
                                PrimitiveValueKind.INT, root, true, 2)
                        .sorted())));
    }

    @Test
    void numericReferenceCoversCanonicalBlocksSpecialValuesAndOverflow() {
        GeneratedTable floating = floatingTable();
        for (int index = 0; index < 1025; index++) {
            double value;
            switch (index & 3) {
                case 0: value = 1.0e16d; break;
                case 1: value = 1.0d; break;
                case 2: value = -1.0e16d; break;
                default: value = -0.0d; break;
            }
            addFloating(floating, index, (float) value, value, null);
        }
        PrimitivePipelineCapture doublePlan = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(floating),
                PrimitiveValueKind.DOUBLE,
                (GeneratedCallbacks.RowToDoubleMapper)
                        () -> floating.queryCursor().viewDouble(2),
                false);
        for (long count : new long[] {1023L, 1024L, 1025L}) {
            PrimitivePipelineCapture bounded = doublePlan.limit(count);
            double reference = ReferencePrimitiveInterpreter
                    .sumFloatingForTesting(bounded);
            double optimized = PrimitivePlanOperation.sumDouble(bounded);
            assertEquals(
                    Double.doubleToLongBits(reference),
                    Double.doubleToLongBits(optimized));
        }

        addFloating(floating, 2000L, Float.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY, null);
        addFloating(floating, 2001L, -0.0f, -0.0d, null);
        addFloating(floating, 2002L, 0.0f, 0.0d, null);
        addFloating(floating, 2003L, Float.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, null);
        addFloating(floating, 2004L, Float.NaN, Double.NaN, null);
        PrimitivePipelineCapture ordered = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(floating),
                PrimitiveValueKind.DOUBLE,
                (GeneratedCallbacks.RowToDoubleMapper)
                        () -> floating.queryCursor().viewDouble(2),
                false).sorted();
        assertTrue(Arrays.equals(
                ReferencePrimitiveInterpreter.valuesForTesting(ordered),
                PrimitivePlanOperation.valuesForTesting(ordered)));
        assertTrue(Double.isNaN(
                ReferencePrimitiveInterpreter.sumFloatingForTesting(doublePlan)));
        assertTrue(Double.isNaN(PrimitivePlanOperation.sumDouble(doublePlan)));

        GeneratedTable positiveInfinity = floatingTable();
        addFloating(positiveInfinity, 1L, Float.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, null);
        addFloating(positiveInfinity, 2L, 1.0f, 1.0d, null);
        PrimitivePipelineCapture floatPlan = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(positiveInfinity),
                PrimitiveValueKind.FLOAT,
                (GeneratedCallbacks.RowToFloatMapper)
                        () -> positiveInfinity.queryCursor().viewFloat(1),
                false);
        assertEquals(
                Double.doubleToLongBits(
                        ReferencePrimitiveInterpreter
                                .sumFloatingForTesting(floatPlan)),
                Double.doubleToLongBits(
                        PrimitivePlanOperation.sumDouble(floatPlan)));
        assertEquals(Double.POSITIVE_INFINITY,
                PrimitivePlanOperation.sumDouble(floatPlan));

        GeneratedTable integral = table(64L << 20, MutationFaultInjector.NONE);
        add(integral, Long.MAX_VALUE, "maximum", 0, null);
        add(integral, 1L, "one", 0, null);
        PrimitivePipelineCapture longPlan = PrimitivePipelineCapture.row(
                LogicalRowPlan.tableScan(integral),
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> integral.queryCursor().viewLong(0),
                false);
        SomaOperationException referenceOverflow = assertThrows(
                SomaOperationException.class,
                () -> ReferencePrimitiveInterpreter
                        .sumIntegralForTesting(longPlan));
        SomaOperationException optimizedOverflow = assertThrows(
                SomaOperationException.class,
                () -> PrimitivePlanOperation.sumIntegral(longPlan));
        assertEquals(SomaFailureCode.ARITHMETIC_OVERFLOW,
                referenceOverflow.code());
        assertEquals(referenceOverflow.code(), optimizedOverflow.code());
        assertEquals(referenceOverflow.operation(), optimizedOverflow.operation());
    }

    @Test
    void pipelineArgumentFailureDoesNotClaimButTerminalFailureDoes() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        GeneratedPipeline open = table.selectAll();
        SomaOperationException invalid = assertThrows(
                SomaOperationException.class, () -> open.skip(-1L));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, invalid.code());
        assertEquals(1L, open.count());

        GeneratedPipeline failing = table.selectAll();
        SomaOperationException callback = assertThrows(
                SomaOperationException.class,
                () -> failing.anyMatch(() -> {
                    throw new IllegalStateException("application detail");
                }));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, callback.code());
        SomaOperationException consumed = assertThrows(
                SomaOperationException.class, failing::count);
        assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, consumed.code());

        GeneratedPipeline errorPipeline = table.selectAll();
        assertThrows(AssertionError.class, () -> errorPipeline.forEach(() -> {
            throw new AssertionError("fatal application error");
        }));
        assertEquals(1L, table.count());
        SomaOperationException errorConsumed = assertThrows(
                SomaOperationException.class, errorPipeline::count);
        assertEquals(
                SomaFailureCode.PIPELINE_ALREADY_CONSUMED,
                errorConsumed.code());
    }

    @Test
    void linkedPipelineHasOneWinnerAcrossBranchesAndThreads() throws Exception {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());

        GeneratedPipeline parent = table.selectAll();
        GeneratedPipeline child = parent.filter(() -> true);
        SomaOperationException lostBranch = assertThrows(
                SomaOperationException.class, parent::count);
        assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, lostBranch.code());
        assertEquals(1L, child.count());

        GeneratedPipeline racing = table.selectAll();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Object> first = new AtomicReference<Object>();
        AtomicReference<Object> second = new AtomicReference<Object>();
        Thread firstThread = new Thread(() -> raceTerminal(racing, start, first));
        Thread secondThread = new Thread(() -> raceTerminal(racing, start, second));
        firstThread.start();
        secondThread.start();
        start.countDown();
        firstThread.join(5000L);
        secondThread.join(5000L);
        assertFalse(firstThread.isAlive());
        assertFalse(secondThread.isAlive());

        int successes = terminalSuccess(first.get()) + terminalSuccess(second.get());
        int consumed = consumedFailure(first.get()) + consumedFailure(second.get());
        assertEquals(1, successes);
        assertEquals(1, consumed);

        assertEquals(1L, table.selectAll().count());
        assertEquals(1L, table.selectAll().count());
    }

    @Test
    void fieldMappedAndPrimitiveCarriersShareTheOneShotContract() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());

        GeneratedFieldPipeline field = table.fieldSource(2);
        assertEquals(1L, field.count());
        assertConsumed(field::count);
        GeneratedFieldPipeline fieldParent = table.fieldSource(2);
        GeneratedFieldPipeline fieldChild = fieldParent.filter(() -> true);
        assertConsumed(fieldParent::count);
        assertEquals(1L, fieldChild.count());

        GeneratedMappedPipeline<Integer> mapped = table.selectAll()
                .map(() -> table.queryCursor().viewInt(2));
        assertEquals(1L, mapped.count());
        assertConsumed(mapped::count);
        GeneratedMappedPipeline<Integer> mappedParent = table.selectAll()
                .map(() -> table.queryCursor().viewInt(2));
        @SuppressWarnings("unchecked")
        GeneratedMappedPipeline<Integer> mappedChild =
                (GeneratedMappedPipeline<Integer>) mappedParent.limit(1L);
        assertConsumed(mappedParent::count);
        assertEquals(1L, mappedChild.count());
        GeneratedMappedPipeline<Integer> mappedFailure = table.selectAll()
                .map(() -> table.queryCursor().viewInt(2));
        @SuppressWarnings("unchecked")
        GeneratedMappedPipeline<Integer> failingMappedChild =
                (GeneratedMappedPipeline<Integer>) mappedFailure.filter(value -> {
                    throw new IllegalStateException("mapped failure");
                });
        SomaOperationException mappedCallback = assertThrows(
                SomaOperationException.class, failingMappedChild::count);
        assertEquals(SomaFailureCode.CALLBACK_FAILED, mappedCallback.code());
        assertConsumed(failingMappedChild::count);

        GeneratedPrimitiveValuePipeline primitive = table.selectAll()
                .primitiveInt(() -> table.queryCursor().viewInt(2));
        assertEquals(1L, primitive.count());
        assertConsumed(primitive::count);
        GeneratedPrimitiveValuePipeline primitiveParent = table.selectAll()
                .primitiveInt(() -> table.queryCursor().viewInt(2));
        GeneratedPrimitiveValuePipeline primitiveChild =
                primitiveParent.filterInt(value -> true);
        assertConsumed(primitiveParent::count);
        assertEquals(1L, primitiveChild.count());
        GeneratedPrimitiveValuePipeline primitiveFailure = table.selectAll()
                .primitiveInt(() -> table.queryCursor().viewInt(2));
        GeneratedPrimitiveValuePipeline failingPrimitiveChild =
                primitiveFailure.filterInt(value -> {
                    throw new IllegalStateException("primitive failure");
                });
        SomaOperationException primitiveCallback = assertThrows(
                SomaOperationException.class, failingPrimitiveChild::count);
        assertEquals(SomaFailureCode.CALLBACK_FAILED, primitiveCallback.code());
        assertConsumed(failingPrimitiveChild::count);

        io.github.somaruntime.soma.SomaLongStream specialized =
                table.selectAll().mapToLong(
                        () -> table.queryCursor().viewLong(0));
        assertEquals(1L, specialized.count());
        assertConsumed(specialized::count);
        io.github.somaruntime.soma.SomaLongStream specializedParent =
                table.selectAll().mapToLong(
                        () -> table.queryCursor().viewLong(0));
        io.github.somaruntime.soma.SomaLongStream specializedChild =
                specializedParent.filter(value -> true);
        assertConsumed(specializedParent::count);
        assertEquals(1L, specializedChild.count());
    }

    @Test
    void fieldTopValidatesBeforeClaimingItsRowLineage() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        GeneratedFieldPipeline open = table.fieldSource(2);

        SomaOperationException invalid = assertThrows(
                SomaOperationException.class,
                () -> open.top(-1L, () -> 0));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, invalid.code());
        assertEquals(1L, open.count());
    }

    @Test
    void callbackReentrancyPreservesCurrentRuntimeFailureAndPublishesNothing() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        long version = table.stateVersionForTesting();

        SomaOperationException failure = assertThrows(
                SomaOperationException.class,
                () -> table.selectAll().forEach(() ->
                        add(table, 2L, "two", 2, new Object())));
        assertEquals(SomaFailureCode.REENTRANT_GROUP_OPERATION, failure.code());
        assertEquals(1L, table.size());
        assertEquals(version, table.stateVersionForTesting());
        assertMissing(table, 2L);
    }

    @Test
    void callbackRejectsReplayedForeignStructuredFailureCode() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        SomaOperationException replayed = assertThrows(
                SomaOperationException.class,
                () -> table.selectAll().skip(-1L));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, replayed.code());

        SomaOperationException outer = assertThrows(
                SomaOperationException.class,
                () -> table.selectAll().forEach(() -> {
                    throw replayed;
                }));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, outer.code());
        assertSame(replayed, outer.getCause());
        assertEquals(1L, table.size());
    }

    @Test
    void callbackRejectsStructuredFailureReplayedFromEarlierGroupOperation() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        AtomicReference<SomaOperationException> saved =
                new AtomicReference<SomaOperationException>();

        table.selectAll().forEach(() -> {
            try {
                add(table, 2L, "two", 2, new Object());
            } catch (SomaOperationException failure) {
                assertEquals(SomaFailureCode.REENTRANT_GROUP_OPERATION, failure.code());
                saved.set(failure);
            }
        });

        SomaOperationException outer = assertThrows(
                SomaOperationException.class,
                () -> table.selectAll().forEach(() -> {
                    throw saved.get();
                }));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, outer.code());
        assertSame(saved.get(), outer.getCause());
        assertEquals(1L, table.size());
    }

    @Test
    void pointAddAdmitsIndexReplacementBeforePreparingSidecars() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        AtomicInteger observations = new AtomicInteger();
        MutationFaultInjector observer = point -> {
            if (point == MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING) {
                assertTrue(memory.temporaryBytes() > 0L);
                observations.incrementAndGet();
            }
            return false;
        };
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, observer);

        add(table, 1L, "one", 1, new Object());

        assertEquals(1, observations.get());
        assertEquals(0L, memory.temporaryBytes());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
        TableStateRoot root = table.rootForTesting();
        root.key.validateForTesting(root.directory, root.size);
        for (IdentityHashIndex index : root.indexes) {
            index.validateForTesting(root.directory, root.size);
        }
    }

    @Test
    void pointAddRejectsInsufficientBudgetBeforeAllocatingSidecars() {
        GlobalMemoryManager memory = new GlobalMemoryManager(1L);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        Object rootIdentity = table.rootIdentityForTesting();
        TableStateRoot root = table.rootForTesting();

        SomaOperationException failure = assertThrows(
                SomaOperationException.class,
                () -> add(table, 1L, "one", 1, new Object()));

        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, failure.code());
        assertSame(rootIdentity, table.rootIdentityForTesting());
        assertEquals(0L, table.size());
        assertEquals(0L, root.key.managedBytes());
        for (IdentityHashIndex index : root.indexes) {
            assertEquals(0L, index.managedBytes());
        }
        assertEquals(0L, memory.retainedBytes());
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void expressionAndOrderRejectDifferentTableIdentityBeforeExecution() {
        GeneratedTable first = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedTable second = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedProbe literal = first.newProbe(2);
        literal.putInt(2, 1);

        SomaOperationException expression = assertThrows(
                SomaOperationException.class,
                () -> second.filter(first.eq(literal.seal())));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, expression.code());
        SomaOperationException order = assertThrows(
                SomaOperationException.class,
                () -> second.sortedBy(first.asc(2)));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, order.code());
        SomaOperationException composed = assertThrows(
                SomaOperationException.class,
                () -> first.<Object>asc(2).then(second.<Object>asc(2)));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, composed.code());

        SomaOperationException forgedExpression = assertThrows(
                SomaOperationException.class,
                () -> first.filter(new FakeExpression()));
        assertEquals(
                SomaFailureCode.INVALID_ARGUMENT,
                forgedExpression.code());
        SomaOperationException forgedOrder = assertThrows(
                SomaOperationException.class,
                () -> first.sortedBy(new FakeOrder()));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, forgedOrder.code());
    }

    @Test
    void materializationAdmitsWorstCaseBeforeAnyApplicationCallback() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        for (long key = 0L; key < 4L; key++) {
            add(table, key, "value", (int) key, new Object());
        }
        long requiredScratch = 4L
                * (testLayout().detachedRowEstimateBytes() + 48L);
        final int[] callbacks = new int[1];
        GeneratedPipeline pipeline = table.selectAll();
        try (GlobalMemoryManager.TemporaryLease pressure = memory.leaseTemporary(
                memory.budgetBytes() - memory.retainedBytes()
                        - (requiredScratch - 1L),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object())) {
            SomaOperationException rejected = assertThrows(
                    SomaOperationException.class,
                    () -> pipeline.toList(() -> {
                        callbacks[0]++;
                        return table.queryCursor().viewInt(2);
                    }));
            assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, rejected.code());
            assertEquals(0, callbacks[0]);
            SomaOperationException consumed = assertThrows(
                    SomaOperationException.class, pipeline::count);
            assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, consumed.code());
        }
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void sliceCardinalityBoundsPreserveLongDomainMaterialization() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        LogicalRowPlan rows = LogicalRowPlan.tableScan(table)
                .skip(7L)
                .limit(5L);
        assertEquals(5L, rows.outputUpperBound(Long.MAX_VALUE));
        assertEquals(0L, rows.limit(0L).outputUpperBound(Long.MAX_VALUE));

        MappedPipelineCapture<Object> mapped = MappedPipelineCapture
                .root(rows, () -> null)
                .skip(2L)
                .limit(1L);
        assertEquals(1L, mapped.outputUpperBound(Long.MAX_VALUE));

        PrimitivePipelineCapture primitive = PrimitivePipelineCapture
                .mapped(
                        mapped,
                        PrimitiveValueKind.INT,
                        (io.github.somaruntime.soma.SomaToIntFunction<Object>) value -> 0)
                .skip(1L)
                .limit(1L);
        assertEquals(0L, primitive.outputUpperBound(Long.MAX_VALUE));
    }

    @Test
    void boundPlanUsesPublishedIndexCardinalityForFieldDistinct() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        for (long key = 0L; key < 128L; key++) {
            add(table, key, "bucket-" + key % 3L, (int) key, new Object());
        }

        LogicalRowPlan distinct = LogicalRowPlan.tableScan(table)
                .distinctField(1)
                .sortedBy((GeneratedOrder<?>) table.<Object>asc(1));
        BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                CanonicalRowLowering.source(table, distinct),
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        assertEquals(3L, bound.outputUpperBound());

        GeneratedProbe bucket = table.newProbe(1);
        bucket.putReference(1, "bucket-1");
        LogicalRowPlan selectionFrontend = LogicalRowPlan.indexSelection(
                table, 0, bucket.seal().snapshot(table, 1));
        BoundCanonicalRowOperation selection = new BoundCanonicalRowOperation(
                CanonicalRowLowering.source(table, selectionFrontend),
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        assertEquals(43L, selection.outputUpperBound());
    }

    @Test
    void fieldMaterializationUsesFieldShapeResourceEstimate() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        for (long key = 0L; key < 128L; key++) {
            add(table, key, "bucket", (int) key, new Object());
        }

        long fieldEstimate = testLayout().detachedFieldEstimateBytes(3);
        long rowEstimate = testLayout().detachedRowEstimateBytes();
        assertTrue(fieldEstimate < rowEstimate);
        long required = RowExecutionSupport.arrayBytes(
                128L, fieldEstimate + 56L, new Object());
        GeneratedFieldPipeline field = table.fieldSource(3);
        try (GlobalMemoryManager.TemporaryLease pressure = memory.leaseTemporary(
                memory.budgetBytes() - memory.retainedBytes() - required,
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object())) {
            Object[] values = field.toArray(
                    () -> table.queryCursor().viewReference(3),
                    Object.class);
            assertEquals(128, values.length);
        }
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void nestedRowMappedPrimitiveScratchIsAdmittedBeforeCallbacks() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4,
                MutationFaultInjector.NONE);
        for (long key = 0L; key < 16L; key++) {
            add(table, key, "same", (int) (key % 4L), new Object());
        }
        final int[] callbacks = new int[1];
        LogicalRowPlan rows = LogicalRowPlan.tableScan(table)
                .sortedBy((GeneratedOrder<?>) table.<Object>desc(2));
        MappedPipelineCapture<Integer> mapped = MappedPipelineCapture
                .root(rows, () -> {
                    callbacks[0]++;
                    return table.queryCursor().viewInt(2);
                })
                .distinct()
                .sorted(Integer::compareTo);
        PrimitivePipelineCapture primitive = PrimitivePipelineCapture
                .mapped(
                        mapped,
                        PrimitiveValueKind.INT,
                        (io.github.somaruntime.soma.SomaToIntFunction<Integer>) value -> {
                            callbacks[0]++;
                            return value.intValue();
                        })
                .distinct()
                .sorted();

        try (GlobalMemoryManager.TemporaryLease pressure = memory.leaseTemporary(
                memory.budgetBytes() - memory.retainedBytes(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object())) {
            SomaOperationException rejected = assertThrows(
                    SomaOperationException.class,
                    () -> PrimitivePlanOperation.toIntArray(primitive));
            assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, rejected.code());
            assertEquals(0, callbacks[0]);
        }
        assertEquals(0L, memory.temporaryBytes());
    }

    @Test
    void allocationOverflowAndInjectedFailuresPublishNothing() {
        SwitchableFault fault = new SwitchableFault();
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, fault);

        Object initial = table.rootIdentityForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH);
        assertThrows(SomaOperationException.class,
                () -> add(table, 1L, "one", 1, new Object()));
        assertSame(initial, table.rootIdentityForTesting());
        assertEquals(0L, table.size());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        fault.point.set(null);
        table.reserve(4);
        Object reserved = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_FINAL_COMMIT);
        assertThrows(SomaOperationException.class,
                () -> add(table, 1L, "one", 1, new Object()));
        assertSame(reserved, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertEquals(0L, table.size());

        fault.point.set(null);
        add(table, 1L, "one", 1, new Object());
        assertEquals(1L, table.size());
        Object beforeOverflow = table.rootIdentityForTesting();
        SomaOperationException overflow = assertThrows(
                SomaOperationException.class,
                () -> CheckedStructural.fromLong(
                        (long) Integer.MAX_VALUE + 1L,
                        io.github.somaruntime.soma.SomaOperation.RESERVE,
                        table));
        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, overflow.code());
        assertSame(beforeOverflow, table.rootIdentityForTesting());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
    }

    @Test
    void updateAndRemoveCandidateFailuresPreserveOneLogicalGeneration() {
        SwitchableFault fault = new SwitchableFault();
        GeneratedTable table = table(64L << 20, fault);
        Object first = new Object();
        Object second = new Object();
        add(table, 1L, "one", 1, first);
        add(table, 2L, "two", 2, second);

        Object beforeUpdate = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_FINAL_COMMIT);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "one", 11, first));
        assertSame(beforeUpdate, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertRow(table, 1L, "one", 1, first);

        fault.point.set(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertSame(beforeUpdate, table.rootIdentityForTesting());
        assertEquals(1L, indexCount(table, "one"));
        assertEquals(0L, indexCount(table, "moved"));
        assertRow(table, 1L, "one", 1, first);

        assertThrows(SomaOperationException.class, () -> remove(table, 2L));
        assertSame(beforeUpdate, table.rootIdentityForTesting());
        assertEquals(2L, table.size());
        assertRow(table, 2L, "two", 2, second);
    }

    @Test
    void incrementalPointSidecarCommitFailurePreservesOneGenerationAndSkipsRebuild() {
        OccurrenceFault fault = new OccurrenceFault();
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), dualIndexLayout(), 4, fault);
        Object first = new Object();
        Object second = new Object();
        add(table, 1L, "one", 1, first);
        add(table, 2L, "two", 2, second);

        Object root = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        long retained = memory.retainedBytes();

        fault.arm(MutationFaultPoint.BEFORE_INCREMENTAL_SIDECAR_COMMIT, 1);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING, 1);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_INCREMENTAL_SIDECAR_COMMIT, 1);
        assertThrows(SomaOperationException.class, () -> remove(table, 2L));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_INDEX_REBUILD, 1);
        UpdateResult moved = update(table, 1L, "moved", 11, first);
        assertEquals(1L, moved.changed());
        assertEquals(0, fault.seen());
        assertEquals(1L, indexCount(table, "moved"));
        assertEquals(1L, intIndexCount(table, 11));

        fault.arm(MutationFaultPoint.BEFORE_KEY_REBUILD, 1);
        RemoveResult removed = remove(table, 2L);
        assertEquals(1L, removed.removed());
        assertEquals(0, fault.seen());
        assertMissing(table, 2L);
        assertEquals(1L, table.size());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
    }

    @Test
    void structuralAccountingAndIntBoundaryFailBeforeAllocation() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        table.reserve(8);
        assertEquals(34_368L, table.managedBytesForTesting());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        Object published = table.rootIdentityForTesting();
        SomaOperationException overInt = assertThrows(
                SomaOperationException.class,
                () -> CheckedStructural.fromLong(
                        (long) Integer.MAX_VALUE + 1L,
                        io.github.somaruntime.soma.SomaOperation.RESERVE,
                        table));
        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, overInt.code());
        assertSame(published, table.rootIdentityForTesting());
        assertEquals(8L, table.capacity());
        assertEquals(34_368L, memory.retainedBytes());

        assertTrue(TableChunkDirectory.estimatedDirectoryBytes(
                Integer.MAX_VALUE,
                io.github.somaruntime.soma.SomaOperation.RESERVE,
                new Object()) > Integer.MAX_VALUE);
    }

    @Test
    void oneGroupRejectsExternalOverlap() throws Exception {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        add(table, 1L, "one", 1, new Object());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();

        Thread worker = new Thread(() -> {
            try (GeneratedRow row = table.beginUpdate()) {
                row.putLong(0, 1L);
                if (!row.locateForUpdate()) throw new AssertionError();
                row.beginEditorCallback();
                entered.countDown();
                try {
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("release timeout");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                } finally {
                    row.endEditorCallback();
                }
                row.finishUpdate();
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        });
        worker.start();
        assertTrue(entered.await(5L, TimeUnit.SECONDS));
        SomaOperationException overlap = assertThrows(
                SomaOperationException.class, table::size);
        assertEquals(SomaFailureCode.CONCURRENT_GROUP_OPERATION, overlap.code());
        release.countDown();
        worker.join(5000L);
        assertFalse(worker.isAlive());
        assertNull(workerFailure.get());
    }

    private static GeneratedTable table(long budget, MutationFaultInjector fault) {
        return new GeneratedTable(
                testGroup(new GlobalMemoryManager(budget)), testLayout(), 4, fault);
    }

    private static void raceTerminal(
            GeneratedPipeline pipeline,
            CountDownLatch start,
            AtomicReference<Object> result) {
        try {
            if (!start.await(5L, TimeUnit.SECONDS)) {
                result.set(new AssertionError("start timeout"));
                return;
            }
            result.set(Long.valueOf(pipeline.count()));
        } catch (Throwable failure) {
            result.set(failure);
        }
    }

    private static int terminalSuccess(Object value) {
        return Long.valueOf(1L).equals(value) ? 1 : 0;
    }

    private static int consumedFailure(Object value) {
        return value instanceof SomaOperationException
                && ((SomaOperationException) value).code()
                == SomaFailureCode.PIPELINE_ALREADY_CONSUMED ? 1 : 0;
    }

    private static void assertConsumed(Runnable terminal) {
        SomaOperationException consumed = assertThrows(
                SomaOperationException.class, terminal::run);
        assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, consumed.code());
    }

    private static void assertGroupedEquals(
            GroupedLongEntry<Object>[] optimized,
            GroupedLongEntry<Object>[] reference) {
        assertEquals(optimized.length, reference.length);
        for (int index = 0; index < optimized.length; index++) {
            assertEquals(optimized[index].key(), reference[index].key());
            assertEquals(optimized[index].value(), reference[index].value());
        }
    }

    private static void assertPrimitiveGroupedEquals(
            LongGroupedLongEntry[] optimized,
            LongGroupedLongEntry[] reference) {
        assertEquals(optimized.length, reference.length);
        for (int index = 0; index < optimized.length; index++) {
            assertEquals(optimized[index].key(), reference[index].key());
            assertEquals(optimized[index].value(), reference[index].value());
        }
    }

    private static long[] relationLocators(
            final GeneratedRelation relation,
            final boolean reference) {
        return relation.terminal(
                new GeneratedRelation.PairWork<long[]>() {
                    @Override
                    public long[] run(GeneratedRelation.RelationBinding binding) {
                        final ArrayList<Long> locators = new ArrayList<Long>();
                        GeneratedRelation.PairVisitor visitor =
                                new GeneratedRelation.PairVisitor() {
                                    @Override
                                    public boolean visit(int left, int right) {
                                        locators.add(Long.valueOf(left));
                                        locators.add(Long.valueOf(right));
                                        return true;
                                    }
                                };
                        if (reference) {
                            relation.visitBoundReference(binding, false, visitor);
                        } else {
                            relation.visitBound(binding, false, visitor);
                        }
                        long[] result = new long[locators.size()];
                        for (int index = 0; index < result.length; index++) {
                            result[index] = locators.get(index).longValue();
                        }
                        return result;
                    }
                },
                false,
                0L);
    }

    private static GeneratedTable floatingTable() {
        GeneratedTableLayout layout = GeneratedTableLayout.create(
                "Floating",
                4,
                new byte[] {
                        GeneratedTableLayout.LONG,
                        GeneratedTableLayout.FLOAT,
                        GeneratedTableLayout.DOUBLE,
                        GeneratedTableLayout.REFERENCE
                },
                new byte[] {
                        GeneratedTableLayout.EQ_LONG,
                        GeneratedTableLayout.EQ_FLOAT_CANONICAL,
                        GeneratedTableLayout.EQ_DOUBLE_CANONICAL,
                        GeneratedTableLayout.EQ_OBJECT_IDENTITY
                },
                new int[] {0, 1, 2, 3},
                new int[] {1, 1, 1, 1},
                new boolean[] {false, false, false, true},
                0,
                new int[0]);
        return new GeneratedTable(
                testGroup(new GlobalMemoryManager(64L << 20)), layout, 4,
                MutationFaultInjector.NONE);
    }

    private static CanonicalRowPhysicalPlan primitivePhysicalPlan(
            GeneratedTable table,
            PrimitivePipelineCapture frontend,
            CanonicalPrimitiveOperation.TerminalKind terminal) {
        CanonicalPrimitiveOperation primitive =
                CanonicalPrimitiveLowering.operation(frontend, terminal, null);
        BoundCanonicalRowOperation bound = new BoundCanonicalRowOperation(
                primitive.source,
                table,
                table.layout(),
                table.rootForTesting(),
                io.github.somaruntime.soma.SomaOperation.QUERY,
                new Object());
        return CanonicalRowPlanner.plan(
                CanonicalRowPlanner.normalize(bound),
                CanonicalRowPhysicalRequest.primitive(primitive, 0L));
    }

    private static PrimitivePipelineCapture longFieldCapture(
            GeneratedTable table,
            PredicateIr predicate,
            boolean parallel) {
        LogicalRowPlan rows = LogicalRowPlan.tableScan(table);
        if (predicate != null) rows = rows.typedFilter(predicate);
        rows = rows.fieldProjection(1);
        if (parallel) rows = rows.parallel();
        return PrimitivePipelineCapture.row(
                rows,
                PrimitiveValueKind.LONG,
                (GeneratedCallbacks.RowToLongMapper)
                        () -> table.queryCursor().viewLong(1),
                false,
                1);
    }

    private static void addRleCost(
            GeneratedTable table,
            long key,
            long value) {
        try (GeneratedRow row = table.beginAdd()) {
            row.putLong(0, key);
            row.putLong(1, value);
            row.add();
        }
    }

    private static GeneratedTableLayout joinBoundLayout() {
        return GeneratedTableLayout.create(
                "JoinBound",
                4,
                new byte[] {
                        GeneratedTableLayout.LONG,
                        GeneratedTableLayout.LONG
                },
                new byte[] {
                        GeneratedTableLayout.EQ_LONG,
                        GeneratedTableLayout.EQ_LONG
                },
                new int[] {0, 1},
                new int[] {1, 1},
                new boolean[] {false, false},
                0,
                new int[0]);
    }

    private static GeneratedTableLayout testLayout() {
        return GeneratedTableLayout.create(
                "Entity",
                4,
                new byte[] {
                        GeneratedTableLayout.LONG,
                        GeneratedTableLayout.REFERENCE,
                        GeneratedTableLayout.INT,
                        GeneratedTableLayout.REFERENCE
                },
                new byte[] {
                        GeneratedTableLayout.EQ_LONG,
                        GeneratedTableLayout.EQ_STRING_CONTENT,
                        GeneratedTableLayout.EQ_INT,
                        GeneratedTableLayout.EQ_OBJECT_IDENTITY
                },
                new int[] {0, 1, 2, 3},
                new int[] {1, 1, 1, 1},
                new boolean[] {false, true, false, true},
                0,
                new int[] {1});
    }

    private static GeneratedTableLayout rleCostLayout() {
        return GeneratedTableLayout.create(
                "RleCost",
                4,
                new byte[] {
                        GeneratedTableLayout.LONG,
                        GeneratedTableLayout.LONG
                },
                new byte[] {
                        GeneratedTableLayout.EQ_LONG,
                        GeneratedTableLayout.EQ_LONG
                },
                new int[] {0, 1},
                new int[] {1, 1},
                new boolean[] {false, false},
                0,
                new int[0]);
    }

    private static GeneratedTableLayout dualIndexLayout() {
        return GeneratedTableLayout.create(
                "DualIndexEntity",
                4,
                new byte[] {
                        GeneratedTableLayout.LONG,
                        GeneratedTableLayout.REFERENCE,
                        GeneratedTableLayout.INT,
                        GeneratedTableLayout.REFERENCE
                },
                new byte[] {
                        GeneratedTableLayout.EQ_LONG,
                        GeneratedTableLayout.EQ_STRING_CONTENT,
                        GeneratedTableLayout.EQ_INT,
                        GeneratedTableLayout.EQ_OBJECT_IDENTITY
                },
                new int[] {0, 1, 2, 3},
                new int[] {1, 1, 1, 1},
                new boolean[] {false, true, false, true},
                0,
                new int[] {1, 2});
    }

    private static void add(
            GeneratedTable table,
            long key,
            String name,
            int value,
            Object object) {
        try (GeneratedRow row = table.beginAdd()) {
            row.putLong(0, key);
            row.putReference(1, name);
            row.putInt(2, value);
            row.putReference(3, object);
            row.add();
        }
    }

    private static void addFloating(
            GeneratedTable table,
            long key,
            float ratio,
            double weight,
            Object object) {
        try (GeneratedRow row = table.beginAdd()) {
            row.putLong(0, key);
            row.putFloat(1, ratio);
            row.putDouble(2, weight);
            row.putReference(3, object);
            row.add();
        }
    }

    private static UpdateResult update(
            GeneratedTable table,
            long key,
            String name,
            int value,
            Object object) {
        try (GeneratedRow row = table.beginUpdate()) {
            row.putLong(0, key);
            if (!row.locateForUpdate()) return table.missingUpdate();
            row.beginEditorCallback();
            row.editReference(1, name);
            row.editInt(2, value);
            row.editReference(3, object);
            row.endEditorCallback();
            return row.finishUpdate();
        }
    }

    private static RemoveResult remove(GeneratedTable table, long key) {
        try (GeneratedRow row = table.beginRemove()) {
            row.putLong(0, key);
            return row.remove();
        }
    }

    private static long indexCount(GeneratedTable table, String name) {
        GeneratedProbe probe = table.newProbe(1);
        probe.putReference(1, name);
        return table.indexCount(0, probe.seal());
    }

    private static long intIndexCount(GeneratedTable table, int value) {
        GeneratedProbe probe = table.newProbe(2);
        probe.putInt(2, value);
        return table.indexCount(1, probe.seal());
    }

    private static void assertDualIndexState(
            GeneratedTable table,
            GlobalMemoryManager memory,
            Object root,
            long version,
            long retained,
            Object first,
            Object second) {
        assertSame(root, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertEquals(retained, memory.retainedBytes());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
        assertRow(table, 1L, "one", 1, first);
        assertRow(table, 2L, "two", 2, second);
        assertEquals(1L, indexCount(table, "one"));
        assertEquals(1L, indexCount(table, "two"));
        assertEquals(1L, intIndexCount(table, 1));
        assertEquals(1L, intIndexCount(table, 2));
        assertEquals(0L, indexCount(table, "moved"));
        assertEquals(0L, intIndexCount(table, 11));
    }

    private static long keyAt(Map<Long, ExpectedRow> rows, int requested) {
        int index = 0;
        for (Long key : rows.keySet()) {
            if (index++ == requested) return key.longValue();
        }
        throw new AssertionError("random expected key is missing");
    }

    private static int[] sameShardIntValues(
            GeneratedTableLayout layout,
            int required) {
        int[][] values = new int[64][required];
        int[] sizes = new int[64];
        TypedValues probe = new TypedValues(layout);
        int slot = layout.leafSlot(2);
        for (int candidate = 0; candidate < 1_000_000; candidate++) {
            probe.intValue(slot, candidate);
            int shard = (int) (layout.hashField(probe, 2) >>> 58);
            if (sizes[shard] < required) {
                values[shard][sizes[shard]++] = candidate;
                if (sizes[shard] == required) return values[shard];
            }
        }
        throw new AssertionError("unable to construct same-shard int values");
    }

    private static int intValueOutsideShard(
            GeneratedTableLayout layout,
            int referenceValue) {
        TypedValues probe = new TypedValues(layout);
        int slot = layout.leafSlot(2);
        probe.intValue(slot, referenceValue);
        int referenceShard = (int) (layout.hashField(probe, 2) >>> 58);
        for (int candidate = 0; candidate < 1_000_000; candidate++) {
            probe.intValue(slot, candidate);
            if ((int) (layout.hashField(probe, 2) >>> 58) != referenceShard) {
                return candidate;
            }
        }
        throw new AssertionError("unable to construct another-shard int value");
    }

    private static void assertExpectedState(
            GeneratedTable table,
            Map<Long, ExpectedRow> expected,
            GlobalMemoryManager memory) {
        assertEquals(expected.size(), table.size());
        Map<String, Long> nameCounts = new HashMap<String, Long>();
        Map<Integer, Long> valueCounts = new HashMap<Integer, Long>();
        for (Map.Entry<Long, ExpectedRow> entry : expected.entrySet()) {
            ExpectedRow row = entry.getValue();
            assertRow(
                    table,
                    entry.getKey().longValue(),
                    row.name,
                    row.value,
                    row.reference);
            Long names = nameCounts.get(row.name);
            nameCounts.put(row.name, names == null ? 1L : names + 1L);
            Integer value = Integer.valueOf(row.value);
            Long values = valueCounts.get(value);
            valueCounts.put(value, values == null ? 1L : values + 1L);
        }
        for (Map.Entry<String, Long> count : nameCounts.entrySet()) {
            assertEquals(count.getValue().longValue(), indexCount(table, count.getKey()));
            GeneratedProbe probe = table.newProbe(1);
            probe.putReference(1, count.getKey());
            LogicalRowPlan plan = LogicalRowPlan.indexSelection(
                    table, 0, probe.seal().snapshot(table, 1));
            assertTrue(Arrays.equals(
                    QueryOperation.referenceLocatorsForTesting(plan),
                    QueryOperation.optimizedLocatorsForTesting(plan)));
        }
        for (Map.Entry<Integer, Long> count : valueCounts.entrySet()) {
            assertEquals(count.getValue().longValue(), intIndexCount(
                    table, count.getKey().intValue()));
        }
        TableStateRoot root = table.rootForTesting();
        root.key.validateForTesting(root.directory, root.size);
        for (IdentityHashIndex index : root.indexes) {
            index.validateForTesting(root.directory, root.size);
        }
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
    }

    private static final class ExpectedRow {
        final String name;
        final int value;
        final Object reference;

        ExpectedRow(String name, int value, Object reference) {
            this.name = name;
            this.value = value;
            this.reference = reference;
        }
    }

    private static void assertRow(
            GeneratedTable table,
            long key,
            String name,
            int value,
            Object object) {
        try (GeneratedRow row = table.beginGet()) {
            row.putLong(0, key);
            row.get();
            assertEquals(key, row.readLong(0));
            assertEquals(name, row.readReference(1));
            assertEquals(value, row.readInt(2));
            assertSame(object, row.readReference(3));
        }
    }

    private static void assertMissing(GeneratedTable table, long key) {
        try (GeneratedRow row = table.beginFind()) {
            row.putLong(0, key);
            assertFalse(row.find());
        }
    }

    private static GeneratedGroup testGroup(GlobalMemoryManager memoryManager) {
        try {
            Constructor<GeneratedGroup> constructor = GeneratedGroup.class
                    .getDeclaredConstructor(
                            GlobalMemoryManager.class,
                            String.class,
                            Object.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    memoryManager, "test.generated", new Object());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static GeneratedGroup testGroup(
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor) {
        try {
            Constructor<GeneratedGroup> constructor = GeneratedGroup.class
                    .getDeclaredConstructor(
                            GlobalMemoryManager.class,
                            ForkJoinPool.class,
                            String.class,
                            Object.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    memoryManager,
                    parallelExecutor,
                    "test.generated",
                    new Object());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static GeneratedGroup testGroup(
            GlobalMemoryManager memoryManager,
            SomaCompression compression) {
        try {
            Constructor<GeneratedGroup> constructor = GeneratedGroup.class
                    .getDeclaredConstructor(
                            GlobalMemoryManager.class,
                            ForkJoinPool.class,
                            SomaCompression.class,
                            String.class,
                            Object.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    memoryManager,
                    ForkJoinPool.commonPool(),
                    compression,
                    "test.generated",
                    new Object());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static GeneratedGroup testGroup(
            GlobalMemoryManager memoryManager,
            ForkJoinPool parallelExecutor,
            SomaCompression compression) {
        try {
            Constructor<GeneratedGroup> constructor = GeneratedGroup.class
                    .getDeclaredConstructor(
                            GlobalMemoryManager.class,
                            ForkJoinPool.class,
                            SomaCompression.class,
                            String.class,
                            Object.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    memoryManager,
                    parallelExecutor,
                    compression,
                    "test.generated",
                    new Object());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class TrackingForkJoinPool extends ForkJoinPool {
        private final AtomicInteger submissions = new AtomicInteger();
        private final GlobalMemoryManager memory;
        private final AtomicLong peakTemporaryBytes = new AtomicLong();

        TrackingForkJoinPool(int parallelism) {
            this(parallelism, null);
        }

        TrackingForkJoinPool(
                int parallelism,
                GlobalMemoryManager memory) {
            super(parallelism);
            this.memory = memory;
        }

        @Override public ForkJoinTask<?> submit(Runnable task) {
            submissions.incrementAndGet();
            if (memory == null) return super.submit(task);
            return super.submit(() -> {
                observeTemporary();
                task.run();
                observeTemporary();
            });
        }

        private void observeTemporary() {
            long current = memory.temporaryBytes();
            long previous;
            do {
                previous = peakTemporaryBytes.get();
                if (current <= previous) return;
            } while (!peakTemporaryBytes.compareAndSet(previous, current));
        }
    }

    private static WeakReference<GeneratedGroup> createCollectableGroup(
            GlobalMemoryManager memoryManager) {
        try {
            Field accessField = GeneratedRuntime.class
                    .getDeclaredField("GROUP_FACTORY_ACCESS");
            accessField.setAccessible(true);
            GeneratedGroup group = GeneratedGroup.create(
                    accessField.get(null),
                    memoryManager,
                    "test.generated",
                    new Object());
            GeneratedTable table = new GeneratedTable(
                    group, testLayout(), 4, MutationFaultInjector.NONE);
            add(table, 1L, "retained", 1, new Object());
            return new WeakReference<GeneratedGroup>(group);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class SwitchableFault implements MutationFaultInjector {
        private final AtomicReference<MutationFaultPoint> point =
                new AtomicReference<MutationFaultPoint>();

        @Override
        public boolean fail(MutationFaultPoint candidate) {
            return candidate == point.get();
        }
    }

    private static final class OccurrenceFault implements MutationFaultInjector {
        private MutationFaultPoint point;
        private int occurrence;
        private int seen;

        void arm(MutationFaultPoint requestedPoint, int requestedOccurrence) {
            point = requestedPoint;
            occurrence = requestedOccurrence;
            seen = 0;
        }

        int seen() {
            return seen;
        }

        @Override
        public boolean fail(MutationFaultPoint candidate) {
            if (candidate != point) return false;
            seen++;
            return seen == occurrence;
        }
    }

    private static final class ThrowingHash {
        int calls;

        @Override
        public int hashCode() {
            calls++;
            throw new IllegalStateException("hash failure");
        }
    }

    private static final class FakeExpression
            implements SomaExpression<Object> {
        @Override public SomaExpression<Object> and(
                SomaExpression<Object> other) { return this; }
        @Override public SomaExpression<Object> or(
                SomaExpression<Object> other) { return this; }
        @Override public SomaExpression<Object> not() { return this; }
        @Override public io.github.somaruntime.soma.SomaRelationExpression and(
                io.github.somaruntime.soma.SomaRelationExpression other) {
            return this;
        }
        @Override public io.github.somaruntime.soma.SomaRelationExpression or(
                io.github.somaruntime.soma.SomaRelationExpression other) {
            return this;
        }
    }

    private static final class FakeOrder implements SomaOrder<Object> {
        @Override public SomaOrder<Object> then(SomaOrder<Object> next) {
            return this;
        }
    }
}
