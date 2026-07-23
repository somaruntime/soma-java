# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式 Blueprint/Design、commit `fd82eba` 的实现与当前正式 evidence

事实范围：主要设计能力的一致性判断和直接依据

非事实范围：授权修复、重新定义 Design 或声明 public release readiness

最后审查日期：2026-07-23

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
| Access Model 与 Candidate Scan | 一致且 evidenced | Access Pattern/API oracle、ordered-stage differential、one-shot/retention tests、四场景与 external consumers | 保持 Point/Candidate/Column/Key/Bulk/Ownership 边界 |
| swap-remove 与 IndexBuffer execution | 一致且 evidenced | generated access/remove tests、component benchmark | 保持 |
| Index / IndexSnapshot caller-responsibility | 一致且 evidenced | detached `IndexSnapshot`、optional `requireCurrent`、wrong/stale consumer tests；正式 Owner 已明确非 stable identity/row snapshot | 保持 raw detached API，不增加强制 hot-path guard |
| child ownership/lifecycle | 一致且 evidenced | child external consumer、ownership/materialization Gate | 保持 |
| structured failure/plan/stats | 一致且 evidenced | runtime diagnostics、compatibility/error fixtures | 保持 |
| detached materialization/budget | 一致且 evidenced | child/materialization fixtures、testkit comparator | 保持 |
| hot-path performance shape | 一致但 evidence 有限 | 16条component allocation、24条memory、JFR attribution、Scan code-size与FJSP 5-fork A/B；完整Gate passed | 结论限制在已测环境与lane，见当前性能摘要 |
| FJSP 目标场景 | 一致且 evidenced | unique sequence、完整 import/setup preflight、reusable frontier staging、FCFS/SPT indicator、publish-before-heap、checked commit；phase-6 adoption lane passed | 保持Table事实与外部queue职责分离 |
| VRP 目标场景 | 一致且 evidenced | definition/assignment/workspace 分离、全 insertion ordinal、hard-constraint propagation、route-version stale guard、authoritative-first commit；scenario Gate passed | 保持 route/assignment 单一事实源与 derived workspace rebuild 边界 |
| Simulation 目标场景 | 一致且 evidenced | definition/vector 分离、application event heap、nanosecond clock、derivative staging、numeric atomicity/fail-stop、trace export；scenario Gate passed | 保持 `StateVectorRow` 数值事实源和 projection 非权威性 |
| Game 目标场景 | 一致且 evidenced | definition/state 分离、keyed tile/occupancy、generation/revision stale guard、cache rebuild、damage total order/primitive staging；scenario Gate passed | 保持 unit position 权威、occupancy 可重建以及 damage fail-stop |
| G0–G5 功能与 package Gate | passed | 当前 [报告入口](../../reports/README.md) | 保持 evidence 可重放 |
| G6 public release evidence | blocked | SCM/ownership/signing/publishing/support matrix 等真实事实不足 | 保持 blocked，不得误报 release ready |
| 设计驱动文档体系 | 一致且 evidenced | 32份旧Owner已处置；Design 具备层次/关注点/上位关系与场景追踪；Blueprint、Map、Conformance职责分离；checker 已覆盖结构门禁 | 保持唯一Owner、抽象层次和Temporary退役门禁 |

## 3. 当前结论

正式 Design 对 core compiler/runtime 的描述与 `fd82eba` 一致，没有发现需要修改 core Design/public API 的 blocking deviation。Access Model、Candidate Scan、Unique point family、scalar Index terminal、Traversal naming、v4 identity 与四场景均已完成 clean cutover；其余未闭合项只有两类：

1. 性能结论仍受测量环境与 lane 范围约束；
2. G6因外部发布事实保持blocked。

`CF-001..003` 的关闭依据是 schema、executable journey、fixtures、benchmark 映射和文档投影共同完成，不是仅凭 Blueprint wording 关闭。有限性能 evidence 和 G6 仍按既有 Owner 处置。

本结论不扩大任何任务授权；Conformance 只记录当前判断与相关 Owner 已作出的处置决定，不表示差距实现已获授权或完成。

## 4. Evidence 入口

- [当前 G0–G6 状态](../../reports/java-v1-goal-execution-status.md)
- [Packed Index / Exact Access / IndexBuffer 收口](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)
- [Packed Exact Index 切换后尾项治理](../../reports/2026-07-20-packed-exact-index-post-cutover-closeout-report.md)
- [G5 examples/benchmark Gate](../../reports/java-v1-g5-examples-benchmark-gate-report.md)
- [性能优化后本机诊断](../../reports/2026-07-17-post-optimization-g6-diagnostic-report.md)
- [设计驱动文档体系正式切换](../../reports/2026-07-20-documentation-framework-cutover-report.md)
- [文档架构专题治理](../../reports/2026-07-20-document-architecture-governance-report.md)
- [四场景 Blueprint 采纳治理](../../reports/2026-07-21-four-scenario-blueprint-adoption-report.md)
- [Access Model / Candidate Scan 正式切换治理](../../reports/2026-07-23-access-model-candidate-scan-governance-report.md)
- [Access Model / Candidate Scan 性能证据](../../reports/2026-07-23-access-model-candidate-scan-performance-report.md)
