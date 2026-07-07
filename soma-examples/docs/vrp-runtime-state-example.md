# VRP 构造解 runtime state 示例

状态：正式设计文档
日期：2026-07-07
Owner：`soma-examples`

## 1. 文档定位

本文是 `VRP 构造解 runtime state 示例` 的独立场景文档。它从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 拆分而来，遵守该总览文档中的通用建模规则。

## 2. 场景边界

VRP 示例表达 greedy insertion / cheapest insertion 构造解过程中的 runtime state：

```text
vehicles / customers / travel cost lookup
  -> unassigned customer workspace
  -> route visit dense rows
  -> insertion candidate workspace
  -> route/customer mutation
```

不表达局部搜索、Tabu、LNS、列生成、CP-SAT 或最优性证明。路线访问序列使用 dense table 表达，因为插入位置会频繁变化，`position` 是当前 route sequence 的位置，不是 stable business identity。

## 3. Schema source 示例

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
    @SomaDefault("0")
    public long routeVersion;

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

## 4. 使用方式

- `Customer`、`Vehicle`、`Route` 是 keyed entity state；
- `TravelCost` 是 keyed lookup table，用于 `LocationPairKey -> distance/travel time`；
- `RouteVisitRow` 是 dense route sequence，不承诺 `position` 是 stable key；
- `UnassignedCustomerRow` 和 `InsertionCandidateRow` 是 dense workspace，通过 order access 支撑构造解选择；
- 上层 VRP constructor 负责容量、时间窗、候选生成和路线关闭策略。

Source-of-truth 口径：

- `RouteVisitRow.position` 是 route 当前访问顺序的事实源；
- `Customer.state` 和 `Customer.assignedRoute` 表达 customer 是否已经分配到某条 route；
- `Customer.assignedPosition` 如果保留，只是诊断 / snapshot 字段，不应作为 route sequence 的权威事实；
- `UnassignedCustomerRow` 是由 `Customer.state == UNASSIGNED` 派生出的 hot workspace / frontier view，constructor 必须在分配或跳过 customer 时同步删除或重建；
- `Route.routeVersion` 是 route sequence mutation epoch，每次 route visit segment rewrite 后递增；当前 dense workspace 默认每轮重建 candidate，通常不需要跨轮 stale candidate 校验，但如果某个实现保留候选行跨轮复用，必须把 route version 纳入校验。

`TravelCost` 在 canonical 示例中是 required lookup：构造 candidate 时访问到缺失 `LocationPairKey` 表示输入矩阵不完整，应暴露 typed missing key / required lookup error。若业务要把缺失 arc 表达为不可行候选或 fallback distance，必须由 VRP constructor 显式选择并写入场景契约，SOMA runtime 不猜测业务语义。

`InsertionCandidateRow.by_best_delta` 只服务当前 dense workspace 的 selection order。它不是全局策略排序承诺，也不表示 maintained order 与 dynamic `sorted(comparator)` 性能等价；是否保留该 order、改用 dynamic sort，或升级为 keyed insertion frontier，需要通过 benchmark 比较。

`rewriteRouteVisitsForInsertion(...)` 不是零成本 helper。一次插入至少会读取当前 route visits，构造插入后的 sequence，重写 position / arrival / departure / loadAfterVisit，并在保留 `Customer.assignedPosition` 时同步刷新受影响 customer 的诊断 snapshot。现有 V1 示例只承诺 whole-table rebuild 或 route-local rebuild 的业务边界，不承诺 route segment rewrite public API。

VRP constructor 拥有跨 table 一致性。`Customer`、`Route`、`RouteVisitRow`、`UnassignedCustomerRow`、`InsertionCandidateRow` 的提交序列没有 SOMA runtime transaction；中间失败时，constructor 必须停止构造、回滚外部 snapshot，或重建 derived workspace / candidate rows。
