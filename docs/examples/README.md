# 参考应用

<!--
类型：Product Projection
状态：当前
Owner：SOMA Java example projection
受众：应用开发者、技术选型者
输入事实源：三个独立 example module 的 source、README 与 canonical validation
最后审查日期：2026-07-30
-->

三个参考应用分别展示 SOMA 怎样进入不同业务边界。它们只消费正式 public artifacts，
彼此不共享领域 runtime、fixture 或 testkit。

| 应用 | 展示重点 | 入口 |
|---|---|---|
| 工业动态调度引擎 | direct Access、Candidate Scan、增量 frontier、application-owned solver loop | [运行说明](../../soma-examples/industrial-dynamic-scheduler/README.md) |
| 个体生态仿真 | packed 迭代状态、exact group、staged mutation、确定性 lifecycle | [运行说明](../../soma-examples/grassing-individual-simulation/README.md) |
| 实时派工规则引擎 | reusable multi-source DataFlow、Join/GroupBy、受控并行、detached command | [运行说明](../../soma-examples/real-time-dispatch-rule-engine/README.md) |

这些应用是使用方法和可执行 evidence，不拥有 SOMA 产品语义。维护者需要查看应用
Blueprint、Design 和 Validation 时，从
[soma-examples 模块事实](../../project/modules/soma-examples/README.md)进入。
