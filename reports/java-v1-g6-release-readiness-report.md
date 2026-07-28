# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：selected `private-github-source` profile passed；public/Maven not selected

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、private repository consumer 与 release profile reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：release governance、private SCM、GitHub CI、package/security
qualification与support matrix

事实范围：selected private-source G6、允许声明和禁止声明

非事实范围：public GitHub、Maven Central、production SLA或公开性能声明

最后审查日期：2026-07-28

Gate：G6 selected release profile

## 1. 结论

用户已明确选择`private-github-source`作为当前唯一release profile。该profile的
G6通过：SOMA Java由ArthurFeng拥有并以Apache-2.0授权，源码和完整Git历史位于
private `somaruntime/soma-java`；真实SCM、maintainer、support、security、
CODEOWNERS、clean provenance、Linux/macOS Zulu 8 support matrix、external
consumer、package/reproducibility、SBOM/vulnerability/license和GitHub CI证据均
已闭合。

这项结论允许表述“SOMA Java V1 private GitHub source profile ready for controlled
development”。它不允许表述public release ready、Maven Central ready、
production ready或跨环境性能ready。版本仍是`0.2.0-SNAPSHOT`，没有创建tag，
没有公开仓库，也没有上传Maven artifact。

Codex Cloud实验没有完成真实fresh-container full check，当前由maintainer停止，
状态为`not-selected / not-ready`。它不影响selected private-source G6，也不能
成为Cloud readiness声明。

## 2. Identity、SCM 与治理事实

| 关注点 | 结论 | 真实事实 |
|---|---|---|
| copyright / license | passed | copyright owner为ArthurFeng；`NOTICE`使用ArthurFeng；License为Apache-2.0 |
| product / namespace | passed | 品牌SOMA；Organization `somaruntime`；Maven groupId和Java root均为`io.github.somaruntime.soma`；artifact保持`soma-*` |
| SCM | passed | private `https://github.com/somaruntime/soma-java`；完整历史原样推送；`develop`为default development branch，`main`为stable baseline |
| Git identity | passed | 新提交使用ArthurFeng与GitHub-confirmed noreply email；GitHub提交归属已实际确认 |
| maintainer / support | passed | ArthurFeng / GitHub `@283586450`；普通问题进入private repository Issues |
| security | passed | private GitHub Security Advisory为首选入口；不可用时通过已记录owner建立私下联系 |
| ownership | passed | `.github/CODEOWNERS`为`* @283586450`；Actions workflow使用只读权限和immutable action SHA |

Organization当前plan不支持private repository branch protection/ruleset，GitHub
API真实返回upgrade限制。本profile不伪造已配置状态；现有控制是private visibility、
单一owner、default/stable branch分离、CODEOWNERS、SHA-pinned Actions和clean
qualification。若未来扩大协作者或公开仓库，应重新裁决ruleset。

## 3. Package、security 与 executable evidence

Manual GitHub `Release qualification`在clean immutable commit上实际执行：

1. exact Azul Zulu 8.94.0.17 / `1.8.0_492-b09`、`javac 1.8.0_492`、
   Maven 3.9.16与Ubuntu 24.04 x64；
2. 完整`./scripts/check.sh`，包含public/generated contract、external Maven
   consumer、三个reference application和普通benchmark Gate；
3. 两个隔离Maven repository的release-shaped package与byte-for-byte
   reproducibility；
4. pinned OSV-Scanner、SBOM、known-vulnerability与declared-license检查；
5. 最终`git diff --exit-code`。

校准run `30363514814`在
`b2cdf2ff586dd1f6e3091eb8db0d5f08902d816f`全部通过：
`project-check: ok`、`external-consumer-check: ok`、`package-smoke: ok`、
`security-release-scan: ok`和clean workspace。最终closeout commit仍由同一
workflow与普通CI再次绑定；GitHub Actions run是commit-bound外部evidence Owner。

Package evidence只证明当前source可以形成确定、可审计的release shape。因为当前
profile不分发binary，signing/OIDC、publishing account、Maven endpoint和公开
artifact URL为`not-applicable / not-selected`，不是虚构passed或waived。

## 4. Support matrix 与性能边界

正式matrix见[Support matrix](java-v1-support-matrix-report.md)：

- macOS 26.5.2 / Darwin 25.5.0 arm64：Zulu 8 build/contract和记录profile性能
  evidence；
- GitHub Actions Ubuntu 24.04 x64：Zulu 8 build/contract、package/security与
  clean workspace evidence；

Linux上的macOS性能baseline为`not-applicable`。Small/Medium、single/double100M、
String、Metadata、两层自适应并行、Result Delivery与三个Example既有目标和
qualification没有降低，但不能由private-source G6外推为任意环境或任意workload。

## 5. Profile 状态

| Profile / claim | 状态 | 边界 |
|---|---|---|
| private GitHub source | passed | 受控源码协作、CI、support/security与selected matrix已形成 |
| Codex Cloud development | not-selected / not-ready | setup与部分离线检查存在；完整fresh-container验收未完成，当前停止追求 |
| public GitHub source | not-selected | repository保持private；未建立public community/release义务 |
| Maven Central / binary publishing | not-selected | 未配置signing、OIDC或publishing endpoint，未上传artifact |
| production readiness / SLA | not-claimed | 需要具体部署、workload与运营证据，不由library G6推断 |

## 6. Scope non-regression 与后续触发条件

- G0–G5和全部既定V1能力保持passed，不因release profile收窄而optional化；
- `CF-006`只对selected private-source profile关闭；public/Maven未被伪装为通过；
- 本次变化是identity cutover、cross-platform completion与release evidence
  completion，没有新增public/schema/runtime语义或第三方production dependency；
- 若重新选择Codex Cloud、public repository、Maven publishing、新Zulu/OS/
  architecture、公开性能claim或production SLA，必须重新进入相应
  Design/Engineering/Gate裁决，不得沿用本报告越界声明。
