# SOMA Production Core 质量检查方法可行性探索 Candidate Design

类型：Temporary / Candidate Design / Exploratory Method

状态：`DESIGN_FROZEN / Q0-Q2_AUTHORIZED / NOT_FORMAL_FACT / NOT_IMPLEMENTED`

日期：2026-08-13

正式事实源：否

Owner：一次production-core质量检查实验的信息模型、工具边界、执行协议、成本归因、采用裁决与失败边界

## 1. 文档责任

本文回答：怎样用一次有界实验判断质量检查方法对SOMA Java是否具有净价值。它不定义SOMA
产品语义，不拥有production correctness，不建立永久质量流程，也不授权修改POM、代码、tests、
CI或稳定脚本。

上游专题章程见[README](README.md)。正式产品和工程事实仍由以下Owner拥有：

- [V1 Blueprint](../../blueprint/README.md)；
- [Design总览](../../design/README.md)；
- [核心抽象、叙事与不变量证明链](../../design/core-abstractions-and-narratives.md)；
- [Implementation Architecture](../../design/implementation-architecture.md)；
- [Conformance](../../conformance/README.md)。

## 2. Design Intent

质量工具只产生候选信号；它们不能拥有缺陷语义、Design Intent、修复授权或最终质量结论。

本设计的主叙事是：

```text
freeze exact experiment candidate
    -> prove tool and integration trustworthiness
        -> collect one raw baseline without source changes
            -> classify signals against current Owners and Evidence
                -> perform only authorized high-confidence repairs
                    -> rerun only affected evidence
                        -> compare unique value with full cost
                            -> recommend adopt / partial adopt / reject
                                -> promote evidence and remove trial surfaces
```

设计必须同时防止两种错误：

1. 工具报告存在就声称项目质量提升；
2. 为追求报告数字而破坏原本正确的抽象、性能路径和测试职责。

## 3. 核心信息模型

以下名称是实验文档概念，不要求创建production class或新framework：

| Concept | Owner | Lifecycle |
|---|---|---|
| `ExperimentCandidate` | 本专题 | 一次baseline；绑定source、dirty state、JDK、Maven、tool、ruleset和scope |
| `ToolRunManifest` | temporary runner | 单次run；记录命令、版本、环境、输入fingerprint、起止时间和报告完整性 |
| `RawSignal` | 工具原始报告 | immutable evidence；不直接等于defect |
| `SignalDisposition` | Q2裁决 | 绑定规则、source identity、分类、理由和相关正式Owner；只在本专题有效 |
| `CostLedger` | 本专题 | 从Q0开始到Q2关闭；记录全部可观测成本 |
| `MethodDecision` | 专题Conformance | 实验结束时形成；按工具给出采用建议和claim boundary |

这些信息不得形成第二套全局质量数据库。若未来真实重复信号证明需要长期registry，必须另行
Surface Admission。

## 4. 实验身份与可重放性

### 4.1 ExperimentCandidate

一次baseline至少绑定：

- Git commit、branch、dirty path清单和相关diff fingerprint；
- Java vendor/version、`JAVA_HOME`与Java 8事实；
- Maven version与实际settings/repository来源边界；
- OS、architecture；
- 两个module及其source/test roots；
- Xlint参数、PMD plugin/engine/ruleset和JaCoCo plugin版本；
- candidate runner/config fingerprint；
- 是否clean target、是否复用依赖缓存；
- 每个报告的路径和checksum。

未绑定这些身份的报告只能用于诊断，不能支持采用裁决。

### 4.2 报告状态

必须分别输出：

```text
TOOL_EXECUTION = PASS | FAIL
REPORT_INTEGRITY = COMPLETE | PARTIAL | UNTRUSTED
FINDINGS = NONE | PRESENT | NOT_AVAILABLE
QUALITY_VERDICT = PENDING | PASS | PARTIAL | FAIL
```

`TOOL_EXECUTION=PASS`只表示工具成功完成；不能被压缩成“质量PASS”。

## 5. Source scope

| Surface | Xlint | PMD | JaCoCo | 首次实验解释 |
|---|---:|---:|---:|---|
| `soma-runtime/src/main/java` | 是 | 是 | 由runtime Surefire tests投影 | production core |
| `soma-runtime/src/test/java` | 编译warning | 否 | 是 | test path evidence，不做style治理 |
| `soma-processor/src/main/java` | 是 | 是 | 由processor Surefire tests投影 | compiler/codegen core |
| `soma-processor/src/test/java` | 编译warning | 否 | 是 | fixture/DSL避免低价值PMD噪声 |
| `target/generated-sources` | 编译时观察 | 默认排除 | 默认排除 | derived output；finding回溯generator/template |
| forked `javac` / consumer / compile-negative JVM | 由既有qualification拥有 | 否 | 默认不计 | JaCoCo Surefire agent不能冒充跨进程coverage |
| `soma-examples` / `benchmarks` | 既有check编译 | 否 | 否 | 不属于首次production-core实验 |
| Shell/Python/build-support | 否 | 否 | 否 | 本专题不声明全仓质量覆盖 |

相同generator defect可以按同一template family归并，但必须保留代表性source和consumer evidence。
拥有真实runtime责任的authoritative Java source不能仅因类名含`Generated`而排除。

## 6. Tool design

### 6.1 Java 8 `javac -Xlint:all`

- 使用qualified Java 8 compiler；
- 首轮report-only，不启用`-Werror`；
- production与test warning分开归类；
- annotation processing、forked compiler和generated output必须标明实际观察边界；
- 只有真实warning已经清零或均有正式裁决后，未来才可以讨论`-Werror`。

### 6.2 PMD

Q0必须冻结**精确rule allowlist**，不能直接启用完整category。初始规则只能从Java 8适用的
`errorprone`、`bestpractices`和少量确定性`multithreading`规则中逐项选择；`design`、`codestyle`、
documentation、CPD与统一复杂度规则不在首轮范围。

规则准入条件：

1. 能描述具体错误模式或高置信坏味道；
2. 适用于Java 8和当前SOMA source shape；
3. finding可以追溯到明确source/consumer/Owner；
4. 不以文件长度、命名偏好或抽象数量直接授权重构；
5. dry-run噪声可在一次Q2预算内完成分类。

在baseline前最多允许一次ruleset调整，用于移除明显不适用规则；baseline fingerprint冻结后不得
为了改善数字继续调规则。

截至2026-08-13，设计时点候选为
[Maven PMD Plugin `3.28.0`](https://maven.apache.org/plugins/maven-pmd-plugin/summary.html)与
[PMD `7.26.0`](https://github.com/pmd/pmd/releases)，但这些属于易漂移工具事实，不是长期合同。
Q0必须按官方release、[Java支持周期](https://pmd.github.io/pmd/pmd_about_support_lifecycle.html)、
license、known vulnerability和dependency tree重新验证；若显式覆盖plugin默认PMD engine，必须
证明组合受支持且报告可重放。

### 6.3 JaCoCo

JaCoCo只回答“由instrumented Surefire JVM执行了哪些line/branch”。它不证明：

- 未覆盖路径就是缺陷；
- coverage百分比代表质量；
- forked `javac`、independent consumer或Shell资格已经被观察；
- performance、allocation或fixed-host结果仍有效。

截至2026-08-13，设计时点候选为
[JaCoCo `0.8.15`](https://www.jacoco.org/jacoco/)，Q0仍需重新验证。

#### Processor JVM参数组合

`soma-processor`当前Surefire必须保留：

```text
-Xbootclasspath/a:${java.home}/../lib/tools.jar
```

[JaCoCo `prepare-agent`](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)不能直接
争夺或覆盖现有`argLine`。优先验证以下候选组合：

1. `prepare-agent`使用独立`propertyName`，例如`soma.jacoco.argLine`；
2. 普通build为该property提供空默认值；
3. Surefire使用late property evaluation组合agent参数；
4. processor在同一最终`argLine`中同时保留`tools.jar`；
5. runtime module使用相同agent property，但没有processor bootclasspath参数。

候选形态：

```xml
<argLine>@{soma.jacoco.argLine} -Xbootclasspath/a:${java.home}/../lib/tools.jar</argLine>
```

这只是Q0待证明的integration hypothesis。必须验证property未激活时不会把literal placeholder传给
JVM，激活时agent与`tools.jar`同时生效，且两个module报告均非空、可解释。失败时不得以不可信
coverage继续Q1。

## 7. Build integration与Surface Admission

### 7.1 优先级

Q0按以下顺序选择最小实现：

1. fully-qualified CLI orchestration可以可靠固定配置且不修改normal build；
2. 若CLI不能正确组合Xlint、PMD和JaCoCo，则在两个module各使用一个default-off `quality`
   profile；
3. 不为消除少量POM重复引入parent、第三artifact或共享build module。

### 7.2 Trial runner

若实施获批，首轮只允许在`build-support/quality/`创建temporary trial runner、PMD ruleset和必要
说明：

- **consumer**：Q0-Q2实验与Codex；
- **Owner**：工具编排、report identity和实验失败，不拥有production quality；
- **lifecycle**：从Q0开始，到Q2采用裁决后删除或经另一个正式晋升专题替换；
- **failure boundary**：环境、plugin、规则或报告不可信时非零失败；finding存在本身不改变exit；
- **Evidence**：Java 8重放、normal-build不变、artifact/dependency无leak、报告完整性。

首轮不创建`scripts/quality.sh`。`scripts/`只接纳已经证明有长期consumer的稳定入口。

### 7.3 Maven profile

若Q0证明必须修改POM：

- profile默认关闭；
- plugin有精确版本；
- normal Maven lifecycle、现有`check.sh`/`qualify.sh`语义不变；
- plugin不进入runtime dependency tree或production artifact；
- JaCoCo report与PMD output只进入ignored `target/quality/`；
- profile必须与trial runner共同退役，除非后续获得正式晋升授权。

## 8. Execution protocol

### 8.1 Q0 feasibility

```text
verify environment and official tool facts
    -> freeze candidate versions and exact PMD rules
        -> prove Java 8 execution
            -> prove JaCoCo argLine composition
                -> prove normal build/artifact/dependency unchanged
                    -> freeze ExperimentCandidate
```

Q0不得修改production source来让工具运行。工具与现有合法build无法共存时，应把它记为方法成本
或直接否证H3。

### 8.2 Q1 baseline

```text
remove prior reports
    -> record candidate manifest
        -> run Xlint once
        -> run PMD once
        -> run JaCoCo once
            -> verify report integrity and checksums
                -> freeze raw reports and CostLedger baseline
```

同一输入的重复运行不提供新Evidence。只有工具失败、报告不完整或最终修复改变对应source时才能
重跑受影响部分。

### 8.3 Q2 triage

每条signal或同源family依次执行：

```text
raw signal
    -> verify rule and exact source
        -> trace direct consumer and formal Owner
            -> compare existing tests/qualification/design evidence
                -> classify value and actionability
                    -> fix within authority, record limitation, or reject signal
```

不得根据rule、coverage、复杂度或文件长度直接删除/拆分/合并核心抽象。

## 9. Signal taxonomy

| Classification | 含义 | Q2动作 |
|---|---|---|
| `TRUE_DEFECT` | 当前production行为、failure、resource、build或contract存在真实缺陷 | 在授权内修复并验证；越过Owner则停止升级 |
| `USEFUL_LEAD` | 尚未证明为缺陷，但揭示具体、高价值调查点 | 完成一次最小调查；不扩张成开放审查 |
| `DUPLICATE_EXISTING_EVIDENCE` | 信号已由现有test/Gate/Conformance拥有 | 计入重复成本，不新增平行防线 |
| `GENERATED_DERIVATIVE` | 同一generator/template问题的机械投影 | 回到authoritative generator归并 |
| `FALSE_POSITIVE` | 规则语义与实际source/contract不符 | 记录本次裁决；不自动全局suppression |
| `NOT_APPLICABLE` | 规则前提不属于当前scope或语言/架构 | 从本次ruleset移除并说明 |
| `TOOL_LIMITATION` | 工具无法可靠观察真实执行或source形态 | 限定claim，必要时否证该工具价值 |

`ACCEPTED_RISK`不属于本实验可自主作出的SignalDisposition。正确性、架构、性能、安全或产品风险
只能由正式Owner接受。

## 10. Cost model

### 10.1 必须记录的成本

| Cost family | 字段 |
|---|---|
| Setup/integration | Q0开始/结束时间、POM/config/runner改动量、失败尝试及原因 |
| Machine | 每个工具wall time、峰值资源若可低成本获得、dependency下载是否发生 |
| Codex reasoning | triage开始/结束时间、review pass数量、signal/family数量、扩大读取次数 |
| Verification | 修复后重跑了哪些工具/test/qualification及wall time |
| Maintenance projection | 长期版本更新、规则裁决、CI/本地运行的预期责任 |
| Token | 只记录Codex宿主或Goal明确提供的实际usage；不可见时标记`NOT_OBSERVABLE` |

不能用turn数、文本长度或主观感觉伪造Token数字。Token不可见时，可以记录signal数量、读取范围、
tool call和active review time作为工作量背景，但必须明确它们不是Token。

### 10.2 收益分类

| Value | 定义 |
|---|---|
| `HIGH` | 揭示真实correctness、failure、resource、concurrency、build/artifact或重要架构缺陷 |
| `MEDIUM` | 揭示有具体consumer和failure/maintenance影响的latent问题 |
| `LOW` | 主要是可读性、风格或局部简化，没有具体产品/工程风险 |
| `NONE` | 已知重复、误报、不适用或无法支持行动 |

报告必须同时给出收益和成本；不能只展示修复数量。

## 11. Decision model

每项工具分别形成：

| Dimension | Evidence |
|---|---|
| Unique value | `HIGH/MEDIUM/LOW/NONE`信号及既有Evidence对照 |
| Precision | actionable、重复、误报、不适用和工具限制数量 |
| Full cost | setup、machine、reasoning、verification、Token与维护投影 |
| Integration | normal build/artifact/dependency是否保持，配置是否脆弱 |
| Wrong incentive | 是否诱发coverage gaming、机械重构、suppression或测试重复 |
| Repeatability | 同一candidate能否可靠重放并得到相同原始结论 |

### 11.1 `ADOPT`

至少存在明确的独立新增价值，且信号精度、重放性、集成与增量成本共同可接受。`ADOPT`只是一项
晋升建议，不自动创建稳定命令、CI或质量门。

### 11.2 `PARTIAL_ADOPT`

工具组合不是原子包。可以只采用Xlint，或只在高风险compiler/runtime变更时运行PMD/JaCoCo；也可以
保留report-only而拒绝CI/threshold。

### 11.3 `REJECT`

下列任一结论可支持拒绝某项工具：

- 没有产生既有Evidence之外的独立价值；
- 噪声与重复信号主导Codex判断成本；
- Java 8/Maven集成脆弱或报告边界不可信；
- 为维护指标需要增加无产品价值的测试、配置或suppression；
- 长期维护责任明显大于偶发收益。

## 12. Failure与安全边界

- plugin、环境、rule load、agent injection或报告校验失败：`TOOL_EXECUTION=FAIL`；
- 某类报告缺失：`REPORT_INTEGRITY=PARTIAL/UNTRUSTED`，不得外推完整baseline；
- finding存在：不自动使build失败，也不自动允许source修改；
- Tool `Error`、OOME或JVM crash：保留原失败与环境，不包装成SOMA structured failure；
- instrumentation run不能与benchmark/profile结果混用；
- 原始报告只进入ignored build output，不提交为正式artifact；
- 最终Conformance可以保存精确tool/rule identity与必要复现参数，但不复制整套trial runner成为
  第二个executable Owner；
- 报告中的本地路径、环境或潜在敏感信息不得进入公开交付物；
- 自动fix、自动suppression和大包blanket exclusion禁止；
- 任何production修改必须使用现有测试/qualification证明没有语义、资源或性能退化。

## 13. Verification matrix

| Claim | 最小Evidence |
|---|---|
| Java 8兼容 | qualified Java 8运行、官方support与实际plugin smoke |
| normal build不变 | profile关闭前后普通clean build、artifact set/hash语义与dependency tree对照 |
| JaCoCo可信 | agent与processor bootclasspath同时生效、两module非空报告、明确fork边界 |
| PMD可解释 | exact allowlist、dry-run、rule load failure与代表性finding定位 |
| report identity | source/tool/rules/environment fingerprint与checksum |
| 无production leakage | runtime dependency、jar/source/javadoc/package smoke |
| finding裁决可信 | direct source/consumer/Owner/Evidence trace与分类 |
| 修复安全 | 定向test/qualification，hot path相关时相称performance evidence |
| 方法值得采用 | per-tool value/cost/integration/precision/behavior matrix |
| Temporary closure | Conformance记录、trial surface删除或另行正式晋升 |

## 14. 过度设计控制

首轮明确不创建：

- 通用quality framework、Java API或新module；
- 永久`scripts/quality.sh`；
- 全局quality-signal registry；
- CI workflow、PR bot、dashboard或趋势数据库；
- coverage threshold、PMD threshold或统一quality score；
- 为未来工具预留plugin interface；
- 跨项目Skill或自动审查prompt；
- 多轮未来专题试用才能结束的开放生命周期。

只有本次实验给出明确consumer、收益和成本证据后，后续专题才能分别准入上述surface。

## 15. Rejected alternatives

| 方案 | 本轮拒绝理由 |
|---|---|
| 一次引入Checkstyle/SpotBugs/PMD/JaCoCo/Sonar | 规则重叠、Java运行时与裁决成本过大，无法归因单项价值 |
| 首轮直接接CI | 尚不知道信号精度与成本，容易把试验变成阻断流程 |
| coverage统一80% | 百分比不表达关键Invariant，也会诱发低价值测试 |
| 每个治理专题固定全量运行 | 与风险相称原则冲突，且本次尚未证明日常价值 |
| 正式保存所有finding disposition | 在重复consumer出现前预建第二套长期Owner |
| 自动修复全部finding | 工具无法拥有Design Intent、hot-path与failure语义 |
| 通过未来若干专题证明方法 | Temporary无法有界关闭，成本也不能归因到一次候选 |

## 16. Implementation readiness checklist

Product Owner已于2026-08-13确认：

- [x] 接受本专题是探索实验，不预设采用；
- [x] 接受Q0-Q2三Slice与一次baseline边界；
- [x] 授权核对、下载和试运行精确PMD/JaCoCo Maven plugin；
- [x] 授权必要的default-off POM/profile与`build-support/quality/`trial surface；
- [x] 明确production修复仍受既有Design和实施授权约束；
- [x] 接受最终可能为`REJECT`并删除全部试验机制；
- [x] 不授权CI、质量门、稳定脚本、Skill、发布或其他工具扩张。

设计完成不等于上述授权已经获得。

## 17. Definition of Done

本Candidate Design可冻结的条件：

- 实验问题、假设与否证方式明确；
- source、generated与forked process边界明确；
- Xlint、PMD、JaCoCo职责不重叠且不冒充quality Owner；
- JaCoCo与processor `argLine`冲突有明确候选解与Q0证明要求；
- exact PMD rules属于Q0配置事实，不被category或版本口号替代；
- signal taxonomy、cost ledger与采用模型可指导一次真实实验；
- Q0-Q2均有独立Exit，且一次只激活一个Slice；
- Stop Rule与Temporary closure不依赖未来专题；
- 没有提前创建长期process、gate、registry、API或module；
- 实施与release授权边界明确。

## 18. 当前设计结论

本设计已经把上一版无界的“长期质量流程提案”收敛为一次production-core方法实验，并关闭了：

- Q3/Q4跨未来专题导致Temporary无法结束；
- JaCoCo与processor Surefire `argLine`冲突未设计；
- PMD版本与category规则被过早固化；
- stable script、global registry与全面审查被提前准入；
- 工具运行成功、finding存在和质量结论相互混淆；
- 时间、Token和判断成本没有成为正式实验输出。

当前状态为`DESIGN_FROZEN / Q0-Q2_AUTHORIZED`。工具正式采用、CI、质量门、稳定脚本与发布仍未授权。
