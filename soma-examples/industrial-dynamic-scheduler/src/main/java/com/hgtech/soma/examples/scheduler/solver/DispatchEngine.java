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
  private AssignmentSummaryFlow.Evidence summaryEvidence;
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
    AssignmentSummaryFlow summaryFlow = null;
    try {
      summaryFlow = new AssignmentSummaryFlow();
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
      AssignmentSummaryFlow.Metrics metrics =
          summaryFlow.summarize(
              runtime.assignments(),
              runtime.operationCount(),
              runtime.jobCount(),
              committer.makespan());
      summaryEvidence = metrics.evidence;
      return new DispatchSummary(
          metrics.assignments,
          committer.completedJobs(),
          metrics.makespan,
          metrics.totalTardiness,
          metrics.weightedTardiness,
          eventProcessor.processedEvents());
    } finally {
      try {
        if (summaryFlow != null) summaryFlow.close();
      } finally {
        frontier.close();
      }
    }
  }

  AssignmentSummaryFlow.Evidence summaryEvidence() {
    if (summaryEvidence == null) {
      throw new IllegalStateException(
          "assignment summary evidence is unavailable");
    }
    return summaryEvidence;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
