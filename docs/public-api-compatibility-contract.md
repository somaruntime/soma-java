# Public API 与兼容性契约

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[兼容性、安全与版本](design/compatibility-security-and-versioning.md)

事实范围：public/generated/internal surface 分类、兼容性维度、版本身份、deprecation 和 consumer migration

非事实范围：artifact 发布流程、具体 annotation/API 语义、schema hash 算法、runtime plan 参数和 release 结果

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/public-api-compatibility-contract.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/public-api-compatibility-contract.md
```
