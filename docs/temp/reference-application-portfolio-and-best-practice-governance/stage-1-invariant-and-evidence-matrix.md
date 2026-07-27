# Stage 1 不变量与 Evidence 矩阵

类型：Temporary

状态：active（detailed design candidate）

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

实施授权：本专题 Stage 1–6

事实范围：三应用关键不变量、唯一 Owner、构造防线与计划 evidence

非事实范围：尚未实现的代码状态、正式 Conformance 和 release 声明

最后审查日期：2026-07-27

## 1. Portfolio

| 不变量 | 唯一 Owner | 事实产生处防线 | Evidence |
|---|---|---|---|
| 三应用互不依赖 | aggregator/checker | POM module 与 package DAG | isolated build、JAR/package scan |
| 每项 integrated capability 只有一个主要应用 Owner | portfolio matrix | application Design/validation registration | checker + current Report |
| test data generation 与 live runtime 分离 | each Scenario/Problem Factory | immutable detached input | repeat checksum、production JAR scan |
| 应用不重新定义 SOMA Design | root documentation governance | application metadata | docs checker |

## 2. Grassing

| 不变量 | 唯一 Owner | 事实产生处防线 | Evidence |
|---|---|---|---|
| Session failure 后不可访问 | `SomaSimulationSession` | state + close-on-Throwable | RuntimeException/Error/cleanup representative |
| Result 发布前 Runtime 已关闭 | Session `finish` | create -> close -> publish order | detached consumption、released access |
| projection 不重复证明 runtime contract | Factory/Batch boundary | production 不调用 full verifier | source/JAR scan + test-only projection check |
| result traversal 无隐藏共享 accumulator | `SimulationResultAssembler` | method-local accumulator | AoS checksum、packed-order reversal |
| direct Access 是 canonical | application Design | no business DataFlow | package/source scan、AoS |

## 3. RTD

| 不变量 | 唯一 Owner | 事实产生处防线 | Evidence |
|---|---|---|---|
| Config 完整、版本化、bounded | `DispatchConfigLoader` / immutable Config | strict key set + constructor validation | invalid config matrix |
| Scenario detached、replayable | `DispatchScenarioFactory` / Scenario | defensive copy、identity uniqueness、stable checksum | repeat/reordered input |
| runtime 只拥有 live state | `DispatchRuntimeFactory` / Runtime | Batch/Delta publish + close-on-failure | projection/reference check、release |
| Definition 不持有 live state | `DispatchRulePlan` | static immutable Template | identity repeat、多 runtime reuse |
| Invocation one-shot 且 source 独占 | SOMA + rule executor | explicit bind/context/token/budget | lifecycle/cancellation |
| command 不保存 Index/borrow | `DispatchCommand` factory | primitive/stable-key constructor only | API/JAR inspection |
| cycle 内 work/resource 唯一 | command selector | primitive selection marks + validated batch | independent Java oracle |
| cross-root commit 不伪装 transaction | dispatcher state machine | preflight + ordered commit + fail-stop | negative mutation boundary |
| managed/borrowed ownership 正确 | `DataFlowContext` + dispatcher close | explicit Context injection | managed closed、borrowed executor remains usable |
| sequential/parallel deterministic | logical Definition + stable command order | total order + deterministic greedy selection | checksum differential |
| budget/cancel 不发布 partial output | Invocation / dispatcher | execute-before-command publish | low-budget/cancel negative |

## 4. Industrial

| 不变量 | 唯一 Owner | 事实产生处防线 | Evidence |
|---|---|---|---|
| assignment Table 是唯一完成事实 | `AssignmentCommitter` | append-on-commit | domain validator |
| metrics 来自 authoritative assignments | `AssignmentSummarizer` | one synchronous scan | old/new reference comparison |
| tardiness 按 job 计算一次 | summarizer job accumulator | stable job key map | hand oracle、full validator |
| 迁移不降低 DataFlow coverage | portfolio checker | RTD evidence prerequisite | atomic coverage Gate |
| Result/performance 不回归 | Result/benchmark Owners | unchanged contract/baseline | correctness + existing 3-fork Gate |

## 5. Gate

| Evidence lane | 责任 | Fork |
|---|---|---:|
| application correctness/architecture | per application semantic contract | 0 |
| sequential/managed/borrowed differential | RTD deterministic execution | 0 |
| cancellation/budget/failure | boundary contract | 0 |
| default application performance | Fast regression | 3 |
| scale application performance | Scale regression | 3 |
| long-run application performance | Soak regression | 3 |
| component performance | domain-neutral runtime mechanics | existing policy |

性能失败必须先归因 application algorithm、representation、SOMA usage、Gate
methodology 和 environment；只有领域中性 reproduction 才能扩大到 core。
