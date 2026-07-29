# Access Model 与 Candidate Scan 设计

类型：Design

状态：正式

Owner：SOMA access semantics 与 Candidate Scan

设计层次：`D2` 能力设计

主要关注点：访问族、组合代数、Candidate Scan、terminal 与成本边界

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：Access Pattern、合法组合、sequence、one-shot lifecycle、Candidate Scan 执行约束和成本模型

非事实范围：packed/exact 数据结构、精确 generated signature、具体 generator 类布局、benchmark 数值和 application 算法

最后审查日期：2026-07-29

SOMA Access Model 是使用者与 packed columnar Table 交互的完整语义体系。Pipeline 只负责 CandidateAccess；Point、Column、Key、Bulk 和 Ownership 路径不为追求 API 对称而绕入同一个 planner。

Candidate Scan 同时是 [Transformation Model](transformation-model.md) 中 `Candidate` Shape 的线性、lazy、one-shot 特化子代数。它可以复用 shared semantics 和 reference oracle，但继续拥有 compact generated plan、source/terminal specialization 与最低抽象税路径；不能为了 graph 形式统一而改走 generic object executor。

## 1. Access family

```text
SomaTable
  ├─ PointAccess       已知 current Index、primary key 或 secondary unique
  ├─ CandidateAccess   从 Packed/Exact source 逐步筛选、排序并终结
  ├─ ColumnAccess      遍历或按 current Index 读取一个 typed leaf
  ├─ KeyAccess         遍历或物化 stable logical keys
  ├─ BulkAccess        Batch append/replace、clear
  └─ Ownership         定位 child Table 或替换 owned child facts
```

能力完整表示每个重要 Access Pattern 都有唯一、自然的 canonical path，不表示每个 facade 都拥有相同的方法集合。

## 2. 基础对象

| 对象 | 语义 | Validity |
|---|---|---|
| Table | packed columns、access structures 与 lifecycle 的 live unit | active lifecycle |
| current Index | 当前 `[0,size)` 物理位置 | 来源 Table 任意 mutation/lifecycle 后失效 |
| Key | keyed table 的 primary logical identity | 跨 packed relocation 稳定 |
| Exact value | secondary unique/group 的 lookup value | access identity，不是 row identity |
| Candidate sequence | 单次 operation 内有顺序的 current Index 序列 | operation 内有效 |
| Cursor / UpdateCursor | callback-scoped typed live access | 仅 callback 调用期间有效 |
| IndexSnapshot | Candidate Index 序列的 detached copy | 数值 detached，语义 currentness 不稳定 |
| ColumnView | scoped typed live-column borrow | pin/close/lifecycle 约束内有效 |
| Materialized Object | detached schema carrier/object graph | 与 live storage 脱离 |
| Batch | detached construction/import staging | publish 后不成为 live storage |

Source 决定初始 sequence：Packed 使用执行时物理顺序，Exact 使用当前 group traversal order，child 使用 child Table 的对应 source order。除显式 `sorted` 外，这些顺序都不是跨 mutation 的业务顺序。

Required String value 可以作为 Primary/Unique/Exact source component，并使用 Java
String authoritative value equality；optional absence 不进入 selector。reference
identity、hash/fingerprint 或 intern 状态都不构成 access equality。

## 3. 组合代数

### 3.1 CandidateAccess

```text
CandidateAccess
  := CandidateSource Stage* CandidateTerminal

CandidateSource
  := Packed | ExactGroup | ExactUniqueBridge | OwnedChild

Stage
  := Filter | Skip | Limit | Sort

CandidateTerminal
  := Probe | Borrow | CurrentIndex | IndexSnapshot
   | Materialize | Update | Remove
```

- Probe：`count`、`anyMatch`、`noneMatch`；
- Borrow：callback-scoped `forEach`；
- CurrentIndex：`findIndex`、`requireIndex`；
- IndexSnapshot：复制全部最终 Candidate Index；
- Materialize：`findFirst`、`firstOrThrow`、`fetchAll`；
- Update/Remove：对最终 candidate set 提交 mutation。

`ExactUniqueBridge` 的 cardinality 上界为一，但 canonical unique access 首先属于 PointAccess；只有调用者确实需要 stage 时才进入 CandidateAccess。

### 3.2 独立访问路径

```text
PointAccess
  := CurrentIndex | PrimaryKey | SecondaryUnique
  -> Exists | LocateIndex | ReadColumn | Materialize | Mutate

ColumnAccess
  := FullColumnTraversal | CurrentIndexRead | IndexSnapshotGather

KeyAccess
  := KeyTraversal -> Borrow | Materialize

BulkAccess
  := Batch -> Append | Replace
   | Clear
   | ParentPoint -> ReplaceChildren
```

PointAccess 不建立通用 Pipeline。ColumnAccess 不隐式获得 cross-column filter/sort；KeyAccess 不允许 element mutation；BulkAccess 是 stage/validate/publish boundary，不与 Candidate Stage 组合。

## 4. Generated API 投影

- Table 本身就是 Packed source；不生成第二个等价的命名根入口；
- Table 直接提供 Packed terminal，以及从第一个 stage 开始的 `filter/skip/limit/sorted`；
- `@SomaIndex` 生成 `scanByX(...)` exact-group source；
- `@SomaUnique` 生成 `containsByX/findIndexByX/requireIndexByX/findByX/fetchByX/mutateByX/deleteByX` point family，并在需要 stage 时提供 `scanByX(...)` bridge；
- primary key 使用 `containsKey/findIndex/requireIndex/find/fetch/mutate/delete`；
- Candidate handle、read callback 和 update callback 分别采用 `*Scan`、`*Cursor`、`*UpdateCursor`；
- 单列和 key 路径分别采用 typed `*ColumnTraversal` 与 `*KeyTraversal`；
- 批量 Index 结果只称 `indexSnapshot()`，不伪装成 row collection。

精确 overload、参数顺序和按 schema 生成的名称由代码、golden 与 external consumer 拥有当前事实。Design 只拥有上述语法和 cardinality。

## 5. Stage 与 sequence

- Stage 严格按声明顺序执行；后续 stage 只看到前一 stage 的输出；
- Filter 保留通过项的 upstream 相对顺序，不得跨 Skip/Limit/Sort 重排；
- Skip/Limit 以进入该 stage 的 sequence 为准，并允许 cooperative short-circuit；
- Sort 只排序当前 candidate set，不移动 Table columns；compare 为零时保持 upstream 相对顺序；
- 多个 Sort 依次建立新 sequence，不静默删除前一个 Sort；
- 需要业务确定性时，application comparator 必须覆盖全部 tie-break；
- 未显式 Sort 的 first/limit/snapshot/materialization 只反映当前 source sequence。

Exact source 不能扩展回全表。Sort 即使对结果 cardinality 无影响也不能因优化而跳过 comparator failure 语义；只有在语义等价被证明时才允许 terminal specialization。

## 6. Lazy、one-shot 与 terminal boundary

Candidate Scan 只惰性记录 source 与有序 stage，不在 intermediate 时扫描 Table、复制 Index sequence 或物化 schema object。Terminal 在执行时绑定 current source，完成同步 operation 后 consumed。

- Intermediate 成功后，旧 handle alias 立即 consumed；一条 handle 不能分叉；
- intermediate 参数校验或 plan append 在所有权转移前失败时，原 handle 保持 current；
- terminal 一旦接受执行，必须先消费 handle；即使 begin、preflight、default budget、callback 或 resource failure，仍不能重试同一 handle；
- 显式 terminal 参数的 null/非法值在 operation 接受前拒绝，不误消费 handle；
- KeyTraversal 和 ColumnTraversal 也是 one-shot traversal，但不进入 Candidate Scan plan；
- consumed Candidate Scan 与 Traversal 分别返回稳定的 typed lifecycle failure；
- callback、comparator 和 allocator 不得重入同一 ownership aggregate。

Public Scan handle 只拥有短生命周期 semantic plan identity。Terminal 完成或失败后必须清除 callback、reference selector 与 Table strong reference，避免 consumed handle 长期保留 application graph。

## 7. Index、snapshot 与 materialization

- `findIndex/requireIndex` 返回单个 current Index，不先创建多项 snapshot；
- `indexSnapshot()` 显式承担 `O(M)` copy；empty/single 可以采用等语义 specialization；
- snapshot gather 只允许在同一来源 Table 的同步只读批次中，通过一个或多个 ColumnView 立即消费；
- `requireCurrent(snapshot)` 只作测试、调试或边界防御，不进入强制 hot path；
- 跨 operation 保存稳定引用必须使用 Key 或 application-owned identity；
- materializing terminal 返回 detached schema object/List，并显式承担 object graph 与 budget 成本。

Snapshot、materialization 和 mutation 是三种不同的 correctness、ownership 与 allocation boundary，API 和 evidence 不得混称。

## 8. Mutation terminal

Update/Remove 必须先冻结最终 candidate set，再执行 validation、resource/unique/exact/ownership preflight 与 publish。UpdateCursor 只写 staged scratch；Remove 使用 swap-remove 并同步修复 primary/exact/child relocation。

Callback failure、conflict 或资源拒绝保持旧 stable state。SOMA 的原子性只覆盖一次同步 Table/ownership-aggregate operation，不覆盖 callback 的外部副作用、多次调用或跨 root transaction。

## 9. 物理执行约束

Semantic plan 使用 schema-specific typed source 和 compact ordered stage storage。小 stage 链使用固定 inline slots，溢出只使用 primitive kind/argument arrays 与 callback reference array；不建立 per-stage linked node、Iterator、Java Stream、generic Sink graph 或 per-candidate object。

Candidate physical shape 是 closed internal set：

```text
Range | SegmentRange | ExactSinglePass | Bitmap | SparseIndexes
```

选择输入包括 known cardinality、density/contiguity、downstream reuse/random access、
sort/barrier 与 budget。Contiguous single-pass 使用 Range/SegmentRange；maintained
exact + scalar/single-pass terminal 直接使用 Exact；ultra-sparse reused candidate
可以使用 compact indexes；dense reused membership 可以使用 Bitmap；只有
sort/stable random access/multi-pass 才 materialize。Choice/formula identity进入
Explain，不公开 live Candidate 或 pull cursor。

Bitmap 只用于至少两个单字段 exact-equality source/predicate 的重复交集，并由
`planningRows/current rows × current distinct groups × retained bytes × expected
word work` 的 versioned formula 选择；不规定 public cardinality 常数，不适用于
Unique、String/composite selector 或 universal candidate buffer。Exact selector 的
hash/full-equality 与 authoritative column 保持 correctness Owner；公式、预算、
布局或 mutation 条件不满足时回退 ExactSinglePass + filter，不得漏行。

Terminal-time executor 按 operation shape 选择：

- Packed zero-stage terminal 走直接路径；
- Exact source 直接绑定 eager maintained group；
- 无 Sort 且无需完整冻结时使用 fused streaming traversal；
- Sort barrier 使用 table-local `IndexBuffer` 与稳定 primitive sort scratch；
- 单 Sort 且 terminal 最多需要一项时，可以用 first-on-equal 的 stable arg-min `O(M)`；
- snapshot/materialization/update/remove 仍遵守各自 copy、budget 或 freeze 边界。

这些优化不能改变 callback 次序、stable tie、empty/failure、logical stats、one-shot lifecycle 或 mutation atomicity。

## 10. 成本模型

设 `N` 为 Table size、`M` 为当前 candidate count、`C` 为触碰列数、`G` 为 exact group size、`P` 为 probe/full-equality 成本：

| Pattern | 时间主项 | allocation/retained 主项 |
|---|---|---|
| Packed scan | `O(N × C)` | 仅在 stage/terminal需要时使用 candidate scratch |
| current Index | `O(1)` | 无 materialization 时为常量 |
| primary/unique point | `O(P)` | maintained locator/index retained cost |
| exact group | `O(P + G)` | group/link retained cost |
| bitmap exact intersection | `O(words + matches)` | 仅公式选择的 maintained primitive words；否则为零 |
| filter/skip/limit | `O(M)` 或短路边界 | fused counters/primitive scratch |
| full sort | `O(M log M)` | `O(M)` reusable primitive scratch |
| best-one | `O(M)` | 常量或 single-index scratch |
| snapshot | `O(M)` | detached `O(M)` copy |
| materialize | `O(M × object graph)` | detached graph + budget |
| ColumnView gather | `O(M × C)` | view + application projection |
| update/remove | candidate work + maintained access-path delta | staged mutation/primitive candidates |
| append/replace | batch columns + locator/exact/ownership maintenance | Batch + growth/staging |

具体常数、allocation、GC 和 code size 属于 Report；benchmark 方法与 Gate 属于 Engineering。

普通 `Iterator<T>`、closeable pull cursor 或 terminal-returned live Candidate 不进入
Access Model。Candidate intermediate laziness 与 Result Delivery 是两件事；callback
Result Delivery 的统一 lifecycle 由 [DataFlow 执行模型](dataflow-execution-model.md)
和 [Materialization 边界](materialization-boundary.md)共同约束。

## 11. Java Stream 借鉴边界

SOMA 借鉴 Java Stream 的 lazy intermediate、source/stage/terminal、one-shot、short-circuit、stateful barrier 和 terminal specialization，但不实现 `java.util.stream.Stream`，也不引入 Spliterator、parallel、Collector、generic map/flatMap/reduce 或通用 query object。

新增 Access Pattern、Stage 或 Terminal 前，必须说明现有代数为何不能自然表达、cardinality/sequence/validity、failure/resource、snapshot/materialization/mutation边界、typed/no-boxing实现，以及来自 Blueprint、oracle 和 benchmark 的证据。
