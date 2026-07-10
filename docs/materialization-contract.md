# Materialization 契约

状态：正式设计文档
Owner：根项目协调层
事实范围：detached materialization、recursive ownership traversal、List/Map shape、budget、ordering、equality 和失败语义
非事实范围：live child API、TableStore layout、external DTO mapping、serialization 和 persistence
最后审查日期：2026-07-10

## 1. 定位

Materialization 是从 SomaTable 当前事实构造 caller-owned Java object graph 的显式 boundary。它产生观察结果，不改变 Table，也不建立 live synchronization。

```text
TableStore authoritative facts
  -> generated materializer
  -> @SomaTable object + @SomaValue + List/Map
  -> caller-owned detached graph
```

本文是 materialization 跨模块语义的唯一 Owner。Annotation 只声明 shape，processor 生成 materializer，runtime-core 提供 storage traversal 和 budget accounting。Default budget 的 plan lifecycle 由 [Runtime plan 契约](../soma-runtime-core/docs/runtime-plan-contract.md) 拥有；typed exceed/allocation error envelope 由 [Runtime errors 与 diagnostics 契约](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) 拥有。

## 2. Materialized Object

单行 materialization 直接使用对应 `@SomaTable` schema class。它：

- 不持有 RowSlot、child handle、bitmap、sidecar、Cursor 或 ColumnView；
- 不自动 write-back、dirty track 或同步；
- 可以在 Table mutation/release 后继续存在；
- 可能相对当前 Table 变旧；
- 重新进入 Table 必须经过 Batch 或 generated mutation boundary。

`View` 一词保留给仍连接 live storage 的 borrowed access，不用于 detached materialization。

## 3. Container shape

| Logical source | Materialized shape |
|---|---|
| single row | `R` |
| dense whole table / dense child | `List<R>` |
| keyed whole table / keyed child | `Map<K,R>` |
| ordered/index/dynamic Row Pipeline | `List<R>` |
| key pipeline | `List<K>` |

Whole keyed Table 的 `Map` 表达 identity lookup；Row Pipeline 即使来源是 keyed table，仍按 pipeline sequence 返回 `List<R>`。

Returned collection 由 caller 拥有，可以修改或丢弃，但这些操作不改变 SomaTable。

## 4. Recursive ownership traversal

Materializing parent row 时，必须递归 materialize 它拥有的全部 child tables：

```text
Parent
  -> dense child List<Child>
       -> keyed grandchild Map<Key, Grandchild>
```

递归只沿 ownership edge。Scalar/value key reference 不自动 lookup，也不触发 implicit join。

返回图必须完整：

- parent scalar/value fields；
- optional presence；
- 所有 reachable required child；
- 所有 present optional child；
- descendant tables 的完整 rows 和 fields。

## 5. Required 与 optional child

| Runtime state | Materialized field |
|---|---|
| required logical empty dense child | non-null empty `List` |
| required logical empty keyed child | non-null empty `Map` |
| optional absent child | `null` |
| optional present-empty dense child | non-null empty `List` |
| optional present-empty keyed child | non-null empty `Map` |

Absent、present-empty 和 present-nonempty 是不同事实，不能合并。Required child 即使尚未分配 physical storage，也 logical present。

## 6. Materializing APIs

以下属于 materialization boundary：

```text
find(key)
fetch(key)
fetchAt(rowIndex)
findFirst()
firstOrThrow()
fetchAll()
materialize()
```

每个 materializing API 必须提供默认/effective budget 与显式 budget overload：

```text
fetch(key)
fetch(key, budget)
fetchAll()
fetchAll(budget)
materialize()
materialize(budget)
```

Count、predicate、update/remove、key traversal、non-materializing column terminal 和 ColumnView 不隐式 materialize schema object graph。

## 7. All-or-nothing

一次 materialization invocation 是结果原子单元：

- 任一 descendant 失败时不返回 partial parent、partial list 或 partial map；
- 失败不修改 Table、不提升 mutation epoch；
- caller 不获得已构造一半的公开 result；
- temporary allocations 可以由 JVM 回收，但不能注册为 live runtime state；
- error 必须包含 ownership/materialization path。

Materialization 不提供 cancellation/timeout partial result。Application 需要取消时，应在调用边界外控制是否发起。

## 8. MaterializationBudget

每次 deep materialization 必须使用一个覆盖整个 invocation 的确定性 budget。`fetchAll` 不能按 top-level row 重置。

永久维度至少包括：

| Dimension | 含义 |
|---|---|
| maximum ownership depth | ownership path 最大深度 |
| maximum table instances | materialized table instance 数 |
| maximum rows | 所有 reachable rows 总数 |
| maximum leaf values | materialized leaf 总数 |
| maximum estimated allocation bytes | 估算分配上限 |

V1 不默认使用 wall-clock timeout，因为相同事实不应因机器速度不同得到不同结果。Elapsed time 只进入 diagnostics。

超限返回 typed `materialization_budget_exceeded`，至少包含：

- exceeded dimension；
- configured limit；
- current/proposed count 或 estimate；
- effective budget identity；
- ownership/materialization path。

Allocation failure 与 budget exceeded 是不同 error。

## 9. V1 初始 runtime plan

以下是初始工程默认值，不是 Schema、schema hash 或永久兼容承诺：

| Dimension | Default |
|---|---:|
| ownership depth | 32 |
| table instances | 100,000 |
| rows | 1,000,000 |
| leaf values | 50,000,000 |
| estimated allocation | 256 MiB |
| wall-clock timeout | 不设置 |

Application/table configuration 可以覆盖这些值。默认值必须由 deep-materialization benchmark lane 校准；改变默认值属于 runtime plan change。

## 10. Ordering

- Dense child/whole table 没有显式 order 时按当次 packed traversal materialize；这不是稳定业务顺序；
- keyed `Map` 不承诺业务 iteration order；
- order/index/dynamic sort Row Pipeline 按其确定的 sequence materialize 为 `List`；
- consumer 依赖稳定顺序时必须选择明确 order source 和完整 tie-break；
- structural mutation 后不得依赖旧 packed order。

## 11. Equality 与 hash

V1 `@SomaTable` row 不生成 structural `equals/hashCode`，默认是 Object identity。它是 caller-owned、可修改的 detached data carrier，但不是 `@SomaValue`；修改 detached row 不会自动 write-back。

- `@SomaValue` 和 generated key 使用 canonical value equality/hash；
- `List` / `Map` 保留 Java Collection contract；
- materialized graph 不承诺 deep equality；
- Record/schema row 不应作为 stable Map key 或 Set identity；
- tests 使用 soma-testkit 的 schema-aware recursive comparator。

Comparator 必须保留 optional absent/present-empty、dense order、keyed content 和 floating canonical semantics 的差异。

## 12. Copy、borrow 和 external mapping

Materialized Object 是 copy，不是 Row Cursor 或 ColumnView。Returned graph 不允许包含 live child facade 或 runtime internal handle。

External DTO/API/persistence mapping 位于 application adapter：

```text
SomaTable -> Materialized Object or primitive projection -> external mapper
```

SOMA 不生成稳定 wire/persistence format，不自动把 external DTO 与 Table 双向同步。

## 13. 性能边界

Deep materialization 成本近似：

```text
O(reachable rows × materialized leaf fields)
```

并产生与 object/collection graph 规模相称的 allocation。它适用于 API boundary、export、debug 和测试，不是 hot-loop default。

Benchmark 必须把 import、columnar computation、deep materialization 和 external DTO mapping 分开计量。

## 14. 实现义务

Processor/codegen 必须：

- 生成所有 materializing API 的 default/explicit budget overload；
- 生成正确的 schema object、`List`、`Map` 和 optional shape；
- 只沿 validated ownership metadata 递归；
- 不为 table row 生成 structural equality/hash。

Runtime-core 必须：

- 提供 deterministic counters、allocation estimator version 和 path tracker；
- 在进入公开 result 前检查 budget；
- 区分 budget、allocation、lifecycle 和 invariant errors；
- 不泄漏 live handle/view/cursor。

Testkit 必须提供 explicit recursive comparator 和 budget/path assertion。

## 15. 非目标

V1 不提供 lazy materialized child collection、live object graph、automatic key-reference join、object write-back、snapshot isolation、wall-clock materialization timeout、serialization 或 persistence。
