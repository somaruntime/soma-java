# Java runtime core 契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-runtime-core`

## 1. 目标

`soma-runtime-core` 提供无第三方依赖的 Java columnar runtime kernel。它不解析 annotation，不生成 Java source，也不拥有用户 schema 语义。

Runtime core 的目标：

```text
schema-known long-lived runtime state
+ keyed / dense table storage
+ packed primitive/object columns
+ optional bitmap
+ batch boundary
+ access sidecar
+ predictable materialization
```

## 2. Column storage

每个 generated table instance 使用 packed row storage：

- 每个 leaf field 对应一组同长 column；
- scalar、semantic scalar 和 enum 使用 primitive column；
- value field 按 leaf expansion 展开为 columns；
- optional field 使用 presence bitmap 加 payload column 或 handle column；
- string V1 baseline 使用 `String[]` payload column，后续可引入 string pool；
- table-typed field 使用 child table handle/reference column；
- row index 是当前 packed storage 内的位置，不是 stable business identity。

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

## 3. Table kind storage

Runtime core 只承载两类 table：keyed table 和 dense table。

Keyed table：

- 有 stable logical key；
- 必须维护 primary key index sidecar；
- 支持 duplicate key detection、`fetch(key)`、`containsKey(key)`、`mutate(key)` 和 `delete(key)`；
- 适合 entity state、lookup table 和唯一性约束。

Dense table：

- 没有 stable logical key；
- 以 packed row storage、row-index iteration、ColumnView 和 ordered access 为主要访问方式；
- 适合矩阵/数组型 runtime state、packed scan 和 solver workspace；
- 可以是长生命周期 table，也可以通过 `replaceAll(batch)` 在同一 table instance 内反复刷新；
- row index 只对当前 table state 有效，structural mutation 后不得作为 stable identity 使用。

Runtime core 不把短生命周期 Java 临时对象作为优化目标。dense workspace 的价值在于复用 column capacity、批量刷新和 sidecar access，而不是替代普通局部对象。

## 4. Optional bitmap

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

## 5. Key and hash index

Keyed table 必须有 primary key index sidecar。

查找语义：

```text
key leaf values -> normalized hash -> bucket -> full key equality -> row index
```

V1 自研 primitive hash index：

- int key -> row；
- long key -> row；
- generated composite key -> row；
- open addressing；
- not-found sentinel；
- duplicate key detection；
- remove / row move update；
- rehash；
- collision full equality。

Hash value、bucket layout 和 probing strategy 是 internal implementation detail，不进入 generated public API、DTO 或 schema hash。

## 6. Sparse set

V1 提供 self-owned sparse set，用于 bounded int id 或 dense membership 场景：

```text
dense[]
sparse[]
size
contains(id) = sparse[id] < size && dense[sparse[id]] == id
```

Sparse set 负责：

- add；
- remove；
- contains；
- dense iteration；
- clear reuse capacity；
- capacity growth；
- deterministic iteration order as stored in dense array。

Sparse set 是 runtime internal structure，不作为 public generated collection 暴露。

## 7. Secondary index and unique index

V1 index sidecar 分为：

- primary key index；
- secondary non-unique index；
- secondary unique index。

规则：

- index selector 由 processor 归一化；
- generated code 负责将 selector leaf values 写入 runtime index；
- unique index duplicate 必须返回可区分错误；
- non-unique index 可以使用 row list、row chain 或 rebuildable sidecar；
- row move 后必须同步维护或标记 dirty。

V1 可先实现 primary key 和 order，secondary index/unique 按 gate 优先级逐步补齐，但正式 release claim 只能引用已验证能力。

## 8. Order sidecar

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

## 9. Batch boundary

`reserve`、`addBatch` 和 `replaceAll` 是 V1 性能边界。

规则：

- loader 应先估算 capacity，再 reserve；
- addBatch 按 batch size 扩容和写入；
- replaceAll 尽量复用 capacity，是 dense table 刷新矩阵行、packed data 和 solver workspace 的主要边界；
- index 和 order sidecar 在 batch boundary 统一更新或标记 dirty；
- per-row append 不是默认 import 路径；
- allocation failure 或 memory limit exceeded 必须映射为可区分错误。

## 10. DTO materialization / buffer / ColumnView

Runtime 必须区分：

| 类型 | 持有 live storage | mutation 后语义 |
|---|---|---|
| ViewPlan | 否 | terminal operation 基于执行时 table 状态 |
| DTO | 否 | detached materialized copy，不反映后续 mutation |
| KeyBuffer | 否 | stable materialized key values |
| RowIndexBuffer | 否 | 只对生成时 table epoch 有效 |
| ColumnView | 是 | live readonly view，structural mutation 返回 view_pinned |

ColumnView 必须强持有 owner table 或 storage owner。close/release 后继续读取必须返回 released_view 类错误。

## 11. Epoch and lifecycle

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

## 12. Mutation 分类

Mutation 分为：

| 类别 | 示例 | active view 存活时 |
|---|---|---|
| non-structural | 修改现有非 key fixed-width leaf，且不改变 storage length/layout | 可以允许 |
| structural | reserve、append batch、replaceAll、delete、row move、string storage relocation、child table replacement、index/order rebuild with exposed view risk | 返回 view_pinned 或延后 |

如果实现无法证明 mutation 不影响 ColumnView 所依赖的 storage，应按 structural mutation 处理。

## 13. Runtime errors

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

## 14. Memory reporting

Java-only V1 不使用 native memory tracker。它使用 heap memory estimate / runtime stats：

- current estimated bytes；
- high water mark；
- table count；
- active view count；
- column capacity；
- row count；
- last allocation failure reason。

该估算用于 diagnostics、benchmark smoke 和 package smoke，不等同于 JVM 精确 heap profiler。

## 15. Concurrency boundary

V1 runtime table 默认是 synchronous single-owner object。

不承诺：

- cross-thread concurrent read/write safety；
- internal lock strategy；
- transaction；
- actor/scheduler；
- parallel scan/sort。

跨线程共享 table 或 ColumnView 时，上层 application model 必须自行保证 ownership、synchronization 和 lifecycle。

## 16. Performance evidence boundary

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
