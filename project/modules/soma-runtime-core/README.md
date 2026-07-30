# soma-runtime-core 文档入口

类型：Module

状态：正式

Owner：SOMA Java runtime-core 模块导航

事实范围：runtime module 的 Design、实现、协议与验证入口

非事实范围：复制 Table、Access、Ownership、Failure 或 Performance 语义

最后审查日期：2026-07-30

代码与构建入口位于 [`soma-runtime-core`](../../../soma-runtime-core/README.md)。

Runtime 的长期规范性语义由根级 [Table、存储与访问](../../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../../design/correctness-and-failure.md)、[Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md)及[性能模型](../../design/performance-model.md)拥有。

当前实现、协议和测试从 [Runtime Core Map](../../implementation-map/runtime-core-map.md)、[可执行契约地图](../../implementation-map/executable-contract-map.md)和[测试与 evidence Map](../../implementation-map/test-and-evidence-map.md)进入。

历史契约由 Git 保存，不在 current checkout 维持平行 Design 或链接 tombstone。
