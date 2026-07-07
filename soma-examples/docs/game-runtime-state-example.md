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
- `MapTileRow` 是 dense grid layout，适合 render/pathing scan；如果某个项目主要按 coordinate random fetch tile，可以改为 keyed table；
- `MoveCandidateRow`、`PendingDamageRow` 是 dense workspace；
- game loop OOP 层负责回合推进、技能规则、路径搜索和渲染同步，SOMA 不替代 game engine。
