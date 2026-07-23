# Runtime 正确性模型

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[Correctness 与 failure](design/correctness-and-failure.md)、[Ownership 与 lifecycle](design/ownership-and-lifecycle.md)

事实范围：runtime 跨组件不变量、状态机、失败原子性、correctness oracle 和 gate 映射

非事实范围：runtime class/API、具体数据结构算法、场景业务规则和性能结论

最后审查日期：2026-07-20

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/runtime-correctness-model.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/runtime-correctness-model.md
```
