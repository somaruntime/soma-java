# SOMA Java 产品文档

<!--
类型：Product Documentation Entry
状态：当前
Owner：SOMA Java 产品信息投影
受众：使用者、技术选型者、集成者
输入事实源：project/ 下的正式 Blueprint、Design、Report 与当前代码和交付物
最后审查日期：2026-07-30
-->

这里按任务提供使用 SOMA 所需的最小充分信息。内部设计、实现地图、Gate 和
qualification evidence 不作为默认阅读前置；需要追溯时，每个投影会链接其正式
事实来源。

## 我要开始使用

- [Getting Started](getting-started/README.md)：工具链、Maven 接入和第一个
  ordinary consumer；
- [SOMA Java V1 应用开发者手册（Word）](guides/soma-java-v1-application-developer-manual.docx)：
  完整建模、生成、运行、验证、试用和回退路径；
- [应用开发指南](guides/README.md)：lifecycle、resource、性能和排障入口。

## 我要做技术选型

- [架构与技术选型](architecture/README.md)：产品边界、架构概览和技术白皮书；
- [性能适用性与最佳用法](guides/performance-and-scale.md)：如何解读本机 evidence、
  规划大规模状态并避免常见性能坏味道；
- [已知限制](getting-started/java-v1-install-and-consumer-guide.md#14-known-limitations)；
- [支持边界](../SUPPORT.md)与[安全策略](../SECURITY.md)。

## 我要看真实案例

- [三个参考应用](examples/README.md)：工业动态调度、个体生态仿真和实时派工规则
  引擎；
- 每个应用都可以独立构建，并明确区分业务状态、SOMA runtime state、算法临时
  结构和 detached output。

## 我要贡献或维护

- [贡献指南](../CONTRIBUTING.md)；
- [项目事实入口](../project/README.md)；
- [模块导航](../project/modules/README.md)；
- [开发与维护过程](../project/process/development-guide.md)。

`docs/` 是产品信息投影，不重新定义 SOMA 的 Blueprint、Design、当前实现或
release evidence。
