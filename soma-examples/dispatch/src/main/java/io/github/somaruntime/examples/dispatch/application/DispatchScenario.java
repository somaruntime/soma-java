package io.github.somaruntime.examples.dispatch.application;

import io.github.somaruntime.examples.dispatch.domain.DispatchDecision;
import io.github.somaruntime.examples.dispatch.domain.DispatchStatus;
import io.github.somaruntime.examples.dispatch.soma.PendingDispatch;
import io.github.somaruntime.examples.dispatch.soma.PendingDispatchTable;
import io.github.somaruntime.examples.dispatch.soma.Soma;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Low-allocation dispatch application service.
 *
 * <p>Index selection and typed filtering stay in SOMA.  The detached list is
 * deliberately small and is sorted by application policy before a single
 * point update publishes the assignment.</p>
 */
public final class DispatchScenario {
    private final PendingDispatchTable pending;

    public DispatchScenario() {
        this.pending = Soma.pendingDispatchTable();
    }

    public void loadInitialState() {
        pending.reserve(3L);
        pending.add(new PendingDispatch(201L, 4, 100, 0L, 8L, DispatchStatus.READY));
        pending.add(new PendingDispatch(202L, 4, 80, 0L, 5L, DispatchStatus.READY));
        pending.add(new PendingDispatch(203L, 5, 90, 1L, 11L, DispatchStatus.READY));
    }

    public long readyCount() {
        return pending.filter(pending.status.eq(DispatchStatus.READY)).count();
    }

    public long readyCountParallel() {
        return pending.filter(pending.status.eq(DispatchStatus.READY)).parallel().count();
    }

    public DispatchDecision choose(int machineId) {
        List<DispatchDecision> values = pending.byMachineId(machineId)
                .filter(pending.status.eq(DispatchStatus.READY))
                .map(view -> new DispatchDecision(view.requestId(), view.machineId(),
                        view.priority(), view.processingMinutes()))
                .toList();
        List<DispatchDecision> ordered = new ArrayList<DispatchDecision>(values);
        Collections.sort(ordered, new Comparator<DispatchDecision>() {
            @Override
            public int compare(DispatchDecision left, DispatchDecision right) {
                int byPriority = Integer.compare(right.priority(), left.priority());
                return byPriority != 0
                        ? byPriority : Long.compare(left.requestId(), right.requestId());
            }
        });
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    public void assign(DispatchDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        pending.update(decision.requestId(), editor -> editor.status(DispatchStatus.ASSIGNED));
    }

    public void complete(DispatchDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        pending.update(decision.requestId(), editor -> editor.status(DispatchStatus.COMPLETED));
    }
}
