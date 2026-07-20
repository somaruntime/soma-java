# Design 导航与 Owner 表

类型：Design 入口

状态：候选

Owner：SOMA Java 系统设计

事实范围：候选 Design 的唯一 Owner 分配与导航

非事实范围：当前实现位置、验证结果和迁移过程

最后审查日期：2026-07-20

Design 拥有系统应当遵守的长期规范性设计。这里按抽象层次和关注点拆分，不按现有文件逐份翻译。

| Design Owner | 唯一拥有的事实 |
|---|---|
| [设计宪法](soma-java-design-constitution.md) | 总体原则、产品边界和不可违反的不变量 |
| [领域语言](domain-language.md) | 跨模块 canonical 术语和“不等同于”边界 |
| [系统架构](system-architecture.md) | 模块职责、编译链、运行时分层和依赖方向 |
| [Schema 与生成 API](schema-and-generated-api.md) | annotation 语义、schema normalization、生成接口和命名边界 |
| [Table、存储与访问](table-storage-and-access.md) | keyed/dense、packed SoA、exact access、IndexBuffer 和 Row Pipeline |
| [Ownership 与 lifecycle](ownership-and-lifecycle.md) | root/child ownership、view、epoch、release 和资源生命周期 |
| [Correctness 与 failure](correctness-and-failure.md) | 原子性、一致性、结构化错误和失败后的可信状态 |
| [Materialization 边界](materialization-boundary.md) | detached object graph、预算、导出边界和 allocation admission |
| [Runtime Plan 与可观测性](runtime-plan-and-observability.md) | create-time plan、resource admission、plan identity、stats 与诊断副作用边界 |
| [性能模型](performance-model.md) | hot-path 机械形状、成本模型、优化约束与证据要求 |
| [兼容性、安全与版本](compatibility-security-and-versioning.md) | 兼容面、协议身份、输入信任边界、产品与发布身份 |

一个事实如果跨越多个关注点，由最直接决定其语义的文档拥有，其他文档只链接。设计宪法只拥有系统级不变量，不复制机制操作细节；精确的当前 signature、field、error code 和默认常量由代码及[可执行契约地图](../implementation-map/executable-contract-map.md)定位，不能在 Design 中维护第二份实现清单。实现路径进入 Implementation Map；测量结果进入 Report。
