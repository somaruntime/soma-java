# SOMA Java 逻辑层 API 草稿

类型：Temporary

状态：candidate-for-evaluation

Owner：SOMA V1 产品与 API 重构专题

事实范围：基于当前专题决策形成的一套候选用户语义、generated API 形状与使用示例

非事实范围：当前已经存在的 generated signature、正式 Blueprint/Design、
runtime 实现、兼容性承诺、性能结论和实施授权

最后审查日期：2026-07-30

> 本文中的 Java 代码是目标 API 草稿，不是当前 checkout 已生成、已编译通过的
> API。精确名称、类型和参数必须在设计确认并实施后，由真实 generated source、
> `javap` golden 和独立 Java 8 consumer compile/run 决定。

## 1. 文档角色与最短普通路径

本文件是[专题决策记录](README.md)的 API 投影，只回答一个问题：

> 普通 Java 用户应该怎样理解和使用 SOMA，而不必理解 Column、candidate scratch、
> epoch、cursor、staging、planner 或 execution protocol？

它不保存独立设计理由，也不替代正式 Blueprint/Design。若示例、术语或待决状态与
专题 README 不一致，以 README 的最新 D 编号为准。

### 最短普通路径

普通单 Group application 不需要先理解 Group：

```java
TransportTimeTable transportTimes = Soma.transportTimeTable();

transportTimes.add(new TransportTime(pair, 18L));

TransportTime snapshot = transportTimes.get(pair);

long slowCount =
    transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .count();

UpdateResult delayed =
    transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .update(editor ->
            editor.transportMinutes(
                Math.addExact(editor.transportMinutes(), 5L))
        );
```

在这条路径中：

- `Soma.transportTimeTable()` 始终返回 default Group 中同一个 Table；
- `TransportTime` 是 detached OOP boundary object；
- `record` / `editor` 由 Java 8 分别推断为
  `TransportTimeTable.Record` / `Editor`，普通代码不写出类型名；
- `.stream()` 表达一次 finite、lazy、one-shot Table-local operation；
- `count()` 是 Query terminal，`update(...)` 是 whole-selection atomic Update
  terminal。

只有需要双缓存、测试隔离或多份独立状态时才显式创建 Group：

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();
```

### 用户需要理解的完整概念

目标用户只需要理解：

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field

Table/Field
    -> stream()
        -> intermediate operation*
            -> one Query/Update/Remove terminal

Table
    -> direct add / point query / point update / point remove / capacity

Soma/Group/Table/Field
    -> _metadata()
```

物理 Column 仍然存在于存储层和执行层，但完全退出普通用户 API：

```text
Record Selection
    -> Logical Field/Value Projection
        -> Physical Column Access
```

### 决策来源

| API 投影 | 专题决策 |
|---|---|
| 四层职责与单一用户模型 | D-001、D-003 |
| Stream source/intermediate/terminal | D-002、D-009、D-010、D-011、D-013 |
| storage value-state 与 ordinary Object boundary | D-004、D-005 |
| 无 Batch / binary relation | D-006、D-007 |
| Group uniqueness、GC 与 detached object | D-012、D-014 |
| default Group 与 nested Record/Editor/Stream | D-015 |

## 2. 候选用户对象层级

本草稿选择 `Soma` 根入口、generated Group/Table/Field member 风格：

```java
SomaGroup defaultGroup = Soma.defaultGroup();
SomaGroup active = Soma.createGroup();

Soma.transportTimeTable()
defaultGroup.transportTimes
active.transportTimes
active.machineStates

group.transportTimes
table.machinePair
table.machinePair.fromMachine
table.machinePair.fromMachine.value
```

它更接近用户提出的：

```text
soma.group
group.table
table.field
field.nestedField
```

其中 `Soma` 是 generated composition 的根入口和 factory namespace，
`SomaGroup`、Table 与 Field 是 compiler-generated、typed capability endpoint：

- `Soma.createGroup()` 每次创建一个相互独立的 Group；
- `Soma.defaultGroup()` 重复调用返回同一个 generated default Group；
- `Soma.transportTimeTable()` 返回 default Group 中唯一的
  `TransportTimeTable`；
- Group 上的 Table member 指向该 Group 中唯一的 root Table instance；
- Table 上的 Field member 表示 logical field，不是 live ColumnView；
- nested Field member 保留 `@SomaValue` 的逻辑结构；
- Field endpoint 不携带 current row position、iterator state 或 borrowed array；
- 真正的 operation 只有在调用 `stream()` 或 Table direct method 时开始。

Table 与 Field navigation 确认生成 `public final` typed member，以保持
`group.transportTimes`、`table.machinePair.fromMachine.value` 这一用户层级。
processor 必须为与 SOMA 保留 operation 名冲突的 schema name 提供 compile-time
diagnostic，不能在生成后静默改名。

完整 capability tree：

```text
Generated Soma
    -> _metadata()
    -> defaultGroup()
    -> schema-specific default Table accessor
    -> createGroup()
        -> generated SomaGroup
        -> _metadata()
        -> generated root Table member
            -> stream()
            -> _metadata()
            -> direct operation
            -> generated logical Field member
                -> stream()
                -> _metadata()
                -> nested Field member
            -> owned Child Table
                -> same Table capability tree
```

`Soma._metadata()` 是根级 meta-operation；generated `Soma` 只强持有一个 typed
default Group，不建立可枚举全部 live Group 的 JVM-global registry，也不提供跨
Group 数据操作。

## 3. 示例 Schema

以下 schema 只用于展示逻辑层。`@SomaValue` 的 immutable/canonical constructor
规则已确认；`@SomaTable` detached carrier 的 materialization constructor 仍需在
后续 schema 专题裁决精确生成方式。

```java
@SomaValue
public final class MachineId {
    @SomaField public long value;
}

@SomaValue
public final class MachinePairKey {
    @SomaField public MachineId fromMachine;
    @SomaField public MachineId toMachine;
}

@SomaTable(name = "transport_times", defaultCapacity = 4096)
@SomaIndex(
    name = "by_from_machine",
    fields = {"machinePair.fromMachine.value"})
public final class TransportTime {
    @SomaKey public MachinePairKey machinePair;
    @SomaField public long transportMinutes;

    public TransportTime(
            MachinePairKey machinePair,
            long transportMinutes) {
        this.machinePair = machinePair;
        this.transportMinutes = transportMinutes;
    }
}

@SomaTable(name = "machine_states", defaultCapacity = 256)
public final class MachineState {
    @SomaKey public MachineId machineId;
    @SomaField public long availableMinute;
    @SomaField public boolean enabled;

    public MachineState(
            MachineId machineId,
            long availableMinute,
            boolean enabled) {
        this.machineId = machineId;
        this.availableMinute = availableMinute;
        this.enabled = enabled;
    }
}
```

编译后，用户面对的是 schema-specific generated API，而不是 generic metadata
interpreter。

`@SomaValue` 采用 compiler-owned immutable value contract：

- compiler 生成 canonical 全字段 constructor；
- compiler 将 class/Field 固化为 final shape；
- compiler 生成稳定的 `equals/hashCode/toString`；
- 不生成默认构造函数；
- application 不手写与 canonical generated member 冲突的 constructor；
- 同一 immutable value instance 可以安全共享，但不能通过改写字段代表另一个值。

`@SomaTable` 则是 mutable detached carrier。它可以只有 application 需要的全字段
constructor，不应为了 storage 写入强制无业务意义的默认构造函数。由于
`get/find` 需要重建 detached Table carrier，processor 仍必须在编译期解析一个
明确的 Table materialization constructor。当前示例采用“按 logical top-level
Field declaration order 的全字段 constructor”作为候选规则；精确匹配、overload
ambiguity 和 schema evolution diagnostic 尚待裁决，但不得退回 reflection、
`Unsafe` 或强制 zero-argument constructor。

## 4. 候选 generated surface

下面的类型名只表达角色：

| 候选 generated shape | 用户看到的职责 |
|---|---|
| `Soma` | generated composition 根入口、default Group/Table accessor、Group factory 与 Soma metadata |
| `SomaGroup` | 每 Group 唯一 Table 导航与 Group metadata |
| `TransportTimeTable` | Table direct operation、record stream、Field endpoints |
| `MachineId` / `MachinePairKey` | compiler-generated immutable `@SomaValue` |
| `TransportTime` | mutable detached add/update input 或 get/find materialized snapshot |
| `TransportTimeTable.Record` | callback-scoped read-only logical record |
| `TransportTimeTable.Editor` | `extends Record`；update callback 内可读取当前值、只补充允许 mutation 的 logical record operation |
| `TransportTimeTable.Stream` | finite、single-source、one-shot record selection pipeline |
| typed Field endpoint | Field stream、nested Field、Field metadata |
| typed primitive/value stream | primitive 或 `@SomaValue` operation |
| `UpdateResult` | matched/changed 等 detached operation result |
| `RemoveResult` | matched/removed 等 detached operation result |
| typed metadata facade | immutable descriptor 或 detached runtime observation |

普通用户不直接看到：

- `ColumnView` / `ColumnTraversal`；
- current Index、`IndexSnapshot` 或 relocation；
- Candidate/IndexBuffer/sort scratch；
- UpdateCursor、publish token 或 transaction object；
- Definition/Template/Invocation/DataFlow Context；
- Batch staging object；
- physical plan、parallel morsel 或 execution vector。

三个 Table-nested 类型是 public generated semantic contract，但普通调用由 Java 8
lambda 和链式返回类型自动推断，不要求用户 import、构造或实现。其 implementation、
row position、cursor 和 column accessor 仍完全 internal。

普通路径见第 1 节；只有保存 pipeline 或编写 helper 时才需要显式写出
`TransportTimeTable.Stream` / `Record` / `Editor`。`Record` 与 `Editor` 只能在
同步 callback 内使用，不能保存为跨 operation live handle；`Stream` 是 one-shot，
terminal 后不得复用。

`TransportTime` 只在 API boundary 作为 detached logical object 使用。Table
内部仍以 primitive/reference columns 保存事实，不保留这个 carrier object，也不把
它变成 live record proxy。

## 5. Group 与 Table

### 5.1 Soma 根入口与显式 Group

processor 为一个 schema/group composition 生成 typed `Soma` 根入口和
`SomaGroup`：

```java
SomaGroup active = Soma.createGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable transportTimes = active.transportTimes;
MachineStateTable machineStates = active.machineStates;
```

`active` 与 `backup` 是两个相互独立的 ownership aggregate，可以各自包含同一种
generated root Table。每个 Group 内，每个 root Table identity 最多一个实例：

```java
active.transportTimes   // active 中唯一 TransportTimeTable
active.machineStates    // active 中唯一 MachineStateTable

backup.transportTimes   // backup 中另一个独立 TransportTimeTable
backup.machineStates    // backup 中另一个独立 MachineStateTable
```

不存在：

```java
active.transportTimes("first");
active.transportTimes("second");
```

若业务确实需要两个不同角色，应声明两个不同 Table identity，或者建立两个不同
Group。

`Soma` / `SomaGroup` 在这里是 generated composition 类型，不是依赖 reflection
解析任意 Table class 的 generic runtime registry。它们的精确 package、composition
declaration 与生成规则仍需单独裁决。

### 5.2 共享且可显式取得的 default Group

普通单 Group 场景直接取得 default Table：

```java
TransportTimeTable transportTimes = Soma.transportTimeTable();
```

它是 default Group 中唯一 Table instance 的 schema-specific typed shortcut：

```java
Soma.transportTimeTable()
    == Soma.defaultGroup().transportTimes;
```

重复调用 accessor 始终返回相同实例，不创建新 Table，也不抛异常：

```java
TransportTimeTable first = Soma.transportTimeTable();
TransportTimeTable second = Soma.transportTimeTable();

assert first == second;
```

需要完整 composition 或第二份独立状态时：

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable activeTimes = active.transportTimes;
TransportTimeTable backupTimes = backup.transportTimes;

assert activeTimes != backupTimes;
```

目标 API 不提供 `TransportTimeTable.create()`、`defaultInstance()`、
`Soma.setDefaultGroup(...)` 或 `resetDefaultGroup()`。Group creation 统一属于
`Soma`；Table 只导航或操作已经属于某个 Group 的实例。default Group 是稳定
ownership anchor，不是可切换的 active pointer。

Java 中 singleton 的准确范围是每个 generated Soma composition、每个 ClassLoader
一个 default Group；普通单 ClassLoader application 中表现为 JVM-global
singleton。实现使用一个 generated typed static reference，不维护 generic runtime
registry。

### 5.3 Ownership 与 GC

逻辑层不提供 `release()`、`close()`、`AutoCloseable` 或 ownership token：

- root Table 直接属于一个 Group；
- owned Child Table 的直接 owner 是 parent record，但它传递地属于同一个 Group；
- default Group 由 generated `Soma` 强引用，持续到对应 ClassLoader 卸载或 JVM
  退出；
- `Soma.createGroup()` 创建的显式 Group、Table、Field facade 和底层 heap arrays
  不再可达时，由 Java GC 回收；
- runtime 不得用 live Group registry、background thread、永久 ThreadLocal 或
  metadata observation 意外保留已经不可达的显式 Group；
- GC 回收时机由 JVM 决定，SOMA 不承诺方法返回后立即归还 heap memory。

测试隔离、双缓存和独立 computation 应使用 `Soma.createGroup()`。default Group
的 thread-safe publication 只保证 singleton 初始化，不表示 Table operation
支持 concurrent access。

仍处于使用中的大 Table 应通过 capacity operation 控制内存，而不是依赖
“先 release 再继续使用”的失效模型。

## 6. Table direct operation

Table root 只保留直接作用于该 Table owner 的操作。

### 6.1 Capacity 与基本状态

```java
transportTimes.reserve(expectedRows);

int size = transportTimes.size();
int capacity = transportTimes.capacity();
```

`reserve` 只做 resource/capacity admission，不增加 logical records。

V1 不提供 `trimToSize()`。它不是使用 SOMA 的必要能力，也不应为了理论上的
capacity 收缩增加 API、reallocation failure 和性能心智负担。若未来出现真实、
重复、无法由 Group GC 或 Table 重建解决的内存压力，再用独立证据重新裁决。

### 6.2 Add

`add` 没有 existing-record selection，因此是 Table direct operation：

```java
transportTimes.add(new TransportTime(pair, 18L));
```

`TransportTime` 是本次 operation 的 detached input，不是 live record：

1. SOMA 在调用边界读取并校验它的逻辑字段；
2. `@SomaValue` 递归展开为 primitive/reference columns；
3. ordinary Object field 只复制 reference slot；
4. validation 全部成功后，一次发布新 row；
5. Table 不保留 `TransportTime` carrier object。

因此调用返回后再修改 input object，不会改变已经写入的 primitive 或
compiler-flattened value；ordinary reference field 的 referent 仍按已确认规则由
application 自己维护。`@SomaTable` carrier 不需要默认构造函数；
`@SomaValue` 也只生成 canonical 全参构造函数。

这里采用一条候选产品边界原则：

> 对 application 暴露普通 Java/OOP 语义对象；在 SOMA 内部由编译生成层将对象
> lowering 为 columnar storage 与专门化执行。

更简洁地说是：

```text
Object-oriented application boundary
    -> compiler-generated lowering
        -> data-oriented SOMA storage/execution
```

这不表示 application 只能使用 mutable object graph，也不禁止 primitive、array、
lambda、immutable value 或普通 Java 控制流。OOP 是用户语义和集成边界，不是要求
SOMA hot path materialize object 的实现约束。

多个 add 仍是多个独立 operation：

```java
transportTimes.reserve(input.size());

for (InputTransportTime value : input) {
    transportTimes.add(
        new TransportTime(
            value.machinePair(),
            value.transportMinutes()));
}
```

第 `N` 次 add 失败时，前 `N-1` 次已经成功发布；V1 不提供 public Batch object。
单次 add 的 duplicate Key、invalid value、overflow 或 resource refusal 使用 stable
structured failure，不用 `false` 隐藏原因。因此本草稿暂不为 `add` 增加
`boolean` 或冗余 `AddResult`。

#### 6.2.1 Application-owned `@SomaTable` carrier reuse

由于 `add(value)` 是同步 operation，且 SOMA 在返回前已经读取并拆列 detached
input，application 可以选择复用 mutable `@SomaTable` carrier：

```java
TransportTime reusable =
    new TransportTime(firstPair, firstMinutes);

for (InputTransportTime value : input) {
    reusable.machinePair = value.machinePair();
    reusable.transportMinutes = value.transportMinutes();
    transportTimes.add(reusable);
}
```

这种复用属于 application optimization，不是 SOMA Batch、live record 或 object
pool contract。安全条件是：

- 只复用 mutable `@SomaTable` carrier；immutable `@SomaValue` 不允许改写复用；
- 每次 add 前完整覆盖所有逻辑字段，避免携带上一行状态；
- 只能在前一次同步 add 返回后修改 carrier；
- 同一个 carrier 不得并发用于多个 operation；
- primitive 与 compiler-flattened `@SomaValue` 在 add 返回后已被复制，可安全
  为下一行给 Table carrier 重新赋值；不能修改 `@SomaValue` 自身的 final Field；
- ordinary Object field 只复制 reference slot；后续修改同一个 referent 会被所有
  指向它的 rows 观察到，不能把 carrier reuse 误解为 referent deep-copy。

短生命周期对象通常也是 JVM 擅长处理的形状。没有 profile/benchmark 证据时，
不把手工对象池或复用设为默认最佳实践；它们可能增加 stale-field bug、retained
objects 和维护成本。只有证明百万级导入的 carrier allocation 成为真实瓶颈后，
才先比较 application-owned reuse；仍不足时再评估 generated field-argument
overload，V1 不预先维护第二套 canonical add API。

### 6.3 Key point query

```java
Optional<TransportTime> found = transportTimes.find(pair);
TransportTime existing = transportTimes.get(pair);
```

- `find` 明确表达 absence；
- `get` 在 missing 时抛出 stable structured failure；
- 返回值是 detached logical result，不是 live record。

这里不照搬 `java.util.Map#get` 的 nullable contract，也不照搬 C++ container：
C++ `at(key)` 通常在 missing 时抛异常，而 `operator[]` 可能插入默认值。SOMA
明确规定：

```text
find(key) -> Optional<detached row>
get(key)  -> detached row，missing 时 structured failure
```

`get` 绝不隐式插入零值 row。`get/find` 会 materialize 一个逻辑对象；只读取单个
primitive Field 的高频路径应使用 generated Field projection，避免不必要的整行
对象分配。

### 6.4 Key point update

```java
UpdateResult result =
    transportTimes.update(new TransportTime(pair, 24L));
```

`update(value)` 从 value 的 `@SomaKey` 取得 identity，要求对应 row 已存在，并把
其 payload 作为一个 Table-local atomic replacement 发布：

- missing 时抛出 stable `missing_key` failure，不执行 upsert；
- replacement 的 Key 只用于定位和一致性校验，不支持借此 rekey；
- Table 读取并拆列 value，但不保留 carrier object；
- `UpdateResult` 可以报告 `matched == 1` 与 `changed == 0/1`；
- 它与 selection update lowering 到同一个 mutation kernel。

下面这个接口不进入草稿：

```java
transportTimes.find(pair).update(replacement);
```

因为已经确认的 `find(pair)` 返回标准 `Optional<TransportTime>`，`Optional` 没有
`update`，而自定义一个可更新的 `RowRef`/handle 会重新引入 live identity、
currentness 和结构失效心智模型。若 application 先 `find` 再决定是否更新，应由
普通 Java 控制流调用 `transportTimes.update(replacement)`。

### 6.5 Key point remove

```java
RemoveResult result = transportTimes.remove(pair);
```

missing 是返回 `removed == 0`，还是让 `remove(key)` 在 missing 时产生 structured
failure，后续再裁决；草稿不再引入另一套 `requireRemove` 命名。

## 7. Stream source

`.stream()` 表示开始描述一个 finite、single-source、lazy SOMA operation。

### 7.1 Whole Table record source

```java
transportTimes.stream()
```

逻辑上从当前 whole Table record domain 开始；物理上可以 lowering 为 contiguous
row-position range。

### 7.2 Key/Unique/Index record selection

Key/Unique/Index 都投影为 record selection，不形成另一套查询语言：

```java
transportTimes.byKey(pair).stream();                // 0..1 record
transportTimes.byFromMachine(fromMachine).stream(); // 0..N records
```

`byFromMachine` 来自 `@SomaIndex(name = "by_from_machine", ...)`。普通用户不选择
hash、bitmap、scan 或其他物理 access path。

对于仅需要 point result 的 Key/Unique，直接 `find/get` 更自然；需要继续
filter、sort、project、update 或 remove 时再进入 stream。

### 7.3 Field-first source

```java
transportTimes.transportMinutes.stream();
transportTimes.machinePair.stream();
transportTimes.machinePair.fromMachine.stream();
transportTimes.machinePair.fromMachine.value.stream();
```

它们都继承 whole Table 的 current record domain 和 encounter order：

```text
table.field.stream()
    == table.stream().select(table.field)
```

Field-first 不创建第二份数据，也不暴露 physical Column。

### 7.4 Owned Child Table

Owned child 不是特殊 stream source。通过 parent ownership path 得到 child 后，它
就是另一张正常 SOMA Table：

```java
EligibleMachineTable eligibleMachines =
    jobs.eligibleMachines(jobKey);

long count = eligibleMachines.stream().count();
```

精确 child navigation syntax 尚待裁决；唯一约束是 child 不能 share、reparent 或
跨到另一个 Group。child 随 parent/root 的 reachability 一起进入 GC ownership
aggregate，不拥有独立 release。

## 8. Intermediate operation

### 8.1 Record stream

最小 Record stream：

```java
table.stream()
     .filter(recordPredicate)
     .sorted(recordComparator)
     .skip(offset)
     .limit(maximum);
```

Record callback 使用 generated logical getters：

```java
transportTimes.stream()
    .filter(record ->
        record.machinePair().fromMachine().value() == fromMachine.value
            && record.transportMinutes() > 0L);
```

lambda 参数由 Java 8 推断为 `TransportTimeTable.Record`。它是 callback-scoped
logical record，不是 materialized `TransportTime`、Iterator element 或可缓存
live proxy。

### 8.2 Logical Field projection

Record-first projection 使用 generated Field endpoint：

```java
transportTimes.stream()
    .filter(recordPredicate)
    .select(transportTimes.transportMinutes)
    .sum();
```

Composite/nested projection：

```java
transportTimes.stream()
    .filter(recordPredicate)
    .select(transportTimes.machinePair.fromMachine.value)
    .distinct()
    .toArray();
```

`select` 只改变 logical source shape，执行层自动读取需要的 leaf columns。

### 8.3 Field stream

Field stream 可以继续：

```java
transportTimes.transportMinutes.stream()
    .filter(minutes -> minutes > 0L)
    .sorted()
    .distinct()
    .skip(10)
    .limit(100);
```

Field projection、filter、sort、skip 和 limit 保留 source-record lineage。
`distinct/group/aggregate/materialize` 等 operation 会失去或终止 one-to-one
lineage；生成类型必须据此收窄后续 mutation capability。

### 8.4 不进入首版逻辑层的 operation

- `peek`；
- generic `flatMap`；
- `concat/union`；
- generic Table join；
- `parallel()` / `unordered()` / `findAny()`；
- arbitrary mutable `Collector`；
- infinite `generate/iterate`；
- ordinary Iterator、Spliterator、Publisher 或 async lazy result。

## 9. Terminal operation

### 9.1 Query terminal

Record/Field stream 的核心 Query terminal：

```java
count()
anyMatch(...)
allMatch(...)
noneMatch(...)
findFirst()
getFirst()
forEach(...)
toArray()
toList()
sum()
average()
min()
max()
minBy(...)
maxBy(...)
```

示例：

```java
OptionalLong shortest =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .select(transportTimes.transportMinutes)
        .min();
```

### 9.2 Update terminal

Record update：

```java
UpdateResult result =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .filter(record -> record.transportMinutes() > 0L)
        .update(editor -> {
            long updated = Math.addExact(editor.transportMinutes(), delay);
            editor.transportMinutes(updated);
        });
```

`filter` 的参数由编译器推断为 `TransportTimeTable.Record`，`update` 的参数推断为
`TransportTimeTable.Editor`；普通使用不写出这两个 nested type。

Field update：

```java
UpdateResult result =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .select(transportTimes.transportMinutes)
        .update(minutes -> Math.addExact(minutes, delay));
```

两个写法表达同一个 Table-local mutation boundary。Field update 只在 source 仍保留
one-to-one record lineage 时存在。

### 9.3 Remove terminal

Record stream 的 `remove` 删除最终 selection 对应的 records：

```java
RemoveResult result =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .filter(record -> record.transportMinutes() <= 0L)
        .remove();
```

Field-first source 上若提供删除，建议使用明确名称：

```java
RemoveResult result =
    transportTimes.transportMinutes
        .stream()
        .filter(minutes -> minutes <= 0L)
        .removeRows();
```

`removeRows` 避免被误解为“把 Field 清零或设为 null”。是否保留该 convenience
仍待评估；Record stream 的 `remove()` 是 canonical 语义。

## 10. Mutation 的用户契约

### 10.1 Whole-selection all-or-nothing

一次 `update/remove` terminal 是一个 Table/ownership-aggregate operation：

```text
成功
    -> 整个最终 selection 一次可见

失败
    -> zero records visible
```

用户不需要也不能调用：

```java
freezeCandidates();
beginTransaction();
stage();
validateIndexes();
commit();
rollback();
```

candidate freeze、staging、Unique/Index/owned-child preflight、resource admission 和
publish 都属于执行层。

### 10.2 Callback failure

```java
try {
    transportTimes.stream().update(editor -> {
        editor.transportMinutes(
            Math.addExact(editor.transportMinutes(), delay));
    });
} catch (SomaOperationException failure) {
    // Table 保持 terminal 开始前的可信状态
}
```

若任意 record 发生 overflow、callback failure、Unique conflict 或 resource refusal，
整个 selection 不发布。

### 10.3 Non-interference

以下代码不合法：

```java
transportTimes.stream().forEach(record -> {
    transportTimes.add(...); // reentrant source mutation
});
```

以下代码合法：

```java
transportTimes.stream()
    .filter(predicate)
    .update(editor -> editor.transportMinutes(newValue));
```

区别在于：第二种 mutation 由当前 terminal 控制；第一种是在 callback 中重入来源
Table。

### 10.4 Currentness 与失效

- pipeline 是 lazy、one-shot；
- terminal 开始时绑定 current Table state；
- terminal 执行期间 mutation guard 保证来源不会被另一个 operation 静默改变；
- terminal 后没有可继续使用的 iterator/cursor；
- detached result 独立于后续 Table mutation；
- Field endpoint 可以重复发起新 operation，因为它不保存 row position；
- retained live record/current Index/snapshot 不进入普通用户模型。

因此用户不会遇到 C++ 风格 undefined iterator behavior。冲突、reentrancy、
或真正 stale handle 必须产生 stable structured failure。逻辑层没有
released-owner 状态；对象仍然可达就仍然有效，不可达后由 GC 处理。

### 10.5 Sequential/parallel

首版逻辑 API 不提供 `.parallel()`。执行层可以在不改变 observable semantics 时
选择 sequential 或 bounded parallel plan，但必须保证：

- 相同结果与 encounter order contract；
- 相同 checked overflow/failure；
- 相同 whole-selection atomicity；
- callback 不并发暴露给未声明线程安全的 application object。

## 11. Field mutation capability

本草稿采用以下候选规则：

- 普通 primitive/reference payload Field：可 query，可 update；
- 参与 `@SomaIndex` / `@SomaUnique` 的 Field：仍可 update，runtime 原子维护
  derived access structure；
- `@SomaKey` Field：add 时必须赋值，发布后默认只读；若未来需要更换 identity，
  使用明确 `rekey` 专题，而不是普通 Field update；
- owned Child Field：通过 child ownership API 创建、替换或删除，不通过普通
  reference setter；
- ordinary Object reference：Field update 替换 reference slot；referent 内部状态
  仍由 application 拥有，SOMA 不追踪。

这些是为了让第一版草稿闭合而选择的候选规则，尚未成为正式 Design。

## 12. Metadata

`_metadata()` 是 `Soma`、Group、Table、Field 统一、只读的 SOMA
meta-operation namespace。

### 12.1 Soma metadata

```java
SomaMetadata metadata = Soma._metadata();

String apiVersion = metadata.compatibility().apiVersion();
List<TableIdentity> tables = metadata.schema().tables();
```

Soma metadata 描述 generated composition、schema compatibility 和 runtime
capability，不枚举 live Group，也不建立全局 mutable registry。

### 12.2 Group metadata

```java
GroupMetadata metadata = group._metadata();

long retainedBytes = metadata.runtime().retainedBytes();
List<TableIdentity> tables = metadata.schema().tables();
```

### 12.3 Table metadata

```java
TableMetadata metadata = transportTimes._metadata();

String logicalName = metadata.schema().logicalName();
int rows = metadata.runtime().rows();
int capacity = metadata.runtime().capacity();
```

### 12.4 Field metadata

```java
FieldMetadata metadata =
    transportTimes.machinePair.fromMachine.value._metadata();

String path = metadata.schema().logicalPath();
LogicalType type = metadata.schema().logicalType();
boolean keyPart = metadata.schema().isKeyPart();
```

Metadata 不允许：

```java
metadata.setRows(...);
metadata.rebuildIndex(...);
metadata.backingArray();
metadata.physicalColumnOrdinal();
```

Schema descriptor 是 immutable；runtime observation 是 detached snapshot。

## 13. 进阶与组合示例

### 13.1 default Group 与第二个 Group

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable activeTimes = active.transportTimes;
TransportTimeTable backupTimes = backup.transportTimes;
```

`activeTimes` 与 `backupTimes` 是相互独立的 Table。用户不需要手动释放 Group 或
Table；显式创建的 `backup` 对象图不再可达时由 Java GC 回收，default Group 则由
generated `Soma` 持续持有到对应 ClassLoader 卸载或 JVM 退出。

### 13.2 创建、导入、查询、更新和删除

```java
SomaGroup group = Soma.createGroup();
TransportTimeTable times = group.transportTimes;

times.reserve(4096);

for (InputTransportTime input : inputs) {
    times.add(
        new TransportTime(
            input.machinePair(),
            input.transportMinutes()));
}

TransportTime current = times.get(pair);
Optional<TransportTime> maybeCurrent = times.find(optionalPair);

UpdateResult replaced =
    times.update(new TransportTime(pair, 24L));

OptionalLong shortest =
    times.byFromMachine(fromMachine)
        .stream()
        .filter(record ->
            record.machinePair().toMachine().value() !=
                fromMachine.value)
        .select(times.transportMinutes)
        .min();

UpdateResult delayed =
    times.byFromMachine(fromMachine)
        .stream()
        .select(times.transportMinutes)
        .update(minutes ->
            Math.addExact(minutes, disruptionDelay));

RemoveResult removed =
    times.stream()
        .filter(record -> record.transportMinutes() <= 0L)
        .remove();
```

### 13.3 Group 中多个不同 Table

```java
SomaGroup group = Soma.createGroup();

TransportTimeTable times = group.transportTimes;
MachineStateTable machines = group.machineStates;

MachineState machine = machines.get(machineId);

long arrival =
    Math.addExact(
        machine.availableMinute,
        times.get(pair).transportMinutes);
```

这是两个独立 Table operation，由普通 Java 控制流协调。SOMA 不把它伪装成
cross-Table transaction 或 join。

### 13.4 Record-first 与 Field-first 等价

```java
long a =
    transportTimes.transportMinutes
        .stream()
        .sum();

long b =
    transportTimes.stream()
        .select(transportTimes.transportMinutes)
        .sum();
```

`a` 与 `b` 应 lowering 为同一个 canonical field plan。

### 13.5 Metadata 导航

```java
String apiVersion =
    Soma._metadata()
        .compatibility()
        .apiVersion();

String tableName =
    group.transportTimes
        ._metadata()
        .schema()
        .logicalName();

String fieldPath =
    group.transportTimes.machinePair.fromMachine.value
        ._metadata()
        .schema()
        .logicalPath();

int rows =
    group._metadata()
        .runtime()
        .table(group.transportTimes._metadata().identity())
        .rows();
```

## 14. Structured failure

用户只需要处理稳定、业务可判断的 failure category：

```java
try {
    transportTimes.stream()
        .filter(predicate)
        .update(updater);
} catch (SomaOperationException failure) {
    switch (failure.code()) {
        case "missing_key":
        case "unique_conflict":
        case "arithmetic_overflow":
        case "resource_limit_exceeded":
        case "reentrant_table_operation":
            handle(failure);
            break;
        default:
            throw failure;
    }
}
```

上面的 code 只是候选命名。稳定要求是：

- error category/code/context 可机器判断；
- mutation failure 后旧 Table state 仍可信；
- 不把 partial progress、scratch 或内部 exception message 交给用户解释；
- 不用 `null`、`-1`、NaN 或 generic `IllegalStateException` 表示核心状态。

## 15. 从 predecessor 到目标逻辑层

| Predecessor surface | 草稿目标 |
|---|---|
| Packed/Exact Scan | `table.stream()` / `table.byX(...).stream()` |
| Cursor | callback 内的 `Table.Record` |
| UpdateCursor | callback 内的 `Table.Editor` |
| ColumnTraversal/ColumnView | logical Field stream 自动 lowering |
| current Index / IndexSnapshot | 不进入普通路径；Key 或 detached result 跨 operation |
| generated Batch / NewRow callback | `reserve + repeated add(detachedObject)`；无 public staging object |
| Scan update/remove | SOMA stream 的 atomic Update/Remove terminal |
| DataFlow Definition/Template/Invocation | 不进入目标用户模型 |
| runtime metadata flat methods | Soma/Group/Table/Field 统一 `_metadata()` |
| multi-instance Group slot | Group 中每 generated root Table identity 唯一 |
| `Table.create()` + per-call implicit Group | `Soma.defaultGroup()` + `Soma.<tableType>()`；显式隔离使用 `Soma.createGroup()` |
| 顶级 Row/MutableRow/RowStream | Table-nested `Record` / `Editor` / `Stream` |
| manual Group/Table release | Java reachability + GC；live Table 由 capacity operation 管理 |

replacement 必须一次退出 predecessor，不能长期维持两套 canonical API。

## 16. 这份草稿刻意没有决定的内容

这里只列影响本 API 投影的未决项；完整专题 backlog 以
[README 的待裁决清单](README.md#待裁决清单与专题退役条件)为准。

### Generated naming 与 composition

1. generated `Soma` / `SomaGroup` 的 composition declaration、package 及
   Table/Field/`stream`/`_metadata` 保留名 collision rule；
2. Record-first projection 使用 `select(field)`，还是生成 `.field()` shortcut；
3. nested `Record` / `Editor` getter/setter 与 callback functional interface 的
   精确 signature。

### Query、mutation 与结果

1. Key 发布后是否 immutable，是否需要独立 `rekey`；
2. Field stream 是否保留 `removeRows()` convenience；
3. stream terminal 使用 `findFirst/getFirst`，还是只保留 `findFirst`；point API
   已选择 `find/get`；
4. `@SomaIndex` 多行 source 的默认 encounter order；
5. `clear` 是否保留，及其 atomic/capacity contract；
6. `UpdateResult` / `RemoveResult` 的最小字段、no-op 与 missing contract；
7. unified mutation epoch 还是 content/access/layout 分离 epoch，以及哪些状态需要
   投影为用户可判断的 stale failure。

### Metadata 与 materialization

1. metadata facade 的精确类型和最小字段；
2. materialization budget 是否需要进入普通 API；
3. detached `@SomaTable` materialization constructor 的字段映射、overload 和
   schema evolution rule。

## 17. 草稿验收方式

在这份草稿被接受后，仍不能直接声称 API 成立。至少需要：

1. 将确认语义 promotion 到唯一正式 Blueprint/Design Owner；
2. 生成最小 schema 的真实 Java 8 source；
3. 用 `javap`/golden 固定 generated signatures；
4. 在独立 Maven consumer 中使用 Amazon Corretto full JDK 8 clean compile/run；
5. 覆盖 `Soma.defaultGroup()`、`Soma.<tableType>()`、显式 Group、
   Table/Field/metadata、point、Query、Update 和 Remove journey；
6. 覆盖 missing Key、duplicate Key、Unique conflict、overflow、resource refusal、
   reentrancy 等 negative case；
7. 证明 update/remove whole-selection all-or-nothing；
8. 证明 Field source lowering 不依赖 public Column API，且 hot path 无 per-record DTO、
   boxing collection、reflection 或 metadata interpretation；
9. 证明 default Group 只由 generated typed static reference 持有，runtime 不通过
   live Group registry、background thread、永久 ThreadLocal 或 metadata
   observation 意外保留不可达的显式 Group；
10. 用 `javap`/negative fixture 固定 `@SomaValue` canonical constructor、final
    Field、无默认构造和 member collision；
11. 对大规模导入分别测量直接分配与 application-owned `@SomaTable` carrier
    reuse，只在 allocation 被证明为瓶颈时评估第二种 generated add signature；
12. 用 generated source、`javap` 和独立 Java 8 consumer 证明
    `Table.Record` / `Editor` / `Stream` 的 public accessibility、普通 lambda
    inference、callback non-escape 与 one-shot Stream failure；
13. 验证 repeated default access identity、显式 Group isolation、ClassLoader
    scope，以及 `Table.create()` / default reset 在目标 surface 中缺席；
14. 用三个 reference application 评估表达力，并重新建立性能 evidence。

在上述证据完成前，本文只是一套供 Product Owner 评估的候选用户逻辑层。
