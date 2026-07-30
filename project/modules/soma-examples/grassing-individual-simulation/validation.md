# 个体生态仿真验证

类型：应用验证

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：config、Scenario/Simulator lifecycle、source-set、AoS oracle、long-run和performance evidence

最后审查日期：2026-07-30

## 配置责任

| Config | 规模 | 责任 |
|---|---:|---|
| correctness | 8 × 6、5 individuals、12 ticks | 逐 tick AoS 等价、负路径 |
| default | 128 × 72、1,000 individuals、1,000 ticks | production CLI、replay、Fast multi-fork |
| large | 1280 × 720、100,000 individuals、1,000 ticks | 大 live set、capacity、column/group/bulk 与 Scale multi-fork |
| long-run | 400 × 225、10,000 individuals、10,000 ticks | 持续 birth/death churn、numeric、内存稳定性与 Soak multi-fork |

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
- production prepare 不执行 test-only 全量 projection verification；
- `SimulationSessionLifecycle` 对 ordinary failure 和 unexpected `Error` 都
  fail-stop，并保留 cleanup suppressed failure；
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
拒绝。Result/Diagnostics 的构造 invariant、operation-local result accumulator
和 runtime 关闭后 detached 可消费性也由 test 覆盖。

两槽SomaGroup在create后报告2个active member/Table，release后member、Table和
structural resources归零；partial-create不遗留单独root。

## 性能 artifact

Benchmark setup 完成配置解析、initial-state generation、checksum、校验、
bootstrap 和 tick-0 trace；measurement 只覆盖 tick systems。
`grassing-simulation-benchmark-v3` 的每个独立 JVM fork 输出：

- config/input/result checksum 与 schema/runtime-plan identity；
- commit、fork/configured forks、实际 JDK/JVM/OS/architecture/CPU/max heap；
- profile、logical world、ticks/initial population、warmup/measurement 与实际
  tick executions；
- setup/tick nanos、nanos/tick；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- initial/maximum population 与 population table growth count；
- `claimAllowed=false`。

Application-owned baseline位于test resources，当前版本为default v6、large v5、
long-run v5。generated update scratch切换为受plan limit约束的几何留存后，三个
profile在同一Amazon Corretto 8本机以clean immutable
`b189d1130055c51602162fb761ddd727d3d91718`各完成5-fork重校；旧版本只由Git保存：

| Profile | RuntimePlan | time limit | allocated limit | Young/Full GC envelope |
|---|---|---:|---:|---|
| default | `85bda3…` | `225,127,374 ns` | `7,405,220 B` | `0/0 ms；0/0 ms` |
| large | `0e0bef…` | `8,986,689,750 ns` | `76,431,180 B` | `2/3 ms；0/0 ms` |
| long-run | `248dbb…` | `5,452,954,251 ns` | `34,417,510 B` | `2/2 ms；0/0 ms` |

Allocation继续按应用级`ceil(p50 × 1.25)`治理。Default timing按既定公式收紧；
large与long-run的旧timing envelope比本轮公式结果更严格且五个新fork全部通过，
因此保持原上限，不因校准噪声放宽。GC按新五fork最大值形成envelope；exact、
operation scratch、population、growth和全部业务checksum保持全等。
update scratch high-water因用有界留存换取更少的反复复制而分别更新为
`36,024 B`、`5,400,048 B`和`360,024 B`。
Default、large、long-run分别由Fast、Scale、Soak承担，Full组合全部九个workload。

Comparator 在 exact environment/workload 下判断 `passed/failed`，环境不同时为
`not-applicable`；invalid schema/shape/claim/fork/identity 仍失败。旧 64 KiB/tick
粗阈值已由更严格、版本化、环境感知的 allocation baseline 唯一接管，不保留双
Owner。上述本机 evidence 只用于回归诊断，不外推为 SLA、支持矩阵、release 或
public claim。
