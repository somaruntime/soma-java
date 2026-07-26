# SOMA Transformation Model 裁决、证据与收口

类型：Temporary

状态：active（Stage 1–3 immutable candidate audit complete；implementation not authorized）

Owner：SOMA Transformation governance decisions and evidence

正式事实源：否

事实范围：候选裁决状态、未决项、证据要求、风险、Gate 和 readiness 自审

非事实范围：正式产品决定、已支持能力、已完成实现和正式性能结果

审查基线：`45fc992d46a6053a203c6d833624cf299269bf6e`

最后审查日期：2026-07-26

## 1. 状态含义

| 状态 | 含义 |
|---|---|
| Fixed Target | 用户已确认的专题目标；仍须在最终正式 Design 中原子固化 |
| Resolved | Temporary 已有唯一语义/架构结论；进入 immutable candidate，但尚非正式 Design |
| Candidate | Temporary 已形成推荐方向，但需 Stage 1–3 语义/架构证据后冻结 |
| Open | 当前仍有多个合理答案，是 production 实施阻断项 |

pre-release clean break 降低迁移负担，不会把 Candidate/Open 自动升级为正式决定。

F0 Freeze 只覆盖 README 最低目标和 Decision Register 中的 Fixed Target。
Stage 1–3 现已把原 Candidate/Open 收敛为 Resolved；Fixed Target/Resolved
共同构成 Temporary immutable candidate，但仍不自动成为正式 Design 或
Implementation Authorization。

## 2. Decision Register

| ID | 责任 | 状态 | 当前方向 / 关闭条件 |
|---|---|---|---|
| D1 | 产品定位与准入 | Fixed Target | Schema-Defined、Compiler-Specialized、JVM Heap-Resident embedded runtime-state computing；SOMA 拥有 state/local-compute plane，application 拥有 control plane |
| D2 | State/Shape/Traits/Properties | Resolved | State Transition 连接 State/Behavior；Shape/Traits/Properties 正交；legality 与 terminal matrix 已冻结 |
| D3 | graph topology | Resolved | 单 Invocation finite DAG；shared pure result；feedback 只跨 Invocation |
| D4 | DSL/builder/IR boundary | Resolved | generated typed DSL + controlled one-shot builder；public Definition opaque，internal IR/physical plan 不公开 |
| D5 | reuse lifecycle | Resolved | immutable Definition/Template；explicit compile/retain；one-shot Invocation state machine |
| D6 | multi-source consistency | Resolved | Invocation-owned typed binding；aggregate ID canonical guard、alias dedup、cross-schema compatibility、reverse cleanup |
| D7 | joined/grouped/window effect | Resolved | Joined/Grouped/Windowed read-only；semi/anti Candidate 或 analyzer-proven single-source lineage 才可 mutate |
| D8 | Delta/retained/incremental ownership | Resolved | detached ordered keyed Delta + optional structural-epoch/version guard；无 implicit upsert/idempotence；retained/incremental 非目标 |
| D9 | logical operator set | Fixed Target | README 冻结受控 capability family，包括 Combine 与 Prefix Scan；不追求 SQL/关系代数完备，Stage 2 关闭具体 variant |
| D10 | static/dynamic planner input | Resolved | static lowering + bind-time specialization + deterministic barrier-local selection；不反馈共享 Template |
| D11 | parallel semantics | Fixed Target | 独占 Invocation 内部并行；不开放 concurrent Table access；保持 sequential identity、fixed merge、bounded failure、deterministic commit |
| D12 | annotation/generated compatibility | Resolved | annotation/schema hash 不变；generated/runtime v5 + transformation/kernel v1 原子 clean break，不保留双 protocol |
| D13 | DSL/DataFlow shared core | Resolved | shared analyzer/candidate program/reference oracle；Scan 保持 specialized executor，不形成双 correctness model |
| D14 | typed expression/function/callback | Resolved | generated expression、registered semantic ID/version function/reducer、definition-instance opaque fence |
| D15 | schema-level Execution Context | Resolved | Context 只拥有 execution resources/defaults/accounting；source 只在 Invocation bind，不设 registry/global cache |
| D16 | identity/cache invalidation | Resolved | canonical SHA-256 Definition/Template identity；epoch 只属 Invocation；无 global cache，opaque callback instance-bound |
| D17 | fan-out/fan-in/effect scheduling | Resolved | shared pure result、consumer-counted scratch、ordered compatible Combine、Effect terminal only |
| D18 | Window/incremental split | Resolved | finite count/range Window；temporal retained Window 与 automatic incrementalization 非目标 |
| D19 | module topology | Resolved | 独立 `soma-dataflow`；每 Table 最多一个 companion；Java 8 external-consumer/footprint prototype 通过 |
| D20 | diagnostics/observability | Resolved | graph stats 与 TableStats 分开；OFF/BASIC/DETAILED；Explain detached/redacted/non-hot-path |
| D21 | executor ownership | Fixed Target | Sequential/AdaptiveParallel；managed dedicated + borrowed executor |
| D22 | external state handoff | Fixed Target | Batch/Delta + Safe Point + detached output；MES/JDBC 单独专题 |
| D23 | Value semantics | Resolved | required/hashable key、explicit absence、Java 8 arithmetic、stable tie、floating left-fold fallback 已冻结 |
| D24 | reference evaluator | Resolved | test-only logical oracle + differential/property evidence；不进 production artifact/hot path |
| D25 | resource/deadline/cancellation | Resolved | Context upper bound + Invocation narrowing；hard output/scratch/task/deadline；logical failure priority与bounded drain |
| D26 | explainability contract | Resolved | logical/bound explain 信息、redaction、stats mode 与非 hot-path边界冻结 |
| D27 | projection/derivation authority | Resolved | Table live state 是 epoch 内权威事实；index/plan/result 是有 source/rule/lifecycle 的 derived representation |
| D28 | Join preservation semantics | Fixed Target | inner/left-outer/left-semi/left-anti equi Join；right variants 组合；full/cross/theta 非最低目标 |
| D29 | Result consumption model | Fixed Target | Shape-aware scalar/borrow/IndexSnapshot/detached-columnar/materialized/command；每个 admitted Shape 必须有低物化 canonical terminal |
| D30 | Combine/Prefix Scan semantics | Fixed Target | ordered same-lineage concat/union-all 与 inclusive/exclusive prefix scan；完整 set algebra 非最低目标 |

Fixed Target 中的 capability family 不能被 Candidate/Open 的实现困难静默移除。

## 3. Stage 1–3 强制关闭责任

| 责任 | 主要阶段 | 必须冻结 | evidence-dependent |
|---|---|---|---|
| Product/Coverage | Stage 1 | scenario → semantic projection → runtime fact/behavior → access → shape/operator/effect → contract gap | fixture 规模与展示格式 |
| Value/Expression | Stage 2 | D2、D14、D23/D30 的完整 semantics | generated class、encoding、kernel |
| State/Operator/Effect | Stage 2 | D3、D7–D9、D17–D18、D27–D30 | physical data structure |
| Parallel | Stage 2–3 | D11、callback thread-safety、failure identity | partition、threshold、worker count |
| Binding/Lifecycle | Stage 3 | D5–D6、D15、Safe Point | coordinator class/layout |
| Identity/Resource/Explain | Stage 3 | D16、D20、D25–D26 | hash/cache/check interval/renderer |
| Module/API/Compatibility | Stage 3 | D4、D12–D13、D19 | internal class/package naming |
| Reference Model | Stage 2–3 | D24 oracle semantics与 coverage | evaluator/fixture implementation |

Stage 4 不能接手未决语义或架构。physical parameter 也不能在缺少 prototype/
benchmark evidence 时提前写死。

## 4. Coverage 与成本证据

Stage 1–3 必须形成：

```text
Problem World / Scenario
  -> Design Intent / Semantic Projection
  -> Authoritative Runtime Facts / Identity / Ownership
  -> Behavior Narrative / State Transition
  -> Access Pattern
  -> Input/Output Shape / Expression / Operator / Effect
  -> Contract Projection / Existing API / Gap
  -> Execution Role / Physical Representation Candidate
  -> Correctness / Performance Evidence
  -> Feedback or Replacement Closure
```

成本模型至少覆盖：

- source/output cardinality、lineage width 和 touched columns；
- streaming、short-circuit、bounded state、barrier 和 fan-out；
- Partition/Combine branch count、prefix scan accumulator width/order；
- scratch、detached、retained memory 和 output cap；
- branch distribution、distinct groups、join selectivity/skew 和 order；
- mutation frequency、Index maintenance 和 relocation；
- build/analyze/compile/bind/execute/effect/cleanup；
- sequential/parallel crossover、worker scratch、tasks、cancel/drain；
- current/high-water retained heap、array capacity、allocation、Young/Full GC、
  p50/p95/p99 或等价 tail evidence。

## 5. 三重实施 Gate

| Gate | 必须证明 |
|---|---|
| Semantic Closure | 最低包络内的 Value、Shape、Expression、Operator、Effect、order、lineage、failure 和 non-goal 全闭合 |
| Architecture Feasibility | JVM heap-resident module/API、codegen/class footprint、binding、scratch、identity、parallel 和 Scan fast path 由受控 prototype 证明 |
| Implementation Authorization | 用户明确批准 immutable candidate 进入 public/generated/runtime 实施 |

当前状态：

```text
Semantic Closure         PASSED
Architecture Feasibility PASSED
Implementation Authority NOT GRANTED
```

前两个 Gate 只表示 Temporary candidate 已闭合并通过受控 feasibility evidence；
在用户审查并明确授权前，不得修改 production/public/generated/runtime surface。

## 6. 抽象与单项实施准入

新增或提升为核心的 public/internal abstraction，必须先说明：

1. 它在问题、语义、契约、执行或物理哪个层级表达什么稳定含义；
2. 它是否提供语义压缩、不变量保护、ownership/lifecycle 归属或依赖隔离；
3. 它参与哪条同层主叙事，向下一层投影为什么；
4. 它的状态、失败、失效/重建和 evidence Owner；
5. 为什么局部实现、组合或既有抽象不能更清楚地承担责任。

不能回答这些问题的 wrapper、manager、handle、node、module 或 cache 不进入核心
设计；单实现、单调用方、代码长度和静态“未使用”也不能单独否决真实抽象。

任何 operator/vertical slice 实施前必须具备：

1. accepted logical semantics；
2. input/output Shape 与 Value semantics；
3. cardinality、lineage、order、fan-out 和 failure；
4. expression analyzability/callback fence；
5. canonical public/generated API 或 internal-only 决定；
6. source binding、Effect 和 consistency boundary；
7. JVM heap-resident、no-boxing、schema-specific implementation design；
8. reference oracle、negative/property test 和 application trace；
9. component cost matrix；
10. compatibility、footprint 和 external consumer 影响。

缺少任一项，只能继续设计或 prototype，不能成为 product capability。

## 7. Evidence 与 Benchmark 纪律

- correctness、reference identity 和 negative path 先于 timing；
- routine comparison 使用有限 fork，不自动循环 rerun/rebaseline；
- 分开测 build/analyze/compile/bind/execute/effect/materialize；
- parallel evidence 明确 executor、workers、partition、threshold、hardware；
- 不隐式使用 common pool；
- 发现慢点时区分 application、schema/access path、logical graph、planner、
  physical strategy 和 kernel；
- 两个 example 保持 problem/workload identity；
- 不用缩小规模、自动放宽阈值或单次 wall-clock 掩盖问题。

### 7.1 Stage 1–3 有界执行协议

Stage 1–3 采用“证据够用即停”，不把设计闭合变成长时间调优：

1. 每个 Decision 只进行一次事实审计、一次必要 prototype 和一次结论复核；
2. 同一问题最多比较两个能保持 F0 目标的候选；第三种方向必须先证明前两种都
   无法满足语义或架构约束；
3. Stage 1 只形成 coverage 与 gap，不实现 operator；
4. Stage 2 只冻结可观察语义和 reference oracle，不选择 P2 physical strategy；
5. Stage 3 prototype 只回答 compatibility、footprint、lifecycle、determinism 和
   fast-path feasibility，不追求最终吞吐数字；
6. 可由静态依赖、编译、golden 或 deterministic test 回答的问题，不运行
   benchmark；需要 timing 时先单 fork feasibility，只有结论会改变架构裁决时
   才运行有限多 fork；
7. 同一实验最多一次修正和复跑。仍不确定时记录 evidence limit 和保守裁决，
   不循环调参、rebaseline 或缩小 workload；
8. 窄 Gate 随 slice 运行；`./scripts/check.sh` 只在 Stage 3 immutable candidate
   前运行一次；
9. 不新增 F0 之外的能力，不提前进入 Stage 4 production 实施，不以文档篇幅、
   LOC 或漂亮 benchmark 数字替代退出条件。

各阶段唯一退出条件：

| Stage | 唯一退出条件 |
|---|---|
| 1 | 场景、runtime fact/behavior、Access、Transformation、contract coverage 全映射，gap 均有 Owner |
| 2 | admitted capability 的 Shape、Value、order、lineage、Effect、failure 与 legality 无 Open |
| 3 | contract/IR/binding/module/identity/resource/explain 均有唯一候选，P1 prototype 通过，形成 immutable candidate |

## 8. Risk Register

### P0：实施前必须关闭

- D2/D27/D29 State、Shape、Result 与 fact/derivation authority；
- D9/D23/D28 Join variant、absence、cardinality、order、lineage 与 Effect；
- D17/D30 fan-in、disjointness、Prefix Scan 与 branch/order semantics；
- D6/D15 multi-source binding、root ownership 和 read consistency；
- D7/D8/D18 Effect、Delta apply、Window 与 retained state；
- D14/D23 Expression 与 Value semantics；
- D16 identity/cache invalidation；
- D19 module/public/internal boundary；
- D25 deadline/resource/cancellation；
- admitted operator variant 与 canonical API。

状态：`CLOSED`。Stage 1 coverage、Stage 2 semantic closure 与 Stage 3 contract
projection 已将上述责任映射到 Resolved/Fixed Target；不存在留给 Stage 4 决定的
可观察语义。

### P1：必须由 prototype 证明

- generated source/class/type/compile footprint；
- schema-level coordinator partial-bind/cleanup；
- fan-out/shared node/scratch budget；
- opaque callback retention/cache；
- sequential/parallel identity、cancel/drain 和 worker scratch；
- Candidate Scan zero-stage/exact/best-one/mutation fast path；
- golden、schema hash、protocol 和 external consumer closure。

状态：`PASSED FOR ARCHITECTURE CANDIDATE`。证据组合为：

- Temporary Java 8 prototype：module/external consumer、typed binding、
  canonical guard/partial cleanup、consumer-counted scratch、callback retention、
  direct/fixed-partition identity、cancel/drain、executor ownership 和 companion
  footprint；
- 当前 v4 executable evidence：Candidate Scan fast path、golden/schema hash、
  generated protocol、external consumer 和 33-table code-size baseline；
- Stage 4 强制 replacement Gate：真实 v5 binding 必须逐 slice 重新证明上述
  executable contract。本状态不是用 prototype 替代 production evidence。

### P2：由稳定性能证据选择

- branchy/branchless；
- scalar/tree reduction kernel 与 crossover；
- index/hash/merge/sort-merge join；
- buffered/in-place partition；
- vectorization、partition size、cache layout/eviction。

P2 是 physical strategy，不是产品语义。某个 P2 策略被否决不构成目标缩水。

## 9. F0 Product/Scope Freeze Readiness 历史基线

本节记录 immutable F0 commit
`f3e694828c0b623b85dfe4e842c75e668db9ff9b` 当时的状态，不代表当前未决数：

| 维度 | 结果 | 结论 |
|---|---|---|
| Owner/结构 | PASS | README 拥有唯一设计脊柱；Product、Transformation、Execution、Decision 只展开各自层级 |
| Scope non-regression | PASS | 最低能力包络、Access/Scan、DSL/DataFlow、Parallel、Safe Point、两 example 和 evidence 未缩水 |
| 六项补充追踪 | PASS | Combine、Prefix Scan、Partitioned legality、Result、function/reducer 与 Delta apply 均进入唯一 Owner、Decision 和 Stage |
| 抽象与叙事 | PASS | product projection、State Transition、Definition/Invocation 和 parallel/commit 主叙事已建立 |
| 事实与推导 | PASS | runtime live fact、derived access/cache/plan、staged mutation 与 detached output 已分离 |
| Freeze boundary | PASS | F0 只冻结 Product/Scope；15 个 Candidate、7 个 Open 和三重实施 Gate 保持显式 |
| 边界一致性 | PASS WITH OPEN DECISIONS | logical/physical、compute/I/O、sequential/parallel、heap/native 与 application/core 已分层 |
| Implementation readiness | BLOCKED | Stage 1–3 的语义、contract 和 architecture prototype 尚未闭合 |

正式 Blueprint/Design、代码、API、test 和 benchmark 均未修改。阻断项是：

- D23 Value semantics 未冻结；
- D6/D15 multi-source binding/context 未冻结；
- D7/D8/D18 retained state 与 Effect 细节未冻结；
- D16 identity/cache 未冻结；
- D19 module topology 未经过 footprint prototype；
- D25 resource/deadline/cancellation 未冻结；
- Partitioned/Grouped/Windowed legality、Result terminal 与 Combine/Scan 细节未冻结；
- capability/API coverage matrix 尚未形成。

因此，Product/Scope Baseline 已通过 F0 Freeze Readiness，可以作为后续
Stage 1–3 不得缩水的 immutable 目标基线；Semantic/Architecture Design 仍是
candidate，还不是 immutable implementation candidate。不得开始 Stage 4
production 实施。

## 10. Stage 1–3 Immutable Candidate 自审

| 维度 | 结果 | 结论 |
|---|---|---|
| Decision closure | PASS | 8 Fixed Target、22 Resolved、0 Candidate、0 Open |
| Scope non-regression | PASS | F0 最低能力、Parallel、Safe Point、两 example、Scan fast path 和 evidence 全保留 |
| Stage 1 coverage | PASS | 四场景 projection 与 Access/Transformation/contract gap 均有唯一 Owner |
| Semantic Closure | PASS | Value、Shape、Operator、Result、Effect、order、lineage、failure 和 non-goal 已冻结 |
| Architecture Feasibility | PASS | module/API/IR/binding/context/identity/resource/explain 有唯一候选并经 Java 8 prototype |
| Access/Scan boundary | PASS | Point/Column/Key/Bulk/Ownership 独立；Candidate Scan 不改走 generic DAG executor |
| Lifecycle/atomicity | PASS | 不开放 concurrent Table、跨 root transaction 或新半发布 lifecycle；既有 operation 原子性不削弱 |
| Footprint discipline | PASS | 一个 Table 最多一个 companion；不按 operator × Table 生成；Stage 4 仍有真实 code-size Gate |
| P2 separation | PASS | Join/reduction/partition/kernel/crossover 未被误写为固定产品语义 |
| Implementation readiness | READY FOR AUTHORIZATION | 三重 Gate 前两项通过，Implementation Authority 仍未授予 |

本轮只修改 active Temporary 及其 prototype，没有修改正式 Blueprint/Design、
production/public/generated API、annotation Schema、runtime、examples、benchmark
或正式支持声明。Prototype 不进入 reactor 和 product artifact。

Stage 4 仍须以真实代码逐 slice 证明 generated v5 binding、schema hash不变、
Scan specialization、external consumer、failure atomicity、parallel identity、
allocation/footprint 和 application trace；这些是 implementation evidence，
不是尚未裁决的产品语义。

因此 Stage 1–3 可以形成 immutable implementation candidate，但不得自动开始
Stage 4。下一步必须由用户审查并明确授予 Implementation Authorization。

## 11. Stage 4–6 剩余顺序

1. 用户审查并授权 frozen candidate；
2. Stage 4 按 Execution Architecture 的 vertical slice 原子实施；
3. Stage 5 选择 P2 physical strategy 并完成稳定性能验真；
4. Stage 6 scope non-regression、正式文档原子固化、Report、Temporary 退役与
   完整 Gate。

## 12. 最终收口检查

专题退役前，所有 Open/Candidate 必须关闭，admitted capability 必须端到端完成，
DSL/DataFlow/Parallel 与当前 Access/Scan 必须具有匹配的 correctness、lifecycle、
footprint 和性能证据。不得残留 temporary API、generic executor、双路径、
compatibility alias、第二 memory backend、重复 Owner 或 future-rewrite dependency。
最后原子同步正式 Design、代码、test、example、benchmark、Conformance、Report
和 checker，删除 Temporary，并在最终源码上通过完整 Gate。

## 13. 外部设计参照

非规范性参照包括 [Java Stream](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html)、
[Arrow/Acero](https://arrow.apache.org/docs/cpp/acero/overview.html)、
[DuckDB](https://duckdb.org/why_duckdb)、[Flink](https://nightlies.apache.org/flink/flink-docs-stable/index.html)、
[DataFusion invariants](https://datafusion.apache.org/contributor-guide/specification/invariants.html)、
[Artemis-odb](https://github.com/junkdog/artemis-odb/wiki/Introduction-to-Entity-Systems)和
[DBSP](https://arxiv.org/abs/2203.16684)。它们只校验 lazy/columnar/planning/
DAG/data-orientation/incremental 分层，不拥有或扩大 SOMA 产品语义。
