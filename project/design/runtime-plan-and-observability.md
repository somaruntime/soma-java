# Runtime Plan 与可观测性设计

类型：Design

状态：正式

Owner：SOMA runtime configuration 与 observability semantics

设计层次：`D2` 能力设计

主要关注点：Metadata phases、create-time plan、Group/resource admission、stats 与诊断边界

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprint/soma-java-product-blueprint.md)

事实范围：Descriptor/Plan/Effective/Runtime Metadata 分相、create-time plan、
Group/resource admission、plan identity、stats/observation/explain 边界

非事实范围：Java builder 的当前完整方法清单、内部计数器字段、某次统计结果和 benchmark 阈值

最后审查日期：2026-07-29

本 Owner 把 plan 与 observability 放在同一能力边界：plan 决定实例创建时准入哪些资源和观测成本，observability 只能暴露该实例已选择的模式，不能反向引入隐藏策略。当前 builder 方法和计数器布局属于实现事实。

## 1. Runtime Plan 的角色

`RuntimePlan` 是 schema-seeded、create-time immutable execution contract。它在
aggregate 发布前确定 capacity、resource、layout/access、stats 和 materialization
策略；hot path 不重复解析 Metadata，也不从全局配置、环境变量或动态 registry
隐式取值。

Plan 至少覆盖以下维度：

- schema/generated/runtime protocol identity；
- root table 与所有 reachable child table 的有效 table plan；
- initial capacity、growth、primary locator、locator physical layout formula 与
  secondary exact-access policy；
- aggregate storage、bulk scratch、retained scratch 和 materialization budget；
- stats mode 与诊断采集级别；
- estimator、algorithm 和 plan protocol identity。
- 每个 Table 的 non-binding `planningRows` hint 与 checked hard `maximumRows`。

这里规定语义类别；当前 Java signature、默认常量和字段清单由[可执行契约地图](../implementation-map/executable-contract-map.md)定位。

完整 Metadata control plane 分相为：

| phase | Owner/语义 | 可变性 |
|---|---|---|
| Descriptor | generated schema/type/access/ownership facts；精确组成见 Schema Owner | immutable |
| Plan Builder | application 只修改开放的 capacity/workload/resource/output/stats/parallel/String profile | freeze 前 parent-owned mutable editor |
| Effective Metadata | validated RuntimePlan/TablePlan/ChildPlan/layout/access/resource identity | immutable |
| Runtime Metadata | detached Group/member/Table/Segment/access topology snapshot | runtime 变化时重新 snapshot |
| Observation/Explain | module-owned current/high-water/outcome/physical reason | immutable detached snapshot |

payload、presence、locator/link、membership registry、candidate、scratch 和 output
不是 Metadata。Builder build 后及其 child editor 全部失效；修改 Builder 不影响已
build plan，修改 plan 不影响已创建 instance。

## 2. Scope 与绑定

- generated facade 提供 schema-specific default plan，并允许 caller 在 create 前基于 builder 形成显式 override；
- canonical entry 是 generated `SchemaMetadata.newPlan()`；raw
  `RuntimePlan.builder(schemaHash, protocol...)` 只属于 generated/internal
  protocol；
- override precedence 必须确定，未识别 table/child/field/selector path 必须 fail closed；
- root plan 覆盖整个 ownership aggregate，child 不能在 attach 后拥有脱离 root 的隐式 plan；
- schema-scoped RuntimePlan 可以包含多个 root 的配置全集；未 attach root 只保留
  bounded Metadata，不产生 storage/resource reservation。Group member attach 只
  绑定该 root 及其 reachable child subtree；
- effective plan 在 create 时完成 schema、protocol、resource 和 ownership validation，再绑定到 aggregate；
- table 已创建后不能替换 plan，也不能因后续全局配置变化而改变行为；
- `clear()` 不改变 plan；`release()` 后只能按 lifecycle contract 读取允许保留的 plan/diagnostic identity。

Schema default 是生成时的稳定输入，runtime default 是协议版本的一部分，application override 是本次 instance 的显式选择。三者不能用未记录的 fallback 混合。

`planningRows` 只进入 versioned physical cost formula，超过它不失败；
`maximumRows` 是 hard contract，reserve/append 在跨越前以 resource failure 拒绝。
未显式设置 maximum 时，generated default 根据 V1 `int` row boundary、row width
和 default structural budget确定生成，不允许一个含混的 expected 字段同时承担
hint 与 hard limit。

## 2.1 SomaGroupPlan 与 attach

`SomaGroupPlan` 是 composition-scoped immutable Plan Metadata，至少包含 stable
`logicalGroupId`、Group hard resource envelope 与 stable member slots。每个 slot
由 Group 内唯一 `memberId`、root Table Descriptor、对应 `SomaMetadata` 和
`RuntimePlan` 定义；同一 schema/root descriptor 可以出现在多个 slot。

Group create 只验证全部 identity、formula 和总 envelope，不为未 attach slot
预分配或预记 Table bytes。Table attach 在 publication 前原子完成 parent Group
member entitlement、initial/transient lease 与 private root construction；任何失败
都 rollback，且不改变 membership、ledger 或 epoch。Simple `Table.create()` 使用
只含该 root 的 implicit Group。

## 3. Validation 与 resource admission

Plan construction/create 必须拒绝：

- 非法、负值、overflow 或彼此矛盾的 capacity/byte/count 限制；
- 缺失或多余的 table/child plan；
- 与 schema/generated/runtime identity 不匹配的 algorithm、estimator 或 protocol；
- 无法覆盖最小合法 storage、required child 或 operation scratch 的预算；
- 会把 bounded resource policy 退化为 silent unbounded growth 的配置。

Mutation/materialization 在执行前使用 effective plan 做资源 preflight。Expected resource rejection 保持旧 stable state；raw JVM fatal allocation error 不包装成可恢复业务失败。

resource ledger hierarchy 为：

- GroupLedger 唯一拥有 member maximum structural entitlement、actual retained 与
  storage/access growth transient hard limit；
- TableLedger 只做 parent-owned attribution，不二次扣费；
- InvocationLedger 独立拥有一次 DataFlow shared/worker scratch、output、tasks/
  workers/queue，不重复统计 source retained；
- transient/scratch/output 使用 `finally`-closed phase lease，current 可回落，
  high-water 单调。

String reference slot/presence/locator 是 exact structural hard bytes。reachable
String bytes 只能由 versioned `StringResourceProfile` 形成 caller-declared
`PROFILED_UNVERIFIED` estimate；runtime 不读取 String internals或维护 identity
set，因而不能把 length/cardinality/sharing 声明当作 hard memory cap。Actual
identity-dedup reachability 与 JVM heap 只属于 qualification Report。未声明 profile
为 `UNPROFILED`，不能进入 String scale claim。

`StringResourceProfile` 至少声明 UTF-16 code-unit length、value cardinality、
distinct object identity estimate、intra/inter-table sharing、presence ratio、
payload/Key/Unique/Index/Group/Join role、simultaneously-live Table count 与
estimator identity。Plan freeze/attach只验证这些声明和checked estimate；profile
真实性由application负责，qualification负责核对。

该 profile 不是 Schema length、fixed-width layout 或 mutation admission。它在
Plan freeze 前可修改，freeze 后只描述 caller 预计 workload；append/update 可以
保存任意合法长度的 String reference。实际长度/cardinality/sharing 偏离 profile
时，Observation 标明 claim 不适用或需重新 qualification，不得拒绝 value、搬迁
String payload 或暗中改变 storage backend。

## 4. Identity 与兼容性

Effective plan 必须有 deterministic identity/hash。Hash 输入使用明确的 canonical order、width 和 encoding，不依赖 locale、filesystem、reflection/hash iteration 或 builder 调用顺序。

Schema hash 与 runtime plan hash 表达不同事实：前者标识 schema contract，后者标识 instance execution policy。Plan hash 不证明结果相同、性能相同或支持矩阵通过；它只证明参与 identity 的 effective plan fields 相同。

改变 plan protocol、hash input、default strategy 或 compatibility meaning 时，必须经过 compatibility review，并同步 generated metadata、runtime verification、golden 与 external consumer evidence。

## 5. Metadata、Observation 与 Explain Owner

同一事实只投影一次：

| object | facts |
|---|---|
| Descriptor Metadata | schema/type/access/ownership/default |
| Effective Metadata | resolved plan/layout/access/resource identity |
| `SomaGroupMetadata` | detached Group/member/Table/Segment/access topology |
| Group/Table Observation | lifecycle/fault/current/high-water/resource/access counters |
| DataFlow Explain | Definition/Template eligibility、formula identity、strategy reason |
| Invocation Observation | 本次 bind/execute/tasks/scratch/output/delivery/outcome/failure |

Runtime Metadata/Observation 在调用时 snapshot，旧 snapshot 保持 detached historical
value；release 后允许读取最后 terminal snapshot。只有 Descriptor 与 Effective
Metadata 进入 plan identity。Runtime-core 不依赖 DataFlow type；dataflow 可以组合
runtime component，但不把 Candidate/relation/scheduler facts写回 Group Metadata。

Runtime topology types至少包括 `SomaGroupMetadata`、
`SomaTableRuntimeMetadata`、`SomaSegmentMetadata`、
`SomaIndexRuntimeMetadata` 与 `SomaUniqueRuntimeMetadata`。Schema-side
`SomaIndexMetadata/SomaUniqueMetadata`描述声明语义，runtime variants描述当前
physical binding；二者不能同名冒充。

## 6. Stats model

Stats 是 immutable observation，不是业务事实。Snapshot 必须区分：

- current facts：rows、capacity、active view/operation、released state、current retained bytes；
- since-reset counters：operations、scanned、matched、changed、probes、collisions、rehashes 等；
- lifetime/high-water：capacity、storage、scratch、child instance 等不可因 reset 伪造回落的历史高点；
- last-operation detail：只描述最近一次已完成 operation，失败时不得伪造已提交 changed/removed。

Candidate Scan 的 source/cardinality shortcut 可以减少 physical traversal，但 `scanned/matched/changed` 仍按公开 operation 的 logical reference semantics 发布；physical loop、comparison 和 allocation 进入 benchmark evidence，不混入业务统计。Caller-owned Scan plan/handle 不是 Table retained storage，不计入 TableStats；Table-owned `IndexBuffer`、sort/update scratch 仍进入 current/high-water accounting。

`resetStats()` 只重置明确允许重置的观测窗口；不能修改 table rows、capacity、epoch、ownership、plan、lifecycle 或 lifetime high-water。Snapshot 不返回 live mutable counter view。

Keyed Table 的主定位器必须分别报告 current retained structural bytes 与 lifetime
high-water bytes。rehash、staged replace、clear、stats reset 和 release 不能伪造
high-water 回落；release 可以把 current bytes 降为零。该数值只计算 SOMA-owned
locator arrays，不包含 authoritative columns 中可达的 String object bytes，也不
等同于 JVM observed heap。

## 7. 采集模式与副作用

Summary mode 只承担低干扰的核心计数；diagnostic mode 可以增加 probe、collision、memory 和 last-operation detail，但必须显式启用并单独 benchmark。任何模式都不得：

- 在 hot loop 拼接诊断字符串或构造 per-row event；
- 写 stdout/stderr、安装全局 logger、发起网络/文件 I/O；
- 暴露 absolute path、credential、raw handle/bucket/Index 或任意 payload `toString()`；
- 让统计失败改变 table operation 的成功语义。

## 8. Evidence 边界

Plan、Metadata phase 和 runtime observation 的长期语义由本 Design 拥有；当前
surface 由代码/public golden拥有；具体计数值和测量结果进入 Report。验证至少覆盖
deterministic plan hash、invalid plan、create/attach rollback、override precedence、
planning/maximum、parent/root/Invocation ledger、String profile status、Metadata/
Observation projection ownership、stats current/reset/high-water、summary/diagnostic
overhead和 release 后允许观察的边界。
