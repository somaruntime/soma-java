# 工业动态调度应用 Design

类型：应用 Design

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-23

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
- `problem/` 拥有 top-level Spec、Factory、预检、lookup 和 input checksum，
  不 import SOMA runtime/generated code；
- `schema/` 只声明 annotation schema；
- `solver/` 拥有 canonical facade、一次性 session、算法状态机与 result
  assembly；
- `runtime/` 拥有 RuntimePlan、Table aggregate、Problem projection、
  projection verification、event queue 和 resource calendar；
- `result/` 只依赖 detached problem，拥有 immutable Result、checksum 和完整
  domain validator；
- fixture、oracle、verification、benchmark 和 JVM metrics 只存在于
  `src/test`。

Runtime 不反向调用 Factory；Factory 的可变 `Random` 状态在 Problem 构造完成后
即可释放。生产包不依赖 test/evidence 包。

## Problem、Solver 与 Result

`SchedulingProblem` defensive-copy 所有顶层集合，并把校验、lookup 和 checksum
分别委托给自己的唯一 Owner。`SyntheticSchedulingProblemFactory` 只根据
`ProblemGenerationConfig` 创建输入；fixture 不复用为生产数据源。

`SchedulingSolver.solve(problem)` 是普通调用入口。需要区分 preparation/solve
measurement 时使用 `prepare(problem)` 返回 `SchedulingSession`。Session 状态为
`READY -> SOLVING -> CLOSED`，成功、失败或显式关闭都会释放唯一拥有的 Runtime。

`ScheduleResult` 包含全部 detached `ScheduledOperation`、目标统计、稳定 checksum
和 `SolveDiagnostics`。Result assembler 逐字段复制 schema record，并在 Runtime
关闭前捕获 `RuntimeSnapshot`；Result 不保存 ColumnView、Index、IndexSnapshot、
Cursor、Batch 或 mutable schema record。

## Schema projection

| 角色 | SOMA table | 主要访问 |
|---|---|---|
| input definition | Job/Operation/Machine/Setup/Transport | key、unique、owned child |
| authoritative state | Machine/Operation/SecondaryResourceState | point read/mutation、column |
| derived frontier | DispatchCandidate | exact group、update、filter/sort、remove |
| result | OperationAssignment | append、key traversal、materialization |

`EligibleMachine` 和 `MaintenanceWindow` 是严格 owned child。Event queue 与
resource lane array 是 application structure，可由 input/assignment 重建，不成为
live SOMA storage 的旁路事实源。

## 调度算法与责任

`DispatchEngine` 只编排状态机；`ExternalEventProcessor` 拥有 event replay 和首工序
发布；`CandidateFrontier` 拥有 candidate 发布、刷新、全序选择和版本复验；
`AssignmentCommitter` 拥有 authoritative mutation、candidate 退役和 successor
发布。四者不得复制 comparator、refresh 或 commit 顺序。

每次循环：

1. frontier 为空时消费下一个外部事件时刻；
2. release 和 material 两类事件都发布后，首工序进入 frontier；
3. packed update 根据 machine、operation、resource version 刷新全部候选；
4. 按 `setupStart -> completion -> priority -> due -> stable identity` 全序选择；
5. 若尚未消费的事件早于候选 setupStart，先发布事件并重新选择；
6. 重新检查 candidate key 和三个 source version；
7. 按 assignment、machine、resource、operation、frontier 的显式顺序提交；
8. successor 带 predecessor end/machine 发布，其他候选通过 swap-remove 退役。

Machine interval 包含 setup 和 processing，并跳过所有 maintenance window。
Secondary resource 用按可用时间排序的 lane array 找到满足 units 的最早时刻；
assignment 是该约束的最终权威事实。

## Failure 与 lifecycle

- problem 在首次 Table mutation 前完成 identity/reference/range/matrix/event 预检；
- runtime factory/projection 失败会关闭整个尚未发布的 aggregate；
- 单 Table operation 保持 SOMA 失败原子性；
- SOMA V1 没有跨 Table transaction；authoritative write 后失败使 solve fail-stop；
- derived frontier、event projection 与 resource calendar 可以从 input/assignment
  重建；
- `SchedulerRuntime` 是唯一 owner，按 result/derived/lookup/state/definition
  逆序 release；
- callback 不重入同一 aggregate，不产生外部副作用；
- session、dispatch engine 和 pipeline 都是 one-shot。

## Index 与物理顺序

业务 tie-break 始终使用 stable identity。Machine calendar 对 child 的扫描不依赖
physical order；result checksum 在按 operation identity 排序后计算。验证器还会
反转 materialized assignment 清单，证明输出不依赖 packed physical order。

`IndexSnapshot` 只在同步只读批次消费；test-only 负路径显式验证 mutation 后
stale、wrong-source 和 release 后访问均被拒绝。生产 JAR 不携带这些 evidence
runner。
