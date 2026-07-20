# Runtime-state benchmark 契约

状态：正式设计文档
Owner：`soma-benchmarks`
事实范围：runtime implementation shape、FJSP、VRP、Simulation、Game、child locality 和 deep-materialization benchmark lanes
非事实范围：evidence level/artifact、public API/schema/runtime contract 和性能结果
最后审查日期：2026-07-20

## 1. 目标

本文定义 SOMA Java V1 的具体 benchmark scenario/lane。每条 lane 都必须遵守 [Benchmark evidence 契约](benchmark-evidence-contract.md) 的 smoke、diagnostic、claim-grade 和 artifact 规则。

Scenario contract 只定义“测什么、与什么同语义 baseline 比较”，不预先声明哪个方案更快，也不把 blueprint 方案升级为 V1 public capability。

## 2. Runtime implementation shape lanes

Scenario benchmark 之前必须有 component-level shape evidence，避免把实现缺陷误判为建模结论：

| Lane | 必须比较 | 主要证据 |
|---|---|---|
| `kernel.packed_scan` | Row Pipeline、Column path、handwritten primitive array | packed rows、touched columns/bytes、rows/s、allocation/op |
| `kernel.pipeline_fusion` | fused terminal 与显式 intermediate baseline；exact source的count/filter/sorted + `IndexSnapshot`/materialization子lane | traversal count、Cursor count、intermediate allocation、snapshot copy、materialization、short-circuit |
| `kernel.keyspace` | full-int/long/composite Hash KeySpace 与同语义 primitive baseline | load factor、probe/collision、rehash、missing、allocation |
| `kernel.exact_index` | incremental append/update/remove、collision equality、mutation/read storm；rows与distinct groups独立变化的cardinality matrix | entry/group、probe/collision/rehash、no-read-rebuild、row-link与group/bucket retained/high-water bytes |
| `kernel.compaction` | single/batch swap-remove、clear reuse | moved rows、locator/exact-link repair、scratch、retained capacity |
| `kernel.stats_overhead` | summary-only 与 diagnostic mode | time/allocation delta、counter/histogram cost |

这些 lane 验证 runtime implementation shape，不直接批准某个 application schema。Scenario 建模建议仍需要对应 FJSP/VRP/Simulation/Game lane。

## 3. FJSP benchmark lanes

FJSP 的 canonical 场景是 `MachineCandidate` keyed runtime frontier。该结论只适用于候选身份稳定、候选跨 dispatch 轮次增量维护、并且需要按 machine / operation 删除的 FJSP dispatch 场景，不推广为所有候选集的通用模式。

### 3.1 Frontier maintenance

| Lane | 度量对象 | 必须拆分的成本 | 不允许的结论 |
|---|---|---|---|
| `fjsp.input.candidate_child_scan` | 遍历 `OperationDefinition.candidateMachines` dense child | parent fetch/live-child locate、child rows、packed scan、allocation、TableStore count | 只证明 per-operation owned input 的 locality/cost |
| `fjsp.input.candidate_flat_index` | flat composite-key processing table grouped exact-index baseline | exact lookup、candidate rows、physical locality、exact-index memory | 作为同语义 child baseline，不预设更慢 |
| `fjsp.frontier.release` | release operation 后为可加工 machine 生成 candidate | candidate-child scan、setup lookup、candidate batch builder、`addBatch`、by_machine/by_operation exact-index delta | 不能说所有场景都应使用 keyed frontier |
| `fjsp.frontier.indicator_update` | 当前 machine 下更新 setup、ready、FCFS/SPT sort values | `findByMachine` candidate rows、`SetupTime.fetch`、changed rows、unchanged exact-index entries | 不能把 setup lookup 隐藏进 comparator |
| `fjsp.frontier.dispatch_sort` | dynamic sort 选择当前 machine 的候选 | candidate count、comparator 调用、`IndexBuffer` growth/reuse、`firstOrThrow` | 不能宣称等价 application-owned priority queue |
| `fjsp.frontier.cleanup` | 选中 operation 后删除所有 machine-operation candidate | `findByOperation` rows、`remove`、index cleanup、compaction | 不能暗示跨 table atomic commit |
| `fjsp.machine_dynamic_sort` | `Machine.rows().sorted(...).firstOrThrow()` | physical scan、`IndexBuffer` sort、comparator calls、machine count | 不能把全量显式排序记成 O(1) |
| `fjsp.data_role.operation_split` | `OperationDefinition + OperationRuntimeState + OperationAssignment` 对比旧 mixed operation row | definition/state lookup、assignment insert、cross-table recovery、result export | 不能复制 assignment shadow fields |

### 3.2 对照 lane

FJSP 必须保留 dense workspace 对照 lane：

- dense `replaceAll(buildCandidatesFor(machineId, now))`；
- dense `replaceAll + dynamic sorted`；
- keyed frontier add/update/sort/remove；
- setup lookup required matrix；
- Materialized Object export and external DTO mapping。

只有当 keyed frontier 在相同语义、相同数据规模和相同 dispatch rule 下表现更好，且 phase evidence 能说明收益来自增量维护而不是算法策略差异时，才能把 keyed frontier 写成该 FJSP 场景的推荐 benchmark 结论。

### 3.3 教学算法的 10 万工序 integrated solve

`fjsp.solve.fcfs_spt_100k` 固定复用 `soma-examples` 的教学算法 kernel，而不是在 benchmark
模块复制 solver。默认 workload 为：

| 维度 | 值 |
|---|---:|
| jobs | 1,000 |
| operations per job | 100 |
| total operations | 100,000 |
| machines | 100 |
| candidate machines per operation | 3 |
| dispatch rule | effective-ready -> FCFS -> SPT -> identity tie-break |

Runner 必须记录 seed、JDK/OS/architecture、JVM args、warmup 和 measurement iteration。
Benchmark-only synthetic problem generation 位于 measurement window 之外；每次 iteration
至少分离 `problem -> FjspInstance` import、`solve`、`export` 三段时间。`solve` 从尚未
release operation 的完整 instance 开始，包含 initial release 和全部 100,000 assignments；
problem generation/import 不得混入 solve 时间。结果至少校验 assignment count、job
completion count、makespan、total tardiness、frontier empty 和 deterministic checksum。

`OperationDefinition.candidateMachines` 仍是 parent-owned dense child。100,000 operation
会超过默认 `maximumOwnershipTableInstances=65,536`，该 preset 必须从 generated default
`RuntimePlan` 派生并显式提高 ownership table-instance 和 aggregate-storage budget，同时在
artifact 中记录 effective plan；不得为绕过预算改成 DTO/Collection live storage。该 lane
首先是 diagnostic evidence，未提供同语义 baseline、独立 fork 和统计分析前
`claimAllowed=false`。

## 4. VRP benchmark lanes

VRP 的默认正式示例仍应把 `InsertionCandidateRow` 作为 dense workspace。Keyed insertion frontier 只在严格前提下进入 benchmark：候选跨轮次保留、可按 route/customer/position 局部失效、`RouteVersion` 能防 stale candidate、全量 rebuild 已被诊断为瓶颈。

### 4.1 Candidate workspace

| Lane | 度量对象 | 必须拆分的成本 | 不允许的结论 |
|---|---|---|---|
| `vrp.dense_workspace.full_rebuild` | 每轮重建全部 insertion candidates | customer/route/visit/travel lookup、builder 构造、`replaceAll`、exact-index bulk build | 不能把 `replaceAll` 当作免费边界 |
| `vrp.dense_workspace.dynamic_sort` | dense workspace 上 dynamic sort / limit | candidate count、`IndexBuffer` growth/reuse、comparator 调用 | 不能推导为长期维护的业务顺序 |
| `vrp.dense_workspace.route_exact_sort` | `findByRoute(routeId).sorted(...).firstOrThrow()` | exact lookup/group size、candidate sort、full-scan baseline | 不能把 exact source 与业务排序混成一种索引 |
| `vrp.keyed_frontier` | 可选 keyed insertion frontier | key insert/remove、findByRoute/findByCustomer、RouteVersion stale cleanup | 不能作为默认方案 |
| `vrp.data_role.customer_split` | `CustomerDefinition + CustomerAssignment + rebuildable UnassignedCustomerRow` 对比 mixed customer row | definition lookup、assignment insert、workspace remove/rebuild、export | unassigned workspace 不能成为 assignment fact source |

### 4.2 Route sequence and travel lookup

| Lane | 度量对象 | 必须拆分的成本 | 设计压力点 |
|---|---|---|---|
| `vrp.route_segment_rewrite` | 插入客户后更新 `RouteVisitRow.position` | whole-table rebuild、route-local rebuild、row move/segment rewrite 候选成本 | 是否需要 future segment API |
| `vrp.travel_cost_fetch` | `TravelCost.fetch(locationPair)` | normal、missing、collision、composite key 构造 | missing 语义必须由业务定义 |
| `vrp.travel_cost_preprojection` | route-local cached neighbor cost / dense projection | projection build、reuse、invalidations | 是否减少 hot loop random lookup |

VRP claim 必须区分数据布局收益、构造启发式算法收益和候选生成策略收益。不能因为 keyed frontier 在某个大规模局部更新场景更快，就推导出 dense workspace 不适合 VRP。

## 5. 连续仿真 benchmark lanes

连续仿真的核心不是 frontier，而是 dense long-lived state vector、application-owned event heap 和 trace/export buffer。

| Lane | 度量对象 | 必须拆分的成本 | 不允许的结论 |
|---|---|---|---|
| `simulation.state_vector.row_pipeline_update` | `StateVectorRow` 原地 update | scanned rows、changed rows、exact-index delta（若有）、materialized object count | 不能把每步 `replaceAll` 当作默认 |
| `simulation.state_vector.column_view` | ColumnView / scratch 两阶段 primitive scan | acquire/read/release、scratch write、释放 view 后 mutation | 不能在 active view 下暗示安全 structural mutation |
| `simulation.state_vector.primitive_baseline` | Java primitive array baseline | loop time、copy/projection cost | 不能用不同语义证明 SOMA 更快 |
| `simulation.event_heap.consume` | application-owned min-heap due consume | heap size、due ratio、push/pop、table diagnostic/export adapter | 不把 heap 能力归入 SOMA runtime |
| `simulation.event_table.scan_sort_remove` | `PendingEventRow` scan/sort/remove 对照 | table size、due ratio、`IndexBuffer` sort、swap-remove | 只作为同语义诊断对照，不承诺 range-pop |
| `simulation.trace.append_export` | trace append 与 export order | append batch、explicit export sort、object/export adapter | 不能把 export order 成本混入主循环 claim |
| `simulation.coefficient_preprojection` | `FlowCoefficient` lookup vs preprojection | keyed fetch、dense projection build、ColumnView scan | 不能让 derivative inner loop 隐藏 random lookup |
| `simulation.data_role.definition_state` | topology/parameter definition + `StateVectorRow` 对比 entity numeric cache | definition lookup、state scan、final projection、legacy cache sync/recovery | 不能同时把 entity cache 和 state vector 写成 authoritative |

仿真场景的 source-of-truth 必须在 benchmark 描述中写清：如果 `StateVectorRow` 是数值状态事实源，`Tank` / `Valve` 中的对应字段只能是 boundary cache、配置或 export snapshot；如果反向选择 entity table 为事实源，则 state vector 只能作为派生 workspace，两者不能混在同一 claim 里。

## 6. Game runtime benchmark lanes

Game runtime 默认把 `MoveCandidateRow` 作为 selected-unit / current-action dense workspace，不把它升级为全局 action frontier。全局 `ActionCandidate` keyed frontier 只能作为 diagnostic lane。

| Lane | 度量对象 | 必须拆分的成本 | 不允许的结论 |
|---|---|---|---|
| `game.move_workspace.selected_unit` | selected unit 的 move candidates | pathing/visibility/ability lookup、builder、`replaceAll`、explicit sort | 不能推广为 all-units frontier |
| `game.move_workspace.dynamic_sort` | dynamic sort / top-k 候选 | candidate count、`IndexBuffer` growth/reuse、tie-breaker | 不能承诺 public top-k terminal |
| `game.action_frontier.optional` | 可选全局 keyed action frontier | stable identity、ActionVersion、invalidations、index cleanup | 不能进入默认正式示例 |
| `game.coordinate_lookup` | `(x, y) -> tile` lookup variants | dense scan、external adapter、keyed/unique coordinate | 不能把物理遍历顺序宣称为 O(1) |
| `game.damage_resolution` | pending damage resolution buffer | explicit sort、target unit `fetch/mutate`、aggregation、`clear` | 不能把它说成纯 dense scan |
| `game.occupancy_consistency` | `GameUnitState.position` 与 `TileOccupancyRow.occupantUnit` 同步 | commit 成功、cache 更新失败、rebuild/stop-frame/snapshot policy | 不能暗示 SOMA 有跨表 transaction |
| `game.data_role.tile_split` | `MapTileDefinitionRow + TileOccupancyRow` 对比 mixed tile row | paired scan/lookup、occupancy rebuild、memory、visibility/pathing access | 不能仅凭 split/co-location 结构推导性能优势 |

Game 场景必须明确 SOMA 不是 ECS、game engine、pathfinding engine、replay system 或 network command log。相关能力如果需要，应作为 application-owned loop、外部 adapter 或后续独立设计专题处理。

## 7. Child locality and deep materialization lanes

Parent-owned child table 与 recursive Materialized Object 必须有独立 evidence lane，不能混入普通 live child scan、Row Pipeline scan 或 response serialization 总耗时。

| Lane | 度量对象 | 必须记录 | Claim boundary |
|---|---|---|---|
| `child_locality.parent_scan` | 单个 parent 的 packed child scan | parent/child rows、physical scan rows、cache/profile counters when available | 只能与同语义 flat baseline 比较 |
| `child_locality.flat_filter` | composite-key flat table scan/index-filter | total rows、candidate rows、index lookup、physical row locality | index candidate 定位不等于 physical continuity |
| `materialization.recursive_success` | root -> child -> grandchild schema object graph | table/row/present-leaf/depth、object/List/Map/entry count、estimated bytes、elapsed | 只证明对应 shape/scale 的成本 |
| `materialization.budget_boundary` | 每个 budget dimension 的 limit/limit+1 | effective budget、dimension、limit、current/proposed、path、no-partial result | smoke 只证明 error path 可运行 |
| `materialization.allocation_failure` | estimator 通过但实际 allocation 失败的受控 fixture | estimate、actual failure category、table/epoch unchanged | 不得与 budget exceed 合并 |
| `materialization.external_dto` | Materialized Object -> external DTO/wire mapping | materialization phase、adapter phase、serialization phase | adapter/serialization 成本不归因于 SOMA runtime |

Default runtime-plan profile 的 32 depth、100,000 table instances、1,000,000 rows、50,000,000 present leaves 和 256 MiB estimate 必须逐维校准。Calibration result 可以建议修改 runtime-plan defaults，但不能改变 schema hash，也不能把某台机器上的阈值写成永久兼容性承诺。

V1 不设置 wall-clock materialization timeout lane。Elapsed time 只作为 diagnostic metric；application timeout/cancellation 必须在独立 orchestration lane 中度量。

## 8. Scenario 与设计边界

- Access Pattern Card 由 formal examples/scenarios 提供；
- benchmark scale、seed、warmup 和 repetition 属于 runner/runtime plan；
- diagnostic result 可以发现设计风险，但不能直接修改 owner contract；
- 只有重复、同语义、claim-grade evidence 才允许提出正式设计变更；
- 未证明的 top-k、route rewrite、event heap、coordinate O(1) 或 cross-table transaction 不进入 V1 承诺。

## 9. 非目标

本文不规定 runner CLI/JSON 字段名、JVM 参数默认值、release 性能阈值，也不报告任何已测得优势。
