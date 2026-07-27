package com.hgtech.soma.examples.rtd.rule;

import com.hgtech.soma.dataflow.AbsenceOrder;
import com.hgtech.soma.dataflow.CandidateFlow;
import com.hgtech.soma.dataflow.DataFlowDefinition;
import com.hgtech.soma.dataflow.DataFlowResults;
import com.hgtech.soma.dataflow.DataFlowTemplate;
import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.JoinedFlow;
import com.hgtech.soma.dataflow.JoinedIndexResult;
import com.hgtech.soma.dataflow.JoinedOrder;
import com.hgtech.soma.dataflow.KeyExpression;
import com.hgtech.soma.dataflow.LongScalarResult;
import com.hgtech.soma.dataflow.OutputSlot;
import com.hgtech.soma.dataflow.ParameterSlot;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateDataFlow;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateDataFlow;

/** 可跨 runtime 重用、且不持有 live Table 的 dispatch-rule Template。 */
public final class DispatchRulePlan {
  static final ParameterSlot<Long> NOW =
      ParameterSlot.of(0, "currentMinute", Long.class);

  final WorkStateDataFlow.Source work =
      WorkStateDataFlow.source(0, "work");
  final ResourceStateDataFlow.Source resources =
      ResourceStateDataFlow.source(1, "resources");
  final OutputSlot<LongScalarResult> readyCount;
  final OutputSlot<GroupedLongResult> demandByCapability;
  final OutputSlot<JoinedIndexResult> rankedPairs;
  final DataFlowDefinition<DataFlowResults> definition;
  final DataFlowTemplate<DataFlowResults> template;
  final DataFlowDefinition<LongScalarResult> admittedLoadDefinition;
  final DataFlowTemplate<LongScalarResult> admittedLoadTemplate;

  public DispatchRulePlan() {
    CandidateFlow<WorkStateDataFlow.Binding> ready =
        work.candidatesByStatus(WorkStatus.PENDING)
            .filter(work.columns().releaseMinute()
                .lessThanOrEqualTo(work.longParameter(NOW)));
    CandidateFlow<ResourceStateDataFlow.Binding> available =
        resources.candidates()
            .filter(resources.columns().enabled()
                .and(resources.columns().availableMinute()
                    .lessThanOrEqualTo(
                        resources.longParameter(NOW))));
    JoinedFlow<
        WorkStateDataFlow.Binding,
        ResourceStateDataFlow.Binding> compatible =
        ready.innerJoin(available).on(
            KeyExpression.of(work.columns().capability()),
            KeyExpression.of(resources.columns().capability()));
    JoinedOrder<
        WorkStateDataFlow.Binding,
        ResourceStateDataFlow.Binding> order =
        compatible.leftOrder(
            work.columns().priority().descending()
                .then(work.columns().dueMinute().ascending())
                .then(work.columns().releaseMinute().ascending())
                .then(work.columns().workIdValue().ascending()))
            .then(compatible.rightOrder(
                resources.columns().availableMinute().ascending()
                    .then(resources.columns().resourceIdValue().ascending()),
                AbsenceOrder.LAST));
    DataFlowDefinition.Builder graph =
        DataFlowDefinition.builder();
    readyCount = graph.output("readyCount", ready.count());
    demandByCapability = graph.output(
        "demandByCapability",
        ready.groupBy(
            KeyExpression.of(work.columns().capability())).counts());
    rankedPairs = graph.output(
        "rankedPairs",
        compatible.sortedBy(order).indexSnapshot());
    definition = graph.build();
    template = definition.compile();
    admittedLoadDefinition = work.candidates()
        .project(work.columns().processingMinutes())
        .sum();
    admittedLoadTemplate = admittedLoadDefinition.compile();
  }

  public String definitionIdentity() {
    return definition.identity();
  }

  public String templateIdentity() {
    return template.identity();
  }
}
