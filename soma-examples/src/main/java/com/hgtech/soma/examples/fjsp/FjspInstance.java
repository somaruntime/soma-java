package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.OperationAssignment;
import com.hgtech.soma.examples.fjsp.schema.generated.JobDefinitionTable;
import com.hgtech.soma.examples.fjsp.schema.generated.JobResultTable;
import com.hgtech.soma.examples.fjsp.schema.generated.JobRuntimeStateTable;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateTable;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.fjsp.schema.generated.SetupTimeTable;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.RuntimePlan;

import java.util.List;

/** 一个求解过程的全部 SOMA runtime state 与生命周期 owner。 */
public final class FjspInstance implements AutoCloseable {
  final int operationCount;
  final int jobCount;
  final JobDefinitionTable jobs;
  final JobRuntimeStateTable jobStates;
  final JobResultTable jobResults;
  final OperationDefinitionTable definitions;
  final OperationRuntimeStateTable operationStates;
  final OperationAssignmentTable assignments;
  final MachineTable machines;
  final SetupTimeTable setupTimes;
  final MachineCandidateTable frontier;
  private boolean closed;

  FjspInstance(int operationCount, int jobCount, RuntimePlan plan) {
    this.operationCount = operationCount;
    this.jobCount = jobCount;
    jobs = JobDefinitionTable.create(plan);
    jobStates = JobRuntimeStateTable.create(plan);
    jobResults = JobResultTable.create(plan);
    definitions = OperationDefinitionTable.create(plan);
    operationStates = OperationRuntimeStateTable.create(plan);
    assignments = OperationAssignmentTable.create(plan);
    machines = MachineTable.create(plan);
    setupTimes = SetupTimeTable.create(plan);
    frontier = MachineCandidateTable.create(plan);
  }

  public List<OperationAssignment> exportAssignments() {
    return assignments.fetchAll(MaterializationBudget.defaults());
  }

  public String schemaHash() {
    return frontier.runtimePlan().schemaHash();
  }

  public String runtimePlanHash() {
    return frontier.runtimePlan().runtimePlanHash();
  }

  public long sidecarRebuildCount() {
    return frontier.statsSnapshot().sidecarRebuildCount();
  }

  public int assignmentCapacity() {
    return assignments.capacity();
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    frontier.release();
    setupTimes.release();
    machines.release();
    assignments.release();
    operationStates.release();
    definitions.release();
    jobResults.release();
    jobStates.release();
    jobs.release();
  }
}
