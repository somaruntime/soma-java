# Java-only SOMA V1 support matrix report

类型：Report / Support Matrix

状态：上一candidate matrix retained；successor待同SHA重验

Owner：SOMA Java G6 support matrix

受众：SOMA maintainer、private repository consumer 与 platform support reviewer

适用版本：`1.0.0`

输入事实源：exact Corretto JDK 8 本机验证、private GitHub Actions、当前 Gate
与同SHA immutable candidate evidence

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
java/runtime/javac/javap 精确版本。Zulu 和其他 distribution 不属于当前支持矩阵；
历史结果不构成当前支持声明。

## 2. Selected matrix

| OS / architecture | V1 build 与 contract | V1 性能/规模 | 当前证据 |
|---|---|---|---|
| macOS 26.5.2 / Darwin 25.5.0, arm64/aarch64 | predecessor passed；successor pending | successor DataFlow v5 passed；runtime-scale pending | DataFlow v5已在clean successor executable commit固定3-fork通过且threshold不放宽；上一candidate的canonical Full、runtime-scale `runtime-scale-qualification-bd25e1194931-5669bf68ddf5` 8/8 required与本地package/security可追溯，最终successor仍待其余重验 |
| Ubuntu 24.04, Linux x86_64/amd64 | predecessor passed；successor pending | not-selected | 上一signed-off commit的private CI Full与manual package/security/provenance通过；当前successor必须产生新的retained bundle |

Codex Cloud development readiness不属于selected release support matrix；Windows、
其他OS/JDK/architecture也未被选择。

## 3. 支持含义

macOS或Ubuntu的predecessor `passed`只表示在表中精确组合和上一V1 candidate
closure上已经：

- 使用 Maven Wrapper 构建全部 production module；
- 运行 Corretto 8 javac plugin/processor并生成 Java 8 classfile；
- 执行 public/generated contract、external Maven consumer、runtime/DataFlow、
  component与三个reference application Gate。

只有macOS行拥有表中明确列出的性能/规模claim，因为DataFlow与runtime-scale
qualification只在该环境形成。Linux行不得用build/contract结果伪装成performance
evidence。

这些结论不表示artifact已公开发布，不提供production SLA，也不承诺10M/100M、
任意schema/String profile或未列环境。

## 4. 未列环境

Windows、其他macOS/Linux版本、其他architecture、其他Corretto update、其他
JDK vendor、JDK 9+、ECJ、IDE内置compiler和非Maven build均为
`unsupported/untested`。扩大矩阵必须先选择真实目标，再在同一immutable
candidate上执行适用Gate；不能用理论兼容、旧vendor结果或`--release 8`替代
Corretto 8 javac authority。

## 5. G6 sign-off

上一candidate矩阵的签署依据为：

1. macOS/aarch64 canonical Full、DataFlow 3-fork与required runtime-scale通过；
2. package/reproducibility、security/provenance evidence可校验；
3. Ubuntu 24.04/x64 private CI Full与manual qualification在signed-off commit通过；
4. retained evidence中的commit、version、dirty state、JDK、OS/architecture和
   checksum一致；
5. Product Owner于2026-07-29明确给出以上述最终同SHA success为生效条件的
   sign-off。

当前successor只有在同一新clean immutable SHA上重放上述适用evidence并通过，
条件式sign-off才生效；在此之前本矩阵不签署successor。

Private-source readiness始终不外推为public/Maven/production readiness。

Small/Medium、单/双1M、String、Metadata、parallel、Result Delivery与三个
Example的目标未缩减；10M/100M research/stress仍由预注册、高内存、人工监管入口
拥有，但不是V1 release blocker。
