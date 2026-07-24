# 测试与 evidence 地图

类型：Implementation Map

状态：正式

Owner：SOMA 测试与 evidence 实现导航

对应 Design：[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

事实范围：当前测试层次、fixture、Gate 和 evidence artifact 入口

最近实现核对基线：commit `c0fa1c9`

最后审查日期：2026-07-24

## 1. 验证层次

| 层次 | 当前实现入口 | 主要证明 |
|---|---|---|
| Maven tests | 各 module `src/test` + root `mvnw verify` | handwritten unit/invariant |
| compile fixtures | [`soma-testkit/src/test/fixtures/compiler`](../../soma-testkit/src/test/fixtures/compiler) | positive/negative compiler behavior |
| generated golden | fixtures 中 `expected/*.javap.txt`、schema JSON/hash | generated/schema compatibility |
| external consumers | `external-maven-*` fixtures + [`check-external-consumer.sh`](../../scripts/check-external-consumer.sh) | 普通 consumer compile/run |
| runtime invariant | `check-runtime-*`、`check-generated-*`、`check-access-*`、`check-child-*` | storage/lifecycle/access correctness、Candidate sequence、one-shot/retention、unique point 与 v4 identity |
| reference application isolation | [`check-reference-applications.sh`](../../scripts/check-reference-applications.sh) | 两个 child 在 evidence-local repository 中独立 clean/repeat build、schema/hash/generated manifest、runtime graph 与 Java 8 classfile |
| application correctness/evidence | [`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh)、[`check-grassing-simulation.sh`](../../scripts/check-grassing-simulation.sh)、Fast/Scale/Soak/Full performance Gate | versioned config、detached input checksum、oracle/validator、failure/lifecycle，以及六个 profile 的多 fork timing/allocation/GC/high-water |
| neutral benchmark | [`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-post-cutover-components.sh`](../../scripts/check-post-cutover-components.sh)、[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh) | artifact integrity、lane 责任边界、source/stage/terminal allocation、cardinality memory 与三类 representative generated footprint |
| package/security | [`package-smoke.sh`](../../scripts/package-smoke.sh) 及 security/release scripts | distribution boundary |

## 2. Testkit

[`MaterializedGraphComparator.java`](../../soma-testkit/src/main/java/com/hgtech/soma/testkit/MaterializedGraphComparator.java) 提供 detached materialized graph 的显式内容比较。Compiler fixtures 同时覆盖 spoofing、cycle、invalid selector/child/default、Unicode order、generated-name collision 和外部 Maven 使用。

Generated API 的最直接 compatibility evidence 是外部 fixture 的实际 javac/Maven compile/run 与 `javap` golden；源码字符串断言只适合作为辅助定位。

Codegen admission 额外约束 selector-less Table 的私有 exact-index stage 使用显式
构造器，防止 javac 8 synthetic access marker 在 clean build 边界漂移；这项断言
只保护私有生成字节码的可重复编译。

## 3. Evidence artifact

Benchmark runner 生成结构化 artifact，并由 [`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkArtifactValidator.java) 或 lane-specific strict validator 校验。当前 smoke 使用 benchmark-owned `GroupCandidate` 和 `DenseWorkspaceFact` 分别证明 grouped exact access 与无 maintained index 的 `replaceAll + sorted`，禁止把后者误记为 exact-index evidence。

两个 reference application 的 v3 artifact 精确登记 profile、目标规模、heap、
fork、Schema/RuntimePlan、result identity、hot-operation 执行次数、归一化指标、
allocation、GC、growth/high-water 和 `claimAllowed=false`。每个 child 各自拥有
default、large、long-run 三份 baseline；Fast、Scale、Soak 分责，Full 组合全部
六个 workload。

Scan code-size evidence 对 neutral benchmark、industrial scheduler 和 grassing simulation 分别保留首个切换候选的 fixed-candidate + 15% ceiling，同时生成 surface、Scan artifact 和 schema footprint；checker要求三层汇总闭合。它用于定位生成规模变化，不是容量承诺或单 feature 因果模型。报告只能引用可追踪到 commit、环境、命令和 artifact 的测量；console 文本不是唯一 evidence。

## 4. 维护提示

新增或修改 Design capability 时，至少选择一个直接不变量测试和一个外部/集成路径。测试如果只证明当前类内部实现而没有覆盖 public/generated behavior，不能单独关闭 Conformance 差距。
