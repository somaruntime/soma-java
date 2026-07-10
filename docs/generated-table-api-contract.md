# Generated Table API 契约

状态：正式设计文档
Owner：根项目协调层
事实范围：Generated keyed/dense Table、Direct API、Row/Key/Column Pipeline、Mutator、child facade 和 ColumnView 的用户语义
非事实范围：schema annotation、code generation 过程、TableStore 数据结构、deep materialization 细节和性能实现算法
最后审查日期：2026-07-10

## 1. 目标

本文定义 SOMA Java V1 generated table facade 的跨模块公共语义。Processor 必须生成符合本文的 API，runtime-core 必须执行符合本文的行为；二者都不是本文的联合 Owner。

推荐心智模型：

```text
Table Direct API   = 精确 key/row-index boundary access
Row Pipeline       = columnar row traversal + controlled mutation
Key Pipeline       = stable key value traversal
Column Pipeline    = primitive single-column traversal
ColumnView         = explicitly borrowed live column access
Materialization    = detached boundary, defined separately
```

SOMA 不把 `Stream<schema object>`、`List<Row>` 或 mutable DTO graph 作为主 runtime API。

## 2. Table facade 与类型

每张 schema table 生成一个 typed table facade。V1 至少需要表达：

```text
XxxTable
XxxRows
XxxRow
XxxMutableRow
XxxMutator
XxxBatch
XxxKeyPipeline              // keyed only
typed column pipeline/view  // when supported
typed child facade          // parent field only
```

`XxxRow` / `XxxMutableRow` 是 callback-scoped cursor interface，不是 schema class、DTO 或 live object proxy。Cursor callback 返回后立即失效，不得保存或跨线程传递。

Key field 不生成 setter。Optional field cursor API 必须表达 presence、value、`valueOr(default)`、set 和 clear。

## 3. API 层级

| 层级 | Canonical API | 主要用途 |
|---|---|---|
| Direct | `fetch/find/containsKey/mutate/delete/fetchAt/mutateAt` | 单 row boundary 和精确定位 |
| Structural | `addBatch/replaceAll/clear/release` | 批量构造和生命周期 |
| Row Pipeline | `filter/sorted/limit/update/remove` | set-oriented traversal |
| Key Pipeline | `keys()` | keyed table key traversal/export |
| Column Pipeline | `xxxValues()` | primitive 单列遍历 |
| ColumnView | `xxxColumn()` | explicit borrow 和底层循环 |
| Child facade | `parent.children(key)` 等 | parent-owned child live access |

普通用户优先使用 Direct API 和 Row Pipeline；ColumnView 是显式的低层性能工程路径。

## 4. Direct API

### 4.1 Keyed table

Canonical shape：

```java
boolean exists = operations.containsKey(key);
Optional<Operation> found = operations.find(key);
Operation required = operations.fetch(key);
operations.mutate(key)
    .setStartMinute(start)
    .setEndMinute(end)
    .commit();
operations.delete(key);
```

规则：

- `find(key)` 返回 `Optional<R>`，missing 时为空；
- `fetch(key)` 返回 detached `R`，missing 时抛 typed `missing_key`；
- `containsKey(key)` 是 canonical existence API；
- `mutate(key)` 精确修改非 key fields；
- `delete(key)` 是 structural mutation；
- 修改 identity 只能 delete + insert。

### 4.2 Dense table

```java
int size = distances.size();
Distance row = distances.fetchAt(rowIndex);
distances.mutateAt(rowIndex)
    .setDistance(cost)
    .commit();
```

Dense row index 是当前 packed storage location，不是 stable identity。Structural mutation 后旧 index 可以失效。`fetchAt` 适合 boundary/debug；大量 primitive hot reads 应使用 pipeline 或 ColumnView。

### 4.3 Whole-table operation

- `addBatch(batch)` 增量增加合法 rows；
- `replaceAll(batch)` 全量替换，失败时旧事实不变；
- `clear()` 删除全部 rows，但 table 仍 active；
- `release()` 结束 ownership aggregate 生命周期；
- `materialize()` 是独立 detached boundary，由 [Materialization 契约](materialization-contract.md) 定义。

## 5. Row Pipeline 用户模型

Generated table facade 本身是默认 packed-row source：

```java
particles
    .filter(p -> p.alive())
    .limit(100)
    .update(p -> {
        p.setX(p.x() + p.vx() * dt);
        p.setY(p.y() + p.vy() * dt);
    });
```

`table.rows()` 可以作为显式入口，但不是主写法。Intermediate operation 是 lazy plan，terminal 执行时才遍历。

Row Pipeline 是 one-shot：terminal 之后进入 consumed state，再次 terminal 必须返回 typed `pipeline_consumed`。

## 6. Source methods

Source 只选择 terminal 的初始 row sequence：

| Source | 示例 | 语义 |
|---|---|---|
| default packed | `table.filter(...)` | 当前 packed rows；keyed table 不承诺 key order |
| explicit rows | `table.rows()` | default source 的显式别名 |
| index/unique | `findByCell(cellId)` | maintained selector 的候选 rows |
| maintained order | `byRenderOrder()` | order sidecar traversal |
| child-local | `routes.visits(routeId)` | parent-owned child table source |

Index、unique 和 order source 都返回同一种 typed Row Pipeline。它们不是 query DSL、join 或 sidecar handle。

Grouped selector 如果完整对应一个 `@SomaValue` field，generated method 应接受该 value type；否则使用 normalized leaf 顺序。具体命名冲突由 processor golden 固化。

## 7. Intermediate operations

V1 canonical operations：

```text
filter(predicate)
skip(n)
limit(n)
sorted(comparator)
```

- `filter` 接受 row-level predicate，不承诺 lambda-to-index analysis；
- `sorted` 是 dynamic row order，不移动真实 columns，不等同于 `@SomaOrder`；
- comparator 必须纯读，不修改 table；
- equal comparator result 应保留 source order，以获得 deterministic traversal；
- `sorted(...).limit(n)` 按排序后顺序截断；
- V1 不把 `map/flatMap/reduce/collect` 建成通用 object pipeline。

## 8. Read terminals

```text
count()
anyMatch(predicate)
noneMatch(predicate)
forEach(consumer)
findFirst()
firstOrThrow()
fetchAll()
rowIndexes()
```

规则：

- `forEach` 使用 read-only Row Cursor；
- `findFirst` 返回 optional detached row；
- `firstOrThrow` 在 empty result 时抛 typed error；
- `fetchAll` 按 pipeline row sequence 返回 `List<R>`；
- `rowIndexes` 返回 epoch-sensitive packed indexes，不是业务 identity；
- `findXxx` 表示 optional result，required-result 使用 `fetchXxx` 或 `firstOrThrow`；
- 不引入 `fetchFirst`，避免与 `fetch(key)` / `fetchAt` 混淆。

Materializing terminal 的递归和 budget 由 [Materialization 契约](materialization-contract.md) 定义。

## 9. Mutation terminals

```text
UpdateResult update(updater)
RemoveResult remove()
```

- `update` 接受 mutable cursor，只修改非 key fields；
- `remove` 删除 matched rows，是 structural mutation；
- `filter(...).remove()` 是 canonical 删除写法；
- candidate sequence 在 terminal 开始时确定，不因 terminal 内字段变化重新进入 filter/index/order/sort；
- selector fields 变化可以在 terminal 结束时统一维护 sidecar；
- expected failure 不得留下 partial row 或 partial sidecar。

`UpdateResult` 至少表达 scanned、matched、changed 和 sidecar maintenance；`RemoveResult` 至少表达 scanned、matched、removed、compaction 和 sidecar maintenance。它们不是性能证明，但必须支持 diagnostics 和 evidence。

## 10. Key Pipeline

Keyed table 提供：

```java
operations.keys().forEach(key -> ...);
List<OperationKey> keys = operations.keys().fetchAll();
Optional<OperationKey> first = operations.keys().findFirst();
OperationKey required = operations.keys().firstOrThrow();
```

Key callback 获得 stable value object，允许保存。未来若提供 no-allocation key cursor，必须使用不同 API 名称，不能改变现有 value semantics。

Table-level `forEach` 遍历 row cursor；key traversal 必须显式 `keys()`。

## 11. Column Pipeline 与 ColumnView

Column Pipeline：

```java
operations.orderIdValues().forEachLong(id -> ...);
operations.startMinuteValues().forEachInt(value -> ...);
```

- primitive column 使用 primitive callback，避免 boxing；
- Java 8 没有对应 callback 时，可以生成 SOMA typed functional interface；
- column pipeline 不允许 structural mutation；
- 它不等同于 borrowed ColumnView。

ColumnView 使用 explicit acquire/release：

```java
try (FloatColumnView distance = distances.distanceColumn()) {
    for (int i = 0; i < distances.size(); i++) {
        routeCost += distance.getFloat(i);
    }
}
```

Active ColumnView 期间，与其稳定性冲突的 structural mutation 在修改前返回 `view_pinned`。Release 可以级联使 view 失效；后续 access 返回 released/stale error。

## 12. Optional field API

Optional primitive/value cursor 至少生成：

```text
xxxPresent()
xxxAbsent()          // convenience
xxx()                // absent -> typed error
xxxOr(defaultValue)
setXxx(value)
clearXxx()
```

Absent 不得折叠为 Java primitive default。Setter 同时写 payload 和 presence；clear 只改变 presence，dead reference payload 必须按 runtime contract 清理。

## 13. Child facade

Public API 不接受任意 live child table 对象 attachment。Generated child facade 按 parent key/row index 和 field slot 定位 owned child：

- required child 始终 logical present，empty storage 可以 lazy allocate；
- optional child 必须区分 absent、present-empty 和 present-nonempty；
- `child().clear()` 保持 present-empty；
- `unsetChild()` 进入 absent 并 cascade release；
- `replaceAll(batch)` 替换内容，不 reparent live child；
- parent delete/clear/release 级联 child subtree；
- replacement 必须先构造并验证新 subtree，再切换 handle；失败时旧 subtree 不变。

Detailed ownership/lifecycle 由 [runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md) 拥有。

## 14. Pipeline 与 mutation 生命周期

- Cursor 只在 callback 内有效；
- terminal 期间禁止通过同一 table 外部 API 做 structural mutation；
- callback 读取其他 table 合法；跨表 mutation ordering 和 compensation 由 application 负责；
- V1 不提供 snapshot isolation、nested structural mutation 或 cross-table transaction；
- released table、escaped/stale cursor、consumed pipeline、active view conflict 必须返回 typed error；
- callback 和 pipeline 不得跨线程使用。

## 15. 性能语义边界

Public API 只承诺：

- non-materializing terminal 不需要 per-row schema object；
- default packed source、index/order source 和 dynamic sort 语义可区分；
- primitive column path 和 borrowed ColumnView 可显式选择；
- materialization 是独立 allocation boundary。

Loop fusion、Cursor reuse、primitive specialization、row permutation、scratch 和 no-per-row-allocation 由 [runtime 性能实现契约](../soma-runtime-core/docs/runtime-performance-implementation-contract.md) 拥有。任何速度声明仍需 benchmark。

## 16. 与 Java Stream 的关系

Row Pipeline 借用 Java Stream 的部分命名，但不实现 `java.util.stream.Stream`：

- 元素是 callback-scoped cursor，不是 object row；
- update/remove 是一等 terminal；
- 不承诺 parallel/spliterator/collector；
- sidecar、view pin 和 mutation lifecycle 是 SOMA 语义。

需要通用 Java Stream 时，先显式 materialize：

```java
table.filter(...).fetchAll().stream();
```

## 17. 非目标

V1 不提供 parallel stream、automatic predicate pushdown、join planner、general collection DSL、snapshot isolation、thread-safe traversal、cross-table transaction、public row pointer 或 public custom AccessPath。
