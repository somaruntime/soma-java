## 变更目标

说明本次变更解决的问题、范围和明确非目标。

## Owner 与兼容性

- [ ] 已从 Blueprint 确认目标并找到唯一 Design Owner，或本次不改变长期设计
- [ ] 已说明 public/generated/schema/runtime-plan/error/release 兼容性影响
- [ ] 未把 README、Report、Guide、Implementation Map、Temporary 或 superseded 历史文档当作 Design
- [ ] 未引入未决第三方 dependency 或跨模块反向依赖
- [ ] HGTECH 只用于组织/发布 metadata，SOMA public/generated/schema/runtime 概念未被组织名侵入；coordinates/artifact identity 未经批准不变

## V1 防缩水

涉及的 Blueprint / Design Owner：

当前仍存在的 Conformance 差距与 Gate：

- [ ] 实现继续服务正式 Blueprint/Design，没有把未实现目标改写为 future、optional、MVP、Lite 或永久非目标
- [ ] 当前产物是最终 Design 的有效子集；后续只需 additive completion 或 contract-preserving internal refinement
- [ ] 未引入后续必须迁移 consumer 的 temporary public/generated API
- [ ] 未把 temporary canonical storage/hot path、stub、fake 或 test-only bypass 当作 capability evidence
- [ ] 未闭合偏差仍由 Conformance 记录，没有被报告或实现静默隐藏
- [ ] Blueprint、Design、Owner、Gate 和 release claim 未被实现反向改写；如有变化，已附明确授权和正式设计决策
- [ ] Closeout 包含 `V1 scope non-regression`，并确认后续不需要 public migration、核心事实迁移或 canonical hot-path rewrite

## 验证

- [ ] `./scripts/check-docs.sh`
- [ ] `./mvnw -B -ntp verify`
- [ ] `git diff --check`
- [ ] 已列出本次 surface 所需的 compile/golden/invariant/consumer/benchmark 证据
- [ ] 已记录实际 JDK vendor/version/build、Maven Wrapper/Maven version（适用时）、OS、architecture 和完整命令；未把本机结果外推为 support matrix
- [ ] 未因 RC 暂无硬性性能数值门槛而跳过 performance-shape 或 benchmark-path evidence

## 风险与遗留

记录 skipped check、known limitation、migration/rollback 或需要 owner 决策的事项。
