package com.hgtech.soma.examples.rtd.result;

import com.hgtech.soma.examples.rtd.support.StableHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 领域成功输出；不包含 DataFlow diagnostics 或 live handle。 */
public final class DispatchResult {
  private final int cycles;
  private final int totalWork;
  private final int dispatchedWork;
  private final int pendingWork;
  private final List<DispatchCommand> commands;
  private final String inputChecksum;
  private final String checksum;

  public DispatchResult(
      int cycles,
      int totalWork,
      int dispatchedWork,
      int pendingWork,
      List<DispatchCommand> commands,
      String inputChecksum) {
    if (cycles <= 0 || totalWork < 0
        || dispatchedWork < 0 || pendingWork < 0) {
      throw new IllegalArgumentException("invalid result counts");
    }
    if (Math.addExact(dispatchedWork, pendingWork) != totalWork) {
      throw new IllegalArgumentException(
          "result state counts do not cover total work");
    }
    if (commands == null) throw new NullPointerException("commands");
    if (inputChecksum == null) {
      throw new NullPointerException("inputChecksum");
    }
    ArrayList<DispatchCommand> detached =
        new ArrayList<DispatchCommand>(commands.size());
    Set<Long> workIds = new HashSet<Long>();
    for (DispatchCommand command : commands) {
      if (command == null) throw new NullPointerException("command");
      if (!workIds.add(Long.valueOf(command.workId()))) {
        throw new IllegalArgumentException(
            "work dispatched more than once: " + command.workId());
      }
      detached.add(command);
    }
    if (detached.size() != dispatchedWork) {
      throw new IllegalArgumentException(
          "command count differs from dispatched work");
    }
    this.cycles = cycles;
    this.totalWork = totalWork;
    this.dispatchedWork = dispatchedWork;
    this.pendingWork = pendingWork;
    this.commands = Collections.unmodifiableList(detached);
    this.inputChecksum = inputChecksum;
    checksum = computeChecksum();
  }

  private String computeChecksum() {
    StableHash hash = new StableHash()
        .addString("rtd-dispatch-result-v1")
        .addString(inputChecksum)
        .addInt(cycles)
        .addInt(totalWork)
        .addInt(dispatchedWork)
        .addInt(pendingWork)
        .addInt(commands.size());
    for (DispatchCommand command : commands) {
      hash.addLong(command.workId())
          .addLong(command.resourceId())
          .addInt(command.capability())
          .addLong(command.issueMinute())
          .addLong(command.startMinute())
          .addLong(command.completionMinute())
          .addInt(command.priority())
          .addLong(command.expectedWorkVersion())
          .addLong(command.expectedResourceVersion());
    }
    return hash.finishHex();
  }

  public int cycles() { return cycles; }
  public int totalWork() { return totalWork; }
  public int dispatchedWork() { return dispatchedWork; }
  public int pendingWork() { return pendingWork; }
  public List<DispatchCommand> commands() { return commands; }
  public String inputChecksum() { return inputChecksum; }
  public String checksum() { return checksum; }
}
