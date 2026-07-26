# Transformation Model 设计

类型：Design

状态：正式

Owner：SOMA Transformation semantics

设计层次：`D2` 能力设计

主要关注点：Logical Shape、Value、Expression、Operator、Result、Effect 与组合合法性

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：Transformation 的长期语义、数据形状、lineage、operator、result/effect 和组合边界

非事实范围：public overload 清单、IR encoding、物理算法、当前代码位置和测量数值

最后审查日期：2026-07-27

## 1. 定位

SOMA 的运行时计算由三个相邻但不等同的模型组成：

```text
Access Model          数据从哪里来、怎样定位
Transformation Model 数据怎样改变 shape、cardinality、order 和 lineage
Effect Model          怎样消费、物化、修改或交付结果
```

Candidate Scan 是 `Candidate` 的线性、lazy、one-shot 特化，不代表全部 Access 或 Transformation。Point、Column、Key、Bulk 和 Ownership 保持 canonical 独立路径，也可以作为 typed Definition 的 Source。

SOMA 只接受 schema-bound、有限、进程内的 typed computation。字符串 query、动态 schema、任意对象 source、SQL、分布式 job graph、无限 stream 和 application control flow 不属于本模型。

## 2. 事实、推导与状态变化

```text
State(t) + Input / Parameter + Logical Definition
  -> derive candidates / values / relations
  -> produce Result or freeze Effect
  -> validate resource and invariants
  -> State(t + 1) and/or Detached Output
```

- active Table 是 SOMA 本地计算的权威 live state；
- Key/Unique/Exact structure 是同步维护的派生表示，不是第二份事实；
- Definition 是 immutable logical semantics，不绑定 current Table；
- Candidate、Projected、Grouped、Joined、Windowed 和 scratch 只属于一次 Invocation；
- MutationSet 是未提交意图，成功 safe-point commit 后才改变 live state；
- Result、IndexSnapshot、Delta 和 command 是 detached 投影，不自动成为稳定 source state；
- operator 不跨 Invocation 暗中维护 derived view、watermark 或 cache。

## 3. Logical Shape

Shape 至少回答 element、cardinality、lineage、value state、logical order 和 effect capability：

| Shape | 语义 | Canonical 低物化消费 | Effect |
|---|---|---|---|
| `Candidate<S>` | single-source current Index sequence | probe、borrow、current Index、IndexSnapshot | 保留唯一 writable lineage 时 update/remove |
| `Projected<L,V>` | typed derived value，可显式保留 lineage | scalar、borrow、detached columnar | 只有未 duplicate/expand 的唯一 current Index lineage 可回写 |
| `Partitioned<K,S>` | disjoint logical branches | branch borrow/columnar | 只在同一 partition、无重复且 target 唯一时恢复 |
| `Grouped<K,S>` | invocation-local logical groups | group cursor、aggregate、columnar | read-only |
| `Joined<L,R>` | 两个 source lineage；outer side 可 absent | joined borrow/columnar | read-only；semi/anti 输出 Candidate 例外 |
| `Expanded<P,C>` | parent 顺序下的有限 owned-child expansion | borrow/columnar | read-only |
| `Windowed<S>` | finite ordered window | window cursor、aggregate、columnar | read-only |
| `Scalar<V>` | 聚合或 probe 的小型结果 | direct typed value / explicit absence | 不可回写 |
| `Delta<T>` | detached keyed change envelope | validate/apply/handoff | single-aggregate safe-point apply |

这些名称表达语义角色，不要求 public Java 类型与表格逐字对应。不存在万能 `Result<T>` object graph：Result 形态由 Shape、lineage、lifecycle、size 和 budget 推导。

## 4. Value 与 Expression

- required、optional absent、default、zero、empty、invalid 和 missing 是不同状态；
- integer arithmetic 的 overflow policy 显式定义；不能用 wraparound 偶然行为作为业务语义；
- built-in floating sum/average 与 order-sensitive reducer 使用 canonical left fold；
- min/max/reduce 的 empty 使用 explicit absence；count 的 empty 为零；
- stable comparator 相等时保留 upstream first；
- generated typed expression 是 canonical optimizable path；
- registered function/reducer 必须声明 typed signature、semantic identity/version、purity、determinism、thread-safety 和 failure/value semantics；
- opaque callback 是 sequential、order-sensitive、non-reorderable fence，不分析 bytecode，也不能读取未声明 Table 或 mutable global state。

只有声明并满足 identity、associativity、deterministic merge 和 thread-safety 的 reducer 才能 fixed-tree parallel merge。错误 trait 是 caller contract violation；runtime 不通过反射或 bytecode 推断真实性。

## 5. Operator Catalog

| Family | 支持的语义范围 |
|---|---|
| Selection | filter、skip、limit；保持 upstream order，可 short-circuit |
| Projection | column、tuple、typed expression；逐 element `1 -> 1` |
| Aggregation | count/match、sum/average、min/max、arg-min/max、mergeable reduction |
| Prefix Scan | inclusive/exclusive ordered scan，输出等 cardinality |
| Partition | predicate split、key partition；每项进入一个声明分支 |
| Combine | compatible same-lineage branch 的 ordered concat/union-all；不隐式 distinct/sort |
| Rearrangement | stable sort、top-k、GroupBy |
| Join | inner、left outer、left semi、left anti equi join |
| Expand | owned-child finite expand；不是 arbitrary flatMap |
| Window | finite ordered count/range window；不保留跨 Invocation temporal state |
| Effect | probe、borrow、snapshot、materialize、update、remove、Delta apply、handoff |

GroupBy 按 key 首次出现顺序产生 group，group 内保持 upstream order。Hash iteration、worker completion 和 packed relocation 都不能成为可观察 logical order。

Join 保持 left-driven order 和 duplicate multiplicity。Left outer 的缺失 right 是显式 side absence；semi/anti 保留 left Candidate lineage。Full outer、cross、theta/predicate join 不属于当前产品边界；hash、merge、sort-merge 只是可替换物理策略。

Window 只接受已建立 total order 的 finite input。width/step 必须为正，range key required 且 non-decreasing，区间与 partial policy 显式；retained Window、watermark、late event 和 automatic incremental view maintenance 不在当前模型内。

## 6. Composition Algebra

```text
LogicalDefinition
  := SourceNode OperatorNode* EffectNode?

OperatorNode
  := Unary | Binary | MultiInput | MultiOutput
```

一次 Invocation 是有限 DAG：

- Join 是 binary，Partition 是 multi-output，Combine 是受 shape/lineage/order 约束的 multi-input；
- shared pure node 具有一次逻辑结果，Effect node 不得被 fan-out 重复执行；
- 每条 edge 必须可推导 Shape、cardinality、lineage、order 和 Value semantics；
- Joined/Grouped/Windowed 不能直接 update/remove；
- 不同 Table 的 current Index namespace 不能 Combine；
- unordered input 不能 Window 或 Prefix Scan；
- detached Result 不能恢复 writable lineage；
- cycle/feedback 只能由 application 通过 Table、Delta 或其他显式 state 跨 Invocation 表达。

DSL fast path 可以绕过通用 graph object，但必须与本模型共享语义、reference oracle 和 differential evidence，不能形成第二套 correctness model。

## 7. Result 与 Effect

| Result form | 边界 |
|---|---|
| Probe / primitive Scalar | 小型直接值和 explicit absence |
| Borrowed traversal | callback-scoped，terminal 返回即失效 |
| IndexSnapshot | 只用于 single-Table Candidate，继续遵守 caller-responsibility |
| Detached columnar | typed/primitive heap arrays、presence 和 shape identity，受 output budget |
| Materialized object | 明确承担 object graph 与 materialization budget |
| MutationSet / Delta / Command | detached effect intent 或 external handoff |

Effect 在 commit 前冻结 candidate/mutation set，并完成 target lineage、resource、access-path 和 conflict preflight。Single-source live lineage 可以 update/remove；multi-source 结果只能 read 或产生 detached command，不获得跨 Table transaction。

Keyed Delta 使用 stable Key 表达 Insert/Update/Delete：

- target/schema identity、ordered entry 和可选 expected structural epoch 显式；
- duplicate key、presence/version conflict 和全部资源需求在 commit 前拒绝；
- entry declaration order 是可观察 apply order；
- dense Table 不接受跨 operation current Index Delta，只使用 Batch 或当前 Invocation 的 frozen MutationSet；
- 成功只发布一次 operation result；SOMA 不持有 delta-id 历史，也不承诺 exactly-once。

外部 I/O、JDBC/CDC、checkpoint、retry、transaction 和 publisher 由 application 拥有。SOMA 的 handoff 边界止于 detached Batch/Delta 输入与 detached Result/Command 输出。

## 8. 构造正确性

关键语义在事实产生处关闭：

- immutable typed Expression/Parameter 拥有 carrier、presence、source 和 trait；
- shape-specific handle 只暴露合法 operator/terminal；
- one-shot Builder 在发布 Definition 前关闭 source/output/effect/identity 冲突；
- Result factory 不公开非法 absence、shape 或 partial state；
- Effect factory 与 safe-point commit 在 publish 前关闭 target、epoch、duplicate、budget 和 ownership。

Public/generated 边界使用稳定结构化失败。内部 `assert` 只允许守护关闭后也不会损坏数据或语义的纯推导事实；可能导致错误 lineage、失效 Index、越界、错误 publish 或原子性破坏的条件必须使用真实 internal failure。
