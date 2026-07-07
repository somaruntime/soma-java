# Game runtime frontier 临时蓝图

状态：临时蓝图
日期：2026-07-07
适用范围：`soma_java` Java-only V1 设计讨论

## 1. 目标与适用范围

本文基于 `soma-examples/docs/game-runtime-state-example.md`，审视 grid tactics / turn-based game loop 场景下 SOMA runtime state 的建模方式。

Game 场景和 FJSP 的相似点是：都有候选 action / move 的生成与选择。差异是：当前示例的 `MoveCandidateRow` 更适合作为 selected unit / current action 的 dense workspace，而不是全局长期 action frontier。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论 game loop runtime state，不讨论 ECS scheduling、rendering、input、network replication、AI search 或完整 game rules；
- SOMA 保存 hot runtime state，game loop 拥有行动规则、路径搜索、伤害结算、跨 table 一致性和 DTO export；
- 本文是临时蓝图，不是正式 schema contract。

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

## 3. 主 hot loop 拆解

典型 turn-based game loop 可以拆成：

```text
initialize players / units / map tiles / ability_costs
  -> repeat until battle end:
       select next unit by turn order / ready state
       generate move candidates for selected unit
       choose move or ability action
       update GameUnit.position
       rebuild or update MapTileRow.occupantUnit cache
       generate pending damage rows
       resolve pending damage rows
       mutate GameUnit hp/state and Player score
       export DTO snapshot at boundary
```

如果当前回合只处理一个 selected unit，则每次：

```java
moveCandidateRows.replaceAll(buildMovesFor(selectedUnit));
MoveCandidate chosen = moveCandidateRows.byTotalCost().firstOrThrow();
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

### 4.3 `MapTileRow`

`MapTileRow` 是 dense long-lived grid layout：

- `terrain`、`moveCost`、`blocksSight` 偏静态；
- `occupantUnit` 是运行时占用状态或 cache；
- `by_grid_position` 支持稳定的 scan/order；
- 如果 pathing 主要按 coordinate random fetch tile，dense + order 不等价于 O(1) lookup。

这里存在典型双事实源风险：

```text
GameUnit.position <-> MapTileRow.occupantUnit
```

蓝图建议明确 source-of-truth：

- `GameUnit.position` 是单位位置事实源；
- `MapTileRow.occupantUnit` 是 tile occupancy cache，用于 pathing/visibility；
- 每次移动先提交 `GameUnit.position`，再更新或重建 occupancy cache；
- SOMA V1 不提供跨 table transaction，失败处理由 game loop 拥有。

## 5. 潜在瓶颈识别

主要风险：

- **全局 move candidate rebuild**：对所有 unit 做 pathing 会掩盖大量重复计算；
- **coordinate lookup 不匹配**：如果 pathing inner loop 需要频繁 `(x, y) -> tile`，dense order source 不是 O(1) coordinate fetch；
- **candidate comparator 做 lookup**：不能在 `sorted` comparator 中读取 `MapTileRow`、`AbilityCost` 或 `GameUnit`；
- **occupancy 双写 drift**：`GameUnit.position` 和 `MapTileRow.occupantUnit` 更新失败会造成不一致；
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

@SomaTable(name = "map_tile_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_grid_position", by = {
    @SomaSort("position.y"),
    @SomaSort("position.x")
})
public final class MapTileRow {
    @SomaField
    public GridPosition position;

    @SomaField
    public TerrainType terrain;

    @SomaField
    public int moveCost;

    @SomaField
    public boolean blocksSight;

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
public final class ActionCandidateKey {
    @SomaField
    public UnitId unitId;

    @SomaField
    public AbilityId abilityId;

    @SomaField
    public GridPosition targetPosition;

    @SomaField
    public long actionVersion;
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
GameUnit selected = units.byTurnOrder()
    .filter(u -> u.state() == UnitState.READY)
    .firstOrThrow();
```

### 7.2 为 selected unit 生成 move workspace

```java
void rebuildMoveCandidatesFor(GameUnit selected) {
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
MoveCandidateRow chooseMoveFor(GameUnit selected) {
    return moveCandidateRows.byTotalCost()
        .filter(m -> m.unitId().equals(selected.unitId))
        .filter(m -> m.remainingActionPoints() >= 0)
        .firstOrThrow();
}
```

`moveCandidateRows.replaceAll(batch)` 是单 selected-unit workspace 边界。`unitId` 主要是诊断 / export 字段；选择阶段保留 `unitId == selected.unitId` 断言，避免读者把该 table 误解成多 unit 混合候选 frontier。

如果 comparator 需要 tie-breaker，应只读取 `MoveCandidateRow` 字段，不在 comparator 内查 `MapTileRow` 或 `GameUnit`。

### 7.4 Commit move

```java
enum MoveCommitResult {
    COMMITTED,
    OCCUPANCY_CACHE_REBUILT
}

MoveCommitResult commitMove(UnitId unitId, GridPosition from, GridPosition to) {
    units.mutate(unitId)
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

`GameUnit.position` 是 source-of-truth；`MapTileRow.occupantUnit` 是 cache。若 cache 更新失败，game loop 必须停止当前 frame、回滚到外部 snapshot，或从 `GameUnit.position` 重建 occupancy cache。SOMA V1 不提供跨 table transaction。

`tryUpdateOccupancyCache(...)` 不能假设 `MapTileRow.byGridPosition()` 是 primary key lookup。可选边界是：

- 保留 dense grid，并由外部 grid adapter 维护 `(x, y) -> rowIndex` invariant；
- 在初始化时校验 dense grid 没有 duplicate / missing coordinate；
- 改为 keyed / unique coordinate `MapTile` 方案；
- 将 coordinate lookup 作为 benchmark lane，比较 dense scan、external row-index adapter、keyed tile table。

### 7.5 Damage 结算

```java
void resolveDamage() {
    pendingDamageRows.byResolutionOrder()
        .forEach(d -> {
            GameUnit target = units.fetch(d.targetUnit());
            int nextHp = Math.max(0, target.hp - d.damage());

            units.mutate(d.targetUnit())
                .setHp(nextHp)
                .setState(nextHp == 0 ? UnitState.DEAD : target.state)
                .commit();
        });

    pendingDamageRows.clear();
}
```

这不是纯 dense buffer scan。每条 pending damage 至少包含 `PendingDamageRow` ordered traversal、`units.fetch(d.targetUnit())` keyed lookup / DTO materialization、`units.mutate(...)` keyed mutation，以及 `pendingDamageRows.clear()` 的 sidecar dirty 成本。benchmark 应拆开 pending buffer scan、unit keyed lookup/mutation 和 sidecar maintenance。

这是跨 table mutation，SOMA V1 不提供 transaction。game loop 必须决定失败时是停止 frame、回滚到外部 snapshot，还是重建 derived workspace。

## 8. Cache 友好性分析

当前 dense workspace 方案的 cache 友好性来自：

- `MoveCandidateRow` 按 selected unit 批量重建，候选连续写入；
- `by_total_cost` order 只作用于当前 workspace，是否固化为正式 schema 需要 benchmark；
- `PendingDamageRow` 作为 resolution buffer 连续扫描；
- `MapTileRow` dense layout 适合整图扫描、渲染导出和 visibility pass。

主要削弱因素：

- pathing inner loop 如果频繁 coordinate random fetch，dense ordered rows 可能不够；
- `MapTileRow.occupantUnit` 和 `GameUnit.position` 双写需要额外同步；
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
- 示例必须持续强调 SOMA 不是 ECS / game engine，不拥有 system scheduling 或 game rules。

## 10. 自审结论

### 10.1 通过项

- 没有把 `MoveCandidateRow` 错误升级为默认全局 frontier；
- 保留了 dense workspace 对 selected-unit action 的合理性；
- 明确 `PendingDamageRow` 是 resolution buffer，不是 history/log；
- 明确 `MapTileRow` 是 dense grid layout，但 coordinate hot lookup 可能需要额外建模；
- 明确 `GameUnit.position` 是 source-of-truth，`MapTileRow.occupantUnit` 是 cache；
- future `ActionCandidate` frontier 暂不采纳为正式设计。

### 10.2 风险和坏味道

- `MapTileRow.occupantUnit` 作为 cache 容易和 `GameUnit.position` drift，失败时必须重建或回滚；
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
- 全局 `ActionCandidate` frontier 是否值得进入后续正式示例或 benchmark。

### 10.4 当前判定

该蓝图已按审查结论修正后保留为临时蓝图。正式示例应同步 selected-unit `MoveCandidateRow` workspace、`PendingDamageRow` resolution buffer、coordinate lookup 边界、occupancy cache source-of-truth 和无跨表 transaction 口径。selected-unit `replaceAll + byTotalCost`、coordinate lookup、damage resolution 可作为 benchmark 候选；全局 `ActionCandidate` frontier 当前只作为 future option，暂不采纳为正式设计。
