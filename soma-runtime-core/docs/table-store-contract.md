# TableStore 契约

状态：正式设计文档
Owner：`soma-runtime-core`
事实范围：TableStore composition、RowSpace、ColumnStore、presence、KeySpace、AccessStructures、AccessPath、Batch 和 storage-facing buffers
非事实范围：ownership/lifecycle/errors、public API、schema semantics 和性能参数
最后审查日期：2026-07-11

## 1. 目标

`soma-runtime-core` 提供 annotation-agnostic Java columnar storage kernel。本文拥有 runtime storage components 及其组合边界；public Table/API 由根级契约拥有，lifecycle 由 [Runtime lifecycle 契约](runtime-lifecycle-contract.md) 拥有，errors/stats 由 [Runtime errors 与 diagnostics 契约](runtime-errors-and-diagnostics-contract.md) 拥有，plan 由 [Runtime plan 契约](runtime-plan-contract.md) 拥有，hot-path discipline 由 [Runtime 性能实现契约](runtime-performance-implementation-contract.md) 拥有。

## 2. Runtime internal table model

Runtime core 使用 `TableStore` 组合模型承载 generated table 的 runtime internal storage。Public/generated API 仍只暴露 keyed table 和 dense table 两类 table kind；runtime internal 不使用 `Sparse Table` / `Unkeyed Sparse Table` 作为顶层抽象。

Generated source 与 runtime-core 的跨 package binding 位于 `com.hgtech.soma.runtime.generated`，分类为 generated-runtime protocol，不是 application API/SPI。它可以公开最窄的 typed RowSpace/column/presence/lifecycle primitive供 generated package绑定，但 generated facade public signature不得泄漏这些 type。`com.hgtech.soma.runtime.internal` 继续只承载 runtime artifact内部实现。

首个 protocol type set 固化为：`GeneratedMetadata`、`RuntimeCompatibility`（create-time identity validation）、`RuntimeFailures`（bounded structured error factory）、`KeyCanonicalization`（strict floating key validation/bit binding）、`GeneratedColumn` + `ColumnGroup`（group capacity staging）、`DenseTableState`（packed size/structural epoch/release/stats coordination）、`BooleanColumn`、`ByteColumn`、`ShortColumn`、`IntColumn`、`LongColumn`、`FloatColumn`、`DoubleColumn`、`PresenceBitmap`、`MaterializationTracker`、`SparseIntKeySpace`、`HashIntKeySpace` 和 `HashLongKeySpace`。Concrete primitive column/key space提供 typed lookup/update；generic staging只发生在 growth boundary，hot loop由 generated code持有 concrete type。首次实现的 exact public/protected protocol methods进入独立 manifest，此后不得删除、改变语义或在不提升 runtime compatibility identity时产生 incompatible signature change。

Exact current protocol matrix（Phase 1 + P2-A，全部位于 `com.hgtech.soma.runtime.generated`）：

```text
GeneratedMetadata(String schemaHash, String generatedTarget, String compilerIdentity,
  String generatedProtocol, String runtimeCompatibility, String planProtocol,
  String algorithm, String allocationEstimator)
GeneratedMetadata.schemaHash/generatedTarget/compilerIdentity/generatedProtocol/
  runtimeCompatibility/planProtocol/algorithm/allocationEstimator -> non-null String
RuntimeCompatibility.verify(GeneratedMetadata, RuntimePlan, String tableLogicalName) -> TablePlan

GeneratedColumn.stageCapacity(int) -> Object
GeneratedColumn.commitCapacity(Object) -> void
GeneratedColumn.clearRange(int fromInclusive, int toExclusive) -> void
ColumnGroup(int initialCapacity, GeneratedColumn... columns)
ColumnGroup.capacity() -> int
ColumnGroup.ensureCapacity(int required, int growthNumerator, int growthDenominator) -> boolean

PrimitiveColumn() / PresenceBitmap() public no-arg construction
PrimitiveColumn.get(int) -> exact primitive
PrimitiveColumn.set(int, exact primitive) -> void
PrimitiveColumn.copyFrom(same concrete type, int source, int target, int length) -> void
PresenceBitmap.isPresent(int)/setPresent(int)/clearPresent(int)
PresenceBitmap.copyFrom(PresenceBitmap, int source, int target, int length)
PresenceBitmap.presentCount() -> int

DenseTableState(String tableLogicalName, RuntimePlan, TablePlan, ColumnGroup)
DenseTableState.size/capacity -> int; structuralEpoch -> long; isReleased -> boolean
DenseTableState.runtimePlan -> RuntimePlan
DenseTableState.checkActive(String operation) -> void
DenseTableState.checkRowIndex(int rowIndex, String operation) -> int
DenseTableState.beginOperation(String operation) -> void
DenseTableState.endOperationSuccess(String operation, long scanned, long matched, long changed) -> void
DenseTableState.endOperationFailure(String operation, long scanned, long matched, String errorCode) -> void
DenseTableState.prepareAppend(int count) -> int startRow
DenseTableState.commitAppend(int expectedStartRow, int count) -> void
DenseTableState.prepareReplace(int newSize) -> int previousSize
DenseTableState.commitReplace(int expectedPreviousSize, int newSize) -> void
DenseTableState.prepareClear() -> int previousSize
DenseTableState.prepareRelease() -> int previousSize
DenseTableState.commitClear(int expectedPreviousSize) -> void
DenseTableState.commitRelease(int expectedPreviousSize) -> void
DenseTableState.updateScratch(long currentBytes, long highWaterBytes) -> void
DenseTableState.updateResult(long scanned, long matched, long changed,
  long sidecarMaintained, long sidecarRebuilt) -> UpdateResult
DenseTableState.statsSnapshot() -> TableStats; resetStats() -> void

SparseIntKeySpace(int maximumKey); size()/contains(int)/rowOf(int)
SparseIntKeySpace.put(int key, int rowSlot)/removeAt(int rowSlot)/clear() -> void
HashIntKeySpace(int expectedSize); size()/contains(int)/rowOf(int)
HashIntKeySpace.put(int key, int rowSlot)/remove(int key)/updateRow(int key, int rowSlot)/clear() -> void
HashLongKeySpace(int expectedSize); size()/contains(long)/rowOf(long)
HashLongKeySpace.put(long key, int rowSlot)/remove(long key)/updateRow(long key, int rowSlot)/clear() -> void
KeyCanonicalization.strictFloatKeyBits(String table, String field, float value, String operation) -> int
KeyCanonicalization.strictDoubleKeyBits(String table, String field, double value, String operation) -> long
KeyCanonicalization.strictFloatStorage/strictDoubleStorage(...) -> canonical float/double

MaterializationTracker(MaterializationBudget, String rootPath)
MaterializationTracker.addTableInstances/addRows/addLeafValues/addEstimatedBytes(long) -> void
MaterializationTracker.budgetIdentity() -> String
MaterializationTracker.estimatedBytes/rows/leafValues/tableInstances -> long
```

`HashIntKeySpace` / `HashLongKeySpace` 的 `remove` 只写 tombstone，不在 remove/packed compaction 内触发 rehash/allocation；generated keyed delete 先移除 deleted key，再在同一 structural commit 前逐 survivor 调用 `updateRow` 修复移动后的 slot。rehash 只能发生在后续 insert/growth boundary，不能留下对已提交 row 的 stale locator。

`boolean`、`byte`、`short`、`int` 和 `float` key 静态绑定 `HashIntKeySpace`；`long`、`double` key 静态绑定 `HashLongKeySpace`。floating key 在 Batch/import、lookup和compaction repair均先经 `KeyCanonicalization` 拒绝 non-finite、把 `-0.0` canonicalize为 `+0.0` 并使用 canonical bits；不在 hot lookup 创建 boxed key、tuple或metadata interpreter。

`GeneratedColumn` 的 `Object` 只承载 staged primitive array并由 `ColumnGroup` 在 growth boundary内部回传给同一 concrete column；generated source/hot loop不读取或 cast该 Object。`ensureCapacity` 返回是否实际增长。Prepare方法完成active/reentrant/range/overflow/capacity preflight但不改变size/epoch；generated typed copy/clear成功后调用匹配的commit。Mismatch进入internal invariant failure。新增protocol方法可以additive，现有方法不能靠 generated code migration重命名。

```text
XxxTable
  -> XxxTableStore
       -> TableLayout
       -> RowSpace
            -> KeySpace        // keyed table only
       -> ColumnStore
       -> AccessStructures
       -> AccessPath
       -> MutationCoordinator
       -> LifecycleState
```

核心职责：

| 组件 | 职责 |
|---|---|
| `TableStore` | 一张 generated table 的 runtime internal aggregate owner |
| `TableLayout` | schema hash、field layout、column binding、selector metadata |
| `RowSpace` | row membership、`RowSlot` 分配、packed slot 有效性规则 |
| `KeySpace` | keyed table 才有的 `RowKey -> RowSlot` 身份定位结构 |
| `ColumnStore` | primitive/object columns、presence bitmap、capacity 和 slot-level payload |
| `AccessStructures` | secondary index、unique index、order sidecar 等被维护的访问结构 |
| `AccessPath` | default scan、index source、order source 等 Row Pipeline source 的内部执行入口 |
| `MutationCoordinator` | batch、replaceAll、delete、row move、sidecar dirty/rebuild、epoch 协调 |
| `LifecycleState` | epoch、active view、released、stats、typed lifecycle errors |

Public keyed table 映射为 `TableStore + RowSpace + KeySpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`。

Public dense table 映射为 `TableStore + RowSpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`。Dense table 没有 `KeySpace`，但仍保留 ColumnView、Row Pipeline、Materialized Object、secondary index/order、lifecycle 和 typed errors。

## 3. ColumnStore

每个 generated table instance 使用 packed row storage：

- 每个 leaf field 对应一组同长 column；
- scalar、semantic scalar 和 enum 使用 primitive column；
- value field 按 leaf expansion 展开为 columns；
- optional field 使用 presence bitmap 加 payload column 或 handle column；
- string V1 baseline 使用 `String[]` payload column，后续可引入 string pool；
- `@SomaChild List`/`Map` field 使用 `ChildTableHandle` locator column，不存储 Java Collection 或 public/live object reference；
- `RowSlot` 是当前 packed storage 内的位置，不是 stable business identity。Public dense table direct API 中的 row index 映射到当前 `RowSlot`。

每个公开可用的 stable table state 中，live `RowSlot` 必须形成 `[0, size)` packed range；terminal 内 temporary removal marks 可以存在，但成功返回后不得留下长期 tombstone/hole。Single/batch delete 的具体 swap-remove/compact algorithm 属于 implementation，结果必须恢复 packed invariant，并同步维护或 dirty 所有 locator/sidecar。

V1 runtime core 至少提供：

- `IntColumn`；
- `LongColumn`；
- `DoubleColumn`；
- `FloatColumn`；
- `ByteColumn`；
- `ObjectColumn<T>`；
- capacity reservation；
- append batch；
- swap-remove / row move helper；
- clear while reusing capacity。

Column implementation 是 internal API，generated public API 不暴露 column mutation primitive。

Capacity growth 使用 group staging：所有 leaf columns、presence words与 RowSpace所需新 arrays先成功分配/复制，再一次 publish新 capacity。任一 validation、controlled allocation failure或 raw OOME发生在 publish 前时，旧 arrays、size、capacity与epoch保持一致；不能逐 column直接替换后再尝试补救。Generated binding在 terminal/bulk boundary解析 concrete typed columns，hot loop不使用 generic `getField(columnId)`/boxed dispatch。

### 3.1 Child handle storage material

`@SomaChild List/Map` field 在 parent column 中保存 opaque `ChildTableHandle` locator，不保存 Java Collection 或 live public object。Handle 的 owner validation、forest invariant、cascade 和 release 属于 [Runtime lifecycle 契约](runtime-lifecycle-contract.md)。

## 4. Public table kind mapping

Runtime core 必须支持 public/generated API 的两类 table：keyed table 和 dense table。二者共享 `TableStore` 组合模型，区别在于是否存在 `KeySpace`。

Keyed table：

- 有 stable logical key；
- 必须维护 `KeySpace`，用于 `RowKey -> RowSlot`；
- 支持 duplicate key detection、`fetch(key)`、`containsKey(key)`、`mutate(key)` 和 `delete(key)`；
- 适合 entity state、lookup table 和唯一性约束。

Dense table：

- 没有 stable logical key；
- 以 packed row storage、row-index iteration、ColumnView 和 ordered access 为主要访问方式；
- 适合矩阵/数组型 runtime state、packed scan 和 solver workspace；
- 可以是长生命周期 table，也可以通过 `replaceAll(batch)` 在同一 table instance 内反复刷新；
- row index 只对当前 table state 有效，structural mutation 后不得作为 stable identity 使用。

Runtime core 不把短生命周期 Java 临时对象作为优化目标。dense workspace 的价值在于复用 column capacity、批量刷新和 sidecar access，而不是替代普通局部对象。

## 5. Optional bitmap

Optional presence 使用 `long[]` word bitmap：

```text
row_index -> words[row_index / 64] bit (row_index % 64)
```

规则：

- bit `1` 表示 present；
- bit `0` 表示 absent；
- word count 覆盖 table size；
- 最后一个 word 的 unused bits 被忽略；
- payload column length 与 table row length 对齐；
- bitmap 不进入 materialized shape；
- runtime 维护 `presentCount` 或等价 metadata；
- generated predicate 支持 all-present、all-absent、mixed chunk scan。

## 6. KeySpace and primary key lookup

Keyed table 必须有 `KeySpace`。Primary key lookup 属于 table identity / row 定位，不作为普通 secondary index sidecar 处理。

查找语义：

```text
RowKey leaf values -> KeySpace -> RowSlot
```

V1 至少支持以下 `KeySpace` 实现材料：

- `SparseIntKeySpace`：bounded int id，使用 sparse-set-style `dense[] + sparse[]`；
- `HashKeySpace`：int / long / enum ordinal / generated composite key，使用 hash-based key lookup。

`HashKeySpace` 至少支持：

- int key -> `RowSlot`；
- long key -> `RowSlot`；
- enum key -> exact enum direct API、static cached member array 和 packed `int ordinal -> RowSlot`；
- generated composite key -> `RowSlot`；
- open addressing；
- not-found sentinel；
- duplicate key detection；
- remove / row move update；
- rehash；
- collision full equality。

Hash value、bucket layout 和 probing strategy 是 internal implementation detail，不进入 generated public API、Materialized Object 或 schema hash。

### 6.1 Floating identity/access canonicalization

Runtime core 必须提供 generated binding 可复用的 floating validation/canonicalization primitive：

- ordinary payload column 接受 Java `NaN`、positive/negative infinity 和 negative zero；
- key/index/unique/order floating leaf 写入或查询前必须 finite；
- strict leaf negative zero canonicalize 为 positive zero；
- key equality/hash、secondary matching、unique detection 和 order comparator 使用相同 canonical value；
- invalid value 在 visible mutation 前返回 typed invalid-value error，并携带 field/selector/materialization path；
- ordinary payload 不承诺保留不同 NaN payload bit pattern。

Runtime 不根据 column type 自行猜测 strict role；generated adapter 从 normalized schema model 传入明确 role/binding。

## 7. SparseIntKeySpace / sparse set material

V1 提供 self-owned sparse-set-style material，用于 `SparseIntKeySpace` 或 bounded int id membership 场景：

```text
dense[]
sparse[]
size
contains(id) = sparse[id] < size && dense[sparse[id]] == id
```

该结构负责：

- add；
- remove；
- contains；
- dense iteration；
- clear reuse capacity；
- capacity growth；
- deterministic iteration order as stored in dense array。

Sparse Set 是 runtime internal implementation material，不是 table 本体，不作为 public generated collection 暴露。

## 8. AccessStructures: secondary index / unique index

V1 `AccessStructures` 至少承载：

- secondary non-unique index；
- secondary unique index；
- order sidecar。

Primary key lookup 由 `KeySpace` 承载，不列为普通 secondary index。

规则：

- index selector 由 processor 归一化；
- generated code 负责将 selector leaf values 写入 runtime index；
- unique index duplicate 必须返回可区分错误；
- non-unique index 可以使用 row list、row chain 或 rebuildable sidecar；
- row move 后必须同步维护或标记 dirty。

V1 可先实现 primary key 和 order，secondary index/unique 按 gate 优先级逐步补齐，但正式 release claim 只能引用已验证能力。

## 9. AccessStructures: order sidecar

`order` 是 table-level ordered access contract，不改变 packed storage physical row order。

Runtime sidecar：

```text
orderedRows = int[] row permutation
orderDirty = boolean
```

规则：

- insert/delete/replaceAll/clear/row move 后 order sidecar 必须保持正确或标记 dirty；
- 修改参与 order 的字段后标记 dirty 或 eager update；
- terminal operation 前 lazy rebuild；
- generated comparator 基于 normalized selector 和 direction；
- order sidecar 不进入 Materialized Object、ColumnView 或 public API；
- ordered access 返回 key buffer、row index buffer 或 materialized schema object/list。

## 10. AccessPath

`AccessPath` 是 Row Pipeline source 的内部执行入口。它只决定 terminal 开始时的初始 `RowSequence`，不改变 table storage 本体。

V1 至少需要：

- default scan path：遍历当前 packed rows；
- index path：从 maintained secondary index / unique index 产生候选 rows；
- order path：从 maintained order sidecar 产生 ordered rows；
- dynamic sorted path：基于本次 pipeline comparator 生成临时 row permutation。

`AccessPath` 不进入 public API。Generated `findByXxx(...)`、`byXxx(...)` 和默认 table source 是 public/generated API 表达；runtime internal 可映射到对应 `AccessPath`。

## 11. Batch boundary

`reserve`、`addBatch` 和 `replaceAll` 是 V1 性能边界。

规则：

- loader 应先估算 capacity，再 reserve；
- addBatch 按 batch size 扩容和写入；
- replaceAll 尽量复用 capacity，是 dense table 刷新矩阵行、packed data 和 solver workspace 的主要边界；
- index 和 order sidecar 在 batch boundary 统一更新或标记 dirty；
- per-row append 不是默认 import 路径；
- deterministic memory limit 和可控 allocator/provider failure 必须映射为可区分错误；raw `OutOfMemoryError` 原样传播，capacity/column staging 保证 publish 前旧 stable state 仍满足 invariant。

## 12. Materialization / buffer / ColumnView

Runtime 必须区分：

| 类型 | 持有 live storage | mutation 后语义 |
|---|---|---|
| Pipeline plan | 否 | terminal operation 基于执行时 table 状态 |
| Materialized schema object / `List` / `Map` | 否 | detached complete copy，不反映后续 mutation |
| KeyBuffer | 否 | stable materialized key values |
| RowIndexBuffer | 否 | 只对生成时 table epoch 有效 |
| ColumnView | 是 | live readonly view，structural mutation 返回 view_pinned |
| ChildTableHandle | 是，internal | 绑定 ownership/lifecycle，不进入 public result |

### 12.1 Materialization support

Generated materializer 读取 storage-facing row/column/child traversal primitive；runtime 不构造 schema-specific object shape。Recursive counters、path、budget 和 all-or-nothing lifecycle 由 [Runtime lifecycle 契约](runtime-lifecycle-contract.md) 与根级 [Materialization 契约](../../docs/materialization-contract.md) 共同约束。

### 12.2 ColumnView boundary

ColumnView 必须强持有 owner table 或 storage owner。close/release 后继续读取必须返回 released_view 类错误。ColumnView 不进入 Materialized Object；detached object graph 可以超过 Table/ownership aggregate 生命周期存在。

## 13. Storage invariants

Stable public state 必须满足根级 [Runtime 正确性模型](../../docs/runtime-correctness-model.md)：

- live rows packed in `[0,size)`；
- columns/presence/key mapping aligned；
- key/index/order locator 在 row move 后 current 或明确 dirty；
- dead reference 不保持无意义 GC reachability；
- runtime internal handle/buffer 不进入 public result。

## 14. 非目标

本文不规定 public generated method、ownership state machine、concurrency、error payload、growth/hash/index concrete algorithm、serialization 或 persistence。
