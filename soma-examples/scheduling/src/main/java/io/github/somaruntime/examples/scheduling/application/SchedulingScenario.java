package io.github.somaruntime.examples.scheduling.application;

import io.github.somaruntime.examples.scheduling.domain.JobState;
import io.github.somaruntime.examples.scheduling.domain.SchedulingDecision;
import io.github.somaruntime.examples.scheduling.soma.Job;
import io.github.somaruntime.examples.scheduling.soma.JobTable;
import io.github.somaruntime.examples.scheduling.soma.Soma;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Application service for a deterministic scheduling cycle.
 *
 * <p>The service owns orchestration and compensation.  SOMA owns the keyed
 * state, index lookup, typed predicates, and table-local point publication.</p>
 */
public final class SchedulingScenario {
    private final JobTable jobs;

    public SchedulingScenario() {
        this.jobs = Soma.jobTable();
    }

    public void loadInitialState() {
        jobs.reserve(3L);
        jobs.add(new Job(101L, 1, 20, 0L, 18L, JobState.READY));
        jobs.add(new Job(102L, 1, 50, 0L, 12L, JobState.READY));
        jobs.add(new Job(103L, 2, 10, 5L, 30L, JobState.READY));
    }

    public long readyCount() {
        return jobs.filter(jobs.state.eq(JobState.READY)).count();
    }

    public long readyCountParallel() {
        return jobs.filter(jobs.state.eq(JobState.READY)).parallel().count();
    }

    public List<SchedulingDecision> candidatesForMachine(int machineId) {
        List<SchedulingDecision> decisions = jobs.byMachineId(machineId)
                .filter(jobs.state.eq(JobState.READY))
                .map(view -> new SchedulingDecision(view.jobId(), view.machineId(),
                        view.priority(), view.processingMinutes()))
                .toList();
        List<SchedulingDecision> ordered = new ArrayList<SchedulingDecision>(decisions);
        Collections.sort(ordered, new Comparator<SchedulingDecision>() {
            @Override
            public int compare(SchedulingDecision left, SchedulingDecision right) {
                int byPriority = Integer.compare(right.priority(), left.priority());
                return byPriority != 0
                        ? byPriority : Long.compare(left.jobId(), right.jobId());
            }
        });
        return ordered;
    }

    public void markRunning(long jobId) {
        jobs.update(jobId, editor -> editor.state(JobState.RUNNING));
    }

    public void complete(long jobId) {
        jobs.update(jobId, editor -> editor.state(JobState.COMPLETE));
    }

    public long remainingJobs() {
        return jobs.size();
    }
}
