# 参考应用与 benchmark 地图

类型：Implementation Map

状态：正式

Owner：SOMA reference application / benchmark 实现导航

对应 Blueprint：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

对应 Design：[系统架构](../design/system-architecture.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[性能模型](../design/performance-model.md)

事实范围：当前两个独立参考应用、领域中性 benchmark 和各自 evidence 的代码入口

最近实现核对基线：commit `955c956`

最后审查日期：2026-07-23

## 1. 聚合与应用入口

[`soma-examples/pom.xml`](../../soma-examples/pom.xml) 只聚合两个 child project，不产出领域共享 JAR，也不向 child 注入 parent、dependency management 或 runtime shortcut。

| Application | 自有文档 | executable / runtime | input 与验证 |
|---|---|---|---|
| industrial dynamic scheduler | [application docs](../../soma-examples/industrial-dynamic-scheduler/docs/README.md) | [`SchedulerApplication.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/SchedulerApplication.java)、[`IndustrialScheduler.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime/IndustrialScheduler.java)、[`SchedulerRuntime.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime/SchedulerRuntime.java) | [`SchedulingProblemGenerator.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/problem/SchedulingProblemGenerator.java)、[`SchedulerRuntimeBootstrap.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime/SchedulerRuntimeBootstrap.java)、[`SchedulerVerification.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/evidence/SchedulerVerification.java) |
| grassing individual simulation | [application docs](../../soma-examples/grassing-individual-simulation/docs/README.md) | [`SimulationApplication.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/SimulationApplication.java)、[`SimulationEngine.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/runtime/SimulationEngine.java)、[`SimulationRuntime.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/runtime/SimulationRuntime.java) | [`InitialStateGenerator.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/model/InitialStateGenerator.java)、[`SimulationRuntimeBootstrap.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/runtime/SimulationRuntimeBootstrap.java)、[`SimulationVerification.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/evidence/SimulationVerification.java) |

两个 child POM 都是普通 Java 8 consumer，只声明 `soma-annotations`、`soma-runtime-core` 和 compile-time `soma-processor`。版本化 config 与 detached generator 拥有输入；bootstrap 建立 authoritative runtime，hot loop 不反向依赖 generator。

## 2. Benchmark 入口

- smoke runner / validator：[`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkSmokeRunner.java)、[`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkArtifactValidator.java)；
- neutral component runner / validator：[`PostCutoverComponentBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java)、[`PostCutoverComponentArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentArtifactValidator.java)；
- lane contract、workload、evidence 与 aggregation：[`SmokeLaneContract.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneContract.java)、[`SmokeLaneWorkloads.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneWorkloads.java)、[`SmokeLaneEvidence.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneEvidence.java)、[`SmokeLaneAggregation.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneAggregation.java)；
- neutral schema：[`schema`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/schema)；
- application-integrated evidence：[`SchedulerBenchmark.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/evidence/SchedulerBenchmark.java)、[`SimulationBenchmark.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/evidence/SimulationBenchmark.java)。

`soma-benchmarks` 不依赖或导入 reference application domain。它只测 SOMA component mechanics；真实应用的 allocation、GC、runtime high-water 和 correctness guard 由 application 自有 runner/Gate 负责。全部 smoke/diagnostic artifact 保持 `claimAllowed=false`。

## 3. Gate

- artifact isolation：[`check-reference-applications.sh`](../../scripts/check-reference-applications.sh)；
- scheduler correctness/long-run/multi-fork：[`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh)；
- simulation oracle/long-run/multi-fork：[`check-grassing-simulation.sh`](../../scripts/check-grassing-simulation.sh)；
- neutral smoke/component：[`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-post-cutover-components.sh`](../../scripts/check-post-cutover-components.sh)；
- generated footprint：[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)。

Blueprint 和 Design 决定产品目标与语义；应用拥有领域 correctness，benchmark 拥有测量 artifact，本地图只导航当前实现。
