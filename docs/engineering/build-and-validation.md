# 构建与验证

类型：Engineering

状态：正式

Owner：SOMA Java build/validation 过程

事实范围：受支持的本机构建基线、验证层次、命令与环境记录要求

非事实范围：产品功能语义、正式支持矩阵和某次 Gate 结果

最后审查日期：2026-07-24

## 1. 基线

- 使用项目 Maven Wrapper；
- V1 compiler/runtime validation 使用 Azul Zulu full JDK 8 javac/runtime；
- 根 Maven reactor 必须在 Java 8 source/target 下构建；
- Corretto 和其他 JDK distribution 不属于当前验真或目标支持范围，不要求多 vendor 重放；
- Zulu 本机通过只说明实际记录的 version/build、OS 和 architecture，不自动外推到其他 Zulu update 或平台；
- 不用新 JDK 的 `--release 8` 替代 compiler integration evidence。

Canonical build 必须从根 Maven Wrapper进入同一 reactor graph。Production consumer 的运行边界是 annotations + runtime-core，processor只进入编译/build path；testkit、examples 和 benchmarks 不得成为隐式 production runtime dependency。新增 module、plugin、repository 或第三方 dependency需要先核对架构、供应链和 consumer graph。

## 2. 命令层次

| 变更范围 | 最低验证 |
|---|---|
| Markdown-only | `./scripts/check-docs.sh`、`git diff --check`、链接/Owner 自审 |
| 单模块内部实现 | 对应 Maven tests + 直接 Gate + diff check |
| annotation/schema/generated/public API | compile/golden/external consumer + public API Gate |
| runtime storage/lifecycle/protocol | runtime + generated consumer + invariant + relevant benchmark smoke |
| 跨模块或专题收口 | `./scripts/check.sh` |
| package/release candidate | package/security/reproducibility/external consumer 及获批 release Gate |

执行者可以先运行窄验证快速反馈，但收口验证必须覆盖实际 surface。

## 3. 全局检查

[`scripts/check.sh`](../../scripts/check.sh) 是当前代码库的综合验证入口。它串联
文档、Maven、public API、compiler/codegen、runtime/keyspace/access/child/
breadth、external consumer、reference applications、smoke，以及三层性能基线
架构和 component/application baseline Gate。

任何脚本拆分或加速都必须保持 fail-closed：跳过、找不到工具、artifact schema 错误和 prerequisite 不满足不得被报告为 passed。

## 4. Reproducibility 与 repository hygiene

- dependency/plugin/wrapper version和repository来源必须可审计；
- generated source、schema artifact 和 package不能包含 timestamp、local path或随机顺序；
- source/binary/javadoc/checksum在同一 candidate 上生成，dirty worktree只允许作为明确标记的本机诊断；
- target、本机 benchmark artifact、credential和IDE私有配置不进入版本库事实；
- CI 或本机脚本不得用不同 classpath、test-only bypass或网络偶然命中替代普通 consumer path。

## 5. 记录

可复现验证至少记录：

- commit 和 worktree 状态；
- 完整命令及关键选项；
- JDK vendor/version/build；
- Maven/version；
- OS、architecture；
- 开始/结束时间或 duration；
- exit status、artifact path 和 checksum（如适用）。

临时 `target/` artifact 可以作为本机 evidence input，但不是版本库中的长期事实源；长期结论进入 Report。
