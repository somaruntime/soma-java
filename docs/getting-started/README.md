# Getting Started

<!--
类型：Product Projection
状态：当前
Owner：SOMA Java consumer onboarding
受众：准备在 Java 8 Maven 项目中试用 SOMA 的应用开发者
输入事实源：当前 POM、外部 Maven consumer fixtures、正式兼容性 Design
最后审查日期：2026-07-30
-->

当前只提供 private-source 获取方式。开始前需要：

- 已获授权的 `somaruntime/soma-java` immutable source ref，或 Owner 提供且
  checksum、commit 与 provenance 可核对的 curated source archive；
- Amazon Corretto 8.502.07.1 full JDK 8；
- Git 与仓库自带 Maven Wrapper。

完整 private repository 包含设计、治理、测试和 evidence source；curated archive
只包含可构建 reactor、reference consumer、benchmark source 与必要入口。二者都不
表示 public GitHub Release 或 Maven Central publication。

按 [Java 8 安装与 Maven consumer 指南](java-v1-install-and-consumer-guide.md)
完成 artifact 安装、普通 Maven consumer 配置、Schema 声明、generated API
验证和最小运行。

需要完整教学路径时，阅读
[SOMA Java V1 应用开发者手册（Word）](../guides/soma-java-v1-application-developer-manual.docx)。
需要评估性能和规模边界时，继续阅读
[性能适用性与最佳用法](../guides/performance-and-scale.md)。
