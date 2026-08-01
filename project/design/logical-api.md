# 逻辑层 API Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 generated user hierarchy、Table/Field/Stream capability、terminal、cursor/materialization 与 metadata surface

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 设计目标

普通 Java 用户应通过自然、类型安全、接近 Java Stream 心智的 API 操作 SOMA Table，
同时清楚区分 Table direct operation、Record selection、logical Field projection 和
detached result。本 Design 承接 BP-1、BP-2、BP-4、BP-5 和 BP-8。

Generated API 必须用 type shape 表达合法 capability；不得生成万能 Stream 后在
runtime 抛 `UnsupportedOperationException`。

## 2. 最短普通路径

```java
TransportTimeTable transportTimes = Soma.transportTimeTable();

transportTimes.add(new TransportTime(pair, 18L));

Optional<TransportTime> found = transportTimes.find(pair);
TransportTime required = transportTimes.get(pair);

long count = transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .count();

UpdateResult delayed = transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .update(editor ->
            editor.transportMinutes(
                Math.addExact(editor.transportMinutes(), 5L)));
```

普通 lambda 自动推断 nested `Record`/`Editor`/Stream types；用户不接触 Column、row
index、cursor、plan、transaction 或 release。

## 3. Generated hierarchy

```text
Soma
    -> SomaGroup
        -> TransportTimeTable
            -> machinePair
                -> fromMachine
                    -> value
            -> transportMinutes
```

| Generated shape | 用户语义 |
|---|---|
| `Soma` | composition 根、default Group/Table shortcut、Group factory、parallel configuration、metadata |
| `SomaGroup` | composition 中每种 Table 的唯一 instance navigation |
| `XxxTable` | capacity、add、optional point operation、Record source、Field、Index、metadata |
| `XxxTable.Record` | callback-scoped read-only logical record |
| `XxxTable.Editor` | Update callback 中的 staged logical editor |
| `XxxTable.Stream` | finite、single-source、one-shot Record pipeline |
| generated typed Field | Field/nested Field navigation、Field source、projection 与 metadata |
| generated Value/Table object | stable detached application value/carrier |

`table.machinePair.fromMachine.value` 代表整张 Table 上的 logical Field，不是某条
Record 的实际值，也不是 physical array。

## 4. Group 与 default Group

显式 Group：

```java
SomaGroup active = Soma.createGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable activeTimes = active.transportTimeTable();
MachineStateTable activeMachines = active.machineStateTable();
TransportTimeTable backupTimes = backup.transportTimeTable();
```

Default Group：

```java
TransportTimeTable first = Soma.transportTimeTable();
TransportTimeTable second = Soma.transportTimeTable();

assert first == second;
assert first == Soma.defaultGroup().transportTimeTable();
```

API 不包含 direct Group/Table construction、`XxxTable.create()`、default Group setter
或 reset。不同 Group 的同型 Table/Field endpoint 不可混用；Java type system 无法
编码 instance identity 时，execution 必须以 `INVALID_ARGUMENT` 拒绝 foreign owner。

## 5. Table direct operations

### 5.1 Capacity

```java
table.reserve(expectedRows);
int size = table.size();
int capacity = table.capacity();
```

`reserve` 返回 `void`，不增加 Record。V1 不提供 `trimToSize()`。
`expectedRows < 0` 为 `INVALID_ARGUMENT`；`expectedRows <= capacity` 是 no-op。
新 Table facade 的 `size/capacity` 均为 0；`defaultCapacity` 在第一次 positive
`reserve/add` 时作为 initial allocation hint 生效，不在 accessor/Group creation 时隐藏
分配 payload。

### 5.2 Add

```java
transportTimes.add(new TransportTime(pair, 18L));
```

`add` 返回 `void`。Operation 同步读取并校验 detached input、展开 Value、复制 leaf/
reference slot、完成 resource/Key/Index preflight 后发布一条 Record；Table 不保存
carrier object。Duplicate Key 产生 `DUPLICATE_KEY`，zero publication。

批量导入只使用 `reserve + repeated add`；V1 不提供 public Batch。每次 add 独立
atomic，第 N 次失败不回滚前 N-1 次成功 Record。

Application 可以复用 mutable detached Table object：

```java
TransportTime reusable = new TransportTime();
for (InputTransportTime input : inputs) {
    reusable.machinePair(input.machinePair());
    reusable.transportMinutes(input.transportMinutes());
    transportTimes.add(reusable);
}
```

这不是 live row/object-pool contract。Immutable generated Value 不能通过 mutation
复用。

### 5.3 Point query

只有 keyed Table 生成：

```java
Optional<TransportTime> found = transportTimes.find(pair);
TransportTime required = transportTimes.get(pair);
```

`find` 以 `Optional.empty()` 表达 absence；`get` missing 为 `MISSING_KEY`。两者返回
detached object，`get` 不插入 zero-value Record。

### 5.4 Point update

```java
UpdateResult result =
    transportTimes.update(new TransportTime(pair, 24L));
```

- replacement Key 定位 existing Record；
- missing 为 `MISSING_KEY`，不 upsert；
- Key 不修改；全部非-Key Field 构成一次 Table-local atomic replacement；
- point no-op 返回 `matched() == 1 && changed() == 0`；
- real replacement 返回 `1/1`。

不提供 `transportTimes.find(pair).update(...)`；`find` 是标准 Optional，控制流由
application 显式表达。

### 5.5 Point remove

```java
RemoveResult result = transportTimes.remove(pair);
```

Missing 是 normal no-op：`removed() == 0`；命中并成功删除为 `1`。Keyless Table 不
生成 point API，也不使用 physical row index 替代。

### 5.6 Clear absence

所有 Table 都不生成 direct `clear()`。清空的 canonical 表达是：

```java
table.stream().remove();
```

执行层可以识别 whole-Table selection 并优化，但不能增加第二套同义 API。

## 6. Stream sources

### 6.1 Whole Table

```java
transportTimes.stream();
transportTimes.parallelStream();
```

Whole Table 从完整 Record domain 开始。

### 6.2 Index selection

```java
machineEvents.byMachineId(machineId).stream();
machineEvents.byMachineId(machineId).parallelStream();
```

`by<FieldName>` 返回 generated Table-local `IndexSelection`。它只提供 `stream()` 和
`parallelStream()`，不直接复制 `filter/count/update/remove` terminal。Index 值可
重复，selection 为 `0..N` ordered Record subset。

`IndexSelection` 是 immutable、reusable source descriptor，不是 one-shot Pipeline；它
保存 typed exact-match value 和 Table owner，每次 `stream()/parallelStream()` 创建新的
linked-chain Pipeline，并在 terminal-start late-bind current Index state。构造 selection
不取得 Table admission，也不缓存 record slots/live cursor。

Key 不生成 `byKey(key).stream()`。Point query/update/remove 只使用 Table direct API；
Key logical Field 仍可以只读 stream/project。

### 6.3 Field-first 与 Record-first

以下两个入口同时生成，并 lowering 为同一个 canonical Field plan：

```java
table.transportMinutes.stream();

table.stream()
    .select(table.transportMinutes);
```

Field-first 固定 whole-Table Record domain；Record-first 继承已有 selection。两者必须
保持相同 Field identity、owner、encounter order 和 currentness。

Nested Field 同样可用：

```java
table.machinePair.stream();
table.machinePair.fromMachine.stream();
table.machinePair.fromMachine.value.stream();
```

Projection endpoint 必须属于来源 Stream 的同一 Table instance；foreign Group/Table
endpoint fail closed。

### 6.4 Relation Table

Relation Table 没有专用 navigation/source：

```java
JobOperationTable operations = group.jobOperationTable();
JobMachineEligibilityTable eligibilities = group.jobMachineEligibilityTable();

operations.byJobId(jobId).stream();
eligibilities.byJobId(jobId).stream();
eligibilities.byMachineId(machineId).stream();
```

不生成 owner-scoped child Table accessor，也不通过 detached entity object 导航 live
state。

## 7. Stream kinds 与 capability narrowing

| Stream kind | 保留的 SOMA identity/lineage | Query | Update | Remove |
|---|---|---:|---:|---:|
| Record | Record membership | 是 | 是 | 是 |
| Field | logical Field slot 与 source Record | 是 | 非 Key root | 否 |
| Mapped | 无 live identity | 是 | 否 | 否 |

Intermediate operation：

| 输入 | Operation | 输出 | Query | Update | Remove |
|---|---|---|---:|---:|---:|
| Record | `filter/sorted/skip/limit` | Record | 是 | 是 | 是 |
| Record | `select(field)` | Field | 是 | 按 Field root role | 否 |
| Record | `map/mapToInt/mapToLong/mapToDouble` | Mapped | 是 | 否 | 否 |
| Field | `filter/sorted/skip/limit` | Field | 是 | 保持原能力 | 否 |
| Field | `distinct` | Mapped value | 是 | 否 | 否 |
| reference/Value Field | `map/mapToInt/mapToLong/mapToDouble` | Mapped | 是 | 否 | 否 |
| primitive Field | `map/mapToObj/mapToXxx` | Mapped | 是 | 否 | 否 |
| Mapped | `filter/sorted/distinct/skip/limit/map/mapToXxx` | Mapped | 是 | 否 | 否 |

`select(field)` 是 typed logical projection，不是 `map(Function)` 的别名。它保留 Field
slot lineage，丢弃 Record removal capability。

Record Stream 不提供 `distinct()`；Record membership 本身不重复，按 payload 合并会
让 equality 与后续 mutation membership 含混。Field `distinct()` 只对具有 intrinsic
distinct 的 type 生成，并立刻变成 query-only Mapped Stream。

Key root 及全部 nested Fields 永远 read-only；它们的 generated Stream 不包含
`update()`。普通 `@SomaField`/`@SomaIndex` 在 lineage 未被 distinct/map 破坏时可以
Field update。

## 8. Record、Editor 与 Value View

### 8.1 Record/Editor cursor

`Table.Record` 是 callback-scoped read-only cursor；`Table.Editor extends Record` 是
Update callback 的 staged-mutation cursor。

- sequential terminal 复用常数个 cursor/View；
- parallel terminal 每个 active participant 使用常数个，总量 O(P)；
- 不得跨 callback、terminal、Table、thread/participant 或 execution 使用；
- 不得从 mapper 返回、保存为业务对象或用 identity/equality 表达 Record；
- 当前 callback dynamic scope 内同步 helper 使用合法；
- 需要 stable object 时调用 `fetch()`。

`Record.fetch()` 返回当前 Record 的 detached generated Table object；`Editor.fetch()`
返回包含当前 staged modifications 的 detached candidate，不触发 publish。

Runtime 必须检测 callback/terminal 已结束、foreign Table/thread/participant/execution、
inactive cursor 和可识别 direct mapper-result escape，并以
`CALLBACK_SCOPE_VIOLATION` fail closed。

Java 8 无法区分同 execution、同 participant 中同一 reusable object 的旧 alias。保存
旧引用并在后续 callback 使用属于非法 stateful/interfering callback，不承诺检测；
需要保存数据只能 `fetch()`。SOMA 不虚构 Java borrow checker。

### 8.2 Flattened Value View

完整 Value Field callback 不按 Record materialize immutable Value。Generated shape
分离 reusable callback `V.View` 与 detached Value `V`：

```java
table.machinePair.stream().forEach(view -> {
    long from = view.fromMachine().value();
    MachinePairKey stable = view.fetch();
    consume(from, stable);
});
```

- `filter/map/forEach` 接收 `V.View`；
- `findFirst/toList/toArray` 返回 stable `V`；
- `V.View.fetch()` 只在当前 callback 内调用并显式 materialize；
- mutable Value Field update 使用 `Function<V.View,V>`；
- nested Record navigation 和 View getter 直接访问 flattened leaves。

不得退回 generic `ValueFieldStream<V>` 并隐藏 O(N) DTO allocation。

## 9. Mapped Stream

`map(Function)` 是 lazy、one-to-one、stateless reference mapping；primitive 使用
`mapToInt/mapToLong/mapToDouble`：

```java
String[] routes = table.stream()
        .map(record -> calculateRoute(record))
        .toArray(String.class);

long[] ids = table.stream()
        .mapToLong(record -> record.machinePair().fromMachine().value())
        .distinct()
        .toArray();
```

任何 map 产生 detached value，终止 Record/Field identity 与 mutation lineage。
Reference mapper 可以返回 null；`filter/count/forEach/toList/toArray/distinct` 能处理
null，explicit Comparator `sorted/min/max` 如何比较 null 由 application comparator
决定。任何 reference `findFirst/min/max` 的 logical selected result 为 null 时产生
`NULL_VALUE_UNSUPPORTED`，因为 V1 不引入 nullable Optional；`sorted/toList/toArray`
仍可包含 null。Arbitrary mapped reference 不提供 no-arg natural order。

Mapped reference `distinct` 使用 Java `equals/hashCode`，primitive distinct 使用
primitive value。Application 必须保证 mapped object 在 terminal 期间的
equality/hash/order non-interfering。

## 10. Query terminals

| Terminal family | Record | Field | Mapped |
|---|---:|---:|---:|
| `count`、`anyMatch/allMatch/noneMatch`、`findFirst`、`forEach` | 是 | 是 | 是 |
| `toList` / legal typed `toArray` | 是 | 是 | 是 |
| type-eligible `sum/average/min/max` | 否 | 是 | 是 |
| explicit Comparator `min/max` | 是 | 是 | 是 |

V1 只保留 `findFirst`，不提供 `getFirst`。Empty selection 使用对应 Optional shape；
Record `findFirst` 返回 detached Table object。`min/max` 对 comparator/natural-order equal
values 返回 canonical encounter order 中第一个，顺序/并行一致。

`parallelStream().forEach` callback side-effect order 不保证；严格 encounter order 使用
`stream().forEach`。V1 不提供 `forEachOrdered`。

## 11. Materialization 与 arrays

所有 terminal result detached；没有结果保留 Stream、Record、row position 或 backing
storage。

| Stream | V1 array API |
|---|---|
| Record | 无参 `Xxx[] toArray()`，返回 detached Table object array |
| runtime-reifiable typed reference Field | 无参 typed `toArray()` |
| primitive Field/Mapped | 无参 primitive `toArray()` |
| arbitrary reference `MappedStream<R>` | `<A> A[] toArray(Class<A> componentType)` |
| parameterized reference Field，例如 `List<String>` | `toList()`；不提供伪 typed array |

Reference Mapped Stream 不提供无参 `Object[] toArray()`、
`toArray(IntFunction<A[]>)` 或 `map(Class, Function)`。`toArray(Class<A>)`：

- 允许 exact type、父类型和 `Object.class`；
- empty/all-null 仍返回正确 JVM component type；
- null element 可进入 reference array；
- null/primitive component type 或 incompatible non-null value 为 `INVALID_ARGUMENT`；
- 不泄漏 `ClassCastException`/`ArrayStoreException`；
- 只用 `Array.newInstance` 分配，不做 schema/object reflection；
- 顺序/并行结果 type、content 和 encounter order 等价。

普通 API 只提供无预算 `toList()`/`toArray()`；需要业务上限时 pipeline 使用
`limit(n)`。Implementation 必须 checked cardinality、array length、growth 和 byte
arithmetic；representation boundary 为 `RESOURCE_LIMIT_EXCEEDED`。真实 OOME 保持 JVM
`OutOfMemoryError`。

Primitive `toList()` 只在 terminal result boundary 显式 boxing，不能把 boxing 提前
到 scan/filter/map/aggregate hot path。

## 12. Update terminal

Record update：

```java
UpdateResult result = table.stream()
        .filter(record -> record.transportMinutes() > 0L)
        .update(editor -> {
            long updated = Math.addExact(editor.transportMinutes(), delay);
            editor.transportMinutes(updated);
        });
```

Field update：

```java
UpdateResult result = table.stream()
        .filter(record -> predicate(record))
        .select(table.transportMinutes)
        .update(value -> Math.addExact(value, delay));
```

两者 lowering 到 Table-local staged mutation。Key Field 没有 updater；callback 不
直接写 authoritative storage。`matched()` 是 selection size，`changed()` 是至少一个
受控 slot 按 logical equality 真正变化的 Record 数；`0 <= changed <= matched`。

Logical no-op 不 publish，也不递增 internal version。Opaque Object slot 使用
reference identity 判断 changed；referent 内部 mutation不属于 Table change。

## 13. Remove terminal

只有 Record Stream 可以删除 Record：

```java
RemoveResult result = table.stream()
        .filter(record -> record.transportMinutes() <= 0L)
        .remove();
```

Field Stream 不提供 `remove()`/`removeRows()`；Field 管理值的 query/update，不拥有
Record membership。按 Field 条件删除时，从 Table/Index Record source 开始并使用
Record predicate。

## 14. Metadata namespace

Soma、Group、Table 和 Field 都提供 advanced public `_metadata()`：

```java
Soma._metadata();
group._metadata();
table._metadata();
table.machinePair.fromMachine.value._metadata();
```

前导 `_` 是“非日常业务入口”的视觉标记，不是 access control、experimental 或
unstable。Generated `_` namespace 由 SOMA 保留；V1 当前只使用 `_metadata()`。

Minimum information contract：

| Entry | Schema/identity information | Runtime information |
|---|---|---|
| Soma | composition identity、Table schema enumeration | parallel backend/config state snapshot |
| Group | composition、是否 default | 无 |
| Table | declaration/object/Table identity、Field tree、optional Key、Index Fields、default capacity | immutable `size/capacity` snapshot |
| Field | local name、logical path、declared logical type、role、nullability、nested Fields | 无 |

Metadata 是 stable、read-only facade/snapshot：

- schema metadata 可以 immutable/cache；runtime metadata 是一次自洽观察；
- Table metadata 同一 snapshot 中的 `size/capacity` 必须自洽；`size` 与 direct
  `table.size()` 表达同一事实，不另起 `rows/rowCount`；
- observation side-effect free，不创建 Table、lazy initialize、rebuild Index 或保留
  可 GC object；
- Soma metadata 不返回、替换或关闭 raw ForkJoinPool；
- 不暴露 physical Column/ordinal、row/cursor/array、mutable runtime、planner、
  `stateVersion`、exact retained heap、`retainedBytes`、`estimated owned storage` 或
  Index statistics；
- 当前不提供 schema/API version、schema fingerprint 或 generated Index accessor
  name；
- 各层不为了形式对称强制生成空 `schema()`/`runtime()` facade。

若未来 profiling/tooling 证明需要 owned-storage estimate，必须另行定义是否包含
array header、reference slot 与 shared referent；不能把 Java Object graph/GC 行为包装
成 exact retained-memory promise。

Exact carrier、enum、path representation 与 snapshot method 由
[Generated Java API Signature Design](generated-api-signatures.md)固定；实现不能自行
改名、增加 carrier 或扩张 public runtime surface。

## 15. API absence contract

Generated surface 不包含：

- `XxxTable.create()`、public Group/Table constructor、default Group setter/reset；
- `AddResult`、direct `clear()`、`trimToSize()`、public Batch；
- keyless point API、`byKey` selection、rekey；
- `@SomaUnique`/secondary unique；
- Field remove、Mapped update/remove、Record distinct；
- `getFirst`、`findAny`、`unordered`、`forEachOrdered`；
- `peek`、generic `flatMap`、join、concat/union、Collector、`generate/iterate`、
  async/Future/infinite source；
- Pipeline `.parallel()`/`.sequential()`、per-Stream pool/parallelism overload；
- public Column、row identity、cursor construction或 live Record result；
- mapped noarg Object array、array-factory overload、`TypeToken` container；
- runtime `UNSUPPORTED_OPERATION` 代替 compile-time absence。

## 16. Complete reference journey

```java
SomaGroup group = Soma.createGroup();
TransportTimeTable times = group.transportTimeTable();

times.reserve(4096);
for (InputTransportTime input : inputs) {
    times.add(new TransportTime(
            input.machinePair(),
            input.transportMinutes()));
}

TransportTime current = times.get(pair);
Optional<TransportTime> maybe = times.find(optionalPair);

UpdateResult replaced = times.update(new TransportTime(pair, 24L));

OptionalLong shortest = times.stream()
        .filter(record ->
            record.machinePair().fromMachine().value()
                == fromMachine.value())
        .select(times.transportMinutes)
        .min();

UpdateResult delayed = times.stream()
        .filter(record ->
            record.machinePair().fromMachine().value()
                == fromMachine.value())
        .select(times.transportMinutes)
        .update(minutes -> Math.addExact(minutes, disruptionDelay));

RemoveResult removed = times.stream()
        .filter(record -> record.transportMinutes() <= 0L)
        .remove();
```

Cross-Table composition remains ordinary Java control flow; each operation keeps its own Table
admission and atomicity.

## 17. Implementation admission Gates

Production generated API 必须用 generated source、`javap`、independent Java 8 consumer
和 compile-negative matrix 固定：

- constructor/accessor/nested type signature 与 lambda inference；
- default/explicit Group identity 和 construction boundary；
- Key/Index/Field/Stream method existence and absence；
- Record/Editor/Value View O(1)/O(P)、`fetch()` 与 scope failure；
- primitive specialization/no boxing；
- typed array/runtime component contract；
- cross-Group Field owner guard；
- metadata exact carrier；
- exact Java signature 与 operation property matrix；
- one-shot、currentness、concurrency、mutation/failure runtime behavior；
- three reference scenario expression and profile。

现有可行性结论见
[P2 Generated API Conformance](../conformance/p2-generated-api-feasibility.md)。
精确 projection 见
[Generated Java API Signature Design](generated-api-signatures.md)。
