# SOMA Java 领域语言

类型：Design

状态：正式

Owner：SOMA Java 跨模块 canonical terminology

设计层次：`D1` 跨层基础

主要关注点：跨模块术语、限定词与概念边界

上位设计：[设计宪法](soma-java-design-constitution.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprint/soma-java-product-blueprint.md)

事实范围：术语名称、限定词、层级和“不等同于”边界

非事实范围：API behavior、storage algorithm、lifecycle transition 和性能结论

最后审查日期：2026-07-28

## 1. 限定规则

容易混淆的词必须带限定：document Owner、parent ownership、active owner；Design fact、runtime fact；current Index、secondary exact index、business order position。`View` 只指仍连接 live storage 的 borrow；detached result 使用 Materialized Object；DTO 只指 external DTO。

## 2. Schema 与 public access

| 术语 | 定义 | 不等同于 |
|---|---|---|
| schema-backed row class | 标注 `@SomaTable` 的用户 class，定义 row schema 与 detached row shape | live row、DTO、TableStore |
| `@SomaValue` / Value | compiler-defined immutable inline value，递归 flatten 为 leaf columns | mutable DTO、table、cross-table reference |
| SomaTable | live runtime table 的领域抽象 | annotation、Java Collection、TableStore |
| keyed table | 有 stable logical key 的 table kind | Java Map storage、entity role、secondary unique |
| dense table | 无 stable logical key 的 packed table kind | Java List storage、缩水 table |
| Batch | detached construction/import boundary | live runtime fact、serialization protocol |
| Access Model | Point、Candidate、Column、Key、Bulk 与 Ownership 的完整访问语义 | 只有 Pipeline 的查询 DSL |
| Candidate Scan | generated Candidate source/stage/terminal access | Java Stream、query DSL、parallel pipeline |
| Cursor / UpdateCursor | callback-scoped live borrow | materialized row、可缓存 proxy |
| ColumnTraversal | 单列 one-shot typed traversal | object Stream、ColumnView lifecycle |
| KeyTraversal | keyed table logical key 的 one-shot traversal | row scan、stable key collection view |
| ColumnView | scoped typed live-column borrow | detached array/copy |
| `IndexSnapshot` | detached current-Index 数值序列及可选 currentness 诊断信息；消费契约见 [Schema 与生成 API](schema-and-generated-api.md) | stable row identity、row snapshot、live view |
| Materialized Object | detached schema object/`List`/`Map` observation | live storage、external DTO、snapshot isolation |
| Transformation Model | Shape、Value、lineage、order、Operator、Result 和 Effect 的语义 | Access Model、物理 planner、SQL |
| Logical Shape | element/cardinality/lineage/value-state/order/effect capability 的语义角色 | Java Collection 类型、物理 buffer |
| DataFlow Definition | immutable、可复用的 schema-bound logical computation | live Table、mutable plan、application rule engine |
| DataFlow Template | Definition 的 immutable analyzed/static-lowered projection | bound source、executor、current epoch |
| DataFlow Invocation | 绑定当前 source/parameter/resource 的 one-shot execution | reusable Definition、concurrent Table session |
| Descriptor Metadata | compiler/generated 固化的 schema/type/access/ownership 逻辑描述 | mutable plan、runtime data |
| Plan Metadata | create/bind 前可调整、freeze 后形成 Effective Metadata 的配置 | global mutable config、hot-path interpreter |
| Effective Metadata | validated immutable physical/resource identity | current counters、operation outcome |
| Runtime Metadata | detached Group/member/Table/Segment/access topology snapshot | payload、stats、DataFlow Explain |
| Observation | module-owned current/high-water/lifecycle/outcome snapshot | Metadata、planner input、business fact |
| Explain | analyzed/selected physical decision、formula identity 与 reason | mutable stats、business result |
| SomaGroup | 可选的 root composition/resource/lifecycle boundary | transaction、DataFlow GroupBy、guard prerequisite |
| SomaGroupPlan | stable logical Group/member slots 与 Group resource plan | live membership registry、RuntimePlan replacement |
| Result Delivery | terminal 结果交付方式；默认 Eager，可显式 callback-scoped | Candidate laziness、materialization、Effect |
| Delta | 以 stable Key 和显式 operation 表达的 detached typed change | live Table state、CDC client、exactly-once log |
| Detached Columnar Result | typed/primitive heap arrays、presence 和 shape identity 构成的 detached output | live ColumnView、stable source snapshot、object graph materialization |

## 3. Application modeling

Application data role 与 table kind、ownership 正交：

| Role | 定义 |
|---|---|
| input facts | import 后由 table 持有的原始问题事实，通常 read-mostly |
| working state | 算法执行中 authoritative、rebuildable 或 derived state；必须声明哪一种 |
| result facts | 决策、终值或轨迹；可以独立建表，也可以从 authoritative state 导出 |

常用 modeling role：

- entity state：长期 keyed state；entity 不是第三种 table kind；
- lookup table：按 key 或 packed scan 读取的 input/read-mostly facts；
- runtime frontier：跨轮增量维护、需要局部失效的候选，通常 keyed；
- dense workspace：当前轮/selected entity 使用的 replace/scan/sort 工作集；
- matrix/array-like state：以连续 Index 和 primitive scan 为主的 dense state；
- child table：parent-owned ownership role，可以 keyed 或 dense。

存在一个可描述 key 不自动意味着应该 keyed；是否需要跨轮 identity、point lookup 和局部失效才是建模依据。

## 4. Ownership

| 术语 | 定义 | 边界 |
|---|---|---|
| root table | 没有 owning parent 的 table instance | independent roots 无 SOMA transaction |
| parent row/field slot | child instance 的唯一直接 attachment | 不共享 child instance |
| child table | storage 独立、ownership 从属的 SomaTable | 不是 inline Value |
| schema ownership dependency graph | declarations 之间的 type-level owning graph | runtime instances |
| runtime ownership instance forest | 每个 child instance 恰有一个 owner 的无环 forest | arbitrary object graph/shared DAG |
| ownership aggregate | 一个 root 及递归 owned children 的事实/lifecycle边界 | 单个 TableStore、跨 root transaction |
| group member slot | `SomaGroupPlan` 中稳定的 logical root instance identity | schema table name、current Index |
| `ChildTableHandle` | parent column 中定位 child 的 opaque internal locator | Index、key、public reference |
| reparent | 把 live child 从一个 owner 转到另一个 owner | 用 detached Batch 新建 subtree |
| cross-table key reference | non-owning logical relation | ownership edge、自动 join |

同一 child row type 可被多个 schema declarations 使用；同一 live child instance 不能被多个 parent 共享。

## 5. Runtime internal

| 术语 | 定义 |
|---|---|
| `TableStore` | 单张 generated table 的 internal composition root |
| `RowSpace` | packed membership 和 current Index validity |
| `PrimaryLocator` / KeySpace | keyed table 的 key → current Index locator |
| `ColumnStore` | typed columns、presence、capacity 和 child-handle payload |
| `GroupedExactIndex` | secondary exact bucket/group/row-link derived structure |
| `AccessPath` | Packed/exact Candidate source 的 internal binding |
| `MutationCoordinator` | batch/update/remove/compaction/index/epoch coordination |
| `IndexBuffer` | table-local reusable primitive candidate scratch |
| lifecycle state | epoch、borrow、active operation、released 和 stats state |
| `RuntimePlan` | create-time immutable aggregate execution/resource policy |
| `TableStats` / Table Observation | immutable runtime observation；不拥有 business facts |
| Storage Segment | column storage/growth/GC publication unit |
| Parallel Morsel | bounded scheduler 的 work/cancel/merge unit |
| Execution Vector | cache/JIT-oriented inner-loop block；不是 task 或 public vector API |
| GroupLedger | member structural entitlement 与 storage/access transient hard-limit Owner |
| InvocationLedger | 一次 DataFlow scratch/output/tasks/workers 的 resource Owner |

Transformation internal 的 logical node、physical schedule、kernel 和 worker scratch 不进入本表作为 public 领域概念；其 public 角色与边界由 [DataFlow 执行模型](dataflow-execution-model.md)拥有。

`IndexBuffer` 是 internal operation scratch；`IndexSnapshot` 是 public detached sequence。两者都不拥有业务 identity；`requireCurrent` 的 currentness 机制由 [Ownership 与 lifecycle](ownership-and-lifecycle.md)定义。

## 6. Row、order 与 floating value

- `RowKey`：keyed table 的 stable logical identity；
- current `Index`：当前 packed `[0,size)` 位置，structural mutation 后可指向其他 row；
- Candidate Index sequence：一次 Candidate Scan terminal 使用的候选；
- dynamic sort：只排序本次候选，不移动 columns、不建立 maintained order；
- ordinary floating payload：可以按 Java IEEE-754 保存，业务范围由 application 校验；
- strict identity/access floating leaf：参与 key/index/unique，finite 且把 `-0.0` canonicalize 为 `+0.0`。

Required、optional absent、schema default、zero、empty string、invalid value、missing key 和 empty result 是不同状态；不得以 `0`、`-1`、`NaN` 或空字符串隐式表示 absence。

## 7. Facts 与 evidence

- Design fact source：拥有长期规范性设计的唯一文档；
- runtime fact source：成功 import/mutation 后拥有 live facts 的 SomaTable ownership aggregate；
- derived access structure：locator/index，不是 runtime business fact；
- diagnostics：stats/high-water/probes，不是 business fact；
- evidence artifact：一次验证/测量的结构化证据，不是 Design；
- external DTO：application/API/wire/persistence owner 的 carrier，与 schema-backed row 分属不同兼容契约。
