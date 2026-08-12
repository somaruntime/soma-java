# SOMA Production Core 质量检查方法可行性探索

类型：Temporary / Exploratory Governance Topic

状态：`ACTIVE / DESIGN_FROZEN / Q0-Q2_AUTHORIZED / NOT_QUALITY_GATE`

日期：2026-08-13

正式事实源：否

Owner：本次质量检查方法实验的目标、范围、假设、实施切片、采用裁决与Temporary关闭路径

## 1. 专题结论

本专题不是建设一套永久质量制度，也不预设静态检查一定能提升SOMA Java质量。它要以一次
有界、可重放的真实实验回答：

> `javac -Xlint:all`、精选PMD规则、JaCoCo路径报告和有边界的Codex裁决，能否发现现有
> compiler、tests、Qualification与常规Design/code review没有发现的高价值问题；为这些新增
> 证据付出的工具运行、工程判断和Token成本，是否值得纳入日常开发。

实验允许得到三种同样合法的结论：

- `ADOPT`：净收益明确，建议后续单独晋升最小稳定流程；
- `PARTIAL_ADOPT`：只建议保留有独立价值的工具、规则或触发场景；
- `REJECT`：新增价值不足以覆盖噪声、维护、时间或Token成本，退役全部试验机制。

详细工程设计见[Candidate Design](candidate-design.md)。本文只拥有专题章程，不覆盖正式
Blueprint、Design、code、build或Conformance事实。

## 2. 为什么进行本次探索

SOMA Java已经完成I0-I8、G1-G10、Canonical IR/Execution M1、Vectorized Pipeline和Physical
Execution Engine M2的实施与资格，拥有compiler positive/negative、generated golden、property、
fault injection、reference differential、scenario、profile与package evidence。

当前仍存在一个未知问题：通用静态分析与覆盖路径报告，能否在上述体系之外提供足够高密度的
新增质量信号。现在没有证据支持以下任一结论：

- “引入这些工具一定值得”；
- “成熟的Design与测试体系已经让这些工具没有价值”；
- “工具产生的finding足够准确”；
- “收集与裁决finding的时间、Token和维护成本可接受”。

因此，本专题把方法本身当成待验证对象，而不是先把它晋升为项目流程。

## 3. 目标与可证伪假设

### 3.1 Target

在两个production module上完成一次受控质量基线，测量新增质量价值与完整成本，形成可解释的
`ADOPT / PARTIAL_ADOPT / REJECT`建议，并关闭所有未获晋升的试验surface。

### 3.2 Hypotheses

| ID | 候选假设 | 怎样被否证 |
|---|---|---|
| H1 | 工具能发现既有Evidence未发现的真实production问题 | 所有信号均为已知、重复、误报或低价值风格问题 |
| H2 | 精选规则的信号密度足以支持有边界裁决 | 噪声、机械重复或解释成本主导结果 |
| H3 | 报告可以在Java 8与现有两module构建中可靠重放 | 需要污染normal build、改变artifact或无法获得可信报告 |
| H4 | 增量收益与时间、Token、维护成本相称 | 完成一次基线的成本明显高于其新增证据价值 |
| H5 | 工具不会反向驱动错误抽象、覆盖率造数或hot-path退化 | 为消除finding需要违背Design Intent或增加低价值测试/抽象 |

这些假设必须分别记录Evidence；不能因为工具成功运行就视为成立。

## 4. 范围

### 4.1 包含

- `soma-runtime`与`soma-processor`的authoritative Java production source；
- 两module test source的Xlint与JaCoCo test-JVM执行路径；
- Java 8 `javac -Xlint:all`、精选PMD规则和JaCoCo report-only可行性；
- generated source与forked compiler/consumer的归因边界；
- 一次完整baseline、一次finding裁决、必要且已获授权的高置信修复与受影响复验；
- 工具时间、工程判断时间、信号数量与类型、可观测Token成本和集成摩擦；
- 对每项工具分别作出采用建议，而不是捆绑裁决；
- 实验产物、候选配置与Temporary的关闭路径。

### 4.2 排除

- 宣称已经建立“全项目质量体系”或证明整个仓库质量；
- 默认扫描`soma-examples`、`benchmarks`、文档和全部Shell/Python build machinery；
- 统一coverage百分比、finding数量、文件长度或复杂度门槛；
- `-Werror`、PMD failure gate、coverage gate、CI阻断或release gate；
- 创建永久`./scripts/quality.sh`、全局signal registry、Skill或`AGENTS.md`日常协议；
- 为提高覆盖率冻结private shape、增加低价值转发测试或重复已有qualification；
- 自动修复、自动suppression、大范围风格治理或无Design追溯的重构；
- 把一次机械检查扩张为与finding无关的全仓抽象/架构重新审核；
- 性能profile、benchmark结论或任何release/publication声明；
- 跨多个未来专题持续试用后才能关闭的Q3/Q4生命周期。

## 5. 正式事实与追溯

本专题主要服务：

- [Blueprint BP-14、BP-15](../../blueprint/README.md#8-v1-blueprint-requirements)：build/artifact
  完整性与evidence-backed claim；
- [核心抽象与叙事第22、23节](../../design/core-abstractions-and-narratives.md#22-quality-responsibility-allocation)：
  production defense与tests/evidence的责任分离；
- [Implementation Architecture](../../design/implementation-architecture.md)：Java 8、two-artifact、
  dependency/plugin与build边界；
- [Conformance](../../conformance/README.md)：当前实现与Evidence状态。

BP-12只在finding或修复实际触及primitive hot path时适用；静态报告或coverage本身不能证明
no-boxing、低分配或性能成立。

本Temporary不是第二份Design。发现真实产品、架构、不变量或性能取舍时，必须回到精确正式
Owner；不得在本文件中接受风险或改写合同。

## 6. 当前授权边界

Product Owner已于2026-08-13审核本Candidate，并授权Q0-Q2完整实验：

- 允许核对、下载和试运行精确PMD、JaCoCo Maven plugin；
- 允许必要的default-off POM/profile与`build-support/quality/`trial surface；
- 允许在既有正式Design范围内修复高置信production问题；
- 允许最终形成`ADOPT / PARTIAL_ADOPT / REJECT`并删除未获晋升的试验机制；
- 允许分Slice本地提交，但不推送。

该授权不包含CI、质量门、稳定脚本、Skill、release/publication，也不允许改变Blueprint/Design
产品语义、增加production dependency/artifact或接受正确性、架构、性能和安全取舍。

## 7. Candidate method

首轮只研究三种互补信号：

| Signal source | 候选职责 | 不能证明什么 |
|---|---|---|
| Java 8 `javac -Xlint:all` | compiler可确认的warning | Design正确、运行时正确、无warning即高质量 |
| 精选PMD规则 | 高置信错误模式与少量坏味道线索 | 规则命中即缺陷、复杂度/风格即授权重构 |
| JaCoCo | Surefire test JVM实际执行的line/branch路径 | 未执行即缺陷、百分比即质量、外部fork进程覆盖情况 |

初次实施只允许temporary runner与default-off候选配置；不直接占用`scripts/`稳定入口。精确
集成、报告和signal model由[Candidate Design](candidate-design.md)规定。

## 8. Delivery Slice Map

### Q0 — Tool feasibility与冻结实验输入

目标：证明候选工具在现有Java 8/Maven/two-module边界中可以生成可信报告，并冻结一次实验的
工具、ruleset、source与candidate fingerprint。

必须完成：

- 重新核对工具版本、Java 8、license、known vulnerability和plugin dependency tree；
- 形成精确PMD rule allowlist，不启用整个category；
- 证明JaCoCo agent不会覆盖`soma-processor`已有`tools.jar` JVM参数；
- 明确Surefire JVM、forked `javac`、consumer与negative fixture的覆盖边界；
- 比较CLI orchestration与default-off profile，选择改动更小且可重放的一种；
- 证明普通build、artifact与runtime dependency tree不变化。

Exit：工具链可信或明确判定不可行；若不可行，允许直接形成`REJECT`并关闭专题。

### Q1 — 一次production-core baseline

目标：只对Q0冻结输入运行一次完整baseline，并记录原始信号与完整成本。

必须完成：

- 清理旧报告，绑定source/tool/ruleset/environment fingerprint；
- 运行Xlint、PMD与JaCoCo；
- 输出原始报告与不可混淆的execution/finding状态；
- 记录工具wall time、setup/integration时间和当次可观测Token成本；
- 不修改production source，不调规则美化finding数量。

Exit：报告完整可信，或明确说明哪一类信号无法获得及其影响。

### Q2 — Triage、必要修复与方法裁决

目标：判断信号是否新增、准确、有价值，并计算获得这些价值的实际成本。

必须完成：

- 按`真实缺陷 / 有用线索 / 已知重复 / generated derivative / 误报 / 不适用 / 工具限制`分类；
- 只在既有授权与正式Design内修复高置信问题；
- 最多重跑受修复或配置变化影响的检查，不重复无新Evidence的全量动作；
- 分工具给出价值、精度、成本、摩擦与错误激励评估；
- 形成`ADOPT / PARTIAL_ADOPT / REJECT`建议；
- 将稳定实验结论、精确工具/rule identity与必要复现信息进入一个正式Conformance记录，并删除
  本Temporary；
- 未获Product Owner正式晋升的trial runner、profile、ruleset和suppression全部移除。

Exit：实验问题已经回答，成本与收益可比较，Temporary和试验surface能够关闭。

## 9. 采用裁决

采用不是由单一数字自动决定。Q2按以下五个维度形成Evidence矩阵：

1. **Unique value**：是否发现既有Evidence未拥有的真实问题；
2. **Precision**：actionable signal与噪声/重复的关系；
3. **Cost**：setup、machine、Codex判断、复验和Token成本；
4. **Integration friction**：是否改变normal build、artifact、依赖或维护责任；
5. **Behavioral incentive**：是否诱发错误重构、coverage gaming或测试重复。

决策规则：

- `ADOPT`：存在明确新增价值，信号精度和增量成本相称，且不污染普通build和Design主线；
- `PARTIAL_ADOPT`：只有部分工具、规则或风险触发场景满足上述条件；
- `REJECT`：没有独立新增价值，或噪声、集成、判断与维护成本占主导；
- `INSUFFICIENT_EVIDENCE`不是长期状态：只能指出一次实验为何失真，并决定一次最小重试或直接
  `REJECT`，不能让Temporary无限延期。

即使建议`ADOPT`，本专题也不自动实施日常流程。永久入口、CI、门槛、全局裁决登记与跨项目Skill
都必须基于本次Evidence另行Surface Admission和Product Owner授权。

## 10. Stop Rules

出现以下任一情况时，停止扩张并形成当前结论：

- 工具无法在Java 8或当前Maven拓扑可靠运行；
- JaCoCo或其他配置会覆盖现有JVM参数、改变普通build或产生不可信报告；
- 需要新增第三production artifact、runtime dependency或修改公开产品语义；
- PMD规则只有扩大到style/design全category才能产生信号；
- finding主要来自derived repetition，且无法在generator Owner处归并；
- 为提高指标需要增加低价值测试、抽象、suppression或hot-path成本；
- 相同source、ruleset和环境下重复运行不再产生新Evidence；
- 成本已经明显成为主要发现，而继续扩大不会改变采用裁决；
- 需要Product Owner接受正确性、架构、性能或安全风险。

Stop不是失败。证明方法不适合SOMA，正是本次探索的合法产出。

## 11. Definition of Done

本次专题只有同时满足以下条件才完成：

- [ ] Q0工具与集成可行性有可重放Evidence；
- [ ] 实验输入、candidate、environment和报告identity明确；
- [ ] Q1只运行一次可信baseline，并区分工具执行与质量结论；
- [ ] 所有原始信号得到分类或按同源family归并；
- [ ] 时间、运行、判断、复验和可观测Token成本已记录；
- [ ] 高置信修复没有偏离Blueprint/Design或形成性能退化；
- [ ] 每项工具分别形成价值判断；
- [ ] 最终形成`ADOPT / PARTIAL_ADOPT / REJECT`建议；
- [ ] Conformance只记录Evidence支持的结论；
- [ ] 未获正式晋升的trial surface全部删除；
- [ ] Temporary完成replacement closure。

## 12. 当前结论

当前Candidate Design已经冻结，Q0成为唯一Active Slice：

```text
TOPIC_DESIGN          FROZEN
IMPLEMENTATION        Q0-Q2_AUTHORIZED
TOOL_ADMISSION        NOT_STARTED
QUALITY_BASELINE      NOT_STARTED
DAILY_PROCESS         NOT_ESTABLISHED
CI / GATE             NOT_AUTHORIZED
RELEASE               NOT_AUTHORIZED
```

下一步是完成Q0工具与集成可行性。授权边界不包含plugin正式晋升、push或发布。
