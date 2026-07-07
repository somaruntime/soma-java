# 四个 SOMA 临时蓝图深度设计审查报告

状态：临时治理报告
日期：2026-07-07
范围：`docs/temp/fjsp-machine-candidate-frontier-blueprint.md`、`docs/temp/vrp-runtime-frontier-blueprint.md`、`docs/temp/simulation-runtime-state-blueprint.md`、`docs/temp/game-runtime-frontier-blueprint.md`

## 1. 审查目标

本轮审查的目标不是判断四个蓝图是否“写得完整”，而是判断它们是否真实暴露 SOMA Java-only V1 在生产场景下的 schema annotation、generated API、runtime core、performance model 和用户体验设计压力点。

本报告最初只做设计审查和 gap mapping；本轮治理已经把 P0/P1 中被证明会误导正式示例的事实同步到 `soma-examples/docs/`，并在第 6 节记录落地状态。正式事实源仍以 `docs/`、`soma-annotations/docs/`、`soma-runtime-core/docs/`、`soma-processor/docs/` 和 `soma-examples/docs/` 为准。

## 2. 事实来源

本轮读取并对照了以下正式材料：

- `AGENTS.md`
- `soma-annotations/docs/annotation-schema-contract.md`
- `docs/row-pipeline-api-contract.md`
- `soma-runtime-core/docs/runtime-core-contract.md`
- `docs/runtime-performance-model.md`
- `docs/runtime-correctness-model.md`
- `docs/architecture-design.md`
- `soma-processor/docs/processor-codegen-contract.md`
- `soma-examples/docs/runtime-state-schema-examples.md`
- `soma-examples/docs/fjsp-runtime-state-example.md`
- `soma-examples/docs/vrp-runtime-state-example.md`
- `soma-examples/docs/simulation-runtime-state-example.md`
- `soma-examples/docs/game-runtime-state-example.md`

同时使用四个只读 subagent 分别审查 FJSP、VRP、连续仿真和 Game 蓝图。subagent 未修改文件。

## 3. 总体结论

四个蓝图总体方向成立，且已经把 FJSP 的经验抽象为“先识别 hot loop，再判断生命周期，再选择 keyed/dense/index/order/dynamic sort/ColumnView”，没有把 `MachineCandidate` frontier 模式机械套到所有场景。

核心 SOMA V1 正式契约目前没有发现 P0 级冲突。`keyed table` / `dense table` 两类 table、`@SomaIndex` / `@SomaOrder` 作为稳定访问路径、Row Pipeline one-shot lifecycle、dynamic `sorted(comparator)`、ColumnView `view_pinned`、单表 correctness 与无跨表 transaction 边界，这些正式设计能够解释四个场景。

本轮治理后的后续问题集中在两类：

1. benchmark/evidence contract 需要扩展，覆盖 dynamic sort、maintained order、dense workspace refresh、frontier maintenance、route segment rewrite、coordinate lookup、event queue、trace export、preprojection 等 lane。
2. generated API/runtime 可选能力需要进入研究清单，但不能提前写成 V1 承诺，例如 segment rewrite、top-k public terminal、range remove、heap/event queue、O(1) coordinate lookup、automatic join planner。

## 4. 逐篇审查结论

### 4.1 FJSP

判定：保留为临时蓝图；方向正确；正式示例已同步 required setup lookup、release index、commit failure 和 indicator 生命周期口径；benchmark 细节仍留在后续专题。

通过项：

- `MachineCandidateTable` 作为 keyed runtime frontier 成立，候选身份 `(MachineId, OperationKey)` 稳定，支持 `findByMachine(machineId)` dispatch 和 `findByOperation(operationKey).remove()` cleanup。
- dispatch rule 没有固化为 schema order，FCFS/SPT/setup 等指标先写入 candidate row，再用 dynamic `sorted(comparator)`。
- comparator 只读 candidate row 字段，没有 cross-table lookup。
- row 存在即候选有效，不再保留长期 `active` 字段。

主要风险：

- `SetupTime.fetch(setupKey)` 的 canonical 语义已经在正式 FJSP 示例中定为 required setup matrix lookup；默认 0 或缺失表示不可行只能作为 future variant，必须先修改场景契约。
- `commitAssignment(...)` 连续更新 `Operation`、`Machine`、`MachineCandidate`、`Job`、`Material`，SOMA V1 不提供跨 table transaction；solver loop 必须拥有失败处理、补偿、停止或重建策略。
- `releaseNextOperations(...)` 不能只是 placeholder，必须依赖 `Operation.by_job_sequence`、material/job source-of-truth 或其他稳定索引，避免退化为全表扫描。
- `indicatorReady` 应限定为当前 machine snapshot 下的计算完成状态，不能成为长期业务状态。

建议 benchmark：

- frontier `addBatch`、`findByMachine(...).update(...)`、dynamic sort/top-k、`findByOperation(...).remove()`。
- `Machine.byAvailableTime().firstOrThrow()` 的 order sidecar lazy rebuild。
- `SetupTime.fetch` composite key lookup。
- DTO materialization、batch builder、response export 成本。
- dense workspace rebuild 对照 lane，避免把增量 frontier 写成普遍最优。

### 4.2 VRP

判定：已修正后保留为临时蓝图；dense workspace 仍是默认正式示例；keyed insertion frontier 只是大规模局部增量场景的 benchmark 候选。

通过项：

- 蓝图没有机械套用 FJSP，明确 `InsertionCandidateRow` 默认可继续作为 dense workspace。
- keyed insertion frontier 的前提足够严格：候选跨轮次保留、能局部失效、能按 route/customer/position 清理，且全量 rebuild 已成为瓶颈。
- `RouteVisitRow.position` 被识别为 route sequence 的当前事实源，不是 stable key。
- `RouteVersion` 被用于防 stale candidate，避免旧 `insertAfterPosition` 在 route mutation 后继续有效。
- `TravelCost.fetch` 不进入 comparator，排序只读 candidate row 上已经计算好的字段。

主要风险：

- 蓝图第 6 节已改为可选 keyed frontier benchmark 草案；正式口径继续强调 dense workspace 是默认，keyed frontier 是候选研究方向。
- `(RouteId, CustomerId, InsertAfterPosition, RouteVersion)` 不是跨生命周期 stable business identity，而是 route-version epoch 内的候选身份。
- `Customer.state`、`Customer.assignedRoute`、`Customer.assignedPosition`、`UnassignedCustomerRow`、`RouteVisitRow.position` 的 source-of-truth 已同步到正式示例。`assignedPosition` 若保留，只应是诊断/snapshot。
- route segment rewrite 是最大 runtime/API 压力点。现有 V1 public/generated API 主要表达 `replaceAll`、`clear`、`addBatch`、Row Pipeline update/remove；高效 route-local segment rewrite 还不是明确承诺。
- `by_best_delta` 是当前正式示例便利，不应被写成策略排序的长期 schema 推荐。

建议 benchmark：

- dense `replaceAll + by_best_delta().firstOrThrow()`。
- dense `replaceAll + dynamic sorted/limit`。
- keyed frontier `findByRoute/remove + addBatch + sorted`。
- route segment rewrite：whole-table rebuild、route-local rebuild、未来 row move/segment API。
- `TravelCost.fetch(locationPair)`：normal/missing/collision。
- route-local cached neighbor cost、dense matrix row、ColumnView primitive scan。

### 4.3 连续仿真

判定：已修正后保留为临时蓝图；正式示例已同步 source-of-truth、变量维度、event queue order、trace buffer 和 ColumnView 风险口径；是否迁移更多内容仍需 benchmark 与人工审核。

通过项：

- `StateVectorRow` 作为 dense long-lived numeric state 成立，适合 packed scan、Row Pipeline update、ColumnView/scratch 两阶段 hot path。
- `PendingEventRow.by_event_time` 被正确表达为稳定 ordered access path，不是 heap、priority queue 或 range-pop。
- `TraceSampleRow` 被定位为 trace/export buffer，不反向成为仿真状态事实源。
- ColumnView 示例采用 read-view 关闭后再写入的两阶段模式，符合 active view 下 structural mutation 的 `view_pinned` 边界。
- `FlowCoefficient.fetch` inner-loop 随机 lookup 已被识别为 preprojection/benchmark 压力点。

主要风险：

- `StateVectorRow` 是数值状态 source-of-truth 的口径已同步到正式示例；`Tank.levelLiters`、`Tank.temperatureCelsius` 和 `Valve.openingRatio` 只能作为边界 cache/export DTO 字段。
- `SimVariableKind` 已同步到正式示例，用于区分同一实体多个数值变量；如果未来变量集不可枚举，应另行设计 numeric `variableId` 和低频 mapping table。
- `PendingEventRow.by_event_time().filter(...).forEach(...)` 与后续 `remove()` 是 two-terminal queue 消费，不能被口径化为 heap pop 或 prefix range remove。
- `TraceSampleRow.by_time_entity` 的 lazy rebuild 只能在 export/diagnostic terminal 支付，不能进入每步主计算性能 claim。
- `StateVectorRow.by_vector_index` 可能与默认 packed scan 重叠；是否需要 maintained order sidecar 应由 benchmark 决定。

建议 benchmark：

- `StateVectorRow` Row Pipeline update vs ColumnView/scratch vs `mutateAt` vs primitive array baseline。
- `PendingEventRow` due consume/remove，覆盖 queue size、due ratio、remove/compact、order sidecar dirty/rebuild。
- `TraceSampleRow` append/export order。
- `FlowCoefficient` keyed fetch vs valve-local dense projection vs state-vector adjacent projection。
- `by_vector_index` order source vs default packed scan。

### 4.4 Game Runtime

判定：已修正后保留为临时蓝图；没有 P0 冲突；正式示例已同步 selected-unit workspace、coordinate lookup、occupancy cache 和 damage resolution 口径。

通过项：

- `MoveCandidateRow` 明确是 selected-unit/current-action dense workspace，不是默认全局 action frontier。
- 全局 `ActionCandidate` frontier 只作为 future option，且要求 stable identity、version、失效规则、index 和 cache 清理策略。
- `PendingDamageRow` 是 resolution buffer，不是 history/replay log。
- `GameUnit.position` 是 source-of-truth，`MapTileRow.occupantUnit` 是 occupancy cache。
- damage resolution 成本被拆成 pending traversal、`units.fetch`、`units.mutate` 和 `clear()` sidecar dirty，不再误称为纯 dense scan。

主要风险：

- `MapTileRow` dense grid + `by_grid_position` 只表达 ordered traversal，不等价 O(1) coordinate lookup。
- 如果 pathing inner loop 频繁 `(x, y) -> tile`，正式示例需要选择外部 grid adapter、keyed `MapTile`、unique coordinate source，或明确 dense scan 只适合渲染/visibility pass。
- `MoveCandidateRow.by_total_cost` 是 selected-unit workspace 的待验证 order，不应被误解成全局行动策略。
- `GameUnit.position` 与 `MapTileRow.occupantUnit` 的同步失败需要 game loop 停止 frame、回滚外部 snapshot 或重建 occupancy cache，不能暗示 SOMA 提供跨 table transaction。
- pending damage 若跨 tick、网络重放、幂等结算或取消去重，需要另建 keyed event/command table。

建议 benchmark：

- selected-unit `MoveCandidateRow.replaceAll(batch)`、candidate count、capacity reuse/growth、order dirty/rebuild、`byTotalCost().firstOrThrow()`。
- coordinate lookup：dense scan/order source、外部 `(x,y)->rowIndex` adapter、keyed tile table，并验证 duplicate/missing coordinate 初始化检查。
- `PendingDamageRow` ordered traversal、target `fetch`、target `mutate`、重复 target 聚合、`clear()` sidecar dirty/rebuild。
- 全局 `ActionCandidate` 只做 diagnostic lane，不进入 release performance claim。
- occupancy consistency invariant 场景：move commit 成功、cache 更新失败、rebuild/stop-frame/外部 snapshot。

## 5. 横向设计压力点

### 5.1 Schema Annotation

当前正式 schema 模型能覆盖四个场景，但必须继续守住以下边界：

- schema 只固化稳定事实和稳定访问路径；策略排序、临时 score、dispatch/action rule 不应默认进入 `@SomaOrder`。
- `runtime frontier`、`workspace`、`lookup`、`entity state`、`export buffer` 是建模场景，不是新的 schema kind；V1 不需要 `@SomaTableRole`。
- `@SomaOrder` 表达 table-scoped ordered access，不是 heap、range query、priority queue、O(1) lookup 或物理排序。
- keyed frontier 只有在存在稳定或 epoch-scoped logical identity、需要跨轮次保留、按 key/index 删除和局部刷新时才成立。
- dense workspace 不等于短生命周期对象池，它的价值来自 reusable capacity、packed scan、batch refresh、ColumnView 和 access path。

暴露出的 schema 压力点：

- VRP keyed `InsertionCandidateKey` 属于 epoch-scoped identity，正式文档需要能表达“frontier identity 的有效域”，但不需要新增 annotation。
- 连续仿真需要更明确的 variable dimension，例如 `SimVariableKind` 或 `variableId`，否则 dense state vector 的行语义不清。
- Game coordinate lookup 若成为主路径，dense `MapTileRow` + order 不够；可能要转为 keyed/unique coordinate 方案，或承认外部 grid adapter 属于 application-owned cache。

### 5.2 Generated API / Row Pipeline / Batch / ColumnView

现有 API 在四个场景中基本自然：

- `findByXxx(...)` / `byXxx(...)` 作为 Row Pipeline source。
- `update(...)` 和 `remove()` 表达 set-oriented mutation。
- dense `replaceAll(batch)` 适合作 reusable workspace refresh。
- dynamic `sorted(comparator)` 表达策略排序，comparator 只读当前 row。
- ColumnView 支持极端 hot path 的 read-only primitive scan。

暴露出的 API 压力点：

- route segment rewrite 目前没有清晰 public/generated API 形态。可以先要求业务做 route-local rebuild 或 whole-table `replaceAll`，但如果 VRP benchmark 显示它是主瓶颈，再研究受控 segment rewrite API。
- top-k buffer 目前只能作为 dynamic `sorted(...).limit(n)` 的内部优化研究，不应暴露为 V1 public contract。
- event queue 的 due-range consume/remove 不能通过 `@SomaOrder` 暗示；若 future 需要 range remove 或 heap，需要独立设计，不应伪装成 Row Pipeline 现有能力。
- active ColumnView 下 mutation 的示例教育还不足，尤其是用户容易把 ColumnView read 和同表 update 写在同一 scope。

### 5.3 Runtime Core / Sidecar / Lifecycle

runtime core 的 `TableStore` 组合模型能解释四个场景：

- `KeySpace` 支撑 keyed state/lookup/frontier。
- `RowSpace` 和 packed storage 支撑 dense state/workspace/export。
- `AccessStructures` 支撑 secondary index、unique index、order sidecar。
- `AccessPath` 区分 scan、index source、order source、dynamic sort。
- `MutationCoordinator` 负责 batch、replaceAll、delete、row move、sidecar dirty/rebuild、epoch/stats。
- `LifecycleState` 表达 active view、released、typed lifecycle errors。

暴露出的 runtime 压力点：

- sidecar dirty/rebuild 成本必须在 stats/benchmark 中可见，不能被 `firstOrThrow()` 或示例 hot loop 掩盖。
- structural mutation 与 active ColumnView 的 `view_pinned` 需要覆盖到 examples/gates。
- row move/segment rewrite 是 VRP 典型压力点，但不应在未 benchmark 前进入 V1 必须能力。
- dense grid coordinate lookup 不应从 order sidecar 推导为 O(1)。
- SOMA 只保证单 table mutation 后内部一致性；跨表一致性、失败恢复和补偿必须由上层 loop 拥有。

### 5.4 Performance Model / Benchmark

正式 `docs/runtime-performance-model.md` 的方向正确，但四个蓝图要求后续 benchmark lane 更丰富。新增建议：

- FJSP frontier maintenance：add/update/sort/remove/setup lookup/machine order/export。
- VRP dense workspace vs keyed frontier vs route segment rewrite vs travel-cost lookup/preprojection。
- 连续仿真 state vector update、event queue consume/remove、trace append/export、coefficient preprojection。
- Game selected-unit move workspace、coordinate lookup、pending damage resolution、occupancy consistency。

所有 performance claim 必须区分 smoke、diagnostic benchmark 和 claim-grade benchmark。示例能跑、benchmark smoke 成功或一次本机结果都不能写成性能优势声明。

### 5.5 Examples Governance / 用户体验

四个蓝图说明 SOMA 可以提供清晰的用户路径：

- FJSP：用户用 keyed runtime frontier 保存已 release 候选，用 dynamic sort 表达调度策略。
- VRP：用户可以先用 dense workspace 快速建模，再在瓶颈明确后升级为 keyed frontier 或 route-local rebuild。
- 连续仿真：用户可以把 dense state vector 作为 hot numeric state，并把 trace/export 与主循环分离。
- Game：用户可以把 SOMA 当作 hot runtime state container，而不是 ECS、pathfinding engine 或 replay system。

同时需要加强示例教育：

- 不要把 `replaceAll(buildXxx())` 当成免费边界。
- 不要在 comparator 中做 cross-table lookup。
- 不要把 `@SomaOrder` 当成策略排序默认答案。
- 不要让 DTO/cache/snapshot 字段和 runtime state 形成双事实源。
- 不要暗示 SOMA V1 提供跨 table transaction、heap、range-pop、join planner、pathfinding 或 ODE solver。

## 6. 正式设计文档 Gap Mapping

### P0

P0 表示：若要把相关蓝图内容迁移为正式示例或 release claim，必须先修正；不表示当前临时蓝图不能保留。

| 文档 | Gap | 建议 | 本轮状态 |
|---|---|---|---|
| `soma-examples/docs/simulation-runtime-state-example.md` | `StateVectorRow` 与 `Tank.levelLiters`、`Tank.temperatureCelsius`、可能的 `Valve.openingRatio` 之间 source-of-truth 未正式收口 | 明确 `StateVectorRow` 是数值状态事实源，entity 字段是 boundary cache/export，或反向选择另一条方案 | 已同步到正式示例 |
| `soma-examples/docs/simulation-runtime-state-example.md` | `StateVectorRow` 缺少 `SimVariableKind` / `variableId` 一类变量维度，无法可靠表达同一实体多变量 | 补充变量维度或说明 state vector 只承载单变量场景 | 已同步到正式示例 |
| `soma-examples/docs/fjsp-runtime-state-example.md` | `SetupTime` 缺失语义与跨 table commit 失败处理仍需正式化到可执行场景层 | 定稿 missing setup 语义；补 solver-level commit failure 策略 | 已同步到正式示例；`SetupTime` canonical 为 required lookup |

### P1

| 文档 | Gap | 建议 | 本轮状态 |
|---|---|---|---|
| `soma-examples/docs/vrp-runtime-state-example.md` | `Customer.state/assignedRoute/assignedPosition/UnassignedCustomerRow/RouteVersion` source-of-truth 未完全收口 | 明确 `RouteVisitRow.position` 是 route sequence 事实源；`assignedPosition` 删除或降级为诊断 snapshot | 已同步到正式示例 |
| `soma-examples/docs/vrp-runtime-state-example.md` | `TravelCost.fetch` missing 语义未选择 | 在示例中固定 required lookup error、infeasible candidate 或 fallback policy | 已同步为 canonical required lookup |
| `soma-examples/docs/vrp-runtime-state-example.md` | route segment rewrite 的 public/generated API 边界未说明 | 先记录 whole-table rebuild / route-local rebuild / future segment API 三种选择 | 已同步；future segment API 仍不承诺 |
| `soma-examples/docs/game-runtime-state-example.md` | `MoveCandidateRow` 未足够强调 selected-unit/current-action workspace | 明确它不是 all-units global frontier | 已同步到正式示例 |
| `soma-examples/docs/game-runtime-state-example.md` | `GameUnit.position` 与 `MapTileRow.occupantUnit` source-of-truth/cache 未正式写清 | 明确位置事实源、occupancy cache、失败恢复和无跨表 transaction | 已同步到正式示例 |
| `soma-examples/docs/game-runtime-state-example.md` | coordinate lookup 边界不够醒目 | 明确 `by_grid_position` 是 ordered traversal，不是 O(1) coordinate lookup | 已同步到正式示例 |
| `soma-examples/docs/simulation-runtime-state-example.md` | `PendingEventRow.by_event_time`、`TraceSampleRow.by_time_entity` 的性能口径不够明确 | 强调不是 heap/range-pop；trace order 只在 export/diagnostic terminal 支付 | 已同步到正式示例 |
| `soma-benchmarks/docs/` | benchmark evidence contract 仍缺正式文档 | 建立 benchmark runner、JSONL schema、claim-grade 口径和场景 lane | 保留为后续 benchmark 治理专题 |

### P2

| 文档 | Gap | 建议 |
|---|---|---|
| `docs/runtime-performance-model.md` | 已有 FJSP mapping，但缺 VRP/simulation/game mapping | 后续追加三类场景的 performance mapping |
| `soma-processor/docs/processor-codegen-contract.md` | grouped source golden 目前以 FJSP/RouteVisit 为主 | 后续用 Game coordinate 或 Simulation event order 补更多 golden case |
| `docs/row-pipeline-api-contract.md` | top-k、range remove、segment rewrite 都未承诺 | 维持不承诺；若 benchmark 证明必要，再开独立设计 |
| `docs/domain-glossary.md` | runtime frontier 定义以 FJSP 为主 | 可补一句 frontier 不等于所有候选集合；dense workspace 仍是合法选择 |

## 7. 暂不进入正式设计的内容

以下内容应继续留在 `docs/temp/` 或 benchmark research，不应直接进入正式契约：

- VRP keyed `InsertionCandidate` 作为默认替代方案。
- Game 全局 `ActionCandidate` frontier。
- `top-k` public terminal 或把 top-k 写成 V1 已承诺 API。
- route segment rewrite public API。
- event queue heap / range-pop / prefix remove。
- automatic join planner、lambda predicate index pushdown、comparator cross-table lookup 优化。
- pathfinding engine、ECS、ODE solver、game replay/log engine。
- cross-table transaction 或自动补偿。

## 8. 建议进入 Benchmark 的问题

第一批 benchmark 候选：

1. FJSP frontier maintenance：`MachineCandidate.addBatch`、indicator update、dynamic sort/top-k、cleanup remove、machine order sidecar。
2. VRP candidate generation：dense full rebuild、dynamic sort、maintained `by_best_delta`、keyed frontier、route segment rewrite、`TravelCost.fetch`。
3. Simulation state vector：Row Pipeline update、ColumnView/scratch、primitive baseline、event due consume/remove、trace append/export、FlowCoefficient preprojection。
4. Game runtime：selected-unit move workspace、coordinate lookup variants、pending damage resolution、occupancy cache rebuild.

Benchmark 输出至少应包含 phase timing、candidate/row counts、scanned/matched/changed/removed rows、DTO allocation、sidecar dirty/rebuild count/time、KeySpace insert/remove、missing key count、temporary row-index buffer size、JVM/CPU/seed/scale/warmup/repetition。

## 9. SOMA 已经体现出的优雅路径

本轮审查也证明 SOMA V1 的核心方向有价值：

- schema 以 Java annotation 表达 runtime state，用户不需要维护另一套 IDL。
- generated table-first API 符合 Java 8 心智，`findByXxx`、`byXxx`、`filter`、`update`、`remove`、`firstOrThrow` 组合自然。
- Row Pipeline cursor 避免 hot loop per-row DTO materialization，同时仍保留 DTO boundary。
- dense table 可以清晰覆盖 state vector、route sequence、move workspace、trace/export buffer。
- keyed table 可以清晰覆盖 entity state、lookup、runtime frontier。
- ColumnView 给极端 hot path 留出显式性能工程路径，同时有清晰 lifecycle 边界。
- runtime core 的 TableStore 组合模型能解释 key/index/order/sidecar/lifecycle，而不暴露内部结构给 public API。

## 10. 自审结论

本轮自审结论：

- 没有把 FJSP frontier 绝对化；VRP 和 Game 仍以 dense workspace 为默认，连续仿真不是 frontier 问题。
- 已区分 long-lived state、lookup、runtime frontier、dense workspace、export buffer 和 derived cache。
- 已显式识别 `replaceAll(buildXxx())`、`sorted(comparator)`、`fetch(...)`、builder 构造、DTO materialization、sidecar dirty/rebuild、random lookup 等 hot-loop 成本。
- 已避免把 comparator cross-table lookup 写成可接受方案。
- 已区分 `@SomaOrder` maintained order 与 dynamic sort，不声明二者性能等价。
- 已明确 SOMA V1 不承诺跨 table transaction、heap、range-pop、pathfinding、ECS、ODE solver、join planner 或 automatic predicate pushdown。
- 本轮已经把 P0/P1 中会误导正式示例的口径同步到 `soma-examples/docs/`，但未把临时蓝图整体固化为正式设计。

## 11. 收口建议

本专题可以阶段性收口：四个临时蓝图都能帮助发现 SOMA V1 的真实设计压力点，但现在还不适合整体固化为正式设计。

建议下一步按优先级推进：

1. 建立 `soma-benchmarks/docs/benchmark-evidence-contract.md` 或等价正式文档，把四类场景 lane 和 claim-grade 口径固化。
2. 再决定 route segment rewrite、coordinate lookup、event queue、top-k 是否需要进入 generated API/runtime 设计。
3. 继续把四个蓝图保留在 `docs/temp/` 供人工审核；只有经过 benchmark 或正式设计决策的稳定事实才迁移进正式契约。
