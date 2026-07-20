# 当前 release readiness 摘要

类型：Report / Release 状态

状态：候选输出

Owner：SOMA Java release 状态

受众：项目 Owner 与 release reviewer

事实范围：指定基线的功能 Gate、G6 差距和允许/禁止声明

适用版本：commit `b991f4c`，artifact version `0.1.0-SNAPSHOT`

输入事实源：当前 G0–G6 reports

审查日期：2026-07-20

审查环境：本地 macOS aarch64、Azul Zulu OpenJDK `1.8.0_492-b09`

审查方法：核对 current report index、G0–G6 evidence与完整 `./scripts/check.sh`

最后审查日期：2026-07-20

## 1. 当前能力

- Java-only V1 功能范围与 G0–G5：passed；
- annotation/compiler/generated API/runtime/child/materialization/examples/benchmark 等 22 项功能/evidence capability：evidenced；
- packed exact V3 cutover：passed；
- 本地 package、reproducibility、SBOM、安全与两个 JDK 8 vendor diagnostic：已有通过记录。

## 2. 当前差距

G6 仍为 `blocked`。真实 SCM/project/issue URL、namespace ownership、maintainer/support/private-security contact、最终授权、clean public provenance、signing/OIDC、publishing account/endpoint 和已批准支持矩阵尚未完整形成。

## 3. 允许与禁止声明

当前可以陈述“功能 RC 边界和 G0–G5 已通过相应本地 evidence”。当前不能声明：public RC、正式 release、production ready、Maven Central ready、已签名 provenance、正式支持矩阵或无未知漏洞。

本次 `docs-temp/` 建设不处理发布前置条件，也不改变 G6 状态。直接状态入口：[当前报告索引](../../../reports/README.md)。
