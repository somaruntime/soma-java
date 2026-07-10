# Game runtime frontier 临时蓝图

状态：长期研究蓝图
正式事实源：否
已固化内容：[Game schema 示例](../../soma-examples/docs/game-runtime-state-example.md)、[Runtime-state benchmark 契约](../../soma-benchmarks/docs/runtime-state-benchmark-contract.md)
仍在研究：dense action workspace、occupancy lookup 和 game-loop evidence
最后审查日期：2026-07-10

对齐基线：[设计宪法](../soma-table-design-constitution.md)、[Generated Table API](../generated-table-api-contract.md)、[Runtime 性能模型](../runtime-performance-model.md)

## 1. 目标与适用范围

本文基于 `soma-examples/docs/game-runtime-state-example.md`，审视 grid tactics / turn-based game loop 场景下 SOMA runtime state 的建模方式。

Game 场景和 FJSP 的相似点是：都有候选 action / move 的生成与选择。差异是：当前示例的 `MoveCandidateRow` 更适合作为 selected unit / current action 的 dense workspace，而不是全局长期 action frontier。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论 game loop runtime state，不讨论 ECS scheduling、rendering、input、network replication、AI search 或完整 game rules；
- SOMA 保存 hot runtime state，game loop 拥有行动规则、路径搜索、伤害结算、跨 table 一致性和 external DTO export；
- 本文是临时蓝图，不是正式 schema contract。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime storage。Materializing API/terminal 返回 schema class 或 `List`/`Map`，Row Pipeline callback 参数仍是 callback-scoped Row Cursor。`@SomaValue` 由 compiler 提供 immutable value semantics。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化；snapshot/replay/network output 只能由外部 adapter 构造。

## 2. 当前示例中的 runtime tables

现有正式示例包含以下 table：

| Table | 当前形态 | 生命周期判断 | 主要访问方式 |
|---|---|---|---|
| `Player` | keyed entity state | 战局长期 state | `fetch(playerId)`、`mutate(playerId)` |
| `GameUnit` | keyed entity state | 战局长期 mutable state | `fetch(unitId)`、`by_player`、`by_state`、`by_turn_order` |
| `AbilityCost` | keyed lookup data | 导入后只读 lookup | `fetch(unitAbilityKey)` |
| `MapTileRow` | dense long-lived grid layout | 地图生命周期 state | `by_grid_position()`、packed scan |
| `MoveCandidateRow` | dense workspace | 当前行动候选 | `replaceAll(batch)`、`by_total_cost()` |
| `PendingDamageRow` | dense workspace / resolution buffer | 当前结算阶段临时 state | `replaceAll(batch)`、`by_resolution_order()` |

`GridPosition` 是 value field，flatten 到 tile row columns；`MapTileRow` 没有 key，说明当前示例假设主要访问方式是 grid scan / ordered traversal，而不是 coordinate O(1) fetch。

### 2.1 Application data role 审核

| Data role | Table / field group | 权威性与生命周期 | 优化判断 |
|---|---|---|---|
| input facts | `PlayerDefinition`、`GameUnitDefinition`、`AbilityCost`、`MapTileDefinitionRow` | battle 初始化后 authoritative、read-only/read-mostly | terrain/cost/capability 与 mutable battle state 分开 |
| working state | `PlayerState`、`GameUnitState` | battle 期间 authoritative mutable state | position、hp、action points、score 等下一回合继续依赖的事实 |
| working state | `TileOccupancyRow` | 从 `GameUnitState.position` 可重建的 derived cache | 与 immutable tile definition 分表；失败时丢弃并重建 |
| working state | `MoveCandidateRow`、`PendingDamageRow` | phase-local rebuildable workspace/resolution buffer | 不兼任 action history、damage history 或 result table |
| result facts | final player/unit state、battle outcome projection | 由最终 authoritative state 直接导出 | 默认不创建内容相同的 `BattleResult` shadow table |

当前 `MapTileRow` 把 immutable terrain/move cost 与 mutable `occupantUnit` cache 放在同一 row，生命周期和 mutation policy 不一致。本蓝图采用拆分后的推荐方向：`MapTileDefinitionRow` 只保存输入事实，`TileOccupancyRow` 只保存 derived working cache。类似地，真实 schema 应优先区分 unit/player definition 与 mutable state；如果因 turn-order hot loop 需要把少量只读 leaf co-locate 到 `GameUnitState`，必须标为 preprojected input leaf，而不是第二份可修改 definition。

Final hp/position/score 在 battle 结束前仍是 working state，结束后直接成为 result projection 的来源。这里不额外维护 result table；只有 battle outcome 拥有独立 identity、生命周期或查询需求时，才新增独立 `BattleOutcome`。

### 2.2 Access Pattern Card

以下 card 是 game scenario/runtime-plan input，不进入 Schema/hash。Map size、unit count、candidate count、damage density 和 tick/action frequency 必须由 benchmark fixture 提供。

| Table / phase | Rows/cardinality | Hot columns | Access / mutation mix | Locality / allocation boundary |
|---|---|---|---|---|
| `GameUnitState` | live units | turn/state/position/hp/action points | ordered next-unit、point fetch/mutate、grouped player/state access | 记录 selector selectivity、mutation/read ratio、order dirty/rebuild 和 object-free cursor path |
| `MapTileDefinitionRow` / `TileOccupancyRow` | map cells | terrain/move cost vs occupant | visibility/pathing scan、coordinate access、move 后 cache update/rebuild | paired scan working set、coordinate variants、definition/state split 与 occupancy rebuild 分开计量 |
| `MoveCandidateRow` dense workspace | selected-unit reachable/action candidates | position、cost、remaining AP、tie-break | per action `replaceAll` + dynamic/maintained order + first | builder/column rewrite、sort scratch、order rebuild、capacity reuse、allocation/op 分开 |
| `PendingDamageRow` | current resolution batch | resolution order、source/target、damage | ordered traversal + target point mutate + clear/compact | 记录 target locality、aggregation、sidecar dirty 和 clear reuse；不混入 history/replay |
| `AbilityCost` | unit-class/ability combinations | key leaves、cost/range/damage | action generation point lookup | 记录 lookup count/load/collision 与 preprojection reuse；comparator 内禁止 lookup |

Benchmark 必须额外记录 map working-set bytes、coordinate lookup distribution、selected-unit/all-units candidate scope、optional occupancy density、damage target reuse、JIT warmup/forks、stats mode 和 snapshot/export frequency。

## 3. 主 hot loop 拆解

典型 turn-based game loop 可以拆成：

```text
initialize players / units / map tiles / ability_costs
  -> repeat until battle end:
       select next unit by turn order / ready state
       generate move candidates for selected unit
       choose move or ability action
       update GameUnitState.position
       rebuild or update TileOccupancyRow cache
       generate pending damage rows
       resolve pending damage rows
       mutate GameUnitState hp/state and PlayerState score
       materialize schema object/List and map external DTO snapshot at boundary
```

如果当前回合只处理一个 selected unit，则每次：

```java
moveCandidateRows.replaceAll(buildMovesFor(selectedUnit));
MoveCandidateRow chosen = moveCandidateRows.byTotalCost().firstOrThrow();
```

是合理的 dense workspace 用法。它复用 capacity、连续写入候选、按当前 workspace order 选择。这里的 `by_total_cost` 是 selected-unit workspace 的场景假设，不是全局行动策略；正式化前应通过 benchmark 验证 `replaceAll + order rebuild + firstOrThrow` 的成本。

如果改成每 tick 为所有 unit 构建全局 action set：

```java
moveCandidateRows.replaceAll(buildMovesForAllUnits());
```

则会隐藏 pathing、visibility、ability lookup、tile occupancy 读取、builder 构造和 order sidecar rebuild 成本；这时应重新评估是否需要 keyed action frontier。

## 4. 生命周期判断

### 4.1 `MoveCandidateRow`

当前默认应保持 dense workspace：

- 候选只服务于 selected unit / current action；
- 生成和消费通常在同一 action phase 内完成；
- row 没有 stable logical key；
- `unitId` 是候选归属字段，不足以构成 action identity；
- `by_total_cost` 是当前 workspace 的 selection order，不是长期策略事实。

只有当满足以下条件时，才考虑升级为 keyed action frontier：

- AI 或 simulation 需要跨 tick 保留全局 action candidates；
- 需要按 unit、ability、target position 局部更新或删除；
- tile occupancy 变化只影响局部候选；
- full rebuild for all units 成为热点；
- action candidate 有明确 stable identity 和失效规则。

候选 key 可能是：

```text
(UnitId, AbilityId, TargetPosition, ActionVersion)
```

`ActionVersion` 或 battle tick 是 stale candidate 防线；否则 tile occupancy 和 unit actionPoints 变化后，旧 action row 会错误保留。

### 4.2 `PendingDamageRow`

`PendingDamageRow` 是结算 buffer：

- action 或 ability 产生一批 pending damage；
- 按 `resolutionOrder` 消费；
- 写回 `GameUnit.hp`、`GameUnit.state` 和可能的 `Player.score`；
- 消费后 clear / replace；
- 不应被当作 damage history 或 replay log。

如果未来需要取消、去重、跨 tick 保留、网络重放或幂等结算，就应引入 keyed event / damage command table，而不是继续使用 dense workspace。

### 4.3 `MapTileDefinitionRow` 与 `TileOccupancyRow`

`MapTileDefinitionRow` 是 dense long-lived input layout，`TileOccupancyRow` 是同一 grid layout 上的 derived working cache：

- `terrain`、`moveCost`、`blocksSight` 偏静态；
- `occupantUnit` 只存在于 occupancy cache；
- `by_grid_position` 支持稳定的 scan/order；
- 如果 pathing 主要按 coordinate random fetch tile，dense + order 不等价于 O(1) lookup。

这里存在典型双事实源风险：

```text
GameUnitState.position -> TileOccupancyRow.occupantUnit
```

蓝图建议明确 source-of-truth：

- `GameUnitState.position` 是单位位置事实源；
- `TileOccupancyRow.occupantUnit` 是 tile occupancy cache，用于 pathing/visibility；
- 每次移动先提交 `GameUnitState.position`，再更新或重建 occupancy cache；
- SOMA V1 不提供跨 table transaction，失败处理由 game loop 拥有。

### 4.4 为什么当前 workspace 不建成 child table

`MoveCandidateRow` 的生命周期属于 current action phase，不属于某个 `GameUnit` row 的长期生命周期。把它挂成 unit child 会让 entity materialization 意外递归展开 action workspace，并要求在 selected unit 切换时处理 stale child 内容。`PendingDamageRow` 同样属于全局 resolution phase，而不是某个 source/target unit 的独占 subtree。

因此当前蓝图保持二者为 root-level dense workspace/buffer。只有 future model 明确定义一个拥有稳定生命周期的 `TurnWorkspace` / `ActionContext` parent，并且 child 不共享、不 reparent、删除 parent 时应级联释放时，才重新评估 child ownership；不得只为了“嵌套看起来自然”而引入 child table。

## 5. 潜在瓶颈识别

主要风险：

- **全局 move candidate rebuild**：对所有 unit 做 pathing 会掩盖大量重复计算；
- **coordinate lookup 不匹配**：如果 pathing inner loop 需要频繁 `(x, y) -> tile`，dense order source 不是 O(1) coordinate fetch；
- **candidate comparator 做 lookup**：不能在 `sorted` comparator 中读取 tile definition、occupancy cache、`AbilityCost` 或 `GameUnitState`；
- **occupancy cache drift**：`GameUnitState.position` 更新后若 `TileOccupancyRow` rebuild/update 失败会造成不一致；
- **damage buffer 生命周期扩大**：如果 pending damage 跨 tick 保留，dense workspace 缺少 identity、幂等和清理语义；
- **order sidecar 误用**：`by_total_cost` 是 selected-unit workspace 的待验证场景假设；`by_resolution_order` 更接近稳定结算顺序，但二者都不是物理排序或 heap。

## 6. 推荐的 schema annotation 草案

当前默认方案保留 dense workspace。

```java
@SomaSchema(
    name = "game_runtime_state",
    generatedPackage = "com.example.game.state.generated",
    version = "1"
)
package com.example.game.state;

@SomaTable(name = "map_tile_definition_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_grid_position", by = {
    @SomaSort("position.y"),
    @SomaSort("position.x")
})
public final class MapTileDefinitionRow {
    @SomaField
    public GridPosition position;

    @SomaField
    public TerrainType terrain;

    @SomaField
    public int moveCost;

    @SomaField
    public boolean blocksSight;
}

@SomaTable(name = "tile_occupancy_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_grid_position", by = {
    @SomaSort("position.y"),
    @SomaSort("position.x")
})
public final class TileOccupancyRow {
    @SomaField
    public GridPosition position;

    @SomaField
    @SomaOptional
    public UnitId occupantUnit;
}

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
@SomaOrder(name = "by_total_cost", by = {
    @SomaSort("totalCost"),
    @SomaSort("position.y"),
    @SomaSort("position.x")
})
public final class MoveCandidateRow {
    @SomaField
    public UnitId unitId;

    @SomaField
    public GridPosition position;

    @SomaField
    public int totalCost;

    @SomaField
    public int remainingActionPoints;
}

@SomaTable(name = "pending_damage_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_resolution_order", by = {
    @SomaSort("resolutionOrder"),
    @SomaSort("targetUnit.value")
})
public final class PendingDamageRow {
    @SomaField
    public long resolutionOrder;

    @SomaField
    public UnitId sourceUnit;

    @SomaField
    public UnitId targetUnit;

    @SomaField
    public int damage;
}
```

`PendingDamageRow.by_resolution_order` 可以作为稳定结算顺序的候选 schema order。`MoveCandidateRow.by_total_cost` 只适用于 selected-unit workspace；它是否值得进入正式 schema，需要先比较 maintained order、dynamic sort 和小型 top-k buffer 的 benchmark。

当前暂不采纳全局 `ActionCandidate` frontier。下面只是 future option 的约束清单，不是正式 schema 草案：

```java
@SomaValue
public class ActionCandidateKey {
    @SomaField
    UnitId unitId;

    @SomaField
    AbilityId abilityId;

    @SomaField
    GridPosition targetPosition;

    @SomaField
    long actionVersion;
}

@SomaTable(name = "action_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_unit", fields = {
    "candidateKey.unitId.value"
})
@SomaIndex(name = "by_ability", fields = {
    "candidateKey.abilityId"
})
@SomaIndex(name = "by_target_position", fields = {
    "candidateKey.targetPosition.y",
    "candidateKey.targetPosition.x"
})
public final class ActionCandidate {
    @SomaKey
    public ActionCandidateKey candidateKey;

    @SomaField
    public int totalCost;

    @SomaField
    public int expectedDamage;

    @SomaField
    public int remainingActionPoints;

    @SomaField
    public boolean scoreReady;
}
```

该 keyed frontier 不是当前示例默认设计。只有在跨 tick 保留、局部更新、按 unit / ability / target position 删除都成立，并且定义了 stable identity、失效规则、版本字段、必要 index 和 cache 清理策略后，才值得进入正式方案。`scoreReady` 也只是 future scoring 生命周期字段，不复用 FJSP 的 `indicatorReady` 术语。

## 7. 推荐的 Java 8 SOMA API 使用流程

### 7.1 选择当前单位

```java
GameUnitState selected = unitStates.byTurnOrder()
    .filter(u -> u.state() == UnitState.READY)
    .firstOrThrow();
```

### 7.2 为 selected unit 生成 move workspace

```java
void rebuildMoveCandidatesFor(GameUnitState selected) {
    MoveCandidateBatch batch = moveCandidateRows.newBatch();

    computeReachableTiles(selected, tile -> {
        batch.add()
            .setUnitId(selected.unitId)
            .setPosition(tile.position())
            .setTotalCost(tile.totalCost())
            .setRemainingActionPoints(selected.actionPoints - tile.totalCost());
    });

    moveCandidateRows.replaceAll(batch);
}
```

`computeReachableTiles(...)` 属于 game/pathing layer。它可以读取 SOMA table，但路径搜索规则不进入 SOMA schema。

### 7.3 选择移动候选

```java
MoveCandidateRow chooseMoveFor(GameUnitState selected) {
    return moveCandidateRows.byTotalCost()
        .filter(m -> m.unitId().equals(selected.unitId))
        .filter(m -> m.remainingActionPoints() >= 0)
        .firstOrThrow();
}
```

`moveCandidateRows.replaceAll(batch)` 是单 selected-unit workspace 边界。`unitId` 主要是诊断 / export 字段；选择阶段保留 `unitId == selected.unitId` 断言，避免读者把该 table 误解成多 unit 混合候选 frontier。

如果 comparator 需要 tie-breaker，应只读取 `MoveCandidateRow` 字段，不在 comparator 内查 tile definition、occupancy cache 或 `GameUnitState`。

### 7.4 Commit move

```java
enum MoveCommitResult {
    COMMITTED,
    OCCUPANCY_CACHE_REBUILT
}

MoveCommitResult commitMove(UnitId unitId, GridPosition from, GridPosition to) {
    unitStates.mutate(unitId)
        .setPosition(to)
        .setState(UnitState.MOVED)
        .commit();

    boolean occupancyUpdated = tryUpdateOccupancyCache(from, to, unitId);
    if (!occupancyUpdated) {
        rebuildOccupancyCacheFromUnits();
        moveCandidateRows.clear();
        return MoveCommitResult.OCCUPANCY_CACHE_REBUILT;
    }

    moveCandidateRows.clear();
    return MoveCommitResult.COMMITTED;
}
```

`GameUnitState.position` 是 source-of-truth；`TileOccupancyRow.occupantUnit` 是 cache。若 cache 更新失败，game loop 必须停止当前 frame、回滚到外部 snapshot，或从 `GameUnitState.position` 重建 occupancy cache。SOMA V1 不提供跨 table transaction。

`tryUpdateOccupancyCache(...)` 不能假设 `TileOccupancyRow.byGridPosition()` 是 primary key lookup。可选边界是：

- 保留 dense grid，并由外部 grid adapter 维护 `(x, y) -> rowIndex` invariant；
- 在初始化时校验 dense grid 没有 duplicate / missing coordinate；
- 改为 keyed / unique coordinate `MapTile` 方案；
- 将 coordinate lookup 作为 benchmark lane，比较 dense scan、external row-index adapter、keyed tile table。

### 7.5 Damage 结算

```java
void resolveDamage() {
    pendingDamageRows.byResolutionOrder()
        .forEach(d -> {
            GameUnitState target = unitStates.fetch(d.targetUnit());
            int nextHp = Math.max(0, target.hp - d.damage());

            unitStates.mutate(d.targetUnit())
                .setHp(nextHp)
                .setState(nextHp == 0 ? UnitState.DEAD : target.state)
                .commit();
        });

    pendingDamageRows.clear();
}
```

这不是纯 dense buffer scan。每条 pending damage 至少包含 `PendingDamageRow` ordered traversal、`unitStates.fetch(d.targetUnit())` keyed lookup / schema-object materialization、`unitStates.mutate(...)` keyed mutation，以及 `pendingDamageRows.clear()` 的 sidecar dirty 成本。benchmark 应拆开 pending buffer scan、unit keyed lookup/mutation 和 sidecar maintenance。

`firstOrThrow()`、`fetch(...)` 返回 detached schema object；`@SomaTable` row 不生成 structural equality/hash。`UnitId` / `GridPosition` 等 `@SomaValue` 使用 compiler-defined canonical value equality。Boundary snapshot 应拆分为 Materialized Object、external DTO adapter 和 wire/replay mapping；final player/unit state 直接从 authoritative state materialize，不复制 `BattleResult` shadow rows。若 future parent-owned child 进入 object graph，必须使用 runtime plan 默认或显式 `MaterializationBudget`。

这是跨 table mutation，SOMA V1 不提供 transaction。game loop 必须决定失败时是停止 frame、回滚到外部 snapshot，还是重建 derived workspace。

## 8. Cache 友好性分析

当前 dense workspace 方案的 cache 友好性来自：

- `MoveCandidateRow` 按 selected unit 批量重建，候选连续写入；
- `by_total_cost` order 只作用于当前 workspace，是否固化为正式 schema 需要 benchmark；
- `PendingDamageRow` 作为 resolution buffer 连续扫描；
- `MapTileDefinitionRow` 和 shape-compatible `TileOccupancyRow` dense layout 适合整图扫描、pathing/visibility 和 boundary projection；

主要削弱因素：

- pathing inner loop 如果频繁 coordinate random fetch，dense ordered rows 可能不够；
- `TileOccupancyRow.occupantUnit` 是从 `GameUnitState.position` 重建的 cache，需要明确同步/重建；
- 如果全局 AI 每 tick 生成所有 unit moves，dense full rebuild 可能膨胀；
- `AbilityCost.fetch(unitAbilityKey)` 若在候选 comparator 或 inner loop 中重复发生，会变成随机 lookup 热点。

可能的优化路径：

- selected-unit pathing 保持 dense `MoveCandidateRow.replaceAll(batch)`；
- 全局 AI planning 才重新评估 keyed `ActionCandidate` frontier，当前暂不采纳；
- 对 coordinate lookup 建立外部 grid adapter、正式 keyed `MapTile` 或坐标索引方案，但必须避免暴露 runtime row pointer；
- pending damage 保持 dense buffer，跨 tick event log 另建 keyed command/event table。

## 9. SOMA V1 可能暴露的问题

- Dense grid layout 与 coordinate keyed lookup 的边界需要更清晰；
- 当前 `@SomaOrder(by_grid_position)` 只能表达 ordered traversal，不能承诺 O(1) tile lookup；
- workspace `replaceAll` 很适合 selected-unit move candidates，但不适合未加判断地扩展到全局 action set；
- occupancy cache 更新需要外部 row-index invariant、keyed tile 方案或明确 benchmark，否则用户可能退化成每次全表 scan；
- `by_total_cost` 是 selected-unit workspace 的待验证 order，不应被误解成全局行动策略；
- pending damage 如果扩展成跨 tick command，需要 identity、dedup、replay 和 failure handling；
- selected-unit workspace 和 damage buffer 当前没有合适的 exclusive parent lifecycle，不应机械改成 child table；
- input definitions、mutable battle state、phase workspace 和 final projection 必须分层；不为相同 final hp/position/score 创建 shadow result table；
- 示例必须持续强调 SOMA 不是 ECS / game engine，不拥有 system scheduling 或 game rules。

## 10. 自审结论

### 10.1 通过项

- 没有把 `MoveCandidateRow` 错误升级为默认全局 frontier；
- 保留了 dense workspace 对 selected-unit action 的合理性；
- 明确 `PendingDamageRow` 是 resolution buffer，不是 history/log；
- 明确 `MapTileDefinitionRow` 是 dense input layout、`TileOccupancyRow` 是 derived cache，coordinate hot lookup 仍可能需要额外建模；
- 明确 `GameUnitState.position` 是 source-of-truth，`TileOccupancyRow.occupantUnit` 是 cache；
- input definition、working state、phase workspace 与 final projection 已分层，final state 不复制 shadow result table；
- future `ActionCandidate` frontier 暂不采纳为正式设计。

### 10.2 风险和坏味道

- `TileOccupancyRow.occupantUnit` 作为 cache 容易和 `GameUnitState.position` drift，失败时必须重建或回滚；
- 如果 coordinate lookup 是主路径，当前 dense order 设计会诱导低效 scan；
- 如果 AI planning 需要全局 action candidates，当前 workspace 设计不够；
- `by_total_cost` 可能被误解成永久策略排序，实际只适用于当前 workspace；
- damage resolution 包含 per-row keyed lookup / mutation，不是纯 dense scan；
- damage resolution 跨 table mutation 失败时没有 runtime transaction 保护。

### 10.3 待验证事项

- selected-unit `MoveCandidateRow.replaceAll(batch)` 的规模和 order rebuild 成本；
- coordinate lookup 的 generated API 形态：dense scan、index source、keyed tile table 或外部 grid adapter；
- `PendingDamageRow.byResolutionOrder().forEach(...) + clear()` 的生命周期、sidecar dirty、unit keyed lookup / mutation 成本；
- occupancy consistency 的 game-loop invariant test；
- schema-object materialization、external DTO mapping 和未来 child deep materialization 的独立 allocation/budget lane；
- 全局 `ActionCandidate` frontier 是否值得进入后续正式示例或 benchmark；
- split tile definition/occupancy cache 与旧 mixed `MapTileRow` 的 scan、lookup、rebuild 和 memory cost。

### 10.4 当前判定

该蓝图已按审查结论修正后保留为临时蓝图。正式示例应进一步分离 player/unit/tile input definition、mutable battle state 和 result projection；`MapTileDefinitionRow` 与 `TileOccupancyRow` 不再混合 immutable facts 和 mutable cache。selected-unit `MoveCandidateRow` workspace、`PendingDamageRow` resolution buffer、coordinate lookup、occupancy rebuild 和无跨表 transaction 口径继续保留。selected-unit `replaceAll + byTotalCost`、coordinate lookup、damage resolution、tile split layout 可作为 benchmark 候选；全局 `ActionCandidate` frontier 当前只作为 future option，暂不采纳为正式设计。
