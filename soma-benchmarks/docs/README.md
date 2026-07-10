# soma-benchmarks 正式设计文档

本目录保存 benchmark 方法与具体 runtime-state lanes 的正式设计事实，不保存 benchmark 结果。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [Benchmark evidence 契约](benchmark-evidence-contract.md) | `soma-benchmarks` | evidence level、度量、artifact、claim 和设计反推条件 |
| [Runtime-state benchmark 契约](runtime-state-benchmark-contract.md) | `soma-benchmarks` | component shape、FJSP、VRP、Simulation、Game、child/materialization lanes |

## 实现期缺口

Runner CLI、exact JSONL schema、scale/seed preset、warmup/repetition 默认值和报告模板将在 runner 实现专题中固化。它们属于 benchmark implementation/runtime plan，不改变上述 evidence 和 scenario contract。

当前文档只定义可测问题，不代表已有 claim-grade benchmark 结果。

临时专题进入 `docs/temp/`，不得成为正式事实源。
