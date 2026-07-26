package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.dataflow.CandidateFlow;
import com.hgtech.soma.dataflow.DataFlowContext;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowInvocation;
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.DataFlowStats;
import com.hgtech.soma.dataflow.DataFlowTemplate;
import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.KeyExpression;
import com.hgtech.soma.dataflow.LongExpression;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OptionalLongResult;
import com.hgtech.soma.dataflow.OutputSlot;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentDataFlow;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentTable;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

/**
 * 从 authoritative assignment Table 推导一次 solve 的 detached metrics。
 *
 * <p>Definition/Template 是无 live state 的可复用产品契约；每个 instance 只拥有
 * 当前 session 的 sequential execution context 和一次 Invocation。</p>
 */
final class AssignmentSummaryFlow implements AutoCloseable {
  private static final Plan PLAN = Plan.create();

  private DataFlowContext context = DataFlowContext.sequential();
  private boolean consumed;

  Metrics summarize(
      OperationAssignmentTable assignments,
      int expectedAssignments,
      int expectedJobs,
      long expectedMakespan) {
    if (assignments == null) throw new NullPointerException("assignments");
    if (context == null) {
      throw new IllegalStateException(
          "assignment summary flow is closed");
    }
    if (consumed) {
      throw new IllegalStateException(
          "assignment summary flow is one-shot");
    }
    consumed = true;

    DataFlowInvocation<DataFlowResults> invocation =
        PLAN.template.newInvocation(context)
            .bind(PLAN.source,
                OperationAssignmentDataFlow.bind(assignments));
    DataFlowResults results = invocation.execute();
    Evidence evidence = Evidence.from(invocation.stats());
    GroupedLongResult jobCompletions =
        results.get(PLAN.jobCompletions);
    Tardiness tardiness = summarizeTardiness(
        assignments, jobCompletions, expectedJobs);
    return Metrics.validated(
        results.get(PLAN.assignmentCount).value(),
        results.get(PLAN.makespan),
        tardiness.total,
        tardiness.weighted,
        expectedAssignments,
        expectedMakespan,
        evidence);
  }

  private static Tardiness summarizeTardiness(
      OperationAssignmentTable assignments,
      GroupedLongResult jobCompletions,
      int expectedJobs) {
    if (jobCompletions == null) {
      throw new NullPointerException("jobCompletions");
    }
    if (expectedJobs < 0) {
      throw new IllegalArgumentException(
          "expectedJobs must be non-negative");
    }
    if (jobCompletions.size() != expectedJobs
        || jobCompletions.structuralEpoch()
            != assignments.structuralEpoch()) {
      throw new IllegalStateException(
          "job completion groups do not match current assignment facts");
    }
    LongColumnView dueMinutes = assignments.dueMinuteColumn();
    try {
      IntColumnView priorities = assignments.priorityColumn();
      try {
        long total = 0L;
        long weighted = 0L;
        for (int group = 0; group < jobCompletions.size(); group++) {
          int representative =
              jobCompletions.representativeIndexAt(group);
          long late = Math.max(0L, Math.subtractExact(
              jobCompletions.valueAt(group),
              dueMinutes.getLong(representative)));
          total = Math.addExact(total, late);
          weighted = Math.addExact(
              weighted,
              Math.multiplyExact(
                  late, (long) priorities.getInt(representative)));
        }
        return new Tardiness(total, weighted);
      } finally {
        priorities.close();
      }
    } finally {
      dueMinutes.close();
    }
  }

  private static final class Tardiness {
    final long total;
    final long weighted;

    Tardiness(long total, long weighted) {
      if (total < 0L || weighted < 0L) {
        throw new IllegalArgumentException(
            "tardiness must be non-negative");
      }
      this.total = total;
      this.weighted = weighted;
    }
  }

  @Override
  public void close() {
    if (context == null) return;
    DataFlowContext owned = context;
    context = null;
    owned.close();
  }

  static final class Metrics {
    final int assignments;
    final long makespan;
    final long totalTardiness;
    final long weightedTardiness;
    final Evidence evidence;

    private Metrics(
        int assignments,
        long makespan,
        long totalTardiness,
        long weightedTardiness,
        Evidence evidence) {
      this.assignments = assignments;
      this.makespan = makespan;
      this.totalTardiness = totalTardiness;
      this.weightedTardiness = weightedTardiness;
      this.evidence = evidence;
    }

    static Metrics validated(
        long assignmentCount,
        OptionalLongResult makespan,
        long totalTardiness,
        long weightedTardiness,
        int expectedAssignments,
        long expectedMakespan,
        Evidence evidence) {
      if (expectedAssignments < 0) {
        throw new IllegalArgumentException(
            "expectedAssignments must be non-negative");
      }
      if (expectedMakespan < 0L) {
        throw new IllegalArgumentException(
            "expectedMakespan must be non-negative");
      }
      if (makespan == null) throw new NullPointerException("makespan");
      if (evidence == null) throw new NullPointerException("evidence");
      if (assignmentCount != expectedAssignments) {
        throw new IllegalStateException(
            "assignment summary count does not cover the solve");
      }
      if (assignmentCount > Integer.MAX_VALUE) {
        throw new IllegalStateException(
            "assignment summary count exceeds application range");
      }
      if (expectedAssignments == 0) {
        if (makespan.isPresent() || expectedMakespan != 0L) {
          throw new IllegalStateException(
              "empty assignment summary has a makespan");
        }
        return new Metrics(
            0, 0L, totalTardiness, weightedTardiness, evidence);
      }
      if (!makespan.isPresent()) {
        throw new IllegalStateException(
            "non-empty assignment summary has no makespan");
      }
      long derivedMakespan = makespan.value();
      if (derivedMakespan != expectedMakespan) {
        throw new IllegalStateException(
            "assignment summary makespan disagrees with dispatch state");
      }
      if (totalTardiness < 0L || weightedTardiness < 0L) {
        throw new IllegalStateException(
            "assignment summary produced negative tardiness");
      }
      return new Metrics(
          (int) assignmentCount,
          derivedMakespan,
          totalTardiness,
          weightedTardiness,
          evidence);
    }
  }

  static final class Evidence {
    final String definitionIdentity;
    final String templateIdentity;
    final String policyIdentity;
    final long boundSources;
    final long scanned;
    final long matched;
    final long outputElements;
    final int tasks;
    final int workers;

    private Evidence(DataFlowStats stats) {
      definitionIdentity = stats.definitionIdentity();
      templateIdentity = stats.templateIdentity();
      policyIdentity = stats.policyIdentity();
      boundSources = stats.boundSources();
      scanned = stats.scanned();
      matched = stats.matched();
      outputElements = stats.outputElements();
      tasks = stats.tasks();
      workers = stats.workers();
    }

    static Evidence from(DataFlowStats stats) {
      if (stats == null) throw new NullPointerException("stats");
      if (!"SUCCESS".equals(stats.outcome())
          || !stats.failureCode().isEmpty()
          || !stats.failurePhase().isEmpty()) {
        throw new IllegalStateException(
            "assignment summary invocation did not complete cleanly");
      }
      if (stats.definitionIdentity().isEmpty()
          || stats.templateIdentity().isEmpty()
          || stats.policyIdentity().isEmpty()
          || stats.boundSources() != 1L
          || stats.scanned() < 0L
          || stats.matched() < 0L
          || stats.outputElements() < 3L) {
        throw new IllegalStateException(
            "assignment summary diagnostics are incomplete");
      }
      return new Evidence(stats);
    }
  }

  private static final class Plan {
    final OperationAssignmentDataFlow.Source source;
    final OutputSlot<LongScalarResult> assignmentCount;
    final OutputSlot<OptionalLongResult> makespan;
    final OutputSlot<GroupedLongResult> jobCompletions;
    final DataFlowTemplate<DataFlowResults> template;

    private Plan(
        OperationAssignmentDataFlow.Source source,
        OutputSlot<LongScalarResult> assignmentCount,
        OutputSlot<OptionalLongResult> makespan,
        OutputSlot<GroupedLongResult> jobCompletions,
        DataFlowTemplate<DataFlowResults> template) {
      this.source = source;
      this.assignmentCount = assignmentCount;
      this.makespan = makespan;
      this.jobCompletions = jobCompletions;
      this.template = template;
    }

    static Plan create() {
      OperationAssignmentDataFlow.Source source =
          OperationAssignmentDataFlow.source("assignments");
      CandidateFlow<OperationAssignmentDataFlow.Binding> all =
          source.candidates();
      LongExpression<OperationAssignmentDataFlow.Binding> end =
          source.columns().endMinute();
      LongExpression<OperationAssignmentDataFlow.Binding> due =
          source.columns().dueMinute();
      LongExpression<OperationAssignmentDataFlow.Binding> priority =
          source.columns().priority();
      KeyExpression<OperationAssignmentDataFlow.Binding> job =
          KeyExpression.of(
              source.columns().operationKeyJobIdValue())
              .then(due)
              .then(priority);

      DataFlowDefinition.Builder builder =
          DataFlowDefinition.builder();
      OutputSlot<LongScalarResult> assignmentCount =
          builder.output("assignment-count", all.count());
      OutputSlot<OptionalLongResult> makespan =
          builder.output("makespan", all.project(end).max());
      OutputSlot<GroupedLongResult> jobCompletions =
          builder.output(
              "job-completions",
              all.groupBy(job).max(end));
      DataFlowDefinition<DataFlowResults> definition =
          builder.build();
      return new Plan(
          source,
          assignmentCount,
          makespan,
          jobCompletions,
          definition.compile());
    }
  }
}
