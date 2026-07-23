# 个体生态仿真验证

类型：应用验证

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：config、generator、bootstrap、AoS oracle、long-run和performance evidence

最后审查日期：2026-07-23

## 配置责任

| Config | 规模 | 责任 |
|---|---:|---|
| correctness | 8 × 6、5 individuals、12 ticks | 逐 tick AoS 等价、负路径 |
| default | 96 × 72、800 individuals、500 ticks | CLI journey、replay、multi-fork |
| large | 512 × 384、30,000 individuals、300 ticks | capacity、column/group/bulk 成本 |
| long-run | 256 × 192、5,000 individuals、2,000 ticks | birth/death churn、numeric 与内存稳定性 |

每个 profile 都重复生成 input 并比较 checksum。correctness/default 还以相同
detached input 创建独立 runtime，并反转 initial packed order 后比较结果。

## 独立 AoS oracle

correctness lane 使用普通 `ArrayList<Individual>` 和 `double[]` 独立实现全部 system；
每个 tick 后按 stable ID 比较：

- grass 每个 cell 的 IEEE-754 bits；
- identity、position、energy、mode、direction；
- population、birth 和 death；
- 最终 trace 与 result。

AoS 与 SOMA 实现只共享配置和无状态 deterministic-random 定义，不共享 Table、
Pipeline、Batch 或 system mutation code。

## Invariant 与负路径

四个 profile 都验证：

- grass finite 且在 seed floor 与 carrying capacity 边界内；
- live energy positive/finite，position/direction/mode 合法；
- `initial + births - deaths == population`；
- mode group counts 覆盖全部 population；
- key traversal、同步 `IndexSnapshot` + ColumnView gather；
- trace tick 单调，final trace 与 result 一致。

correctness 额外验证 invalid config/NaN/duplicate identity、primary-key point access、
duplicate Batch 原子拒绝、swap-remove、wrong-source/stale IndexSnapshot、clear 和
release 后访问拒绝。

## 性能 artifact

Benchmark setup 完成配置解析、initial-state generation、checksum、校验、bootstrap
和 tick-0 trace；measurement 只覆盖 tick systems。每个独立 JVM fork 输出：

- config/input/result checksum 与 schema/runtime-plan identity；
- warmup、measurement、setup/tick nanos；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- initial/maximum population 与 population table growth count；
- `claimAllowed=false`。

Gate 要求至少三个 fork，identity/checksum 跨 fork 唯一稳定，并防止 allocation
退化到每 measured tick 超过 64 KiB。该阈值只保护当前应用证据不被配置解析或
materialization 意外污染，不是 SOMA 通用性能承诺，也不外推为发布支持矩阵。
