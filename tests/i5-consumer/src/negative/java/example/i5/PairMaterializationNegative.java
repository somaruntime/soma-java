package example.i5;

final class PairMaterializationNegative {
    void reject(MachineEventTable events, MachineStateTable states) {
        events.join(states)
                .on(events.machineId, states.machineId)
                .toList();
    }
}
