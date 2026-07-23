# soma_java 报告索引

类型：Report 入口

状态：当前

Owner：SOMA Java 正式报告输出

事实范围：当前与历史审查、验证、benchmark、治理和 release evidence导航

非事实范围：Blueprint、Design 或当前代码实现事实

最后审查日期：2026-07-23

`reports/` 保存带时间点的正式审查、验证、benchmark、治理和 release evidence。报告不是 Design；目标、规范和当前实现分别回到 [Blueprint](../docs/blueprints/README.md)、[Design](../docs/design/README.md)和[Implementation Map](../docs/implementation-map/README.md)。

## 当前 V1 状态与 Gate

- [Java V1 Goal execution status](java-v1-goal-execution-status.md)
- [当前性能摘要](current-performance-summary.md)（适用环境和 claim 边界见报告 metadata）
- [2026-07-23 Access Model / Candidate Scan 产品化治理](2026-07-23-access-model-candidate-scan-governance-report.md)（当前v4产品模型、正式Owner、实现与scope non-regression结论）
- [2026-07-23 Access Model / Candidate Scan 性能证据](2026-07-23-access-model-candidate-scan-performance-report.md)（component、JFR、code-size与FJSP多fork A/B）
- [2026-07-21 四场景 Blueprint 采纳专题治理](2026-07-21-four-scenario-blueprint-adoption-report.md)（当前四场景、fixture、benchmark 映射与一致性证据）
- [2026-07-20 Packed Exact Index 切换后尾项治理收口](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)（当前post-cutover功能/性能实现与证据）
- [2026-07-17 Packed Index / Exact Access / IndexBuffer 重设计实施收口](2026-07-17-packed-exact-index-runtime-redesign-report.md)（当前 v3 实现形态与 `4b6fa43` 重验证入口）

| Gate | 状态 | 正式报告 |
|---|---|---|
| G0 | passed | [scope freeze](java-v1-g0-scope-freeze-report.md) |
| G1 | passed | [schema processing](../soma-processor/reports/java-v1-g1-schema-processing-report.md)；packed/exact cutover见当前专题报告 |
| G2 | passed | [code generation](../soma-processor/reports/java-v1-g2-code-generation-report.md)；v4 generated surface见[当前治理报告](2026-07-23-access-model-candidate-scan-governance-report.md) |
| G3 | passed | [Access Model / Candidate Scan v4治理](2026-07-23-access-model-candidate-scan-governance-report.md)与[性能证据](2026-07-23-access-model-candidate-scan-performance-report.md)；packed/exact v3报告保留前序证据 |
| G4 | passed | [generated API/package](java-v1-g4-package-smoke-report.md) |
| G5 | passed | [四场景 Blueprint 采纳报告](2026-07-21-four-scenario-blueprint-adoption-report.md)与[Access Model / Candidate Scan治理](2026-07-23-access-model-candidate-scan-governance-report.md)；[旧 examples/benchmark Gate](archive/java-v1-g5-examples-benchmark-gate-report.md) 为 v2 历史快照 |
| G6 | blocked | [release readiness](java-v1-g6-release-readiness-report.md) |

Gate报告保留执行当时的Owner路径和术语作为evidence provenance；其中旧root/module契约现已`superseded`，当前Design与Engineering必须从[正式文档入口](../docs/README.md)进入。历史路径不因仍可读取而恢复current Owner身份。

- [G6 support matrix evidence](java-v1-support-matrix-report.md)

## 实施 checkpoint 与治理报告

- [2026-07-23 Access Model / Candidate Scan 产品化治理](2026-07-23-access-model-candidate-scan-governance-report.md)
- [2026-07-23 Access Model / Candidate Scan 性能证据](2026-07-23-access-model-candidate-scan-performance-report.md)
- [2026-07-21 四场景 Blueprint 采纳专题治理](2026-07-21-four-scenario-blueprint-adoption-report.md)
- [2026-07-20 文档架构专题治理](2026-07-20-document-architecture-governance-report.md)
- [2026-07-20 设计驱动文档体系正式切换](2026-07-20-documentation-framework-cutover-report.md)
- [2026-07-20 Packed Exact Index 切换后尾项治理收口](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)
- [2026-07-17 Packed Index / Exact Access / IndexBuffer 重设计实施收口](2026-07-17-packed-exact-index-runtime-redesign-report.md)
- [2026-07-17 性能优化后 G6 本机诊断](2026-07-17-post-optimization-g6-diagnostic-report.md)
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
