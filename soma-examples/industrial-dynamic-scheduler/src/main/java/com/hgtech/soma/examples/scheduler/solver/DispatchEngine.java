package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;

/**
 * 单次 solve 的 orchestration state machine。
 *
 * <p>跨 Table authoritative write 失败继续采用 fail-stop 语义。</p>
 */
final class DispatchEngine {
  private final SchedulerRuntime runtime;
  private final CandidateFrontier frontier;
  private final ExternalEventProcessor eventProcessor;
  private final AssignmentCommitter committer;
  private boolean solved;

  DispatchEngine(SchedulerRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    this.runtime = runtime;
    frontier = new CandidateFrontier(runtime);
    eventProcessor = new ExternalEventProcessor(runtime, frontier);
    committer = new AssignmentCommitter(runtime, frontier);
  }

  DispatchSummary solve() {
    if (solved) throw new IllegalStateException("engine is one-shot");
    solved = true;
    try {
      while (runtime.assignmentSize() < runtime.operationCount()) {
        if (frontier.isEmpty()) {
          require(eventProcessor.hasPending(),
              "unscheduled operations remain without event or candidate");
          eventProcessor.processThrough(eventProcessor.nextMinute());
          continue;
        }
        SelectedCandidate selected = frontier.select();
        long setupStart = Math.subtractExact(
            selected.effectiveStartMinute, selected.setupMinutes);
        if (eventProcessor.hasPending()
            && eventProcessor.nextMinute() <= setupStart) {
          eventProcessor.processThrough(eventProcessor.nextMinute());
          continue;
        }
        frontier.revalidate(selected);
        committer.commit(selected, setupStart);
      }
      eventProcessor.processThrough(committer.makespan());
      require(frontier.isEmpty(),
          "frontier must be empty after all assignments");
      require(committer.completedJobs() == runtime.jobCount(),
          "all jobs must be completed");
      return new DispatchSummary(
          runtime.assignmentSize(),
          committer.completedJobs(),
          committer.makespan(),
          committer.totalTardiness(),
          committer.weightedTardiness(),
          eventProcessor.processedEvents());
    } finally {
      frontier.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
