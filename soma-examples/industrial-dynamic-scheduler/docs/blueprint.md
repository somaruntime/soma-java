# 工业动态调度应用 Blueprint

类型：应用 Blueprint

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-27

## 目标体验

使用者通过一份版本化配置生成可重放的工业问题，把已校验输入交给一个普通
`SchedulingSolver`，获得在 Runtime 关闭后仍完整可用的不可变 `ScheduleResult`。
改变问题规模或 seed 不改变 solver 分支，也不把测试数据生成逻辑带进 hot loop。

```text
properties
  -> strict ProblemConfigLoader
  -> ProblemGenerationConfig
  -> SchedulingProblemFactory
  -> immutable SchedulingProblem + input checksum
  -> SchedulingSolver
  -> internal Runtime/Schema + event/frontier/commit
  -> typed Assignment Summary DataFlow
  -> detached ScheduleResult
  -> independent domain validation
```

## 使用旅程

配置显式控制 jobs、operations/job、machine eligibility、setup family、
secondary-resource capacity、release/material distribution、maintenance、
transport、machine delay、due/priority 和 seed：

```properties
seed=1702
jobs=24
operations.per.job=8
machines=12
candidate.machines.per.operation=3
secondary.resource.max.capacity=3
maintenance.period.minutes=480
machine.delay.events=6
```

同一组领域事实必须产生相同 input checksum，输入集合的排列顺序不得改变其
semantic identity。CLI 另行输出 config checksum、generator version 和 seed，
用于重放“怎样生成输入”；这些 provenance 不进入 Problem identity，也不与
result checksum 混用。benchmark warmup/forks/measurements 使用另一份 test-only
配置，不参与输入 identity。

应用代码只面向 Problem、Solver 和 Result：

```java
ProblemGenerationConfig config = ProblemConfigLoader.load("default");
SchedulingProblemFactory factory =
    new SyntheticSchedulingProblemFactory();
SchedulingProblem problem = factory.create(config);

SchedulingSolver solver = new SomaSchedulingSolver();
ScheduleResult result = solver.solve(problem);
ScheduleValidator.ValidationSummary validated =
    ScheduleValidator.validate(problem, result);
```

需要分别测量 preparation 与 solve 时，可以使用一次性 session：

```java
SchedulingSession session = solver.prepare(problem);
try {
  ScheduleResult result = session.solve();
  // result 已 detached；solve 返回前内部 Runtime 已关闭。
} finally {
  session.close();
}
```

## SOMA schema 的应用方式

应用先从实际访问模式决定 Schema。Operation 使用 stable key 和
`job + sequence` 的 0..1 查找；eligible option 是不可变 exact group，因此使用
一张 flat Table，而不是为每个 operation 创建 child Table：

```java
@SomaTable(name = "operation_definitions")
@SomaUnique(name = "by_job_sequence",
    fields = {"operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
  @SomaKey public OperationKey operationKey;
  @SomaField public int sequenceNo;
  @SomaField public SetupFamilyId setupFamily;
  @SomaField public ResourceId requiredResource;
  @SomaField public int requiredResourceUnits;
}

@SomaTable(name = "eligible_machines")
@SomaIndex(name = "by_operation", fields = {
    "operationKey.jobId.value",
    "operationKey.operationId.value"})
public final class EligibleMachine {
  @SomaField public OperationKey operationKey;
  @SomaField public MachineId machineId;
  @SomaField public long processingMinutes;
}
```

投影使用 Batch；求解时按 operation 直接遍历 exact group，不生成中间
materialization：

```java
eligibleMachines.scanByOperation(operationKey)
    .forEach(option -> publishCandidate(
        option.machineIdValue(),
        option.processingMinutes()));
```

Job、machine/resource state、setup/transport lookup 和 assignment 分别使用与其
cardinality 对应的 key/unique/point/append 路径。跨事件保存
`OperationKey`、`MachineId` 等 stable key；current Index 与 `IndexSnapshot`
只在同步只读批次内使用。Solver 终点才把 `OperationAssignment` schema record
复制为 `ScheduledOperation`。

同一个 authoritative assignment Table 还通过生成的 typed DataFlow 一次推导
count、makespan 和 job completion。应用只声明业务变换，SOMA 负责绑定、执行和
detached result：

```java
OperationAssignmentDataFlow.Source source =
    OperationAssignmentDataFlow.source("assignments");
CandidateFlow<OperationAssignmentDataFlow.Binding> all =
    source.candidates();

DataFlowDefinition.Builder builder = DataFlowDefinition.builder();
OutputSlot<LongScalarResult> count =
    builder.output("assignment-count", all.count());
OutputSlot<OptionalLongResult> makespan =
    builder.output("makespan",
        all.project(source.columns().endMinute()).max());
DataFlowTemplate<DataFlowResults> template =
    builder.build().compile();

DataFlowContext context = DataFlowContext.sequential();
try {
  DataFlowResults values =
      template.newInvocation(context)
          .bind(source,
              OperationAssignmentDataFlow.bind(assignments))
          .execute();
} finally {
  context.close();
}
```

正式实现还以 `job + due + priority` 分组并取每个 job 的最大 end time，从而在
Result 边界计算 tardiness。该 summary 不替代 candidate frontier，也不把
dispatch rule、事件或跨 Table commit 强行放入 DataFlow。

Candidate frontier 是可由 Table facts 重建的算法状态，而不是领域事实。应用使用
固定容量 primitive pool、machine-local group 和每机一个代表项的 indexed
min-heap 实现全局 best-one；它不伪装成 SOMA Table，也不长期保存 SOMA Index。

## 领域闭环

应用覆盖：

- flexible machine 与 machine-specific processing；
- job/operation precedence、release 和 material readiness；
- sequence-dependent setup；
- maintenance calendar；
- inter-machine transport；
- secondary-resource multi-lane capacity；
- due date、priority、tardiness；
- job release、material ready 和 machine delay 外部事件；
- candidate 增量 refresh、global total-order best-one、version revalidation、
  explicit cross-table commit 与 successor release。

Event queue、candidate pool/heap、resource lane calendar、maintenance evaluator
和跨 Table 提交顺序属于应用，不被包装成 SOMA 产品能力。SOMA 负责列式事实、
point/exact-group access、mutation、append、lifecycle 和受控 materialization；
它不因此成为通用调度框架。

## 成功标准

- 普通 Maven consumer 可以独立构建与运行；
- production JAR 只包含应用代码，不包含 fixture、oracle、verification 或
  benchmark；
- correctness fixture 有手算结果；
- 四个配置均可重放并通过完整 validator；
- long-run 持续 mutation 后 frontier 清空且所有 operation 恰好一次 assignment；
- 多 fork 记录 solve time、allocated bytes、Young/Full GC 和 runtime high-water；
- correctness Gate 证明真实 generated DataFlow 完成一次 invocation、覆盖全部
  assignment，并且输出仍与独立领域 validator 一致；
- 所有结果默认只作为本机应用证据，不形成 release 或普遍性能主张。
