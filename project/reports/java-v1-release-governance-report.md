# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` freeze candidate assembled；same-SHA evidence resolves sign-off

Owner：SOMA Java V1 release governance

受众：SOMA maintainer、release owner、private repository consumer

适用版本：`1.0.0`

输入事实源：正式 Blueprint/Design/Process、当前POM/source/generated
surface、Conformance、项目组织治理、Gate scripts、private SCM/CI与retained
release evidence

事实范围：当前候选身份、产品目标核对、G0–G6、上轮尾项、性能/规模专题、
AI consumer Skill、scope non-regression与selected profile claim

非事实范围：tag/release授权、public GitHub、Maven Central、production SLA或
未列环境支持

最后审查日期：2026-07-30

## 1. 治理目标与 release identity

治理目标不是让一组测试显示绿色，而是把完整SOMA Java产品推进到可以冻结并进入
真实项目试用的V1 `1.0.0` private-source candidate，同时不缩小Blueprint、
Design、failure/resource contract或Gate。本轮在上一conditional sign-off基础上
完成三类冻结前补充：

- integral overflow policy按Product Owner裁决统一为fail-closed checked
  semantics，并关闭compiler/DataFlow/workflow相关偏差；
- 三个reference application分别完成profiling与1M/10倍规模诊断，修复generated
  update scratch allocation和Industrial重复refresh读取，保留RTD stable-order
  barrier与Industrial frontier scaling的真实边界；
- 按角色与场景把完整项目事实集中到`project/`、把用户信息投影到`docs/`，并以
  allowlist建立可验证的curated source archive。

Report不预写尚未执行的动态workflow状态：本报告所在clean SHA只有在下文规定的
同SHA evidence全部通过后才签署，否则保持blocked。

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

Tag、GitHub Release、visibility、signing与publishing仍是外部授权边界。POM中的
`v1.0.0`只定义planned release identity，不证明tag已存在。

## 2. 产品目标核对

| Blueprint目标 | 当前实现与evidence | 当前判断 |
|---|---|---|
| Java 8 Schema-Defined、Compiler-Specialized | annotation、Corretto javac 8 processor、schema-specific generated facade；compiler/golden/external consumer覆盖literal与generated scratch变更 | 一致；最终同SHA Full/consumer解析 |
| State / Owner + Capability + Plan / Lifecycle | Group/root/owned-child、Metadata/Plan、Table/DataFlow lifecycle；未发现live DTO/Collection graph或并行Owner | 一致 |
| packed columnar runtime-state data plane | primitive/String columns、exact/Candidate/column path；generated scratch仍为Table-owned、typed、bounded primitive arrays | 一致 |
| typed local Transformation / DataFlow | expression、aggregation、Group/Join/Window/Expanded、bounded scheduler；所有integral路径fail closed | 一致；DataFlow v5 clean calibration已闭合 |
| predictable resource / failure / observation | Plan hard limits、Group/Table/Invocation ledger、structured failure、retained/transient/high-water分层；几何scratch无法被Plan接纳时退回exact capacity | 一致；resource语义未放宽 |
| 普通Java 8 consumer可用 | 四个同版本artifact、processor build-only、真实external Maven fixture、三个reference application | 一致；最终consumer/application Gate解析 |
| 可用于真实应用建模 | Industrial、Grassing、RTD覆盖调度、动态个体仿真与typed dispatch三种不同shape，并给出Owner/Access/Plan/lifecycle/performance用法 | 达到V1试用前置；不等于特定业务适配完成 |
| 性能与规模不过度外推 | 九个application profile、8条required runtime-scale；本轮另有100K/1M diagnostic和checksum guard | 一致；保留环境/workload边界 |
| selected private-source release诚实可追溯 | clean SHA、Full、binary/source package、security/provenance、matrix、conditional sign-off | 由最终retained bundle解析 |

产品语义、实现、测试和reference consumer之间没有新的未裁决差距。当前产品已经
具备进入真实项目试用所需的schema compiler、typed runtime、resource/failure、
lifecycle、consumer和受限规模证据。使用者不进入完整项目事实即可完成接入，
贡献者和维护者仍可追溯全部Owner与evidence。尚未拥有production telemetry、
跨环境SLA、public distribution或任意领域算法复杂度保证；这些边界不会被写成
V1已完成能力。

## 3. 候选与 Gate

进入本轮性能专题前的远端基线为`4eac584af643…`。当前source successor包含：

- `b189d1130055…`：有界scratch增长、Industrial refresh读取优化与consumer回归；
- `eac9b60fdbbb…`：Grassing 5-fork calibration后的三个baseline原位replacement。

`eac9b60…`已完成九个application profile的3-fork Full并全部通过。因为最终治理
Report会产生新的clean SHA，release状态仍由最终同SHA artifact而不是上述中间
commit名称解析。

| Gate | 状态 | 直接依据与边界 |
|---|---|---|
| G0 | passed | Java-only scope、正式Owner、claim boundary与核心抽象叙事稳定 |
| G1–G4 | same-SHA evidence-resolved | 最终clean SHA的canonical Full、external consumer与private CI Full都必须通过 |
| G5 | same-SHA evidence-resolved | 最终SHA的九个application profile与8条required runtime-scale lane必须全部passed；10M/100M不进入blocker |
| G6 | conditional Owner sign-off | 同SHA binary/source package、reproducibility、security/provenance、sealed bundle、support matrix、private CI、manual qualification与下载checksum全部成功即passed，否则blocked |

旧vendor、旧candidate、working-tree profile或单机最好值不能替代这些evidence。

## 4. 上轮尾项与本轮新增专题

| 项目 | 最终处置 |
|---|---|
| canonical Full | 上一candidate已闭合；最终successor必须同SHA重放，任一失败即blocked |
| exact qualification provenance | runtime-scale继续绑定clean commit、唯一source manifest、8条required record与checksum |
| source identity边界 | manifest只覆盖实际production/build/runner closure，并纳入执行shell library |
| DataFlow baseline provenance | v5绑定clean executable commit固定3 forks；workload与threshold未放宽 |
| 抽象叙事闭环 | Design Index继续拥有Why/Owns/Not/Relationships/Lowering/Lifecycle/Resource/Failure/Evidence/Evolution |
| release evidence留存 | manual workflow保留同SHA identity、binary/source package、security、checksums与90天artifact |
| NOTICE / security drift | 品牌与checksum边界保持；OSV仍只说明执行时已知advisory |
| AI consumer Skill | Codex positive real consumer通过；第二宿主与negative行为验证由Owner对V1明确waive，不声明multi-tool support |
| Temporary | 上轮专题目录已删除；本次项目组织cutover仍由active Temporary约束，只有DOCX路径、全部Gate与delivery验证闭合后才退役 |
| 三example profiling | closed：三个example各有CPU/allocation/GC/thread或规模证据；实现、保持或拒绝优化均有明确裁决 |
| 大数据shape | closed for V1：Grassing 1M、Industrial 1M fixed-machine、RTD 10倍work用于诊断；100M受32 GiB专题上限且非required，不盲目消耗资源 |
| 性能最佳用法 | closed：进入用户性能指南与当前性能Report，不建立第二套Design |

上轮遗留尾项没有被本专题遗漏。Performance专题新增发现中，generated scratch
allocation和重复refresh读取已关闭；Industrial frontier与RTD stable sort被保留为
应用shape/算法边界，而不是伪装成SOMA Design gap。

## 5. 性能与规模治理裁决

Grassing的generated update scratch从exact-growth改为1.5倍有界增长：

- 100K × 1,000 tick allocation约下降85.7%；
- 1M × 100 tick allocation约下降86.2%；
- retained update scratch按workload约增加32%–42%，仍受Table Plan与Group
  ledger限制；preferred growth不被接纳时使用exact required；
- schema/input/result/plan checksum保持，新的allocation baseline明显收紧，
  timing Gate没有放宽。

Industrial在同一machine candidate group提升不变version/family/availability读取：

- 100K solve约下降8.9%；
- 1M fixed-100-machine solve约下降30.7%；
- 1M单位成本仍约为100K的6.4倍，表明application `CandidateFrontier`复杂度尚非
  线性；不对任意frontier形状作承诺。

RTD未改代码：profile显示stable primitive sort约占CPU sample的66.8%，worker
parked来自bounded executor等待而非锁瓶颈。V1没有足够证据用新索引、并发Table或
不稳定顺序改写该语义barrier。

完整数字、baseline版本、最佳用法和claim boundary由
[当前性能与规模摘要](current-performance-summary.md)唯一拥有。

## 6. G6 package、security 与 provenance

最终candidate的manual workflow必须验证：

- parent/module POM、四个binary/source/javadoc artifact共17件；
- Java classfile major 52、License/NOTICE精确；
- 两个隔离build byte-for-byte一致；
- curated source archive内容与allowlist一致，从archive本身完成exact toolchain与
  Maven reactor verify，且不泄漏`project/`、repository tests、CI或raw evidence；
- SBOM、OSV known-vulnerability与declared-license诊断；
- production runtime第三方依赖为0；
- binary/source package与security summary的commit、`dirty=false`和
  `artifactVersion=1.0.0`与candidate一致；
- 完整bundle生成`evidence-checksums.sha256`并保留90天。

OSV结果只代表执行时已发布的已知advisory，不是public security certification。
Unsigned artifact符合当前private-source profile；Maven/publishing/signing未选择。

## 7. AI consumer Skill

唯一canonical Skill为`.agents/skills/use-soma-java/`，只拥有AI consumer
workflow。Blueprint/Design继续拥有长期语义，POM/generated source/class/golden/
external consumer继续拥有精确surface。

已形成instruction-only bundle、结构/drift Gate以及Codex positive real-consumer
compile/run evidence。Product Owner于2026-07-29明确决定V1暂不执行第二独立AI宿主
和negative/anti-pattern行为验证。该项是`waived for V1`，不是passed；因此不得
声明multi-tool support，未来扩大工具support profile时必须重新取证。

## 8. Scope non-regression 与 surface delta

相对上一private-source signed-off基线，当前successor累计变更为：

- public Java type、module、production artifact与runtime dependency delta均为0；
- package-private production type仍只增加DataFlow
  `IntegralArithmetic`及其invocation-local `ExactSum`；
- processor统一generated Java literal escaping，并把generated update scratch改为
  Plan/ledger约束的几何增长与exact fallback；
- DataFlow raw/closed、scalar/parallel reduction、prefix、Group、Window、
  Expanded统一为fail-closed checked semantics；transformation/kernel protocol为
  v5/v6；
- Industrial仅在已有`CandidateFrontier`/`SchedulerRuntime`内提升同group不变
  读取；Grassing和RTD领域production source不变；
- external dense fixture增加resource/growth regression；九个application baseline
  总数不变，Grassing三个baseline原位replacement为v6/v5/v5；
- Blueprint/Design/Map/Conformance/Process/Report与模块事实原子迁入`project/`；
  用户入口迁入`docs/`，没有平行current路径或兼容tombstone；
- 新增一个curated source allowlist及其smoke script，release workflow把archive
  build、content set、checksum和provenance纳入同SHA bundle；
- workflow checkout关闭credential persistence；
- 没有temporary public/generated API、parallel Design Owner、test-only bypass、
  新第三方production dependency、migration API或未裁决`UNKNOWN`；active项目组织
  Temporary在DOCX与最终Gate闭合前仍保留。

这是contract-preserving internal refinement加已裁决的integral semantics
closure，不依赖未来public consumer、核心事实或canonical hot-path migration才成立。

## 9. 最终授权边界

Product Owner已确认条件式G6 sign-off，并授权本轮把`develop`推送到private
`somaruntime/soma-java`、触发CI和Release qualification workflow。最终commit只有
在产品语义偏差关闭、DOCX内嵌旧路径归零、Temporary退役、本机同SHA Full/G5、
private CI、manual qualification和下载bundle checksum全部成功后才生效为
`passed`。

Tag、GitHub Release、repository visibility、signing、publishing和公开发布仍未
授权，本轮不执行。Private-source G6 passed也不构成production SLA、public
availability、Maven readiness或真实业务项目适配结论。
