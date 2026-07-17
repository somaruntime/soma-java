# Game runtime state 示例

状态：正式设计文档
Owner：`soma-examples`
事实范围：Game runtime data role、Access Pattern Card、schema 和使用边界
非事实范围：game rules/ECS/pathfinding、public contract 和性能 claim
最后审查日期：2026-07-17

## 1. 文档定位

本文是 `Game runtime state 示例` 的独立场景文档。它从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 拆分而来，遵守该总览文档中的通用建模规则。

## 2. 场景边界

Game 示例表达 grid tactics / turn-based game loop 的 runtime state：

```text
players / units / map tiles / ability cost lookup
  -> visibility or move candidate dense rows
  -> pending damage dense rows
  -> unit mutation and detached schema object / boundary DTO export
```

SOMA 不是 ECS framework，不拥有 system scheduling、rendering、input、network replication 或 game rules。它只承载 game loop 中需要高频扫描、排序、按 key mutation 或 detached schema object 构造的 runtime state；外部 DTO 只属于 adapter 边界。

### 2.1 Access Pattern Card

| Core path | Cardinality/working set | Access/mutation mix | Allocation/evidence boundary |
|---|---|---|---|
| unit/player state | live units/players | explicit-sort next-unit、point/exact-group access、field mutate | selector selectivity、`IndexBuffer` sort、Cursor path 与 materialized fetch 分开 |
| map/occupancy | map cells、optional occupancy density | visibility/pathing scan、coordinate access、move 后 cache update/rebuild | paired working set、coordinate variants 和 occupancy consistency 分开 |
| move/damage workspaces | selected-unit candidates、current damage rows | `replaceAll`、dynamic sort、target point mutate、clear/swap-remove | builder、sort/compaction scratch、capacity reuse、allocation/op 和 stats mode 分开 |

Fixture/benchmark 必须补充 map working-set bytes、selected-unit/all-units scope、damage target reuse、KeySpace load/collision、JIT warmup/forks 和 snapshot/export frequency；这些值不进入 Schema/hash。

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
public class PlayerId {
    @SomaField
    long value;
}

@SomaValue
public class UnitId {
    @SomaField
    long value;
}

@SomaValue
public class UnitClassId {
    @SomaField
    long value;
}

@SomaValue
public class GridPosition {
    @SomaField
    int x;

    @SomaField
    int y;
}

@SomaValue
public class UnitAbilityKey {
    @SomaField
    UnitClassId unitClassId;

    @SomaField
    AbilityId abilityId;
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

    @SomaField
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
public final class MapTileRow {
    @SomaField
    public GridPosition position;

    @SomaField
    public TerrainType terrain;

    @SomaField
    public int moveCost;

    @SomaField
    public boolean blocksSight;

    @SomaField
    @SomaOptional
    public UnitId occupantUnit;
}

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
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
- `MapTileRow` 是 dense grid layout，适合 render / visibility / packed scan；需要 grid traversal 时显式排序或由外部 grid adapter 保持坐标布局；
- `MoveCandidateRow` 默认是 selected-unit / current-action dense workspace；`unitId` 是诊断、导出或断言字段，不构成 action identity，也不表示 all-units global frontier；
- `PendingDamageRow` 是当前结算阶段的 dense resolution buffer；
- game loop OOP 层负责回合推进、技能规则、路径搜索和渲染同步，SOMA 不替代 game engine。

`MoveCandidateRow` 的 total-cost 次序只服务 selected-unit workspace 的本次选择，通过 `sorted(comparator)` 显式建立。它不是全局行动策略排序承诺；是否为全局 AI planning 引入 keyed `ActionCandidate` frontier，需要通过 benchmark 和单独设计判断。当前正式示例不引入全局 `ActionCandidate` frontier；只有跨 tick 保留、局部失效、稳定 identity、版本语义和 cache 清理策略都成立后，它才可能成为后续研究方向。

`GameUnit.position` 是单位位置事实源；`MapTileRow.occupantUnit` 是 occupancy cache。移动提交时，game loop 应先提交 `GameUnit.position`，再更新或重建 `MapTileRow.occupantUnit`。如果 cache 更新失败，game loop 必须停止当前 frame、回滚外部 snapshot，或从 `GameUnit.position` 重建 occupancy cache；SOMA V1 不提供跨 table transaction 或自动补偿。

如果 pathing hot loop 频繁执行 `(x, y) -> tile`，应由外部 grid adapter 维护 `(x, y) -> current Index` invariant，并在 structural mutation 后重建；或者改为 keyed / unique coordinate `MapTile`。coordinate lookup 应进入 benchmark lane，不能从物理遍历顺序推导性能结论。

`PendingDamageRow` 在本次结算 terminal 中按 resolution order 显式排序，写回 keyed `GameUnit` / `Player` 后 clear 或 replace。它不是 history / replay log；如果需要跨 tick 保留、取消、去重、网络重放或幂等结算，应另建 keyed event / command table。damage resolution 包含 pending row traversal/sort、target `fetch`、target `mutate` 和 buffer clear 的成本，不能被描述为纯 dense scan。
