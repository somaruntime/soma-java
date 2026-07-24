# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式 Blueprint/Design、既有 core 产品语义、compiler/codegen
stability `c0fa1c9`、industrial scheduler candidate `a7d4fde`、其余
reference application / performance baseline evidence `938b3d5`

事实范围：主要设计能力的一致性判断和直接依据

非事实范围：授权修复、重新定义 Design 或声明 public release readiness

最后审查日期：2026-07-24

## 1. 判定口径

- **一致且 evidenced**：代码形态与正式 Design 一致，并有对应 Gate/consumer evidence；
- **一致但 evidence 有限**：没有发现设计偏差，但结论只在当前测量/环境成立；
- **目标差距**：Blueprint 目标形态尚未完整投影到当前示例；不等同于 core Design 失败；
- **blocked**：明确目标尚缺必需外部事实或 evidence。

## 2. 能力矩阵

| 关注点 | 当前判定 | 依据 | 处置 |
|---|---|---|---|
| Java 8 annotation/schema/compiler | 一致且 evidenced | processor/plugin、compile fixtures；G1 passed | 保持 |
| deterministic normalization/hash | 一致且 evidenced | schema JSON/hash golden、Unicode fixture | 保持 |
| schema-specific generated API | 一致且 evidenced | Table/Scan/Cursor/Traversal/point families 的 external Maven consumers、public `javap` golden；G2/G4 passed | 保持 v4 clean surface，不恢复旧 alias |
| packed keyed/dense storage | 一致且 evidenced | generated/runtime checks；G3 passed | 保持 |
| primary identity 与 exact access | 一致且 evidenced | V3 Hash KeySpace、GroupedExactIndex、access fixtures；packed exact cutover passed | 保持 |
| Access Model 与 Candidate Scan | 一致且 evidenced | Access Pattern/API oracle、ordered-stage differential、one-shot/retention tests、external consumers 与两个 isolated reference applications | 保持 Point/Candidate/Column/Key/Bulk/Ownership 边界 |
| swap-remove 与 IndexBuffer execution | 一致且 evidenced | generated access/remove tests、component benchmark | 保持 |
| Index / IndexSnapshot caller-responsibility | 一致且 evidenced | detached `IndexSnapshot`、optional `requireCurrent`、wrong/stale consumer tests；正式 Owner 已明确非 stable identity/row snapshot | 保持 raw detached API，不增加强制 hot-path guard |
| child ownership/lifecycle | 一致且 evidenced | child external consumer、ownership/materialization Gate | 保持 |
| structured failure/plan/stats | 一致且 evidenced | runtime diagnostics、compatibility/error fixtures | 保持 |
| detached materialization/budget | 一致且 evidenced | child/materialization fixtures、testkit comparator | 保持 |
| hot-path performance shape | 一致但 evidence 有限 | 版本化 neutral component 与六个 application profile baseline、统一 comparator、allocation/GC/high-water/timing、9-fork 校准和 Fast/Scale/Soak/Full Gate | 结论限制在 baseline 精确环境与 workload；其他环境为 `not-applicable` |
| reference application boundary | 一致且 evidenced | `soma-examples` 仅聚合两个 independent child；isolated repository/runtime graph/source-shape Gate | 应用只消费 public artifacts，不反向拥有 core Design |
| industrial dynamic scheduler | 一致且 evidenced | Problem semantic identity/range preflight、canonical Solver/Session、domain-only detached Result、flat eligible exact-group projection、application-owned global frontier、完整约束/oracle/claim validator/failure/lifecycle/JAR purity，以及 1k/100k/10k operations 的 hot + canonical multi-fork baseline；见[架构治理](../../reports/2026-07-23-industrial-dynamic-scheduler-architecture-governance-report.md)与[设计性能治理](../../reports/2026-07-24-industrial-scheduler-design-and-performance-governance-report.md) | 保持应用分层、Access Pattern→Schema 追踪和唯一 canonical journey；领域事实与 execution evidence 继续分离且 application-owned |
| grassing individual simulation | 一致且 evidenced | Config/Scenario Factory/Simulator/Session/Result canonical journey、Engine/System/Runtime/Schema 分责、production/test 隔离、detached Result、AoS逐tick等价、order independence、JAR/DAG，以及 1k×1k/100k×1k/10k×10k 的 multi-fork baseline；见[架构治理](../../reports/2026-07-23-grassing-individual-simulation-architecture-governance-report.md)与[规模性能治理](../../reports/2026-07-24-reference-application-scale-performance-baseline-governance-report.md) | 保持应用分层、one-shot lifecycle 和唯一 canonical journey；领域事实与 integrated evidence 继续 application-owned |
| G0–G5 功能与 package Gate | passed | 当前 [报告入口](../../reports/README.md) | 保持 evidence 可重放 |
| G6 public release evidence | blocked | SCM/ownership/signing/publishing/support matrix 等真实事实不足 | 保持 blocked，不得误报 release ready |
| 设计驱动文档体系 | 一致且 evidenced | 32份旧Owner已处置；Design 具备层次/关注点/上位关系与场景追踪；Blueprint、Map、Conformance职责分离；checker 已覆盖结构门禁 | 保持唯一Owner、抽象层次和Temporary退役门禁 |
| 项目复杂度与可维护性 | 一致且 evidenced | historical/current拓扑、processor/codegen与benchmark责任拆分、normalized generated-footprint诊断、byte-stable generation、完整Gate与多fork对照 | 继续使用软触发器和责任Gate，不设置LOC配额 |

## 3. 当前结论

正式 Design 对 core compiler/runtime 的描述与当前实现一致，没有发现需要修改 core Design/public API 的 blocking deviation。旧四场景已经从产品 Blueprint、Design trace、current Conformance、共享 example JAR 和 benchmark dependency 中退出；其 SOMA evidence 责任分别由 core fixtures、neutral component benchmark 与两个 isolated reference applications 接管。领域专属算法已按非产品事实退役，历史 Report 只保留 provenance。

Access Model、Candidate Scan、Unique point family、scalar Index terminal、
Traversal naming 与 v4 identity 没有变化；两个参考应用的架构治理都只消费既有
SOMA 契约，不建立新的产品契约。其余未闭合项只有两类：

1. component 和六个 reference application profile 已有可持续回归 baseline，但性能结论
   仍受精确环境与 lane 范围约束，且没有 public claim；
2. G6因外部发布事实保持blocked。

本结论不扩大任何任务授权；Conformance 只记录当前判断与相关 Owner 已作出的处置决定，不表示差距实现已获授权或完成。

## 4. Evidence 入口

- [当前 G0–G6 状态](../../reports/java-v1-goal-execution-status.md)
- [Packed Index / Exact Access / IndexBuffer 收口](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)
- [Packed Exact Index 切换后尾项治理](../../reports/2026-07-20-packed-exact-index-post-cutover-closeout-report.md)
- [G5 examples/benchmark Gate](../../reports/archive/java-v1-g5-examples-benchmark-gate-report.md)
- [性能优化后本机诊断](../../reports/2026-07-17-post-optimization-g6-diagnostic-report.md)
- [设计驱动文档体系正式切换](../../reports/2026-07-20-documentation-framework-cutover-report.md)
- [文档架构专题治理](../../reports/2026-07-20-document-architecture-governance-report.md)
- [四场景 Blueprint 采纳治理（历史 provenance）](../../reports/2026-07-21-four-scenario-blueprint-adoption-report.md)
- [Access Model / Candidate Scan 正式切换治理](../../reports/2026-07-23-access-model-candidate-scan-governance-report.md)
- [Access Model / Candidate Scan 性能证据](../../reports/2026-07-23-access-model-candidate-scan-performance-report.md)
- [项目复杂度与可维护性治理](../../reports/2026-07-23-project-complexity-and-maintainability-governance-report.md)
- [复杂度可持续性后续治理](../../reports/2026-07-23-complexity-sustainability-governance-report.md)
- [工业动态调度参考应用架构治理](../../reports/2026-07-23-industrial-dynamic-scheduler-architecture-governance-report.md)
- [个体生态仿真参考应用架构治理](../../reports/2026-07-23-grassing-individual-simulation-architecture-governance-report.md)
- [三层性能基线治理](../../reports/2026-07-24-three-layer-performance-baseline-governance-report.md)
- [Reference Application 大规模性能基线治理](../../reports/2026-07-24-reference-application-scale-performance-baseline-governance-report.md)
- [Industrial Dynamic Scheduler 设计与性能治理](../../reports/2026-07-24-industrial-scheduler-design-and-performance-governance-report.md)
