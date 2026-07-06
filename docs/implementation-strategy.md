# SOMA 实现方案设计

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文定义 `soma_java` V1 的推荐实现方案，用于把 Java annotation schema、generated Soma API 和 Java runtime core 粘合成可验证的实现路线。

本文不替代 owner 契约文档：

- schema annotation 以 `soma-annotations/docs/annotation-schema-contract.md` 为准；
- processor/codegen 以 `soma-processor/docs/processor-codegen-contract.md` 为准；
- runtime core 以 `soma-runtime-core/docs/runtime-core-contract.md` 为准；
- Row Pipeline API 以 `docs/row-pipeline-api-contract.md` 为准；
- release gate 和 evidence 以 `docs/validation-gates.md` 为准。

如果本文与 owner 文档冲突，应先修正 owner 文档或回到设计决策，不允许用实现方案绕过正式契约。

## 2. 推荐方案

V1 推荐采用：

```text
compile-time strong generation
+ schema-agnostic runtime-core components
+ generated TableStore aggregate
+ shared Row Pipeline execution kernel
```

具体形态：

```text
annotated Java schema
  -> normalized schema model / schema hash
  -> generated XxxTable public facade
  -> generated XxxTableStore runtime aggregate
       -> TableLayout
       -> RowSpace
            -> KeySpace        // keyed table only
       -> generated ColumnStore binding
       -> AccessStructures
       -> AccessPath
       -> MutationCoordinator
       -> LifecycleState
```

处理边界：

- processor 负责 schema validation、normalized model、schema hash 和 deterministic codegen；
- generated code 负责 schema-specific API、typed row cursor、DTO materialization、field binding、selector comparator 和 source method；
- runtime-core 负责 columnar primitives、bitmap、`RowSpace`、`KeySpace`、`AccessStructures`、`AccessPath`、lifecycle、typed errors 和 stats；
- examples、benchmarks 和 gates 只通过 generated/public API 或发布形态验证，不绕过 runtime internal。

这个方案的核心判断是：V1 的性能和可维护性来自“schema-specific generated code + shared runtime kernel”的组合，而不是 runtime reflection，也不是每张表完全复制一套不可复用 runtime。

## 3. 不推荐方案

### 3.1 Metadata interpreter

用 runtime metadata interpreter 解释 schema、字段和 selector，可以减少 codegen 工作量，但会削弱 V1 目标：

- hot path 容易退回 object/reflection/map-style 访问；
- Row Pipeline cursor、ColumnView、typed mutator 和 DTO materialization 难以保持强类型；
- schema hash/runtime compatibility 的失败点更容易延后到运行期；
- generated API 可能只剩 thin wrapper，无法支撑 table-first 用户模型。

因此 metadata interpreter 不作为 V1 主方案。Runtime metadata 可以存在于 generated metadata 中用于 diagnostics 和 compatibility check，但不能成为 hot path 主执行机制。

### 3.2 Fully generated runtime per table

每张 table 完全生成自己的 column、key lookup、index、order、lifecycle 和 error handling，可以获得极端静态化，但会带来长期维护风险：

- row move、view pinned、stale view、released view、sidecar dirty 等规则会在多份 generated code 中重复；
- runtime invariant 难以集中测试；
- bug fix 需要同时修正 processor template 和所有已生成形态；
- V1 gate evidence 容易膨胀成大量 golden diff，而不是核心 runtime 行为验证。

因此 V1 不采用“全量生成 runtime”作为主方案。Generated code 只生成 schema-specific glue，稳定的数据结构和生命周期规则放在 runtime-core。

### 3.3 DTO-backed table

用 `List<DTO>`、`Map<Key, DTO>` 或 live DTO proxy 作为 runtime row storage，与 SOMA 定位冲突：

- hot storage 不是 columnar；
- optional bitmap、primitive column、ColumnView、batch import 和 sidecar access 无法自然成立；
- DTO detached materialization 边界会被破坏。

该方案不符合 V1 基线，不进入实现候选。

## 4. Runtime internal 分层

`TableStore` 是 generated table 的 runtime internal aggregate owner。它不是 public API，也不是单一数据结构；它组合多个职责明确的部件。

| 部件 | 实现责任 |
|---|---|
| `TableLayout` | schema hash、runtime compatibility、field layout、selector metadata |
| `RowSpace` | row membership、`RowSlot` 分配、packed slot 有效性、row move |
| `KeySpace` | keyed table 的 `RowKey -> RowSlot` 身份定位 |
| `ColumnStore` | primitive/object column、presence bitmap、capacity、slot-level payload |
| `AccessStructures` | secondary index、unique index、order sidecar |
| `AccessPath` | scan、index source、order source、dynamic sort source |
| `MutationCoordinator` | batch、replaceAll、delete、row move、dirty/rebuild、epoch 协调 |
| `LifecycleState` | store epoch、active view、released、stats、typed lifecycle errors |

关键规则：

- `Sparse Set` 只作为 `SparseIntKeySpace` 的实现材料，不是 table 本体；
- primary key lookup 属于 `KeySpace`，不混同为普通 secondary index；
- dense table 没有 `KeySpace`，但仍拥有完整 `ColumnStore`、`AccessStructures`、`AccessPath`、`MutationCoordinator`、`LifecycleState`；
- runtime sidecar、bitmap word、hash bucket、allocator policy 和 row pointer 不进入 public/generated API。

## 5. Generated code 分工

Processor 从 normalized schema model 生成 source。Generated source 不直接解释 Java annotation element，也不依赖 processor internal model。

每个 generated table 至少包含以下实现面：

- `XxxTable`：public table facade；
- `XxxBatch`：construction/import boundary；
- `XxxTableStore`：schema-specific runtime aggregate wiring；
- `XxxRows`：Row Pipeline plan object；
- `XxxRow` / `XxxMutableRow`：callback cursor；
- `XxxMutator`：single-row mutation；
- generated key/value classes when needed；
- DTO materialization mapper；
- typed ColumnView accessor or wrapper；
- generated comparator / selector binder；
- schema hash and runtime compatibility metadata。

Generated code 应负责：

- field leaf 到 runtime column 的静态绑定；
- key leaf 到 `KeySpace` 的编码、比较和错误定位；
- index/unique/order selector 的 leaf extraction；
- DTO detached copy materialization；
- cursor getter/setter；
- public API naming 和 Java 8 type shape。

Generated code 不应负责：

- 自行实现一套独立 bitmap word policy；
- 自行复制 HashKeySpace / SparseIntKeySpace 规则；
- 绕过 runtime typed errors；
- 暴露 sidecar handle、row pointer 或 runtime internal array。

## 6. Runtime-core 分工

Runtime-core 应提供 schema-agnostic reusable kernel。它不理解 Java annotation，也不生成 source。

V1 runtime-core 至少应沉淀：

- primitive columns：`IntColumn`、`LongColumn`、`DoubleColumn`、`FloatColumn`、`ByteColumn`、`ObjectColumn<T>`；
- optional bitmap helpers；
- `RowSpace` / row move helpers；
- `SparseIntKeySpace` and sparse-set-style material；
- `HashKeySpace` for int、long and generated composite key adapters；
- `AccessStructures` for secondary index、unique index and order sidecar；
- `RowSequence` and `AccessPath` execution primitives；
- lifecycle / view tracking / runtime stats；
- typed runtime error hierarchy or error code model。

Runtime-core 可以通过 internal interfaces 接受 generated adapters，例如 key equality、selector value extraction 和 comparator。接口边界必须保持 Java 8 兼容，且不能把 processor model 或 annotation type 带入 runtime-core。

## 7. 关键执行流程

### 7.1 Table create

`XxxTable.create()` 初始化 `XxxTableStore`，绑定 generated `TableLayout`、columns、optional bitmaps、key/index/order plan、runtime compatibility metadata 和 lifecycle state。

Create 阶段必须能读取 schema hash、schema version、processor version 和 runtime compatibility version。compatibility mismatch 应在初始化阶段失败，不能延后到 hot path。

### 7.2 Batch import / replaceAll

`addBatch(batch)` 和 `replaceAll(batch)` 是 V1 import 性能边界：

1. generated batch 提供 row count 和 typed value access；
2. `MutationCoordinator` 检查 active view、capacity 和 lifecycle；
3. `RowSpace` 分配或重建 row slots；
4. generated binding 写入 `ColumnStore`；
5. keyed table 写入 `KeySpace` 并检测 duplicate key；
6. `AccessStructures` 同步维护或标记 dirty；
7. structural mutation 成功后提升 `storeEpoch`。

如果 active ColumnView 使 structural mutation 不安全，必须返回 view_pinned 类错误。

### 7.3 Direct fetch / mutate

Keyed table：

```text
RowKey leaf values -> KeySpace -> RowSlot -> ColumnStore -> DTO / Mutator
```

Dense table：

```text
row index -> RowSlot -> ColumnStore -> DTO / Mutator
```

`fetch(key)`、`fetchAt(rowIndex)` 和 Row Pipeline `findFirst()` / `firstOrThrow()` / `fetchAll()` 都 materialize detached DTO，不返回 live row proxy。

### 7.4 Row Pipeline terminal

Row Pipeline construction 只记录 lazy plan，不扫描 table、不复制 row、不 acquire ColumnView。Terminal execution 才读取当前 table state：

```text
AccessPath -> RowSequence -> filter/sort/skip/limit -> terminal
```

Mutation terminal 开始前必须固定本次 `RowSequence` 或 traversal plan。一次 terminal 内的 update 不应让同一 row 因 index/order/filter 变化而重复进入本次 traversal。

### 7.5 ColumnView lifecycle

ColumnView 是 live readonly view。Acquire 时增加 active view count 并记录 epoch；release idempotent。

规则：

- close/release 后读取返回 released_view；
- table structural mutation 遇到 active view 返回 view_pinned，除非实现能证明 storage address/length/layout 不变；
- view epoch 与 store epoch 不一致返回 stale_view；
- ColumnView 强持有 owner table 或 storage owner，避免 use-after-release 语义。

## 8. 实施顺序

实施顺序可以分阶段，但不得改变 V1 release 目标。

### Phase 1: dense table 最小闭环

目标是先跑通无 `KeySpace` 的完整 table store 形态：

- `RowSpace`；
- primitive `ColumnStore`；
- required / optional field；
- `Batch`、`addBatch`、`replaceAll`；
- `fetchAt(rowIndex)` DTO materialization；
- default scan Row Pipeline；
- `filter`、`limit`、`findFirst`、`firstOrThrow`、`fetchAll`；
- `update` for non-key fields；
- ColumnView acquire/read/release；
- lifecycle typed errors and stats。

该阶段证明 dense table 不是简化数组，而是完整 `TableStore` 去掉 identity space。

### Phase 2: keyed table identity

在 Phase 1 基础上加入 `KeySpace`：

- generated key class / key adapter；
- `SparseIntKeySpace` for bounded int id；
- `HashKeySpace` for int、long and composite key；
- duplicate key、missing key、delete、row move update；
- `containsKey`、`fetch(key)`、`find(key)`、`mutate(key)`、`delete(key)`；
- key field 不生成 mutable setter。

### Phase 3: AccessStructures and AccessPath

加入 maintained access structures：

- order sidecar and lazy rebuild；
- secondary non-unique index；
- secondary unique index；
- generated `findByXxx(...)` and `byXxx(...)` source methods；
- `AccessPath` to `RowSequence` bridge；
- mutation terminal 后 sidecar dirty / rebuild / maintenance stats。

可以先实现 order 和 primary key lookup，但 V1 release claim 不能省略已承诺的 secondary index / unique 能力，除非正式 gate 记录 waiver。

### Phase 4: processor/codegen hardening

将 runtime 能力接入 processor：

- normalized model golden；
- schema hash golden；
- generated source deterministic output；
- generated table / batch / rows / mutator / ColumnView shape；
- selector validation diagnostics；
- name collision diagnostics；
- package smoke with Java 8 target。

### Phase 5: examples, benchmark smoke and release evidence

补齐可审计证据：

- FJSP-style examples smoke；
- Row Pipeline update/remove smoke；
- ordered source smoke；
- stale/released/view_pinned error path；
- benchmark smoke JSONL；
- G1-G6 reports。

Benchmark smoke 只证明场景和工具链可运行，不写成性能优势声明。

## 9. V1 不缩水约束

实现阶段允许内部排期，但不得把排期偷换成 scope shrink。

V1 必须保留：

- table-first generated API；
- keyed table and dense table public model；
- `TableStore` composition model；
- primitive column + optional bitmap；
- `KeySpace` primary key lookup；
- secondary index、unique index、order sidecar；
- Row Pipeline with generated row cursor；
- controlled in-place update and remove terminal；
- ColumnView live readonly lifecycle；
- DTO detached materialization；
- typed runtime errors；
- schema hash and runtime compatibility check；
- golden / package smoke / benchmark smoke / gate evidence。

以下行为属于缩水或偏航：

- 把 table 实现成 `List<DTO>` / `Map<Key, DTO>`；
- 把 dense table 实现成缺少 Row Pipeline、ColumnView、order、lifecycle 的普通数组；
- 把 primary key lookup 当作普通 secondary index，导致 identity 语义不清；
- 用 runtime reflection/interpreter 作为 hot path 主机制；
- 只生成 facade，不生成 typed cursor / mutator / DTO mapper；
- 用 generic runtime exception 覆盖 duplicate key、missing key、stale view、view pinned、released view 等错误；
- package smoke 绕过 artifact 或 generated API 直接访问 internal；
- benchmark smoke 被写成性能优势声明。

## 10. 验证映射

实现完成度必须通过 gate evidence 表达，不由口头判断替代。

| 能力 | 主要证据 |
|---|---|
| annotation schema / validation | G1 annotation schema report |
| normalized model / codegen determinism | G2 processor/codegen report |
| `TableStore` / runtime components | G3 runtime core report |
| generated API / Java 8 package | G4 package smoke report |
| examples / benchmark smoke | G5 examples and benchmark reports |
| release claim closure | G6 release readiness report |

Runtime core 单测应覆盖 component invariant；processor 测试应覆盖 diagnostics、golden output 和 generated compile；package smoke 应验证用户视角闭环。

## 11. 待补 owner 设计

本文不补写未定模块契约。以下设计仍应由 owner 文档单独补齐：

- `soma-testkit` 的 compile/golden/runtime invariant helper 契约；
- `soma-benchmarks` 的 runner、JSONL evidence schema、baseline claim 口径；
- secondary index / unique index 的具体 runtime 数据结构选择；
- generated source formatter 和 golden diff tolerance；
- memory estimate 的细化口径。

这些缺口不阻止本文作为实现路线进入正式文档体系，但在对应 gate 通过前不能支撑 V1 release claim。
