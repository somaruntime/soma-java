# VRP 构造解 runtime state 示例

类型：Report / 开发者 current-executable 场景

状态：当前

Owner：`soma-examples` output

受众：使用或维护当前 VRP runtime-state example 的开发者

适用版本：最后 implementation-affecting baseline `a137b10`

输入事实源：当前 example source、[VRP Blueprint](../../docs/blueprints/vrp-runtime-state-blueprint.md)、Design 与 phase-6 evidence

事实范围：当前 VRP schema、candidate build/select/commit journey、失败与性能边界

非事实范围：完整 VRP solver、SOMA public contract 和性能优势

最后审查日期：2026-07-21

> 本文只记录当前 executable example。目标仍由 Blueprint 拥有，长期语义仍由 Design 拥有。

## 1. 当前场景

[`VrpScenario.java`](../src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java) 执行一轮 cheapest-insertion journey：

```text
validate/import definitions + current solution
  -> rebuild unassigned workspace
  -> enumerate every route insertion ordinal
  -> publish feasible dense candidate workspace
  -> total-order select + stale preflight
  -> publish assignment and rewritten route
  -> retire derived workspaces
  -> export current Route
```

教学 fixture 同时包含一条非空 route 和一条空 route。候选生成会覆盖非空 route 的 `0..visitCount` 以及空 route 唯一合法的 ordinal `0`，不是只演示单一插入位置。

## 2. 当前数据角色

| Role | 当前 Table | 权威性与访问方式 |
|---|---|---|
| input facts | `CustomerDefinition`、`VehicleDefinition`、`TravelCost` | keyed、import 后只读；required directed travel pair 缺失即 malformed input |
| current solution | `Route` + parent-owned `RouteVisitRow` child | route scalar 与 visits 是下一轮评分和最终路线结果的唯一事实源 |
| assignment result | `CustomerAssignment` | keyed `customer -> route`；row absence 表示尚未分配 |
| derived working state | `UnassignedCustomerRow` | definition/assignment 差集的 dense workspace，可重建 |
| derived candidate state | `InsertionCandidateRow` | 当前轮 dense workspace，`replaceAll(batch)` 后显式排序 |

`CustomerAssignment` 不复制 position、arrival 或 departure；这些事实只保存在 current route visits。当前 one-active-route-per-vehicle 模型用 `Route.by_vehicle` secondary unique access，而不是普通 group index。

## 3. 关键 schema 投影

```java
@SomaTable(name = "customer_definitions", defaultCapacity = 4096)
public final class CustomerDefinition {
    @SomaKey public CustomerId customerId;
    @SomaField public long inputOrder;
    @SomaField public LocationId locationId;
    @SomaField public int demand;
    @SomaField public long readySecond;
    @SomaField public long dueSecond;
    @SomaField public long serviceSeconds;
}

@SomaTable(name = "customer_assignments", defaultCapacity = 4096)
public final class CustomerAssignment {
    @SomaKey public CustomerId customerId;
    @SomaField public RouteId routeId;
}

@SomaTable(name = "routes", defaultCapacity = 512)
@SomaUnique(name = "by_vehicle", fields = {"vehicleId.value"})
public final class Route {
    @SomaKey public RouteId routeId;
    @SomaField public VehicleId vehicleId;
    @SomaField public int load;
    @SomaField public long totalDistanceMeters;
    @SomaField public long totalDurationSeconds;
    @SomaField public long routeVersion;
    @SomaChild(initialCapacity = 32) public List<RouteVisitRow> visits;
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

完整、可编译声明以 [`com.hgtech.soma.examples.vrp`](../src/main/java/com/hgtech/soma/examples/vrp) 为准。`RouteVisitRow` 预投影 immutable `LocationId`，并统一使用 seconds；candidate schema 不维护 `by_route` 或业务 order。

## 4. Build 与 select

`CandidateBuilder` 使用 owner-owned reusable `InsertionCandidateRowBatch` 和 primitive route workspace。对每个 customer/route，它先按 `position` 读取 live child，再枚举全部 ordinal，完整传播：

- depot edge 和所有受影响 visit edge；
- arrival、ready/due window 与 service time；
- cumulative load 与 vehicle capacity；
- total distance、duration 和插入 customer arrival；
- `Math.addExact` / `subtractExact` 的 overflow 检查。

只有满足 hard constraints 的 row 才进入 Batch。Batch 完整构造后通过 `replaceAll` 一次发布；排序 comparator 只读取 candidate row，顺序为：

```text
deltaDistanceMeters
  -> projectedArrivalSecond
  -> routeId
  -> customerId
  -> insertionOrdinal
  -> routeVersion
```

若 unassigned customer 仍存在而 candidate 为空，场景抛出 `VrpInfeasibleException`；不会选择物理第一行、放宽约束或复用上一轮候选。

## 5. Commit 与失败边界

选中值先被复制为 application-owned immutable `ChosenInsertion`，不保存 SOMA Index。Commit 在任何权威写之前重新验证 route version、assignment absence、ordinal、完整 route projection 及 candidate 汇总值：

1. stale candidate 返回 `STALE`，不写任何权威事实；
2. 写入 `CustomerAssignment`；
3. `replaceVisits` 发布完整 child rewrite；
4. 更新 route load/distance/duration/version；
5. 只有权威事实完整成功后，才清理 unassigned/candidate derived workspace。

SOMA 不提供跨 Table transaction。从第一次权威写开始发生任何失败，当前 solve instance 进入 fail-stop；仅 derived cleanup 失败时，application 才能从 route/assignment 权威事实重建 workspace 后继续。

## 6. 性能与证据边界

- candidate build 复用 Batch、primitive sequence 和 child rewrite staging；
- ColumnView 在一个 phase 内复用并在 mutation 前关闭；
- dynamic sort 使用 table-local `IndexBuffer`，candidate table 不承担 maintained index 成本；
- phase-6 Gate 固定 schema/hash、222-type manifest 中的 VRP generated surface、public facts、Java 8 classfile 和 scenario output；
- benchmark `generated.dense_scratch_replace_sort` 单独测量无 maintained index 的 56-byte candidate workspace；它不再伪装成 exact-index lane。

当前 scenario APC 的 route-visit hot leaf width 为 40 bytes，报告只覆盖该 fixture 的 executed result accounting，不构成普遍 VRP 性能结论。

## 7. 非目标

本示例不实现局部搜索、LNS、Tabu、列生成、最优性证明、稀疏图 shortest-path fallback、跨 root transaction 或可重试的持久化命令协议。
