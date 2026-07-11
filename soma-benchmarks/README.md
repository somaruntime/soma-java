# soma-benchmarks

Benchmark evidence methodology、runtime-state lanes 与 Java 8 smoke runner 模块。

正式设计：[soma-benchmarks/docs/README.md](docs/README.md)

本机 smoke 入口：`../scripts/check-benchmark-smoke.sh`。Runner 输出
`soma-benchmark-smoke-v2` JSONL，并由独立 validator 按 checked-in schema 与
required-lane manifest 复核；所有 smoke record 固定 `claimAllowed=false`。

模块 README 只负责导航，不包含性能结论。
