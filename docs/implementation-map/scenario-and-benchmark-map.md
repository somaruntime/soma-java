# 场景与 benchmark 地图

类型：Implementation Map

状态：正式

Owner：SOMA scenario/benchmark 实现导航

对应 Blueprint：[产品蓝图](../blueprints/soma-java-product-blueprint.md)、[FJSP](../blueprints/fjsp-runtime-state-blueprint.md)、[VRP](../blueprints/vrp-runtime-state-blueprint.md)、[连续仿真](../blueprints/simulation-runtime-state-blueprint.md)、[Game](../blueprints/game-runtime-state-blueprint.md)

对应 Design：[Table、存储与访问](../design/table-storage-and-access.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[性能模型](../design/performance-model.md)

事实范围：当前四类示例、FJSP solver 和 benchmark runner 的代码入口

最近实现核对基线：`8f685e2`

最后审查日期：2026-07-23

## 1. 示例入口

| 场景 | 目标入口 | 当前 executable 入口 | 关键实现 |
|---|---|---|---|
| 总入口 | [产品蓝图](../blueprints/soma-java-product-blueprint.md) | [`ScenarioSuite.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/ScenarioSuite.java) | 运行四类 scenario |
| FJSP | [FJSP 蓝图](../blueprints/fjsp-runtime-state-blueprint.md) | [`FjspScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspScenario.java) | unique job-sequence access、完整 setup/import preflight、reusable frontier staging、FCFS/SPT indicator、application indexed machine heap、fail-stop solver |
| VRP | [VRP 蓝图](../blueprints/vrp-runtime-state-blueprint.md) | [`VrpScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java) | definition/assignment 分离、unique vehicle route、parent-owned visits、全 ordinal candidate projection、route-version stale guard 与 derived-workspace recovery |
| Simulation | [连续仿真蓝图](../blueprints/simulation-runtime-state-blueprint.md) | [`SimulationScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/SimulationScenario.java) | definition/vector 分离、nanosecond clock、application `PriorityQueue`、optional event projection、derivative staging、numeric fail-stop 与 trace export |
| Game | [Game 蓝图](../blueprints/game-runtime-state-blueprint.md) | [`GameScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/game/GameScenario.java) | definition/state 分离、keyed tile/occupancy、action generation/revision stale guard、cache rebuild、damage total order 与 primitive staging |

四个 executable journey 已采用当前 Blueprint 的 canonical data role、identity、顺序和失败边界。Blueprint 仍拥有目标，代码与本地图只拥有当前投影；后续任何偏差继续由 Conformance 识别。

## 2. Benchmark 入口

- smoke/all-lane runner：[`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkSmokeRunner.java)；
- packed/exact component runner：[`PostCutoverComponentBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java)；
- FJSP scale runner：[`FjspScaleBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspScaleBenchmark.java)；
- FJSP options/model/report：[`FjspBenchmarkOptions.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkOptions.java)、[`FjspBenchmarkMeasurement.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkMeasurement.java)、[`FjspBenchmarkReport.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkReport.java)；
- JVM/GC metrics：[`JvmRuntimeMetrics.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/JvmRuntimeMetrics.java)；
- smoke orchestration/compatibility facade：[`SmokeLaneSuite.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneSuite.java)；
- lane manifest、identity、metadata 与 validation：[`SmokeLaneContract.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneContract.java)；
- typed workloads 与 fixture execution：[`SmokeLaneWorkloads.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneWorkloads.java)；
- observation evidence 与 repeated-measurement merge：[`SmokeLaneEvidence.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneEvidence.java)、[`SmokeLaneAggregation.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneAggregation.java)。

`BenchmarkModel` 依赖 `SmokeLaneContract`，不反向依赖 suite；suite 保留 JSON schema lane binding 所需的窄 compatibility delegate，但不拥有 lane 事实。Source-shape checker 防止 manifest、workload、validation 与 aggregation 责任重新集中。

FJSP multi-fork allocation/GC诊断与component runner分别记录场景allocation/GC、Candidate Scan source/stage/terminal allocation和exact-index distinct-group retained payload；code-size runner同时输出 fixed-candidate Gate、逐 Scan artifact 与逐 schema footprint 诊断。2026-07-20 machine-selection A/B只作为application heap决策的历史证据。Smoke runner 的 `generated.exact_index_incremental_lookup` 使用 keyed `MachineCandidate` grouped exact access，`generated.dense_scratch_replace_sort` 使用无 maintained index 的 VRP insertion workspace；所有这些 artifact 均为`claimAllowed=false`诊断证据。

## 3. 追踪方式

Blueprint 决定场景要验证的目标 access pattern；Design 决定不可违反的语义；examples 提供 executable reference；benchmarks 隔离待测 lane；reports 只陈述 artifact 支持的结论。场景代码本身不拥有 core Design。
