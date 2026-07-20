# Blueprint 导航

类型：Blueprint 入口

状态：正式

Owner：SOMA Java 产品蓝图

事实范围：目标用户、目标形态、目标使用方式、代表性场景与非目标

非事实范围：精确公共 API、实现算法、当前能力和验证结论

最后审查日期：2026-07-19

Blueprint 描述项目希望成为什么，并为 Design 提供方向约束。示例代码只表达目标体验，不承诺精确生成名称或当前已经支持。每份蓝图声明其设计约束入口；完整的反向追踪由 [Design 导航](../design/README.md)拥有。

## 蓝图

- [SOMA Java 产品蓝图](soma-java-product-blueprint.md)：项目目标、使用者体验和成功标准；
- [FJSP runtime state 蓝图](fjsp-runtime-state-blueprint.md)：增量候选 frontier 与显式调度策略；
- [VRP runtime state 蓝图](vrp-runtime-state-blueprint.md)：route state、局部 workspace 与可选 frontier；
- [连续仿真 runtime state 蓝图](simulation-runtime-state-blueprint.md)：dense 数值状态、外部事件堆和 trace 边界；
- [Game runtime state 蓝图](game-runtime-state-blueprint.md)：entity state、phase-local workspace 与 derived cache。

场景蓝图不是 SOMA 对业务算法的所有权声明。调度策略、路径搜索、数值模型、游戏规则和跨表事务仍由 application 拥有。
