# Ownership 与 lifecycle 设计

类型：Design

状态：正式

Owner：SOMA ownership 与 lifecycle semantics

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：root/parent/child ownership、aggregate access、epoch、view、operation 和 release

非事实范围：child schema syntax、storage layout、错误文案和 application transaction

最后审查日期：2026-07-20

## 1. Ownership model

Root table 及其递归 owned children 构成一个 ownership aggregate。每个 child table instance：

- 由一个 parent row 的一个 child field 独占；
- 只有一个 owner，不可 share 或 reparent；
- 不能形成 cycle；
- parent row 删除/替换/释放时按 contract cascade；
- 不允许通过 child facade 独立 release owned child；
- optional absent 不创建 child instance，required logical-empty child 不必 eager allocate backing storage。

`List<R>` child 映射为 parent-owned dense table；`Map<K,R>` child 映射为 parent-owned keyed table。Keyed/dense 语义与 ownership 语义彼此独立。

## 2. Handle 与可达性

Parent live column 只保存 opaque child handle/token，不保存 `List`、`Map` 或 public child object。Registry 负责 owner validation、generation、resolve、cascade 和 retained resource accounting。

Raw handle、owner token 和 RowSlot 不进入 public error context、DTO 或 generated public signature。Dangling、wrong-owner 和 cycle 是 invariant failure，不得被当作普通 empty child。

## 3. Access model

一个 ownership aggregate 只允许单 owner、同步、非并发访问。SOMA 不做内部锁共享，也不承诺跨线程可见性。Application 若需要线程切换，必须在没有 active operation/view 的明确边界转移整个 aggregate 的独占所有权。

同一 aggregate 内同时只允许一个 active operation/materialization boundary。Callback、comparator、allocator provider 和 materializer 不得重入 table/child aggregate。

## 4. Structural epoch

Structural change 成功后递增 table structural epoch。Cursor、mutator、pipeline和ColumnView按各自契约强制校验identity/epoch；IndexSnapshot只记录来源与captured epoch，采用caller-responsibility：

- cursor、mutator 和 one-shot pipeline；
- ColumnView；
- child facade/handle generation；
- materialization traversal state。

Caller只在一个同步只读批次内立即消费IndexSnapshot，并在任意来源mutation/lifecycle变化后视为失效。可选`requireCurrent`只检查owner、active lifecycle、structural epoch和range；它不能检测非结构mutation。其余强制borrow/lifecycle stale access必须返回typed failure。Epoch递增必须overflow-safe；无法继续表示时fail closed。

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
- root `release` 递归释放 ownership aggregate 并进入 terminal state；
- release 后 data access 一律失败，只允许 contract 明确保留的 diagnostics，例如 runtime plan、released state 和 stats snapshot；
- release/cascade 失败不能留下外部可访问的半释放 forest。

## 7. Application 边界

Ownership aggregate 不是跨 table transaction。两个独立 roots 的变更顺序、snapshot、compensation、rebuild 和 failure recovery 均由 application 设计。
