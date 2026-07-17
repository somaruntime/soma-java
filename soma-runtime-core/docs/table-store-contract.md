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

当前v3 protocol type set固化为：`GeneratedMetadata`、`RuntimeCompatibility`（create-time identity validation与hash primary-locator estimator）、`RuntimeFailures`、`KeyCanonicalization`、`GeneratedColumn` + `ColumnGroup`、`DenseTableState`、primitive/object columns、`PresenceBitmap`、`MaterializationTracker`、`MaterializationAllocation`、`ChildOwnershipRegistry`、`OwnedChildTable`、`IndexBuffer`、`GroupedExactIndex`、`IntKeySpace`、`HashIntKeySpace`、`HashLongKeySpace` 和 `HashCompositeKeySpace`。V3删除`SparseIntKeySpace`与`RowPermutationSidecar`。`MaterializationAllocation`是materialization boundary的scoped controlled allocation admission protocol，不进入row/storage hot path。`StorageBudget`、column retained-byte accounting和cascade scratch counters是runtime package-private实现，不是generated/public protocol。Concrete column/locator/index提供typed lookup/update；generic staging只发生在growth/bulk boundary，hot loop由generated code持有concrete/static protocol。

Exact current protocol matrix（Phase 1 + Phase 2 + Phase 3 access structures，全部位于 `com.hgtech.soma.runtime.generated`）：

```text
GeneratedMetadata(String schemaHash, String generatedTarget, String compilerIdentity,
  String generatedProtocol, String runtimeCompatibility, String planProtocol,
  String algorithm, String allocationEstimator)
GeneratedMetadata.schemaHash/generatedTarget/compilerIdentity/generatedProtocol/
  runtimeCompatibility/planProtocol/algorithm/allocationEstimator -> non-null String
RuntimeCompatibility.verify(GeneratedMetadata, RuntimePlan, String tableLogicalName) -> TablePlan
RuntimeCompatibility.verifyAccess(TablePlan, boolean hasSelectors) -> TablePlan
RuntimeCompatibility.createIntKeySpace(TablePlan, int expectedSize) -> IntKeySpace
RuntimeCompatibility.estimatedKeySpaceBytes(TablePlan, String implementation,
  boolean intKey, int expectedSize) -> long
RuntimeCompatibility.estimatedHashKeySpaceBytes(String implementation, int expectedSize) -> long

GeneratedColumn.stageCapacity(int) -> Object
GeneratedColumn.commitCapacity(Object) -> void
GeneratedColumn.clearRange(int fromInclusive, int toExclusive) -> void
ColumnGroup(String table, TablePlan, ChildOwnershipRegistry, int initialCapacity,
  GeneratedColumn... columns)
ColumnGroup capacity/resource-accounting methods -> package-private DenseTableState implementation
GeneratedColumn estimated/retained/release accounting methods -> package-private ColumnGroup implementation

PrimitiveColumn() / PresenceBitmap() public no-arg construction
PrimitiveColumn.get(int) -> exact primitive
PrimitiveColumn.set(int, exact primitive) -> void
PrimitiveColumn.copyFrom(same concrete type, int source, int target, int length) -> void
ObjectColumn<T>.get(int) -> T; set(int, T) -> void
ObjectColumn<T>.copyFrom(ObjectColumn<T>, int source, int target, int length) -> void
PresenceBitmap.isPresent(int)/setPresent(int)/clearPresent(int)
PresenceBitmap.copyFrom(PresenceBitmap, int source, int target, int length)
PresenceBitmap.presentCount() -> int

DenseTableState(String tableLogicalName, RuntimePlan, TablePlan, ColumnGroup)
DenseTableState.size/capacity -> int; structuralEpoch -> long; isReleased -> boolean
DenseTableState.runtimePlan -> RuntimePlan
DenseTableState.checkActive(String operation) -> void
DenseTableState.checkRowIndex(int rowIndex, String operation) -> int
DenseTableState.beginOperation(String operation) -> void
DenseTableState.beginMaterialization/beginOperationMaterialization(String operation) -> void
DenseTableState.preflightStructuralOperation(String operation) -> void
DenseTableState.preflightStructuralOperation(String operation, boolean structuralChange) -> void；两种形式都检查active operation、materialization reentrancy和view pin，`structuralChange=true`时额外预检structural epoch可递增
DenseTableState.endMaterializationSuccess/endMaterializationFailure(MaterializationTracker) -> void
DenseTableState.endOperationSuccess(String operation, long scanned, long matched, long changed) -> void
DenseTableState.endOperationFailure(String operation, long scanned, long matched, String errorCode) -> void
DenseTableState.abortOperation(String operation) -> void
DenseTableState.prepareAppend(int count) -> int startRow
DenseTableState.preflightAppendStorage(int count, long proposedKeySpaceBytes, String operation)
DenseTableState.preflightReplaceStorage(int count, long proposedKeySpaceBytes, String operation)
DenseTableState.preflightKeySpaceStorage(long proposed, String operation)
DenseTableState.reserveBulkScratch/releaseBulkScratch(long bytes, String operation)
DenseTableState.commitKeySpaceStorage(long previous, long proposed, String operation)
DenseTableState.abortConstruction() -> void
DenseTableState.commitAppend(int expectedStartRow, int count) -> void
DenseTableState.prepareReplace(int newSize) -> int previousSize
DenseTableState.commitReplace(int expectedPreviousSize, int newSize) -> void
DenseTableState.prepareClear() -> int previousSize
DenseTableState.prepareChildChange/commitChildChange(String operation) -> void
DenseTableState.prepareRelease() -> int previousSize
DenseTableState.commitClear(int expectedPreviousSize) -> void
DenseTableState.commitRelease(int expectedPreviousSize) -> void
DenseTableState.markOwned(String path)/rejectOwnedRelease(String operation) -> void
DenseTableState.preflightOwnedRelease(String operation) -> void
DenseTableState.isOwned/hasPinnedBorrow -> boolean
DenseTableState.commitOwnedRelease(boolean aggregateRelease) -> void
DenseTableState.updateScratch(long currentBytes, long highWaterBytes) -> void
DenseTableState.reserve(int expectedCapacity) / operationScratch(long currentBytes) -> void
DenseTableState.updateResult(long scanned, long matched, long changed) -> UpdateResult
DenseTableState.statsSnapshot() / statsSnapshot(long childInstances, long descendantRows)
  -> TableStats; resetStats() -> void

IntKeySpace.implementation/size/capacity/used/contains/rowOf/requireInsertKey/
  retainedBytes/retainedBytesAfterEnsureAdditional/allocationBytesDuringEnsureAdditional/
  ensureAdditionalCapacity/put/remove/removeAt/updateRow/clear/releaseStorage/
  probeCount/collisionCount/rehashCount/addMetrics/resetMetrics
HashIntKeySpace(int expectedSize); size()/contains(int)/rowOf(int)
HashIntKeySpace.put(int key, int rowSlot)/remove(int key)/updateRow(int key, int rowSlot)/clear() -> void
HashLongKeySpace(int expectedSize); size()/contains(long)/rowOf(long)
HashLongKeySpace.put(long key, int rowSlot)/remove(long key)/updateRow(long key, int rowSlot)/clear() -> void
HashCompositeKeySpace(int expectedSize); size()/ensureInsertCapacity() -> int/void
HashCompositeKeySpace.estimatedPeakBytes(int expectedSize) -> long
HashCompositeKeySpace.firstSlot(long hash)/nextSlot(int slot) -> int
HashCompositeKeySpace.isEmpty/isLive(int slot) -> boolean
HashCompositeKeySpace.hashAt(int slot) -> long; rowAt(int slot) -> int
HashCompositeKeySpace.putAt(int slot, long hash, int rowSlot) -> void
HashCompositeKeySpace.removeAt(int slot)/updateRowAt(int slot, int rowSlot)/clear() -> void
HashIntKeySpace / HashLongKeySpace / HashCompositeKeySpace
  .capacity()/used() -> int
  .probeCount()/collisionCount()/rehashCount() -> long
  .addMetrics(long probes, long collisions, long rehashes) -> void
  .resetMetrics() -> void
HashIntKeySpace / HashLongKeySpace / HashCompositeKeySpace
  .retainedBytes()/retainedBytesAfterEnsureAdditional(int)/
  allocationBytesDuringEnsureAdditional(int)/ensureAdditionalCapacity(int)/releaseStorage()
PresenceBitmap.wordAt(int wordIndex) -> long
IndexBuffer.ensureCapacity(int)/array()/reset()/retainedBytes()/release() -> primitive scratch lifecycle
GroupedExactIndex.ensureCapacity(int rowCapacity, int additionalGroups) -> void
GroupedExactIndex.firstGroup(long hash)/nextHashGroup(int group)/representativeRow(int group) -> int
GroupedExactIndex.createGroup(long hash)/link(int group, int row)/unlink(int row)/relocate(int from, int to) -> void
GroupedExactIndex.groupSize(int group)/firstRow(int group)/nextRow(int row) -> int
GroupedExactIndex.clear()/release()/retainedBytes()/probeCount()/collisionCount()/rehashCount()/resetMetrics()
KeyCanonicalization.strictFloatKeyBits(String table, String field, float value, String operation) -> int
KeyCanonicalization.strictDoubleKeyBits(String table, String field, double value, String operation) -> long
KeyCanonicalization.strictFloatStorage/strictDoubleStorage(...) -> canonical float/double

MaterializationTracker(MaterializationBudget, String rootPath)
MaterializationTracker.addTableInstances/addRows/addLeafValues/addEstimatedBytes(long) -> void
MaterializationTracker.checkOwnershipDepth(int, String path) -> void
MaterializationTracker.addTableInstances/addRows/addLeafValues/addEstimatedBytes(
  long, String path) -> void
MaterializationTracker.addListAllocation(int, String path) -> void
MaterializationTracker.addMapAllocation(int, boolean boxedPrimitiveKeys, String path) -> void
MaterializationTracker.addOptionalAllocation(boolean present, String path) -> void
MaterializationTracker.budgetIdentity() -> String
MaterializationTracker.estimatedBytes/rows/leafValues/tableInstances -> long;
  maximumDepth -> int
MaterializationTracker.enterOwnership/exitOwnership(Object identity, String path) -> void
MaterializationAllocation.installForCurrentThread(Provider) -> Scope
MaterializationAllocation.preflight(String phase, long estimatedBytes, String path) -> void
MaterializationAllocation.Provider.allow(String phase, long estimatedBytes, String path)
  -> boolean
MaterializationAllocation.Scope.close() -> void
ChildOwnershipRegistry.beginMaterialization/endMaterialization/preflightMutation/
  newOwnerToken/stage/publish/discardStaged/resolve/preflightPinned/release/
  preflightRelease/releasePreflighted/beginCascade/collectCascade/preflightCascade/
  commitCascade/cancelCascade/releaseStorage/
  childInstanceCount/descendantRowCount/hasPinned
OwnedChildTable.hasPinnedSubtree/preflightOwnedRelease/releaseOwnedSubtree/subtreeChildInstanceCount/
  subtreeDescendantRowCount
```

`HashIntKeySpace` / `HashLongKeySpace` 的 `remove` 只写 tombstone，不在 remove/swap-remove 内触发 rehash/allocation；generated keyed delete先移除deleted key，再对实际tail-fill survivor调用`updateRow`修复目标Index。rehash只能发生在insert/growth boundary，不能留下对已提交row的stale locator。

`boolean`、`byte`、`short`、`int` 和 `float` key 静态绑定 `HashIntKeySpace`；`long`、`double` key 静态绑定 `HashLongKeySpace`。floating key 在 Batch/import、lookup和compaction repair均先经 `KeyCanonicalization` 拒绝 non-finite、把 `-0.0` canonicalize为 `+0.0` 并使用 canonical bits；不在 hot lookup 创建 boxed key、tuple或metadata interpreter。

`GeneratedColumn` 的 `Object` 只承载 staged primitive array并由 `ColumnGroup` 在 growth boundary内部回传给同一 concrete column；generated source/hot loop不读取或 cast该 Object。`ensureCapacity` 返回是否实际增长。Prepare方法完成active/reentrant/range/overflow/capacity preflight但不改变size/epoch；generated typed copy/clear成功后调用匹配的commit。Mismatch进入internal invariant failure。新增protocol方法可以additive，现有方法不能靠 generated code migration重命名。

```text
XxxTable
  -> XxxTableStore
       -> TableLayout
       -> RowSpace
            -> PrimaryLocator  // keyed table only
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
| `RowSpace` | row membership、packed `Index` 与 `[0,size)` 有效性规则 |
| `PrimaryLocator` | keyed table 才有的 `RowKey -> Index` hash定位结构 |
| `ColumnStore` | primitive/object columns、presence bitmap、capacity 和 slot-level payload |
| `AccessStructures` | secondary exact index与unique的bucket/group/row-link结构 |
| `AccessPath` | default scan、exact-index source与dynamic sort等Row Pipeline source的内部执行入口 |
| `MutationCoordinator` | batch、replaceAll、update、swap-remove、exact-index delta、epoch协调 |
| `LifecycleState` | epoch、active view、released、stats、typed lifecycle errors |

Public keyed table 映射为 `TableStore + RowSpace + PrimaryLocator + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`。

Public dense table 映射为 `TableStore + RowSpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`。Dense table没有PrimaryLocator，但仍保留ColumnView、Row Pipeline、Materialized Object、secondary exact index/unique、lifecycle和typed errors。

## 3. ColumnStore

每个 generated table instance 使用 packed row storage：

- 每个 leaf field 对应一组同长 column；
- scalar、semantic scalar 和 enum 使用 primitive column；
- value field 按 leaf expansion 展开为 columns；
- optional field 使用 presence bitmap 加 payload column 或 handle column；
- string V1 baseline 使用 `String[]` payload column，后续可引入 string pool；
- `@SomaChild List`/`Map` field 使用 `ChildTableHandle` locator column，不存储 Java Collection 或 public/live object reference；
- `RowSlot` 是当前 packed storage 内的位置，不是 stable business identity。Public dense table direct API 中的 row index 映射到当前 `RowSlot`。

每个公开可用的 stable table state 中，live `RowSlot` 必须形成 `[0, size)` packed range；remove candidate可以暂存在`IndexBuffer`，但不得分配或保留`boolean[size]`全表mark，成功返回后也不得留下长期 tombstone/hole。Single/batch delete必须通过swap-remove/tail-fill恢复packed invariant，并同步维护所有primary locator与exact index。

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

Runtime core 必须支持 public/generated API 的两类 table：keyed table 和 dense table。二者共享 `TableStore` 组合模型，区别在于是否存在`PrimaryLocator`。

Keyed table：

- 有 stable logical key；
- 必须维护hash-based `PrimaryLocator`，用于 `RowKey -> RowSlot`；
- 支持 duplicate key detection、`fetch(key)`、`containsKey(key)`、`mutate(key)` 和 `delete(key)`；
- 适合 entity state、lookup table 和唯一性约束。

Dense table：

- 没有 stable logical key；
- 以 packed row storage、current-Index iteration、ColumnView 和exact/dynamic-sorted access为主要访问方式；
- 适合矩阵/数组型 runtime state、packed scan 和 solver workspace；
- 可以是长生命周期 table，也可以通过 `replaceAll(batch)` 在同一 table instance 内反复刷新；
- row index 只对当前 table state 有效，structural mutation 后不得作为 stable identity 使用。

Runtime core 必须减少hot terminal中的短生命周期Java对象。Dense workspace的价值在于复用column capacity、批量刷新、IndexBuffer和exact-access storage；普通局部对象仍由application按业务需要使用。

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

## 6. Primary locator

Keyed table必须有hash-based primary locator。Primary lookup属于table identity，不作为普通secondary index处理：

```text
canonical RowKey leaves -> hash locator -> current packed Index
```

Primitive int-width/enum/floating-bit key使用`HashIntKeySpace`，long-width key使用`HashLongKeySpace`，String/composite Value key使用`HashCompositeKeySpace`。所有实现采用open addressing、not-found sentinel、duplicate detection、tombstone delete、growth-boundary rehash和row relocation repair；不使用Key到bounded Entity的Sparse Set映射。

`HashCompositeKeySpace`只保存primitive hash、probe state和packed Index；generated table在同hash candidate上静态展开normalized leaf equality。只有full equality才视为同一identity，hash collision继续probe。remove/swap-remove先按row leaf找回identity slot，再remove或更新实际moved survivor的Index。

Hash value、bucket layout和probing strategy是internal implementation detail，不进入generated public API、Materialized Object或schema hash。

### 6.1 Floating identity/access canonicalization

Runtime core必须提供generated binding可复用的floating validation/canonicalization primitive：

- ordinary payload column接受Java `NaN`、positive/negative infinity和negative zero；
- key/index/unique floating leaf写入或查询前必须finite；
- strict leaf negative zero canonicalize为positive zero；
- key equality/hash、secondary matching和unique detection使用相同canonical value；
- invalid value在visible mutation前返回typed invalid-value error，并携带field/selector/materialization path。

## 7. Packed Index 与 IndexBuffer

两类table的live rows始终占据`[0,size)`。`Index`只是当前物理位置；structural mutation后旧Index可以指向另一row。Public bulk导出使用epoch-bearing detached `IndexSnapshot`，runtime hot execution使用table-local `IndexBuffer`。

`IndexBuffer`只保存primitive `int[] + length/high-water`，用于dynamic sort、mutation candidate freeze和显式snapshot copy；reset只归零logical length，不逐元素清零。读取exact group时优先沿group link零复制遍历，不为每个stage复制候选数组。

## 8. Grouped exact index / unique

每个`@SomaIndex`/`@SomaUnique`由一个`GroupedExactIndex`承载：

```text
hash bucket -> same-hash group chain -> group head -> row links
row -> group / prev / next
```

Generated code拥有selector hash与full canonical equality；runtime只拥有primitive bucket/group/link管理。规则：

- index允许group size为0..N；unique要求group size最多1；
- append/update/remove采用incremental link/unlink/relocate；
- replaceAll/create可以在未发布fresh structure中bulk build；
- read path不存在dirty/full rebuild/full-scan fallback；
- hash collision通过same-hash group chain与representative-row full equality区分；
- group内枚举顺序不作承诺；
- update先验证整次terminal的final-state uniqueness，允许合法value swap，再统一publish；
- mutation成功返回时所有structures已经current，expected failure保留旧facts。

## 9. Swap-remove / tail-fill

Dense与keyed table删除都不保证物理顺序。单row删除把最后一个survivor移动到hole；multi-row删除先排序selected Index，再从tail domain选择未删除survivor填充front holes。实际move数`compacted <= removed`。

每次move必须同步复制全部column/presence/child handle，修复primary locator，并调用每个exact index的`relocate(from,to)`；不允许stable forward compaction、`boolean[size]`全表mark或read-time repair。

## 10. AccessPath

`AccessPath`只决定terminal开始时的初始source sequence：

- default scan：当前物理`0..size-1`；
- exact-index source：terminal开始时定位current group并沿row links遍历；
- dynamic sorted path：把当前candidate写入IndexBuffer并按本次comparator排序。

未排序terminal只遵循current source sequence，不承诺business order。`AccessPath`不进入public API；generated `findByXxx(...)`与默认table source映射到内部path，业务顺序显式使用`sorted(...)`。

## 11. Batch boundary

`reserve`、`addBatch` 和 `replaceAll` 是 V1 性能边界。

规则：

- loader 应先估算 capacity，再 reserve；generated reserve同时覆盖columns、primary locator与exact indexes，而不是只增长column arrays；
- addBatch 按 batch size 扩容和写入；
- replaceAll 尽量复用 capacity，是 dense table 刷新矩阵行、packed data 和 solver workspace 的主要边界；
- primary locator与exact index在detached staged state中统一build/validate，并随batch facts原子publish；
- per-row append 不是默认 import 路径；
- deterministic memory limit 和可控 allocator/provider failure 必须映射为可区分错误；raw `OutOfMemoryError` 原样传播，capacity/column staging 保证 publish 前旧 stable state 仍满足 invariant。

## 12. Materialization / buffer / ColumnView

Runtime 必须区分：

| 类型 | 持有 live storage | mutation 后语义 |
|---|---|---|
| Pipeline plan | 否 | terminal operation 基于执行时 table 状态 |
| Materialized schema object / `List` / `Map` | 否 | detached complete copy，不反映后续 mutation |
| KeyBuffer | 否 | stable materialized key values |
| IndexSnapshot | 否 | 只对来源table的captured structural epoch有效；stale/wrong-table使用fail closed |
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
- primary locator与all exact indexes在row move后current；
- dead reference 不保持无意义 GC reachability；
- runtime internal handle/buffer 不进入 public result。

## 14. 非目标

本文不规定 public generated method、ownership state machine、concurrency、error payload、growth/hash/index concrete algorithm、serialization 或 persistence。
