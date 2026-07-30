# 架构与技术选型

<!--
类型：Product Projection
状态：当前
Owner：SOMA Java architecture projection
受众：技术选型者、架构师与应用负责人
输入事实源：SOMA Java 产品 Blueprint、Design、性能与 support reports
最后审查日期：2026-07-30
-->

SOMA Java 是 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 的
Java 8 runtime-state computing library。它通过编译期生成的类型安全 API，把稳定
Schema 降低为 packed columnar storage 和封闭访问能力；运行时不使用反射或
metadata interpreter 驱动逐行 hot loop。

适合采用 SOMA 的系统通常同时具有：

- 大量、频繁变化的进程内状态；
- 稳定字段和明确访问模式；
- 高频筛选、精确定位、聚合、关联与受控更新；
- 对 allocation、GC、失败原子性和生命周期有明确要求。

如果目标是任意对象容器、动态 SQL、跨进程存储、分布式查询或通用工作流，SOMA
不是合适抽象。

深入材料：

- [SOMA Java V1 技术白皮书（Word）](soma-java-v1-technical-white-paper.docx)；
- [性能适用性与最佳用法](../guides/performance-and-scale.md)；
- [三个参考应用](../examples/README.md)；
- [当前支持边界](../../SUPPORT.md)。

需要审查规范性架构、能力契约和证据时，再进入
[项目 Blueprint](../../project/blueprint/README.md)、
[Design](../../project/design/README.md)和
[当前 Report](../../project/reports/README.md)。
