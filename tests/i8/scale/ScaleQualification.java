package com.example.soma.i8;

import com.example.soma.i2.StateCode;
import com.example.soma.i2.soma.ScalarRecord;
import com.example.soma.i2.soma.ScalarRecordTable;
import com.example.soma.i2.soma.Soma;
import io.github.somaruntime.soma.IntGroupedLongResult;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime;
import io.github.somaruntime.soma.internal.ScalarTableRuntime;
import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Narrow-scale correctness and telemetry smoke; it deliberately has no performance threshold.
 */
public final class ScaleQualification {
    private static final int MACHINE_CARDINALITY = 16;
    private static final long DIAGNOSTIC_DEADLINE_MILLIS = 120000L;

    private ScaleQualification() {
    }

    public static void main(String[] args) {
        long rows;
        if (args.length == 0) {
            rows = 1000000L;
        } else {
            try {
                rows = Long.parseLong(args[0]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("rows must be a decimal long", exception);
            }
        }
        if (rows < MACHINE_CARDINALITY || rows > 5000000L) {
            throw new IllegalArgumentException("rows must be in [16, 5000000]");
        }
        System.setProperty("soma.test.chunkSize", "4096");
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            Soma.configure(SomaConfiguration.builder().parallelExecutor(pool).build());
            ScalarRecordTable table = Soma.scalarRecordTable();
            long deadlineNanos = System.nanoTime()
                    + DIAGNOSTIC_DEADLINE_MILLIS * 1000000L;
            long usedBefore = usedMemory();

            long reserveStart = System.nanoTime();
            table.reserve(rows);
            long reserveNanos = System.nanoTime() - reserveStart;

            long loadStart = System.nanoTime();
            for (long i = 0L; i < rows; i++) {
                if ((i & 4095L) == 0L && System.nanoTime() > deadlineNanos) {
                    throw new AssertionError("diagnostic deadline exceeded during scale load");
                }
                int machine = (int) (i % MACHINE_CARDINALITY);
                table.add(new ScalarRecord(
                        i, machine, "label-" + (i % 32L), (i & 1L) == 0L,
                        (byte) (i & 7L), (short) (i & 31L), (char) ('A' + (i & 3L)),
                        (int) i, (float) i, (double) i, null, StateCode.READY));
            }
            if (System.nanoTime() > deadlineNanos) {
                throw new AssertionError("diagnostic deadline exceeded after scale load");
            }
            long loadNanos = System.nanoTime() - loadStart;

            long expectedSum = Math.multiplyExact(rows, rows - 1L) / 2L;
            long expectedMachineSeven = countForMachine(rows, 7);
            long filterStart = System.nanoTime();
            long sequentialFilter = table.selectAll().filter(table.machine.eq(7)).count();
            long sequentialFilterNanos = System.nanoTime() - filterStart;

            long sumStart = System.nanoTime();
            long sum = table.selectAll().mapToLong(view -> view.count()).sum();
            long sumNanos = System.nanoTime() - sumStart;

            long parallelStart = System.nanoTime();
            long parallelFilter = table.selectAll().filter(table.machine.eq(7)).parallel().count();
            long parallelFilterNanos = System.nanoTime() - parallelStart;

            if (table.size() != rows || table.capacity() < rows) {
                throw new AssertionError("table size/capacity");
            }
            if (sequentialFilter != expectedMachineSeven || parallelFilter != expectedMachineSeven) {
                throw new AssertionError("sequential/parallel filter result");
            }
            if (sum != expectedSum) {
                throw new AssertionError("sum result");
            }
            long middleKey = rows / 2L;
            if (table.get(0L).id() != 0L
                    || table.get(middleKey).id() != middleKey
                    || table.get(rows - 1L).id() != rows - 1L
                    || table.find(rows).isPresent()) {
                throw new AssertionError("first/middle/last/missing point lookup after scale load");
            }
            IntGroupedLongResult groups = table.groupBy(table.machine).count();
            if (groups.size() != MACHINE_CARDINALITY) {
                throw new AssertionError("group cardinality");
            }
            if (table.remove(middleKey).removed() != 1L
                    || table.find(middleKey).isPresent()
                    || table.get(rows - 1L).id() != rows - 1L
                    || table.size() != rows - 1L) {
                throw new AssertionError("remove/rebuild index survivor proof");
            }
            referenceKeyFallbackProof();
            publishFailureCleanupProof();

            long usedAfter = usedMemory();
            System.out.println("profile=I8_NARROW_SCALE_SMOKE");
            System.out.println("status=PASS");
            System.out.println("rows=" + rows);
            System.out.println("capacity=" + table.capacity());
            System.out.println("stateVersion=" + table._metadata().stateVersion());
            System.out.println("reserveMillis=" + millis(reserveNanos));
            System.out.println("loadMillis=" + millis(loadNanos));
            System.out.println("sequentialFilterMillis=" + millis(sequentialFilterNanos));
            System.out.println("parallelFilterMillis=" + millis(parallelFilterNanos));
            System.out.println("sumMillis=" + millis(sumNanos));
            System.out.println("usedMemoryBeforeBytes=" + usedBefore);
            System.out.println("usedMemoryAfterBytes=" + usedAfter);
            System.out.println("maxMemoryBytes=" + Runtime.getRuntime().maxMemory());
            System.out.println("diagnosticDeadlineMillis=" + DIAGNOSTIC_DEADLINE_MILLIS);
            System.out.println("note=no-performance-threshold-or-G9-claim");
        } finally {
            pool.shutdown();
        }
    }

    private static long countForMachine(long rows, int machine) {
        return rows / MACHINE_CARDINALITY
                + (rows % MACHINE_CARDINALITY > machine ? 1L : 0L);
    }

    private static void referenceKeyFallbackProof() {
        PrimitiveLongTableRuntime.GroupRuntime group =
                new PrimitiveLongTableRuntime.GroupRuntime();
        ScalarTableRuntime runtime = new ScalarTableRuntime(group, 2L,
                new ScalarTableRuntime.FieldSpec[] {
                    new ScalarTableRuntime.FieldSpec(
                            ScalarTableRuntime.FieldKind.REFERENCE, true, false, true),
                    new ScalarTableRuntime.FieldSpec(
                            ScalarTableRuntime.FieldKind.LONG, false, false)
                }, 0);
        ScalarTableRuntime.Append first = runtime.beginAppend();
        first.setReference(0, "a").setLong(1, 10L).commit();
        ScalarTableRuntime.Append second = runtime.beginAppend();
        second.setReference(0, "b").setLong(1, 20L).commit();
        ScalarTableRuntime.Query query = runtime.beginQuery(SomaOperation.QUERY);
        try {
            if (query.findLocator("a") != 0L || query.findLocator("b") != 1L) {
                throw new AssertionError("reference-key fallback lookup");
            }
        } finally {
            query.close();
        }
        ScalarTableRuntime.Append duplicate = runtime.beginAppend();
        try {
            duplicate.setReference(0, new String("a")).setLong(1, 30L).commit();
            throw new AssertionError("reference-key duplicate accepted");
        } catch (SomaOperationException failure) {
            duplicate.abort();
            if (failure.code() != SomaFailureCode.DUPLICATE_KEY) {
                throw new AssertionError("reference-key duplicate failure", failure);
            }
        }
        ScalarTableRuntime.PointUpdate update = runtime.beginUpdate("a");
        update.setLong(1, 11L);
        update.prepare();
        update.commit();
        if (runtime.remove("a").removed() != 1L) {
            throw new AssertionError("reference-key remove");
        }
        query = runtime.beginQuery(SomaOperation.QUERY);
        try {
            if (query.findLocator("a") >= 0L || query.findLocator("b") != 0L
                    || query.longAt(1, 0L) != 20L) {
                throw new AssertionError("reference-key fallback rebuild");
            }
        } finally {
            query.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void publishFailureCleanupProof() {
        PrimitiveLongTableRuntime.GroupRuntime group =
                new PrimitiveLongTableRuntime.GroupRuntime();
        ScalarTableRuntime runtime = new ScalarTableRuntime(group, 4L,
                new ScalarTableRuntime.FieldSpec[] {
                    new ScalarTableRuntime.FieldSpec(
                            ScalarTableRuntime.FieldKind.LONG, true, false),
                    new ScalarTableRuntime.FieldSpec(
                            ScalarTableRuntime.FieldKind.REFERENCE, false, false)
                }, 0);
        runtime.reserve(4L);
        ScalarTableRuntime.Append first = runtime.beginAppend();
        first.setLong(0, 1L).setReference(1, "stable").commit();
        long beforeVersion = runtime.stateVersion();
        AtomicReference<Object> current;
        try {
            Field currentField = ScalarTableRuntime.class.getDeclaredField("current");
            currentField.setAccessible(true);
            current = (AtomicReference<Object>) currentField.get(runtime);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("test-only publish failure setup", failure);
        }
        Object oldRoot = current.get();
        ScalarTableRuntime.Append failed = runtime.beginAppend();
        current.set(null);
        try {
            failed.setLong(0, 2L).setReference(1, "transient").commit();
            throw new AssertionError("forced publish failure was accepted");
        } catch (SomaOperationException failure) {
            failed.abort();
            if (failure.code() != SomaFailureCode.CONCURRENT_GROUP_OPERATION
                    || failure.operation() != SomaOperation.ADD) {
                throw new AssertionError("unstable forced publish failure", failure);
            }
        } finally {
            current.set(oldRoot);
        }
        if (runtime.size() != 1L || runtime.stateVersion() != beforeVersion) {
            throw new AssertionError("failed append changed logical root");
        }
        ScalarTableRuntime.Query query = runtime.beginQuery(SomaOperation.QUERY);
        try {
            if (query.findLocator(Long.valueOf(2L)) >= 0L
                    || !"stable".equals(query.referenceAt(1, 0L))) {
                throw new AssertionError("failed append leaked payload or index");
            }
        } finally {
            query.close();
        }
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long millis(long nanos) {
        return nanos / 1000000L;
    }
}
