# Ownership 与 lifecycle 设计

类型：Design

状态：正式

Owner：SOMA ownership 与 lifecycle semantics

设计层次：`D2` 能力设计

主要关注点：SomaGroup、Ownership aggregate、borrow/currentness 与资源生命周期

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：Group composition、root/parent/child ownership、aggregate access、
epoch/currentness、view、operation、version、fault 和 release

非事实范围：child schema syntax、storage layout、公开 IndexSnapshot 消费契约、错误文案和 application transaction

最后审查日期：2026-07-28

## 1. Ownership model

Root table 及其递归 owned children 构成一个 ownership aggregate。每个 child table instance：

- 由一个 parent row 的一个 child field 独占；
- 只有一个 owner，不可 share 或 reparent；
- 不能形成 cycle；
- parent row 删除/替换/释放时按 contract cascade；
- 不允许通过 child facade 独立 release owned child；
- optional absent 不创建 child instance，required logical-empty child 不必 eager allocate backing storage。

`List<R>` child 映射为 parent-owned dense table；`Map<K,R>` child 映射为 parent-owned keyed table。Keyed/dense 语义与 ownership 语义彼此独立。

### 1.1 SomaGroup composition

`SomaGroup` 可选地把一个或多个 root ownership aggregate 组合到 stable
composition/resource/release boundary：

- frozen `SomaGroupPlan` 可以声明多个 schema，并用不同 stable member slot 保存
  同一 root descriptor 的多个 instance；
- 每个 root 仍拥有独立 ChildOwnershipRegistry、aggregate identity 与 fault state；
- root attach 是 parent resource admission、private construct、publish membership/
  membershipEpoch 的原子 transaction；不支持 detach/reparent 或动态新增 slot；
- Group runtime 有 opaque `groupInstanceId`；application-supplied
  `logicalGroupId` 才是未来 restore-compatible logical identity；
- Group 不提供 snapshot isolation、跨 root atomic mutation 或业务一致性。

Group state 为 `ACTIVE/DEGRADED/FAULTED/RELEASED`。单个 member fault 使 Group
`DEGRADED`，不污染其他 root 的 trust state；需要 faulted root 的 operation失败。
Group自身 publication/release invariant 失败才使 Group `FAULTED`。

## 2. Handle 与可达性

Parent live column 只保存 opaque child handle/token，不保存 `List`、`Map` 或 public child object。Registry 负责 owner validation、generation、resolve、cascade 和 retained resource accounting。同一个 Registry 也是 aggregate trust state 的唯一 Owner；root 与所有递归 child 共享该状态，避免 table-local fault 产生半可信 ownership forest。

Raw handle、owner token 和 RowSlot 不进入 public error context、DTO 或 generated public signature。Dangling、wrong-owner 和 cycle 是 invariant failure，不得被当作普通 empty child。

## 3. Access model

一个 ownership aggregate 只允许单 owner、同步、非并发访问。SOMA 不做内部锁共享，也不承诺跨线程可见性。Application 若需要线程切换，必须在没有 active operation/view 的明确边界转移整个 aggregate 的独占所有权。

同一 aggregate 内同时只允许一个 active operation/materialization boundary。Callback、comparator、allocator provider 和 materializer 不得重入 table/child aggregate。

DataFlow multi-source Invocation 只在 application 已独占全部 source aggregate 时按
opaque aggregate instance identity 取得同步 guard。同一 aggregate 的多个 logical
alias 去重；partial acquire 反向释放。Source 可以来自同 Group、跨 Group、implicit
Group、跨 schema 或同类型不同实例；Group 不是 guard prerequisite/Owner。
Parallel worker 共享一次 Invocation 的独占权，不因此获得并发调用 Table API 的
能力。

## 4. Structural epoch

Structural change 成功后递增 table structural epoch。以下 live borrow 或 operation state 按各自契约强制校验 identity/epoch：

- Cursor、UpdateCursor、one-shot Candidate Scan 与 Traversal；
- ColumnView；
- child facade/handle generation；
- materialization traversal state。
- DataFlow Invocation、borrowed result 和 generated binding guard。

`IndexSnapshot` 采用 caller-responsibility，不是强制 live borrow；完整公开消费契约由 [Schema 与生成 API](schema-and-generated-api.md)拥有。本 Owner 只定义 currentness 机制：snapshot 记录 source 与 captured structural epoch；可选 `requireCurrent` 检查 owner、active lifecycle、structural epoch 和 range，但不能检测非结构 mutation。其余强制 borrow/lifecycle stale access 必须返回 typed failure。Epoch 递增必须 overflow-safe；无法继续表示时 fail closed。

## 5. View 与 mutation

ColumnView 是 scoped typed live view，适合连续 primitive access，但不授予 backing array 所有权。Active view pin 期间：

- 可能使 backing storage 迁移、row relocation 或 release 的结构变更必须拒绝；
- view 只能执行其 API 明确允许的读写；
- view release 后再次访问失败；
- stats/diagnostics 必须能够观察 active view 和 blocked lifecycle 状态。

View 约束由 operation 是否会破坏其 binding 决定，不能用“当前恰好没有扩容”绕过 contract。

## 6. Clear、replace 与 release

- `clear` 删除所有 live facts并递归处理 children，但可以保留准入的 reusable capacity；
- `replaceAll` 先完整 stage/validate 新 aggregate delta，再原子 publish；
- implicit Group 中 root `release` 递归释放 aggregate 并关闭 Group；
- explicit Group 中单独 `table.release()` 返回 ownership conflict，只有
  `group.release()` 可以释放 members；
- explicit Group release 先 preflight 全部 root 没有 active operation/view/
  callback，再按 reverse attachment order 释放；正常 conflict 在任何 root
  release 前失败；
- release 后 data access 一律失败，只允许 contract 明确保留的 diagnostics，例如 runtime plan、released state 和 stats snapshot；
- release/cascade 失败不能留下外部可访问的半释放 forest。

Internal invariant 或无法证明旧 stable state 的 unexpected failure 使整个
aggregate 单向进入 faulted。此后 normal access fail closed，只允许 bounded
diagnostics 与 root cleanup attempt；owned child 不能绕过 root 独立恢复或释放。
Faulted 与 released 是不同状态：fault 表示事实可信度已无法证明，release 是资源
lifecycle 的 terminal transition。

## 7. Application 边界

Ownership aggregate 不是跨 table transaction。两个独立 roots 的变更顺序、snapshot、compensation、rebuild 和 failure recovery 均由 application 设计。

Multi-source DataFlow 只提供一次同步 read boundary；Joined/Grouped/Windowed result 默认 detached/read-only。跨 root mutation 仍由 application 以多个 single-aggregate safe point 明确排序。

Group 与每个 Table 各自拥有 optional String `dataVersion` application marker，只能在
safe point set/clear；SOMA 不解释、比较、传播或要求单调，也不因此修改 structural
epoch。Group `membershipEpoch`、Table `structuralEpoch`、schema/plan identity 与
dataVersion 是四种不同 currentness/compatibility facts。
