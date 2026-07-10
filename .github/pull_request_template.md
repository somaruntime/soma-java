## 变更目标

说明本次变更解决的问题、范围和明确非目标。

## Owner 与兼容性

- [ ] 已找到并先修改唯一正式 Owner 文档，或本次不改变设计事实
- [ ] 已说明 public/generated/schema/runtime-plan/error/release 兼容性影响
- [ ] 未把 README、report、guide 或 `docs/temp/` 当作事实源
- [ ] 未引入未决第三方 dependency 或跨模块反向依赖

## 验证

- [ ] `./scripts/check-docs.sh`
- [ ] `./mvnw -B -ntp verify`
- [ ] `git diff --check`
- [ ] 已列出本次 surface 所需的 compile/golden/invariant/consumer/benchmark 证据

## 风险与遗留

记录 skipped check、known limitation、migration/rollback 或需要 owner 决策的事项。
