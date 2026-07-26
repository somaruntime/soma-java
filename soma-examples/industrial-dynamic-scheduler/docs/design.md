# 工业动态调度应用 Design

类型：应用 Design

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-27

## 责任方向

依赖方向固定为：

```text
application
  -> config / problem / factory
  -> solver facade
       -> dispatch engine / event / frontier / committer
       -> runtime factory / projection / schema
       -> detached result assembly

test fixture / oracle / verification / benchmark
  -> production contracts
```

- `application/` 是 composition root，只选择配置、Factory 和 Solver；
- `config/` 只读取 problem-generation properties、校验全部 key 并输出
  canonical text；
- `problem/` 拥有 top-level Spec、Factory、引用/范围预检、lookup 和 semantic
  checksum，不 import SOMA runtime/generated code；generation provenance 由
  Config/Factory/CLI evidence 另行拥有；
- `schema/` 只声明 annotation schema；
- `solver/` 拥有 canonical facade、一次性 session、算法状态机、result
  assembly，以及从 authoritative assignment Table 推导结果指标的
  `AssignmentSummaryFlow`；
- `runtime/` 拥有 RuntimePlan、Table aggregate、Problem projection、
  event queue 和 maintenance calendar；
- `result/` 只依赖 detached Problem，拥有 immutable Result、checksum 和完整
  domain validator；
- 完整 projection verification、fixture、oracle、execution evidence bridge、
  benchmark 和 JVM metrics 只存在于 `src/test`。

Runtime 不反向调用 Factory；Factory 的可变 `Random` 状态在 Problem 构造完成后
即可释放。生产包不依赖 test/evidence 包。

## Problem、Solver 与 Result

`SchedulingProblem` defensive-copy 所有顶层集合，并把校验、lookup 和 checksum
分别委托给自己的唯一 Owner。`SyntheticSchedulingProblemFactory` 只根据
`ProblemGenerationConfig` 创建输入；fixture 不复用为生产数据源。

`SchedulingSolver.solve(problem)` 是普通调用入口。需要区分 preparation/solve
measurement 时使用 `prepare(problem)` 返回 `SchedulingSession`。Session 状态为
`READY -> SOLVING -> CLOSED`，成功、失败或显式关闭都会释放唯一拥有的 Runtime。

`SchedulingProblem` 的 checksum 只表达领域事实：各输入集合按稳定业务 identity
规范化后计算，generator version、seed、config checksum 和调用者给出的集合顺序
都不改变它。构造函数在任何 SOMA Table mutation 前完成引用闭包、容量和最坏时间
算术预检。

`ScheduleResult` 只包含全部 detached `ScheduledOperation`、目标统计和稳定
result checksum。Result assembler 逐字段复制 schema record；Result 不保存
SOMA diagnostics、ColumnView、Index、IndexSnapshot、Cursor、Batch 或 mutable
schema record。Schema/runtime-plan、allocation、scratch 和 processed-event 等
执行证据由 package-private `SolveEvidence` 捕获，只通过 test access 进入验真和
benchmark。

## Schema projection

| 行为 | Access Pattern | SOMA 表示 / 应用表示 |
|---|---|---|
| job definition | primary-key point + column read | `JobDefinition` |
| operation definition | primary-key point、`job + sequence` secondary unique | `OperationDefinition` |
| operation options | immutable exact-group traversal | flat `EligibleMachine` + `by_operation` |
| operation/machine/resource state | primary-key point read/mutation + reused ColumnView | 三张 authoritative state Table |
| setup/transport | composite primary-key point lookup | `SetupTime` / `TransportTime` |
| assignment | append、key traversal、bounded materialization | `OperationAssignment` |
| assignment summary | reusable typed DataFlow、count/max/groupBy、detached scalar | `AssignmentSummaryFlow` |
| candidate frontier | operation-machine key、machine group、global arg-min | application-owned primitive pool + indexed min-heap |
| event/maintenance/resource lane | priority/calendar operations | application structure |

每个保留的索引都必须有实际消费者。没有查询来源的 status/machine/resource
secondary index、只重复输入事实的 machine definition、逐 operation child Table
和 SOMA candidate projection 均不保留。Maintenance interval 属于 immutable
Problem；`MachineCalendar` 是按 machine Index 排列的应用派生结构。Event queue、
candidate pool/heap 和 resource lane array 可由 input/assignment 重建，不成为
authoritative Table 的旁路事实源。

## 调度算法与责任

`DispatchEngine` 只编排状态机；`ExternalEventProcessor` 拥有 event replay 和首工序
发布；`CandidateFrontier` 拥有 option exact-group 读取、candidate 发布/刷新、
全序选择和版本复验；`AssignmentCommitter` 拥有 authoritative mutation、
candidate 退役和 successor 发布。四者不得复制 comparator、refresh 或 commit
顺序。

每次循环：

1. frontier 为空时消费下一个外部事件时刻；
2. release 和 material 两类事件都发布后，首工序进入 frontier；
3. 每个 machine 在 heap 中最多保留一个当前代表项；machine mutation 或 candidate
   membership 变化把该代表标为 dirty；
4. 只刷新 heap root 所属 machine 的候选组，再按
   `setupStart -> completion -> priority -> due -> stable identity` 全序选择；
5. 若尚未消费的事件早于候选 setupStart，先发布事件并重新选择；
6. 重新检查 candidate key 和三个 source version；
7. 按 assignment、machine、resource、operation、frontier 的显式顺序提交；
8. successor 带 predecessor end/machine 发布，其他候选从 primitive pool
   swap-remove 退役。

Machine availability 只向后移动，dirty root 中缓存的代表分数因此是 lower bound；
若它仍是全局最小项，只需重算该 machine 才能决定下一项。Resource lane readiness
也只向后移动；若新 readiness 不晚于 candidate 已计算的 effective start，则只推进
resource version，不改变 score，否则重算 root machine。上述证明和 stable
tie-break 是增量 frontier 保持与全量全序选择等价的前提。

Machine interval 包含 setup 和 processing，并跳过所有 maintenance window。
Secondary resource 用按可用时间排序的 lane array 找到满足 units 的最早时刻；
assignment 是该约束的最终权威事实。

## Failure 与 lifecycle

- problem 在首次 Table mutation 前完成 identity/reference/range/matrix/event 预检；
- runtime factory/projection 失败会关闭整个尚未发布的 aggregate；
- 单 Table operation 保持 SOMA 失败原子性；
- SOMA V1 没有跨 Table transaction；authoritative write 后失败使 solve fail-stop；
- derived candidate frontier、event queue 与 resource calendar 可以从
  input/assignment 重建；
- `SchedulerRuntime` 是 Table aggregate 的唯一 owner，按
  result/lookup/state/definition 逆序 release；solver session 另外拥有并关闭
  candidate frontier；
- callback 不重入同一 aggregate，不产生外部副作用；
- session、dispatch engine 和 pipeline 都是 one-shot。

## Index 与物理顺序

业务 tie-break 始终使用 stable identity。Machine calendar 对 child 的扫描不依赖
physical order；result checksum 在按 operation identity 排序后计算。验证器还会
反转 materialized assignment 清单，证明输出不依赖 packed physical order。

`IndexSnapshot` 只在同步只读批次消费；test-only 负路径显式验证 mutation 后
stale、wrong-source 和 release 后访问均被拒绝。生产 JAR 不携带这些 evidence
runner。

## Result transformation

调度循环结束后，`AssignmentSummaryFlow` 把当前
`OperationAssignmentTable` 绑定到一个预编译、无 live state 的 Definition：

```text
all assignments
  -> count
  -> max(endMinute)
  -> groupBy(job + due + priority).max(endMinute)
  -> immediate current-Index column reads for due/priority
  -> detached metrics
```

最后一步只在同一同步只读批次消费 group representative Index；它不把 Index 或
ColumnView 保存进 Result。Tardiness 以每个 job 的 completion 计算，而不是按
operation 重复累加。Definition/Template 可复用，session 内的 Context 和
Invocation one-shot；`DispatchEngine` 在 `finally` 中关闭 Context。

Result 指标的完整性由 `AssignmentSummaryFlow.Metrics.validated` 在事实产生处
负责：assignment cardinality、empty/non-empty makespan、dispatch makespan
一致性和非负目标值使用真实失败，不依赖断言或 test-only validator。DataFlow
diagnostics 只进入 package-private `SolveEvidence`，不成为领域 Result。
