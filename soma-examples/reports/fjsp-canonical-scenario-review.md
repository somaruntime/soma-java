# FJSP canonical scenario review

状态：正式审查报告
日期：2026-07-06
Owner：`soma-examples`

## 1. 审查目标

本报告对 FJSP 示例场景做 canonical scenario review。目标不是评估算法优劣，也不是进入 Java 实现，而是用一个真实 V1 runtime loop 反向压测当前设计体系：

```text
schema annotation
  -> processor / codegen
  -> generated table-first API
  -> TableStore runtime internal
  -> Row Pipeline / ColumnView / mutation
  -> typed errors
  -> gate evidence
```

审查问题：

- FJSP 示例是否能自然覆盖 keyed entity state、keyed lookup table 和 dense workspace；
- 示例是否迫使 V1 保留 `KeySpace`、`AccessStructures`、`AccessPath`、Row Pipeline、ColumnView、typed errors 和 gate evidence；
- 示例是否暴露出需要在 correctness model、performance model、testkit 或 benchmark 契约中补齐的设计缺口；
- 审查结论是否会引起 annotation schema 或 Row Pipeline API 基线变更。

## 2. 输入文档

本次审查读取并对照以下正式文档：

- `docs/architecture-design.md`；
- `docs/implementation-strategy.md`；
- `docs/row-pipeline-api-contract.md`；
- `docs/validation-gates.md`；
- `docs/documentation-governance.md`；
- `soma-annotations/docs/annotation-schema-contract.md`；
- `soma-processor/docs/processor-codegen-contract.md`；
- `soma-runtime-core/docs/runtime-core-contract.md`；
- `soma-examples/docs/runtime-state-schema-examples.md`；
- `soma-examples/docs/fjsp-e2e-scenario.md`。

本报告是审查证据，不是新的设计事实源。若本文与 owner 文档冲突，以 owner 文档为准。

## 3. Canonical scenario boundary

FJSP canonical scenario 使用简化 `FCFS + SPT` dispatch loop：

```text
request boundary
  -> load jobs / operations / machines / processing times / setup times
  -> batch import generated tables
  -> rebuild ready operation dense workspace
  -> select ready operation by FCFS
  -> rebuild candidate score dense workspace
  -> select candidate machine by SPT
  -> mutate operation assignment
  -> mutate machine availability
  -> repeat
  -> DTO materialization / export boundary
```

场景内的 table 分为三组：

| 场景组 | Table | Public kind | Runtime pressure |
|---|---|---|---|
| keyed entity state | `Job`、`Operation`、`Machine` | keyed table | stable identity、optional state mutation、order/index sidecar、DTO export |
| keyed lookup table | `ProcessingTime`、`SetupTime` | keyed table | composite key lookup、secondary index、grouped ordered source、missing lookup semantics |
| dense workspace | `ReadyOperationRow`、`CandidateScoreRow` | dense table | `replaceAll(batch)`、ordered `findFirst()` / `firstOrThrow()`、row index non-identity、ColumnView lifecycle |

`ProcessingTime` 和 `SetupTime` 不应建成 `Operation` 的 child table。它们是独立 keyed lookup table。`ReadyOperationRow` 和 `CandidateScoreRow` 是长期 table instance 中反复刷新的 dense workspace，不是普通短生命周期 Java 临时对象。

## 4. Scenario-to-architecture matrix

| FJSP action | Annotation / codegen pressure | Generated API pressure | Runtime internal pressure | Evidence pressure |
|---|---|---|---|---|
| load schema and create tables | `@SomaSchema`、`@SomaTable`、value key、optional、index/order selector validation | `create()`、metadata、schema hash、runtime compatibility | `TableLayout`、`LifecycleState` | G1、G2、G4 |
| batch import entity and lookup data | default capacity、batch row count、key uniqueness | `reserve(size)`、`addBatch(batch)` | `RowSpace` allocation、`ColumnStore` writes、`KeySpace` insert、sidecar dirty/update | G3、G4、G5 |
| fetch operation / machine by key | composite `OperationKey`、`MachineId` | `containsKey(key)`、`fetch(key)`、`mutate(key)` | `KeySpace -> RowSlot -> ColumnStore` | G3、G5 |
| query processing time candidates | `OperationMachineKey` flattening、`by_operation` index、`by_operation_spt` order | `findByOperation(...)`、`byOperationSpt(...)` source | `AccessStructures` index/order、`AccessPath` to `RowSequence` | G2 golden、G3、G5 |
| refresh ready operation workspace | dense table without `@SomaKey` | `replaceAll(batch)`、`byFcfs().findFirst()` | `RowSpace` rebuild/reuse、order sidecar lazy rebuild、epoch update | G3、G5、benchmark smoke |
| refresh candidate score workspace | dense table without identity | `replaceAll(batch)`、`bySpt().firstOrThrow()` | dense `TableStore` without `KeySpace` but with `AccessStructures` | G3、G5、benchmark smoke |
| assign operation | optional field setter、key setter absence | `mutate(key).setAssignedMachine(...).setStartMinute(...).commit()` or Row Pipeline `update(...)` | `MutationCoordinator`、optional bitmap、order/index dirty maintenance | G3、G4、G5 |
| update machine availability | non-key fixed-width mutation | `mutate(machineId).setAvailableFromMinute(...)` | non-structural update、order sidecar dirty if selector affected | G3、G5 |
| read hot column | generated typed column access | `xxxColumn()` / column pipeline | `ColumnView` acquire/read/release、view_pinned/stale/released | G3、G5、benchmark smoke |
| export result | DTO materialization boundary | `fetchAll()` / `fetch(key)` | DTO mapper detached copy | G2 golden、G4、G5 |

## 5. Per-scenario review

### 5.1 Keyed entity state

`Job`、`Operation`、`Machine` 适合作为 keyed entity state。它们有 stable logical identity，并且 FJSP loop 需要通过 key 做精确读取和 mutation。

通过项：

- `OperationKey` 使用 nested `@SomaValue` 表达 composite key，符合 value flattening 和 single logical key 规则；
- `Operation.assignedMachine`、`startMinute`、`processingMinutes`、`endMinute` 等 optional state 能压测 optional bitmap 与 nullable DTO materialization；
- `Operation.by_dispatch_order` 能压测 order sidecar、selector flattening 和 Row Pipeline source method；
- `Machine.by_state` 与 `Machine.by_machine_id` 能压测 secondary index 与 order 并存；
- key field 不应生成 setter，identity change 必须通过 delete + insert 表达。

风险与审查要求：

- `Operation` assignment 和 `Machine` availability 是两张 table 的连续 mutation。V1 不提供 cross-table transaction，因此示例必须把跨表一致性归属到 solver/application loop，不能让 SOMA 承诺事务；
- 如果 assignment 使用 Row Pipeline `update` terminal，必须遵守 terminal 开始前固定 traversal plan 的规则；如果使用 `mutate(key)`，G5 仍需要用其他路径证明 Row Pipeline mutation terminal；
- optional getter 在 absent 时必须报 typed runtime error，hot path 应使用 presence getter 或 `valueOr(defaultValue)`。

结论：keyed entity state 对当前 V1 目标没有形成缩水压力，反而验证了 `KeySpace`、optional bitmap、Row Pipeline、mutator 和 DTO boundary 都是必需项。

### 5.2 Keyed lookup table

`ProcessingTime` 与 `SetupTime` 应保持独立 keyed lookup table，不应回退为 child table 或 `Map<Key, DTO>`。

通过项：

- `ProcessingTime.operationMachineKey` 和 `SetupTime.setupTimeKey` 都是 composite key，能压测 generated key class、key equality、hash collision 和 duplicate key detection；
- `ProcessingTime.by_operation` 能表达 operation -> candidate machines 的 secondary non-unique access；
- `ProcessingTime.by_operation_spt` 能表达同一 operation 下按 processing time 排序的 grouped order source；
- `SetupTime.by_machine_to_family` 能表达 machine + target setup family 的 lookup source；
- lookup data 与 `Operation` 生命周期分离，符合 runtime data plane 建模。

风险与审查要求：

- `byOperationSpt(operationKey)` 这类 grouped order source 是 FJSP 场景的核心易用性压力点。Row Pipeline 契约已允许 order selector prefix 生成 grouped order source，但 processor/codegen golden 必须固化具体生成命名、参数顺序和 selector prefix 规则；
- missing lookup 语义需要在 example smoke 中明确：如果 processing time 是输入完整性要求，应使用 `firstOrThrow()` 或 `fetch(key)` 并期望 typed missing error；如果业务允许缺失，应使用 `find(...)` / `containsKey(...)` / empty pipeline，由 application 解释不可行候选；
- secondary index 与 order sidecar 的维护成本不能被 primary `KeySpace` lookup 混同。

结论：keyed lookup table 是 FJSP 场景中防止 SOMA 滑回对象关系模型的关键压力点。V1 不能用 child table 或普通 map wrapper 替代它。

### 5.3 Dense workspace

`ReadyOperationRow` 与 `CandidateScoreRow` 适合作为 dense workspace。它们没有 stable logical key，主要靠 batch refresh、packed scan、ordered source 和 optional ColumnView 支撑 solver hot loop。

通过项：

- dense table 没有 `KeySpace`，但仍然需要 `RowSpace`、`ColumnStore`、`AccessStructures`、`AccessPath`、`MutationCoordinator` 和 `LifecycleState`；
- `replaceAll(batch)` 是 workspace 刷新的核心边界，符合 batch/reuse capacity 设计；
- `ReadyOperationRow.by_fcfs().findFirst()` 能压测 dense ordered source；
- `CandidateScoreRow.by_spt().firstOrThrow()` 能压测 dense ordered source + required result error；
- row index 只对当前 table state 有效，不能被当作业务 identity；
- ColumnView 可用于读取 `processingMinutes`、`projectedEndMinute` 或机器 availability 这类 primitive column。

风险与审查要求：

- `replaceAll(batch)` 遇到 active ColumnView 时必须遵守 `view_pinned`，除非实现能证明 storage address / length / layout 不变；
- ordered source terminal 前 lazy rebuild 的成本应进入 stats 或 benchmark evidence，不能隐藏；
- dense table `fetchAt(rowIndex)` 只能作为 boundary/debug 友好 API，不应成为 dense hot loop 主路径；
- repeated scratch rebuild 的性能必须通过 benchmark smoke 或后续 claim-grade benchmark 分别观察 construction/import、order rebuild 和 terminal traversal，不能只给一个总耗时。

结论：dense workspace 明确证明 dense table 不是缩水版 table。它只是缺少 `KeySpace`，其他 runtime 能力不能省略。

## 6. Correctness review

FJSP canonical scenario 至少要求后续 correctness model 覆盖以下不变量：

| 不变量 | 场景触发点 | 必要原因 |
|---|---|---|
| key -> slot 一致 | entity / lookup batch import、delete、row move | `fetch(key)`、`mutate(key)`、lookup source 不能漂移 |
| column length / bitmap alignment | optional operation state、batch import、replaceAll | DTO materialization 和 optional cursor getter 必须一致 |
| sidecar consistency | index/order selector update、replaceAll、row move | `findByXxx(...)` / `byXxx(...)` 不能读到旧 row |
| terminal traversal freeze | Row Pipeline update assignment | update selector 字段时同一 row 不能重复进入本次 terminal |
| ColumnView lifecycle | hot column read + workspace refresh | active view 下 structural mutation 必须可观察为 `view_pinned` |
| DTO detached copy | export after mutation | 修改 DTO 不能写回 table，后续 mutation 不改变已返回 DTO |
| cross-table non-transaction boundary | operation assignment + machine update | SOMA V1 不承诺跨 table atomicity |

这些不变量不应只留在本报告中。它们应进入后续 `docs/runtime-correctness-model.md`，并由 G3 runtime core gate 与 G5 examples gate 提供证据。

## 7. Performance review

FJSP 场景能形成四类性能观察点：

| 性能观察点 | 场景动作 | 不允许的 claim |
|---|---|---|
| import / construction | initial `addBatch`、workspace `replaceAll` | 不能把 import dominate 的总耗时解释为 lookup 或 scan 优势 |
| keyed lookup | `ProcessingTime` / `SetupTime` lookup | 不能把 `KeySpace` 和 secondary index 成本混成一个指标 |
| ordered dense workspace | `byFcfs().findFirst()`、`bySpt().firstOrThrow()` | 不能忽略 order lazy rebuild 成本 |
| hot column read | ColumnView / column pipeline | 不能用 DTO `fetchAll()` 路径代表 ColumnView 性能 |

本次 review 只能确认 FJSP 是合适的 benchmark smoke 基础。它不能支撑“比 Java collection 更快”“更省内存”或“生产级 hot path”的性能声明。后续 `runtime-performance-model.md` 和 `soma-benchmarks/docs/benchmark-evidence-contract.md` 必须把 smoke、diagnostic 和 claim-grade benchmark 分开。

## 8. Gate mapping

| Gate | FJSP canonical scenario 应提供或引用的证据 |
|---|---|
| G1 | FJSP schema 的 annotation/type/key/index/order/optional cases；invalid selector compile failure |
| G2 | `OperationKey` / `OperationMachineKey` / `SetupTimeKey` normalized golden；generated table/batch/rows/mutator/ColumnView golden；grouped order source golden |
| G3 | `TableStore` component invariant；batch import；duplicate/missing key；row move；sidecar dirty/rebuild；ColumnView stale/released/view_pinned |
| G4 | Java 8 package smoke：generated FJSP tables compile/run；schema hash/runtime compatibility metadata readable |
| G5 | FJSP request -> loader -> generated tables -> dispatch loop -> exporter smoke；ordered source；Row Pipeline terminal；ColumnView；typed lifecycle errors |
| G6 | release readiness report 引用 G1-G5，不直接把本报告当作 runtime 证据 |

## 9. Findings

### F1: 已处理 - runtime correctness model 需要吸收 FJSP 不变量

严重度：中

FJSP 场景把 key/slot、optional bitmap、sidecar、terminal traversal freeze、ColumnView lifecycle 和 cross-table non-transaction boundary 串在一起。原审查时项目架构已将 runtime correctness model 列为下游设计缺口，本场景进一步确认它应在实现前优先完成。

处理：

- 已新增 `docs/runtime-correctness-model.md`，并把本报告第 6 节作为输入；
- G3 需要覆盖 component invariant 和 cross-component invariant；
- G5 只负责 E2E 可观察路径，不替代 G3 runtime invariant。

### F2: 已处理 - runtime performance model 需要拆分 FJSP 性能问题

严重度：中

FJSP 场景中 import、lookup、ordered workspace、ColumnView 和 DTO export 都可能影响总耗时。若只用一个 benchmark 总耗时，很容易把不同设计问题混在一起。

处理：

- 已新增 `docs/runtime-performance-model.md`，明确 FJSP 的 hot path 与 allocation boundary；
- benchmark evidence contract 仍是后续下游 owner 文档，应区分 smoke、diagnostic 和 claim-grade benchmark；
- release claim 不得引用 benchmark smoke 作为性能优势证明。

### F3: 已处理 - grouped index/order source 需要 codegen golden 固化

严重度：中

`ProcessingTime.by_operation` 与 `ProcessingTime.by_operation_spt` 自然希望生成 `findByOperation(operationKey)`、`byOperationSpt(operationKey)` 或等价 grouped source。Row Pipeline 契约已经允许 index/order source method 与 order selector prefix 生成 grouped order source，但 FJSP 场景要求 processor/codegen 把命名、参数、prefix matching 和 collision handling 固化为 golden case。

处理：

- 已在 `soma-annotations/docs/annotation-schema-contract.md`、`docs/row-pipeline-api-contract.md` 和 `soma-processor/docs/processor-codegen-contract.md` 固化 grouped source 设计；
- G2 golden 后续必须覆盖 `by_operation` 与 `by_operation_spt` 的 grouped source；
- 若 codegen 不支持 grouped convenience，V1 examples 必须明确使用全表 order / index source + filter 的写法，并承认性能边界；
- 不应在 runtime internal 暴露 order sidecar handle 来绕过 generated API。

### F4: 已处理 - lookup missing semantics 需要在 example smoke 中明确

严重度：中

`ProcessingTime` 或 `SetupTime` 缺失可能代表输入不完整，也可能代表候选不可行。SOMA runtime 只能提供 typed missing / empty result 语义，业务解释应由 loader 或 solver core 决定。

处理：

- 已在 `soma-examples/docs/fjsp-e2e-scenario.md` 明确：`ProcessingTime` 缺失 operation-machine pair 表示候选不可行；`SetupTime` 在 canonical FJSP 中是 required setup matrix lookup；
- FJSP example smoke 对 required lookup 使用 `fetch(...)` / `firstOrThrow()` 并断言 typed error；
- 对 optional lookup 使用 `find(...)` / `containsKey(...)` 或 empty pipeline；
- 不把业务 infeasible 和 runtime missing key 压成 generic exception。

### F5: 已处理 - FJSP 文档中的 workspace 命名需要对齐

严重度：低

`soma-examples/docs/fjsp-e2e-scenario.md` 此前使用旧 workspace 概念名描述 dense workspace；`runtime-state-schema-examples.md` 中实际 schema class 是 `ReadyOperationRow` / `CandidateScoreRow`，table name 是 `ready_operation_rows` / `candidate_score_rows`。

处理：

- 已将 E2E 契约和 annotation schema 示例对齐为 `ReadyOperationRow` / `CandidateScoreRow` dense workspace；
- 该问题不影响 annotation/API 基线，不阻塞本次 canonical review 结论。

## 10. 审查结论

FJSP canonical scenario 能作为 V1 设计压力测试的第一条标准场景。

结论：

- 当前 FJSP schema shape 能覆盖 keyed entity state、keyed lookup table 和 dense workspace；
- 该场景没有要求修改已定 annotation schema 或 Row Pipeline API 基线；
- 该场景没有造成 V1 目标缩水，反而确认 `KeySpace`、`AccessStructures`、`AccessPath`、Row Pipeline、ColumnView、DTO detached materialization、typed errors、schema hash/runtime compatibility 和 gate evidence 都不能省略；
- `ProcessingTime` / `SetupTime` 必须保持独立 keyed lookup table，不能回退为 child table 或 `Map<Key, DTO>`；
- `ReadyOperationRow` / `CandidateScoreRow` 证明 dense table 不是缩水版 table；
- 在进入实现前，仍应补 testkit contract 和 benchmark evidence contract；`runtime-correctness-model.md` 与 `runtime-performance-model.md` 已进入正式设计文档体系。

本报告可作为后续 G0 scope freeze、G3 runtime core gate、G5 examples gate 和 benchmark evidence contract 的设计输入，但不能单独作为 release runtime correctness 或 performance evidence。
