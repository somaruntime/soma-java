# 正式切换前审查

类型：Temporary

状态：审查完成，具备切换条件

Owner：SOMA Java 文档体系重构专题

事实范围：候选体系对预期工作方式的承载能力、发现的问题、优化结果、rehearsal 与切换前判定

非事实范围：正式启用新体系、修改产品代码、关闭产品场景差距或声明 release readiness

审查输入：框架 `1.0.0-rc.2`、现行文档 `fe163b8`、实现 `b991f4c`

最后审查日期：2026-07-20

## 1. 审查问题

本轮不是检查目录是否齐全，而是回答新体系能否支持以下真实工作：先理解目标 Blueprint，找到唯一 Design，借 Implementation Map进入当前代码/测试，完成有授权的功能或专题变更，以 Conformance识别偏差，用 Engineering形成可信 evidence，并把输出放入 Report；重大设计在 Temporary中保持独立直到最后原子固化。

## 2. 发现与优化

| 审查面 | 初始问题 | 本轮处置 | 当前判定 |
|---|---|---|---|
| Blueprint | VRP/Simulation/Game 只有几十行摘要，无法承载使用者视角 | 吸收现行 rich research blueprint，补齐 annotation、API journey、Access Pattern、权威数据角色和待验证取舍 | 通过 |
| Design 颗粒度 | 高层设计较清楚，但 schema/API、runtime plan/stats 的长期边界过薄 | 扩充 Schema/generated、compatibility/security/versioning；新增 Runtime Plan/observability Owner | 通过 |
| 代码事实边界 | “不迁移详细契约”可能被误解为丢失精确 API | 新增 executable contract map；明确代码/golden/schema artifact/validator拥有当前精确 surface，Design拥有目标语义与演进规则 | 通过 |
| Owner 唯一性 | runtime plan/stats 分散在 correctness/performance，Reports 物理位置未裁决 | 分离专属 Design Owner；登记 `guides/`、`reports/` 和 example module output 的逻辑 Report映射 | 通过 |
| 迁移完整性 | 旧体系只有文件级第一版 mapping | 对 32 份现行正式 Owner、四份旧 Blueprint、Guides/Reports 与 executable entry逐项裁决 | 通过 |
| Implementation Map | baseline 名义可能被 docs-only commit 误解 | 定义“最后 implementation-affecting commit”语义；登记 exact surface与 code/test入口 | 通过 |
| Conformance | 产品场景差距、G6 blocked 与文档切换 blocker混在一起 | 明确已裁决产品差距不阻塞文档切换；切换 lifecycle单独管理 | 通过 |
| Engineering | 缺少新目录 checker 的可执行要求和 Gate Owner | 增加 metadata/current/superseded/temp/baseline规则与 G0–G6 governance | 通过 |
| 信息预算 | 现行500行统一限制会拒绝rich Blueprint | 规定分类化信息预算；Blueprint按使用者 journey保留深度，Map仍保持简短 | 通过 |
| Report | 候选 `docs-temp/reports` 可能形成第三入口 | 明确 staging-only；正式输出继续由 `guides/` 与 `reports/` 承载 | 通过 |

## 3. 迁移与目录 rehearsal

在 `/tmp` 的隔离副本中，把候选 Blueprint、Design、Implementation Map、Conformance 和 Engineering overlay 到目标 `docs/<category>/` 深度，并执行以下检查：

- 35 份目标分类 Markdown required metadata完整；
- 每个非 README 文档被其分类入口索引；
- 相对 Markdown link在目标路径全部可解析；
- 正式分类没有指向 `docs-temp` 或 `docs/temp` 的事实引用；
- 每份 Implementation Map具有 `b991f4c` 实现核对基线；
- 32 份现行正式 Owner全部出现在事实迁移审计中。

Rehearsal 第二次以明确 `/bin/sh` 语义运行并通过。第一次 ad-hoc zsh harness暴露了 command-substitution word splitting差异；因此正式 checker必须保留明确 POSIX shell入口，不能依赖调用者交互 shell语义。

当前仓库的 `./scripts/check-docs.sh`、metadata audit、`git diff --check`与完整 `./scripts/check.sh`均已通过，完整 Gate以`project-check: ok`结束。当前 checker仍服务旧体系，正式切换时必须与入口和 metadata规则在同一变更中更新，并在切换后的目标结构上重跑完整 Gate。

## 4. 剩余动作的性质

以下事项仍未执行，但它们属于正式切换本身，不是候选设计缺陷：

1. 获得明确切换授权并锁定切换 candidate；
2. promote候选分类、重写正式入口与 Agent导航；
3. 将旧 Owner原子标记 superseded，将 example docs reclassify 为 current-executable Report；
4. merge或删除 candidate Reports staging，修复历史 Blueprint links；
5. 更新正式 checker，运行完整 Gate并形成 Governance Report；
6. 最后删除本 Temporary，确认没有正式引用指向它。

其中任何一步失败都应整体回退切换，不允许旧新 Owner长期并列。

## 5. 判定

候选体系已经能够承载预期的设计驱动工作方式，并且在信息预算、实现事实边界、场景表达、迁移完整性和可执行治理之间取得了可用平衡。本轮未发现仍需继续修改候选分类或权威模型的 blocking defect。

因此，当前判定是：**内容与治理方案 ready for cutover，但尚未正式启用**。下一专题可以直接执行原子切换；不得把此结论误写成已经切换、产品场景差距已实现或 G6 已通过。
