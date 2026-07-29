# SOMA V1 Release 治理交接

类型：Temporary

状态：active（等待下一轮 V1 release 治理统一收口）

Owner：SOMA Java 当前治理收口与 V1 release transition

事实范围：本 Codex 线程确认的治理意图、稳定认知与工程方法、关键裁决、当前
production/evidence candidate、计划对照、审计结论、未闭合事项和下一轮治理入口

非事实范围：重新定义正式 Blueprint/Design、授权 release、扩大支持矩阵、声明
public/Maven readiness，或替代 Conformance、Report 和可执行 Gate 的当前事实

最后审查日期：2026-07-29

## 1. 临时性质与使用方式

本文件是一次性治理交接，不是新的 Design、Conformance、Roadmap 或 release
事实源。它保留本 Codex 长线程中下一轮治理仍需使用、但尚未全部原子收口的上下文，
避免新线程只看到当前代码而丢失目标、稳定方法、裁决理由和审计发现。

下一轮 V1 release 治理必须：

1. 先以当前 HEAD、正式 Owner、代码、Gate 和 artifact 重新核实本文件中的
   drift-prone 状态；
2. 将仍然成立的稳定事实原子固化到唯一 Blueprint/Design/Engineering/
   Conformance/Report Owner；
3. 完成这里登记的当前治理尾项和 selected release profile 的 Definition of Done；
4. 删除整个 `docs/temp/soma-v1-release-transition-handoff/`，不得归档、改名为
   historical 或长期作为 current 入口。

## 2. 本线程的治理起点

本轮不是把 SOMA 重新定义为数据库，也不是补齐 SQL 功能。它以现代 SQL、
内存列式系统和编译式执行引擎为研究输入，重新审视 SOMA 的逻辑类型、约束、
Transformation、执行 lowering、物理结构、中间结果、并行与规模承诺，同时保持
SOMA 原有产品定义和核心抽象。

治理前确认的主要问题是：

- 原 100M 挑战不能只看单表，必须考虑两个同时驻留的 100M Table；同时不能让
  Small/Medium 为超大规模设计承担不可解释固定税；
- 前期 Research 对 Data Representation 的结论不能直接进入 production，还需结合
  SOMA Java 的中间结果、计算流程、JIT/cache/memory bandwidth/GC 与并行模型做
  独立技术验证；
- 实际 payload 与 Metadata 必须分离；完整 Metadata hierarchy 及
  mutable-before-freeze Plan 已成为此前治理的正式基础；
- String V1 继续保存 caller `String` reference，不引入 dictionary、character
  arena 或 intern backend；实际存储引用不等于支持任意 Java object；
- 普通 object graph、`List`、`Map` 和任意 DTO 不进入 live schema field；应用对象
  通过 SOMA 中的 stable ID 与 application sidecar/registry 关联；
- Eager Detached 保持完整、原子、易推理的默认 Result Delivery；最终只选择同步、
  read-only、callback-scoped delivery 作为惰性试点，不引入普通 `Iterator`、
  closeable cursor、Generator、Publisher 或 async push；
- Storage Segment 与 parallel work unit 必须分离；一个 Segment 也能按成本拆成
  多个 morsel，但不建立两套 executor/并行框架；
- runtime 可以理解为一组封闭 Capability，由接口、泛型和编译期/generated binding
  隔离实现，以便未来局部替换；V1 不开放 application strategy SPI；
- 代码、测试、benchmark、脚本和文档都应围绕核心抽象、Capability 与产品叙事，
  控制规模并退出治理批次、迁移入口和无 Owner 的噪声。

## 3. 关键认知模型

### 3.1 产品级关系

本线程确认的总体关系是：

```text
产品叙事
  -> 核心抽象：State / Owner + Capability + Plan / Lifecycle
  -> 每项 Capability 的三层展开：
       Logical semantics
         -> Java carrier + generated type-safe capability
         -> JVM / OS / CPU-aware physical strategy
  -> Evidence
  -> Conformance / Product Claim
```

四部分不处于同一抽象层：

- 产品叙事回答 SOMA 为什么存在、为谁解决什么问题；
- 核心抽象回答系统由什么长期概念构成、谁拥有事实与生命周期；
- 三层模型回答每项能力怎样从易用、类型安全的逻辑契约降低为 Java 8 和物理执行；
- Evidence 是横切验真层，不是运行时中的第四类业务抽象。

### 3.2 每个抽象自己的叙事

产品叙事不是唯一叙事。每个核心抽象都必须拥有自己的叙事和叙事展开，至少回答：

1. `Why`：为什么需要这个抽象，它解决什么系统矛盾；
2. `Owns`：它拥有哪些事实、能力和不变量；
3. `Not`：它明确不是什么，不能冒充哪些相邻抽象；
4. `Relationships`：它与哪些抽象组合，依赖方向是什么；
5. `Lifecycle`：怎样创建、freeze、bind、使用、失败和释放；
6. `Lowering`：其 logical semantics、Java carrier/generated capability 与
   physical strategy 分别是什么；
7. `Resource / Failure`：资源和失败后可信状态由谁闭合；
8. `Evidence`：哪些 contract、differential、consumer、benchmark 或 qualification
   才能证明它成立；
9. `Evolution`：未来怎样局部替换实现而不破坏语义与叙事。

当前正式文档已经建立抽象层次、唯一 Owner、事实/非事实范围、canonical terminology
和大量能力契约，但尚未把“每个抽象必须有完整叙事闭环”明确为共同治理规则。
下一轮应在现有设计宪法和 Design 导航中做窄修正，并复核既有 Owner 的开篇叙事；
不得为每个抽象另建一套平行文档。

## 4. 下一线程必须继承的稳定认知、哲学与方法

本章不是新增的正式 Design，也不把聊天意见提升为当前产品事实。它提炼本线程和
既有工程实践中跨专题仍然成立的思考方式，帮助下一线程理解“为什么这样治理”和
“遇到新情况怎样判断”。具体能力、代码位置、性能数字、Gate、环境和 release
状态仍必须由当前 HEAD、正式 Owner 与可执行 evidence 决定。

### 4.1 北极星：做成一个可理解、可使用、可验证的产品

本次以及下一轮治理的最高价值，不是再增加一批能力，而是：

> 重新确认 SOMA 应该如何被理解、如何工作，以及每个核心抽象为什么存在。

“把 SOMA 产品做到最好”不等于功能最多、抽象最多、单项 benchmark 数字最高，
而是让以下内容形成同一个可追踪系统：

```text
真实用户问题与产品价值
  -> 一致的产品叙事和心智模型
  -> 必要且边界清楚的核心抽象
  -> 易用、类型安全的逻辑语义
  -> 可预测的 ownership / lifecycle / failure / resource 行为
  -> Java 8 与物理机器上的高性能执行
  -> 可以复现且不过度外推的 Evidence
  -> README、Guide、Example、诊断、支持与 release claim
```

易用性主要由逻辑层、生成 API、错误模型和用户旅程决定；高性能主要由 Java
carrier、specialized kernel、数据布局、计算流程以及 JVM/OS/CPU 适配决定；
正确性、ownership、lifecycle、资源边界和 Evidence 贯穿两者。不能以牺牲其中一端
来伪造另一端的成功。

产品化还意味着：用户能快速开始、能判断适用边界、失败时知道发生了什么、资源
消耗可以解释、版本和支持范围可信、Example 展示推荐路径、发布声明与证据完全
一致。代码实现只是产品的一部分。

### 4.2 统一的设计推理链

新的设计、治理或瘦身工作都应沿以下链路推理：

```text
现实问题 / 用户价值
  -> Design Intent
  -> 语义投影与模型
  -> 核心抽象与 Architecture
  -> Narrative / State Transition
  -> Information Demand
  -> Access Pattern
  -> Data Representation / Runtime Execution
  -> Evidence / Product Claim
  -> Replacement / Closure
```

这条链路中的每一步都有证明义务：

- 先说清楚要解决的真实矛盾，而不是从某个数据结构、SQL 特性或现有类开始；
- 语义和模型决定抽象，抽象不能由当前实现形状或熟悉的设计模式反向发明；
- Narrative 不是营销文案，而是一个抽象从输入、状态变化、组合、失败到输出的
  因果解释；
- Information Demand 和 Access Pattern 决定表示与执行策略，表面结构相似不代表
  应使用同一种实现；
- Evidence 只证明明确声明的语义、性能和资源边界，不能由“测试是绿的”替代；
- 新实现若替换旧实现，必须同时关闭 predecessor、旧入口、旧事实源、迁移检查和
  claim，不把清理由未来任务承担。

编码前至少要能复述：目标、范围、当前事实、核心抽象及其叙事、Owner、生命周期、
不变量、访问模式、失败与资源边界、验证方式和退出条件。不能回答时，先研究或建立
有边界的 Temporary/TV，不直接扩张 production surface。

### 4.3 产品叙事、抽象叙事和三层模型的关系

产品叙事与每个抽象的叙事是横向的“意义和因果链”；Logical → Java carrier →
Physical 是纵向的“实现 lowering”。二者同时存在：

- 产品叙事约束哪些抽象值得存在；
- 抽象叙事约束一项 Capability 拥有什么、怎样组合和演进；
- 三层 lowering 防止把 public 逻辑类型、Java 承载形式和某个物理算法混为一谈；
- Evidence 横切验证每个叙事承诺及其 lowering 是否成立。

因此，现代关系系统只能提供设计启发，不能取代 SOMA 原有的抽象与叙事；某个
primitive carrier、Hash/Bitmap 算法或 SQL 术语也不能成为新的产品定义。

### 4.4 实现服务于设计，而不是设计服从现状

权威方向始终是 Blueprint → Design → Implementation。当前代码可以暴露物理约束、
缺陷和新证据，但不能因为“已经这样实现”就静默降低目标或改写长期语义。

同样，代码治理不能从 LOC、文件数、重复片段、单实现、单调用者或浅层 unused
scan 直接得出删除/合并结论。这些只能作为调查信号。对可疑 surface 应先判断：

- `ACTIVE`：当前目标和 consumer 正在使用；
- `SUPERSEDED`：已有完整 replacement，可以在同一变更退出；
- `LATENT_REQUIRED`：当前调用少，但承担已确认的最终语义或边界；
- `DEFERRED`：正式目标仍需要，但当前尚未实施；
- `COMPATIBILITY`：明确承担版本或 consumer 兼容；
- `ABANDONED`：已裁决不再需要；
- `UNKNOWN`：尚无足够证据，不得贸然删除或长期搁置。

任何重构或瘦身都应证明：Design intent 未缩水、完整调用链已追踪、不变量和
consumer 未破坏、Evidence 有替代、旧入口能够同步退出。反过来，没有独立语义、
失败域、consumer、生命周期和 Evidence 的推测性抽象，也不应因“未来可能有用”
而保留。

### 4.5 从信息需求选择表示与执行

Data Representation 不是设计起点。推荐推理顺序是：

```text
Behavior / Algorithm
  -> Information Demand
  -> Access Pattern
  -> Representation
```

同时受 Ownership、Lifecycle、Invariant、Boundary、resource budget 和 Evidence
约束。对 SOMA，应先识别 table 的生命周期角色、identity、ownership 和真正 hot
loop，再选择 keyed/dense、column、exact access、candidate、bitmap、scratch、
intermediate 或 result representation；不能把某个领域中成功的模式机械套到所有
Table。

性能也不是只看 payload 布局。必须计算端到端成本，包括：

- 输入选择、predicate/project/aggregate/join 的融合程度；
- candidate、selection、bitmap、index 与中间结果是否有界；
- barrier 和 materialization 的数量、shape 与生命周期；
- per-row dispatch、boxing、branch、allocation 和 JIT inline 条件；
- cache locality、false sharing、worker-local scratch、merge 和 memory bandwidth；
- mutation 维护、clear/release、retained bytes 与 GC；
- Small/Medium 的固定税以及 sequential/parallel crossover。

优化不能只是把成本从读取转移到 mutation、从 heap allocation 转移到 retained
scratch、从一次 operation 转移到 rebuild，或从 SOMA ledger 转移到 JVM
observed heap。

### 4.6 事实、Owner、派生结构与观察必须分开

保持低心智负担的关键不是把所有内容叫作 Metadata，而是让不同性质的信息有唯一
Owner 和明确生命周期：

- live Table storage 拥有当前 payload、membership、identity 与 ownership facts；
- Descriptor、mutable-before-freeze Plan 和 Effective/Runtime Metadata 是控制面，
  描述 schema、策略、预算和已生效配置，不保存 live payload 或工作集；
- Index、Bitmap、locator、stats 和 filter 是由 authoritative state 派生的访问或
  执行结构，必须有 currentness、epoch、mutation、clear/release 和 fallback 规则；
- Explain、diagnostics、result 和 metrics 是有生命周期的 Observation，不成为
  runtime 的平行控制面；
- Test、benchmark、qualification 和 Report 是证据或特定时点结论，不反向拥有
  Design 或 live facts。

“一个事实一个 Owner”不等于所有信息只能放在一个类中。应按语义、更新权限和
生命周期分层，但不得维护两个都声称 authoritative 的来源。Group、root ownership
aggregate 和 parent-owned child 的边界也决定 mutation、concurrency、failure 与
release 的闭合范围；SOMA 不因组合多个 Table 而获得跨 root transaction。

### 4.7 Capability 是稳定责任单元，不是接口数量目标

把 runtime 理解为一组可局部替换的 Capability 是正确且值得保留的方向，但
Capability 首先是一项稳定的语义责任、失败域、生命周期和 Evidence 单元，不必
机械对应一个 public interface、一个 class 或一个 module。

接口、泛型、generated protocol 和内部策略可以帮助隔离实现；只有当新 surface
拥有独立语义、consumer、lifecycle、failure/resource boundary 与验证责任时，
才值得成为新类型或 Owner。否则应由现有 Capability 承载。

V1 继续采用封闭 Capability 集和 compiler/boundary binding：

- application 使用 schema-specific、type-safe generated capability；
- 内部实现可以替换，但不能形成第二 correctness model；
- binding 只发生在 compile、plan/create、bind 或 operation boundary；
- hot loop 保持 specialized，不能变成 metadata interpretation、reflection、
  ServiceLoader 或 per-row polymorphic dispatch；
- 不为了“可扩展”开放 application strategy SPI。

局部可替换性的目的，是让未来更好的设计或技术能够替换物理策略而不破坏逻辑
语义、Owner 和用户心智模型，不是把所有实现抽象成插件。

### 4.8 类型体系是逻辑承诺，不是 Java 存储类型清单

SOMA V1 的字段认知应保持四类：

1. primitive-backed scalar：primitive、enum、date/time/instant 和显式 semantic
   scalar，以 primitive column 承载；
2. reference-backed immutable scalar：V1 仅白名单 `String`，保存 caller object
   reference；
3. compiler-flattened value：`@SomaValue` 编译期展开为多个 column，不保存 value
   object reference；
4. owned structured state：通过 parent-owned child Table 表达，不保存 `List`、
   `Map` 或任意 object graph。

逻辑类型目录和允许操作矩阵属于 logical layer；`int`、`long` 或 shared expression
carrier 属于 Java 承载；column、kernel、index 或 filter 属于 physical layer。
因此不引入 `SomaInt`/`SomaLong` wrapper 来制造名义类型，也不能因内部共享
`long` carrier 就让 enum、date/time 和普通 numeric 暴露相同运算。

String reference-backed baseline 是有意的 V1 取舍：String 可以在 row mutation
时替换为不同对象和不同长度；长度、cardinality、sharing、field role 与 live Table
count 是 resource estimate 和 qualification profile，不是固定长度 Schema 约束。
任意可变 Java object 会绕过 SOMA 的 mutation、epoch、Index/Unique、ownership、
concurrency 和 materialization 边界，必须通过 stable ID 与 application sidecar/
registry 关联。

### 4.9 正确性、失败和资源边界必须在同一设计中闭合

“构造即正确”和“失败后仍可推理”优先于局部便利：

- 能在编译期、normalization、freeze 或 bind 阶段关闭的错误，不推迟到 hot loop；
- expected failure 不得发布部分 storage、locator、ownership、epoch 或 result；
- internal invariant violation 必须 fail fast，并提供结构化诊断；
- resource budget 在会产生巨量 intermediate/materialization 前 preflight，不把
  `OutOfMemoryError` 当正常控制流；
- one-shot Invocation 拥有 guards、scratch、parallel worker state 和 cleanup；
- controlled effect 只在确定的 safe point 提交；
- clear/release/GC evidence 与 create/mutation 性能同等重要。

Eager Detached 保持默认，是因为它提供完整、原子、易推理的 result contract；
callback-scoped delivery 只在同步、read-only、one-shot 和 bounded lifecycle
范围内降低输出峰值。惰性不能成为逃避资源模型、泄漏 live state 或产生 partial
commit 的通用机制。

### 4.10 Evidence 服务于决策和声明，不服务于仪式

Evidence 的价值在于回答明确问题：

- contract/golden/negative compile 证明 public/generated 语义；
- differential/reference oracle 证明 specialized fast path 没有形成第二语义；
- external consumer 证明普通 Java 8 项目可以真实使用 artifact；
- reference application 证明核心能力可以组成完整用户旅程；
- component benchmark 识别策略适用域和回归；
- qualification 证明特定 commit、artifact、JDK、OS/architecture、heap、profile
  和 workload 下的产品 claim；
- Conformance 记录目标、Design、实现与 Evidence 的真实偏差。

测试、benchmark、脚本和文档应按核心抽象、Capability、cross-capability journey、
external consumer、reference application 与产品 claim 组织，而不是按治理阶段、
TV 编号或历史批次永久分类。Smoke 不能冒充 qualification，本机结果不能外推到
未验证平台，row count 不能替代 String/shape/resource profile，某次 snapshot
也不能成为无期限支持声明。

已经通过且输入、假设、实现和证据目标未变化的高成本 Evidence 应直接复用。验证
范围按风险从窄到宽，只在真正的候选边界运行一次 Full/qualification。

### 4.11 技术验证必须有决策价值和退出条件

独立临时 Lab 的目的，是用最小 production 扰动回答高价值问题，不是提前构建另一
套 SOMA。每项 TV 应遵守：

- 一个 TV 只回答一个可裁决问题；
- 先建立可信 baseline，之后一次只改变一个关键变量；
- 每个方向默认最多比较三个有理论依据的候选；
- 预先声明 workload、环境、correctness oracle、收益阈值、资源上限和退出条件；
- 先用 Small/Medium 识别固定税和 crossover，只有 1M 仍不能回答问题时才扩大；
- 同时观察 latency/throughput、allocation、retained bytes、GC、mutation、
  clear/release 与并行资源，而非只选最好看的单一数字；
- 结果明确分类为 `accepted`、`narrowed`、`rejected` 或
  `condition-not-triggered`；
- 未达到阈值的方向停止，不做无边界参数调优；
- 裁决进入唯一正式 Owner 后删除 Lab 和 Temporary。

Research 只能产生候选与假设，不能直接授权 production 优化；TV 通过也不自动
授权 public/schema/runtime semantics 变化，仍需正式 Design 裁决。

### 4.12 治理强度与风险相称

治理的目标是降低产品风险和认知熵，不是制造文档、checker 和审批仪式。按变更
风险选择：

- 轻量：局部、可逆、无 public/Design/release claim 影响；
- 标准：跨文件或单 Capability，但边界清楚且有成熟模式；
- 严格：重大 Design、public/generated protocol、跨模块语义、performance/
  security qualification 或 release claim。

无论哪一级，都应把过程当作检查点：一个当前事实基线、一个清楚的接受矩阵、按
slice 的直接 Evidence、必要时一次整体 Full、一次完整自审和最终 closure。只有
发生实质变化才重复审查或高成本 Gate。不能把每次思考都固化成永久 artifact，也
不能用“流程轻量”掩盖真实风险。

Conformance、只读 audit 和 Research 只产生事实或候选，不扩大实施授权。修改
Blueprint、Design、public/schema/runtime semantics、最终 Gate、release claim、
发布渠道或执行 push/release 等外部动作，仍以用户明确授权和当前仓库规则为准。

### 4.13 仓库瘦身的目标是降低认知熵

“更少代码”不是独立目标；目标是让每个保留的 production type、API、test、
benchmark lane、fixture、script、文档和 Example 都能回答：

1. 它属于哪个产品叙事、核心抽象或 Capability；
2. 它拥有什么独立语义或 Evidence；
3. 谁是 consumer；
4. 谁拥有它，生命周期怎样结束；
5. 删除或替换它会破坏什么；
6. 为什么现有 Owner 不能承载。

治理批次、旧 phase、已完成 migration、历史路径 blacklist、tombstone、重复
Owner 和“以后再清理”的兼容入口不应长期留在 current checkout。历史由 Git
保存，正式文档只描述当前事实。Replacement 应在同一变更退出 predecessor、
旧入口、migration-only checker、重复测试与过期 claim。

瘦身也适用于 Evidence surface：

- 测试围绕语义不变量和用户旅程，不围绕实现类数量增长；
- benchmark 围绕产品 claim 和物理决策，不为每次实验永久加 lane；
- script 提供清晰的 fast/full/qualification 入口，不机械串联大量 Maven 进程；
- 文档由唯一 Owner 展开，不累计同一事实的多个故事版本；
- Example 是 reference consumer，不成为第二套 framework 或领域共享库。

专题 closeout 应确认 parallel Owner、migration artifact、未退役 Temporary 和
未裁决 `UNKNOWN` 为零；但不能为追求表面整洁而删除 `LATENT_REQUIRED` 或真实
Conformance。

### 4.14 当前事实、记忆和历史必须分级使用

记忆和本文件只用于路由与恢复意图；当前项目事实必须从 live repository 判断。
下一线程应区分：

- `STABLE PRINCIPLE`：跨时点仍成立的设计/工程原则；
- `CURRENT`：已由当前正式 Owner、代码或 Gate 核实的事实；
- `SNAPSHOT / DRIFT_PRONE`：commit、性能数字、环境、status、支持矩阵和 readiness；
- `SUPERSEDED`：已有新 Owner 或新裁决替代；
- `HISTORICAL`：只解释演进，不参与当前决策。

凡是 commit、branch、dirty state、artifact checksum、JDK/Maven/OS、Gate、
benchmark、remote、GitHub issue、release/support 状态，都应在使用前廉价复核。
不得把本线程早期的 Zulu、100M guarantee、旧 G5/G6、旧路径或旧 benchmark
结论带回 current narrative。

### 4.15 长任务是闭环控制，不是不断执行命令

可靠的长任务循环是：

```text
设计 -> 规划 -> 执行 -> 观察 -> 异常识别
  -> 核实诊断 -> 修复或重新规划 -> 验证 -> 收口
```

时间预算只是异常探测器，异常信号识别和处置才是根本。阶段数量异常膨胀、一次
日常检查触发几十次构建、远超基线、相同失败重复出现、轮询没有新状态、验证成本
与决策价值失衡时，应立即停止执行层惯性，先判断是正常慢、环境阻塞、产品/脚本
设计问题还是执行循环失控。

任务终止条件是已确认 Definition of Done 完整闭合，或存在需要新授权/外部变化的
真实 blocker；不能因时间、token 或困难自行缩小目标，也不能因“还可以再优化”
无限延长。下一轮的具体异常处置规则见本文件最后一章。

### 4.16 下一线程的变更前检查表

开始任何 production、Design、Evidence 或瘦身变更前，至少回答：

1. 这项工作解决哪个真实用户问题或 V1 product gap？
2. 它服务哪个 Blueprint、核心抽象和抽象叙事？
3. 当前事实来自哪个正式 Owner、代码、Gate 或 artifact？
4. 谁拥有 authoritative fact，哪些只是 Plan、derived structure 或 Observation？
5. lifecycle、mutation、failure、cleanup 和 concurrency 边界是什么？
6. logical semantics、Java carrier/generated capability 和 physical strategy 各是什么？
7. Information Demand、Access Pattern 和真正 hot path 是什么？
8. Small/Medium、1M、String、双表或并行中哪些 profile 真正适用？
9. correctness oracle、resource budget、Evidence 和 product claim 是什么？
10. 新 surface 是否有独立 consumer、failure domain、lifecycle 和 Evidence？
11. 被替换的 predecessor、旧入口、旧 Owner、旧测试和旧 claim 怎样在同一变更退出？
12. 哪些结论需要用户 Design/release 授权，哪些只属于当前实现 refinement？

如果这些问题尚不能回答，应把不确定性变成一个有边界的 Research、Temporary、
Conformance 或 TV 问题，而不是在 production 中用更多抽象掩盖。

## 5. 治理前 Research 与计划

治理前 Research 将方向分为三类。

### 5.1 直接吸收的设计原则

- logical type、Java carrier 与 physical representation 分层；
- 封闭 logical type catalog 和类型允许操作矩阵；
- required/optional/default、Key/Unique/Exact 与编译期合法性保持克制约束；
- logical/physical plan 不变量、pipeline/barrier、bounded intermediate；
- Explain 与物理选择可观察；
- Small/Medium、单表 1M、双表 1M + 1M 成为 V1 qualification；
- 10M/100M 降为 research/stress，不阻塞 V1。

### 5.2 明确不进入 V1

- SQL parser、DDL/DML/DQL 兼容层、事务、持久化和分布式执行；
- foreign key、table reference、cascade、trigger、跨表 CHECK；
- 任意 Java object/DTO/Collection graph；
- `SomaInt`、`SomaLong` 等 wrapper/type alias；
- 普通 Iterator/Stream、开放 runtime SPI、Java 16 Vector API；
- String dictionary/arena 和全套磁盘数据库索引。

### 5.3 必须先独立验证

- logical expression carrier 与 closed/fused loop；
- chunk/vector/selection representation；
- Segment min/max/zone map；
- 低基数 Bitmap；
- primitive Join min/max/Bloom dynamic filter；
- 中间结果与 barrier 前 materialization；
- 单 Segment 多 morsel、cache、worker-local state 和 merge；
- Decimal 仅在决定考虑纳入 V1 时触发验证。

执行流程按“Temporary 与独立 Lab → 高决策价值 TV → D1–D8 Design 裁决 →
production cutover → evidence/examples/Temporary 退出”推进。独立 Lab 只服务本专题，
治理完成后必须删除。

## 6. D1–D8 最终裁决与 production 结果

用户已明确授权 D1–D8 按推荐裁决更新正式 Design、Conformance、相关 Gate 并进入
production 实施。

| 决策 | 最终裁决与当前实现 |
|---|---|
| D1 logical type | enum/date/time/instant 使用 type-specific expression facade；内部共享 primitive carrier；不增加 wrapper/alias |
| D2 numeric kernel | 只对 required long column + constant arithmetic/comparison common chain 启用 closed whole-loop kernel；reference expression graph 保留 oracle/fallback |
| D3 Segment statistics | 拒绝 V1 mutable Segment min/max/zone-map Capability；没有 production type、配置或平行 Owner |
| D4 Bitmap | 只在公式许可的单字段 primitive exact equality intersection 中启用 maintained bitmap；exact hash/full equality 与 links 保持 authoritative/fallback |
| D5 Join filter | primitive 单分量 Join 可选 Invocation-local min/max 或 Bloom；最终 hash/full equality 不变；String 和 composite Key 禁用 |
| D6 String | 长度只属于 create 前可调 resource estimate 和 evidence profile，不是 Schema 约束或 mutation admission |
| D7 Scale | required envelope 为 Small、Medium、单 1M、双 1M、String、Expansion、Delivery、Soak；10M/100M 为 non-blocking research/stress |
| D8 产品边界 | 保持 SOMA 产品定义与核心抽象；不增加 SQL/DDL/DML/DQL、foreign key、reference 或通用数据库 API |

补充结果：

- TIME direct/Batch/replace/Mutator/Delta/flattened value 写入统一校验
  `[0, 86_400_000_000_000)`；
- Date/Instant arithmetic checked；Time arithmetic 为 24 小时 modular；
- protocol 切换到 generated/runtime v12、transformation v4、kernel v5、
  Candidate/relation formula v2，无旧 logical API 双轨；
- `CandidateLongEqualityAccess` 是 generated protocol，不进入 application data model；
- 三个 reference application 已按 canonical generated API、Metadata/Plan、Group、
  DataFlow 和 detached result 审计，不需要装饰性 production 改写；
- 测试与 benchmark 按 Capability、reference differential、external consumer、
  reference application 和 qualification 组织，没有保留以 TV 编号命名的 canonical
  taxonomy；
- 原逻辑/执行 Temporary 与独立验证 Lab 已删除。

## 7. 当前 Evidence

### 7.1 Runtime-scale qualification

当前本机 evidence 环境：

- Amazon Corretto `1.8.0_502-b07` full JDK 8；
- Maven Wrapper `3.9.16`；
- macOS `26.5.2` / Darwin `25.5.0`、`aarch64`、Apple M5 Pro、48 GiB；
- G1 GC；每条 lane 独立 JVM 和显式 heap/timeout。

当前 artifact：

- qualification ID：
  `runtime-scale-qualification-20260729-d90e8499d51f`；
- executable product/evidence content SHA-256：
  `d90e8499d51f7477db3959033895853e223bd692794e25eb8bdf234492e3c2ba`；
- combined artifact SHA-256：
  `4bdc5b51407aaec838af0a95de81249c717e8beab9fea78e1cbf4db8a4abbbef`；
- strict schema v2 SHA-256：
  `eeb8eb1e0f5beda9b3970746b796eb0c5e58a7b8ccd5a9f7cc21dbafc98b4ce2`。

八条 required lane 均为 applicable/passed：

- Small/Fast；
- Medium，覆盖 single Segment sequential 与 multi-morsel parallel；
- 实际驻留单表 1M；
- 两个同时驻留的 1M numeric roots；
- 两个同时驻留的 1M String 角色 Table；
- Expansion；
- Eager/callback Delivery；
- lifecycle/GC Soak。

String evidence 同时声明长度、value/object cardinality、sharing、field role、
live Table count、mutation/clear/release/GC，并区分 SOMA structural bytes、
SOMA-retained reachable String model 和 JVM observed heap。String 长度是 workload
profile，不是字段限制。

### 7.2 Component 与合同证据

- public/generated golden、negative compile、external Maven consumer；
- runtime/DataFlow Capability contract；
- sequential/reference differential；
- DataFlow component Corretto baseline v4；
- Access component baseline；
- 三个 reference application correctness 与环境限定 baseline；
- runtime-scale strict validator 的 false-claim、shrunken-1M、extra/missing/
  duplicate lane negative paths；
- Java 8 classfile major 52。

DataFlow v4 只更新 compiled authoring plan identity checksum
`1064084655879852845`；其余 execution checksum 以及 allocation、timing、tail 和
GC envelope 未放宽。

## 8. 相对治理前计划的审计

### 8.1 已达到

- 产品叙事未被数据库分类取代；
- `State / Owner + Capability + Plan / Lifecycle` 已进入正式 Design；
- logical → Java carrier/generated capability → physical strategy 已进入正式
  Design；
- logical type、closed kernel、Bitmap、Join filter、parallel、String 与 scale
  均有明确裁决和 production/evidence；
- 未通过或不值得进入 V1 的方向没有伪装成 roadmap；
- Small/Medium、单 1M、双 1M 和 String 已形成受环境/profile约束的证据；
- Example、测试、benchmark、文档与代码继续围绕同一 Capability 和产品边界；
- 没有 SQL/database 功能膨胀、普通 Iterator、任意 object storage、旧 protocol
  双轨、active 旧专题或独立 Lab 残留。

### 8.2 不是缩水的裁决

- 100M 从 V1 guarantee 调整为 research/stress 是用户明确批准的产品目标校准；
- Segment statistics 被 D3 明确否决；
- generic value constraint 未形成通用 V1 capability；当前只保留
  required/optional/default 和 TIME 等逻辑类型内在约束；
- Decimal 的验证前提没有触发；
- String、composite Key 不启用 runtime filter；
- lazy output 只保留 callback-scoped delivery。

这些结果应在最终治理报告中继续以 accepted / narrowed / rejected /
condition-not-triggered 区分，不能让未采用方向从叙事中无解释消失。

### 8.3 尚未达到严格收口

本文件创建前的审计结论是：技术治理主体已经完成，但正式 repository closeout
尚未完成，不能无保留声明达到卓越性要求。

提交本轮全部变更后，“巨大 dirty worktree、无提交承载”这一项应关闭；下一轮仍需
实时确认提交后 worktree 和 HEAD。其余尾项如下：

1. **Canonical Full**
   - 首次 Full 的前序阶段通过，DataFlow component 在旧 v3 authoring identity
     处按设计 fail closed；
   - v4 校准后受影响的 docs、baseline architecture、DataFlow component 和 diff
     已通过；
   - 第二次 Full 在 `prepare-external-artifacts` 因当前 Codex 沙箱不能写
     `~/.m2` 中止，不是 product failure；
   - 尚不存在一次针对最终候选整体 exit 0 的 canonical `./scripts/check.sh`。
2. **精确 commit provenance**
   - qualification 的 content SHA 与当时 executable worktree 精确一致；
   - artifact 的 `commit` 仍是治理前基线 `6bd260c`，而正式 Gate 要求在精确新
     immutable candidate 上重放；
   - 当前 Governance Report 的 `completed / G5 passed` 表述早于这一事实，下一轮
     必须按最终 evidence 原子校准 Report 与 Conformance。
3. **Qualification source identity 边界**
   - 当前 source list 过度包含整个模块 `src/**`、tests、Examples 和无关 component
     baseline，导致无关变化触发昂贵的八 lane qualification；
   - 同时没有包含实际 source 的 `scripts/lib/sha256.sh` 和
     `scripts/lib/supported-jdk.sh`；
   - 应在下一次重型 qualification 前做一次窄的 identity-boundary 修正。
4. **DataFlow baseline provenance**
   - v4 baseline 以旧 HEAD 加 `working-tree candidate` 说明校准来源；
   - 三 fork 结果可信，但还不是理想的 immutable calibration provenance。
5. **抽象叙事闭环**
   - 当前正式文档已拥有抽象层次、Owner、边界和机制；
   - 尚未明确制度化“每个核心抽象拥有 Why/Owns/Not/Relationships/Lowering/
     Lifecycle/Resource/Failure/Evidence/Evolution 的叙事闭环”；
   - 这是产品可理解性、未来局部替换和防止再次膨胀的治理要求，不是装饰性文案。

`CandidateProgram`、`GroupedExactIndex` 等核心实现体量上升属于观察信号，不是仅凭
LOC 拆分或删除的结论。下一轮只有在发现独立语义、Owner、生命周期或失败域时才
进行 capability-based refinement，不能为“看起来更小”制造更多类型。

## 9. 下一轮 V1 Release 治理的产品边界

下一轮目标是把 SOMA 推向 **V1 Release Candidate / selected private-source
release**，不是自动扩大发布渠道。

当前已经确认：

- copyright owner 与发布主体：ArthurFeng；
- 代码与文档许可证：Apache-2.0；
- SOMA 名称、Logo 和 Banner 品牌权利由 ArthurFeng 保留，仅允许为说明原始 SOMA
  项目而合理使用；
- GitHub Organization：`somaruntime`；
- repository：`somaruntime/soma-java`；
- Maven groupId / Java namespace：`io.github.somaruntime.soma`；
- 当前 selected release profile：private GitHub source repository；
- public repository 与 Maven Central：not-selected；
- 当前唯一 compiler/runtime validation authority：
  Amazon Corretto 8.502.07.1 full JDK 8；
- Zulu 和其他 JDK vendor 不属于当前支持范围；
- Git 历史可以原样保留；
- 三个 Example 是独立 Java 8 reference consumers；
- Codex Cloud development readiness 是独立 environment gap，不是 V1 release
  profile 的替代名称；用户已明确不以 Cloud 可用性作为当前必要目标。

V1 发布前还确认有一项产品化任务：在仓库中建立帮助 AI coding tools 正确使用
SOMA 的 skill，并在 README 提供让工具安装该 skill 的提示词。下一轮应先核实是否
已有对应 GitHub issue，再决定实施；不能让 skill 重新定义 SOMA Design。

## 10. 下一轮建议顺序

### R0：重新核实当前候选

- 读取正式入口、当前 HEAD、status、diff、Gate 与本文件；
- 确认本轮提交包含预期全部 surface，且没有新 drift；
- 不重跑已经通过且输入未变化的高成本动作。

### R1：关闭本轮治理尾项

- 精确修正 runtime-scale source identity；
- 建立 immutable executable candidate；
- 在能够写标准 `~/.m2` 的新 Codex 线程中运行一次 canonical Full；
- 只对精确候选运行一次 required qualification，不运行非阻塞 research；
- 校准 DataFlow baseline provenance；
- 原子更新 Governance Report、Conformance 与 performance summary；
- 在现有 Design Constitution/Design Index 中固化递归抽象叙事模型，不新增平行
  文档体系。

### R2：V1 RC 审计

- 以 Blueprint → Design → Code/Generated Surface → Evidence → Conformance
  做 scope non-regression；
- 复核 public/generated API、schema/compiler/runtime protocol、三个 Examples、
  package shape、license/NOTICE/brand、SCM 与 support contacts；
- 复核代码、测试、benchmark、脚本和文档 surface，删除只服务已完成迁移的噪声，
  但不以 LOC、单调用者或浅层 unused scan 删除抽象；
- 明确 V1 version、RC candidate identity、支持矩阵和 selected channel 的 claim。

### R3：G6 selected private-source

- 在同一最终 candidate 上完成 clean package/security provenance；
- 完成最终 support-matrix sign-off；
- 执行用户已确认的 manual heavy qualification；
- 只对 private GitHub source profile作出适用结论；
- 不创建 public release、Maven Central publish、tag 或 release，除非用户另行明确
  授权。

### R4：最终 Temporary 退出

- 稳定事实进入唯一正式 Owner；
- 所有 Report/Conformance claim 与最终 commit/artifact一致；
- worktree clean，parallel Owner、migration artifact、active旧专题、
  未裁决 `UNKNOWN` 为零；
- 删除本 Temporary topic；
- 提交并在用户明确要求时推送。

## 11. 下一轮 Definition of Done

只有同时满足以下条件，才能把 SOMA 推进到 V1 RC 或 selected release 的相应状态：

- 产品叙事、核心抽象、每个抽象自己的叙事和三层实现模型形成可追踪闭环；
- D1–D8 的 production、public/generated protocol、Evidence 与 Conformance 在精确
  immutable candidate 上一致；
- Small/Medium、单 1M、双 1M、String、Expansion、Delivery、Soak 保持 required
  qualification；10M/100M 不绑架 V1；
- canonical Full、适用 package/security/provenance 和 manual qualification 有
  可追踪 artifact；
- selected private-source profile 的 identity、license、brand、SCM、support、
  security 和 support matrix 闭合；
- 三个 Example 与 AI skill（若在 V1 前实施）符合最佳实践且不发明 core Design；
- 没有目标缩水、数据库功能膨胀、开放 SPI、任意 object storage、临时 API、
  parallel Owner、迁移双轨、未退役 Temporary 或虚假 readiness claim；
- 任何真实缺口进入 Conformance，不用 future/MVP/optional 改名消失；
- 完成后删除本文件。

## 12. 长任务异常控制

下一轮不得重复本线程早期工程治理中出现的低价值等待与重跑：

- 高成本命令前先回答：输入、假设或 evidence 目标发生了什么变化，本次将获得什么
  新证据；
- 相同输入已通过的证据直接复用；
- 同一失败只有在采取了具体修正后才能重试一次；
- 超出 Fast/Full/qualification 预算首先识别异常信号并诊断，不用固定间隔
  `sleep` 或盲目轮询；
- 一个问题连续两次没有新增状态或证据时停止并重新规划；
- 不在低决策价值的命名、格式、无界 benchmark 调参或装饰性重构上打转；
- 不因为时间、token、权限或环境困难降低产品目标；真正无法闭合的条件记录为
  blocker，并保留其 Owner。
