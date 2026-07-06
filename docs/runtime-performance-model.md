# Runtime performance model

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文定义 `soma_java` V1 runtime performance model。它说明 V1 性能信心来自哪些 hot path、复杂度边界、allocation 边界、sidecar lifecycle、runtime stats 和 benchmark claim 边界。

本文不是 benchmark 报告，不声明任何已测得性能优势。任何“更快”“更省内存”“适合生产大规模 hot path”的 release claim，都必须由正式 benchmark evidence 支撑。

本文不替代 owner 契约：

- 项目级架构以 `docs/architecture-design.md` 为准；
- runtime component contract 以 `soma-runtime-core/docs/runtime-core-contract.md` 为准；
- Row Pipeline API 以 `docs/row-pipeline-api-contract.md` 为准；
- gate evidence 以 `docs/validation-gates.md` 为准。

## 2. Performance principles

V1 性能设计的基础是：

- primitive field 使用 primitive column；
- optional field 使用 presence bitmap + payload column；
- hot Row Pipeline 不 per-row materialize DTO；
- generated row cursor 直接访问 column storage；
- batch import 以 `reserve` / `addBatch` / `replaceAll` 为边界；
- primary key lookup 由 `KeySpace` 承载；
- secondary index、unique index、order source 由 `AccessStructures` + `AccessPath` 承载；
- dynamic sort 使用 row permutation，不移动真实 column storage；
- ColumnView 直接读取 live column storage；
- DTO materialization 明确属于 boundary/export/debug/test 路径。

V1 不应把 benchmark smoke、示例能跑、或单机一次运行结果写成性能优势声明。

## 3. Complexity notation

本文使用以下符号描述复杂度：

| Symbol | 含义 |
|---|---|
| `N` | table 当前 live rows |
| `B` | batch rows |
| `G` | grouped source candidate rows |
| `P` | Row Pipeline terminal candidate rows |
| `M` | matched/materialized rows |
| `C` | table leaf columns |
| `S` | selector leaves |
| `K` | key leaves |
| `W` | optional bitmap words |

复杂度是设计目标和证据组织方式，不是已测得性能结论。实际实现必须由 benchmark evidence 验证。

## 4. Performance lanes

V1 benchmark 和 stats 不应只给一个总耗时。至少拆分以下 lane：

| Lane | 典型场景 | 需要单独观察的成本 |
|---|---|---|
| import / construction | initial `addBatch`、workspace `replaceAll` | reserve、growth、column write、bitmap write、key insert、sidecar dirty/update |
| primary key lookup | `fetch(key)`、`containsKey(key)`、`mutate(key)` | hash/sparse lookup、collision、composite key equality、missing key |
| secondary index source | `findByOperation(operationKey)` | candidate generation、row list/chain traversal、dirty rebuild |
| order source | `byOperationSpt(operationKey)`、`bySpt()` | order sidecar rebuild、grouped prefix seek/filter、ordered traversal |
| Row Pipeline terminal | `count`、`findFirst`、`fetchAll`、`update`、`remove` | scanned/matched/materialized/changed/removed rows |
| dense workspace refresh | `ReadyOperationRow.replaceAll(batch)` | capacity reuse/growth、full column rewrite、sidecar dirty、ordered terminal |
| ColumnView / column pipeline | primitive column scan | acquire/read/release、active view、view errors |
| DTO export | `fetchAll()`、response export | DTO allocation、field copy、list allocation |

FJSP canonical scenario 必须避免把 import/construction、lookup、ordered workspace、ColumnView 和 DTO export 混成一个结论。

## 5. Import and construction

`reserve(size)`、`addBatch(batch)` 和 `replaceAll(batch)` 是 V1 import 性能边界。

设计目标：

- loader 应先估算 row count，并尽量调用 `reserve(size)`；
- `addBatch(batch)` 按 batch size 扩容和写入；
- `replaceAll(batch)` 尽量复用 capacity，是 dense workspace 刷新的主要路径；
- optional bitmap 写入应按 word/chunk 组织，避免 per-row object allocation；
- keyed table import 必须记录 `KeySpace` insert 与 duplicate detection 成本；
- sidecar 可以 eager update，也可以 dirty 后 lazy rebuild，但 stats/benchmark 必须能区分。

复杂度口径：

- pure column write 约为 `O(B * C_written)`；
- optional bitmap write 约为 `O(B)` 或 `O(W_changed)`；
- keyed insert 约为 `O(B * K)` expected，collision / rehash 另计；
- sidecar update/rebuild 不得隐藏在 import 总耗时里。

`replaceAll(batch)` 不是免费操作。即使复用 capacity，也仍需要 row count reset、column writes、bitmap writes、key/index/order dirty 或 rebuild、epoch/stats 更新。

## 6. KeySpace performance

Primary key lookup 属于 `KeySpace`，不是普通 secondary index。

V1 需要分别观察：

- `SparseIntKeySpace` bounded int id normal case；
- `HashKeySpace` int / long normal case；
- generated composite key lookup；
- hash collision full equality；
- missing key；
- duplicate key detection；
- remove / row move update；
- rehash。

性能 claim 不能把 primary key lookup、secondary index source 和 order source 混成一个 “lookup” 指标。

FJSP 中：

- `OperationTable.fetch(operationKey)` 压测 composite key primary lookup；
- `ProcessingTimeTable.fetch(operationMachineKey)` 压测 composite lookup key；
- `ProcessingTimeTable.findByOperation(operationKey)` 压测 secondary grouped index source，不属于 primary lookup。

## 7. Secondary index and order source

`AccessStructures` 维护 secondary index、unique index 和 order sidecar。`AccessPath` 从它们产生 terminal 初始 `RowSequence`。

### 7.1 Secondary index source

设计目标：

- `findByXxx(...)` 从 maintained index / unique sidecar 生成 candidate rows；
- non-unique index 可使用 row list、row chain 或 rebuildable sidecar；
- unique index candidate rows 最多 0/1；
- dirty index 在 source 使用前必须 rebuild；
- candidate generation 与后续 pipeline filter/update 应分别统计。

FJSP 示例：

```java
processingTimes.findByOperation(operationKey)
```

该 source 的性能 lane 应记录 candidate rows `G`，而不是只记录最终 chosen candidate。

### 7.2 Order source

Order sidecar 是 row permutation，不改变 physical column storage。

设计目标：

- `byXxx()` 返回 full ordered source；
- grouped order source 可使用 leading selector prefix，例如 `byOperationSpt(operationKey)`；
- insert/delete/replaceAll/selector update 后 order sidecar eager maintain 或 dirty；
- terminal 使用 dirty order sidecar 前 lazy rebuild；
- lazy rebuild 成本必须进入 stats/benchmark，不能被 `findFirst()` 掩盖成近似 O(1)。

FJSP 示例：

```java
processingTimes.byOperationSpt(operationKey).firstOrThrow()
candidateScoreRows.bySpt().firstOrThrow()
```

`byOperationSpt(operationKey)` 的成本至少包含 grouped prefix selection、ordered row traversal 和可能的 sidecar rebuild。若 codegen 退化为 full scan + filter + dynamic sort，benchmark claim 必须按该路径解释，不能仍声明 maintained order source 性能。

## 8. Row Pipeline terminal

Row Pipeline construction 是轻量 lazy plan；terminal 才执行。

Terminal stats 至少应能表达：

- scanned rows；
- candidate rows；
- matched rows；
- materialized rows；
- changed rows；
- removed rows；
- sidecar dirty count；
- sidecar rebuild count / cost hint；
- DTO allocation count when terminal materializes DTO。

不同 terminal 的性能语义：

| Terminal | Hot path expectation | Allocation boundary |
|---|---|---|
| `count()` | no DTO materialization | no DTO allocation |
| `findFirst()` | stops after first matching row when source permits | at most one DTO on success |
| `firstOrThrow()` | same as `findFirst` plus required-result error | at most one DTO on success |
| `fetchAll()` | materializes all matched DTOs | DTO/list allocation proportional to `M` |
| `update(updater)` | mutable cursor, no DTO materialization | sidecar dirty/update stats |
| `remove()` | structural mutation terminal | compaction/sidecar maintenance stats |

Mutation terminal 开始前必须固定本次 traversal plan。该 correctness 规则同时影响性能：terminal 内 selector update 不应导致 repeated source seek 或重复进入同一 row。

## 9. Dense workspace

Dense workspace 是 V1 性能模型的重要场景。它没有 `KeySpace`，但仍然是完整 `TableStore`：

```text
TableStore + RowSpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState
```

FJSP dense workspace：

- `ReadyOperationRow` / `ready_operation_rows`；
- `CandidateScoreRow` / `candidate_score_rows`。

性能要求：

- workspace table instance 可以长期持有；
- 每轮通过 `replaceAll(batch)` 刷新 rows；
- capacity reuse 应减少 allocation，但不免除 column rewrite；
- `replaceAll` 后 row index 只对当前 state 有效；
- order sidecar 可 dirty 后 lazy rebuild；
- active ColumnView 下 structural `replaceAll` 必须处理 `view_pinned`；
- ordered `findFirst()` / `firstOrThrow()` 必须分别观察 lazy rebuild 与 terminal traversal。

Dense workspace 不等于短生命周期 Java 临时对象管理器，也不能缩水为缺少 Row Pipeline、ColumnView、order、lifecycle 的普通数组。

## 10. ColumnView and column pipeline

ColumnView 是显式性能工程路径。

设计目标：

- acquire/release 成本可观察；
- active view count 可进入 runtime stats；
- primitive read 不 materialize DTO；
- structural mutation 遇到 active view 返回 `view_pinned`；
- stale/released view error 不应被 benchmark 忽略；
- ColumnView benchmark 不能用 DTO `fetchAll()` 结果替代。

Column pipeline 是更易用的单列遍历入口；如果它不暴露 explicit acquire/release，也仍不得允许 structural mutation 与 column traversal 产生未定义行为。

## 11. DTO materialization and export

DTO materialization 是 boundary/export/debug/test 路径，不是 Row Pipeline hot traversal 主模型。

需要单独统计：

- materialized DTO count；
- copied field count；
- optional absent/present copy；
- list allocation；
- key/value object allocation；
- export serialization outside SOMA boundary。

FJSP response export 可以使用 `fetchAll()` 或 keyed `fetch(key)` materialize DTO，但该成本不能混入 “lookup 快” 或 “ColumnView 快” 的 claim。

## 12. Memory and allocation model

V1 使用 Java heap estimate / runtime stats，不承诺精确 JVM heap profiler。

至少估算：

- primitive column arrays；
- object column arrays；
- optional bitmap words；
- `SparseIntKeySpace` dense/sparse arrays；
- `HashKeySpace` buckets / key storage；
- secondary index row lists/chains；
- unique index structures；
- order `orderedRows` permutation；
- temporary row-index buffers for dynamic sort / terminal；
- generated cursor / pipeline objects；
- DTO objects and lists；
- high water mark；
- last allocation failure reason。

Stats 是 diagnostics 和 benchmark smoke 的辅助证据，不是单独的 memory claim。

## 13. Benchmark evidence levels

V1 使用三层 benchmark evidence：

| Level | 目的 | 可用于 release 性能 claim |
|---|---|---|
| smoke | runner、scenario、artifact、JSONL 能运行 | 否 |
| diagnostic | 拆 phase、定位瓶颈、观察成本结构 | 通常否 |
| claim-grade | 有 baseline、规模、环境、重复次数、统计口径 | 是，且只能引用覆盖范围内结论 |

Benchmark smoke 必须避免结论性措辞。它只能证明工具链和场景可运行。

Claim-grade benchmark 至少需要：

- scenario/lane；
- scale；
- seed；
- commit or artifact version；
- Java version；
- JVM args；
- OS/CPU/memory；
- command；
- warmup；
- repetitions；
- baseline id；
- phase timings；
- allocation estimate；
- DTO count；
- sidecar rebuild count/time；
- GC / heap observation method；
- status；
- known limitations；
- claim allowed / not allowed。

Benchmark runner / JSONL schema / baseline 细节应由后续 `soma-benchmarks/docs/benchmark-evidence-contract.md` 定义。

## 14. Baseline rules

Baseline 必须同语义比较。

允许的 baseline 类型包括：

- Java `Map<Key, DTO>` for keyed lookup；
- Java `List<DTO>` for scan/export；
- DTO Java Stream after materialization；
- primitive array loop for explicit ColumnView-like path；
- generated public API 的不同 access path。

禁止：

- 用 ColumnView primitive scan 对比 DTO export 总耗时后声明整体 runtime 更快；
- 用 import-dominated 总耗时证明 lookup / scan 优势；
- 绕过 generated/public API 直接读取 sidecar、bucket、row pointer 后写入 release claim；
- 对不同业务语义的 baseline 做速度 claim。

## 15. FJSP performance mapping

| FJSP path | Lane | Claim boundary |
|---|---|---|
| initial table import | import / construction | 不证明 lookup 或 traversal 优势 |
| `ProcessingTime.fetch(operationMachineKey)` | primary key lookup | 与 composite key baseline 比较 |
| `ProcessingTime.findByOperation(operationKey)` | secondary index source | 观察 candidate generation and traversal |
| `ProcessingTime.byOperationSpt(operationKey)` | grouped order source | 必须包含 lazy rebuild / prefix selection |
| `ReadyOperationRow.replaceAll(batch)` | dense workspace refresh | 观察 capacity reuse、column rewrite、sidecar dirty |
| `CandidateScoreRow.bySpt().firstOrThrow()` | ordered dense terminal | 不把 lazy rebuild 隐藏为 terminal O(1) |
| assignment `update` / `mutate` | Row Pipeline / mutator | 观察 changed rows and sidecar dirty |
| `xxxColumn()` scan | ColumnView | 不与 DTO export 混淆 |
| response `fetchAll()` | DTO export | 只证明 materialization/export boundary |

FJSP 可以作为 benchmark smoke 和 diagnostic benchmark 的基础。它本身不是性能优势证据。

## 16. Non-goals

V1 performance model 不承诺：

- 比所有 Java collections 更快；
- 比手写 primitive loop 一定更快；
- JIT/GC 无噪音；
- exact heap profiling；
- parallel scan / parallel sort；
- thread-safe performance；
- native memory layout；
- production-grade all-platform performance matrix；
- 未经 baseline 的 release 性能 claim。
