# Java runtime core 契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-runtime-core`

## 1. 目标

`soma-runtime-core` 提供无第三方依赖的 Java columnar runtime kernel。它不解析 annotation，不生成 Java source，也不拥有用户 schema 语义。

Runtime core 的目标：

```text
schema-known long-lived runtime state
+ TableStore composition model
+ packed primitive/object columns
+ optional bitmap
+ batch boundary
+ access structures / access paths
+ predictable materialization
```

## 2. Runtime internal table model

Runtime core 使用 `TableStore` 组合模型承载 generated table 的 runtime internal storage。Public/generated API 仍只暴露 keyed table 和 dense table 两类 table kind；runtime internal 不使用 `Sparse Table` / `Unkeyed Sparse Table` 作为顶层抽象。

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

Public dense table 映射为 `TableStore + RowSpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`。Dense table 没有 `KeySpace`，但仍保留 ColumnView、Row Pipeline、DTO materialization、secondary index/order、lifecycle 和 typed errors。

## 3. ColumnStore

每个 generated table instance 使用 packed row storage：

- 每个 leaf field 对应一组同长 column；
- scalar、semantic scalar 和 enum 使用 primitive column；
- value field 按 leaf expansion 展开为 columns；
- optional field 使用 presence bitmap 加 payload column 或 handle column；
- string V1 baseline 使用 `String[]` payload column，后续可引入 string pool；
- table-typed field 使用 child table handle/reference column；
- `RowSlot` 是当前 packed storage 内的位置，不是 stable business identity。Public dense table direct API 中的 row index 映射到当前 `RowSlot`。

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
- bitmap 不进入 DTO shape；
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
- `HashKeySpace`：int / long / generated composite key，使用 hash-based key lookup。

`HashKeySpace` 至少支持：

- int key -> `RowSlot`；
- long key -> `RowSlot`；
- generated composite key -> `RowSlot`；
- open addressing；
- not-found sentinel；
- duplicate key detection；
- remove / row move update；
- rehash；
- collision full equality。

Hash value、bucket layout 和 probing strategy 是 internal implementation detail，不进入 generated public API、DTO 或 schema hash。

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
- order sidecar 不进入 DTO、ColumnView 或 public API；
- ordered access 返回 key buffer、row index buffer 或 materialized DTO。

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
- allocation failure 或 memory limit exceeded 必须映射为可区分错误。

## 12. DTO materialization / buffer / ColumnView

Runtime 必须区分：

| 类型 | 持有 live storage | mutation 后语义 |
|---|---|---|
| ViewPlan | 否 | terminal operation 基于执行时 table 状态 |
| DTO | 否 | detached materialized copy，不反映后续 mutation |
| KeyBuffer | 否 | stable materialized key values |
| RowIndexBuffer | 否 | 只对生成时 table epoch 有效 |
| ColumnView | 是 | live readonly view，structural mutation 返回 view_pinned |

ColumnView 必须强持有 owner table 或 storage owner。close/release 后继续读取必须返回 released_view 类错误。

## 13. Epoch and lifecycle

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

## 14. Mutation 分类

Mutation 分为：

| 类别 | 示例 | active view 存活时 |
|---|---|---|
| non-structural | 修改现有非 key fixed-width leaf，且不改变 storage length/layout | 可以允许 |
| structural | reserve、append batch、replaceAll、delete、row move、string storage relocation、child table replacement、index/order rebuild with exposed view risk | 返回 view_pinned 或延后 |

如果实现无法证明 mutation 不影响 ColumnView 所依赖的 storage，应按 structural mutation 处理。

## 15. Runtime errors

Runtime errors 至少区分：

- duplicate key；
- missing key；
- invalid selector；
- field not found；
- dtype mismatch；
- stale view；
- view pinned；
- released view；
- table released；
- allocation failure；
- memory limit exceeded；
- internal invariant violation。

这些错误不能压缩成 generic runtime exception，否则用户无法判断 schema、生命周期、资源还是调用顺序问题。

## 16. Memory reporting

Java-only V1 不使用 native memory tracker。它使用 heap memory estimate / runtime stats：

- current estimated bytes；
- high water mark；
- table count；
- active view count；
- column capacity；
- row count；
- last allocation failure reason。

该估算用于 diagnostics、benchmark smoke 和 package smoke，不等同于 JVM 精确 heap profiler。

## 17. Concurrency boundary

V1 runtime table 默认是 synchronous single-owner object。

不承诺：

- cross-thread concurrent read/write safety；
- internal lock strategy；
- transaction；
- actor/scheduler；
- parallel scan/sort。

跨线程共享 table 或 ColumnView 时，上层 application model 必须自行保证 ownership、synchronization 和 lifecycle。

## 18. Performance evidence boundary

Runtime benchmark smoke 至少观察：

- optional all-present scan；
- optional all-absent scan；
- optional mixed bitmap chunk scan；
- key lookup normal case；
- key lookup hash collision case；
- batch import with reserve；
- batch import without enough capacity；
- ordered access lazy rebuild；
- dense table replaceAll + ordered `findFirst` / `firstOrThrow`；
- ColumnView acquire/read/release。

Benchmark smoke 只证明工具链和场景可运行；性能优势声明必须另有 baseline、规模、环境、重复次数和统计口径。
