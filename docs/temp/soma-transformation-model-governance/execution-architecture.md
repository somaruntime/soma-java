# SOMA Transformation Execution Architecture

类型：Temporary

状态：active（Stage 3 architecture feasibility and immutable candidate audit complete）

Owner：SOMA Transformation execution architecture

正式事实源：否

事实范围：候选 contract/IR 分层、runtime roles 与叙事、binding、planner、
parallel、safe point、module、identity、resource、explain 和 change map

非事实范围：正式 public type/name、已完成实现、最终 physical algorithm 和性能结论

审查基线：`f3e694828c0b623b85dfe4e842c75e668db9ff9b`

最后审查日期：2026-07-26

## 1. 当前事实与约束

| Surface | 当前事实 | 架构约束 |
|---|---|---|
| Formal architecture | generated facade schema-specific；runtime-core 不解释 annotation | binding 可以生成，metadata interpreter 不能进入 hot path |
| Candidate Scan | [`GeneratedScanPlan`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanPlan.java) 是 mutable linear one-shot plan | 保留 fast path，不能直接扩成 reusable DAG |
| Evaluation | [`GeneratedScanEvaluation`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GeneratedScanEvaluation.java) 保存 terminal-local selection/stats | graph scratch/result 必须有独立 lifetime |
| Lifecycle | [`DenseTableState`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/DenseTableState.java) 只有 per-Table operation guard | multi-source invocation 需要上层 coordinator |
| Runtime plan | [`RuntimePlan`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RuntimePlan.java) 拥有 storage/runtime budget | transformation identity/policy 不应污染 storage plan |
| Processor | annotation/normalized model 只理解 schema、storage 和 access role | 不分析 Java method/lambda body |
| Memory placement | generated columns、access structures 和 Scan scratch 使用 JVM heap array | 保持 heap-resident，不引入 off-heap/native 第二后端 |
| Footprint | [Candidate Scan 报告](../../../reports/2026-07-23-access-model-candidate-scan-performance-report.md) 的 33-table Scan source 为 785,446 bytes | 不能继续按 `operator × Table` 展开 executor |
| Performance | [当前性能摘要](../../../reports/current-performance-summary.md) 已记录 Scan allocation 与两个 example baseline | 通用 IR 不得让短链绕过 specialization |

当前 compatibility identity：

```text
generated protocol     soma-generated-runtime-v4
runtime compatibility  soma-runtime-java8-v4
storage plan protocol  soma-runtime-plan-v3
```

Transformation protocol、graph identity 和 kernel identity 必须独立建模；只有真实
输入集合变化才升级对应 protocol。

## 2. 目标执行管线

```text
Application Rule / Generated DSL / Controlled Definition Builder
  -> Immutable Logical Definition
  -> Analyzer / Legality
  -> Static Lowering
  -> Immutable Compiled Template
  -> Bound One-shot Invocation
     - current source binding and lifecycle tokens
     - parameters
     - execution policy and budget
  -> Bind-time Physical Specialization
  -> Sequential / Parallel Kernels
  -> Deterministic Effect Commit
  -> Result / Diagnostics
```

Application Rule Graph、Logical Definition 和 Physical Plan 是三个对象。DSL 是
authoring surface，不是 IR；public reusable definition 也不等于公开 internal
node hierarchy。

## 3. Runtime Roles

| Role | 生命周期 | 可以持有 | 禁止持有 |
|---|---|---|---|
| Definition | immutable、可复用 | schema/source identity、expression、operator、effect、parameter schema、显式 callback | Table、current Index、Cursor、scratch |
| Compiled Template | immutable、可复用 | validated graph、static lowering、候选 kernel、protocol identity | current Table state、executor、worker scratch |
| Execution Context | 长生命周期、显式关闭 | managed/borrowed executor、默认 execution policy/budget、active invocation accounting | source registry、Table、Template cache、active Invocation mutable state |
| Invocation | one-shot、非线程共享 | bound sources、lifecycle token、parameters、policy、budget | 跨 terminal 复用 |
| Scratch | invocation/worker-local | selection、hash/group/window buffers、staged effect | detached long-lived result |
| Result/Diagnostics | 明确 live/detached | scalar、borrowed、IndexSnapshot、detached-columnar/materialized result、stats、explain reference | 来源状态跨 mutation 保持稳定的虚假承诺 |

Definition/Template 可以安全共享；Invocation、Cursor 和 mutable scratch 不共享。
opaque callback 若被 Definition 持有，必须记录 retention/thread-safety，并限制为
definition-instance scoped cache。

### 3.1 抽象成立条件与 Execution Context 边界

上述 Role 不是按类图预设的层级。它们成立，是因为分别拥有不同的语义、
immutability、ownership、lifecycle 或 failure boundary。Stage 3 冻结每个 Role
时必须说明它参与哪条主叙事，以及向下一层投影为何种状态或机制。

Execution Context 只是 application lifecycle 内的 execution-resource composition
root，不是 source、cache、policy 或 Result semantics 的共同事实源。application
仍拥有 Table root 和调用并发权；source 只在 Invocation 上显式 binding，Context
不登记或长期保留 Table。Template 由 application 显式持有，不设置隐式 global
cache。Context 只组合确有共同 close/并行资源生命周期的 executor、默认 policy/
budget 和 active-invocation accounting，不能扩成万能 mutable manager。

### 3.2 Runtime 主叙事

Definition/Template lifecycle：

```text
author typed semantics
  -> normalize and analyze
  -> compile candidate kernels
  -> validate and publish immutable Template
  -> application retain / discard / explicitly recompile Template
```

Definition 本身不因 source cardinality、executor 或 epoch 改变；这些动态事实只
进入 Invocation。Template protocol/policy mismatch 只拒绝本次 bind/execute，
不修改或替换 Definition semantics。

Builder 是 one-shot mutable authoring object：`OPEN -> PUBLISHED | FAILED`。
`build()` 先完成 structural validation，再一次发布 immutable Definition；成功或
失败后都不可继续修改。Definition 的 `compile` 产生 immutable Template，compile
failure 不污染 Definition，caller 可以在修正外部 policy 后重新显式 compile。

Invocation lifecycle：

```text
create one-shot Invocation
  -> bind sources and lifecycle tokens
  -> validate parameters / compatibility / budget
  -> specialize physical execution
  -> execute and stage Result / Effect
  -> preflight and deterministic commit, or publish detached Result
  -> cleanup scratch / workers / guards
  -> complete, fail or cancel permanently
```

- bind failure 不启动 worker，也不留下 partial guard；
- execute/cancel failure 完成 bounded drain，不发布 partial Result/Effect；
- preflight failure 不修改 live state；
- commit failure 进入明确的 fail-stop/rebuild boundary，不能伪装成普通空结果；
- cleanup failure 不能覆盖更早的 primary failure；
- terminal state 后 Invocation 不可复用。

Invocation 状态固定为：

```text
NEW -> BINDING -> READY -> RUNNING -> COMMITTING -> COMPLETED
                   \          \           \
                    \----------> FAILED / CANCELLED
```

任一 execute/effect terminal 接受后 Invocation 即 consumed；失败或取消不能重新
bind/execute。只读 Result 在 cleanup/guard release 后才发布 detached form；
borrowed traversal 在 RUNNING 内消费，不越过 cleanup。

## 4. API 与 IR Boundary

| Surface | 候选责任 |
|---|---|
| public/generated | schema/table/column/source binding、typed expression/function/reducer contract、controlled Definition builder、Context/Policy/Budget、shape-aware Result/Delta/Command、non-hot-path Explain |
| internal | normalized IR、analyzer/property/cost、kernel/schedule/scratch、cache 和 generated-runtime bridge |

默认不公开 internal IR node hierarchy、physical strategy、kernel 或 mutable plan。
用户可以定义 reusable graph，但不能依赖 internal encoding。

Stage 3 冻结以下 public role/name；精确 overload 可在 Stage 4 按 Java 8
type-safety 做 additive refinement，不能合并其 lifecycle：

| Public role | Contract |
|---|---|
| `DataFlowDefinition<R>` | immutable logical semantics；由 controlled builder 一次 publish |
| `DataFlowTemplate<R>` | immutable analyzed/static-lowered candidate；显式 compile/retain |
| `DataFlowContext` | execution resources；`AutoCloseable`，不持有 Table/Definition |
| `DataFlowInvocation<R>` | one-shot bind/parameter/execute boundary |
| `ExecutionPolicy` | Sequential/AdaptiveParallel、executor mode、stats/explain policy |
| `ExecutionBudget` | output/scratch/task/deadline hard bounds |
| `CancellationToken` | caller-owned cooperative cancellation signal |
| `DataFlowStats` / `DataFlowExplain` | detached non-business diagnostics |
| existing `SomaRuntimeException` envelope | stable dataflow phase/code/context；不建立第二套 generic exception hierarchy |
| primitive scalar / detached-columnar result | shape-aware result，不使用 per-element generic object |
| typed function / reducer contracts | semantic identity、value/failure/parallel traits |

每个 generated Table 最多增加一个 `<Table>DataFlow` companion，拥有 typed
source slot、column/value expression 和 generated binding；不按
`operator × Table` 生成 executor/type。跨 Table Definition 使用显式 source alias，
Invocation 以对应 typed slot 绑定 live Table。现有 `<Table>Scan`、Point、
ColumnView、Batch 和 child facade 保持 canonical direct access，不被 wrapper
替代。

`DataFlowDefinition.Builder` 是 public controlled authoring surface，但 build 后
不暴露 node list；shape role 可以出现在 Java generic marker 中，physical property
和 kernel 不进入 public type。DSL terminal 可直接构建并执行 one-shot
Invocation；reusable DataFlow 显式保留 Definition/Template。二者共享 analyzer、
value/shape semantics 和 generated binding，不共享 mutable plan。

### 4.1 JVM heap-resident mechanical boundary

- live columns、locator/index、Definition/Template、Invocation scratch 和 detached
  result 都使用普通 JVM heap object/array；
- hot data 优先使用 primitive array、bitmap、compact descriptor 和生成的
  static binding，不用 per-element object/boxing graph；
- 不增加 `Unsafe` memory、direct/off-heap buffer、JNI、native kernel 或另一套
  allocator/lifecycle/error model；
- storage、scratch、cache 和 detached output 分开预算，记录 current/high-water
  retained heap、allocation/op 和 GC/tail evidence；
- heap-resident 不承诺 zero GC 或 hard real-time，目标是 bounded allocation、
  reusable capacity 和可解释的 GC pressure。

## 5. Binding 与 Multi-source Read

Definition 声明 typed logical source slot；本次 Invocation 把 slot 显式绑定为
Table/Column/IndexSnapshot/application parameter。Context 不参与 source lookup。

multi-source read 至少满足：

1. 在取得 guard 前解析全部 required slot，拒绝 missing/extra/wrong-schema binding；
2. generated binding 暴露 ownership-aggregate identity、schema/runtime protocol、
   lifecycle token 和受控 access hook，不暴露 backing array；
3. 同一个 Table 可以绑定多个 logical alias，但按 physical ownership aggregate
   去重，只取得一个 guard；
4. independent root/aggregate 在 create 时取得 overflow-safe monotonic
   `aggregateInstanceId`，Invocation 按该 ID 升序取得 guard；
5. partial acquire failure 时按反向顺序释放已取得 guard；
6. Invocation 期间不允许来源 aggregate 发生其他同步 operation；
7. terminal/cleanup 后按反向顺序释放；区分 bind、execute、effect 和 cleanup
   failure。

这只建立同一 application-owned invocation 内的同步 read boundary，不是 database
snapshot isolation、跨线程共享或跨 Table transaction。cross-schema Definition
是合法的，只要所有 source companion 的 transformation/generated/runtime
protocol 兼容；同一 ownership aggregate 内的 child 只能通过 root binding
解析，不能绕过 parent lifecycle 单独注册。

Application 必须在调用前独占全部 source aggregate。SOMA guard 负责 fail-closed
验证而不是把 Table 提升为 concurrent object；并发绑定重叠 aggregate 返回 typed
busy/reentrancy failure，不等待、不死锁。

## 6. Static Lowering 与 Bind-time Specialization

Static Lowering 只使用 schema/source/expression/operator/effect、logical
Shape/Traits/legality、generated access capabilities 和 kernel/runtime protocol。

Compiled Template 保存候选 fused region、barrier 和 kernel，不绑定 Table。

Analyzer 拥有唯一 Value/Shape/operator/failure semantics。现有 Candidate Scan
不改走 generic DAG executor：generated Scan plan 与 DataFlow 的 single-source
candidate region 都降低为同一 compact candidate program contract，并由既有
zero-stage/exact/streaming/best-one/mutation kernel 执行。DataFlow 只在出现
Projection、multi-source 或 barrier 时进入 graph schedule。reference oracle 与
differential test 防止 fast path 形成第二 correctness model。

Bind-time Specialization 使用 cardinality/group/distinct/selectivity/skew、
available access path、actual order/partition、execution budget/policy 和
sequential/parallel crossover。

动态选择不能修改共享 Template，不能改变 logical result/failure，也不能依赖
hash iteration、线程完成顺序或不可重现的 global state。是否允许在 barrier 后
根据实际 cardinality 进行第二次 specialization，Stage 3 裁决为允许：

- 只在显式 barrier 已经产生 invocation-local cardinality/distinct/skew 事实后；
- 只在 Template 预先列出的 compatible kernel 之间选择；
- 选择规则由版本化 planner policy 和 deterministic threshold 决定；
- 结果只写入本次 Invocation physical schedule，不回写共享 Template/cache；
- Explain 记录 observed property、chosen/rejected kernel 和 fallback reason。

不得用运行时试跑多个 kernel、wall-clock 自调优或跨 Invocation 隐式学习替代这套
规则。

## 7. Resource、Deadline 与 Cancellation

Transformation budget 独立于 storage `RuntimePlan`，覆盖 output cardinality、
invocation/worker scratch、retained result、task/worker、deadline/cancellation、
Template 和 explain/stats。V1 不设隐式 Template cache。

预算拒绝必须在 publish/effect 前 fail closed。deadline/cancellation 默认在
partition、barrier、task 和 terminal boundary 检查，不能为了响应速度在每个
element 强制增加 hot-path branch。

取消后停止新 task，bounded cancel/drain 已提交 worker，释放 scratch/guard，不
发布 partial Result/Effect，并以确定规则选择 primary/suppressed failure。

`ExecutionBudget` 至少拥有：

- `maxOutputElements`、`maxOutputBytes`；
- `maxInvocationScratchBytes`、`maxWorkerScratchBytes`；
- `maxTasks`、`maxWorkers`；
- optional monotonic deadline 与 `CancellationToken`；
- stats/explain 的额外 budget。

Context default 是 immutable upper bound，Invocation 只能收紧；materializing、
detached-columnar、Join expansion、Group/Window 和 task fan-out 没有可证明上界时
必须要求显式 budget。deadline 只比较 `System.nanoTime` 同一时钟域，不成为
Definition identity。

失败优先级按 phase 和 logical partition 冻结：bind/preflight failure 优先；
execute 中选择最小 logical partition ordinal 的 worker failure；若没有 worker
failure，再选择 caller cancellation、deadline、effect 和 cleanup。由 primary
failure 触发的 cancel/drain 不覆盖 primary，其余 failure 作为 bounded suppressed
diagnostic。具体 check interval、threshold 和 task size 是 evidence-selected
mechanical parameter，不改变 failure identity。

## 8. Parallel Execution

```text
Execution Mode
  ├─ Sequential
  └─ AdaptiveParallel
       ├─ SOMA-managed executor
       └─ Caller-provided executor
```

- Sequential 不创建或借用 worker；
- AdaptiveParallel 允许 planner 回退 sequential，不强制每个 operator 并行；
- managed 模式由 Execution Context 持有 bounded、可复用、可关闭的 dedicated
  `ForkJoinPool`，不能 per Invocation 创建；
- borrowed 模式接受 `ExecutorService`/`ForkJoinPool`，SOMA 从不 shutdown；
- `ForkJoinPool.commonPool()` 只有被 caller 显式传入时才可使用；
- executor 不进入 Definition identity，属于 Context/Invocation policy。

Context 可以在 disjoint aggregate 上支持多个 application 调用，但不让
Invocation/Cursor/scratch 共享；overlapping source 仍由 aggregate guard 拒绝。
managed Context `close()` 在存在 active Invocation 时抛出 typed busy failure并
保持 open，caller 在 quiescent 后重试；成功 close 才 shutdown/await dedicated
pool。borrowed Context close 只释放 SOMA accounting，不 shutdown、interrupt 或
改变 caller executor。

Application owner 必须先独占本次 Invocation 涉及的 ownership aggregate，worker
只是该同步 operation 内部的实现细节。Parallel execution 不允许 application
并发调用 Table API，也不授予跨线程共享 live Table 的能力。

最低范围是 deterministic contiguous partition、pure/fused data parallelism、
fixed-tree mergeable Aggregation、independent pure branch、parallel stage +
single-source deterministic commit，以及 evidence-based sequential fallback。

不变量：

- worker 使用独立 Cursor/scratch；
- stable output、`first`、skip/limit、short-circuit、tie-break 和 floating
  reduction 保持 fixed logical/sequential identity；
- opaque callback 默认是 parallelization fence；
- nested Invocation 默认 flatten 或 sequential fallback；
- worker failure 完成 bounded cancellation/drain；
- worker 只读 disjoint partition 或写 worker-local scratch，不直接竞争
  swap-remove、Index maintenance 或 shared Table write。

fixed partition boundary 由 logical cardinality 和版本化 partition policy 推导，
不依赖 worker completion order。merge 只按 partition ordinal；`first`、stable
output、top-k tie 和 Prefix Scan offset 都以该 order 恢复。built-in floating
left-fold 与 opaque callback 永远 sequential fallback。

并行只是 Invocation execution 子叙事：

```text
partition deterministically
  -> execute worker-local kernels
  -> cancel/drain on failure
  -> merge in fixed logical order
  -> stage Result / Effect
  -> single deterministic publish or commit
```

它不能改变上层 Definition、Value、order、failure 或 Effect semantics。

## 9. Fan-out、Fan-in、Barrier 与 Scratch

Join、GroupBy、Sort、Window、Prefix Scan、Partition、Combine 和 shared node
会产生 barrier、fan-out 或 fan-in。
执行层必须定义：

- 每条 edge 的 consumer count；
- shared pure node 是否执行一次；
- selection/buffer ownership 和 reference lifetime；
- materialization/barrier point；
- worker-local 与 invocation-shared scratch；
- failure propagation 和释放顺序。

fan-out 不能共享一个 mutable Cursor。需要多消费者时，planner 必须选择可重复
source、selection buffer、partition output 或显式 materialization，并受 budget
约束。Combine 只能消费 shape/lineage-compatible branch，保持声明的 branch
order；只有同一次 Partition 产生且 analyzer 证明 disjoint 的 branch 才能恢复
writable single-source lineage。Prefix Scan 的 partition summary、offset
propagation 和 fixed merge 使用 worker-local scratch，不能改变 sequential
Value/failure identity。

## 10. Effect Commit 与 Safe Point

single-source Effect：

```text
compute candidates
  -> freeze MutationSet
  -> validate/resource/access-path preflight
  -> deterministic commit
  -> publish stats/result
```

multi-source result 默认只读或产生 detached command/MutationSet，不获得跨 Table
atomic commit。

External state handoff：

```text
Application I/O / Mapping
  -> detached typed Batch / Delta
  -> validation/staging
  -> no-active-Invocation safe point
  -> deterministic apply order
  -> publish stable Table state

Detached Result / Command
  -> application-owned publisher
```

Safe Point 不伪造跨 root transaction。Stage 3 冻结 preflight、apply order、
partial failure、epoch publish 和 stale result 规则。
Keyed Delta 的 Insert/Update/Delete 必须先做 duplicate/version/presence/resource
全量预检；dense Table 不接受跨 operation current Index Delta。Detached-columnar
Result 重新导入也必须显式转为 Batch/Delta，不能被直接绑定为 writable live state。
JDBC/CDC、checkpoint、retry 和 publisher 属于独立 RTD synchronization topic。

Stage 3 裁决：

- Safe Point 是目标 ownership aggregate 成功取得 exclusive operation guard、
  没有 active borrow/Invocation 的状态，不是 Context-wide global pause；
- Delta 在取得 guard 后验证 schema/target/expected structural epoch、全部 Key/presence/
  version、duplicate、capacity、unique/exact/child 和 scratch budget；
- apply 严格保持 entry declaration order，因为 swap-remove 后的 packed order
  是可观察 source sequence；physical implementation 可以分阶段准备，但不能改变
  等价 publication order；
- preflight 前/中失败不修改 live state；全部可能失败的 allocation、growth、
  equality/conflict 和 derived-access preparation 必须在 commit 前完成，commit
  narrative 只执行已验证、non-allocating 的 primitive/reference publish step；
- Stage 4 若证明某项 mutation 在 publish 中仍可能抛出可恢复 failure，必须改用
  staged shadow 或完整 undo，而不能新增半发布 lifecycle、削弱现有失败原子性；
- 成功操作只在 structural change 时按既有契约增加一次 structural epoch，并一次
  发布 stats/result；非结构更新不发明第二套 epoch。旧 current Index、
  IndexSnapshot 和 borrowed Result 按现有 lifecycle 失效，已发布 detached
  Result 保持 detached；
- multi-root command 由 application 依序调用多个 single-aggregate safe point，
  SOMA 不承诺全部成功或自动补偿。

## 11. Explainability 与 Observability

非 hot-path Explain 至少说明 logical graph/Shape/lineage/order/Effect，
fusion/barrier/materialization/access path，chosen/rejected kernel，
parallel/partition/fallback，cardinality/scratch/task/budget，以及 opaque
fence/deadline/cancellation/failure phase。

Graph execution stats 与 TableStats 分开，至少区分 build/analyze/compile/bind/
execute/effect/cleanup。Explain 的 API 名称和文本/结构化 encoding 可以在实现期
调整，但信息责任、redaction 和非 hot-path 边界在 Stage 3 冻结：

- logical explain 可从 Definition/Template detached 生成，不绑定 Table；
- bound explain 可以增加 actual cardinality/property、chosen/rejected kernel 和
  fallback reason，但不输出业务列值、Key、callback `toString` 或 executor detail；
- constant/parameter 默认只输出 type、presence 和 redacted marker，caller 明确
  opt-in 才允许 safe literal；
- `StatsMode.OFF` 不建立 per-node计数；`BASIC` 记录 phase/cardinality/budget；
  `DETAILED` 才允许 node/kernel/task counters，并必须进入 evidence overhead；
- `DataFlowStats` 与 `DataFlowExplain` 都是 detached diagnostics，不参与
  Definition identity、不作为 planner input，也不拥有 runtime fact。

## 12. Identity、Cache 与 Compatibility

必须分离 Schema、Logical Definition、Expression/Function、Transformation
Protocol、Kernel Protocol、Planner Policy 和 Bound Source State/Epoch identity。

- Definition identity 使用 canonical binary encoding 和 SHA-256，输入包括
  ordered source slot、schema hash、Shape/operator/expression、constant canonical
  value、parameter type/name、Effect、function/reducer semantic ID/version；
- Template identity 包含 Definition identity、transformation/kernel/generated
  runtime protocol 和 planner policy identity；
- executor instance 不属于 Definition/Template identity；
- bound cardinality、Table lifecycle token 和 captured structural epoch 属于 Invocation；
- opaque callback 不伪造稳定 hash；包含 opaque callback 的 Definition 使用
  definition-instance identity，不能跨 Definition cache；
- identity mismatch 必须在执行前 fail closed。

V1 不建立 Context/global Template cache。application 显式持有可复用 Template；
Definition 可以只在自身实例内 memoize immutable compile result，并因此自然承担
callback retention。关闭 application 对 Definition/Template 的引用就是唯一
retention release boundary，不使用 weak map、classloader-global registry 或
unbounded cache。

generated dataflow binding 使 generated/runtime compatibility 升级为
`soma-generated-runtime-v5` / `soma-runtime-java8-v5`；新增
`soma-transformation-v1` 与 `soma-kernel-v1`。Schema semantics 和
`RuntimePlan` 输入未改变，因此不改 schema hash 或 `soma-runtime-plan-v3`。
Transformation/Kernel protocol 后续独立版本化。

## 13. Module Topology

Stage 3 选择独立 production module `soma-dataflow`：

```text
soma-annotations -> soma-processor -> generated binding
soma-runtime-core <- soma-dataflow <- generated consumer
```

module topology 是语义和 lifecycle boundary 的部署投影，不拥有或重新定义
Transformation semantics。

runtime-core 保持 storage/access/lifecycle；`soma-dataflow` 拥有
Definition、analyzer/planner/execution/scratch/explain；processor 只生成
schema-specific binding/hook，不复制 executor。runtime-core 不依赖 processor，
transformation module 不解释 annotation，所有 production module 保持纯
Java 8/JVM heap-resident。

选择独立 module 的原因是 Transformation 的 Definition/Template/Invocation、
planner、parallel 和 Result 具有独立变化原因，放入 runtime-core 会让最底层
storage/lifecycle kernel 同时承担计算编排。反向拆成多个 operator module 则会
制造部署和 protocol 膨胀。

普通完整 SOMA consumer 增加 `soma-dataflow` runtime dependency；只消费既有
storage/access generated facade 的低层 consumer 可以不引用 `<Table>DataFlow`
companion，但 processor 在生成完整 schema artifact 时仍要求 dataflow API 位于
compile classpath。`soma-dataflow` 只依赖 `soma-runtime-core`，不依赖
annotations、processor、examples、benchmarks 或第三方 runtime。

footprint boundary 冻结为：每个 Table 最多一个 companion、每个 Schema 最多一个
registry/manifest extension；operator/expression/result/kernel 为 shared types，
不得按 `operator × Table` 展开。prototype 负责验证 dependency、public type、
source/class bytes 和 external Java 8 consumer；仅凭 LOC 不做裁决。

## 14. Codegen 与模块 Change Map

| Surface | 预计动作 |
|---|---|
| `soma-annotations` | 默认保持 storage/access annotation 纯净 |
| `soma-dataflow` | 新增 public Definition/Template/Context/Invocation、Expression/Result 与 internal analyzer/planner/kernel |
| runtime Scan | `GeneratedScanPlan` 保持 one-shot fast path；`GeneratedScanEvaluation` 与 graph scratch 分离 |
| runtime lifecycle | `DenseTableState` 保持 per-Table；`RuntimePlan` 保持 storage plan；只增加 aggregate instance identity 与窄 operation-guard bridge |
| processor/codegen | normalization/model/emitter 生成 typed facade/source/expression binding，不分析 callback、不按 operator × Table 展开 |
| generated access bridge | 复用 exact/unique semantics，只暴露 capability 和 specialized hook |
| protocol | generated/runtime 升 v5；transformation/kernel 建立独立 v1；schema hash/runtime-plan v3 不变 |
| `CodegenLimits` / checker | 增加 companion、source/class/type/compile footprint Gate |
| `soma-testkit` | reference oracle、legality、lifecycle、golden、external consumer |
| `soma-examples` | 原子迁移两 consumer，保留 application-owned structures |
| `soma-benchmarks` | build/analyze/compile/bind/execute/effect 与 parallel crossover |

JDBC/CDC connector、MES mapper、checkpoint 和 database publisher 不在 change map。

### 14.1 Clean-break 与兼容闭包

- annotation vocabulary 与 Schema semantic/hash 保持不变；
- generated v5 以同一次 processor/runtime/dataflow 版本原子发布，v4 generated
  artifact 与 v5 runtime fail closed，不提供双 protocol adapter；
- `<Table>DataFlow` 是 additive generated companion；现有 Scan/Point/Column/Bulk
  surface 只有在 Stage 4 证明命名或 shared bridge 必须调整时才 clean-break，并
  需要重新请求用户裁决；
- external consumer 必须分别证明 direct Access-only 使用与 dataflow 使用；
- golden 同时覆盖 schema JSON/hash 不变、generated manifest/protocol 改变、
  public `javap`、source/class/type footprint 和 clean/repeat determinism；
- examples、benchmarks、testkit、Implementation Map 和正式 Design 只在整个
  implementation candidate 端到端成立后原子迁移，不维护 v4/v5 双路径。

## 15. Controlled Architecture Prototype

Temporary-only prototype 位于
[`prototype/`](prototype/)，运行命令：

```text
sh docs/temp/soma-transformation-model-governance/prototype/run.sh
```

2026-07-26 在 Azul Zulu JDK 8 `1.8` 一次 bounded run 通过：

| Evidence | 结果 |
|---|---|
| module/dependency | API → generated companion → external consumer 分层独立编译，无第三方依赖 |
| Java 8 surface | Definition/Template/Context/Invocation、typed slot/binding 与 managed/borrowed executor 可编译运行 |
| multi-source lifecycle | aggregate ID canonical acquire、alias dedup、partial failure reverse cleanup 通过 |
| execution identity | direct 与 fixed-partition parallel count 一致；one-shot/cancel/worker-failure cleanup 通过 |
| executor ownership | managed close、borrowed never-shutdown 通过 |
| callback/cache | Definition-instance retention、无 global Template cache 通过 |
| footprint | compressed API prototype 13 class / 25,804 bytes；单 generated companion 2 class / 3,087 bytes |

该 prototype 不进入 reactor、production artifact 或性能声明。它只证明 Java 8
typing、dependency、lifecycle 和 bounded generated companion 方向可行；没有实现
真实 operator、Table guard 或 planner。现有 Candidate Scan fast path 继续由
v4 component/golden/external-consumer evidence 证明；Stage 4 每个 vertical slice
必须用真实 v5 binding 做 differential、code-size 和 component cost Gate，不能把
本 prototype 数字当成最终 footprint。

Prototype 第一次扩展 worker-failure test 暴露 `ForkJoinPool Future` 会包装
exception；只修正 cause-chain assertion 后复跑一次通过，没有循环调参或
benchmark。

## 16. 推荐实施依赖

以下是依赖顺序，不是缩减目标：

1. 冻结 Value/Expression/Operator/Effect 和 capability variant；
2. 用 internal prototype 证明 binding、IR、module、footprint 和 Scan fast path；
3. 冻结 public/generated surface、identity、resource、explain 和 clean-break plan；
4. 获得 production 实施授权；
5. 建立 Definition/Template/Invocation、analyzer、binding、shape-aware Result
   和 reference oracle 基础；
6. Slice A：闭合 single-source Selection/Projection/Aggregation/Prefix Scan、
   Candidate Scan specialization 和 detached Result；
7. Slice B：闭合 Join、GroupBy、Partition/Combine、finite Window 与相应只读
   Result；
8. Slice C：闭合 single-source Effect、MutationSet、Batch/Delta 与 Safe Point；
9. Slice D：让同一 Definition 经 sequential/parallel execution 保持完全相同的
   result、order、failure 和 Effect identity；
10. 原子迁移 example、benchmark、正式文档、Conformance、Report 和 checker。

每个 slice 同时拥有 typed contract、semantic analysis、IR/lowering、runtime
narrative、physical representation、negative/reference evidence、component cost
和 application trace。它必须独立正确并可保留，不能依赖后续消除 boxing、
修复 lifecycle 或替换 generic executor 才成立。

## 17. Stage 3 Architecture Feasibility

Stage 3 已关闭：

- D4–D6：controlled builder/opaque IR、Definition/Template/Invocation lifecycle、
  Invocation-owned typed binding 与 canonical multi-root read guard；
- D10、D13：static lowering + deterministic barrier specialization，以及
  Candidate Scan shared semantics/specialized executor；
- D12、D19：generated/runtime v5、transformation/kernel v1、独立
  `soma-dataflow` module 与 bounded companion；
- D15–D16：Context 只拥有执行资源、无 source/global cache，canonical identity
  与 callback retention；
- D20、D25–D26：detached/redacted explain、stats mode、hard budget、
  cancellation/failure priority 和 executor ownership；
- Safe Point/Delta 的 preflight、non-allocating commit、epoch 和现有失败原子性
  边界。

受控 prototype 与现有 v4 Scan/golden/external-consumer evidence 共同证明目标
架构可行，且没有要求 generic object hot path、第三方依赖、concurrent Table、
跨 root transaction 或新 lifecycle 语义。P2 physical algorithm 与 component
性能 candidate 已由 [Stage 5 正确性与性能证据](stage-5-evidence.md)闭合。
