# Java annotation schema 契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-annotations` / `soma-processor`

## 1. 目标

本文定义 Java annotation schema 的用户模型、annotation 语义、类型系统、normalized schema model 和 schema hash 口径。

Java annotation schema 取代 `.soma` text IDL，作为 `soma_java` V1 的唯一 schema source。Java DTO 同时承担两层契约：

- schema source：annotation processor 从 package、DTO class、field 和 annotation 中生成 normalized schema model；
- materialized DTO contract：runtime `fetch(key)`、snapshot 或 export 直接返回 DTO 对象。

SOMA runtime 的热路径存储不以 DTO object graph 为基础，而以 `ColumnStore`、presence bitmap、`KeySpace`、`AccessStructures` 和 child table storage 为基础。DTO 是用户侧 schema source 与 materialized DTO object，不是 runtime hot layout 本身，也不是 live row proxy。V1 不引入独立 `Record` public API，也不提供 `fetchDto()`；用户侧保持 `fetch(key)` 返回 DTO 的简单模型。

SOMA 在本项目中的定义是运行时高性能数据容器。它主要覆盖三类长生命周期 runtime state：

1. 表达实体状态，例如 `Job`、`Operation`、`Machine`、`Material`；
2. 表达需要频繁查询的静态或导入后只读数据，例如 `Machine-Operation` processing time、TSP city-to-city distance matrix；
3. 表达没有 stable key、以连续 row index / packed storage 为主要访问方式的数据，例如某个城市到其他城市的距离 row table、矩阵行、数组型 runtime state。

短生命周期 Java 临时对象不是 SOMA 的核心 scope。SOMA 可以提供 dense table 作为 solver workspace，但其价值来自可复用的 columnar runtime state、批量替换、packed scan 和 ordered/indexed access，而不是替代普通局部变量或一次性对象分配。

## 2. Schema declaration

V1 schema declaration 至少包含以下概念：

| 概念 | Java schema source | 语义 |
|---|---|---|
| schema | `package-info.java` + `@SomaSchema` | schema identity、generated package、version policy |
| enum | Java `enum` referenced by SOMA field | fixed enum member set and ordinal storage metadata |
| value | `final class` + `@SomaValue` | inline value object, expanded into leaf columns |
| table | `final class` + `@SomaTable` | keyed or dense runtime data container |
| field | `@SomaField` | required field, optional logical name / semantic scalar metadata |
| key | `@SomaKey` | single logical primary key field, optional logical name / semantic scalar metadata |
| optional | `@SomaOptional` | presence bitmap + payload column, materialized as nullable DTO field |
| default | `@SomaDefault` | deterministic schema default for required field |
| ignore | `@SomaIgnore` | explicitly excluded DTO helper field |
| index | `@SomaIndex` / `@SomaIndexes` | secondary non-unique access |
| unique | `@SomaUnique` / `@SomaUniques` | secondary unique access |
| order | `@SomaOrder` / `@SomaOrders` | table-scoped ordered access |
| sort | `@SomaSort` | one ordered selector item, direction defaults to ASC |

V1 要求 schema source 显式表达 SOMA 语义。普通 Java field、getter、setter、Lombok 约定、bean naming 或 Java 字段初始化表达式不能自动成为 schema fact。

用户侧 annotation 使用 `field` 语义，不使用 `column` 命名。`column` 是 value flatten 之后的 runtime 物理概念，不作为 V1 public annotation 名称。

### 2.1 Annotation ergonomics

V1 annotation API 同时追求表达能力和易用性。规则：

- `@SomaTable.name` 缺省时使用 Java class simple name 作为 logical table name；需要稳定 snake case、plural name 或跨 Java 重命名保持 schema name 时，显式填写 `name`；
- `@SomaField.name`、`@SomaKey.name`、`@SomaOptional.name` 缺省时使用 Java field name 作为 logical field name；显式 `name` 用于 schema logical name override；
- selector path 使用 logical field name；如果字段设置了 `name` override，selector 必须使用 override 后的 logical path；
- generated Java API 方法名默认从 Java field name 派生，不因为 schema logical name override 破坏 Java 侧可读性；
- `@SomaSort.value` 是 `field` 的 shorthand，`direction` 默认 `ASC`；
- `@SomaIndex.value`、`@SomaUnique.value`、`@SomaOrder.value` 是 `name` 的 alias，但 Java annotation 语法在同时设置其他参数时仍应使用 named form；
- normalized schema model 必须把所有缺省值归一化为显式 logical name、semantic 和 direction，因此 annotation 简写不影响 schema hash 的确定性。

示例：

```java
@SomaTable(defaultCapacity = 4096) // logical table name defaults to Operation
@SomaOrder(name = "by_dispatch_order", by = {
    @SomaSort("inputOrder"),
    @SomaSort("sequenceNo"),
    @SomaSort(value = "operationKey.operationId.value", direction = SomaDirection.DESC)
})
public final class Operation {
    @SomaKey(name = "operation_key")
    public OperationKey operationKey;

    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long earliestStartMillis;
}
```

### 2.2 Package schema

每个 schema package 必须在 `package-info.java` 中声明 `@SomaSchema`：

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaSchema;
```

`@SomaSchema` 语义：

- `name` 是 schema logical name，进入 normalized schema model 和 `schema_hash`；
- `generatedPackage` 是 generated API package，进入 generated metadata；
- `version` 是用户侧 schema version label，进入 metadata，但不替代 exact `schema_hash`；
- 同一 compilation unit 中属于同一 schema 的 `@SomaValue` / `@SomaTable` / enum 必须可归入一个明确 schema package；
- V1 不支持一个 Java package 中声明多个独立 SOMA schema。

### 2.3 Annotation target and retention

V1 annotation API 的 target / retention 基线：

| Annotation | Target | Retention |
|---|---|---|
| `@SomaSchema` | `PACKAGE` | `SOURCE` |
| `@SomaValue` | `TYPE` | `SOURCE` |
| `@SomaTable` | `TYPE` | `SOURCE` |
| `@SomaField` | `FIELD` | `SOURCE` |
| `@SomaKey` | `FIELD` | `SOURCE` |
| `@SomaOptional` | `FIELD` | `SOURCE` |
| `@SomaDefault` | `FIELD` | `SOURCE` |
| `@SomaIgnore` | `FIELD` | `SOURCE` |
| `@SomaIndex` / `@SomaIndexes` | `TYPE` | `SOURCE` |
| `@SomaUnique` / `@SomaUniques` | `TYPE` | `SOURCE` |
| `@SomaOrder` / `@SomaOrders` | `TYPE` | `SOURCE` |
| `@SomaSort` | `ANNOTATION_TYPE` | `SOURCE` |

V1 runtime 不通过 reflection 解释 schema。annotation retention 使用 `SOURCE`，processor 负责生成 normalized schema model、metadata 和 Java source。

Java enum 不需要额外 enum annotation。被 `@SomaField`、`@SomaKey` 或 `@SomaOptional` 引用的 Java enum 自动纳入 schema；enum member order 使用 source declaration order。

## 3. 类型系统

V1 类型分为：

- `boolean`；
- signed integer widths：`byte`、`short`、`int`、`long`；
- floating point：`float`、`double`；
- semantic scalar：date、time、date_time；
- Java enum；
- `String`；
- schema value；
- schema table typed field。

Java-only V1 不提供 unsigned primitive。业务上的非负数，例如 minute、count、input order、distance，可以使用 `int` 或 `long` 表达；非负约束由 loader、application validation 或后续 validation annotation 承担。V1 不引入 `@SomaRange`，也不把 unsigned range 作为 runtime storage type。

Semantic scalar storage：

| Semantic type | Storage |
|---|---|
| date | `int` epoch day |
| time | `long` nanoseconds since midnight |
| date_time | `long` UTC epoch milliseconds |

V1 不从 Java type name 猜测 semantic scalar。semantic scalar 必须由 annotation metadata 显式声明。`@SomaField`、`@SomaKey` 和 `@SomaOptional` 都支持 `semantic = SomaSemantic.NONE | DATE | TIME | DATE_TIME`，缺省为 `NONE`。

`String` 在 Java runtime 中可以使用 object reference column 存储，但 schema 层仍只表达 `String` 语义，不暴露 JVM address 或 object identity。

## 4. DTO field membership

V1 采用 explicit field membership：

- `@SomaTable` / `@SomaValue` 中的 instance field 必须显式标注 `@SomaField`、`@SomaKey`、`@SomaOptional` 或 `@SomaIgnore`；
- `static` field 不属于 schema；
- `transient` field 不自动成为 schema field，建议显式标注 `@SomaIgnore`；
- unannotated instance field 是 processor error；
- `@SomaField`、`@SomaKey`、`@SomaOptional` 互斥；
- `@SomaDefault` 是附加 metadata，只能叠加在允许 default 的 field annotation 上；
- DTO materialization 要求 DTO class 有 processor 可访问的 no-arg constructor，且 schema field 可由 generated code 写入；否则 processor 必须报错。

这样可以避免 DTO helper/cache/debug 字段被误纳入 schema，也避免用户以为某个 Java 字段参与 SOMA 存储但 processor 静默忽略。

## 5. Value

`@SomaValue` 是 schema-owned inline value，不拥有 table identity，也不是 runtime row boundary。

规则：

- value 可以包含 scalar、semantic scalar、enum、string 或 nested value；
- value 内部字段必须显式标注 `@SomaField` 或 `@SomaIgnore`；
- value 内部不允许 `@SomaKey`、`@SomaOptional`、`@SomaIndex`、`@SomaUnique` 或 `@SomaOrder`；
- value 内部不允许字段类型为 `@SomaTable`；
- value 作为 table field 时按 leaf expansion 展开为 columns；
- value 被 `@SomaKey` 使用时，其 leaf fields 共同构成 composite key；
- value leaf order 进入 normalized schema model 和 schema hash；
- value equality 基于 normalized leaf values。

`@SomaValue` 不表达 cross-table object reference。跨表关系应使用 `MachineId`、`OperationKey` 这类 value/key 表达，然后由 generated table API 做 lookup。

## 6. Table

`@SomaTable` 是 runtime state 的主要边界。V1 table 只分为两类：keyed table 和 dense table。

### 6.1 Keyed table

Keyed table 有 stable logical key。

适用场景：

- entity state，例如 `Job`、`Operation`、`Machine`、`Material`；
- lookup table，例如 `ProcessingTime`、`SetupTime`、city pair distance lookup；
- 需要唯一性约束、`containsKey(key)`、`fetch(key)`、`mutate(key)` 或 `delete(key)` 的 runtime data。

规则：

- keyed table 必须有且只有一个 logical key；
- key 可以是 scalar、semantic scalar、enum 或 value；
- 复合业务身份通过 value key 表达；
- primary key lookup 是 keyed table 的基础能力；runtime internal 由 `KeySpace` 承载，不作为普通 secondary index sidecar 暴露给 schema/API；
- key equality 由 normalized key leaf path 和 storage type 决定。

### 6.2 Dense table

Dense table 没有 stable logical key。

适用场景：

- packed scan；
- row-index iteration；
- matrix / array-like runtime state；
- 批量替换或重建的数据平面；
- solver workspace，例如 `ReadyOperationScratch`、`CandidateScoreScratch`。

Dense table 可以是长生命周期 runtime state，也可以作为长生命周期 table 实例中的 scratch workspace。它不适合表达需要 stable identity、跨轮次 `fetch(key)` 或唯一性约束的数据。

规则：

- dense table 不声明 `@SomaKey`；
- dense row index 只是当前 packed storage 的位置，不是 stable business identity；
- dense table 可以声明 `@SomaOrder`，用于按当前 storage state 生成 ordered access；
- dense table 可以使用 `replaceAll(batch)` 批量刷新，同时复用 capacity；
- dense table 可以 materialize DTO，但不暴露 stable key API。

### 6.3 Child table ownership

Table-typed field 是 ownership 关系，不是第三种 table kind。

规则：

- table 可以包含 `@SomaField`、`@SomaKey`、`@SomaOptional`、`@SomaIgnore`、`@SomaIndex`、`@SomaUnique`、`@SomaOrder` 和 table-typed field；
- `@SomaField` / `@SomaOptional` 标注的 value typed field 会 flatten；
- `@SomaField` 标注的 table typed field 表示 parent row owns child table instance；
- child table storage 不允许被多个 parent row 共享；
- child table 的 key/index/unique/order 只作用于该 child table instance；
- cross-table reference 不使用 table typed field，而使用 scalar、enum、semantic scalar 或 value key。

Operation 内嵌 candidate machine list 这类结构在 FJSP hot path 中不应默认建成 child table。它更适合归一化为 `ProcessingTime` keyed lookup table，因为 processing time 是 operation-machine 复合身份下的可查询 runtime data，不应混入 `Operation` row 的生命周期。

### 6.4 Default capacity

`@SomaTable` 可以声明 `defaultCapacity`：

```java
@SomaTable(name = "operations", defaultCapacity = 4096)
public final class Operation {
    ...
}
```

`defaultCapacity` 语义：

- 只允许出现在 `@SomaTable`；
- 必须大于 `0`；
- 表示 initial row capacity hint，不是 max row count；
- 不进入 logical `schema_hash`；
- 可以进入 generated runtime plan、allocation plan 或 `runtimePlanHash`；
- root table 创建时可作为默认初始容量；
- child table instance 第一次创建时可作为默认初始容量；
- child table 必须 lazy allocation，不得因为 parent table capacity 而 eager 创建所有 child storage。

## 7. Key

`@SomaKey` 是 keyed table 的 stable logical identity。

规则：

- `@SomaKey` 只能出现在 `@SomaTable` 的 direct field 上；
- key field 必须 required；
- key 可以是 scalar、semantic scalar、enum 或 value；
- 复合业务身份通过 value key 表达；
- key leaf 不生成普通 mutator setter；
- 修改 identity 必须通过 delete + insert 表达；
- key equality 由 normalized key leaf path 和 storage type 决定；
- `@SomaKey` 不允许叠加 `@SomaDefault`；
- key path 上不允许出现带 `@SomaDefault` 的 value leaf。

`@SomaValue` 内部不允许使用 `@SomaKey`。value 是否成为 key，由外层 table field 上的 `@SomaKey` 决定。

## 8. Optional

`@SomaOptional` 表示 presence，不等于默认值。

规则：

- optional 可作用于 boxed scalar、semantic scalar wrapper、enum、string、value 和 table field；
- optional scalar 在 DTO 中使用 boxed type，例如 `Long`、`Integer`、`Double`、`Boolean`，absent materialize 为 `null`；
- runtime 仍使用 presence bitmap 加 primitive payload column 或 handle column，不因 boxed DTO 字段改变 hot layout；
- absent 不等于 Java primitive default；
- generated API 可以暴露 presence predicate / `OrThrow` / `OrDefault` convenience method，但用户不直接维护 bitmap；
- `@SomaOptional` 不允许叠加 `@SomaDefault`。

如果 `@SomaOptional` 作用于 table typed field，absent 表示该 child table field 没有绑定 child table instance。普通 child table field 的逻辑默认是 empty child table，并由 runtime lazy allocation。

## 9. Default

`@SomaDefault` 表示确定性 schema default。它不是 Java 字段初始化值。

示例：

```java
@SomaField
@SomaDefault("0")
public int priority;

@SomaField
@SomaDefault("READY")
public MachineState state;

@SomaField
@SomaDefault("")
public String name;
```

规则：

- `@SomaField` 可以叠加 `@SomaDefault`；
- `@SomaKey` 不允许 default；
- `@SomaOptional` 默认语义是 absent，不允许叠加 default；
- table typed child field 不允许叠加 default；
- Java 字段初始化值不进入 schema default；
- default 必须是确定性、可解析、可归一化的字符串字面量；
- append/new row 时，如果 required field 未显式赋值，则写入 schema default；
- fetch 时返回实际存储值，不重新计算 default；
- `@SomaDefault` 进入 normalized schema model 和 `schema_hash`。

Literal parsing 规则：

| Field type | Default literal |
|---|---|
| integer | 十进制字面量 |
| floating point | 十进制或 SOMA 规定的有限浮点字面量 |
| boolean | `true` / `false` |
| enum | enum member name |
| String | annotation string value |
| date/time/date_time | SOMA 规定的 ISO text form |
| value | V1 不支持整体 default，只支持 leaf default |
| table | 不支持 default |

`@SomaValue` 内部 leaf field 可以声明 `@SomaDefault`。该 default 是 value 类型本身的语义默认值，会影响所有使用该 value 的 table field。只在某一张 table 中成立的默认值，不应放入共享 value 类型。任何被 `@SomaKey` 引用的 value path 都不得依赖 value leaf default。

## 10. Index / unique / order

`@SomaIndex`、`@SomaUnique` 和 `@SomaOrder` 是 table-level access constraints。

规则：

- 只能放在 `@SomaTable` 类型上；
- Java 8 repeated annotation 必须提供容器 annotation，例如 `@SomaIndexes`、`@SomaOrders`；
- selector 使用 logical field path，例如 `operationKey.jobId.value`、`machineId.value`；
- selector 可以引用 value leaf path；
- selector 不可穿透 child table；
- selector 只能引用 required 且不含 string、table 或 optional leaf 的 field path；
- index 是 secondary non-unique access；
- unique 是 secondary unique access；
- order 是 table-scoped ordered access，不表示 physical row reorder；
- order selector 必须通过 `@SomaSort` 声明，`direction` 缺省为 `ASC`；
- 同一 table 内 index、unique、order 名称不能冲突。

Grouped ordered access 使用 selector prefix 表达。例如 `ProcessingTime.byOperationSpt(operationKey)` 这类访问，应把 operation key leaf 作为 order 前缀，再把 SPT 排序字段放在后面：

```java
@SomaOrder(name = "by_operation_spt", by = {
    @SomaSort("operationMachineKey.operationKey.jobId.value"),
    @SomaSort("operationMachineKey.operationKey.operationId.value"),
    @SomaSort("processingMinutes"),
    @SomaSort("operationMachineKey.machineId.value")
})
```

Processor 可以基于 selector prefix 生成自然的 grouped access source method，并返回同一套 Row Pipeline。V1 支持 Java lambda 作为 row-level `filter` / `update` callback，但不引入 arbitrary join planner，也不承诺 lambda predicate 自动下推到 index。Selector diagnostics 必须指出出错 path、失败的 path segment、候选字段列表、是否因 optional/string/table leaf 被拒绝，以及对应 Java element location。

## 11. 建模最佳实践

V1 不引入 table role annotation。`entity state`、`lookup data`、`matrix/array state` 和 `workspace` 是建模场景，不是新的 schema kind。

推荐建模：

| 场景 | 推荐 table kind | 说明 |
|---|---|---|
| 实体状态 | keyed table | 有 stable logical key，支持 `fetch(key)` 和 mutation |
| 频繁查询的静态数据 | keyed table 或 dense table | 有自然唯一 key 时用 keyed lookup；以 packed scan / matrix row 为主时用 dense table |
| 连续 row index / packed storage 数据 | dense table | row index 是当前 storage 位置，不是业务身份 |
| solver workspace | dense table | 可长期持有并反复 `replaceAll(batch)`，不等同于短生命周期 Java 临时对象 |
| parent-owned 局部集合 | child table | 只在 parent row owns child table 且不共享生命周期时使用 |

FJSP 中，`ProcessingTime` 和 `SetupTime` 是 keyed lookup table，不应嵌入 `Operation`。TSP 中，如果 `cityA, cityB -> distance` 是频繁按 pair 查询的事实，可以建 keyed lookup table；如果算法主要按当前 city 的一整行距离做 packed scan，则可以建 dense distance row table。

## 12. 完整示例

下面示例展示 Java-only FJSP runtime state 的推荐建模。它刻意不把 candidate machines 建成 `Operation` 的 child table，而是使用 `ProcessingTime` keyed lookup table 和 `CandidateScoreScratch` dense table。

代码块是 schema source 的合并展示；真实 Java 项目中 `package-info.java`、enum、value class 和 table DTO class 应按 Java 文件规则拆分。

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

public enum MachineState {
    READY,
    DOWN
}

@SomaValue
public final class JobId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationId {
    @SomaField
    public long value;
}

@SomaValue
public final class MachineId {
    @SomaField
    public long value;
}

@SomaValue
public final class SetupFamilyId {
    @SomaField
    public long value;
}

@SomaValue
public final class OperationKey {
    @SomaField
    public JobId jobId;

    @SomaField
    public OperationId operationId;
}

@SomaValue
public final class OperationMachineKey {
    @SomaField
    public OperationKey operationKey;

    @SomaField
    public MachineId machineId;
}

@SomaValue
public final class SetupFamilyPair {
    @SomaField
    public SetupFamilyId fromFamily;

    @SomaField
    public SetupFamilyId toFamily;
}

@SomaValue
public final class SetupTimeKey {
    @SomaField
    public MachineId machineId;

    @SomaField
    public SetupFamilyPair familyPair;
}

@SomaTable(name = "jobs", defaultCapacity = 1024)
@SomaOrder(name = "by_dispatch_order", by = {
    @SomaSort("inputOrder"),
    @SomaSort("jobId.value")
})
@SomaOrder(name = "by_due_minute", by = {
    @SomaSort("dueMinute"),
    @SomaSort("inputOrder"),
    @SomaSort("jobId.value")
})
public final class Job {
    @SomaKey
    public JobId jobId;

    @SomaField
    public long inputOrder;

    @SomaField
    public long dueMinute;

    @SomaField
    public int operationCount;

    @SomaField
    @SomaDefault("0")
    public int nextSequenceNo;

    @SomaOptional
    public Long completedMinute;

    @SomaOptional
    public Long tardinessMinutes;
}

@SomaTable(name = "operations", defaultCapacity = 4096)
@SomaIndex(name = "by_job_sequence", fields = {
    "operationKey.jobId.value",
    "sequenceNo"
})
@SomaOrder(name = "by_dispatch_order", by = {
    @SomaSort("inputOrder"),
    @SomaSort("sequenceNo"),
    @SomaSort("operationKey.jobId.value"),
    @SomaSort("operationKey.operationId.value")
})
public final class Operation {
    @SomaKey
    public OperationKey operationKey;

    @SomaField
    public long inputOrder;

    @SomaField
    public int sequenceNo;

    @SomaField
    public SetupFamilyId setupFamily;

    @SomaOptional
    public MachineId assignedMachine;

    @SomaOptional
    public Long setupStartMinute;

    @SomaOptional
    public Long setupMinutes;

    @SomaOptional
    public Long startMinute;

    @SomaOptional
    public Long processingMinutes;

    @SomaOptional
    public Long endMinute;
}

@SomaTable(name = "machines", defaultCapacity = 128)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaOrder(name = "by_machine_id", by = {
    @SomaSort("machineId.value")
})
public final class Machine {
    @SomaKey
    public MachineId machineId;

    @SomaField
    @SomaDefault("READY")
    public MachineState state;

    @SomaField
    @SomaDefault("0")
    public long availableFromMinute;

    @SomaOptional
    public SetupFamilyId lastSetupFamily;
}

@SomaTable(name = "processing_times", defaultCapacity = 8192)
@SomaIndex(name = "by_operation", fields = {
    "operationMachineKey.operationKey.jobId.value",
    "operationMachineKey.operationKey.operationId.value"
})
@SomaIndex(name = "by_machine", fields = {
    "operationMachineKey.machineId.value"
})
@SomaOrder(name = "by_operation_spt", by = {
    @SomaSort("operationMachineKey.operationKey.jobId.value"),
    @SomaSort("operationMachineKey.operationKey.operationId.value"),
    @SomaSort("processingMinutes"),
    @SomaSort("operationMachineKey.machineId.value")
})
public final class ProcessingTime {
    @SomaKey
    public OperationMachineKey operationMachineKey;

    @SomaField
    public long processingMinutes;
}

@SomaTable(name = "setup_times", defaultCapacity = 1024)
@SomaIndex(name = "by_machine_to_family", fields = {
    "setupTimeKey.machineId.value",
    "setupTimeKey.familyPair.toFamily.value"
})
public final class SetupTime {
    @SomaKey
    public SetupTimeKey setupTimeKey;

    @SomaField
    public long setupMinutes;
}

@SomaTable(name = "ready_operation_rows", defaultCapacity = 1024)
@SomaOrder(name = "by_fcfs", by = {
    @SomaSort("inputOrder"),
    @SomaSort("sequenceNo"),
    @SomaSort("operationKey.jobId.value"),
    @SomaSort("operationKey.operationId.value")
})
public final class ReadyOperationScratch {
    @SomaField
    public OperationKey operationKey;

    @SomaField
    public long inputOrder;

    @SomaField
    public int sequenceNo;

    @SomaField
    public long predecessorEndMinute;
}

@SomaTable(name = "candidate_score_rows", defaultCapacity = 256)
@SomaOrder(name = "by_spt", by = {
    @SomaSort("processingMinutes"),
    @SomaSort("projectedEndMinute"),
    @SomaSort("operationMachineKey.machineId.value")
})
public final class CandidateScoreScratch {
    @SomaField
    public OperationMachineKey operationMachineKey;

    @SomaField
    public long setupStartMinute;

    @SomaField
    public long setupMinutes;

    @SomaField
    public long startMinute;

    @SomaField
    public long processingMinutes;

    @SomaField
    public long projectedEndMinute;
}
```

示例中的语义：

- `Job`、`Operation`、`Machine` 是 keyed entity state table；
- `ProcessingTime`、`SetupTime` 是 keyed lookup table；
- `ReadyOperationScratch`、`CandidateScoreScratch` 是 dense table，没有 stable key；
- `OperationMachineKey`、`SetupTimeKey` 等是 `@SomaValue`，在 table 中递归 flatten；
- `assignedMachine` 是 cross-table reference value，不是 `Machine` object reference；
- `MachineState` 是 Java enum，被 SOMA field 引用后自动进入 schema；
- `@SomaDefault` 影响 new row 的默认写入值，并进入 `schema_hash`；
- `defaultCapacity` 只影响 allocation hint，不进入 `schema_hash`。

## 13. Declaration order

V1 normalized schema model 必须稳定。

V1 baseline：

- Java enum member order 使用 source declaration order；
- table/value field order 使用 annotation processor 从 javac element model 读取到的 source declaration order；
- field position 隐式来自 source declaration order；
- V1 不要求用户显式声明 `position`；
- index/unique/order declaration order 使用 annotation array order 或 repeated annotation 的 source order；
- processor golden tests 必须证明同一 source 在同一 Java 8 toolchain 下 canonical output 稳定；
- runtime 不得依赖 reflection order。

如果后续需要跨 compiler 的更强稳定性，可以引入 explicit `position`，但它不是 V1 默认要求。

## 14. Normalized schema model

Normalized schema model 至少包含：

- schema version；
- schema name from `@SomaSchema`；
- generated target：`java8-columnar`；
- generated package；
- enum declaration list and member order；
- value declaration list and leaf expansion；
- table declaration list；
- table kind：`keyed` or `dense`；
- field / optional list；
- key declaration；
- schema default declaration；
- index / unique / order declaration；
- selector normalized path；
- resolved storage type；
- layout order；
- semantic scalar storage metadata；
- string storage policy；
- optional storage policy；
- table-typed field ownership metadata；
- generated public API names；
- canonical serialization input；
- schema hash。

Normalized schema model 不包含：

- local filesystem path；
- timestamp；
- random id；
- generated output directory；
- formatter details；
- runtime benchmark result；
- `defaultCapacity`；
- runtime allocation plan。

Runtime plan 可以包含：

- `schema_hash`；
- `defaultCapacity`；
- storage hint；
- allocation strategy；
- runtime plan hash。

## 15. Canonical schema hash

V1 使用 exact schema hash 作为 compatibility boundary。

```text
schema_hash = lowercase_hex(SHA-256("soma-java:v1:schema\n" + canonical_normalized_schema_model))
```

Canonical form：

- UTF-8 JSON；
- object key 按 Unicode code point 升序排列；
- array 顺序保留 schema 语义顺序；
- string 使用 JSON 标准转义；
- integer 使用十进制文本；
- boolean 使用 `true` / `false`；
- 不输出 null 字段；
- 缺省语义必须归一化为显式字段。

Generated code、runtime metadata、testkit 和 reports 必须引用同一个 schema hash。

## 16. Breaking change

V1 中以下变化均视为 breaking change：

- 修改 `@SomaSchema.name`；
- 重命名 enum、value、table；
- 重命名或重排 enum member；
- 重命名 key、field、optional、index、unique、order；
- keyed table 与 dense table 之间切换；
- 修改字段类型；
- 修改 semantic scalar storage；
- 修改 key 类型、key leaf structure 或 equality 语义；
- 修改 selector、order direction 或 access name；
- 删除字段；
- 新增改变 layout 的字段；
- 修改 optional / required 语义；
- 修改 default literal 或 default 解析结果；
- 修改 value structure；
- 修改 string storage policy；
- 修改 table-typed field ownership；
- 修改 generated public API name or return type。

V1 默认不承诺 additive compatibility。新增字段也会改变 layout、DTO shape、generated API 和 schema hash。

以下变化不属于 logical schema breaking change，但可能改变 runtime behavior 或性能，应进入 runtime plan evidence：

- 修改 `defaultCapacity`；
- 修改 storage hint；
- 修改 allocation strategy；
- 修改 benchmark-only metadata。

## 17. Diagnostics

Processor diagnostics 至少区分：

- missing or duplicate `@SomaSchema`；
- invalid schema name；
- invalid generated package；
- duplicate schema name；
- unresolved type；
- invalid field type；
- unannotated instance field；
- mutually exclusive field annotations；
- multiple key declarations；
- optional key；
- key used inside value；
- default used on key；
- default used on key value leaf path；
- default used on optional；
- default used on table-typed child field；
- invalid default literal；
- invalid defaultCapacity；
- invalid index selector；
- invalid unique selector；
- invalid order selector；
- invalid order direction；
- selector path through child table；
- selector path uses Java field name after logical name override；
- duplicate generated access name；
- value contains table field；
- value contains key/index/unique/order declaration；
- unsupported Java language feature；
- optional primitive field cannot represent absent in DTO；
- unsupported unsigned type expectation；
- schema hash generation failure。

Diagnostics golden comparison 以 diagnostic code、severity、element location、related symbol 和是否阻止 codegen 为稳定字段。message 文本允许优化，但不能改变机器可读 code 语义。
