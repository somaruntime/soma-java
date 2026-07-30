# soma_java Agent Guide

## 当前状态

本仓库正在从干净起点重新定义 SOMA Java。当前 active checkout 不包含 production
code、Maven reactor、测试、benchmark、example、发布 workflow 或可用 consumer
API。不得从 Git 历史中的 predecessor 推断当前 capability、兼容性、性能或发布
状态。

开始工作前必须读取：

- [项目状态](project/README.md)；
- [产品基础决策](project/temp/soma-product-foundation/README.md)；
- [逻辑层 API 草稿](project/temp/soma-product-foundation/logical-api-draft.md)。

## 固定身份与边界

- 产品品牌为 SOMA；
- 仓库为 `somaruntime/soma-java`；
- copyright owner、发布主体和 maintainer 为 ArthurFeng；
- Java package / Maven group 基线为 `io.github.somaruntime.soma`；
- 当前语言方向为 Java 8；
- License 为 Apache License 2.0；
- 品牌资产和权利边界由 `assets/` 与 `NOTICE` 拥有。

这些身份事实不预先决定 module、artifact、generated type、annotation、runtime
implementation 或 release profile。

## Clean-slate 约束

- predecessor 只存在于 Git ref `archive/pre-product-reset-2026-07-31`；
- 不复制、cherry-pick 或包装 predecessor source；
- 不保留 legacy 目录、兼容层、双 API、migration adapter 或 `v2` 平行 module；
- 不因为旧类型、旧测试或旧 benchmark 已存在就要求新设计兼容；
- 若需要历史经验，只提取问题、约束和 evidence，重新从当前 Design 推导实现；
- `DataFlow`、`Transformation`、`Candidate`、public `Batch`、public physical
  `Column`、`Segment` 和 manual `release()` 不属于当前候选产品模型；
- 在 Product Owner 完成正式设计裁决前，不创建 production module 或 public API。

## 设计与文档

- 当前两份 `project/temp/soma-product-foundation/` 文档是 active Temporary；
- `README.md` 是候选决策 Owner，`logical-api-draft.md` 只是其 API 投影；
- Temporary 不能冒充正式 Blueprint/Design，也不能被实现反向改写；
- 稳定事实应按职责 promotion 到唯一正式 Owner，完成审查和 evidence 后删除
  Temporary；
- 文档和代码注释默认使用中文；
- 根 README 只承担产品入口，不复制完整设计。

## Surface admission

新增 module、production type、public/generated API、dependency、test taxonomy、
benchmark、script、workflow 或正式文档前，必须说明：

1. 独立 capability 与用户；
2. Owner、lifecycle 和 failure boundary；
3. 为什么当前 surface 不能承载；
4. 与产品基础决策的关系；
5. 需要什么 evidence 才能成立。

不要为未来可能性预建目录、接口、抽象或兼容层。

## 当前阶段验证

当前是 Markdown 与 repository-surface 阶段：

- 使用 `rg` / `rg --files` 搜索；
- 使用 `apply_patch` 编辑；
- 运行 `git diff --check`；
- 检查 Markdown 相对链接；
- 确认 active checkout 没有 predecessor code、build artifact 或 release claim；
- 记录但不外推本机环境事实。

出现真实代码和 build system 后，再由正式 Process Owner 定义相称的 compile、
consumer、negative、performance 和 release Gate。

## Git

- 长期分支只使用 `main`、`develop`、`release`；
- 常规工作在 `develop`；
- 保留用户现有修改；
- 未经明确授权不推送、不发布、不创建 GitHub Release 或 Package；
- destructive 操作必须先有精确、可恢复的目标。
