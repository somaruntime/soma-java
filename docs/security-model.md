# Security model

类型：历史设计

状态：superseded

Owner：根项目协调层

当前取代者：[兼容性、安全与版本](design/compatibility-security-and-versioning.md)、[测试与 evidence](engineering/testing-and-evidence.md)、[Release 治理](engineering/release-governance.md)

事实范围：SOMA Java trust boundary、protected assets、compile/runtime abuse cases、resource exhaustion、diagnostic exposure 和 supply-chain obligations

非事实范围：业务授权、application data classification、漏洞报告联系人、具体修复结果和第三方 scanner 配置

最后审查日期：2026-07-10

历史正文基线：commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 的 `docs/security-model.md`

本路径仅保留历史链接稳定性与 provenance，不再拥有当前事实，也不参与 current 文档导航。当前工作必须使用上方“当前取代者”。

## 历史正文

切换前的完整正文由 Git 历史保存，可使用以下命令读取：

```sh
git show 74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1:docs/security-model.md
```
