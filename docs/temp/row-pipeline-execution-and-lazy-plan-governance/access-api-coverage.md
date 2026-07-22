# SOMA Access Pattern / API 覆盖矩阵

类型：Temporary

状态：Stage 1 evidence complete；决策见 Stage 1 决策文档

Owner：SOMA Java Access Model API 审查

事实范围：当前 generated/runtime API 对 Access Model 的覆盖、重复、缺口与抽象问题

非事实范围：当前用户承诺的改变、已经实施的新名称、正式兼容性声明

基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

输入：[Access Model](access-model.md)

最后审查日期：2026-07-22

## 1. 证据方法

覆盖矩阵由以下当前事实交叉核对：

- FJSP generated `MachineCandidateTable`、`OperationDefinitionTable`、`*Rows`、`*Keys`、`*Batch`；
- runtime `IndexSnapshot`、typed `*ColumnPipeline`、typed `*ColumnView`；
- external Maven keyed/access/child consumer；
- FJSP、VRP、Simulation、Game executable scenario；
- formal Design 只用于判断语义是否符合目标，不用于猜测当前 signature。

“完整”表示当前已有自然可执行路径；“部分”表示能力存在但 cardinality、命名、成本或组合不自然；“缺口”表示重要 Pattern 没有 canonical API。

## 2. Pattern 覆盖矩阵

| Pattern | 当前 API | 覆盖 | 判定 |
|---|---|---|---|
| `AP-01` packed candidate | `rows()`；Table 上 `filter/skip/limit/sorted/...` forwarding | 完整但重复 | source 与 forwarding 表达同一候选语义 |
| `AP-02` current Index point | `fetchAt`、`mutateAt`；bounds failure | 部分 | read/mutate 清晰，但与 locate terminal 的 Index 词族断裂 |
| `AP-03` primary-key point | `containsKey`、`findRowIndex`、`rowIndexOf`、`find/fetch/mutate/delete` | 完整但命名分裂 | `RowIndex` 与 `At` 混用；materialize/Index 成本可区分 |
| `AP-04` secondary unique point | `findByX(...) -> *Rows` | 部分 | `0..1` 被 group-shaped Pipeline 掩盖，没有 direct point family |
| `AP-05` exact group | `findByX(...) -> *Rows` | 完整但命名含混 | `find` 听起来像 point/materialize，实际返回 lazy candidate source |
| `AP-06` owner child | `child(parentKey)`、`replaceChild(parentKey,batch)` | 完整 | ownership context 与 detached replacement 清晰 |
| `AP-07` Key traversal | `keys().forEach/fetchAll/findFirst/firstOrThrow` | 完整但抽象含混 | `*Keys` 像 collection，实际是 terminal-only traversal facade |
| `AP-08` full column traversal | `fieldValues().forEachX` | 完整但命名过强 | `*ColumnPipeline` 没有 stage，仅是 traversal |
| `AP-09` current-index column read | `fieldColumn().isPresent/getX/close` | 完整 | `ColumnView` 正确表达 live scoped borrow |
| `AP-10` snapshot gather | `IndexSnapshot` + one/more `ColumnView` | 完整且显式 | 不需要复制为新的 generic gather API |
| `AP-11..14` stages | `filter/skip/limit/sorted` | 完整 | 顺序组合、stable sort、deep-chain 当前已有 oracle |
| `AP-15` count/match | `count/anyMatch/noneMatch` | 完整 | `allMatch` 可由 `noneMatch(negated)` 表达，不构成已证需求 |
| `AP-16` borrow | `forEach(Consumer)` + callback row cursor | 完整但 Row 命名含混 | lifetime 正确，概念应改为 Cursor |
| `AP-17` first/best Index | 无 scalar Index terminal | 缺口 | hot path 需 `sorted(...).limit(1).rowIndexes()` 再 `indexAt(0)` |
| `AP-18` snapshot | `rowIndexes()` -> `IndexSnapshot` | 完整但名称错误 | 返回 snapshot，不是 rows，也不是 raw array |
| `AP-19` single materialize | `findFirst/firstOrThrow` + budget overload | 完整 | 明确 detached object，但名称需与 Index terminal区分 |
| `AP-20` bulk materialize | `fetchAll` + budget overload | 完整 | explicit expensive boundary |
| `AP-21` candidate update | `update(Updater)` | 完整 | staged failure-atomic mutation |
| `AP-22` candidate remove | `remove()` | 完整 | final candidate set + swap-remove |
| `AP-23` point mutation | key/current Index `mutate/delete` | primary/current完整 | secondary unique direct mutation缺失 |
| `AP-24` append | `Batch.add/addValues` + `addBatch` | 完整 | typed detached staging |
| `AP-25` replace | `replaceAll(batch)` | 完整 | all-or-nothing publish |
| `AP-26` clear | `clear()` | 完整 | capacity 可复用，Index 失效 |
| `AP-27` child replace | `replaceChildren(parentKey,batch)` | 完整 | owner-scoped replacement |

## 3. 当前 API family

### 3.1 Candidate family

```text
Table.rows() / Table.filter(...) / Table.sorted(...)
Exact selector findByX(...)
  -> *Rows
  -> filter | skip | limit | sorted
  -> count | anyMatch | noneMatch | forEach
   | findFirst | firstOrThrow | fetchAll | rowIndexes
   | update | remove
```

当前 family 的核心语义是合理的 one-shot CandidateAccess，但有三类产品问题：

1. Table forwarding 与显式 `rows()` 是两条等价入口；
2. `Rows/Row/MutableRow` 同时暗示 collection、record 和 live cursor；
3. exact group 与 exact unique 都叫 `findByX` 并返回同一种 shape，cardinality 信息丢失。

### 3.2 Point family

PrimaryKey 已经区分：

```text
containsKey          -> boolean
findRowIndex         -> -1/current Index
rowIndexOf           -> required current Index
find/fetch           -> detached materialization
mutate/delete        -> point mutation
```

能力完整，但 `findRowIndex/rowIndexOf` 把逻辑 element 和物理 Index 混入 Row 词族。SecondaryUnique 没有对应 family，迫使调用者建立 Pipeline、执行 terminal，甚至创建 snapshot/materialized object。

### 3.3 Column family

```text
fieldValues() -> *ColumnPipeline -> forEachX
fieldColumn() -> *ColumnView -> isPresent/getX/close
```

两者实际边界明确：前者是 full packed traversal，后者是 scoped random access。问题只在前者名称把 terminal-only facade 表述成 Pipeline。

### 3.4 Key family

`*Keys` 只有 terminal，没有 stage。它不需要与 Candidate Pipeline 共享实现或 shape；当前名称和可复用 facade 容易让用户把它理解为 live collection view。

### 3.5 Bulk 与 ownership

Batch、add/replace/clear 和 child access 已经自然表达独立路径。它们不应为了“统一 DSL”并入 Candidate Pipeline。

## 4. 重复与缺口

### 4.1 等价入口

Table 同时暴露 `rows().filter(...)` 和 `filter(...)`，也同时暴露 `rows().count()` 与 `count()` 等 forwarding。它增加 generated surface/code size，并让文档无法指出唯一 canonical 起点。

目标设计需要二选一：

- 以显式 Candidate source 为唯一组合入口；或
- 明确定义 Table shortcut 只是语法糖，并为其承担长期 compatibility/code-size 成本。

不能把两者都称为 canonical API。

### 4.2 Unique cardinality 丢失

`@SomaUnique` 已在写路径维护唯一性，却在读路径返回 `*Rows`。这会导致：

- `0..1` 语义无法从 signature 读出；
- point exists/index/materialize/mutate/delete 需要绕经 CandidateAccess；
- source/plan/cursor/snapshot 分配可能出现在本可直接 probe 的路径；
- `@SomaIndex` 与 `@SomaUnique` 的产品价值差异只剩写时冲突。

SecondaryUnique 应拥有 point-shaped canonical API。若保留 Candidate composition，应使用显式 bridge，而不是让 group shape 继续充当唯一入口。

### 4.3 scalar Index terminal 缺失

当前有 primary-key locate Index，但 CandidateAccess 没有 first/best current Index terminal。FJSP hot path 因而需要：

```text
findByMachine(...)
  .filter(...)
  .sorted(...)
  .limit(1)
  .rowIndexes()
```

然后检查 snapshot size 并读取 `indexAt(0)`。这是 API 缺口，不应只靠优化 `IndexSnapshot` allocation 掩盖。

### 4.4 Index 词族分裂

当前同一 current Index 概念分散在：

- `findRowIndex(key)`；
- `rowIndexOf(key)`；
- `fetchAt(index)`；
- `mutateAt(index)`；
- `rowIndexes()`；
- `IndexSnapshot.indexAt(position)`。

`At` 适合表达“以 current Index 访问”，`find/require Index` 适合表达 locate terminal；`rowIndex/rowIndexes` 没有额外语义，应退出 canonical vocabulary。

### 4.5 traversal 名称与 lifecycle

`*ColumnPipeline` 和 `*Keys` 没有 stage，当前 facade 可重复 terminal 调用。产品上更自然的模型是每次方法调用获得一个同步 traversal operation，并只消费一次。这样与 live collection view 区分，也允许一致的 typed lifecycle failure。

是否 one-shot 需要同时验证 allocation 和 compatibility；不能仅为词汇整齐引入共享 generic base 或额外对象层。

## 5. 不应新增的 API

当前覆盖分析不支持以下扩展：

- generic `map/flatMap/collect/reduce`；
- range lookup 或 maintained order；
- 自动 snapshot-to-column gather object；
- public top-k family；
- 为每个 primitive 类型复制大组 reduction；
- application event/priority queue；
- stable physical Index handle。

它们要么可由现有明确路径表达，要么会破坏 columnar specialization、identity 或产品边界。

## 6. API 决策输入

覆盖矩阵为后续裁决提供以下约束：

1. Access Model 是 umbrella；Candidate Pipeline 只能命名其自身；
2. Candidate source、Point、Traversal、View、Batch 必须保持不同 shape；
3. SecondaryUnique 需要 point API，并明确 Candidate bridge 是否保留；
4. Candidate first/best-one 需要 scalar Index terminal；
5. `rowIndexes()` 应表达 `IndexSnapshot`；
6. Table forwarding 必须有唯一 canonical 规则；
7. Row 只保留在 schema-backed row/materialized row 等确实表示 logical record 的位置；
8. 所有 rename 必须 clean cutover，不保留长期 alias。

最终名称、migration 和 identity 由 Stage 1 决策文档拥有；本矩阵不把候选写成当前能力。
