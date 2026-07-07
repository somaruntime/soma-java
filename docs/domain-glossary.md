# soma_java 领域术语表

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文维护 `soma_java` 跨模块沟通时使用的核心领域术语，避免 public API、schema annotation、processor、runtime internal 和 evidence 语境混用。

术语表不替代各 owner 文档的契约定义。某个术语涉及具体行为时，以对应 owner 文档为准：

- public schema annotation 以 `soma-annotations/docs/annotation-schema-contract.md` 为准；
- generated API / Row Pipeline 以 `docs/row-pipeline-api-contract.md` 为准；
- runtime internal 以 `soma-runtime-core/docs/runtime-core-contract.md` 为准；
- release gate / evidence 以 `docs/validation-gates.md` 为准。

## 2. 命名原则

核心抽象必须有清晰、正向、边界明确的名称。若一个抽象只能通过“非 X”“无 X”“类似 X 但不是 X”解释，或需要用某个内部数据结构代表整个对象，应先审查抽象边界。

命名必须区分：

- public/generated API 术语；
- schema annotation 术语；
- processor/codegen 术语；
- runtime internal 术语；
- benchmark / release evidence 术语。

## 3. Public / Generated API 术语

| 术语 | 定义 | 边界 |
|---|---|---|
| `Table` / generated `XxxTable` | 用户面对的 generated table-first API facade | 不暴露 runtime sidecar、bitmap word、row pointer、allocator policy |
| keyed table | 声明了 stable logical key 的 public table kind | schema/API 层术语；runtime 内部由 `TableStore + RowSpace + KeySpace + ColumnStore + ...` 承载 |
| dense table | 没有 stable logical key 的 public table kind | 不是缩水版 table；runtime 内部仍有 `ColumnStore`、`AccessStructures`、`AccessPath`、lifecycle 等能力 |
| `Batch` | 批量导入、追加或替换的数据边界 | 不是 runtime row storage 本体 |
| DTO | schema source class，同时也是 detached materialized boundary object | 不是 hot-loop live row object，不是 runtime row proxy |
| Row Pipeline | generated table 上的 row traversal / filter / update / terminal API | 不等同于 `Stream<DTO>`；callback 使用 generated row cursor |
| ColumnView | 对 runtime column storage 的 live readonly view | 有 lifecycle / owner holding / stale-view 规则 |
| runtime frontier | solver/application 在运行中增量维护的候选集合 | 是用户 schema 建模场景；若有 stable key，应建成 keyed table，不是 runtime internal cache 或 sidecar |

## 4. Runtime Internal 术语

| 术语 | 定义 | 边界 |
|---|---|---|
| `TableStore` / generated `XxxTableStore` | 一张 generated table 的 runtime internal aggregate owner | 组合 row、column、access、mutation、lifecycle；不是 public API |
| `TableLayout` | schema hash、field layout、column binding、selector metadata | 不持有实际 row 数据 |
| `RowSpace` | row membership、`RowSlot` 分配、packed slot 有效性规则 | 不持有 field payload，不代表 secondary index 或 order |
| `KeySpace` | keyed table 才有的 `RowKey -> RowSlot` 身份定位结构 | primary key lookup 属于身份空间，不按普通 secondary index 处理 |
| `SparseIntKeySpace` | bounded int id 的 sparse-set-style `KeySpace` 实现 | Sparse Set 只是实现材料，不是 table 本体 |
| `HashKeySpace` | long key、composite key 或 general key 的 hash-based `KeySpace` 实现 | hash bucket / probing 是 internal detail |
| `ColumnStore` | primitive/object columns、presence bitmap、capacity 和 slot-level payload | 不拥有 key、secondary index、order 或 mutation policy |
| `AccessStructures` | 被维护的 secondary index、unique index、order sidecar 等访问结构 | 不包含 primary key identity 本身 |
| `AccessPath` | default scan、index source、order source 等 Row Pipeline source 的内部执行入口 | 负责产生 `RowSequence`，不拥有 payload storage |
| `MutationCoordinator` | batch、replaceAll、delete、row move、sidecar dirty/rebuild、epoch 协调 | 避免 `ColumnStore` / `RowSpace` 各自隐藏跨组件副作用 |
| `LifecycleState` | epoch、active view、released、stats、typed lifecycle errors | 不定义 schema，也不持有 field payload |

Runtime internal 的核心模型：

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

## 5. Row / Access 术语

| 术语 | 定义 | 边界 |
|---|---|---|
| `RowKey` | stable logical identity | 仅 keyed table 有；structural mutation 后仍代表业务身份 |
| `RowSlot` | 当前 packed column storage 中的物理位置 | structural mutation / row move 后可能变化 |
| row index | public dense table direct API 中暴露的当前位置概念 | 不是 stable business identity；讨论 runtime internal 时优先使用 `RowSlot` |
| `RowSequence` | 某次 Row Pipeline terminal 使用的 row slot 序列 | 可来自 scan、index source、order source 或 dynamic sort |
| primary key lookup | `KeySpace` 维护的 `RowKey -> RowSlot` 身份定位 | 不作为普通 secondary index 解释 |
| secondary index | `AccessStructures` 维护的非主键访问结构 | 由 generated source method 进入 Row Pipeline |
| order sidecar | `AccessStructures` 维护的 ordered row permutation | 不改变 `ColumnStore` 的 physical row order |

FJSP 中的 `MachineCandidate` 是 runtime frontier 的典型例子：row 存在表示候选有效，`(MachineId, OperationKey)` 是 stable logical identity；被选中的 operation 应通过 `findByOperation(operationKey).remove()` 删除全部相关候选，而不是用 `active` 字段长期保留失效 row。

## 6. 不推荐术语

| 不推荐术语 | 原因 |
|---|---|
| Sparse Table | 容易把 Sparse Set 误提升为 table 本体 |
| Unkeyed Sparse Table | 否定式命名，不能正向表达 dense table 的 runtime 组合能力 |
| Data Table | 过于宽泛，容易暗示只有数据列、没有 key/index/order/lifecycle |
| TableCore | 不如 `TableStore` 明确表达 runtime ownership 和 lifecycle |

## 7. V1 不缩水约束

内部命名调整不得改变 V1 public contract：

- public table kind 仍是 keyed table 和 dense table；
- Java annotation schema / generated API 术语不因 runtime internal 命名改变而变化；
- dense table 不是缩水版 table；
- runtime internal 必须保留 key/index/unique/order、Row Pipeline、ColumnView、DTO detached materialization、typed runtime errors、schema hash/runtime compatibility 和 gate evidence 的 V1 目标。
