# Build support

本目录集中存放构建与资格机制，不是 SOMA 的第三项 production artifact：

- `linkage/`：runtime 与 processor contract version 的唯一构建资源 Owner；
- `codegen/`：仓库内部的机械 source consistency 工具；
- `qualification/`：按长期能力组织的可重放资格实现；其中`build-session.py`
  只为一次组合qualification绑定source candidate、Java/Maven工具链与artifact校验和，
  manifest仅位于ignored `target/`，不是持久cache或新的稳定入口；
- `delivery/`：本地 source delivery 的显式 allowlist。

日常入口始终使用 [`scripts/`](../scripts/README.md)，不要把这里的分层脚本当作新的产品 API。
