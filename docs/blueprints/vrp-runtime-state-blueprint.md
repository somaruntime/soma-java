# VRP runtime state 蓝图

类型：Blueprint

状态：正式

Owner：VRP 目标场景

事实范围：VRP 构造解中 SOMA 的目标角色、使用者体验、数据分层与候选建模取舍

非事实范围：VRP 算法正确性、精确公共 API、当前实现状态、benchmark 结论和 release readiness

设计约束入口：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

最后审查日期：2026-07-20

目标约束：route/customer/candidate 的业务顺序由显式 `.sorted(totalComparator)` 或 application-owned 专用结构产生；`@SomaIndex` 只承担 always-current exact access，物理遍历顺序不构成业务契约。

## 1. 目标与适用范围

本文展示 greedy insertion / cheapest insertion VRP 构造解场景下 SOMA runtime state 的目标建模方式。

核心问题不是把 FJSP 的 `MachineCandidate` 机械复制到 VRP，而是判断何时使用 dense `InsertionCandidateRow` workspace，何时才应升级为增量维护的 keyed insertion frontier。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论构造解 runtime state，不讨论局部搜索、Tabu、LNS、列生成、CP-SAT 或最优性证明；
- SOMA 保存 hot runtime state，VRP constructor 拥有插入策略、容量和时间窗规则、跨 table 一致性和失败处理；
- 本文定义目标使用形态，不是精确 schema/API contract；代码片段用于表达使用者意图。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime storage。Materializing API/terminal 直接返回 schema class 或 `List`/`Map`；Row Pipeline callback 参数仍是 callback-scoped Row Cursor。`@SomaValue` 由 compiler 提供 immutable value semantics。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化。

## 2. 目标数据角色与 Table 形态

| Table | 目标形态 | 生命周期责任 | 主要访问方式 |
|---|---|---|---|
| `CustomerDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(customerId)`、due/input 显式排序 |
| `VehicleDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(vehicleId)`、按 vehicle id 显式排序 |
| `Route` | keyed working/result fact | 构造过程中持续 mutation | `fetch(routeId)`、`mutate(routeId)`、`by_vehicle` |
| `TravelCost` | keyed lookup data | 导入后只读 lookup | `fetch(locationPair)` |
| `RouteVisitRow` | per-route dense child `List<RouteVisitRow>` | route 当前访问序列 | parent key + child-local `.rows().sorted(byPosition)` |
| `CustomerAssignment` | keyed result fact | row absence 表示尚未分配 | `fetch(customerId)`、`by_route` |
| `UnassignedCustomerRow` | dense rebuildable workspace | definition/assignment 差集的热视图 | `.rows().sorted(byDueThenInput)` |
| `InsertionCandidateRow` | dense workspace，满足采用条件时可换为 keyed frontier | 可重建候选状态 | `.rows().sorted(byBestDelta)` 或 grouped exact access |

`RouteVisitRow` 使用 dense table 是合理的：`position` 是当前 route sequence 中的位置，不是 stable business identity；插入会导致后续 position 大量变化，用 keyed table 反而会把 row identity 和位置维护复杂化。

路线顺序的事实源是 `RouteVisitRow.position`。`CustomerAssignment.routeId` 表示 customer 已分配到哪条 route；`assignedPosition` 若作为诊断 / snapshot 字段存在，必须在 route segment rewrite 后同步重写，否则会和 `RouteVisitRow.position` drift。

### 2.1 Application data role 边界

| Data role | Table / field group | 权威性与生命周期 | 优化判断 |
|---|---|---|---|
| input facts | `CustomerDefinition`、`VehicleDefinition`、`TravelCost` | import 后 authoritative、read-only/read-mostly | 与 assignment/route progress 分开，candidate build 只读取必要字段 |
| working state | `Route`、`RouteVisitRow` | constructor 持续修改的 authoritative current solution | 同时也是最终 route result 的来源；不另建内容相同的 `RouteResult` shadow table |
| working state | `UnassignedCustomerRow` | 可由 customer definitions - assignments 重建的 dense workspace | 不是 customer assignment 的第二事实源；失败时允许重建 |
| working state | `InsertionCandidateRow` 或可选 keyed frontier | 可重建 candidate state | stale guard、invalidation 和 rebuild 规则必须明确 |
| result facts | `CustomerAssignment` + current route sequence | assignment 独立 identity；route visits 在构造中增量形成 | assignment 由独立 keyed table 唯一持有；route/result 直接从 current solution 导出 |

目标模型不把 customer 输入、assignment result 和 unassigned workspace 混在一个 row 中，避免形成多份“是否已分配”表达：

```text
CustomerDefinition     // input facts
CustomerAssignment     // result fact; row absence means unassigned
UnassignedCustomerRow  // rebuildable working workspace, never authoritative
```

`Route` 和 `RouteVisitRow` 则不机械拆出 result table：它们在 cheapest insertion 中既是下一轮评分依赖的 current solution，也是最终路线结果的唯一事实源，co-location 具有明确的一致性和局部性理由。Boundary exporter 在 solve 完成后 materialize/map current route facts，不复制另一套 `RouteResult`。

### 2.2 Access Pattern Card

以下 card 是 scenario/runtime-plan input，不进入 Schema/hash；benchmark 必须给出实际 scale、selectivity、working set 和 frequency。

| Table / phase | Rows/cardinality | Hot columns | Access / mutation mix | Locality / allocation boundary |
|---|---|---|---|---|
| `Route.visits` dense child | route count × empty/typical/high visits per route | `position`、customer/location、arrival/departure/load | scoring 时 parent-key child-local scan；commit 时 route-local rewrite/replace | 记录 child instance count/small-array overhead；与 flat grouped-index/filter baseline 比较 |
| `InsertionCandidateRow` dense workspace | unassigned customers × considered routes × positions | feasibility、delta、arrival、penalty、tie-break | 每轮 `replaceAll` + explicit dynamic sort + first | builder、column rewrite、sort scratch reuse 和 allocation/op 分开；不把 pathing/scoring 算法归因于 runtime |
| optional keyed insertion frontier | affected route/customer/position/version pairs | key/version、indicator fields | incremental add/update/remove + grouped lookup | 仅在跨轮 reuse 和局部 invalidation 成立时测；记录 mutation/read ratio 与 stale cleanup |
| `TravelCost` | location-pair facts | key leaves、distance/time | candidate scoring 中高频 random point lookup | 记录 load/collision、lookup reuse、preprojection build/amortization；comparator 内禁止 lookup |
| `UnassignedCustomerRow` | remaining unassigned customers | due/input/demand | explicit sorted selection、remove/rebuild | 作为 rebuildable workspace；记录 swap-remove、dynamic-sort scratch 和 capacity retention |

必须额外记录 route/global scan ratio、selector cardinality/selectivity、visits working-set bytes、candidate scratch high-water、stats mode 和 materialization/export frequency。

## 3. 主 hot loop 拆解

典型 cheapest insertion 构造解可以拆成：

```text
initialize customers / vehicles / routes / travel_costs
  -> initialize unassigned_customer_rows
  -> initialize empty route_visit_rows
  -> repeat until no unassigned customers:
       choose candidate generation scope
       compute insertion indicators
       publish candidate rows
       select best candidate
       commit route insertion
       update Customer / Route / RouteVisitRow / UnassignedCustomerRow
       invalidate or recompute affected candidates
```

如果每轮都执行：

```java
insertionCandidates.replaceAll(buildAllInsertionCandidates());
InsertionCandidateRow chosen = insertionCandidates.rows()
    .sorted(byBestDeltaComparator).firstOrThrow();
```

这一行会隐藏多类成本：

- 遍历所有未分配 customer；
- 遍历所有 route；
- 遍历每条 route 的所有 insertion position；
- 多次读取 `TravelCost`；
- 计算 capacity、arrival、waiting、time window penalty；
- 构造 batch row；
- 为本轮 candidate sequence 执行显式 dynamic sort；
- 只为了选一个候选，却重建了全局候选集合。

因此 VRP 应按规模和变更局部性分两种方案。

## 4. 生命周期判断

### 4.1 保留 dense workspace 的条件

当满足以下条件时，`InsertionCandidateRow` 作为 dense workspace 仍然是好设计：

- 实例规模较小；
- 每轮策略确实需要全局重算所有 feasible insertion；
- time window propagation 导致一次插入会影响多数 route/customer pair；
- candidate row 不需要跨轮按 stable key 删除或更新；
- 主要收益来自 packed scan、batch `replaceAll`、capacity reuse 和 table-local sort scratch。

这时 `replaceAll(batch)` 是 dense table 的目标使用边界。

### 4.2 升级为 keyed insertion frontier 的条件

当满足以下条件时，应考虑引入 keyed runtime frontier：

- 每次 commit 只影响一条 route 或少量 route；
- 大量未分配 customer 对其他 route 的 candidate score 不变；
- 需要按 route、customer 或 route-position 局部删除 / 更新候选；
- 每轮全量 `replaceAll(buildAllInsertionCandidates())` 成为热点；
- 每轮 candidate dynamic sort 成为主要成本，且局部 invalidation 已经足够稳定。

推荐的 frontier identity 可以是：

```text
(RouteId, CustomerId, InsertAfterPosition, RouteVersion)
```

其中 `RouteVersion` 或等价字段非常关键。VRP 的 `insertAfterPosition` 不是 stable identity；一次插入会移动后续 position。如果没有 route version，旧 candidate row 可能仍然指向过期位置。

### 4.3 `RouteVisitRow`：flat dense 与 parent-owned dense child

`RouteVisitRow` 没有 stable business identity，并且从领域生命周期看，一条 visit row 只属于一条 `Route`。当主访问模式是“`RouteId` + 遍历/替换该 route 的 visits”时，它符合 keyed parent -> dense child 的 ownership 模型：

```text
Route keyed parent row
  -> required visits child table
       -> RouteVisitRow(position, customerId, arrivalMinute, ...)
```

概念 schema 可以表达为：

```java
@SomaTable(name = "routes", defaultCapacity = 512)
public final class Route {
    @SomaKey
    public RouteId routeId;

    // 其余 Route scalar/value fields 省略。

    @SomaChild(initialCapacity = 32)
    public List<RouteVisitRow> visits;
}

@SomaTable(name = "route_visit_rows", defaultCapacity = 32)
public final class RouteVisitRow {
    @SomaField
    public int position;

    @SomaField
    public CustomerId customerId;

    // arrival/departure/load fields 省略。
}
```

这里的 `List<RouteVisitRow>` 明确表示 dense child ownership；runtime parent row 只保存 child handle，不保存 Java `List`。Detached `Route` materialization 才递归构造 caller-owned `List<RouteVisitRow>`。

如果采用 child 方案：

- `RouteVisitRow.routeId` 从 child row 中删除，parent ownership 已确定其归属；
- child 内业务顺序由 `position` 和完整 tie-break 的显式 comparator 产生，不再把 `routeId` 放入排序条件；
- required child 初始是 logical empty，storage lazy allocation；
- route-local rewrite 使用 detached child Batch staged/validated 后替换 child 内容；失败时旧 child 保持不变；
- 删除、clear 或 release parent 时级联 release visits subtree；
- 不允许把一个 live visits child attach 给另一条 Route；跨 route 移动 customer 表达为旧 child 删除数据、新 child 构造数据，不是 reparent child instance；
- 跨 `Route`、`Customer`、`UnassignedCustomerRow` 和 candidate table 的业务提交仍不具备 runtime transaction。

`routes.fetch(routeId)` 会完整递归 materialize `Route + List<RouteVisitRow>`。候选评分 hot loop 只读取 `routeVersion/load` 并遍历 live visits 时，不应被迫构造完整 detached object graph，因此 generated API 必须同时提供 `routes.visits(routeId)` 这类按 parent key 定位 live child facade 的入口。该入口不 materialize parent/child object graph，callback 仍使用 child Row Cursor。

两种方案必须在相同语义下比较：

- flat：全局 dense `RouteVisitRow(routeId, position, ...)` + exact-group lookup/filter + explicit sort；
- child：per-route dense child + parent-key child access；
- 分别记录 route-local scan/rewrite、global all-route scan、exact-index maintenance、sort scratch、allocation、recursive object/List export 和 `MaterializationBudget`；
- 使用 benchmark contract 的 `child_locality.parent_scan` / `child_locality.flat_filter`，不能仅凭局部性直觉声明性能优势。

## 5. 场景对 Design 的压力

主要风险：

- **全量 candidate rebuild**：复杂度接近 `unassignedCustomerCount * routeCount * averageRouteLength`，如果每轮只 commit 一个 customer，会产生大量重复计算；
- **隐藏 cross-table lookup**：candidate build 中频繁读取 `Customer`、`Route`、`RouteVisitRow`、`TravelCost`，不能放在 comparator 内；
- **重复 builder 构造**：如果每轮构造完整 batch，Java 对象分配和 field copy 可能盖过 columnar runtime 收益；
- **业务顺序误用**：每轮 `replaceAll` 后的 dynamic sort 是显式策略成本，不能把它伪装成 Table 自动维护或物理顺序；
- **route position stale**：dense `RouteVisitRow.position` 是当前位置，structural mutation 后旧 row index 和旧 position 都不能作为长期引用；
- **TravelCost lookup 语义隐藏**：`scoreInsertion(...)` 必须明确 missing pair 是 typed required lookup error、不可行候选，还是 fallback distance；不能由 SOMA runtime 猜；
- **indicator 粒度过粗**：一次性计算所有 delta、arrival、capacity、penalty，会掩盖哪些指标能在 route/customer 级缓存。

## 6. 可选 keyed frontier 的采用条件

小规模或全局重算场景以 dense workspace 为默认目标。`InsertionCandidateRow` 每轮通过 `replaceAll(batch)` 发布 candidate workspace；`by_best_delta` 只是本轮显式 comparator，不进入 Schema。

本节 schema 采用 per-route dense child 作为推荐形态；flat root-level `RouteVisitRow(routeId, position, ...)` 只保留为 benchmark baseline。

只有在全量 rebuild 已经被 benchmark 证明是瓶颈，且候选确实需要跨轮次保留、按 route/customer 局部删除和按 route version 失效时，才考虑下面的 keyed frontier 方案。

**本节是条件式目标方案，不替换默认的 dense `InsertionCandidateRow` workspace。**

```java
@SomaSchema(
    name = "vrp_construction_runtime_state",
    generatedPackage = "com.example.vrp.state.generated",
    version = "1"
)
package com.example.vrp.state;

@SomaValue
public class InsertionCandidateKey {
    @SomaField
    RouteId routeId;

    @SomaField
    CustomerId customerId;

    @SomaField
    int insertAfterPosition;

    @SomaField
    long routeVersion;
}

@SomaTable(name = "customer_assignments", defaultCapacity = 4096)
@SomaIndex(name = "by_route", fields = {
    "routeId.value"
})
public final class CustomerAssignment {
    @SomaKey
    public CustomerId customerId;

    @SomaField
    public RouteId routeId;

    @SomaField
    public long arrivalMinute;
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

@SomaTable(name = "insertion_candidates", defaultCapacity = 16384)
@SomaIndex(name = "by_route", fields = {
    "candidateKey.routeId.value"
})
@SomaIndex(name = "by_customer", fields = {
    "candidateKey.customerId.value"
})
public final class InsertionCandidate {
    @SomaKey
    public InsertionCandidateKey candidateKey;

    @SomaField
    public long baseDeltaDistanceMeters;

    @SomaField
    public long projectedArrivalMinute;

    @SomaField
    public int projectedLoad;

    @SomaField
    public boolean capacityFeasible;

    @SomaField
    public long timeWindowPenalty;

    @SomaField
    public long violationPenalty;

    @SomaField
    public boolean indicatorReady;
}
```

说明：

- 上面的 `InsertionCandidate` 是增量 frontier 候选方案，不是默认形态；
- `InsertionCandidateKey` 的身份只在同一个 `RouteVersion` epoch 内有效，不是跨整个求解生命周期稳定的 business identity；
- `InsertionCandidate` 不声明 `by_best_delta` order。候选的策略排序属于 constructor 策略层，可通过 frontier 的 dynamic sort 完成；
- 草案不保留单独 `dispatchScore`。本蓝图选择保留 comparator 实际使用的 indicator 字段，避免把某个策略 score 固化成 schema 事实；
- 当 benchmark 证明全局 best candidate 需要跨轮次维护时，应评估 application-owned priority queue，而不是恢复 Table maintained order；
- `RouteVersion` 是 stale candidate 防线，避免旧 position 在 route mutation 后继续有效；
- `RouteVisitRow` 在推荐方案中是 per-route dense child；flat root table 只作为相同语义 benchmark baseline。Candidate frontier 决策不自动改变 visit ownership。

## 7. 推荐的 Java 8 SOMA API 使用流程

### 7.1 初始化

```java
void initializeRuntime(ProblemInput input) {
    customerDefinitions.addBatch(buildCustomerDefinitionBatch(input));
    vehicleDefinitions.addBatch(buildVehicleDefinitionBatch(input));
    routes.addBatch(buildRouteBatch(input));
    travelCosts.addBatch(buildTravelCostBatch(input));

    customerAssignments.clear();
    unassignedCustomerRows.replaceAll(buildUnassignedBatch(input));
    insertionCandidates.clear();
}
```

### 7.2 发布或刷新受影响 route 的候选

```java
void refreshInsertionCandidatesForRoute(RouteId routeId) {
    Route route = routes.fetch(routeId);
    long routeVersion = route.routeVersion;

    insertionCandidates.findByRoute(routeId).remove();

    CandidateBatch batch = insertionCandidates.newBatch();

    unassignedCustomerRows.rows().sorted(byDueThenInputComparator)
        .forEach(u -> {
            CustomerDefinition customer = customerDefinitions.fetch(u.customerId());

            for (RouteVisitRow visit : route.visits) {
                    int insertAfter = visit.position;
                    InsertionScore score = scoreInsertion(route, customer, insertAfter);
                    if (!score.feasibleByTravelCost()) {
                        return;
                    }

                    InsertionCandidateKey key = new InsertionCandidateKey(
                        routeId,
                        customer.customerId,
                        insertAfter,
                        routeVersion
                    );

                    batch.add()
                        .setCandidateKey(key)
                        .setBaseDeltaDistanceMeters(score.deltaDistanceMeters())
                        .setProjectedArrivalMinute(score.projectedArrivalMinute())
                        .setProjectedLoad(score.projectedLoad())
                        .setCapacityFeasible(score.capacityFeasible())
                        .setTimeWindowPenalty(score.timeWindowPenalty())
                        .setViolationPenalty(score.violationPenalty())
                        .setIndicatorReady(false);
            }
        });

    insertionCandidates.addBatch(batch);
}
```

注意：上面是目标流程草案。同一 table 的 Row Pipeline callback 内不得对该 table 做 structural mutation；`findByRoute(routeId).remove()` 必须在读取和 batch add 之前完成。需要嵌套访问时，应先复制必要的候选信息，或使用 ColumnView / application loop 分阶段遍历。

`scoreInsertion(...)` 不能隐藏业务语义。它至少应显式读取 predecessor / successor location，并对每个 `TravelCost.fetch(locationPair)` 缺失采用固定策略：

- required lookup missing：抛 typed runtime error，中止本次构造；
- infeasible candidate：该 insertion 不进入 batch；
- fallback distance：由 VRP constructor 提供确定性 fallback，SOMA runtime 不参与猜测。

上述三种策略只能选一种作为场景契约，不能在 comparator 或 runtime core 中临时决定。

### 7.3 分阶段计算 indicator

```java
void updateCandidateIndicators() {
    insertionCandidates
        .filter(c -> !c.indicatorReady())
        .update(c -> {
            long violation = c.capacityFeasible()
                ? c.timeWindowPenalty()
                : CAPACITY_VIOLATION_PENALTY + c.timeWindowPenalty();

            c.setViolationPenalty(violation);
            c.setIndicatorReady(true);
        });
}
```

### 7.4 选择与提交

```java
InsertionCandidate chosen = insertionCandidates
    .filter(c -> c.indicatorReady())
    .sorted((a, b) -> {
        int byViolation = Long.compare(a.violationPenalty(), b.violationPenalty());
        if (byViolation != 0) {
            return byViolation;
        }

        int byDelta = Long.compare(a.baseDeltaDistanceMeters(), b.baseDeltaDistanceMeters());
        if (byDelta != 0) {
            return byDelta;
        }

        int byArrival = Long.compare(a.projectedArrivalMinute(), b.projectedArrivalMinute());
        if (byArrival != 0) {
            return byArrival;
        }

        return Long.compare(
            a.candidateKey().customerId().value(),
            b.candidateKey().customerId().value()
        );
    })
    .firstOrThrow();

commitInsertion(chosen);
```

### 7.5 Commit insertion

```java
enum CommitResult {
    COMMITTED,
    STALE
}

CommitResult commitInsertion(InsertionCandidate chosen) {
    InsertionCandidateKey key = chosen.candidateKey;
    Route route = routes.fetch(key.routeId);

    if (route.routeVersion != key.routeVersion) {
        insertionCandidates.findByRoute(key.routeId).remove();
        refreshInsertionCandidatesForRoute(key.routeId);
        return CommitResult.STALE;
    }

    rewriteRouteVisitsForInsertion(
        key.routeId,
        key.customerId,
        key.insertAfterPosition
    );

    CustomerAssignmentBatch assignmentBatch = customerAssignments.newBatch();
    assignmentBatch.add()
        .setCustomerId(key.customerId)
        .setRouteId(key.routeId)
        .setArrivalMinute(chosen.projectedArrivalMinute);
    customerAssignments.addBatch(assignmentBatch);

    routes.mutate(key.routeId)
        .setLoad(chosen.projectedLoad)
        .setTotalDistanceMeters(
            route.totalDistanceMeters + chosen.baseDeltaDistanceMeters
        )
        .setRouteVersion(route.routeVersion + 1L)
        .commit();

    unassignedCustomerRows
        .filter(u -> u.customerId().equals(key.customerId))
        .remove();

    insertionCandidates.findByCustomer(key.customerId).remove();
    refreshInsertionCandidatesForRoute(key.routeId);

    return CommitResult.COMMITTED;
}
```

`firstOrThrow()` 和 `fetch(...)` 都返回 detached schema object。`routes.fetch(routeId)` 会递归物化完整 `Route.visits` List，因此上面的 reference code 以可读性为主；route-scoring hot path 应改用 parent-key live child facade 和 Row Cursor。`@SomaTable` row 不生成 structural equality/hash；代码中的 `CustomerId` / candidate key 比较依赖 immutable `@SomaValue` equality。

`rewriteRouteVisitsForInsertion(...)` 不是一个可以忽略成本的 helper。它至少包含：

```text
routes.visits(routeId).rows().sorted(byPositionComparator)
  -> 读取当前 route visits
  -> 构造插入后的新 sequence
  -> 重写 position / arrival / departure / loadAfterVisit
  -> 使用 child Batch 批量替换该 route 的 visits child rows
  -> 如保留 Customer.assignedPosition 诊断字段，则同步重写受影响 customer
```

目标 generated API 必须能够用 route-level batch rebuild 或外部 sequence builder 后 `replaceAll` 表达受控 rewrite，不能假设存在未设计的高效 row move。若要新增 segment rewrite API，必须先进入对应 Design Owner。

调用方不能把 `CommitResult.STALE` 当作 successful commit。主循环应在 `STALE` 时 retry selection；否则会出现无进展计数、重复 stale candidate 或错误跳过 customer。

跨 table commit 的一致性由 VRP constructor 拥有。SOMA V1 不提供跨 table transaction；如果中间失败，constructor 必须有清晰的停止、补偿或重建 frontier 策略。

`CustomerAssignment` 是 customer assignment 的唯一 result fact。`UnassignedCustomerRow` 只是从 definition/assignment 差集构造的 hot workspace；如果 assignment 已写入但 workspace remove 失败，constructor 应停止本轮并重建 unassigned/candidate workspace，而不是回写另一份 `Customer.state` 作为补偿。

## 8. Cache 友好性分析

保留 dense workspace 的 cache 友好性来自：

- candidate rows 连续写入；
- `replaceAll(batch)` 复用 capacity；
- candidate business sequence 来自 explicit dynamic sort；
- 可用 dynamic sort / top-k IndexBuffer 完成当前轮选择；
- 适合规模较小且全局重算不可避免的情况。

keyed insertion frontier 的 cache 友好性来自：

- route/customer/position 候选跨轮保留，避免全量重建；
- 只删除和重算受影响 route 或 selected customer 的候选；
- indicator 更新集中在 frontier rows，comparator 只读取 candidate row 字段；
- `TravelCost` lookup 发生在 indicator build 阶段，不发生在排序 comparator 中。

潜在代价：

- keyed frontier 引入 primary locator、secondary exact index 和更多增量维护；
- `findByRoute(routeId).remove()` 与 `addBatch` 会导致结构性 mutation；
- 如果每次插入实际影响大部分 route，增量 frontier 可能比 dense full rebuild 更慢；
- dynamic sort 使用 table-local `IndexBuffer`；application heap 是另一种跨轮次维护结构，两者必须按同语义分别测量。

因此选择具体形态前必须通过同语义 benchmark lane 比较：

- dense `replaceAll + rows().sorted(byBestDelta).firstOrThrow()`；
- 不固化 order 的 dense `replaceAll + dynamic sort / top-k`；
- keyed frontier `findByRoute/remove + addBatch + dynamic sorted`；
- 极端 hot path 下的 ColumnView / primitive loop；
- keyed pair `TravelCost.fetch(locationPair)`；
- route-local cached neighbor cost；
- dense / matrix distance row；
- ColumnView primitive distance scan。

## 9. 目标形态必须处理的边界

- 必须明确 dense workspace 与 keyed frontier 的选择边界，避免把 `replaceAll` 或 keyed frontier 绝对化；
- child-local live access 例如 `routes.visits(routeId).rows().sorted(byPosition)` 对 VRP 很关键，generated parent-key child API 命名需要 golden 固化；
- Row Pipeline 不支持同 table callback 内 structural mutation，这会影响 route sequence 插入和候选刷新，需要示例明确分阶段；
- `RouteVisitRow` dense sequence 的插入需要受控的 route-local rewrite；不能假设存在未设计的高效 row move；
- `RouteVisitRow.position` 是 route 当前顺序事实源；`Customer.assignedPosition` 如果保留，只能作为诊断 / snapshot 字段并由 constructor 同步重写；
- missing `TravelCost` 的语义必须由业务定义：typed required lookup error、不可行候选，或 fallback distance，不能由 SOMA runtime 猜测。

## 10. 目标决策与证明义务

### 10.1 目标决策

- 默认使用 dense `InsertionCandidateRow` workspace；只有稳定 identity、跨轮复用和局部失效同时成立时才采用 keyed frontier；
- `RouteVisitRow` 保持 dense route sequence；flat root 与 parent-owned child 是 locality/ownership 取舍，不与 candidate frontier 决策绑定；
- `CustomerDefinition`、`CustomerAssignment` 和 rebuildable `UnassignedCustomerRow` 分离；Route/RouteVisit current solution 同时作为最终 route result，不复制 shadow result；
- comparator 只读取 candidate row 字段；`RouteVersion` 防止 stale position candidate；`CommitResult.STALE` 不计作成功提交；
- 跨 table 一致性、missing `TravelCost` 语义和失败恢复由 VRP constructor 拥有。

### 10.2 采用前证明义务

- 固化 route-local rewrite 和 parent-key live child access 的 generated API 语义；
- 在相同语义下比较 flat dense visits 与 per-route dense child 的 locality、global scan、rewrite、allocation 和 recursive materialization 成本；
- 比较 dense rebuild 与 keyed frontier 在不同规模、mutation/read ratio 和 invalidation 局部性下的 crossover；
- 验证 `InsertionCandidateKey` 的 stale cleanup、`indicatorReady` 生命周期和 exact-index 增量维护成本；
- 为 `TravelCost.fetch(locationPair)` 选择唯一业务语义，并验证 customer definition/assignment/workspace 分离后的 commit 与 recovery。
