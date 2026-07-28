# 个体生态仿真验证

类型：应用验证

状态：当前

Owner：grassing-individual-simulation

对 SOMA 产品规范性：否

事实范围：config、Scenario/Simulator lifecycle、source-set、AoS oracle、long-run和performance evidence

最后审查日期：2026-07-28

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

Application-owned baseline位于test resources，当前版本为default v4、large v3、
long-run v3。Amazon Corretto 8成为唯一JDK authority后，三个profile均在同一
Corretto 8本机以5-fork candidate重新校准；旧Zulu baseline只由Git保存其历史
evidence含义：

| Profile | RuntimePlan | time limit | allocated limit | Young/Full GC envelope |
|---|---|---:|---:|---|
| default | `6005f0…` | `225,824,688 ns` | `14,494,170 B` | `0/0 ms；0/0 ms` |
| large | `01703a…` | `8,986,689,750 ns` | `533,070,360 B` | `16/14 ms；2/39 ms` |
| long-run | `9c21c5…` | `5,452,954,251 ns` | `59,967,160 B` | `0/0 ms；0/0 ms` |

校准commit为`092617b67247cbaed354857daed9d6e1457b876e`，每个baseline
登记自己的candidate content checksum。
Allocation按应用级`median + 25%`治理，不再混用maximum；timing、deterministic
high-water与GC规则与正式Benchmark治理一致。Schema、config/input/result、
population/growth和AoS oracle identity保持。旧baseline的Schema/RuntimePlan
identity已落后于当前生成物；旧Zulu与新Corretto对同一当前source生成结果一致，
因此这是evidence漂移修正，不是JDK不确定性。
Default、large、long-run分别由Fast、Scale、Soak承担，Full组合全部九个workload。

Comparator 在 exact environment/workload 下判断 `passed/failed`，环境不同时为
`not-applicable`；invalid schema/shape/claim/fork/identity 仍失败。旧 64 KiB/tick
粗阈值已由更严格、版本化、环境感知的 allocation baseline 唯一接管，不保留双
Owner。上述本机 evidence 只用于回归诊断，不外推为 SLA、支持矩阵、release 或
public claim。
