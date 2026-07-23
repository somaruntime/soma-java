# Schema processing 契约

类型：历史设计

状态：superseded

Owner：`soma-processor`

当前取代者：[Schema 与生成 API](../../docs/design/schema-and-generated-api.md)、[兼容性、安全与版本](../../docs/design/compatibility-security-and-versioning.md)、[Compiler 与 codegen Map](../../docs/implementation-map/compiler-and-codegen-map.md)

事实范围：compile-time collection、validation、declaration order、normalized schema、exact hash、compatibility identity 和 diagnostics

非事实范围：public annotation semantics、generated API behavior、runtime storage 和 benchmark

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `soma-processor/docs/schema-processing-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:soma-processor/docs/schema-processing-contract.md
```
