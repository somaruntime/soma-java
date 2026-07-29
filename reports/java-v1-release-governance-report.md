# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` source candidate assembled；same-SHA evidence resolves sign-off

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

上一轮已把完整SOMA Java产品推进到V1 `1.0.0`的selected
`private-github-source` sign-off。本轮冻结前产品目标审计发现并关闭了integral
overflow语义偏差，同时验证了compiler、DataFlow与workflow修复，形成新的
source-side candidate。Report不预写尚未执行的动态workflow状态：本报告所在clean
SHA只有在本节规定的同SHA evidence全部通过后才签署，否则保持blocked。治理仍不
把release解释为public repository、Maven Central或production readiness，也不
通过缩小Blueprint、Design或Gate关闭差距。

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

## 2. 产品目标核对

本轮先核对产品目标，再判断release mechanics。当前链路如下：

| Blueprint目标 | Design / executable surface | 当前evidence | 当前结论 |
|---|---|---|---|
| Java 8 Schema-Defined、Compiler-Specialized | annotation、javac 8 plugin/processor、schema-specific generated facade | compiler/default/Unicode contract覆盖本轮literal escaping修复；最终同SHA Full/external consumer是生效条件 | 目标一致；由同SHA evidence解析 |
| State / Owner + Capability + Plan / Lifecycle | SomaGroup/root/owned-child、Metadata/Plan、Table/DataFlow lifecycle | 未发现并行Owner或live object graph回归；最终同SHA lifecycle/consumer Gate是生效条件 | 目标一致；由同SHA evidence解析 |
| packed columnar runtime-state data plane | primitive/String columns、exact access、Candidate、无DTO/Collection hot storage | public/generated surface与runtime dependency无变化；代码审查未发现reflection/metadata interpreter或隐藏I/O进入hot path | 一致 |
| typed local Transformation / DataFlow | expression、aggregation、Group/Join/Window/Expanded、bounded scheduler | integral arithmetic已按Owner裁决改为fail-closed checked semantics；raw/closed、scalar/parallel、prefix、Group、Window、Expanded contract与clean 3-fork component通过 | 目标一致；最终G5由同SHA evidence解析 |
| predictable resource / failure / observation | ExecutionBudget、structured failure、ledger、detached/callback result | window count与detached expansion在分配/枚举前稳定拒绝；最终同SHA lifecycle/failure Gate是生效条件 | 目标一致；由同SHA evidence解析 |
| 普通Java 8 consumer可用 | 四个同版本artifact、processor build-only、generated API、三个reference application | public/generated与独立consumer入口已覆盖；最终同SHA consumer/application Gate是生效条件 | 目标一致；由同SHA evidence解析 |
| 性能与规模不过度外推 | Small/Medium、单/双1M、String、Expansion、Delivery、Soak；10M/100M research | DataFlow v5在clean commit固定3-fork通过且threshold不放宽；最终application/runtime-scale artifact决定完整G5 | 目标一致；由同SHA evidence解析 |
| selected private-source release诚实可追溯 | clean SHA、Full、package/security/provenance、matrix、conditional sign-off | 最终private CI、manual qualification与下载bundle checksum是唯一动态状态Owner | conditional sign-off |

产品语义偏差、Temporary与未裁决`UNKNOWN`已经归零，source-side候选与产品目标
一致。是否达到“可冻结的V1产品”不由此静态文本预判，而由同一immutable candidate
的真实consumer、全部适用Gate、qualification与bundle checksum按下节规则解析。

## 3. 候选与 Gate

Successor DataFlow v5固定3-fork已在clean executable commit `a2914918…`
通过，workload与threshold均未放宽。Runtime-scale 8条required lane仍只有上一
candidate的clean commit `bd25e119…`和精确source tree `5669bf68ddf5…`
retained evidence，不覆盖当前successor。新的最终动态身份必须由successor
qualification bundle中的`candidate.properties`和package/security provenance
唯一拥有。

| Gate | 状态 | 直接依据与边界 |
|---|---|---|
| G0 | passed | Java-only scope、正式Owner、claim boundary与核心抽象叙事规则稳定 |
| G1–G4 | same-SHA evidence-resolved | 本报告所在clean SHA的canonical Full与private CI Full都必须通过；任一缺失或失败即blocked |
| G5 | same-SHA evidence-resolved | DataFlow v5 clean calibration已闭合；最终SHA的三个application与8条required runtime-scale lane必须全部passed |
| G6 | conditional Owner sign-off | 同SHA package/reproducibility、security/provenance、sealed bundle、support matrix、private CI、manual qualification与下载checksum全部成功即passed，否则blocked |

旧vendor、旧candidate或单机结果只解释其原环境，不替代当前evidence。

## 4. 上轮尾项闭环

| 尾项 | 最终处置 |
|---|---|
| canonical Full | closed：clean candidate的唯一canonical Full exit 0；最终Ubuntu同SHA Full再次通过 |
| exact qualification provenance | closed：runtime-scale绑定clean commit、唯一source manifest与8条required record |
| source identity 边界 | closed：manifest精确包含production/build/runner closure，排除tests、Examples与无关baseline，并纳入实际shell library |
| DataFlow baseline provenance | closed for successor：v5绑定clean `a2914918…`固定3-fork，workload/threshold不变；v4由Git保存且current checkout只保留v5 |
| 抽象叙事闭环 | closed：Design Index制度化Why/Owns/Not/Relationships/Lowering/Lifecycle/Resource/Failure/Evidence/Evolution |
| release evidence 留存 | closed：同SHA identity、package/security、checksums与90天artifact由manual workflow保留 |
| NOTICE drift | closed：品牌边界与security checksum一致，未降低fail-closed强度 |
| AI consumer Skill | closed with waiver：canonical Skill、结构/drift、Codex positive consumer通过；剩余behavior/multi-host evidence由Owner对V1 waive |
| Temporary | closed：稳定事实进入正式Owner，专题目录删除且不归档 |

Runtime-scale required qualification只运行Small、Medium、单1M、双1M、String、
Expansion、Delivery与Soak；10M/100M research不进入V1 blocker，也没有被删除。

## 5. G6 package、security 与 provenance

上一signed-off commit的manual workflow验证：

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

## 6. AI consumer Skill

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

## 7. Scope non-regression 与 surface delta

当前successor相对上一signed-off commit的已验证变更为：

- public Java type delta：0；package-private production type delta：+2
  （`IntegralArithmetic`与其invocation-local `ExactSum`）；
- module delta：0；production artifact仍为四个；
- runtime dependency delta：0；
- processor内部统一generated Java literal escaping，并扩充String default fixture；
- DataFlow修复time-window count overflow，并使Expanded scalar cardinality保持
  `long`、detached terminal在枚举前拒绝不可表示array cardinality；
- integral raw/closed expression、scalar/parallel reduction、prefix、Group、
  Window与Expanded统一为fail-closed checked semantics；transformation/kernel
  protocol分别升级为v5/v6，并增加一个canonical arithmetic contract；
- workflow checkout关闭credential persistence；
- DataFlow benchmark workload/threshold、schema identity、generated/runtime
  protocol、public signature和artifact coordinate未改变；baseline从v4原位替换为
  绑定clean successor的v5，只更新versioned authoring identity。
- 九个reference-application baseline完成版本化replacement；业务checksum保持
  不变。八个profile不改threshold；RTD long-run基于10-fork calibration与3-fork
  final confirmation，只把可重复的单次1 ms young-GC envelope按既有公式校正为
  count/pause 2，allocation、timing与full-GC Gate不变；

没有temporary public/generated API、parallel Design Owner、test-only bypass、
新第三方production dependency、migration artifact、active Temporary或未裁决
产品语义。

## 8. 最终授权边界

Product Owner于2026-07-29确认条件式G6 sign-off：本报告所在successor最终commit
只有在产品语义偏差关闭、Temporary退役、private CI、manual qualification和下载
bundle checksum全部成功后才生效为`passed`。Source checkout故意不复制会漂移的
run状态；retained `candidate.properties`、package/security provenance与
`evidence-checksums.sha256`共同解析该条件，任一缺失或失败即`blocked`。

Tag、GitHub Release、repository visibility、signing、publishing和公开发布仍未
授权；本轮不执行这些动作。Private-source G6 passed也不构成production SLA、
public availability或真实业务项目适配结论。
