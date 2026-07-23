# SOMA Java V1 实现策略

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[设计宪法](design/soma-java-design-constitution.md)、[Validation Gate 治理](engineering/validation-gates.md)、[Conformance](conformance/README.md)

事实范围：跨模块实现架构、垂直切片顺序、阶段出口和 V1 防缩水规则

非事实范围：具体 class/package、runtime 算法参数、项目排期和 release 结果

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/implementation-strategy.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/implementation-strategy.md
```
