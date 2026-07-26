# SOMA Transformation Execution Architecture

类型：Temporary

状态：active（execution-architecture candidate；Stage 3 feasibility pending）

Owner：SOMA Transformation execution architecture

正式事实源：否

事实范围：候选 contract/IR 分层、runtime roles 与叙事、binding、planner、
parallel、safe point、module、identity、resource、explain 和 change map

非事实范围：正式 public type/name、已完成实现、最终 physical algorithm 和性能结论

审查基线：`45fc992d46a6053a203c6d833624cf299269bf6e`

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
| Definition | immutable、可复用 | schema/source identity、expression、operator、effect、parameter schema | Table、current Index、Cursor、scratch |
| Compiled Template | immutable、可缓存 | validated graph、static lowering、候选 kernel、protocol identity | current Table state、executor、worker scratch |
| Execution Context | 长生命周期、显式关闭 | source registry、managed executor、template cache、global policy | active Invocation mutable state |
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

Execution Context 只是 application lifecycle 内的 composition root 和协调边界，
不是 source、executor、cache、policy 或 Result semantics 的共同事实源。
application 仍拥有 root 和调用并发权；Context 只能组合具有共同 application
lifecycle、但分别拥有 protocol/budget/failure/close 责任的组件，不能用万能
mutable manager 隐藏差异。prototype 若证明这些责任没有共同变化原因，就组合
独立组件而不继续扩大 Context。

### 3.2 Runtime 主叙事

Definition/Template lifecycle：

```text
author typed semantics
  -> normalize and analyze
  -> compile candidate kernels
  -> validate and publish immutable Template
  -> cache / invalidate / rebuild derived Template
```

Definition 本身不因 source cardinality、executor 或 epoch 改变；这些动态事实只
进入 Invocation。Template cache 失效不修改或替换 Definition semantics。

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

## 4. API 与 IR Boundary

| Surface | 候选责任 |
|---|---|
| public/generated | schema/table/column/source binding、typed expression/function/reducer contract、controlled Definition builder、Context/Policy/Budget、shape-aware Result/Delta/Command、non-hot-path Explain |
| internal | normalized IR、analyzer/property/cost、kernel/schedule/scratch、cache 和 generated-runtime bridge |

默认不公开 internal IR node hierarchy、physical strategy、kernel 或 mutable plan。
用户可以定义 reusable graph，但不能依赖 internal encoding。

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

Execution Context 把 logical source identity 解析为本次 Invocation 的
Table/Column/IndexSnapshot/application parameter。

multi-source read 至少满足：

1. 解析全部 source 并验证 schema/runtime compatibility；
2. 以 canonical order 取得各 source operation guard；
3. partial bind failure 时反向释放已取得 guard；
4. Invocation 期间不允许来源 Table 发生其他同步 operation；
5. terminal/cleanup 后反向释放；
6. 区分 bind、execute、effect 和 cleanup failure。

这只建立同一 single-owner context 内的同步 read boundary，不是 database
snapshot isolation、跨线程共享或跨 Table transaction。是否允许跨 schema
source，以及 context 如何登记独立 root，仍由 D6/D15 在 Stage 3 冻结。

## 6. Static Lowering 与 Bind-time Specialization

Static Lowering 只使用 schema/source/expression/operator/effect、logical
Shape/Traits/legality、generated access capabilities 和 kernel/runtime protocol。

Compiled Template 保存候选 fused region、barrier 和 kernel，不绑定 Table。

Bind-time Specialization 使用 cardinality/group/distinct/selectivity/skew、
available access path、actual order/partition、execution budget/policy 和
sequential/parallel crossover。

动态选择不能修改共享 Template，不能改变 logical result/failure，也不能依赖
hash iteration、线程完成顺序或不可重现的 global state。是否允许在 barrier 后
根据实际 cardinality 进行第二次 specialization，需要 Stage 3 明确。

## 7. Resource、Deadline 与 Cancellation

Transformation budget 独立于 storage `RuntimePlan`，覆盖 output cardinality、
invocation/worker scratch、retained result、task/worker、deadline/cancellation、
template/cache 和 explain/stats。

预算拒绝必须在 publish/effect 前 fail closed。deadline/cancellation 默认在
partition、barrier、task 和 terminal boundary 检查，不能为了响应速度在每个
element 强制增加 hot-path branch。

取消后停止新 task，bounded cancel/drain 已提交 worker，释放 scratch/guard，不
发布 partial Result/Effect，并以确定规则选择 primary/suppressed failure。

具体 check interval、threshold 和 task size 由 prototype/benchmark 决定。

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
  -> publish Runtime Epoch

Detached Result / Command
  -> application-owned publisher
```

Safe Point 不伪造跨 root transaction。Stage 3 必须冻结 preflight、apply order、
partial failure 的 fail-stop/rebuild、epoch publish 和 stale result 规则。
Keyed Delta 的 Insert/Update/Delete 必须先做 duplicate/version/presence/resource
全量预检；dense Table 不接受跨 operation current Index Delta。Detached-columnar
Result 重新导入也必须显式转为 Batch/Delta，不能被直接绑定为 writable live state。
JDBC/CDC、checkpoint、retry 和 publisher 属于独立 RTD synchronization topic。

## 11. Explainability 与 Observability

非 hot-path Explain 至少说明 logical graph/Shape/lineage/order/Effect，
fusion/barrier/materialization/access path，chosen/rejected kernel，
parallel/partition/fallback，cardinality/scratch/task/budget，以及 opaque
fence/deadline/cancellation/failure phase。

Graph execution stats 与 TableStats 分开，至少区分 build/analyze/compile/bind/
execute/effect/cleanup。Explain 的 API 名称和文本/结构化 encoding 可以在实现期
调整，但信息责任、redaction 和非 hot-path 边界必须在 Stage 3 冻结。

## 12. Identity、Cache 与 Compatibility

必须分离 Schema、Logical Definition、Expression/Function、Transformation
Protocol、Kernel Protocol、Planner Policy 和 Bound Source State/Epoch identity。

- Definition identity 使用 canonical graph/order/width/encoding；
- Template cache key 至少包含 schema、definition、expression、protocol、planner
  policy；
- executor instance 不属于 Definition/Template identity；
- bound cardinality、Table lifecycle token 和 Runtime Epoch 属于 Invocation；
- opaque callback 不伪造稳定 hash，只允许 definition-instance cache；
- identity mismatch 必须在执行前 fail closed。

generated binding 改变时升级 generated/runtime compatibility；storage plan 输入
未变时不滥升 `soma-runtime-plan-v3`。Transformation/Kernel protocol 独立版本化。

## 13. Module Topology Candidate

候选依赖方向：

```text
soma-annotations -> soma-processor -> generated binding
soma-runtime-core <- soma-dataflow <- generated consumer
```

暂用 `soma-dataflow` 表示新责任，不是已冻结 artifact 名。module topology 是
语义和 lifecycle boundary 的部署投影，不拥有或重新定义 Transformation
semantics。

runtime-core 保持 storage/access/lifecycle；transformation module 候选拥有
Definition、analyzer/planner/execution/scratch/explain；processor 只生成
schema-specific binding/hook，不复制 executor。runtime-core 不依赖 processor，
transformation module 不解释 annotation，所有 production module 保持纯
Java 8/JVM heap-resident。

D19 必须通过 dependency、public type count、compile/source/class footprint 和
external consumer prototype 后冻结。仅凭 LOC 不能决定是否增加 module。

## 14. Codegen 与模块 Change Map

| Surface | 预计动作 |
|---|---|
| `soma-annotations` | 默认保持 storage/access annotation 纯净 |
| runtime Scan | `GeneratedScanPlan` 保持 one-shot fast path；`GeneratedScanEvaluation` 与 graph scratch 分离 |
| runtime lifecycle | `DenseTableState` 保持 per-Table；`RuntimePlan` 保持 storage plan；上层组合 guard/policy |
| processor/codegen | normalization/model/emitter 生成 typed facade/source/expression binding，不分析 callback、不按 operator × Table 展开 |
| generated access bridge | 复用 exact/unique semantics，只暴露 capability 和 specialized hook |
| `CodegenLimits` / checker | 增加 source/class/type/compile footprint Gate |
| `soma-testkit` | reference oracle、legality、lifecycle、golden、external consumer |
| `soma-examples` | 原子迁移两 consumer，保留 application-owned structures |
| `soma-benchmarks` | build/analyze/compile/bind/execute/effect 与 parallel crossover |

JDBC/CDC connector、MES mapper、checkpoint 和 database publisher 不在 change map。

## 15. 推荐实施依赖

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
