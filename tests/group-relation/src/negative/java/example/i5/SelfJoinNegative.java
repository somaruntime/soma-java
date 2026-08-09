package example.i5;

final class SelfJoinNegative {
    void reject(MachineEventTable events) {
        events.join(events);
    }
}
