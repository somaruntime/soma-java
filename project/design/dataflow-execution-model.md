# DataFlow 执行模型设计

类型：Design

状态：正式

Owner：SOMA typed DataFlow execution

设计层次：`D2` 能力设计

主要关注点：Definition、Template、Invocation、binding、资源、并行、safe point 与执行 identity

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprint/soma-java-product-blueprint.md)

事实范围：Ad-hoc DSL 与 reusable DataFlow 的执行角色、lifecycle、资源、并行、failure 和协议边界

非事实范围：Operator 逻辑语义、public overload 清单、内部 IR/数组布局、物理阈值和测量数值

最后审查日期：2026-07-29

## 1. 一套语义、两种使用形态

Ad-hoc DSL Operation 面向局部 one-shot、低 plan cost；Reusable DataFlow 面向反复执行的 RTD rule、算法步骤、simulation/game system。二者共享 [Transformation Model](transformation-model.md) 的 Shape、Expression、Operator、Result、Effect、failure 和 legality，可以选择不同 fast path，不能形成两套 correctness model。

```text
Generated DSL / controlled Builder
  -> immutable Definition
  -> analyzer / static lowering
  -> immutable Template
  -> bound one-shot Invocation
  -> bind-time specialization
  -> sequential / parallel kernel
  -> Result or deterministic Effect commit
```

Application rule graph、SOMA logical Definition 和 physical schedule 是不同对象。Public DSL 不是 IR，internal node/kernel/strategy 不是 application SPI。

## 2. Runtime Roles

| Role | 生命周期与 Owner | 可以持有 | 禁止持有 |
|---|---|---|---|
| `DataFlowDefinition` | immutable、可复用 | typed source/expression/operator/effect、parameter schema、logical identity | Table、current Index、Cursor、scratch |
| `DataFlowTemplate` | immutable、可复用 | validated graph、static lowering、candidate kernels、protocol identity | current Table state、executor、worker scratch |
| `DataFlowContext` | application-owned、显式 close | managed/borrowed executor、默认 policy/budget、active accounting | Table registry、Definition、implicit global cache |
| `DataFlowInvocation` | one-shot、非线程共享 | bound sources/tokens、parameters、policy、budget、scratch | 跨 terminal 复用 |
| Result/Diagnostics | Eager detached 或明确 callback-scoped | scalar、columnar/materialized result、DeliveryResult、stats/explain | 虚假的 source snapshot 稳定性、pull/async result |

Definition/Template 可以共享；Invocation、Cursor 和 mutable scratch 不共享。V1 不提供 Context/global Template cache；application 显式持有 Template，释放引用就是 retention 边界。

## 3. 构造与生命周期

Definition Builder 是 one-shot：

```text
OPEN -> PUBLISHED | FAILED
```

`build()` 在一次 publish 前完成 source、parameter、output、effect、identity 和 graph legality 检查；成功或失败后都不可继续修改。Compile 失败不污染 Definition。

Invocation 是 one-shot：

```text
NEW -> BINDING -> READY -> RUNNING -> COMMITTING -> COMPLETED
                   \          \           \
                    \----------> FAILED / CANCELLED
```

- terminal 被接受后即 consumed，失败/取消后不可重试；
- bind failure 不启动 worker或留下 partial guard；
- execute/cancel failure bounded drain，不发布 partial detached Result/Effect；
- preflight failure 不修改 live state；
- cleanup 不覆盖更早 primary failure；
- borrowed result 只在 RUNNING 内消费，detached result 在 guard/scratch cleanup 后发布。

上述 lifecycle、compatibility、budget、ownership 和 publish 条件若失配会破坏语义，必须使用真实 failure，不能依赖可关闭的 assertion。

### 3.1 Callback Result Delivery lifecycle

callback-scoped streaming 是唯一 Lazy Output execution。Generated
`CallbackDeliveryDefinition/Template/Invocation<V>` 只是标准
Definition/Template/Invocation 的 typed facade，必须使用相同 analyzer、identity、
explain、binding、guard、budget、failure 和 one-shot state machine；不得暗中
compile或创建第三 invocation type。

Visitor 是 typed Invocation parameter，Definition/Template 不持有 consumer，
consumer instance 不进入其 identity。`false` 表示消费当前 value 后 early stop；
`DeliveryResult` 只发布 `deliveredElements/completed`。Callback exception、cancel、
deadline、source conflict 或 cleanup failure 不返回 partial DeliveryResult。

现有 Candidate/Value/Group/Join/Window borrowed traversal 必须迁入这一 lifecycle，
不保留 legacy consumer-in-Definition path。Cursor/guard/use-after-callback fail
closed；String getter 的 immutable value可以保留。Join visitor 必须表达 left/right
Cursor、outer absence、logical order 与共同 callback lifetime。Opaque visitor
sequential 执行，外部 side effect 不由 SOMA 回滚。

## 4. Generated Binding 与模块边界

`soma-dataflow` 独立拥有 Definition、Template、Invocation、Context、typed expression/result、analyzer、planner、kernel 和 diagnostics。`soma-runtime-core` 继续拥有 storage/access/lifecycle；processor 为每张 Table 最多生成一个 `<Table>DataFlow` companion，提供 typed Source、column expression、point/exact/owned-child capability 和窄 binding。

```text
soma-annotations -> soma-processor -> schema-specific companion
soma-runtime-core <- soma-dataflow <- generated consumer
```

- runtime/dataflow 不解释 annotation metadata；
- processor 不分析 lambda/method body；
- 不按 `operator × Table` 生成 executor/type；
- existing Point、Scan、ColumnView、Batch 和 child facade 保持 canonical direct access；
- generated public surface 不暴露 backing array、raw handle 或 internal IR。

普通完整 consumer 在 runtime classpath 增加 `soma-dataflow`。只调用既有 direct access 的源码仍可不引用 DataFlow type，但完整 schema generation 的 compile classpath 必须具备 companion 所需 API。

## 5. Binding 与多 Source 读取

Definition 声明 typed source slot；Invocation 显式绑定 Table/Column/IndexSnapshot/parameter，Context 不查找或保存 source。

Multi-source bind：

1. 先解析全部 required slot，拒绝 missing/extra/wrong-schema；
2. 校验 schema、generated/runtime/transformation/kernel protocol；
3. 允许同 Group、cross-Group、implicit Group、cross-schema、同 Table descriptor
   不同 instance 和 self-alias；同一 physical ownership aggregate 的 logical alias
   去重；
4. 按 overflow-safe aggregate instance identity 升序取得 guard；
5. partial acquire failure 反向释放；
6. Invocation 期间禁止来源 aggregate 的其他 operation；
7. terminal/cleanup 反向释放并区分 bind、execute、effect、cleanup failure。

这只是 application 独占下的同步 read boundary，不是 snapshot isolation、并发
Table API 或跨 Table transaction。Invocation 是 source validation、guard sort/
acquire/reverse-release 的唯一 Owner；SomaGroup 只提供 stable member/aggregate
identity。Child 必须经 root binding/owned expansion 解析，不能绕过 parent lifecycle。

## 6. Lowering 与物理选择

Analyzer 是 Value、Shape、operator、failure legality 的唯一执行语义 Owner。Candidate single-source region 降低为 compact candidate program，并复用 zero-stage、exact、streaming、best-one 和 mutation specialization；只有 Projection、multi-source 或 barrier 才进入 graph schedule。

Required numeric column、constant arithmetic 与 comparison 组成的 common chain
可以降低为 closed whole-loop kernel，由 kernel 拥有 outer candidate loop、
boundary check 与 primitive evaluation；reference expression graph 继续作为
correctness oracle 和不适用形态的 fallback。Optional presence、registered/opaque
callback、cross-column 或 String chain 在未证明等价前保持 reference path，不能用
另一层逐 row virtual wrapper冒充 fusion。

Template 只保存静态候选。Bind-time 可以根据 cardinality、group/distinct/selectivity、actual order、available access path、budget 和 policy，在预先兼容的 kernel 中确定性选择；不能：

- 修改共享 Template；
- 试跑多个 kernel或用 wall-clock 自调优；
- 依赖 hash iteration、worker completion 或跨 Invocation 隐式学习；
- 改变 logical result、order、failure 或 Effect。

Physical Hash Join、primitive min/max/Bloom runtime filter、low-cardinality Bitmap
intersection、fixed-tree reduction、branchy loop、buffered partition 和
scalar/parallel crossover 都是内部选择，不进入 logical API。Runtime filter 和
Bitmap 必须由 versioned formula 确定、受 Invocation/storage budget 约束、进入
Explain，并保留 authoritative equality 与 deterministic fallback。

## 7. Resource、Cancellation 与 Diagnostics

`ExecutionBudget` 与 storage `RuntimePlan` 分离，至少约束 output elements/bytes、invocation/worker scratch、tasks/workers、deadline/cancellation 和 stats/explain。Context default 是 immutable upper bound，Invocation 只能收紧。

- 无可证明上界的 Join expansion、Group、Window、materialization 和 task fan-out必须显式预算；
- compiler/plan 或 validated maintained facts 无法证明 finite scratch/output bound
  时，除独立有界 scalar/fused terminal 外，在 relation enumeration/state
  allocation/callback 前以 resource failure拒绝；
- deadline 使用 monotonic time，cancellation token 对 caller/worker 具有明确
  cross-thread visibility；在 morsel/vector、barrier、task 和 terminal boundary
  检查，避免 per-element 强制分支；
- 失败优先选择 bind/preflight，其次最小 logical partition ordinal，再处理 cancellation/deadline/effect/cleanup；
- stats 区分 build/analyze/compile/bind/execute/effect/cleanup；
- explain 说明 shape/lineage/order/barrier/access path/kernel/parallel/fallback/budget，但不输出业务值、Key、callback `toString()` 或 executor detail；
- Stats/Explain 是 detached diagnostics，不参与 Definition identity或业务结果。

## 8. Parallel Execution

```text
Storage Segment
  -> zero / one / many Parallel Morsels
       -> cache/JIT-oriented Execution Vectors

Sequential
AdaptiveParallel
  -> SOMA-managed bounded executor
  -> caller-provided ExecutorService / ForkJoinPool
```

- Sequential 不创建或借用 worker；
- AdaptiveParallel 可以按成本确定性回退 Sequential；
- managed executor 由 Context 持有、bounded、可复用并在成功 close 时关闭；
- borrowed executor 永不由 SOMA shutdown/interrupt；
- common pool 只有 caller 显式传入时才使用；
- application 仍必须独占 source aggregate；parallel worker 是一次同步 Invocation 内部细节。
- Segment 是 storage/growth/GC unit，不是固定 task；大 Segment 可以拆成多个
  morsel，多个小 Segment 可以合并；
- Morsel 是 scheduling/cancel/fixed-order merge unit，Execution Vector 是
  inner-loop block；全部 morsel 进入同一个 bounded scheduler，不建立 nested
  executor/common-pool fallback；
- task count 可以大于 workers，但受 `maximumTasks` 约束并按 bounded waves执行；

最低并行能力是 deterministic contiguous partition、pure/fused kernel、fixed-tree mergeable reduction、independent pure branch、parallel stage + deterministic single-source commit。Opaque callback、built-in floating left fold、短路/顺序无法等价的 operator 必须 sequential fallback。

Worker 使用 cache-line-disjoint scratch/stats/partial state，按 logical morsel ordinal
merge；stable output、first、skip/limit、tie、Prefix Scan offset 和 failure identity
不依赖完成顺序。Worker 不直接竞争 swap-remove、Index maintenance 或 shared
Table write。

direct/parallel 使用 versioned deterministic cost formula，至少消费 rows、operator、
touched width、expression/hash cost、selectivity、scratch、workers 和 memory-
bandwidth proxy；不能只看 Segment 数或固定 row threshold。Small/Medium 保持
direct fast path，单 Segment 也可以在 estimated work 足够时拆 morsel。

## 9. Effect Commit 与 Safe Point

```text
compute candidate
  -> freeze MutationSet
  -> validate target/resource/access path
  -> deterministic non-allocating commit
  -> publish result/stats once
```

Safe Point 是目标 ownership aggregate 已取得 exclusive guard 且没有 active borrow/Invocation 的状态，不是 Context-wide global pause。所有可能失败的 allocation、growth、equality/conflict 和 derived-access preparation 必须在 commit 前完成。

Keyed Delta 在 guard 内完整预检 target/schema/epoch、Key/presence/version、duplicate、capacity、unique/exact/child 和 scratch budget；成功按 declaration order apply。Multi-root command 由 application 依序调用多个 single-aggregate safe point，SOMA 不承诺全部成功或自动补偿。

## 10. Identity 与兼容性

必须分离：

- schema/hash；
- logical Definition identity；
- registered function/reducer semantic identity；
- generated/runtime protocol；
- transformation/kernel protocol；
- planner policy；
- bound source lifecycle/epoch。

Definition 使用 canonical、length-prefixed encoding 和 SHA-256；Template identity 追加 transformation/kernel/generated-runtime/planner policy。Executor instance、bound cardinality 和 epoch不进入 Definition。

当前协议：

```text
generated/runtime  soma-generated-runtime-v12 / soma-runtime-java8-v12
transformation     soma-transformation-v5
kernel             soma-kernel-v6
storage plan       soma-runtime-plan-v6
storage formula    soma-storage-layout-v1
locator formula    soma-primary-locator-layout-v1
candidate formula  soma-candidate-physical-v2
relation formula   soma-relation-strategy-v2
scheduler formula  soma-morsel-scheduler-v1
invocation ledger  soma-invocation-ledger-v1
```

Mismatch 在执行前 fail closed；不提供旧协议双 adapter、反射 fallback 或 best-effort execution。
