# SOMA Access Model 核心术语

类型：Temporary

状态：Stage 1 canonical candidate 已由 `fd82eba` 实现；待正式领域语言固化

Owner：SOMA Java Access Model 核心术语治理

事实范围：目标 canonical terminology、概念边界、generated replacement与迁移约束

非事实范围：正式术语Owner或兼容承诺

输入：[产品模型](product-model.md)、[Stage 1 决策](stage-1-decisions.md)

最后审查日期：2026-07-23

## 1. 命名原则

1. 使用者名称表达访问职责，internal名称表达columnar机制。
2. logical element、callback borrow、Index sequence和materialized object分开。
3. current Index必须与Key/stable identity分开。
4. `View`只表示仍连接live storage的scoped borrow。
5. 同一概念一个canonical name；不长期保留兼容alias。
6. 不暗示未提供的Arrow兼容、vector engine、parallel、stable order或snapshot isolation。
7. Row只在确实表示schema record/materialized record/count的位置使用，不做机械词频清零。

## 2. Product vocabulary

| Canonical term | 定义 | 不等同于 |
|---|---|---|
| SOMA Access Model | SOMA全部合法访问路径与组合规则 | Candidate Pipeline |
| PointAccess | 以current Index、Key或Unique exact value定位`0..1`项 | group scan |
| CandidateAccess | CandidateSource + ordered stages + terminal | 全部Access Model |
| Candidate Scan | CandidateAccess的generated one-shot lazy handle | full-table必扫、read-only scan |
| Candidate source | Packed Table、ExactGroup、ExactUnique bridge或owned child binding | materialized List |
| candidate sequence | 单次operation内有顺序的current Index序列 | stable identity/order |
| Key Traversal | one-shot logical key borrow/materialization | Map view、Candidate Scan |
| Column Traversal | one-shot typed full-column traversal | multi-stage Pipeline、ColumnView |
| ColumnView | close/pin约束内的typed live-column indexed borrow | detached array |
| schema carrier | `@SomaTable`用户class及detached materialization shape | live stored object、DTO |
| table element | 某current Index上aligned fields的logical组合 | JVM row object、identity |
| Cursor | callback-scoped read borrow | Iterator、stable ref、materialized object |
| UpdateCursor | candidate update callback中的staged read/write borrow | direct Mutator |
| Mutator | caller-owned one-shot point mutation builder | callback Cursor |

“Scan”表示从一个candidate source执行受控evaluation，不表示必然packed全表scan，也不表示只读；`update/remove`是合法terminal。

## 3. Storage 与 result vocabulary

| Canonical term | 定义 |
|---|---|
| current Index | 当前packed `[0,size)`位置，mutation/lifecycle后失效 |
| Key | keyed Table的stable logical identity |
| PrimaryLocator | Key → current Index的derived structure |
| GroupedExactIndex | secondary exact group/row-link derived structure |
| PackedIndexSpace | packed membership、capacity和current Index validity |
| IndexBuffer | Table-owned reusable primitive candidate scratch |
| IndexSnapshot | detached current-Index sequence；不是stable row snapshot |
| Batch | detached construction/import staging |
| Materialized Object | detached schema carrier/List/Map graph |
| RuntimePlan | create-time immutable execution/resource policy |
| TableStats | immutable runtime observation，不拥有business facts |

`IndexBuffer`绝不进入application API；`IndexSnapshot`不暴露internal backing array，也不延长Index有效期。

## 4. Generated canonical mapping

| Current token | Target token/处置 |
|---|---|
| `*Rows` | `*Scan` |
| `*Row` callback interface | `*Cursor` |
| `*MutableRow` callback interface | `*UpdateCursor` |
| `rows()` | 删除；Table是Packed source |
| `findByX` group source | `scanByX` |
| `findByX` unique source | `findByX` point materialization；bridge为`scanByX` |
| `rowIndexes()` | `indexSnapshot()` |
| no scalar candidate Index terminal | `findIndex()` / `requireIndex()` |
| `findRowIndex(key)` | `findIndex(key)` |
| `rowIndexOf(key)` | `requireIndex(key)` |
| `rowIndex` parameter | `index` |
| `*Keys` | `*KeyTraversal` |
| `*ColumnPipeline` | `*ColumnTraversal` |
| `RowSpace` | `PackedIndexSpace` |

保留 `findFirst/firstOrThrow/fetchAll` 表达detached materialization，返回类型与Index terminal明确区分。

## 5. Index method grammar

统一规则：

```text
findIndex...     missing -> -1
requireIndex...  missing -> typed failure
fetchAt(index)   current Index -> required materialized carrier
mutateAt(index)  current Index -> point Mutator
indexSnapshot()  candidate sequence -> detached IndexSnapshot
```

- Table `findIndex(key)`/`requireIndex(key)`定位PrimaryKey；
- `findIndexByX`/`requireIndexByX`定位SecondaryUnique；
- Scan `findIndex()`/`requireIndex()`取得最终sequence first；
- `At`只用于caller已经持有current Index的direct access。

## 6. Row 的保留与退出

退出canonical core/generated callback vocabulary：

- Row Pipeline、Row Cursor、MutableRow、RowSpace、rowIndexes、rowIndex参数。

可以保留：

- 用户自己的schema class名，例如`TraceSampleRow`；
- “row-oriented vs columnar”技术比较；
- materialized row/row count等确实指table record数量的说明；
- `TableStats.rows()`、materialization budget rows等稳定public count语义，除非另有独立API治理；
- 历史Report、superseded文档中的当时事实。

不因用户domain type含Row就重命名application模型；本专题治理SOMA拥有的核心概念。

## 7. Diagnostic vocabulary

| Current | Target |
|---|---|
| `rows.count` / `rows.*` | `scan.count` / `scan.*` |
| `escaped_row_cursor` | `escaped_cursor` |
| mutable cursor escape | `escaped_update_cursor` |
| `pipeline_consumed` | 保留；仍准确描述Candidate Pipeline |
| Key/Column repeated terminal | `traversal_consumed` |

operation/error label是observable migration surface。Generator传入预绑定literal；hot path不拼接field/source字符串。

## 8. Collision 与保留名

Processor必须在生成前检查：

- `<Type>Scan`、`<Type>Cursor`、`<Type>UpdateCursor`、`<Type>KeyTraversal`；
- `scanByX`与unique point family；
- `findIndex/requireIndex/indexSnapshot`及leaf overload；
- typed `*ColumnTraversal` handwritten public type；
- nested Plan/StageStorage/source subtype和用户declaration冲突；
- Java keyword、Unicode code-point order、method erasure与parameter slot。

当前四场景未发现冲突不代表通用schema可跳过admission。

## 9. Migration 规则

- 一次clean regeneration；不生成deprecated alias或adapter；
- generated public、handwritten runtime、diagnostics、golden、consumer和example同一slice切换；
- schema hash保持不变，generated/runtime protocol升级；
- 文档固化时只把长期概念提升到唯一Owner，不复制Temporary过程；
- checker只检查current formal/code surface，不把历史Report当残留错误。

## 10. Stage 1 结论

Stage 0的`Operation Pipeline/Scan vs Elements` shortlist已经关闭：Access Model成为umbrella，Candidate Scan成为multi-field lazy family，Cursor/UpdateCursor、KeyTraversal、ColumnTraversal、PackedIndexSpace和Index词族形成唯一候选。

本文件仍是Temporary；`fd82eba` 已完成 clean naming cutover 与完整迁移 Gate，但在
S2.7 原子固化前仍不替代正式领域语言 Owner。
