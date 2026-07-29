# DataFlow 实现地图

类型：Implementation Map

状态：正式

Owner：SOMA DataFlow 实现导航

对应 Design：[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)

事实范围：当前 DataFlow public roles、generated binding、execution、effect、并行和验证入口

非事实范围：规范性 Transformation 语义、完整 public signature 清单和性能结论

最近实现核对基线：包含本文件的 V1 `1.0.0` private-source sign-off commit；
精确commit由Git与同SHA qualification artifact记录

最后审查日期：2026-07-29

## 1. Production module

[`soma-dataflow`](../../soma-dataflow) 当前是独立 Java 8 production module，只依赖 `soma-runtime-core`。主要入口：

| 责任 | 当前入口 |
|---|---|
| Definition/Template/Invocation | [`DataFlowDefinition.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowDefinition.java)、[`DataFlowTemplate.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowTemplate.java)、[`DataFlowInvocation.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowInvocation.java) |
| Context/policy/resource | [`DataFlowContext.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowContext.java)、[`ExecutionPolicy.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/ExecutionPolicy.java)、[`ExecutionBudget.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/ExecutionBudget.java)、internal `InvocationLedger` |
| typed Shape/Expression | `CandidateFlow`、primitive/String `*ValueFlow`、`GroupedFlow`、`JoinedFlow`、`WindowedFlow`；raw numeric/boolean/String与logical `EnumExpression`/`DateExpression`/`TimeExpression`/`InstantExpression`；无 generic Object value family |
| result/effect | Eager Detached primitive scalar/columnar、group/join/window/expand result、`DeltaApplyResult`、candidate effect operations；callback-scoped `*Visitor` delivery |
| physical choice | [`CandidatePhysicalFormula.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/CandidatePhysicalFormula.java)、[`RelationStrategyFormula.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/RelationStrategyFormula.java)、[`MorselSchedulerFormula.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/MorselSchedulerFormula.java)、internal `ClosedNumericKernel` 与 `JoinRuntimeFilter` |
| generated bridge | [`io.github.somaruntime.soma.dataflow.generated`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/generated) |
| diagnostics | [`DataFlowStats.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowStats.java) 的 work/parallel/resource/delivery components 与 [`DataFlowExplain.java`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow/DataFlowExplain.java) |

当前 public API 以 shape-specific Java types 排除非法组合；内部 `DataFlowProgram`、`CandidateProgram`、parallel execution、group/join/window plan 和 expression node 不作为 SPI。

## 2. Generated projection

Processor 的 [`DenseDataFlowSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseDataFlowSourceEmitter.java) 为每张 Table 生成一个 `<Table>DataFlow` companion；[`DenseTableSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseTableSourceEmitter.java) 写入 lifecycle/access bridge 和 keyed Delta apply。Generated manifest、`javap` 与 external consumer 拥有精确 surface。

当前 identity：

- generated/runtime `v12`；
- transformation `v4`、kernel `v5`、planner `v4`；
- runtime plan为 `v6`，storage与primary-locator layout formula均为`v1`，
  Candidate/relation formula为`v2`，morsel/Invocation ledger formula为`v1`，
  Schema hash语义未变化。

## 3. 执行叙事

```text
typed Definition
  -> immutable Template
  -> one-shot Invocation
  -> generated source binding / canonical aggregate guards
  -> typed cardinality/resource preflight
  -> closed candidate/relation specialization or explicit graph barrier
  -> one bounded adaptive morsel scheduler
  -> Eager Detached / callback-scoped delivery / safe-point Effect
  -> phase leases drained and detached component stats
```

Candidate closed shapes为contiguous range、segment-aware range、exact
single-pass、formula-bound bitmap intersection和sparse indexes；universal
IndexBuffer不再是全部terminal的默认物理表示。Required long-column constant
arithmetic/comparison common chain可由closed whole-loop kernel直接执行packed
visit/count/select；reference graph保留oracle与fallback。Group/Join/Window按
closed strategy预聚合、probe或bounded enumeration，
Expand的known overflow、over-budget及unknown-unprovable cardinality均在枚举和
callback前拒绝。Candidate `skip/limit` 将selection capacity上界下推到streaming
selection。
Packed callback scan按 Effective Plan 的物理 Segment 使用外层 loop；这不改变
Candidate logical sequence。Parallel使用managed或borrowed executor；storage
Segment、execution vector和parallel morsel相互独立，单Segment中型输入也可拆成
morsel。Sequential是oracle，formula基于cardinality/cost/budget/workers选择
crossover；opaque callback保持sequential。

`ResultDeliveryMode.EAGER_DETACHED`仍是默认；Candidate/Value/Group/Join/Window
只通过同步callback-scoped visitor提供惰性试点。Visitor只在Invocation read
scope内有效，支持bounded early stop、consumer failure、cancel/deadline和
non-escape guard；普通`Iterator`、pull cursor、Publisher、async push与partial
detached output没有进入surface。

Invocation仍按root opaque identity排序并canonical acquire，而不是按Group合并guard。
因此同一Group内不同root、跨Group、同schema多实例、跨schema和self alias保持同一
multi-source语义。部分acquire失败时按已获得root逆序释放；Group membership只提供
composition/lifecycle，不成为Join prerequisite或跨root transaction。

Primitive单分量Join可在一次Invocation内建立min/max或Bloom build-side filter；
filter只排除确定不匹配的probe，所有保留候选仍经过hash/full equality。String、
复合Key、dense/high-hit或收益不足形态直接使用baseline relation path，filter
scratch由Invocation ledger计量并在结束时释放。

## 4. 验证入口

- capability contracts：[`check-dataflow-contracts.sh`](../../scripts/check-dataflow-contracts.sh)，
  一次编译后分别验证 Invocation、Selection/Value、Relation、Mutation、
  Execution、Point/Delivery 与 Shape/Graph；
- property/reference differential：[`check-dataflow-reference.sh`](../../scripts/check-dataflow-reference.sh)；
- external/generated/golden：既有 public、codegen、dense/keyed/access/child/breadth 和 external consumer Gates；
- footprint：[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)；
- component performance：[`check-dataflow-performance.sh`](../../scripts/check-dataflow-performance.sh)；
- application trace：[`check-real-time-dispatch-rule-engine.sh`](../../scripts/check-real-time-dispatch-rule-engine.sh)及三个reference application profile Gates；
- production-scale：[`check-runtime-scale-qualification.sh`](../../scripts/check-runtime-scale-qualification.sh)的relation、parallel、expansion、delivery与soak lanes。
