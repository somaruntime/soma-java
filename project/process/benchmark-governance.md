# Benchmark 治理

类型：Process

状态：正式

Owner：SOMA Java benchmark 过程

事实范围：benchmark lane、环境、artifact、对照和 claim 审批规则

非事实范围：性能设计目标和某次测量数值

最后审查日期：2026-07-29

## 1. 三层责任

| 层次 | Owner | 回答的问题 | 当前状态 |
|---|---|---|---|
| Component Performance Baseline | `soma-benchmarks` | SOMA 领域中性 mechanics 是否回归 | 已建立本机环境基线 |
| Reference Application Integrated Performance Baseline | 各 child application | 固定真实 workload 的端到端 hot operation 是否回归 | 三个应用各拥有 default、large、long-run 基线 |
| Public Performance Evidence / Claim | 经审批的正式 Report | 哪些环境、workload 和统计证据允许对外声明 | 当前不存在 |

前两层可以进入工程回归 Gate；第三层不是自动汇总结果，必须另有环境矩阵、
统计强度、审批和正式 Report。任何 local baseline 都不能自动晋升为 public claim。

`soma-benchmarks` 可以拥有领域中性的 artifact parser/comparator，但不拥有应用
workload 或阈值。应用 baseline 位于各自 test resources，child POM 不依赖
`soma-benchmarks`，production JAR 也不包含 baseline。

## 2. Lane 与 artifact

每个 lane 必须绑定 Access Pattern Card 和可验证问题，并明确 setup、warmup、
measurement、fork、dataset、stats mode、thread/ownership model、checksum，以及
哪些 allocation/GC 属于被测 operation。

版本化 JSONL measurement 至少记录：

- schema/artifact version、commit、fork/configured forks；
- 实际 Java/JVM、JVM args、OS、architecture、CPU 型号和 max heap；
- workload、result/schema/runtime-plan identity；
- timing、allocation、Young/Full GC、retained/high-water 和 checksum；
- `claimAllowed=false`。

Baseline 使用 `soma-performance-baseline-v1`，包含 Owner/layer/subject、
artifact version、校准 commit/date/forks/formula、精确环境、minimum forks、
workload identity、record shape 和 metric rules。Record 必须且只能匹配一个
exact shape；缺字段、额外字段或未知 contract 均失败。

Runner/validator/baseline/result schema 都是 evidence compatibility surface；字段
变化需要版本化、strict parser 和 negative paths。

### 2.1 Runtime-scale production qualification

Runtime boundary/scale governance完成production实现后，必须建立下列独立
production-shape lanes；它们不能被一个“100M passed”记录替代：

| lane | required scope |
|---|---|
| Small/Fast | 0/1/16/256/1K/4K primitive+String；create/point/exact/scan/column/batch/mutate/Group/Join/callback fixed tax |
| Medium | 32K/64K/256K primitive+String；layout/Candidate/relation/parallel crossover |
| 1M Single | 一张实际驻留的1M narrow numeric-Key Table；Point/Exact/Scan/Column/Batch/Delta/Join/Group/Window、closed numeric kernel与ledger归零 |
| 1M Double | 两个同时驻留的1M narrow numeric roots；双侧aggregate、same/cross Group bounded relation与整体release |
| String | 两张同时驻留的1M reference-backed String角色Table；payload、Key/Unique/Index、Group/Join、任意长度mutation、no-op、presence、clear/release/GC |
| Expansion | known overflow/over-budget与unknown-unprovable bound均在enumeration/callback前拒绝 |
| Delivery | Eager与Candidate/Value/Group/Join/Window callback全量/early-stop/failure/cancel/deadline/non-escape/String/GC |
| Soak | repeated create/load/mutate/Delta/callback/clear/release、executor/fault cleanup、ledger回零 |

`10M Research`、`100M Single Stress`、`100M Double Stress`和`100M String
Stress`保留为显式、非阻塞的research/stress lanes。它们使用
`research-stress-v1`、`required=false`，不能关闭或阻塞V1 qualification，也不能
替代上述1M保证。

每条lane运行前冻结当前authority Amazon Corretto full JDK 8 build、
OS/architecture、JVM args/heap/GC、
dataset seed、schema/row width、left/right rows、String profile、multiplicity/skew、
oracle/checksum、resource budget、operational timeout、warmup/measurement/fork、
baseline+tolerance或structural pass rule，以及
`passed/failed/inconclusive/not-applicable`判定。Required applicable lane只有
`passed`才能关闭qualification；`inconclusive`不是通过。

Operational timeout只防止无界执行，不是public latency SLA。所有artifact保持
`claimAllowed=false`；1M保证和可选10M/100M observation都只适用于明确profile，
不外推任意Schema/String/high-expansion或support matrix。

## 3. Comparator 状态

Comparator 按以下顺序处理：先验证 schema、shape、claim、artifact version、fork、
workload identity 和跨 fork 环境一致性，再判断 baseline 环境是否适用，最后解释
metric：

| 状态 | 含义 | 进程结果 |
|---|---|---:|
| `passed` | 环境适用，全部规则通过 | 0 |
| `failed` | contract/identity 无效，或适用环境发生回归 | 非 0 |
| `not-applicable` | artifact 合法，但环境与 baseline 不同 | 0 |

环境不匹配不能掩盖坏 artifact。`not-applicable` 只表示本次未验真该环境 baseline，
不表示性能通过或失败。Comparator result 同样版本化并保持
`claimAllowed=false`。

## 4. 指标、fork 与校准

准入 aggregation 为 `all-equal`、`maximum` 和 `median`；comparison 为
`equal` 和 `at-most`。Deterministic identity/high-water 用全等，GC 用 maximum，
timing 用 multi-fork median。Component allocation 使用 maximum；带完整 JVM
执行路径的 application allocation 使用 median fitness envelope，避免 TLAB、
tiered compilation 或延迟初始化的单个极值触发 rebaseline。Setup/preparation
可以报告，但不进入当前三个应用的 hot-operation timing Gate。

- Access component 普通 Gate 使用 5 个独立 JVM fork；
- DataFlow component 使用固定 3 个独立 JVM fork，并记录 invocation p50/p90/p99/max；
- 九个 application profile 的普通 Gate 各使用 3 个独立 JVM fork；
- 新建或重校 application baseline 通常使用同环境 5 fork；
- 9 fork 只用于明确授权的方差诊断或 public claim 准备；
- application allocation limit 为 `ceil(p50 × 1.25)`；
- timing limit 为 `ceil(max(p50 × 1.50, p90 × 1.25))`，p90 使用
  nearest-rank。
- GC count 在校准最大值为零时上限为零，否则为 `max + 1`；GC pause 在最大值
  为零时上限为零，否则为 `ceil(max × 1.25)`。

异常样本、热降频或后台噪声明显时应先记录为 methodology finding，不得自动循环
重跑或用单个异常结果放宽 baseline。普通 Gate 只读 checked-in baseline，不提供
update-in-place。Rebaseline 必须单独产生候选 artifact/diff，并说明触发原因、
旧/新 identity、环境、correctness 结果以及对应层级的固定 fork 证据：Access
component为5 fork，DataFlow component为3 fork，application至少为5 fork。环境
变化新增baseline，不覆盖旧环境事实。

Runner 在进入 multi-fork 前必须完成所有低成本、确定性的 admission：class-load、
CLI/input contract、必要的 classfile/descriptor identity 和 correctness smoke。
Admission 失败立即终止，不允许先消耗多个 fork 再发现候选不可启动，也不允许用
重复 fork 掩盖 build/class-set 不一致。

JDK vendor/build变化属于environment identity变化。旧vendor baseline只保留其
历史evidence含义并由Git保存，不得在新authority下改名复用；current checkout中的
新baseline必须由新环境的真实multi-fork artifact校准。在新baseline或重型
qualification形成前，`not-applicable`与`blocked`保持其真实含义。

## 5. 当前 Owner 与 Gate

- component baseline：`soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/`；
- scheduler/simulation/RTD baseline：各 child `src/test/resources/benchmark/`；
- neutral comparator：`PerformanceBaselineDefinition` /
  `PerformanceBaselineComparator`；
- 三层结构 Gate：`scripts/check-performance-baseline-architecture.sh`；
- component Gate：`check-access-performance.sh`、`check-dataflow-performance.sh`；
- application Fast/Scale/Soak/Full Gate：
  `check-reference-application-performance.sh <fast|scale|soak|full>`；
- application correctness 与架构 Gate：`check-industrial-scheduler.sh`、
  `check-grassing-simulation.sh`、`check-real-time-dispatch-rule-engine.sh`；
- runtime-scale model/runner/validator：
  `RuntimeScaleQualificationModel`、`RuntimeScaleQualificationRunner`、
  `RuntimeScaleQualificationArtifactValidator`与
  `META-INF/soma/runtime-scale-qualification-schema-v2.json`；
- runtime-scale完整Gate：`scripts/check-runtime-scale-qualification.sh`；
- 综合入口：`scripts/check.sh`。

普通benchmark smoke只运行runtime-scale `small-fast` contract smoke、strict
validator、negative claim和Java major 52检查。完整八条required lane的
qualification是至少12GiB物理内存的显式重型Gate，不隐式放入普通`check.sh`；
`research`模式要求至少40GiB并仍不构成release blocker。其source identity只绑定
可执行产品/evidence源码，最终Report或Temporary清理不会反向改变被验真的
candidate。

`check-performance-baseline-architecture.sh` 固定验证当前
component=2、reference-application=9、public-claim=0，以及模块依赖和 Owner
边界。新增环境或 public claim 必须显式修改 Owner、evidence 和 Gate。

`scripts/check.sh` 不再隐式启动 application fork；功能、构建、架构与性能责任
分开。Fast、Scale、Soak 分别承担 default、large、long-run，Full 只在跨应用
runner/comparator 变更或明确要求完整性能验真时组合九个 workload。应用内部变更
只运行受影响 profile，一次失败进入归因，不自动 rebaseline。运行频率差异不改变
baseline 的正式性、3-fork 下限、失败含义或 correctness guard。

综合Full只准备一次benchmark class set，smoke、Access与DataFlow复用同一已校验
输入；三个执行阶段仍保持串行，不能与其他功能Gate或彼此并行。各独立脚本直接
运行时仍自行完成build prerequisite，不依赖编排器的隐式状态。

## 6. 对照与 claim

需要设计结论时使用同语义对照，如 full scan vs exact source、dynamic sort vs
application heap、Candidate Scan vs point/column path、direct Candidate vs
DataFlow bind/execute、sequential vs parallel、hot path vs detached output/materialization。
对照必须保持相同结果、tie-break、failure 和 lifecycle，不能通过减少语义换数字。

Smoke 只证明 lane 可执行、artifact 合法和基本 invariant；local baseline 只支持
精确记录的环境/workload。单次最好结果、无 warmup/fork、无 correctness guard 或
混入 setup 的结果不得进入正式性能声明。跨机器、跨 JDK、production 或 public
claim 需要独立授权和对应矩阵。

性能退化可以触发调查；是否改变 Design 由相关 Design Owner 决定，不由 benchmark 自动决定。
