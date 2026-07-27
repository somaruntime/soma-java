# SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导

类型：Temporary

状态：active（TV0–TV9 与 P4 已完成；P5 初审问题已修正，等待独立复核）

Owner：SOMA runtime boundary、Group 与 scale-readiness governance

正式事实源：否

实施授权：当前 Goal 已明确授权在 P6 正式固化后，按通过独立审计的最终设计修改
SOMA production、test、benchmark、public/generated contract、Guide、Report 和
必要 Example；P5 通过前仍不得开始 P6/P8

事实范围：本轮讨论形成的产品定位、产品目标形态、运行时责任边界、Group、完整
Metadata 体系、Capability Model、V1 类型与存储边界、容量、版本、DataFlow、
Result Delivery、并行粒度、Snapshot/Restore、规模目标候选、系统设计/核心抽象/
叙事再审视、代码与测试规模治理、三个 Example 的后置审计，以及独立 Technical
Validation Lab 的验证边界

非事实范围：当前正式产品语义、当前实现符合性、任意 String profile 的单表或双表
一亿行能力、当前 production conformance、已完成的 code/test replacement closure、
readiness 和发布声明

原始输入仓库基线：`253e383317a84bacdc164bf105a57a7eb1303987`

本轮 Research 复核基线：`6cdc34f673c7bead208173e13df913e7c0a719fe`

最后审查日期：2026-07-28

## 1. 意图

本 Temporary 固化下一次治理的上游指导，避免实施由当前类、现有 API 或单个
example 反向定义 SOMA。它记录的是候选设计，不在正式 Design 之外建立第二套长期
事实，也不代表当前代码已经实现这些目标。

本轮认知演进为：

```text
columnar Table + DSL
  -> Access Model
  -> Transformation Model
  -> finite DataFlow computation
  -> multi-Table runtime state
  -> SomaGroup
  -> complete SomaMetadata system
  -> application/runtime responsibility boundary
  -> Small / Medium / Large performance envelope
  -> two-Table 100M × 100M challenge
  -> canonical SOMA system narrative
  -> closed compiler-bound Capability Model
  -> Eager Detached + optional callback-scoped Result Delivery
  -> product-shape and developer-journey governance
  -> core-abstraction-centered code/test scale
  -> three reference applications as post-core product acceptance
```

本 Goal 已先审计当前事实并冻结验证问题，再由独立技术验证提供机械与成本
evidence；P4 集成设计已经消费这些结论，P5 初审问题已由
[独立设计与范围审计](independent-design-and-scope-audit.md)跟踪。P5 复核和 P6
正式固化前，仍不得依据本 Temporary 或单项 Lab 结果直接修改
production/public/generated contract。

本轮已经确认、可作为技术验证输入的决策是：

- 建立完整 `SomaMetadata` 心智入口，Descriptor 是其中一个分支；精确 API 已在
  [P4 集成设计](integrated-final-design.md)形成并应用 P5 初审修正；
- Metadata Plan 只在对应 create/bind 前受控可变，Effective Metadata 绑定后冻结，
  hot path 不解释 Metadata graph；
- Result Delivery 是封闭 Capability Model 中的可替换能力；Eager Detached 继续是
  完整、原子、易推理的默认能力，callback-scoped streaming 是唯一可选 Lazy Output
  试点；
- callback streaming 只在同步 one-shot、read-only、作用域内消费的接口上验证，
  ordinary `Iterator<T>`、closeable pull cursor、Generator、Publisher 和 partial
  detached publication 不进入本次治理；
- Scale 不是单一“大表”问题，必须同时覆盖 Small、Medium、Large、single-100M 和
  两个 root Table 各 100M rows 的受约束 Challenge；
- Storage Segment、Parallel Morsel 和 Execution Vector 是三种独立粒度；单 Segment
  可以拆分，多个小 Segment 可以合并，并行按 estimated work 自适应激活；
- V1 schema storage 只允许四类：primitive-backed scalar、reference-backed
  immutable scalar、compiler-flattened value 与 owned structured state；
- V1 的 reference-backed immutable scalar 白名单只有 `String`。String 保持引用
  存储，不引入 dictionary、字符 arena、intern 或其他字符串后端；
- 物理上能够保存对象引用不等于语义上支持任意 Java 对象；arbitrary object 不能
  成为普通 schema field，application object association 使用稳定 ID 加 application
  sidecar/registry；
- String scale evidence 必须区分 SOMA-owned structural bytes、SOMA-retained
  reachable String bytes 与 JVM observed heap，并声明长度、value cardinality、
  object-identity sharing、字段角色和同时存活 Table 数；
- 当前 Research 结论只是技术假设，只有通过有界技术验证和 Owner 裁决后才能进入
  detailed design，更不能直接进入 production；
- TV0–TV8 全部在 `soma_java` 仓库之外的独立 Lab 中完成；SOMA 只提供只读事实和
  baseline 形状，不承载实验实现；TV8 只验证 reference-backed String baseline，
  不重新比较字符串后端；
- TV9 已完成并经过独立复核：callback-scoped streaming
  `accepted for limited read-only pilot`，Eager Detached 继续是默认；
  ordinary Iterator/pull cursor/async/mutation/effect/partial publication 仍排除；
  logical single/double-100M 只证明 delivery surrogate，不是 SOMA readiness；
- 本专题是重新确认“SOMA 应该如何被理解、如何工作，以及每个核心抽象为什么存在”
  的机会；最终设计必须形成一条 canonical system narrative；
- SOMA runtime 可以理解为 schema-defined state owner、compiler-bound capability
  set 与 deterministic execution 的组合；Capability 是核心抽象轴，但 V1 不建设
  开放插件/SPI，hot path 不允许逐 row interface dispatch；
- 正式 Product Blueprint 是产品目标形态的 Owner；本专题必须从完整 developer
  journey、默认体验、诊断、资源、兼容、文档和 Example 共同判断 SOMA 是否成为
  优秀产品，而不是只完成内部技术重构；
- 用户确认的产品目标和 Blueprint 不得因当前实现、实现难度、token 或时间被静默
  缩小；无法闭合的目标进入 Conformance/Owner Decision，只有用户可以降低目标；
- 围绕 accepted 核心抽象和系统叙事控制 production/test 规模；更小是消除无语义
  增益责任和重复 evidence 的结果，不设置 LOC/文件/类/测试数配额；
- code/test 只能在最终设计、Owner disposition、replacement closure 和实施授权后
  裁剪，不能因单一 caller、代码长度或外观相似直接删除；
- Temporary 按职责拆分；技术验证先提供物理与成本结论，再完成最终 SOMA 设计和
  code/test disposition。
- production core 稳定后审计三个 Example；只有发现与最终设计或最佳实践不一致时
  才实施治理，不为展示新能力进行装饰性重写。

### 1.1 独立技术验证边界

技术验证回答“某个机械方向是否值得 SOMA 将来采用”，不回答“SOMA 当前是否已经
readiness”，也不以修改 SOMA 来换取验证结论：

```text
SOMA current code/docs/evidence
  -> read-only problem and baseline extraction
  -> standalone Technical Validation Lab
  -> accepted / rejected / inconclusive
  -> Owner adoption decision
  -> separate SOMA detailed-design and implementation authorization
```

独立 Lab 必须满足：

- 物理位置位于 `soma_java` repository/workspace 之外；精确名称和路径由 TV0 记录；
- 不依赖 SOMA Maven module、JAR、generated artifact、source set、package-private
  protocol 或 symlink；
- 使用 Lab-owned package 和最小 surrogate，语义等价地复刻需要比较的 baseline
  mechanical shape，不把实验类冒充 SOMA contract；
- 可以只读引用 SOMA commit、代码位置、成本模型和行为约束，但不得修改
  `soma-runtime-core`、`soma-processor`、`soma-dataflow`、`soma-testkit`、
  `soma-benchmarks` 或 `soma-examples`；
- Lab code 可以替换或删除，不承担 public API、compatibility、migration 和长期
  architecture 责任；
- Lab 通过只形成技术方向 evidence；是否进入 SOMA 必须另起 adoption/detailed-design
  工作，并在真实 production shape 上重新完成适用验证；
- Lab 是本次治理的一次性验证载体。Owner 裁决、必要 evidence 摘要转移和正式
  Design/Report 固化完成后，必须删除整个 Lab source、build output、generated
  dataset 和临时 artifact；正式 Owner 不得长期依赖 Lab path。

当前一次性 Lab 位于：

```text
/Users/arthur/Documents/HGTECH/projects/prototype/soma_runtime_scale_technical_validation_lab
```

该路径同时是精确 deletion root。Lab 使用 `com.hgtech.lab.somascale` package、
Zulu JDK 8 和零第三方依赖的 `javac/java` harness；TV0–TV7 已在 Lab revision
`591bbab505b14a2261d2700b7f0ffdcdde6cfb1f` 收口，证明 narrow primitive/numeric
机械方向。TV8 raw evidence 已在 Lab revision `b2970d2` 收口，在同一隔离边界内
验证 reference-backed String，不修改或依赖 SOMA production。

TV8 的可转移技术结论是：

- typed reference column、String authoritative value equality、Key/Unique/Index、
  Group/aggregate Join、mutation/epoch/clear/release mechanics 可以进入最终设计；
- single-100M low-cardinality shared payload/group，以及两个独立 Table 各 100M、
  equal-value/distinct-object pools 的 aggregate Join 实际通过；
- 上述 large profile 固定为 UTF-16 length 16、每表 cardinality/object identities
  1,024；它不是任意 String 100M claim；
- 4 GiB heap 下，single/double 100M high-cardinality unique String Key 分别以
  `11,677,813,220 B` / `23,355,626,440 B` modeled required 确定性拒绝；
- 两表 aggregate Join 的完整 pair output 约 `78.125 TB`，因此只接受 aggregate
  terminal，不接受 full pair materialization；
- dictionary、字符 arena、intern、compression 和 arbitrary-object backend 没有
  进入候选，不能描述成被性能实验否决。

TV0–TV9 已全部完成。TV9 不否定 TV3/TV4/TV7 的 eager evidence，也不重新打开
ordinary Iterator、closeable cursor 或异步 Publisher；它只支持把同步
callback-scoped streaming 作为少量 read-only generated typed terminal 的受限
Result Delivery 试点。TV9 implementation/raw/decision revision 为
`75fe7a7` / `bbc13e8` / `cf322ab`。

职责拆分：

- [Scale Architecture 技术假设与验证协议](scale-architecture-technical-validation.md)：
  记录独立 Lab 中的大规模存储、索引、中间结果与执行粒度候选，以及在升级为
  SOMA 设计输入前必须取得的技术证据；
- [TV0–TV9 技术验证 Evidence Synthesis](technical-validation-evidence-synthesis.md)：
  自包含转移 accepted/rejected/inconclusive、profile、Owner 与 claim boundary，
  供最终设计消费且不依赖 Lab 实验类型；
- [集成最终设计](integrated-final-design.md)：
  关闭 canonical narrative、Metadata、SomaGroup、Capability、String、storage/
  locator、Candidate/Relation、scheduler、Result Delivery、resource/failure、
  production slices、qualification 和正式 Owner promotion；
- [SOMA 系统设计、核心抽象与叙事再审视](system-design-and-narrative-governance.md)：
  记录 canonical narrative、抽象审查、Owner 裁决和 evidence 驱动的最终设计问题；
- [SOMA 代码与测试规模治理](code-and-test-scale-governance.md)：记录 code/test
  保留、简化、合并、删除、替代闭环和 scope non-regression 规则。
- [SOMA Capability Model 与 Result Delivery 治理](capability-and-result-delivery-governance.md)：
  记录封闭能力集合、cold-path binding、hot-path specialization、Eager 默认、
  callback streaming 试点和 TV9；
- [SOMA 产品目标形态与长任务执行治理](productization-and-goal-execution-governance.md)：
  记录产品北极星、目标锁、Goal Traceability、防偏航/防打转协议、三个 Example
  后置审计和整体完成定义。
- [Goal Traceability](goal-traceability.md)：
  以稳定 Goal ID 连接产品价值、正式 Owner、TV evidence、production slice、验证、
  Example 和剩余差距，并在 P0–P11 每个阶段关闭时更新。

## 2. 产品定位候选

> SOMA 是 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 的高性能
> 列式运行时状态计算库。它面向单个或多个 Table，提供直接访问、惰性 Candidate
> Scan、有限 Data Transformation DAG、受控数据并行和高效运行时状态维护。

目标场景包括 RTD、工业实时调度、算法运行时、仿真、游戏以及其他需要低内存、
高吞吐、Schema 驱动计算的 Java 8 应用。

SOMA 不成为：

- 数据库、MES 镜像或通用缓存产品；
- 跨 Table 事务、自动回滚或分布式一致性引擎；
- 多调用者并发安全容器；
- application workflow、Rule Scheduler 或 Dispatch Rule 自动执行引擎；
- JDBC/CDC、MES 同步、双缓存切换或 checkpoint 管理框架；
- durable/distributed runtime 或通用 Java Stream 替代品。

## 3. 产品层次与责任边界

```text
Application / External Execution Engine
  -> MES projection, synchronization and eviction
  -> rule scheduling, loop, retry and compensation
  -> multi-Table commit ordering and double-buffer switch
  -> historical metrics, alerting and SLA
  -> explicitly invokes SOMA

SOMA
  -> SomaGroup
  -> generated columnar Tables
  -> direct Point / Column / Batch access
  -> lazy Candidate Scan
  -> finite typed DataFlow DAG
  -> local execution budget, diagnostics and data parallelism
```

应用拥有完整系统的业务一致性和执行叙事；SOMA 拥有其内部状态表示、访问、有限
Transformation 计算和单次操作正确性。

## 4. Table 与容量模型

候选容量语义为：

- `@SomaTable` 声明默认初始容量；
- `reserve(expectedCapacity)` 允许 application 按已知规模提前预留；
- append/add 超过当前 capacity 时自动扩容；
- schema/generated metadata 提供稳定默认 capacity/growth policy；
- 受控 Storage Plan Metadata 可以在 Table create 前形成显式 override；
- effective storage plan 在 create 时冻结，既有 Table 不因后续配置变化而改变；
- 自动扩容可以参考 `ArrayList` 的约 1.5 倍策略，但必须结合列式存储重新设计。

概念公式为：

```text
requiredCapacity = size + incomingCount
grownCapacity    = oldCapacity + oldCapacity / 2
newCapacity      = max(requiredCapacity, grownCapacity)
```

实现必须保证：

- size/capacity/byte arithmetic overflow-safe；
- columns、presence、primary locator、secondary exact access 和必要 scratch 在发布
  前完成整体资源预检；
- 不向 caller 暴露部分列或部分 access structure 已经扩容的状态；
- expected allocation/resource failure 保留旧稳定状态；
- 大规模导入以 `reserve()` 或已知规模 Batch 为推荐路径，自动扩容主要承担正确性
  和易用性。

一亿行附近扩容会同时保留新旧大数组，可能产生很高的瞬时 heap 峰值。因此“支持
自动扩容”不等于“大规模场景无需容量规划”。

### 4.1 V1 类型与存储边界

V1 schema storage 采用四类互斥的语义分类。该分类由 compiler/Descriptor/Layout
在 create/bind 前关闭，不能由 runtime hot path 根据 `Object` 实例动态解释：

| 类型类别 | V1 范围 | 物理表示 | 明确排除 |
|---|---|---|---|
| primitive-backed scalar | primitive、enum、date/time、显式 semantic scalar | primitive column | boxed live storage |
| reference-backed immutable scalar | 白名单仅 `String` | typed reference column | arbitrary Java object |
| compiler-flattened value | `@SomaValue` 及 nested value | canonical leaf columns | value object reference |
| owned structured state | parent-owned child Table | opaque child handle + child columns | live `List`、`Map` 或 object graph |

`String` 是具有稳定不可变值语义的特殊白名单类型，不是“任意 reference field”的
先例。V1 继续保存 caller 提供的 String 引用，不复制、不 intern、不做 Unicode
normalization、case folding、dictionary encoding 或字符 arena 转换。Key、Unique、
Index、Group、Join 和 order 必须使用确定的 String value semantics，不能使用引用
identity。

普通 Java 对象即使当前 class/field 看似不可变，也可能通过外部别名、可变子图、
自定义 equality/hash/order 或生命周期绕过 SOMA 的 mutation、epoch、
Key/Unique/Index、ownership、并发和 materialization 边界。因此 arbitrary object
不能成为普通 schema field，也不能通过 marker interface、普通 annotation 或
runtime registration 动态准入。确需关联 application object 时，SOMA 只保存稳定
primitive/String/flattened-value ID；对象本身由 application sidecar/registry 管理。

String 内存必须使用三种不同口径：

1. `SOMA-owned structural bytes`：reference column、presence、locator/index、scratch、
   output 等由 SOMA 分配的结构；
2. `SOMA-retained reachable String bytes`：因 Table 引用而保持可达的 String 对象
   及字符载荷；它们不因此成为 SOMA 独占或创建的对象；
3. `JVM observed heap`：特定 JDK、reference width、object layout、GC 与共享关系下
   的实际观测值。

确定性 estimator、reachable model 和 JVM observation 不得互相冒充。跨行或跨 Table
共享同一 String object 时，报告必须同时给出 per-Table reachability 和按 object
identity 去重的 aggregate reachability。任何 String scale 结论还必须声明
UTF-16 code-unit length、value cardinality、distinct object identity、sharing ratio、
optional/absence ratio、字段角色和 simultaneously-live Table count，不能只写 row
count。

## 5. 完整 SomaMetadata 体系

SOMA 候选采用完整 Metadata 体系作为统一心智入口。这里的“完整”表示 Schema、
Group、Table、Column、Segment、Access、Plan、Runtime 与 Observation 都能在同一
概念体系中被准确定位，不表示 hot path 通过动态对象树解释 schema 或策略。

候选结构为：

```text
SomaMetadata
  -> descriptor
       -> SomaSchemaMetadata
       -> SomaTableMetadata
       -> SomaColumnMetadata
       -> SomaKeyMetadata
       -> SomaUniqueMetadata
       -> SomaIndexMetadata
       -> SomaOwnershipMetadata
  -> plan
       -> SomaStoragePlanMetadata
            -> table / column / segment / access-path policy
       -> SomaExecutionPlanMetadata
            -> parallel / scratch / output policy
       -> SomaBudgetMetadata
  -> effective
       -> SomaLayoutMetadata
       -> SomaCompatibilityMetadata
       -> SomaPlanIdentityMetadata

SomaGroupPlan
  -> stable group/member plan metadata

SomaGroup.metadata()
  -> SomaGroupMetadata
       -> detached runtime topology metadata

observe / explain
  -> module-owned immutable Observation / Explain
```

### 5.1 Descriptor、Plan、Effective 与 Observation

| 分支 | 候选语义 | 可变性 |
|---|---|---|
| Descriptor | compiler/generated 固化的稳定逻辑描述 | processor 发布后不可变 |
| Plan Builder | application 对兼容物理策略的显式选择 | 对应 create/bind 前可修改 |
| Effective | 完整验证并绑定到 Group/Table/Invocation 的有效策略和 layout identity | create/bind 后不可变 |
| Runtime State | rows、segments、bucket/link、scratch、epoch 和 active lifecycle | 只由 SOMA 内部受控修改 |
| Observation | current、since-reset、high-water、last-operation 和 explain | 对 application 只暴露 immutable snapshot |

`SomaSchemaMetadata` 描述编译期 schema generation unit；`SomaGroupMetadata` 描述
runtime `SomaGroup` 实例及其 member topology snapshot。current/high-water 和
DataFlow physical decision 分别由 runtime Observation 与 DataFlow
Explain/Invocation Observation 拥有。DataFlow `Grouped` result 不是
`SomaGroupMetadata`，也不因此获得长期 identity 或跨 Table transaction。

Column payload、presence、locator bucket、row link、group membership、candidate、
scratch 和 result payload 都不是 Metadata。它们是权威事实、同步派生结构或单次
Invocation 数据，必须进入 retained/peak/output 成本模型，不能因受 Metadata 描述
而被归类成低成本控制信息。

### 5.2 Metadata 与 RuntimePlan 的关系

public `RuntimePlan` / `TablePlan` 不再以“直接删除”为既定候选。当前 plan 责任应
进入完整 Metadata 的 Plan 分支：

| 当前责任 | Metadata 候选位置 |
|---|---|
| schema/generated/runtime compatibility | Descriptor / Compatibility Metadata |
| 默认初始容量、growth、storage strategy | Storage Plan Metadata |
| locator/unique/exact physical strategy | Access-Path Plan Metadata |
| DataFlow parallel、scratch、task | Execution Plan / Budget Metadata |
| materialization/output limit | Output / Materialization Budget Metadata |
| stats mode 与 explain | Observation Plan / Observation Metadata |
| deterministic effective identity | Effective Metadata / plan hash |
| JVM 总 heap、双缓存和应用总预算 | application |

现有 `RuntimePlan` / `TablePlan` 最终是保留、重命名、兼容迁移还是被新的 Metadata
类型替换，必须在技术验证、public API review 和 compatibility design 后裁决。不能
先删除类型再寻找责任 Owner，也不能为了统一 Metadata 恢复 reflection、dynamic
registry 或 per-row metadata interpreter。

### 5.3 修改与冻结边界

- Descriptor 中的 field type、optional/default、Key/Unique/Index field set、
  child ownership 和 schema identity 不允许在运行期修改；
- capacity、segment、shard、load、scratch、output、stats 和并行策略可以成为
  兼容的 Plan 候选，但只有明确准入的字段才能由 application override；
- Storage Plan 在 Group/Table create 前冻结；Execution Plan 在 Context/Template/
  Invocation 对应边界冻结；不同 plan identity 不能混为一个全局 mutable config；
- 已创建 Table 不因修改 Builder、环境变量或 global registry 静默改变 layout；
- hot path 只消费生成代码、预绑定 ordinal/layout 和 effective strategy，不遍历
  Metadata graph。

## 6. SomaGroup 与 canonical API

P5 修正后的 canonical API 骨架为：

```java
RuntimePlan machinePlan = MachineSchemaMetadata.newPlan().build();
RuntimePlan candidatePlan = CandidateSchemaMetadata.newPlan().build();
SomaGroupPlan groupPlan = SomaGroupPlan.builder("dispatch-runtime")
        .resourceBudget(groupBudget)
        .member("machines", MachineTable.metadata(), machinePlan)
        .member("candidates", CandidateTable.metadata(), candidatePlan)
        .build();
SomaGroup group = Soma.createGroup(groupPlan);

MachineTable machines = MachineTable.create(group, "machines");
CandidateTable candidates = CandidateTable.create(group, "candidates");
```

不变量：

- 每个 root Table 从创建到 release 永久属于一个 Group；
- owned child 继承 root 所属 Group，不独立 reparent；
- 一个 Group 可以组合多个 schema；同一 root descriptor 可由不同 stable member
  slot 保存多个实例；
- 一个 JVM 可以存在多个相互独立的 Group；
- read-only multi-source DataFlow 可以跨 Group/schema/同类型实例，Group 不是 guard
  acquisition 的必要边界；
- Group 不提供跨 Table transaction 或隐式一致性；
- application 可以把不同 Group 解释为 active/staging，但 SOMA 不理解或执行
  双缓存协议；
- 不引入额外 `SomaRuntime` host/process manager；
- `Soma` 只作为跨模块根对象的 canonical factory/entry，不成为承载所有操作的
  God Class。

Group member identity、实例数、implicit/explicit release 和 future restore typed
handle foundation 已由[集成设计第 5 节](integrated-final-design.md)裁决。

## 7. Group 与 Table 的 data version

Group 和 Table 分别拥有独立、application-defined 的数据版本：

```text
schema version    -> Schema compatibility identity
structural epoch  -> current physical Index validity
data version      -> application synchronization progress
```

候选语义：

- Group data version 与各 Table data version 相互独立；
- 更新任一 Table version 不自动更新 Group version；
- 更新 Group version 不传播到 Table；
- SOMA 不解释、比较或强制 version 单调；
- application 可以只维护 Group、只维护 Table、同时维护或完全不使用；
- version 可以表示 MES SCN、同步批次或 application logical clock，其业务含义不
  进入 SOMA。

完整 Metadata 的分层和冻结原则由第 5 节记录。P4 进一步冻结 Group/Table
data version 为相互独立的 optional String application marker，只能在 safe point
set/clear，不传播、不要求单调，也不修改 structural epoch；未来 Snapshot/Restore
在 quiescent boundary 保存该值。

## 8. 单 Table、多 Table 与一致性

SOMA 同时支持：

```text
Single-Table
  -> Point / Exact / Column / Candidate / Batch / Mutation

Multi-Table
  -> Join / Group / Combine / other multi-source Transformation
```

跨 Table 有三种 application 组合方式：

1. 使用 SOMA DataFlow 表达有限的多来源数据变换；
2. 使用 Java primitive、stable key 或 detached value 保存中间状态，把过程拆为
   多次 single-Table operation；
3. DataFlow 计算 detached command/result，再由 Java application 完成业务判断和
   顺序提交。

SOMA 采用 single-owner、synchronous operation。数据并行只是一场同步 Invocation
内部的实现，不产生并发 Table API。多 Table 修改的排序、失败恢复、补偿和重建由
application 负责。

## 9. DataFlow 与应用编排

必须区分两个不同抽象层次。

### 9.1 SOMA DataFlow Graph

SOMA 拥有一个有限、typed、可验证的数据变换 DAG：

```text
Tables + Parameters
  -> Join
  -> Filter
  -> Projection
  -> Group / Aggregate / Window
  -> Detached Result or controlled single-aggregate Effect
```

SOMA 可以拥有：

- logical `DataFlowDefinition`；
- analyzed/lowered reusable `DataFlowTemplate`；
- bound one-shot `DataFlowInvocation`；
- Schema binding、Shape、lineage、order 和 operator legality；
- sequential/parallel kernel、budget、cancellation、local stats/explain。

### 9.2 Application Orchestration Graph

以下责任从 SOMA 产品边界删除并留给外部执行引擎：

- Dispatch Rule 自动发现、注册和执行；
- trigger、timer、polling loop 和 rule dependency scheduling；
- 多次 Invocation 的业务状态机；
- retry、compensation、cache switch 和 multi-Table commit；
- 历史统计、Dashboard、告警和 SLA。

最终关系为：

```text
Application Rule Executor / Orchestration Engine
  -> explicitly invokes one or more SOMA DataFlow Invocation
       -> SOMA executes one finite Transformation DAG
```

单次 Invocation 的内部事实由 SOMA 观测；跨 Invocation 的历史分析和业务解释由
application 拥有。下一次治理不得把 SOMA DataFlow 删除成只有 storage primitive，
也不得把它扩张成应用工作流引擎。

## 10. MES、工作集与双缓存边界

FAB MES 可能包含上百张表和十亿级历史数据。SOMA 不应完整复制数据库，而是承载
application 投影出的 active runtime working set。

application 负责：

- 哪些 MES 数据进入 SOMA；
- 初始化、增量同步、冲突和 checkpoint；
- stale/invalid runtime state 的驱逐；
- 分区、双缓存、publish/swap 和 JVM 总 heap 规划。

SOMA 只提供通用的 Batch/Delta、single-aggregate safe-point mutation、完整
SomaMetadata 体系、eager detached output 和未来 Snapshot/Restore 基础。RTD
Runtime State Synchronization 仍应作为独立的未来应用/设计专题。

## 11. Snapshot/Restore 的未来兼容基础

Fast Snapshot/Restore 很有价值，但当前不直接落地 public format/API。本阶段需要
避免封死以下基础：

- stable Group/Table logical identity；
- Group/Table 独立 data version；
- schema hash 和 generated/runtime protocol identity；
- 没有 active operation/view/invocation 的 quiescent boundary；
- deterministic columns/presence/locator/exact-access 表示；
- restore 全部成功前不发布 partial Group；
- processor-generated codec 的未来扩展空间。

未来专题再裁决 binary format、checksum、channel I/O、compatibility、文件原子性、
恢复 fallback 和 portable/physical snapshot 边界。文件位置、保留策略、fsync、
MES delta replay 和 Group 发布仍由 application 负责。

## 12. 中间结果与执行成本

候选性能原则：

- Candidate Scan intermediate stage 保持 lazy/fused；
- DataFlow Definition/Template 可以延迟到 one-shot Invocation terminal 执行；
- Result Delivery 是显式 Capability，不与 intermediate laziness 混淆；
- Eager Detached 是默认路径：完整 scalar、detached columnar、materialized result
  或完整 Effect 构造成功后一次性发布；
- callback-scoped streaming 是可选 read-only 试点：同步、one-shot、只在 terminal
  调用栈内消费，Cursor/guard/borrow 不得逃逸；String getter 返回的 immutable value
  可以保留；
- callback streaming 的 source guard、scratch、budget、cancel 和 cleanup 必须在
  调用返回前关闭；callback 已产生的 application side effect 不由 SOMA 回滚；
- 不新增 ordinary `Iterator<T>`、closeable pull cursor、Python Generator、
  `Flow.Publisher`、异步 push 或 terminal 返回后继续持有 source guard 的结果类型；
- Table-local `IndexBuffer` 和 sort/update scratch 尽可能复用；
- snapshot/materialization 是显式 detached allocation boundary；
- Join、Group、Sort、Window 等 barrier 可以需要中间状态，但必须 primitive、
  bounded、可估算并尽可能复用；
- 大型 detached result 可以内部按 Segment 构造，但全部成功前不能向 caller 发布；
- 最终 output 超出显式预算时整体失败，不通过部分交付掩盖无界结果；
- 不把 `List<Row>`、DTO graph、Java Stream 或通用 Map graph 引入 hot storage；
- output cardinality、scratch、parallel tasks 和 materialization 使用显式 budget；
- Application Java orchestration 跨 operation 只保存 stable key、primitive 或
  detached value，不保存 current Index/Cursor/View。

Large 与 `100M × 100M` 工作负载必须在 terminal 前通过 access path、filter、
semi/anti/exists、pre-aggregation、Join–Group fusion、limit/best/top-k bound 或其他
等语义 specialization 消除无用结果。callback streaming 可以降低 retained output
peak 并支持 early stop，但不减少逻辑 cardinality、总计算工作或 source bytes；
它不作为无界 Join expansion 的补救手段。能够预计算为超预算的 high-expansion
输出仍应在 source touch 或枚举前确定性拒绝。

### 12.1 并行执行边界

候选并行模型不是“Segment 并行 + Segment 内再开一层并行”，而是：

```text
Storage Segment
  -> zero / one / many scheduling Morsel
       -> one or many cache-oriented Execution Vector
```

- 一个大 Segment 在 estimated work 足够时可以拆成多个 disjoint morsel；
- 多个小 Segment 可以合并到一个 morsel，避免 one-segment-one-task 固定税；
- 所有 morsel 压平到一个 bounded scheduler，不建立 nested executor；
- worker-local scratch/hash/partial/stats、disjoint output range、cache-line sharing 和
  deterministic merge 必须进入验证；
- Small/Medium 应存在 sequential fast path；是否并行由 operator、cardinality、
  touched columns、expression/hash cost、selectivity、scratch、worker 和 bandwidth
  共同决定，不能仅由 Segment 数量决定；
- Segment、Morsel 和 Execution Vector 参数分别服务 storage/growth/GC、scheduling
  与 cache/JIT，不绑定成一个固定大小。

P7/P8 必须用当前代码和 production allocation evidence 逐项核实并实施这些原则，
不能把 P4 设计或 Lab 方向当作已实现事实。

## 13. Scale Readiness 候选目标

候选产品目标为一个受约束的规模包络，而不是单一行数口号：

> 单张 SOMA Table 在一亿条记录以内的受约束 Schema、Access Pattern、硬件和 heap
> profile 下，应当具有良好性能；Scale Challenge 还必须覆盖两个独立 root Table
> 同时各有一亿条记录的多来源计算。超过单表一亿条不作性能保证。

双表 Challenge 不承诺任意 `100M × 100M` 笛卡尔或高 multiplicity 多对多结果完整
物化。独立 Lab 的每条 scale lane 必须声明：

- left/right Schema、row width、row count 和同时驻留的 retained bytes；
- String field 的 UTF-16 code-unit length、value cardinality、distinct object
  identity、sharing ratio、optional/absence ratio 与 payload/Key/Unique/Index/
  Group/Join 字段角色；
- `1:1`、`N:1`、有界 `1:N` 或受限 `N:M` 关系；
- hit rate、selectivity、distinctness、skew 和 maximum multiplicity；
- maintained primary/unique/exact access path 与实际 touched columns；
- terminal 是 count/exists/semi/anti/aggregate/bounded result 还是完整 detached
  result；
- output upper bound、scratch、growth/rehash transient peak 和 GC headroom。

该目标当前不是能力声明，也不意味着任意宽 Schema 或 full global
Sort/Join/Window 都有同一延迟保证。独立 Lab 的结果只能判断候选机械方向，不能
证明 SOMA 单表或双表一亿行 readiness。

配套技术验证协议已经冻结以下必填维度；TV0 必须把它们实例化为可复现 workload
manifest、实际预算和判定线：

- representative narrow/medium/wide Schema；
- row count、distinctness、selectivity、group size 和 join expansion；
- Point/Exact/Scan/Column/Batch/Delta/Join/Group/Window 的目标 workload；
- latency、throughput、allocation、retained heap、peak heap、Young/Full GC 和
  pause 指标；
- Fast、Small/Medium/Large、single-100M、`100M × 100M` Challenge 与 long-run
  Soak lane；
- Zulu JDK 8、heap、hardware、fork/warmup 和 claim boundary；
- 哪些检查属于 Lab 日常 check，哪些只进入显式 standalone scale qualification；
- baseline shape、candidate、Lab harness 和 workload 的归因协议。

不得为了通过 Lab check/scale lane 缩小问题规模、减少目标能力或把所有性能问题都
归咎于 SOMA。

## 14. 后续治理的最低闭环

后续至少需要完成：

1. 以本专题按职责拆分的 Temporary 冻结治理目标、语义不变量、技术问题、
   Capability/Result Delivery、产品形态、code/test scale 规则和
   停止条件；
2. 在 `soma_java` 之外建立独立 Lab，完成 TV0 manifest，并只读记录 SOMA baseline；
3. 在 Lab 中完成 TV1–TV6，逐项形成 accepted/rejected/inconclusive；
4. 只让 final candidates 进入 TV7 standalone single-100M 与 `100M × 100M`
   narrow primitive/numeric composite scale validation；
5. 在 TV8 只验证 reference-backed String baseline 的 payload、Key/Unique/Index、
   Group/Join、Small/Medium、单表/双表规模、mutation/clear/release、GC 和三层
   memory accounting；
6. 形成自包含 Technical Validation Report 和 Owner decision matrix；
7. preregister 并完成 TV9，只比较 Eager Detached 与 callback-scoped streaming，
   形成受限 Result Delivery Owner decision；
8. 使用已接受 evidence 完成 canonical product narrative、核心抽象、完整 Metadata、
   Capability Model、Result Delivery、runtime/execution/resource/module/API/
   compatibility 的最终详细设计；
9. 从正式 Product Blueprint 的 developer journey、默认体验、诊断、兼容、文档和
   release claim boundary 对最终设计进行独立产品审查；
10. 按最终设计审计 code/test/doc/evidence，形成 Owner disposition 和 replacement
   closure，不以 LOC 或外观相似裁决；
11. 获得正式 Design、public/generated contract 和 production 实施授权后，按可独立
   保留的 slice 实现、迁移并裁剪不必要 code/test；
12. production core 稳定后审计三个 Example，只有存在最终设计或最佳实践偏差时
    才实施必要治理；
13. 重新建立 production-shape correctness、compatibility、allocation、scale、
   external-consumer 与 scope non-regression evidence；
14. 原子固化正式 Owner 和 Governance Report，删除独立 Lab 与全部 Temporary。

TV0–TV9 与其 Technical Validation Report/Owner Decision Matrix 已完成，即上述
第 1–7 项已经完成。整体下一阶段进入 integrated final design。Lab 仍是本次治理的
一次性 evidence 载体；只有正式 Design/Report 完成必要证据转移、production
qualification 收口后才删除。

TV0–TV9 是独立 Lab 的技术验证协议，不是 SOMA 正式性能 Gate。Lab 代码和结果不得
直接合并或外推为 SOMA readiness；最终 Gate 只能在后续 SOMA adoption/implementation
取得实际 evidence 后冻结。本次治理完成前还必须先形成自包含的 Technical Validation
Report 和 Owner decision matrix，再删除独立 Lab；删除后不得留下正式文档对 Lab
路径、构建或运行时产物的依赖。

## 15. 非回归与停止条件

下一次治理不得静默削弱：

- Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8 定位；
- packed columnar storage、primary/unique/exact access 和 swap-remove；
- Access Model、Transformation Shape/operator 和 Candidate lazy semantics；
- ownership、lifecycle、Index currentness 和 single-operation failure atomicity；
- DataFlow finite DAG、deterministic parallelism、budget、Eager Detached 默认能力和
  callback-scoped streaming 的受限试点边界；
- 正式 Product Blueprint 的目标用户、完整 developer journey、核心能力和三个
  independent reference consumer；
- Zulu JDK 8 验真边界以及当前 correctness/external-consumer Gate。

当前实现、实现难度、Lab 的 narrow profile、剩余 token 或时间都不能成为降低上述
目标的理由。不能在当前条件下关闭的项目保持为 Conformance/Owner Decision；只有
用户可以修改正式目标。

以下事项必须获得明确实施授权后才能修改：

- annotation Schema、public/generated API 或 protocol；
- 完整 Metadata、RuntimePlan/TablePlan 处置及 compatibility migration；
- Group/Version 的新 public contract；
- DataFlow public lifecycle 或 execution semantics；
- production/test/benchmark/script 的删除、合并或替代；
- 正式 Design、Conformance、Gate 和性能 claim。

## 16. Temporary 退役条件

本专题只有在以下条件全部满足后才能删除：

- 独立 Lab 的 TV0–TV9 evidence、规模目标和 standalone qualification 设计已完成
  Owner 裁决；
- Technical Validation Report 与 Owner decision matrix 已自包含必要结论和证据摘要；
- 独立 Lab 已删除，正式事实源不依赖其 path、source 或 artifact；
- canonical product/system narrative、核心抽象、Metadata、Capability Model、
  Result Delivery 和最终详细设计已完成 Owner 裁决；
- code/test replacement closure 已完成，不再存在已替代平行路径或重复 evidence；
- 三个 Example 已完成 post-core 审计，必要治理及独立 evidence 已闭合；
- 获得相应实施与正式切换授权；
- 长期事实原子固化至唯一 Blueprint/Design/Engineering Owner；
- implementation map、Conformance、Guide/Report 和 checker 已同步；
- 全部适用 Gate 与 scope non-regression 审查通过；
- 正式 Governance Report 已形成；
- 不再有正式入口引用本 Temporary。

若候选方向被否决，应记录裁决并直接删除本 Temporary，不将其归档或标记为
superseded。
