package example.i2;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class I2ConsumerMain {

    private I2ConsumerMain() {
    }

    public static void main(String[] arguments) throws Exception {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(512L << 20)
                .build());

        AllTypesTable table = concurrentDefaultFirstAccessor();
        require(table == Soma.defaultGroup().allTypesTable(), "default Group routing");
        require(table == Soma.allTypesTable(), "default Table identity");
        require(table != Soma.createGroup().allTypesTable(), "explicit Group isolation");
        assertConcurrentExplicitFirstAccessor();

        MachinePair firstKey = new MachinePair(
                new MachineId(1L), new MachineId(2L), null, Status.NEW);
        MachinePair secondKey = new MachinePair(
                new MachineId(3L), new MachineId(4L), "pair", null);
        Payload payload = new Payload("identity");
        byte[] bytes = new byte[] {1, 2};
        List<String> tags = new ArrayList<String>();
        tags.add("tag");

        table.add(new AllTypes(
                firstKey, "FB", new MachineId(7L),
                true, (byte) 8, (short) 9, 'x', 10, 11L,
                Float.intBitsToFloat(0x7f800001), -0.0d,
                Status.ACTIVE, payload, bytes, tags));
        table.add(new AllTypes(
                secondKey, "Ea", new MachineId(7L),
                false, (byte) 1, (short) 2, 'y', 3, 4L,
                1.5f, 2.5d, null, null, null, null));
        table.add(new AllTypes(
                new MachinePair(new MachineId(5L), new MachineId(6L), null, Status.DONE),
                null, new MachineId(8L),
                false, (byte) 0, (short) 0, '\0', 0, 0L,
                0.0f, 0.0d, Status.DONE, null, null, null));

        AllTypes detached = table.get(firstKey);
        require(detached.enabled(), "boolean round trip");
        require(detached.byteValue() == (byte) 8, "byte round trip");
        require(detached.shortValue() == (short) 9, "short round trip");
        require(detached.charValue() == 'x', "char round trip");
        require(detached.intValue() == 10, "int round trip");
        require(detached.longValue() == 11L, "long round trip");
        require(Float.isNaN(detached.ratio()), "float round trip");
        require(Double.doubleToLongBits(detached.weight())
                == Double.doubleToLongBits(-0.0d), "double round trip");
        require(detached.status() == Status.ACTIVE, "Enum identity round trip");
        require(detached.payload() == payload, "ordinary Object identity");
        require(detached.bytes() == bytes, "array identity");
        require(detached.tags() == tags, "parameterized Object identity");
        require(detached.key().equals(firstKey), "nested Value materialization");
        require(table.byName("FB").count() == 1L, "String hash collision left");
        require(table.byName("Ea").count() == 1L, "String hash collision right");
        require(table.byName(null).count() == 1L, "nullable Index");
        require(table.byMachineId(new MachineId(7L)).count() == 2L,
                "repeated Value Index");
        require(table.filter(table.ratio.eq(Float.NaN)).count() == 1L,
                "canonical NaN equality");
        require(table.filter(table.weight.eq(+0.0d)).count() == 1L,
                "signed zero distinction");

        UpdateResult updated = table.update(firstKey, editor -> {
            editor.name(null);
            editor.machineId(new MachineId(8L));
            editor.longValue(12L);
        });
        require(updated.matched() == 1L && updated.changed() == 1L,
                "point update result");
        require(table.byName("FB").count() == 0L, "Index old value removed");
        require(table.byName(null).count() == 2L, "Index null moved");
        require(table.byMachineId(new MachineId(8L)).count() == 2L,
                "Value Index moved");

        RemoveResult removed = table.remove(secondKey);
        require(removed.removed() == 1L, "point remove result");
        require(table.remove(secondKey).removed() == 0L, "missing remove result");
        require(table.size() == 2L, "remove size");
        require(!table.find(secondKey).isPresent(), "removed Key absent");
        long beforeInvalid = table.size();
        try {
            table.add(new AllTypes());
            throw new AssertionError("null Value Key was accepted");
        } catch (SomaOperationException failure) {
            require(failure.code() == SomaFailureCode.INVALID_ARGUMENT,
                    "null Key failure code");
        }
        require(table.size() == beforeInvalid, "null Key published state");

        StringKeyRecordTable stringKeys = Soma.stringKeyRecordTable();
        stringKeys.add(new StringKeyRecord(new String("FB"), 1L));
        stringKeys.add(new StringKeyRecord(new String("Ea"), 2L));
        require(stringKeys.get("FB").value() == 1L, "Key hash collision left");
        require(stringKeys.get("Ea").value() == 2L, "Key hash collision right");
        try {
            stringKeys.add(new StringKeyRecord(null, 3L));
            throw new AssertionError("null String Key was accepted");
        } catch (SomaOperationException failure) {
            require(failure.code() == SomaFailureCode.INVALID_ARGUMENT,
                    "null String Key failure code");
        }

        LogEntryTable keyless = Soma.logEntryTable();
        keyless.add(new LogEntry(10L, "first"));
        keyless.add(new LogEntry(20L, null));
        require(keyless.size() == 2L, "keyless sequential Table");

        EligibleMachineTable relation = Soma.eligibleMachineTable();
        relation.add(new EligibleMachine(new JobId(1L), new MachineId(7L), 10L));
        relation.add(new EligibleMachine(new JobId(1L), new MachineId(8L), 11L));
        relation.add(new EligibleMachine(new JobId(2L), new MachineId(7L), 12L));
        require(relation.byJobId(new JobId(1L)).count() == 2L,
                "relation left endpoint Index");
        require(relation.byMachineId(new MachineId(7L)).count() == 2L,
                "relation right endpoint Index");

        assertKeyAndIndexCapabilityMatrix();
        assertSingleReferenceLeafValueSemantics();

        System.out.println("i2-consumer: ok");
    }

    private static void assertKeyAndIndexCapabilityMatrix() {
        BooleanKeyRecordTable booleanKeys = Soma.booleanKeyRecordTable();
        booleanKeys.add(new BooleanKeyRecord(false, 1L));
        require(booleanKeys.get(false).value() == 1L, "boolean Key");

        ByteKeyRecordTable byteKeys = Soma.byteKeyRecordTable();
        byteKeys.add(new ByteKeyRecord((byte) 2, 2L));
        require(byteKeys.get((byte) 2).value() == 2L, "byte Key");

        ShortKeyRecordTable shortKeys = Soma.shortKeyRecordTable();
        shortKeys.add(new ShortKeyRecord((short) 3, 3L));
        require(shortKeys.get((short) 3).value() == 3L, "short Key");

        CharKeyRecordTable charKeys = Soma.charKeyRecordTable();
        charKeys.add(new CharKeyRecord('k', 4L));
        require(charKeys.get('k').value() == 4L, "char Key");

        IntKeyRecordTable intKeys = Soma.intKeyRecordTable();
        intKeys.add(new IntKeyRecord(5, 5L));
        require(intKeys.get(5).value() == 5L, "int Key");

        EnumKeyRecordTable enumKeys = Soma.enumKeyRecordTable();
        enumKeys.add(new EnumKeyRecord(Status.DONE, 6L));
        require(enumKeys.get(Status.DONE).value() == 6L, "Enum Key");

        KeyabilityIndexMatrixTable indexes = Soma.keyabilityIndexMatrixTable();
        FloatingValue floating = new FloatingValue(Float.NaN, -0.0d);
        indexes.add(new KeyabilityIndexMatrix(
                true, (byte) 7, (short) 8, 'm', 9, 10L,
                "indexed", Status.ACTIVE, new MachineId(11L), floating));
        indexes.add(new KeyabilityIndexMatrix(
                false, (byte) 0, (short) 0, '\0', 0, 0L,
                null, null, new MachineId(0L), new FloatingValue(1.0f, 2.0d)));
        require(indexes.byBooleanValue(true).count() == 1L, "boolean Index");
        require(indexes.byByteValue((byte) 7).count() == 1L, "byte Index");
        require(indexes.byShortValue((short) 8).count() == 1L, "short Index");
        require(indexes.byCharValue('m').count() == 1L, "char Index");
        require(indexes.byIntValue(9).count() == 1L, "int Index");
        require(indexes.byLongValue(10L).count() == 1L, "long Index");
        require(indexes.byStringValue("indexed").count() == 1L, "String Index");
        require(indexes.byStatus(Status.ACTIVE).count() == 1L, "Enum Index");
        require(indexes.byStatus(null).count() == 1L, "nullable Enum Index");
        require(indexes.byMachineId(new MachineId(11L)).count() == 1L, "Value Index");
        require(indexes.filter(indexes.floatingValue.eq(
                new FloatingValue(Float.NaN, -0.0d))).count() == 1L,
                "float/double Value payload equality");
    }

    private static void assertSingleReferenceLeafValueSemantics() {
        NullableCodeRecordTable table = Soma.nullableCodeRecordTable();
        NullableCode nullLeaf = new NullableCode(null);
        table.add(new NullableCodeRecord(nullLeaf, new NullableCode(null), 17L));
        require(table.get(new NullableCode(null)).value() == 17L,
                "single-reference Value Key");
        require(table.byCode(new NullableCode(null)).count() == 1L,
                "single-reference Value Index");
        require(table.filter(table.code.eq(new NullableCode(null))).count() == 1L,
                "single-reference Value eq");
        require(table.filter(table.code.in(new NullableCode(null))).count() == 1L,
                "single-reference Value in");
    }

    private static AllTypesTable concurrentDefaultFirstAccessor() throws Exception {
        final AllTypesTable[] observed = new AllTypesTable[16];
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[observed.length];
        for (int index = 0; index < workers.length; index++) {
            final int slot = index;
            workers[index] = new Thread(() -> {
                try {
                    observed[slot] = Soma.allTypesTable();
                } catch (Throwable problem) {
                    failure.compareAndSet(null, problem);
                }
            });
            workers[index].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        require(failure.get() == null, "concurrent default accessor failure");
        for (AllTypesTable value : observed) {
            require(value == observed[0], "concurrent default first accessor identity");
        }
        return observed[0];
    }

    private static void assertConcurrentExplicitFirstAccessor() throws Exception {
        final SomaGroup group = Soma.createGroup();
        final AllTypesTable[] observed = new AllTypesTable[16];
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[observed.length];
        for (int index = 0; index < workers.length; index++) {
            final int slot = index;
            workers[index] = new Thread(() -> {
                try {
                    observed[slot] = group.allTypesTable();
                } catch (Throwable problem) {
                    failure.compareAndSet(null, problem);
                }
            });
            workers[index].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        require(failure.get() == null, "concurrent explicit accessor failure");
        for (AllTypesTable value : observed) {
            require(value == observed[0], "concurrent first accessor identity");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
