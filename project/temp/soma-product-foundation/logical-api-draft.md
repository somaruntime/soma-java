# SOMA Java 逻辑层 API 草稿

类型：Temporary API Projection

状态：Candidate

正式事实源：否

上游 Owner：[产品基础决策](README.md)

最后审查日期：2026-07-31

## 1. 文档角色

本文只回答：如果当前产品基础决策成立，普通 Java 用户看到的 SOMA 逻辑层可能
是什么样子。

本文中的 type name、method name、constructor mapping、result type 和 metadata
shape 仍是候选。它们必须经过真实 generated source、`javap` 和独立 Java 8
consumer 验证后才能进入正式 Design。

## 2. 最短普通路径

候选体验应接近：

```java
TransportTimeTable transportTimes = Soma.transportTimeTable();

transportTimes.add(new TransportTime(pair, 18L));

Optional<TransportTime> found = transportTimes.find(pair);
TransportTime required = transportTimes.get(pair);

long count =
    transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .count();

UpdateResult delayed =
    transportTimes.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .update(editor ->
            editor.transportMinutes(
                Math.addExact(editor.transportMinutes(), 5L)));
```

普通使用时，用户不显式书写 generated `Record`、`Editor`、`Stream`，也不接触
physical Column、cursor、row index、plan、transaction 或 release。

## 3. 示例 Schema

下面的 annotation 和生成规则都是候选：

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

`@SomaValue` 由 compiler 固化为 immutable value：

- canonical 全字段 constructor；
- final class/Field shape；
- stable `equals/hashCode/toString`；
- 无默认构造函数。

`@SomaTable` 是 mutable detached carrier，不是 live row。Application 可以提供
符合业务的 constructor；processor 必须在编译期绑定明确的 materialization
constructor，不能依赖 reflection、`Unsafe` 或强制默认构造函数。

## 4. Generated object hierarchy

候选 generated 层级：

```text
Soma
    -> SomaGroup
        -> TransportTimeTable
            -> machinePair
                -> fromMachine
                    -> value
            -> transportMinutes
```

候选角色：

| Generated shape | 用户语义 |
|---|---|
| `Soma` | composition 根入口、default Group/Table、Group factory、metadata |
| `SomaGroup` | 一个 ownership aggregate 中的唯一 root Table 导航 |
| `TransportTimeTable` | direct operation、record stream、Field endpoint |
| `TransportTimeTable.Record` | callback-scoped read-only logical record |
| `TransportTimeTable.Editor` | Update callback 中的受控 logical editor |
| `TransportTimeTable.Stream` | finite、single-source、one-shot record pipeline |
| typed Field endpoint | nested Field、Field stream 与 Field metadata |
| `TransportTime` | detached add/update input 或 get/find result |

`Record`、`Editor` 和 `Stream` 是 public generated contract，但普通 lambda 应自动
推断它们。它们不允许暴露或保存内部 row position、cursor 或 array reference。

## 5. Group 与 default Group

### 5.1 显式 Group

```java
SomaGroup active = Soma.createGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable activeTimes = active.transportTimes;
MachineStateTable activeMachines = active.machineStates;

TransportTimeTable backupTimes = backup.transportTimes;
```

一个 Group 中每种 generated root Table identity 只有一个实例。需要两份同类状态
时创建两个 Group，而不是给 Table instance 命名。

### 5.2 Default Group

```java
TransportTimeTable first = Soma.transportTimeTable();
TransportTimeTable second = Soma.transportTimeTable();

assert first == second;
assert first == Soma.defaultGroup().transportTimes;
```

需要第二份隔离状态时：

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();

assert active.transportTimes != backup.transportTimes;
```

目标 surface 不包含：

```java
TransportTimeTable.create();
Soma.setDefaultGroup(...);
Soma.resetDefaultGroup();
```

## 6. Table direct operation

### 6.1 Capacity

```java
transportTimes.reserve(expectedRows);

int size = transportTimes.size();
int capacity = transportTimes.capacity();
```

`reserve` 不增加 logical records。V1 不提供 `trimToSize()`。

### 6.2 Add

```java
transportTimes.add(new TransportTime(pair, 18L));
```

单次 add：

1. 同步读取并校验 detached input；
2. 展开 `@SomaValue`；
3. 复制 primitive/value leaf，复制 ordinary Object reference slot；
4. 在所有 preflight 成功后发布一个新 record；
5. 不保存 carrier object。

批量导入使用 `reserve + repeated add`：

```java
transportTimes.reserve(inputs.size());

for (InputTransportTime input : inputs) {
    transportTimes.add(
        new TransportTime(
            input.machinePair(),
            input.transportMinutes()));
}
```

每次 add 是独立 atomic operation。第 N 次失败不回滚前 N-1 次成功记录。

Application 可以在证明 allocation 是瓶颈后复用 mutable `@SomaTable` carrier：

```java
TransportTime reusable = new TransportTime(firstPair, firstMinutes);

for (InputTransportTime input : inputs) {
    reusable.machinePair = input.machinePair();
    reusable.transportMinutes = input.transportMinutes();
    transportTimes.add(reusable);
}
```

这种复用不是 Batch、live record 或 SOMA object-pool contract。immutable
`@SomaValue` 自身不能通过修改 Field 复用。

### 6.3 Point query

```java
Optional<TransportTime> found = transportTimes.find(pair);
TransportTime required = transportTimes.get(pair);
```

- `find` 表达 absence；
- `get` missing 时产生 stable structured failure；
- 两者都返回 detached object；
- `get` 不插入 zero-value row。

### 6.4 Point update

```java
UpdateResult result =
    transportTimes.update(new TransportTime(pair, 24L));
```

候选语义：

- 从 replacement 的 `@SomaKey` 定位 existing record；
- missing 时 fail closed，不做 upsert；
- Key 默认只用于定位，不通过普通 update rekey；
- payload 作为一个 Table-local atomic replacement 发布；
- `UpdateResult` 至少能区分 matched 与 changed。

不提供：

```java
transportTimes.find(pair).update(replacement);
```

`find` 返回标准 `Optional`；用户通过普通 Java 控制流决定是否继续调用 Table
update。

### 6.5 Point remove

```java
RemoveResult result = transportTimes.remove(pair);
```

missing 是 `removed == 0` 还是 structured failure 尚待裁决。

## 7. Stream source

### 7.1 Whole Table

```java
transportTimes.stream()
```

逻辑上从 whole Table record domain 开始。

### 7.2 Key、Unique、Index selection

```java
transportTimes.byKey(pair).stream();
transportTimes.byFromMachine(fromMachine).stream();
```

它们产生 0..1 或 0..N record selection，不暴露 hash、bitmap、scan 或其他物理
access path。

仅需要 point result 时优先 `find/get`；需要继续 filter、sort、project、update 或
remove 时进入 stream。

### 7.3 Field-first

```java
transportTimes.transportMinutes.stream();
transportTimes.machinePair.stream();
transportTimes.machinePair.fromMachine.stream();
transportTimes.machinePair.fromMachine.value.stream();
```

候选等价关系：

```java
transportTimes.transportMinutes.stream()
```

与：

```java
transportTimes.stream()
    .select(transportTimes.transportMinutes)
```

必须继承相同 record domain、encounter order 和 currentness，并 lowering 为同一个
canonical field plan。

### 7.4 Owned child

Owned child 通过 parent ownership path 得到后，就是一张正常 Table：

```java
EligibleMachineTable eligibleMachines =
    jobs.eligibleMachines(jobKey);

long count = eligibleMachines.stream().count();
```

精确 navigation syntax 尚未裁决。child 不能 share、reparent 或跨 Group。

## 8. Intermediate operation

### 8.1 Record stream

```java
transportTimes.stream()
    .filter(record ->
        record.machinePair().fromMachine().value() ==
            fromMachine.value)
    .sorted(recordComparator)
    .skip(offset)
    .limit(maximum);
```

lambda 参数由 Java 8 推断为 `TransportTimeTable.Record`。它不是 materialized
`TransportTime`，不能逃逸 callback。

### 8.2 Logical projection

```java
transportTimes.stream()
    .filter(record -> record.transportMinutes() > 0L)
    .select(transportTimes.transportMinutes)
    .sum();
```

Nested projection：

```java
long[] machineIds =
    transportTimes.stream()
        .select(transportTimes.machinePair.fromMachine.value)
        .distinct()
        .toArray();
```

### 8.3 Field stream

```java
transportTimes.transportMinutes.stream()
    .filter(minutes -> minutes > 0L)
    .sorted()
    .distinct()
    .skip(10)
    .limit(100);
```

filter、projection、sort、skip 和 limit 可以保留 record lineage。distinct、
aggregation 和 materialization 等 operation 可能失去或终止 one-to-one lineage；
generated type 必须相应收窄 mutation capability。

## 9. Query terminal

核心候选：

```text
count
anyMatch / allMatch / noneMatch
findFirst
forEach
toArray / toList
sum / average / min / max
minBy / maxBy
```

示例：

```java
OptionalLong shortest =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .select(transportTimes.transportMinutes)
        .min();
```

所有 materialized result 都是 detached。`getFirst` 是否存在仍待裁决。

## 10. Update terminal

### 10.1 Record update

```java
UpdateResult result =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .filter(record -> record.transportMinutes() > 0L)
        .update(editor -> {
            long updated =
                Math.addExact(editor.transportMinutes(), delay);
            editor.transportMinutes(updated);
        });
```

`editor` 由 Java 8 推断为 `TransportTimeTable.Editor`。

### 10.2 Field update

```java
UpdateResult result =
    transportTimes.byFromMachine(fromMachine)
        .stream()
        .select(transportTimes.transportMinutes)
        .update(minutes -> Math.addExact(minutes, delay));
```

两种写法应 lowering 到同一个 mutation kernel。Field update 只在线性 record
lineage 仍然存在时可用。

## 11. Remove terminal

```java
RemoveResult result =
    transportTimes.stream()
        .filter(record -> record.transportMinutes() <= 0L)
        .remove();
```

如果 Field stream 支持删除，必须使用不易误解的名称：

```java
RemoveResult result =
    transportTimes.transportMinutes
        .stream()
        .filter(minutes -> minutes <= 0L)
        .removeRows();
```

`removeRows()` 是否进入 V1 尚未裁决；Record stream `remove()` 是 canonical
候选。

## 12. Atomicity 与 currentness

一次 selection Update/Remove：

```text
成功 -> 整个最终 selection 一次可见
失败 -> zero records published
```

用户不需要：

```java
beginTransaction();
stage();
validateIndexes();
commit();
rollback();
```

这些都是执行层内部责任。

禁止 callback 中重入来源 Table：

```java
transportTimes.stream().forEach(record -> {
    transportTimes.add(...);
});
```

允许由当前 terminal 控制的 mutation：

```java
transportTimes.stream()
    .filter(predicate)
    .update(editor -> editor.transportMinutes(newValue));
```

Pipeline 是 lazy、finite、one-shot。terminal 后没有 iterator、cursor 或 live
Record 可以继续使用。detached result 独立于后续 mutation。

## 13. Metadata

`_metadata()` 是统一的只读 namespace。

### 13.1 Soma

```java
SomaMetadata metadata = Soma._metadata();

String apiVersion = metadata.compatibility().apiVersion();
List<TableIdentity> tables = metadata.schema().tables();
```

### 13.2 Group

```java
GroupMetadata metadata = group._metadata();

long retainedBytes = metadata.runtime().retainedBytes();
```

### 13.3 Table

```java
TableMetadata metadata = transportTimes._metadata();

String logicalName = metadata.schema().logicalName();
int rows = metadata.runtime().rows();
int capacity = metadata.runtime().capacity();
```

### 13.4 Field

```java
FieldMetadata metadata =
    transportTimes.machinePair.fromMachine.value._metadata();

String path = metadata.schema().logicalPath();
LogicalType type = metadata.schema().logicalType();
boolean keyPart = metadata.schema().isKeyPart();
```

Metadata 不允许修改 rows、重建 index 或暴露 backing arrays。

## 14. Structured failure

候选调用体验：

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

具体 code 尚未冻结，但必须满足：

- category/code/context 可机器判断；
- operation failure 后旧 Table state 仍可信；
- 不把 partial progress、scratch 或内部 exception message 交给用户解释；
- 整数溢出 fail closed；
- 顺序与内部并行路径具有相同 failure 语义。

## 15. 一个完整候选 journey

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

跨 Table 组合由普通 Java 控制流表达：

```java
MachineState machine = group.machineStates.get(machineId);

long arrival =
    Math.addExact(
        machine.availableMinute,
        group.transportTimes.get(pair).transportMinutes);
```

SOMA 不把它伪装成 join 或 cross-Table transaction。

## 16. 刻意没有决定的 API

1. generated composition declaration 与 package；
2. 保留名、collision 和 accessor naming；
3. `select(field)` 与 Field shortcut 的最终形状；
4. nested Record/Editor getter、setter 和 callback interface；
5. Key、Unique、Index selector naming 与 encounter order；
6. Key immutability 与 `rekey`；
7. `getFirst`、`clear`、`removeRows`；
8. point remove missing contract；
9. `UpdateResult` / `RemoveResult` 字段；
10. metadata facade 类型；
11. materialization budget；
12. Table constructor matching 与 schema evolution；
13. currentness epoch 与 stale failure；
14. owned child navigation。

完整未决语义以[产品基础决策](README.md#15-待裁决问题)为准。

## 17. API 成立条件

本草稿必须至少通过：

1. 最小 schema 的真实 generated Java source；
2. `javap` / golden signature；
3. 独立 Java 8 Maven consumer；
4. 普通 lambda inference；
5. default Group identity 与显式 Group isolation；
6. add/find/get/point update/remove；
7. Record-first 与 Field-first canonical lowering；
8. Query/Update/Remove journey；
9. missing、duplicate、Unique conflict、overflow、resource、reentrancy negative；
10. whole-selection atomicity；
11. callback non-escape 与 one-shot Stream failure；
12. 无 public Column、per-record DTO hot path、boxing collection、reflection 或
    metadata interpreter；
13. 三个真实 reference scenario 的表达力与性能验证。

在这些证据完成前，本文不构成可用 API 或 release 承诺。
