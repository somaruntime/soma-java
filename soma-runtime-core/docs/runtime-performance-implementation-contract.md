# Runtime 性能实现契约

类型：历史设计

状态：superseded

Owner：`soma-runtime-core`

当前取代者：[性能模型](../../docs/design/performance-model.md)、[Table、存储与访问](../../docs/design/table-storage-and-access.md)、[Runtime Core Map](../../docs/implementation-map/runtime-core-map.md)

事实范围：packed/primitive/fused/allocation-bounded runtime kernel、capacity/scratch、primary locator/exact index和stats overhead

非事实范围：跨模块性能模型、public API、benchmark scenario/结果和具体永久阈值

最后审查日期：2026-07-20

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `soma-runtime-core/docs/runtime-performance-implementation-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:soma-runtime-core/docs/runtime-performance-implementation-contract.md
```
