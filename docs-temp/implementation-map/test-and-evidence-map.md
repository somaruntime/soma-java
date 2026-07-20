# 测试与 evidence 地图

类型：Implementation Map

状态：候选

Owner：SOMA 测试与 evidence 实现导航

对应 Design：[Correctness 与 failure](../design/correctness-and-failure.md)、[性能模型](../design/performance-model.md)

事实范围：当前测试层次、fixture、Gate 和 evidence artifact 入口

最近实现核对基线：`b991f4c`

最后审查日期：2026-07-20

## 1. 验证层次

| 层次 | 当前实现入口 | 主要证明 |
|---|---|---|
| Maven tests | 各 module `src/test` + root `mvnw verify` | handwritten unit/invariant |
| compile fixtures | [`soma-testkit/src/test/fixtures/compiler`](../../soma-testkit/src/test/fixtures/compiler) | positive/negative compiler behavior |
| generated golden | fixtures 中 `expected/*.javap.txt`、schema JSON/hash | generated/schema compatibility |
| external consumers | `external-maven-*` fixtures + [`check-external-consumer.sh`](../../scripts/check-external-consumer.sh) | 普通 consumer compile/run |
| runtime invariant | `check-runtime-*`、`check-generated-*`、`check-access-*`、`check-child-*` | storage/lifecycle/access correctness |
| scenario | [`check-examples-phase6.sh`](../../scripts/check-examples-phase6.sh) | FJSP/VRP/simulation/game executable behavior |
| benchmark | [`check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)、[`check-fjsp-allocation-gc.sh`](../../scripts/check-fjsp-allocation-gc.sh)、[`check-post-cutover-components.sh`](../../scripts/check-post-cutover-components.sh) | artifact integrity、FJSP allocation/GC与component allocation/cardinality memory |
| package/security | [`package-smoke.sh`](../../scripts/package-smoke.sh) 及 security/release scripts | distribution boundary |

## 2. Testkit

[`MaterializedGraphComparator.java`](../../soma-testkit/src/main/java/com/hgtech/soma/testkit/MaterializedGraphComparator.java) 提供 detached materialized graph 的显式内容比较。Compiler fixtures 同时覆盖 spoofing、cycle、invalid selector/child/default、Unicode order、generated-name collision 和外部 Maven 使用。

Generated API 的最直接 compatibility evidence 是外部 fixture 的实际 javac/Maven compile/run 与 `javap` golden；源码字符串断言只适合作为辅助定位。

## 3. Evidence artifact

Benchmark runner 生成结构化 artifact，并由 [`BenchmarkArtifactValidator.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkArtifactValidator.java) 或lane-specific strict validator校验。报告只能引用可追踪到 commit、环境、命令和 artifact 的测量；console 文本不是唯一 evidence。

## 4. 维护提示

新增或修改 Design capability 时，至少选择一个直接不变量测试和一个外部/集成路径。测试如果只证明当前类内部实现而没有覆盖 public/generated behavior，不能单独关闭 Conformance 差距。
