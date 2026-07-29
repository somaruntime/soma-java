# 参考应用与 benchmark 地图

类型：Implementation Map

状态：正式

Owner：SOMA reference application / benchmark 实现导航

对应 Blueprint：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

对应 Design：[系统架构](../design/system-architecture.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)、[性能模型](../design/performance-model.md)

事实范围：当前三个独立参考应用、领域中性 benchmark 和各自 evidence 的代码入口

最近实现核对基线：包含本文件的 V1 `1.0.0` private-source sign-off commit；
精确commit由Git与同SHA qualification artifact记录

最后审查日期：2026-07-29

## 1. 聚合与应用入口

[`soma-examples/pom.xml`](../../soma-examples/pom.xml) 只聚合三个相互独立的
child project，不产出领域共享 JAR，也不向 child 注入 parent、dependency
management 或 runtime shortcut。

| Application | 自有文档 | executable / runtime | input 与验证 |
|---|---|---|---|
| industrial dynamic scheduler | [application docs](../../soma-examples/industrial-dynamic-scheduler/docs/README.md) | [`SchedulerApplication.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/application/SchedulerApplication.java)、[`SomaSchedulingSolver.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/solver/SomaSchedulingSolver.java)、[`CandidateFrontier.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/solver/CandidateFrontier.java)、[`AssignmentSummarizer.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/solver/AssignmentSummarizer.java)、[`SchedulerRuntime.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/runtime/SchedulerRuntime.java) | [`SchedulingProblem.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/problem/SchedulingProblem.java)、[`SyntheticSchedulingProblemFactory.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/problem/SyntheticSchedulingProblemFactory.java)、[`RuntimeProjector.java`](../../soma-examples/industrial-dynamic-scheduler/src/main/java/io/github/somaruntime/soma/examples/scheduler/runtime/RuntimeProjector.java)、[`SchedulerVerification.java`](../../soma-examples/industrial-dynamic-scheduler/src/test/java/io/github/somaruntime/soma/examples/scheduler/verification/SchedulerVerification.java) |
| grassing individual simulation | [application docs](../../soma-examples/grassing-individual-simulation/docs/README.md) | [`SimulationApplication.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/SimulationApplication.java)、[`Simulator.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/simulation/Simulator.java)、[`SimulationSession.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/simulation/SimulationSession.java)、[`SomaSimulator.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/simulation/SomaSimulator.java)、[`SimulationResult.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/result/SimulationResult.java) | [`SyntheticSimulationScenarioFactory.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/scenario/SyntheticSimulationScenarioFactory.java)、[`SimulationRuntimeFactory.java`](../../soma-examples/grassing-individual-simulation/src/main/java/io/github/somaruntime/soma/examples/grassing/runtime/SimulationRuntimeFactory.java)、[`SimulationVerification.java`](../../soma-examples/grassing-individual-simulation/src/test/java/io/github/somaruntime/soma/examples/grassing/evidence/SimulationVerification.java) |
| real-time dispatch rule engine | [application docs](../../soma-examples/real-time-dispatch-rule-engine/docs/README.md) | [`RealTimeDispatchApplication.java`](../../soma-examples/real-time-dispatch-rule-engine/src/main/java/io/github/somaruntime/soma/examples/rtd/RealTimeDispatchApplication.java)、[`SomaDispatcher.java`](../../soma-examples/real-time-dispatch-rule-engine/src/main/java/io/github/somaruntime/soma/examples/rtd/dispatch/SomaDispatcher.java)、[`DispatchRulePlan.java`](../../soma-examples/real-time-dispatch-rule-engine/src/main/java/io/github/somaruntime/soma/examples/rtd/rule/DispatchRulePlan.java)、[`DispatchRuntime.java`](../../soma-examples/real-time-dispatch-rule-engine/src/main/java/io/github/somaruntime/soma/examples/rtd/runtime/DispatchRuntime.java) | [`SyntheticDispatchScenarioFactory.java`](../../soma-examples/real-time-dispatch-rule-engine/src/main/java/io/github/somaruntime/soma/examples/rtd/feed/SyntheticDispatchScenarioFactory.java)、[`ReferenceDispatcher.java`](../../soma-examples/real-time-dispatch-rule-engine/src/test/java/io/github/somaruntime/soma/examples/rtd/reference/ReferenceDispatcher.java)、[`DispatchVerification.java`](../../soma-examples/real-time-dispatch-rule-engine/src/test/java/io/github/somaruntime/soma/examples/rtd/evidence/DispatchVerification.java) |

三个 child POM 都是普通 Java 8 consumer，只声明 `soma-annotations`、
`soma-runtime-core`、generated companion 所需的 `soma-dataflow` 和 compile-time `soma-processor`。工业调度应用采用
`application/config/problem/solver/runtime/result/schema` 分层；个体生态仿真采用
`config/scenario/simulation/runtime/result/schema/support` 分层；RTD 采用
`config/feed/runtime/rule/dispatch/result/schema/support` 分层。三个应用的
fixture/oracle/verification/benchmark 均位于 test source-set，production JAR
不含 evidence implementation。版本化 config 与 detached factory 拥有输入，
runtime hot loop 不反向依赖 generator 或 factory。

工业调度的 eligible option 由一张 flat immutable Table 和
`by_operation` exact-group 承载；完整 projection 逐值复核位于 test-only
`SchedulerProjectionTestAccess`。Candidate 不进入 SOMA schema，而由
`CandidatePool`、`MachineFrontierHeap` 和 stable domain identity 维护；这三者是
可从 Table facts 重建的算法状态。

调度 hot loop 仍由 application-owned primitive frontier 负责；solve 完成后，
`AssignmentSummarizer` 从 authoritative `OperationAssignment` Table 通过 direct
ColumnView 单遍推导 assignment count、makespan 和按 job completion 聚合的
tardiness metrics。它只使用 operation-local primitive grouping，不维护第二份
live fact，也不把 current Index、View 或 Table 引用带入 detached Result。

生态仿真保持 direct Candidate Scan、exact group、ColumnView、Batch 和
application-owned primitive world/scratch；Session lifecycle 对 ordinary 与
unexpected failure 统一 fail-stop，完整 projection verification 只存在于 test。

RTD 把 detached snapshot/delta 投影为 `WorkState`/`ResourceState`，使用一次建立的
`DispatchRulePlan` 重复绑定两张 Table，执行 filter、GroupBy、inner Join、stable
sort 和受控 parallel；Invocation 后立即复制 detached command，由
`DispatchCommitter` 使用 stable key 全批次预检并顺序提交两个 root。

三个 reference application 均已完成最终设计审计。真实偏差是同一运行期内相关
root Table 共享 lifecycle/resource owner，却由应用手工创建和逆序释放。当前
factory分别建立稳定冻结的显式 `SomaGroupPlan`：

- industrial scheduler：`industrial-scheduler-solve`，九个root slot；
- grassing simulation：`grassing-simulation-session`，两个root slot；
- RTD：`real-time-dispatch-horizon`，两个root slot。

Runtime aggregate唯一拥有Group并通过`runtimeMetadata()`公开detached
`SomaGroupMetadata`；partial-create、normal/fault cleanup和release均走Group
协议。业务模型、算法、Result、workload与领域correctness保持各应用的
canonical 产品叙事，不为展示效果增加平行 adapter 或共享领域层。

## 2. Benchmark 入口

- smoke runner / validator：[`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/BenchmarkSmokeRunner.java)、[`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/BenchmarkArtifactValidator.java)；
- neutral component runner / validator：[`AccessComponentBenchmark.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/AccessComponentBenchmark.java)、[`AccessComponentArtifactValidator.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/AccessComponentArtifactValidator.java)；
- DataFlow component runner：[`DataFlowComponentBenchmark.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/DataFlowComponentBenchmark.java)；
- runtime-scale qualification runner/model/validator：
  [`RuntimeScaleQualificationRunner.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/RuntimeScaleQualificationRunner.java)、
  [`RuntimeScaleQualificationModel.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/RuntimeScaleQualificationModel.java)、
  [`RuntimeScaleQualificationArtifactValidator.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/RuntimeScaleQualificationArtifactValidator.java)；
- baseline parser / comparator：[`PerformanceBaselineDefinition.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/PerformanceBaselineDefinition.java)、[`PerformanceBaselineComparator.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/PerformanceBaselineComparator.java)；
- lane contract、workload、evidence 与 aggregation：[`SmokeLaneContract.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/SmokeLaneContract.java)、[`SmokeLaneWorkloads.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/SmokeLaneWorkloads.java)、[`SmokeLaneEvidence.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/SmokeLaneEvidence.java)、[`SmokeLaneAggregation.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/SmokeLaneAggregation.java)；
- neutral schema：[`schema`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/schema)；
- application-integrated evidence：[`SchedulerBenchmark.java`](../../soma-examples/industrial-dynamic-scheduler/src/test/java/io/github/somaruntime/soma/examples/scheduler/benchmark/SchedulerBenchmark.java)、[`SimulationBenchmark.java`](../../soma-examples/grassing-individual-simulation/src/test/java/io/github/somaruntime/soma/examples/grassing/evidence/SimulationBenchmark.java)、[`DispatchBenchmark.java`](../../soma-examples/real-time-dispatch-rule-engine/src/test/java/io/github/somaruntime/soma/examples/rtd/benchmark/DispatchBenchmark.java)。

`soma-benchmarks` 不依赖或导入 reference application domain。它只测 SOMA component mechanics；真实应用的 allocation、GC、runtime high-water 和 correctness guard 由 application 自有 runner/Gate 负责。全部 smoke/diagnostic artifact 保持 `claimAllowed=false`。

Access component runner 的 nested accumulator 使用显式无参构造器；Gate 在任何 fork
前执行 descriptor 与 class-load preflight，再进入既有五 fork baseline。该规则
只保证 measurement candidate 可启动且 class set 自洽，不改变 lane、阈值或
performance claim。

DataFlow runner 的 direct predicate 是不捕获 `Workload` 的命名对象，避免 Java 8
private synthetic accessor；Gate 在固定三 fork 前执行完整 15-lane admission。
`authoring.compile` 使用独立且写入 artifact 的最小 warmup，避免把 tiered
compilation 过渡期误记为稳态 p90；workload、fork、阈值和 baseline 不变。

当前十一份 checked-in baseline 分别为两份 component baseline 和九份
application profile baseline：

- [`Access component baseline`](../../soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/access-component-corretto8-macos-aarch64-v1.json)；
- [`DataFlow component baseline`](../../soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/dataflow-component-corretto8-macos-aarch64-v5.json)；
- scheduler [`default`](../../soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-default-corretto8-macos-aarch64-v6.json)、
  [`large`](../../soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-large-corretto8-macos-aarch64-v5.json)、
  [`long-run`](../../soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-long-run-corretto8-macos-aarch64-v5.json)；
- simulation [`default`](../../soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-default-corretto8-macos-aarch64-v4.json)、
  [`large`](../../soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-large-corretto8-macos-aarch64-v3.json)、
  [`long-run`](../../soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-long-run-corretto8-macos-aarch64-v3.json)；
- RTD [`default`](../../soma-examples/real-time-dispatch-rule-engine/src/test/resources/benchmark/performance-baseline-default-corretto8-macos-aarch64-v3.json)、
  [`large`](../../soma-examples/real-time-dispatch-rule-engine/src/test/resources/benchmark/performance-baseline-large-corretto8-macos-aarch64-v3.json)、
  [`long-run`](../../soma-examples/real-time-dispatch-rule-engine/src/test/resources/benchmark/performance-baseline-long-run-corretto8-macos-aarch64-v3.json)。

Comparator 只拥有领域中性协议。三个应用各自拥有 workload identity、阈值和
test-resource baseline，POM 不依赖 `soma-benchmarks`；baseline 不进入 production
JAR。当前没有 public performance claim。

当前 benchmark family 的唯一责任为：

| Family | 唯一 evidence |
|---|---|
| Smoke，20 required lanes | 低成本 executable artifact、Access Pattern Card、strict invariant |
| Access component | direct/Candidate stage、point/key/column、allocation 与 exact cardinality |
| DataFlow component | direct 对照、固定税、parallel crossover、Effect、delivery 与 stats |
| Runtime scale，8 required + 4 research lanes | required：Small/Medium、单1M、双1M、String、Expansion、Delivery、Soak；research：10M及single/double/String 100M stress |
| 3 applications × 3 profiles | 三种领域叙事的 default/large/long-run integrated evidence |
| Generated footprint | compiler specialization 的 source/class family size |

每个 family 都拥有其他 family 不能替代的 claim，因此当前没有待删除的重复 lane。
新增 lane 必须说明新的 design decision/claim、oracle、profile、通过规则与退役条件；
只复用 setup、environment、artifact parser 等 mechanics 不能构成第二个 evidence
Owner。

## 3. Gate

- artifact isolation：[`check-reference-applications.sh`](../../scripts/check-reference-applications.sh)；
- scheduler correctness/long-run/multi-fork：[`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh)；
- simulation oracle/long-run/multi-fork：[`check-grassing-simulation.sh`](../../scripts/check-grassing-simulation.sh)；
- RTD reference/parallel/budget/commit/multi-fork：[`check-real-time-dispatch-rule-engine.sh`](../../scripts/check-real-time-dispatch-rule-engine.sh)；
- neutral smoke/component：[`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-access-performance.sh`](../../scripts/check-access-performance.sh)；
- DataFlow semantic/component：[`check-dataflow-reference.sh`](../../scripts/check-dataflow-reference.sh)、[`check-dataflow-performance.sh`](../../scripts/check-dataflow-performance.sh)；
- application Fast/Scale/Soak/Full：
  [`check-reference-application-performance.sh`](../../scripts/check-reference-application-performance.sh)
  的 `fast`、`scale`、`soak`、`full` mode；
- baseline Owner/层次防回归：[`check-performance-baseline-architecture.sh`](../../scripts/check-performance-baseline-architecture.sh)；
- generated footprint：[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)。
- runtime-scale qualification：
  [`check-runtime-scale-qualification.sh`](../../scripts/check-runtime-scale-qualification.sh)；

全部 profile runner 都调用同一 comparator；Access component 普通 Gate 为 5
fork，DataFlow component 为固定 3 fork，九个应用 profile 各为 3 fork，新
application baseline 通常至少 5 fork。9 fork
只用于明确授权的方差诊断，不是失败后的自动重跑。普通 `scripts/check.sh`
不隐式启动 application fork；Fast/Scale/Soak 按 profile 独立运行，只有跨应用
runner/comparator 变化或明确完整验真时使用 Full。环境匹配时判定
`passed/failed`，环境不同但
artifact 合法时为
`not-applicable`。Blueprint 和 Design 决定产品目标与语义；应用拥有领域
correctness，benchmark 拥有测量 artifact，本地图只导航当前实现。
