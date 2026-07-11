# soma_java Agent Guide

`soma_java` 是 Java 8 annotation schema + Java columnar runtime 项目。当前处于完整 V1 implementation/release-evidence 阶段；实时 Gate 状态以报告索引为准。

## 必读入口

- 所有正式设计事实从 [docs/README.md](docs/README.md) 进入；
- 文档分类、Owner 和生命周期遵守 [docs/documentation-governance.md](docs/documentation-governance.md)；
- 模块内部修改前阅读对应 `<module>/docs/README.md`；
- README、AGENTS、guides、reports 和 `docs/temp/` 都不是设计事实源。

## Project Boundary

- 只承载 Java 8 使用场景；
- 不承诺 Python、C ABI、native runtime 或跨语言 FFI；
- 不在正式设计决策前增加第三方依赖；
- 实现不得把 schema object、DTO、Java Collection graph 或 metadata interpreter 变成 runtime hot storage/path。

以上是 Agent 操作护栏；完整语义仍以正式 owner 文档为准。

## Release Identity and Validation Baseline

- 发布身份以 [Versioning 与 release 契约](docs/versioning-and-release-contract.md) 为唯一事实源：组织与发布主体为 HGTECH，产品品牌为 SOMA，Maven `groupId` / Java package root 为 `com.hgtech.soma`，正式 artifact 名保持 `soma-*`；
- HGTECH 只能进入真实组织归属、SCM、POM/publishing/provenance 等发布边界，不得成为 annotation、generated API、runtime type、error、schema 或其他 SOMA 产品公共概念的命名前缀；历史排除身份遵守正式 Owner 的零扩散规则；
- Apache License 2.0 是 V1 推荐方向；正式 `LICENSE`、POM metadata 和 G6 evidence 未完成前，不得声明 public RC/release readiness；
- V1 实施先使用本机完整 JDK 8 javac、Maven Wrapper 和当前 OS/architecture；每次 validation 记录实际 JDK vendor/version/build、OS、architecture 和命令，本机通过不得外推为正式支持矩阵；
- G6 support matrix 未确定或未验证时只能保持 `not-started` 或 `blocked`，不得标记完成、从 V1 删除或用单机 smoke 替代。

## V1 Scope Preservation

- Java-only SOMA V1 是唯一产品实施目标；`Phase 0` 至 `Phase 6` 只是 [实现顺序和验证 checkpoint](docs/implementation-strategy.md)，不是 `v0.x`、MVP、Lite、Basic、独立 release 或替代目标；
- 实施任务开始前必须阅读 [SomaTable 设计宪法](docs/soma-table-design-constitution.md)、[实现策略](docs/implementation-strategy.md)、[V1 验证门禁](docs/validation-gates.md) 和对应 module owner docs；
- 用户启动 Codex Goal 时，只建立“完成完整 SOMA Java V1 并满足 G0-G6”的总 Goal；Phase 只能是 plan/checkpoint，总 Goal 不得因单个 Phase 完成而标记 complete；
- 编辑代码前必须列出本次涉及的 Capability ID、唯一 Owner、slice 出口、故意未实现的 V1 breadth、禁止捷径和计划 evidence；
- 允许当前 Phase 尚未实现后续 breadth，但已实现部分必须是最终 V1 架构的有效子集；不得用临时 public/generated API、`List<Row>`/DTO live storage、reflection/metadata interpreter、Java Stream hot path、share/reparent child、generic error 或 test-only bypass 代替正式语义；
- 未实现 capability 只能保持 `not-started`、`in-progress`、`implemented-unverified`、`evidenced` 或 `blocked`；不得自行标记 `dropped`、`optional`、无目标阶段的 `deferred`，也不得移入未批准的新版本；
- 如果后续达到 V1 需要迁移 consumer、核心事实、public/generated contract 或 canonical hot path，当前 slice 必须停止，不能以“以后重构”为理由继续；
- 修改宪法、capability ledger、正式 Owner、public/schema/runtime semantics、最终 Gate 或 release claim 前必须停止并请求用户明确决定；批准后先修改唯一 Owner，再实施代码；
- 实施 closeout 必须包含 `V1 scope non-regression`：Capability 状态变化、实际 evidence、未实现项的原 Phase/Gate、Owner/Gate 是否变化，以及后续是 additive completion/internal refinement 还是 migration/rewrite；后者不得 closeout；
- 不得为合理化已经写出的 shortcut 而反向修改正式目标、non-goal、Owner 文档或 Gate。

## Module Ownership

- `soma-annotations`：public schema annotation；
- `soma-processor`：javac 8 integration、processing、normalization/hash、diagnostics、code generation；
- `soma-runtime-core`：TableStore、lifecycle、runtime plan、errors/diagnostics、runtime 性能实现；
- `soma-testkit`：compile/golden/invariant/evidence helpers；
- `soma-examples`：formal Java 8 scenarios 和 Access Pattern Cards；
- `soma-benchmarks`：benchmark evidence 与 runtime-state lanes。

跨模块 public semantics 由根级正式契约拥有；模块只能拥有自己的实现义务，不使用联合 Owner。

## Documentation Workflow

- 文档、报告和代码注释默认使用中文；
- 正式设计进入 `docs/` 或 `<module>/docs/`；
- 普通临时设计在固化后迁入 Owner 并删除；
- 四份长期研究蓝图保留在 `docs/temp/`，但不能被实现/gate 当作事实源；
- 用户/开发者指南未来进入 `guides/`，不进入 `reports/`；
- 报告只记录审查、验证、benchmark 和 release evidence；
- 修改后运行 `./scripts/check.sh`；需要缩小验证时，至少运行 `./scripts/check-docs.sh`、`git diff --check` 和与变更 surface 相称的 Maven validation。

## File and Git Rules

- 使用 `rg` / `rg --files` 搜索；
- 保留用户现有未提交修改，不覆盖无关内容；
- 本地文件编辑使用 `apply_patch`；
- Java 保持 Java 8；
- V1 compiler authority 是正式契约中的 full JDK 8 javac；不能用新 JDK 的 `--release 8` 冒充 supported transformer；
- 长期分支只使用 `main`、`develop`、`release`；常规工作在 `develop`；
- 未经用户要求不创建其他长期分支，不执行 destructive Git 操作。
