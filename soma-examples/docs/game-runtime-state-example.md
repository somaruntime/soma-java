# Game runtime state 示例

类型：Report / 开发者 current-executable 场景

状态：当前

Owner：`soma-examples` output

受众：使用或维护当前 Game runtime-state example 的开发者

适用版本：最后 implementation-affecting baseline `a137b10`

输入事实源：当前 example source、[Game Blueprint](../../docs/blueprints/game-runtime-state-blueprint.md)、Design 与 phase-6 evidence

事实范围：当前 Game schema、move/cache/damage journey、失败与性能边界

非事实范围：完整 game engine、ECS/pathfinding、SOMA public contract 和性能优势

最后审查日期：2026-07-21

> 本文只记录当前 executable example。目标仍由 Blueprint 拥有，长期语义仍由 Design 拥有。

## 1. 当前场景

[`GameScenario.java`](../src/main/java/com/hgtech/soma/examples/game/GameScenario.java) 执行一个 turn-based tactics slice：

```text
validate/import definitions + mutable state
  -> rebuild keyed occupancy cache from live units
  -> total-order select READY unit
  -> publish selected-unit move workspace
  -> prepare/revalidate/commit move
  -> update or rebuild occupancy cache
  -> stage total-ordered damage commands
  -> mutate unit/player state and clear damage buffer
  -> export authoritative state
```

SOMA 只承载 hot runtime state 与 typed access；turn scheduling、pathfinding、action revision、cache recovery 和 damage transaction policy属于 application `Battle`。

## 2. 当前数据角色

| Role | 当前 Table | 权威性与访问方式 |
|---|---|---|
| input facts | `PlayerDefinition`、`GameUnitDefinition`、`AbilityCost`、`MapTileDefinitionRow` | keyed、import 后只读；tile 以 `GridPosition` point lookup |
| mutable authoritative state | `PlayerState`、`GameUnitState` | keyed mutation；unit position 是 occupancy 的事实源 |
| derived cache | `TileOccupancyRow` | keyed coordinate cache；从全部 live unit positions 可重建 |
| selected-action workspace | `MoveCandidateRow` | 单 selected unit 的 dense `replaceAll + sorted` workspace，不重复 unit identity |
| phase command buffer | `PendingDamageRow` | dense current-resolution buffer；成功后 clear，不是 history/replay log |

`GameUnitState` 中的 player/initiative 是 immutable definition leaf 的 hot-path preprojection；import 校验两边一致，之后不作为第二份可修改 definition。

## 3. 关键 schema 投影

```java
@SomaTable(name = "game_unit_definitions", defaultCapacity = 1024)
public final class GameUnitDefinition {
    @SomaKey public UnitId unitId;
    @SomaField public PlayerId playerId;
    @SomaField public UnitClassId unitClassId;
    @SomaField public int initiative;
}

@SomaTable(name = "game_unit_states", defaultCapacity = 1024)
@SomaIndex(name = "by_player", fields = {"playerId.value"})
@SomaIndex(name = "by_state", fields = {"state"})
public final class GameUnitState {
    @SomaKey public UnitId unitId;
    @SomaField public PlayerId playerId;
    @SomaField public int initiative;
    @SomaField public GridPosition position;
    @SomaField public int hp;
    @SomaField public int actionPoints;
    @SomaField public UnitState state;
    @SomaField @SomaOptional public UnitId targetUnit;
}

@SomaTable(name = "map_tile_definition_rows", defaultCapacity = 4096)
public final class MapTileDefinitionRow {
    @SomaKey public GridPosition position;
    @SomaField public TerrainType terrain;
    @SomaField public int moveCost;
    @SomaField public boolean blocksSight;
}

@SomaTable(name = "tile_occupancy_rows", defaultCapacity = 4096)
public final class TileOccupancyRow {
    @SomaKey public GridPosition position;
    @SomaField @SomaOptional public UnitId occupantUnit;
}

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
public final class MoveCandidateRow {
    @SomaField public GridPosition position;
    @SomaField public int totalCost;
    @SomaField public int remainingActionPoints;
}

@SomaTable(name = "pending_damage_rows", defaultCapacity = 1024)
public final class PendingDamageRow {
    @SomaField public long resolutionOrder;
    @SomaField public long sequenceNo;
    @SomaField public UnitId sourceUnit;
    @SomaField public UnitId targetUnit;
    @SomaField public int damage;
}
```

完整声明以 [`com.hgtech.soma.examples.game`](../src/main/java/com/hgtech/soma/examples/game) 为准。

## 4. Move action 协议

READY unit 按 `(initiative, unitId)` 升序显式排序。Move workspace 的 owner 是 immutable application `MoveActionContext`，其中保存 unit identity、origin、initial AP、pathing revision 与 generation；candidate row 不复制这些相同 facts。

候选发布使用 reusable `MoveCandidateRowBatch`。选择 comparator 是：

```text
totalCost -> position.y -> position.x
```

Comparator 只读取 row，不查 tile、occupancy、ability 或 unit table。`PreparedMove` 绑定 context 与选中值，不保存 SOMA Index。重建 workspace、切换 selected unit 或推进 pathing revision 后，旧 prepared move 返回 `STALE`，不会写入权威状态。

Commit 会重新验证 unit state、origin/AP、target terrain 和 source/target occupancy；随后先提交 `GameUnitState.position/actionPoints/state`，再推进 revision，最后增量更新 occupancy。两个 cache mutation 不是 transaction：预期的 SOMA cache failure 会先 invalidate cache，再从 live units 完整重建；重建也失败则当前 battle/frame 停止。

## 5. Occupancy invariant

Cache rebuild 使用 owner-owned `TileOccupancyRowBatch`，并验证：

- 每个 coordinate 至多一个 live unit；
- 每个 live unit 都映射到一个存在且可通行的 tile；
- cache 为每个 tile 生成一行，absence 用 optional `occupantUnit` 表达；
- rebuild 完整成功后才通过 `replaceAll` 发布并重新标记为 valid。

因此 `GameUnitState.position` 与 cache 不构成两个平级事实源。场景在 move 后又执行一次完整 rebuild，直接证明 cache 的 reconstructibility。

## 6. Damage 协议

Application 为每个 resolution batch 单调分配 `sequenceNo`，并按 `(resolutionOrder, sequenceNo)` total order 读取。`DamageCommandBuffer` 在同步只读批次内把完整 command facts 复制到 reusable primitive arrays，验证 non-negative、source/target existence、sequence uniqueness 和 aggregate overflow，关闭 Table read boundary 后才开始跨表 mutation。

从第一次 unit mutation直到 player score 更新和 pending buffer clear，任何异常都会使 `DamageResolution` fail-stop；未清理 command 不得重放。需要 retry/replay/幂等时，应改为带 stable command identity 和 checkpoint 的另一种 application model。

## 7. 性能与证据边界

- move Batch、occupancy rebuild Batch、damage Batch 与 primitive command arrays 都由 owner 复用；
- ColumnView 在 phase 粒度打开并关闭，不在每个 tile/command 内重复创建；
- current APC ledger 分别为 unit state 16、occupancy 8、move 16、damage 28 bytes，aggregate width 68；
- phase-6 Gate 固定 schema/hash、generated/public surface、stale action、keyed cache rebuild、damage total order 和 fail-stop behavior。

这些结果只覆盖当前 Java 8 teaching fixture，不构成固定矩形 grid、ECS 或 large-scale pathfinding 的性能承诺。

## 8. 非目标

本示例不实现 ECS archetype、rendering、network replication、全局 AI action frontier、复杂 pathfinding、rollback netcode、持久化 replay log 或跨 Table transaction。
