# Generated Table API 契约

状态：正式设计文档
Owner：根项目协调层
事实范围：Generated keyed/dense Table、Direct API、Row/Key/Column Pipeline、Mutator、child facade 和 ColumnView 的用户语义
非事实范围：schema annotation、code generation 过程、TableStore 数据结构、deep materialization 细节和性能实现算法
最后审查日期：2026-07-10

## 1. 目标

本文定义 SOMA Java V1 generated table facade 的跨模块公共语义。Processor 必须生成符合本文的 API，runtime-core 必须执行符合本文的行为；二者都不是本文的联合 Owner。

Public/internal compatibility 由 [Public API 与兼容性契约](public-api-compatibility-contract.md) 拥有；具体 runtime error code/context 和 callback failure mapping 由 [Runtime errors 与 diagnostics 契约](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) 拥有。

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

首个 dense generated facade 固化以下命名与 construction baseline；后续 keyed/child/access breadth 只能 additive 扩展，不能迁移这些已接受 consumer：

```text
XxxTable.create()
XxxTable.create(RuntimePlan)
XxxTable.defaultRuntimePlan()
XxxTable.runtimePlan()
XxxTable.statsSnapshot()
XxxTable.resetStats()
new XxxBatch()
new XxxBatch(initialCapacity)
XxxBatch.add(Xxx detachedRow)
XxxBatch.addValues(XxxBatch.Writer)
XxxBatch.addValues(requiredValues..., optionalPresent, optionalPrimitiveValue...) // JVM slots允许时
```

`RuntimePlan`、`MaterializationBudget`、`UpdateResult` 和 structured runtime exception 位于 `com.hgtech.soma.runtime` handwritten runtime API。`XxxRows` 的 generated nested SAM types `Predicate`、`Consumer`、`Updater` 分别接收 `XxxRow` / `XxxMutableRow`；generated facade 的 public signature 不暴露 RowSlot、column、bitmap、runtime generated-protocol type 或 storage binding。

Application override 从 generated default plan 派生：读取 `RuntimePlan.requireTable(name)`，通过 `TablePlan.toBuilder()` 修改，再用 `RuntimePlan.toBuilder().replaceTable(...)` 替换；fresh plan使用 `addTable(...)`。直接构造 incompatible identity虽可用于 negative/compatibility tooling，但 `create` 必须拒绝。Table create 后 `runtimePlan()` 返回同一 immutable effective plan，不能 live mutate。

### 2.1 Phase 1 exact Java signature matrix

以下以 carrier `Xxx` 表示 schema-specific exact signature；所有未声明 `throws`，runtime failure 使用 unchecked envelope：

```java
public final class XxxTable {
    public static XxxTable create();
    public static XxxTable create(RuntimePlan plan);
    public static RuntimePlan defaultRuntimePlan();
    public RuntimePlan runtimePlan();
    public int size();
    public int capacity();
    public long structuralEpoch();
    public boolean isReleased();
    public void addBatch(XxxBatch batch);
    public void replaceAll(XxxBatch batch);
    public void clear();
    public void release();
    public Xxx fetchAt(int rowIndex);
    public Xxx fetchAt(int rowIndex, MaterializationBudget budget);
    public List<Xxx> materialize();
    public List<Xxx> materialize(MaterializationBudget budget);
    public XxxMutator mutateAt(int rowIndex);
    public XxxRows rows();
    public XxxRows filter(XxxRows.Predicate predicate);
    public XxxRows skip(long count);
    public XxxRows limit(long count);
    public XxxRows sorted(XxxRows.Comparator comparator);
    public long count();
    public boolean anyMatch(XxxRows.Predicate predicate);
    public boolean noneMatch(XxxRows.Predicate predicate);
    public void forEach(XxxRows.Consumer consumer);
    public Optional<Xxx> findFirst();
    public Xxx firstOrThrow();
    public List<Xxx> fetchAll();
    public int[] rowIndexes();
    public UpdateResult update(XxxRows.Updater updater);
    public TableStats statsSnapshot();
    public void resetStats();
}

public final class XxxBatch {
    public XxxBatch();
    public XxxBatch(int initialCapacity);
    public int size();
    public int capacity();
    public boolean isEmpty();
    public XxxBatch add(Xxx detachedRow);
    public XxxBatch addValues(Writer writer);
    public XxxBatch addValues(fieldValues...); // only when JVM parameter slots are legal
    public void clear();
    public interface Writer { void write(RowBuilder row); }
    public interface RowBuilder { /* generated typed setXxx/clearXxx methods */ }
}

public interface XxxRow { /* typed getters and optional presence API */ }
public interface XxxMutableRow extends XxxRow { /* void setXxx(...)/clearXxx() */ }

public final class XxxMutator {
    // required/optional typed setters return this; optional clearXxx() returns this
    public void commit();
}

public final class XxxRows {
    public XxxRows filter(Predicate predicate);
    public XxxRows skip(long count);
    public XxxRows limit(long count);
    public XxxRows sorted(Comparator comparator);
    public long count();
    public boolean anyMatch(Predicate predicate);
    public boolean noneMatch(Predicate predicate);
    public void forEach(Consumer consumer);
    public Optional<Xxx> findFirst();
    public Xxx firstOrThrow();
    public List<Xxx> fetchAll();
    public int[] rowIndexes();
    public UpdateResult update(Updater updater);
    public interface Predicate { boolean test(XxxRow row); }
    public interface Consumer { void accept(XxxRow row); }
    public interface Updater { void update(XxxMutableRow row); }
    public interface Comparator { int compare(XxxRow left, XxxRow right); }
}

public final class UpdateResult {
    public static UpdateResult create(long scanned, long matched, long changed,
        long sidecarMaintained, long sidecarRebuilt);
    public long scanned();
    public long matched();
    public long changed();
    public long sidecarMaintained();
    public long sidecarRebuilt();
}
```

Field-derived signature rules：required primitive getter返回 primitive；optional primitive生成 `boolean xxxPresent()`、`boolean xxxAbsent()`、primitive `xxx()`、primitive `xxxOr(primitive defaultValue)`；mutable row用 `void setXxx(primitive)` / `void clearXxx()`，Mutator用返回 `XxxMutator` 的同名方法。Batch `RowBuilder` setter返回 `void`并用 assignment state区分 unset；`addValues(Writer)`在 callback成功且required assignments/default validation完成后才增加 size。

Direct all-field `addValues(fieldValues...)` 按 normalized field order展开 required primitive和每个 optional `(boolean present, primitive payload)`；只有其 JVM parameter slot count连同 instance receiver不超过 255 时才生成。更宽 schema仍拥有最终 `Writer/RowBuilder` primitive入口，不生成不可加载方法，也不退化为 DTO/List storage。`present=false` 时 payload不验证并 canonicalize为 primitive zero；它不是 logical value。

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

Batch 是 detached columnar construction buffer，不是 schema-object list：

- `add(row)` 立即把 carrier facts复制进 Batch；后续修改 row 不改变 Batch；
- `addValues(...)` 是 primitive/presence canonical bulk path，参数顺序由 normalized source field/leaf order 固化；optional reference/value convenience 输入的 `null` 表示 absent；
- `add(row)` 把 carrier 当前 field value（包括 primitive zero）视为显式输入；required reference/value null一律 `invalid_null_value`。Direct all-field `addValues(...)` 的参数全部显式。只有 `addValues(Writer)` 的 RowBuilder assignment state能表达 missing；callback结束时未赋 required field且无 default返回 `missing_required_field`，显式 null setter仍返回 `invalid_null_value`；绝不从 carrier zero/null猜测“用户忘记赋值”；
- Batch 在 `addBatch` / `replaceAll` 后不 consumed，table 复制调用开始时的 Batch facts；Batch 可继续修改、`clear()` 和复用，且不与 table 共享 live arrays；
- empty `addBatch` 是 no-op；`replaceAll(empty)` 产生合法 empty table，并只在 visible facts 实际改变时提升 structural epoch；
- Batch validation/growth failure 保持 Batch 原 size/facts；table import failure 保持 table 原 facts；
- `Writer`/RowBuilder callback failure包装为 `callback_failed` 且 Batch不变；RowBuilder只对一次 callback有效，为保证 escaped builder不在后续 append中重新变成有效，每次 Writer invocation使用独立 callback-scoped builder。该 allocation属于显式 Batch construction lane；常见合法宽度使用 direct all-field overload避免该 wrapper；
- Batch 不持有 Table、RuntimePlan、ColumnView、Cursor 或 child handle。

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

Intermediate operation 线性消费当前 plan node并返回新的 typed derived plan；旧 node 的任何 alias立即进入 consumed state。一个 node 不能派生两个 branch，也不能在 intermediate 后再执行 terminal。需要两个独立 traversal 时，caller 从 table/`rows()` 创建两个 source。Table facade 本身不是 pipeline；每次 source 创建都是新的 one-shot chain。该选择贴合 solver hot-loop 的明确执行边界，避免 branch 在不同 mutation epoch执行造成隐含时间语义，同时不限制 caller 显式创建多个 traversal。

Intermediate 先验证 null callback、negative skip/limit和资源边界，再原子地消费旧 node并发布新 node；expected validation failure不消费旧 node。Terminal一旦开始执行，无论 success、empty、callback failure或其他 runtime failure都进入 `CONSUMED` / `FAILED_CONSUMED`，不得重试同一 plan。

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
- V1 exact `rowIndexes()` 返回 primitive `int[]` detached buffer；不返回 boxed `List<Integer>`、live view或可跨 structural epoch解释的 identity；
- `findXxx` 表示 optional result，required-result 使用 `fetchXxx` 或 `firstOrThrow`；
- `firstOrThrow` 空结果使用 lookup category `empty_result`，context包含table/source和terminal operation；
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

Phase 1 固化 `UpdateResult` 的稳定 public shape：immutable `long scanned()`、`long matched()`、`long changed()`、`long sidecarMaintained()` 和 `long sidecarRebuilt()`。Dense/no-sidecar slice 后两项为零但不能省略；后续 sidecar implementation 直接填充，不迁移 consumer。

计数单位固定：`scanned` 是从 source实际拉取并进入 intermediate evaluation 的 candidate row数，因 limit/short-circuit未拉取的不计；`matched` 是依次通过 filter/skip/limit并到达 updater 的 row数；`changed` 是 publish时最终 staged logical field/presence与 terminal前不同的 distinct row数，同值 setter和先改后恢复不计；`sidecarMaintained` 是本 terminal至少执行一次 incremental maintenance的 distinct sidecar结构数；`sidecarRebuilt` 是完成 full rebuild并发布的 distinct sidecar结构数。一个 sidecar处理多个 row仍计一。Failed terminal不返回 `UpdateResult`，且 committed `changed` 为零；attempted scanned/matched进入 failed last-operation stats。

`update` 使用整次 terminal 的 primitive staging：filter/candidate 先冻结 matched row indexes，单个 reusable mutable cursor 只写 staging columns，全部 callback/validation 成功后统一 publish。任一 callback 失败时 live columns、presence、epoch 和 stats 中的 committed facts保持不变，并以 `callback_failed` 保留 cause；不得逐 row 直接写 live facts再承诺未来补 rollback。

Staging 只覆盖 matched row indexes与本 schema可变 primitive/presence columns，使用 table-local reusable primitive scratch；执行 callback前按 checked arithmetic预估 `matched × staged widths + row-index/presence scratch`，超过 effective `TablePlan.maximumUpdateScratchBytes` 时以 `memory_limit_exceeded` 失败且不调用 updater。Scratch可保留复用但不得超过 plan上限，current/high-water进入 stats。后续可以用等语义 chunk/undo algorithm优化，但不能改变 all-or-nothing、callback visibility或 public result。

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
- mutating terminal callback期间禁止通过同一 table的任何外部 API重入读取或写入，返回 `reentrant_access`；callback只能通过本次 mutable cursor观察 pre-terminal row facts与自己对当前 row的 staged修改。读取其他 table合法；
- callback 读取其他 table 合法；跨表 mutation ordering 和 compensation 由 application 负责；
- V1 不提供 snapshot isolation、nested structural mutation 或 cross-table transaction；
- released table、escaped/stale cursor、consumed pipeline、active view conflict 必须返回 typed error；
- callback 和 pipeline 不得跨线程使用。

`mutateAt`/keyed `mutate` 返回 one-shot Mutator。Mutator 捕获 structural epoch；`commit()` 后再次 set/clear/commit 返回 `mutation_consumed`，commit 前发生 structural change 返回 `stale_mutator`。Setter 只写 staged values，`commit()` 成功时一次 publish；callback/pipeline mutable cursor 采用同一 staging原则。

Application callback 抛出异常时，non-mutating terminal 不修改 table；mutating terminal 必须保持 visible failure atomicity，并按 runtime error contract 保留 cause。实现如果无法满足，不能以“callback 是用户代码”为由发布 partial mutation 语义。

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
