# Stage 6 工业动态调度应用投影

类型：Temporary

状态：immutable application candidate

Owner：industrial-dynamic-scheduler DataFlow application trace

事实范围：工业调度对 SOMA Transformation/DataFlow 的自然投影、实施边界与验收

非事实范围：正式应用 Design、当前已支持能力和跨环境性能声明

最后审查日期：2026-07-27

## 1. 意图

工业调度已经使用 SOMA Table 承载 definitions、authoritative runtime state 和
assignments，并用应用自有 primitive frontier 维护高频候选状态。Stage 6 不把
已经证明高效的 frontier 强行改造成 Table 或通用 graph，而是在真正属于
Transformation 的边界使用 Typed DataFlow：

```text
authoritative OperationAssignment Table
  -> reusable typed Assignment Summary Definition
  -> one-shot bound Invocation
  -> detached dispatch metrics
  -> detached ScheduleResult
```

这条 trace 证明 DataFlow 可以服务正式应用，同时保留 Access Model 的分工：
point/exact/ColumnView 与 primitive frontier 继续负责 dispatch hot loop；
DataFlow 负责从完成后的列式事实推导多项 summary。

## 2. 责任与非回归

新增 `AssignmentSummaryFlow`，其唯一责任是：

- 在 class initialization 时构造并编译 immutable multi-output Definition；
- 每个 solve 使用独立 sequential `DataFlowContext`；
- 每次 summary 使用 one-shot Invocation 绑定当前 assignment Table；
- 从同一 Candidate source 推导 assignment count、makespan 和按
  `job + due + priority` 分组的 completion；在无 mutation 的同步批次中立即消费
  group representative Index，以 O(job count) fold 得到两类 tardiness；
- 输出 detached metrics 与 detached diagnostics，不保留 Table、Index、
  IndexSnapshot、Cursor 或 generated Binding。

保持不变：

- `CandidateFrontier` 仍是 candidate refresh/selection 的唯一 Owner；
- `AssignmentCommitter` 仍拥有 assignment 与 runtime-state mutation 顺序；
- event queue、resource calendar 和 machine heap 仍属于应用；
- 不新增 Schema/Index，不改变 Problem、Result 或 public Solver API；
- 不改变 current Index、ownership、lifecycle、失败原子性或跨 Table 语义；
- grassing-individual-simulation 不迁移、不增加调用 trace。

## 3. 按构造即正确

| 不变量 | 唯一 Owner | 防线 |
|---|---|---|
| graph output 名称、类型与 identity 唯一 | `DataFlowDefinition.Builder` | one-shot build；collision 在 publish 前拒绝 |
| Template 不绑定 live Table | immutable `DataFlowTemplate` | static plan 只保存 schema-bound logical contract |
| Invocation 只绑定当前 Runtime | `AssignmentSummaryFlow.summarize` | generated binding + one-shot Invocation |
| summary 必须覆盖全部 assignment | `AssignmentSummaryFlow.Metrics` factory | count、empty max、范围和目标 operation count 使用真实失败 |
| diagnostics 不成为业务事实 | `AssignmentSummaryFlow.Evidence` | detached copy；只进入 package-private `SolveEvidence` |
| Context 不越过 session | `AssignmentSummaryFlow.close` | `DispatchEngine` finally 关闭；重复 close 幂等 |

这些检查中任何一项关闭后都可能产生错误业务结果，因此不用 `assert`。测试只验证
构造防线、一次 canonical solve、deterministic replay 和现有性能基线，不为四个
output 重复通用 null/lifecycle 测试。

## 4. 验收

- isolated Java 8 Maven consumer 构建；
- correctness/default/large/long-run 保持相同 input/result identity；
- application verification 证明真实 DataFlow invocation 成功且覆盖 runtime facts；
- public/generated API、Schema hash 和 frontier contract 不变；generated-runtime v5
  引起的 RuntimePlan hash 只进行 provenance-aware identity migration；
- default/large/long-run 各使用一次 5-fork 校准证据验证既有阈值，不重新计算、
  放宽或循环调节性能阈值；
- production JAR 不包含 benchmark/fixture/oracle/verification；
- formal Blueprint/Design/Implementation/Conformance/Report 最终原子接管本事实，
  随后删除本文件。

## 5. Immutable candidate evidence

候选提交：`44108fb624455bd83595dc997fdbdf42a5583102`

应用 correctness Gate 首先识别出“按 operation 累加 tardiness”与领域语义不一致；
修复位于事实 Owner：DataFlow 先按 `job + due + priority` GroupBy，再对每组
`end` 求最大值，最后只按 job completion 计算 tardiness。测试未放宽，也未增加
重复边界用例。

三个既有 workload 各执行一次 5-fork 校准。输入、结果与 Schema identity 保持
不变，只有 generated-runtime v5 的 RuntimePlan hash 发生预期变化：

| profile | operations | solve 范围 | hot allocation 范围 | GC | 结论 |
|---|---:|---:|---:|---:|---|
| default | 1,000 | 14.68–15.75 ms | 4.06 MB | young/full 0/0 | 既有阈值通过 |
| large | 100,000 | 1.30–1.33 s | 121.93–126.59 MB | young/full 2/0 | 既有阈值通过 |
| long-run | 10,000 | 47.45–50.91 ms | 11.86–14.99 MB | young/full 1/0 | 既有阈值通过 |

基线版本仅升级 identity/provenance，全部 metric reference 原样保留。这里证明
应用 trace 没有破坏既有性能包络，不形成跨机器支持或发布声明。
