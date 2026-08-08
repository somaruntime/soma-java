package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeneratedTableTest {

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
        PlainChunk chunk = root.directory.plainChunk(0L);
        int nameSlot = testLayout().leafSlot(1);
        int objectSlot = testLayout().leafSlot(3);
        assertNull(chunk.references(nameSlot)[2]);
        assertNull(chunk.references(objectSlot)[2]);

        IdentityHashIndex index = root.indexes[0];
        GeneratedProbe probe = table.newProbe(1);
        probe.putReference(1, "same");
        probe.seal();
        long first = index.first(root.directory, probe);
        assertEquals(0L, first);
        assertEquals(1L, index.next(first));
        assertEquals(-1L, index.next(1L));

        assertEquals(0L, remove(table, 2L).removed());
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
        PlainChunk formerTail = root.directory.plainChunk(2L);
        assertNull(formerTail.references(testLayout().leafSlot(1))[1]);
        assertNull(formerTail.references(testLayout().leafSlot(3))[1]);
    }

    @Test
    void pagedDirectoryCrossesLeafBoundaryWithKeyIndexAndCompactionIntact() {
        GeneratedTable table = table(64L << 20, MutationFaultInjector.NONE);
        long rows = 257L * 4L;
        table.reserve(rows);
        Object beforeBoundaryReference = null;
        Object atBoundaryReference = null;
        Object tailReference = null;
        for (long value = 0L; value < rows; value++) {
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

        PlainChunk formerTail = table.rootForTesting().directory.plainChunk(256L);
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
        table.reserve(4L);
        Object reserved = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_FINAL_COMMIT);
        assertThrows(SomaOperationException.class,
                () -> add(table, 1L, "one", 1, new Object()));
        assertSame(reserved, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
        assertEquals(0L, table.size());

        fault.point.set(null);
        Object beforeOverflow = table.rootIdentityForTesting();
        SomaOperationException overflow = assertThrows(
                SomaOperationException.class,
                () -> table.reserve(Long.MAX_VALUE));
        assertEquals(SomaFailureCode.ARITHMETIC_OVERFLOW, overflow.code());
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
    void keyAndEveryIndexRebuildFailurePreservePayloadSidecarsAndAccounting() {
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

        fault.arm(MutationFaultPoint.BEFORE_INDEX_REBUILD, 1);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_INDEX_REBUILD, 2);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_SIDECAR_ACCOUNTING, 1);
        assertThrows(SomaOperationException.class,
                () -> update(table, 1L, "moved", 11, first));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_KEY_REBUILD, 1);
        assertThrows(SomaOperationException.class, () -> remove(table, 2L));
        assertDualIndexState(table, memory, root, version, retained, first, second);

        fault.arm(MutationFaultPoint.BEFORE_INDEX_REBUILD, 2);
        assertThrows(SomaOperationException.class, () -> remove(table, 2L));
        assertDualIndexState(table, memory, root, version, retained, first, second);
    }

    @Test
    void structuralAccountingAndVirtualLongBoundaryFailBeforeAllocation() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedTable table = new GeneratedTable(
                testGroup(memory), testLayout(), 4, MutationFaultInjector.NONE);
        table.reserve(8L);
        assertEquals(67_904L, table.managedBytesForTesting());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        Object published = table.rootIdentityForTesting();
        SomaOperationException overInt = assertThrows(
                SomaOperationException.class,
                () -> table.reserve((long) Integer.MAX_VALUE + 1L));
        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, overInt.code());
        assertSame(published, table.rootIdentityForTesting());
        assertEquals(8L, table.capacity());
        assertEquals(67_904L, memory.retainedBytes());

        long virtualChunks = (long) Integer.MAX_VALUE + 1L;
        assertTrue(TableChunkDirectory.estimatedDirectoryBytes(
                virtualChunks, io.github.somaruntime.soma.SomaOperation.RESERVE,
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

    private static GeneratedTable floatingTable() {
        GeneratedTableLayout layout = GeneratedTableLayout.create(
                "Floating",
                4L,
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

    private static GeneratedTableLayout testLayout() {
        return GeneratedTableLayout.create(
                "Entity",
                4L,
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

    private static GeneratedTableLayout dualIndexLayout() {
        return GeneratedTableLayout.create(
                "DualIndexEntity",
                4L,
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

        @Override
        public boolean fail(MutationFaultPoint candidate) {
            if (candidate != point) return false;
            seen++;
            return seen == occurrence;
        }
    }
}
