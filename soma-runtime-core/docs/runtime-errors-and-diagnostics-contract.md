# Runtime errors 与 diagnostics 契约

类型：历史设计

状态：superseded

Owner：`soma-runtime-core`

当前取代者：[Correctness 与 failure](../../docs/design/correctness-and-failure.md)、[Runtime Plan 与可观测性](../../docs/design/runtime-plan-and-observability.md)、[可执行契约地图](../../docs/implementation-map/executable-contract-map.md)

事实范围：runtime exception envelope、stable error code/category/context、callback failure、stats snapshot/reset 和 logging side-effect boundary

非事实范围：schema compile diagnostics、public generated method naming、lifecycle state transition、runtime algorithm 和 application logging policy

最后审查日期：2026-07-20

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md
```
