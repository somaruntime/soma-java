package io.github.somaruntime.examples.scheduling.modeling;

/** Immutable machine definition. Runtime availability always starts at zero. */
public final class MachineModel {
    private final long machineId;

    public MachineModel(long machineId) {
        this.machineId = machineId;
    }

    public long machineId() { return machineId; }
}
