package example.i5;

final class OuterSelectNegative {
    void reject(MachineEventTable events, MachineStateTable states) {
        events.join(states)
                .on(events.machineId, states.machineId)
                .left()
                .select(events.eventId, states.stateId);
    }
}
