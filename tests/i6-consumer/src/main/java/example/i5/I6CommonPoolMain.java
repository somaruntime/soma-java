package example.i5;

public final class I6CommonPoolMain {
    private I6CommonPoolMain() {}

    public static void main(String[] args) {
        SomaGroup group = Soma.createGroup();
        MachineEventTable events = group.machineEventTable();
        for (int index = 0; index < 8_500; index++) {
            events.add(new MachineEvent(
                    index + 1L, index & 7, "R", index, true));
        }
        long count = events.parallel()
                .filter(events.duration.ge(4_250L))
                .count();
        if (count != 4_250L) {
            throw new AssertionError("commonPool parallel count: " + count);
        }
    }
}
