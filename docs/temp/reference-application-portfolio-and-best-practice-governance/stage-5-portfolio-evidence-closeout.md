# Stage 5 Portfolio Evidence 收口

类型：Temporary

状态：complete

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

事实范围：三应用 correctness、architecture、isolation、scale、soak、parallel 和 performance evidence

非事实范围：正式长期文档、release readiness 和跨机器性能声明

最后审查日期：2026-07-27

## 1. Evidence Owner 闭包

三个应用分别拥有自己的业务事实、oracle、benchmark 和 baseline；portfolio
checker 只验证聚合、隔离和 coverage，不拥有领域结论：

| Application | Correctness Owner | 性能与规模 Owner |
|---|---|---|
| industrial scheduler | hand oracle、独立 Result validator、replay/failure checks | scheduler benchmark 与三份 application baseline |
| grassing simulation | AoS per-tick differential、order/lifecycle checks | simulation benchmark 与三份 application baseline |
| RTD rule engine | independent reference evaluator、sequential/parallel identity、commit boundary checks | dispatch benchmark 与三份 application baseline |

SOMA operator/Shape 语义仍由 `soma-dataflow` reference differential 拥有；kernel
机制仍由 neutral component benchmark 拥有。应用不重复接管这两项责任。

## 2. 三层 Portfolio Gate

```text
Fast:  三应用 default × 3 forks
Scale: 三应用 large × 3 forks
Soak:  三应用 long-run × 3 forks
```

三个 orchestration script 现在各自包含 industrial、grassing 和 RTD，baseline
architecture 固定为两份 component baseline 加九份 application baseline。
application POM、source、fixture、benchmark 和 baseline 保持两两隔离；聚合模块不
提供领域 parent、共享 JAR 或 shortcut。

## 3. RTD 基线与并行证据

RTD 三份初始 baseline 由 immutable commit
`78c63611fc3e3fc03ff97d8f5e3a4728244c70ec` 的 5-fork 校准建立：

- allocation：`ceil(p50 × 1.25)`；
- timing：`ceil(max(p50 × 1.50, p90 × 1.25))`，p90 使用 nearest-rank；
- GC count：零保持零，否则 `max + 1`；
- GC pause：零保持零，否则 `ceil(max × 1.25)`；
- task 和 maximum worker：`all-equal`。

`callerAllocatedBytes` 只测同步 Invocation 调用线程；GC 是进程范围。两者没有被
误写为所有 worker 分配或 JVM 全局 allocation。全部 baseline
`claimAllowed=false`，只支持同环境本机非回归，不形成产品性能承诺。

| Profile | Workload identity | 3-fork timing / limit | caller allocation / limit | parallel identity |
|---|---|---:|---:|---:|
| default | 1024 work、24 cycles、32 resources | 70.98 ms / 114.38 ms | 17.22 MB / 21.52 MB | 228 tasks、4 workers |
| large | 15000 work、20 cycles、128 resources | 793.82 ms / 1186.56 ms | 119.57 MB / 152.61 MB | 64 tasks、4 workers |
| long-run | 13000 work、500 cycles、64 resources | 142.83 ms / 210.29 ms | 730.15 MB / 911.26 MB | 1504 tasks、4 workers |

三档均同时验证 Config、generator、input、result、Schema、RuntimePlan、
Definition、Template 和 demand identity；ordinary 3-fork comparator 全部通过。

## 4. 两个既有应用的非回归

industrial 在删除 `AssignmentSummaryFlow`、改用单遍
`AssignmentSummarizer` 后，三档旧 baseline 无修改通过：

| Profile | 3-fork timing / limit | allocation / limit | Result |
|---|---:|---:|---|
| default | 14.98 ms / 21.26 ms | 3.96 MB / 4.95 MB | unchanged |
| large | 1362.22 ms / 1930.66 ms | 118.53 MB / 143.34 MB | unchanged |
| long-run | 48.48 ms / 70.57 ms | 13.09 MB / 17.27 MB | unchanged |

grassing 的 Schema、input、Result 与性能阈值均未变化。首次 ordinary default
比较发现旧 baseline 仍记录 generated runtime v4 的 `runtimePlanHash`；该 baseline
校准早于 generated/runtime protocol v5。最小诊断证明只有 plan protocol identity
变化，因此三份 baseline 只迁移 `runtimePlanHash`，保留原 9-fork 阈值和
calibration provenance，没有 rebaseline、增加 fork 或放宽阈值。迁移后的三档
ordinary evidence 全部通过：

| Profile | 3-fork timing / limit | allocation / limit | Result |
|---|---:|---:|---|
| default | 144.93 ms / 220.36 ms | 11.43 MB / 12.11 MB | unchanged |
| large | 6161.18 ms / 8922.05 ms | 426.41 MB / 447.72 MB | unchanged |
| long-run | 3678.83 ms / 5406.07 ms | 47.36 MB / 49.73 MB | unchanged |

## 5. Coverage 与 Scope Non-regression

- RTD 已独立证明 reusable Definition/Template/Invocation、多 Source Join、
  GroupBy、controlled parallel、budget/cancellation、detached command/result
  和 application-owned commit；
- industrial 继续拥有 direct Access、Candidate Scan、frontier/event loop 和
  Java orchestration，不再承担展示性 DataFlow responsibility；
- grassing 继续拥有 packed iteration、exact group、staged mutation、Batch、
  swap-remove、determinism、lifecycle 和 fail-stop，没有被强制 DataFlow 化；
- 九个 workload 的规模、测量次数、普通 fork 数和性能阈值均未降低；
- 没有修改 SOMA public/generated API、annotation Schema、Access、
  Transformation、DataFlow 或 runtime 语义；
- 没有引入第三方依赖、数据库同步、跨 Table transaction 或跨应用领域共享；
- component benchmark 没有因 application-only 变更被重新归属或重校准。

Stage 5 已满足退出条件。Stage 6 只进行最终 scope non-regression、正式 Owner
原子固化、完整 Gate、Governance Report 和 Temporary 退役。
