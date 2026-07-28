# 测试与 evidence 地图

类型：Implementation Map

状态：正式

Owner：SOMA 测试与 evidence 实现导航

对应 Design：[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)、[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

事实范围：当前测试层次、fixture、Gate 和 evidence artifact 入口

最近实现核对基线：2026-07-28 runtime-scale working-tree candidate（base
`6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`）

最后审查日期：2026-07-28

## 1. 验证层次

| 层次 | 当前实现入口 | 主要证明 |
|---|---|---|
| Maven tests | 各 module `src/test` + root `mvnw verify` | handwritten unit/invariant |
| compile fixtures | [`tests/fixtures/compiler`](../../tests/fixtures/compiler) | positive/negative compiler behavior |
| generated golden | fixtures 中 `expected/*.javap.txt`、schema JSON/hash | generated/schema compatibility |
| external consumers | `external-maven-*` fixtures + [`check-external-consumer.sh`](../../scripts/check-external-consumer.sh) | 普通 consumer compile/run |
| runtime invariant | [`check-runtime-contracts.sh`](../../scripts/check-runtime-contracts.sh) 与 `check-generated-*-contract.sh` | flat/head-tail storage、atomic segment publication、String reference cleanup、locator current/high-water、完整 Runtime Metadata、lifecycle/access correctness、root/Group fault containment、atomic attach/release、Candidate sequence、one-shot/retention、unique point 与 v11 identity |
| DataFlow contract | [`check-dataflow-contracts.sh`](../../scripts/check-dataflow-contracts.sh)、public/generated golden | Definition/Template/Invocation、Shape/operator legality、binding/resource/parallel/effect |
| reference differential | [`DataFlowReferenceDifferentialCheck.java`](../../soma-benchmarks/src/test/java/com/hgtech/soma/benchmarks/DataFlowReferenceDifferentialCheck.java) | plain-array oracle 与 fast path/graph、sequential/parallel 的通用语义等价 |
| reference application isolation | [`check-reference-applications.sh`](../../scripts/check-reference-applications.sh) | 三个 child 在 evidence-local repository 中独立 clean/repeat build、schema/hash/generated manifest、runtime graph 与 Java 8 classfile |
| application correctness/evidence | [`check-industrial-scheduler.sh`](../../scripts/check-industrial-scheduler.sh)、[`check-grassing-simulation.sh`](../../scripts/check-grassing-simulation.sh)、[`check-real-time-dispatch-rule-engine.sh`](../../scripts/check-real-time-dispatch-rule-engine.sh)、Fast/Scale/Soak/Full performance Gate | versioned config、detached input checksum、oracle/validator、failure/lifecycle/resource ownership，以及九个 profile 的多 fork timing/allocation/GC/high-water |
| neutral benchmark | [`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-access-performance.sh`](../../scripts/check-access-performance.sh)、[`check-dataflow-performance.sh`](../../scripts/check-dataflow-performance.sh)、[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh) | artifact integrity、direct/Candidate/DataFlow cost、parallel crossover、safe-point Effect、cardinality memory 与 generated footprint |
| package/security | [`package-smoke.sh`](../../scripts/package-smoke.sh) 及 security/release scripts | distribution boundary |

## 2. Repository-owned fixtures

[`tests/fixtures`](../../tests/fixtures) 保存 compiler、public/generated golden 与
独立 Maven consumer 输入；它不是 module，不产出 artifact。Compiler fixtures
覆盖 spoofing、cycle、invalid selector/child/default、Unicode order、
generated-name collision 和外部 Maven 使用。

Generated API 的最直接 compatibility evidence 是外部 fixture 的实际 javac/Maven compile/run 与 `javap` golden；源码字符串断言只适合作为辅助定位。

当前 fixture/evidence 组合按能力覆盖：

- schema classifier、arbitrary object stable-ID diagnostic、String
  Key/Unique/Index collision、Metadata hierarchy/default/ownership/immutability
  与 generated-name collision；
- plan canonical identity、Group lifecycle/fault、atomic segment publication、
  primitive/String/presence boundary、locator repair/high-water 与 dead-reference
  cleanup；
- Candidate/Group/Join/Window/Delta、unknown-bound fail-closed、sequential/
  parallel equivalence、callback delivery、Effect 与 diagnostics；
- clean/repeat external Maven consumer、current public/generated `javap`、
  schema JSON/hash、reference differential 和 bounded qualification。

API 迁移完成与否由 current golden、source 和 external consumer 的一致性证明，
不再用旧 token 黑名单或逐 forwarding-method 重复测试代替能力契约。

## 3. Evidence artifact

Benchmark runner 生成结构化 artifact，并由 [`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkArtifactValidator.java) 或 lane-specific strict validator 校验。当前 smoke 使用 benchmark-owned `GroupCandidate` 和 `DenseWorkspaceFact` 分别证明 grouped exact access 与无 maintained index 的 `replaceAll + sorted`，禁止把后者误记为 exact-index evidence。

三个 reference application 的版本化 artifact 精确登记 profile、目标规模、heap、
fork、Schema/RuntimePlan、result identity、hot-operation 执行次数、归一化指标、
allocation、GC、growth/high-water 或 parallel task，以及
`claimAllowed=false`。每个 child 各自拥有 default、large、long-run 三份
baseline；Fast、Scale、Soak 分责，Full 组合全部九个 workload。

Runtime-scale qualification使用独立strict schema、runner和validator，十条required
lane分别拥有预注册workload、环境、预算、timeout、oracle、memory accounting、
status与`claimAllowed=false`。Small/Medium、1M/10M、single/double100M、
shared-reference String 100M、Expansion、Delivery和Soak不得合并成一个模糊
“100M passed”结论；`check-benchmark-smoke.sh`只运行small-fast contract smoke，
完整重型入口是`check-runtime-scale-qualification.sh`。

Scan code-size evidence 对 neutral benchmark、industrial scheduler、grassing
simulation 和 RTD rule engine 分别保留首个切换候选的 fixed-candidate + 15%
ceiling，同时生成 surface、Scan、DataFlow 和 schema footprint；checker 要求各层
汇总闭合。它用于定位生成规模变化，不是容量承诺或单 feature 因果模型。报告只能
引用可追踪到 commit、环境、命令和 artifact 的测量；console 文本不是唯一
evidence。

## 4. 维护提示

新增或修改 Design capability 时，先确认不变量在唯一 production Owner 处已关闭，再选择一个直接构造/契约测试和一个外部、differential 或集成路径。不要为每个方法重复相同 null/lifecycle case；property/reference differential 应覆盖通用语义，代表性组合覆盖新增语义，少量 canonical end-to-end 与性能 evidence 守住产品边界。只冻结 private helper 或内部数组布局的测试不能关闭 Conformance 差距。
