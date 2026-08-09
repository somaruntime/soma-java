package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.examples.simulation.EntityState;
import io.github.somaruntime.examples.simulation.EntityStateTable;
import io.github.somaruntime.examples.simulation.Event;
import io.github.somaruntime.examples.simulation.EventTable;
import io.github.somaruntime.examples.simulation.Soma;
import io.github.somaruntime.examples.simulation.SomaGroup;
import io.github.somaruntime.examples.simulation.schema.EventType;

public final class SimulationMain {
    private SimulationMain() {}

    public static void main(String[] args) {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(512L << 20)
                .compression(SomaCompression.AUTO)
                .build());
        SomaGroup group = Soma.createGroup();
        EventTable events = group.eventTable();
        EntityStateTable states = group.entityStateTable();
        states.add(new EntityState(1L, 10L, 0L));
        events.add(new Event(1L, 1L, 20L, 1, EventType.ADD, 5L));
        events.add(new Event(2L, 1L, 10L, 1, EventType.ADD, 3L));
        events.add(new Event(3L, 1L, 10L, 9, EventType.SUBTRACT, 2L));

        SimulationService service = new SimulationService(events, states);
        require(service.nextEvent().get().eventId() == 3L,
                "explicit event order and tie-break");
        int steps = 0;
        while (service.step()) steps++;
        require(steps == 3, "all events consumed exactly once");
        require(states.get(1L).value() == 16L
                        && states.get(1L).processedEvents() == 3L,
                "deterministic state transition");
        require(events.size() == 0L, "consumed events removed");
        System.out.println("simulation-reference: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
