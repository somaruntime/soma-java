## 变更目标

说明本次变更解决的问题、范围和明确非目标。

## Owner 与兼容性

- [ ] 已找到并先修改唯一正式 Owner 文档，或本次不改变设计事实
- [ ] 已说明 public/generated/schema/runtime-plan/error/release 兼容性影响
- [ ] 未把 README、report、guide 或 `docs/temp/` 当作事实源
- [ ] 未引入未决第三方 dependency 或跨模块反向依赖

## V1 防缩水

涉及的 Capability ID：

当前未实现但仍保留在 V1 的 capability、Phase 和 Gate：

- [ ] 本次只服务完整 Java-only SOMA V1；Phase 没有被改写为 `v0.x`、MVP、Lite、Basic 或独立产品目标
- [ ] 当前产物是最终 V1 架构的有效子集；后续可通过 additive completion 或 contract-preserving internal refinement 收敛
- [ ] 未引入后续必须迁移 consumer 的 temporary public/generated API
- [ ] 未把 temporary canonical storage/hot path、stub、fake 或 test-only bypass 当作 capability evidence
- [ ] 未实现 capability 仍映射到原完整出口和最终 Gate，没有被标记 `dropped`、`optional` 或 indefinite `deferred`
- [ ] 宪法、正式 Owner、capability ledger、Gate 和 release claim 未被实现反向改写；如有变化，已附用户/项目决策者批准和正式 scope-change 记录
- [ ] Closeout 包含 `V1 scope non-regression`，并确认后续不需要 public migration、核心事实迁移或 canonical hot-path rewrite

## 验证

- [ ] `./scripts/check-docs.sh`
- [ ] `./mvnw -B -ntp verify`
- [ ] `git diff --check`
- [ ] 已列出本次 surface 所需的 compile/golden/invariant/consumer/benchmark 证据

## 风险与遗留

记录 skipped check、known limitation、migration/rollback 或需要 owner 决策的事项。
