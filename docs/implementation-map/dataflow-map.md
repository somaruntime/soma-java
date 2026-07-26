# DataFlow 实现地图

类型：Implementation Map

状态：正式

Owner：SOMA DataFlow 实现导航

对应 Design：[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)

事实范围：当前 DataFlow public roles、generated binding、execution、effect、并行和验证入口

非事实范围：规范性 Transformation 语义、完整 public signature 清单和性能结论

最近实现核对基线：commit `2aa8c15`

最后审查日期：2026-07-27

## 1. Production module

[`soma-dataflow`](../../soma-dataflow) 当前是独立 Java 8 production module，只依赖 `soma-runtime-core`。主要入口：

| 责任 | 当前入口 |
|---|---|
| Definition/Template/Invocation | [`DataFlowDefinition.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowDefinition.java)、[`DataFlowTemplate.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowTemplate.java)、[`DataFlowInvocation.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowInvocation.java) |
| Context/policy/resource | [`DataFlowContext.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowContext.java)、[`ExecutionPolicy.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/ExecutionPolicy.java)、[`ExecutionBudget.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/ExecutionBudget.java) |
| typed Shape/Expression | `CandidateFlow`、`*ValueFlow`、`GroupedFlow`、`JoinedFlow`、`WindowedFlow`、`*Expression` |
| result/effect | primitive scalar/columnar、group/join/window/expand result、`DeltaApplyResult`、candidate effect operations |
| generated bridge | [`com.hgtech.soma.dataflow.generated`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/generated) |
| diagnostics | [`DataFlowStats.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowStats.java)、[`DataFlowExplain.java`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowExplain.java) |

当前 public API 以 shape-specific Java types 排除非法组合；内部 `DataFlowProgram`、`CandidateProgram`、parallel execution、group/join/window plan 和 expression node 不作为 SPI。

## 2. Generated projection

Processor 的 [`DenseDataFlowSourceEmitter.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseDataFlowSourceEmitter.java) 为每张 Table 生成一个 `<Table>DataFlow` companion；[`DenseTableSourceEmitter.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceEmitter.java) 写入 lifecycle/access bridge 和 keyed Delta apply。Generated manifest、`javap` 与 external consumer 拥有精确 surface。

当前 identity：

- generated/runtime `v5`；
- transformation/kernel `v1`；
- runtime plan仍为 `v3`，Schema hash 语义未变化。

## 3. 执行叙事

```text
typed Definition
  -> immutable Template
  -> one-shot Invocation
  -> generated source binding / canonical aggregate guards
  -> candidate specialization or graph barriers
  -> sequential / adaptive parallel execution
  -> detached Result or single-source safe-point Effect
```

Candidate `skip/limit` 将 selection capacity 上界下推到 streaming selection。Parallel 使用 managed 或 borrowed executor；sequential 是 oracle，default adaptive crossover 当前由 evidence 选择，物理常量不在本地图复制。

## 4. 验证入口

- semantic vertical slices：[`check-dataflow-slice-f.sh`](../../scripts/check-dataflow-slice-f.sh)；
- property/reference differential：[`check-dataflow-reference.sh`](../../scripts/check-dataflow-reference.sh)；
- external/generated/golden：既有 public、codegen、dense/keyed/access/child/breadth 和 external consumer Gates；
- footprint：[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)；
- component performance：[`check-dataflow-performance.sh`](../../scripts/check-dataflow-performance.sh)；
- industrial application trace：[`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh) 及 application profile Gates。
