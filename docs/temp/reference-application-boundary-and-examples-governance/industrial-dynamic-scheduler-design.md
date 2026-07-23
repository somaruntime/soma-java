# 工业动态调度参考应用详细设计

类型：Temporary

状态：active

Owner：工业动态调度参考应用候选设计

事实范围：工业动态调度应用的领域目标、闭环、SOMA projection、正确性、失败与 evidence 要求

非事实范围：SOMA 产品能力、通用调度器承诺、精确 generated API 或最终性能结论

最后审查日期：2026-07-23

## 1. 应用目标

该应用是可独立运行和验证的动态 dispatch engine，不是教学型两工序示例，也不是通用 APS 产品。它以完整、参数化的工业场景证明 SOMA 在高频 runtime state 上的使用方式。

应用拥有调度规则、event queue、跨表提交、领域 validator、checkpoint/fail-stop 和结果解释；SOMA 只拥有声明式 schema、generated facade、packed storage、exact access、Candidate Scan、bulk、ownership 和单 Table operation 语义。

## 2. 强制领域能力

- job、operation、machine、setup family、secondary resource 的 stable identity；
- operation precedence 和 successor release；
- alternative machine eligibility 与 machine-specific processing duration；
- job release、material readiness、predecessor completion；
- sequence-dependent setup；
- machine availability calendar 与 maintenance interval；
- inter-machine transport time；
- secondary resource capacity/availability；
- due date、priority 和 tardiness accounting；
- application-owned event queue；
- machine dispatch、candidate refresh、selection、commit 和 result export；
- dynamic event 至少覆盖 release/availability 变化，而不是只做静态一次性排序。

## 3. 数据角色

候选 Table 角色在详细 schema 定稿时必须保持：

| 角色 | 典型事实 | 目标访问 |
|---|---|---|
| input definition | job/operation/machine/eligibility/setup/calendar/resource | key/unique/exact/child |
| authoritative state | machine、operation、resource runtime state | point mutation、packed/column |
| derived frontier | machine-operation candidates | exact group、update/filter/best/remove |
| result fact | assignments、job result | append、key traversal、materialization |
| application structure | event/machine/resource heap、calendar evaluator | Java 8 application-owned |

不把 event queue、calendar algorithm 或 cross-table transaction 强行建进 SOMA。

### 3.1 应用 schema

候选 schema package 为 `com.hgtech.soma.examples.scheduler.state`，generated package 为其 `.generated`。精确字段可以在不改变下列责任的前提下随编译诊断调整：

| Table | Kind / maintained access | 责任 |
|---|---|---|
| `JobDefinition` | keyed `JobId` | release、material readiness、due、priority、operation count |
| `OperationDefinition` | keyed `OperationKey`；unique `(JobId, sequence)`；owned `EligibleMachine` child | precedence、setup family、resource demand、候选机定义 |
| `MachineDefinition` | keyed `MachineId`；owned `MaintenanceWindow` child | calendar、maintenance 与初始 setup family |
| `MachineState` | keyed `MachineId` | next availability、last setup family、version |
| `OperationState` | keyed `OperationKey`；index readiness status | predecessor completion、scheduled/released/version |
| `SecondaryResourceState` | keyed `ResourceId` | capacity、next availability、version |
| `SetupTime` | keyed `(MachineId, fromFamily, toFamily)` | required exact setup lookup |
| `TransportTime` | keyed `(fromMachine, toMachine)` | required exact transport lookup |
| `DispatchCandidate` | keyed `(OperationKey, MachineId)`；index machine/operation | derived frontier 与排序指标 |
| `OperationAssignment` | keyed `OperationKey`；index machine/resource | authoritative result fact |

Event heap、machine heap、calendar evaluator、input arrays 和 validator 不进入 schema。Optional last family 只表示首个 assignment 的显式 absence，不使用 sentinel。

### 3.2 代码责任

```text
config/       strict properties loading, CLI overrides, effective-config output
problem/      detached domain records, generator, input checksum
state/        annotation schema only
runtime/      bootstrap, aggregate owner, event/machine heaps, dispatch engine
validation/   tiny expected oracle, full invariant validator, result checksum
evidence/     verification main and benchmark runner
```

`problem/` 不 import `.state.generated` 或 `com.hgtech.soma.runtime`；`runtime/` 不生成输入。`SchedulerRuntime` 是所有 Table、heap、Batch 和 scratch 的唯一 lifecycle owner。

## 4. 配置、问题生成与运行时装载

应用把“构造可重放工业问题”和“执行动态调度”分成三个生命周期：

```text
SchedulerConfig
  -> SchedulingProblemGenerator
  -> SchedulingProblem
  -> SchedulerRuntimeBootstrap
  -> SchedulerRuntime
```

- `SchedulerConfig` 从版本化 `.properties` 读取 jobs、operations/job、machines、candidate degree、setup families、calendar/maintenance、transport、secondary resources、release/event distribution、seed 和运行边界；
- `SchedulingProblemGenerator` 只生成 detached definition、initial state 和按稳定顺序排列的 external events；它不访问 SOMA Table，也不参与 dispatch；
- `SchedulingProblem` 在 import 前完成 identity、reference、duration、capacity 和 event-order 校验，并提供稳定 input checksum；
- `SchedulerRuntimeBootstrap` 把完整问题投影到 SOMA authoritative state 和应用 event queue；装载完成后 generator 即可释放；
- correctness、default、large、long-run 分别使用独立配置；CLI 覆盖值进入最终配置摘要，不允许隐藏的环境默认值改变问题；
- 测试必须区分 generator determinism、bootstrap projection equivalence 和 runtime scheduling correctness。

问题规模的改变只改变输入，不改变 solver 的分支结构或注入专用测试逻辑。

## 5. 主循环

候选执行模型：

```text
consume next application event
  -> publish authoritative release/availability fact
  -> identify affected operations/machines/resources
  -> stage and publish candidate frontier changes
  -> refresh candidate indicators for one dispatch scope
  -> filter feasibility
  -> choose by explicit total comparator or application heap
  -> revalidate identities, versions and resources
  -> commit authoritative assignment/state in explicit order
  -> retire/rebuild derived frontier
  -> release successors and new events
```

任何 `STALE` 或 feasibility change 不计为成功 assignment。业务顺序必须有 identity tie-break，不能依赖 physical Index。

## 6. Access Model 覆盖

| Family | 应用使用 |
|---|---|
| Point | key/unique fetch、locate Index、point mutation |
| Candidate | packed/exact source、filter、sort/best、update、remove |
| Column | hot scalar read、validator gather |
| Key | deterministic result/export traversal |
| Bulk | import、frontier append/replace、result append |
| Ownership | operation-owned eligible machines/calendar intervals 等严格 child |
| Snapshot/materialization | 同步只读 validator、最终结果边界 |

Index/IndexSnapshot 只在同步只读 batch 中消费；跨 event 保存 stable key。

## 7. Correctness oracle

应用必须提供独立领域 validator，至少验证：

- 每个 operation 恰好一次 assignment；
- precedence、release、material、transport 时间；
- machine eligibility、processing duration、setup；
- calendar/maintenance 不重叠；
- machine capacity 和 secondary resource capacity；
- no-overlap、checked arithmetic、non-negative duration；
- successor release 与 event ordering；
- due/priority/result accounting；
- deterministic total order；
- authoritative state、frontier 和 result 之间不存在 stale success。

小规模 fixture 使用独立 AoS/reference implementation 或可手算 expected schedule；大规模使用 invariant + deterministic checksum，不能以 SOMA 输出自证正确。

## 8. Failure 与 lifecycle

- import 在首次 authoritative mutation 前完成 identity/reference/unit/range/capacity preflight；
- reusable Batch/scratch 属于 application aggregate，不跨线程；
- callback 不重入同一 aggregate，不发布外部副作用；
- SOMA V1 不提供跨 root transaction；
- authoritative write 后不可恢复失败使当前 solve/session fail-stop，或从 application checkpoint 重建；
- derived frontier/heap/cache 可以从 authoritative state 重建；
- root/child/ColumnView 按明确 `finally` 顺序释放。

## 9. Evidence

至少提供：

- deterministic small oracle；
- constraint-specific negative fixtures；
- swap-remove/Index invalidation/stale candidate tests；
- artifact-isolated clean/repeat build；
- 版本化 workload 配置与 detached dataset generator，记录 generator version、最终配置、seed、输入摘要及 jobs/operations/machines/candidate degree/setup/calendar/resource/event distribution；
- warmup + 多 fork throughput/latency；
- allocated bytes、Young/Full GC、high-water；
- long-running mutation 与 result checksum；
- 默认 `claimAllowed=false`。

旧 FJSP 100k benchmark 只能作为历史对照，不得用不同语义 workload 直接宣称性能改善。

### 9.1 受版本控制的 workload

| Config | 用途 | 证明边界 |
|---|---|---|
| `correctness.properties` | 小规模、固定 seed | generator、bootstrap、手算结果与全部约束 |
| `default.properties` | 普通演示 | CLI journey、deterministic validator |
| `large.properties` | 较大规模 | capacity、frontier、allocation/GC smoke |
| `long-run.properties` | 多轮或大事件量 | stale event、swap-remove、resource/lifecycle 稳定性 |

Benchmark setup 必须在计时前完成 config load、problem generation、input validation 和 bootstrap；测量只包含 solve/runtime，另行报告 setup 时间。
