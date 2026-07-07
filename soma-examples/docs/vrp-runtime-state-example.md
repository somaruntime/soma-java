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
