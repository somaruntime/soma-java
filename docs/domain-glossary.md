# soma_java 领域术语表

状态：正式设计文档
Owner：根项目协调层
事实范围：跨模块 canonical 术语、限定词和“不等同于”边界
非事实范围：API behavior、schema validation、runtime lifecycle 和性能结论
最后审查日期：2026-07-20

## 1. 目标与权威边界

本文维护 `soma_java` 跨模块沟通使用的 canonical 领域术语，避免 schema annotation、generated API、runtime internal、application adapter 和 evidence 语境混用。

本文只拥有术语名称、层级和“不等同于”边界。具体行为以对应 owner contract 为准：

- 跨模块永久原则：[SomaTable 设计宪法](soma-table-design-constitution.md)；
- 项目边界和架构分层：[项目架构设计](architecture-design.md)；
- schema annotation：[annotation schema 契约](../soma-annotations/docs/annotation-schema-contract.md)；
- compiler lowering：[compiler integration 契约](../soma-processor/docs/compiler-integration-contract.md)；
- normalization/hash/diagnostics：[schema processing 契约](../soma-processor/docs/schema-processing-contract.md)；
- build/dependency 与 compatibility：[Build 契约](build-and-dependency-contract.md)和 [Public API 兼容性契约](public-api-compatibility-contract.md)；
- generated API：[Generated Table API 契约](generated-table-api-contract.md)；
- materialization：[Materialization 契约](materialization-contract.md)；
- runtime storage/lifecycle：[soma-runtime-core](../soma-runtime-core/docs/README.md)；
- correctness/performance/evidence：对应 [正式设计索引](README.md) 中登记的 Owner。

## 2. 命名与限定规则

核心抽象必须有清晰、正向、边界明确的名称。若一个抽象只能通过“非 X”“无 X”“类似 X 但不是 X”解释，或需要用某个内部数据结构代表整个对象，应先审查抽象边界。

文档中的术语应使用足够限定词：

| 易混词 | 必须使用的限定 |
|---|---|
| owner | document owner、parent ownership、active owner thread |
| fact source | design fact source、runtime fact source |
| index | row index、secondary index、order position；child locator 使用 `ChildTableHandle` |
| graph | schema ownership dependency graph、runtime ownership instance forest |
| materialized object | detached schema object、`List<R>` 或 `Map<K, R>`；不再使用 generated `XxxRecord` 第二类型 |
| view | 仅用于仍连接 live storage 的 ColumnView 等 borrowed view |
| DTO | 使用 external DTO；不得用 DTO 指代 SOMA schema-backed materialization |

代码类型、annotation、错误码和 metadata key 使用反引号；领域分类使用 lowercase English，例如 keyed table、dense table、runtime frontier。

## 3. Schema 与 generated public 术语

| 正式术语 | 层级 / Owner | 定义 | 不等同于 |
|---|---|---|---|
| Schema-backed row class | schema / materialization | 标注 `@SomaTable` 的用户 class；定义 row fields/kind/ownership/access metadata，并直接作为 detached single-row materialization type | live runtime row、`TableStore`、external DTO |
| `@SomaValue` / Value | schema / compile-time | compiler-defined immutable inline value；annotated fields 逻辑上 `public final`，递归 flatten 为 leaf columns，并具有 canonical value equality/hash | mutable DTO、table row、cross-table object reference |
| `SomaTable` | cross-module domain | live runtime table 的领域抽象 | `@SomaTable` annotation、`TableStore`、Java Collection |
| generated `XxxTable` | generated API | 用户访问 SomaTable 的 public/generated facade | runtime internal store、child handle |
| keyed table | schema/generated API | 声明 stable logical key 的 table kind；schema/whole-table materialization logical container 是 `Map<K, R>` | runtime Java Map storage、entity role、secondary index |
| dense table | schema/generated API | 不声明 stable logical key 的 table kind；schema/whole-table materialization logical container 是 `List<R>` | runtime Java List storage、缩水版 table |
| `@SomaChild` | schema field | 声明 parent-owned child slot 与 per-field initial-capacity runtime-plan override；`List<R>` 推导 dense child，`Map<K,R>` 推导 keyed child | 新的 table kind、live child object attachment |
| `Batch` | generated API | detached construction/import boundary，可携带 nested child batch data | runtime fact source、serialization protocol、live child table |
| Row Pipeline | generated API | generated table 上的 row traversal/filter/sort/update/remove/materializing terminal API；多行结果 materialize 为 `List<R>` | `Stream<live row>`、query DSL、parallel pipeline |
| Row Cursor | generated API | callback-scoped borrowed row accessor；read callback 使用 readonly cursor，update callback 使用 mutable cursor | Materialized Object、可保存 row proxy |
| Column Pipeline | generated API | 单列 typed traversal path，避免 schema-object materialization | ColumnView lifecycle、object Stream |
| ColumnView | generated/runtime API | 对 live column storage 的 readonly borrowed view | detached copy、Materialized Object |
| Materialized Object | schema/generated boundary | SomaTable 当前事实的 detached、完整、caller-owned schema object/`List`/`Map`；修改后不自动写回 | runtime fact source、live view、`@SomaValue`、持久化格式 |
| `MaterializationBudget` | generated/runtime API | 每次 object/collection materialization 的 deterministic resource budget | schema constraint、wall-clock timeout、JVM heap limit |

## 4. Application data role、Table kind 与 modeling role

`application data role` 回答“这部分数据在一次 SOMA 计算中承担什么职责”。它与 table kind、ownership 和 modeling role 正交，不是新的 Schema kind，不需要 annotation，也不进入 schema hash。

| Application data role | 定义 | 边界 |
|---|---|---|
| input facts | 从 external DTO、API、文件或其他结构导入的原始问题事实；成功 import 后由对应 SomaTable 持有 | 通常 read-only/read-mostly；不是历史 input object |
| working state | 算法执行过程中维护的 entity state、runtime frontier、event queue、workspace 或 derived cache | 必须继续标明 authoritative、rebuildable 或 derived，不能用“中间状态”掩盖事实权威性 |
| result facts | 算法产生的决策、状态终值、轨迹或其他业务结果 | 可以独立建表，也可以由现有 authoritative state 直接导出；不得复制同一事实 |

三类 data role 默认按不同生命周期、可变性、访问模式和 compatibility owner 分开。因 hot-loop locality 或一致性需要在同一 table 中 co-locate 时，文档必须按 field group 标明 primary role 和唯一事实源。Data role 只指导 application modeling；SOMA 不阻止混合，也不自动创建 input/result table。

Table kind 只回答“是否有 stable logical key”；modeling role 回答“这张表在业务和 hot loop 中做什么”。两者不得混为新的 schema kind。

| Modeling role | 定义 | 常见 table kind | 边界 |
|---|---|---|---|
| entity state | 长生命周期实体运行时状态 | keyed | entity 不是第三种 kind |
| lookup table | 频繁按 key 或 packed row 读取的导入/静态事实 | keyed 或 dense | 由 identity 与访问模式决定 |
| runtime frontier | application/solver 增量维护、跨轮保留并支持局部失效的候选集合 | 通常 keyed | 不是所有 candidate set 的默认答案 |
| dense workspace | 当前轮次/selected entity/局部算法使用、主要以 replace/scan/order 访问的 workspace | dense | 不要求跨轮 identity 或按 key 局部删除 |
| matrix/array-like state | 以连续 row index、packed scan 或整行访问为主的数据 | dense | row index 不是业务身份 |
| child table | parent-owned ownership role | keyed 或 dense | ownership role 与 table kind 正交 |

FJSP `MachineCandidate` 是 runtime frontier 的典型例子：`(MachineId, OperationKey)` 在 frontier 有效期内是 logical identity，并需要按 machine/operation 局部查找和删除。

Runtime frontier 不等于所有 candidate set。若候选只在当前轮次、当前 selected entity 或当前 workspace 内有效，没有跨轮保留和按 key 局部失效需求，应优先建模为 dense workspace，例如 VRP `InsertionCandidateRow` 或 Game selected-unit `MoveCandidateRow`。`epoch-scoped identity` 只有在该 table instance 有效期内稳定且确实需要 keyed lookup/局部失效时，才支持 keyed frontier 建模；存在可描述 identity 本身不自动推出 keyed table。

## 5. Ownership 术语

| 正式术语 | 层级 / Owner | 定义 | 边界 |
|---|---|---|---|
| root table | runtime ownership | 没有 owning parent row/field slot 的 table instance | independent root 之间无 SOMA transaction |
| parent table | schema/runtime ownership | 声明或持有 `List`/`Map` child field 的 table role | 不是新的 table kind |
| parent row/field slot | runtime ownership | child instance 的唯一直接 ownership attachment point | 一个 slot 不共享 live child instance |
| child table | schema/runtime ownership | storage 独立、ownership 从属的 SomaTable | 不是 inline Value，不 flatten 到 parent columns |
| child table field | schema | `@SomaChild List<R>` 或 `@SomaChild Map<K,R>`，建立 parent-to-child ownership edge | 非 owning cross-table key reference、live Java Collection storage |
| ownership edge | schema/runtime | child table field 建立的有向 owning relation | 普通 scalar/value key relation |
| schema ownership dependency graph | processor | table declarations 之间的 type-level ownership graph，必须无环 | runtime instance graph |
| runtime ownership instance forest | runtime | table instances 的有向 ownership forest，每个 child instance 恰有一个 owner | arbitrary object graph、共享 DAG |
| SomaTable ownership aggregate | cross-module/runtime | 一个 root table instance 及递归 owned child instances，共同形成 runtime fact/lifecycle/materialization 边界 | 单个 `TableStore`、independent-root transaction |
| `ChildTableHandle` | runtime internal | parent row/field slot 中定位 child instance 的 opaque locator | row index、secondary index、业务 key、public reference |
| reparent | forbidden lifecycle operation | 将 live child instance 从原 owner attach 到另一个 parent | 用 detached child Batch 构造新 subtree |
| cross-table key reference | schema/application | 通过 scalar/enum/semantic scalar/Value key 表达 non-owning relation | table-typed ownership、自动 join/materialization |

Schema 中同一个 child table type 可以被多个 parent declarations 复用；runtime 中同一个 child instance 不能被多个 parent row 共享。这两个层级不得混淆。

## 6. Materialization 与 boundary semantics

| Boundary kind | 代表对象 | 是否连接 live storage | 生命周期/写回语义 |
|---|---|---:|---|
| owned runtime fact | SomaTable ownership aggregate | 是 | 只能通过 generated mutation boundary 修改 |
| detached construction | Batch/nested child Batch | 否 | import 前不是真实 table fact |
| detached observation | schema object/`List`/`Map` | 否 | 可超过 Table 生命周期；caller-owned，可修改但无自动 write-back |
| external adapter object | input/output external DTO | 否 | 由 application/API/wire/persistence owner 定义；只通过显式 mapper 与 Batch 或 Materialized Object 交换数据 |
| callback borrow | Row Cursor | 是 | callback 返回后失效，不得逃逸 |
| scoped borrow | ColumnView | 是 | 受 owner、epoch、close/release、view_pinned 约束 |
| internal locator | current `Index`/`ChildTableHandle`/exact-index group或link position | 是 | 不公开、不序列化、不进入业务身份 |

Recursive materialization 只沿 ownership edge，把 parent row 可达的全部 child subtree 转换成完整 schema object graph；dense child 写入 `List`，keyed child 写入 `Map`；普通 key reference 不触发 lookup、join 或 graph expansion。

Required empty child materialize 为 non-null empty `List`/`Map`；optional absent 使用 `null`，optional present-empty 使用 non-null empty collection。任一层失败都不返回 partial object graph，也不修改 SomaTable。

`@SomaTable` row 不生成 semantic structural `equals()` / `hashCode()`；`List`/`Map` 保留 Java Collection contract，immutable `@SomaValue` 保留 canonical value equality/hash。完整 graph 内容比较使用 testkit 显式 recursive comparator。

`external DTO` 是 application/API/wire/persistence boundary 拥有的 consumer-specific data carrier，可以按外部契约进行 projection、aggregation、rename、flatten 或 versioning；其 shape 不要求与 SOMA schema class 一致。

输入方向由上层把 input/request DTO 显式校验并映射成 Batch，再 import 到 SomaTable；输出方向先从 SomaTable materialize 完整 schema object graph，再由上层显式映射成 output/response DTO。Materialized Object 和 external DTO 都 detached，但不是同一 canonical 类型、不共享兼容性契约，也不会自动同步。

## 7. Runtime fact 分类

| 分类 | 示例 | 是否为 runtime fact source |
|---|---|---:|
| authoritative runtime fact | logical rows、field values、presence、ownership relation | 是 |
| derived access structure | primary locator、secondary exact index、unique index | 否 |
| live diagnostics | stats、high-water、allocation estimate、probe/collision/rehash count | 否 |
| materialized observation | schema object、detached `List`/`Map`、export/response DTO、debug output | 否 |
| borrowed access | Row Cursor、ColumnView | 否，直接观察 live fact |
| external fact | input/request DTO、database、file、protobuf、API request/response | 不属于 SOMA runtime truth |

`design fact source` 指拥有某项正式设计契约的文档；`runtime fact source` 指成功 import 后拥有运行时数据事实的 SomaTable ownership aggregate。禁止省略限定词后混用。

## 8. Runtime internal 术语

| 正式术语 | 定义 | 边界 |
|---|---|---|
| `TableStore` / generated `XxxTableStore` | 单张 generated table instance 的 runtime internal storage composition root | 不等于跨 child 的 ownership aggregate，不是 public API |
| `TableLayout` | schema hash、field layout、column binding、selector/runtime-plan metadata | 不持有实际 row payload |
| `RowSpace` | row membership、current `Index`分配、packed range有效性 | 不持有field payload/locator/index policy |
| `PrimaryLocator` | keyed table的`RowKey -> current Index` identity locator | primary key lookup，不是secondary index |
| `KeySpace` | primary locator在现行代码、runtime protocol、plan与stats中的canonical umbrella term | 不表示Sparse Set、bounded entity-id space、stable Index或新的schema概念 |
| `HashIntKeySpace` / `HashLongKeySpace` / `HashCompositeKeySpace` | `KeySpace`的primitive/composite hash实现 | bucket/probing是internal detail |
| `ColumnStore` | primitive/object columns、presence bitmap、capacity、slot payload、child handle column | 不拥有 key/access/mutation policy |
| `GroupedExactIndex` / `AccessStructures` | maintained secondary exact index与unique的bucket/group/row-link | derived，不拥有authoritative facts |
| `AccessPath` | scan/exact-index/dynamic-sort source的internal execution entry | 产生candidate Index sequence，不拥有payload |
| `MutationCoordinator` | batch、replaceAll、update、swap-remove、child replacement、locator/index/epoch协调 | 防止subcomponent隐藏跨组件副作用 |
| `IndexBuffer` | table-local reusable `int[] + length` operation scratch | internal，terminal后logical reset，不是public result |
| `LifecycleState` | epoch、active borrow、released、stats、typed lifecycle errors | 不定义 schema、不持有 field payload |

```text
XxxTable
  -> XxxTableStore
       -> TableLayout
       -> RowSpace
       -> PrimaryLocator       // keyed table only
       -> ColumnStore
       -> AccessStructures
       -> AccessPath
       -> MutationCoordinator
       -> LifecycleState
```

## 9. Row 与 access 术语

| 正式术语 | 定义 | 边界 |
|---|---|---|
| `RowKey` | stable logical identity | 仅 keyed table 有；identity change 使用 delete + insert |
| `Index` | table当前packed `[0,size)`物理位置；也是dense direct API的整数位置 | 非stable identity；来源Table任意mutation/lifecycle变化后caller不再继续使用 |
| `IndexSnapshot` | public detached Index数值序列与来源table/captured structural epoch | 非stable identity或row snapshot；只供一个同步只读消费批次立即使用 |
| candidate Index sequence | 某次Row Pipeline terminal使用的Index序列 | 可来自scan、exact group或dynamic sort |
| primary key lookup | `PrimaryLocator`的`RowKey -> current Index` identity lookup | 不作为普通secondary index |
| secondary exact index | `GroupedExactIndex`维护的non-primary exact access structure | 不支持range，不保证physical/group order |
| unique index | group size至多1的secondary exact structure | 不等于primary key identity |
| dynamic sort | 单次terminal在`IndexBuffer`中排序candidate Index | 不移动columns，不形成maintained order |

`requireCurrent(snapshot)` 是可选的边界防御术语：它检查 owner、active lifecycle、structural epoch 和 current Index range，不表示 snapshot 具有自动 guarded consumption，也不证明非结构字段变化后的 filter/order 语义。跨 operation 的稳定引用统一使用 `RowKey` / `@SomaKey`。

## 10. Value state 与浮点术语

必须区分 required value、optional absent、schema default、zero value、empty string、invalid value、exceptional floating value、missing key 和 empty result。禁止用 `0`、`-1`、`NaN` 或空字符串作为 absence sentinel。

| 术语 | 定义 |
|---|---|
| ordinary floating payload | 不参与key/index/unique的float/double leaf，可保存Java IEEE-754 exceptional values |
| strict identity/access floating leaf | 参与key/index/unique的floating leaf，必须finite，并把`-0.0` canonicalize为`+0.0` |
| canonical floating value | default/write/query boundary完成finite validation和zero normalization后，用于equality/hash的值 |
| business numeric constraint | 非负、范围、业务单位等 loader/application-owned 规则，不由 SOMA 自动推断 |

## 11. Metadata、compatibility 与 execution 术语

| 正式术语 | 定义 | 是否进入 schema hash |
|---|---|---:|
| schema version label | 人工可读版本标签 | 是，按 normalized schema contract |
| exact schema hash | normalized logical schema 的精确 compatibility identity | 自身即结果 |
| artifact version | 一组 published annotations/processor/runtime artifact 的 release identity | 否 |
| compiler integration identity | transformer protocol、adapter、supported javac family 和 lowering semantics identity | 否 |
| processor/runtime compatibility version | generated code 与 runtime protocol identity | 否 |
| Access Pattern Card | scenario/runtime-plan 输入；记录 rows、hot columns、access/mutation mix、selectivity、optional/child density、working set、allocation/export frequency | 否 |
| runtime performance shape | packed/primitive/fused/allocation-bounded hot-loop 结构及其可验证 evidence | 否 |
| runtime plan | capacity、growth、primary-locator/exact-index strategy、storage/allocation/scratch hint、stats mode、MaterializationBudget default、estimator version等执行计划 | 否 |
| runtime plan hash | effective runtime plan identity | 否 |
| runtime stats | live diagnostics，不是 schema fact | 否 |
| runtime error code | 不依赖 message parsing 的 stable machine-readable failure identity | 否 |
| synchronous single-owner execution | 同一 ownership aggregate 任一时刻仅一个 active owner thread 顺序访问 | 不适用 |
| quiescent point | 无运行中 terminal/mutation/materialization，且无 active Cursor/Pipeline/ColumnView 的顺序移交点 | 不适用 |

并发能力、quiescent handoff 和 cross-table consistency 的行为以 [SomaTable 设计宪法](soma-table-design-constitution.md) 与 [Runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md) 为准；本表只固定术语。

## 12. 不推荐或受限术语

| 术语 | 规则 / 原因 |
|---|---|
| TypeA / TypeB | 只允许作为讨论阶段占位名；正式使用 keyed table / dense table |
| Sparse Table / Unkeyed Sparse Table | 把 Sparse Set implementation 误提升为 table kind，且是否定式命名 |
| Data Table | 过于宽泛，掩盖 key/access/lifecycle/ownership |
| TableCore | 使用 `TableStore` 表达单表 internal composition root |
| DTO（指 SOMA materialization result） | SOMA result 使用 Materialized Object；external DTO 只用于 application/API/wire/persistence adapter |
| View（指 detached result） | View 保留给 live borrowed storage semantics |
| Value Object（指 table materialization） | 与 immutable `@SomaValue` 的 value semantics 混淆 |
| snapshot（指 materialized object） | detached materialization 不承诺 snapshot isolation；使用 detached observation |
| child table index | 使用 `ChildTableHandle`；避免与 row index/secondary index 混淆 |
| child table reference | public schema 使用 ownership edge；runtime internal 使用 handle，cross-table relation 使用 key reference |
| owner / ownership graph（无前缀） | 必须说明 document/parent/thread 或 schema/runtime 层级 |

## 13. V1 术语不变量

术语调整不得改变 `docs/soma-table-design-constitution.md`、`docs/architecture-design.md` 和 `docs/validation-gates.md` 已拥有的 V1 能力与证据边界。尤其不得通过改名删除 immutable `@SomaValue`、List/dense 与 Map/keyed mapping、parent-owned child table、KeySpace/index/unique/order、Row Pipeline、ColumnView、recursive Materialized Object、typed errors、schema/runtime compatibility 或 gate evidence；也不得把 application data role 误提升为新的 Schema kind/annotation，或借分层复制 authoritative fact。
