# SOMA Java 项目组织治理报告

类型：Report / Project Organization

状态：cutover implementation complete；final DOCX 与同 SHA Gate evidence unresolved

Owner：SOMA Java 项目组织治理

受众：SOMA 使用者、贡献者、维护者、release reviewer 与 Agent

适用版本：`1.0.0`

输入事实源：面向角色与场景的项目组织框架 `2.0.0-rc.1`、正式 Blueprint/
Design/Implementation Map/Conformance/Process、产品文档、当前仓库表面、文档与
release checker

事实范围：本次信息架构 cutover、角色旅程、源码交付边界、验证状态和 surface delta

非事实范围：重新定义 SOMA 产品语义、自动授权 tag/public/Maven release、动态
CI 运行结果或 production readiness

最后审查日期：2026-07-30

## 1. 问题与治理目标

原信息架构把 Blueprint、Design、Implementation Map、Conformance、工程过程、
报告、用户指南和模块内部事实分散在产品、工程、报告与模块表面。信息没有丢失，
但使用者进入仓库时会先看到完整开发全貌；同一角色需要跨多个目录判断哪些内容是
产品说明、哪些内容是设计事实、哪些只是当前 evidence。

本次治理采用“事实体系、角色投影、交付物”三层模型：

- `project/` 保存 SOMA 怎样被设计、实现、验证和演进的完整项目事实；
- 根 README、`docs/` 和 example README 面向使用者、集成者与技术选型者投影
  可行动信息；
- Maven artifact、精简源码包和完整 private repository 分别声明自己的内容集合，
  不再把整个 checkout 默认视为同一种交付物。

目录不是保密边界，`project/` 仍进入 Git；治理目标是降低默认噪声、保持唯一 Owner
和提供可验证的交付选择，而不是隐藏项目事实。

## 2. 当前信息架构

| 当前层次 | Owner / 入口 | 职责 |
|---|---|---|
| Product Projection | 根 README、`docs/getting-started/`、`docs/guides/`、`docs/architecture/`、`docs/examples/` | 按使用、开发、选型和案例场景提供最小充分信息 |
| Blueprint | `project/blueprint/` | 拥有产品目标形态和目标使用方式 |
| Design | `project/design/` | 拥有长期规范性语义 |
| Modules | `project/modules/<module>/` | 导航模块职责、实现、测试与局部应用事实 |
| Implementation Map | `project/implementation-map/` | 投影当前代码与可执行入口 |
| Conformance | `project/conformance/` | 比较目标、Design、实现与 evidence |
| Process | `project/process/` | 治理构建、验证、benchmark、文档和 release |
| Report | `project/reports/` | 拥有当前性能、审查与 release 结论 |
| Temporary | `project/temp/<topic>/` | 只承载已授权 active topic，完成即删除 |
| Example 使用入口 | application 根 README、`docs/examples/` | 从用户旅程直接到三个可运行 consumer |

当前 checkout 不保留平行 Owner、兼容副本、tombstone 或 migration-only index；
历史由 Git 保存。根级 `AGENTS.md`、`CONTRIBUTING.md`、`SECURITY.md`、
`SUPPORT.md` 和模块 README 因平台或工具发现规则保留，但只作为角色入口。

## 3. 权威关系与角色旅程

| 角色 / 场景 | 默认入口 | 可以完成的任务 | 是否必须进入 `project/` |
|---|---|---|---|
| 首次使用者 | 根 README → `docs/getting-started/` | 获取、构建、生成 API、运行最小 consumer | 否 |
| 应用开发者 | `docs/guides/`、`docs/examples/` | 建模、生命周期、排障、运行三个 reference application | 否 |
| 技术选型者 | `docs/architecture/`、性能与规模指南 | 理解架构、适用边界、已验证规模与不承诺项 | 否；需要审计时可跟随正式 evidence 链接 |
| 贡献者 | `CONTRIBUTING.md` → `project/modules/`、`project/process/` | 找到 Design、实现、测试、Gate 和变更闭环 | 是 |
| 维护者 / release reviewer | `project/README.md` | 到达全部正式 Owner、Conformance、Report 与 delivery process | 是 |
| Agent | `AGENTS.md` → 当前任务 Owner | 在授权边界内定位事实和验证要求 | 按任务需要 |

产品投影只改变教学顺序和表达，不重新定义 Blueprint/Design。用户遇到 compatibility、
security、performance 或 failure 边界时可以追溯正式事实，但完成普通接入不以阅读
完整项目治理过程为前置。

## 4. 精简源码交付

`scripts/manifests/source-release-files.txt` 是精简源码包的显式 allowlist；
`scripts/source-package-smoke.sh` 从 clean immutable commit 生成
`soma-java-1.0.0-source.tar.gz`，并验证：

- 内容集合与 manifest 完全一致；
- Maven reactor、四个 production module、三个 reference consumer、benchmark
  module、Wrapper、License/NOTICE/CHANGELOG 和 toolchain check 完整；
- `project/`、repository tests、CI、原始 evidence、本机文件和敏感路径不进入包；
- 依赖完整Design事实的Agent Skill不形成断裂的archive副本，仍从同SHA完整
  authenticated repository获取；
- 从生成的 archive 本身执行 exact toolchain check 与 Maven reactor verify；
- archive checksum、commit、version、dirty state 与 delivery profile 写入
  provenance。

完整 private repository 继续包含 `project/` 并拥有可审查历史；精简源码包是
额外的受控交付视图，不冒充 public GitHub Release 或 Maven publication。

## 5. 可执行门禁与当前验证

文档 Gate 已扩展为 fail closed 地验证：

- project facts、产品投影、角色入口、metadata、唯一索引和相对链接；
- Design Owner、Implementation Map 基线、模块集中和 example 归属；
- legacy current 路径、平行模块 `docs/`、superseded tombstone 和未登记文档为零；
- canonical AI Skill 的安装边界与正式 Owner 路径；
- source allowlist 有序、无内部治理/evidence surface，并由 release workflow 调用；
- DOCX 不仅是合法 OOXML archive，而且不得保留旧 current 路径。

当前已通过 `./scripts/check.sh fast` 的 Corretto toolchain、文档/scope、架构、
Maven reactor 与 diff 检查。两份产品 DOCX 的 OOXML 中仍存在 cutover 前路径；
在完成最小文本替换、逐页渲染复核和最终同 SHA Gate 前，本专题不得声明 closed，
Temporary 不得退役。

## 6. Scope non-regression

本次治理只改变信息架构、入口、checker、release workflow 和源码交付集合：

- production Java type、generated/public API、module、artifact、dependency 与
  runtime semantics delta 为零；
- test/fixture/benchmark workload 与性能 baseline delta 为零；
- Blueprint、Design、Conformance 和 Report 的既有事实通过移动保留，不从当前
  checkout 删除；
- 没有新增 parallel Owner、长期 roadmap、archive/tombstone 或 migration API；
- public GitHub、Maven Central、tag、GitHub Release 与 production readiness
  仍未选择或授权。

最终收口条件是 DOCX 内嵌路径归零、角色旅程与全部适用 Gate 通过、源码包和
private same-SHA qualification 可校验，以及
`project/temp/project-organization-v2/` 退役。动态 workflow 状态只由同 SHA
retained evidence 解析，不在 source report 中预写。
