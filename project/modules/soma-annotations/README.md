# soma-annotations 文档入口

类型：Module

状态：正式

Owner：SOMA Java annotation 模块导航

事实范围：模块职责、Design、当前 public surface、实现与验证入口

非事实范围：重新定义 annotation、schema 或 generated API 语义

最后审查日期：2026-07-30

代码与构建入口位于 [`soma-annotations`](../../../soma-annotations/README.md)。

本模块的长期规范性语义由根级 [Schema 与生成 API](../../design/schema-and-generated-api.md) 拥有；当前 public surface 由 annotation source、golden 和[可执行契约地图](../../implementation-map/executable-contract-map.md)拥有或登记。

当前实现从 [Compiler 与 codegen Map](../../implementation-map/compiler-and-codegen-map.md) 进入。

本目录不再拥有独立 Design；历史契约由 Git 保存。当前事实与根级 Design 冲突时，
修改实现或正式 Owner，不建立平行 Owner。
