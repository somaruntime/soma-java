# 四个 SOMA 临时蓝图深度设计审查报告

状态：临时治理报告；已按 2026-07-10 SomaTable 正式设计基线复审
日期：2026-07-10
范围：[FJSP blueprint](../../docs/temp/fjsp-machine-candidate-frontier-blueprint.md)、[VRP blueprint](../../docs/temp/vrp-runtime-frontier-blueprint.md)、[Simulation blueprint](../../docs/temp/simulation-runtime-state-blueprint.md)、[Game blueprint](../../docs/temp/game-runtime-frontier-blueprint.md)

## 1. 审查目标

本报告判断四个临时蓝图在 SomaTable 设计宪法固化后是否仍然成立，哪些内容只需术语/API 同步，哪些内容暴露了新的 application data role、ownership、Materialized Object、性能或 evidence 缺口。

本报告不把临时蓝图整体提升为正式契约，也不批准新的 public/generated API。稳定事实仍由根 `docs/` 和模块 owner 文档拥有；临时蓝图负责探索真实场景和记录尚未正式化的设计压力。

## 2. 当前正式事实源

本次 delta review 以以下正式文档为准：

- [SomaTable 设计宪法](../../docs/soma-table-design-constitution.md)；
- [领域术语](../../docs/domain-glossary.md)；
- [架构设计](../../docs/architecture-design.md)；
- [Generated Table API 契约](../../docs/generated-table-api-contract.md)；
- [Runtime correctness model](../../docs/runtime-correctness-model.md)；
- [Runtime performance model](../../docs/runtime-performance-model.md)；
- [Annotation schema contract](../../soma-annotations/docs/annotation-schema-contract.md)；
- [Code generation contract](../../soma-processor/docs/code-generation-contract.md)；
- [TableStore contract](../../soma-runtime-core/docs/table-store-contract.md)；
- [Runtime performance implementation contract](../../soma-runtime-core/docs/runtime-performance-implementation-contract.md)；
- [Testkit contract](../../soma-testkit/docs/testkit-contract.md)；
- [Benchmark evidence contract](../../soma-benchmarks/docs/benchmark-evidence-contract.md)；
- [Validation gates](../../docs/validation-gates.md)。

2026-07-07 的初次审查使用四个只读 subagent 分场景分析；本次复审直接对照当前正式 owner 文档和已修改蓝图，没有让 subagent 修改文件。

## 3. 总体结论

四个蓝图的核心场景判断仍然成立：

- FJSP 继续使用 keyed `MachineCandidate` runtime frontier；
- VRP 的 insertion candidate 默认仍是 dense workspace，keyed frontier 只是严格条件下的候选；
- Simulation 核心仍是 dense long-lived state vector、event queue 和 trace/export buffer；
- Game 的 move candidate 仍是 selected-unit dense workspace，pending damage 仍是 resolution buffer。

本轮新增的横向结论是：四个场景都必须先把数据分为 input facts、working state 和 result facts，再讨论 keyed/dense、root/child 和 access path。三类职责默认按生命周期、可变性和访问模式分开，但不能机械创建 result shadow table；current solution/state 已经是结果事实唯一 owner 时，应直接 materialize/export。

新的 schema/materialization 设计改变了旧的 P0 boundary：`@SomaTable` class 现在既定义 row schema，也直接作为 detached single-row materialization type；`@SomaValue` 是 compiler-defined immutable value；`List`/`Map` 分别表达 dense/keyed table 的逻辑容器。四个蓝图现已统一为：

```text
@SomaTable class
  -> row schema + detached single-row carrier

fetch/findFirst/firstOrThrow
  -> schema class

Row Pipeline fetchAll
  -> List<R>

whole-table materialize
  -> dense List<R> / keyed Map<K,R>

Row Pipeline callback
  -> callback-scoped Row Cursor

external API/wire object
  -> DTO adapter
```

新的实质性设计压力分成两类：一是 FJSP/VRP/Simulation/Game 的 mixed entity rows 是否应拆分 input definition、working state、result fact；二是何时使用 parent-owned `List`/`Map` child 而不是 flat root table。FJSP candidate-machine input 和 VRP route visits 已有明确 ownership/access pattern，分别采用 per-operation/per-route dense child；同语义 flat table 保留为 benchmark baseline。

## 4. 横向设计基线

### 4.1 Schema-backed row、Value 与 Cursor

- `@SomaTable` class 定义 schema 并直接作为 detached single-row materialization type，但不是 live runtime row/storage；
- `@SomaValue` 由 compiler 提供 implicit final class、public-final fields、construction 与 canonical equality/hash；
- dense/keyed child materialize 为 `List`/`Map`，runtime parent row 只保存 child handle；
- `@SomaTable` row 不生成 structural equality/hash，Java Collection 与 immutable Value 各自保留明确 equality contract；
- Row Pipeline callback 参数是 borrowed cursor，不允许逃逸；
- `firstOrThrow()` / `fetch()` 即使只返回一行，也发生 schema-object materialization，应纳入 allocation evidence。

### 4.2 Application dataflow 与 data role

使用方 canonical dataflow 是：

```text
external input -> validate/map/Batch -> input fact tables
input facts + working state -> high-performance computation -> authoritative result facts
result facts -> Materialized Object -> external DTO/wire/file
```

审核规则：

- 每张 table 或明确 field group 标明 input facts、working state 或 result facts；
- working state 继续区分 authoritative、rebuildable frontier/workspace 和 derived cache；
- 生命周期、可变性或 access pattern 明显不同时默认分开；
- co-location 必须由 locality、一致性或 benchmark 解释；
- result 已由 current state 唯一持有时直接 export，不再维护 shadow result table；
- data role 不成为 `@SomaTableRole`、table kind 或 schema-hash input。

### 4.3 Ownership 与 child table

Table kind 与 ownership 正交。Keyed/dense table 都可以作为 root、parent 或 child，但只有 parent row exclusive owns child instance 时才建立 `@SomaChild List<R>` / `Map<K,R>` field。

不应机械使用 child table：

- FJSP `MachineCandidate` 同时按 machine 和 operation 访问，不独占归属于任一 parent row；
- Simulation `StateVectorRow` 需要跨实体连续扫描，拆成 per-entity child 会破坏 global vector locality；
- Game `MoveCandidateRow` 和 `PendingDamageRow` 属于 action/resolution phase，不属于 `GameUnit` 的长期生命周期；
- VRP `RouteVisitRow` 则具有明确的 route ownership，必须进入 flat-vs-child 决策。

### 4.4 并发、事务与持久化

四个蓝图都遵守：

- ownership aggregate 只由单线程同步访问；
- 不支持 concurrent read/write、parallel pipeline 或 internal lock；
- 不提供跨独立 root table transaction；
- solver/simulator/game loop 拥有跨 table 提交顺序、补偿、停止或外部 snapshot 恢复；
- SomaTable、Materialized Object 和 trace/workspace 都不是 persistence/wire format；持久化、序列化和 DTO mapping 只属于外部 adapter。

### 4.5 MaterializationBudget 与 export

Boundary export 必须分开计量：

```text
SomaTable -> Materialized Object
Materialized Object -> external DTO
external DTO -> wire/file
```

递归 materialization 只沿 ownership edge，并受 runtime-plan `MaterializationBudget` 约束。超限不返回 partial object graph，也不修改 Table。Blueprint benchmark 不得把 schema object/List/Map allocation 或 external DTO mapping 混入 Row Pipeline/ColumnView hot-path claim。

### 4.6 浮点异常值

普通 floating payload 允许 Java IEEE-754 的 NaN、Infinity 和 negative zero；NaN 不能表达 absence。进入 key/index/unique/order 的 floating leaf 使用 finite + canonical positive-zero 语义。

Simulation 的 state/derivative/scale 属于普通 payload；finite、non-zero scale、non-negative 等物理约束由 simulator/loader 负责，并必须有独立的 failure/diagnostic policy。

### 4.7 Runtime performance implementation discipline

四个蓝图都使用同一套五层模型：

```text
Access Pattern
  -> Logical Table / Ownership Model
  -> Physical Layout and Access Structures
  -> Generated / Runtime Execution Kernel
  -> Stats, Benchmark and Runtime-plan Feedback
```

每个蓝图已增加 Access Pattern Card，记录核心 table/phase 的 rows/cardinality、hot columns、access/mutation mix、selector/optional/child density、working set、allocation 和 export boundary。Card 是 scenario/runtime-plan 输入，不进入 Schema/hash，也不预设具体规模或性能优势。

Runtime 实现必须保持 packed `[0,size)`、primitive-specialized、fused、no-per-row allocation hot path；growth、rehash、compaction、sidecar rebuild、scratch high-water 和 stats overhead 可观察。四个场景的性能结论不得建立在 boxed sidecar、intermediate Collection、persistent tombstone、generic metadata field dispatch 或 hidden rebuild storm 上。

## 5. 逐篇审查结论

### 5.1 FJSP

判定：核心 frontier 模型保留；input/runtime/result 职责进一步拆分；candidate-machine input 使用 per-operation dense child。

保留项：

- `(MachineId, OperationKey)` 是 frontier 有效期内的 logical identity；
- `findByMachine(machineId)` 支撑 dispatch，`findByOperation(operationKey).remove()` 支撑 cleanup；
- dispatch comparator 只读 candidate row，setup lookup 发生在 indicator update；
- dynamic sort 不等价 maintained order；
- solver loop 拥有跨 table commit failure handling。

本次同步：

- `OperationDefinition`、`OperationRuntimeState`、`OperationAssignment` 分别拥有 input、working、result facts；
- assignment row 是唯一结果事实，definition/runtime state 不再保存 assignment shadow fields；
- `OperationDefinition` / `OperationRuntimeState` / `MachineState` / `MachineCandidate` 直接作为 detached single-row materialization type；
- `OperationDefinition.candidateMachines` 使用 `List<CandidateMachineDefinition>` dense child，flat composite-key processing table 保留 benchmark baseline；
- DTO export 拆成 Materialized Object 与 external DTO adapter；
- `fetch()` / `firstOrThrow()` 的 schema object/List allocation 进入 benchmark；
- 明确 `MachineCandidate` 不属于 machine/operation child ownership。

待 evidence：candidate dense child vs flat grouped-index baseline、definition/state split lookup 与旧 co-located row、frontier release/update/sort/remove、setup lookup、machine order sidecar、assignment commit、selected object export 和 dense workspace baseline。

### 5.2 VRP

判定：candidate workspace/frontier 判断保留；`RouteVisitRow` 采用 parent-owned dense child，flat dense 作为 benchmark baseline。

Candidate 结论不变：

- `InsertionCandidateRow` 默认是 dense workspace；
- keyed insertion frontier 只有在跨轮保留、局部失效和 `RouteVersion` stale guard 成立时才进入 benchmark；
- `RouteVisitRow.position` 不是 stable key；
- `TravelCost` missing 语义由 constructor 固定，runtime 不猜测。

Data role 优化：

- `CustomerDefinition` 保存 input facts，`CustomerAssignment` 独占 assignment result；
- `UnassignedCustomerRow` 降级为 definitions - assignments 可重建 workspace，不再与 `Customer.state` 共同表达权威状态；
- `Route` / `RouteVisitRow` 是 constructor current solution，也是最终 route result 的唯一事实源；不复制 `RouteResult` shadow table。

Ownership 新判断：

- route-local ownership/lifecycle 支持 per-route dense child；
- child row 可删除冗余 `routeId`，order 只保留 `position` 和完整 tie-break；
- required child logical empty、lazy storage、replace 全有或全无、parent lifecycle cascade；
- live child 不能共享或 reparent；
- global all-route scan 可能更适合 flat table，不能只凭局部性做性能结论。

Generated live access contract 已进入 owner design obligation：hot loop 通过 parent key 获取 typed live child facade，例如 `routes.visits(routeId)`，不递归构造完整 `Route + List<RouteVisitRow>`。Exact generated name 由 golden 固化，但能力不再是 optional checkpoint。

待 evidence：flat-vs-child route scan/rewrite/global scan、recursive object/List budget、dense candidate rebuild、keyed frontier、TravelCost preprojection 和 segment rewrite。

### 5.3 Continuous Simulation

判定：dense numeric state 模型保留；input definition、numeric working state、trace/final result 明确分层；新增 floating exceptional-value policy。

保留项：

- `StateVectorRow` 是 canonical numeric state source-of-truth；
- entity table 中的对应字段是 boundary cache/export observation；
- `PendingEventRow.by_event_time` 是 order source，不是 heap/range-pop；
- trace order 成本只在 export/diagnostic boundary 支付；
- ColumnView read 与 mutation 分阶段，避免 active view conflict；
- coefficient random lookup 是否 preproject 由 benchmark 决定。

本次同步：

- `TankDefinition` / `ValveDefinition` 保存 topology/parameter input，numeric state 只由 `StateVectorRow` 权威持有；
- optimized blueprint 默认不保留 entity numeric shadow cache；兼容旧示例时只能作为 derived cache；
- `TraceSampleRow` 是 sampled result，final state 直接从 state vector projection，不复制 `FinalStateRow`；
- generated materialization result 统一使用 schema-backed Materialized Object；
- Materialized Object、external DTO 和 wire/file export 分阶段；
- ordinary floating payload 的 NaN/Infinity 由 simulator policy 解释；
- finite value、non-zero scale 和 exceptional integration outcome 进入 fixture/evidence。

Simulation 不改成 per-entity child，因为核心 hot loop 是跨实体连续 state-vector scan。

### 5.4 Game Runtime

判定：selected-unit workspace 模型保留；input definition、battle state、phase workspace 与 result projection 明确分层；拒绝无 owner 的 child table。

保留项：

- `MoveCandidateRow` 是 current action dense workspace，不是全局 action frontier；
- `PendingDamageRow` 是 resolution buffer，不是 history/replay log；
- `GameUnitState.position` 是位置事实源，`TileOccupancyRow` 是 application-owned derived cache；
- coordinate order 不是 O(1) lookup；
- game loop 拥有 cache rebuild、frame stop 或外部 snapshot policy。

本次同步：

- player/unit definition 与 mutable state 分开；少量 hot readonly leaf 只允许显式 preprojection；
- `MapTileDefinitionRow` 保存 immutable tile input，`TileOccupancyRow` 保存可重建 occupancy cache；
- final hp/position/score 直接从 authoritative state export，不复制 `BattleResult`；
- `GameUnitState` / `MoveCandidateRow` 直接作为 detached single-row materialization type；
- keyed fetch 的 schema-object allocation 与 damage mutation 分开统计；
- snapshot/export 明确经过 external DTO adapter；
- move/damage workspace 不挂到 `GameUnit` child，避免 entity materialization 递归展开 phase-local workspace。

Future `ActionCandidate`、keyed event/command 或拥有稳定生命周期的 `TurnWorkspace` 必须单独设计，不能从当前蓝图推导为 V1 承诺。

## 6. Gap Mapping

### P0：本轮已收口

| Gap | 处理结果 |
|---|---|
| 旧 public `XxxRecord` / `ChildRecords` 分离契约 | 四蓝图改为 schema class / `List` / `Map` materialization |
| generated result 被统一称为 DTO | 改为 Materialized Object；DTO 只保留 external adapter 含义 |
| Row Pipeline callback 与 materialized object 混淆 | 明确 callback 参数是 borrowed Row Cursor |
| `@SomaValue` 要求用户重复书写 modifiers/equality | 固化 compiler-defined immutable/public-final/construction/equality/hash semantics |
| 旧报告仍称 benchmark contract 缺失 | 更新为正式 benchmark evidence contract 已存在 |
| 旧报告仍称 performance/glossary mapping 待补 | 改为当前 owner 文档已覆盖 |
| 并发/持久化边界未显式 | 四蓝图统一声明 single-owner synchronous / no persistence |
| 缺少 application data role 心智模型 | 正式宪法、术语表、架构数据流、示例总则和四蓝图统一补充 input/working/result 分类 |
| 结果分表可能制造第二事实源 | 固化“独立 result table 可选；current state 已权威持有时直接 export”原则 |
| 列存/Sparse Set/DOD 只有方向、缺少 implementation discipline | 新增 runtime-core 正式 performance implementation contract，固定 packed/primitive/fused/allocation-bounded hot path |
| 蓝图没有可执行的访问模式输入 | 四蓝图增加 Access Pattern Card；明确其属于 scenario/runtime plan，不进入 Schema/hash |

### P1：需要正式设计或 evidence 后迁移

| Gap | Owner/下一步 |
|---|---|
| parent key 下 live parent/child access 且不 deep-materialize | processor/codegen golden + package smoke；能力已进入正式 contract |
| VRP flat dense vs per-route dense child | child 为推荐 schema，flat 保留 benchmark baseline |
| VRP child replacement 与 route scalar commit sequence | runtime/codegen lifecycle contract + invariant tests |
| Simulation finite/non-zero scale policy | simulator example fixture/error evidence |
| materializing terminal 在 hot loop 的 allocation | scenario benchmark phase stats |
| schema object/List/Map deep comparison | testkit explicit comparator/assertion |
| 四场景其余 mixed table 拆分是否进入正式示例 | examples owner + schema/codegen golden + scenario benchmark 后逐场景决策；不机械一次性拆分 |
| FJSP definition/state split 与 assignment table | 正式示例已同步；仍需 lookup/commit/recovery benchmark 与 E2E evidence |
| VRP customer definition/assignment split | workspace rebuild invariant + formal example migration review |
| Simulation/Game definition-state/cache split | state/cache invariant + layout/allocation benchmark |

### P2：继续保留为 research/future option

- route segment rewrite / route-local row move public API；
- public top-k terminal；
- event heap、range-pop、prefix remove；
- generated coordinate O(1) lookup；
- global Game `ActionCandidate` frontier；
- automatic join planner、lambda predicate index pushdown；
- cross-table transaction 或自动补偿。

## 7. Benchmark 映射

正式 benchmark evidence contract 已覆盖：

- FJSP frontier maintenance 与 dense baseline；
- VRP dense workspace、keyed frontier、route rewrite 和 TravelCost lookup；
- Simulation state vector、event queue、trace export 和 coefficient preprojection；
- Game move workspace、coordinate lookup、damage resolution 和 occupancy consistency；
- input/working/result split 与 co-located baseline 的 lookup、mutation、rebuild、memory 和 export 成本；
- child locality、flat filter、recursive materialization、budget boundary 和 external DTO mapping。
- packed scan、pipeline fusion、KeySpace、sidecar clean/dirty/rebuild storm、compaction/capacity/scratch 和 stats-overhead component lanes；
- 四蓝图 Access Pattern Card 的 rows/hot columns/selectivity/optional-child density/working-set/allocation inputs。

VRP 后续需要把 generic child locality lane 绑定到明确场景参数：route count、visits per route、mutation ratio、global scan ratio、parent object export ratio 和 effective budget。没有 benchmark 结果前，本报告只批准比较问题，不批准性能结论。

## 8. 暂不进入正式设计的内容

以下内容继续留在 `docs/temp/` 或 benchmark research：

- VRP keyed `InsertionCandidate` 作为默认方案；
- 把 VRP flat root visits 恢复为唯一正式 schema；flat 只作为 benchmark baseline；
- Game 全局 `ActionCandidate` frontier；
- route segment rewrite/top-k/event heap/coordinate O(1) public API；
- cross-table transaction、ECS、pathfinding、ODE solver、replay engine；
- 任何没有 baseline/scale/environment/repetition 的性能优势结论；
- 把 application data role 编码成新的 annotation/table kind/schema hash 字段。

## 9. 自审结论

本次复审确认：

- 没有把 FJSP frontier 机械推广到其他场景；
- 没有把 child table 机械推广到所有局部集合；
- schema-backed row、Materialized Object、Cursor、immutable Value 和 external DTO 已分开；
- ownership、source-of-truth、derived cache 和 cross-table consistency 已分开；
- input facts、working state 和 result facts 已作为 application-facing 第一层分类，并与 physical table kind 分开；
- 四个蓝图都避免为已有 authoritative current state 创建 shadow result table；
- materializing terminal、Row Pipeline、ColumnView 和 external export 的成本已分开；
- floating storage legality 与 simulator business validity 已分开；
- benchmark contract 已存在，不再作为待补 owner 文档；
- 所有高级能力继续受 evidence 和独立设计门禁约束。

## 10. 当前收口判断

四个蓝图现在可以继续作为与正式 SomaTable 宪法一致的临时设计输入。Application data role 心智模型已经进入正式跨模块设计；各场景具体 split/co-location 仍属于临时蓝图建议，必须经过 schema/codegen golden、scenario invariant 和 benchmark 后再迁移正式示例。

FJSP、Simulation 和 Game 没有新的 SOMA architecture blocker。FJSP 的 operation definition/state/assignment 及 candidate child 已同步到正式 example；Simulation 的 definition/state/cache 与 Game 的 definition/battle state/tile cache 仍是逐场景 migration checkpoint，不能在缺少 benchmark 时机械拆分。VRP 的 per-route visits child 已同步到正式 example，customer definition/assignment split 仍保留为场景 checkpoint。

VRP 的 candidate 模型同样可以继续；`RouteVisitRow` ownership 已收敛为 per-route dense child。Generated key-scoped live child access 与 flat-vs-child evidence 仍是实现/证据 checkpoint，但不再反向悬置 ownership 语义。
