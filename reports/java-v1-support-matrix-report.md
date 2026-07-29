# Java-only SOMA V1 support matrix report

类型：Report / Support Matrix

状态：`1.0.0` final candidate qualification blocked

Owner：SOMA Java G6 support matrix

受众：SOMA maintainer、private repository consumer 与 platform support reviewer

适用版本：`1.0.0`

输入事实源：exact Corretto JDK 8 本机验证、private GitHub Actions、当前 Gate
与 immutable candidate evidence

事实范围：当前 JDK authority、selected private-source build/contract 支持，以及
明确记录环境中的性能/规模 evidence

非事实范围：public/Maven release、production SLA、未列平台与跨环境性能外推

最后审查日期：2026-07-29

Gate：G6 selected `private-github-source`

## 1. JDK authority

唯一 compiler 与 validation authority 是 Amazon Corretto full JDK 8：

- Corretto 8.502.07.1；
- OpenJDK runtime `1.8.0_502-b07`，VM `25.502-b07`；
- full `javac 1.8.0_502`；
- Maven Wrapper / Apache Maven 3.9.16。

Root Maven Enforcer验证 Java 8 与 vendor；`scripts/lib/supported-jdk.sh`进一步验证
java/runtime/javac/javap 精确版本。Zulu 和其他 distribution 均不属于当前支持
矩阵；历史 Zulu 结果只解释其原 candidate，不能关闭当前 Gate。

## 2. Selected matrix

当前表只列出 selected V1 profile 需要验真的组合。`previous-candidate evidence`
可以证明验证通道存在，但在 final immutable candidate 重放前不能写成 V1 passed。

| OS / architecture | V1 build 与 contract | V1 性能/规模 | 当前证据 |
|---|---|---|---|
| macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | blocked pending final Full | blocked pending final DataFlow/runtime-scale qualification | 旧 Corretto artifact `runtime-scale-qualification-20260729-d90e8499d51f`及 component/application baseline 仅为 previous-candidate diagnostic |
| Ubuntu 24.04, Linux x86_64/amd64 | blocked pending final private CI Full | 不选择 Linux 性能/规模 claim | 旧 clean commit `844d74d` 的 private CI Full run `30440373950`已通过；final candidate 尚未重放 |

Codex Cloud development readiness 不属于 selected release support matrix；Windows、
其他 OS/JDK/architecture 也未被选择。

## 3. 支持含义

最终将 macOS 或 Ubuntu 的 build/contract 标记为 `passed`，只表示在表中精确组合、
同一 V1 immutable candidate 上已经：

- 使用 Maven Wrapper 构建全部 production module；
- 运行 Corretto 8 javac plugin/processor并生成 Java 8 classfile；
- 执行 public/generated contract、external Maven consumer、runtime/DataFlow、
  component 与三个 reference application Gate。

只有 macOS 行在同一 candidate 完成规定的 DataFlow/runtime-scale qualification
后，才拥有表中明确列出的性能/规模 claim。Linux 行不需要、也不得用 build/contract
结果伪装成 performance evidence。

这些结论不表示 artifact 已公开发布，不提供 production SLA，也不承诺
10M/100M、任意 schema/String profile 或未列环境。

## 4. 未列环境

Windows、其他 macOS/Linux 版本、其他 architecture、其他 Corretto update、其他
JDK vendor、JDK 9+、ECJ、IDE 内置 compiler 和非 Maven build 均为
`unsupported/untested`。扩大矩阵必须先选择真实目标，再在同一 immutable
candidate 上执行适用 Gate；不能用理论兼容、旧 vendor 结果或 `--release 8`
替代 Corretto 8 javac authority。

## 5. G6 sign-off 条件

本矩阵只有同时满足以下条件才可签署：

1. macOS/aarch64 final clean commit 的 canonical Full、DataFlow 3-fork 与
   required runtime-scale qualification 通过；
2. 同一 commit 的 package/reproducibility、security/provenance evidence 可校验；
3. Ubuntu 24.04/x64 private CI Full 在同一 commit 通过并保留 run identity；
4. evidence 中 commit、version、dirty state、JDK/OS/architecture 与 checksum
   一致；
5. Owner 明确 sign-off，且报告不把 private-source readiness 外推为
   public/Maven/production readiness。

在这些条件关闭前，本报告保持 `blocked`。Small/Medium、单/双 1M、String、
Metadata、parallel、Result Delivery 与三个 Example 的目标不缩减；10M/100M
research/stress 仍由预注册、高内存、人工监管入口拥有，但不是本轮 release
blocker。
