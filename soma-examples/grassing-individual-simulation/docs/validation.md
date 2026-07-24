# 个体生态仿真验证

类型：应用验证

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：config、Scenario/Simulator lifecycle、source-set、AoS oracle、long-run和performance evidence

最后审查日期：2026-07-24

## 配置责任

| Config | 规模 | 责任 |
|---|---:|---|
| correctness | 8 × 6、5 individuals、12 ticks | 逐 tick AoS 等价、负路径 |
| default | 96 × 72、800 individuals、500 ticks | production CLI、replay、multi-fork |
| large | 512 × 384、30,000 individuals、300 ticks | capacity、column/group/bulk 成本 |
| long-run | 256 × 192、5,000 individuals、2,000 ticks | birth/death churn、numeric 与内存稳定性 |

每个 profile 都重复生成 input 并比较 checksum。correctness/default 还以相同
detached Scenario 创建独立 Session，并反转 initial packed order 后比较结果。
production resources 只包含 default；其余 profile 与 benchmark options 位于 test
resources。

## 架构与 source-set

专项 Gate fail-closed 验证：

- Application 只经由 `Simulator` facade 执行，canonical Scenario/Session/Result
  contract 唯一；
- package dependency DAG 无环，config/scenario/support 不依赖 SOMA runtime；
- production 不依赖 test/evidence/oracle，production JAR 不包含其 class/resource；
- `state`、旧 generator/bootstrap/result/evidence identity 不会恢复；
- isolated repository、ordinary production classpath、clean/repeat manifest、
  Java major 52 与 generated/schema reproducibility。

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

correctness 额外验证 invalid config/NaN/duplicate identity、Session one-shot/fail
stop、partial-create/release closure、primary-key point access、duplicate Batch
原子拒绝、swap-remove、wrong-source/stale IndexSnapshot、clear 和 release 后访问
拒绝。Result/Diagnostics 的构造 invariant 和 runtime 关闭后 detached 可消费性也
由 test 覆盖。

## 性能 artifact

Benchmark setup 完成配置解析、initial-state generation、checksum、校验、
bootstrap 和 tick-0 trace；measurement 只覆盖 tick systems。
`grassing-simulation-benchmark-v2` 的每个独立 JVM fork 输出：

- config/input/result checksum 与 schema/runtime-plan identity；
- commit、fork/configured forks、实际 JDK/JVM/OS/architecture/CPU/max heap；
- warmup、measurement、setup/tick nanos；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- initial/maximum population 与 population table growth count；
- `claimAllowed=false`。

Application-owned baseline 位于 test resources。普通 Gate 使用 3 fork；9-fork
校准 `ab28350` 得到：

- allocation `7,439,296..7,440,784 bytes`，上限 `7,812,824`；
- tick p50 `66,086,417 ns`、p90/max `76,318,167 ns`，median 上限
  `99,129,626 ns`；
- exact/update/operation scratch high-water 固定为
  `64,333 / 27,336 / 12,776 bytes`；
- maximum population `1,139`、population growth count `1`；
- Young/Full GC count 与 pause 均为 `0`。

Comparator 在 exact environment/workload 下判断 `passed/failed`，环境不同时为
`not-applicable`；invalid schema/shape/claim/fork/identity 仍失败。旧 64 KiB/tick
粗阈值已由更严格、版本化、环境感知的 allocation baseline 唯一接管，不保留双
Owner。上述本机 evidence 只用于回归诊断，不外推为 SLA、支持矩阵、release 或
public claim。
