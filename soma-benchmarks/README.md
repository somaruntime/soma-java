# soma-benchmarks

Benchmark evidence methodology、runtime-state lanes 与 Java 8 smoke runner 模块。

正式设计：[soma-benchmarks/docs/README.md](docs/README.md)

本机 smoke 入口：`../scripts/check-benchmark-smoke.sh`。Runner 输出
`soma-benchmark-smoke-v4` JSONL，并由独立 validator 按 checked-in schema 与
required-lane manifest 复核；所有 smoke record 固定 `claimAllowed=false`。

10 万工序 FJSP allocation/GC 诊断门禁入口：
`../scripts/check-fjsp-allocation-gc.sh`。该门禁要求落盘 phase allocated bytes、
allocation/op、Young/Full GC count/time 与采集方法；它不设置跨机器绝对性能阈值，
也不产生性能 claim。

模块 README 只负责导航，不包含性能结论。
