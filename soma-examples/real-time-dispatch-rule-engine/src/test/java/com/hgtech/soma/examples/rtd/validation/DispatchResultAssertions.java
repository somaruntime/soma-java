package com.hgtech.soma.examples.rtd.validation;

import com.hgtech.soma.examples.rtd.result.DispatchCommand;
import com.hgtech.soma.examples.rtd.result.DispatchResult;

import java.util.List;

/** 领域 Result 的 reference differential 断言。 */
public final class DispatchResultAssertions {
  private DispatchResultAssertions() {
  }

  public static void equivalent(
      DispatchResult expected, DispatchResult actual) {
    require(expected != null && actual != null, "result is null");
    require(expected.cycles() == actual.cycles(), "cycles");
    require(expected.totalWork() == actual.totalWork(), "total work");
    require(
        expected.dispatchedWork() == actual.dispatchedWork(),
        "dispatched work");
    require(expected.pendingWork() == actual.pendingWork(), "pending work");
    require(
        expected.inputChecksum().equals(actual.inputChecksum()),
        "input checksum");
    require(
        expected.checksum().equals(actual.checksum()),
        "result checksum");
    List<DispatchCommand> left = expected.commands();
    List<DispatchCommand> right = actual.commands();
    require(left.size() == right.size(), "command count");
    for (int index = 0; index < left.size(); index++) {
      equivalent(left.get(index), right.get(index), index);
    }
  }

  private static void equivalent(
      DispatchCommand left, DispatchCommand right, int index) {
    require(left.workId() == right.workId(), "work id " + index);
    require(
        left.resourceId() == right.resourceId(),
        "resource id " + index);
    require(
        left.capability() == right.capability(),
        "capability " + index);
    require(
        left.issueMinute() == right.issueMinute(),
        "issue minute " + index);
    require(
        left.startMinute() == right.startMinute(),
        "start minute " + index);
    require(
        left.completionMinute() == right.completionMinute(),
        "completion minute " + index);
    require(
        left.priority() == right.priority(),
        "priority " + index);
    require(
        left.expectedWorkVersion()
            == right.expectedWorkVersion(),
        "work version " + index);
    require(
        left.expectedResourceVersion()
            == right.expectedResourceVersion(),
        "resource version " + index);
  }

  public static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(
          "dispatch result mismatch: " + message);
    }
  }
}
