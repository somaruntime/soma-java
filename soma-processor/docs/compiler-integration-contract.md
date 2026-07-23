# Compiler integration 契约

类型：历史设计

状态：superseded

Owner：`soma-processor`

当前取代者：[系统架构](../../docs/design/system-architecture.md)、[Schema 与生成 API](../../docs/design/schema-and-generated-api.md)、[Compiler 与 codegen Map](../../docs/implementation-map/compiler-and-codegen-map.md)

事实范围：`@SomaValue` source lowering、javac integration、processor phase ordering、supported compiler/build boundary 和 fail-closed behavior

非事实范围：annotation logical semantics、generated API behavior、normalized schema/hash、runtime storage 和 IDE 产品实现

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `soma-processor/docs/compiler-integration-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:soma-processor/docs/compiler-integration-contract.md
```
