package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.examples.simulation.EntityState;
import io.github.somaruntime.examples.simulation.EntityStateTable;
import io.github.somaruntime.examples.simulation.Event;
import io.github.somaruntime.examples.simulation.EventTable;
import io.github.somaruntime.examples.simulation.schema.EventType;
import java.util.Optional;

public final class SimulationService {
    private final EventTable events;
    private final EntityStateTable states;

    public SimulationService(EventTable events, EntityStateTable states) {
        this.events = events;
        this.states = states;
    }

    public Optional<Event> nextEvent() {
        return events.sortedBy(
                events.eventMinute.asc()
                        .then(events.priority.desc())
                        .then(events.eventId.asc()))
                .findFirst();
    }

    public boolean step() {
        Optional<Event> candidate = nextEvent();
        if (!candidate.isPresent()) return false;
        Event event = candidate.get();
        EntityState previous = states.get(event.entityId());
        long signedDelta = event.eventType() == EventType.ADD
                ? event.delta()
                : Math.negateExact(event.delta());
        states.update(event.entityId(), editor -> {
            editor.value(Math.addExact(editor.value(), signedDelta));
            editor.processedEvents(Math.addExact(editor.processedEvents(), 1L));
        });
        RemoveResult removed = events.remove(event.eventId());
        if (removed.removed() != 1L) {
            states.update(event.entityId(), editor -> {
                editor.value(previous.value());
                editor.processedEvents(previous.processedEvents());
            });
            throw new IllegalStateException("consumed Event disappeared before removal");
        }
        return true;
    }
}
