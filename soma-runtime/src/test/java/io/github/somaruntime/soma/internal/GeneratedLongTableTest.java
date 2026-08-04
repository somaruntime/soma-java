package io.github.somaruntime.soma.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.SomaRelationExpression;
import io.github.somaruntime.soma.UpdateResult;
import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GeneratedLongTableTest {

    @Test
    void pagedPrimitiveStorageSupportsZeroKeyAndTinyChunkBoundary() {
        GeneratedLongTable table = table(64L << 20, MutationFaultInjector.NONE);

        assertEquals(0L, table.size());
        assertEquals(0L, table.capacity());
        Object emptyRoot = table.rootIdentityForTesting();
        table.reserve(5L);
        assertEquals(8L, table.capacity());
        assertNotSame(emptyRoot, table.rootIdentityForTesting());
        assertEquals(1L, table.stateVersionForTesting());

        for (long key = 0L; key < 6L; key++) {
            table.add(new long[] {key, key * 10L});
        }
        assertEquals(6L, table.size());
        assertArrayEquals(new long[] {0L, 0L}, table.find(0L));
        assertArrayEquals(new long[] {5L, 50L}, table.get(5L));
        assertEquals(6L, table.count());

        SomaOperationException duplicate = assertThrows(
                SomaOperationException.class,
                () -> table.add(new long[] {0L, 99L}));
        assertEquals(SomaFailureCode.DUPLICATE_KEY, duplicate.code());
        assertEquals(SomaOperation.ADD, duplicate.operation());
        assertEquals(6L, table.size());
    }

    @Test
    void typedPipelineBindsAtTerminalAndIsOneShot() {
        GeneratedLongTable table = table(64L << 20, MutationFaultInjector.NONE);
        table.reserve(8L);
        table.add(new long[] {1L, 10L});
        table.add(new long[] {2L, 20L});

        SomaExpression<Object> atLeastTwenty = table.ge(1, 20L);
        GeneratedLongPipeline pipeline = table.filter(atLeastTwenty);
        table.add(new long[] {3L, 30L});
        assertEquals(2L, pipeline.count());

        SomaOperationException consumed = assertThrows(
                SomaOperationException.class,
                pipeline::count);
        assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, consumed.code());

        GeneratedLongPipeline parent = table.selectAll();
        GeneratedLongPipeline child = parent.filter(table.ge(1, 20L));
        SomaOperationException branched = assertThrows(
                SomaOperationException.class,
                parent::count);
        assertEquals(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, branched.code());
        assertEquals(2L, child.count());

        assertEquals(2L, table.filter(table.in(1, new long[] {30L, 10L, 10L})).count());
        assertEquals(0L, table.filter(table.in(1, new long[0])).count());
        assertEquals(1L, table.filter(
                table.gt(1, 10L).and(table.lt(1, 30L))).count());
        assertEquals(2L, table.filter(table.eq(1, 10L).or(table.eq(1, 30L))).count());
        assertEquals(2L, table.filter(table.eq(1, 20L).not()).count());

        SomaOperationException range = assertThrows(
                SomaOperationException.class,
                () -> table.between(1, 5L, 4L));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, range.code());

        GeneratedLongTable other = table(64L << 20, MutationFaultInjector.NONE);
        GeneratedLongPipeline stillOpen = table.selectAll();
        SomaOperationException foreign = assertThrows(
                SomaOperationException.class,
                () -> stillOpen.filter(other.eq(1, 1L)));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, foreign.code());
        assertEquals(3L, stillOpen.count());

        SomaOperationException applicationExpression = assertThrows(
                SomaOperationException.class,
                () -> table.filter(new ForeignExpression()));
        assertEquals(SomaFailureCode.INVALID_ARGUMENT, applicationExpression.code());
    }

    @Test
    void primitiveKeySidecarResizesAndCheckedCapacityFailsClosed() {
        int largeCapacity = 1 << 29;
        int largeThreshold = (int) (((long) largeCapacity * 5L) / 8L);
        assertTrue(LongKeyIndex.hasInsertCapacity(largeThreshold - 1, largeCapacity));
        assertFalse(LongKeyIndex.hasInsertCapacity(largeThreshold, largeCapacity));

        GeneratedGroup group = testGroup(new GlobalMemoryManager(128L << 20));
        GeneratedLongTable table = new GeneratedLongTable(
                group, "Entity", 64L, 2, 0, 64, MutationFaultInjector.NONE);
        for (long key = 0L; key < 5000L; key++) {
            table.add(new long[] {key, key * 3L});
        }
        for (long key = 0L; key < 5000L; key += 37L) {
            assertArrayEquals(new long[] {key, key * 3L}, table.get(key));
        }
        assertEquals(5000L, table.size());

        Object root = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        SomaOperationException overflow = assertThrows(
                SomaOperationException.class,
                () -> table.reserve(Long.MAX_VALUE));
        assertEquals(SomaFailureCode.ARITHMETIC_OVERFLOW, overflow.code());
        assertSame(root, table.rootIdentityForTesting());
        assertEquals(version, table.stateVersionForTesting());
    }

    @Test
    void pointUpdateStagesValuesAndEnforcesEditorScope() {
        GeneratedLongTable table = table(64L << 20, MutationFaultInjector.NONE);
        table.add(new long[] {7L, 70L});
        AtomicBoolean missingCallback = new AtomicBoolean();

        UpdateResult missing = table.update(
                8L,
                missingCallback,
                session -> missingCallback.set(true));
        assertEquals(0L, missing.matched());
        assertEquals(0L, missing.changed());
        assertFalse(missingCallback.get());

        long before = table.stateVersionForTesting();
        UpdateResult noChange = table.update(7L, this, session -> session.value(1, 70L));
        assertEquals(1L, noChange.matched());
        assertEquals(0L, noChange.changed());
        assertEquals(before, table.stateVersionForTesting());

        AtomicReference<LongEditorSession> escaped = new AtomicReference<LongEditorSession>();
        UpdateResult changed = table.update(7L, this, session -> {
            assertEquals(70L, session.value(1));
            session.value(1, 75L);
            escaped.set(session);
        });
        assertEquals(1L, changed.matched());
        assertEquals(1L, changed.changed());
        assertEquals(75L, table.get(7L)[1]);
        assertEquals(before + 1L, table.stateVersionForTesting());

        SomaOperationException scope = assertThrows(
                SomaOperationException.class,
                () -> escaped.get().value(1));
        assertEquals(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, scope.code());

        SomaOperationException reentrant = assertThrows(
                SomaOperationException.class,
                () -> table.update(7L, this, session -> table.size()));
        assertEquals(SomaFailureCode.REENTRANT_GROUP_OPERATION, reentrant.code());
        assertEquals(75L, table.get(7L)[1]);

        Exception checked = new Exception("checked callback failure");
        SomaOperationException wrapped = assertThrows(
                SomaOperationException.class,
                () -> table.update(7L, this, session -> sneakyThrow(checked)));
        assertEquals(SomaFailureCode.CALLBACK_FAILED, wrapped.code());
        assertSame(checked, wrapped.getCause());
        assertEquals(75L, table.get(7L)[1]);
    }

    @Test
    void candidateAndFinalCommitFailuresPublishNothing() {
        SwitchableFault fault = new SwitchableFault();
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedGroup group = testGroup(memory);
        GeneratedLongTable table = new GeneratedLongTable(
                group, "Entity", 4L, 2, 0, 4, fault);

        Object initialRoot = table.rootIdentityForTesting();
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());
        fault.point.set(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH);
        SomaOperationException reserveFailure = assertThrows(
                SomaOperationException.class,
                () -> table.reserve(5L));
        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, reserveFailure.code());
        assertSame(initialRoot, table.rootIdentityForTesting());
        assertEquals(0L, table.capacity());
        assertEquals(0L, table.stateVersionForTesting());
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        fault.point.set(null);
        table.reserve(4L);
        Object reservedRoot = table.rootIdentityForTesting();
        long reservedVersion = table.stateVersionForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_FINAL_COMMIT);
        assertThrows(SomaOperationException.class,
                () -> table.add(new long[] {1L, 10L}));
        assertSame(reservedRoot, table.rootIdentityForTesting());
        assertEquals(reservedVersion, table.stateVersionForTesting());
        assertEquals(0L, table.size());
        assertEquals(null, table.find(1L));
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        fault.point.set(null);
        table.add(new long[] {1L, 10L});
        Object addedRoot = table.rootIdentityForTesting();
        long addedVersion = table.stateVersionForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_FINAL_COMMIT);
        assertThrows(SomaOperationException.class,
                () -> table.update(1L, this, session -> session.value(1, 11L)));
        assertSame(addedRoot, table.rootIdentityForTesting());
        assertEquals(addedVersion, table.stateVersionForTesting());
        assertEquals(10L, table.get(1L)[1]);
        assertEquals(table.managedBytesForTesting(), memory.retainedBytes());

        GlobalMemoryManager candidateMemory = new GlobalMemoryManager(64L << 20);
        GeneratedLongTable candidateAdd = new GeneratedLongTable(
                testGroup(candidateMemory),
                "CandidateEntity",
                4L,
                2,
                0,
                4,
                fault);
        Object candidateAddRoot = candidateAdd.rootIdentityForTesting();
        fault.point.set(MutationFaultPoint.BEFORE_CANDIDATE_PUBLISH);
        assertThrows(SomaOperationException.class,
                () -> candidateAdd.add(new long[] {9L, 90L}));
        assertSame(candidateAddRoot, candidateAdd.rootIdentityForTesting());
        assertEquals(0L, candidateAdd.size());
        assertEquals(candidateAdd.managedBytesForTesting(), candidateMemory.retainedBytes());
    }

    @Test
    void missingPointUpdateDoesNotAdmitTemporaryMemoryOrInvokeCallback() {
        GlobalMemoryManager memory = new GlobalMemoryManager(64L << 20);
        GeneratedLongTable table = new GeneratedLongTable(
                testGroup(memory),
                "Entity",
                4L,
                2,
                0,
                4,
                MutationFaultInjector.NONE);
        table.add(new long[] {1L, 10L});
        Object root = table.rootIdentityForTesting();
        long version = table.stateVersionForTesting();
        AtomicBoolean callback = new AtomicBoolean();

        long remaining = memory.budgetBytes() - memory.retainedBytes();
        try (GlobalMemoryManager.TemporaryLease ignored = memory.leaseTemporary(
                remaining, SomaOperation.UPDATE, new Object())) {
            UpdateResult missing = table.update(
                    2L,
                    callback,
                    session -> callback.set(true));
            assertEquals(0L, missing.matched());
            assertEquals(0L, missing.changed());
            assertFalse(callback.get());
            assertSame(root, table.rootIdentityForTesting());
            assertEquals(version, table.stateVersionForTesting());

            SomaOperationException admitted = assertThrows(
                    SomaOperationException.class,
                    () -> table.update(1L, callback, session -> callback.set(true)));
            assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, admitted.code());
            assertFalse(callback.get());
        }
    }

    @Test
    void budgetFailureAndConcurrentGroupAdmissionAreFailClosed() throws Exception {
        GeneratedLongTable constrained = table(1024L, MutationFaultInjector.NONE);
        Object root = constrained.rootIdentityForTesting();
        SomaOperationException resource = assertThrows(
                SomaOperationException.class,
                () -> constrained.reserve(1L));
        assertEquals(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, resource.code());
        assertSame(root, constrained.rootIdentityForTesting());

        GeneratedLongTable table = table(64L << 20, MutationFaultInjector.NONE);
        table.add(new long[] {1L, 1L});
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                table.update(1L, this, session -> {
                    entered.countDown();
                    try {
                        if (!release.await(5L, TimeUnit.SECONDS)) {
                            throw new AssertionError("test release timeout");
                        }
                    } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                    }
                });
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        });
        worker.start();
        assertTrue(entered.await(5L, TimeUnit.SECONDS));
        SomaOperationException concurrent = assertThrows(
                SomaOperationException.class,
                table::size);
        assertEquals(SomaFailureCode.CONCURRENT_GROUP_OPERATION, concurrent.code());
        release.countDown();
        worker.join(5000L);
        assertFalse(worker.isAlive());
        assertEquals(null, workerFailure.get());
        assertEquals(1L, table.size());
    }

    @Test
    void oversizedDefaultHintFallsBackAndRandomizedReferenceRemainsEquivalent() {
        GeneratedGroup hintGroup = testGroup(new GlobalMemoryManager(1L << 20));
        GeneratedLongTable hinted = new GeneratedLongTable(
                hintGroup,
                "Hinted",
                1_000_000L,
                2,
                0,
                4,
                MutationFaultInjector.NONE);
        hinted.add(new long[] {1L, 2L});
        assertEquals(1L, hinted.size());
        assertEquals(4L, hinted.capacity());

        GeneratedGroup group = testGroup(new GlobalMemoryManager(128L << 20));
        GeneratedLongTable table = new GeneratedLongTable(
                group, "Entity", 8L, 2, 0, 8, MutationFaultInjector.NONE);
        Map<Long, Long> reference = new LinkedHashMap<Long, Long>();
        Random random = new Random(20260804L);
        for (int step = 0; step < 1000; step++) {
            long key = random.nextInt(250);
            if (!reference.containsKey(key)) {
                long value = random.nextInt(1000);
                table.add(new long[] {key, value});
                reference.put(key, value);
            } else if ((step & 1) == 0) {
                long value = random.nextInt(1000);
                UpdateResult result = table.update(
                        key, this, editor -> editor.value(1, value));
                long old = reference.get(key);
                assertEquals(old == value ? 0L : 1L, result.changed());
                reference.put(key, value);
            } else {
                assertEquals(reference.get(key).longValue(), table.get(key)[1]);
            }

            if (step % 41 == 0) {
                long threshold = random.nextInt(1000);
                long expected = 0L;
                for (long value : reference.values()) {
                    if (value >= threshold) {
                        expected++;
                    }
                }
                assertEquals(expected, table.filter(table.ge(1, threshold)).count());
            }
        }
        assertEquals(reference.size(), table.size());
        for (Map.Entry<Long, Long> entry : reference.entrySet()) {
            assertArrayEquals(
                    new long[] {entry.getKey(), entry.getValue()},
                    table.get(entry.getKey()));
        }
    }

    private static GeneratedLongTable table(
            long budget,
            MutationFaultInjector faultInjector) {
        GeneratedGroup group = testGroup(new GlobalMemoryManager(budget));
        return new GeneratedLongTable(
                group, "Entity", 4L, 2, 0, 4, faultInjector);
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

    private static void sneakyThrow(Throwable failure) {
        GeneratedLongTableTest.<RuntimeException>throwUnchecked(failure);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
        throw (T) failure;
    }

    private static final class SwitchableFault implements MutationFaultInjector {

        private final AtomicReference<MutationFaultPoint> point =
                new AtomicReference<MutationFaultPoint>();

        @Override
        public boolean fail(MutationFaultPoint candidate) {
            return point.get() == candidate;
        }
    }

    private static final class ForeignExpression implements SomaExpression<Object> {

        @Override
        public SomaExpression<Object> and(SomaExpression<Object> other) {
            return this;
        }

        @Override
        public SomaRelationExpression and(SomaRelationExpression other) {
            return this;
        }

        @Override
        public SomaExpression<Object> or(SomaExpression<Object> other) {
            return this;
        }

        @Override
        public SomaRelationExpression or(SomaRelationExpression other) {
            return this;
        }

        @Override
        public SomaExpression<Object> not() {
            return this;
        }
    }
}
