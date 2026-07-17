# VRP 构造解 runtime state 示例

状态：正式设计文档
Owner：`soma-examples`
事实范围：VRP 构造解 data role、Access Pattern Card、schema 和使用边界
非事实范围：完整 VRP solver、public contract 和性能 claim
最后审查日期：2026-07-17

## 1. 文档定位

本文是 `VRP 构造解 runtime state 示例` 的独立场景文档。它从 [Runtime state schema 典型示例](runtime-state-schema-examples.md) 拆分而来，遵守该总览文档中的通用建模规则。

## 2. 场景边界

VRP 示例表达 greedy insertion / cheapest insertion 构造解过程中的 runtime state：

```text
vehicles / customers / travel cost lookup
  -> unassigned customer workspace
  -> per-route visit dense children
  -> insertion candidate workspace
  -> route/customer mutation
```

不表达局部搜索、Tabu、LNS、列生成、CP-SAT 或最优性证明。每条 `Route` 独占一个 `List<RouteVisitRow>` dense child，因为插入位置会频繁变化，`position` 是当前 route sequence 的位置，不是 stable business identity。Flat root-level `RouteVisitRow(routeId, position, ...)` 只作为相同语义的 benchmark baseline。

### 2.1 Access Pattern Card

| Core path | Cardinality/working set | Access/mutation mix | Allocation/evidence boundary |
|---|---|---|---|
| `Route.visits` child | route count × empty/typical/high visits | parent-key child scan、route-local rewrite/replace | child instance/small-array overhead 与 flat grouped baseline 同时计量 |
| insertion workspace | unassigned customers × considered routes × positions | per-round `replaceAll`、route exact-source、dynamic sort、first | builder、column rewrite、`IndexBuffer` sort scratch 和 capacity reuse 分开 |
| travel/unassigned state | location pairs、remaining customers | repeated point lookup、physical scan + explicit sort、swap-remove/rebuild | KeySpace load/collision、selector selectivity、compaction 和 preprojection amortization 分开 |

Fixture/benchmark 必须补充 route/global scan ratio、hot columns、touched bytes、mutation/read ratio、optional/child density、JIT warmup/forks、stats mode 和 export frequency；这些值不进入 Schema/hash。

## 3. Schema source 示例

```java
@SomaSchema(
    name = "vrp_construction_runtime_state",
    generatedPackage = "com.example.vrp.state.generated",
    version = "1"
)
package com.example.vrp.state;

import java.util.List;

public enum CustomerState {
    UNASSIGNED,
    ASSIGNED,
    SKIPPED
}

@SomaValue
public class CustomerId {
    @SomaField
    long value;
}

@SomaValue
public class VehicleId {
    @SomaField
    long value;
}

@SomaValue
public class RouteId {
    @SomaField
    long value;
}

@SomaValue
public class LocationId {
    @SomaField
    long value;
}

@SomaValue
public class LocationPairKey {
    @SomaField
    LocationId fromLocation;

    @SomaField
    LocationId toLocation;
}

@SomaTable(name = "customers", defaultCapacity = 4096)
@SomaIndex(name = "by_state", fields = {"state"})
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

    @SomaField
    @SomaOptional
    public RouteId assignedRoute;

    @SomaField
    @SomaOptional
    public Integer assignedPosition;

    @SomaField
    @SomaOptional
    public Long arrivalMinute;
}

@SomaTable(name = "vehicles", defaultCapacity = 512)
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

    @SomaChild(initialCapacity = 32)
    public List<RouteVisitRow> visits;
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

@SomaTable(name = "route_visit_rows", defaultCapacity = 32)
public final class RouteVisitRow {
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
@SomaIndex(name = "by_route", fields = {"routeId.value"})
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
- `Route.visits` 是 parent-owned dense route sequence，不承诺 child row 的 `position` 是 stable key；
- `UnassignedCustomerRow` 和 `InsertionCandidateRow` 是 dense workspace；route-local 候选先由 `by_route` exact source 收窄，再通过 `sorted(...)` 完成本轮选择；
- 上层 VRP constructor 负责容量、时间窗、候选生成和路线关闭策略。

Source-of-truth 口径：

- `Route.visits` 中 `RouteVisitRow.position` 是该 route 当前访问顺序的事实源；parent ownership 已确定归属，因此 child row 不重复保存 `routeId`；
- `Customer.state` 和 `Customer.assignedRoute` 表达 customer 是否已经分配到某条 route；
- `Customer.assignedPosition` 如果保留，只是诊断 / snapshot 字段，不应作为 route sequence 的权威事实；
- `UnassignedCustomerRow` 是由 `Customer.state == UNASSIGNED` 派生出的 hot workspace / frontier view，constructor 必须在分配或跳过 customer 时同步删除或重建；
- `Route.routeVersion` 是 route sequence mutation epoch，每次 route visit segment rewrite 后递增；当前 dense workspace 默认每轮重建 candidate，通常不需要跨轮 stale candidate 校验，但如果某个实现保留候选行跨轮复用，必须把 route version 纳入校验。

`TravelCost` 在 canonical 示例中是 required lookup：构造 candidate 时访问到缺失 `LocationPairKey` 表示输入矩阵不完整，应暴露 typed missing key / required lookup error。若业务要把缺失 arc 表达为不可行候选或 fallback distance，必须由 VRP constructor 显式选择并写入场景契约，SOMA runtime 不猜测业务语义。

Insertion candidate 的 best-delta 次序只属于当前 terminal：先从 `findByRoute(routeId)` 获取候选，再显式按 violation、distance、arrival 和 customer identity 调用 `sorted(comparator)`。SOMA 不维护全局业务顺序；如果将来需要跨轮复用候选，应另行设计 keyed insertion frontier、版本和失效策略。

`rewriteRouteVisitsForInsertion(...)` 不是零成本 helper。一次插入至少会读取当前 route child，构造插入后的 sequence，重写 position / arrival / departure / loadAfterVisit，并在保留 `Customer.assignedPosition` 时同步刷新受影响 customer 的诊断 snapshot。V1 使用 `routes.visits(routeId)` 定位 live child facade；child 内容 replacement 必须 staged/validated 后原子切换，失败时旧 child 保持不变。该示例不承诺零拷贝 route segment rewrite public API。

正式 smoke 的 `route-rewrite` 证据必须从已有2行的非空 route sequence 插入1个
customer并形成3行结果，同时证明插入点之后的原 row 从position 1移动到position 2、
arrival/departure/loadAfterVisit同步重写、`routeVersion`递增以及Customer诊断位置同步；
empty -> single-row initialization不能命名为route insertion/rewrite evidence。选择与局部验证
使用generated row locator和Value leaf ColumnView；递归`Route + List`只在export boundary执行。

VRP constructor 拥有跨 table 一致性。`Customer`、`Route` 及其 visits child、`UnassignedCustomerRow`、`InsertionCandidateRow` 的提交序列没有 SOMA runtime transaction；中间失败时，constructor 必须停止构造、回滚外部 snapshot，或重建 derived workspace / candidate rows。

`routes.fetch(routeId)` 会递归 materialize detached `Route + List<RouteVisitRow>`；hot-loop 局部扫描应优先使用 `routes.visits(routeId)`，避免为访问 live child 而构造完整 object/List graph。
