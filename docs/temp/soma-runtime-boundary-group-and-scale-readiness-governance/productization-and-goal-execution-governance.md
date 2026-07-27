# SOMA 产品目标形态与长任务执行治理

类型：Temporary

状态：active（作为本专题 Goal 的产品北极星、目标锁与执行契约）

Owner：SOMA product-shape、goal preservation 与 long-running execution governance

正式事实源：否

实施授权：仅限本专题 Temporary 文档收口和对 SOMA 的只读审计；后续正式
Design、production、test、benchmark、Example 与删除授权必须由用户启动 Goal 时
在消息中明确给出

事实范围：本专题确认的产品化审查维度、目标不缩水原则、Goal traceability、
防偏航/防打转协议、阶段规划、三个 Example 的后置审计和完成定义

非事实范围：当前已经 product-ready、G6/public release readiness、已批准的 public
API、production 实施已完成或任意性能 SLA

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

正式产品目标：[SOMA Java 产品蓝图](../../blueprints/soma-java-product-blueprint.md)

最后审查日期：2026-07-28

## 1. 治理意图

本专题不只优化 runtime mechanics，而要从 SOMA Java 产品目标形态重新审视：

> SOMA 应该如何被理解、如何工作、每个核心抽象为什么存在，以及 Java 8
> application developer 为什么愿意长期使用它。

本文件不建立第二份 Product Blueprint。正式 Blueprint 继续拥有目标用户、目标形态
与目标使用体验；本专题负责检查新的 Metadata、Group、Capability、Scale 和 Result
Delivery 设计是否让该产品目标更完整、更简单、更可靠，而不是只让内部 benchmark
更快。

## 2. 产品北极星

候选一句话叙事：

> SOMA Java 是面向 Java 8 application developer 的 Schema-Defined、
> Compiler-Specialized、JVM Heap-Resident、类型安全、资源可预测的高性能
> runtime-state computing library。

SOMA 产品化不是功能堆积。优秀产品应同时具备：

- 明确的目标用户、问题边界和非目标；
- 少量、稳定、可解释的核心概念；
- IDE/javac 友好的 schema-specific typed API 与 actionable diagnostics；
- 简单默认路径和显式高级路径；
- ownership、lifecycle、failure、resource 和 compatibility 可推理；
- Small/Medium/大型规模都没有隐藏的结构税或无界成本；
- examples、guides、benchmark、package、version 与支持声明相互一致；
- 当前未达到的目标进入 Conformance，不通过营销或命名消失。

### 2.1 卓越性要求

本 Goal 不是“按清单完成最低实现”，也不是只让现有 Gate 重新通过。Codex 应充分
使用设计推理、代码审计、技术 Research、独立实验、性能分析、产品思维和验证能力，
在已冻结边界内寻找整体最优方案：

- 语义比命名更准确，抽象比当前类层次更接近问题本质；
- 默认路径尽可能直接，advanced path 的成本和责任显式；
- correctness、failure 和 lifecycle 优先构造即正确；
- 性能优化优先消除 bytes、passes、allocation、retained state 和无用工作；
- simple/Small 路径不为 Large/general capability 支付不必要固定税；
- physical implementation 可演进，但 application mental model 和稳定契约不漂移；
- 代码、测试、文档和 Example 共同讲述同一个产品，而不是各自局部最优；
- 当已有方案不是最佳选择时，Codex 应提出更好的候选并用有限 evidence 裁决，
  不能因“当前就是这样”而保守复制。

“做到最好”不表示增加最多功能、抽象或配置。它表示在 SOMA Java V1 已确认的目标、
非目标、Java 8、heap-resident、compiler-specialized 和产品责任边界内，取得语义、
体验、正确性、性能、资源、可维护性与演进性的最佳整体平衡。

Codex 可以在授权范围内自主作出实现和内部设计判断。若判断会改变正式产品目标、
删除目标能力、扩大产品边界或引入新的长期依赖，则必须形成 Owner Decision，不能
以“更优”为名自行改变目标。

## 3. 完整产品旅程

最终设计和实施必须从端到端使用者旅程审查：

```text
定义 Annotation Schema
  -> javac/compiler diagnostics
  -> generated typed facade
  -> 查看并受控配置 Metadata Plan
  -> create SomaGroup / Table
  -> reserve/load/mutate
  -> 选择 Access / Transformation / DataFlow capability
  -> resource preflight 与 deterministic physical plan
  -> Eager Detached 或显式 callback-scoped streaming
  -> stats/explain/error
  -> clear/release
  -> troubleshooting / compatibility / upgrade
```

每个核心抽象必须能说明它改善了哪一段产品旅程，或关闭了哪个不可缺少的不变量。
内部复杂度若不能转化为稳定语义、正确性、可替换性、性能或诊断价值，应当被简化。

## 4. 产品化验收维度

| 维度 | 最终必须回答 |
|---|---|
| Positioning | SOMA 解决什么问题，明确不成为数据库、workflow、任意对象容器或分布式引擎 |
| Mental Model | Group、Table、Metadata、Capability、Plan、Invocation、Result 如何形成一条叙事 |
| Developer Experience | schema authoring、generated API、默认配置、诊断、错误是否自然 |
| Semantic Completeness | storage、access、mutation、relation、result 和 lifecycle 是否闭环 |
| Performance Predictability | Small/Medium、single/double 100M、String、GC、预算和拒绝是否可解释 |
| Reliability | ownership、epoch、atomicity、cancel、cleanup、fault boundary 是否确定 |
| Evolution | capability 可局部替换，public/generated semantics 和 compatibility 不漂移 |
| Delivery | guides、Examples、external consumer、benchmark、package、support boundary 是否一致 |
| Operability | stats、explain、resource identity 和错误上下文是否可行动且不泄漏 payload |

产品形态完整与 public release readiness 是两个不同判断。G6 外部事实不足时继续
保持 blocked，但不能因此降低产品架构、开发体验或 evidence 目标。

## 5. 目标锁与权威顺序

执行中的权威顺序为：

```text
用户明确决定 + 正式 Blueprint
  -> 正式 Design
  -> 已接受的本专题治理决策
  -> 技术 evidence
  -> 当前实现事实
```

Evidence 决定“怎样实现、支持到什么已验证 profile”，不能授权 Agent 静默改变
“产品最终应该是什么”。若目标在当前条件下无法实现：

1. 保留原目标；
2. 记录证据、影响和候选；
3. 写入 Conformance 或 Owner Decision；
4. 继续全部不受影响的工作；
5. 只有用户可以降低、删除或延期正式目标。

以下行为视为 scope shrinkage：

- 因当前代码难改而反向修改 Blueprint/Design；
- 把 required 改为 future、MVP、optional 或 unsupported；
- 用临时 public API、generic fallback、test-only bypass 或 future migration 冒充闭环；
- 缩小 workload、Schema、String profile、双表条件或阈值制造通过；
- 只完成容易部分便宣布整个专题完成。

受约束的 100M profile 和确定性 resource rejection 是已明确的产品边界，不是缩水；
但 narrow Lab 通过不能被表述为任意 Schema、任意 String 或 SOMA production
readiness。

## 6. Goal Traceability

Goal 开始时必须建立并持续更新以下矩阵：

| 字段 | 含义 |
|---|---|
| Goal ID | 稳定目标编号 |
| Product Value | 服务的用户旅程或产品质量 |
| Blueprint / Design Owner | 唯一长期事实 Owner |
| Evidence | TV、contract、differential、benchmark 或 application |
| Implementation | production/generated/internal path |
| Validation | test、external consumer、Gate |
| Example | 三个应用中自然证明该能力的 Owner，或 N/A |
| Status | pending / in-progress / satisfied / conformance-gap / blocked |

任何工作若不能映射到 Goal ID、正式差距或完成条件，不进入当前关键路径。

## 7. 长任务防偏航与防打转协议

- 同时只允许一个主要阶段处于 `in-progress`；
- 每项 Research 先写问题、候选、指标、停止条件和将改变的设计决策；
- TV0–TV8 的 inconclusive 项优先转成 conservative formula、internal parameter 或
  bounded claim，不自动开启新 TV；本次唯一预先批准的新增验证是 TV9；
- 每个问题原则上一个主要实验加一次独立确认；最多三个候选；
- 到达 decision point 即停止，噪声内记为 `inconclusive`；
- 相同输入且已通过的重型验证不重复运行；
- 同一失败原因不原样进行第三次尝试；
- 实施迭代运行窄验证，全量 Gate 只在阶段出口、重大集成点和最终收口运行；
- 不做装饰性重构、命名漫游、无界 benchmark 调优或当前 Goal 不需要的 future
  extension；
- 旁支问题记录到 backlog/Conformance，不离开关键路径；
- 每阶段关闭时立即记录 decision、evidence、remaining gap、scope non-regression
  和本地 checkpoint commit；
- 后续证据没有产生真实矛盾时，不重新打开已关闭阶段；
- 不以节省 token、时间、代码改动或实现难度为理由降低目标。

## 8. 实施阶段

```text
P0 live baseline / dirty-work attribution
  -> P1 Temporary、Goal Traceability 与 TV9 preregistration
  -> P2 standalone TV9
  -> P3 evidence synthesis + Owner Decision Matrix update
  -> P4 product/narrative/capability/metadata integrated final design
  -> P5 independent design and scope audit
  -> P6 accepted design freeze + formal Owner promotion manifest
  -> P7 code/test/doc/evidence disposition
  -> P8 production implementation by semantic slice
  -> P9 three-Example audit and necessary governance
  -> P10 full production-shape qualification + final evidence audit
  -> P11 atomic formal promotion, final Gate, Lab/Temporary deletion
```

不得在 P4/P5 设计闭合前开始大规模 production refactor；不得等到 P10 才第一次
核对 public/generated compatibility 或 Example consumer。

P6 只冻结经过独立审计的 accepted design，并形成逐项迁入唯一正式 Owner 的
promotion manifest；不提前把尚未由 production、test 和 evidence 证明的中间状态
写入正式 Design。P11 按文档治理要求在一次 closeout 变更中执行：

```text
promote long-lived facts to formal Owners
  -> refresh Implementation Map / Conformance / Reports / Guides
  -> validate references and all applicable Gates
  -> delete Temporary and the independent Lab
```

## 9. 三个 Example 的后置治理

Core Design、public/generated contract 和 production implementation 稳定后，审计：

- `industrial-dynamic-scheduler`；
- `grassing-individual-simulation`；
- `real-time-dispatch-rule-engine`。

审计检查：

- 只消费 public/generated API，不依赖 internal protocol；
- 从各自 business model 自然选择 Access/Transformation/DataFlow；
- Group/Table/child ownership、lifecycle、resource budget 和 release 正确；
- Eager Detached 默认；callback streaming 只在确有产品价值且 TV9/Design 允许时使用；
- hot path 不使用 DTO、Collection graph、Java Stream、reflection 或 arbitrary object；
- 三个应用不共享领域 JAR、Schema、fixture 或 baseline；
- docs、correctness、performance 和 canonical journey 与最终产品叙事一致。

现有正式 Conformance 已认为三个 Example 一致且 evidenced，因此必须先审计再决定。
若没有新设计偏差，记录 `RETAIN / no change`；不得为了展示新 Capability 做装饰性
改写。若有偏差，则完成必要治理和各应用独立验证。

## 10. 完成定义

只有以下条件全部满足，Goal 才能完成：

- TV9 已得到 accepted/rejected/inconclusive，并更新 Result Delivery 决策；
- canonical product narrative、核心抽象、Metadata、Capability、Result Delivery、
  scale/resource、parallel 和 module/API/compatibility 设计已闭合；
- 所有 accepted Lab 方向经过 Owner adoption，不直接复制实验类；
- production、generated contract、tests、benchmark、docs 已按最终设计完成；
- code/test 删除具有 replacement closure，没有目标能力或 evidence domain 缩水；
- 三个 Example 已审计，必要治理和独立 Gate 已完成；
- production-shape Small/Medium、single/double 100M bounded profiles、String、
  allocation/GC/failure/compatibility/external consumer evidence 已形成；
- `./scripts/check.sh`、docs check、diff check 和全部适用 Gate 通过；
- Blueprint/Design/Implementation Map/Conformance/Engineering/Reports/Guides 一致；
- G6 与性能 claim 保持真实边界；
- 正式 facts/evidence 已自包含，不依赖 Lab 或 Temporary；
- 独立 Lab 与本专题全部 Temporary 已按授权删除；
- 最终 Governance Report 明确回答目标是否缩水、产品形态达到何种成熟度及剩余风险。
