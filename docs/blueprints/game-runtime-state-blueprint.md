# Game runtime state 蓝图

类型：Blueprint

状态：正式

Owner：Game 目标场景

事实范围：entity state、phase-local workspace、grid/cache 与结果投影的目标使用形态

非事实范围：完整 game engine、ECS 调度、网络协议、精确公共 API、当前实现状态和性能结论

设计约束入口：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Materialization 边界](../design/materialization-boundary.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

最后审查日期：2026-07-23

目标约束：所有 grid、turn、candidate、damage 业务顺序都通过显式 `.sorted(totalComparator)` 或 application-owned 专用结构产生。`@SomaIndex` 只提供 always-current exact access，物理遍历顺序不构成业务契约。

## 1. 目标与适用范围

本文展示 grid tactics / turn-based game loop 场景下 SOMA runtime state 的目标建模方式。

Game 场景和 FJSP 的相似点是都有候选 action / move 的生成与选择。差异是：`MoveCandidateRow` 默认服务 selected unit / current action 的 dense workspace，而不是全局长期 action frontier。

适用边界：

- 只讨论 Java 8 generated Table / Candidate Scan / ColumnView 使用方式；
- 只讨论 game loop runtime state，不讨论 ECS scheduling、rendering、input、network replication、AI search 或完整 game rules；
- SOMA 保存 hot runtime state，game loop 拥有行动规则、路径搜索、伤害结算、跨 table 一致性和 external DTO export；
- 本文定义目标使用形态，不是精确 schema/API contract；未标为算法伪代码的片段按目标 Java 8 使用代码审查，允许省略 import、外围 battle owner 和完整 pathfinding/rules，但必须明确 turn total order、coordinate identity、action preflight、derived cache 失效和跨表 failure protocol。

本蓝图中的 `@SomaTable` class 同时定义 element schema 与 detached single-item materialization shape，但不是 live runtime storage。Materializing API/terminal 返回 schema class 或 `List`/`Map`，Candidate Scan callback 参数仍是 callback-scoped Cursor。`@SomaValue` 由 compiler 提供 immutable value semantics。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化；snapshot/replay/network output 只能由外部 adapter 构造。

## 2. 目标数据角色与 Table 形态

| Table | 目标形态 | 生命周期责任 | 主要访问方式 |
|---|---|---|---|
| `PlayerDefinition` / `PlayerState` | keyed input / mutable state | battle input 与 authoritative score/state 分离 | `fetch(playerId)`、`mutate(playerId)` |
| `GameUnitDefinition` / `GameUnitState` | keyed input / mutable state | capability definition 与 hp/position/action state 分离 | `fetch(unitId)`、player/state exact lookup、turn explicit sort |
| `AbilityCost` | keyed lookup data | 导入后只读 lookup | `fetch(unitAbilityKey)` |
| `MapTileDefinitionRow` | keyed long-lived input layout | immutable terrain/cost facts | `fetch(position)`、packed scan、显式 grid order |
| `TileOccupancyRow` | keyed derived cache | 从 unit position 重建 | `fetch/mutate(position)`、可丢弃重建 |
| `MoveCandidateRow` | dense workspace | 单个 action phase 的候选 | `replaceAll(batch)`、explicit cost sort |
| `PendingDamageRow` | dense resolution buffer | 单个 resolution phase 的临时 state | `replaceAll(batch)`、explicit resolution sort |

`GridPosition` 是稳定 cell identity，flatten 到 primitive key leaves。Canonical tactics journey 的 pathfinding/occupancy 会频繁按 coordinate 点查，因此 tile definition 和 occupancy 都以 `GridPosition` 作为 `@SomaKey`；keyed table 仍保持 packed column scan。固定矩形大图若经 benchmark 证明 hash locator 不合适，可以使用 application-owned row-major primitive grid，但那是另一种明确的数据结构，不把 SOMA current Index 当长期 coordinate identity。

### 2.1 Application data role 边界

| Data role | Table / field group | 权威性与生命周期 | 优化判断 |
|---|---|---|---|
| input facts | `PlayerDefinition`、`GameUnitDefinition`、`AbilityCost`、`MapTileDefinitionRow` | battle 初始化后 authoritative、read-only/read-mostly | terrain/cost/capability 与 mutable battle state 分开 |
| working state | `PlayerState`、`GameUnitState` | battle 期间 authoritative mutable state | position、hp、action points、score 等下一回合继续依赖的事实 |
| working state | `TileOccupancyRow` | 从 `GameUnitState.position` 可重建的 derived cache | 与 immutable tile definition 分表；失败时丢弃并重建 |
| working state | `MoveCandidateRow`、`PendingDamageRow` | phase-local rebuildable workspace/resolution buffer | 不兼任 action history、damage history 或 result table |
| result facts | final player/unit state、battle outcome projection | 由最终 authoritative state 直接导出 | 默认不创建内容相同的 `BattleResult` shadow table |

目标模型将 immutable terrain/move cost 与 mutable `occupantUnit` cache 分开：`MapTileDefinitionRow` 只保存输入事实，`TileOccupancyRow` 只保存 derived working cache。类似地，schema 应优先区分 unit/player definition 与 mutable state；如果因 turn-order hot loop 需要把少量只读 leaf co-locate 到 `GameUnitState`，必须标为 preprojected input leaf，而不是第二份可修改 definition。

Final hp/position/score 在 battle 结束前仍是 working state，结束后直接成为 result projection 的来源。这里不额外维护 result table；只有 battle outcome 拥有独立 identity、生命周期或查询需求时，才新增独立 `BattleOutcome`。

### 2.2 Access Pattern Card

以下 card 是 game scenario/runtime-plan input，不进入 Schema/hash。Map size、unit count、candidate count、damage density 和 tick/action frequency 必须由 benchmark fixture 提供。

| Table / phase | Elements/cardinality | Hot columns | Access / mutation mix | Locality / allocation boundary |
|---|---|---|---|---|
| `GameUnitState` | live units | turn/state/position/hp/action points | explicit sorted next-unit、point fetch/mutate、player/state exact access | 记录 selector selectivity、mutation/read ratio、sort scratch 和 object-free cursor path |
| `MapTileDefinitionRow` / `TileOccupancyRow` | map cells | terrain/move cost vs occupant | keyed coordinate access、visibility/pathing packed scan、move 后 cache update/rebuild | 两张 root table 的 Index 独立；点查、paired-by-key scan、definition/state split 与 occupancy rebuild 分开计量 |
| `MoveCandidateRow` dense workspace | selected-unit reachable/action candidates | position、cost、remaining AP、tie-break | per action `replaceAll` + explicit dynamic sort + first | builder/column rewrite、sort scratch、capacity reuse、allocation/op 分开 |
| `PendingDamageRow` | current resolution batch | resolution order、source/target、damage | explicit sorted traversal + target point mutate + clear | 记录 target locality、aggregation、sort scratch 和 clear reuse；不混入 history/replay |
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
MoveCandidateRow chosen = moveCandidateRows
    .sorted(byTotalCostComparator).firstOrThrow();
```

是合理的 dense workspace 用法。它复用 capacity、连续写入候选、按本轮显式 comparator 选择。这里的 total-cost comparator 是 selected-unit workspace 的场景假设，不是全局行动策略；采用时应通过 benchmark 验证 `replaceAll + sorted + firstOrThrow` 的成本。

如果改成每 tick 为所有 unit 构建全局 action set：

```java
moveCandidateRows.replaceAll(buildMovesForAllUnits());
```

则会隐藏 pathing、visibility、ability lookup、tile occupancy 读取、builder 构造和 dynamic-sort 成本；这时应重新评估是否需要 keyed action frontier。

## 4. 生命周期判断

### 4.1 `MoveCandidateRow`

默认形态是 dense workspace：

- 候选只服务于 selected unit / current action；
- 生成和消费通常在同一 action phase 内完成；
- row 没有 stable logical key；
- selected `UnitId` 由 application action context 持有，不在每个 dense candidate row 重复保存；
- total-cost comparator 是当前 workspace 的 selection rule，不是 Schema 或长期策略事实。

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
- 按唯一 `(resolutionOrder, sequenceNo)` total order 消费；
- 写回 `GameUnit.hp`、`GameUnit.state` 和可能的 `Player.score`；
- 消费后 clear / replace；
- 不应被当作 damage history 或 replay log。

若业务需要取消、去重、跨 tick 保留、网络重放或幂等结算，就应引入 keyed event / damage command table，而不是继续使用 dense workspace。

### 4.3 `MapTileDefinitionRow` 与 `TileOccupancyRow`

`MapTileDefinitionRow` 是 keyed long-lived input layout，`TileOccupancyRow` 是由 unit position 派生的 keyed working cache：

- `terrain`、`moveCost`、`blocksSight` 偏静态；
- `occupantUnit` 只存在于 occupancy cache；
- `GridPosition` primary key 支持 coordinate exact lookup；
- grid-position comparator 只负责 deterministic traversal，不替代 keyed lookup。

这里存在典型双事实源风险：

```text
GameUnitState.position -> TileOccupancyRow.occupantUnit
```

蓝图建议明确 source-of-truth：

- `GameUnitState.position` 是单位位置事实源；
- `TileOccupancyRow.occupantUnit` 是 tile occupancy cache，用于 pathing/visibility；
- 每次移动先提交 `GameUnitState.position`，再更新或重建 occupancy cache；
- definition 与 occupancy 是两个独立 roots；即使按相同 row-major input 构建，也不能假设相同 coordinate 在两张表具有相同 current Index；
- SOMA V1 不提供跨 table transaction，失败处理由 game loop 拥有。

Battle 初始化必须验证 tile coordinate 无重复、occupancy key set 与可进入 map cell 规则一致、每个 live unit 恰好占用一个合法 coordinate、同一 coordinate 最多一个 unit，并验证 hp、action points、move/ability cost 与 damage 的非负业务约束。重建 occupancy 时先在 detached Batch 中完成这些检查，再一次 `replaceAll` 发布；发布后既有 Index、IndexSnapshot 或 application Index projection 全部丢弃。

### 4.4 为什么 phase workspace 不建成 child table

`MoveCandidateRow` 的生命周期属于 current action phase，不属于某个 `GameUnit` row 的长期生命周期。把它挂成 unit child 会让 entity materialization 意外递归展开 action workspace，并要求在 selected unit 切换时处理 stale child 内容。`PendingDamageRow` 同样属于全局 resolution phase，而不是某个 source/target unit 的独占 subtree。

因此二者保持 root-level dense workspace/buffer。只有模型明确定义一个拥有稳定生命周期的 `TurnWorkspace` / `ActionContext` parent，并且 child 不共享、不 reparent、删除 parent 时应级联释放时，才重新评估 child ownership；不得只为了“嵌套看起来自然”而引入 child table。

## 5. 场景对 Design 的压力

主要风险：

- **全局 move candidate rebuild**：对所有 unit 做 pathing 会掩盖大量重复计算；
- **coordinate identity 误用**：definition/occupancy 虽然都以 `GridPosition` keyed lookup，但它们的 current Index 互不等同；不能把一张表的 Index 用到另一张表；
- **candidate comparator 做 lookup**：不能在 `sorted` comparator 中读取 tile definition、occupancy cache、`AbilityCost` 或 `GameUnitState`；
- **occupancy cache drift**：`GameUnitState.position` 更新后若 `TileOccupancyRow` rebuild/update 失败会造成不一致；
- **damage buffer 生命周期扩大**：如果 pending damage 跨 tick 保留，dense workspace 缺少 identity、幂等和清理语义；
- **业务顺序误用**：total-cost 与 resolution-order 都是显式 comparator；二者都不是物理顺序或 heap。

## 6. 推荐的 schema annotation 草案

目标默认方案保留 dense workspace。

```java
@SomaSchema(
    name = "game_runtime_state",
    generatedPackage = "com.example.game.state.generated",
    version = "1"
)
package com.example.game.state;

@SomaTable(name = "map_tile_definition_rows", defaultCapacity = 4096)
public final class MapTileDefinitionRow {
    @SomaKey
    public GridPosition position;

    @SomaField
    public TerrainType terrain;

    @SomaField
    public int moveCost;

    @SomaField
    public boolean blocksSight;
}

@SomaTable(name = "tile_occupancy_rows", defaultCapacity = 4096)
public final class TileOccupancyRow {
    @SomaKey
    public GridPosition position;

    @SomaField
    @SomaOptional
    public UnitId occupantUnit;
}

@SomaTable(name = "move_candidate_rows", defaultCapacity = 2048)
public final class MoveCandidateRow {
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
    public long sequenceNo;

    @SomaField
    public UnitId sourceUnit;

    @SomaField
    public UnitId targetUnit;

    @SomaField
    public int damage;
}
```

`PendingDamageRow` 使用 `(resolutionOrder, sequenceNo)` total comparator，其中 `sequenceNo` 在当前 resolution batch 内唯一；`MoveCandidateRow` 使用 `(totalCost, position.y, position.x)` total comparator。二者都不进入 Schema；需要比较 dynamic sort 与小型 top-k `IndexBuffer`。默认 move workspace 只属于一个 selected unit，因此不重复保存 `unitId`；若同一 table 混入多 unit，它就已经不是本默认模型。

`sequenceNo` 由 application resolution context 单调分配并在溢出前 fail closed。这里只需要发布前唯一性验证，不为从未执行的 sequence point lookup 维护 `@SomaUnique`；若未来出现稳定、频繁的 command-id access，再重新评估 keyed command model。

默认形态不采纳全局 `ActionCandidate` frontier。下面只是条件式方案的约束清单，不是正式 schema 草案：

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

该 keyed frontier 不是默认设计。只有在跨 tick 保留、局部更新、按 unit / ability / target position 删除都成立，并且定义了 stable identity、失效规则、版本字段、必要 index 和 cache 清理策略后，才值得采用。`scoreReady` 也只是该方案的 scoring 生命周期字段，不复用 FJSP 的 `indicatorReady` 术语。

## 7. 推荐的 Java 8 SOMA API 使用流程

### 7.1 选择当前单位

```java
GameUnitState selected = unitStates
    .filter(u -> u.state() == UnitState.READY)
    .sorted((a, b) -> {
        int compared = Integer.compare(a.initiative(), b.initiative());
        return compared != 0 ? compared
            : Long.compare(a.unitIdValue(), b.unitIdValue());
    })
    .firstOrThrow();
```

这里把 `initiative` 定义为升序的 turn-order rank：数值越小越先行动，并以 `UnitId` 完成 tie-break。这是一次显式 dynamic sort，不是 generated maintained-order selector。采用“数值越大优先”的产品应把字段/比较方向明确改成对应语义，不能只反转 comparator 而不更新场景契约。若 turn order 跨大量 action 持续维护并成为热点，应由 application 使用以 `UnitId` 为 identity 的 indexed heap/turn scheduler；不能原地修改 Java `PriorityQueue` 中的排序字段，也不能让 SOMA 物理顺序决定下一个 unit。

### 7.2 为 selected unit 生成 move workspace

```java
MoveActionContext rebuildMoveCandidatesFor(GameUnitState selected) {
    if (!occupancyCacheValid) {
        throw new IllegalStateException("occupancy cache is not readable");
    }
    if (selected.actionPoints < 0) {
        throw new IllegalArgumentException("negative action points");
    }
    long nextGeneration = Math.addExact(
        currentActionGeneration, 1L);
    MoveActionContext nextContext = new MoveActionContext(
        selected.unitId, selected.position, selected.actionPoints,
        currentPathingRevision, nextGeneration);

    MoveCandidateRowBatch batch = reusableMoveBatch;
    batch.clear();

    computeReachableTiles(selected, tile -> {
        int totalCost = tile.totalCost();
        if (totalCost < 0) {
            throw new IllegalArgumentException("negative move cost");
        }
        int remaining = Math.subtractExact(
            selected.actionPoints, totalCost);
        if (remaining >= 0) {
            batch.addValues(tile.position(), totalCost, remaining);
        }
    });

    moveCandidateRows.replaceAll(batch);
    currentActionGeneration = nextGeneration;
    currentMoveContext = nextContext;
    return currentMoveContext;
}
```

`computeReachableTiles(...)` 属于 game/pathing layer。它按 `GridPosition` 从 tile definition/occupancy point lookup，验证目标可进入且不被占用，并在 non-negative action-point budget 内为每个非 origin coordinate 至多产生一个候选；原地等待是另一种显式 action，不伪装成 move。路径搜索规则不进入 SOMA schema。`nextContext` 在 workspace publish 前完成构造，因此 `replaceAll` 失败不会推进 generation/context；`reusableMoveBatch` 是 battle owner 的 bounded staging，在同步 action phase 内复用，不跨线程共享。`currentPathingRevision` 是 application-owned checked counter；任何会改变可达性或占用的 authoritative mutation 都必须推进它。

### 7.3 选择移动候选

```java
PreparedMove chooseMove(MoveActionContext context) {
    if (context == null
            || !context.equals(currentMoveContext)
            || context.pathingRevision() != currentPathingRevision) {
        throw new IllegalStateException("stale move action context");
    }

    MoveCandidateRow chosen = moveCandidateRows
        .filter(m -> m.remainingActionPoints() >= 0)
        .sorted((a, b) -> {
            int compared = Integer.compare(a.totalCost(), b.totalCost());
            if (compared != 0) return compared;
            compared = Integer.compare(
                a.positionYValue(), b.positionYValue());
            return compared != 0 ? compared
                : Integer.compare(a.positionXValue(), b.positionXValue());
        })
        .firstOrThrow();
    return PreparedMove.fromCurrentContext(context, chosen);
}
```

`moveCandidateRows.replaceAll(batch)` 是单 selected-unit workspace 边界。Selected unit identity、origin、initial action points、pathing revision 和 workspace generation 留在 immutable application `MoveActionContext`，不复制进每个 candidate row。`PreparedMove` 是只能由 battle owner 从当前 context 与刚选择的 detached row 构造的 immutable application value；它把 context generation 与 position/cost facts 绑定为一个提交参数。`MoveActionContext` 的 value equality 覆盖全部这些字段。切换 selected unit、重建 workspace，或任何会改变 pathing/occupancy/cost 的 state mutation 后，旧 context/`PreparedMove` 一律失效。

7.1 和 7.3 的 readable path 分别物化一个 detached unit/candidate，并构造一个 `PreparedMove`；它们不是零分配声明。只有 benchmark 证明该成本显著时，才改用立即消费的 `IndexSnapshot + ColumnView` 或 application indexed scheduler；任何方案都不能把 SOMA Index 保存进 action context。

如果 comparator 需要 tie-breaker，应只读取 `MoveCandidateRow` 字段，不在 comparator 内查 tile definition、occupancy cache 或 `GameUnitState`。

### 7.4 Commit move

```java
enum MoveCommitResult {
    COMMITTED,
    STALE
}

MoveCommitResult commitMove(PreparedMove prepared) {
    if (prepared == null
            || prepared.context() == null
            || !prepared.context().equals(currentMoveContext)) {
        return MoveCommitResult.STALE;
    }

    MoveActionContext context = prepared.context();
    UnitId unitId = context.unitId();
    GameUnitState current = unitStates.fetch(unitId);
    GridPosition from = current.position;
    GridPosition to = prepared.position();

    if (!occupancyCacheValid
            || context.pathingRevision() != currentPathingRevision) {
        return MoveCommitResult.STALE;
    }

    TileOccupancyRow source = tileOccupancyRows.fetch(from);
    TileOccupancyRow target = tileOccupancyRows.fetch(to);
    if (current.state != UnitState.READY
            || !current.position.equals(context.origin())
            || current.actionPoints != context.initialActionPoints()
            || prepared.totalCost() < 0
            || prepared.remainingActionPoints() < 0
            || prepared.remainingActionPoints()
                != Math.subtractExact(
                    current.actionPoints, prepared.totalCost())
            || source.occupantUnit == null
            || !source.occupantUnit.equals(unitId)
            || target.occupantUnit != null) {
        return MoveCommitResult.STALE;
    }

    long nextPathingRevision = Math.addExact(
        currentPathingRevision, 1L);
    unitStates.mutate(unitId)
        .setPosition(prepared.position())
        .setActionPoints(prepared.remainingActionPoints())
        .setState(UnitState.MOVED)
        .commit();
    currentPathingRevision = nextPathingRevision;

    try {
        tileOccupancyRows.mutate(from).clearOccupantUnit().commit();
        tileOccupancyRows.mutate(to).setOccupantUnit(unitId).commit();
    } catch (SomaRuntimeException cacheFailure) {
        occupancyCacheValid = false;
        try {
            rebuildOccupancyCacheFromUnits();
            occupancyCacheValid = true;
        } catch (RuntimeException rebuildFailure) {
            rebuildFailure.addSuppressed(cacheFailure);
            throw rebuildFailure;
        }
        recordRecoveredOccupancyFailure(cacheFailure);
    }

    currentMoveContext = null;
    moveCandidateRows.clear();
    return MoveCommitResult.COMMITTED;
}
```

`MoveActionContext`/`PreparedMove` 只保存 application identity、generation、pathing revision 与选中值，不保存 SOMA Index。`GameUnitState.position` 是 source-of-truth；`TileOccupancyRow.occupantUnit` 是 cache。`STALE` 不算成功 action，caller 必须重建候选。revision overflow 在 authoritative mutation 前 fail closed；position commit 成功后 revision 必须推进，即使后续 cache 增量更新失败也不能回退。

cache 的两个 keyed mutation 不是事务；预期的 SOMA table failure 会先把 cache 标为不可读，再从全部 live unit positions 重建。重建成功后由 non-throwing diagnostic sink 记录原 failure，并把 action 视为已提交；重建失败时保留原 failure 为 suppressed evidence，并由 battle owner 停止当前 frame/battle。普通 application bug/Error 不被当成可恢复 cache miss 吞掉。SOMA V1 不提供跨 table transaction。

authoritative unit state 与 occupancy cache 已提交后，先把 `currentMoveContext` 失效，再清理 derived move workspace。若 `clear()` 失败，旧 candidate 即使仍在 Table 中也没有可提交的 context；本次 move 已经完成，caller 不得重试 commit，只能先重建 workspace，或在 Table 已不可用时停止 battle。

Canonical keyed coordinate path 仍需与其他形态做同语义比较：

- keyed `GridPosition -> current Index` locator；
- 固定矩形 grid 的 application-owned row-major primitive array；
- 在单次同步只读消费批次内临时构造并立即消费的 coordinate-to-current-Index projection。

最后一种方案不能跨 operation 保存：它只在当前同步只读批次内有效，来源 Table 期间不得 mutation，批次结束即丢弃；任何 mutation 或 lifecycle change 都使其失效，而不只是 structural mutation。definition 与 occupancy 也不能跨 Table 复用 current Index。需要长期 coordinate mapping 时必须保存 `GridPosition` key，或使用 application-owned row-major primitive storage，而不是保存 SOMA Index。

### 7.5 Damage 结算

```java
void resolveDamage() {
    IndexSnapshot ordered = pendingDamageRows
        .sorted((a, b) -> {
            int compared = Long.compare(
                a.resolutionOrder(), b.resolutionOrder());
            return compared != 0 ? compared
                : Long.compare(a.sequenceNo(), b.sequenceNo());
        })
        .indexSnapshot();

    damageCommands.clear();
    try (LongColumnView resolutionOrders =
             pendingDamageRows.resolutionOrderColumn();
         LongColumnView sequences = pendingDamageRows.sequenceNoColumn();
         LongColumnView sources = pendingDamageRows.sourceUnitValueColumn();
         LongColumnView targets = pendingDamageRows.targetUnitValueColumn();
         IntColumnView amounts = pendingDamageRows.damageColumn()) {
        for (int i = 0; i < ordered.size(); i++) {
            int index = ordered.indexAt(i);
            damageCommands.add(
                resolutionOrders.getLong(index),
                sequences.getLong(index),
                sources.getLong(index),
                targets.getLong(index),
                amounts.getInt(index));
        }
    }

    validateDamageCommands(damageCommands, unitStates);
    for (int i = 0; i < damageCommands.size(); i++) {
        UnitId targetId = new UnitId(damageCommands.targetId(i));
        int damage = damageCommands.amount(i);
        GameUnitState target = unitStates.fetch(targetId);
        int nextHp = Math.max(0, Math.subtractExact(target.hp, damage));

        unitStates.mutate(targetId)
            .setHp(nextHp)
            .setState(nextHp == 0 ? UnitState.DEAD : target.state)
            .commit();
    }

    pendingDamageRows.clear();
}
```

`damageCommands` 是 application-owned reusable primitive staging，先在同步只读 Index batch 中复制完整 command facts，再离开 pending-table operation 执行跨表 mutation；它不保存 SOMA Index。`validateDamageCommands` 至少拒绝 negative damage、missing source/target、重复 `sequenceNo`、非法 order 和同 target 累积结算的 arithmetic overflow。从第一次 unit mutation 到最终 `pendingDamageRows.clear()` 的任何失败，都使 canonical dense-buffer battle instance 不可继续使用，且不得重放仍未 clear 的 command；需要 retry/replay/幂等语义时应改用带 stable command identity 的 keyed event model 和 application checkpoint。

这不是纯 dense buffer scan。每条 pending damage 还包含 application staging、unit keyed lookup/materialization、`UnitId` reference-path 构造、unit mutation和最终 clear。benchmark 应分别计量 pending sort/copy、per-target access/mutation、application rule cost 和 clear；hot lane 可以使用 flattened key lookup 与 primitive ColumnView 减少这些对象，但不能改变 fail-stop/command identity 语义。

`firstOrThrow()`、`fetch(...)` 返回 detached schema object；`@SomaTable` row 不生成 structural equality/hash。`UnitId` / `GridPosition` 等 `@SomaValue` 使用 compiler-defined canonical value equality。Boundary snapshot 应拆分为 Materialized Object、external DTO adapter 和 wire/replay mapping；final player/unit state 直接从 authoritative state materialize，不复制 `BattleResult` shadow rows。若引入 parent-owned child object graph，必须使用 runtime plan 默认或显式 `MaterializationBudget`。

这是跨 table mutation，SOMA V1 不提供 transaction。game loop 必须决定失败时是停止 frame、回滚到外部 snapshot，还是重建 derived workspace。

## 8. Cache 友好性分析

Dense workspace 方案的 cache 友好性来自：

- `MoveCandidateRow` 按 selected unit 批量重建，候选连续写入；
- total-cost dynamic sort 只作用于当前 workspace；
- `PendingDamageRow` 作为 resolution buffer 连续扫描；
- `MapTileDefinitionRow` 和 `TileOccupancyRow` 各自保持 packed keyed layout，兼顾 coordinate lookup 与整图 scan，但不共享 current Index；

主要削弱因素：

- keyed coordinate lookup 仍有 hash/probe 成本，固定矩形大图可能更适合 application-owned row-major primitive array；
- `TileOccupancyRow.occupantUnit` 是从 `GameUnitState.position` 重建的 cache，需要明确同步/重建；
- 如果全局 AI 每 tick 生成所有 unit moves，dense full rebuild 可能膨胀；
- `AbilityCost.fetch(unitAbilityKey)` 若在候选 comparator 或 inner loop 中重复发生，会变成随机 lookup 热点。

可能的优化路径：

- selected-unit pathing 保持 dense `MoveCandidateRow.replaceAll(batch)`；
- 仅在全局 AI planning 满足跨 tick identity 与局部失效条件时评估 keyed `ActionCandidate` frontier；
- canonical path 使用 keyed `GridPosition`；固定矩形高压场景再与 application-owned primitive grid 或单批次 Index projection 比较；
- pending damage 保持 dense buffer，跨 tick event log 另建 keyed command/event table。

## 9. 目标形态必须处理的边界

- keyed tile/occupancy 是 canonical coordinate access；显式 grid-position sort 只表达确定性遍历，不替代 key lookup；
- 两张独立 table 的 current Index 不可互换；application Index projection 只能在一次同步、只读、来源 table 无 mutation 的消费批次内立即使用；
- workspace `replaceAll` 很适合 selected-unit move candidates，但不适合未加判断地扩展到全局 action set；
- occupancy cache 的多 row 更新不是跨 operation transaction；失败后必须先 invalidate，再从 unit position 重建；
- total-cost comparator 是 selected-unit workspace 的策略假设，不应被误解成全局行动策略；
- pending damage 如果扩展成跨 tick command，需要 identity、dedup、replay 和 failure handling；
- selected-unit workspace 和 damage buffer 没有合适的 exclusive parent lifecycle 时，不应机械改成 child table；
- input definitions、mutable battle state、phase workspace 和 final projection 必须分层；不为相同 final hp/position/score 创建 shadow result table；
- 示例必须持续强调 SOMA 不是 ECS / game engine，不拥有 system scheduling 或 game rules。

## 10. 目标决策与证明义务

### 10.1 目标决策

- Player/unit/tile input definition、mutable battle state、phase workspace 与 result projection 分层；final state 不复制 shadow result table；
- map tile definition 与 occupancy 以 `GridPosition` 作为 primary identity；两张 table 的 current Index 永不互换；
- `GameUnitState.position` 是位置事实源，`TileOccupancyRow.occupantUnit` 是可重建 cache；
- selected-unit `MoveCandidateRow` 与 `PendingDamageRow` 保持 root-level dense phase buffer；后者不是 history/log；
- 全局 `ActionCandidate` frontier 只有在跨 tick identity、复用和局部失效同时成立时才采用；
- game loop 拥有 coordinate strategy、occupancy recovery、damage command semantics 和跨 table failure 处置。

### 10.2 采用前证明义务

- 验证 selected-unit `replaceAll + dynamic sort` 的规模边界，并与条件式 keyed frontier 做同语义比较；
- 比较 canonical keyed tile access、application row-major primitive grid 与单批次 Index projection，并验证各自 invariant 和成本；
- 验证 pending-damage sort/clear、per-target keyed lookup/mutation 与 occupancy rebuild；
- 分开计量 schema materialization、external DTO mapping 和任何 parent-owned child deep materialization；
- 以 invariant test 证明 definition/state 分离、position/occupancy cache 同步和 failure recovery。
