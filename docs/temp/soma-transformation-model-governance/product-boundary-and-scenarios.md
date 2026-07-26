# 产品边界与场景证据

类型：Temporary

状态：active（F0 product-boundary baseline frozen）

Owner：SOMA Transformation product boundary

正式事实源：否

事实范围：候选产品定位、场景证据、能力准入和 core/application/external 边界

非事实范围：当前已支持能力、正式 Blueprint、API 契约和性能声明

最后审查日期：2026-07-26

## 1. 候选产品定位

> **SOMA Java 是一款面向 Java 8 的 Schema-Defined、Compiler-Specialized、
> JVM Heap-Resident 高性能运行时状态计算库。**

```text
SOMA Java
  = schema-defined
  + compiler-specialized
  + JVM heap-resident
  + embedded
  + packed columnar
  + mutable runtime state
  + typed access and transformation
  + predictable memory and latency
```

这一定义试图覆盖 RTD、算法、仿真和游戏的共同需求，但不让任何一个应用领域成为
SOMA core 的业务模型。

各限定分别回答不同问题：

- Schema-Defined：Schema 定义 shape、value、identity、ownership 和稳定 access；
- Compiler-Specialized：编译期生成 schema-specific binding/API/kernel，运行期
  不解释 application metadata；
- JVM Heap-Resident：live storage、access structure 和 reusable scratch 由普通
  JVM heap 中的 primitive/reference array 承载，不依赖 JNI、native 或 off-heap；
- Runtime-State Computing：SOMA 同时拥有可变状态存储与围绕状态的本地 typed
  computation，不是只有 layout，也不是通用计算平台。

annotation 是当前 Java 8 的 Schema authoring surface，不是产品本质。若将来改变
声明语法，只要 Schema semantics 与 generated specialization 不变，就没有改变
这里的产品定位。

SOMA 与 application 的责任边界是：

```text
Application Control Plane
  event loop / domain policy / I/O / transaction / recovery
                     |
           Batch / Delta / Invocation / Result
                     v
SOMA Local Compute Plane
  Access -> Transformation -> DataFlow -> Controlled Effect
                     |
                     v
SOMA Runtime-State Plane
  JVM heap-resident packed columns / identity / ownership / lifecycle
                     ^
                     |
SOMA Compile Plane
  Schema -> validation -> normalization -> generated specialization
```

SOMA 拥有 Schema、运行时状态数据面和本地计算面；application 拥有业务控制流、
外部 I/O、事务、补偿和领域策略。

这里的“实时”只表示 embedded runtime 对可预测内存、低 allocation、稳定
tail latency 和 deterministic execution 的 soft real-time 目标，不承诺 hard
real-time deadline、无 GC、lock-free 或操作系统级调度保证。

### 1.1 场景投影与事实边界

目标场景不是 feature checklist。每个场景必须先说明 source reality、SOMA
projection、允许的信息损失和 Identity 映射，再用于推导能力：

| 场景 | 上游现实事实与行为 | SOMA 保留的运行时投影 | application 继续拥有 |
|---|---|---|---|
| RTD | operation/resource/calendar 状态、事件和 dispatch cycle | typed current state、稳定业务 Identity、关系键、可计算属性与受控 Delta | MES I/O、业务规则 Owner、事务、恢复、发布 |
| 算法 | problem state、candidate、score input 和迭代行为 | 高频读取/修改的状态列、typed access、derived value 输入 | solver policy、专用 frontier、搜索控制和终止判断 |
| 仿真 | individual/environment state、tick 和 birth/death event | 适合列式访问的 live state、Identity/ownership relation、tick-local computation | simulation clock、system order、无访问收益的 spatial scratch |
| 游戏 | identity-bound hot state、system behavior 和 frame update | schema-bound state、受约束关联、gather/scatter 输入 | game loop、render、network、event/state machine |

这里的 SOMA Table 是 application 问题事实面向本地计算目的的投影，不自动成为
MES、求解器或游戏世界的全局事实源。Batch/Delta 经 validation 和 safe point
发布后，Table 才是该 Runtime Epoch 内 SOMA 计算的权威 live state；上游系统的
持久化或业务权威性不因此转移。

投影不得丢失计算所需的 Identity、ownership、absence、order 或 Value semantics。
未进入 SOMA 的信息必须明确由 application 保留，不能在 physical implementation
中以隐藏 metadata、callback capture 或第二份 mutable cache 补回。

## 2. RTD 作为能力上限场景

一个典型 Dispatch Rule 可以表现为：

```text
Event / State Delta
  -> update operations, resources and calendars
  -> join current runtime facts
  -> filter feasible candidates
  -> project priority and cost
  -> group by resource
  -> best / top-k
  -> commit assignment
  -> emit result delta
```

RTD 对 SOMA 的价值不只在于证明 Join、GroupBy 和 Window 有用，更在于暴露：

- 高频 mutation 与局部 recomputation；
- multi-table lineage/read consistency，以及 inner/left-outer/semi/anti 对 required、optional、exists/missing relation 的区分；
- priority frontier 与 incremental maintenance；
- bounded allocation、GC 和 tail latency；
- dispatch rule 的重复执行和 plan construction 成本；
- deterministic tie-break、failure 和 commit boundary。

完整 RTD 软件仍由 application 拥有消息接入、业务规则、并发协调、持久化、
外部事务、调度循环和结果发布。SOMA 候选责任只覆盖 embedded runtime state
及其 typed operation execution。

RTD 当前是 capability narrative 和能力上限探针，不是第三个 reference
application，也不是当前实现证据。若后续需要 fixture，应先以 Temporary-only
trace/contract 验证模型；未经单独授权不新增 RTD 产品示例。

## 3. 其他目标场景

算法重点检验 filtering、derived score、arg-min/max、top-k、prefix scan、
partition/combine 和 batch mutation；仿真重点检验 column traversal、
identity/exact access、aggregation、持续 churn 和 tick determinism；游戏重点
检验 identity-bound association、system traversal、gather/scatter 和稳定
frame time。大结果必须能以 borrowed 或 detached-columnar 形式消费，不能因为
跨 Shape 就退化成 per-element object graph。专用 frontier、simulation
clock/spatial scratch、game loop/event machine 继续由 application 拥有；例如
Position + Velocity 只证明受约束 identity join，不证明 arbitrary relational
join。

两个现有 reference application 负责提供真实性和 non-regression evidence，
不拥有 core Transformation semantics。它们可以证明某个抽象自然或别扭，却不能
仅凭现有实现形状限制 SOMA 的最终模型。

## 4. 共同能力判定

能力进入 core 不能只因为它存在于数据库、HPC 或 Java Stream。至少需要回答：

1. 是否被两个以上目标场景自然需要，或属于一个能力上限场景的基础闭包；
2. 是否可以保持 schema-specific typed、no-boxing 执行；
3. 是否能定义明确的 shape、lineage、order、lifecycle 和 effect；
4. 是否存在自然且唯一的 canonical API；
5. 是否比现有 Access Model 或 application-owned structure 提供实质语义；
6. 是否保持 JVM heap-resident、资源有界且可测量的内存/GC/性能模型；
7. 是否仍属于 SOMA state/local-compute plane，而不是 application control plane。

这些标准用于裁决具体 variant、API 和 core/composition 边界；专题 README 已冻结
最低目标包络。不能因某个初始 physical prototype 不理想，就把其中的 Join、
GroupBy、finite Window 或 Reusable DataFlow 整体移出目标。

Parallel execution 也已进入最低目标包络，因为大规模算法 scan/reduction、
仿真 tick system 和 RTD multi-source rule 都可能从中受益。但 RTD 的低 tail
latency 不等于线程越多越好：小 workload、opaque callback、short-circuit、
memory-bound kernel 或高 worker contention 必须能够自动/显式回退 sequential。
并行能力只属于一次 Invocation 内部的受控执行，不改变 application 对 Table、
event loop、transaction 或 executor lifecycle 的责任。

## 5. 相邻体系边界

SOMA 借鉴 Java Stream 的 lazy/fusion/short-circuit、Arrow 的 columnar layout、
DuckDB/ClickHouse 的 logical/physical planning、Flink 的 DAG/parallel/failure
分层和 ECS 的 data orientation；但不继承 generic object stream、跨语言
format/off-heap、SQL/OLAP、distributed job 或 Entity/Component/System 产品身份。
Operator Catalog 只表达目标场景需要的受控能力族，不追求这些相邻体系的功能
完备性。

## 6. MES 数据库同步边界裁决

RTD 的 MES 数据同步具有独立的 I/O、mapping、checkpoint、retry、reconciliation、
credential、transaction 和 operational lifecycle，不能成为 SOMA operator 或
runtime-core 责任。本专题只接纳以下通用 handoff：

```text
detached typed Batch / Delta
  -> safe-point mutation
  -> runtime epoch

detached dispatch result / command
  -> application publisher
```

数据库读取与发布使用 application-owned I/O executor，不能复用 SOMA parallel
compute executor。Initial snapshot、polling/CDC、MES schema mapping、source
checkpoint 和结果写回若要实施，必须单独建立 `RTD Runtime State Synchronization`
设计专题；当前没有该实施授权，也不预建 `soma-jdbc-sync`。

## 7. 增量执行假设

RTD、仿真和游戏都是持续变化的运行时。完整 Transformation Model 不能只研究
一次性 batch operator，还必须明确裁决是否接纳：

```text
State(t) + Delta(t)
  -> affected candidates / groups / joins
  -> recompute or maintain
  -> State(t + 1)
```

待裁决问题：

- Delta 作为 detached handoff、SOMA mutation result 和 Logical Shape 时，是否
  共享一种 value/identity/version contract；
- Group、Join、Window 是否允许 retained derived state；
- retained state 是否只是 access structure，还是新的 runtime fact；
- incremental maintenance 失败如何保持旧 stable state；
- 完全重算与增量维护由谁选择并承担内存预算。

automatic incremental maintenance 是独立的代数、生命周期和恢复问题。当前
候选将它保持为 non-goal；D8/D18 必须在 Stage 2 最终关闭。若要接纳，必须修改
最低目标包络并获得用户决定，不能以“optimizer”或普通 scratch 暗中实现。

## 8. 当前边界保护

正式裁决前仍以正式 Blueprint/Design 为准；Candidate Scan 保持既有
CandidateAccess，application-owned heap/frontier/event queue 不自动迁入 SOMA。
本专题不建立 RTD framework、SQL/distributed runtime 或 native/off-heap backend，
也不宣称当前实现已具备这里描述的候选能力。
