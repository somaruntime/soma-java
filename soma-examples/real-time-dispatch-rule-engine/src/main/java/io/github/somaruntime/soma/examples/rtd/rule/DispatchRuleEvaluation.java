package io.github.somaruntime.soma.examples.rtd.rule;

import io.github.somaruntime.soma.dataflow.DataFlowStats;
import io.github.somaruntime.soma.examples.rtd.result.DispatchCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次 Invocation 的 detached command batch 与 execution facts。 */
public final class DispatchRuleEvaluation {
  private final List<DispatchCommand> commands;
  private final long readyCandidates;
  private final long joinedPairs;
  private final int demandGroups;
  private final String demandChecksum;
  private final DataFlowStats stats;

  DispatchRuleEvaluation(
      List<DispatchCommand> commands,
      long readyCandidates,
      long joinedPairs,
      int demandGroups,
      String demandChecksum,
      DataFlowStats stats) {
    this.commands = Collections.unmodifiableList(
        new ArrayList<DispatchCommand>(commands));
    this.readyCandidates = readyCandidates;
    this.joinedPairs = joinedPairs;
    this.demandGroups = demandGroups;
    this.demandChecksum = demandChecksum;
    this.stats = stats;
  }

  public List<DispatchCommand> commands() { return commands; }
  public long readyCandidates() { return readyCandidates; }
  public long joinedPairs() { return joinedPairs; }
  public int demandGroups() { return demandGroups; }
  public String demandChecksum() { return demandChecksum; }
  public DataFlowStats stats() { return stats; }
}
