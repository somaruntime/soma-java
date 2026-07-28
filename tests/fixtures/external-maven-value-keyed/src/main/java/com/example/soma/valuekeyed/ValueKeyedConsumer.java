package com.example.soma.valuekeyed;

import com.example.soma.valuekeyed.generated.ValueKeyedMachineBatch;
import com.example.soma.valuekeyed.generated.ValueKeyedMachineTable;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

public final class ValueKeyedConsumer {
    private ValueKeyedConsumer() {
    }

    public static void main(String[] args) {
        MachineId first = new MachineId(9000000001L);
        MachineId second = new MachineId(-17L);
        ValueKeyedMachineBatch batch = new ValueKeyedMachineBatch();
        batch.addValues(first, 10).addValues(second, 20);
        ValueKeyedMachineTable table = ValueKeyedMachineTable.create();
        table.addBatch(batch);
        require(table.fetch(new MachineId(9000000001L)).payload == 10, "value key fetch");
        require(table.keys().fetchAll().get(1).equals(second), "value key export");
        expectCode("duplicate_key", new Action() { public void run() { table.addBatch(new ValueKeyedMachineBatch().addValues(new MachineId(-17L), 7)); } });
        expectCode("missing_key", new Action() { public void run() { table.fetch(new MachineId(3L)); } });
        table.delete(first);
        require(table.fetch(second).payload == 20, "value key compaction repair");
    }

    private static void expectCode(String expected, Action action) {
        try { action.run(); throw new AssertionError("expected " + expected); }
        catch (SomaRuntimeException failure) { require(expected.equals(failure.code()), failure.code()); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Action { void run(); }
}
