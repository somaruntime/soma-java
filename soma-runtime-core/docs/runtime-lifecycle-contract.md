# Runtime lifecycle 契约

类型：历史设计

状态：superseded

Owner：`soma-runtime-core`

当前取代者：[Ownership 与 lifecycle](../../docs/design/ownership-and-lifecycle.md)、[Correctness 与 failure](../../docs/design/correctness-and-failure.md)、[Runtime Core Map](../../docs/implementation-map/runtime-core-map.md)

事实范围：child ownership、materialization accounting、epoch/view/pipeline lifecycle、mutation coordination、concurrency 和 release

非事实范围：public API naming、storage data structure、schema declaration 和 benchmark claim

最后审查日期：2026-07-20

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `soma-runtime-core/docs/runtime-lifecycle-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:soma-runtime-core/docs/runtime-lifecycle-contract.md
```
