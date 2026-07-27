# Stage 4 Industrial DataFlow 责任迁移收口

类型：Temporary

状态：complete

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

事实范围：Stage 4 实施事实、迁移闭包和进入 Stage 5 的条件

非事实范围：正式应用文档、尚未完成的 portfolio 性能证据和 release 声明

最后审查日期：2026-07-27

## 1. 原子迁移结果

Stage 3 已先建立独立 RTD application 的 reusable Definition/Template/Invocation、
multi-source Join、GroupBy、parallel、budget/cancellation、detached command/result
和 application-owned commit evidence。在替代 coverage 成立后，本 Stage 才完成：

```text
authoritative OperationAssignmentTable
  -> AssignmentSummarizer
  -> DispatchSummary
  -> detached ScheduleResult
```

`AssignmentSummaryFlow` 已删除；`DispatchEngine`、`SolveEvidence`、
`SchedulerExecutionTestAccess` 和 correctness evidence 中只服务旧展示责任的
DataFlow identity/stats 也已删除。portfolio 没有 coverage 空窗。

## 2. 新的事实 Owner

`AssignmentSummarizer` 是无状态 application service。每次调用使用四个
ColumnView，在一个同步只读批次内按当前物理 Index 单遍读取 job identity、end、
due 和 priority：

- assignment count 与 makespan 直接在 packed traversal 中推导；
- application-owned primitive open-address state 按 stable job identity 聚合
  completion；
- due/priority 在同一 job 内不一致时在事实产生处失败；
- completion、total tardiness 和 weighted tardiness 使用 checked arithmetic；
- assignment、job 和 makespan 必须覆盖 DispatchEngine 的已证明终态。

group state 只存在于 summarize operation，不是第二份 live domain state。
dispatch commit 仍只写 authoritative assignment Table，没有引入累计 tardiness 或
completion shadow state。

## 3. Dependency 裁决

工业调度的 business path 已不再调用 reusable DataFlow，但当前完整 Schema
generation 会产生 typed DataFlow companion；这些生成 source 的 ordinary consumer
编译和生产 artifact linkage 仍需要 `soma-dataflow`。因此依赖保留，并在 POM
明确为 generated companion contract，而不是含混的业务迁移状态。

## 4. Evidence

本 Stage 使用语义与架构窄 Gate，稳定候选的三 fork 性能留到 Stage 5：

- Zulu JDK 8 reactor package：通过，production/test source 完整重编译；
- correctness profile：hand oracle、独立 Result validator、projection/lifecycle
  negative 和 replay checks：通过；
- default profile：1000 operations 的 input/result identity 保持；
- default Result checksum 仍为
  `c9565a45d52b4446928c5e9fe8546ab6328c404b5b07e62baa74a0e85541facf`，
  与既有 v4 baseline 相同；
- production source/JAR 不再包含 `AssignmentSummaryFlow` 或旧 DataFlow evidence；
- production JAR 包含 `AssignmentSummarizer`，不包含 test/evidence implementation。

## 5. Scope non-regression

- `ScheduleResult`、checksum、validator、dispatch rule、event loop、frontier、
  workload 和性能阈值均未改变；
- assignment Table 仍是唯一 authoritative completion fact；
- 没有修改 SOMA API、Schema、DataFlow 或 runtime 语义；
- 没有删除 industrial 的领域能力，也没有把 frontier/event loop 强行迁入 graph；
- Stage 5 仍必须用现有三 fork baseline 证明默认、scale 和 soak 性能非回归。
