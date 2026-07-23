# 工业动态调度应用 Blueprint

类型：应用 Blueprint

状态：candidate

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

事实范围：应用目标用户、使用旅程、约束覆盖和成功标准

最后审查日期：2026-07-23

目标是通过版本化配置生成可重放的工业调度问题，装载到独立 runtime aggregate，执行动态 event/dispatch/commit 循环，并输出经完整领域 validator 验证的 assignment。

应用覆盖 flexible machine、precedence、release/material readiness、setup、calendar/maintenance、transport、secondary resource、due/priority；它不把这些规则定义为 SOMA 能力。
