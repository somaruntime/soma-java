# Runtime 正确性模型

状态：正式设计文档
Owner：根项目协调层
事实范围：runtime 跨组件不变量、状态机、失败原子性、correctness oracle 和 gate 映射
非事实范围：runtime class/API、具体数据结构算法、场景业务规则和性能结论
最后审查日期：2026-07-10

## 1. 目标

本文把 [SomaTable 设计宪法](soma-table-design-constitution.md) 转换为可测试的 runtime correctness model。它定义实现返回控制权时必须成立的事实，不规定内部类名或算法。

组件职责由 [TableStore 契约](../soma-runtime-core/docs/table-store-contract.md) 和 [runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md) 落地。

## 2. Correctness boundary

正确性分为三层：

| 层级 | 一致性单元 | Owner |
|---|---|---|
| Table-local | 单个 TableStore 的 rows、columns、keys、sidecars、epoch | runtime-core |
| Ownership aggregate | root table 与全部 owned child subtree | runtime-core |
| Cross-root business consistency | 多个独立 root tables 的 operation sequence | application/solver |

SOMA V1 不把跨 root table transaction 纳入 runtime correctness。Application 必须决定 mutation ordering、validation、compensation 和 failure handling。

## 3. Core invariants

### 3.1 RowSpace

稳定状态下：

- `0 <= size <= capacity`；
- live rows 恰好占据 `[0, size)`；
- 不保留 persistent tombstone；
- 每个 column 的 addressable capacity 与 RowSpace 对齐；
- swap-remove/batch compact 后 moved row 的所有 columns、keys、sidecars 和 child handles 同步修复；
- structural mutation 提升对应 epoch；
- released store 不再拥有 live rows。

### 3.2 KeySpace

Keyed table 必须满足：

- 每个 live logical key 恰好定位一个 live row；
- 每个 live row 恰好有一个 key；
- duplicate insert 在 visible mutation 前失败；
- missing lookup 不返回 stale slot；
- remove/compaction 后 key-to-slot 映射正确；
- key equality/hash 与 normalized key semantics 一致。

Dense table 没有 KeySpace；row index 不得进入 logical key contract。

### 3.3 Floating identity/access

参与 key/index/unique/order 的 floating leaf：

- 必须 finite；
- 写入、lookup 和 selector boundary canonicalize `-0.0` 为 `+0.0`；
- equality、hash、matching 和 order 使用同一 canonical value；
- invalid value 在 visible state 改变前失败。

普通 payload 的 NaN/infinity/negative-zero 语义由 annotation contract 定义，不能用 NaN 表达 absence。

### 3.4 ColumnStore 与 presence

- 每个 live row 的 required field 可读且类型正确；
- optional presence bitmap 是 absence 的唯一事实；
- absent primitive payload 不可经 public value getter 伪装为 zero；
- present count 与 bitmap 一致；
- clear/remove/release 后 dead reference 不继续保持 GC reachability；
- child handle column 只保存有效 owned handle 或合法 absent/empty state。

### 3.5 AccessStructures

Index、unique、order 和 stats 是派生结构：

- 状态必须是 clean/current 或明确 dirty；
- read path 只能使用与当前 base facts 一致的结构；
- dirty structure 在使用前正确 rebuild；
- unique selector 对 live facts 保持唯一；
- rebuild failure 不把部分结构发布为 current；
- sidecar 不能成为业务事实源。

### 3.6 AccessPath 与 terminal

- AccessPath 只产生当前 terminal 的 candidate sequence；
- candidate row 必须属于 terminal 开始时的有效 source domain；
- filter/skip/limit/sorted/short-circuit 顺序符合 API；
- mutation terminal 不因自身 field update 重新进入 source/filter/sort；
- dynamic permutation 绑定对应 epoch；
- callback 不获得 internal row pointer/sidecar handle。

### 3.7 Ownership aggregate

- 每个 child instance 恰好一个 owning parent row/field slot；
- no sharing、no reparent、no orphan、no dangling handle；
- runtime ownership graph 无环；
- required child 始终 logical present；
- optional absent、present-empty、present-nonempty 可区分；
- delete/clear/unset/replacement/release 按契约 cascade；
- replacement 失败时旧 subtree 保持不变。

### 3.8 Materialization

- 只沿 ownership edge 递归；
- returned graph 完全 detached；
- required/optional collection shape 正确；
- budget 覆盖整个 invocation；
- counters 和 path deterministic；
- 任一失败不返回 partial result、不修改 Table。

详细语义由 [Materialization 契约](materialization-contract.md) 拥有。

## 4. 状态机

### 4.1 Table lifecycle

```text
CREATING
  -> ACTIVE
  -> RELEASED

CREATING failure -> no published Table
RELEASED -> no transition back
```

只有 ACTIVE 接受正常访问。Release 对 ownership aggregate 级联并使已有 borrowed access 进入 released/stale error path。

### 4.2 Public mutation

```text
PRECHECK
  -> PREPARE
  -> APPLY_BASE_FACTS
  -> MAINTAIN_OR_DIRTY_DERIVED
  -> COMMIT_EPOCH
  -> SUCCESS
```

Expected error 应在 APPLY 前发生。Prepare 后的 allocation/validation failure 不改变 visible facts。Internal failure 若无法恢复到合法状态，必须作为 invariant violation 暴露，不能伪装为普通 invalid input。

### 4.3 Row Pipeline

```text
NEW -> EXECUTING -> CONSUMED
              \-> FAILED_CONSUMED
```

Pipeline one-shot。Cursor 只在 callback active window 有效。Nested same-table structural mutation、reentrant terminal 和 cross-thread use 非法。

### 4.4 ColumnView

```text
ACQUIRED -> ACTIVE -> CLOSED
                  \-> INVALIDATED_BY_RELEASE
```

Active view 阻止冲突 structural mutation；mutation 必须在修改前返回 `view_pinned`。Closed/released view 后续读取返回 typed error。

### 4.5 Sidecar

```text
CLEAN_CURRENT
  -> DIRTY
  -> REBUILDING
  -> CLEAN_CURRENT

REBUILDING failure -> DIRTY
```

只有完整 rebuild 成功后才发布新结构。

### 4.6 Child slot

Required child：

```text
LOGICAL_EMPTY_UNALLOCATED <-> PRESENT_ALLOCATED
clear -> logical empty
parent release -> released
```

Optional child：

```text
ABSENT -> PRESENT_EMPTY/PRESENT_NONEMPTY
clear  -> PRESENT_EMPTY
unset  -> ABSENT + cascade release
replace -> atomic handle switch
```

## 5. 失败原子性

### 5.1 Table-local

Insert、batch、update、remove、replaceAll 和 sidecar rebuild 必须在返回时满足：

- success：base facts、derived structures/dirty state 和 epoch 一致；
- expected failure：调用前 visible facts 保持不变；
- release：整个 owner scope 进入 terminal state；
- invariant violation：明确报告，不能继续假装合法。

### 5.2 Ownership aggregate

Child replacement/import 应先完整构造和校验新 subtree，成功后切换 handle 并释放旧 subtree。任何 descendant failure 都不能产生 orphan、shared child 或 partial switch。

### 5.3 Cross-root

SOMA 不承诺多表 all-or-nothing。Application 可以采用 validate-first、ordered commit、idempotent retry、compensation 或重建，但这些是 solver/application contract。

## 6. Error classification

Correctness 至少区分：

- schema/processor diagnostic；
- invalid input/value；
- duplicate/missing key；
- absent optional；
- invalid row index；
- stale/released/view pinned；
- pipeline consumed/reentrant；
- materialization budget/path；
- schema/runtime compatibility；
- allocation/resource；
- internal invariant violation。

具体 runtime error code、category、context 和 stats/diagnostic boundary 由 [runtime errors 与 diagnostics 契约](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) 拥有；lifecycle state transition 仍由 [runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md) 拥有。

## 7. Differential oracle

Runtime 实现必须有一个非 columnar reference oracle，按同一 logical semantics 建模：

- keyed/dense rows；
- optional presence；
- key/index/unique/order expected result；
- mutation/compaction；
- child ownership state；
- materialized object graph；
- lifecycle/errors。

Oracle 只用于测试，不进入 production dependency。

对相同 operation trace，比较：

- logical rows/fields/presence；
- key/index/order query result；
- update/remove result；
- child ownership graph；
- error category/path；
- materialized graph。

## 8. Invariant evidence

Runtime invariant helper 分两层：

| Helper | 检查 |
|---|---|
| table-local | packed rows、column length、bitmap、key mapping、sidecar、epoch |
| aggregate | child owner、cycle/orphan/dangling、cascade、replacement、pin/release |

Testkit 还必须提供 schema-aware materialization comparator。具体 helper contract 由 [soma-testkit 契约](../soma-testkit/docs/testkit-contract.md) 拥有。

Scenario-specific oracle，例如 FJSP dispatch/frontier，进入对应 [examples scenario](../soma-examples/docs/README.md)，不进入通用 runtime model。

## 9. Gate mapping

| Evidence | Gate |
|---|---|
| schema compile/diagnostic/hash | G1 |
| generated API/golden/package | G2 |
| table-local/aggregate differential and lifecycle | G3 |
| assembled package smoke | G4 |
| examples scenario oracle and benchmark smoke | G5 |
| complete evidence review | G6 |

Gate 的正式状态和报告路径由 [V1 验证门禁](validation-gates.md) 拥有。

## 10. 非目标

本文不提供 formal proof、linearizability、concurrent consistency、cross-table transaction、persistence recovery、业务 feasibility 或性能结论。
