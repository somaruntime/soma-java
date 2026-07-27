# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：P6 target Design 与 production baseline `aea5cc0`

事实范围：主要设计能力的一致性判断和直接依据

非事实范围：授权修复、重新定义 Design 或声明 public release readiness

最后审查日期：2026-07-28

## 1. 判定口径

- **一致且 evidenced**：代码形态与正式 Design 一致，并有对应 Gate/consumer evidence；
- **一致但 evidence 有限**：没有发现设计偏差，但结论只在当前测量/环境成立；
- **目标差距**：Blueprint 目标形态尚未完整投影到当前示例；不等同于 core Design 失败；
- **blocked**：明确目标尚缺必需外部事实或 evidence。

## 2. 能力矩阵

| 关注点 | 当前判定 | 依据 | 处置 |
|---|---|---|---|
| Java 8 annotation/schema/compiler | 部分一致；target gap | processor/plugin、compile fixtures；当前 String selector reject、generic Object value surface 与四类封闭体系不一致 | 保留 javac 8 pipeline；按 Schema Owner 完成 classifier/String/typed protocol cutover |
| deterministic normalization/hash | 一致且 evidenced | schema JSON/hash golden、Unicode fixture | 保持 |
| Metadata/generated API | target gap | current per-Table default plan、无完整 Descriptor/SchemaMetadata/SomaGroupPlan；public Object/legacy borrow surface仍存在 | 按 Metadata phases 与 breaking matrix原子迁移；不引入 reflection/temporary adapter |
| packed keyed/dense storage | 部分一致；target gap | 当前 flat SoA、growth/invariant evidence存在；flat-head/segmented-tail 与 Segment publication尚无 production binding | flat保持 baseline；按 plan formula新增受限 Large layout |
| primary identity 与 exact access | 部分一致；target gap | 当前 V3 locator/exact一致；String selector、compact/segmented locator candidate与新 resource ledger未闭合 | 保持 authoritative equality；补 String、locator formula与 point/rehash/growth evidence |
| Access Model 与 Candidate Scan | 部分一致；target gap | 现有 ordered-stage/one-shot evidence；当前主要依赖 universal IndexBuffer | 保留 direct Access；实现 closed Candidate shapes并删除 superseded universal path |
| Transformation Model | 部分一致；target gap | current Shape/operator/reference differential保留；Group/Join/Delta/Window specialized cost/preflight尚未按新 Design闭合 | 保持 logical semantics；补 fusion/preaggregate/incremental/fail-closed evidence |
| Typed DataFlow execution | 部分一致；target gap | Definition/Template/Invocation和managed/borrowed基础存在；bounded morsel/vector scheduler、closed-value protocol、unified callback delivery未闭合 | Invocation继续唯一 guard Owner；按一套 lifecycle完成替换 |
| 按构造即正确 | 一致且 evidenced | typed immutable expression/result、one-shot Builder/Invocation、stable boundary failures、真实 internal publish guards；contract/property/differential evidence | 生产 Owner 继续承担不变量；测试不重复冻结 private layout |
| swap-remove 与 candidate execution | 部分一致；target gap | swap-remove一致；IndexBuffer evidence只覆盖当前 baseline | 保持 mutation invariant；Candidate physical多形态需新增 differential |
| Index / IndexSnapshot caller-responsibility | 一致且 evidenced | detached `IndexSnapshot`、optional `requireCurrent`、wrong/stale consumer tests；正式 Owner 已明确非 stable identity/row snapshot | 保持 raw detached API，不增加强制 hot-path guard |
| child ownership/lifecycle | 部分一致；target gap | root/child aggregate evidence保留；SomaGroup/attach/GroupLedger/version/release尚不存在 | 保持 child forest；新增 Group不得合并 root trust/transaction |
| structured failure/plan/observation | 部分一致；target gap | current errors/plan/stats有效；Metadata/Observation/Explain分责、parent/root/Invocation ledger与String profile未闭合 | 保留 stable categories；实现分级resource和module-owned observation |
| Result Delivery/materialization | 部分一致；target gap | Eager/materialization与legacy borrowed traversal存在；callback consumer当前进入Definition且无统一generated lifecycle | Eager保持默认；迁移全部 incumbent borrow并禁止Iterator/pull/async |
| hot-path/scale performance shape | evidence不足于新目标 | 既有component/application baseline只覆盖旧lanes；无Small/Medium String、1M/10M全workload、single/double100M、delivery/soak新qualification | production实现后执行预注册qualification；当前不允许scale/public claim |
| reference application boundary | 既有一致；待P9复核 | `soma-examples` 三个 independent child与既有evidence仍有效；尚未消费新Metadata/Group/String/delivery contract | P8仅做必要编译迁移；P9独立审计，无偏差则RETAIN |
| industrial dynamic scheduler | 一致且 evidenced | Problem/Solver/Result/frontier/完整约束闭环；`AssignmentSummarizer` 从 authoritative assignment Table 直接单遍推导 count/makespan/job completion/tardiness，三个既有 baseline 无修改通过 | 保持 primitive frontier 为 dispatch hot-path Owner；summary 使用 application-owned primitive grouping，不再承担 DataFlow 展示责任 |
| grassing individual simulation | 一致且 evidenced | Config/Scenario Factory/Simulator/Session/Result canonical journey、Engine/System/Runtime/Schema 分责、test-only projection verification、fail-stop lifecycle、operation-local Result accumulator、AoS逐tick等价、order independence，以及 1k×1k/100k×1k/10k×10k 的 multi-fork baseline | 保持 direct Access/Transformation、应用分层和唯一 canonical journey；不强行引入 reusable DataFlow |
| real-time dispatch rule engine | 一致且 evidenced | 独立 Config/Scenario/Runtime/Rule/Dispatch/Result；reusable Definition/Template/Invocation、多 Source Join、GroupBy、sequential/managed/borrowed、budget/cancel、detached command/result、application commit、plain-Java reference 与三个 profile baseline | 作为 DataFlow application coverage 的唯一 portfolio Owner；不扩张到 MES/JDBC/transaction/distributed execution |
| G0–G5 历史功能与 package Gate | 历史 candidate passed；新 target未重放 | 当前 [报告入口](../../reports/README.md) | P6 Design promotion不自动继承为新candidate通过；P10重跑适用Gate |
| G6 public release evidence | blocked | SCM/ownership/signing/publishing/support matrix 等真实事实不足 | 保持 blocked，不得误报 release ready |
| 设计驱动文档体系 | target已固化；implementation/evidence gap显式 | P6 Design与本Conformance同批更新 | P8/P10关闭gap；P11删除Temporary/Lab |
| 项目复杂度与可维护性 | 一致且 evidenced | historical/current拓扑、processor/codegen与benchmark责任拆分、normalized generated-footprint诊断、byte-stable generation、完整Gate与多fork对照 | 继续使用软触发器和责任Gate，不设置LOC配额 |

## 3. 当前结论

正式 Design 已原子接纳完整 Metadata control plane、SomaGroup、四类 V1 类型、
String reference baseline、closed Capability Set、受限 layout/Candidate/relation、
bounded scheduler、Eager + callback delivery 与分级 resource/scale qualification。
这些是 target facts，不是 current support claim。

当前 implementation 保留此前 packed Access/Transformation/DataFlow、ownership、
failure与三个 application evidence，但与新目标存在 [known gaps](known-gaps.md)：
完整 Metadata/Group、String/typed closed-value protocol、Large physical plan、
specialized relation/parallel、unified callback delivery、resource hierarchy与全部
production-shape qualification均尚未闭合。因此“没有 blocking deviation”的旧结论
不再适用于本 P6 target。

P6 不修改 production，也不关闭任何上述 gap。G6仍因外部发布事实 blocked；Lab/
local evidence不能改变这一结论。

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
- [Transformation Model 与 Typed DataFlow 产品化治理](../../reports/2026-07-27-transformation-dataflow-governance-report.md)
- [正确性保持与软件结构治理](../../reports/2026-07-27-correctness-preservation-and-software-structure-governance-report.md)
- [Reference Application Portfolio 与最佳实践治理](../../reports/2026-07-27-reference-application-portfolio-and-best-practice-governance-report.md)
