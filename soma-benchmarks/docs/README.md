# soma-benchmarks 正式设计文档

本目录保存 benchmark 方法与具体 runtime-state lanes 的正式设计事实，不保存 benchmark 结果。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [Benchmark evidence 契约](benchmark-evidence-contract.md) | `soma-benchmarks` | evidence level、度量、artifact、claim 和设计反推条件 |
| [Runtime-state benchmark 契约](runtime-state-benchmark-contract.md) | `soma-benchmarks` | component shape、FJSP、VRP、Simulation、Game、child/materialization lanes |

## Runner 实现入口

V1 smoke runner、exact JSONL schema、strict validator 和required-lane manifest已固化于：

- `BenchmarkSmokeRunner` / `BenchmarkArtifactValidator`；
- `META-INF/soma/benchmark-smoke-schema-v4.json`；
- `scripts/check-benchmark-smoke.sh`；
- `soma-benchmarks/reports/java-v1-benchmark-smoke-report.md`。

Smoke preset固定记录scale、seed、warmup、single fork、measurement iterations和环境，但不固定为claim-grade性能方法；CLI实现参数与schema兼容规则仍服从 [Benchmark evidence 契约](benchmark-evidence-contract.md)。

当前文档与runner只定义/执行可测问题，不代表已有claim-grade benchmark结果。

临时专题进入 `docs/temp/`，不得成为正式事实源。
