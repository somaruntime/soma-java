# SOMA Runtime Boundary、Group、Scale Readiness 与产品化集成设计

类型：Temporary

状态：`independent_audit_changes_applied`

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
  -> application creates an implicit Group or freezes an explicit SomaGroupPlan
  -> generated root Tables atomically attach to stable logical member slots
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

需要共同 lifecycle、resource envelope 或稳定 composition identity 的多个 root Table
使用 explicit Group。Group 不是 read-only multi-source DataFlow 的强制边界：

```java
RuntimePlan machinePlan = MachineSchemaMetadata.newPlan().build();
RuntimePlan candidatePlan = CandidateSchemaMetadata.newPlan().build();
SomaGroupPlan groupPlan = SomaGroupPlan.builder("dispatch-runtime")
        .resourceBudget(groupBudget)
        .member("machines", MachineTable.metadata(), machinePlan)
        .member("candidates", CandidateTable.metadata(), candidatePlan)
        .build();
SomaGroup group = Soma.createGroup(groupPlan);
try {
    MachineTable machines = MachineTable.create(group, "machines");
    CandidateTable candidates = CandidateTable.create(group, "candidates");
    // finite typed multi-source DataFlow
} finally {
    group.release();
}
```

advanced path 显式暴露 Metadata、plan、budget、parallel 和 observation；simple
path 不要求理解这些对象。一个 Group 可以声明多个 schema，也可以用不同 member
slot 保存同一种 root Table 的多个实例；跨 Group、跨 schema、同类型不同实例和
self-join 的 read-only DataFlow 能力继续成立。

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
`RuntimePlan.Builder` 与 `SomaGroupPlan.Builder` 在 freeze 前可变。

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

SomaGroupPlan                        composition-scoped plan metadata
  -> stable logical group id
  -> one or more stable member slots
       -> SomaMetadata + root SomaTableMetadata + RuntimePlan
  -> GroupResourcePlan

SomaGroup.metadata()
  -> SomaGroupMetadata               detached runtime topology snapshot
       -> SomaTableRuntimeMetadata
       -> SomaSegmentMetadata
       -> SomaIndexRuntimeMetadata
       -> SomaUniqueRuntimeMetadata
```

`SomaIndexMetadata` 表示 schema-declared secondary exact selector，不表示 current
physical row `Index`。`SomaSegmentMetadata` 只属于 runtime topology Metadata，
不属于 logical schema Descriptor。

Metadata、Observation 与 Explain 的事实投影固定为：

| owner | 唯一拥有的事实 | 不拥有 |
|---|---|---|
| Descriptor Metadata | schema/type/field/key/index/ownership/default | runtime state、计数、strategy reason |
| Plan/Effective Metadata | caller plan、hard limit、resolved physical identity/budget | current/high-water、operation outcome |
| Group Runtime Metadata | detached group/member/table/storage topology 与 immutable identity snapshot | stats、failure outcome、DataFlow strategy |
| Group/Table Observation | lifecycle、current/high-water resource/access counters、fault | Candidate/relation/scheduler/delivery |
| DataFlow Explain | Definition/Template analyzed shape、formula identity、eligible strategy/reason | mutable runtime counters |
| Invocation Observation | 本次 bind/strategy/tasks/scratch/output/delivery/outcome/failure | Group topology Owner |

`metadata()`、`observe()` 和 `explain()` 每次返回 immutable detached snapshot；旧
snapshot 不会随 runtime 改变。Group/Table release 后仍可读取最后一个 terminal
metadata/observation snapshot。只有 Descriptor 与 Effective Metadata 进入 plan
identity；runtime Metadata、Observation 和 Explain outcome 不进入。

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
        .planningRows(10_000_000L)
        .maximumRows(100_000_000L)
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

`planningRows` 是 physical choice/cost formula 的非强制 hint；超过它不导致失败。
`maximumRows` 是 checked hard contract，append/reserve 在跨越它之前返回
`RESOURCE_REJECTED`。generated default 必须同时给出两个值；若 application 未显式
设置 hard maximum，processor/runtime 根据 V1 `int` row limit、row width与默认
structural budget 生成确定的 maximum，不能把“expected”暗中当 hard limit。

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

- 一个或多个 schema、一个或多个 root instance 的可选 composition boundary；
- stable logical group/member identity、root membership 和 Group lifecycle；
- member structural retained entitlement 与 storage/access growth transient hard
  envelope；
- Group data version 与 aggregate observation；
- Group-owned release。

它明确不提供：

- read-only multi-source DataFlow 的必要前提或 read-guard acquisition Owner；
- 跨 root transaction、snapshot isolation 或 multi-root atomic mutation；
- application business consistency、MES sync、retry、compensation 或 active/staging
  swap；
- 一个 root fault 自动污染全部 root 的 shared fault boundary。

每个 root Table 及其全部 owned children 仍是一个独立 ownership/fault aggregate。
Group 不把多个 root 塞入同一个 `ChildOwnershipRegistry`：每个 root 创建自己的
registry、aggregate id 与 local fault state；Group ledger 只在其上协调总 resource
envelope、membership 和 release。

### 5.2 Group plan、member identity 与 attach

- `SomaGroupPlan` 在 Group create 前冻结，拥有 application-supplied stable
  `logicalGroupId`、Group hard resource envelope 与一组 stable member slot；
- 每个 slot 由 Group 内唯一 `memberId`、`SomaMetadata`、root
  `SomaTableMetadata` 和 immutable `RuntimePlan` 定义；
- 同一个 schema 或 root Table descriptor 可以出现在多个不同 slot，从而支持
  active/staging、同类型不同实例和 self-join；
- Group runtime 另有 opaque `groupInstanceId`；它用于 guard/diagnostic，不冒充可
  跨 restore 的 logical identity；
- owned child 的多个实例由 parent row/field identity 管理，不占 root slot；
- Table 从 attach 到 Group release 永久属于该 Group，不支持 detach/reparent；
- 不允许 create 后动态新增 slot；未 attach slot 只保留 bounded plan metadata，
  不分配 Table storage，也不占 member structural entitlement；
- root attach 只允许在 Group safe point，不能与 active operation/callback 并发。

`Soma.createGroup(groupPlan)` 只验证 plan/protocol/identity、checked formula 与 Group
总 hard envelope，不为全部 slot 预分配或预记 Table bytes。
`Table.create(group, memberId)` 的 attach 是一个 publication transaction：

```text
validate exact slot/table/schema/plan and safe point
  -> parent Group ledger reserves member maximum structural entitlement
  -> acquire bounded initial-allocation/growth transient lease
  -> construct root registry/state privately
  -> publish root + slot membership + membershipEpoch once
  -> release transient lease
```

任一步失败都按逆序 rollback，且不改变 membership、ledger、epoch 或 visible Table。
root retained/current 明细由 root ledger 记录，但 Group ledger 是 hard-limit 的唯一
Owner；不能 parent/local 双重扣费。release 先释放 local state，再向 parent 返还
entitlement。

`membershipEpoch` 只在成功 attach 时增长。Runtime Metadata snapshot 以它判断
topology currentness；Definition/Template 不依赖 live membership，Invocation 始终
显式绑定 source，并在执行时验证 aggregate identity/lifecycle，所以 later safe-point
attach 不使既有 Template 失效。

### 5.3 Explicit 与 implicit Group

- `Table.create()` / `Table.create(RuntimePlan)` 创建 implicit single-root Group；
- implicit root 的 `table.release()` 委托并关闭整个 implicit Group；
- `Table.create(SomaGroup, memberId)` attach 到 explicit Group；
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
- read-only multi-source DataFlow 可以绑定同 Group、跨 Group、implicit Group、
  跨 schema、同类型不同实例或同 aggregate alias；
- DataFlow Invocation 是 source validation、alias de-duplication、canonical guard
  sort/acquire/reverse-release 的唯一 Owner；Group 只提供 stable member/aggregate
  identity；
- guard 按 opaque aggregate instance id 排序，同 aggregate alias 只 acquire 一次；
  V1 仍是 synchronous single-owner API；
- mutation/Effect 仍只允许一个 root ownership aggregate，不因跨 Group read 能力而
  获得 multi-root commit。

### 5.5 Version 与 epoch

```text
schema identity       compatibility
Group logicalGroupId  application/restoration identity
Group groupInstanceId current runtime guard/diagnostic identity
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

### 6.3 现有 generic/Object/callback surface 的 replacement closure

封闭类型目标不允许把当前 generic `Object` API 原样保留为事实。P7/P8 按下表处置，
相关 generated/runtime/dataflow protocol 在同一个可编译 migration slice bump：

| current surface | V1 disposition | replacement Owner |
|---|---|---|
| schema arbitrary reference field | REMOVE；processor stable diagnostic | 四类 schema type classifier，S1 |
| schema String through `objectValue` / `ObjectExpression` / `ObjectValueFlow` / `ObjectColumnResult` | MIGRATE | typed String reference column/expression/result，S1/S6 |
| enum/date/time/semantic scalar object carrier | MIGRATE | canonical primitive carrier；cold facade 才 materialize typed value，S1/S6 |
| `@SomaValue` object carrier | MIGRATE | generated flattened primitive/String leaf binding；detached boundary 才构造 value，S1/S6 |
| generic object invocation parameter/result | REMOVE from value system | typed primitive/String/flattened parameter key 与 typed detached result，S6 |
| Table Access callback | RETAIN scoped Access semantics | generated cursor fence；consumer 只在 call boundary，S3 |
| current Candidate/Value/Group/Join/Window `borrow(consumer)` | MIGRATE, no parallel legacy | 第 12 节统一 Callback Delivery Definition/Template/Invocation，S6/S10 |
| test oracle/cold internal generic helper | RETAIN internal only | testkit/internal boundary |

executor、callback visitor 和 application sidecar handle 是 control/application
boundary object，不是 schema/DataFlow value。Java generic erasure 可能在冷边界产生
synthetic bridge，但不得让逐 row kernel 使用 `Object` carrier、cast 或 boxing。

## 7. Storage 与 layout

### 7.1 两种受限 physical layout

一个 logical Table storage contract 可以绑定：

1. `FLAT`：Small/Medium、point-heavy、declared maximum 可由安全 contiguous array
   承载；
2. `FLAT_HEAD_SEGMENTED_TAIL`：Large scan/growth，保留一个 flat head，后续使用
   fixed-size tails。

application 不设置 Segment 魔数，只声明 planning rows、hard maximum rows、
workload profile 与 resource bounds。versioned internal cost formula 在
`RuntimePlan.build()` 选择 layout，并把实际 choice、segment rows 和原因写入
Effective Metadata/Explain。
TV 中的 32K 只是代表值，不是 public contract。

若实际增长超过 hard maximum rows，必须在 allocation 前 `RESOURCE_REJECTED`，
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
- `SomaSegmentMetadata` 只暴露 ordinal、row range、capacity 和 backing identity，
  不暴露 backing arrays；retained/high-water/lifecycle 属于 Table Observation。

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
- locator backing 是 internal `FLAT` / `BOUNDED_SEGMENTED` candidate；容量、
  contiguous allocation risk、growth/rehash peak 和 probe/point cost 进入 versioned
  formula；
- TV7 验证的 flat `int[]` + compact fingerprint 是 production baseline。
  `BOUNDED_SEGMENTED` 只有在 S5 及 Q-100M 的 point lookup、collision、rehash、
  growth peak evidence 通过后才能成为有效 binding；未通过时保持 flat 或在
  allocation 前拒绝，不能用实验推断强制启用。

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

relation strategy 使用 versioned deterministic internal cost formula，而不是冻结
TV crossover 常数。输入至少包括 maintained-access availability/build/probe cost、
fan-out/multiplicity/skew、reuse/pass count、preaggregate state bytes、generic hash
scratch、output bound、order/barrier 与 touched width。choice、formula identity、
eligible/rejected reason 进入 Explain；S7 在 Q-MEDIUM/Q-10M 做 semantic differential
和 crossover evidence。

### 10.4 Typed high-expansion preflight

若 cardinality/multiplicity/row width 可先验，使用 checked add/multiply 计算
operation scratch 与 final output upper bound。超出 budget 时必须在读取任何 source
row 或分配 operation state前返回 `RESOURCE_REJECTED`。

若 upper bound 依赖 source statistics，Invocation 可以在 acquire 后读取 size/
maintained cardinality metadata，但仍必须在枚举 pair 或分配大 output 前拒绝。
callback delivery 不能绕过这一规则。

若 compiler/plan-derived facts 或经过验证的 maintained facts 无法证明 finite
scratch/output upper bound，则除具有独立有界 state 的 scalar/fused semantic
terminal 外，Invocation 必须在 relation enumeration、operation-state allocation
和 callback 启动前 fail closed，返回 typed `RESOURCE_REJECTED`。`streaming`、
early stop 或“consumer 可能不读完”都不是未知上界的准入证明。

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

callback-scoped streaming 是唯一 Lazy Output capability。首个 frozen generated
surface 是 typed Candidate visit terminal；processor 为每个 Table 的 Scan facade
生成：

```java
public interface Visitor {
    boolean visit(TableCursor value);
}
```

generated DataFlow Source 投影：

```java
public CallbackDeliveryDefinition<Scan.Visitor> visitWhile(
        CandidateFlow<Binding> candidates);
```

canonical 使用方式为：

```java
CallbackDeliveryDefinition<WorkStateScan.Visitor> definition =
        source.visitWhile(candidates);
CallbackDeliveryTemplate<WorkStateScan.Visitor> template =
        definition.compile();
DeliveryResult result = template
        .newInvocation(context, visitor)
        .bind(source, binding)
        .execute();
```

`CallbackDeliveryDefinition/Template/Invocation` 只是现有
`DataFlowDefinition/Template/Invocation` 的 generated typed facade：使用同一个
analyzer、identity、explain、binding、guard、budget 和 failure lifecycle，不暗中
compile，也不拥有第二套 invocation。visitor 使用独立 typed invocation parameter
slot；Definition/Template 不保存 application consumer，consumer identity 不进入
Definition/Template identity。

为避免“旧 borrow + 新 delivery”双轨，现有 Candidate、primitive/String Value、
Group、Join 和 Window `borrow(consumer)` 的只读语义在 S6/S10 迁入同一 lifecycle，
旧 signature 完成 replacement closure 后删除。迁移不新增新的 shape：

- Candidate visitor 接收单一 generated Cursor；
- primitive/String projection visitor 接收 typed value；
- Group visitor 保留 current group key/count/member 语义；
- Join visitor 明确 left/right Cursor、outer absence、order 和两者共同 callback
  lifetime；
- Window visitor 保留 current frame/order 语义。

每个 shape 都必须独立通过 Q-DELIVERY 才能成为 supported surface；TV9 只接受
mechanics，不直接验证新的 generated Cursor facade。在 S10 与 production-shape
qualification 关闭前，这一 capability 保持 Conformance gap；若某 shape 验证失败，
不能恢复 legacy path或自行降低既有目标；Eager Detached 只提供结果语义 fallback，
该 shape 保持正式 Conformance gap，P10/P11 不能把它算作 replacement closure，除非
修复后通过或产品 Owner 明确降低目标。

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
  output-sized detached result；
- cancel/deadline 使用 production `CancellationToken`、monotonic deadline 和
  cross-thread visibility contract；TV9 的 ordinal surrogate 不能冒充该证据。

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

ledger 层级固定为：

- `GroupLedger` 是其 member structural entitlement、actual retained 和
  storage/access growth transient hard limit 的唯一 Owner；
- root `TableLedger` 是 parent-owned attribution/detail，不拥有第二份预算；attach、
  growth、rehash 和 release 都通过 parent atomic reserve/commit/rollback/return；
- implicit Group 使用同一协议，但只含一个 root，不为 schema 中未使用 Table 计费；
- `InvocationLedger` 独立拥有一次 Invocation 的 shared/worker scratch、output、
  tasks/workers/queue；source retained bytes 不在多个 Group 与 Invocation 间重复
  计数；
- cross-Group read-only Invocation 仍只使用一个 Invocation ledger；各 source Group
  ledger 不因此合并。

scratch/transient 使用 phase lease：acquire 时增加 current、release 时减少，
high-water 单调记录；不能把顺序阶段的累计 allocation 当成 simultaneous peak。
output 在成功 publication 前保持 reservation。

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
三层口径具有不同的可执行强度：

| layer | production strength |
|---|---|
| structural bytes | exact hard ledger；allocation 前 reserve/reject |
| retained reachable String bytes | caller-declared、versioned estimator；`DECLARED_UNVERIFIED` planning fact |
| JVM observed heap / identity-deduped reachability | qualification-only observation |

Plan freeze/attach 会检查 String profile 的完整性、checked estimate 与 caller planning
limit；declared estimate 自身超限可以拒绝。但 SOMA 保存 caller reference，且不在
write hot path 读取 String internals或维护 object-identity set，所以不能验证实际
length、identity cardinality、sharing，也不能把 profile 宣称为 production hard
memory cap。application 对 profile 真实性负责；Observation 必须标记
`PROFILED_UNVERIFIED` 或 `UNPROFILED`，不得发布伪精确的 aggregate dedup bytes。

qualification 使用外部/测试专用 instrumentation 核对 actual workload，并在 Report
中同时给出 per-Table estimate、identity-deduped aggregate reachability 和 JVM heap。
任何未知/未声明或被 evidence 违反的 profile 都不能进入 String scale claim。若未来
要求可执行的 String hard cap，必须另行设计 write-time constraint 和代价；本轮不能
用声明冒充。

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
LOOKUP
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

immutable observation 按模块单向组合，不建立 runtime-core → dataflow 依赖：

- Group/Table Observation 回答 lifecycle/fault、data version、
  membership/structural epoch、retained/high-water、String profile status、
  access collision/rehash；对应 topology/physical identity 只引用 runtime Metadata；
- DataFlow Explain 回答 analyzed Candidate/relation/terminal eligibility、cost formula
  identity、direct/parallel reason 和 rejected alternative；
- Invocation Observation 回答本次 morsels/tasks/workers/fallback、scratch/output
  phase high-water、Result Delivery mode/delivered/completed、outcome/failure；
- dataflow 可以引用 runtime observation component，runtime-core 不认识 Candidate、
  relation、scheduler 或 delivery type。

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
- processor 已拥有的 deterministic column/access identity；本轮不新增 codec
  interface、SPI 或 placeholder public API。

binary format、checksum、I/O、fsync、portable/physical snapshot、fallback 和 MES
replay 属于未来独立设计，不进入本次代码。

## 17. Compatibility 与 migration

项目仍处于 G6 前，允许为闭合 Design 做有证据的 generated/public breaking change，
但不得同时保留两个事实体系。

迁移顺序：

1. 生成 `SchemaMetadata` 与完整 Descriptor；
2. `RuntimePlan` 成为 Metadata Plan/Effective 唯一 Owner；
3. 加入 `SomaGroupPlan/SomaGroup`，保留 implicit single-Table convenience；
4. 只有需要共同 composition/lifecycle/resource 的 consumer 迁移 explicit Group；
   ordinary cross-aggregate DataFlow 不被强制迁移；
5. 原子替换 generic Object value protocol 与 legacy borrow lifecycle；
6. 升级 generated/runtime、plan、transformation/kernel protocol identity；
7. golden、external Maven consumer、三个 Example 和 Guide 同步迁移；
8. replacement closure 后删除 duplicate default-plan emitter、旧 strategy strings、
   universal candidate path 和 superseded tests/docs。

不提供 reflection adapter、temporary public API、双 protocol runtime 或 runtime
schema interpreter。

breaking impact 在 P7 disposition 前冻结如下；P8 每项必须具有 regeneration、
external consumer 与 migration evidence：

| surface/change | classification | replacement / identity |
|---|---|---|
| per-generated-Table default plan emitter | generated breaking | schema-scoped `SchemaMetadata` owner；plan/protocol bump |
| raw public `RuntimePlan.builder(...)` | public breaking | generated `newPlan()`；plan identity bump |
| `expectedMaximumRows` ambiguity | plan breaking | `planningRows` + hard `maximumRows`；canonical hash bump |
| explicit Group `Table.create/release` | additive + lifecycle breaking when adopted | stable member slot；explicit table release returns conflict |
| String selector compile rejection | schema/generated additive | typed String Key/Unique/Index；schema/generated protocol bump |
| generic Object value APIs | public/generated breaking | 第 6.3 节 typed closed-kind protocol |
| `borrow(consumer)` retains consumer in Definition | public/dataflow breaking | 第 12.2 节 callback facade；Definition/Template identity excludes consumer |
| locator/layout strategy identity | effective-plan internal breaking | versioned formula/physical identity；no public magic number |
| runtime/group/dataflow observation split | public additive/replacement | module-owned immutable component；no duplicate Owner |

P8 只做 core contract 落地和为保持编译/lifecycle 所必需的 consumer migration；P9
才做三个 Example 的独立产品/最佳实践审计。explicit Group/generated Metadata 属于
真实 contract migration，callback 展示、业务流程重排和装饰性重构不属于 P8 自动
授权。

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
| A21 | ACCEPT FOR LIMITED PILOT：Eager default + one unified callback delivery capability | dataflow/processor，S6/S10 |

其中“保留并补 evidence”仍要求逐调用链核实，没有证据不得把当前相似实现直接算作
完成。

## 19. Production implementation slices

每个 slice 必须保持工程可编译，并先做窄验证；只有阶段边界运行重 Gate。

| slice | 交付 | 必须通过的窄 evidence |
|---|---|---|
| S1 | protocol、Descriptor、four-kind classifier、generated SchemaMetadata/typed String protocol | compile/golden/negative diagnostics/external consumer |
| S2 | Plan/Effective、SomaGroupPlan、module-owned observation envelope、resource/String profile | canonical identity/freeze/compatibility/projection ownership |
| S3 | SomaGroup attach transaction、parent/root ledger、explicit/implicit ownership、version/fault/release | lifecycle/rollback/ledger/multi-schema/multi-instance differential |
| S4 | flat + segmented-tail storage、atomic segment growth | storage differential/growth/clear/release/allocation |
| S5 | compact Primary/Unique/Exact、String selector、locator backing formula | duplicate/collision/mutation/Delta/point/rehash/growth/GC differential |
| S6 | closed-value protocol replacement、candidate shapes、Invocation phase lease、specialized terminal/fail-closed preflight、callback core lifecycle | oracle/protocol/budget/atomicity/intermediate allocation |
| S7 | GroupBy/Join fusion/preaggregation/String relation/Window/cost formula | semantic differential/skew/overflow/order/crossover |
| S8 | changed-row Delta staging/crossover | Delta differential/atomicity/transient peak |
| S9 | bounded morsel scheduler/worker state | deterministic sequential-vs-parallel/cancel/cache evidence |
| S10 | generated callback surfaces、observation component composition、Guide/benchmark hooks | eager-vs-callback shape differential/failure/non-escape/allocation |

P7 disposition 可以把已完全符合的 production 事实标为 RETAIN，但不能跳过与本文
invariant 的逐项 trace。

## 20. Production-shape qualification

qualification 不是一个“100M passed”布尔值，而是以下固定 profile 集合：

| lane | profile 与问题 |
|---|---|
| Q-SMALL/FAST | 0/1/16/256/1K/4K primitive + String；create/point/exact/scan/column/batch/mutate/Group/Join/callback；Metadata/Group/scheduler fixed tax |
| Q-MEDIUM | 32K/64K/256K primitive + String payload/selector；flat/segmented crossover、point/exact/scan/column/batch/Delta/Group/Join/Window、sequential/parallel |
| Q-1M | primitive + String 全 workload：Point/Exact/Scan/Column/Batch/Delta/Join/Group/Window、clear/release/GC |
| Q-10M | Large scan/growth、shared/high-cardinality String、bounded relation/output、relation/parallel crossover |
| Q-100M-SINGLE | actual resident narrow two-long numeric-Key Table；exact reserve；bounded fused aggregate |
| Q-100M-DOUBLE | two simultaneously resident 100M roots；same-Group 与 cross-Group acquisition；bounded 1:1/N:1/semi/aggregate |
| Q-100M-STRING | narrow reference payload with declared length/cardinality/identity/sharing；single/double roots；impossible declared profile rejection 与 actual qualification |
| Q-EXPANSION | known-overflow、known-over-budget 与 unknown-unprovable bound 都在 relation/callback 前拒绝 |
| Q-DELIVERY | Eager vs Candidate/Value/Group/Join/Window callback；full/early-stop/exception/real cancel/deadline/conflict/segment-boundary/non-escape/use-after-callback/String/GC/resource |
| Q-SOAK | repeated create/load/mutate/Delta/callback/clear/release；managed/borrowed executor、fault cleanup、String reference reclamation、ledger 回零/high-water、GC |

String Key/Unique/Index/Group/Join 的 full semantics 至少在 1M 生产 Table 通过；10M
执行代表性的低/高 cardinality lane。100M String lane 只承诺文档声明的 shared
payload profile，不外推为任意 String access role。

Q-1M String 还必须显式覆盖 required/optional presence、空字符串、equal-value
different-object append/no-op mutation、epoch 与 retained reference、order/filter/
materialization、clear/release，以及含 String leaf 的 `@SomaValue`。Q-100M locator
同时覆盖 flat baseline 与任何拟启用 segmented candidate 的 point lookup、
collision、rehash 和 growth peak。

每条 lane 在执行前 preregister：

```text
git revision
Azul Zulu full JDK 8 vendor/version/build
Maven / OS / architecture / JVM args / heap / GC / reference model
dataset seed / schema / row width / left-right rows
String profile / relation multiplicity-selectivity-skew
correctness oracle and checksum
retained/transient/scratch/output/task budget
operational timeout
warmup / measurement / fork
baseline comparator + tolerance or structural pass rule
passed / failed / inconclusive / not-applicable rule
claimAllowed=false
```

运行 artifact 再记录 simultaneously-live bytes、各 phase high-water、elapsed、
allocation、GC、oracle/failure phase 和 environment fingerprint。统一复用正式
Benchmark Governance 的 artifact/comparator 规则，不新造模糊阈值。P10 要求所有
applicable required lane 为 `passed`；`inconclusive` 不是通过。

100M 的目标是受约束 profile 下可完成、无无界 peak、无错误结果、无错误 readiness
外推。operational timeout 只防止无界执行，不是 public latency SLA。Small/Medium
fixed-tax 判定使用预注册 baseline/tolerance；crossover 由 versioned formula 和
Benchmark Report 记录，不成为 public compatibility contract。

## 21. 三个 Example 与产品化

production core 稳定后再审计三个 Example：

- 无设计/最佳实践偏差则 `RETAIN`，不做装饰性重构；
- 只有确需共同 lifecycle/resource/composition identity 的 multi-root runtime
  factory 才迁移 explicit `SomaGroup`；
- plan configuration 应通过 generated `SchemaMetadata`，不手工拼平行 schema facts；
- RTD 当前 sorted `JoinedIndexResult` 在本轮默认 `RETAIN Eager`；只有 P9 证明对应
  Join visitor 能减少真实 retained output且不改变 order/business atomicity，才采用
  callback；最终 DispatchResult 保持 Eager Detached；
- Grassing 与 Scheduler 只迁移真实 contract，不为展示新 API 改写业务叙事；
- 每个 Example 保持独立 consumer Gate，example docs 只拥有 application facts。

产品文档最终必须覆盖：5 分钟 quick start、Metadata/Group mental model、String 和
resource profile、DataFlow、failure diagnostics、parallel/executor ownership、
Result Delivery、scale claim boundary、upgrade/migration。

产品化验收不是主题清单：必须提供可独立 Maven compile/run 的 simple-path quick
start、explicit Metadata+Group advanced path、String/resource/scale-boundary
示例，以及 diagnostics cookbook（stable code、phase、logical path、budget context、
建议行动）。Guide snippet 由 external consumer fixture 或 source-snippet Gate
验证；Guide 只解释正式 Design，不成为第二事实 Owner。

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

本 Goal 使用 Owner 已授权的 design-first 两阶段 promotion：

1. P6 将全部 accepted target 语义原子写入正式 Blueprint/Design/Engineering，并在
   同一 commit/commit series 把尚未实现项逐项写入 Conformance；
2. P6 后本 Temporary 不再拥有设计事实，design-bearing 文件缩减为正式 Owner
   pointer/执行 trace，避免形成第二套规范；
3. P8/P10 只依据正式 Design 实施并以 evidence 关闭 Conformance；
4. P11 在正式 Report/Guide/Implementation Map/Conformance 自包含且引用扫描闭合后，
   删除剩余 Temporary 与一次性独立 Lab，不归档。

这不是把未实现能力声明为当前支持；正式 Design 是 target Owner，Conformance 是
current gap Owner。

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
- 跨 root transaction、workflow engine、MES synchronization；
- dictionary/arena/intern String backend；
- 用 Lab/local evidence 声明 G6 或 public release readiness。

## 24. P5 独立审计结论与退出条件

独立审计最初裁决为 `CHANGES_REQUIRED / BLOCKED_FOR_P6`。本文已应用其最小修正：

- Group plan/attach/resource hierarchy 已闭合，且保留 cross-Group/cross-schema/
  multi-instance/self-join read-only DataFlow；
- runtime Metadata、Group/Table Observation、DataFlow Explain/Invocation
  Observation 已消除双 Owner 与模块反向依赖；
- structural hard ledger、String declared estimate 与 qualification-only heap 已分级；
- generic Object 与 incumbent borrow surface 已有 atomic replacement matrix；
- callback 已回到唯一 Definition→Template→Invocation lifecycle；
- locator segmented backing 不再由 TV evidence 过度外推；
- unknown high-expansion bound 已 fail closed；
- qualification 已覆盖 String Small/Medium、全部 workload、Fast/Soak、真实 callback
  lifecycle 与可判定 preregistration；
- breaking migration、Guide product acceptance、P8/P9 分层和 P11 临时资产删除已闭合。

P5 的独立事实、证据与 re-audit 结论由
[独立设计与范围审计](independent-design-and-scope-audit.md)保存。只有该审计确认
全部 BLOCKER 关闭后才进入 P6；不得用本文作者自审代替。
