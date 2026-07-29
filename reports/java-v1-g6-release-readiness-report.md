# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：selected `private-github-source` profile blocked

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、private repository consumer与release profile reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：release governance、private SCM、CI配置、package/security历史
qualification与current support matrix

事实范围：selected private-source G6、有效事实、缺失evidence和claim boundary

非事实范围：public GitHub、Maven Central、production SLA或公开性能声明

最后审查日期：2026-07-29

Gate：G6 selected release profile

## 1. 结论

`private-github-source`仍是当前唯一selected release profile，但Amazon Corretto 8
取代Zulu 8成为唯一JDK authority后，G6重新成为`blocked`。

下列长期事实仍成立：SOMA Java由ArthurFeng拥有并以Apache-2.0授权；private
repository为`somaruntime/soma-java`且保留完整Git历史；SCM、maintainer、
support、security与CODEOWNERS均有真实Owner。

当前authority已经在implementation commit `dddf62b`的GitHub Actions run
`30410355517`形成Ubuntu x64 exact Corretto 8 Full。下列release evidence仍未在
同一最终candidate形成：

- clean release-shaped package与byte-for-byte reproducibility；
- SBOM、known-vulnerability、declared-license与security qualification；
- 最终clean workspace、artifact checksum与macOS/Linux Corretto matrix sign-off。

因此当前允许继续受控本地与GitHub Linux CI开发，但不允许表述“private GitHub
source profile ready”。旧Zulu qualification结果是历史candidate证据，不能改名
或外推为Corretto release passed。

## 2. Identity、SCM 与治理事实

| 关注点 | 当前状态 | 真实事实 |
|---|---|---|
| copyright / license | passed | copyright owner与`NOTICE`为ArthurFeng；Apache-2.0 |
| product / namespace | passed | SOMA；Organization `somaruntime`；Maven/Java root `io.github.somaruntime.soma`；artifact保持`soma-*` |
| SCM | passed | private `https://github.com/somaruntime/soma-java`；完整历史；`develop`为开发分支，`main`为稳定基线 |
| maintainer / support | passed | ArthurFeng / GitHub `@283586450`；普通问题进入private repository Issues |
| security / ownership | passed | private Security Advisory优先；`.github/CODEOWNERS`为`* @283586450`；Actions最小权限与SHA pin |
| JDK authority | passed local + CI | Amazon Corretto 8.502.07.1、`1.8.0_502-b07`、`javac 1.8.0_502` |
| Linux build/contract | passed | Ubuntu 24.04 x64，commit `dddf62b`，CI run `30410355517`，Full 7分46秒 |
| package/security evidence | blocked | 尚未在同一最终candidate执行manual release qualification |

Organization plan不支持private repository branch protection/ruleset的历史限制仍按
真实事实记录，不伪造已配置状态。若扩大协作者或公开仓库，需要重新裁决。

## 3. 历史evidence与当前适用性

2026-07-28 Zulu manual `Release qualification`曾完成Full、两个隔离Maven
repository的package/reproducibility、security扫描与clean workspace。这证明旧
Zulu candidate在当时profile下成立；JDK authority迁移后，它不再关闭当前G6。

本机Corretto已完成：

- exact toolchain及Zulu negative probe；
- reactor/public/generated/external consumer/runtime/DataFlow contract；
- Access/DataFlow component baseline；
- 三个Example correctness与九profile application performance；
- v2 Small/Medium、单1M、双1M、String、Expansion、Delivery、Soak qualification。

本机证据不能代替clean package/security；Linux build/contract已由上述
commit-bound CI evidence补齐。

## 4. Profile状态

| Profile / claim | 状态 | 边界 |
|---|---|---|
| private GitHub source | blocked | Corretto Linux Full已通过；等待同一最终candidate的manual release qualification与sign-off |
| local macOS development | passed for recorded environment | Corretto build/contract/component/application/runtime-scale；不外推其他环境 |
| Codex Cloud development | candidate / qualification-blocked | setup已收敛；等待fresh-container setup/Fast/Full/clean-worktree有界验收；不是release profile |
| public GitHub source | not-selected | repository保持private |
| Maven Central / binary publishing | not-selected | 未配置signing/OIDC/publishing，未上传artifact |
| production readiness / SLA | not-claimed | 需要具体部署、workload与运营证据 |

## 5. 重新关闭条件

G6 Owner只在同一clean immutable commit上获得下列全部证据后重新标记passed：

1. Ubuntu x64 exact Corretto 8的`./scripts/check.sh full`；
2. release qualification的隔离package/reproducibility和security evidence；
3. `git diff --exit-code`与artifact checksum；
4. Support Matrix、Conformance和本报告同步更新。

当前`dddf62b`只关闭了条件1；条件2–4仍阻塞最终release candidate。

这些条件不得用macOS smoke、旧Zulu run、Cloud部分日志、placeholder或waive替代。
Public/Maven profile仍保持`not-selected`，无需为了关闭private-source G6扩大
发布范围。

## 6. Scope non-regression

- JDK authority迁移不改变public/schema/runtime语义或第三方production依赖；
- G0–G5已通过；`CF-016`由当前Corretto v2 qualification关闭；
- G6 blocked是evidence诚实性，不是降低产品目标；
- 未创建tag、未公开仓库、未上传artifact、未声明production/public readiness。
