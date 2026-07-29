# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：selected `private-github-source` profile blocked for successor candidate

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、private repository consumer与release profile reviewer

适用版本：`1.0.0`

输入事实源：release governance、private SCM、CI/workflow、package/security
scripts、current support matrix、retained qualification artifact与AI consumer Skill

事实范围：selected private-source G6、适用evidence、Owner sign-off与claim boundary

非事实范围：public GitHub、Maven Central、production SLA或公开性能声明

最后审查日期：2026-07-29

Gate：G6 selected release profile

## 1. 结论

`private-github-source`仍是当前唯一selected release profile。上一精确candidate
的`1.0.0`坐标、identity、license/brand、private SCM/support、exact Corretto 8
authority、macOS qualification、Ubuntu同SHA Full、可复现package、
security/provenance、support matrix和Owner sign-off evidence仍可追溯；当前
successor candidate的G6为`blocked`。

冻结前审计发现的integral overflow policy已由Product Owner裁决为fail-closed
checked semantics，并完成Design、实现、protocol与contract replacement closure；
successor还包含compiler、DataFlow与workflow修复。必须以新的clean immutable SHA
重放适用Full、G5、package/security、support matrix和manual qualification，
条件式Owner sign-off才会生效。

上一candidate的精确SHA、workflow run、attempt、ref、version、package/security
provenance与bundle checksum由其同SHA retained qualification artifact唯一记录，
但不构成successor evidence。Report不复制一份会漂移的动态run清单。

AI Skill的Codex positive consumer已经真实compile/run。Product Owner于
2026-07-29明确waive V1的negative/anti-pattern与第二独立宿主行为验证；影响是
不得声明multi-tool support，未来扩大工具支持前必须重新取得真实宿主证据。

## 2. Identity、SCM 与治理事实

| 关注点 | 状态 | 真实事实 |
|---|---|---|
| copyright / license | passed | copyright owner与`NOTICE`为ArthurFeng；Apache-2.0 |
| product / namespace | passed | SOMA；Organization `somaruntime`；Maven/Java root `io.github.somaruntime.soma`；artifact保持`soma-*` |
| SCM | passed | private `somaruntime/soma-java`；`develop`为默认开发分支，`main`为稳定基线 |
| maintainer / support | passed | ArthurFeng / GitHub `@283586450`；普通问题进入private repository Issues |
| security / ownership | passed | private Security Advisory优先；`.github/CODEOWNERS`为`* @283586450`；Actions最小权限与immutable SHA pin |
| JDK authority | passed | Amazon Corretto 8.502.07.1、`1.8.0_502-b07`、`javac 1.8.0_502` |
| macOS qualification | successor component passed；remaining pending | DataFlow v5固定3-fork已在clean successor executable commit通过；canonical Full与runtime-scale 8条required lane仍须绑定最终SHA，性能/规模claim保持environment/profile bounded |
| Ubuntu same-SHA qualification | predecessor passed；successor pending | 上一signed-off commit的private CI Full与manual qualification通过；当前successor须形成新同SHA evidence，Linux只形成build/contract claim |
| package / security / provenance | predecessor passed；successor pending | 上一candidate的17件release-shaped artifact双构建一致、classfile 52、License/NOTICE、SBOM、OSV、license与runtime dependency边界通过；当前successor须重放 |
| AI consumer Skill | passed with V1 waiver | canonical instruction-only Skill、结构/drift Gate、Codex positive consumer通过；剩余behavior/multi-host evidence被Owner waive，不声明multi-tool support |

Organization plan不支持private repository branch protection/ruleset。当前控制为
private access、CODEOWNERS、长期分支约束、SHA-pinned workflow、clean candidate
admission和人工sign-off；这是已记录的残余平台风险，不伪造平台能力。

## 3. Profile 状态

| Profile / claim | 状态 | 边界 |
|---|---|---|
| private GitHub source | blocked for successor candidate | 产品语义已闭合；等待新SHA的同SHA Full、G5、package/security/provenance、matrix与Owner sign-off |
| local macOS development | successor prequalification in progress | 新arithmetic/DataFlow contract、独立consumer预检、Fast与DataFlow v5 clean 3-fork通过；canonical Full、runtime-scale、application与package/security仍待最终SHA |
| public GitHub source | not-selected | repository保持private |
| Maven Central / binary publishing | not-selected | 未配置signing/OIDC/publishing，不分发binary |
| Codex Cloud development | not release-scoped | 不进入当前支持矩阵，也不替代private-source G6 |
| production readiness / SLA | not-claimed | 需要真实项目workload、部署与运营证据 |

Private-source profile仍验证package/security mechanics，因为它们证明source能生成
一致、可审计的release-shaped artifact及dependency/provenance边界；这不等于选择
Maven distribution，也不要求signing或publishing。

## 4. Evidence retention

上一candidate的Manual release workflow曾在同一clean commit上：

1. 固定并验证exact Corretto、Maven、ripgrep和OSV-Scanner；
2. 写入candidate SHA/ref/run/version/profile；
3. 运行Full、两次隔离package build与security scan；
4. 校验package/security summary都是同一SHA、`dirty=false`和同一version；
5. 对完整evidence bundle生成SHA-256；
6. 使用SHA-pinned、Node 24的`actions/upload-artifact`保留90天。

下载后必须以`evidence-checksums.sha256`重新校验bundle。90天artifact不是public
distribution，也不替代未来可能另行授权的tag或GitHub Release。

## 5. Owner sign-off 与授权边界

Product Owner于2026-07-29确认条件式G6 sign-off：successor必须包含最终
Owner/Conformance/Report、AI waiver、当前全部已验证修复和Temporary退役，并在
同一clean immutable commit上完成private CI、manual qualification与下载bundle
checksum，G6 sign-off才生效。当前尚未满足这些条件。

Push与workflow运行已获本轮授权。Tag、GitHub Release、repository visibility、
signing和publishing仍需另行明确授权；G6 passed不执行或暗示这些动作，也不声明
public、Maven或production readiness。

## 6. Scope non-regression

- Java 8、public/generated signature和production dependency未改变；integral
  observable semantics已按Owner授权改为fail closed，transformation/kernel
  protocol分别升级为v5/v6；
- public/Maven保持`not-selected`，10M/100M保持non-blocking research；
- AI Skill不进入Maven JAR，也不成为Design或API Owner；
- public Java type、module与runtime dependency增量均为零；package-private
  production type增加`IntegralArithmetic`与嵌套`ExactSum`，由同一DataFlow
  arithmetic Owner承载；active Temporary与未裁决产品语义均为零；
- G6保持blocked，不能用上一candidate evidence、明确waiver或条件式sign-off
  降低产品目标。
