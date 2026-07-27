# soma_java 报告索引

类型：Report 入口

状态：当前

Owner：SOMA Java 正式报告输出

事实范围：当前与历史审查、验证、benchmark、治理和 release evidence导航

非事实范围：Blueprint、Design 或当前代码实现事实

最后审查日期：2026-07-28

`reports/` 保存带时间点的正式审查、验证、benchmark、治理和 release evidence。报告不是 Design；目标、规范和当前实现分别回到 [Blueprint](../docs/blueprints/README.md)、[Design](../docs/design/README.md)和[Implementation Map](../docs/implementation-map/README.md)。

2026-07-28 runtime-scale P6 target 已进入正式 Design，但 production code、generated
surface、三个 Example 和 production-scale qualification 尚未迁移。下表中的
G1–G5 因此按新 target 重新置为 `blocked`；旧报告仍是其精确 candidate 的历史
evidence，不能外推为新 target 已通过。

## 当前 V1 状态与 Gate

- [Java V1 Goal execution status](java-v1-goal-execution-status.md)
- [当前性能摘要](current-performance-summary.md)（适用环境和 claim 边界见报告 metadata）
- [2026-07-27 Reference Application Portfolio 与最佳实践治理](2026-07-27-reference-application-portfolio-and-best-practice-governance-report.md)（三个独立应用、RTD DataFlow trace、industrial/grassing 复审、九份 application baseline 与 scope non-regression）
- [2026-07-27 Transformation Model 与 Typed DataFlow 产品化治理](2026-07-27-transformation-dataflow-governance-report.md)（generated/runtime v5、transformation/kernel v1、构造契约、reference differential、受控并行、工业应用 trace 与 scope non-regression）
- [2026-07-27 正确性保持与软件结构治理](2026-07-27-correctness-preservation-and-software-structure-governance-report.md)（aggregate fault containment、五条证明链、CP-001–CP-007、public/API/protocol 与性能非回归）
- [2026-07-24 Industrial Dynamic Scheduler 设计与性能治理](2026-07-24-industrial-scheduler-design-and-performance-governance-report.md)（Problem/Result 契约、Access Pattern→Schema、global frontier、canonical/hot 9-fork evidence 与 scope non-regression）
- [2026-07-24 Reference Application 大规模性能基线治理](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)（六个 default/large/long-run baseline、Fast/Scale/Soak/Full Gate、性能归因与 scope non-regression）
- [2026-07-24 三层性能基线治理](2026-07-24-three-layer-performance-baseline-governance-report.md)（component、两个 application baseline、环境感知 comparator、9-fork 校准与 public claim 边界）
- [2026-07-23 个体生态仿真参考应用架构治理](2026-07-23-grassing-individual-simulation-architecture-governance-report.md)（Config/Scenario/Simulator/Session/Engine/System/Runtime/Schema/Result分层、source-set与canonical journey）
- [2026-07-23 工业动态调度参考应用架构治理](2026-07-23-industrial-dynamic-scheduler-architecture-governance-report.md)（Problem/Factory/Solver/Runtime/Schema/Result分层、source-set与canonical journey）
- [2026-07-23 参考应用边界与 `soma-examples` 重构治理](2026-07-23-reference-application-boundary-governance-report.md)（历史边界基线；当时两个独立参考应用、neutral benchmark 与旧场景退役）
- [2026-07-23 复杂度可持续性后续治理](2026-07-23-complexity-sustainability-governance-report.md)（benchmark分责、processor所有权、generated-footprint诊断与runtime保留裁决）
- [2026-07-23 项目复杂度与可维护性治理](2026-07-23-project-complexity-and-maintainability-governance-report.md)（历史/current拓扑、processor/codegen分责、byte-stable与多fork非回归）
- [2026-07-23 Access Model / Candidate Scan 产品化治理](2026-07-23-access-model-candidate-scan-governance-report.md)（当前v4产品模型、正式Owner、实现与scope non-regression结论）
- [2026-07-23 Access Model / Candidate Scan 性能证据](2026-07-23-access-model-candidate-scan-performance-report.md)（component、JFR、code-size与FJSP多fork A/B）
- [2026-07-20 Packed Exact Index 切换后尾项治理收口](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)（当前post-cutover功能/性能实现与证据）
- [2026-07-17 Packed Index / Exact Access / IndexBuffer 重设计实施收口](2026-07-17-packed-exact-index-runtime-redesign-report.md)（当前 v3 实现形态与 `4b6fa43` 重验证入口）

| Gate | 状态 | 正式报告 |
|---|---|---|
| G0 | passed | [scope freeze](java-v1-g0-scope-freeze-report.md) |
| G1 | blocked（new target） | [schema processing](../soma-processor/reports/java-v1-g1-schema-processing-report.md)只保留旧 candidate evidence；四类 type、String 与 Metadata admission 待迁移 |
| G2 | blocked（new target） | [code generation](../soma-processor/reports/java-v1-g2-code-generation-report.md)只保留旧 candidate evidence；generated Metadata、Group 与 callback projection 待迁移 |
| G3 | blocked（new target） | 旧 [Transformation/DataFlow 治理](2026-07-27-transformation-dataflow-governance-report.md)与[Access Model / Candidate Scan治理](2026-07-23-access-model-candidate-scan-governance-report.md)保留历史 evidence；新 storage、relation、scheduler、resource 与 delivery target 待迁移 |
| G4 | blocked（new target） | [generated API/package](java-v1-g4-package-smoke-report.md)只保留旧 candidate evidence；新 external-consumer matrix 待通过 |
| G5 | blocked（new target） | 旧 [Reference Application Portfolio 治理](2026-07-27-reference-application-portfolio-and-best-practice-governance-report.md)和[大规模性能基线治理](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)只保留历史 evidence；P9 与 production-scale qualification 待通过 |
| G6 | blocked | [release readiness](java-v1-g6-release-readiness-report.md) |

Gate报告保留执行当时的Owner路径和术语作为evidence provenance；其中旧root/module契约现已`superseded`，当前Design与Engineering必须从[正式文档入口](../docs/README.md)进入。历史路径不因仍可读取而恢复current Owner身份。

- [G6 support matrix evidence](java-v1-support-matrix-report.md)

## 实施 checkpoint 与治理报告

- [2026-07-27 Reference Application Portfolio 与最佳实践治理](2026-07-27-reference-application-portfolio-and-best-practice-governance-report.md)
- [2026-07-27 正确性保持与软件结构治理](2026-07-27-correctness-preservation-and-software-structure-governance-report.md)
- [2026-07-27 Transformation Model 与 Typed DataFlow 产品化治理](2026-07-27-transformation-dataflow-governance-report.md)
- [2026-07-24 Industrial Dynamic Scheduler 设计与性能治理](2026-07-24-industrial-scheduler-design-and-performance-governance-report.md)
- [2026-07-24 Reference Application 大规模性能基线治理](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)
- [2026-07-24 三层性能基线治理](2026-07-24-three-layer-performance-baseline-governance-report.md)
- [2026-07-23 个体生态仿真参考应用架构治理](2026-07-23-grassing-individual-simulation-architecture-governance-report.md)
- [2026-07-23 工业动态调度参考应用架构治理](2026-07-23-industrial-dynamic-scheduler-architecture-governance-report.md)
- [2026-07-23 参考应用边界与 `soma-examples` 重构治理](2026-07-23-reference-application-boundary-governance-report.md)
- [2026-07-23 复杂度可持续性后续治理](2026-07-23-complexity-sustainability-governance-report.md)
- [2026-07-23 项目复杂度与可维护性治理](2026-07-23-project-complexity-and-maintainability-governance-report.md)
- [2026-07-23 Access Model / Candidate Scan 产品化治理](2026-07-23-access-model-candidate-scan-governance-report.md)
- [2026-07-23 Access Model / Candidate Scan 性能证据](2026-07-23-access-model-candidate-scan-performance-report.md)
- [2026-07-20 文档架构专题治理](2026-07-20-document-architecture-governance-report.md)
- [2026-07-20 设计驱动文档体系正式切换](2026-07-20-documentation-framework-cutover-report.md)
- [2026-07-20 Packed Exact Index 切换后尾项治理收口](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)
- [2026-07-17 Packed Index / Exact Access / IndexBuffer 重设计实施收口](2026-07-17-packed-exact-index-runtime-redesign-report.md)
- [2026-07-17 性能优化后 G6 本机诊断](2026-07-17-post-optimization-g6-diagnostic-report.md)

## 历史治理证据（保留原路径）

- [2026-07-21 四场景 Blueprint 采纳专题治理](2026-07-21-four-scenario-blueprint-adoption-report.md)（`a137b10` 时点证据；不再拥有 current G5 或当前示例）

## 历史归档

- [2026-07-11 SOMA Java V1 专题治理收口](archive/soma-java-v1-topical-governance-report.md)
- [Java V1 Phase 0 compiler/build checkpoint](archive/java-v1-phase-0-compiler-build-report.md)
- [Java V1 Phase 0 schema carrier construction 设计缺口](archive/java-v1-phase-0-schema-carrier-design-gap.md)
- [Java V1 Phase 3 access structures](archive/java-v1-phase-3-access-structures-report.md)（v2 历史 checkpoint；已由 packed/exact v3 切换取代）
- [Java V1 Phase 4 child/materialization](archive/java-v1-phase-4-child-materialization-report.md)
- [Java-only SOMA V1 Phase 5 full breadth closeout](archive/java-v1-phase-5-full-breadth-report.md)
- [旧 examples/benchmark G5 Gate](archive/java-v1-g5-examples-benchmark-gate-report.md)
- [2026-07-10 Implementation-readiness 治理收尾](archive/2026-07-10-implementation-readiness-governance-report.md)
- [2026-07-10 Compiler integration feasibility](archive/2026-07-10-compiler-integration-spike-report.md)
- [2026-07-10 文档体系重构收尾报告](archive/2026-07-10-documentation-system-refactor-report.md)
- [2026-07-10 四个蓝图设计审查](archive/2026-07-10-four-blueprints-design-review-report.md)
- [2026-07-10 SomaTable 设计治理收尾](archive/2026-07-10-soma-table-design-governance-closeout-report.md)
- [2026-07-07 FJSP frontier 文档治理](archive/2026-07-07-fjsp-frontier-document-governance-report.md)
- [2026-07-06 架构设计审查](archive/2026-07-06-architecture-design-review-report.md)
- [2026-07-06 设计文档治理](archive/2026-07-06-design-document-governance-report.md)

历史报告记录当时的输入、结论和缺口；其中旧文件名与旧 Owner 路径不代表当前文档结构。
