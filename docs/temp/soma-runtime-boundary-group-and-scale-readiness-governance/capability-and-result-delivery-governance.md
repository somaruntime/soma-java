# Capability 与 Result Delivery 候选议题转移记录

类型：Temporary

状态：promoted at `d5f713d`

Owner：P6 capability/result-delivery promotion trace

正式事实源：否

事实范围：原候选议题、正式 Owner 映射和 Git provenance

非事实范围：Capability 或 Result Delivery 的长期规范、当前实现或 readiness

最后审查日期：2026-07-28

本文件原本承载 P4 之前的 Capability Model 与 Lazy Output 候选讨论。P5 审计后的
结论已进入正式 Owner，本文不再保留平行设计正文。

| 原候选议题 | 正式 Owner |
|---|---|
| 封闭 Capability Set、可替换边界、hot specialization | [系统架构](../../design/system-architecture.md) |
| Eager Detached 默认与 callback-scoped streaming 受限试点 | [Result Delivery 与物化边界](../../design/materialization-boundary.md) |
| Definition/Template/Invocation 与 callback lifecycle | [DataFlow 执行模型](../../design/dataflow-execution-model.md) |
| resource preflight、failure 与 observation | [Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md)、[正确性与失败](../../design/correctness-and-failure.md) |
| ordinary Iterator/pull/async 等拒绝方向 | [兼容性、安全与版本](../../design/compatibility-security-and-versioning.md) |

技术依据保留在 [TV Evidence Synthesis](technical-validation-evidence-synthesis.md)，
P8 replacement closure 由 P7 production disposition 追踪。原正文
可从 `aea5cc0` 及更早 Git history 审计；本文件随 P11 删除。
