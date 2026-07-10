# Runtime lifecycle 契约

状态：正式设计文档
Owner：`soma-runtime-core`
事实范围：child ownership、materialization accounting、epoch/view/pipeline lifecycle、mutation coordination、concurrency 和 release
非事实范围：public API naming、storage data structure、schema declaration 和 benchmark claim
最后审查日期：2026-07-10

## 1. 目标

本文定义 TableStore ownership aggregate 在 create、access、mutation、materialization、clear 和 release 期间的合法状态与失败语义。

Storage material 由 [TableStore 契约](table-store-contract.md) 拥有；errors/stats 由 [Runtime errors 与 diagnostics 契约](runtime-errors-and-diagnostics-contract.md) 拥有；公共行为由根级 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 和 [Materialization 契约](../../docs/materialization-contract.md) 拥有；本文只拥有 runtime-core 的 lifecycle execution。

## 2. ChildTableHandle 与 ownership registry

Runtime core 必须为 `@SomaChild` field 提供 schema-agnostic child ownership material：

- `ChildTableHandle` 是 opaque instance locator，不是 row index、secondary index、business key 或 Java object reference contract；
- parent row/field slot 绑定至多一个 child instance；required child 可以处于 logical-present/unallocated-empty 状态，optional child 另有 explicit presence；
- child instance 有独立 `TableStore`、key/index/order 和 lifecycle state，但 lifecycle authority 属于 enclosing parent ownership aggregate；
- runtime ownership instance graph 必须是 forest，每个 child instance 只有一个 owner；
- public/generated API 不得导出 handle、attach existing child 或 reparent；
- handle registry 必须能检测 dangling handle、wrong-owner handle、released child 和 runtime ownership cycle，并进入 internal invariant violation path。

Schema type-level cycle 由 processor 拒绝；runtime cycle detection 只用于防御 corrupted/impossible state，不是动态 object-graph feature。

## 3. Recursive materialization tracker

Generated materializer 负责 schema-specific object/List/Map construction；runtime core 提供 schema-agnostic tracker：

- 只允许沿 registered ownership edge 进入 child instance；
- 记录 ownership/materialization path；
- 检查 dangling/wrong-owner/released/cycle；
- 维护 table-instance、row、present leaf、depth 和 estimated-allocation counters；
- 同一个 `fetchAll()` invocation 共享一组 counters；
- 任一层失败时丢弃 partial detached construction，不修改 Table/epoch。

Required logical-empty dense/keyed child materialize 为 empty `List`/`Map`；optional child absence materialize 为 `null`，由 presence state 决定，不能由 handle sentinel 猜测。普通 scalar/value key reference 不进入 recursive tracker。

### 3.1 MaterializationBudget

`MaterializationBudget` 至少包含 maximum ownership depth、table instances、rows、present leaf values 和 estimated allocation bytes。Root depth 为 `0`；所有 counter 使用 overflow-safe `long`/checked arithmetic。

Allocation estimate 使用 runtime-plan-versioned fixed accounting model，只估算本次新分配 schema object/List/Map/entry/wrapper；不读取实时 JVM heap，也不把共享 immutable payload object memory重复计入。Estimator version 与 default budget 进入 runtime plan identity，不进入 schema hash；per-call override 不改变 table runtime plan identity。

超限返回 `materialization_budget_exceeded`，包含 dimension、configured limit、current/proposed count or estimate、effective budget identity 和 path。实际 JVM allocation failure 使用独立 `allocation_failure`。V1 不实现 wall-clock materialization timeout；elapsed time 仅记录 diagnostics。

## 4. Epoch and lifecycle

Runtime table 至少维护：

- `storeEpoch`；
- active view count；
- released flag；
- optional memory estimate stats。

规则：

- structural mutation 成功后提升 `storeEpoch`；
- ColumnView acquire 记录 `viewEpoch`；
- `viewEpoch != storeEpoch` 映射为 stale view；
- structural mutation 遇到 active ColumnView 时返回 view_pinned，除非实现能证明 storage address/length/layout 不变；
- released view 和 stale view 是不同错误；
- destroy/clear 必须避免 use-after-release 语义。

### 4.1 Ownership aggregate lifecycle

Child operations 使用以下状态/级联规则：

- required child 始终 logical present；unallocated 与 present-empty 是同一业务语义，`clear()` 后仍 present；
- optional child 区分 absent、present-empty、present-data；`clear()` 不改变 presence，`unset` 才释放 subtree 并进入 absent；
- `ensure` 完成 absent -> present-empty；`replaceChild(batch)` 完成 absent/present -> staged new subtree；
- delete parent row、parent clear、optional unset 和 child replacement 递归 release 对应 old subtree；
- parent release 递归 release 整个 ownership aggregate；release idempotent；
- child clear 释放其 rows 拥有的 descendant subtree，但 child instance 自身保持 open/present。

Replacement 必须 stage and validate complete new subtree，成功后 atomically switch parent handle，再通过幂等、非分配 cleanup release old subtree。Expected construction/validation failure 保持 old handle/subtree unchanged；不得产生 orphan、dangling handle 或 partial replacement。

Delete/clear/unset/replacement 必须检查整个 affected subtree 的 active ColumnView/pinned borrow。任意 descendant pinned 时，在 visible state 改变前返回 `view_pinned`。Final aggregate `release()` 是 terminal lifecycle operation，可以把 existing child facade/view 统一置为 released；后续读取返回 released error，而不是继续访问 detached storage。

## 5. Mutation 分类

Mutation 分为：

| 类别 | 示例 | active view 存活时 |
|---|---|---|
| non-structural | 修改现有非 key fixed-width leaf，且不改变 storage length/layout | 可以允许 |
| structural | reserve、append batch、replaceAll、delete、row move、string relocation、child ensure/unset/replacement/cascade clear、index/order rebuild with exposed view risk | affected subtree pinned 时返回 view_pinned |

如果实现无法证明 mutation 不影响 ColumnView 所依赖的 storage，应按 structural mutation 处理。

Final `release()` 不等同于普通 structural mutation：它可以终止 aggregate 并级联 invalidate active views/facades；release 后所有访问必须稳定返回 released error。

## 6. Concurrency boundary

V1 runtime table/ownership aggregate 是 synchronous single-owner object。

不支持：

- concurrent read/read；
- concurrent read/write；
- concurrent write/write；
- internal lock strategy；
- transaction；
- actor/scheduler；
- parallel scan/sort。

V1 不提供 thread-handoff API。Application 如需跨线程顺序移交，必须在无 active terminal/mutation/materialization/Cursor/Pipeline/ColumnView 的 quiescent point 建立 happens-before，并保证只有新 owner thread 继续访问。Runtime 不协调、不加锁，也不提供共享访问承诺。

## 7. Serialization and persistence boundary

Runtime core 不提供 Table/ownership aggregate state serialization、persistence format、database mapping、backup/restore、replay 或 schema migration。`TableStore`、RowSlot、row index、`ChildTableHandle`、Cursor 和 ColumnView 都不可序列化。

Materialized Object 可以由上层映射成 protobuf/JSON/database DTO，但它本身不是稳定 wire/persistence format。Batch 是 detached construction/import boundary，不是反序列化协议。

## 8. Correctness 与 performance

Lifecycle 必须满足 [Runtime 正确性模型](../../docs/runtime-correctness-model.md) 的 state/invariant/all-or-nothing 要求。Pin check、cascade、cleanup 和 stats 不能把 hot path 退化为 per-row allocation；errors/stats 遵守 [Runtime errors 与 diagnostics 契约](runtime-errors-and-diagnostics-contract.md)，性能实现遵守 [Runtime 性能实现契约](runtime-performance-implementation-contract.md)。

## 9. 非目标

本文不提供 concurrent access、cross-table transaction、snapshot isolation、persistence、schema migration、external DTO synchronization 或 public attachment/reparent API。
