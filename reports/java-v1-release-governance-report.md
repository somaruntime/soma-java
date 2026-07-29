# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` G0–G6 passed for selected private-source

Owner：SOMA Java V1 release governance

受众：SOMA maintainer、release owner、private repository consumer

适用版本：`1.0.0`

输入事实源：正式 Blueprint/Design/Engineering、当前 POM/source/generated
surface、Conformance、Gate scripts、private SCM/CI与retained release evidence

事实范围：当前候选身份、G0–G6、上轮尾项、AI consumer Skill、scope
non-regression与selected profile claim

非事实范围：tag/release授权、public GitHub、Maven Central、production SLA或
未列环境支持

最后审查日期：2026-07-29

## 1. 治理目标与 release identity

本轮已把完整SOMA Java产品推进到V1 `1.0.0`的selected
`private-github-source` sign-off，没有把release解释为public repository、
Maven Central或production readiness，也没有通过缩小Blueprint、Design或Gate
关闭差距。

| 事实 | 当前值 |
|---|---|
| 产品 / Owner | SOMA / ArthurFeng |
| SCM | private `somaruntime/soma-java` |
| Maven / Java root | `io.github.somaruntime.soma` |
| release version | `1.0.0` |
| planned immutable tag | `v1.0.0`；尚未创建 |
| JDK authority | Amazon Corretto 8.502.07.1 full JDK 8 |
| selected profile | private GitHub source |
| not-selected | public GitHub、Maven/binary publishing |

Tag、GitHub Release、visibility与publishing仍是外部授权边界。POM中的`v1.0.0`
只定义planned release identity，不证明tag已存在。

## 2. 候选与 Gate

DataFlow固定3-fork在clean commit `733db714…`通过；runtime-scale 8条required
lane在clean commit `bd25e119…`和精确source tree `5669bf68ddf5…`通过。
canonical local Full、local package/security以及最终signed-off commit的Ubuntu
CI与manual qualification共同形成当前evidence closure。最终动态身份由
qualification bundle中的`candidate.properties`和package/security provenance
唯一拥有。

| Gate | 状态 | 直接依据与边界 |
|---|---|---|
| G0 | passed | Java-only scope、正式Owner、claim boundary与核心抽象叙事规则稳定 |
| G1–G4 | passed | clean-candidate canonical Full与最终同SHA Ubuntu Full通过 |
| G5 | passed | differential、component、三个application、DataFlow 3-fork与8-lane required qualification通过 |
| G6 | passed | 同SHA package/reproducibility、security/provenance、sealed evidence、support matrix与条件式Owner sign-off闭合 |

旧vendor、旧candidate或单机结果只解释其原环境，不替代当前evidence。

## 3. 上轮尾项闭环

| 尾项 | 最终处置 |
|---|---|
| canonical Full | closed：clean candidate的唯一canonical Full exit 0；最终Ubuntu同SHA Full再次通过 |
| exact qualification provenance | closed：runtime-scale绑定clean commit、唯一source manifest与8条required record |
| source identity 边界 | closed：manifest精确包含production/build/runner closure，排除tests、Examples与无关baseline，并纳入实际shell library |
| DataFlow baseline provenance | closed：clean commit固定3-fork，workload/threshold不变；checker拒绝working-tree、dirty或relaxed calibration |
| 抽象叙事闭环 | closed：Design Index制度化Why/Owns/Not/Relationships/Lowering/Lifecycle/Resource/Failure/Evidence/Evolution |
| release evidence 留存 | closed：同SHA identity、package/security、checksums与90天artifact由manual workflow保留 |
| NOTICE drift | closed：品牌边界与security checksum一致，未降低fail-closed强度 |
| AI consumer Skill | closed with waiver：canonical Skill、结构/drift、Codex positive consumer通过；剩余behavior/multi-host evidence由Owner对V1 waive |
| Temporary | closed：稳定事实进入正式Owner，专题目录删除且不归档 |

Runtime-scale required qualification只运行Small、Medium、单1M、双1M、String、
Expansion、Delivery与Soak；10M/100M research不进入V1 blocker，也没有被删除。

## 4. G6 package、security 与 provenance

同一signed-off commit的manual workflow验证：

- 17件parent/module POM、binary/source/javadoc artifact；
- Java classfile major 52、License/NOTICE精确；
- 两个隔离build byte-for-byte一致；
- SBOM、OSV known-vulnerability与declared-license诊断；
- production runtime第三方依赖为0；
- package/security summary的commit、`dirty=false`和`artifactVersion=1.0.0`
  与candidate一致；
- 完整bundle生成`evidence-checksums.sha256`并保留90天。

OSV结果只代表执行时已发布的已知advisory，不是public security certification。
Unsigned artifact符合当前private-source profile；Maven/publishing/signing未选择。

## 5. AI consumer Skill

唯一canonical Skill为`.agents/skills/use-soma-java/`，只拥有AI consumer
workflow。Blueprint/Design继续拥有长期语义，POM/generated source/class/golden/
external consumer继续拥有精确surface。

已形成最小portable frontmatter、四份按需reference、安全project-scoped安装提示、
手动fallback、无scripts/allowed-tools的instruction-only bundle、结构/drift Gate
以及Codex positive real-consumer compile/run evidence。

Product Owner于2026-07-29明确决定V1暂不把canonical Skill和测试prompt提交给
第二独立AI宿主，也不执行剩余negative/anti-pattern宿主行为验证。该项状态为
`waived for V1`，不是伪造passed；影响是不得声明multi-tool support。未来选择新的
AI工具support profile时必须重新完成发现、安装、触发和行为验证。

## 6. Scope non-regression 与 surface delta

本轮没有修改production Java、public/generated fixture或protocol identity：

- production/public Java type delta：0；
- module delta：0；production artifact仍为四个；
- runtime dependency delta：0；
- tests/fixtures只同步SOMA consumer version；
- benchmark不改workload/threshold，只修qualification identity；
- scripts/workflow只收口version Owner、evidence retention和clean-candidate admission；
- docs/report只固化V1 identity、AI consumer入口、抽象叙事和current evidence。

没有temporary public/generated API、parallel Design Owner、test-only bypass、
canonical hot-path migration、新第三方production dependency、migration artifact、
未退役Temporary或未裁决产品`UNKNOWN`。

## 7. 最终授权边界

Product Owner于2026-07-29给出条件式G6 sign-off：本报告所在最终commit只有在
private CI、manual qualification和下载bundle checksum全部成功后才生效为
`passed`。Push与workflow运行已获授权。

Tag、GitHub Release、repository visibility、signing、publishing和公开发布仍未
授权；本轮不执行这些动作。Private-source G6 passed也不构成production SLA、
public availability或真实业务项目适配结论。
