# SOMA Transformation Model

类型：Temporary

状态：active（Stage 2 semantic closure complete；Stage 3 contract projection complete）

Owner：SOMA Transformation semantics

正式事实源：否

事实范围：候选 State/Derivation、Source、Logical Shape、Value、Expression、
Operator、Effect、State Transition 和组合语义

非事实范围：正式 API 名称、IR encoding、module、physical algorithm 和已实现能力

最后审查日期：2026-07-26

## 1. 模型责任

```text
Access Model          数据从哪里来、如何定位
Transformation Model 数据怎样改变 shape、cardinality、order 和 lineage
Effect Model          怎样观察、物化、修改或向 application 交付结果
```

Pipeline 只是 Candidate sequence 的线性组合机制，不代表 SOMA 的全部访问或
Transformation Model。Point、Column、Key、Bulk 和 Ownership 可以保持独立路径，
也可以通过 typed binding 成为 Logical Definition 的 Source。

Typed DSL/DataFlow 是 Schema-bound Java authoring surface，不是查询语言：不接纳
字符串 query、runtime catalog、动态 schema、任意对象 source 或未受约束的
operator extension。Reusable Definition 是一次进程内、有限 Invocation 的计算
定义，不是 distributed job graph 或 application control flow。

本模型只定义可观察语义和 legality。数组、IndexBuffer、bitmap、hash table、
parallel tree 等属于 [Execution Architecture](execution-architecture.md)。

### 1.1 State、Behavior 与 State Transition

SOMA 同时拥有运行时状态平面和围绕该状态的本地计算平面。Transformation
不是脱离状态的 operator 清单，其完整语义叙事是：

```text
State(t) + Input / Parameter + Logical Definition
  -> derive candidates / values / relations
  -> decide Result or stage MutationSet
  -> validate invariants and resource boundary
  -> State(t + 1) and/or Detached Output
```

pure operator 只产生 invocation-local derived information，不修改 source。
只有显式 Effect 能形成 borrow、materialization、handoff 或 state mutation。
失败、取消或预算拒绝发生在 commit 前时，不发布 partial Result/Effect；commit
失败的恢复边界由 Execution Architecture 冻结。

### 1.2 权威事实、投影与推导

| 信息 | 性质与 Owner | 生命周期规则 |
|---|---|---|
| Schema semantics | compiler/runtime 共享的结构与契约事实 | protocol/version 决定兼容性 |
| Table live state | 当前 active lifecycle 内 SOMA 计算的权威事实 | 只由合法 Table Operation 或 safe-point apply 改变 |
| Key/Unique/Exact access structure | 从 live state 派生并同步维护的物化表示 | 失配是 invariant violation，不能成为第二事实源 |
| Logical Definition | 用户声明的计算语义事实 | immutable，可复用，不绑定 current state |
| Compiled Template/Physical Plan/cache | 从 Definition、Schema 和 policy 推导 | 可验证、失效和重建，不拥有 logical semantics |
| Candidate/Projected/Grouped/Joined/Windowed | invocation-local 派生序列或关系 | 受 lineage、source lifecycle 和 invocation 约束 |
| Scratch/Cursor/current Index | 当前 operation/invocation 的机械状态 | 不构成稳定 Identity，不得跨 lifecycle 保存 |
| MutationSet | 已冻结但未提交的变更意图 | commit 前不是 live fact，失败时不得部分发布 |
| Snapshot/Result/Delta/Command | 明确语义和 epoch 的 detached 投影或推导 | 不伪装为稳定来源状态快照，也不自动成为 retained state |

Projection 回答 application reality 中哪些事实进入 SOMA；Derivation 回答可由
当前权威事实和 Definition 算出什么。物化 index、cache、snapshot 或 result
不会改变其派生性质。任何派生信息都必须能说明 source facts、rule、Identity
映射、publication/invalidity 和 discard/rebuild 条件。

## 2. 三层描述

### 2.1 Logical Shape

Logical Shape 描述数据在语义上是什么，不承诺 Java 泛型类或物理容器：

| 维度 | 必答问题 |
|---|---|
| element | current Index、column value、tuple、group、window、delta 或 scalar |
| cardinality | `0..1`、`0..N`、`N -> 1`、`N -> N`、expanding 或多输出 |
| lineage | 来源 Table/aggregate，是否多 source，能否回到一个 live source |
| value state | required、absent、null/sentinel 是否存在及如何比较 |
| logical order | unordered、source sequence、stable、total、event-time |
| effect capability | read-only、single-source writable、detached |

候选语义词汇：

```text
Candidate<T>      single-table current Index sequence
Projected<L,V>    保留或显式丢弃 lineage 的 derived value
Ordered<S,K>      由显式 key 建立顺序
Grouped<K,S>      invocation-time logical groups
Partitioned<K,S>  predicate/key 产生的 disjoint logical branches
Joined<L,R>       保留两个 source lineage；outer variant 允许显式 absent side
Windowed<S>       一次 invocation 内的有限 ordered window
Scalar<V>         聚合结果
Delta<T>          detached、带 operation/key/version 的变化
MutationSet<T>    已冻结但尚未提交的 single-source 变更
```

这些是语义角色，不等于必须生成同名 public Java type。

### 2.2 Operator Traits

| 维度 | 候选值 |
|---|---|
| arity | source、unary、binary、multi-input、multi-output |
| cardinality | preserve、reduce、expand、scalarize、partition |
| execution | streaming、short-circuit、bounded-state、full barrier |
| state scope | stateless、invocation-local、retained |
| order | preserve、establish、destroy、require |
| determinism | deterministic、order-sensitive、floating-order-sensitive |
| splittability | sequential-only、partitionable、mergeable |
| effect | pure、borrow、materialize、single-source mutate、external handoff |

`statefulness` 和 `splittability` 属于 Operator，不属于 Logical Shape。

### 2.3 Physical Properties

Physical Properties 是 planner 可利用、但不能改变语义的执行事实：

- packed、selection vector、bitmap、hash table、ordered run；
- actual order、partition、maintained access path 和 source binding；
- cardinality、distinct count、selectivity、skew；
- live borrow、scratch、detached result、retained structure；
- memory budget、parallelism 和 kernel identity。

同一 Logical Definition 可以拥有多个 Physical Plan；所有 Plan 必须保持相同
Shape、Value、Operator、Effect 和 failure identity。

## 3. Value Semantics

Value semantics 是 Join、GroupBy、Window、ordering、hash 和 parallel reduction
共享的契约：

| Value | equality / hash | built-in order |
|---|---|---|
| boolean、integral、char、enum | Java value equality 与对应稳定 hash | value/ordinal 的 total order |
| String | Java content equality/hash | Unicode code-point lexicographic order |
| `@SomaValue` / composite | canonical leaf order 的结构 equality/hash | 全部 leaf 可排序时的 lexicographic order |
| ordinary float/double payload | Java 8 comparison/IEEE-754 arithmetic；不自动变成 hash key | `Float.compare`/`Double.compare` total order |
| finite canonical floating key | 拒绝 NaN/Infinity，`-0.0` 规范化为 `+0.0` | canonical numeric order |
| optional value | presence 与 payload 是两个状态 | 显式 requested 时 absent-first；没有隐式 sentinel |

GroupBy/Join/hash key 必须是 required、hashable value；optional 输入必须先显式
`isPresent`、`requirePresent` 或 `coalesce`，避免把 absence 静默解释为可匹配 key。
outer-join 的 absent side 是 tuple-side presence，不是字段 `null`、default 或
optional leaf absence。Equality 与 hash 必须一致；order 不参与 Identity。

算术与 reduction：

- primitive 表达式保持 Java 8 promotion、two's-complement integral overflow、
  integer divide-by-zero 和 IEEE-754 行为；narrowing 必须显式；
- built-in integral `sum` 使用 `long` accumulator；`long` overflow 与 Java
  `LongStream.sum` 一样 wrap；`average` 使用 `(count, sum)` 并返回显式 empty；
- `min/max/arg-min/arg-max/top-k` 使用显式 total comparator，compare 为零时保留
  upstream first/stable order；
- reduction/scan 明确 seed、empty、accumulate、merge、finish 和 failure；
- built-in floating sum/average 与 order-sensitive reducer 使用 canonical
  left fold，AdaptiveParallel 必须 sequential fallback；
- 只有 reducer 声明并满足 identity、associativity、deterministic merge 和
  thread-safety，才允许 fixed-tree parallel merge；声明错误属于 caller contract
  violation，由 differential/property evidence 防御，runtime 不分析 bytecode。

所有 kernel、sequential/parallel executor 和 reference oracle 使用同一规则。
Value/absence/overflow/floating semantics 在 Stage 2 冻结；精确 Java method 名称
仍由 Stage 3 投影。

## 4. Source 与 Lineage

候选 Source：

| Source | Shape / cardinality | 主要 lineage |
|---|---|---|
| Packed | `0..N` Candidate | single Table |
| Primary/Unique Point | `0..1` Candidate/value | single Table |
| Exact Group | `0..N` Candidate | single Table |
| Owned Child | `0..N` Candidate | one ownership aggregate |
| Column | `0..N` primitive/value sequence | optional Table lineage |
| IndexSnapshot Gather | detached Index sequence gather | source Table，currentness 受现有契约约束 |
| External Batch/Delta | detached typed input | application/external lineage |
| Parameter | scalar/value | invocation |

External Batch/Delta 必须已由 application 完成 I/O、mapping、ordering 和基础
validation。JDBC `ResultSet`、connection、CDC client、message consumer 或网络
stream 不能成为 Source；blocking I/O 不进入 SOMA graph 或 compute executor。

Join、Projection 或 Expand 必须显式描述 lineage preservation。失去 live
single-source lineage 的结果只能 read/materialize/handoff，不能直接 mutate Table。

## 5. Expression Model

| 表达 | 能力与边界 |
|---|---|
| Generated typed expression | column/reference、constant/parameter、boolean/comparison/arithmetic/conversion 与 tuple/composite key；可参与 legality、pruning、fusion、identity 和 parallel |
| Registered pure function/reducer | 显式 typed signature、semantic identity/version、purity、determinism、thread-safety、failure/value-state；reducer 另声明 seed/accumulate/merge/finish 与 associativity |
| Opaque callback fence | cursor callback escape hatch；不分析 bytecode、不用 reflection、默认不重排/并行/跨进程缓存，只允许声明 retention/exception/thread-safety/side-effect 的 definition-instance identity |

registered contract 由 caller 声明；analyzer 只能验证 typed/structural constraint，
不能从 Java method body 推断 purity、determinism 或 thread-safety，这些责任必须
由契约、test 和 evidence 共同承担。Opaque callback 提供表达力，但不能伪装成
registered function，也不能通过虚假 trait 绕过 sequential、lifecycle 或
failure fence。

Generated expression 是 canonical optimizable path。Registered function 只在
generated vocabulary 无法自然表达领域计算时使用，并以 caller 提供的稳定
`semanticId + version + typed signature` 参与 Definition identity。Opaque callback
只按 Definition 实例 identity 缓存，默认 sequential、order-sensitive、
non-reorderable；callback 抛出的第一个 logical-order failure 是 Invocation primary
failure。任何表达都不得读取或修改未声明 Table、Cursor 或 mutable global state。

## 6. Operator Catalog

| Family | 最低目标 variant | 主要语义 |
|---|---|---|
| Selection | filter、skip、limit | candidate preserving/reducing，可能 short-circuit |
| Projection | column、tuple、typed derived expression | 保留或丢弃 lineage |
| Aggregation | count/match、sum/average、min/max、arg-min/max、mergeable reduction | sequence 到 Scalar |
| Prefix Scan | inclusive/exclusive ordered scan | sequence 到等 cardinality Projected |
| Partition | predicate split、key partition | sequence 到 Partitioned branches |
| Combine | ordered same-lineage concat/union-all | compatible branches 到一个 sequence |
| Rearrangement | sort、top-k、GroupBy | 建立 order 或 groups |
| Join | inner/left-outer/left-semi/left-anti equi Join | 双 source Joined 或 left Candidate |
| Expand | owned-child Expand | element 到有限多 element |
| Window | finite ordered count/time window | ordered sequence 到 Windowed |
| Effect | probe、borrow、snapshot、materialize、update、remove、scatter、handoff | live/detached boundary |

Catalog 只表达受控能力族；unbounded stream/object flat-map 与完整 set algebra
非目标。exact index 不等于 GroupBy，`remove_if` 是 Selection + Effect，prefix
scan 是独立的 order-sensitive `N -> N` operator，不伪装成 reduction；time
Window 必须 finite/ordered，retained/incremental 不在最低包络。

### 6.1 基础 Operator Semantics

- Selection 保持 upstream order；`skip/limit` 按进入该 stage 的 sequence 计数；
- Projection 是逐 element 的 `1 -> 1` typed derivation。只有显式保留唯一
  single-source current Index 且不 duplicate/expand 时，才保留 writable lineage；
- count 在 empty 上为零；match 允许 logical short-circuit；无 identity seed 的
  min/max/reduce 在 empty 上返回 explicit absence，不能返回 default；
- stable sort 建立 comparator order；top-k 等价于完整 stable sort 后取前 `k`，
  `k < 0` 拒绝、`k = 0` 为空；
- GroupBy 按 key 在 upstream 中第一次出现的次序产生 group，group 内保持
  upstream order；hash iteration 不能成为可观察顺序；
- predicate Partition 由调用点声明 branch 顺序；key Partition 采用 key 首次出现
  顺序。每个 upstream element 恰好进入一个 branch，除显式 later filter 外不丢失；
- owned-child Expand 按 parent upstream order，再按各 child 当前 source order
  展开。跨多个 parent 的结果默认 read-only；
- 所有 barrier 在接受 terminal 后才执行；budget/failure 不得发布 partial result。

### 6.2 Join Semantics

Join 的 match mechanism 与 preservation semantics 正交：

| Variant | 输出与 lineage | 最低语义 |
|---|---|---|
| Inner Equi | matched `Joined<L,R>`，双 lineage | 只发布相等 key 的匹配组合 |
| Left Outer Equi | `Joined<L,R?>`，双 lineage | 保留全部 left；无匹配时 right 为 logical absent |
| Left Semi | `Candidate<L>`，single-left lineage | 只保留存在至少一个匹配的 left |
| Left Anti | `Candidate<L>`，single-left lineage | 只保留不存在匹配的 left |

left-driven variant 保持 left order；primary/unique 至多匹配一个 right，exact-group
按本次 bound right sequence 展开并受 output budget 约束。对于每个 left，
Inner/Left Outer 按 right bound sequence 发布全部匹配，保留 duplicate
multiplicity；Left Outer 无匹配时只发布一个 absent-right tuple。semi/anti 保持
single-source Effect 能力，Joined 默认只读。right variants 交换 source 后组合；
full/cross/theta 不在最低包络。logical absent 不是 `null`/默认值/sentinel，D23
的投影使用显式 side-presence；point/exact/hash/merge/sort-merge 只是 physical
strategy。Join key 必须使用上一节的 required hashable equality；self-join 可以
使用两个 logical alias，但同一 physical Table 只取得一次 lifecycle guard。

### 6.3 Combine 与 Prefix Scan

最低 Combine 是 shape/value-compatible、same-lineage branch 的 ordered
concat/union-all：按输入 branch 声明顺序连接，并保留每个 branch 的内部顺序和
重复项。它不隐式 distinct、sort 或跨不同 Table 的 current Index namespace。
只有 analyzer 能证明输入来自同一次 Partition 且彼此 disjoint 时，合并结果才
可恢复 single-source Effect；其他 Combine 默认 read-only。

Prefix Scan 必须声明 input order、inclusive/exclusive、seed/identity、
accumulate/merge 和 failure/value semantics。输出与输入等 cardinality，并以
`Projected<L,A>` 表达每个位置的累计值。只有 contract 与 fixed decomposition
能够证明结果 identity 时才允许 parallel scan；否则 planner 必须 sequential
fallback。Tree Scan 是 physical strategy，不改变逻辑顺序或 floating contract。

### 6.4 Finite Window

Window 只接受已建立 total logical order 的 finite input：

- count window 具有正 `width`、正 `step` 和显式 `includePartial/dropPartial`；
  anchor 从 Index 0 开始，窗口为 `[anchor, min(anchor + width, N))`；
- range/time window 使用 required non-decreasing `long` order key、正 width、
  正 step、显式 origin 和 partial policy，窗口区间为 half-open
  `[anchor, anchor + width)`；
- range anchor 只覆盖 bound input 的有限 key range；empty window 不发布；
- window 按 anchor order 发布，元素保持 upstream order；overflow、非单调 key、
  非法 width/step 在发布前 fail closed；
- window state 只属于 Invocation scratch，不跨 Invocation 持有 watermark、
  late event、eviction 或 recovery state。

## 7. Composition Algebra

```text
LogicalDefinition
  := SourceNode OperatorNode* EffectNode?

OperatorNode
  := Unary | Binary | MultiInput | MultiOutput
```

单次 invocation 默认是有限 DAG：

- Join 是 binary；
- Partition 是 multi-output；
- Combine 是受 shape、lineage 和 branch order 约束的 multi-input；
- fan-out 需要明确共享 node、consumer count 和 logical materialization；
- shared pure node 在同一 invocation 只具有一次逻辑结果；
- Effect 只能位于明确 terminal/sink boundary，不能被公共子表达式隐式重复；
- cycle/feedback 通过 Table、Delta 或 application state 跨 invocation 表达。

Application 的 event loop、solver policy、simulation clock、game system order、
transaction 和 recovery 只负责触发 Definition/Invocation，不成为 Logical Node。

每条 edge 必须能推导 input/output Shape、cardinality、lineage、order 和 Value
semantics。缺少任一项时，Definition 在执行前 fail closed。

Stage 2 冻结以下 Shape legality：

| Shape | 合法后续 | canonical 低物化 terminal | Effect |
|---|---|---|---|
| Candidate | select/project/sort/top-k/group/partition/scan/combine/aggregate/join/owned-child expand | probe、borrow、current Index、IndexSnapshot | 保持唯一 single-source lineage 时 update/remove |
| Projected | select/project/sort/top-k/group/partition/scan/combine/aggregate/join/window | scalar 或 typed borrowed traversal；大结果 detached-columnar | 仅保留唯一 current Index、无 duplicate/expand 时可回到 single-source MutationSet |
| Partitioned | branch-local Candidate/Projected 合法操作、branch terminal、disjoint Combine | branch-scoped borrow 或 detached-columnar | analyzer 证明同一 Partition、branch 不重复且 target lineage 唯一时可恢复 Effect |
| Grouped | group projection、per-group aggregate、having、sort group、detached consumption | group cursor/aggregate scalar 或 detached-columnar | read-only |
| Joined | select/project/sort/top-k/group/window/aggregate | joined cursor 或 presence-aware detached-columnar | read-only；semi/anti 输出 Candidate 例外 |
| Windowed | window projection/aggregate/select | window cursor、scalar stream 或 detached-columnar | read-only |
| Scalar | direct read、detached handoff | direct typed scalar/explicit absence | 不可回写 |
| Delta/MutationSet | validate、compose disjoint target commands、apply/handoff | detached command summary | 仅显式 safe-point/single-source commit |

不合法的核心组合包括：Joined/Grouped/Windowed 直接 update/remove、不同 Table
current Index Combine、unordered input Window/Prefix Scan、optional key 未显式处理的
Join/GroupBy、Effect node fan-out、以及 detached result 直接恢复 writable lineage。

表中能力是闭包责任，不是最终 public method 清单。每个 admitted Shape 必须至少
有一个不依赖 per-element object materialization 的 canonical terminal。

图的同层主叙事保持为：

```text
bind typed sources
  -> derive/filter/combine values
  -> cross explicit barrier when required
  -> produce Result or freeze Effect input
```

某个 node 可以进入 kernel、hash/group/window 或 reduction 子叙事，但这些物理
细节不能泄漏并打断 logical graph 的语义叙事。

## 8. Window、Delta 与 Retained State

必须区分：

- finite ordered Window：本专题最低目标，state 只属于 invocation；
- temporal retained Window：跨 invocation 持有 event/time state；
- automatic incremental view maintenance：由 Delta 驱动长期维护 derived state。

后两者需要 watermark、late event、eviction、recovery、retained-memory ownership
和独立等价性证明。Stage 2 将 temporal retained Window 与 automatic
incremental view maintenance 冻结为非目标；若要接纳，必须单独改变最低目标
包络并获得用户决定。Invocation 结束后只允许丢弃 scratch 或交付 detached
Result，不保留由 operator 暗中维护的第二份 runtime fact。

`Delta` 在本专题中是带明确 Identity/version/operation semantics 的 detached
input/output projection，可作为 safe-point mutation 输入；它不是 live Table
事实，也不自动意味着 retained incremental executor。最低 apply semantics 是：

- Delta envelope 记录 schema/target identity、ordered entries 和可选
  expected structural epoch；不保存 JDBC/CDC handle；
- keyed target 以 stable Key 表达 `Insert`、`Update`、`Delete`；row-level
  expected version 只有 schema/contract 显式提供 version expression 时才存在；
- Insert 要求 absent，Update/Delete 要求 present；`Upsert` 不是隐式默认语义；
- 同一 Delta 中 duplicate target Key 拒绝；entry declaration order 是可观察
  apply order，physical executor 不得按 hash iteration 静默重排；
- expected structural epoch/version conflict 在 staging 时 fail closed；SOMA 不持有已消费
  delta-id 历史，因此不承诺跨调用 exactly-once/idempotence，application 必须用
  checkpoint、expected structural epoch/version 或业务 Identity 控制 replay；
- dense target 没有跨 operation stable identity，只能使用 Batch append/replace
  或当前 Invocation 内的 frozen MutationSet；
- 整批 validation、resource/access-path preflight 成功后按 deterministic order
  apply，并只发布一次 operation result；structural epoch 严格遵守既有
  structural-change 契约。范围仍是一个 Table/ownership aggregate，不是跨 root
  transaction。

## 9. Effect 与 External Handoff

Effect 前冻结 candidate/mutation set，并定义 target lineage、resource/access-path
preflight、commit/relocation order、failure 和 live/detached boundary。
single-table live lineage 可以 update/remove；Projection 只有保留该 lineage 才可
回写；Joined/Grouped/Windowed 默认 read-only。multi-source 只产生 detached
MutationSet/command，不获得跨 Table atomic commit；external handoff 只返回
detached value，不执行 SQL、网络、checkpoint 或 transaction。

Probe/Scalar 读取不是 mutation；Borrow 是 callback-scoped consumption effect。
Borrow callback 的外部副作用不属于 SOMA 原子性，callback failure 只能保证不发布
尚未 commit 的 SOMA MutationSet，不能回滚 application 已执行的 I/O 或对象修改。
State mutation 与 detached handoff 不能在同一个隐式 terminal 中混合；需要两者时
先 deterministic commit，再由 application 显式发布 detached command。

## 10. Result 与 Consumption Model

Result form 由 Shape、lineage、lifecycle、size 和 budget 推导，不以一个万能
`Result<T>` object graph 抹平差异：

| Form | 适用边界 |
|---|---|
| Probe/Scalar | count、match、aggregate 等小型直接值 |
| Borrowed traversal | Invocation 内 callback-scoped、不可 escape 的低物化消费 |
| IndexSnapshot | 仅 single-Table Candidate 的 current Index detached copy |
| Detached columnar result | Projected/Joined/Grouped/Windowed/Partitioned 的 typed columns、presence 与 shape identity |
| Materialized object | 明确承担 object graph 与 output budget 的 application boundary |
| MutationSet/Command/Delta | detached effect intent 或 external handoff |

Detached columnar result 使用 JVM heap typed/primitive arrays，并拥有独立 output
budget；它不是 live Table、稳定 source snapshot 或可直接回写的 lineage。重新进入
SOMA 必须经过显式 Batch/Delta validation 与 publish。

消费契约冻结为：

- Borrowed traversal 只在 Invocation terminal callback 中有效；Cursor、tuple、
  group/window view 不得逃逸，terminal 返回即失效；
- IndexSnapshot 只支持 single-Table Candidate，并继续遵守 caller-responsibility；
- detached-columnar 在成功返回前完成全部 output budget、presence bitmap 和
  shape/protocol identity 建立；caller 显式 close 只在未来持有可关闭资源时需要，
  V1 普通 heap array result 不要求 close；
- materialization 必须显式选择并预检 object/cardinality budget；
- empty scalar 使用 primitive-specific presence/result，不用 `null` 或默认值；
- outer Join absence 通过 side presence 读取；访问 absent side 的 leaf 返回 typed
  failure，不返回默认 carrier；
- execute、budget、callback、cancel 或 allocation failure 不返回 partial detached
  Result。Borrow 已发生的 application side effect 除外，责任如上一节。

## 11. Candidate Scan 子代数

当前 Candidate Scan 对应：

```text
CandidateSource
  -> candidate-preserving linear stages
  -> candidate terminal/effect
```

它是完整 Transformation Model 的线性、lazy、one-shot 特化子代数。现有
zero-stage、exact-group、short-circuit、best-one 和 mutation semantics 必须保留。
Reusable Definition 不得直接复用 current mutable `GeneratedScanPlan`。

DSL fast path 可以绕过通用 graph object，但必须通过 shared semantics、reference
oracle 和 differential evidence 证明结果与完整模型一致。

## 12. Reference Oracle

Stage 2 冻结一个 test-only、领域中性的 reference evaluator：

- 使用小规模 detached logical facts和直接 Java 8 collections/object tuple 表达
  semantics，不进入 production artifact 或 hot path；
- 每个 operator 只实现本文件定义的 cardinality、order、lineage/absence 与
  failure，不模拟 physical planner；
- 覆盖 empty/single/duplicate、optional/outer absence、stable tie、overflow、
  callback failure、budget rejection、Join multiplicity、Partition/Combine、
  Window boundary、Delta conflict 和 sequential/parallel identity；
- Candidate Scan fast path、generated DSL 和 reusable DataFlow 对同一 fixture
  进行 differential comparison；
- property test 改变 input cardinality、group distribution、join selectivity、
  partition count 和 worker decomposition，但不通过随机 timing 判断正确性。

Oracle 只拥有测试语义投影；Transformation Model 仍是规范 Owner。

## 13. Stage 2 Semantic Closure

Stage 2 已冻结 State/Derivation、Value/absence、Expression、Operator variants、
Shape legality、Join preservation/multiplicity、Group/Partition/Combine order、
finite Window、Prefix Scan、Result、Effect、Delta apply、retained-state non-goal、
parallel eligibility 和 reference oracle。

D2、D3、D7–D9、D14、D17–D18、D23–D24、D27–D30 已具有唯一语义结论。
Java contract role、IR boundary、binding/context、module 和 cache/resource 已由
[Execution Architecture](execution-architecture.md) 在 Stage 3 冻结。具体
physical strategy 仍由 Stage 5 evidence 选择，不构成 Stage 2 语义缺口。
