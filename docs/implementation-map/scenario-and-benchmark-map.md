# 参考应用与 benchmark 地图

类型：Implementation Map

状态：正式

Owner：SOMA reference application / benchmark 实现导航

对应 Blueprint：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

对应 Design：[系统架构](../design/system-architecture.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[性能模型](../design/performance-model.md)

事实范围：当前两个独立参考应用、领域中性 benchmark 和各自 evidence 的代码入口

最近实现核对基线：reference application architecture `69e5dc6` / `287350d`；
performance baseline implementation `5be618a`

最后审查日期：2026-07-24

## 1. 聚合与应用入口

[`soma-examples/pom.xml`](../../soma-examples/pom.xml) 只聚合两个 child project，不产出领域共享 JAR，也不向 child 注入 parent、dependency management 或 runtime shortcut。

| Application | 自有文档 | executable / runtime | input 与验证 |
|---|---|---|---|
| industrial dynamic scheduler | [application docs](../../soma-examples/industrial-dynamic-scheduler/docs/README.md) | [`SchedulerApplication.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/application/SchedulerApplication.java)、[`SomaSchedulingSolver.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/solver/SomaSchedulingSolver.java)、[`SchedulerRuntime.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime/SchedulerRuntime.java) | [`SyntheticSchedulingProblemFactory.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/problem/SyntheticSchedulingProblemFactory.java)、[`SchedulerRuntimeFactory.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/com/hgtech/soma/examples/scheduler/runtime/SchedulerRuntimeFactory.java)、[`SchedulerVerification.java`](../../soma-examples/industrial-dynamic-scheduler/src/test/java/com/hgtech/soma/examples/scheduler/verification/SchedulerVerification.java) |
| grassing individual simulation | [application docs](../../soma-examples/grassing-individual-simulation/docs/README.md) | [`SimulationApplication.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/SimulationApplication.java)、[`Simulator.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/simulation/Simulator.java)、[`SimulationSession.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/simulation/SimulationSession.java)、[`SomaSimulator.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/simulation/SomaSimulator.java)、[`SimulationResult.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/result/SimulationResult.java) | [`SyntheticSimulationScenarioFactory.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/scenario/SyntheticSimulationScenarioFactory.java)、[`SimulationRuntimeFactory.java`](../../soma-examples/grassing-individual-simulation/src/main/java/com/hgtech/soma/examples/grassing/runtime/SimulationRuntimeFactory.java)、[`SimulationVerification.java`](../../soma-examples/grassing-individual-simulation/src/test/java/com/hgtech/soma/examples/grassing/evidence/SimulationVerification.java) |

两个 child POM 都是普通 Java 8 consumer，只声明 `soma-annotations`、
`soma-runtime-core` 和 compile-time `soma-processor`。工业调度应用采用
`application/config/problem/solver/runtime/result/schema` 分层；个体生态仿真采用
`config/scenario/simulation/runtime/result/schema/support` 分层。两个应用的
fixture/oracle/verification/benchmark 均位于 test source-set，production JAR
不含 evidence implementation。版本化 config 与 detached factory 拥有输入，
runtime hot loop 不反向依赖 generator 或 factory。

## 2. Benchmark 入口

- smoke runner / validator：[`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkSmokeRunner.java)、[`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkArtifactValidator.java)；
- neutral component runner / validator：[`PostCutoverComponentBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java)、[`PostCutoverComponentArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentArtifactValidator.java)；
- baseline parser / comparator：[`PerformanceBaselineDefinition.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineDefinition.java)、[`PerformanceBaselineComparator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PerformanceBaselineComparator.java)；
- lane contract、workload、evidence 与 aggregation：[`SmokeLaneContract.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneContract.java)、[`SmokeLaneWorkloads.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneWorkloads.java)、[`SmokeLaneEvidence.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneEvidence.java)、[`SmokeLaneAggregation.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneAggregation.java)；
- neutral schema：[`schema`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/schema)；
- application-integrated evidence：[`SchedulerBenchmark.java`](../../soma-examples/industrial-dynamic-scheduler/src/test/java/com/hgtech/soma/examples/scheduler/benchmark/SchedulerBenchmark.java)、[`SimulationBenchmark.java`](../../soma-examples/grassing-individual-simulation/src/test/java/com/hgtech/soma/examples/grassing/evidence/SimulationBenchmark.java)。

`soma-benchmarks` 不依赖或导入 reference application domain。它只测 SOMA component mechanics；真实应用的 allocation、GC、runtime high-water 和 correctness guard 由 application 自有 runner/Gate 负责。全部 smoke/diagnostic artifact 保持 `claimAllowed=false`。

当前三份 checked-in baseline 分别位于：

- [`component baseline`](../../soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/post-cutover-component-zulu8-macos-aarch64-v1.json)；
- [`scheduler baseline`](../../soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json)；
- [`simulation baseline`](../../soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json)。

Comparator 只拥有领域中性协议。两个应用各自拥有 workload identity、阈值和
test-resource baseline，POM 不依赖 `soma-benchmarks`；baseline 不进入 production
JAR。当前没有 public performance claim。

## 3. Gate

- artifact isolation：[`check-reference-applications.sh`](../../scripts/check-reference-applications.sh)；
- scheduler correctness/long-run/multi-fork：[`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh)；
- simulation oracle/long-run/multi-fork：[`check-grassing-simulation.sh`](../../scripts/check-grassing-simulation.sh)；
- neutral smoke/component：[`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-post-cutover-components.sh`](../../scripts/check-post-cutover-components.sh)；
- baseline Owner/层次防回归：[`check-performance-baseline-architecture.sh`](../../scripts/check-performance-baseline-architecture.sh)；
- generated footprint：[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)。

三个专项脚本都调用同一 comparator；component 普通 Gate 为 5 fork，两个应用各为
3 fork。环境匹配时判定 `passed/failed`，环境不同但 artifact 合法时为
`not-applicable`。Blueprint 和 Design 决定产品目标与语义；应用拥有领域
correctness，benchmark 拥有测量 artifact，本地图只导航当前实现。
