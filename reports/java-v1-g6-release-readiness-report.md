# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：selected `private-github-source` conditional sign-off；bundle resolves result

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、private repository consumer与release profile reviewer

适用版本：`1.0.0`

输入事实源：release governance、private SCM、CI/workflow、package/security
scripts、current support matrix、retained qualification artifact与AI consumer Skill

事实范围：selected private-source G6、适用evidence、Owner sign-off与claim boundary

非事实范围：public GitHub、Maven Central、production SLA或公开性能声明

最后审查日期：2026-07-30

Gate：G6 selected release profile

## 1. 结论

`private-github-source`仍是当前唯一selected release profile。上一精确candidate
的`1.0.0`坐标、identity、license/brand、private SCM/support、exact Corretto 8
authority、macOS qualification、Ubuntu同SHA Full、可复现package、
security/provenance、support matrix和Owner sign-off evidence仍可追溯。当前
successor使用条件式G6：同一clean SHA的全部required evidence通过即`passed`，
任一缺失或失败即`blocked`；Report不复制会漂移的workflow状态。

冻结前审计发现的integral overflow policy已由Product Owner裁决为fail-closed
checked semantics，并完成Design、实现、protocol与contract replacement closure。
随后三个reference application的profiling/规模专题修复了generated update scratch
allocation与Industrial重复refresh读取，保留了RTD stable-order和Industrial
frontier scaling的声明边界；successor还包含compiler、DataFlow与workflow修复。
新的clean immutable SHA必须重放适用Full、G5、package/security、support matrix和
manual qualification，条件式Owner sign-off才会生效。

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
| macOS qualification | same-SHA evidence-resolved | DataFlow v5与九个application profile已有clean多fork证据；最终SHA的canonical Full、application与runtime-scale 8条required lane必须通过，性能/规模claim保持environment/profile bounded |
| Ubuntu same-SHA qualification | same-SHA evidence-resolved | 最终SHA的private CI Full与manual qualification必须通过；Linux只形成build/contract claim |
| package / security / provenance | same-SHA evidence-resolved | 最终SHA必须形成17件release-shaped artifact双构建、classfile 52、License/NOTICE、SBOM、OSV、license与runtime dependency evidence |
| AI consumer Skill | passed with V1 waiver | canonical instruction-only Skill、结构/drift Gate、Codex positive consumer通过；剩余behavior/multi-host evidence被Owner waive，不声明multi-tool support |

Organization plan不支持private repository branch protection/ruleset。当前控制为
private access、CODEOWNERS、长期分支约束、SHA-pinned workflow、clean candidate
admission和人工sign-off；这是已记录的残余平台风险，不伪造平台能力。

## 3. Profile 状态

| Profile / claim | 状态 | 边界 |
|---|---|---|
| private GitHub source | conditional sign-off | 同SHA Full、G5、package/security/provenance、matrix、private CI、manual qualification与下载bundle checksum全部成功即passed，否则blocked |
| local macOS development | same-SHA evidence-resolved | arithmetic/DataFlow contract、DataFlow v5、三example profiling/优化与九profile clean 3-fork已闭合；最终clean SHA artifact决定Full、runtime-scale、application与package/security状态 |
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
checksum，G6 sign-off才生效。Retained bundle中的`candidate.properties`、
package/security summary与`evidence-checksums.sha256`共同解析结果；任一缺失、
commit/version/dirty不一致或Gate失败都保持`blocked`。

Push与workflow运行已获本轮授权。Tag、GitHub Release、repository visibility、
signing和publishing仍需另行明确授权；G6 passed不执行或暗示这些动作，也不声明
public、Maven或production readiness。

## 6. Scope non-regression

- Java 8、public/generated signature和production dependency未改变；integral
  observable semantics已按Owner授权改为fail closed，transformation/kernel
  protocol分别升级为v5/v6；
- generated update scratch改为Plan/ledger约束的几何增长并在preferred capacity
  不可接纳时退回exact required；Industrial只在现有type内提升同group不变读取；
  没有新增public surface或并行事实Owner；
- Grassing三个baseline原位replacement为v6/v5/v5，allocation envelope收紧且
  timing Gate未放宽；九个application baseline总数仍为9；
- public/Maven保持`not-selected`，10M/100M保持non-blocking research；
- AI Skill不进入Maven JAR，也不成为Design或API Owner；
- public Java type、module与runtime dependency增量均为零；package-private
  production type增加`IntegralArithmetic`与嵌套`ExactSum`，由同一DataFlow
  arithmetic Owner承载；active Temporary与未裁决产品语义均为零；
- G6不能用上一candidate evidence、明确waiver或条件式文本伪造passed；只接受
  本报告所在同一SHA的retained bundle解析结果。
