# Game runtime state 示例

状态：正式设计文档
日期：2026-07-07
Owner：`soma-examples`

## 1. 文档定位

本文是 `Game runtime state 示例` 的独立场景文档。它从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 拆分而来，遵守该总览文档中的通用建模规则。

## 2. 场景边界

Game 示例表达 grid tactics / turn-based game loop 的 runtime state：

```text
players / units / map tiles / ability cost lookup
  -> visibility or move candidate dense rows
  -> pending damage dense rows
  -> unit mutation and DTO export
```

SOMA 不是 ECS framework，不拥有 system scheduling、rendering、input、network replication 或 game rules。它只承载 game loop 中需要高频扫描、排序、按 key mutation 或 DTO materialization 的 runtime state。

## 3. Schema source 示例

```java
@SomaSchema(
    name = "game_runtime_state",
    generatedPackage = "com.example.game.state.generated",
    version = "1"
)
package com.example.game.state;

public enum TerrainType {
    PLAIN,
    FOREST,
    WATER,
    WALL
}

public enum UnitState {
    READY,
    MOVED,
    STUNNED,
    DEAD
}

public enum AbilityId {
    MOVE,
    ATTACK,
    HEAL
}

@SomaValue
public final class PlayerId {
    @SomaField
    public long value;
}

@SomaValue
public final class UnitId {
    @SomaField
    public long value;
}

@SomaValue
public final class UnitClassId {
    @SomaField
    public long value;
}

@SomaValue
public final class GridPosition {
    @SomaField
    public int x;

    @SomaField
    public int y;
}

@SomaValue
public final class UnitAbilityKey {
    @SomaField
    public UnitClassId unitClassId;

    @SomaField
    public AbilityId abilityId;
}

@SomaTable(name = "players", defaultCapacity = 16)
public final class Player {
    @SomaKey
    public PlayerId playerId;

    @SomaField
    public int teamNo;

    @SomaField
    @SomaDefault("0")
    public long score;
}

@SomaTable(name = "units", defaultCapacity = 1024)
@SomaIndex(name = "by_player", fields = {"playerId.value"})
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_turn_order", by = {
    @SomaSort("initiative"),
    @SomaSort("unitId.value")
})
public final class GameUnit {
    @SomaKey
    public UnitId unitId;

    @SomaField
    public PlayerId playerId;

    @SomaField
    public UnitClassId unitClassId;

    @SomaField
    public GridPosition position;

    @SomaField
    public int hp;

    @SomaField
    public int actionPoints;

    @SomaField
    public int initiative;

    @SomaField
    @SomaDefault("READY")
    public UnitState state;

    @SomaOptional
    public UnitId targetUnit;
}

@SomaTable(name = "ability_costs", defaultCapacity = 128)
public final class AbilityCost {
    @SomaKey
    public UnitAbilityKey unitAbilityKey;

    @SomaField
    public int actionPointCost;

    @SomaField
    public int range;

    @SomaField
    public int baseDamage;
}

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

## 4. 使用方式

- `Player`、`GameUnit` 是 keyed entity state；
- `AbilityCost` 是 keyed lookup table；
- `MapTileRow` 是 dense grid layout，适合 render / visibility / packed scan；`by_grid_position` 是 ordered traversal，不是 O(1) coordinate lookup；
- `MoveCandidateRow` 默认是 selected-unit / current-action dense workspace；`unitId` 是诊断、导出或断言字段，不构成 action identity，也不表示 all-units global frontier；
- `PendingDamageRow` 是当前结算阶段的 dense resolution buffer；
- game loop OOP 层负责回合推进、技能规则、路径搜索和渲染同步，SOMA 不替代 game engine。

`MoveCandidateRow.by_total_cost` 只服务 selected-unit workspace 的 selection order。它不是全局行动策略排序承诺，也不表示 maintained order 与 dynamic `sorted(comparator)` 性能等价；是否保留该 order、改用 dynamic sort，或为全局 AI planning 引入 keyed `ActionCandidate` frontier，需要通过 benchmark 和单独设计判断。当前正式示例不引入全局 `ActionCandidate` frontier；只有跨 tick 保留、局部失效、稳定 identity、版本语义和 cache 清理策略都成立后，它才可能成为后续研究方向。

`GameUnit.position` 是单位位置事实源；`MapTileRow.occupantUnit` 是 occupancy cache。移动提交时，game loop 应先提交 `GameUnit.position`，再更新或重建 `MapTileRow.occupantUnit`。如果 cache 更新失败，game loop 必须停止当前 frame、回滚外部 snapshot，或从 `GameUnit.position` 重建 occupancy cache；SOMA V1 不提供跨 table transaction 或自动补偿。

如果 pathing hot loop 频繁执行 `(x, y) -> tile`，不能把 `by_grid_position` 当作 coordinate fetch。可选边界是：保留 dense grid 并由外部 grid adapter 维护 `(x, y) -> rowIndex` invariant，同时初始化时校验 duplicate / missing coordinate；或改为 keyed / unique coordinate `MapTile` 方案。coordinate lookup 应进入 benchmark lane，而不是从 `@SomaOrder` 推导性能结论。

`PendingDamageRow` 按 `by_resolution_order` 消费，写回 keyed `GameUnit` / `Player` 后 clear 或 replace。它不是 history / replay log；如果需要跨 tick 保留、取消、去重、网络重放或幂等结算，应另建 keyed event / command table。damage resolution 包含 pending row traversal、target `fetch`、target `mutate` 和 buffer clear 的成本，不能被描述为纯 dense scan。
