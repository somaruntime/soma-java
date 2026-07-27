# SOMA 系统设计、核心抽象与叙事再审视

类型：Temporary

状态：active（TV0–TV8 evidence 已就绪；Result Delivery TV9 待执行）

Owner：SOMA system design、core abstraction 与 narrative governance

正式事实源：否

实施授权：仅限 Temporary 文档收口和对 SOMA 的只读审计；不授权修改正式
Blueprint/Design、production、test、benchmark、public/generated contract 或构建

事实范围：本专题确认的设计再审视意图、产品目标形态、审查维度、候选系统叙事、
V1 类型与存储边界、Capability Model、Result Delivery、抽象裁决方法、技术验证
输入、三个 Example 后置审计与最终设计闭环

非事实范围：已接受的新系统架构、Metadata 精确 API、现有抽象的删除结论、正式
Design 修改、production 实施或 readiness

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

技术输入：[Scale Architecture 技术假设与验证协议](scale-architecture-technical-validation.md)

配套治理：[代码与测试规模治理](code-and-test-scale-governance.md)

能力治理：[Capability Model 与 Result Delivery](capability-and-result-delivery-governance.md)

最后审查日期：2026-07-28

产品与执行治理：[产品目标形态与长任务执行](productization-and-goal-execution-governance.md)

当前事实复核基线：`6cdc34f673c7bead208173e13df913e7c0a719fe`

## 1. 治理意图

本专题借 runtime boundary、Metadata、Group 和 Scale Readiness 治理，重新确认：

> SOMA 应该如何被理解、如何工作，以及每个核心抽象为什么存在。

这不是默认现有设计错误，也不是以新命名、新分层或代码重写制造“架构升级”。当前
Blueprint、Design、Implementation Map、Conformance、Code/Test 和 Report 继续拥有
各自正式事实；本文件只建立 evidence 驱动的候选审查框架。

目标是让最终设计同时具备：

- 一个可以从用户问题讲到 runtime release 的 canonical system narrative；
- 与该叙事一致的核心抽象、Owner、lifecycle、invariant 和 Access Pattern；
- 清晰的数据面、Metadata 控制面、执行面和观测面；
- 明确区分 primitive-backed scalar、reference-backed immutable scalar、
  compiler-flattened value 与 owned structured state；
- 一个封闭、compiler-bound、可在 operation boundary 选择物理实现的 Capability
  Model，不建设开放插件系统；
- Eager Detached 默认与 callback-scoped streaming 受限试点的 Result Delivery
  边界；
- 简单路径低心智负担，高级路径显式暴露成本与责任；
- 编译期生成、运行时机械实现和 application orchestration 的稳定边界；
- 可以解释代码、测试、性能证据和模块职责为什么存在的完整闭环；
- 从产品使用者旅程判断默认 API、诊断、资源、兼容、文档和 Example 是否共同形成
  一个优秀产品。

## 2. 顺序与前置边界

最终设计依赖技术验证结论，因此执行顺序为：

```text
freeze intent / invariants / validation questions
  -> standalone technical validation
  -> Technical Validation Report + Owner decision
  -> system narrative and abstraction finalization
  -> impact / compatibility / migration design
  -> authorized implementation
  -> scope non-regression and formal promotion
```

技术验证前只冻结问题、语义不变量、候选和判定标准，不提前固定物理答案。技术验证
也不得通过 benchmark 反向定义产品语义；任何候选必须保持已经声明的 ownership、
lineage、order、lifecycle、failure、determinism 和 application boundary。

TV0–TV8 已完成，后续设计消费的 evidence 入口为独立 Lab 的 Technical Validation
Report、Owner Decision Matrix 和本 Temporary 已转移的摘要。Result Delivery 决策
在 TV0–TV8 收口后发生变化，因此 integrated final design 前还必须完成 TV9。尤其
需要保持三条边界：

- reference-backed String 在明确 profile/resource bounds 下已通过机械验证，但
  不能外推为 arbitrary object 或任意 String 100M；
- Lab 接受的是 mechanics/cost direction，不是 SOMA public API、默认阈值、
  production integration、Gate 或 readiness。
- TV9 只比较 Eager Detached 与 callback-scoped streaming，不重新打开 ordinary
  Iterator、closeable pull cursor、Publisher 或 mutation/effect streaming。

## 3. 候选 Canonical SOMA Narrative

以下是待审查的叙事骨架，不是已经接受的正式设计：

```text
Application declares Annotation Schema
  -> processor validates and normalizes schema
  -> compiler classifies four closed V1 storage categories
  -> generated Descriptor and typed facade
  -> application configures compatible Plan
  -> create/bind validates and freezes Effective Metadata
  -> SomaGroup / generated Table publishes runtime facts
  -> maintained Point / Candidate / Column / Key / Bulk / Ownership access
  -> Transformation Definition / Template describes finite computation
  -> Invocation binds current sources and parameters
  -> acquire / preflight / physical selection / resource admission
  -> sequential or bounded parallel generated kernel
  -> Result Delivery selects default Eager Detached
     or explicit callback-scoped read-only streaming
  -> complete Result / Effect publishes once, or scoped callback closes
  -> immutable Observation / Explain
  -> operation, Table and Group release
```

最终叙事必须说明：

- 每一步由谁创建和拥有；
- 输入事实、派生信息和输出分别是什么；
- 哪一步可以配置、何时冻结、何时失效；
- 失败、取消、预算不足和 cleanup 分别停止在哪里；
- 哪些步骤位于 hot path，哪些只发生在 create/bind/terminal/observation boundary；
- simple path 如何避免承担 advanced path 的抽象税。

### 3.1 V1 类型与对象边界

候选 canonical narrative 必须明确：SOMA 的 schema type system 不是 JVM reference
capability 的镜像。V1 只有四类 storage semantics：

- primitive-backed scalar：primitive、enum、date/time 和显式 semantic scalar；
- reference-backed immutable scalar：白名单仅 `String`；
- compiler-flattened value：`@SomaValue` 展开为 canonical leaf columns；
- owned structured state：parent-owned child Table，不保存 live Collection graph。

String 保存 caller reference，但以稳定 immutable value semantics 参与
Key/Unique/Index、Group/Join、order、mutation 和 materialization。它不授权任意 Java
对象成为 schema field，也不引入 dictionary、字符 arena、intern 或 normalization。
需要关联 application object 时，Table 保存稳定 ID，application sidecar/registry
拥有对象、alias、mutation 和 lifecycle。

该分类属于 compiler/Descriptor/Layout 的 cold fact；generated hot path 只消费已经
绑定的 typed column/ordinal/access mechanics，不能通过 `Object`、reflection、
runtime registry 或 marker interface 动态判断。

## 4. 六个审查视角

### 4.1 产品与使用者

- SOMA 的一句话定位、目标用户、目标问题和非目标是否一致；
- Schema → compile diagnostics → generated facade → Metadata Plan → create → load
  → access/transform → result delivery → observe → release/upgrade 是否自然；
- 默认路径是否简单，Plan、budget、parallel 和 diagnostics 是否按需展开；
- Application 与 SOMA 的 orchestration、transaction、I/O 和 recovery 边界是否清楚；
- abstraction、API、diagnostics、Guide、Example、benchmark 和 support claim 是否
  讲述同一个产品；
- 当前实现困难或 narrow Lab evidence 是否被错误用于降低正式产品目标。

### 4.2 语义与信息

- runtime facts、Metadata、derived state、Candidate、Result 和 Observation 是否区分；
- String reference、flattened value leaf、child handle 与 arbitrary object 是否有
  不可混淆的 schema/storage 分类；
- Shape、cardinality、lineage、order、absence、identity 和 version 是否只有一个
  canonical 解释；
- Point/Candidate/Column/Key/Bulk/Ownership 是否各自服务真实 Access Pattern；
- specialized fast path 是否仍共享同一 correctness model。

### 4.3 Ownership、Lifecycle 与 Failure

- Schema、Group、Table、owned child、Definition、Template、Invocation、Cursor、
  Result、Plan 和 Metadata 的 Owner 是否唯一；
- create、bind、acquire、preflight、execute、commit/publish、release 是否闭合；
- current Index、borrow、snapshot、detached result 和 observation 的失效边界是否明确；
- failure atomicity、safe point、cancellation 和 cleanup 是否由正确 Owner 关闭。

### 4.4 编译、生成与 Runtime

- processor 应生成哪些 descriptor、ordinal、layout 和 typed capability；
- generated facade 与 generated-runtime internal protocol 如何分责；
- runtime-core 是否保持 schema-agnostic mechanical primitive；
- dataflow 是否只拥有 Transformation/DataFlow execution，不吸收 live storage 或
  application control flow；
- Metadata 是否在 bind 前完成解释，hot path 是否只消费 specialized primitive
  representation。

### 4.5 资源与性能

- retained storage、growth/rehash transient、scratch、worker、task、output 和
  JVM/GC headroom 是否采用同一成本叙事；
- String 是否分别报告 SOMA-owned structural bytes、SOMA-retained reachable String
  bytes 与 JVM observed heap，而不是用 reference slots 冒充完整 retained heap；
- Small/Medium 固定税与 Large/`100M × 100M` bytes moved 是否同时受约束；
- Segment、Morsel、Execution Block、Candidate shape 和 access path 是否职责正交；
- Explain 是否能说明 physical choice、budget 和 fallback，而不引入 runtime
  wall-clock self-tuning。

### 4.6 Contract 与 Evidence

- public/generated API 是否只投影稳定语义，不泄漏内部物理结构；
- compatibility、schema hash、plan identity 和 protocol 是否各有唯一 Owner；
- tests、golden、external consumer、differential、application 和 benchmark 是否
  分别证明独立问题；
- Design、Implementation Map、Conformance、Report 和 Temporary 是否没有平行事实。

## 5. 核心抽象审查

优先审查：

- `SomaMetadata`、Descriptor、Plan、Effective、Observation；
- 四类 V1 schema/storage kind、String whitelist 与 application stable-ID sidecar
  boundary；
- `SomaGroup`、root Table、owned child 和 ownership aggregate；
- Schema/Metadata、Storage、Access、Mutation、Relation、Transformation、Execution、
  Result Delivery、Resource、Observation Capability；
- Point、Candidate、Column、Key、Bulk、Ownership；
- Candidate、Transformation Shape、Definition、Template、Invocation；
- current Index、`IndexSnapshot`、`IndexBuffer`；
- Result、Effect、Borrow、Materialization；
- Storage Segment、Parallel Morsel、Execution Block；
- resource budget、stats、explain、version、epoch 和 lifecycle。

每个抽象必须回答：

| 维度 | 问题 |
|---|---|
| Design Intent | 为什么需要它，服务哪个目标场景 |
| Semantic Role | 它表达什么，明确不表达什么 |
| Owner | 谁产生并维护权威事实 |
| Lifecycle | 何时创建、冻结、失效和释放 |
| Invariant | 哪些条件必须构造即正确 |
| Access Pattern | 谁以什么频率和成本消费 |
| Boundary | 为什么不能与相邻抽象合并 |
| Evidence | 哪个代码、测试、应用或报告证明 |
| Decision | 保留、澄清、重设计、待验证或越界 |

不能用单一实现、单一 caller、方法长度、调用深度、类数或 LOC 独立决定抽象去留。

## 6. 裁决语言

每项发现只进入以下一种状态：

```text
RETAIN
CLARIFY
REDESIGN_CANDIDATE
NEEDS_TECHNICAL_VALIDATION
OUT_OF_SCOPE
```

- `RETAIN`：设计和责任正确，记录理由与防回归边界；
- `CLARIFY`：语义正确，但术语、叙事、Owner 或生命周期需要收口；
- `REDESIGN_CANDIDATE`：存在结构性设计问题，等待 Owner 和详细设计；
- `NEEDS_TECHNICAL_VALIDATION`：缺少机械、成本或规模 evidence；
- `OUT_OF_SCOPE`：与本治理目标无关。

独立 Lab 只接收 `NEEDS_TECHNICAL_VALIDATION`。Lab 结论仍需回到本文件完成 Owner
裁决，不能直接修改正式 Design 或 production。

## 7. 最终设计应回答的问题

技术验证完成后，最终设计至少需要关闭：

1. SOMA 的 canonical mental model 和 end-to-end narrative；
2. 四类 V1 type/storage semantics、String whitelist、arbitrary-object rejection 与
   stable-ID sidecar boundary；
3. 完整 Metadata scope、分层、修改与冻结边界；
4. SomaGroup、Table、ownership aggregate 和 version/lifecycle；
5. Access、Transformation 和 DataFlow 的组合与非等同边界；
6. storage、index、candidate、scratch、terminal 和 parallel 物理策略；
7. public/generated API、generated-runtime protocol 和 module dependency；
8. Eager Detached 默认、callback-scoped streaming 试点、Materialization、Borrow
   和 Effect 的非等同边界；
9. failure、resource admission、publication/cleanup、stats 和 explain；
10. 产品 developer journey、默认体验、diagnostics、Guide 与 Example acceptance；
11. compatibility/migration、Implementation Map、Conformance 和 validation impact；
12. 与最终设计不再一致的 code/test/doc/evidence 的替代和删除闭环。

## 8. 停止条件

- 没有 evidence 时不固定 Segment/Morsel/Block、locator、candidate threshold 等参数；
- 不为追求统一恢复 generic query engine、reflection 或 Metadata interpreter；
- 不把 Capability Model 扩张为开放 SPI，或把 interface dispatch 留在逐 row hot path；
- 不在 TV9 前固定 callback streaming public signature，也不把它扩张为 Iterator、
  closeable cursor、Publisher、mutation 或 Effect；
- 不把 G6 发布事实、MES 同步、跨 Table transaction 或 application workflow 纳入；
- 不用当前实现反向降低 Blueprint，也不为减少代码删除目标能力；
- 正式 Design 修改、public/generated contract 和 production 实施前必须获得新的
  Owner 授权。

本文件只在 accepted decisions 原子固化、代码/测试替代闭环、全部适用 evidence 和
scope non-regression 通过后随上位 Temporary 一并删除。
