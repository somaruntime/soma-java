# Row Pipeline API 契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-processor` / `soma-runtime-core`

## 1. 目标

本文定义 `soma_java` V1 的 Row Pipeline API 用户模型、能力边界、mutation 语义和性能口径。

Row Pipeline API 的目标是：让 Java 用户用接近 Java Stream 的方式遍历 runtime state，同时保留 SOMA columnar runtime 的核心能力：packed scan、index/order source、row cursor 直接读写 column storage、原地更新和 DTO boundary materialization。

推荐心智模型：

```text
Java Stream = object pipeline, mostly read / collect
SOMA Rows   = columnar row pipeline, read + controlled in-place update
```

SOMA 不把 `Stream<DTO>` 作为主 API。DTO 是 schema source 和 materialized boundary object；Row Pipeline 的 callback 参数是 generated row cursor / mutable row cursor，不是 DTO，也不是 live row proxy object graph。

V1 API 需要同时服务两类 table：

- keyed table：有 stable logical key，适合 `fetch(key)`、`containsKey(key)`、`mutate(key)`、唯一性约束和 keyed lookup；
- dense table：没有 stable logical key，以连续 row index / packed storage 为自然访问方式，适合 packed scan、矩阵/数组型 runtime state、批量替换和 row-index iteration。

## 2. API 层级

V1 用户面对的是同一个 generated table facade，但 API 应按职责分层理解：

| 层级 | 代表 API | 主要用途 | 是否 hot path |
|---|---|---|---|
| Table Direct API | `fetch(key)`、`fetchAt(rowIndex)`、`containsKey(key)`、`mutate(key)`、`mutateAt(rowIndex)`、`delete(key)`、`replaceAll(batch)` | 单 row boundary 访问、keyed lookup、dense row-index 定位、生命周期和结构性操作 | 取决于方法 |
| Row Pipeline | `table.filter(...)`、`findByXxx(...)`、`byXxx(...)`、`update(...)`、`remove()` | set-oriented traversal、scan、filter、dynamic sort、批量原地更新 | 是 |
| Key Pipeline | `table.keys()` | keyed table 的 key traversal / key export | 不是主要 hot path |
| Column Pipeline | `table.xxxValues()` 或 generated column traversal method | 单列遍历、轻量统计、避免 DTO materialization | 是 |
| ColumnView | `table.xxxColumn()` 或 generated typed `ColumnView` | 极端 hot path、显式 view lifecycle、底层性能工程 | 是，但更底层 |

规则：

- 普通用户优先使用 Table Direct API 和 Row Pipeline；
- 需要 key traversal 时显式使用 `keys()`，不要把 table-level `forEach` 理解为 key traversal；
- 需要单列热路径时优先使用 Column Pipeline；需要 explicit acquire/release、view pinned 语义或底层循环时再使用 ColumnView；
- `@SomaIndex` / `@SomaOrder` 生成的是 Row Pipeline source method，不引入独立 query DSL。

Runtime internal 可把默认 scan、index source、order source 和 dynamic sorted source 理解为不同 `AccessPath`。`AccessPath` 只决定 terminal 开始时的初始 row sequence，不改变 public Row Pipeline API，也不暴露 sidecar handle。

## 3. 用户模型

每张 generated table 本身就是默认 Row Pipeline source：

```java
particles
    .filter(p -> p.alive())
    .limit(100)
    .update(p -> {
        p.setX(p.x() + p.vx() * dt);
        p.setY(p.y() + p.vy() * dt);
        p.setAge(p.age() + dt);
    });
```

`particles` 表示 table facade，同时也是默认 packed row traversal source。`particles.rows()` 可以作为显式入口或调试入口保留，但不作为主推荐写法。

Schema 中声明的 `@SomaIndex`、`@SomaUnique`、`@SomaOrder` 可以生成更自然的 source method，但这些 source method 仍然返回同一种 row pipeline：

```java
particles.filter(p -> p.alive());   // default packed scan source
particles.findByCell(cellId);       // index source
particles.byRenderOrder();          // maintained order source
```

这里 `p` 是 generated row cursor。`filter` / `forEach` / `sorted` 的 callback 使用 read-only row cursor；`update` 的 callback 使用 mutable row cursor。runtime 可以复用 cursor object，callback 返回后 cursor 失效，用户不得保存 cursor 引用。

## 4. Core API shape

V1 推荐生成以下类型：

```text
ParticleTable
ParticleRows
ParticleRow
ParticleMutableRow
ParticleKeyPipeline       // keyed table only
ParticleColumnPipeline    // generated per field / column family when supported
```

命名示例：

```java
public interface ParticleRow {
    long particleId();
    boolean alive();
    float x();
    float y();
    float vx();
    float vy();
    float age();
}

public interface ParticleMutableRow extends ParticleRow {
    void setAlive(boolean value);
    void setX(float value);
    void setY(float value);
    void setVx(float value);
    void setVy(float value);
    void setAge(float value);
}
```

规则：

- key field 不生成 mutable setter；identity change 必须通过 delete + insert 表达；
- optional field 生成 presence getter、absent getter、value getter、`valueOr(defaultValue)`、setter 和 clear 方法；
- value field 可以生成 grouped accessor，也可以生成 leaf-level getter / setter，最终形态由 codegen golden 固化；
- cursor API 不暴露 row pointer、bitmap word、sidecar handle 或 column array。

Row Pipeline 的 public callback 类型优先使用 Java 8 标准 functional interface：

```java
Predicate<ParticleRow>
Consumer<ParticleRow>
Consumer<ParticleMutableRow>
Comparator<ParticleRow>
```

如 benchmark 或 codegen 约束证明 generated functional interface 有明确收益，V1 可以生成 `ParticleRowPredicate`、`ParticleRowUpdater` 等专用接口，但这应是性能和类型诊断决策，不应让用户心智模型偏离 Java lambda。

## 5. Table Direct API

Table Direct API 用于不需要 set-oriented traversal 的直接访问。

Keyed table 推荐提供：

```java
boolean exists = operations.containsKey(key);
Operation operation = operations.fetch(key);
Optional<Operation> found = operations.find(key);
operations.mutate(key)
    .setStartMinute(start)
    .setEndMinute(end)
    .commit();
operations.delete(key);
```

Dense table 推荐提供：

```java
int n = distances.size();
Distance distance = distances.fetchAt(rowIndex); // materialized boundary read
distances.mutateAt(rowIndex)
    .setDistance(cost)
    .commit();
```

Dense table 的 row-index direct API 主要解决定位语义；如果是在热循环中读取大量 primitive value，应优先使用 Row Pipeline、Column Pipeline 或 ColumnView，避免重复 DTO materialization。

语义：

- `find(key)` 返回 `Optional<DTO>`，不存在时返回 `Optional.empty()`；
- `fetch(key)` 返回 materialized DTO detached copy，不存在时抛 typed runtime error；
- `containsKey(key)` 是 canonical key existence API，`contains(key)` 可以作为 alias，但不作为文档主写法；
- `fetchAt(rowIndex)` 返回 materialized DTO detached copy，row index 越界或 row 不存在时抛 typed runtime error；它是 boundary/debug 友好 API，不是 dense hot loop 的首选读取路径；
- `mutate(key)` / `mutateAt(rowIndex)` 是 precise single-row update，不允许修改 key identity；
- dense table 的 row index 是当前 packed storage index，不是 stable business identity；执行 structural mutation 后，已有 row index 可能失效；
- V1 可以支持 dense table `removeAt(rowIndex)`，但必须明确 packed compaction 语义；在没有必要前，推荐 dense table 通过 `replaceAll(batch)`、`clear()`、`addBatch(batch)` 表达批量替换。

## 6. Source methods

Source method 只决定 terminal 开始时的初始 row sequence，不改变后续 pipeline API。Runtime internal 可以将 source method 映射为 `AccessPath`，由 `AccessPath` 产生本次 terminal 的 `RowSequence`。

默认 source：

```java
particles
```

语义：table facade 本身遍历当前 table 的 packed rows。Dense table 以 row-index iteration 为自然 source；keyed table 以当前 packed storage order 为默认 source，不承诺 key order。

显式 source：

```java
particles.rows()
```

语义：与直接使用 `particles` 等价。它用于需要强调 row traversal 的文档、测试或调试场景，不作为普通用户主入口。

Index source：

```java
particles.findByCell(cellId)
operations.findByJobSequence(jobId, sequenceNo)
processingTimes.findByOperation(operationKey)
```

语义：使用 maintained index / unique sidecar 生成候选 `RowSequence`。后续仍可继续 `filter`、`sorted`、`limit`、`forEach`、`update` 或 `remove`。

命名规则：`@SomaIndex(name = "by_cell", ...)` 推荐生成 `findByCell(...)`；如果 index selector 是 `cellId`，也可以生成更完整的 `findByCellId(...)`，最终命名由 generated API name collision rule 和 golden case 固化。

Grouped index source 可以由 normalized selector 的完整 leaf set 生成。如果 selector leaf 正好对应一个 value field，例如 `operationMachineKey.operationKey` 的所有 leaf，则 generated source method 应使用 value type 参数，例如 `findByOperation(OperationKey operationKey)`；否则使用 normalized leaf 参数顺序。

Unique index source 在 V1 仍返回 Row Pipeline，最多包含 0/1 row。是否额外生成 `findOneByXxx(...)` 或 `fetchByXxx(...)` 这类 direct convenience API，属于后续易用性优化，不作为 V1 核心要求。

Order source：

```java
particles.byRenderOrder()
operations.byDispatchOrder()
routeVisits.byRoutePosition(routeId)
```

语义：使用 maintained order sidecar 生成有序 `RowSequence`。Grouped order source 可以由 `@SomaOrder` 的 selector prefix 生成。

Grouped order source 使用 order leading selector prefix 过滤同一 ordered sidecar 的逻辑分组。例如 `byRoutePosition(RouteId routeId)` 表示先限定 `routeId`，再按 `position` 的后续 selector 顺序产生 `RowSequence`。它不是 join、不是 lambda 下推，也不暴露 order sidecar handle。

Source method 是性能入口，不是能力边界。没有 index/order source 时，用户仍然可以从 table 默认 source 开始 scan、filter 和 dynamic sort。

## 7. Intermediate operations

V1 Row Pipeline 推荐支持以下 intermediate operations：

```java
filter(predicate)
skip(n)
limit(n)
sorted(comparator)
```

`filter(predicate)` 是 Java lambda predicate。它可以表达任意 row-level 判断，但 V1 不承诺把 lambda predicate 自动识别为 index seek。需要 index 加速时，用户应从 generated index source method 开始：

```java
particles.findByCell(cellId)
    .filter(p -> p.alive())
    .update(p -> p.setVisible(true));
```

`sorted(comparator)` 表示 dynamic sort。它不移动真实 column storage，推荐实现为 row-index permutation 或 top-k row-index buffer。它不等同于 `@SomaOrder` maintained order sidecar。

排序规则：

- comparator 使用 read-only row cursor；
- comparator 必须是 pure comparator，不应修改 table 或依赖会在排序期间变化的外部状态；
- comparator 判定相等时，V1 推荐保留 source order，保证 deterministic traversal；
- `sorted(...).limit(n)` 按排序后的 deterministic order 截断；
- mutation terminal 执行前先确定本次 terminal 的 traversal plan；如果 update 修改 order selector 字段，不影响本次 terminal 内尚未访问 row 的顺序，sidecar 在 terminal 结束时同步维护。

V1 不把 `map`、`flatMap`、`reduce`、`collect` 作为 Row Pipeline 核心能力。它们容易把 API 拉回 object pipeline / general collection library。DTO materialization 后用户可以使用 Java Stream 完成通用 collection 操作。

## 8. Terminal operations

V1 Row Pipeline 推荐支持以下 read terminal：

```java
count()
anyMatch(predicate)
noneMatch(predicate)
forEach(consumer)
findFirst()
firstOrThrow()
fetchAll()
rowIndexes()
```

语义：

- `forEach` 的 callback 参数是 read-only row cursor；
- `findFirst()` materialize 第一个 matching DTO，返回 `Optional<DTO>`；
- `firstOrThrow()` materialize 第一个 matching DTO，空结果时抛 typed runtime error；
- `fetchAll()` materialize DTO list，用于 API boundary、export、debug 或测试；
- `rowIndexes()` 返回本次 pipeline 的 current packed row index 列表，适合 dense table、底层调试或和 ColumnView 配合；row index 不是 stable business identity。

命名规则：

- `findXxx` 表示 optional result；
- `fetchXxx` 表示 materialized result，且 required-result API 在缺失时抛 typed runtime error；
- `firstOrThrow()` 是 pipeline required-result 入口；V1 不推荐再引入 `fetchFirst()`，避免和 `fetch(key)` / `fetchAt(rowIndex)` 混淆。

V1 Row Pipeline 推荐支持以下 mutation terminal：

```java
UpdateResult update(updater)
RemoveResult remove()
```

语义：

- `update` 的 callback 参数是 mutable row cursor；
- `update` 只能修改非 key field；
- `remove` 删除当前 pipeline 中的 matched rows，是 structural mutation terminal；
- `filter(predicate).remove()` 是推荐删除写法；
- `removeIf(predicate)` 可以作为 convenience alias，但不作为主模型；
- `UpdateResult` 至少记录 scanned rows、matched rows、changed rows、sidecar dirty count 和 sidecar maintenance cost hint；
- `RemoveResult` 至少记录 scanned rows、matched rows、removed rows 和 compaction / sidecar maintenance cost hint；
- `matched rows` 表示进入 terminal action 的 row 数量；`changed rows` 表示至少一个 field 实际发生值变化的 row 数量；
- 修改 index/order selector 字段时，runtime 可以延迟到 terminal end 统一维护 sidecar；
- mutation terminal 执行期间，candidate `RowSequence` 按 terminal 开始时的 current state 和 source/intermediate plan 确定，不因本次 terminal 内部 update 重新进入 filter、sort、index 或 order source。

## 9. Key Pipeline and Column Pipeline

Keyed table 可以暴露 key pipeline：

```java
operations.keys().forEach(key -> ...);
List<OperationKey> keys = operations.keys().fetchAll();
Optional<OperationKey> first = operations.keys().findFirst();
OperationKey key = operations.keys().firstOrThrow();
```

语义：

- `keys()` 返回 key pipeline，不是 row pipeline；
- `keys().forEach(...)` 遍历 key，不 materialize DTO；
- V1 默认 `keys().forEach(...)` 向 callback 提供 stable key value object，允许用户保存该 key；如果后续引入 no-allocation key cursor，必须使用不同 API 名称，不能改变 `forEach` 的 value semantics；
- `keys().fetchAll()` materialize key list；
- `findFirst()` 返回 `Optional<Key>`；
- `firstOrThrow()` 在空结果时报 typed runtime error。

Table 的 `forEach(...)` 默认遍历 row cursor，不遍历 key。需要 key traversal 时必须显式使用 `keys()`。

Column pipeline 可以作为 ColumnView 的易用入口：

```java
operations.orderIdValues().forEachLong(id -> ...);
operations.startMinuteValues().forEachInt(value -> ...);
```

语义：

- generated column pipeline 使用 Java lowerCamel plural / `xxxValues()` / 明确 column method name，最终命名由 golden case 固化；
- 不生成 `operations.OrderId` 这类 public field-style API；
- primitive column pipeline 应优先使用 primitive callback，避免 `Consumer<Integer>` / `Consumer<Long>` 装箱；
- Java 8 内置 `IntConsumer`、`LongConsumer`、`DoubleConsumer` 可直接使用；`float`、`boolean` 等类型可以生成 `FloatConsumer`、`BooleanConsumer` 等 SOMA 专用 functional interface；
- column pipeline 不替代 typed `ColumnView` lifecycle；如果需要 explicit acquire/release，仍使用 generated ColumnView API；
- column pipeline 不允许 structural mutation。

## 10. Mutation and lifecycle rules

Row Pipeline 是 lazy pipeline：intermediate operation 只记录 traversal plan，terminal operation 才遍历 table。

V1 pipeline 推荐采用 Java Stream-like one-shot lifecycle：

- terminal operation 执行后，pipeline object 进入 consumed 状态；
- consumed pipeline 再次执行 terminal operation 必须报 typed runtime error；
- 需要重复执行同一逻辑时，应重新从 table source method 构造 pipeline；
- source method 和 intermediate operation 本身必须是轻量对象构造，不扫描 table、不复制 row、不 acquire ColumnView。

Callback 生命周期规则：

- row cursor 只在 callback 调用期间有效；
- row cursor 不允许逃逸到 callback 外；
- pipeline 执行期间，不允许通过同一 table 的外部 API 做 structural mutation；
- pipeline callback 中不允许调用同一 table 的 `addBatch`、`replaceAll`、`clear`、`delete`、`remove` 或另一个 structural mutation terminal；
- callback 可以读取其他 table；修改其他 table 必须由上层业务保证不会形成交叉 table mutation cycle；V1 不提供 cross-table transaction；
- 非 structural field update 必须通过当前 `update` callback 的 mutable cursor 表达；
- active ColumnView 下的 structural mutation 仍遵守 `view_pinned` 规则；
- Row Pipeline 不承诺 snapshot isolation，terminal 基于执行时 table current state。

如果实现检测到 cursor escape、pipeline consumed、nested structural mutation、table released、schema/runtime mismatch 或 active view pinned，必须返回 typed runtime error，而不是静默产生 undefined behavior。

## 11. Optional field rules

Optional field 在 DTO 层使用 boxed Java type，在 Row Pipeline cursor 层使用 presence-aware primitive / value accessor。

示例：

```java
operations
    .filter(o -> o.endMinuteAbsent())
    .update(o -> o.setEndMinute(endMinute));

int end = row.endMinuteOr(Integer.MAX_VALUE);
```

规则：

- optional field 至少生成 positive presence getter，例如 `endMinutePresent()`；
- 可以生成 negative convenience getter，例如 `endMinuteAbsent()`，用于 filter readability；
- value getter 在 absent 时必须抛 typed runtime error，不能静默返回 Java primitive default；
- `valueOr(defaultValue)` 用于 hot path 默认值读取；
- setter 设置 presence bit 并写入 value；
- clear 方法清除 presence bit，后续 value getter 按 absent 处理。

## 12. Performance contract

V1 可以声明 Row Pipeline 具备以下性能基础：

- 不做 per-row DTO allocation；
- cursor object 可以复用；
- primitive field getter / setter 直接访问 column storage；
- table 默认 source 对 dense table 和 packed keyed table 是连续 row scan；
- generated index/order source 直接使用 maintained sidecar；
- dynamic `sorted` 使用 row-index buffer，不重排 column storage；
- `update` terminal 可以批量维护 dirty index/order sidecar；
- generated row cursor 和 callback path 应避免 reflection、boxing 和 per-row lambda adapter allocation。

V1 不应声明：

- arbitrary Java lambda predicate 自动下推到 index；
- dynamic `sorted(comparator)` 与 maintained `@SomaOrder` 性能等价；
- Row Pipeline 比手写 primitive loop 一定更快；
- Row Pipeline 是 thread-safe stream；
- Row Pipeline 支持 parallel stream。

极端 hot path 仍可以使用 generated `ColumnView` 或更底层的 runtime internal loop，但这属于显式性能工程路径，不是普通用户主入口。正式性能口径必须通过 benchmark lane 证明，至少区分 Row Pipeline、ColumnView / primitive loop、DTO materialization / Java Stream 三类路径。

## 13. Relationship to Java Stream

Row Pipeline 参考 Java Stream 的命名与使用体验，但不实现 `java.util.stream.Stream` 作为主 API。

原因：

- Java Stream 的元素通常是 object；SOMA Rows 的元素是 callback-scoped row cursor；
- Java Stream 没有原地 column update 作为一等 terminal；
- Java Stream 的 parallel / spliterator / collector contract 会引入 V1 不承诺的语义；
- Java Stream 无法表达 SOMA sidecar maintenance、view pinned、cursor lifecycle 和 table mutation boundary。

允许提供 boundary convenience：

```java
operations.fetchAll().stream()
```

这表示先 materialize DTO，再进入普通 Java Stream。它不是 hot path API。

## 14. Relationship to DTO and mutator API

`fetch(key)` 仍然是 keyed table 的直接 DTO materialization API：

```java
Operation operation = operations.fetch(key);
```

`fetchAt(rowIndex)` 是 dense table 的直接 DTO materialization API：

```java
Distance distance = distances.fetchAt(rowIndex);
```

Row Pipeline 用于 set-oriented traversal 和 batch-like mutation：

```java
operations
    .filter(o -> o.endMinuteAbsent())
    .update(o -> o.setPriority(o.priority() + 1));
```

Generated single-row mutator 仍然保留，用于明确 key 或 row index 定位的单 row update：

```java
operations.mutate(key)
    .setStartMinute(start)
    .setEndMinute(end)
    .commit();

distances.mutateAt(rowIndex)
    .setDistance(cost)
    .commit();
```

两者关系：

- `mutate(key)` / `mutateAt(rowIndex)` 是 precise single-row update；
- `filter(...).update(...)` 是 traversal-based update；
- 两者都不允许修改 key identity；
- 两者都必须维护 affected index/unique/order sidecar；
- DTO 是 detached materialized object，不是 live row proxy；修改 DTO 不会自动写回 table。

## 15. Examples

Game particle update：

```java
particles
    .filter(p -> p.alive())
    .update(p -> {
        p.setX(p.x() + p.vx() * dt);
        p.setY(p.y() + p.vy() * dt);
        p.setAge(p.age() + dt);
    });
```

Spatial index source：

```java
particles.findByCell(cellId)
    .filter(p -> p.alive())
    .sorted((a, b) -> Float.compare(a.z(), b.z()))
    .limit(100)
    .forEach(p -> renderer.draw(p.x(), p.y()));
```

FJSP candidate indicator update：

```java
UpdateResult result = machineCandidates.findByMachine(machineId)
    .update(c -> {
        long setup = setupTimes.fetch(setupKey(machineId, lastFamily, c.targetSetupFamily()))
            .setupMinutes;
        long effectiveReady = Math.max(c.baseReadyMinute(), machineReadyMinute);

        c.setSetupMinutes(setup);
        c.setEffectiveReadyMinute(effectiveReady);
        c.setFcfsValue(effectiveReady);
        c.setSptValue(setup + c.processingMinutes());
        c.setIndicatorReady(true);
    });
```

FJSP selected operation frontier cleanup：

```java
RemoveResult result = machineCandidates.findByOperation(operationKey)
    .remove();
```

Dense table boundary row-index access：

```java
Distance distance = distances.fetchAt(rowIndex);
```

Dense table hot row-index access through ColumnView：

```java
try (FloatColumnView distance = distances.distanceColumn()) {
    int cityCount = distances.size();
    for (int i = 0; i < cityCount; i++) {
        routeCost += distance.getFloat(i);
    }
}
```

Delete first ten unassigned operations：

```java
RemoveResult result = operations
    .filter(o -> o.endMinuteAbsent())
    .limit(10)
    .remove();
```

Key traversal：

```java
operations.keys().forEach(key -> ...);
List<OperationKey> keys = operations.keys().fetchAll();
```

Column traversal：

```java
operations.orderIdValues().forEachLong(id -> ...);
```

Boundary export：

```java
List<Operation> assigned = operations
    .filter(o -> o.endMinutePresent())
    .fetchAll();
```

Required first row：

```java
Operation next = operations.byDispatchOrder()
    .filter(o -> o.endMinuteAbsent())
    .firstOrThrow();
```

## 16. Codegen obligations

Processor / codegen 至少需要保证：

- generated row cursor accessor 与 schema field 类型一致；
- key field 不生成 mutable setter；
- optional field 生成 presence / absent / value / valueOr / clear / setter 语义；
- keyed table 生成 `fetch(key)`、`find(key)`、`containsKey(key)`、`mutate(key)` 和 `delete(key)`；
- dense table 生成 `fetchAt(rowIndex)`、`mutateAt(rowIndex)` 和 row-index boundary diagnostics；
- dense table 的 generated ColumnView 支持按 row index 直接读取 primitive value，用于热循环；
- generated index/order source method 返回同一 row pipeline type；
- table facade default traversal methods delegate to default packed row source；
- keyed table `keys()` 返回 key pipeline，不与 row pipeline 混淆；
- generated column pipeline method 使用 method API，不使用 public field-style API；
- primitive column pipeline 使用 primitive callback，避免不必要装箱；
- lambda callback 使用 Java 8 functional interface；
- cursor 类型不暴露 runtime internal handle；
- generated source deterministic；
- row pipeline API shape 进入 golden output；
- invalid API name collision 在 processor validation 阶段失败。

## 17. Runtime obligations

Runtime core 至少需要支持：

- row index iteration over packed storage；
- dense table row-index direct access and diagnostics；
- cursor binding / rebinding；
- primitive column getter / setter；
- optional bitmap read / write；
- one-shot pipeline consumed state；
- update terminal sidecar maintenance；
- structural mutation conflict detection；
- mutation terminal candidate `RowSequence` stability；
- deterministic dynamic row-index sorting buffer；
- released table / stale access / view pinned error；
- key pipeline and column pipeline traversal；
- `UpdateResult` / `RemoveResult` stats collection；
- row pipeline execution stats for diagnostics and benchmark smoke。

## 18. Non-goals

V1 Row Pipeline 不做：

- `java.util.stream.Stream` implementation；
- parallel stream；
- automatic lambda-to-index analysis；
- arbitrary join planner；
- ORM query DSL；
- general-purpose collection `map` / `flatMap` / `collect` framework；
- snapshot isolation；
- thread-safe table traversal；
- cross-table mutation transaction；
- user-visible row pointer or row proxy object graph；
- public custom `AccessPath` extension API。

未来如果需要用户自定义高性能查找或排序入口，可以在不改变 Row Pipeline 用户模型的前提下引入受控的 custom row selection API。但 V1 只保留 runtime 内部 `AccessPath` 抽象，不把 row pointer、sidecar handle 或任意 row sequence provider 暴露为 public contract。
