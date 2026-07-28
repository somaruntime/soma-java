# 构建与验证

类型：Engineering

状态：正式

Owner：SOMA Java build/validation 过程

事实范围：受支持的本机构建基线、验证层次、命令与环境记录要求

非事实范围：产品功能语义、正式支持矩阵和某次 Gate 结果

最后审查日期：2026-07-29

## 1. 基线

- 使用项目 Maven Wrapper；
- V1 compiler/runtime validation 使用 Amazon Corretto 8.502.07.1 full JDK 8
  （Java `1.8.0_502-b07`、`javac 1.8.0_502`）；
- 根 Maven reactor 必须在 Java 8 source/target 下构建；
- Zulu和其他JDK distribution不属于当前验真或目标支持范围，不要求多vendor
  重放；
- Corretto本机通过只说明实际记录的version/build、OS和architecture，不自动
  外推到其他Corretto update或平台；
- 不用新 JDK 的 `--release 8` 替代 compiler integration evidence。

macOS开发机推荐使用`brew install --cask corretto@8`，并以
`/usr/libexec/java_home -v 1.8`选择JDK。Homebrew负责安装与升级机制，但一次
`brew upgrade`不会自动扩大SOMA支持范围；新update必须先通过兼容验证，再原子
更新exact toolchain、CI与support evidence。

Canonical build 必须从根 Maven Wrapper进入同一 reactor graph。Production consumer 的完整运行边界是 runtime-core + dataflow，annotations/processor进入编译生成路径；只使用 direct Access 的源码不需要引用 DataFlow type。仓库级 `tests/fixtures`、examples 和 benchmarks 不得成为隐式 production runtime dependency。新增 module、plugin、repository 或第三方 dependency需要先核对架构、供应链和 consumer graph。

## 2. 命令层次

| 变更范围 | 最低验证 |
|---|---|
| Markdown-only | `./scripts/check-docs.sh`、`git diff --check`、链接/Owner 自审 |
| 单模块内部实现 | 对应 Maven tests + 直接 Gate + diff check |
| annotation/schema/generated/public API | compile/golden/external consumer + public API Gate |
| runtime storage/lifecycle/protocol | runtime + generated consumer + invariant + relevant benchmark smoke |
| 日常跨模块反馈 | `./scripts/check.sh fast` + 受影响直接 Gate |
| 跨模块、PR候选或专题收口 | 一次默认的完整`./scripts/check.sh` |
| package/release candidate | package/security/reproducibility/external consumer 及获批 release Gate |

执行者必须先使用与surface成比例的窄验证；Full只在候选收口时运行一次，但其
覆盖范围不能因运行频率下降而缩小。

## 3. 全局检查

[`scripts/check.sh`](../../scripts/check.sh) 是单一综合验证编排入口：

- `./scripts/check.sh fast`：exact toolchain、文档/范围、performance baseline
  架构、reactor verify和diff，用于日常反馈；
- `./scripts/check.sh`或`./scripts/check.sh full`：在Fast基础上覆盖public API、
  compiler/codegen、runtime/DataFlow capability、external consumer、三个
  reference application、benchmark smoke和Access/DataFlow component baseline；
- package/security、application Full和runtime-scale qualification仍由现有独立
  手动入口拥有，不成为Full的隐式工作。

Full先串行形成reactor与共享external artifact事实，再把只读或独立工作目录/
repository的功能Gate按组并行。默认最大并行度是4，实际值为
`min(SOMA_CHECK_JOBS, availableProcessors)`；performance、package、security、
first/repeat oracle内部顺序和qualification不得并行。

编排器为每个阶段输出`start/pass/fail/duration`和总耗时。准备重复高成本动作前，
必须能够说明输入、假设或验证目标发生的变化和预期新evidence；相同状态无新增
evidence时停止，不使用固定间隔sleep或盲目轮询。初始反馈预算为Fast warm local
不超过60秒、Full local不超过5分钟、Full CI不超过10分钟；超出预算触发归因，
不能自动循环重跑或放宽Gate。

GitHub Actions 与 Codex Cloud 的 Linux 环境、exact toolchain 和 setup 入口见
[GitHub 私有仓库与 Codex Cloud 开发](github-and-cloud-development.md)。

任何脚本拆分、共享准备或并行加速都必须保持fail-closed：跳过、找不到工具、
prepared artifact缺失、artifact schema错误和prerequisite不满足不得被报告为
passed。并行组任一任务失败后不再启动后续batch，也不自动重试。
effective POM和dependency topology只校验声明及解析结果，使用标准Maven本地缓存；
独占repository只保留给确实承担published-shape隔离或repeat-build oracle的证据。

## 4. Maven lifecycle 与 local repository

- 日常构建和Full默认使用Maven管理的用户级local repository；远端dependency、
  plugin和wrapper由其缓存，reactor artifact通过一次`install`供独立consumer
  解析；
- 不复制或手工拼装Maven repository内部layout。并行Maven进程共享同一repository
  时使用Resolver file lock与GAV name mapper，且只并行独立consumer；
- 同一check run中reactor `verify`、external artifact `install`和benchmark
  `test-compile`各准备一次，后续Gate验证prepared class/artifact存在后复用；
- `clean`只在clean-build、生成确定性、package shape或repeat-build oracle确实
  需要时使用，不作为每个脚本的习惯性前缀；
- package reproducibility、security evidence和Cloud隔离准备可以使用专用
  repository，但必须说明其独立oracle；它们不成为日常Fast/Full缓存策略。

## 5. Reproducibility 与 repository hygiene

- dependency/plugin/wrapper version和repository来源必须可审计；
- generated source、schema artifact 和 package不能包含 timestamp、local path或随机顺序；
- source/binary/javadoc/checksum在同一 candidate 上生成，dirty worktree只允许作为明确标记的本机诊断；
- target、本机 benchmark artifact、credential和IDE私有配置不进入版本库事实；
- CI 或本机脚本不得用不同 classpath、test-only bypass或网络偶然命中替代普通 consumer path。

## 6. Repository surface

- 新 surface 必须对应新的产品语义、失败域、consumer 或不可替代 evidence；仅为
  future extensibility、test convenience 或一次迁移不得形成长期 artifact；
- capability contract 可以共享 compile/setup/helper，但不能合并不同
  invariant/oracle；同一编排不得复制成多个 forwarding wrapper；
- public/generated protocol 先按 generator binding 和 external consumer 审查，
  不能用 application source 的直接引用数判死；
- replacement 在同一变更中删除 predecessor、旧入口和 migration-only checker；
- current Map/Report 只保留当前事实，历史 checkpoint 和已迁移 canonical path
  由 Git 保存；
- 大文件、单实现、单调用者、LOC 和文件数只作为调查信号，不是拆分或删除结论；
  应优先保持同层次、按执行顺序展开的核心叙事；
- 跨模块或治理专题 closeout 必须报告 surface delta、保留理由、仍存在的
  Conformance，以及 `UNKNOWN`/parallel Owner/active Temporary 是否归零。

这些规则控制长期维护成本，但不设置会诱导巨型类、压缩可读性或删除关键证据的
硬数量配额。

## 7. 记录

可复现验证至少记录：

- commit 和 worktree 状态；
- 完整命令及关键选项；
- JDK vendor/version/build；
- Maven/version；
- OS、architecture；
- 开始/结束时间或 duration；
- exit status、artifact path 和 checksum（如适用）。

临时 `target/` artifact 可以作为本机 evidence input，但不是版本库中的长期事实源；长期结论进入 Report。
