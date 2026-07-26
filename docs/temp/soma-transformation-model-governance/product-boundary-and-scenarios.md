# 产品边界与场景证据

类型：Temporary

状态：active（F0 product boundary frozen；Stage 1 coverage closed）

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
发布后，Table 才是当前 active lifecycle 内 SOMA 计算的权威 live state；上游系统的
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
  -> stable Table state

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

Stage 2 结论：

- Delta 是 detached typed handoff/Shape，拥有 stable Key、ordered operation、
  optional structural-epoch/version guard；不是 retained executor state；
- Group、Join、Window 只保留 Invocation-local scratch/result；
- 只有既有 Key/Unique/Exact access structure 可以作为同步维护的 derived
  representation，operator 不能暗中建立第二份长期 runtime fact；
- 每次 Invocation 从当前 live Table state 重新求值；application 可以通过显式
  Batch/Delta 更新输入，但 planner 不跨 Invocation 自动维护 view；
- retained temporal state、watermark、incremental recovery 和 maintenance
  memory budget 需要未来独立产品决定。

automatic incremental maintenance 因此被冻结为 non-goal。若要接纳，必须修改
最低目标包络并获得用户决定，不能以“optimizer”或普通 scratch 暗中实现。

## 8. Stage 1 场景语义投影

场景只用于证明共同语义和边界，不把领域对象提升为 SOMA core 概念：

| 场景 | 权威 runtime fact | 行为叙事 | Access / Transformation 投影 | Effect / Result | 保持在 application |
|---|---|---|---|---|---|
| RTD | operation、resource、calendar、setup/transport 与 versioned external Delta | safe-point apply → join feasible relation → filter/project → group/rank → stage assignment command | Point/Exact/Column/Batch/Delta；Join、Selection、Projection、GroupBy、best/top-k | detached command；显式 single-source MutationSet | MES I/O、规则编排、跨系统事务、恢复和发布 |
| 工业调度算法 | definition、runtime state、eligible relation、machine/resource availability | 定位 frontier → gather columns →计算 score →选择 best →逐 root 提交领域动作 | Point/Unique/Exact/Column 已有；Projection、Aggregation、受约束 Join 与 reusable Definition 是 gap | current Index/Scalar/command；现有 Table mutation 保留 | machine heap、solver loop、跨 Table commit policy |
| 个体仿真 | keyed individual state、environment state、tick input 与 churn | packed/exact traversal → partition behavior → update movement/energy → stage birth/death → aggregate diagnostics | Packed/Exact/Column/Candidate mutation 已有；Partition、Projection、Reduction、parallel tick 是 gap | single-source update/remove、Batch append、detached diagnostics | clock、system order、random policy、spatial/environment scratch |
| 游戏运行时 | identity-bound component state、frame input 和 system order | identity association → select active → transform → reduce/event output → deterministic scatter | Point/Column、identity Join、Partition、Projection、Reduction、parallel system | single-source staged scatter、detached event/command | game loop、render/network、event/state machine |

“工业调度算法”与“个体仿真”有当前代码证据；RTD 和游戏只有设计 trace。
Stage 2 的 reference oracle 可以使用最小 synthetic facts 验证语义，但 Stage 1
不新增第三个 example，也不把概念 trace 表述为当前能力。

## 9. Access / Transformation / Contract Coverage

### 9.1 Source 与 Access

| Pattern | 当前 canonical 路径 | Stage 1 结论 | 后续 Owner |
|---|---|---|---|
| Packed / current Index | Table terminal、Scan、`fetchAt/mutateAt` family | 保留；不统一进 graph 才算完整 | 既有 Access Design |
| primary / secondary unique point | key/unique point family | 保留 `0..1` 语义；可作为 Definition Source binding | Stage 3 generated binding |
| secondary exact group | `scanByX` eager group source | 保留；不得 read-time rebuild | Stage 3 generated binding |
| owned child | parent-owned child facade/Scan | 保留 aggregate ownership，不升级跨 root transaction | Stage 2 lineage / Stage 3 binding |
| Key / Column / IndexSnapshot gather | Traversal、ColumnView、snapshot | 保留独立低成本路径；graph 只能显式 bind | Stage 2 Result / Stage 3 binding |
| Batch / replace / clear | detached Batch + Table operation | 保留；Batch 是外部输入边界 | Stage 2 Effect |
| Delta / Parameter | 尚无 transformation contract | 新增 detached typed Source；不接 I/O client | Stage 2 semantics / Stage 3 contract |
| multi-source relation | application 手工 point/gather | 缺少 schema-aware read-only Join | Stage 2 Join / Stage 3 coordinator |

### 9.2 Operator 与 Result

| 能力族 | 当前能力 | canonical 目标 | Gap 分类 |
|---|---|---|---|
| Selection / Skip / Limit | Candidate Scan | 保留 lazy/fused fast path，并进入共享语义 | semantic bridge |
| Projection | callback 内手工计算 | typed derived Value/tuple，可保留或丢弃 lineage | new semantic/contract |
| Aggregation / best | count/match/best-current-Index 特化 | scalar reduce、min/max/sum/count/arg-min/max/top-k | additive terminal/operator |
| Partition / Combine | application branch | disjoint branches、ordered same-lineage concat/union-all | new semantic/contract |
| Sort | Candidate dynamic stable sort | 扩展到合法 ordered Shape；physical strategy internal | additive semantic |
| GroupBy | application Map/array | invocation-local typed groups + aggregate/borrow result | new semantic/contract |
| Join | application point/gather | inner/left-outer/left-semi/left-anti equi Join | new multi-source semantic |
| Expand | owned child facade | owned-child Expand；不接 arbitrary flatMap | controlled extension |
| finite Window | application loop | ordered invocation-local finite Window | new semantic/contract |
| Prefix Scan | application loop | inclusive/exclusive ordered scan | new semantic/contract |
| Result consumption | probe/current Index/snapshot/borrow/materialize | shape-aware Scalar/Borrow/IndexSnapshot/detached-columnar/Materialize | normalize and extend |
| Effect | update/remove/Batch operation | controlled single-source MutationSet、Delta apply、detached command | normalize and extend |
| reusable execution | one-shot mutable Scan plan | immutable Definition/Template + one-shot Invocation | new architecture |
| parallel execution | application-owned loops | controlled Invocation-internal adaptive parallelism | new architecture |

### 9.3 Contract Gap 处置

- `Preserve`：Point、Column、Key、Bulk、Ownership 和 Candidate Scan fast path 不因
  Transformation Model 被通用化或改名；
- `Bridge`：现有 Scan 通过 shared semantics/reference oracle 对齐，但允许继续使用
  compact generated plan 与 specialized terminal；
- `Add`：Expression、Projection、Aggregation、Partition/Combine、GroupBy、Join、
  finite Window、Prefix Scan、shape-aware Result、Delta 和 reusable DataFlow；
- `Internal`：IR、planner、kernel、scratch、physical Join/Reduction/Partition 策略；
- `Application`：control loop、domain rule、frontier/heap、I/O、transaction、
  clock、spatial scratch、event machine 和 publisher。

每个 gap 已归属于 Stage 2 semantic Owner 或 Stage 3 contract/architecture Owner；
没有使用“未来实现再决定”的未归属 gap。精确 Java 名称和 overload 不属于
Stage 1 退出条件。

## 10. Stage 1 关闭结论

Stage 1 已完成四场景 semantic projection、runtime fact/behavior、
Access/Transformation/contract coverage：

- 当前能力和目标能力已分开；
- Pipeline 没有被提升为全部 Access Model；
- 新能力均能追踪到至少两个目标场景或 RTD 能力闭包；
- direct access、application-owned structure 和外部 I/O 没有被错误迁入 graph；
- 全部 contract gap 已分配 Stage 2/3 Owner。

因此 Stage 1 关闭。该结论不表示任何新 operator 已实现，也不改变正式
Blueprint/Design/API。

## 11. 当前边界保护

正式裁决前仍以正式 Blueprint/Design 为准；Candidate Scan 保持既有
CandidateAccess，application-owned heap/frontier/event queue 不自动迁入 SOMA。
本专题不建立 RTD framework、SQL/distributed runtime 或 native/off-heap backend，
也不宣称当前实现已具备这里描述的候选能力。
