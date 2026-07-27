# SOMA Runtime Boundary、Group、Scale Readiness 与产品化集成设计

类型：Temporary

状态：`ready_for_independent_audit`

Owner：SOMA Java Root Design；各细分责任按本文第 18 节进入正式 Owner

正式事实源：否

实施授权：本文是 P4 的完整设计候选。P5 独立审计通过、P6 原子固化到正式
Blueprint/Design/Conformance/Engineering 后，production 才按本文语义 slice 实施。

事实范围：P4 integrated final design candidate、TV0–TV9 Owner adoption、正式
Owner promotion map、production slice 与 qualification contract

非事实范围：当前正式 Design、当前 production 符合性、已通过 qualification、
G6/public release readiness 或已经授权开始 P8

最后审查日期：2026-07-28

设计输入：

- [治理指导](README.md)
- [系统设计、核心抽象与叙事再审视](system-design-and-narrative-governance.md)
- [Capability Model 与 Result Delivery](capability-and-result-delivery-governance.md)
- [TV0–TV9 Evidence Synthesis](technical-validation-evidence-synthesis.md)
- SOMA 当前正式 Blueprint、Design、Conformance、Implementation Map 和 production
  code

## 1. 设计结论

SOMA Java V1 的 canonical 定位保持不变：

> SOMA Java 是面向 Java 8、Schema-Defined、Compiler-Specialized、
> JVM Heap-Resident、类型安全且资源可预测的 runtime-state computing library。

本轮不把 SOMA 降低为 column storage，也不把它扩张成数据库、事务系统或工作流
引擎。SOMA 的完整产品心智模型冻结为：

```text
State / Owner
  -> SomaGroup
       -> one or more root ownership aggregates
            -> root Table + parent-owned child Tables

Capability
  -> Schema/Metadata
  -> Storage/Layout
  -> Access
  -> Mutation
  -> Exact/Relation
  -> Transformation
  -> Execution
  -> Result Delivery
  -> Resource
  -> Observation/Failure

Plan / Lifecycle
  -> Descriptor
  -> mutable Plan Builder
  -> immutable RuntimePlan / Effective Metadata
  -> Definition
  -> Template
  -> one-shot Invocation
  -> immutable Observation
```

这三个轴共同定义 SOMA。Capability 不是开放插件点，Metadata 不是运行时解释器，
Group 不是跨表事务，Result Delivery 也不是把所有中间计算改成 lazy。

## 2. Canonical 产品叙事

SOMA 的唯一端到端叙事为：

```text
application declares annotation schema
  -> javac 8 processor validates and normalizes it
  -> processor classifies the four closed V1 storage kinds
  -> processor emits immutable Descriptor Metadata and generated typed facade
  -> application optionally adjusts a schema-seeded mutable Plan Builder
  -> build validates and freezes immutable RuntimePlan / Effective Metadata
  -> application creates SomaGroup
  -> generated root Tables attach to stable logical member slots
  -> application loads and mutates root ownership aggregates
  -> generated Point / Candidate / Column / Key / Bulk / Ownership access
  -> typed Transformation Definition
  -> reusable analyzed Template
  -> one-shot Invocation binds current Tables, parameters and budget
  -> acquire sources in canonical order
  -> typed cardinality/resource preflight and physical capability binding
  -> direct or bounded parallel specialized kernel
  -> default Eager Detached publication
     or explicit callback-scoped read-only delivery pilot
  -> immutable Result / Observation / Explain
  -> Invocation cleanup, then Group-owned release
```

每个箭头只有一个事实 Owner。Schema、plan、Definition、Template、Invocation、
runtime data、derived access state、Result 和 Observation 不互相冒充。

### 2.1 两条产品使用路径

单表简单路径继续成立：

```java
MachineTable table = MachineTable.create();
try {
    // load / access / transform
} finally {
    table.release();
}
```

它在内部创建一个只含该 root Table 的 implicit `SomaGroup`。使用者不承担 Group
配置税。

多 root Table 的 canonical 路径必须显式使用同一 Group：

```java
SomaMetadata metadata = SchemaMetadata.metadata();
RuntimePlan plan = metadata.newPlan().build();
SomaGroup group = Soma.createGroup(metadata, plan);
try {
    MachineTable machines = MachineTable.create(group);
    CandidateTable candidates = CandidateTable.create(group);
    // finite typed multi-source DataFlow
} finally {
    group.release();
}
```

advanced path 显式暴露 Metadata、plan、budget、parallel 和 observation；simple
path 不要求理解这些对象。

## 3. V1 类型与对象边界

V1 schema/storage kind 只有四类：

| kind | public semantics | physical storage |
|---|---|---|
| primitive-backed scalar | primitive、enum、date/time、显式 semantic scalar | primitive column；enum 用 ordinal |
| reference-backed immutable scalar | 白名单仅 `java.lang.String` | typed reference column，保存 caller reference |
| compiler-flattened value | `@SomaValue` 的不可变值语义 | 编译期展开为 canonical leaf columns |
| owned structured state | parent-owned child state | child Table handle + ownership registry |

任意 Java object、数组、`List`、`Map`、DTO graph 和 application object graph 都不是
普通 schema field。应用对象使用 SOMA 中的 stable ID 与 application sidecar /
registry 关联。

`@SomaValue` 可以包含合法 scalar leaf，包括 String leaf；SOMA 只保存展开后的 leaf，
不保存 value object reference。child Table 不允许 share、reparent 或脱离 parent
ownership aggregate。

### 3.1 String 冻结语义

String V1 使用 reference-backed baseline：

- 不复制、不 intern、不 normalize，不引入 dictionary 或 character arena；
- required String 必须非 null；optional String 用 presence 表示 absence，present value
  仍必须非 null；空字符串是普通 value；
- payload、Primary Key、Unique、Exact Index、GroupBy、Join、order、filter、mutation
  和 materialization 都使用 `String.hashCode`、`equals` / `compareTo` 所表达的
  authoritative value semantics；
- fingerprint/hash 只能缩小候选，最终相等必须回查 authoritative String column；
- equal-value、different-object 的 mutation 是 logical no-op，保留原 reference，
  不更新 access state 或 epoch；
- append 可以保存 equal-value、different-object 的 caller reference；
- remove、clear、replace、rollback、scratch cleanup 和 release 必须及时清除 dead
  reference；
- detached output 可以保存 String reference，因为 String 具有白名单 immutable
  value semantics；callback 中的 generated Cursor/borrow 仍不得逃逸；
- String 能力不授权 arbitrary object，也不改变 SOMA mutation、epoch、ownership、
  concurrency 或 materialization boundary。

String profile 与规模结论必须同时声明：

```text
UTF-16 code-unit length
value cardinality
distinct object identity count
intra/inter-table sharing ratio
presence/absence ratio
payload / Key / Unique / Index / Group / Join role
simultaneously-live Table count
```

## 4. 完整 Metadata control plane

### 4.1 组成与命名

Metadata 使用组合而不是深继承树。public 类型都是 final immutable value，只有
`RuntimePlan.Builder` 在 freeze 前可变。

```text
SomaMetadata                         schema-scoped mental root
  -> descriptor
       -> SomaSchemaMetadata
       -> SomaTableMetadata
       -> SomaColumnMetadata
       -> SomaKeyMetadata
       -> SomaUniqueMetadata
       -> SomaIndexMetadata
       -> SomaOwnershipMetadata
       -> SomaTypeMetadata
  -> plan
       -> RuntimePlan.Builder        mutable, schema-seeded
  -> effective
       -> RuntimePlan
       -> TablePlan / ChildPlan
       -> resolved layout/access/execution/resource identities

SomaGroup.metadata()
  -> SomaGroupMetadata               immutable runtime observation
       -> SomaTableRuntimeMetadata
       -> SomaSegmentMetadata
       -> SomaResourceMetadata
       -> stats/explain summaries
```

`SomaIndexMetadata` 表示 schema-declared secondary exact selector，不表示 current
physical row `Index`。`SomaSegmentMetadata` 只属于 Effective/Observation，不属于
logical schema Descriptor。

Column payload、presence、bucket、row link、candidate、scratch、result payload 和
live group membership registry 都不是 Metadata。Metadata 描述这些事实，不拥有或
替代它们。

### 4.2 Generated schema entry

processor 在每个 `generatedPackage` 生成唯一 `SchemaMetadata` companion：

```java
public final class SchemaMetadata {
    public static SomaMetadata metadata();
    public static RuntimePlan defaultRuntimePlan();
    public static RuntimePlan.Builder newPlan();
}
```

每个 generated Table 只投影：

```java
public static SomaMetadata schemaMetadata();
public static SomaTableMetadata metadata();
public static RuntimePlan defaultRuntimePlan(); // compatibility convenience
```

所有 Table 引用同一个 generated schema-scoped metadata instance；不使用 global
registry、reflection 或 resource scanning。

Descriptor 类型只开放 processor-populated immutable factory/protocol，不向
application 开放任意构造。`SomaMetadata` 的最小读取面为：

```java
SomaSchemaMetadata descriptor();
List<SomaTableMetadata> tables();
SomaTableMetadata requireTable(String logicalName);
RuntimePlan defaultRuntimePlan();
RuntimePlan.Builder newPlan();
```

Plan 的常用配置不要求 application 重建 TablePlan：

```java
RuntimePlan.Builder plan = SchemaMetadata.newPlan();
plan.table(MachineTable.metadata())
        .initialCapacity(1_000_000)
        .expectedMaximumRows(100_000_000L)
        .workloadProfile(TableWorkloadProfile.LARGE_SCAN);
plan.string(MachineTable.metadata().requireColumn("name"))
        .profile(machineNameProfile);
RuntimePlan effective = plan.build();
```

`RuntimePlan.Builder.table(...)` 和 `.string(...)` 返回 parent-owned mutable editor；
只有 root `build()` 发布 immutable plan。editor 在 build 后失效。raw
`RuntimePlan.builder(schemaHash, protocol...)` 迁入 generated/internal protocol，
不再作为 application canonical entry。

### 4.3 Plan、freeze 与 identity

- `SomaMetadata.newPlan()` 返回由 Descriptor defaults 填充的 mutable Builder；
- Descriptor 的 field/type/optional/default/Key/Unique/Index/ownership/schema hash
  永远不可修改；
- application 只能修改明确开放的 capacity/workload profile/resource/output/stats/
  parallel/String profile；
- `build()` 完成完整性、compatibility、checked arithmetic、layout、access、
  String profile 和 resource validation，产出 immutable `RuntimePlan`；
- Group create 再验证 generated/runtime protocol、schema identity 和硬件无关的
  effective invariants；失败时不发布 Group；
- Builder 修改不影响已经 build 的 plan，plan 修改不影响已创建 Group；
- hot path 只消费预绑定 ordinal、primitive fields 和 concrete strategy，不遍历
  Metadata graph。

以下 identity 保持分离：

```text
schema hash
generated/runtime protocol identity
RuntimePlan effective identity
DataFlow Definition identity
Template identity
Invocation observation identity
```

现有 `RuntimePlan` / `TablePlan` 不形成平行旧体系：它们成为完整 Metadata 的
Plan/Effective 分支。旧的 per-Table default plan 构造迁入 generated
`SchemaMetadata`；Table convenience method 只委托。

## 5. SomaGroup 与 ownership

### 5.1 Group 的语义

`SomaGroup` 是以下责任的产品 Owner：

- 同一 schema 下多个 root Table 的 composition boundary；
- root membership、stable logical slot 和 Group lifecycle；
- 多 source read guard 的 canonical acquisition order；
- Group structural/String/transient resource envelope；
- Group data version 与 aggregate observation；
- Group-owned release。

它明确不提供：

- 跨 root transaction、snapshot isolation 或 multi-root atomic mutation；
- application business consistency、MES sync、retry、compensation 或 active/staging
  swap；
- 一个 root fault 自动污染全部 root 的 shared fault boundary。

每个 root Table 及其全部 owned children 仍是一个独立 ownership/fault aggregate。
Group 不把多个 root 塞入同一个 `ChildOwnershipRegistry`：每个 root 创建自己的
registry、aggregate id 与 local fault state；Group ledger 只在其上协调总 resource
envelope、membership 和 acquisition order。

### 5.2 Member identity 与实例数

- Group 绑定一个 `SomaMetadata` / schema identity；
- V1 member slot 使用 root `SomaTableMetadata.logicalName()`；
- 一个 Group 对同一 logical root Table 最多有一个实例；
- 同一 schema 的第二实例必须位于另一个 Group；
- owned child 的多个实例由 parent row/field identity 管理，不占 root slot；
- Table 从 attach 到 Group release 永久属于该 Group，不支持 detach/reparent；
- root attach 只允许在 Group safe point，不能与 active operation/callback 并发；
- membership revision 可增长，但既有 slot identity 永不改变或复用。

该约束使 active/staging 自然使用两个 Group，避免 alias slot、shared fault 和未来
restore handle 语义膨胀。

### 5.3 Explicit 与 implicit Group

- `Table.create()` / `Table.create(RuntimePlan)` 创建 implicit single-root Group；
- implicit root 的 `table.release()` 委托并关闭整个 implicit Group；
- `Table.create(SomaGroup)` attach 到 explicit Group；
- explicit Group 中的 `table.release()` 确定性返回 ownership conflict；
- explicit Group 只由 `group.release()` 释放；
- Group release 先 preflight 所有 root 均无 active operation/view/callback，再按
  reverse attachment order 清理；正常 conflict 在任何 root release 前失败；
- preflight 后发生 INTERNAL/Error 时 Group 进入 `FAULTED`，后续只允许 observation
  与 best-effort release。

### 5.4 Fault 与 multi-source operation

- root aggregate 可为 `ACTIVE`、`FAULTED`、`RELEASED`；
- Group 可为 `ACTIVE`、`DEGRADED`、`FAULTED`、`RELEASED`；
- 一个 member fault 使 Group observation 为 `DEGRADED`，不阻止其他健康 root 的
  single-source operation；
- 需要 faulted member 的 operation 确定性失败；
- multi-root DataFlow 的全部 root 必须属于同一 explicit Group；
- multi-root read guard 按 aggregate instance id 排序 acquire，reverse order
  release，杜绝锁序漂移；V1 仍是 synchronous single-owner API。

### 5.5 Version 与 epoch

```text
schema identity       compatibility
Group membershipEpoch root membership observation validity
Table structuralEpoch current physical Index/view validity
Group/Table dataVersion application synchronization marker
```

Group 与每个 Table 的 data version 是独立 optional String value，只能在 safe point
set/clear。SOMA 不解释、不比较、不传播，也不要求单调。修改 data version 不修改
Table structural epoch。未来 snapshot 在 quiescent boundary 保存这些值。

## 6. Closed Capability Model

### 6.1 能力族与 Owner

| family | stable semantic owner | production binding |
|---|---|---|
| Schema/Metadata | processor + runtime control plane | compile / plan freeze |
| Storage/Layout | root TableStore | Group/Table create |
| Access | generated facade + TableStore | Table create / operation |
| Mutation | root ownership aggregate | operation preflight |
| Exact/Relation | Table access owner + DataFlow | template/invocation |
| Transformation | DataFlow Definition/Template | compile |
| Execution | DataFlow Context/Invocation | invocation preflight |
| Result Delivery | terminal Result owner | terminal definition/invocation |
| Resource | Group/Table/Invocation ledgers | create/preflight/phase |
| Observation/Failure | owning capability | immutable snapshot/failure |

这张表是设计 taxonomy，不要求十个 public interface。public/generated API 只投影
稳定语义；physical strategy 默认 package-private。

### 6.2 Binding 与替换

物理 binding 只发生在：

```text
processor generation
RuntimePlan build
Group/Table create
Template compile
Invocation preflight
terminal operation boundary
```

逐 row hot loop 不允许：

- interface/virtual callback graph 做 schema interpretation；
- reflection、`Object` carrier、boxing tuple、Map lookup；
- Metadata traversal 或 dynamic plugin registry；
- ServiceLoader、third-party SPI 或 application-supplied strategy。

内部 interface/generic 只可用于 cold boundary、test oracle 或确有两个可替换实现的
operation-level dispatch。替换完成后旧 production path 必须通过 replacement
closure 删除，不能永久保留“legacy + new”双 canonical path。

## 7. Storage 与 layout

### 7.1 两种受限 physical layout

一个 logical Table storage contract 可以绑定：

1. `FLAT`：Small/Medium、point-heavy、declared maximum 可由安全 contiguous array
   承载；
2. `FLAT_HEAD_SEGMENTED_TAIL`：Large scan/growth，保留一个 flat head，后续使用
   fixed-size tails。

application 不设置 Segment 魔数，只声明 expected maximum rows、workload profile
与 resource bounds。versioned internal cost formula 在 `RuntimePlan.build()` 选择
layout，并把实际 choice、segment rows 和原因写入 Effective Metadata/Explain。
TV 中的 32K 只是代表值，不是 public contract。

若实际增长超过 declared maximum rows，必须在 allocation 前 `RESOURCE_REJECTED`，
不得静默切换到不兼容 layout。

### 7.2 Segment publication

- Segment 是 storage/growth/GC unit；
- 同一 Table 的所有 payload/presence/row-link columns 先 stage 同一 segment ordinal；
- 全部 stage 和 budget admission 成功后，一次发布 segment directory/capacity；
- 失败时不改变 visible capacity、size、epoch 或 access state；
- 跨过 flat head 只允许一次受控迁移，后续 growth 不复制全部历史 columns；
- scan 使用 segment-aware outer loop；point access 用 stable row→segment formula；
- reserve、append、replace、swap-remove、clear、release 和 child handle columns 共享
  同一 segment publication invariant；
- `SomaSegmentMetadata` 只暴露 ordinal、row range、capacity、retained bytes 和
  lifecycle state，不暴露 backing arrays。

100M 仍使用 `int` current row index，因为受约束 V1 上限小于 `Integer.MAX_VALUE`。
current Index 永远不是 stable business identity。

## 8. Primary、Unique 与 Exact Index

Primary、Unique 和 secondary Exact Index 共享经过验证的 mechanical primitives，
但保持不同语义 Owner、diagnostic 和 mutation invariant。

### 8.1 Compact locator

- locator 保存 current row/group locator 与 compact fingerprint；
- 不在 bucket 中复制完整 key；
- hash/fingerprint collision 必须回查 authoritative generated columns；
- String 使用 `hashCode` 缩小候选并以 `equals` 最终确认；
- mutation relocation、swap-remove、epoch 和 rollback 必须同步；
- bucket/locator growth 纳入 retained + transient peak；
- 不采用 fixed sharding 作为默认；
- large locator arrays 使用同一 bounded segmented storage primitive，避免单个巨型
  Java array。

Primary 是 key→current row；Unique 是 duplicate constraint；grouped Exact 是
distinct key locator + row-indexed membership link。只有 schema 声明的 Exact/Unique
才长期维护 member link，不能把它变成 universal row tax。

### 8.2 String selectors

String leaf 可以作为 required Primary、Unique 和 Exact Index component。optional
selector component 继续非法；absence 不进入 selector equality。compile diagnostics、
generated method signature、batch/mutator/update/Delta、duplicate diagnostics、
clear/release 和 structural epoch 与 primitive selector 使用同一 contract。

## 9. Candidate 与中间结果

Candidate 是 internal physical shape，不是 public collection：

```text
Range
SegmentRange
Exact single-pass cursor
Bitmap
SparseIndexes
```

选择依据是 known cardinality、density、contiguity、downstream reuse/random access、
sort/barrier 需要和 budget。formula identity 与 choice 进入 Explain。

- contiguous single-pass 选 Range/SegmentRange；
- maintained exact + scalar/single-pass terminal 直接消费 exact cursor；
- ultra-sparse/reused 结果可用 compact indexes；
- dense membership/reuse 可用 Bitmap；
- sort、stable random access 或多次消费才允许 materialize；
- `IndexBuffer` 不再是所有 Candidate 的 universal representation；
- ordinary Iterator、public pull cursor 和 terminal-returned live Candidate 不存在。

Candidate stages 保持 lazy/fused；Definition/Template 的延迟执行与 Result Delivery
不是同一件事。

## 10. Transformation、Relation、Delta 与 Window

### 10.1 Specialized terminal

count/exists/any/none/arg-min/top-k/snapshot/projection/update/remove 等 terminal 使用
operation-specialized kernel。Eager result 在完整构造后一次 publish；Effect 只能
作用于一个 root ownership aggregate，并在 read guard 释放后的 controlled commit
boundary 执行。

### 10.2 GroupBy

- aggregate-only GroupBy 只维护 key→aggregate state 与 deterministic group order；
- member GroupBy 只由 `indexSnapshot`、member callback 或确需成员的 downstream
  语义触发；
- aggregate path 不构建 per-row group member links；
- String group key 使用 authoritative value equality；
- overflow、group cardinality 与 aggregate state bytes 在分配前 checked preflight。

### 10.3 Join

- maintained Primary/Unique/Exact 可满足 N:1、semi/anti/exists 时直接 access-assisted；
- count/exists/semi/anti 与可分解 aggregate 尽可能融合，避免 pair intermediate；
- bounded 1:N join→aggregate 可以先对 many side preaggregate；
- generic hash relation 只在 maintained access 不适用且 scratch bound 可接受时选择；
- order、outer absence、maximum multiplicity 和 output upper bound 是 semantic plan
  的一部分；
- String Join 使用同一 required `KeyExpression` value semantics。

### 10.4 Typed high-expansion preflight

若 cardinality/multiplicity/row width 可先验，使用 checked add/multiply 计算
operation scratch 与 final output upper bound。超出 budget 时必须在读取任何 source
row 或分配 operation state前返回 `RESOURCE_REJECTED`。

若 upper bound 依赖 source statistics，Invocation 可以在 acquire 后读取 size/
maintained cardinality metadata，但仍必须在枚举 pair 或分配大 output 前拒绝。
callback delivery 不能绕过这一规则。

### 10.5 Delta 与 Window

- Delta staging 与 changed-row cardinality 成正比；
- staging 包含 changed columns、presence、key/access relocation 和 rollback facts；
- changed-row plan 超过 deterministic crossover 时选择 bounded full rebuild；
- publication 仍是 single-aggregate atomic，失败不改变 data/epoch/index；
- count/sum 使用 running state，min/max 使用 monotonic deque；
- 只有 algebra、order、frame、absence 允许时才选择 incremental Window；
- fallback 与 scratch cost 进入 Explain，不维护平行语义。

## 11. Execution 与并行

三个物理粒度严格分离：

```text
Storage Segment
  -> zero / one / many Parallel Morsels
       -> cache/JIT-oriented Execution Vectors
```

- Segment 服务 storage/growth/GC；
- Morsel 是 scheduling、cancellation 和 deterministic merge unit；
- Execution Vector 是 inner-loop/cache/JIT block，不是 task；
- 大 Segment 可拆为多个 morsel，小 Segment 可合并；
- 所有 morsel 进入一个 bounded scheduler；不建立 nested executor 或 common-pool
  fallback；
- task count 可以大于 worker count，但必须小于 `maximumTasks`，以 bounded waves
  执行；
- worker scratch/stats/partial state 使用 disjoint slots，不在 steady hot path 写
  shared atomic/cache line；
- merge 按 task ordinal，order-sensitive terminal 保持 deterministic；
- cancellation/deadline 在 morsel/vector boundary 检查；
- managed/borrowed executor ownership 继续显式，只有 managed Context shutdown
  自己的 executor。

direct/parallel choice 使用 versioned deterministic cost formula，输入至少包含 rows、
operator kind、touched width、expression/hash cost、selectivity、scratch、workers 和
memory bandwidth proxy。它不只看 Segment 数或 row threshold。Small/Medium 有
direct fast path。

opaque application callback 的本轮 Result Delivery pilot 一律 sequential；未经新的
evidence 不在多个 worker 上并发调用 consumer。

## 12. Result Delivery

### 12.1 Eager Detached 默认

Eager Detached 是所有 terminal 的默认：

- full construction + publish-once；
- terminal 返回后不持有 source guard；
- result 与 source lifecycle 分离；
- failure/cancel/budget rejection 不暴露 partial result；
- scalar、detached columnar、materialized value 和 complete Effect 都适用。

### 12.2 callback-scoped streaming pilot

首批且唯一的 V1 pilot 是 generated typed Candidate visit terminal。processor 为每个
Table 的 Scan facade 生成：

```java
public interface Visitor {
    boolean visit(TableCursor value);
}
```

generated DataFlow Source 投影：

```java
public CallbackResultDelivery<Scan.Visitor> visitWhile(
        CandidateFlow<Binding> candidates);
```

canonical 使用方式为：

```java
CallbackResultDelivery<WorkStateScan.Visitor> delivery =
        source.visitWhile(candidates);
DeliveryResult result = delivery
        .newInvocation(context, visitor)
        .bind(source, binding)
        .execute();
```

`CallbackResultDelivery` 内部使用独立 typed invocation parameter slot；Definition/
Template 不保存 application consumer。它不是第三种 execution model。

语义冻结为：

- synchronous、one-shot、read-only、consumer 显式 opt-in；
- `false` 表示消费当前 value 后停止；当前 value 计入 `deliveredElements`；
- `DeliveryResult` 只含 `deliveredElements` 与 `completed`，consumer stop 时
  `completed=false`；
- callback exception、cancel、deadline、mutation/release conflict 都不返回
  `DeliveryResult`，而以结构化 failure 结束；
- callback 已发生的外部 side effect 不由 SOMA 回滚；
- Cursor/borrow 只在 callback active 时有效，不得缓存或跨线程使用；
- String getter 返回的 immutable String value 可以由 application 保留；这不会使
  Cursor、source guard 或其他 live view 可逃逸；
- source guard、scratch/resource lease 和 callback state 在 terminal 返回前关闭；
- high-expansion operation preflight 先于 callback；
- sorted/barrier semantics 可以保留 bounded operation scratch，但不得创建
  output-sized detached result。

普通 `Iterator<T>`、closeable pull cursor、Generator、Publisher、async push、
mutation/effect streaming 与 partial detached publication 继续排除。

## 13. Resource Model

### 13.1 账本与 phase lease

资源分为：

```text
SOMA-owned structural retained bytes
SOMA-retained reachable String bytes (estimated profile)
growth/rehash transient bytes
invocation shared scratch current / high-water
worker scratch current / high-water
detached output elements / bytes
tasks / workers / queue
JVM/application reserve and GC headroom
```

Group、Table 和 Invocation 分别拥有 ledger。scratch/transient 使用 phase lease：
acquire 时增加 current、release 时减少，high-water 单调记录；不能把顺序阶段的
累计 allocation 当成 simultaneous peak。output 在成功 publication 前保持 reservation。

每个 lease 的 acquire/release 必须在 `finally` 闭合。overflow、budget excess、
double-release 和 underflow 使用稳定 error code。

### 13.2 String 三层口径

1. `SOMA-owned structural bytes`：reference slots、presence、locator、row links、
   scratch/output；
2. `SOMA-retained reachable String bytes`：Table reference 使 String/object payload
   可达的 estimator；不宣称 SOMA 创建或独占；
3. `JVM observed heap`：指定 JDK vendor/build、reference width、GC、sharing 下的
   observation。

`StringResourceProfile` 是 Plan Metadata，包含第 3.1 节字段和 estimator identity。
Group create 用 declared expected rows/profile 做 admission；qualification 同时验证
actual workload 没有违反 profile。SOMA 不通过反射读取 String internals，也不在
hot path 建 object-identity set。

跨 Table sharing 报告同时给出 per-Table reachability 和 aggregate identity-deduped
reachability。任何未知/未声明 profile 只能获得 `unprofiled` observation，不能进入
String scale claim。

## 14. Failure、并发与 publication

operation phase 固定为：

```text
validate/bind
  -> acquire
  -> semantic/resource preflight
  -> execute into private state
  -> publish/commit
  -> observe
  -> cleanup/release
```

failure category 保持：

```text
INVALID_INPUT
COMPATIBILITY
RESOURCE
CONFLICT
LIFECYCLE
CALLBACK
INTERNAL
```

每个 failure 包含 stable code、operation、logical path、phase 和必要的
expected/actual/budget context，不把 String payload 或 application secret 写入
diagnostics。

- read-only failure 不改变 source；
- mutation/Delta/replace publication 前失败不改变 data/epoch/access state；
- Eager terminal publication 前失败不暴露 result；
- callback 外部 side effect 不具备 atomic rollback；
- source mutation/release 在 active read/callback 时返回 conflict；
- cleanup failure 作为 primary 或 suppressed failure 确定性传播；
- no-OOM 是 admission 目标，不宣称 JVM 绝不会因外部 heap pressure OOM。

## 15. Observation 与 Explain

immutable observation 至少回答：

- schema/plan/protocol identity；
- Group/member state、data version、membership/structural epoch；
- layout、Segment count/ranges、retained/high-water；
- String profile/estimator 与三层口径；
- access/locator retained、collision、rehash；
- Candidate shape、relation strategy、terminal specialization；
- direct/parallel reason、morsels/tasks/workers、fallback；
- scratch/output phase current/high-water 与 budget；
- Result Delivery mode、delivered/completed；
- operation outcome、failure code/phase。

Observation 不是 planner input cache；读取 observation 不改变 physical choice。
V1 不做基于 wall-clock 的运行时 self-tuning。

## 16. Snapshot/Restore 兼容基础

本轮不发布 snapshot format/API，但最终实现必须保留：

- stable schema、Group、member logical identity；
- Group/Table 独立 data version；
- generated/runtime/plan protocol identity；
- quiescent boundary：无 operation/view/callback/invocation；
- deterministic column/presence/access representation；
- restore 全部成功前不发布 partial Group；
- processor-generated codec 的未来扩展点。

binary format、checksum、I/O、fsync、portable/physical snapshot、fallback 和 MES
replay 属于未来独立设计，不进入本次代码。

## 17. Compatibility 与 migration

项目仍处于 G6 前，允许为闭合 Design 做有证据的 generated/public breaking change，
但不得同时保留两个事实体系。

迁移顺序：

1. 生成 `SchemaMetadata` 与完整 Descriptor；
2. `RuntimePlan` 成为 Metadata Plan/Effective 唯一 Owner；
3. 加入 `SomaGroup`，保留 implicit single-Table convenience；
4. multi-root consumer 迁移到 explicit Group；
5. 升级 generated/runtime、plan、transformation/kernel protocol identity；
6. golden、external Maven consumer、三个 Example 和 Guide 同步迁移；
7. replacement closure 后删除 duplicate default-plan emitter、旧 strategy strings、
   universal candidate path 和 superseded tests/docs。

不提供 reflection adapter、temporary public API、双 protocol runtime 或 runtime
schema interpreter。

## 18. A01–A21 Owner disposition

| Evidence | 最终设计裁决 | production Owner / slice |
|---|---|---|
| A01 | ACCEPT：完整 Metadata 分相 | processor + runtime，S1 |
| A02 | ACCEPT：FLAT small/point plan | runtime-core，S4 |
| A03 | ACCEPT：flat-head/segmented-tail Large plan | runtime-core + generated loop，S4 |
| A04 | ACCEPT：compact locator + authoritative equality | runtime-core + processor，S5 |
| A05 | ACCEPT：multi-shape Candidate | dataflow，S6 |
| A06 | ACCEPT：single-pass exact consumption | dataflow + generated access，S6 |
| A07 | ACCEPT：specialized eager + publish-after-complete | dataflow，S6 |
| A08 | ACCEPT：aggregate/member GroupBy 分责 | dataflow，S7 |
| A09 | ACCEPT：N:1/semi/anti/exists access fusion | runtime/dataflow/processor，S7 |
| A10 | ACCEPT：bounded 1:N preaggregation | dataflow，S7 |
| A11 | ACCEPT：changed-row Delta staging | runtime/processor，S8 |
| A12 | ACCEPT：incremental Window | dataflow；保留并补 evidence，S7 |
| A13 | ACCEPT：Definition/Template/Invocation + ordinal bind | dataflow；保留并移除剩余 hot dispatch，S6 |
| A14 | ACCEPT：single bounded morsel scheduler | dataflow，S9 |
| A15 | ACCEPT：worker-local + ordinal merge | dataflow，S9 |
| A16 | ACCEPT：managed/borrowed executor ownership | dataflow；保留并补 lifecycle evidence，S9 |
| A17 | ACCEPT WITH BOUNDS：narrow 100M composite | cross-owner qualification，S4–S10 |
| A18 | ACCEPT：typed high-expansion preflight | dataflow resource owner，S6/S7 |
| A19 | ACCEPT WITH BOUNDS：String-only reference semantics | processor/runtime/dataflow，S3/S5/S7 |
| A20 | ACCEPT：String profile + three-layer accounting | metadata/runtime/engineering，S2/S10 |
| A21 | ACCEPT FOR LIMITED PILOT：Eager default + candidate callback delivery | dataflow/processor，S10 |

其中“保留并补 evidence”仍要求逐调用链核实，没有证据不得把当前相似实现直接算作
完成。

## 19. Production implementation slices

每个 slice 必须保持工程可编译，并先做窄验证；只有阶段边界运行重 Gate。

| slice | 交付 | 必须通过的窄 evidence |
|---|---|---|
| S1 | protocol、Descriptor model、generated SchemaMetadata | compile/golden/external consumer |
| S2 | Plan/Effective/Observation、resource/String profile metadata | canonical identity/freeze/compatibility |
| S3 | SomaGroup、explicit/implicit ownership、version/fault/release | lifecycle/failure/multi-root differential |
| S4 | flat + segmented-tail storage、atomic segment growth | storage differential/growth/clear/release/allocation |
| S5 | compact Primary/Unique/Exact、String selector | duplicate/collision/mutation/Delta/GC differential |
| S6 | candidate shapes、phase lease、specialized terminal/preflight | oracle/budget/atomicity/intermediate allocation |
| S7 | GroupBy/Join fusion/preaggregation/String relation/Window | semantic differential/skew/overflow/order |
| S8 | changed-row Delta staging/crossover | Delta differential/atomicity/transient peak |
| S9 | bounded morsel scheduler/worker state | deterministic sequential-vs-parallel/cancel/cache evidence |
| S10 | callback pilot、observation/explain、Guide/benchmark hooks | eager-vs-callback differential/failure/allocation |

P7 disposition 可以把已完全符合的 production 事实标为 RETAIN，但不能跳过与本文
invariant 的逐项 trace。

## 20. Production-shape qualification

qualification 不是一个“100M passed”布尔值，而是以下固定 profile 集合：

| lane | profile 与问题 |
|---|---|
| Q-SMALL | 0/1/16/256/4K；create/point/scan/mutate；Metadata/Group/scheduler/callback fixed tax |
| Q-MEDIUM | 64K/256K；flat/segmented crossover、exact access、Group/Join、sequential/parallel |
| Q-1M | primitive + String payload/Key/Unique/Index/Group/Join、Delta、clear/release/GC |
| Q-10M | Large scan/growth、shared/high-cardinality String profiles、bounded relation/output |
| Q-100M-SINGLE | actual resident narrow two-long numeric-Key Table；exact reserve；bounded fused aggregate |
| Q-100M-DOUBLE | two simultaneously resident 100M root Tables in one Group；bounded 1:1/N:1/semi/aggregate |
| Q-100M-STRING | narrow reference payload with declared length/cardinality/identity/sharing；single/double roots；high-cardinality impossible profile preflight |
| Q-EXPANSION | checked high-multiplicity output rejected before row enumeration/allocation |
| Q-DELIVERY | Eager vs callback full/early-stop/failure/cleanup/allocation |

String Key/Unique/Index/Group/Join 的 full semantics 至少在 1M 生产 Table 通过；10M
执行代表性的低/高 cardinality lane。100M String lane 只承诺文档声明的 shared
payload profile，不外推为任意 String access role。

每条 lane 记录：

```text
git revision
JDK vendor/version/build
Maven / OS / architecture
heap / GC / reference model
schema and row width
left/right rows and simultaneously-live bytes
String profile
relation multiplicity/selectivity/skew
terminal/output bound
retained/transient/scratch/output/task high-water
elapsed/allocation/GC observation
correctness oracle and failure phase
```

100M 的目标是受约束 profile 下可完成、无无界 peak、无错误结果、无错误 readiness
外推。它不是任意 latency SLA。Small/Medium 不允许因 scale architecture 出现稳定
且无法解释的显著固定税；crossover 阈值由 benchmark Report 记录，不成为 public
compatibility contract。

## 21. 三个 Example 与产品化

production core 稳定后再审计三个 Example：

- 无设计/最佳实践偏差则 `RETAIN`，不做装饰性重构；
- multi-root runtime factory 必须迁移 explicit `SomaGroup`；
- plan configuration 应通过 generated `SchemaMetadata`，不手工拼平行 schema facts；
- RTD 只有在能减少真实 retained output 且不改变业务原子性时，才使用 callback
  pilot；最终 DispatchResult 保持 Eager Detached；
- Grassing 与 Scheduler 只迁移真实 contract，不为展示新 API 改写业务叙事；
- 每个 Example 保持独立 consumer Gate，example docs 只拥有 application facts。

产品文档最终必须覆盖：5 分钟 quick start、Metadata/Group mental model、String 和
resource profile、DataFlow、failure diagnostics、parallel/executor ownership、
Result Delivery、scale claim boundary、upgrade/migration。

## 22. 正式 Owner promotion map

P6 原子固化时：

| decision | formal Owner |
|---|---|
| 产品定位、canonical narrative、Group、Capability | Blueprint + system architecture |
| Metadata/type/generated API | schema/type/generated API Design |
| ownership/lifecycle/version/fault | ownership/lifecycle Design |
| storage/segment/locator/access | storage/access Design |
| Candidate/Relation/Delta/Window | transformation/dataflow/materialization Design |
| scheduler/Result Delivery | dataflow execution + materialization Design |
| resource/failure/observation | performance + correctness/failure + runtime plan Design |
| migration/protocol/security | compatibility/security/versioning Design |
| gaps during S1–S10 | Conformance |
| qualification/Gate/cleanup | Engineering + Report |

正式 Design 描述目标语义；P6 时尚未实现的部分必须同时进入 Conformance，不能让
代码事实冒充已符合。P8/P10 完成后再关闭相应偏差。

## 23. 明确拒绝与非目标

以下方向不得换名回流：

- universal segmented storage、universal Candidate indexes/bitmap；
- locator full-key duplication、fixed default sharding；
- per-row interface/Map/Metadata/Cursor interpretation；
- open SPI、ServiceLoader、plugin registry；
- arbitrary object、Collection/DTO live storage；
- ordinary Iterator、pull cursor、Publisher、async/partial result；
- streaming 掩盖 unbounded relation；
- nested executor、common-pool fallback、shared hot atomic stats；
-跨 root transaction、workflow engine、MES synchronization；
- dictionary/arena/intern String backend；
- 用 Lab/local evidence 声明 G6 或 public release readiness。

## 24. P4 完成与 P5 审计问题

本文关闭了原 Temporary 中的 blocking open questions：

- Metadata 层级、精确 public/generated entry、freeze 与 RuntimePlan 处置；
- Group member identity、同 logical root 实例数、release、fault、version；
- logical/physical capability binding；
- storage、locator、Candidate、relation、scheduler；
- callback pilot 的首批 terminal、consumer/result/lifetime；
- String semantics、accounting 与 qualification；
- production slices、Example 与 formal Owner promotion。

P5 独立审计只需回答：

1. 本文是否遗漏用户冻结的任一 Goal/invariant；
2. 是否把 TV mechanics 误写为无边界 product claim；
3. 是否存在 parallel fact、双 Owner、逃逸 lifecycle 或无法 replacement-close 的
   public surface；
4. S1–S10 是否构成最终设计的有效子集且可按序实施；
5. qualification 是否同时覆盖 Small/Medium、1M/10M、single/double 100M 和 String；
6. 是否诚实保持 G6 blocked。

审计不得重新开放 TV9 候选、做命名漫游或用当前实现困难缩减目标。
