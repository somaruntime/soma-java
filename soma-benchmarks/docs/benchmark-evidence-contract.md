# Benchmark evidence 契约

状态：正式设计文档
Owner：`soma-benchmarks`
事实范围：evidence level、通用度量边界、artifact、claim 和设计反推条件
非事实范围：具体 runtime-state scenario lanes、API/schema/runtime contract 和性能结果
最后审查日期：2026-07-10

## 1. 目标

本文定义 `soma_java` V1 runtime-state 场景的 benchmark evidence contract。它约束 FJSP、VRP、连续仿真和 Game runtime 四类典型 hot runtime state 场景中，哪些性能问题可以进入 benchmark，如何拆分度量边界，什么证据才允许支撑性能 claim，以及什么证据才允许反推 API、runtime 或 schema 设计。

本文补充以下正式文档，不替代它们：

- SomaTable 语义宪法以 [SomaTable 设计宪法](../../docs/soma-table-design-constitution.md) 为准；
- 根级性能模型以 [Runtime performance model](../../docs/runtime-performance-model.md) 为准；
- V1 release gate 以 [V1 验证门禁](../../docs/validation-gates.md) 为准；
- Generated Table API 以 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 为准；
- runtime core 内部边界以 [TableStore 契约](../../soma-runtime-core/docs/table-store-contract.md) 为准；
- runtime implementation 性能纪律以 [Runtime performance implementation contract](../../soma-runtime-core/docs/runtime-performance-implementation-contract.md) 为准；
- processor/codegen 边界以 [Code generation 契约](../../soma-processor/docs/code-generation-contract.md) 为准。

本文是 benchmark 证据契约，不是 benchmark 报告，也不是 API 设计文档。没有具体 benchmark 结果时，本文只能定义可测问题、证据口径和 claim 边界，不能声明 SOMA 已经具备性能优势。

## 2. 非目标

本文不做以下事情：

- 不修改 Java annotation schema、generated Java API 或 runtime core 契约；
- 不把临时蓝图整体固化为 SOMA V1 正式设计；
- 不承诺 SOMA V1 提供跨 table transaction、heap、ECS、ODE solver、pathfinding engine、automatic join planner 或 lambda predicate index pushdown；
- 不承诺 dynamic `sorted(comparator)` 与 maintained `@SomaOrder` 有等价性能；
- 不把 `top-k`、route segment rewrite、event heap/range-pop、coordinate O(1) lookup 写成 V1 public API；
- 不用 benchmark smoke、示例能跑或单机一次结果证明生产级性能优势。

## 3. Evidence levels

Runtime-state benchmark 继续使用三层证据：

| Level | 目的 | 可支持的结论 |
|---|---|---|
| smoke | 验证 runner、scenario、artifact 和结构化输出可运行 | 只能说明 benchmark 路径可执行 |
| diagnostic | 拆分 phase、观察成本结构、定位瓶颈 | 可以说明某个场景下瓶颈在哪里，通常不能支持 release 性能 claim |
| claim-grade | 有 baseline、规模、环境、重复次数和统计口径 | 只能支持覆盖范围内的性能 claim |

所有场景必须先通过 smoke，才能进入 diagnostic。只有 diagnostic 已经证明问题边界清晰，且 claim-grade 具备可复现实验条件时，才允许写性能优势结论。

## 4. 通用度量边界

每个 benchmark lane 至少必须说明：

- scenario 名称、lane 名称、数据规模和 seed；
- commit hash 或 artifact version；
- Java version、JVM args、OS、CPU、memory；
- warmup、fork/process、repetition、measurement iteration；
- baseline id；
- setup/import、hot loop、export 是否分开计量；
- phase timing；
- throughput/latency，例如 rows/s、ns/row 或 ns/op；
- row/candidate count；
- estimated touched columns/bytes 与 working-set size；
- scanned、matched、changed、removed、materialized rows；
- allocation/op、allocated bytes 或明确的 allocation observation method；
- GC count/time 或明确的 GC observation limitation；
- schema object/List/Map/entry allocation / materialization count；
- batch builder、materializer 或 external DTO adapter 构造量；
- `KeySpace` capacity/load factor、insert、lookup、remove、missing key、duplicate key、probe/collision/rehash；
- selector cardinality/selectivity、grouped result size、mutation/read ratio；
- optional density 与 bitmap words scanned；
- index/order sidecar dirty、rebuild count 和 rebuild time；
- dynamic sort temporary row-index buffer size；
- summary-only/diagnostic stats mode 与 instrumentation overhead；
- ColumnView acquire/read/release、stale/released/view_pinned error；
- hardware cache/branch counters when available；缺失时记录 profiler/tool limitation；
- status、失败原因和 known limitations。

以下成本不得隐藏在一个总耗时里：

- `replaceAll(buildXxx())` 背后的 cross-table lookup、builder 构造、column rewrite、sidecar dirty/rebuild；
- `sorted(comparator)` 背后的 candidate materialization、temporary row-index buffer、comparator 调用次数；
- `fetch(key)` 背后的 composite key 构造、hash/collision、missing key 语义；
- maintained order source 的 lazy rebuild；
- object `materialize()` / Row Pipeline `fetchAll()` / recursive materialization / external DTO response mapping；
- ColumnView active scope 与 structural mutation 冲突；
- capacity/scratch first-growth、resize transient double-memory、compaction 和 retained high-water；
- summary-only 与 diagnostic stats 的 instrumentation overhead；
- application-owned source-of-truth cache 同步和失败恢复。

### 4.1 Application data-role split/co-location 对照

Input facts、working state 和 result facts 的职责分离是 modeling baseline，不是预先成立的性能结论。任何把 mixed table 拆成 definition/state/result tables，或因 locality 把 field group co-locate 的建议，都必须在相同业务语义和唯一事实源下比较：

| Lane | 度量对象 | 必须拆分的成本 | 不允许的结论 |
|---|---|---|---|
| `data_role.split` | input/working/result 使用独立 table 的方案 | extra key lookup、cross-table mutation sequence、batch/object/export mapping、table/sidecar memory | 不能仅凭职责清晰声明更快 |
| `data_role.co_located` | 明确 field group co-location 的方案 | wider row scan、unused-column read、mixed mutation/sidecar、shadow-field risk | 不能以 locality 为由复制 authoritative fact |
| `data_role.projection` | input leaf preprojection 或 result projection | projection build、copy bytes、reuse count、invalidation/rebuild | 不能把 derived projection 说成第二事实源 |

两组 baseline 必须记录 table count、row/field count、keyed lookup、scanned columns、mutation count、recovery path、heap/allocation estimate 和 external export cost。职责分离是否进入正式场景 schema，取决于 correctness/invariant 与 benchmark evidence 的共同结果，benchmark 单独不能批准 schema。

## 5. 允许得出的结论

在证据覆盖范围内，benchmark 可以得出以下类型结论：

- 某个访问路径在某个数据规模、mutation 模式、JVM 和硬件环境下比另一个 baseline 更快或更慢；
- 某个 phase 是主要瓶颈，例如 order sidecar rebuild、`TravelCost.fetch`、recursive object export 或 route segment rewrite；
- 某个建模选择更适合该场景，例如 FJSP keyed frontier 或 VRP dense workspace；
- 某个高级能力值得进入后续设计专题，例如 route segment rewrite 或 coordinate lookup API。

benchmark 不可以得出以下结论：

- 从单个 smoke 推出性能优势；
- 从一个场景推出所有 runtime-state 场景的通用建议；
- 从 diagnostic benchmark 推出 release 性能 claim；
- 把算法启发式改进归因于 SOMA data layout；
- 把手写 primitive baseline 与 recursive object/external DTO export 做不同语义比较后声明 SOMA 更快；
- 因为某个 future option 在蓝图中出现，就认为它已经是 V1 承诺。

## 6. 反推 API / runtime / schema 的条件

只有同时满足以下条件，benchmark 结果才允许进入 API、runtime 或 schema 设计讨论：

1. 有重复 benchmark 结果，且记录了 scale、seed、环境、warmup、repetition 和 commit；
2. 有同语义 baseline，并能区分数据布局收益、算法收益和示例策略收益；
3. phase evidence 显示瓶颈稳定存在，不是单次 GC、JIT、机器噪音或实现 bug；
4. 现有 public/generated API 可以表达该场景，但表达成本被证明不可接受，或现有 API 不能表达必要语义；
5. 新能力不会破坏 Java-only V1 边界、table-first API、Row Pipeline 心智模型和 runtime internal 隔离；
6. 新能力有明确 owner、错误语义、lifecycle、validation gate 和 rollback/不采纳路径。

满足这些条件后，也只能进入独立设计专题。benchmark contract 本身不直接批准 API、runtime 或 schema 变更。

## 7. 不得提前承诺的能力清单

以下能力在 V1 中只能作为 benchmark 候选、future option 或独立设计专题，不得写成已承诺能力：

- public `top-k` terminal；
- route segment rewrite / route-local row move API；
- event heap、priority queue、range-pop、prefix range remove；
- coordinate O(1) lookup generated API；
- automatic join planner；
- lambda predicate index pushdown；
- comparator cross-table lookup 优化；
- cross-table transaction 或自动补偿；
- ECS、game engine、pathfinding engine、ODE solver、replay/log engine；
- parallel scan / parallel sort；
- native memory layout、C ABI、Python binding 或 FFI。

## 8. Runner artifact 最小字段

后续 benchmark runner 可以继续细化 JSONL schema。runtime-state scenario benchmark 的 artifact 至少要能表达：

```text
schemaVersion
scenario
lane
level
status
commit
artifactVersion
javaVersion
jvmArgs
os
cpu
memory
scale
seed
warmupIterations
forks
measurementIterations
baselineId
phaseTimings
throughput
latency
rowCounts
candidateCounts
operationCounts
accessPatternCard
touchedBytesEstimate
workingSetEstimate
allocationPerOperation
allocatedBytes
gcStats
sidecarStats
keySpaceStats
selectorStats
optionalDensity
mutationReadRatio
statsMode
materializationStats
effectiveMaterializationBudget
materializationBudgetDimension
materializationPath
allocationEstimatorVersion
externalDtoStats
columnViewStats
allocationEstimate
hardwareCounterStats
knownLimitations
claimAllowed
failureReason
```

字段名和 JSONL 具体 shape 可以由 runner 实现专题继续固化，但不能少于上述语义。`claimAllowed` 为 `false` 时，报告不得把该结果写成性能优势声明。

## 9. 自审清单

每次新增或修改 benchmark lane 时，必须检查：

- 是否把临时蓝图判断固化成 V1 API/runtime/schema 承诺；
- 是否错误套用 FJSP frontier；
- 是否混淆 dense workspace 与 runtime frontier；
- 是否混淆 dynamic sort 与 maintained order；
- comparator 是否做 cross-table lookup；
- 是否隐藏 builder、`replaceAll`、recursive object/external DTO export、sidecar rebuild 或 random lookup 成本；
- 是否遗漏 per-row allocation/boxing、Cursor reuse、loop fusion、packed compaction、capacity/scratch growth 或 stats instrumentation overhead；
- 是否记录 touched bytes/working set、selector selectivity、optional density、mutation/read ratio 和 KeySpace load/collision；
- 是否存在 source-of-truth 双事实源；
- 是否为 input/working/result split 与 co-located 方案提供同语义 baseline，而不是预设任一方更快；
- 是否把 smoke 写成性能优势 claim；
- 是否超出 Java 8 / Java annotation schema / Java columnar runtime；
- 是否引入 Python、native、C ABI、FFI 或第三方依赖口径。

通过自审后，benchmark lane 仍只代表可测问题和证据口径。是否进入正式示例、API、runtime 或 schema 设计，必须另行经过对应 owner 文档和 gate evidence。

## 10. Scenario contract

具体 FJSP、VRP、Simulation、Game、child locality 和 component shape lanes 由 [Runtime-state benchmark 契约](runtime-state-benchmark-contract.md) 拥有。
