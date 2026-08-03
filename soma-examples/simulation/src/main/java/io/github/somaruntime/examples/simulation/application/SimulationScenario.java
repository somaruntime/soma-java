package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.examples.simulation.domain.EventKind;
import io.github.somaruntime.examples.simulation.domain.SimulationClock;
import io.github.somaruntime.examples.simulation.soma.SimulationEventTable;
import io.github.somaruntime.examples.simulation.soma.SimulationEvent;
import io.github.somaruntime.examples.simulation.soma.Soma;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic event-step application service.
 *
 * <p>Each terminal first materializes detached events.  The following loop
 * applies business state transitions and removes the consumed event by Key;
 * no borrowed SOMA View crosses the step boundary.</p>
 */
public final class SimulationScenario {
    private final SimulationEventTable events;
    private final SimulationClock clock;
    private final Map<Integer, Long> entityState;

    public SimulationScenario() {
        this.events = Soma.simulationEventTable();
        this.clock = new SimulationClock();
        this.entityState = new HashMap<Integer, Long>();
    }

    public void loadInitialState() {
        events.reserve(3L);
        events.add(new SimulationEvent(1L, 5L, 10, EventKind.ARRIVAL, 4L, false));
        events.add(new SimulationEvent(2L, 5L, 10, EventKind.PROCESS, 3L, false));
        events.add(new SimulationEvent(3L, 10L, 10, EventKind.DEPARTURE, -2L, false));
    }

    public long pendingAt(long minute) {
        return events.byDueMinute(minute)
                .filter(events.cancelled.eq(false))
                .count();
    }

    public void cancel(long eventId) {
        events.update(eventId, editor -> editor.cancelled(true));
    }

    public void stepTo(long minute) {
        clock.advanceTo(minute);
        List<SimulationEvent> due = events.byDueMinute(minute)
                .filter(events.cancelled.eq(false))
                .toList();
        for (SimulationEvent event : due) {
            long before = entityState.containsKey(event.entityId())
                    ? entityState.get(event.entityId()).longValue() : 0L;
            entityState.put(event.entityId(), Long.valueOf(before + event.delta()));
            events.remove(event.eventId());
        }
    }

    public long stateOf(int entityId) {
        Long value = entityState.get(entityId);
        return value == null ? 0L : value.longValue();
    }

    public long pendingEvents() {
        return events.size();
    }
}
