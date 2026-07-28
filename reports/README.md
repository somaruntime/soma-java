# SOMA Java 报告入口

类型：Report 入口

状态：当前

Owner：SOMA Java 正式报告输出

事实范围：当前综合结论、当前性能/状态和仍需保留的 Gate/release evidence

非事实范围：Blueprint、Design、代码实现事实和历史治理过程

最后审查日期：2026-07-28

`reports/` 只保留当前决策所需的综合报告与不可替代 Gate evidence。已经完成事实
迁移的专题过程、旧 candidate benchmark 和旧治理 checkpoint 由 Git 保存，不在
current checkout 维护 archive 或重复叙事。报告不拥有产品语义；目标、长期契约和
当前实现分别从 [Blueprint](../docs/blueprints/README.md)、
[Design](../docs/design/README.md) 与
[Implementation Map](../docs/implementation-map/README.md) 进入。

## 当前综合结论

- [V1 产品面收敛与仓库瘦身治理](2026-07-28-soma-v1-product-surface-simplification-governance-report.md)：
  在不降低 Blueprint、Design、规模/String、Capability、Gate 与产品化目标的前提
  下，完成 production/public、test、benchmark、fixture、script、document、
  report 和三个 Example 的保留、替换与退役裁决；
- [Runtime Boundary、Group、Scale Readiness 与产品化综合治理](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)：
  TV0–TV9、Metadata、String、single/double 100M、Result Delivery、三个
  reference application、完整 Gate 与 scope non-regression；
- [Java V1 Goal execution status](java-v1-goal-execution-status.md)；
- [当前性能与规模摘要](current-performance-summary.md)。

## Gate 与 release evidence

| Gate | 当前状态 | 必要 evidence |
|---|---|---|
| G0 | passed | [Java-only V1 scope freeze](java-v1-g0-scope-freeze-report.md) |
| G1 | passed | [Schema processing](../soma-processor/reports/java-v1-g1-schema-processing-report.md)与[最新综合重放](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md) |
| G2 | passed | [Code generation](../soma-processor/reports/java-v1-g2-code-generation-report.md)与[最新综合重放](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md) |
| G3 | passed | [最新 runtime/DataFlow 重放](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md) |
| G4 | passed | [Package evidence](java-v1-g4-package-smoke-report.md)与[最新 external-consumer 重放](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md) |
| G5 | passed | [最新 qualification 与 Example 重放](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md) |
| G6 | blocked | [Release readiness](java-v1-g6-release-readiness-report.md)与[Support matrix](java-v1-support-matrix-report.md) |

G6 通过前不得把本机 `0.2.0-SNAPSHOT`、unsigned/dirty artifact 或单机
qualification 外推为 public release、正式支持矩阵、production readiness 或公开
性能承诺。
