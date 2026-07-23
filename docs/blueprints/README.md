# Blueprint 导航

类型：Blueprint 入口

状态：正式

Owner：SOMA Java 产品蓝图

事实范围：SOMA 产品的目标用户、目标形态、目标使用方式与非目标

非事实范围：精确公共 API、实现算法、当前能力和验证结论

最后审查日期：2026-07-23

Blueprint 描述 SOMA 产品希望成为什么，并为 Design 提供方向约束。示例代码只表达目标体验，不承诺精确生成名称或当前已经支持。产品 Blueprint 声明其设计约束入口；完整的反向追踪由 [Design 导航](../design/README.md)拥有。

## 阅读约定

- 产品蓝图给出共同心智模型、访问旅程和 application responsibility；
- 未标为算法伪代码的片段按目标 Java 8 使用代码审查，但 Blueprint 不拥有精确 signature；
- reference path 可以为了可读性 materialize；hot path 必须先选择 Point/Candidate/Column/Key/Bulk/Ownership access family，并披露 current Index、IndexSnapshot、ColumnView、lookup、sort、scratch 与 allocation 边界；
- 业务顺序必须来自 total comparator 或 application-owned 专用结构；物理顺序和 current Index 不是跨 operation 契约；
- 场景 helper 可以省略领域实现细节，但不能隐藏决定 correctness 的 identity、可行性、时间/单位、stale validation 或 failure recovery；
- 条件式方案必须给出采用条件和证明义务，不能与 canonical path 同时维护为两份 live facts；
- 参考应用的 Blueprint 由应用自己拥有，只作为非规范性 consumer evidence，不进入 SOMA Design 追踪。

## 蓝图

- [SOMA Java 产品蓝图](soma-java-product-blueprint.md)：项目目标、使用者体验和成功标准；

当前可执行参考应用从 [`soma-examples`](../../soma-examples/docs/README.md) 进入。它们的领域目标、算法、跨表提交顺序和失败处置由 application 自己拥有。
