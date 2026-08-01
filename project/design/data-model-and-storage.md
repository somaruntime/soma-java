# 数据模型与存储 Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 Group/Table identity、logical data model、authoritative storage、Key/Index、order 与 lifecycle

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 设计目标

本 Design 定义 SOMA 长期 live state 是什么、如何用统一 Table 模型表达业务数据，以及
哪些值可以安全承担 Key/Index。它承接 BP-3、BP-4、BP-8 和 BP-9。

存储实现可以改变 array growth、Index algorithm 或 layout，只要不改变本文的 logical
value、order、identity、null、currentness 和 publication contract。

## 2. Group 与 Table identity

- 每个 Table instance 必须属于一个 `SomaGroup`；
- 一个 Group 可以包含 composition 中多个不同 Table type；
- 同一 Group 中每个 generated Table type 恰好一个 instance；
- 同一 accessor 重复调用返回相同 instance；
- 不同 Group 可以拥有同一种 Table，状态完全隔离；
- 同一 Group 不按业务 owner/name 创建同类型多实例；
- Table 之间没有 SOMA-managed parent/child ownership 或隐藏 lifecycle。

Generated `Soma` 拥有每个 composition、每个 ClassLoader 一个 stable default Group。
普通单 ClassLoader application 中表现为 JVM-global composition singleton：

```java
Soma.transportTimeTable()
    == Soma.defaultGroup().transportTimeTable();
```

需要双缓存或两份同型状态时创建显式 Group：

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();

assert active.transportTimeTable() != backup.transportTimeTable();
```

Group 创建时生成并持有每种 Table 的唯一 lightweight facade 与 Field endpoint，但不分配
payload/Key/Index arrays。新 Table 的 `size()==0 && capacity()==0`；第一次 positive
`reserve` 或 `add` 才按 `max(defaultCapacity, requiredRows)` 建立 storage。这样 Table
identity/accessor 不成为可能分配大数组的隐藏 operation，`defaultCapacity` 仍只是首次
allocation hint。

## 3. Authoritative Table state

一张 Table 的 authoritative live state 只有：

- primitive payload leaf arrays；
- typed Java reference leaf arrays；
- optional Key access structure；
- zero or more non-unique Index access structures；
- logical size、allocated capacity、canonical encounter order；
- 内部 operation currentness/state version。

以下都不是 authoritative storage：

- `.schema` declaration；
- generated detached Table object；
- Java Collection/object graph；
- callback-scoped Record/Editor/Value View；
- execution scratch、candidate staging 或 materialized terminal result。

Execution 可以为一次 operation 建立 bounded staging，但成功后只发布一份 canonical
Table state，失败时丢弃 staging。

## 4. 两类 physical leaf

### 4.1 Primitive leaf

- 使用对应 primitive scalar array；
- 不允许 null；
- 未显式赋值时使用 Java zero value；
- zero 始终是合法 value，不隐式表示 missing；
- canonical scan/filter/map/aggregate/storage path 不得 boxing。

### 4.2 Reference leaf

- 使用声明类型可安全访问的 Java reference array；String/Enum 使用 typed array，ordinary
  或 parameterized Object 的 internal representation 可以是可 reify declared array 或
  `Object[]`，但不得向 generated/public surface 泄漏 universal Object model；
- 普通 payload 允许 null；
- 保存 reference slot，不 deep-copy referent；
- Field update 替换 slot，不观察 referent 内部 mutation；
- referent invariant、thread safety、identity 和 lifecycle 由 application 负责。

String、Enum、generated Value 虽然在 Java 中是 reference type，但它们具有 SOMA
明确定义的 value semantics；ordinary Object 仍是 opaque reference。

V1 不引入 Segment。Capacity growth、copy、Index rebuild 和 publish 围绕 flat arrays
设计，不能增加普通用户可见的 segment identity。

## 5. `@SomaValue` flattening

Nested `@SomaValue` 在 storage 中递归展开为 leaf arrays，在用户语义中仍保持一个
logical Field/value：

```java
@SomaValue
final class MachinePairKey {
    @SomaField MachineId fromMachine;
    @SomaField MachineId toMachine;
}
```

例如两个 `MachineId.long value` 最终可以 lowering 为两个 `long[]`，但用户仍通过
`table.machinePair`、`fromMachine` 和 `value` 导航。Physical leaf count、ordinal 和
backing array 不进入 public API。

Value outer 与每层 nested Value 都 non-null。`add`/`update` candidate 中的 outer
null 必须在 publish 前稳定失败，不能折叠成 all-zero leaves，也不为此增加 optional
presence column。

Value leaf 只允许 primitive、String、Enum 和 nested Value；ordinary Object 或
temporal reference leaf 非法。普通 Value/Index 的 String/Enum leaf 可以 null；作为
Key 时所有 reference leaf 递归 non-null。

## 6. Field type system

### 6.1 Exact-value eligible types

只有 compiler 能确认 stable exact-value semantics 的完整 logical Field 可以承担
Key/Index：

- boolean、byte、short、char、int、long；
- String；
- Enum；
- 只由 eligible leaf 与 nested Value 组成的 `@SomaValue`。

`float`/`double` 可以作为 payload，但 V1 不允许其承担 Key/Index。Ordinary Object、
temporal reference 和 parameterized object graph 不允许作为 Key/Index。

### 6.2 String

- canonical storage 为 `String[]`；
- 保存原引用，不 copy、intern 或 dictionary encode；
- exact equality/Index 使用区分大小写的内容相等；
- non-null natural order 使用 `String.compareTo`；
- Field/Index 允许 null，Key 不允许 null；empty String 合法；
- `distinct` 按 content 合并重复值，并把多个 null 视为一个 equivalence class；
- natural-order operation 遇到 null fail closed；nullable order 必须使用 application
  提供的 null-aware Comparator；
- Locale/case normalization 通过 application-owned normalized Field 表达。

### 6.3 Enum

- canonical storage 为对应 typed Enum array，不 lowering 为 ordinal `int[]`；
- exact equality 使用 constant identity；
- non-null natural order 使用 declaration order；
- Field/Index 允许 null，Key 不允许 null；
- 增删或重排 constant 是 application schema change，需要重新编译并以新 state 启动；
  V1 不做 live migration。

String/Enum natural-order operation 遇到 null 都必须 fail closed；SOMA 不暗中选择
nulls-first/nulls-last。

### 6.4 Float 与 double

- payload equality/distinct 使用 Java wrapper canonical bit equality；
- natural order 使用 Java total order；
- numeric aggregate 遵守 IEEE-754；
- 不承担 Key/Index；
- 包含它们的 Value 可以做 payload，但整个 Value 不再 eligible for Key/Index。

### 6.5 Ordinary Object 与时间类型

Ordinary Java Object 只作为 nullable opaque payload：

- SOMA 不根据任意 `equals/hashCode/Comparable` 或 reference identity 自动建立
  distinct/order/Key/Index；
- application 可以在 callback 中执行普通 OOP operation；
- referent 内部 mutation 不递增 Table currentness，不触发 Index maintenance；
- 第三方 immutable/Comparable type 也不会自动升级为 exact-value Field。

Parameterized reference（例如 `List<String>`）在 generated source signature 中保留 declared
generic type，但 physical slot 只保存 Java reference。SOMA 不扫描、复制或 runtime-validate
type argument/collection element；raw/heap-pollution 后果仍属于 application/Java type
boundary。因为 component type 不可 reify，该 Field 不生成 array terminal。

日期、时间、时区全部由 application 拥有。`java.time.*`、`java.util.Date` 和
`java.sql.*` 不特殊 lowering；直接存入时只是 opaque reference。需要 hot scan、
order、aggregate、Key 或 Index 时，application 转换成具有明确单位/epoch 的 primitive
或 primitive-backed Value。

## 7. Type-level capability

| Field type | Intrinsic distinct | Natural order | Numeric aggregate | Key/Index |
|---|---|---|---|---|
| `boolean` | 是 | 否 | 否 | 是 |
| `byte/short/int/long` | 是 | 是 | checked | 是 |
| `char` | 是 | 是 | 否 | 是 |
| `float/double` | wrapper canonical equality | Java total order | IEEE-754 | 否 |
| String | content equality | `String.compareTo` | 否 | 是 |
| Enum | constant identity | declaration order | 否 | 是 |
| eligible Value | structural equality | 无 implicit order | 否 | 是 |
| opaque Object/temporal | 无 intrinsic distinct | explicit Comparator only | 否 | 否 |

Value structural equality/hash 递归使用 primitive value、String content、Enum identity
和 nested Value；float/double 使用 wrapper canonical bit semantics。Field declaration
order 不自动成为 composite Value business order。

不支持的 intrinsic operation 由 generated type 在编译期排除，不能依赖 runtime
`UnsupportedOperationException`。

## 8. Key contract

- Table 可以 keyless，也最多一个 direct `@SomaKey` Field；
- composite Key 使用一个完整 Value；
- Key 是 stable business identity，不是 physical row position；
- primitive zero 或全零 Value 是合法 Key；
- Key outer 和 reference leaves non-null；
- Key 发布后 immutable；
- 不提供 `rekey`，改变 identity 必须 remove 后 add；
- duplicate add fail closed 且不发布 record；
- Key 提供 `0..1` point access，不产生 `byKey(...).stream()` selection。

Key access structure 与 payload/Index 一起维护；任何 failure 后必须仍与 authoritative
records 一致。

## 9. Index contract

- 一个 Table 可以有多个 direct `@SomaIndex` Field；
- Index 是 non-unique exact-match access path，返回 `0..N` Record selection；
- generated accessor 使用 `by<FieldName>`；
- Index selection 保持 Table canonical order 的有序子序列，不暴露 bucket order；
- String/Enum Index 允许 normal null bucket；
- Value Index outer non-null，String/Enum leaf null 作为 structural component；
- complete Value 可以 lowering 为多个 leaf 的复合物理结构；
- nested-subfield、cross-Field tuple、prefix/range 和 Value 内部 Index 不支持；
- secondary unique 不支持。

产品语义不暴露 Index implementation；V1 production baseline 使用 generated hash
structure。未来若替换为 sorted/其他内部方案，仍必须保持 exact equality、logical
order、atomic maintenance、complexity 与 failure contract，并通过 Architecture 的
替换准入。

## 10. 关系 Table

SOMA 不提供 ChildTable。1:M 使用 many-side Table 保存 one-side ID；N:M 使用 relation
Table 保存两端 ID，并按需要在每个 direction 建 Index：

```java
@SomaTable(defaultCapacity = 65536)
final class JobOperation {
    @SomaKey OperationId operationId;
    @SomaIndex JobId jobId;
    @SomaField long processingMinutes;
}

@SomaTable(defaultCapacity = 65536)
final class JobMachineEligibility {
    @SomaIndex JobId jobId;
    @SomaIndex MachineId machineId;
    @SomaField long processingMinutes;
}
```

```java
operations.byJobId(jobId).stream();
eligibilities.byJobId(jobId).stream();
eligibilities.byMachineId(machineId).stream();
```

边界：

- endpoint ID 的重复存储与 Index sidecar 是可接受成本；
- SOMA 不验证 endpoint 是否存在；
- 删除 entity 不自动删除 relation record；
- 多张 Table 的 operation 独立 atomic，不是 transaction；
- application 决定顺序、补偿和业务一致性；
- Index 不推出 endpoint pair uniqueness；
- 若 pair 是 Value Key，可以 point access，但 V1 不同时索引 nested endpoints；
- 同时需要双向 Index 与 pair uniqueness 时，唯一性由 application 维护。

`List<T>`、array 或普通 object reference 不具有 relationship declaration 语义。作为
`@SomaField` 时只是 opaque nullable payload，不创建或维护另一个 Table。

这里的 `T` 必须是 ordinary application type；compiler-only `@SomaTable/@SomaValue`
declaration 不能被放入 container/array。需要 container of values 时，application 使用
自己拥有的 ordinary Java type；需要 SOMA relationship 时使用 endpoint ID + relation
Table。

## 11. Capacity 与 canonical order

Table 提供：

```java
table.reserve(expectedRows);
int size = table.size();
int capacity = table.capacity();
```

- `reserve` 不增加 logical record；
- V1 不提供 `trimToSize()`；
- capacity growth 使用 checked arithmetic，成功后一次发布；
- `add` 把新 Record 放到当前 canonical order 末尾；
- ordinary update 与不发生 growth 的 reserve 保持 order；
- structural remove 可以确定性重排 survivors，不承诺永久 insertion order；
- 相同起始 state 与 remove selection 的顺序/并行路径发布相同 survivor order；
- physical row index/internal order key 不进入 public identity。

Whole Table source 使用完整 order；Index source 是命中 records 的 ordered subsequence；
Field projection 继承来源 Record order。更高层 operation 的 order contract由
[执行 Design](execution-and-concurrency.md)拥有。

## 12. Lifecycle 与 GC

用户 API 不提供 `release()`、`close()`、`AutoCloseable` 或 ownership token：

- default Group 由 generated static reference 持有；
- default Group 的 Table capacity 因而具有 ClassLoader-lifetime high-water 特征；需要
  整组替换、回收大数组或双缓存时使用 explicit Group，并让旧 Group 失去可达性；
- 显式 Group 及其 Table、Field 和 arrays 不再可达时由 GC 回收；
- runtime 不得用 global live registry、后台 thread、永久 ThreadLocal 或 metadata
  observation 意外保留显式 Group；
- GC 回收时机不属于 SOMA promise；
- application-owned ordinary referent 与 custom ForkJoinPool 由 application 管理。

## 13. Storage invariants

任何可观察 operation boundary 都必须满足：

1. `0 <= size <= capacity`；
2. 所有 payload leaf、Key 和 Index 表示同一 record set；
3. Key uniqueness 与 non-null contract 成立；
4. Index 对每个 record/value 恰好表达其 exact-match membership；
5. canonical encounter order 对 whole Table/Index/Field 一致；
6. failed operation 不改变 payload、Key、Index、size、capacity 或 internal version；
7. detached result/cursor 不成为 authoritative state；
8. ordinary referent 内部 mutation 不被误记为 Table mutation。

## 14. 明确排除

- Segment、page 或 public partition identity；
- optional presence column；
- physical Column API；
- row-position identity；
- Java object graph 作为 canonical storage；
- ChildTable、ownership graph、cascade 和 child handle；
- foreign key、secondary unique、join 或 cross-Table transaction；
- automatic temporal/third-party immutable lowering；
- runtime reflection/metadata interpreter hot path；
- live schema migration。

## 15. Implementation admission Gates

Production storage/runtime 出现前必须证明：

- flatten/unflatten 与 detached materialization correctness；
- all primitive paths 无 hidden boxing；
- Key/Index add/update/remove 与 rollback-equivalent zero publication；
- null/equality/order matrix；
- checked size/capacity/byte arithmetic；
- deterministic structural remove order；
- Group GC reachability 无 accidental retention；
- ordinary Object slot boundary；
- 1:M/N:M 双向 Index journey；
- 大规模 memory footprint、allocation 和 throughput profile。

Dense root、Key/Index、deterministic swap-compaction 与 journal/candidate publication 的 production
baseline 由
[Production Implementation Architecture](implementation-architecture.md)拥有；本 Design
仍唯一拥有其必须保持的 storage semantics。
