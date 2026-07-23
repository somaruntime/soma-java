# Build 与依赖契约

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[系统架构](design/system-architecture.md)、[构建与验证](engineering/build-and-validation.md)

事实范围：Maven reactor、模块依赖方向、artifact classification、consumer build path、dependency policy、reproducibility 和 CI baseline

非事实范围：release approval、public API behavior、compiler lowering internals、test implementation和具体发布结果

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/build-and-dependency-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/build-and-dependency-contract.md
```
