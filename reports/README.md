# SOMA Java 报告入口

类型：Report 入口

状态：当前

Owner：SOMA Java 正式报告输出

事实范围：当前综合结论、当前性能/状态和仍需保留的 Gate/release evidence

非事实范围：Blueprint、Design、代码实现事实和历史治理过程

最后审查日期：2026-07-29

`reports/` 只保留当前决策所需的综合报告与不可替代 Gate evidence。已经完成事实
迁移的专题过程、旧 candidate benchmark 和旧治理 checkpoint 由 Git 保存，不在
current checkout 维护 archive 或重复叙事。报告不拥有产品语义；目标、长期契约和
当前实现分别从 [Blueprint](../docs/blueprints/README.md)、
[Design](../docs/design/README.md) 与
[Implementation Map](../docs/implementation-map/README.md) 进入。

## 当前综合结论

- [SOMA V1 工程体系治理](2026-07-29-soma-v1-engineering-system-governance-report.md)：
  Amazon Corretto 8 authority、Maven lifecycle/local repository、Fast/Full/
  Qualification、最多四路安全并行、一次准备多项取证、CI、测试/Benchmark/
  Example evidence、code-size隔离、Cloud development candidate与异常处置；
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
| G1 | passed | [Schema processing](../soma-processor/reports/java-v1-g1-schema-processing-report.md)与当前Corretto Full |
| G2 | passed | [Code generation](../soma-processor/reports/java-v1-g2-code-generation-report.md)与当前Corretto Full |
| G3 | passed | 当前Corretto runtime/DataFlow contract与component evidence |
| G4 | passed | [Package历史证据](java-v1-g4-package-smoke-report.md)与当前Corretto external-consumer evidence |
| G5 | blocked | Corretto component与九profile application已通过；十lane runtime-scale仍需在当前JDK authority重跑 |
| G6 | selected `private-github-source` blocked | [Release readiness](java-v1-g6-release-readiness-report.md)与[Support matrix](java-v1-support-matrix-report.md) |

JDK authority从Zulu迁移到Corretto不会降低G5/G6目标，也不能把旧vendor证据改名
复用。当前不得把`0.2.0-SNAPSHOT`、unsigned artifact、private CI、Codex Cloud、
历史Zulu qualification或单机Corretto evidence外推为public release、Maven
Central、production readiness、跨环境性能或公开SLA。
