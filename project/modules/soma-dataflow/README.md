# soma-dataflow 文档入口

类型：Module

状态：正式

Owner：SOMA Java dataflow 模块导航

事实范围：DataFlow module 的 Design、实现、generated binding 与验证入口

非事实范围：复制 Transformation、execution、result 或 effect 语义

最后审查日期：2026-07-30

代码与构建入口位于 [`soma-dataflow`](../../../soma-dataflow/README.md)。

本模块的长期规范性语义由根级
[Transformation 模型](../../design/transformation-model.md)、
[DataFlow 执行模型](../../design/dataflow-execution-model.md)、
[物化边界](../../design/materialization-boundary.md)和
[性能模型](../../design/performance-model.md)拥有。

当前 implementation、generated binding、contract、reference differential 与
component evidence 从 [DataFlow Map](../../implementation-map/dataflow-map.md)、
[可执行契约地图](../../implementation-map/executable-contract-map.md)和
[测试与 evidence Map](../../implementation-map/test-and-evidence-map.md)进入。

历史契约由 Git 保存，不在 current checkout 维持平行 Design 或链接 tombstone。
