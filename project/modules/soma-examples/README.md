# soma-examples 模块事实

类型：Module

状态：正式

Owner：SOMA Java reference application 导航

事实范围：三个独立 reference consumer 的职责、内部事实和验证入口

非事实范围：SOMA 核心 Design、公共 API、跨环境性能 claim 和 release readiness

最后审查日期：2026-07-30

`soma-examples` 是 Maven 聚合边界，不产出共享领域 JAR。三个 child application
只消费 SOMA public artifacts，彼此不共享领域模型、fixture、runtime 或 testkit。

| 应用 | 内部事实 | 用户入口 |
|---|---|---|
| 工业动态调度引擎 | [Blueprint](industrial-dynamic-scheduler/blueprint.md)、[Design](industrial-dynamic-scheduler/design.md)、[Validation](industrial-dynamic-scheduler/validation.md) | [运行说明](../../../soma-examples/industrial-dynamic-scheduler/README.md) |
| 个体生态仿真 | [Blueprint](grassing-individual-simulation/blueprint.md)、[Design](grassing-individual-simulation/design.md)、[Validation](grassing-individual-simulation/validation.md) | [运行说明](../../../soma-examples/grassing-individual-simulation/README.md) |
| 实时派工规则引擎 | [Blueprint](real-time-dispatch-rule-engine/blueprint.md)、[Design](real-time-dispatch-rule-engine/design.md)、[Validation](real-time-dispatch-rule-engine/validation.md) | [运行说明](../../../soma-examples/real-time-dispatch-rule-engine/README.md) |

SOMA 的产品目标和规范性语义分别由
[产品 Blueprint](../../blueprint/soma-java-product-blueprint.md)和
[Design](../../design/README.md)拥有；当前代码、source-set、baseline 与 Gate 从
[参考应用与 benchmark Map](../../implementation-map/scenario-and-benchmark-map.md)
进入。

三个应用的综合 profiling、规模结论和最佳用法由
[当前性能与规模摘要](../../reports/current-performance-summary.md)拥有，并投影为
[用户性能指南](../../../docs/guides/performance-and-scale.md)。模块文档不复制
snapshot 数值或 release 状态。

Canonical Gate：

```sh
./scripts/check-reference-applications.sh
./scripts/check-industrial-scheduler.sh
./scripts/check-grassing-simulation.sh
./scripts/check-real-time-dispatch-rule-engine.sh
./scripts/check-reference-application-performance.sh full
```
