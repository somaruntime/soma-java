# SomaTable 设计宪法

状态：正式设计文档
Owner：根项目协调层
事实范围：SomaTable 总心智模型、跨模块永久原则和不可缩水边界
非事实范围：annotation 语法、generated API 细节、runtime 数据结构和 benchmark 结果
最后审查日期：2026-07-10

## 1. 文档定位

本文是 SOMA Java 跨模块语义原则的唯一事实源。它回答“无论具体实现怎样演进，SomaTable 必须始终是什么、不能变成什么”。

具体设计由以下 Owner 落成：

- [项目架构设计](architecture-design.md)：模块、依赖方向和跨层数据流；
- [Generated Table API 契约](generated-table-api-contract.md)：用户可见表访问和 mutation；
- [Materialization 契约](materialization-contract.md)：detached object graph 和递归 child；
- [Runtime 正确性模型](runtime-correctness-model.md)：不变量、状态机和失败原子性；
- [Runtime 性能模型](runtime-performance-model.md)：访问模式、成本和性能声明边界；
- [annotation schema 契约](../soma-annotations/docs/annotation-schema-contract.md)：schema source；
- [schema processing 契约](../soma-processor/docs/schema-processing-contract.md)：normalization、hash 和 diagnostics；
- [TableStore 契约](../soma-runtime-core/docs/table-store-contract.md)：runtime storage；
- [runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md)：ownership、mutation、release 和错误。

非 Owner 文档只能解释本文原则在本地的后果，不能建立第二套语义。

## 2. 设计意图

SOMA Java 是 Java 8 进程内的高性能运行时数据容器和 runtime-state schema system。它以 annotation schema 描述稳定数据形状，以 generated API 提供类型安全访问，以 columnar runtime 保存和计算运行时事实。

设计阶段的目标是先建立可解释、可验证、可演进的契约。实现可以分阶段，但最终 V1 不能因为排期、抽象便利或局部优化而降低正式语义。

## 3. 总心智模型

### 3.1 数据流

```text
API / DTO / file / application object
  -> validate and adapt
  -> Batch / generated mutation boundary
  -> SomaTable ownership aggregate
  -> Row/Key/Column access and high-performance computation
  -> explicit materialization or external mapping
  -> API / DTO / result / diagnostics
```

数据成功进入 runtime 后，SomaTable ownership aggregate 是该运行时状态的唯一事实源。输入对象、detached object、index、order、stats 和输出对象都不是并行事实源。

### 3.2 三个相互正交的分类轴

每张 Table 必须分别回答三个问题：

| 分类轴 | 选项 | 决定什么 |
|---|---|---|
| Table kind | keyed / dense | 是否存在稳定 logical key |
| Ownership role | root / parent / child | 生命周期归谁控制 |
| Application data role | input / working / result | 数据职责、可变性和生命周期 |

这三个轴不能混为一谈：

- keyed/dense 只描述身份；
- root/child 只描述 ownership；
- input/working/result 只描述 application responsibility；
- entity、lookup、frontier、workspace、matrix、cache 是建模角色，不是新的 Table kind。

### 3.3 Schema、runtime 和 materialization

```text
@SomaValue / @SomaTable source
  -> compile-time normalized schema
  -> generated facade and materializer
  -> TableStore columnar facts
  -> detached schema object / List / Map
```

- annotation class 是 schema source；
- live runtime storage 是 TableStore，不是 schema object graph；
- `@SomaTable` class 同时是单行 detached materialization shape，但不是 live row；
- `List<R>` 表达 dense child/container shape；
- `Map<K,R>` 表达 keyed child/container shape；
- materialized object 是 caller-owned copy，不自动 write-back。

## 4. 永久原则

### 原则一：产品边界

SOMA Java 不是 ORM、database、serialization framework、persistence format、protobuf replacement、general object graph、concurrent container 或 Java Collection replacement。

### 原则二：唯一事实源

成功 import 后，logical rows、field values、presence 和 ownership relation 共同构成 SomaTable runtime facts。KeySpace、index、unique、order、permutation、cache 和 stats 都是派生结构；Materialized Object 和 external DTO 是观察或映射。

同一业务事实不得同时由 SomaTable 和 mutable DTO/Collection/cache 独立维护。

### 原则三：Table kind 只由身份决定

- keyed table 有 stable logical key；
- dense table 没有 stable logical key。

Dense row index、RowSlot、ChildTableHandle 和 sidecar position 都是 runtime-local location，不能冒充业务身份。

### 原则四：Ownership 与 Table kind 正交

Keyed/dense table 都可以作为 root、parent 或 child。Parent row 的 child field 保存 internal handle，child 保持独立 storage，但生命周期 authority 属于 parent ownership aggregate。

每个 child instance 只有一个 owning parent row/field slot。Child 不共享、不 reparent；schema ownership graph 必须无环，runtime instance graph 必须是 forest。

### 原则五：值语义和状态必须显式

`@SomaValue` 是 compiler-defined immutable value：具有 canonical construction、value equality/hash，不暴露 setter 或 mutable escape。

必须区分 required、optional absent、default、zero、empty、invalid、missing key 和 empty result。Optional absence 只由 presence 表达，不能使用 `0`、`-1`、`NaN` 或空字符串 sentinel。

普通 floating payload 保留 Java IEEE-754 值域；参与 key/index/unique/order 的 floating leaf 必须 finite，并把 `-0.0` canonicalize 为 `+0.0`。Equality、hash 和 order 必须使用同一 canonical value。

### 原则六：初始化和 mutation 必须受控

Table create 后必须处于空且合法状态。Row 只有在 key、required/default、presence、child binding 和 unique constraints 全部成功后才成为 live row。

所有事实修改只经过 generated Mutator、Batch、Row Pipeline mutation 或 generated child API。Key identity 不原地修改；identity 变化使用 delete + insert。Expected failure 不得留下半写入 row、orphan child 或部分 sidecar 更新。

### 原则七：正确性边界分层

每次单表 mutation 返回后，该表的 RowSpace、ColumnStore、KeySpace、bitmap、index、order 和 epoch 必须一致。

Parent-child handle、exclusive ownership、cascade lifecycle 和 recursive materialization 构成 ownership aggregate correctness。

多个独立 root tables 之间的业务一致性由 application/solver orchestration 负责；V1 不提供跨表事务。

### 原则八：Materialization 是完整 detached boundary

Materializing parent row 必须沿 ownership edge 递归构造完整 child subtree；不沿普通 key reference 自动 join。

Required empty、optional absent 和 optional present-empty 必须可区分。任一层失败时不返回 partial object，不修改 Table。所有 deep materialization 都受确定性 `MaterializationBudget` 约束。

Materialized row 不提供 structural equality/hash；完整内容比较由 testkit 显式 comparator 负责。`@SomaValue` 和 generated key 保留 value equality/hash。

### 原则九：Copy、borrow 和 live access 分离

| Access shape | 是否连接 live storage | 可否跨 runtime lifecycle 保存 |
|---|---:|---:|
| Materialized Object / `List` / `Map` | 否 | 是 |
| Key value | 否 | 是 |
| RowIndexBuffer | 否，但绑定 epoch | 仅在有效 epoch |
| Row Cursor | 是 | 否 |
| ColumnView | 是 | 仅在 active scope |
| ChildTableHandle | 是，internal | 不可公开 |

任何 API 都不能同时承诺 detached-copy 与 live-view 语义。

### 原则十：并发和一致性由上层控制

SomaTable 是 synchronous single-owner object，不支持 concurrent read、concurrent write、parallel pipeline、snapshot isolation 或 internal lock。

跨线程只能在 quiescent point 顺序移交，由上层建立 happens-before，并保证没有 active cursor、pipeline 或 ColumnView。

### 原则十一：访问模式决定建模和性能

真实场景必须先识别 hot loop、data role、ownership 和访问模式，再选择 keyed/dense、root/child、index/order、Row Pipeline、ColumnView 或 materialization。

列式存储、SoA、Sparse Set 和 DOD 提供性能先验，不提供无条件性能保证。正式场景使用 Access Pattern Card 描述规模、hot columns、access/mutation mix、selectivity、optional/child density、working set 和 export frequency；它属于 runtime plan，不进入 schema hash。

稳定 hot path 必须保持 packed、primitive-specialized、fused 和 allocation-bounded，不能退化为 object/metadata interpreter。

### 原则十二：Metadata 分层且不驱动 hot loop

```text
logical schema metadata       -> schema hash
processor/runtime protocol    -> compatibility identity
runtime plan                  -> capacity/storage/strategy hints
runtime stats                 -> live diagnostics
```

Runtime plan 和 stats 不进入 logical schema hash。Metadata 可以配置和诊断，但不能成为 per-cell hot-path interpreter。

### 原则十三：Runtime 不持久化

Table、row index、RowSlot、child handle、Cursor 和 ColumnView 不序列化、不持久化。Materialized Object 可以交给上层 mapper，但本身不是稳定 wire/persistence format。File、database、network、backup、restore 和 migration 由上层 adapter 负责。

### 原则十四：错误、资源和确定性是契约

Schema diagnostics、invalid value、duplicate/missing key、absent optional、invalid index、stale/released/view pinned、pipeline lifecycle、budget、compatibility、allocation 和 invariant violation 必须可区分。

Capacity 是 hint 不是 limit；growth 必须 overflow-safe；allocation failure 不得产生 visible partial state。Schema normalization、generated source、hash、key equality、order 和 diagnostic code 必须可复现。

### 原则十五：实现阶段不得缩水

V1 最终必须保留：

- keyed/dense table；
- parent-owned keyed/dense child；
- immutable `@SomaValue` 和 schema-backed materialization；
- primitive columns、presence bitmap 和 packed rows；
- KeySpace、index、unique、order；
- Direct API、Row/Key/Column Pipeline、Mutator 和 ColumnView；
- typed errors、lifecycle 和 budget；
- schema hash/runtime compatibility；
- compile、golden、correctness、performance shape、package 和 benchmark gate evidence。

实现阶段可以调整顺序和内部策略，但不能用“先做最小版本”改写上述 release scope。

## 5. Application data role 原则

Input facts、working state 和 result facts默认分开建模，以保持 loader、hot runtime 和 exporter 的职责清晰。SOMA 不禁止受控 co-location，但必须满足：

- 不制造第二事实源；
- 有共同生命周期、一致性单元或局部性依据；
- Access Pattern Card 和 benchmark 能解释取舍；
- 不依赖 SOMA 提供跨 root table transaction。

Result table 不是必选。结果若已经是 working table 中的 authoritative fact，应直接 materialize/map；只有结果拥有独立 identity、lifecycle、mutation 或 access pattern 时才建立独立 table。

## 6. 变更规则

修改本宪法意味着改变跨模块永久原则，必须同时审查：

- annotation/schema compatibility；
- generated public API；
- runtime correctness/lifecycle；
- materialization 和 performance；
- examples、testkit、benchmark 和 V1 gates。

局部 API 命名、runtime plan 数值、capacity、hash load factor、scratch policy 和 benchmark scale 不进入本宪法，由对应 Owner 管理。

## 7. 非目标

本文不规定具体 annotation 参数、Java method signature、runtime class、memory layout 数值、benchmark 阈值或报告格式。
