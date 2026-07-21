# VRP runtime state 蓝图

类型：Blueprint

状态：正式

Owner：VRP 目标场景

事实范围：VRP 构造解中 SOMA 的目标角色、使用者体验、数据分层与候选建模取舍

非事实范围：VRP 算法正确性、精确公共 API、当前实现状态、benchmark 结论和 release readiness

设计约束入口：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Table、存储与访问](../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

最后审查日期：2026-07-21

目标约束：route/customer/candidate 的业务顺序由显式 `.sorted(totalComparator)` 或 application-owned 专用结构产生；`@SomaIndex` 只承担 always-current exact access，物理遍历顺序不构成业务契约。

## 1. 目标与适用范围

本文展示 greedy insertion / cheapest insertion VRP 构造解场景下 SOMA runtime state 的目标建模方式。

核心问题不是把 FJSP 的 `MachineCandidate` 机械复制到 VRP，而是判断何时使用 dense `InsertionCandidateRow` workspace，何时才应升级为增量维护的 keyed insertion frontier。

适用边界：

- 只讨论 Java 8 generated table / Row Pipeline / ColumnView 使用方式；
- 只讨论构造解 runtime state，不讨论局部搜索、Tabu、LNS、列生成、CP-SAT 或最优性证明；
- SOMA 保存 hot runtime state，VRP constructor 拥有插入策略、容量和时间窗规则、跨 table 一致性和失败处理；
- 本文定义目标使用形态，不是精确 schema/API contract；未标为算法伪代码的片段按目标 Java 8 使用代码审查，允许省略 import、外围 owner 和领域 helper，但必须把 insertion position、可行性、时间单位、total comparator 与跨表失败边界表达完整。

Canonical CVRPTW 示例统一使用 meter、second 和 non-negative integral load/capacity；input loader 在进入 constructor 前验证单位、非负性、time-window 关系与 checked-arithmetic 上界。其他单位制必须整体替换字段名与 adapter，不能在同一 runtime state 中混用 minute/second 或 distance/cost。

本蓝图中的 `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime storage。Materializing API/terminal 直接返回 schema class 或 `List`/`Map`；Row Pipeline callback 参数仍是 callback-scoped Row Cursor。`@SomaValue` 由 compiler 提供 immutable value semantics。SOMA ownership aggregate 只允许单线程同步访问，不提供并发访问、跨 table transaction、序列化或持久化。

## 2. 目标数据角色与 Table 形态

| Table | 目标形态 | 生命周期责任 | 主要访问方式 |
|---|---|---|---|
| `CustomerDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(customerId)`、due/input 显式排序 |
| `VehicleDefinition` | keyed input fact | import 后 authoritative、read-only | `fetch(vehicleId)`、按 vehicle id 显式排序 |
| `Route` | keyed working/result fact | 构造过程中持续 mutation | `fetch(routeId)`、`mutate(routeId)`、每 vehicle 唯一 active route access |
| `TravelCost` | keyed lookup data | 导入后只读 lookup | `fetch(locationPair)` |
| `RouteVisitRow` | per-route dense child `List<RouteVisitRow>` | route 当前访问序列 | parent key + child-local `.rows().sorted(byPosition)` |
| `CustomerAssignment` | keyed result fact | row absence 表示尚未分配 | `fetch(customerId)`；只有 route-scoped result query 稳定高频时才声明 `by_route` |
| `UnassignedCustomerRow` | dense rebuildable workspace | definition/assignment 差集的热视图 | `.rows().sorted(byDueThenInput)` |
| `InsertionCandidateRow` | dense workspace，满足采用条件时可换为 keyed frontier | 可重建候选状态 | `.rows().sorted(byBestDelta)` 或 grouped exact access |

`RouteVisitRow` 使用 dense table 是合理的：`position` 是当前 route sequence 中的位置，不是 stable business identity；插入会导致后续 position 大量变化，用 keyed table 反而会把 row identity 和位置维护复杂化。

每次 route-local rewrite 必须在 publish 前验证 `position` 恰好为连续、唯一的 `[0, visitCount)`，并验证 customer 不重复、arrival/departure/load propagation 与 vehicle depot 边界一致。该业务 invariant 由 constructor 负责；它不是依赖物理 Index 偶然连续来成立。

路线顺序的事实源是 `RouteVisitRow.position`。`CustomerAssignment.routeId` 只表示 customer 已分配到哪条 route；canonical live model 不再复制 `assignedPosition`，需要位置时从当前 route visits 派生，避免每次 segment rewrite 同步另一份位置事实。

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
| `InsertionCandidateRow` dense workspace | unassigned customers × considered routes × insertion ordinals | route/customer/ordinal/version、delta、arrival/load/duration | 每轮 `replaceAll` + explicit dynamic sort + first | builder、column rewrite、sort scratch reuse 和 allocation/op 分开；不把 routing/scoring 算法归因于 runtime |
| optional keyed insertion frontier | affected route/customer/ordinal/version pairs | key/version、score fields | incremental add/update/remove + grouped lookup | 仅在跨轮 reuse 和局部 invalidation 成立时测；记录 mutation/read ratio 与 stale cleanup |
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
       compute complete feasible insertion scores
       publish candidate rows
       select best candidate
       commit route insertion
       update CustomerAssignment / Route / RouteVisitRow / UnassignedCustomerRow
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
- 计算 capacity、arrival、waiting 和完整 time-window propagation；
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
(RouteId, CustomerId, InsertionOrdinal, RouteVersion)
```

`insertionOrdinal` 的合法范围是 `[0, visitCount]`：`0` 表示 depot 与首个 visit 之间，`visitCount` 表示末尾 visit 与 depot 之间，空 route 唯一合法值也是 `0`。它不是 absence sentinel，而是当前 route version 内明确定义的 edge ordinal。`RouteVersion` 同样不可缺少；一次插入会改变后续 ordinal，如果没有 version，旧 candidate row 可能仍然指向过期 edge。

### 4.3 `RouteVisitRow`：flat dense 与 parent-owned dense child

`RouteVisitRow` 没有 stable business identity，并且从领域生命周期看，一条 visit row 只属于一条 `Route`。当主访问模式是“`RouteId` + 遍历/替换该 route 的 visits”时，它符合 keyed parent -> dense child 的 ownership 模型：

```text
Route keyed parent row
  -> required visits child table
       -> RouteVisitRow(position, customerId, arrivalSecond, ...)
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

    @SomaField
    public LocationId locationId;

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
- **route position stale**：任何来源 Table mutation/lifecycle change 后旧 Index 都失效；route rewrite 后旧 `RouteVisitRow.position` 也只描述旧 route version，二者都不能作为长期 identity；
- **TravelCost lookup 语义隐藏**：canonical input 的 required directed pair 若在运行中 missing，必须按 malformed problem fail closed；不可达 arc、sparse graph 或 shortest-path fallback 只能进入另一个显式 application model；
- **score 粒度过粗**：一次性计算所有 delta、arrival、capacity 和 suffix propagation，会掩盖哪些量能在 route/customer 级安全缓存。

## 6. 默认 schema 与可选 keyed frontier

小规模或全局重算场景以 dense workspace 为默认目标。`InsertionCandidateRow` 每轮通过 `replaceAll(batch)` 发布 candidate workspace；`by_best_delta` 只是本轮显式 comparator，不进入 Schema。

本节 schema 采用 per-route dense child 作为推荐形态；flat root-level `RouteVisitRow(routeId, position, ...)` 只保留为 benchmark baseline。

只有在全量 rebuild 已经被 benchmark 证明是瓶颈，且候选确实需要跨轮次保留、按 route/customer 局部删除和按 route version 失效时，才把默认 dense row 换成后面的 keyed frontier。

```java
@SomaSchema(
    name = "vrp_construction_runtime_state",
    generatedPackage = "com.example.vrp.state.generated",
    version = "1"
)
package com.example.vrp.state;

@SomaTable(name = "customer_assignments", defaultCapacity = 4096)
public final class CustomerAssignment {
    @SomaKey
    public CustomerId customerId;

    @SomaField
    public RouteId routeId;
}

@SomaTable(name = "routes", defaultCapacity = 512)
@SomaUnique(name = "by_vehicle", fields = {"vehicleId.value"})
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
    public LocationId locationId;

    @SomaField
    public long arrivalSecond;

    @SomaField
    public long departureSecond;

    @SomaField
    public int loadAfterVisit;
}

@SomaTable(name = "insertion_candidate_rows", defaultCapacity = 16384)
public final class InsertionCandidateRow {
    @SomaField public RouteId routeId;
    @SomaField public CustomerId customerId;
    @SomaField public int insertionOrdinal;
    @SomaField public long routeVersion;
    @SomaField public long deltaDistanceMeters;
    @SomaField public long projectedArrivalSecond;
    @SomaField public int projectedLoad;
    @SomaField public long projectedTotalDurationSeconds;
}
```

Canonical CVRPTW journey 把 capacity 和 time window 当作 hard constraint：candidate builder 只发布已完成完整 propagation 且可行的 row。若某个产品采用 soft constraint，penalty 必须作为该产品明确命名、带单位且进入 total comparator 的字段，不能复用一个含义模糊的 `violationPenalty`。

`RouteVisitRow.locationId` 是从 immutable `CustomerDefinition` 预投影到 current route sequence 的只读 leaf，用于避免评分时为每个 predecessor/successor 重复 materialize customer；插入时必须与 customer definition 校验一致，之后不能独立修改成第二份 location 事实。

这里假设每个 vehicle 在当前 solve 中只有一条 active route，因此 `by_vehicle` 是 `@SomaUnique`。允许同一 vehicle 多 trip/route 的模型必须改用 `@SomaIndex`，并把 trip identity 纳入业务规则；不能保留 unique 声明后依赖插入冲突控制业务流程。

条件式 keyed frontier 只替换 candidate table；其他事实与 ownership 不变：

```java
@SomaValue
public class InsertionCandidateKey {
    @SomaField RouteId routeId;
    @SomaField CustomerId customerId;
    @SomaField int insertionOrdinal;
    @SomaField long routeVersion;
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
    public long deltaDistanceMeters;

    @SomaField
    public long projectedArrivalSecond;

    @SomaField
    public int projectedLoad;

    @SomaField
    public long projectedTotalDurationSeconds;
}
```

说明：

- 上面的 `InsertionCandidate` 是增量 frontier 候选方案，不是默认形态；
- `InsertionCandidateKey` 的身份只在同一个 `RouteVersion` epoch 内有效，不是跨整个求解生命周期稳定的 business identity；
- `InsertionCandidate` 不声明 `by_best_delta` order。候选的策略排序属于 constructor 策略层，可通过 frontier 的 dynamic sort 完成；
- candidate 只保留 comparator 与 commit 真正需要的事实，不把某个可变策略压成 `dispatchScore`，也不机械复制 FJSP 的 `indicatorReady` 生命周期；
- 当 benchmark 证明全局 best candidate 需要跨轮次维护时，应评估 application-owned priority queue，而不是恢复 Table maintained order；
- `RouteVersion` 是 stale candidate 防线，避免旧 insertion ordinal 在 route mutation 后继续有效；
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
    insertionCandidateRows.clear();
}
```

选择 keyed frontier 的实例以 `insertionCandidates.clear()` 代替最后一行；一个 solve 只采用一种 candidate store，不同时维护 dense 与 keyed 两份候选事实。

### 7.2 默认 dense workspace：完整构建后一次发布

默认方案先在 detached Batch 中完成本轮所有评分，再以一次 `replaceAll` 发布：

```java
InsertionCandidateRowBatch batch = candidateBuilder
    .buildAllFeasible(unassignedCustomerRows, routes, travelCosts);

insertionCandidateRows.replaceAll(batch);
```

`candidateBuilder` 是 application component，不是省略业务语义的黑箱。它必须对每个 `(customer, route)` 恰好一次枚举 `0..visitCount` 的全部 insertion ordinal，包括空 route 的 ordinal `0`；从 vehicle depot 与 ordered visits 得到 predecessor/successor；完成 capacity、整条受影响 suffix 的 arrival/time-window propagation；只发布 hard-constraint feasible row；保证 projected load/duration 与完整 rewritten route 一致，并对 distance、duration、load 和 route version 使用 checked arithmetic。它只能在一个同步只读批次中消费 SOMA Index，不能把 Index 保存进 Batch 或跨轮缓存。返回的 Batch 是 builder-owned reusable staging；`replaceAll`/`addBatch` 完成 detached copy 后才可清空复用，不能被另一轮并发填充。

Canonical input 把 `TravelCost` 定义为求解所需 location pair 的完整 directed lookup。Loader 在 solve 前验证 required pair；运行中 missing lookup 表示 malformed problem，立即终止并丢弃 instance。稀疏 road graph、shortest-path fallback 或不可达 arc 属于另一条明确的 application model，不能由 comparator 或 SOMA runtime 临时猜测。

### 7.3 条件式 keyed frontier：先 stage，再替换受影响 group

只有采用 keyed frontier 时才出现 route-local refresh：

```java
void refreshInsertionCandidatesForRoute(RouteId routeId) {
    Route route = routes.fetch(routeId); // readable reference path
    InsertionCandidateBatch staged = candidateBuilder
        .buildFeasibleForRoute(route, unassignedCustomerRows, travelCosts);

    insertionCandidates.findByRoute(routeId).remove();
    insertionCandidates.addBatch(staged);
}
```

Batch 必须在删除旧 group 前完整构建。`remove + addBatch` 仍然是两个 Table operations，不是原子 group replacement；若第二步失败，frontier 作为 rebuildable workspace 整体失效并由 constructor 重建，不能继续消费残缺 group。`routes.fetch(routeId)` 会递归物化 visits，只适合 reference path；hot path 使用 parent-key live child facade、ColumnView 和 application-owned reusable staging。

### 7.4 使用 total comparator 选择

默认 dense row 的 comparator 覆盖完整 tie-break：

```java
InsertionCandidateRow chosen = insertionCandidateRows.rows()
    .sorted((a, b) -> {
        int compared = Long.compare(
            a.deltaDistanceMeters(), b.deltaDistanceMeters());
        if (compared != 0) return compared;

        compared = Long.compare(
            a.projectedArrivalSecond(), b.projectedArrivalSecond());
        if (compared != 0) return compared;

        compared = Long.compare(a.routeIdValue(), b.routeIdValue());
        if (compared != 0) return compared;

        compared = Long.compare(a.customerIdValue(), b.customerIdValue());
        if (compared != 0) return compared;

        compared = Integer.compare(
            a.insertionOrdinal(), b.insertionOrdinal());
        if (compared != 0) return compared;

        return Long.compare(a.routeVersion(), b.routeVersion());
    })
    .firstOrThrow();
```

keyed frontier 使用同一业务顺序，只把 route/customer/ordinal/version 从 `candidateKey` leaf 读取。Comparator 不访问 route、visit、customer 或 `TravelCost` table，不执行 callback side effect，也不依赖当前物理顺序。

empty candidate result 表示当前 hard-constraint model 没有可行 insertion。Constructor 必须明确返回 infeasible/partial-solution outcome 或终止；不能通过放宽约束、选择未排序第一行或复用上一轮 candidate 静默继续。

### 7.5 Commit insertion

下面的 `ChosenInsertion` 是 application-local immutable value，可由 dense row 或 keyed candidate 投影；它不保存 SOMA Index：

```java
enum CommitResult {
    COMMITTED,
    STALE
}

CommitResult commitInsertion(ChosenInsertion chosen) {
    Route route = routes.fetch(chosen.routeId());
    if (route.routeVersion != chosen.routeVersion()
            || customerAssignments.containsKey(chosen.customerId())) {
        return CommitResult.STALE;
    }

    RouteVisitRowBatch rewritten = routeRewriteWorkspace
        .buildRewrittenVisits(
            route, chosen.customerId(), chosen.insertionOrdinal());
    CustomerAssignmentBatch assignment = reusableAssignmentBatch;
    assignment.clear();
    assignment.addValues(chosen.customerId(), chosen.routeId());

    long nextDistance = Math.addExact(
        route.totalDistanceMeters, chosen.deltaDistanceMeters());
    long nextVersion = Math.addExact(route.routeVersion, 1L);
    if (nextDistance < 0L
            || chosen.projectedLoad() < 0
            || chosen.projectedTotalDurationSeconds() < 0L) {
        throw new IllegalArgumentException("invalid insertion projection");
    }

    customerAssignments.addBatch(assignment);
    routes.replaceVisits(chosen.routeId(), rewritten);
    routes.mutate(chosen.routeId())
        .setLoad(chosen.projectedLoad())
        .setTotalDistanceMeters(nextDistance)
        .setTotalDurationSeconds(chosen.projectedTotalDurationSeconds())
        .setRouteVersion(nextVersion)
        .commit();

    unassignedCustomerRows
        .filter(u -> u.customerIdValue() == chosen.customerId().value)
        .remove();
    retireCandidateWorkspace(chosen);
    return CommitResult.COMMITTED;
}
```

`retireCandidateWorkspace` 只处理 derived candidate state：dense 方案在 commit 后整表 `clear()` 并由下一轮 rebuild；keyed 方案删除已分配 customer 的 group、淘汰旧 route version，并刷新受影响 route。它不能修改 route/assignment authoritative facts，也不能把 stale candidate 当作成功提交。

`firstOrThrow()` 和 `fetch(...)` 都返回 detached schema object。`routes.fetch(routeId)` 会递归物化完整 `Route.visits` List，因此上面的 reference code 以可读性为主；route-scoring hot path 应改用 parent-key live child facade 和 Row Cursor。`@SomaTable` row 不生成 structural equality/hash；代码中的 `CustomerId` / candidate key 比较依赖 immutable `@SomaValue` equality。

`routeRewriteWorkspace.buildRewrittenVisits(...)` 不是一个可以忽略成本的 helper。该 application workspace 按单 route visit 上限准入并复用 primitive/Batch storage；`routes.replaceVisits(...)` 完成 detached copy 后才可重置。它至少包含：

```text
routes.visits(routeId).rows().sorted(byPositionComparator)
  -> 读取当前 route visits
  -> 构造插入后的新 sequence
  -> 重写 position / arrival / departure / loadAfterVisit
  -> 使用 child Batch 批量替换该 route 的 visits child rows
```

publish 前该 helper 重新验证 ordinal 边界、customer/location 一致性、position 连续唯一、customer 不重复、depot edge、capacity/time-window propagation，以及 batch 汇总值与 `ChosenInsertion.projectedLoad/projectedTotalDurationSeconds` 一致。`reusableAssignmentBatch` 同样只在 `customerAssignments.addBatch(...)` 完成 copy 后清空；两份 staging 都不是 live state。

目标 generated API 必须能够用 route-level batch rebuild 或外部 sequence builder 后 `replaceAll` 表达受控 rewrite，不能假设存在未设计的高效 row move。若要新增 segment rewrite API，必须先进入对应 Design Owner。

调用方不能把 `CommitResult.STALE` 当作 successful commit。主循环应先刷新/重建 stale route candidate，再重新 selection；否则会出现无进展计数、重复 stale candidate 或错误跳过 customer。

跨 table commit 的一致性由 VRP constructor 拥有。SOMA V1 不提供跨 table transaction；canonical 方案在任何 authoritative write 已发布后发生失败时立即把本次 solve instance 标记为不可继续使用并整体丢弃。只有全部 authoritative route/assignment facts 已成功，而失败仅发生在 `UnassignedCustomerRow` 或 candidate workspace 清理时，才允许从 authoritative facts 重建这些 derived workspaces 后继续。

`CustomerAssignment` 只保存 customer -> route 归属；arrival/departure/position 都由 current route visits 持有，因为后续 insertion propagation 仍可能改变已分配 customer 的时间与位置。`UnassignedCustomerRow` 只是从 definition/assignment 差集构造的 hot workspace；只有 route visits、route scalar 和 assignment 都已经成功提交后，workspace remove 失败才可以通过重建 unassigned/candidate workspace 恢复。不能回写另一份 `Customer.state` 作为补偿。

## 8. Cache 友好性分析

保留 dense workspace 的 cache 友好性来自：

- candidate rows 连续写入；
- `replaceAll(batch)` 复用 capacity；
- candidate business sequence 来自 explicit dynamic sort；
- 可用 dynamic sort / top-k IndexBuffer 完成当前轮选择；
- 适合规模较小且全局重算不可避免的情况。

keyed insertion frontier 的 cache 友好性来自：

- route/customer/ordinal 候选跨轮保留，避免全量重建；
- 只删除和重算受影响 route 或 selected customer 的候选；
- score 更新集中在 frontier rows，comparator 只读取 candidate row 字段；
- `TravelCost` lookup 发生在 candidate build 阶段，不发生在排序 comparator 中。

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
- `RouteVisitRow.position` 是 route 当前顺序事实源；canonical live model 不复制 `Customer.assignedPosition`；
- canonical journey 在 solve 前验证 required directed `TravelCost` 完整性；稀疏图、不可达 arc 或 shortest-path fallback 必须另立 application model，不能由 SOMA runtime 猜测。

## 10. 目标决策与证明义务

### 10.1 目标决策

- 默认使用 dense `InsertionCandidateRow` workspace；只有稳定 identity、跨轮复用和局部失效同时成立时才采用 keyed frontier；
- canonical `RouteVisitRow` 是 parent-owned dense child；flat root 只保留为同语义 benchmark baseline，visit ownership 决策不与 candidate frontier 选择绑定；
- `CustomerDefinition`、`CustomerAssignment` 和 rebuildable `UnassignedCustomerRow` 分离；Route/RouteVisit current solution 同时作为最终 route result，不复制 shadow result；
- comparator 只读取 candidate row 字段；`RouteVersion` 防止 stale ordinal candidate；`CommitResult.STALE` 不计作成功提交；
- 跨 table 一致性、required `TravelCost` 完整性和失败恢复由 VRP constructor 拥有。

### 10.2 采用前证明义务

- 固化 route-local rewrite 和 parent-key live child access 的 generated API 语义；
- 在相同语义下比较 flat dense visits 与 per-route dense child 的 locality、global scan、rewrite、allocation 和 recursive materialization 成本；
- 比较 dense rebuild 与 keyed frontier 在不同规模、mutation/read ratio 和 invalidation 局部性下的 crossover；
- 验证 `InsertionCandidateKey` 的 stale cleanup、route-version replacement 和 exact-index 增量维护成本；
- 验证 required `TravelCost` 预检，并验证 customer definition/assignment/workspace 分离后的 commit 与 recovery。
