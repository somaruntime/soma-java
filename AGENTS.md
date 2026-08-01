# soma_java Agent Guide

## 当前状态

SOMA Java 已从 clean-slate 起点建立正式 V1 Blueprint、Design、Engineering 与
Conformance baseline，实施准备结论为 `READY_FOR_IMPLEMENTATION`。
当前 active checkout 不包含 production source、production Maven reactor、generated
consumer API、benchmark、Example、CI/release workflow 或 package。正式 Design 不等于
implementation、performance 或 release 已经成立。

开始工作前必须读取：

- [项目状态](project/README.md)；
- [SOMA Java V1 产品蓝图](project/blueprint/README.md)；
- [Design 总览](project/design/README.md)；
- [V1 Implementation Plan](project/engineering/v1-implementation-plan.md)；
- [Conformance 与证据边界](project/conformance/README.md)。

随后只读取与任务直接相关的 Design Owner；不要把全部项目事实加载成每次任务的默认
前置。

## 固定身份与边界

- 产品品牌为 SOMA；
- repository 为 `somaruntime/soma-java`；
- copyright owner、publishing identity 和 maintainer 为 ArthurFeng；
- Java package / Maven group baseline 为 `io.github.somaruntime.soma`；
- 当前语言方向为 Java 8；
- License 为 Apache License 2.0；
- 品牌资产和权利边界由 `assets/` 与 `NOTICE` 拥有。

这些身份事实不预先决定 module、artifact、version、dependency、runtime algorithm 或
release profile。

## Clean-slate 约束

- predecessor 只存在于 Git ref `archive/pre-product-reset-2026-07-31`；
- 不复制、cherry-pick、包装或恢复 predecessor source；
- 不保留 legacy 目录、compatibility layer、双 API、migration adapter 或 `v2` 平行
  module；
- 不因为旧 type/test/benchmark 存在就要求新 Design 兼容；
- 需要历史经验时，只提取问题、约束和 evidence，并从当前 Design 重新推导实现；
- `DataFlow`、`Transformation`、`Candidate`、public `Batch`、public physical
  `Column`、`Segment` 和 manual `release()` 不属于 V1；
- ChildTable、`@SomaChild`、ownership graph、per-owner Table、implicit cascade 和
  cross-Table transaction 不属于 V1；1:M/N:M 使用普通 Table、endpoint ID、Index；
- V1 `@SomaTable` 不接受 `name` identity；package-private `.schema` declaration type
  决定父 package generated object、`XxxTable` 和 `xxxTable()`；
- application API 不泄漏 schema declaration type；
- production topology 已固定为 `soma-runtime` + `soma-processor`，但未经明确
  implementation authorization 仍不创建 production module/public API；授权后只从 I0
  开始，不并行铺开全部 slice。

## 正式事实与文档

- [Blueprint](project/blueprint/README.md)拥有产品意图、边界和成功标准；
- [Design](project/design/README.md)按关注点拥有长期规范性合同；
- code/config/tests 在出现后拥有当前 executable fact；
- [Conformance](project/conformance/README.md)记录 implementation gap 与 evidence；
- root/project README 是入口，不复制 Design；
- Manual、White Paper、Examples 在出现后是角色投影，不是 parallel Design；
- `project/temp/` 只承载 active candidate topic，当前无 active Temporary；
- 新的重大长期变化先进入新的 Temporary，经 Product Owner 裁决和验证后晋升，随后
  删除 Temporary；不得直接在正式 Design 中掩盖未裁决变化。

默认使用中文编写 Design、Report 和代码注释。

## Surface admission

新增 module、production type、public/generated API、dependency、test taxonomy、
benchmark、script、workflow、Process 或正式文档前，必须说明：

1. 独立 capability 与 consumer；
2. Owner、lifecycle 和 failure boundary；
3. 为什么当前 surface 不能承载；
4. 对应的 Blueprint requirement 与 Design Owner；
5. 需要什么 evidence 才能成立。

不要为未来可能性预建空目录、interface、abstraction、module 或 compatibility layer。
Implementation proposal 若需要改变 Blueprint/Design 产品语义，必须停下并请求 Product
Owner 裁决。

## 当前阶段验证

当前是 implementation-ready、pre-implementation 阶段。文档与 repository-surface
变更至少执行：

- 使用 `rg` / `rg --files` 搜索；
- 使用 `apply_patch` 编辑；
- 运行 `git diff --check`；
- 检查 Markdown 相对链接与 current route；
- 检查每项事实的唯一 Owner 和 Blueprint↔Design↔Conformance traceability；
- 确认 active checkout 没有 predecessor code、legacy API、build artifact 或 release
  claim；
- 记录但不外推本机环境事实。

P2 feasibility spike 已退役；其正式结论与限制由
[P2 Conformance record](project/conformance/p2-generated-api-feasibility.md)拥有。不得
把该 historical evidence 当成可运行 production test、完整 runtime、performance 或
release evidence。

Production surface 出现后，按
[V1 Implementation Conformance Gates](project/conformance/v1-implementation-gates.md)
建立相称的 compile、consumer、negative、runtime、concurrency、performance、build、
security、packaging 和 release Gate，并按唯一
[Implementation Plan](project/engineering/v1-implementation-plan.md)一次推进一个 slice。

## Git

- 长期分支只使用 `main`、`develop`、`release`；
- 常规工作在 `develop`；
- 保留用户现有修改；
- 未经明确授权不提交、不推送、不发布、不创建 GitHub Release 或 Package；
- destructive 操作必须先解析精确、可恢复 target；
- 不使用 destructive reset/checkout 覆盖用户 worktree。
