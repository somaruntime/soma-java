# I8 Narrow-Scale Smoke Qualification

状态：`I8_SCALE_SMOKE_PASS`；不是 G9 performance qualification。

本子切片使用现有 I2 generated Scalar Table，在 Java 8 consumer classpath 上加载 1,000,000 行，
验证 reserve/add、size/capacity、Key point lookup、顺序 filter、parallel filter、checked sum、
GroupBy cardinality 及 sequential/parallel 结果等价，同时记录 reserve/load/query 时间和 JVM
used-memory telemetry，并验证首/中/末/missing key、middle-key remove 后的 survivor 与 sidecar
rebuild，以及 non-long reference-key 的 lookup、duplicate、update、remove 与 rebuild fallback。
同时通过 test-only current-slot invalidation 触发真实 CAS publish failure，验证失败 append 不泄漏
payload、index、size 或 version；该 fault injection 不进入 production surface。

测试 harness 对加载阶段设置 120 秒 cooperative diagnostic deadline；脚本再以 150 秒进程级
watchdog 兜底。它们是运行时 safeguards；未专门触发的超时分支不列为 qualification proof，
也不是吞吐或延迟门槛。脚本还验证非法规模参数会 fail closed，并在实际 Java 8 runtime/compiler
上运行。

## Evidence

命令：`./scripts/check-i8-scale-smoke.sh`（默认 1,000,000 行；可传入 `[16, 5,000,000]` 范围内的
smoke 规模）。

该证据用于发现大规模坏味道和建立后续 profile 输入，不设置吞吐、延迟、内存或百万行 Release
阈值，也不声称三条产品场景、压缩、Join 或完整 G9 已通过。

本次观察（本机、Corretto/OpenJDK 8 harness、`-Xmx2g`，非产品资格基线）：1,000,000 行加载完成，
多次重放的 `loadMillis` 约为 280–330ms，`capacity=1,003,520`，remove 后
`stateVersion=1,000,002`，used-memory 约为 489–519MB；这些数值只作为本次重放的可追溯
smoke telemetry，不构成跨机器或跨版本承诺，重复运行允许有测量波动。

正式 Blueprint/Design 未修改。
