# SOMA 产品基础决策

类型：Temporary

状态：Active

正式事实源：否

实施授权：仅授权 clean-slate 仓库重启；尚未授权 production 实现

Owner：本专题候选产品语义、确认方向和未决问题

最后审查日期：2026-07-31

## 1. 文档角色

本文从重启前的完整讨论中提取当前仍有效的产品方向。它只保存“现在认为 SOMA
应该是什么”，不保存旧实现迁移表、被否决方案的完整历史或 predecessor 代码结构。

[逻辑层 API 草稿](logical-api-draft.md)是本文的用户 API 投影。若两者冲突，以
本文为准；冲突必须回到 Product Owner 裁决，不能由实现自行选择。

完整讨论、旧设计和旧实现由 Git ref
`archive/pre-product-reset-2026-07-31` 保存。本文不承担历史档案职责。

## 2. 产品意图

SOMA 面向 Java application 中大规模、频繁变化的进程内 runtime state。它允许
application 使用自然的 Java 对象、注解和 generated typed API 表达业务语义，
同时在内部使用 data-oriented storage 与 compiler-specialized execution。

核心边界是：

```text
Object-oriented application boundary
    -> compiler-generated lowering
        -> data-oriented SOMA storage and execution
```

SOMA 不是 ORM、数据库、SQL/DataFrame、通用 Java Collection、工作流引擎或
分布式计算平台。它也不要求 application 用 physical Column、row position、
cursor、plan 或 scheduler 表达业务。

## 3. 四层职责模型

### 3.1 用户语义层

用户只面对：

- Java schema annotation；
- generated `Soma`、`SomaGroup`、Table 与 Field；
- Table direct operation；
- finite、single-source、Stream-like operation；
- typed metadata；
- detached result 与 structured failure。

普通用户不选择物理 access path，不管理 scratch、index buffer、segment、
transaction token 或 executor。

### 3.2 编译生成层

编译层负责：

- 解析并校验 schema；
- 展开 nested `@SomaValue`；
- 生成 Soma/Group/Table/Field 及其 typed API；
- 将 logical Field 和 operation lowering 为 storage/execution access；
- 在编译期发现命名冲突、非法类型和不可能满足的 contract；
- 生成专门化代码，而不是运行期依赖 reflection 或 metadata interpreter。

### 3.3 存储层

存储层拥有 Table 的 authoritative live state：

- primitive payload leaf array；
- reference payload leaf array；
- Key、Unique、Index 等由 schema 要求的 access structure；
- size、capacity 和 mutation currentness。

Schema object、detached DTO、Java Collection graph 和执行 scratch 不能成为
canonical hot storage。

### 3.4 执行层

执行层拥有：

- selection；
- intermediate operation；
- terminal；
- mutation staging 与 atomic publish；
- resource admission；
- sequential 或 bounded-parallel scheduling；
- stable failure mapping。

执行层不得建立第二份长期 live state，也不得把内部协议暴露为用户必须操作的对象。

## 4. 唯一用户执行模型

用户执行模型统一为：

```text
数据源
    -> 零个或多个中间操作
        -> 一个终止操作
            -> 结果或受控状态变化
```

它借鉴 Java Stream 的 lazy pipeline、无状态/有状态中间操作和 terminal 分层，但
不复制 Java Stream 的全部 API。

SOMA 与 Java Stream 的主要差异是：

- Java Stream 通常消费 Collection/array 等一元数据源；
- SOMA 操作 Table、record selection 和 logical Field；
- Table 同时拥有 add、point query、point update、point remove 和 capacity；
- Key、Unique、Index 与 mutation currentness 属于 SOMA 语义；
- Update/Remove terminal 可以改变来源 Table；
- 结构变化必须有定义良好的 atomicity 和 failure，不能依赖 undefined iterator
  behavior。

旧 Transformation Model 和 DataFlow Model 不进入新产品的用户模型。

## 5. 逻辑对象层级

唯一层级是：

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field
```

候选用户导航：

```java
Soma.defaultGroup()
Soma.createGroup()
Soma.transportTimeTable()

group.transportTimes
table.machinePair
table.machinePair.fromMachine
table.machinePair.fromMachine.value
```

每一级都可以通过 `_metadata()` 进入只读 metadata namespace：

```text
Soma / Group / Table / Field
    -> _metadata()
```

metadata 不拥有业务 mutation，也不暴露 backing array、physical column ordinal
或内部 planner。

## 6. Group、Table identity 与生命周期

### 6.1 Group ownership

- 每个 root Table 必须属于一个 Group；
- 一个 Group 可以包含多个不同 Table；
- 同一个 Group 中，每个 generated root Table identity 只能有一个实例；
- 两个独立 Group 可以各自拥有同一种 Table；
- owned child Table 通过 parent ownership path 存在，不计入 root Table uniqueness；
- child 不允许 share、reparent 或跨 Group。

### 6.2 Default Group

generated `Soma` 拥有一个稳定 default Group：

```java
Soma.transportTimeTable()
    == Soma.defaultGroup().transportTimes;
```

重复调用 default Table accessor 返回同一实例。目标 API 不提供：

- `TransportTimeTable.create()`；
- `Soma.setDefaultGroup(...)`；
- `resetDefaultGroup()`；
- 按名称在一个 Group 中创建同类 Table 多实例。

singleton 的精确范围是每个 generated Soma composition、每个 ClassLoader 一个
default Group；普通单 ClassLoader application 中表现为 JVM-global singleton。

### 6.3 GC lifecycle

用户 API 不提供 `release()`、`close()`、`AutoCloseable` 或 ownership token。

- default Group 由 generated static reference 持有；
- 显式 Group 及其 Table/Field/arrays 不再可达时由 Java GC 回收；
- runtime 不得通过全局 live registry、后台线程、永久 ThreadLocal 或 metadata
  observation 意外保留显式 Group；
- GC 回收时机不属于 SOMA 承诺；
- 大 Table 的 active capacity 通过 `reserve/size/capacity` 管理。

## 7. Schema object 与存储

### 7.1 `@SomaValue`

`@SomaValue` 是 immutable logical value：

- compiler 生成 canonical 全字段 constructor；
- class 与 Field 形成 final shape；
- compiler 生成稳定 `equals/hashCode/toString`；
- 不生成默认构造函数；
- value instance 可以安全共享，不能通过改写 Field 复用。

nested `@SomaValue` 在 storage 中递归展开为 leaf arrays，但在用户语义中仍是一个
logical Field/value。

### 7.2 `@SomaTable` schema object

`@SomaTable` object 只作为 detached OOP boundary value：

- `add` / point `update` 的输入；
- `get` / `find` materialization 的结果；
- 不作为 live storage；
- 不作为 callback-scoped Record；
- primitive 和 flattened value 在 operation 返回前复制；
- ordinary Object field 只复制 reference slot。

Table materialization constructor 的精确 compile-time mapping 尚未裁决；不得使用
reflection、`Unsafe` 或为了 runtime 方便强制无业务意义的默认构造函数。

### 7.3 两类 payload leaf

原始 Table payload 只有：

1. primitive scalar array；
2. typed Java reference array。

primitive leaf：

- 不允许 null；
- 未显式赋值时使用对应 Java zero value；
- 必须保持 primitive storage，不得 boxing。

reference leaf：

- 允许 null；
- 保存普通 Java Object reference；
- SOMA 不限制 referent 可变性；
- referent invariant、thread safety 和生命周期由 application 维护；
- Field update 替换 reference slot，不跟踪 referent 内部变化。

V1 不引入 Segment。capacity growth、publication 和 GC 直接围绕 flat arrays 设计。

## 8. Source、Field 与 physical Column

Table whole-record source、Key/Unique/Index selection 和 Field source 不是三套互不
相关的查询语言：

```text
Record Selection
    -> Logical Field/Value Projection
        -> Physical Column Access
```

- whole Table traversal 产生 record selection；
- Key、Unique、Index 只改变 record selection；
- Field-first 和 Record-first 都是 logical projection；
- physical Column 完全退出普通用户 API；
- execution 根据 logical Field 自动选择一个或多个 leaf arrays；
- composite Field 即使展开为多个 columns，用户仍以一个 Field 使用。

候选等价关系：

```java
table.field.stream()
```

等价于：

```java
table.stream().select(table.field)
```

两条路径必须 lowering 为同一个 canonical plan。

## 9. Intermediate operation

### 9.1 无状态

V1 核心候选包括：

- `filter`；
- `select` / logical projection；
- `skip`；
- `limit`。

`map` 不应退化为任意 object-producing callback。是否提供独立命名以及它与
`select` 的边界仍需裁决。

### 9.2 有状态

V1 核心候选包括：

- `sorted`；
- `distinct`。

任何有状态 operation 都必须有 resource admission、deterministic order 和
fail-closed 行为。generic grouping、window、join、combine 和 reducer 不因旧实现
曾经支持就自动进入 V1。

## 10. Terminal family

### 10.1 Query

核心候选包括：

- `count`；
- `anyMatch` / `allMatch` / `noneMatch`；
- `findFirst`；
- `forEach`；
- `toArray` / `toList`；
- `sum` / `average` / `min` / `max`；
- `minBy` / `maxBy`。

materialization 必须是 detached result。大规模 materialization 是否要求显式
budget 仍待裁决。

### 10.2 Update

Update terminal 对最终 selection 执行 Table-local mutation。用户 callback 面对
generated `Table.Editor` 或 typed Field value，不管理 staging 或 index publish。

### 10.3 Remove

Record stream 的 `remove()` 删除最终 selection 对应的 rows。Field stream 是否
提供明确命名的 `removeRows()` convenience 尚待裁决。

## 11. Table direct operation

Table direct operation 包括：

- `reserve(expectedRows)`；
- `size()`；
- `capacity()`；
- `add(detachedObject)`；
- Key point `find/get/update/remove`；
- 是否提供 `clear()` 尚待裁决。

V1 不提供 `trimToSize()`。

多个 `add` 是多个独立 atomic operation；第 N 次失败时，前 N-1 次成功结果保留。
V1 不提供 public Batch。Application 可以复用 mutable `@SomaTable` carrier，但
这只是 application optimization，不是 SOMA object pool 或 staging contract。

V1 不提供通用 Table binary relation/join。跨 Table 逻辑由普通 Java 控制流拆成
多个单 Table operation；SOMA 不伪装跨 Table transaction。

## 12. Mutation、currentness 与 failure

一次 selection Update/Remove 必须 whole-selection all-or-nothing：

```text
成功 -> 最终 selection 一次可见
失败 -> zero records published
```

执行层内部拥有 candidate freeze、preflight、staging、Unique/Index/child
maintenance、resource admission 和 publish。用户不调用 transaction protocol。

关键约束：

- pipeline lazy、finite、single-source、one-shot；
- terminal 开始时绑定 current Table state；
- terminal 期间禁止 reentrant source mutation；
- callback 不能保存 Record/Editor；
- terminal 后没有 retained iterator、cursor 或 row position；
- detached result 不受后续 Table mutation 影响；
- overflow、Unique conflict、resource refusal 和 callback failure 都 fail closed；
- 所有顺序/内部并行路径必须保持相同结果、顺序 contract、overflow 和 atomicity；
- V1 用户 API 不提供 `.parallel()`。

用户只处理 stable structured failure，不用 `null`、`-1`、NaN 或 generic
`IllegalStateException` 解释核心状态。

## 13. Generated nested types

每个 generated Table 可以拥有低可见度但 public 的 nested semantic contract：

- `Table.Record`：callback-scoped read-only logical record；
- `Table.Editor extends Table.Record`：Update callback 中允许受控写入；
- `Table.Stream`：finite、single-source、one-shot record pipeline。

普通 lambda 应由 Java 8 自动推断这些类型，用户通常不 import、构造或显式书写。
内部 row position、cursor 和 column accessor 不能成为这些 public type 的用户
心智模型。

## 14. 明确排除

当前 V1 候选不包括：

- public Batch 或 staging object；
- Segment；
- physical Column API；
- generic Table join 或 binary relation；
- Transformation/DataFlow Definition、Template、Invocation、Context；
- ordinary Iterator、Spliterator、Publisher 或 async lazy result；
- `peek`、generic `flatMap`、`concat/union`；
- `.parallel()`、`unordered()`、`findAny()`；
- arbitrary mutable Collector；
- infinite `generate/iterate`；
- manual Group/Table release；
- cross-Table transaction；
- reflection/metadata interpreter hot path。

## 15. 待裁决问题

### 15.1 Generated composition 与命名

1. generated `Soma` / `SomaGroup` 的 declaration、package 与 composition 输入；
2. Table、Field、`stream`、`_metadata` 保留名和 collision diagnostic；
3. Record-first `select(field)` 是否同时生成 Field shortcut；
4. nested `Record` / `Editor` getter/setter 与 callback interface 的精确 signature。

### 15.2 Key、Index 与 Field

1. Key、Unique、Index 的自然 API；
2. Table contract identity；
3. duplicate/missing failure；
4. `@SomaIndex` 多行 encounter order；
5. Key 发布后是否 immutable，以及是否需要明确 `rekey`；
6. zero-default Key 是否允许，还是 operation boundary 强制显式赋值。

### 15.3 Null、Object 与 child

1. `@SomaValue` outer null 是否禁止；
2. ordinary Object 可以参与哪些 equality/order/Key/Index operation；
3. owned child 的 physical reference slot、absence 和 failure boundary；
4. primitive always-present 对 predecessor optional 语义的完整 replacement。

### 15.4 Mutation 与 currentness

1. unified mutation epoch 还是 content/access/layout 分离 epoch；
2. point update/remove、`clear`、`UpdateResult` / `RemoveResult` 精确 contract；
3. stable failure code、stale identity 和 reentrant behavior；
4. Field stream 的 mutation capability narrowing；
5. `removeRows()` 是否存在。

### 15.5 Metadata 与 materialization

1. Soma/Group/Table/Field metadata facade 的最小类型与字段；
2. materialization budget 是否进入普通 API；
3. detached `@SomaTable` constructor matching、overload ambiguity 和 schema
   evolution rule。

## 16. 建立正式设计前所需证据

1. 对本文与逻辑 API 草稿做独立一致性审查；
2. 所有待裁决项获得决定或进入明确的后续 active topic；
3. 形成最小真实 schema 的 generated Java 8 source；
4. 用 `javap` / golden 固定 public signatures；
5. 用独立 Java 8 Maven consumer clean compile/run；
6. 覆盖 default Group、显式 Group、Table/Field/metadata、point、Query、Update、
   Remove journey；
7. 覆盖 missing、duplicate、Unique conflict、overflow、resource refusal、
   reentrancy 等 negative case；
8. 证明 selection Update/Remove all-or-nothing；
9. 证明 Field lowering 不依赖 public Column，hot path 无 per-record DTO、boxing
   collection、reflection 或 metadata interpretation；
10. 证明 default Group 不通过 generic live registry 泄漏显式 Group；
11. 对直接分配与 application-owned carrier reuse 做真实大规模 profile；
12. 用调度、仿真和实时派工三个场景重新验证表达力与性能。

上述第 3 项开始涉及 production implementation，必须先由 Product Owner 明确授权。

## 17. Promotion 与退役

专题闭合时：

1. 产品边界进入 Blueprint；
2. 分层、ownership、storage、execution、failure 进入各自唯一 Design Owner；
3. generated signature 由 executable consumer/golden 固定；
4. 用户示例进入相应 Product Docs；
5. 未实现差异进入 Conformance；
6. 删除本 Temporary。

不得把本文长期保留为与正式 Design 并列的第二事实源。
