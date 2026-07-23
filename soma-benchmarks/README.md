# soma-benchmarks

领域中性的 component benchmark、evidence methodology 与 Java 8 smoke runner 模块。

当前过程设计：[Benchmark 治理](../docs/engineering/benchmark-governance.md)；实现导航：[module docs](docs/README.md)

本机 smoke 入口：`../scripts/check-benchmark-smoke.sh`。Runner 输出
`soma-benchmark-smoke-v4` JSONL，并由独立 validator 按 checked-in schema 与
required-lane manifest 复核；所有 smoke record 固定 `claimAllowed=false`。

本模块不依赖 reference application domain。真实应用的 allocation/GC 与 long-run
evidence 分别由 `../scripts/check-industrial-scheduler.sh` 和
`../scripts/check-grassing-simulation.sh` 拥有；这里的 smoke/component artifact
只证明明确 lane，且不设置跨机器绝对性能 claim。

模块 README 只负责导航，不包含性能结论。
