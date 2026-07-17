# Schema、Generated API 与顺序语义设计

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 目标

本文定义拟议模型的用户心智边界：什么决定 table kind，key/unique/index 分别意味着什么，Row Pipeline 如何把 source sequence 逐步变换，以及在不再支持 maintained order 后，`firstOrThrow()`、`limit(n)`、`fetchAll()`、materialization 和外部 priority structure 应如何解释。

本文不选择 runtime hash array 的具体字段，也不决定 public stats/result 的最终 Java signature。

## 2. Table kind 保持不变

Table 仍只分为：

| Table kind | 定义 | 主要 public 能力 | 不代表 |
|---|---|---|---|
| keyed table | 有且只有一个 `@SomaKey` primary identity | `containsKey/find/fetch/mutate/delete/keys`、whole-table `Map<K,R>` | Sparse Set、stable physical position、sorted map |
| dense table | 没有 `@SomaKey` | `fetchAt/mutateAt`、packed scan、whole-table `List<R>` | stable insertion sequence、缩水版 table |

以下事实与 table kind 正交：

- root/parent/child ownership；
- input/working/result data role；
- 是否声明 `@SomaUnique` / `@SomaIndex`；
- 是否适合作为 entity、lookup、frontier、workspace 或 export buffer。

Dense table 可以声明 secondary unique exact access；这不会把它变成 keyed table。它仍没有 primary identity、key pipeline 或 keyed whole-table `Map`。

## 3. Annotation surface

### 3.1 拟保留的 access annotation

| Annotation | Logical meaning | Cardinality | Generated access |
|---|---|---:|---|
| `@SomaKey` | primary unique identity | 0/1 | direct keyed API + optional Key Pipeline |
| `@SomaUnique` | secondary unique exact access | 0/1 | named typed Row Pipeline source |
| `@SomaIndex` | secondary non-unique exact access | 0..K | named typed Row Pipeline source |

共同规则：

- selector/hash 只是定位加速，collision 后必须执行 generated full canonical equality；
- query 参数、write、default 和 mutation 继续遵守同一 floating canonicalization；
- selector 不能穿透 child ownership edge；
- selector 的合法 leaf type breadth 本专题不扩张，待 `O-06` 决策；
- `@SomaUnique` / `@SomaIndex` 只定义 exact access，不定义 order 或 range。

### 3.2 拟删除的 annotation

建议一次性删除：

```text
@SomaOrder
@SomaOrders
@SomaSort
SomaDirection            // 若 inventory 证明只服务 maintained order
```

删除后的规则：

- schema 不声明 business order；
- processor 不生成 `byXxx()` maintained-order source；
- normalized model/schema resource 不再包含 order declaration；
- runtime 不维护 ordered permutation；
- examples 中所有 `byAvailableTime()` / `byEventTime()` / `byPosition()` 等调用必须迁移为 explicit `.sorted(...)`、packed scan、exact index + `.sorted(...)`，或 application-owned heap。

Annotation 不得保留为“能编译但被忽略”的空壳。静默忽略会让 schema 看似声明了顺序，实际没有执行保证，是比 compile failure 更危险的 API。

## 4. Key、unique 与 index 不合并

三者可以复用同一种 primitive hash substrate，但 logical contract 必须分离。

### 4.1 `@SomaKey`

- 决定 table kind；
- 是 detached Map materialization 的 key；
- 生成 direct CRUD 和 Key Pipeline；
- identity field 不生成 setter；
- identity change 使用 delete + insert；
- duplicate 是 `duplicate_key`；missing direct access 是 `missing_key`。

### 4.2 `@SomaUnique`

- 不决定 table kind；
- field/selector 可以在受控 mutation 中修改；
- update 后仍必须满足 live final state uniqueness；
- duplicate 是 `unique_constraint_violation`；
- named source 返回 0/1 row sequence，不获得 primary key CRUD 语义。

### 4.3 `@SomaIndex`

- 不决定 table kind；
- 一个 exact selector value 可以对应多行；
- named source 只承诺返回所有且仅返回 equal selector rows；
- group 内枚举顺序不是 contract；
- mutation、swap-remove、rehash 可以改变后续枚举顺序，而不改变 logical membership。

把 `@SomaKey` 简化成普通 `index + unique` 会丢失 identity immutability、direct API、Map materialization 和 error semantics，因此不采用。

## 5. Range 与 ordering 边界

### 5.1 Range lookup

V1 不提供 range access structure。以下条件使用 columnar scan/filter：

```java
rows.filter(r -> r.readyTime() >= from && r.readyTime() < to)
```

如果前面已有 exact source，可以在较小 group 上继续 filter：

```java
machineCandidates.findByMachine(machineId)
    .filter(c -> c.effectiveReadyTime() <= now)
```

不引入 B+ tree、skip list、tree map 或 ordered hash bucket。未来只有在真实 range-heavy Access Pattern Card 和 benchmark 证明 full/group scan 不合适时，才能作为独立设计专题重新讨论。

### 5.2 Dynamic business order

业务选择顺序由 pipeline 显式声明：

```java
machineCandidates.findByMachine(machineId)
    .filter(c -> c.indicatorReady())
    .sorted((a, b) -> {
        int byFcfs = Long.compare(a.fcfsValue(), b.fcfsValue());
        if (byFcfs != 0) return byFcfs;
        int bySpt = Long.compare(a.sptValue(), b.sptValue());
        if (bySpt != 0) return bySpt;
        int byJob = Long.compare(a.candidateKeyOperationKeyJobIdValue(),
            b.candidateKeyOperationKeyJobIdValue());
        if (byJob != 0) return byJob;
        return Long.compare(a.candidateKeyOperationKeyOperationIdValue(),
            b.candidateKeyOperationKeyOperationIdValue());
    })
    .firstOrThrow();
```

Comparator 必须包含业务需要的完整 tie-break。Stable sort 只能保留本次 input source 的 tie order；如果 source 本身在 mutation 后可改变，缺少 tie-break 的 comparator 不能提供跨 mutation 确定性。

### 5.3 Application-owned priority structure

持续按 priority 取最小值的 event queue、ready queue 或 machine queue，优先使用 application-owned heap。边界规则：

- heap element 保存 stable key/id 和必要 version，不保存 packed Index；
- Table 或 heap 只能有一个 authoritative priority fact，另一方必须是可重建 derived structure；
- 使用 lazy invalidation 时，pop 后按 stable key/version 验证当前事实；
- Table structural mutation 不得让 heap 中保存的 locator 静默指向另一 row；
- heap correctness、retry 和 stale cleanup 由 application owner 负责。

## 6. Source sequence 模型

Row Pipeline 的 source 只产生本次 terminal 的初始 Index sequence：

| Source | Candidate domain | 本次 sequence | 稳定顺序承诺 |
|---|---:|---|---|
| table/default `rows()` | `N=size` | `0,1,...,size-1` | 只对本次 stable state；mutation 后不承诺 |
| primary key exact | 0/1 | matched current Index | cardinality 至多一，顺序无歧义 |
| `@SomaUnique` exact | 0/1 | matched current Index | cardinality 至多一，顺序无歧义 |
| `@SomaIndex` exact | 0..K | GroupedExactIndex 当前 group traversal | 不承诺 physical/insertion/business order |
| child default | child `size` | child 当前 physical Index sequence | 不承诺跨 child mutation 稳定 |

Pipeline 在 construction 时不扫描、不冻结 Index。Terminal 开始时读取 current table/access facts；这保持现有 lazy/current-state 语义。

## 7. Intermediate operation 的序列变换

Intermediate 按链式声明顺序执行：

| Operation | Input | Output | Order consequence |
|---|---|---|---|
| `filter(predicate)` | source/current stage sequence | 只保留 predicate=true | 保留 input relative order |
| `skip(n)` | current sequence | 丢弃前 `min(n,size)` 个 | 保留剩余 relative order |
| `limit(n)` | current sequence | 只保留前 `min(n,size)` 个 | 取决于当前 sequence 是否有业务顺序 |
| `sorted(comparator)` | current sequence | comparator order | equal ties 保留该次 input relative order |

多次 sort、sort 后 filter、filter 后 sort 都必须按链式顺序解释。Internal top-k/arg-min 只在能证明与完整序列变换完全等价时使用。

## 8. Terminal 顺序语义

### 8.1 统一规则

未显式排序的 terminal 服从本次 source/current-stage sequence：

| Terminal | Sequence dependency |
|---|---|
| `count()` | 只依赖 membership；仍按 sequence evaluation predicate |
| `anyMatch/noneMatch` | 按 sequence short-circuit |
| `forEach` | 按 sequence callback |
| `findFirst/firstOrThrow` | materialize sequence 第一个 row |
| `fetchAll` | 按 sequence materialize `List<R>` |
| `rowIndexes` | 按 sequence复制 current Index |
| `update` | 按 sequence调用 updater；最终 publish 仍是一个 mutation 单元 |
| `remove` | 按 sequence计算 candidate set；commit 不保留 sequence/physical order |

因此，“`firstOrThrow()`、`limit(n)`、未排序的 `fetchAll()` 只基于当时物理顺序”需要更精确地表述为：

> 它们只基于当时的 source sequence。default/child-default source 的 sequence 是当前 physical Index 顺序；`@SomaIndex` source 的 sequence 是当前 group traversal，不能称为 physical 或 business order。

### 8.2 常见后果

- `table.firstOrThrow()`：取当前 Index `0` 的 row；swap-remove 后可能变成另一 row；
- `table.limit(10).fetchAll()`：取当前 physical 前十行，不是最早插入十行；
- `findByMachine(machine).firstOrThrow()`：任取该 machine group 当前第一个 member，不应作为 dispatch rule；
- `findByMachine(machine).sorted(totalComparator).firstOrThrow()`：按 comparator 选择确定候选；
- `findByUnique(value).firstOrThrow()`：最多一行，不受 group order 影响；
- `filter(...).remove()`：predicate evaluation 有 source sequence，但删除后 survivors 的 physical order不保留。

### 8.3 Callback side effect

同一次 terminal 内，callback invocation order 仍可观察。Application 若通过 callback 修改另一张 table或外部计数器，就会观察当前 source sequence。需要可重复的 cross-row side effect order时，必须先显式 total sort；否则只能把 callback当作对集合成员的独立操作。

## 9. FJSP 链路的概念与机械执行

假设：

```text
MachineCandidate table rows N = 100,000
findByMachine exact group K = 10,000
filter indicatorReady matched M = 1,000
```

用户心智模型：

```text
100,000 table Index
  -> 10,000 machine group Index
  -> 1,000 ready Index
  -> comparator order
  -> first
```

建议机械执行：

| 步骤 | 执行方式 | 是否创建完整 Index 副本 |
|---|---|---:|
| `findByMachine` | GroupedExactIndex 直接流式遍历 K 个 row link | 否 |
| `filter` | cursor 对 K 个候选融合测试，只让 M 个通过 | 否 |
| `sorted(...).firstOrThrow` | 对 M 个候选执行 stable arg-min | 否；只保留 best Index |
| `firstOrThrow` | 只 materialize chosen row | 是显式单行 allocation boundary |

如果 terminal 是 `sorted(...).fetchAll()`，才需要把 M 个 Index 写入 reusable candidate `IndexBuffer` 并排序。如果是 `update` / `remove`，需要冻结 matched Index set，以保证 mutation 不改变本次 candidate membership。

因此 IndexSet/Index sequence 是正确抽象，但不是要求每一步都物化一份数组。

## 10. Materialization 语义

### 10.1 Dense whole table 与 dense child

`List<R>` 只表达 detached multi-row container shape：

- 元素顺序是本次 current physical traversal；
- swap-remove 后再次 materialize，List order 可以改变；
- `position`、`sequenceNo`、`time` 等业务字段不会自动决定 List order；
- 需要业务顺序时，application 使用 explicit sorted child pipeline/export，或 materialize 后按业务字段排序；
- parent recursive materialization 不隐式执行业务 comparator。

这意味着 `@SomaChild List<R>` 表达 dense ownership/cardinality，不自动表达有序 aggregate。若某个业务对象要求“child List 本身就是稳定 sequence contract”，本专题 baseline 只能要求显式 position + boundary sort；不能从 physical layout 推导。

### 10.2 Keyed whole table 与 keyed child

`Map<K,R>` 表达 primary identity lookup：

- 不承诺 key order或iteration business order；
- map entry membership由 `@SomaKey` 决定；
- 需要ordered export时应用层按 key/field显式排序为List/DTO。

### 10.3 Row Pipeline

Row Pipeline `fetchAll()` 始终返回 `List<R>`，按本次 pipeline sequence materialize。只有显式 `.sorted(totalComparator)` 才建立业务顺序。

## 11. Generated public API 迁移建议

### 11.1 保持不变

- keyed direct API；
- dense `fetchAt/mutateAt`；
- `findByXxx(...)` exact index/unique source；
- `rows/filter/skip/limit/sorted`；
- read/mutation terminals；
- Key/Column Pipeline、ColumnView、child facade；
- schema object/List/Map materialization shape；
- `@SomaKey` identity immutability。

### 11.2 删除

- order annotation types；
- generated `byXxx(...)` maintained-order source；
- order-specific normalized metadata、diagnostics、golden和runtime plan；
- 任何让用户误以为table自动维持business order的API。

### 11.3 待决

- `rowIndexes()` 是否迁移为epoch-bearing result；
- operation result中的sidecar字段；
- 是否增加allocation-free first Index/cursor terminal；
- exact-index public stats最小集合。

在这些待决项关闭前，不应编写临时 public replacement。

## 12. 易用性原则

- exact lookup 继续由 generated typed method 表达，用户不接触 hash/bucket/group id；
- `.sorted(...)` 的显式性是有意的：它让业务 comparator、tie-break 和成本出现在调用处；
- specialized persistent priority使用外部heap，不让Table承担不适合的抽象；
- `IndexBuffer` 不进入 public API，避免用户保存可失效 locator；
- documentation/examples 必须在任何使用 `first/limit/fetchAll` 的位置说明其 source sequence；
- examples 如果需要可重复结果，必须使用 total comparator，而不是依赖当前 physical/index group顺序。

## 13. 设计审查清单

- [ ] 是否接受 dense child `List` 只代表 current physical snapshot，而不是稳定序列？
- [ ] 是否接受 non-unique exact index source 的 group 内顺序完全不承诺？
- [ ] 是否接受 `first/limit/fetchAll/update callback` 在未排序时只服从当次 source sequence？
- [ ] 是否接受一次性删除 order annotation/source，而非 ignored/deprecated 空壳？
- [ ] 是否确认 range/type breadth 不在本专题扩张？
- [ ] 是否需要在本轮增加 allocation-free first terminal，还是留为独立 API 议题？
