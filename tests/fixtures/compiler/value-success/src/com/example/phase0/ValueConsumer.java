package com.example.phase0;

import java.lang.reflect.Modifier;

public final class ValueConsumer {
    private ValueConsumer() {
    }

    public static void main(String[] args) {
        MachineId machineId = MachineId.of(7L);
        MachineId sameMachineId = new MachineId(7L);
        if (!machineId.equals(sameMachineId) || machineId.hashCode() != sameMachineId.hashCode()) {
            throw new AssertionError("MachineId value equality/hash mismatch");
        }
        if (!"MachineId{value=7}".equals(machineId.toString())) {
            throw new AssertionError("unexpected MachineId toString: " + machineId);
        }
        if (!Modifier.isFinal(MachineId.class.getModifiers())) {
            throw new AssertionError("MachineId class is not final");
        }

        double nanA = Double.longBitsToDouble(0x7ff0000000000001L);
        double nanB = Double.longBitsToDouble(0x7ff8000000000000L);
        OperationKey left = new OperationKey(
                machineId, 3, nanA, "ready", OperationState.READY);
        OperationKey right = new OperationKey(
                sameMachineId, 3, nanB, "ready", OperationState.READY);
        if (!left.equals(right) || left.hashCode() != right.hashCode()) {
            throw new AssertionError("NaN canonical equality/hash mismatch");
        }
        int expectedHash = 1;
        expectedHash = 31 * expectedHash + machineId.hashCode();
        expectedHash = 31 * expectedHash + Integer.valueOf(3).hashCode();
        long canonicalNaN = Double.doubleToLongBits(nanA);
        expectedHash = 31 * expectedHash
                + (int) (canonicalNaN ^ (canonicalNaN >>> 32));
        expectedHash = 31 * expectedHash + "ready".hashCode();
        expectedHash = 31 * expectedHash + OperationState.READY.ordinal();
        if (left.hashCode() != expectedHash) {
            throw new AssertionError("canonical enum/value hash mismatch");
        }
        if (new OperationKey(machineId, 3, -0.0d, "ready", OperationState.READY)
                .equals(new OperationKey(
                        machineId, 3, 0.0d, "ready", OperationState.READY))) {
            throw new AssertionError("ordinary value negative zero must remain distinct");
        }
        String expected = "OperationKey{machineId=MachineId{value=7}, sequence=3, "
                + "score=NaN, label=ready, state=READY}";
        if (!expected.equals(left.toString())) {
            throw new AssertionError("unexpected OperationKey toString: " + left);
        }

        try {
            new OperationKey(machineId, 3, 1.0d, "ready", null);
            throw new AssertionError("null enum value unexpectedly accepted");
        } catch (NullPointerException expectedNull) {
            if (!"state".equals(expectedNull.getMessage())) {
                throw new AssertionError("unexpected null diagnostic: " + expectedNull);
            }
        }
    }
}
