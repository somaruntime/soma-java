# 工业动态调度应用 Blueprint

类型：应用 Blueprint

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-23

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

同一最终配置和 seed 必须产生相同 input checksum。CLI 输出的 config checksum
用于证明 override 后究竟运行了什么；input checksum 与 result checksum 不混用。
benchmark warmup/forks/measurements 使用另一份 test-only 配置，不参与问题
identity。

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

Stable identity、secondary unique、exact group 与 owned child 各自表达自己的
cardinality，不把它们强制合成一种索引：

```java
@SomaTable(name = "operation_definitions")
@SomaUnique(name = "by_job_sequence",
    fields = {"operationKey.jobId.value", "sequenceNo"})
public final class OperationDefinition {
  @SomaKey public OperationKey operationKey;
  @SomaField public int sequenceNo;
  @SomaField public SetupFamilyId setupFamily;
  @SomaChild(initialCapacity = 8)
  public List<EligibleMachine> eligibleMachines;
}
```

Frontier 是 derived state，以 operation-machine stable key 保证唯一，并用 exact
group 支持按 machine 或 operation 访问：

```java
@SomaTable(name = "dispatch_candidates")
@SomaIndex(name = "by_machine",
    fields = {"candidateKey.machineId.value"})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"})
public final class DispatchCandidate {
  @SomaKey public DispatchCandidateKey candidateKey;
  // readiness、duration、score 和 source-version fields
}
```

典型 pipeline 只处理当前 candidate set。刷新、选择和 swap-remove 都不全表
物化：

```java
frontier.update(candidate -> refresh(candidate));

frontier.filter(candidate -> candidate.ready())
    .sorted(TOTAL_ORDER)
    .limit(1)
    .forEach(selected::copy);

frontier.scanByOperation(operationKey).remove();
```

跨事件保存 `OperationKey`、`MachineId` 等 stable key；current Index 与
`IndexSnapshot` 只在同步只读批次内使用。Solver 终点才把
`OperationAssignment` schema record 复制为 `ScheduledOperation`。

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
- frontier refresh、total-order selection、version revalidation、
  explicit cross-table commit 与 successor release。

Event queue、resource lane calendar、maintenance evaluator 和跨 Table 提交顺序属于
应用，不被包装成 SOMA 产品能力。SOMA 也不因此成为通用调度框架。

## 成功标准

- 普通 Maven consumer 可以独立构建与运行；
- production JAR 只包含应用代码，不包含 fixture、oracle、verification 或
  benchmark；
- correctness fixture 有手算结果；
- 四个配置均可重放并通过完整 validator；
- long-run 持续 mutation 后 frontier 清空且所有 operation 恰好一次 assignment；
- 多 fork 记录 solve time、allocated bytes、Young/Full GC 和 runtime high-water；
- 所有结果默认只作为本机应用证据，不形成 release 或普遍性能主张。
