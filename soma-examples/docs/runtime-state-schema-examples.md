# Runtime state schema 典型示例

状态：正式设计文档
日期：2026-07-06
Owner：`soma-examples`

## 1. 目标

本文给出 `soma_java` V1 在 Java-only 项目中的四类典型 runtime state schema 示例：

1. FJSP 构造解过程；
2. VRP 构造解过程；
3. 连续仿真过程；
4. game runtime state。

这些示例只表达构造、仿真或 game loop 运行期间的高性能 runtime data container，不表达 optimization search、策略选择、规则调度或 UI / service 集成。上层 OOP 负责 workflow orchestration、algorithm strategy、domain rule 和 solver / simulator / game loop；SOMA 负责 schema-defined hot layout、key/index/order access、packed dense state 和 DTO materialization。

代码块是 schema source 的合并展示。真实 Java 项目中，`package-info.java`、enum、`@SomaValue` 和 `@SomaTable` DTO class 应按 Java 文件规则拆分。

## 2. 通用建模规则

四个示例共同遵守以下规则：

- DTO class 同时是 schema source 和 materialized DTO contract；keyed table 的 `fetch(key)` 返回 DTO detached copy，不引入 `fetchDto()`；
- table 只分为 keyed table 与 dense table；`entity`、`lookup`、`workspace`、`matrix`、`event queue` 是建模场景，不是 annotation role；
- 有 stable logical key 且需要 `fetch(key)` / `containsKey(key)` / uniqueness 的 runtime data 建模为 keyed table；
- 没有 stable key、以 packed scan、row-index iteration、批量替换或矩阵行访问为主的数据建模为 dense table；
- 频繁查询的静态或导入后只读数据，如果有自然唯一 key，优先建成 keyed lookup table；
- `@SomaValue` 表达 inline value / composite key，会 flatten 到 table leaf columns；`@SomaValue` 内部不允许 table typed field；
- cross-table reference 使用 scalar、enum、semantic scalar 或 value key，例如 `MachineId`、`CustomerId`、`UnitId`，不保存另一个 root table object；
- child table 只用于 parent row owns child table instance 的生命周期关系，四个示例默认不使用 child table；
- optional scalar DTO 字段使用 boxed type，例如 `Long`、`Integer`、`Double`、`Boolean`，absent materialize 为 `null`；
- `defaultCapacity` 只是 allocation hint，不进入 logical `schema_hash`。

## 3. FJSP 构造解 runtime state

### 3.1 场景边界

FJSP 示例表达 `FCFS + SPT` 一类构造解过程中的 runtime state：

```text
jobs / operations / materials / machines / processing times / setup times
  -> operation release
  -> machine_candidates keyed runtime frontier
  -> per-machine indicator update
  -> dynamic sorted dispatch
  -> assignment mutation / frontier cleanup
  -> DTO export
```

不表达完整 APS、CP-SAT、局部搜索、重排优化或多目标优化。`ProcessingTime` 和 `SetupTime` 是 keyed lookup table，不嵌入 `Operation` 生命周期；`MachineCandidate` 是 keyed runtime frontier，用于保存 operation release 后可加工的 `(MachineId, OperationKey)` 候选。它不是每轮临时 `replaceAll(batch)` workspace，也不在 schema 中固化 dispatch rule order。

### 3.2 Schema source 示例

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaOrder;
import com.hgtech.soma.annotation.SomaSchema;
import com.hgtech.soma.annotation.SomaSort;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaValue;

public enum MachineState {
    READY,
    DOWN
}

@SomaValue
public final class JobId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationId {
    @SomaField
    public long value;
}

@SomaValue
public final class MachineId {
    @SomaField
    public long value;
}

@SomaValue
public final class MaterialId {
    @SomaField
    public long value;
}

@SomaValue
public final class SetupFamilyId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationKey {
    @SomaField
    public JobId jobId;

    @SomaField
    public OperationId operationId;
}

@SomaValue
public final class OperationMachineKey {
    @SomaField
    public OperationKey operationKey;

    @SomaField
    public MachineId machineId;
}

@SomaValue
public final class SetupFamilyPair {
    @SomaField
    public SetupFamilyId fromFamily;

    @SomaField
    public SetupFamilyId toFamily;
}

@SomaValue
public final class SetupTimeKey {
    @SomaField
    public MachineId machineId;

    @SomaField
    public SetupFamilyPair familyPair;
}

@SomaTable(name = "jobs", defaultCapacity = 1024)
@SomaOrder(name = "by_dispatch_order", by = {
    @SomaSort("inputOrder"),
    @SomaSort("jobId.value")
})
public final class Job {
    @SomaKey
    public JobId jobId;

    @SomaField
    public long inputOrder;

    @SomaField
    public long dueMinute;

    @SomaField
    public int operationCount;

    @SomaField
    @SomaDefault("0")
    public int nextSequenceNo;

    @SomaOptional
    public Long completedMinute;

    @SomaOptional
    public Long tardinessMinutes;
}

@SomaTable(name = "operations", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
public final class Operation {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public long inputOrder;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long releaseMinute;

    @SomaField
    public long jobReadyMinute;

    @SomaField
    public long materialReadyMinute;

    @SomaField
    public SetupFamilyId setupFamily;

    @SomaOptional
    public MachineId assignedMachine;

    @SomaOptional
    public Long setupStartMinute;

    @SomaOptional
    public Long setupMinutes;

    @SomaOptional
    public Long startMinute;

    @SomaOptional
    public Long processingMinutes;

    @SomaOptional
    public Long endMinute;
}

@SomaTable(name = "materials", defaultCapacity = 4096)
public final class Material {
    @SomaKey
    public MaterialId materialId;

    @SomaField
    public long readyMinute;
}

@SomaTable(name = "machines", defaultCapacity = 128)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_available_time", by = {
    @SomaSort("availableFromMinute"),
    @SomaSort("machineId.value")
})
public final class Machine {
    @SomaKey
    public MachineId machineId;

    @SomaField
    @SomaDefault("READY")
    public MachineState state;

    @SomaField
    @SomaDefault("0")
    public long availableFromMinute;

    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "processing_times", defaultCapacity = 8192)
@SomaIndex(name = "by_operation", fields = {
    "operationMachineKey.operationKey.jobId.value",
    "operationMachineKey.operationKey.operationId.value"
})
public final class ProcessingTime {
    @SomaKey
    public OperationMachineKey operationMachineKey;

    @SomaField
    public long processingMinutes;
}

@SomaTable(name = "setup_times", defaultCapacity = 1024)
@SomaIndex(name = "by_machine_to_family", fields = {
    "setupTimeKey.machineId.value",
    "setupTimeKey.familyPair.toFamily.value"
})
public final class SetupTime {
    @SomaKey
    public SetupTimeKey setupTimeKey;

    @SomaField
    public long setupMinutes;
}

@SomaTable(name = "machine_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_machine", fields = {
    "candidateKey.machineId.value"
})
@SomaIndex(name = "by_operation", fields = {
    "candidateKey.operationKey.jobId.value",
    "candidateKey.operationKey.operationId.value"
})
public final class MachineCandidate {
    @SomaKey
    public OperationMachineKey candidateKey;

    @SomaField
    public SetupFamilyId targetSetupFamily;

    @SomaField
    public long operationReleaseMinute;

    @SomaField
    public long jobReadyMinute;

    @SomaField
    public long materialReadyMinute;

    @SomaField
    public long baseReadyMinute;

    @SomaField
    public long processingMinutes;

    @SomaField
    public long setupMinutes;

    @SomaField
    public long effectiveReadyMinute;

    @SomaField
    public long fcfsValue;

    @SomaField
    public long sptValue;

    @SomaField
    public boolean indicatorReady;
}
```

### 3.3 使用方式

- `Job`、`Operation`、`Material`、`Machine` 是 keyed entity state；
- `ProcessingTime`、`SetupTime` 是 keyed lookup data；
- `MachineCandidate` 是 keyed runtime frontier，primary key 是包含 `MachineId` 与 `OperationKey` 的组合；
- `MachineCandidate.by_machine` 支撑当前 machine dispatch，`MachineCandidate.by_operation` 支撑 operation 被选中后的候选清理；
- `MachineCandidate` 不声明 dispatch order，FCFS、SPT 和 setup 相关 indicator 由 solver loop 在 dispatch 前更新，再通过 Row Pipeline `sorted(comparator)` 动态排序；
- `assignedMachine` 是 cross-table key reference，`OperationTable.fetch(key)` materialize `Operation` DTO；
- 上层 dispatch loop 决定 operation release、indicator 计算、FCFS + SPT 排序和跨 table mutation 顺序，SOMA 不拥有调度策略。

## 4. VRP 构造解 runtime state

### 4.1 场景边界

VRP 示例表达 greedy insertion / cheapest insertion 构造解过程中的 runtime state：

```text
vehicles / customers / travel cost lookup
  -> unassigned customer workspace
  -> route visit dense rows
  -> insertion candidate workspace
  -> route/customer mutation
```

不表达局部搜索、Tabu、LNS、列生成、CP-SAT 或最优性证明。路线访问序列使用 dense table 表达，因为插入位置会频繁变化，`position` 是当前 route sequence 的位置，不是 stable business identity。

### 4.2 Schema source 示例

```java
@SomaSchema(
    name = "vrp_construction_runtime_state",
    generatedPackage = "com.example.vrp.state.generated",
    version = "1"
)
package com.example.vrp.state;

public enum CustomerState {
    UNASSIGNED,
    ASSIGNED,
    SKIPPED
}

@SomaValue
public final class CustomerId {
    @SomaField
    public long value;
}

@SomaValue
public final class VehicleId {
    @SomaField
    public long value;
}

@SomaValue
public final class RouteId {
    @SomaField
    public long value;
}

@SomaValue
public final class LocationId {
    @SomaField
    public long value;
}

@SomaValue
public final class LocationPairKey {
    @SomaField
    public LocationId fromLocation;

    @SomaField
    public LocationId toLocation;
}

@SomaTable(name = "customers", defaultCapacity = 4096)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_due_then_input", by = {
    @SomaSort("dueMinute"),
    @SomaSort("inputOrder"),
    @SomaSort("customerId.value")
})
public final class Customer {
    @SomaKey
    public CustomerId customerId;

    @SomaField
    public long inputOrder;

    @SomaField
    public LocationId locationId;

    @SomaField
    public int demand;

    @SomaField
    public long readyMinute;

    @SomaField
    public long dueMinute;

    @SomaField
    public long serviceMinutes;

    @SomaField
    @SomaDefault("UNASSIGNED")
    public CustomerState state;

    @SomaOptional
    public RouteId assignedRoute;

    @SomaOptional
    public Integer assignedPosition;

    @SomaOptional
    public Long arrivalMinute;
}

@SomaTable(name = "vehicles", defaultCapacity = 512)
@SomaOrder(name = "by_vehicle_id", by = {
    @SomaSort("vehicleId.value")
})
public final class Vehicle {
    @SomaKey
    public VehicleId vehicleId;

    @SomaField
    public int capacity;

    @SomaField
    public LocationId startLocation;

    @SomaField
    public LocationId endLocation;

    @SomaField
    @SomaDefault("0")
    public long availableFromMinute;
}

@SomaTable(name = "routes", defaultCapacity = 512)
@SomaIndex(name = "by_vehicle", fields = {"vehicleId.value"})
@SomaOrder(name = "by_route_id", by = {
    @SomaSort("routeId.value")
})
public final class Route {
    @SomaKey
    public RouteId routeId;

    @SomaField
    public VehicleId vehicleId;

    @SomaField
    @SomaDefault("0")
    public int load;

    @SomaField
    @SomaDefault("0")
    public long totalDistanceMeters;

    @SomaField
    @SomaDefault("0")
    public long totalDurationSeconds;

    @SomaField
    @SomaDefault("false")
    public boolean closed;
}

@SomaTable(name = "travel_costs", defaultCapacity = 65536)
public final class TravelCost {
    @SomaKey
    public LocationPairKey locationPair;

    @SomaField
    public long distanceMeters;

    @SomaField
    public long travelSeconds;
}

@SomaTable(name = "route_visit_rows", defaultCapacity = 8192)
@SomaOrder(name = "by_route_position", by = {
    @SomaSort("routeId.value"),
    @SomaSort("position")
})
public final class RouteVisitRow {
    @SomaField
    public RouteId routeId;

    @SomaField
    public int position;

    @SomaField
    public CustomerId customerId;

    @SomaField
    public long arrivalMinute;

    @SomaField
    public long departureMinute;

    @SomaField
    public int loadAfterVisit;
}

@SomaTable(name = "unassigned_customer_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_due_then_input", by = {
    @SomaSort("dueMinute"),
    @SomaSort("inputOrder"),
    @SomaSort("customerId.value")
})
public final class UnassignedCustomerRow {
    @SomaField
    public CustomerId customerId;

    @SomaField
    public int demand;

    @SomaField
    public long dueMinute;

    @SomaField
    public long inputOrder;
}

@SomaTable(name = "insertion_candidate_rows", defaultCapacity = 16384)
@SomaOrder(name = "by_best_delta", by = {
    @SomaSort("violationPenalty"),
    @SomaSort("deltaDistanceMeters"),
    @SomaSort("projectedArrivalMinute"),
    @SomaSort("customerId.value")
})
public final class InsertionCandidateRow {
    @SomaField
    public CustomerId customerId;

    @SomaField
    public RouteId routeId;

    @SomaField
    public int insertAfterPosition;

    @SomaField
    public long deltaDistanceMeters;

    @SomaField
    public long projectedArrivalMinute;

    @SomaField
    public long violationPenalty;
}
```

### 4.3 使用方式

- `Customer`、`Vehicle`、`Route` 是 keyed entity state；
- `TravelCost` 是 keyed lookup table，用于 `LocationPairKey -> distance/travel time`；
- `RouteVisitRow` 是 dense route sequence，不承诺 `position` 是 stable key；
- `UnassignedCustomerRow` 和 `InsertionCandidateRow` 是 dense workspace，通过 order access 支撑构造解选择；
- 上层 VRP constructor 负责容量、时间窗、候选生成和路线关闭策略。

## 5. 连续仿真 runtime state

### 5.1 场景边界

连续仿真示例表达 tank / valve network 的 time-step simulation runtime state：

```text
tank and valve entity state
  -> flow coefficient lookup
  -> state vector dense rows
  -> pending event dense rows
  -> trace sample dense rows
```

不表达 ODE solver 实现、数值积分策略、并行仿真调度或事件业务规则。SOMA 只承载可被 simulator hot loop 反复读取、扫描和更新的状态容器。

### 5.2 Schema source 示例

```java
@SomaSchema(
    name = "continuous_sim_runtime_state",
    generatedPackage = "com.example.sim.state.generated",
    version = "1"
)
package com.example.sim.state;

import com.hgtech.soma.annotation.SomaSemantic;

public enum SimEntityKind {
    TANK,
    VALVE
}

public enum SimEventKind {
    VALVE_SETPOINT,
    MATERIAL_FEED,
    SENSOR_SAMPLE
}

@SomaValue
public final class TankId {
    @SomaField
    public long value;
}

@SomaValue
public final class ValveId {
    @SomaField
    public long value;
}

@SomaValue
public final class MaterialId {
    @SomaField
    public long value;
}

@SomaValue
public final class ValveMaterialKey {
    @SomaField
    public ValveId valveId;

    @SomaField
    public MaterialId materialId;
}

@SomaTable(name = "tanks", defaultCapacity = 256)
@SomaOrder(name = "by_tank_id", by = {
    @SomaSort("tankId.value")
})
public final class Tank {
    @SomaKey
    public TankId tankId;

    @SomaField
    public MaterialId materialId;

    @SomaField
    public double levelLiters;

    @SomaField
    public double capacityLiters;

    @SomaField
    public double temperatureCelsius;

    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long lastUpdateMillis;
}

@SomaTable(name = "valves", defaultCapacity = 512)
@SomaIndex(name = "by_from_tank", fields = {"fromTank.value"})
@SomaIndex(name = "by_to_tank", fields = {"toTank.value"})
public final class Valve {
    @SomaKey
    public ValveId valveId;

    @SomaField
    public TankId fromTank;

    @SomaField
    public TankId toTank;

    @SomaField
    public double openingRatio;

    @SomaField
    public double maxFlowLitersPerSecond;

    @SomaField
    @SomaDefault("true")
    public boolean enabled;
}

@SomaTable(name = "flow_coefficients", defaultCapacity = 1024)
public final class FlowCoefficient {
    @SomaKey
    public ValveMaterialKey valveMaterialKey;

    @SomaField
    public double coefficient;
}

@SomaTable(name = "state_vector_rows", defaultCapacity = 4096)
@SomaOrder(name = "by_vector_index", by = {
    @SomaSort("vectorIndex")
})
public final class StateVectorRow {
    @SomaField
    public int vectorIndex;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public double value;

    @SomaField
    public double derivative;

    @SomaField
    public double scale;
}

@SomaTable(name = "pending_event_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_event_time", by = {
    @SomaSort("eventTimeMillis"),
    @SomaSort("sequenceNo")
})
public final class PendingEventRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long eventTimeMillis;

    @SomaField
    public long sequenceNo;

    @SomaField
    public SimEventKind eventKind;

    @SomaField
    public SimEntityKind targetKind;

    @SomaField
    public long targetId;

    @SomaOptional
    public Double numericPayload;
}

@SomaTable(name = "trace_sample_rows", defaultCapacity = 65536)
@SomaOrder(name = "by_time_entity", by = {
    @SomaSort("sampleTimeMillis"),
    @SomaSort("entityKind"),
    @SomaSort("entityId")
})
public final class TraceSampleRow {
    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long sampleTimeMillis;

    @SomaField
    public SimEntityKind entityKind;

    @SomaField
    public long entityId;

    @SomaField
    public double value;
}
```

### 5.3 使用方式

- `Tank`、`Valve` 是 keyed entity state；
- `FlowCoefficient` 是 keyed lookup table；
- `StateVectorRow` 是 dense packed state vector，`vectorIndex` 只是当前向量布局位置；
- `PendingEventRow` 是 dense event queue workspace，通过 `by_event_time` 取下一批事件；
- `TraceSampleRow` 是 dense trace buffer / export buffer；
- simulator OOP 层负责数值积分、事件应用和采样策略，SOMA 不拥有仿真算法。

## 6. Game runtime state

### 6.1 场景边界

Game 示例表达 grid tactics / turn-based game loop 的 runtime state：

```text
players / units / map tiles / ability cost lookup
  -> visibility or move candidate dense rows
  -> pending damage dense rows
  -> unit mutation and DTO export
```

SOMA 不是 ECS framework，不拥有 system scheduling、rendering、input、network replication 或 game rules。它只承载 game loop 中需要高频扫描、排序、按 key mutation 或 DTO materialization 的 runtime state。

### 6.2 Schema source 示例

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

### 6.3 使用方式

- `Player`、`GameUnit` 是 keyed entity state；
- `AbilityCost` 是 keyed lookup table；
- `MapTileRow` 是 dense grid layout，适合 render/pathing scan；如果某个项目主要按 coordinate random fetch tile，可以改为 keyed table；
- `MoveCandidateRow`、`PendingDamageRow` 是 dense workspace；
- game loop OOP 层负责回合推进、技能规则、路径搜索和渲染同步，SOMA 不替代 game engine。

## 7. 四类示例的覆盖矩阵

| 示例 | Keyed entity state | Keyed lookup data | Dense long-lived state | Dense workspace | 主要证明点 |
|---|---|---|---|---|---|
| FJSP | `Job`、`Operation`、`Material`、`Machine`、`MachineCandidate` | `ProcessingTime`、`SetupTime` | 无 | 无 | runtime frontier 是 keyed table，不是每轮 dense workspace |
| VRP | `Customer`、`Vehicle`、`Route` | `TravelCost` | `RouteVisitRow` | `UnassignedCustomerRow`、`InsertionCandidateRow` | route sequence 是 packed rows，不是 stable key rows |
| 连续仿真 | `Tank`、`Valve` | `FlowCoefficient` | `StateVectorRow`、`TraceSampleRow` | `PendingEventRow` | state vector / event queue 是 dense runtime data |
| Game | `Player`、`GameUnit` | `AbilityCost` | `MapTileRow` | `MoveCandidateRow`、`PendingDamageRow` | SOMA 可承载 game hot state，但不是 ECS / engine |

## 8. 对正式契约的覆盖说明

四个示例覆盖并验证 `soma_java` V1 annotation contract 的几个边界。示例文档不拥有 annotation contract；如果示例与正式契约冲突，以 `soma-annotations/docs/annotation-schema-contract.md` 为准。

- `@SomaField` 比 `@SomaColumn` 更符合 Java DTO schema source；column 是 runtime flatten 之后的物理概念；
- `@SomaKey` 必须是 table direct field 的 logical identity，value key 足以表达 composite key；
- 不需要 `@SomaEnum`，Java enum 被 SOMA field 引用后自动纳入 schema；
- 不需要 `@SomaTableRole`，四个示例中的角色都能由 keyed / dense table 与命名文档说明表达；
- `@SomaOrder` 的多个声明应理解为 named ordered access path，不是 table physical order；
- optional scalar DTO 字段必须使用 boxed type，否则无法表达 absent materialized DTO；
- semantic scalar 只在确实需要时间语义时显式声明，例如连续仿真的 `DATE_TIME`。

## 9. Non-goals

本文不定义：

- generated API 的最终方法签名；
- runtime core 的 sidecar 内部结构；
- benchmark 规模、性能结论或对比口径；
- 完整业务求解器、仿真器或 game engine；
- schema migration 或跨版本兼容策略；
- persistence / wire format / protobuf schema。
