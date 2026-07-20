# 场景与 benchmark 地图

类型：Implementation Map

状态：正式

Owner：SOMA scenario/benchmark 实现导航

对应 Blueprint：[Blueprint 导航](../blueprints/README.md)

对应 Design：[Table、存储与访问](../design/table-storage-and-access.md)、[性能模型](../design/performance-model.md)

事实范围：当前四类示例、FJSP solver 和 benchmark runner 的代码入口

最近实现核对基线：`b991f4c`

最后审查日期：2026-07-20

## 1. 示例入口

| 场景 | 当前 executable 入口 | 关键实现 |
|---|---|---|
| 总入口 | [`ScenarioSuite.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/ScenarioSuite.java) | 运行四类 scenario |
| FJSP | [`FjspScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspScenario.java) | [`FjspSolver.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspSolver.java)、[`FjspCandidateFrontier.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspCandidateFrontier.java)、application-owned `FjspMachineAvailabilityQueue`、schema package |
| VRP | [`VrpScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java) | route/visit/customer/candidate schema classes |
| Simulation | [`SimulationScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/SimulationScenario.java) | state vector、pending event、trace schema classes |
| Game | [`GameScenario.java`](../../soma-examples/src/main/java/com/hgtech/soma/examples/game/GameScenario.java) | player/unit/map/move/damage schema classes |

当前 executable 示例可能仍采用较早的数据角色拆分；正式 Blueprint 中的目标形态不是当前代码地图。两者差距由 Conformance 明确，不在本地图中改写为“已实现”。

## 2. Benchmark 入口

- smoke/all-lane runner：[`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkSmokeRunner.java)；
- packed/exact component runner：[`PostCutoverComponentBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/PostCutoverComponentBenchmark.java)；
- FJSP scale runner：[`FjspScaleBenchmark.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspScaleBenchmark.java)；
- FJSP options/model/report：[`FjspBenchmarkOptions.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkOptions.java)、[`FjspBenchmarkMeasurement.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkMeasurement.java)、[`FjspBenchmarkReport.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/FjspBenchmarkReport.java)；
- JVM/GC metrics：[`JvmRuntimeMetrics.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/JvmRuntimeMetrics.java)；
- scenario smoke lane composition：[`SmokeLaneSuite.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/SmokeLaneSuite.java)。

FJSP machine-selection A/B由commit-bound 5-run JSONL记录；component runner独立记录pipeline allocation与exact-index distinct-group retained payload，两者均为`claimAllowed=false`诊断证据。

## 3. 追踪方式

Blueprint 决定场景要验证的目标 access pattern；Design 决定不可违反的语义；examples 提供 executable reference；benchmarks 隔离待测 lane；reports 只陈述 artifact 支持的结论。场景代码本身不拥有 core Design。
