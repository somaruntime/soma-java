# Generated Table API 契约

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[Schema 与生成 API](design/schema-and-generated-api.md)、[Table、存储与访问](design/table-storage-and-access.md)、[Ownership 与 lifecycle](design/ownership-and-lifecycle.md)、[Materialization 边界](design/materialization-boundary.md)

事实范围：Generated keyed/dense Table、Direct API、Row/Key/Column Pipeline、Mutator、child facade 和 ColumnView 的用户语义

非事实范围：schema annotation、code generation 过程、TableStore 数据结构、deep materialization 细节和性能实现算法

最后审查日期：2026-07-20

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/generated-table-api-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/generated-table-api-contract.md
```
