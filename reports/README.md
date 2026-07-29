# SOMA Java 报告入口

类型：Report 入口

状态：当前

Owner：SOMA Java 正式报告输出

事实范围：当前 V1 release、性能、support matrix 与 G6 readiness

非事实范围：Blueprint、Design、代码实现事实和历史治理过程

最后审查日期：2026-07-29

`reports/` 只保留当前决策所需的综合报告与不可替代 release evidence。已经完成的
专题过程、旧 vendor/candidate benchmark 和阶段 Gate checkpoint 由 Git 保存，
不在 current checkout 维护 archive、tombstone 或平行状态叙事。

报告不拥有产品语义。目标、长期契约和当前实现分别从
[Blueprint](../docs/blueprints/README.md)、
[Design](../docs/design/README.md) 与
[Implementation Map](../docs/implementation-map/README.md)进入。

## 当前报告

- [V1 release governance](java-v1-release-governance-report.md)：`1.0.0`
  candidate identity、G0–G6、上轮尾项、AI Skill、scope non-regression 与最终
  closeout；
- [当前性能与规模摘要](current-performance-summary.md)：component、application、
  runtime-scale 和 environment-bounded claim；
- [G6 release readiness](java-v1-g6-release-readiness-report.md)：selected
  private-source package/security/provenance 与授权边界；
- [Support matrix](java-v1-support-matrix-report.md)：唯一 JDK authority、已验证
  OS/architecture 和不允许外推的环境。

## 当前 Gate

| Gate | 当前状态 | Owner |
|---|---|---|
| G0 | passed | [V1 release governance](java-v1-release-governance-report.md) |
| G1–G4 | blocked pending final Full | [V1 release governance](java-v1-release-governance-report.md) |
| G5 | blocked pending exact candidate qualification | [性能与规模](current-performance-summary.md) |
| G6 | selected `private-github-source` blocked | [Release readiness](java-v1-g6-release-readiness-report.md) 与 [Support matrix](java-v1-support-matrix-report.md) |

当前 `1.0.0` 坐标、unsigned local artifact、private CI、Codex Cloud、历史
qualification 或单机 evidence 均不能外推为 public release、Maven Central、
production readiness、跨环境性能或公开 SLA。
