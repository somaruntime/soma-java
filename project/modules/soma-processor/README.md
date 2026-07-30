# soma-processor 文档入口

类型：Module

状态：正式

Owner：SOMA Java processor 模块导航

事实范围：javac integration、processing、codegen 的 Design、实现与验证入口

非事实范围：重新定义 schema、generated API 或 compatibility 语义

最后审查日期：2026-07-30

代码与构建入口位于 [`soma-processor`](../../../soma-processor/README.md)。

本模块的长期规范性语义由根级 [系统架构](../../design/system-architecture.md)、[Schema 与生成 API](../../design/schema-and-generated-api.md)及[兼容性、安全与版本](../../design/compatibility-security-and-versioning.md)拥有。

当前 implementation、fixture、golden 和 Gate 从 [Compiler 与 codegen Map](../../implementation-map/compiler-and-codegen-map.md)、[可执行契约地图](../../implementation-map/executable-contract-map.md)和[测试与 evidence Map](../../implementation-map/test-and-evidence-map.md)进入。

历史契约由 Git 保存，不在 current checkout 维持平行 Design 或链接 tombstone。
