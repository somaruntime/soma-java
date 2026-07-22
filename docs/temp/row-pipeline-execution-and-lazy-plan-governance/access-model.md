# SOMA Access Model

类型：Temporary

状态：Stage 1 候选冻结

Owner：SOMA Java Access Model 产品语义

事实范围：SOMA 基本 Access Pattern、组合代数、合法性、序列与成本模型

非事实范围：当前 generated 方法名、最终 public rename、具体 generator 类和已实现的性能收益

输入：[Stage 1 章程](stage-1-charter.md)、正式 Table/Access、Schema/API、Lifecycle、Correctness 与 Performance Design

最后审查日期：2026-07-22

## 1. 定位

SOMA Access Model 是用户与 packed columnar table 交互的完整语义体系。它回答五个问题：

1. 从什么 access path 定位数据；
2. 得到一个 current Index、一个 candidate Index sequence，还是一列值；
3. 可以继续组合哪些 operation；
4. terminal 返回 borrow、Index、detached materialization，还是提交 mutation；
5. 该路径的 validity、sequence、failure、allocation 和维护成本是什么。

Pipeline 只负责 **CandidateAccess** 的组合，不代表整个 SOMA Access Model。

## 2. 基础对象

| 对象 | 语义 | 稳定性 |
|---|---|---|
| Table | packed columns、access structures 与 lifecycle 的 live ownership unit | 实例 active 期间存在 |
| current Index | 当前 `[0,size)` 物理位置 | 任意 mutation/lifecycle 后失效 |
| Key | keyed table 的 logical primary identity | 跨 packed relocation 稳定 |
| Exact value | secondary unique/group access 的 lookup value | access identity，不是 row identity |
| Candidate sequence | 单次 candidate operation 中有顺序的 current Index 序列 | operation 内有效 |
| IndexSnapshot | candidate Index 序列的 detached copy | 数值 detached，语义 currentness 不稳定 |
| Cursor | callback-scoped typed live access | 仅 callback 调用期间有效 |
| ColumnView | scoped typed live-column borrow | pin/close/lifecycle 约束内有效 |
| Materialized object | detached schema carrier/object graph | 与 live storage 脱离 |
| Batch | detached construction/import staging | 发布前由 caller 持有，发布后不成为 live storage |

Candidate sequence 的初始顺序由 source 决定：packed source 使用执行时物理顺序；exact group 使用当前 group traversal order；owned child 使用该 child table 的对应 source order。除显式 dynamic sort 外，这些顺序都不是跨 mutation 的业务顺序。

## 3. 组合代数

### 3.1 CandidateAccess

```text
CandidateAccess
  := CandidateSource Stage* CandidateTerminal

CandidateSource
  := Packed | ExactGroup | ExactUnique | OwnedChild

Stage
  := Filter | Skip | Limit | Sort

CandidateTerminal
  := Probe | Borrow | CurrentIndex | IndexSnapshot
   | Materialize | Update | Remove
```

其中：

- `Probe`：count、any/none match 等不返回候选本体的观察；
- `Borrow`：callback-scoped traversal；
- `CurrentIndex`：返回零或一个 current Index，用于 first/best-one；
- `IndexSnapshot`：复制全部最终 candidate Index；
- `Materialize`：零/一项或批量 detached object；
- `Update/Remove`：对最终 candidate set 提交 mutation。

`ExactUnique` 可以作为 CandidateSource 进入组合，但 canonical point access 不应因此被 group-shaped API 掩盖。它的 candidate cardinality 上界始终为一。

### 3.2 PointAccess

```text
PointAccess
  := CurrentIndex | PrimaryKey | SecondaryUnique
  -> Exists | LocateIndex | ReadColumn | Materialize | Mutate
```

PointAccess 不需要建立通用 Pipeline。PrimaryKey 和 SecondaryUnique 先完成 collision-safe probe 与 full equality；CurrentIndex 先验证当前范围/lifecycle。`ReadColumn` 通过 typed ColumnView 完成，`Materialize` 明确承担 detached object 成本。

### 3.3 ColumnAccess

```text
ColumnAccess
  := FullColumnTraversal
   | CurrentIndexRead
   | IndexSnapshotGather
```

- FullColumnTraversal：按 packed Index 遍历一个 typed leaf；
- CurrentIndexRead：在 ColumnView 中读取一个 current Index；
- IndexSnapshotGather：caller 在同步只读批次中遍历 snapshot，并从一个或多个 ColumnView gather。

ColumnAccess 不隐式获得 cross-column filter/sort 能力。涉及多列候选组合时进入 CandidateAccess。

### 3.4 KeyAccess

```text
KeyAccess := KeyTraversal -> Borrow | Materialize
```

Key traversal 只存在于 keyed table。它遍历 logical primary keys，不返回 live row，不允许 filter/sort/update/remove。

### 3.5 BulkAccess

```text
BulkAccess
  := Batch -> Append | Replace
   | Clear
   | ParentPoint -> ReplaceChildren
```

BulkAccess 是 stage/validate/publish boundary，不与 Candidate Stage 组合。`replaceChildren` 先通过 parent Key/current Index 定位唯一 owner，再以 detached child Batch 原子切换 owned subtree。

## 4. Access Pattern Catalog

### 4.1 Source 与 direct access

| ID | Pattern | Input cardinality | Result cardinality | 核心语义 |
|---|---|---:|---:|---|
| `AP-01` | packed full-table candidate source | `N` | `0..N` | 从执行时 `[0,size)` 产生候选 |
| `AP-02` | current Index point | `1` | `0..1` | 当前物理位置，不是 identity |
| `AP-03` | primary-key point | `1` | `0..1` | stable key → current Index |
| `AP-04` | secondary-unique point | `1` | `0..1` | exact value → at most one current Index |
| `AP-05` | secondary exact-group source | `1` | `0..M` | exact value → current candidate group |
| `AP-06` | owner-scoped child | `1 owner` | `1 child table` | ownership navigation，不是 join |

### 4.2 Traversal 与 projection

| ID | Pattern | Output | 核心语义 |
|---|---|---|---|
| `AP-07` | Key traversal | callback/materialized keys | keyed table logical identities |
| `AP-08` | full single-column traversal | primitive/reference callback | 只触碰目标 leaf column/presence |
| `AP-09` | ColumnView current-index read | scalar value/absence | scoped live typed read |
| `AP-10` | IndexSnapshot sparse gather | application projection | snapshot Index + one/more ColumnView |

### 4.3 Candidate stages

| ID | Pattern | Sequence effect | Work set |
|---|---|---|---|
| `AP-11` | filter | stable subsequence | current candidate count `M` |
| `AP-12` | skip | drops first `min(k,M)` | upstream sequence |
| `AP-13` | limit | keeps first `min(k,M)` | upstream sequence，可短路 |
| `AP-14` | dynamic sort | comparator order，ties 保持 upstream order | current candidates only |

### 4.4 Candidate terminals

| ID | Pattern | Result | Allocation boundary |
|---|---|---|---|
| `AP-15` | count/match probe | scalar | 不需要 candidate result copy |
| `AP-16` | borrowed traversal | callback-scoped cursor | 不产生 per-element materialization |
| `AP-17` | first/best current Index | `-1`/Index 或 required Index | 不应先构造 multi-index snapshot |
| `AP-18` | IndexSnapshot | detached Index sequence | `O(M)` explicit copy；empty/single 可 specialized |
| `AP-19` | single materialization | Optional/required schema carrier | `O(object graph)` + budget |
| `AP-20` | bulk materialization | detached List/Map/object graph | `O(M × graph)` + budget |
| `AP-21` | candidate update | UpdateResult | staged candidate mutation + index maintenance |
| `AP-22` | candidate remove | RemoveResult | candidate selection + swap-remove/relocation |

### 4.5 Mutation 与 bulk

| ID | Pattern | 核心语义 |
|---|---|
| `AP-23` | point mutation/delete | PrimaryKey、SecondaryUnique 或 CurrentIndex 定位后单项提交 |
| `AP-24` | addBatch | detached Batch append，validation/preflight 后 publish |
| `AP-25` | replaceAll | detached Batch 全量 replacement，all-or-nothing |
| `AP-26` | clear | 清空 live facts，可保留已准入 capacity |
| `AP-27` | replaceChildren | owner-scoped detached child replacement |

## 5. 合法组合

### 5.1 Candidate stage 规则

- Stage 严格按声明顺序执行；后续 stage 只看到前一 stage 的输出；
- Filter 不得被重排、合并或跨 Skip/Limit/Sort 移动；
- Skip/Limit 以进入该 stage 的 sequence 为准；
- Sort 只排序当前 candidate set，不移动 table columns；
- compare 为零时保留进入 Sort 前的相对顺序；需要业务确定性时 comparator 必须覆盖全部业务 tie-break；
- 多个 Sort 按顺序各自建立新的 sequence，不静默删除前一个 Sort；
- terminal 必须消费整个 one-shot operation；失败后也不能重试同一 handle。

### 5.2 Source 与 terminal 规则

| Source | 合法 terminal | 特殊约束 |
|---|---|---|
| Packed | 全部 CandidateTerminal | 未排序 first/limit 基于当前物理顺序 |
| ExactGroup | 全部 CandidateTerminal | 只遍历 group，不扩展回全表 |
| ExactUnique | 全部 CandidateTerminal | cardinality 上界为一；Sort 是语义合法但物理可 trivialize |
| OwnedChild | 先绑定 child table，再遵循其 packed/exact 规则 | parent/child lifecycle 与 pin 必须有效 |

PointAccess、ColumnAccess、KeyAccess 和 BulkAccess 不接受 Candidate Stage。若调用者需要 stage，必须显式进入 CandidateAccess，而不是在 point/column facade 上逐渐长成第二套 Pipeline。

### 5.3 Snapshot gather 规则

`IndexSnapshotGather` 的唯一合法批次是：

```text
obtain snapshot
  -> open one or more ColumnView from the same source table
  -> synchronously read snapshot indexes
  -> close views
  -> discard snapshot
```

期间不得 mutation、release、replace 或切换 ownership。`requireCurrent(snapshot)` 是可选调试/边界检查，不进入每次 hot read。跨 operation 保存引用必须保存 Key 或 application-owned identity。

### 5.4 Mutation 规则

- Update callback 先写 staged scratch，全部 callback、constraint 和 resource validation 成功后一次 publish；
- Remove 在冻结最终 candidate set 后执行 swap-remove，并同步修复 primary/exact/child relocation；
- Point mutation 与 candidate mutation 不可在同一 active operation 内嵌套；
- mutation 使既有 Index/IndexSnapshot 失效；
- SOMA 只承诺单 table/ownership aggregate operation 原子性，不承诺跨多次调用或跨 root transaction。

## 6. 成本模型

设 `N` 为 table size、`M` 为进入当前 stage/terminal 的 candidate count、`C` 为触碰列数、`G` 为 exact group size、`P` 为 hash probe/full-equality 成本。

| Pattern | 时间主项 | transient/retained 主项 |
|---|---|---|
| packed scan | `O(N × touched columns)` | candidate scratch only when terminal/stage needs it |
| current Index | `O(1)` bounds/lifecycle | 无 materialization 时常量 |
| primary/unique lookup | `O(P)` | maintained locator/index retained cost |
| exact group | `O(P + G)` | group/link retained cost |
| filter | `O(M × predicate touched columns)` | fused path可无新增 candidate array |
| skip/limit | `O(min(M, boundary))` | counters |
| full sort | `O(M log M)` comparisons | `O(M)` reusable primitive scratch |
| best-one after sort | `O(M)` comparisons | `O(1)`/single-index scratch |
| snapshot | `O(M)` copy | detached `O(M)` allocation |
| materialize | `O(M × object graph)` | detached graph + budget |
| ColumnView gather | `O(M × C)` | views + application projection |
| update | candidate work + validation + maintained access-path delta | staged changed fields/candidates |
| remove | candidate work + relocation/index repair | primitive candidate scratch |
| append/replace | batch columns + locator/exact/ownership maintenance | Batch + growth/staging |

这张表只规定比较维度。具体常数、分支、allocation 和 cache 行为由 [Stage 1 Evidence](stage-1-evidence.md)测量。

## 7. 推导原则

Access Model 对后续设计施加以下约束：

- `@SomaUnique` 必须首先表达 PointAccess 的 `0..1` cardinality；若提供 CandidateAccess bridge，名称和成本必须显式；
- first/best-one 需要 scalar current-Index terminal，不能要求先构造 `IndexSnapshot`；
- single-column traversal 不是完整 Pipeline；名称不得暗示 filter/sort/materialize family；
- Key traversal、Column traversal 和 Candidate Pipeline 可以共享 lifecycle/error vocabulary，但不共享泛型 runtime；
- source specialization、stage fusion 和 terminal specialization 必须以 Pattern 成本为依据；
- Snapshot、materialization、callback/application allocation 必须分 lane 计量；
- maintained index 的 read benefit 与 write/retained cost必须成对评估。

## 8. Stage 1 冻结结论

本 Catalog 已覆盖当前正式 Design、四个 executable scenario 和 generated surface 中已知的基本访问模式。后续新增 Pattern 必须说明它不能由现有 algebra 自然表达；新增某条 API 链本身不构成新增 Pattern。

本文件冻结 Access Model，不冻结 public 方法名。命名和 generated surface 由 API 覆盖证据与 Stage 1 决策单独裁决。
