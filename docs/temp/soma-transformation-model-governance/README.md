# SOMA Transformation Model 与 Typed DataFlow 产品化治理

类型：Temporary

状态：active（Stage 1–4 complete；Stage 5 candidate）

Owner：SOMA Transformation Model governance

正式事实源：否

实施授权：用户已于 2026-07-26 授权自主完成 Stage 4–6，并在 Stage 6 后纳入
industrial-dynamic-scheduler；grassing-individual-simulation 明确排除在场景迁移外

事实范围：专题意图、最低目标、范围、阶段、停止条件和验收协议

非事实范围：当前正式产品语义、已支持 API、已实现 DataFlow、性能和发布声明

起始实现基线：`45fc992d46a6053a203c6d833624cf299269bf6e`

最后审查日期：2026-07-26

冻结层级：F0 Product/Scope + Stage 1–3 Semantic/Architecture Candidate；不是
正式 Design 或 production implementation

## 1. 意图

本专题面向 RTD、算法、仿真和游戏等高频可变运行时场景，从 Access Pattern、
Transformation semantics 和 Data Shape 推导 API、IR 与执行实现。当前数组、
Candidate Scan 或单一 FJSP 调用链只能提供实现证据，不能反向定义产品。

本专题把[产品边界与场景证据](product-boundary-and-scenarios.md#1-候选产品定位)
拥有的产品北极星作为 Fixed Target，本文不维护第二份产品定义。在该定位下，
本专题采用下面这条唯一设计脊柱：

```text
Problem World / Scenario + Design Intent
  -> Semantic Projection (Runtime State + Behavior Narrative)
  -> State Transition + Access Pattern
  -> Logical Shape / Operator / Effect
  -> Contract Projection -> Execution / Physical Representation
  -> Evidence -> Feedback or Replacement Closure
```

语义投影决定 SOMA 需要保留哪些运行时事实；行为叙事决定这些事实如何被读取、
组合和改变；二者共同产生 Access Pattern。Representation 只能在 ownership、
lifecycle、invariant、boundary 和 evidence 约束下实现上游语义。

这条脊柱在不同抽象层级重复展开：

| 层级 | 核心责任 | 本专题 Owner |
|---|---|---|
| 产品/问题 | 定位、目标场景、保留与舍弃的现实语义 | Product Boundary |
| 信息/行为 | State、Shape、Value、Lineage、Operator、Effect、State Transition | Transformation Model |
| 契约投影 | generated typed DSL、Definition builder、Result/Delta boundary | Stage 3 API candidate |
| 执行 | Definition、Template、Invocation、binding、failure、parallel、commit | Execution Architecture |
| 物理表示 | column/access path/buffer/kernel/partition/merge 与成本模型 | evidence-selected implementation |
| 验证/收口 | 决策、trace、oracle、benchmark、替换闭环 | Decisions/Evidence |

它们是设计权威链，不是代码层模板；correctness、performance、concurrency 和
observability 是跨层 Concern，不自动成为新抽象。

本专题不是给 Candidate Scan 增加更多 stage，而是在现有 storage/access plane
之上建立 typed transformation execution plane。产品的 canonical 使用叙事是：

```text
Schema
  -> Compiler-specialized Runtime State
  -> Typed Transformation Definition
  -> Bound One-shot Invocation
  -> Result / Safe-point Effect
```

正式 Blueprint/Design 在原子固化前仍是唯一规范性事实。F0 Freeze 只表示产品
意图、抽象层级、目标包络和推导方向稳定，不表示 Stage 1–3、正式能力或实现已
冻结。

## 2. 一套语义、两种使用形态

Ad-hoc DSL Operation 面向局部 one-shot、低 plan cost；Reusable DataFlow 面向
反复执行的 RTD Rule、Simulation/Game System。两者经 generated typed binding
共享 Logical Shape、Expression、Operator、Effect、failure 和 legality，可以
具有不同 fast path，不能形成两套 correctness model。

Application Rule Graph、SOMA Logical Definition 与 Physical Plan 必须分层。业务
反馈可以跨 invocation；单次 SOMA invocation 默认是有限 DAG。

## 3. 最低目标包络

Stage 1–3 可以裁决 canonical variant、组合方式和 physical strategy，但不能在
没有用户明确决定的情况下移除以下目标：

- Schema-defined、compiler-specialized、JVM heap-resident 的 Java 8 产品边界；
- 现有 Point/Candidate/Column/Key/Bulk/Ownership Access；
- typed Selection、Projection、Aggregation、Prefix Scan、Partition、
  Combine/Fan-in、Sort/Top-K/GroupBy；
- schema-aware inner/left-outer/left-semi/left-anti equi Join 与 owned-child Expand；
- 一次 invocation 内的 finite ordered Window；
- shape-aware Probe/Scalar/Borrow/IndexSnapshot/detached-columnar/Materialize
  Result 与 controlled Effect；
- detached typed Batch/Delta、deterministic safe-point mutation 与 detached
  output handoff；
- ad-hoc lazy DSL Operation 与 reusable Typed DataFlow；
- invocation 内受控 data parallelism、fixed-order reduction 和 independent
  pure-branch execution；
- predictable allocation、memory、tail latency 和 Candidate Scan fast path。

若证据表明其中某一能力族无法成立，必须停止并请求用户决定。不得以
composition、non-goal 或 physical strategy 的名义静默移出专题。

### 3.1 F0 Product/Scope Freeze

F0 冻结产品定位、设计脊柱、最低目标包络、非目标、非回归边界、阶段和 Gate。
改变其中任一项需要用户明确决定。Stage 1–3 只能完成 additive semantic/
architecture closure，不能借实现困难删除或降级 Fixed Target。

F0 不冻结具体 Java API、IR encoding、module topology、Value/absence、binding、
resource、failure 或 physical algorithm；这些在 F0 后由 Stage 1–3 决策和
prototype 关闭。因此，F0 本身不是 production 实施授权。

### 3.2 Stage 1–3 Candidate Freeze

Stage 1–3 已在 F0 不缩水前提下关闭：

- Stage 1：四场景 semantic projection 与 Access/Transformation/API coverage；
- Stage 2：Value、Shape、Expression、Operator、Result、Effect、Delta、Window、
  Parallel eligibility 与 reference oracle；
- Stage 3：public role、opaque IR、Definition/Template/Invocation、binding、
  `soma-dataflow` module、identity、resource、executor、explain 和 Safe Point，
  并通过 Temporary Java 8 architecture prototype。

Decision Register 当前为 8 Fixed Target、22 Resolved、0 Candidate、0 Open。
这形成可供用户审查的 immutable implementation candidate。它不修改正式
Blueprint/Design 或当前产品能力，也不授予 Stage 4 production 实施；P2 physical
algorithm 和最终性能数字仍由 Stage 5 evidence 选择。

## 4. 非目标

本专题不把 SOMA 变成：

- SQL parser、持久化数据库、分布式流处理平台或通用 DataFrame；
- 通用 Java Object Stream、任意对象 query engine 或 reflection interpreter；
- off-heap/native runtime、FFI 或以逃离 JVM GC 为目标的第二存储后端；
- RTD 业务规则、消息接入、网络、持久化或 application transaction 框架；
- JDBC、SQL、CDC、MES mapping、checkpoint、retry、reconciliation 或 publisher；
- full-outer/cross/任意 predicate join、无限 stream、hard real-time 或
  multi-table ACID；
- automatic incremental view maintenance 或外部 concurrent Table operation；
- 隐式 common pool、无约束默认并行或固定 physical algorithm 的 API；
- Java 8 以外的产品。

finite Window、detached Delta、只读 multi-source graph、same-lineage branch
Combine 和 controlled Effect 属于本专题目标；完整 set algebra、temporal
retained Window、automatic incrementalization 和真正 concurrent mutation
必须另有明确产品决定。

## 5. 非回归与 Clean Break

- Design 服务 Blueprint，实现服务 Design；代码不能反向降低目标。
- 保持 Java 8、Zulu JDK 8 authority 和无新增第三方 runtime dependency。
- 保持 JVM heap-resident packed columnar storage、schema-specific typed facade
  和无反射 hot path。
- hot path 不引入 per-element DTO、boxing collection、Java Stream 或 generic
  object graph executor。
- current Index、Key、IndexSnapshot、ColumnView、ownership、swap-remove 和
  单 Table Operation 原子性在正式裁决前不变。
- sequential 是 parallel 的语义基准；parallel 只发生在 application 独占的
  Invocation 内部，不能把 Table 变成 concurrent API，也不能共享 Cursor、
  Invocation 或 mutable scratch。
- logical API 不泄漏 Hash Join、Tree Reduction 等 physical strategy。
- 两个 reference application 和现有 correctness/performance evidence 不得缩水。
- 不通过缩小 workload、放宽阈值或反复 rebaseline 掩盖问题。

SOMA 尚未正式发布。Stage 3 裁决并获得实施授权后，可以 clean-break 删除、
重命名或重构不合理的 public/generated API、annotation Schema 和 protocol，并
原子迁移代码、fixture、example、benchmark 和文档。默认不保留 deprecated
alias、双 executor、旧 adapter 或长期迁移层。

## 6. 阶段

| Stage | 状态 | 责任 | 退出条件 |
|---|---|---|---|
| 0 | COMPLETE | 协议、基线、结构整理、自审 | Temporary 自洽；当前事实与风险有证据 |
| 1 | COMPLETE | 场景语义投影、权威运行时事实和 Access/Transformation coverage | capability variant、事实 Owner 与 core/application 边界闭合 |
| 2 | COMPLETE | State Transition、Shape、Value、Expression、Operator、Effect、DAG/Window/Parallel semantics | Semantic Closure Gate 通过 |
| 3 | COMPLETE | contract projection、IR、binding、planner、module、identity、resource、explain 与 prototype | Architecture Feasibility Gate 通过并形成 immutable candidate |
| 4 | COMPLETE | 按 vertical slice 完成 DSL 与 Reusable DataFlow 双轨实施 | admitted capability 已在 `190f90f` 端到端闭合 |
| 5 | CANDIDATE | physical optimization 与稳定性能验真 | correctness、allocation、GC、tail latency、parallel crossover 已形成退出候选 |
| 6 | PENDING | scope non-regression、正式事实原子固化和 Temporary 退役 | 完整 Gate、Report、提交和无 active topic 状态完成 |

Stage 4 的每个 slice 必须是最终设计的有效子集：

```text
Scenario / Semantic Projection
  -> Logical Semantics / State Transition
  -> Generated Contract / Binding
  -> Runtime Narrative / Physical Representation
  -> Correctness / Golden / External Consumer
  -> Component Cost / Application Evidence
  -> Replacement Closure
```

不能使用 temporary public API、generic object executor 或依赖未来重写才正确的
中间实现。

## 7. 实施 Gate 与停止条件

Stage 4 前必须依次通过：

1. Semantic Closure：`PASSED`；
2. Architecture Feasibility：`PASSED`；
3. 用户对 frozen target 的明确 Implementation Authorization：`GRANTED
   2026-07-26`。

以下情况必须停止并请求决定：

- Access Model、Index、ownership、lifecycle 或失败原子性需要改变；
- 引入跨 Table atomic mutation、外部 concurrent Table operation 或 retained
  derived-state 新语义；
- 需要公开 internal IR 或 generated-runtime protocol；
- 最低目标包络能力被删除、降级为 application-owned 或取消验收；
- 弱化两个 reference application、performance identity 或验证 Gate；
- 引入第三方依赖、改变 Java 8 或扩大到持久化/分布式产品；
- 引入 off-heap/native storage、JNI/FFI 或第二套 memory/lifecycle model；
- 将 blocking I/O、checkpoint 或 external transaction 放入 graph/compute pool；
- 无法在不改变目标的情况下获得合理内存、footprint 或性能。

### 7.1 按构造即正确

Stage 4–6 不把正确性主要委托给洪水式测试。关键不变量必须在唯一 Owner
产生事实的位置关闭：

| 抽象 | 唯一 Owner | 构造防线 |
|---|---|---|
| Value/Expression/Parameter | typed expression 与 `ParameterSlot` | carrier、presence、source、参数依赖、parallel fence 进入不可变表达式 |
| Shape/lineage/legality | shape-specific DSL handle | 只暴露合法组合；不以 generic node 恢复 writable lineage |
| Definition graph | one-shot controlled Builder | build 前关闭 source/output/effect/identity 冲突；成功或失败后均不可复用 |
| Template/Invocation | immutable Template 与 one-shot Invocation | bind、compatibility、budget、lifecycle、guard 和 terminal state 真实校验 |
| Result/Effect | shape-specific Result 与 safe-point Effect | live/detached、absence、budget、epoch 和 publish boundary 在构造/commit 处关闭 |
| generated/runtime protocol | processor emitter 与 generated binding | schema-specific typed bridge、protocol/hash/ownership 在边界 fail closed |

public/generated 边界对输入、生命周期、ownership、资源和兼容性使用稳定异常；
内部 `assert` 只守护上游已证明、关闭断言也不会损坏数据或语义的纯推导事实。
任何可能导致错误 publish、越界、失效 Index、错误 lineage 或原子性破坏的检查都
必须保留真实 internal failure。

测试证明防线而不复制防线：集中覆盖构造/契约，使用性质测试和 reference
differential 覆盖通用语义，只为新语义选择代表性组合，并保留少量 canonical
end-to-end、external consumer 和性能非回归证据；不冻结 private helper、内部
数组布局或为每个方法重复相同 null/lifecycle 用例。

## 8. 完成条件

专题退役前，Transformation/Execution 必须一致，DSL/DataFlow 必须共享语义内核，
Parallel 必须闭合 ownership/determinism/failure/budget/fallback，Candidate Scan
不得退化，全部 admitted Shape 必须拥有合法 operator/terminal 和低物化消费路径，
全部 admitted capability 必须具有 correctness/performance evidence。不得残留
generic object hot path、隐藏 materialization、双 executor、兼容尾巴或重复
Owner；最终原子固化正式事实，删除 Temporary，并通过完整 Gate。

阶段性模型、单个算子、示例或 benchmark 数字都不能代替最终收口。

## 9. 专题文档与 Owner

| 文档 | 唯一责任 |
|---|---|
| [产品边界与场景证据](product-boundary-and-scenarios.md) | 为什么做、服务哪些场景、core/application/external 边界 |
| [Transformation Model](transformation-model.md) | Source、Shape、Value、Expression、Operator、Effect 与组合语义 |
| [Execution Architecture](execution-architecture.md) | Definition/Template/Invocation、binding、planner、parallel、safe point、module 与 change map |
| [裁决、证据与收口](decisions-evidence-and-closeout.md) | 决策状态、未决项、证据、Gate、风险和 implementation-readiness 自审 |
| [Stage 5 正确性与性能证据](stage-5-evidence.md) | 构造契约、reference/footprint/component baseline、归因与 P2 裁决 |
