# SOMA Java V1 核心抽象、叙事体系与不变量证明链

类型：Design / Cross-Owner Architecture Skeleton

状态：Active V1 Baseline

正式事实源：是

Owner：SOMA V1 跨Design核心抽象、父子叙事、不变量证明链路由与实施期演进协议

创建日期：2026-08-03

最后审查日期：2026-08-12

本次冻结：Canonical Logical IR / Execution Engine M1 responsibility baseline；finite primitive
Chunk specialization与shared ordinal-work lifecycle成熟化

## 1. 文档责任

本文回答四个问题：

1. 哪些语义节点真正撑起 SOMA 的产品与技术架构；
2. 这些抽象在相近层级上如何按因果、时序和状态迁移形成完整叙事；
3. 每项关键不变量由谁在什么边界建立、保持、失败和证明；
4. implementation 怎样在不静默改写产品语义的前提下审慎修正候选抽象与内部机制。

目标不是预先设计每个 class、interface、helper 或 data structure，而是形成一副足以指导
I0-I8 的正式架构骨架：实施者知道当前正在填写哪个语义位置、它直接服务哪个父叙事、必须
守住哪些不变量，以及什么 evidence 可以支持保留、修正或替换。

## 2. Authority 与状态边界

精确产品事实仍由以下 Owner 拥有：

- [产品蓝图](../blueprint/README.md)：产品意图、用户、边界与成功标准；
- [Design 总览](README.md)及分责 Design：规范性长期合同；
- [Implementation Plan](../engineering/v1-implementation-plan.md)：I0-I8 顺序、exit 与 stop；
- [Conformance](../conformance/README.md)：implementation/evidence gap；
- [最终全局一致性审核](../conformance/v1-final-pre-implementation-global-consistency-review.md)：
  当前baseline的实施准入结论。

本文：

- 不能覆盖或静默修改正式 Blueprint/Design；
- 不能授权 I0、production source、module 或 public API；
- 唯一拥有正式 Design 之间的架构骨架、父子叙事和证明链路由；
- 如果发现正式语义需要改变，必须形成明确 delta，经 Product Owner 裁决与promotion；
- 如果只细化内部候选分解，implementation 可以按本文 change protocol 审慎演进。

本文采用分层抽象、叙事追溯与不变量驱动的判断方式；方法论不替代SOMA正式Owner。

## 3. 非目标

本文不：

- 重新讨论已经关闭的 V1 product capability；
- 把所有 generated type 或 IR node 列为核心抽象；
- 预建 production package、module、SPI、class hierarchy 或 test taxonomy；
- 用抽象数量、类图完整度或文档篇幅代替架构判断；
- 冻结 Chunk geometry、hash、codec、Join coefficient、task multiplier 等 profile choice；
- 把测试提升为不变量 Owner；
- 为每个函数建立永久 traceability graph；
- 让 reference interpreter 形成第二条 production path；
- 把候选名称当成 release compatibility contract。

## 4. 核心判断模型

### 4.1 抽象与叙事

```text
Vertical abstraction
    Product Intent
        -> User Semantic Model
            -> Compiler / Planning / Runtime Architecture
                -> Significant Mechanism Boundary
                    -> Local Implementation Detail

Horizontal narrative
    Input / Current State
        -> Validate / Construct
            -> Derive / Decide
                -> Transition / Execute
                    -> Publish / Return
                        -> Invalidate / Dispose
```

纵向回答“当前用什么词汇理解系统”；横向回答“这些抽象如何共同完成一次真实行为”。父叙事中
的一个有意义步骤可以展开为下一层完整子叙事，但子叙事必须保持父层输入、结果、失败与不变量
承诺。

### 4.2 正确性主脊柱

SOMA 所有重要叙事都必须精化同一条正确性主脊柱：

```text
Untrusted schema / application input
    -> validate and construct a legal representation
        -> publish only complete state or capability
            -> bind authoritative facts
                -> derive and decide without silent mutation
                    -> execute or stage an authorized transition
                        -> validate all invariants
                            -> atomically publish or return detached result
                                -> quiesce and invalidate borrowed facts
```

它包含五项连续责任：

1. 构造正确；
2. 迁移保持；
3. 失败可信；
4. 发布安全；
5. 失效明确。

### 4.3 Typed relation

抽象关系必须使用明确类型，不把全部边都写成“属于”：

| Relation | 含义 |
|---|---|
| `realizes` | 子节点直接兑现主要父叙事的一项承诺 |
| `projects` | 从更丰富事实形成特定 consumer 可见语义 |
| `generates` | compiler 从 validated model 产生 derived source/capability |
| `lowers-to` | logical abstraction 转换为更低层表示或计划 |
| `owns` | Owner 对状态、决策或发布拥有最终权力 |
| `depends-on` | consumer 使用另一个单一 Owner 的能力 |
| `constrains` | 正交不变量限制多个节点，但不成为其父节点 |
| `coordinates` | 最近公共父节点编排多个兄弟节点 |
| `evidences` | oracle/test/profile 支持一项主张，不拥有产品事实 |

主要 `realizes` 关系必须无环。多消费者不等于多 Owner；一个节点若看似有多个主要父节点，
必须先分类为共享消费、依赖、横切约束、编排或职责混杂。

## 5. 成熟度模型

| Maturity | 含义 | Change boundary |
|---|---|---|
| `SEMANTIC_BASELINE` | 已由正式 Blueprint/Design 拥有的用户可观察语义 | 必须经 Product Owner 与正式 promotion 修改 |
| `CANDIDATE_CORE` | 当前最可信、足以指导 implementation 的架构抽象或叙事 | evidence 可触发 Temporary/Design delta |
| `IMPLEMENTATION_HYPOTHESIS` | 为当前候选架构选择的内部机制 | Owner 内可凭 evidence 替换 |
| `EVIDENCE_VALIDATED` | 在 production topology 中已有相称 evidence 的候选 | 仍受上游 contract 约束 |
| `REVISED` | evidence 已证明旧候选需被新候选替换 | 必须关闭旧路径与旧叙事 |
| `RETIRED` | 不再拥有 current 语义或 production responsibility | 不得继续被调用、测试或文档依赖 |

`SEMANTIC_BASELINE`只由正式上游语义授予；implementation node 只有在相称证据进入
Conformance 后才能标为`EVIDENCE_VALIDATED`。尚未获得该证据的架构节点最多为
`CANDIDATE_CORE`或`IMPLEMENTATION_HYPOTHESIS`。

`SEMANTIC_BASELINE`只表示该抽象引用的产品语义已经由正式Owner拥有；本文对它的卡片分组、
primary-parent表达和内部协作分解仍是candidate，可以经审慎review修改，不能反向降低或覆盖
正式合同。

## 6. 抽象准入与停止拆解规则

一个节点满足下列任意一项时，值得作为核心或显著机制抽象审查：

- 表达独立、稳定的 SOMA 语义；
- 拥有权威状态、决策或发布权；
- 保护跨多个步骤的重要不变量；
- 拥有独立 lifecycle 或失效协议；
- 建立 public/internal、compiler/runtime、logical/physical 或 state/execution 边界；
- 被多个 narrative 通过稳定 contract 消费；
- 替换时需要独立 correctness、resource 或 compatibility evidence。

当一个节点同时满足以下条件时停止继续升为核心抽象：

- 没有独立语义、状态、decision right 或 lifecycle；
- 失败完全由直接父节点吸收；
- 只服务一个局部步骤且不形成稳定边界；
- 替换不会改变跨组件 contract、不变量或 evidence strategy；
- 抽取只增加跳转、命名和同步成本。

Hash bucket helper、array copy utility、单个 bit-packing 函数、透明 factory/manager、每种 IR
node class 与每个 generated member 默认停留在 local implementation。若实施证明其获得独立
Owner/lifecycle/invariant，再按 surface admission 提升。

## 7. 候选层级总图

```text
L0 Product Intent
SOMA: single-process compiled mutable Table engine

L1 User Semantic Model
Composition
    -> Soma / SomaGroup
        -> Table
            -> Field / Value
            -> Key / Index
            -> Source / Pipeline / Selection
                -> View / Editor
                -> GroupBy / Join Relation
                -> Detached Result / Structured Failure

L2 Compiler / Planning / Runtime Architecture
Schema Model and Generation Session
Generated Surface
Logical IR and Predicate IR
Planner / Optimizer
Reference Interpreter
Physical Plan / Operators
Operation and Group Admission
StateRoot
Mutation Candidate and Atomic Publication
Managed-memory Admission
Parallel Scheduler
Metadata / Explain Projection

L3 Significant Mechanism Families
Paged Chunk Directory and Leaf Representation
Key / Index Sidecars
Compression Representation and Overlay
Ordinal Work Range and Deterministic Merge
Temporary Lease / Scratch
Full-regeneration Manifest and Version Handshake

L4 Local Implementation
classes, node variants, buckets, arrays, cursors, copy loops, bit kernels, helpers
```

L1 是 product vocabulary；L2 是 implementation 必须理解的 architecture vocabulary；L3 只有在
其生命周期、不变量或替换边界显著时进入本文；L4 不提前设计。

## 8. 候选核心抽象注册表

| ID | Abstraction | Level / Maturity | Primary parent | Primary Owner | Key collaborators |
|---|---|---|---|---|---|
| A0 | SOMA Product Engine | L0 / `SEMANTIC_BASELINE` | V1 North Star | Blueprint | all Design Owners |
| A1 | Schema Composition | L1 / `SEMANTIC_BASELINE` | A0 product model | Schema | Signature、Architecture |
| A2 | Generated Surface | L1-L2 / `SEMANTIC_BASELINE` | A1 validated composition | Signature | Schema、Logical、runtime linkage |
| A3 | Soma Runtime Facade | L1 / `SEMANTIC_BASELINE` | A1 composition access | Logical | Execution configuration |
| A4 | SomaGroup | L1-L2 / `SEMANTIC_BASELINE` | A3 runtime state domain | Storage | Execution admission |
| A5 | Table | L1 / `SEMANTIC_BASELINE` | A4 Group state | Storage | Logical operation surface |
| A6 | Logical Field / Value | L1 / `SEMANTIC_BASELINE` | A5 schema-defined information | Storage | Schema type、Logical capability |
| A7 | Key | L1 / `SEMANTIC_BASELINE` | A5 point identity | Storage | Schema eligibility、Logical point API |
| A8 | Index | L1 / `SEMANTIC_BASELINE` | A5 derived access path | Storage | Schema declaration、Planning substitution |
| A9 | Direct Source / Pipeline | L1-L2 / `SEMANTIC_BASELINE` | A5/A6/A8 computation | Logical | Planning、Execution |
| A10 | Selection | L1-L2 / `SEMANTIC_BASELINE` | A9 Table lineage | Logical | Execution mutation |
| A11 | View / Editor | L1-L2 / `SEMANTIC_BASELINE` | A9/A10 callback boundary | Logical | Execution scope/staging |
| A12 | Relation | L1-L2 / `SEMANTIC_BASELINE` | A9 GroupBy/Join computation | Logical | Planning semantics |
| A13 | Detached Result | L1 / `SEMANTIC_BASELINE` | query/mutation completion | Logical | Failure result boundary |
| A14 | Structured Failure | L1-L2 / `SEMANTIC_BASELINE` | failed operation outcome | Failure | Execution arbitration |
| A15 | Schema Model / Generation Session | L2 / `EVIDENCE_VALIDATED` | A1 compiler realization | Schema | Architecture build host |
| A16 | Canonical / Bound / Predicate IR | L2 / `EVIDENCE_VALIDATED` | A9/A12 lowering与binding | Planning | Logical source、Execution admission |
| A17 | Normalizer / Planner / Optimizer | L2 / `EVIDENCE_VALIDATED` | A16 physical decision与resource estimate | Planning | Architecture operator capability |
| A18 | Reference Interpreter | L2 / `EVIDENCE_VALIDATED` | bound A16 semantic oracle | Planning | Conformance evidence |
| A19 | Physical Plan / Operator | L2-L3 / `EVIDENCE_VALIDATED` | A17 executable decision | Architecture | Execution frame/scheduling |
| A20 | Operation / Group Admission / Execution Frame | L2 / `EVIDENCE_VALIDATED` | terminal coordination与admitted state | Execution | Planning、Failure、Storage、Architecture |
| A21 | StateRoot | L2 / `EVIDENCE_VALIDATED` | A5 authoritative state | Storage | Execution publication |
| A22 | Chunk / Leaf Representation | L2-L3 / `EVIDENCE_VALIDATED` | A21 physical state | Storage | Architecture kernels/backend seam |
| A23 | Key / Index Sidecar | L3 / `EVIDENCE_VALIDATED` | A7/A8 lowering in A21 | Architecture | Storage logical contract |
| A24 | Mutation Candidate / Publication | L2-L3 / `EVIDENCE_VALIDATED` | A20 Table transition | Execution | Architecture mechanism、Storage state |
| A25 | Managed-memory Admission | L2-L3 / `EVIDENCE_VALIDATED` | A20 resource boundary | Execution | Architecture accounting mechanism |
| A26 | Parallel Scheduler / Work Range | L2-L3 / `EVIDENCE_VALIDATED` | A19 parallel refinement | Execution | Architecture scheduler mechanism |
| A27 | Metadata / Explain Projection | L1-L2 / `EVIDENCE_VALIDATED` | state/plan diagnostic projection | Logical | Planning/Execution source facts |

Primary Owner拥有抽象的最终语义或状态宣称；collaborator只能通过typed responsibility参与。例如
Storage拥有A4 `SomaGroup` identity/state domain，Execution只拥有A20对Group operation的admission，
不能发明第二套Group identity或lifecycle。

## 9. Product 与 semantic abstraction cards

### A0 — SOMA Product Engine

- **Intent**：在单 JVM 中让 Java application 用自然 typed API 操作大规模 mutable Tables，
  同时把 data-oriented storage、planning 与 execution complexity 保持在内部；
- **Identity**：产品/composition-independent library capability，不等于某个 runtime singleton；
- **Primary narrative**：schema-defined intent -> generated capability -> stateful computation ->
  evidence-backed result；
- **Invariant**：public semantics 不由 physical representation 或 optimizer strategy反向定义；
- **Non-responsibility**：persistence、distributed execution、cross-Table transaction、application
  referent 与 external side effect；
- **Evidence**：三个产品旅程、G1-G10 与 release claim boundary。

### A1 — Schema Composition

- **Meaning**：一个 package-level `@SomaSchema` 直接包含的完整 Table schema universe；
- **Identity**：exact schema package 与 generated parent namespace；
- **Owned fact**：哪些 Table/Field/Key/Index/Value 共同形成一个 composition；
- **Lifecycle**：source set -> discovered -> validated -> complete model -> generated；失败不发布
  partial composition；
- **Invariant**：full source set、唯一 generated namespace、无 stale/partial member；
- **Relations**：`generates` A2；runtime 每个 A4 都是该 composition 的 state instance；
- **Non-responsibility**：runtime Table data、physical Chunk、dynamic schema registry。

### A2 — Generated Surface

- **Meaning**：validated schema 对 application 和 runtime linkage 的 exact Java 8 projection；
- **Identity**：composition + processor/runtime contract version + generated symbol；
- **Lifecycle**：每次 full regeneration 完整替换；不跨 processor version 承诺 binary identity；
- **Invariant**：不支持的 capability 从 type/member 缺席；schema declaration type 不泄漏；
- **Relations**：`projects` A1/A5-A12，`depends-on` runtime linkage，`evidenced-by` compile/javap；
- **Non-responsibility**：拥有 Table state、决定 query semantics 或使用 reflection 修复 stale build。

### A3 — Soma Runtime Facade

- **Meaning**：composition 的 default/explicit Group access、one-time configuration 与 advanced
  library metadata 入口；
- **Identity**：generated facade per composition；global configuration 最终委托同一 runtime
  Owner；
- **Lifecycle**：class load -> optional configure -> first runtime access freeze -> observe；
- **Invariant**：不成为 service locator、dynamic Table registry 或 Executor owner；
- **Relations**：`creates/accesses` A4，`configures` A25/A26 policy；
- **Non-responsibility**：per-operation execution、Table mutation、关闭 application-owned pool。

### A4 — SomaGroup

- **Meaning**：一个 composition 中全部 Table instance 的隔离状态域与外部 operation serialization
  boundary；
- **Identity**：default singleton 或 explicit created Group object；每种 Table type 恰好一个 instance；
- **Lifecycle**：create/access -> active -> application unreachable -> GC；无 manual close；
- **Invariant**：same Group operation 不重叠；different Group state 隔离；
- **Relations**：`owns` A5 instances；A20 `coordinates` admission；Join 只绑定同一 A4；
- **Non-responsibility**：cross-Table transaction、referential integrity、global live-Group registry；
- **GC accounting**：不反向引用Group/Table的Phantom accounting token在ReferenceQueue投递后
  exactly-once释放retained reservation；GC不承诺同步release。

### A5 — Table

- **Meaning**：schema-defined mutable record set 与唯一 published authoritative state owner；
- **Identity**：Group identity + generated Table type；不是 backing array、slot 或 detached object；
- **Lifecycle**：Group accessor -> initial empty root -> repeated atomic root versions -> Group GC；
- **Invariant**：size/capacity/raw locator属于checked 32位结构域；count/cardinality/
  memory/stateVersion属于checked 64位累计域；payload/Key/Index/compression/
  accounting coherent；
- **Relations**：`owns/publishes` A21，`contains semantic` A6-A8，`is-source-for` A9；
- **Non-responsibility**：stable business order、cross-Table transaction、ordinary referent mutation。

### A6 — Logical Field / Value

- **Meaning**：Table schema 中可被读取、投影、比较或聚合的 logical information；Value 可递归
  flatten 但语义仍完整；
- **Identity**：composition + Table + stable logical path；不是 leaf index 或 physical Column；
- **Lifecycle**：generated descriptor 与 Table facade 同生命周期；callback value View 另见 A11；
- **Invariant**：type/null/equality/order capability 与 schema 一致；representation 不能改变 value；
- **Relations**：`lowers-to` A22 leaves；typed comparison `generates` A16 predicate；
- **Keyable boundary**：只有boolean/byte/short/char/int/long、String、Enum与recursively keyable
  Value可进入Key/Index/GroupBy/Equality Join；float/double仍可comparison/distinct/order；
- **Non-responsibility**：删除 record、拥有 physical array、为 ordinary Object 发明 intrinsic equality。

### A7 — Key

- **Meaning**：可选、唯一、immutable 的 Table-local stable business identity 与 point access；
- **Identity**：完整 direct Field value；zero 合法，reference/Value non-null；
- **Lifecycle**：add validation -> published -> immutable -> record remove；修改用 remove + add；
- **Invariant**：每 Table 0..1、unique、与 payload/current root 同版本；
- **Relations**：semantic Field 的 special role；`lowers-to` A23 Key sidecar；
- **Non-responsibility**：relation foreign key、secondary unique、business referential integrity。

### A8 — Index

- **Meaning**：从 Table authoritative values 派生的 non-unique exact-match access path；
- **Identity**：Table + indexed direct logical Field；不是 hash table object；
- **Lifecycle**：与 A21 同时构建、更新、发布、失效；可从 Table facts 重建；
- **Invariant**：matches 不丢失/不增生、canonical Table order、nullable bucket 合同一致；
- **Relations**：`derives-from` A5/A6，`lowers-to` A23，Planner 可用但不能改变语义；
- **Non-responsibility**：权威业务事实、tuple/range/unique relation、稳定 bucket/posting identity。

### A9 — Direct Source / Pipeline

- **Meaning**：Table/Field/Index 作为 reusable source，intermediate 形成 lazy one-shot computation；
- **Identity**：source owner + linked logical nodes + mode；不是 iterator、materialized collection 或
  bound StateRoot；
- **Lifecycle**：reusable source -> open linked node -> successful intermediate atomically claims
  predecessor/creates one open child -> terminal validation/consume/binding；
- **Invariant**：owner/dependency/type/lineage合法；linked node不能branch；terminal-start binding；
  reused claimed node被拒绝；
- **Relations**：`lowers-to` A16；terminal由 A20 coordinates；
- **Non-responsibility**：持有 old storage、预先执行 callback、成为 prepared query。

### A10 — Selection

- **Meaning**：保留单 Table row lineage、因此有资格执行 Table-local update/remove 的 pipeline；
- **Identity**：A9 logical lineage，不是 locator list；
- **Lifecycle**：selectAll/filter/Index/lineage-preserving operation按A9 claim chain形成 -> terminal；
- **Invariant**：只属于一个 Table/Group；map/Field/Relation 后 mutation capability 消失；
- **Relations**：query behavior继承 A9；mutation由 A20/A24 实现；
- **Non-responsibility**：cross-Table selection、partial batch publication、Field remove。

### A11 — View / Editor

- **Meaning**：在 callback 内以低分配方式读取 current row，或暂存 non-Key mutation；
- **Identity**：owner + operation token + participant/thread + callback epoch；不是 record identity；
- **Lifecycle**：participant-local reusable carrier -> bind callback epoch -> active -> invalidate/rebind；
- **Invariant**：不能安全 escape、跨 thread/owner/epoch；Editor Key 无 setter、写入只进入 staging；
- **Relations**：`projects` A21 row values；Editor `contributes-to` A24 candidate；
- **Non-responsibility**：authoritative state、detached persistence、Java object equality/identity。

### A12 — Relation

- **Meaning**：GroupBy 或同 Group binary Equality/Cross Join 形成的 query-only relation computation；
- **Identity**：bound logical inputs + relation kind/key components + predicates；
- **Lifecycle**：Table source -> relation builder -> each successful builder/intermediate claims
  predecessor -> configured one-shot relation pipeline -> terminal；
- **Invariant**：null/missing/duplicate/order/cardinality语义固定；不产生 mutation lineage；
- **Relations**：`lowers-to` A16 relation node，A17选择算法，A18裁判语义；
- **Non-responsibility**：multi-way planner、foreign key、cross-Table transaction、Relation Pair持久化。

### A13 — Detached Result

- **Meaning**：operation 完成后可脱离 pipeline、View、row position 与 backing storage 使用的结果；
- **Identity**：结果值/content，不绑定 StateRoot currentness；
- **Lifecycle**：成功 terminal 构造/hand-off -> application ownership -> GC；
- **Invariant**：完整或不存在；typed materialization、encounter order 与 null contract稳定；
- **Relations**：`projects` bound state/computation；evidence compares content rather than internal identity；
- **Non-responsibility**：自动随 Table 更新、持有 mutation continuation 或 resource lease。

### A14 — Structured Failure

- **Meaning**：SOMA contract/runtime failure 的 stable machine-readable outcome；
- **Identity**：failure code + logical operation + sanitized context + optional safe cause；
- **Lifecycle**：earliest owning boundary detects -> canonical arbitration -> quiesce/release -> throw；
- **Invariant**：normal absence分离、application不能注入code、zero partial result/state；
- **Relations**：`projects` internal failure phase而不泄漏physical node；`constrains` all narratives；
- **Non-responsibility**：包装 JVM Error 成可恢复失败、回滚 callback side effect、通用 INTERNAL_ERROR。

## 10. Compiler、planning 与 runtime abstraction cards

### A15 — Schema Model / Generation Session

- **Meaning**：processor 对完整 source set 建立的 validated、immutable、generation-ready
  composition model，以及一次 full generation lifecycle；
- **Identity**：composition identity + complete source fingerprint + processor/runtime contract version；
- **Lifecycle**：collect rounds -> detect stable complete input -> validate all -> build symbol table -> write all
  output/manifest -> end；
- **Invariant**：validation/generation前后model不变；任何错误不写partial composition；input order不
  改变generated source；
- **Relations**：`realizes` A1 compile narrative，`generates` A2；manifest `evidences` source/output
  closure；
- **Non-responsibility**：runtime schema、partial incremental guessing、application source mutation。

### A16 — Canonical / Bound / Predicate IR

- **Meaning**：把 A9/A12 Java frontend operation 转换为唯一、可验证、可解释、可优化的typed
  Canonical semantic plan，并在terminal-start形成一次性BoundOperation；
- **Identity**：compiled composition descriptor + Table/Field/Index ordinal、typed node/terminal、
  dependency/shape/lineage/order/cardinality/null/failure properties；不以generated facade、StateRoot或
  physical address作为semantic identity；
- **Lifecycle**：frontend validates/claims -> immutable CanonicalOperation -> A20 Group admission -> bind
  current A21 roots/statistics/provenance -> BoundOperation -> reference或normalization；
- **Invariant**：Canonical data-only且不绑定root/scratch/worker；callback保持opaque host barrier；Field
  direct source只有`TableSource + FieldProject`；relation missing不降级为null；
- **Relations**：`derived-from` A9/A12，A17 consumes bound semantics，A18 directly interprets bound
  semantics，A27 explain projects；
- **Non-responsibility**：直接修改Table、选择hash array、执行callback、成为public/serialized query
  AST、建立frontend SPI。

### A17 — Normalizer / Planner / Optimizer

- **Meaning**：在 bound facts 与固定 logical semantics 下选择等价且资源可准入的 physical
  execution；
- **Identity**：一次 Operation 的NormalizedOperation、PhysicalPlan decision与ResourceEstimate，不跨
  terminal复用；
- **Lifecycle**：consume bound A16 -> normalize/rewrite -> choose access/kernel/algorithm/partition ->
  checked conservative estimate -> hand off A19/A20；
- **Invariant**：只能改变成本，不能改变result/order/null/missing/duplicate/callback/failure；
- **Relations**：`depends-on` A16、A21 immutable statistics、A25 budget；A18 `evidences` legality；
- **Non-responsibility**：拥有public API、修改authoritative state、把missing statistics当failure、
  提供application planner SPI。

### A18 — Reference Interpreter

- **Meaning**：直接按Bound Canonical operation原始顺序语义执行的correctness oracle；
- **Identity**：semantic implementation family，不是用户可选择的engine mode；
- **Lifecycle**：production test harness在independent state copy上运行 -> compare -> discard；
- **Invariant**：共享authoritative bound roots、32位结构域/64位累计域、checked arithmetic、order/
  null/missing/failure semantics，但不消费NormalizedOperation/PhysicalPlan或使用optimizer shortcut；
- **Relations**：`interprets` A16，`evidences` A17/A19/A26；
- **Non-responsibility**：unsupported fallback、production double execution、第二套API或state owner。

### A19 — Physical Plan / Operator

- **Meaning**：A17针对本次bound roots选择的operator chain/tree、kernel family、partition与
  deterministic merge的可执行architecture representation；
- **Identity**：bound root versions + selected access/kernel/algorithm/partition + ResourceEstimate；finite
  typed kernel的eligibility、compiled leaf/predicate binding与parallel responsibility也是同一decision；
- **Lifecycle**：A17 produces plan/estimate -> A20/A25 admit -> A20 frame drives specialized operators ->
  discard；
- **Invariant**：PhysicalPlan不分配O(N)execution storage、不越过callback barrier；closed terminal
  requirement、representation handler、parallel responsibility与complete resource projection只形成一次，
  execution只消费；ordered materialization不建立O(rows) locator companion；operator输出与A18等价；
- **Relations**：`lowers-from` A16/A17，`reads` A21/A22/A23，由A20 ExecutionFrame驱动，parallel时由
  A26细化；
- **Non-responsibility**：actual lease/cursor/scratch/worker/staging lifecycle、第二套semantic plan、
  boxed universal executor、长期缓存、public explain handle、发布Table state（交给A24）。

### A20 — Operation / Group Admission / Execution Frame

- **Meaning**：一次 terminal 或 direct Table operation 的 lifecycle coordinator 与 same-Group
  exclusivity boundary；admission后以ExecutionFrame拥有本次actual short-lived execution state；
- **Identity**：operation id/token + logical operation kind + participating Group/Tables；ExecutionFrame
  另绑定acquired lease、cursor/scratch/worker/staging lifecycle；
- **Lifecycle**：invoke -> validate/claim -> reentrancy/parallel check -> acquire Group guard -> bind ->
  normalize/plan/estimate -> resource admit -> create A19 ExecutionFrame -> execute/stage -> publish/hand-off
  -> quiesce/release；
- **Invariant**：同Group不重叠；Frame只在lease成功后创建且不跨terminal缓存；borrowed Chunk/leaf只在
  当前Frame有效；phase/failure precedence稳定；所有exit释放guard/lease/worker；
- **Relations**：`coordinates` A16-A19、A21、A24-A26、A14；
- **Non-responsibility**：拥有Table state、复制planner/resource/failure事实、成为持有全部service/state的
  God object、跨Group deadlock coordination、持久化operation log。

### A21 — StateRoot

- **Meaning**：一张 Table 当前唯一 atomic-published authoritative logical state descriptor；
- **Identity**：Table identity + stateVersion；不是Java object carrier、array identity或slot address；
- **Lifecycle**：construct candidate或prevalidated final commit -> atomic publish descriptor -> stable
  bound read -> successor publication -> unreachable after quiescence -> GC；
- **Invariant**：header、payload、Key、Index、compression、statistics、accounting同logical generation；
  ordinary operation不能观察partial generation；
- **Relations**：A5 `owns/publishes` current root；A22/A23是其组成；A24产生后继；
- **Non-responsibility**：user operation naming、cross-Table snapshot transaction、callback side effect。

### A22 — Chunk / Leaf Representation

- **Meaning**：A21 中checked 32位结构域Table state的paged physical data representation与generated
  per-Chunk kernel boundary；
- **Identity**：root + Chunk ordinal + logical Field leaf；不是public Column；
- **Lifecycle**：allocate/plain tail -> fill/seal -> optionalencode/overlay -> candidate rebuild -> root discard；
- **Invariant**：same-row-span leaves、checked locator、logical value independent of representation、
  unreachable reference cleared；typed array与encoded integral run borrow只向admitted finite physical
  kernel开放且不逃逸Frame、不形成第二storage truth；
- **Relations**：A6 `lowers-to` leaves；A19 reads via specialized kernel；future backend只能在此seam准入；
- **Non-responsibility**：Table business identity、public configuration、per-element virtual dispatch。

### A23 — Key / Index Sidecar

- **Meaning**：A7/A8 semantics 在某个 A21 中的 derived physical access representation；
- **Identity**：root version + logical Key/Index descriptor；bucket/posting/locator没有public identity；
- **Lifecycle**：point transition预计算受影响slot/Bucket -> validate/allocation -> 与payload
  一次publish；Selection update从write set决定reuse/replacement，remove从final-locator projection
  构造replacement sidecar；encoded/candidate从最终payload rebuild -> validate -> publish -> discard；
- **Invariant**：与payload一一对应、collision不改语义、locator current、memory accounted；Key slot
  内联唯一`int` locator；Index singleton内联一个`int` locator，multi只有一个严格
  升序的`int[]`；无per-record next/reverse/second truth；
- **Relations**：`derives-from` A21 payload，A17可选择lookup，A24必须同步更新；
- **Non-responsibility**：成为第二事实源、决定encounter order、泄漏hash/token到expression。

### A24 — Mutation Candidate / Atomic Publication

- **Meaning**：把一次 authorized Table-local transition 从old legal root转换为new legal root的
  staging与唯一可见commit boundary；
- **Identity**：Operation + target Table + old root version + frozen selection/mapping；
- **Lifecycle**：freeze input -> stage callbacks/values/sidecars -> validate -> finish all throwing work ->
  candidate-root swap或bounded prevalidated in-place final commit -> atomic descriptor publication ->
  transfer retained accounting / discard on failure；
- **Invariant**：成功一次publish，失败old root/version完整；Result与published facts一致；
- **Relations**：`coordinates` A21-A23/A25；Editor writes contribute；A14 governs failed exit；
- **Non-responsibility**：callback external side effect rollback、multi-Table transaction、partial batch
  publication。

### A25 — Managed-memory Admission

- **Meaning**：对所有 Groups 的SOMA-owned retained bytes、temporary peak与result handoff进行global
  checked admission；
- **Identity**：library/ClassLoader-wide frozen configuration + current reservations/leases；
- **Lifecycle**：configuration freeze -> reserve retained/temporary -> transfer/release -> metadata snapshot；
- **Invariant**：all arithmetic checked；known peak在不可逆work前admit；failure无leak/partial publish；
- **Relations**：`constrains` A19/A20/A21-A24/A26；A27 projects effective budget/accounting；
- **Non-responsibility**：ordinary referent/application heap ownership、隐式spill、保证JVM永不OOME；
- **GC boundary**：manager只持有accounting token/phantom，不持有live Group graph；queue在admission、
  configuration与global metadata入口同步drain，无background cleaner。

### A26 — Parallel Scheduler / Work Range

- **Meaning**：把同一 A19 semantic work 细化为bounded ForkJoin participation与canonical merge；
- **Identity**：Operation-local ordinal work queue + effective P + merge tree；Row range与Chunk morsel共享
  lifecycle，但保留各自work meaning；
- **Lifecycle**：partition -> submit P-1 drainers/start gate -> caller+workers execute -> failure arbitration/
  cancel -> deterministic merge -> quiesce -> discard；
- **Invariant**：submission/start/rejection/cancel/interrupt/quiescence只有一个Owner；participants/tasks/
  scratch有界；Chunk partial和materialization fixed range共享该lifecycle；sequential/parallel result与
  non-resource failure等价；mode-specific resource/interrupt failure不产生alternate result；返回前
  quiescent；
- **Relations**：`realizes` A19 parallel mode，`depends-on` application ForkJoinPool，A25 constrains；
- **Non-responsibility**：创建/关闭pool、per-record task、async terminal、改变logical order/tree。

### A27 — Metadata / Explain Projection

- **Meaning**：把少量stable state/config categories或本次planning decision投影给advanced/debug
  consumer；
- **Identity**：metadata是last-published detached snapshot；explain是one-shot operation result；
- **Lifecycle**：read/project -> detached handoff；不反向控制runtime；
- **Invariant**：不泄漏mutable physical object、secret、Class/reflection或parseable unstable plan API；
- **Relations**：metadata `projects` A21/A22/A25（含不泄漏codec的compression effectiveness
  summary）；explain `projects` A16/A17/A19；
- **Non-responsibility**：业务决策输入、跨Table一致snapshot、执行callback/data kernel、兼容所有
  diagnostic text。

## 11. Primary-parent 与 cross-relation map

```text
A0 SOMA Product Engine
├── realizes-via A1 Schema Composition
│   ├── generates A2 Generated Surface
│   └── realized-by A15 Schema Model / Generation Session
├── exposes-via A3 Soma Runtime Facade
│   └── creates/accesses A4 SomaGroup
│       └── owns A5 Table
│           ├── contains-semantics A6 Field / Value
│           ├── contains-role A7 Key
│           ├── derives-access A8 Index
│           └── publishes A21 StateRoot
│               ├── composed-of A22 Chunk / Leaf Representation
│               └── composed-of A23 Key / Index Sidecar
└── computes-via A9 Direct Source / Pipeline
    ├── narrows-to A10 Selection
    ├── callbacks-through A11 View / Editor
    ├── forms A12 Relation
    ├── returns A13 Detached Result
    └── fails-as A14 Structured Failure

A9 / A12 lower-to A16 Canonical / Bound / Predicate IR
    +-- interpreted-by A18 Reference Interpreter
    +-- decided-by A17 Normalizer / Planner / Optimizer
        -> produces A19 Physical Plan / Resource Estimate
            -> admitted-and-coordinated-by A20 Operation / Group Admission
                -> creates A20 operation-local Execution Frame
                -> reads A21 StateRoot
                -> transitions-through A24 Mutation Candidate / Publication
                -> constrained-by A25 Managed-memory Admission
                -> optionally-refined-by A26 Parallel Scheduler

A18 Reference Interpreter evidences bound A16 semantic meaning and A17/A19/A26 equivalence
A27 Metadata / Explain projects A21/A25 or A16/A17/A19 without owning them
```

Cross-cutting constraints：

- identity/currentness constrains A2、A4-A12、A20-A24；
- order/null/missing/numeric constrains A6-A13、A16-A19、A26；
- resource admission constrains every allocating/expanding operation；
- structured failure/quiescence constrains every operation narrative；
- public/internal boundary constrains all lowering relations；
- evidence supports claims but不成为任何production parent。

## 12. Candidate lifecycle models

### 12.1 Composition generation

```text
SOURCE_SET_EXPECTED
    -> COLLECTING_ROUNDS
        -> COMPLETE_INPUT
            -> VALIDATED_MODEL
                -> GENERATED_OUTPUT
                    -> CONSUMER_COMPILED

Any validation/generation/compile failure
    -> BUILD_FAILED
    -> no valid partial composition may be consumed
```

`GENERATED_OUTPUT`是derived build output，不是新的schema事实源。Schema删除、重命名或version
变化要求full replacement；stale output不能进入`CONSUMER_COMPILED`。

### 12.2 Runtime configuration

```text
UNFROZEN
    -- successful Soma.configure --> FROZEN_EXPLICIT
    -- first Group/Table access --> FROZEN_DEFAULT

FROZEN_* -- configure --> CONFIGURATION_FROZEN failure
```

`_metadata()`只观察，不触发freeze。Freeze后的effective budget、ForkJoinPool选择和compression
policy在ClassLoader runtime lifecycle内不变。

### 12.3 Group operation guard

```text
IDLE
    -> ADMITTED(operation token)
        -> BOUND
            -> EXECUTING / STAGING
                -> QUIESCING
                    -> IDLE
```

Same Group overlap在`IDLE -> ADMITTED`前失败；same Group callback reentry与nested parallel在取得
新guard前失败。任何structured failure都必须最终回到`IDLE`。

### 12.4 Pipeline

```text
REUSABLE_SOURCE
    -> OPEN_LINKED_NODE
        -> VALIDATING_INTERMEDIATE
            -> CLAIMED_PREDECESSOR + ONE OPEN_CHILD
        -> VALIDATING_TERMINAL
            -> CONSUMED
                -> ADMITTED_AND_BOUND
                    -> COMPLETED_OR_FAILED

Invalid invocation before legal claim
    -> remains OPEN_LINKED_NODE

Any intermediate/terminal on claimed node
    -> PIPELINE_ALREADY_CONSUMED
```

Pipeline不拥有bound root；binding只发生在terminal。Reusable source不被claim；linked node只允许
一个successful successor或terminal。每个invalid invocation是否发生在claim前，必须由Signature/
Execution现有合同机械投影，implementation不能自行产生多种consume语义。

### 12.5 View / Editor

```text
UNBOUND_REUSABLE_CARRIER
    -> ACTIVE(owner, operation, participant, epoch)
        -> INVALID
            -> REBOUND_TO_NEW_EPOCH
```

旧alias在可检测边界访问失败；Java 8无法可靠检测同participant后续epoch中的全部escaped alias，
因此application保存borrowed object本身属于明确unsupported usage。`fetch()`产生A13 detached object。

### 12.6 StateRoot

```text
CANDIDATE_ROOT or PREVALIDATED_FINAL_COMMIT
    -> ALL_THROWING_WORK_COMPLETE
        -> ATOMIC_DESCRIPTOR_PUBLICATION(version n)
            -> STABLE_BOUND_READ
                -> SUPERSEDED_AFTER_QUIESCENCE
                    -> GC / representation reuse under next exclusive commit
```

Published logical state不再原地发生可能失败的结构改变。受控journaled mechanism只允许在
exclusive final commit窗口执行已证明non-throwing的bounded writes；其visible effect仍是一次
atomic descriptor publication，且metadata/ordinary reader不能看到partial state。

### 12.7 Resource lease

```text
ESTIMATED
    -> RESERVED
        -> IN_USE
            -> TRANSFERRED_TO_RETAINED / HANDED_OFF_RESULT / RELEASED

Failure at any post-reserve phase
    -> RELEASED before structured failure returns
```

Estimate不足可以形成conservative rejection，但不能形成publication后的补记账或negative balance。

## 13. Main narrative N1 — Schema to generated capability

### 13.1 Parent promise

Application声明一次完整schema后，compiler必须生成唯一、完整、类型安全且与runtime精确匹配的
capability；非法或不完整schema不能形成部分可用产品。

### 13.2 Same-level narrative

```text
Build host identifies the complete schema source set
    -> establishes full-source-set handshake and clean output
        -> processor collects the exact package composition
            -> builds A15 immutable schema model and symbol table
                -> validates declaration, type, role, name and capability invariants
                    -> generates A2 complete composition surface and manifest
                        -> javac compiles against exact runtime contract version
                            -> consumer receives one coherent capability universe
```

### 13.3 Owner and transitions

- Build host拥有“输入是否完整、旧输出是否已替换”的边界事实；
- Processor拥有schema semantic validity、generated symbol与diagnostic；
- Runtime/processor version handshake拥有linkage compatibility；
- Consumer compile成功只宣称type/linkage成立，不宣称runtime/performance已成立。

### 13.4 Failure mapping

- invalid schema/name/type -> stable compiler diagnostic；
- missing full-source-set/stale manifest/version mismatch -> build failure；
- source generation I/O/compiler failure -> entire composition build失败；
- 不允许fallback reflection、partial registry或保留旧generated member。

### 13.5 Child narratives

Annotation parsing、Value graph traversal、name collision analysis、source emission与manifest
fingerprint可以分别展开，但只有当它们保护独立不变量或形成边界时才成为实现抽象。

## 14. Main narrative N2 — Runtime state acquisition

### 14.1 Parent promise

Application能以最短路径取得default state，或显式创建隔离state；同一Group中每种Table type只有
一个instance，configuration与allocation行为可预测。

### 14.2 Same-level narrative

```text
Application optionally configures SOMA
    -> runtime validates and freezes global policy
        -> application obtains default or explicit A4 SomaGroup
            -> Group returns the unique generated A5 Table facade
                -> Table initially exposes empty published state without payload allocation
                    -> first reserve/add performs checked materialization and publication
```

未显式configure时，第一个Group/Table access先按versioned conservative policy冻结default。
Repeated accessor只返回同一semantic instance，不创建新的Table state。

### 14.3 Invariants

- configuration只freeze一次；
- default Group per composition/ClassLoader identity稳定；
- explicit Group彼此隔离；
- Table facade identity不随StateRoot更新改变；
- no eager payload allocation只是实现/资源合同，不意味着capacity语义缺席；
- Group/Table依赖GC，不建立manual release或global live registry。

## 15. Main narrative N3 — Query terminal

### 15.1 Parent promise

User从Table/Field/Index direct source表达lazy computation；terminal在一个稳定published state上，
以语义等价、资源受控的方式产生完整detached result或scalar outcome。

### 15.2 Same-level narrative

```text
Application builds A9 linked pipeline
    -> terminal validates arguments, owner, dependency and lifecycle, then atomically consumes receiver
        -> A20 admits the participating SomaGroup
            -> binds current A21 roots and immutable statistics
                -> A16 forms BoundOperation from immutable Canonical meaning
                    +-- A18 interprets bound semantics on independent evidence state（evidence-only）
                    +-- A17 normalizes and chooses PhysicalPlan + ResourceEstimate
                            -> A25 admits peak memory, cardinality and tasks
                                -> A20 creates operation-local ExecutionFrame
                                    -> A19 executes sequentially or through A26
                                        -> canonical result is reduced/materialized
                                            -> A13 detached result is handed off
                                                -> workers quiesce, frame/leases/guard release, pipeline completes
```

### 15.3 Semantic barriers

- opaque callback不能被分析、合并、下推或改变invocation order；
- sort/distinct/top/limit等stateful node建立明确barrier；
- Index substitution、leaf pruning、fusion与compression kernel只能改变cost；
- source encounter order来自bound root，不来自hash bucket或worker completion；
- materialization必须在callback/data work前完成可知的container/array/resource preflight。

### 15.4 Outcome

- empty/missing predicate result是normal outcome；
- scalar、Optional、List/array/result carrier遵守各自null contract；
- callback exception映射为`CALLBACK_FAILED`；
- failure无partial result，返回/抛出前operation已quiescent。

### 15.5 Explain refinement

`_explain()`走validation、Group admission、binding、planning与resource estimate，但不执行callback/
data kernel；它返回A27 detached diagnostic projection并消费one-shot pipeline。

## 16. Main narrative N4 — Table-local mutation

### 16.1 Parent promise

一次point或Selection mutation只能把一张Table的合法published state转换为另一个合法published
state；任何可恢复失败保持old state完整，Result真实对应最终publication。

### 16.2 Common mutation narrative

```text
Validate invocation and mutation capability
    -> admit Group and bind old StateRoot
        -> resolve callback-free early outcome/failure (reserve no-op, duplicate add, point missing)
            -> otherwise derive operation upper bound
            -> admit conservative selection + staging + candidate + scratch + result peak
                -> locate/evaluate/freeze target membership and canonical order
                    -> collect application input or run Editor callbacks into admitted staging
                        -> derive payload, Key, Index, compression and accounting changes
                            -> complete every recoverable allocation/hash/codec validation
                                -> establish VALIDATED_COMPLETE transition
                                    -> perform one non-throwing atomic publication
                                        -> publish truthful Update/Remove result
                                            -> invalidate callbacks, quiesce and release
```

### 16.3 Point refinements

- **reserve**：checked target -> candidate capacity/Chunks -> publish；below capacity no-op；
- **add**：copy detached carrier values -> validateKey/duplicate -> append candidate -> publish；
- **update(Key)**：missing返回matched=0/changed=0且不admit/callback；命中后先admit single-record
  worst-case peak，再进入Editor staging -> non-Key validation -> affected sidecars -> publish；logical
  no-op不改version；
- **remove(Key)**：missing返回removed=0；命中后freeze `R/T` compaction mapping -> 删除removed
  membership -> 将tail payload与所有sidecar locator从`T`调整到`R` -> publish；成本与
  Index数量和受影响Bucket大小相关，不是每次重建全Table Index。

Repeated add是多次独立atomic operation，不形成隐式transaction。

### 16.4 Selection refinements

- pipeline先完整evaluate并freeze最终membership；
- opaque predicate使exact membership未知时，callback前按bound input upper bound保守admit；
- update callback只写columnar write set；all callbacks完成后才选择prevalidated PLAIN leaf commit或
  candidate publication；logical no-op不复制Chunk且不发布；
- remove先冻结dense compaction move plan与operation-scoped locator projection；replacement Key/Index
  从old sidecar直接投影final survivor membership，完成全部分配后，PLAIN路径再无分配执行row move和
  trailing reference clear；
- encoded/overlay与indexed update不能证明bounded non-throwing时继续使用candidate，不以原位路径
  弱化compression、Index或zero-publication；
- parallel callback仍按canonical failure arbitration；
- selection太大而无法staging时在publication前`RESOURCE_LIMIT_EXCEEDED`；
- 不能为降低scratch而拆成partial publish batches。

### 16.5 Correctness boundary

Atomicity覆盖payload、Key、Index、compression与managed accounting。它不覆盖application referent
内部修改、callback side effect、另一个Table或external I/O。

## 17. Main narrative N5 — GroupBy and binary relation

### 17.1 Parent promise

schema-known Table可以进行typed GroupBy；同一Group的两个不同generated Table type可以进行
binary Equality/Cross Join。两者保持明确的null、missing、duplicate、order、cardinality与
resource contract，且全程query-only。

### 17.2 GroupBy narrative

```text
Bind one Table root
    -> validate key/aggregate capability
        -> form A16 Group logical semantics
            -> estimate groups and peak
                -> A17 chooses hash/sort/Index-assisted strategy
                    -> execute key equality and aggregate
                        -> order groups by first bound encounter
                            -> return typed detached Group result
```

Null key形成normal group；一个terminal只选择一个aggregate；physical hash/sort order不能成为
result order。

### 17.3 Equality Join narrative

```text
Validate same composition and same SomaGroup
    -> admit the single Group operation boundary
        -> bind left/right StateRoots together
            -> build typed equality components and Join kind
                -> normalize predicates and analyze Table dependency
                    -> prove safe pushdown / Index substitution / residual
                        -> estimate output and memory peak
                            -> choose lookup/hash/other admitted physical algorithm
                                -> execute with logical left/right order
                                    -> project typed detached result or scalar
```

### 17.4 Relation invariants

- null永不Join match；Outer missing不是Field null；
- duplicate产生完整Cartesian matches；
- Inner/Left按left order，同一left按right order；Full最后输出unmatched right；
- Semi/Anti按existence对每个left最多输出一次、保持left order、right duplicate不放大，只返回
  left ReadStream capability；
- Cross Join必须显式maxOutputRows并在work前checked product；
- predicate pushdown必须保留每个Join kind语义，callback永不下推；
- Relation不返回mutation lineage、不继续multi-way Join、不持有Pair。
- V1没有alias/self-Join/self-Cross；left/right identity来自两个不同generated Table type。

## 18. Main narrative N6 — Parallel refinement

### 18.1 Parent promise

`parallel()`只改变physical participation，不改变query/mutation的logical semantics、numeric tree、
order或failure；terminal保持同步，资源与worker lifecycle有界。

### 18.2 Same-level narrative

```text
Receive the same admitted A19 physical work
    -> validate configured/common ForkJoinPool availability
        -> partition canonical input into fixed ordinal ranges
            or select admitted Chunk ordinals as morsels
            -> reserve bounded tasks and per-participant scratch
                -> submit at most P-1 drainers behind start gate
                    -> caller and workers consume operation-local queue
                        -> arbitrate failure by phase and canonical ordinal
                            -> merge by fixed range/tree order
                                -> cancel if required and wait for quiescence
                                    -> hand off the same logical outcome
```

### 18.3 Invariants

- caller是participant；active participants不超过effective P；
- no per-record task、新pool、alternate Executor或hidden sequential fallback；
- saturated same pool调用仍可由caller progress；
- floating operation使用与sequential相同canonical tree；
- structured failure返回前workers不再访问operation state；
- callback内parallel terminal为nested parallel failure。
- scalar Chunk-morsel count/sum只维护O(Chunk count) partial，不先materialize O(rows) locator membership。
- ordered `long[]`无predicate按Chunk prefix写固定range；pure typed predicate的parallel路径先按Chunk
  count，再由caller形成checked ordinal prefix并写入互不重叠range，不按worker完成顺序合并。

## 19. Main narrative N7 — Compression representation transition

### 19.1 Parent promise

Compression可以降低某些Chunk leaf的retained/scan cost，但不能增加普通用户的模型负担或改变
logical value、Index、order、failure与mutation semantics。

### 19.2 Same-level narrative

```text
Operation touches or seals an eligible Chunk
    -> inspect immutable statistics and AUTO/OFF policy
        -> estimate benefit, rebuild/overlay peak and update rate
            -> choose PLAIN or an admitted representation
                -> build encoded candidate / sparse overlay synchronously
                    -> validate logical equivalence and affected Index
                        -> publish representation with the same StateRoot transition
                            -> dispatch future kernels once per Chunk
```

### 19.3 Boundaries

- active tail/hot Chunk可保持PLAIN；AUTO不保证压缩；
- no background compressor、whole-Table surprise或application-specified codec；
- ordinary Object reference只PLAIN；
- codec/token不进入Field equality、Key/Index identity或metadata stable ABI；metadata只投影不泄漏
  codec的plain-equivalent/current-representation/savings summary；
- forced codec correctness与AUTO cost decision使用不同evidence。

## 20. Main narrative N8 — Failure, quiescence and invalidation

### 20.1 Parent promise

任何operation失败都必须产生truthful、stable、sanitized outcome；不能留下partial result/state、
active worker、leaked lease/guard或仍冒充current的borrowed object。

### 20.2 Same-level narrative

```text
Detect failure at the earliest owning boundary
    -> classify logical operation and stable failure code
        -> choose canonical failure by phase / Field / element / work ordinal
            -> prevent or stop remaining publication-capable work
                -> cancel and wait for all workers
                    -> discard candidate / partial result
                        -> release temporary leases and Group guard
                            -> invalidate View / Editor / Plan / locator
                                -> throw sanitized Structured Failure
```

### 20.3 Defense boundary

- public/generated input、owner、version、resource与lifecycle使用真实校验；
- runtime mutation使用具有完整信息和拒绝权的Owner校验；
- internal derivation中关闭后不影响正确性的条件才可用assert；
- unexpected JVM Error不包装为normal SOMA failure，只做best-effort cleanup；
- callback/Comparator/arbitrary mapped distinct `equals/hashCode`抛出的ordinary Exception统一由
  outer operation包装为`CALLBACK_FAILED`；由当前operation
  runtime产生且hidden provenance匹配的scope/missing/reentry等SOMA failure保留原code，application
  replay/foreign provenance则包装；
- callback external side effect不声称回滚。

## 21. Invariant → Owner → Defense → Evidence proof chains

| ID | Invariant | Unique final Owner | Earliest sufficient defense | Failed-state contract | Primary evidence |
|---|---|---|---|---|---|
| INV-01 | Composition使用完整source set，生成结果无stale/partial member | Schema/processor boundary | build handshake、clean output、aggregating validation、manifest/version handshake | build整体失败，无valid partial composition | clean/full、delete/rename、late-round、version negative |
| INV-02 | Unsupported capability从generated type缺席；Java 8开放marker不能伪造合法owner/provenance | Signature Owner（depends-on Schema eligibility） | schema capability matrix、generated exact member selection与phase-1 provenance validation | normal misuse compile failure；手写/foreign marker稳定`INVALID_ARGUMENT`，不进入planner | source golden、`javap`、compile-negative与foreign-marker runtime negatives |
| INV-03 | 同一Group每种Table type一个instance，不同Group隔离 | Storage Group identity Owner | private construction、supported-source unforgeable composition token、concurrent-safe Group accessor registry-by-type | same-package fake/null/foreign token不能发布instance；foreign owner operation失败；无losing live Table | identity consumer、construction negatives、concurrent first accessor、multiple Group、foreign owner tests；不外推为same-JVM privileged-code sandbox |
| INV-04 | Configuration只freeze一次且effective policy稳定 | Execution configuration Owner | configure/first-access state machine | `CONFIGURATION_FROZEN`，已有policy不变 | ordering、race、metadata-no-freeze tests |
| INV-05 | Published Table header、payload、Key、Index、compression、statistics、accounting同logical generation | Storage StateRoot Owner | candidate complete validation或prevalidated final commit + single descriptor/header publication | old state/version完整或new state完整 | fault injection、root/sidecar/accounting comparison |
| INV-06 | Logical size/capacity/raw locator是checked 32位结构域；count/cardinality/memory/stateVersion是checked 64位累计域；capacity不因remove下降 | Storage representation Owner | exact int API/type、paged directory、widened checked arithmetic、no implicit shrink | 结构请求超限以`RESOURCE_LIMIT_EXCEEDED`、累计溢出以`ARITHMETIC_OVERFLOW`在allocation/publication前失败 | tiny-Chunk、near-int boundary、long cumulative、million real、remove capacity |
| INV-07 | Key唯一、non-null eligible、发布后immutable | Storage Key Owner | compiler eligibility、add duplicate check、Editor setter absence | duplicate/invalid add不发布；rekey不可表达 | compile-negative、collision/zero/null/runtime tests |
| INV-08 | Index是authoritative values的同版本唯一派生access path | Storage Index Owner | generated equality/hash、point affected-Bucket maintenance、Selection one-pass rebuild、root-co-publication | old Index/payload保持一致，无partial posting、duplicate membership或second truth | collision/null/singleton↔multi/repeated/move/rebuild/randomized property tests |
| INV-09 | Linked pipeline不能branch；合法terminal只消费一次并在terminal-start绑定current root | Execution pipeline Owner | intermediate atomic claim、operation validation、consumed flag、bind after admission | invalid pre-claim调用保持open；claimed node复用稳定失败 | lifecycle/branch/owner/currentness positive/negative |
| INV-10 | View/Editor只在声明callback scope有效，Editor只stage non-Key values | Execution callback-scope Owner | private constructor、owner/token/thread/epoch guard、setter generation | scope violation；Table state unchanged | construction/escape/cross-owner/thread/epoch、fetch tests |
| INV-11 | Mutation成功一次publish，失败zero publication，Result对应published facts | Execution publication Owner | frozen membership、full staging、preflight、non-throwing commit | no partial result/root/version/sidecar；workers quiescent | every fault point、no-op、parallel mutation differential |
| INV-12 | Optimizer只改变cost，不改变logical result/callback/failure；reference不消费normalized/physical decision | Planning rewrite Owner | Canonical/Bound/Normalized separation、typed properties、fixed phases、barriers、rewrite admission | conservative plan或structured preflight failure；无reference/unsupported fallback | bound reference differential、property/fuzz、golden explain |
| INV-13 | Group/Join保持key/null/missing/duplicate/order/cardinality合同 | Logical relation Owner（Planning必须保留） | typed relation IR、Join-kind rules、residual tracking、checked output | no partial relation result；resource/argument failure稳定 | all Join kinds、Cartesian、outer truth、algorithm differential |
| INV-14 | Table没有隐式业务顺序；同一bound state的canonical order稳定 | Storage order Owner | Chunk/live slot order、deterministic compaction mapping、ordered Index normalization | failure不改变order；成功order按mapping | remove/Index/parallel/sort/tie property tests |
| INV-15 | Sequential/parallel numeric、membership、publication与non-resource failure等价；mode-specific resource/interrupt failure显式 | Execution parallel Owner（depends-on Logical numeric contract） | fixed range/tree、canonical merge/failure frontier、same IR | failure前cancel/quiesce，no alternate result/fallback | P=1/2/4/16 differential、floating bits、resource、interrupt/rejection |
| INV-16 | Retained + temporary + result peak受global checked budget约束；Group GC最终释放retained accounting | Execution resource Owner | versioned frozen budget、conservative estimate、atomic leases、Phantom token/ReferenceQueue | `RESOURCE_LIMIT_EXCEEDED` before irreversible work；no leak/double release | accounting balance、peak/failure injection、GC reachability/profile calibration |
| INV-17 | Normal absence/no-op与contract failure分离，failure不可由application伪造或重放 | Failure Owner | generated signature、runtime-only construction、hidden current-operation provenance、stable mapping/precedence | truthful normal Result或sanitized structured failure | every code、phase precedence、callback origin/replay/security |
| INV-18 | Ordinary referent与external side effect不被SOMA虚假纳入atomicity | Blueprint product-boundary Owner（Storage/Execution实现） | reference storage contract、no reflection/deep copy、explicit docs/API | Table state可zero-publication，但external effect不声称回滚 | reference identity/mutation boundary、three journeys |
| INV-19 | Metadata/Explain只投影允许信息，不成为第二事实源或控制面 | Logical diagnostic Owner | detached snapshot、allowlisted categories、unstable text boundary | diagnostic failure不修改state，不执行callback/data kernel | API diff、secret/physical leak negatives、no-callback tests |

“Unique final Owner”不要求全部checking code位于一个class。例如INV-05的allocation、hash、codec
可以分散执行，但只有A24 publication boundary有权宣称new root成立。

## 22. Quality responsibility allocation

| Defense layer | 它必须承担的正确性责任 | 不能推给测试的内容 |
|---|---|---|
| Blueprint / Design | 定义语义、边界、Owner、不变量与成功主张 | 测试不能猜测产品到底想做什么 |
| Java type / generated surface | 让illegal capability、wrong lineage与constructor尽量不可表达 | runtime test不能补偿一个永远存在的错误public method |
| Processor construction | 只从完整、validated schema发布完整composition | consumer test不能修复stale/partial generation |
| State ownership / encapsulation | 限制谁能修改、何时有效、谁能发布 | 测试不能替代private boundary与atomic logical-state publication |
| Runtime validation | 在最早掌握完整context的Owner拒绝owner/currentness/resource错误 | assert和happy-path test不能保护external input |
| Mutation/publication protocol | 预检、staging、candidate、non-throwing publish、zero partial state | fault test只能证明protocol，不能充当protocol |
| Reference interpreter | 独立表达IR正确语义并裁判optimizer/parallel | example tests不能穷举所有rewrite组合 |
| Scheduler/resource manager | bounded work、deterministic merge、quiescence、lease balance | stress test不能让unbounded design自动安全 |
| Tests / profiles / Conformance | 证明上述生产防线、observable contract与性能主张成立 | 不拥有production事实、语义或state transition |

原则：生产设计负责“如何保持正确”，测试负责“凭什么相信它确实保持正确”。

## 23. Evidence architecture around abstractions

| Evidence family | Primary abstractions/narratives | Claim protected |
|---|---|---|
| Compiler positive/negative | A1/A2/A6-A12/A15，N1 | schema legality、capability absence、Java 8 inference |
| Generated golden + `javap` | A2/A11，N1 | exact public shape、constructor/access boundary |
| State property tests | A5/A7/A8/A21-A24，N2/N4/N7 | root coherence、Key/Index、compaction、compression equivalence |
| Fault injection | A20/A24/A25/A14，N4/N8 | zero publication、failure precedence、resource/guard release |
| Reference differential | A16-A19/A26，N3/N5/N6 | optimizer/algorithm/parallel semantic equivalence |
| Lifecycle/currentness | A4/A9-A11/A20/A21，N2/N3/N8 | one-shot、scope、binding、Group serialization、invalidation |
| Concurrency/replay | A20/A26，N6/N8 | bounded participation、deterministic failure、quiescence |
| Accounting/profile | A22-A26，N3-N7 | managed peak、allocation/GC、hot path、AUTO policy |
| Security/package consumer | A2/A14/A15/A27 | injection/path/context/version/artifact boundary |
| Canonical product journeys | A0-A27组合 | public API自然性、责任边界与end-to-end closure |

测试组织优先围绕抽象承诺和state transition，不围绕private method数量。一个高证据密度测试可以
同时覆盖多个method；同一invalid state不在每个method重复维护第二套判断。

## 24. Assert、internal failure 与 structured failure boundary

| Boundary | Candidate policy |
|---|---|
| Schema/application/public argument | 真实compiler/runtime validation与stable diagnostic/failure |
| Generated owner/lineage/lifecycle | type absence优先；无法静态排除时runtime structured failure |
| Resource/version/current state | A20/A25真实preflight；不得依赖assert |
| Candidate/root/sidecar relation | publish前真实validation或可证明的non-throwing construction |
| Internal derivation已由上游证明的条件 | 无副作用且关闭后不影响语义/数据/资源时可用assert |
| Unexpected impossible state会损坏数据或产生虚假结果 | 真实internal failure/Error path；不得仅assert或返回空结果 |

Implementation必须至少有一条验证路径启用Java assertions，但assert coverage不进入public
failure compatibility contract。

## 25. I0-I8 implementation fill map

| Slice | Primary abstraction blanks | Narratives established | Invariant proof required before exit |
|---|---|---|---|
| I0 | A1/A2/A15；A3最小linkage；artifact/version boundary | N1 | INV-01、INV-02、INV-04的build/config carrier部分 |
| I1 | A4/A5/A6/A7/A9-A11/A13/A14/A20-A22/A24最小vertical slice | N2、N3、N4、N8最小闭环 | INV-03-07、INV-09-11、INV-17的narrow proof |
| I2 | A6-A8全type breadth；A22/A23 32位结构域/64位累计域 breadth | N1/N2/N4 breadth | INV-02、INV-05-08、INV-14、INV-18 |
| I3 | A9/A10/A13/A16-A19/A27 explain baseline | N3 | INV-09、INV-12、INV-14及numeric contract |
| I4 | A11/A14/A20/A24/A25完整mutation/failure/resource | N4、N8 | INV-10、INV-11、INV-16、INV-17 |
| I5 | A12/A16-A19 relation nodes/result families | N5 | INV-12、INV-13、INV-14、relation resource bound |
| I6 | A19/A20/A25/A26 parallel refinement | N6、N8 parallel path | INV-11、INV-15、INV-16、INV-17 |
| I7 | A22 compression representation；A27 metadata/explain closure | N7及N3 diagnostic refinement | INV-05、INV-08、INV-12、INV-16、INV-19 |
| I8 | A0-A27组合、public Examples、package/release projection | 三个canonical journeys | INV-01至INV-19在production evidence中闭合 |

每个slice不是“实现一批class”，而是建立一段可运行、可失败、可验证的父子叙事。一个abstraction
blank只有同时具备Owner、lifecycle、invariant defense与evidence，才算填完。

## 26. Implementation-time change protocol

### 26.1 M0 — Local mechanism revision

适用：class/package、IR node representation、Chunk rows、hash mixing、codec threshold、task
multiplier、scratch layout等。

允许在直接Owner内凭evidence修改，前提是：

- 不改变任何`SEMANTIC_BASELINE`；
- 不改变跨组件lifecycle/failure/resource contract；
- parent narrative仍完整；
- test不冻结被替换的private shape；
- old path被删除而非并行保留。

更新production code/test/profile/Conformance，不必为每次局部替换建立新Temporary。

### 26.2 M1 — Candidate abstraction or narrative revision

适用：A15-A27边界、primary parent、Owner协作、Operation阶段、StateRoot/candidate separation、
logical/physical lowering等需要改变，但用户语义不变。

触发条件：

- 当前抽象无法完整兑现直接父叙事；
- 两个兄弟争夺同一state/decision；
- invariant没有掌握完整信息与拒绝权的Owner；
- failure/lifecycle无法闭合；
- 为保持当前分解不断增加adapter、duplicate state或特殊分支；
- independent evidence证明候选mechanism无法守住contract。

流程：

```text
counterexample / profile / implementation friction
    -> identify active node, direct parent and siblings
        -> classify affected invariant and Owner
            -> record candidate delta and rejected alternative
                -> revise abstraction/narrative/proof chain
                    -> targeted Design + Conformance review
                        -> replace old path and resume slice
```

若变更跨多个Design Owner或增加长期surface，建立bounded Temporary；否则可在当前slice change
record中完成。Readiness执行targeted delta review。

### 26.3 M2 — Product semantic revision

适用：public/generated signature、capability、result/order/null/missing/numeric/failure、atomicity、
resource visibility、application/SOMA responsibility、V1 scope变化。

必须：

1. 停止当前slice；
2. 记录不能由现有Design解决的真实反例；
3. 建立Temporary并请求Product Owner裁决；
4. 更新Blueprint/唯一Design Owner/Plan/Gate；
5. 重新执行正式promotion/readiness；
6. 不保留旧alias或双production path。

Implementation convenience、局部代码量或“未来可能”不能单独触发M2。

### 26.4 Maturity transition

```text
CANDIDATE_CORE / IMPLEMENTATION_HYPOTHESIS
    -> implementation + matching evidence
        -> EVIDENCE_VALIDATED

evidence contradiction
    -> REVISED(new candidate)
        -> replacement closure
            -> RETIRED(old candidate/path)
```

在pre-release阶段不承诺失败草案compatibility。最终release compatibility只保护正式admitted
surface，不保护本文的internal candidate name。

## 27. Candidate abstraction bad-smell review

| Candidate smell | Risk | Candidate ruling / guard |
|---|---|---|
| `Operation`变成持有所有service/state的God object | 叙事协调与事实Owner混杂 | A20只拥有一次operation lifecycle/token/guard/cleanup；StateRoot、Planner、Resource各自Owner。它可以是叙事概念，不强制一个巨型class |
| `StateRoot`变成处理所有行为的God aggregate | state coherence Owner吞并execution | A21只拥有published logical facts与generation coherence；mutation/planning/execution分别由A24/A17/A20拥有 |
| `Planner/Optimizer`名称掩盖多个阶段 | rewrite、cost、resource互相反向定义 | A17拥有physical decision contract；内部按fixed phases拆pass，但没有证据前不预建public或plugin hierarchy |
| `Relation`形成generic universal engine | GroupBy、Join、Pair与普通stream能力被抹平 | A12只是semantic family；GroupBy和Join保持不同child narrative/type capability，不生成public universal Relation API |
| `Generated Surface`成为第二套产品模型 | generator自行创造语义 | A2只投影正式Schema/Logical/Signature；所有capability必须追溯上游，generated code不拥有新事实 |
| Semantic Key/Index与physical hash/posting同名混淆 | physical token泄漏到API/optimizer correctness | A7/A8保持logical identity；internal实现使用明确`sidecar`/representation命名，二者只通过`lowers-to`关系连接 |
| Global memory manager形成Group registry或runtime container | 生命周期泄漏、Group无法GC | A25只记账reservation/lease与frozen policy，不持有live Group/Table graph |
| Reference Interpreter成为fallback | 双production path、性能/语义漂移 | A18只通过test harness/independent copy evidences；production unsupported/optimizer failure不能fallback |
| Metadata/Explain承载业务逻辑 | unstable implementation成为第二事实源 | A27只提供allowlisted observation；application不得parse explain或用physical diagnostic驱动业务 |
| “Manager/Factory/Provider”泛化命名 | 无语义wrapper与透明转发膨胀 | implementation命名必须对应本文语义或完整子叙事；helper不因复用可能性升级 |
| 每个IR node/class都有独立测试 | test冻结偶然shape、重构成本高 | 以node semantic property、rewrite rule、narrative和differential corpus为证据单位 |
| 为off-heap/mmap预建backend SPI | 提前支付不存在的变体 | 只保留A22 representation/kernel seam；future capability重新admission，不创建placeholder |

当前未发现必须新增public abstraction才能关闭的坏味道。A15-A27均保持candidate status，实施中
最需要监控A20、A21、A17三处是否出现Owner膨胀。

## 28. Canonical journey J1 — Scheduling

### 28.1 Problem projection

Application拥有Job、Machine、ProcessingOption与调度业务状态；SOMA拥有这些状态在一个Group
中的Table representation、Key/Index、query与Table-local mutation。跨Table调度决策和补偿仍由
application拥有。

### 28.2 Narrative

```text
Schema declares Job, MachineState and ProcessingOption Tables
    -> N1 generates typed composition
        -> application creates/obtains one SomaGroup
            -> N2 reserve + repeated add publishes initial roots
                -> Job/Option/Machine sources form typed predicates and Equality Join
                    -> N5 narrows candidates with Index substitution where legal
                        -> N3 maps/sorts/top into a detached scheduling decision
                            -> application validates domain decision
                                -> separate N4 point mutations publish affected Tables
                                    -> application compensates if later cross-Table step fails
```

### 28.3 Abstraction coverage

A1-A17、A19-A25、必要时A26；A18差分验证；A27解释Index/pushdown/Join choice。

### 28.4 Invariants exposed

- Option relation用普通Table和endpoint ID，不使用Child ownership；
- Join同Group、null-never-match、duplicate/order明确；
- decision必须detached，Pair/View不escape；
- 多Table修改不是SOMA transaction；
- Key/Index与mutation publication保持同root；
- million-row candidate selection/profile由G9证明。

## 29. Canonical journey J2 — Simulation

### 29.1 Problem projection

Application拥有simulation clock、event semantics和step orchestration；SOMA拥有Event/EntityState
Tables的存储、query与Table-local transition。Table没有隐式event业务顺序。

### 29.2 Narrative

```text
Schema declares Event and EntityState Tables
    -> N1 generates typed sources
        -> application publishes initial event/state roots
            -> Event source explicitly sortedBy(eventMinute, tie-break key)
                -> N3 returns the next detached Event
                    -> application derives simulation decision
                        -> N4 atomically updates one EntityState Table
                            -> N4 separately removes the consumed Event
                                -> application advances clock or compensates per simulation protocol
```

Parallel只在不改变event ordering/side effect的eligible query中显式使用；`forEachOrdered`也不能替代
application event-state protocol。

### 29.3 Invariants exposed

- explicit sort/tie-break而非Table insertion order；
- detached Event在root变化后仍是稳定值；
- event remove missing/no-op与failure分离；
- callback side effect与Table publication边界明确；
- state update和event remove之间的cross-Table atomicity由application处理。

## 30. Canonical journey J3 — Real-time dispatch

### 30.1 Problem projection

Application拥有实时请求、外部设备状态、deadline和最终dispatch effect；SOMA拥有pending work、
eligible relation与machine runtime state的高频in-process computation。

### 30.2 Narrative

```text
New requests are validated and added to PendingJob Table
    -> exact Index narrows the relevant pending domain
        -> typed predicates and same-Group Join combine eligible option/state
            -> N5 pushdown and Index substitution reduce physical inputs
                -> optional N6 parallel execution computes deterministic scores
                    -> N3 returns detached top decision
                        -> application performs external dispatch effect
                            -> N4 publishes local Table updates according to application protocol
```

### 30.3 Invariants exposed

- ordinary external/device object不作为Key/Index；hot facts显式编码primitive/Value；
- Executor与budget在first access前freeze；
- parallel不会隐藏线程创建或改变score/numeric/tie；
- external dispatch成功/失败不由SOMA冒充transaction；
- result materialization、Join output与scratch在执行前resource admission；
- metadata/explain只用于诊断，不决定dispatch业务。

## 31. Journey review conclusion

三个journey都可以沿当前public semantic model和A0-A27闭环，不需要：

- hidden API；
- ChildTable/cascade；
- cross-Table transaction；
- public Column/Chunk；
- per-stream Executor；
- generic non-equality/multi-way Join；
- Loader/Batch；
- persistence/distributed runtime。

它们共同验证：schema generation、state publication、query/relation、detached decision、
Table-local mutation和application-owned orchestration构成一致产品叙事。I0-I8/G1-G10已经为
Java 8 production topology建立相称的correctness、performance与package evidence；精确边界由
Conformance拥有。

## 32. Formal surface admission

本文作为正式、窄职责Design Owner成立：

- **capability/consumer**：跨Design architecture comprehension与proof-chain routing；consumer是
  implementer、test author、reviewer与AI Agent；
- **Owner/lifecycle**：从正式产品语义到implementation skeleton；随V1 architecture与evidence
  审慎演进；
- **现有surface不足**：Design index只拥有文档authority，八个分责Owner各自拥有精确合同；若把
  dynamic narrative/invariant proof map复制到它们，会形成多份平行事实；
- **Blueprint trace**：BP-1至BP-15，尤其BP-8、BP-11、BP-12、BP-15；
- **failure boundary**：本文不创造runtime failure；M0/M1/M2协议决定变更何时留在Owner内部、
  何时必须停止并请求Product Owner；
- **evidence**：three journeys、Owner/cycle review、I0-I8 fill map、production Conformance feedback。

本文只路由精确合同，不复制Schema/Storage/Logical/Planning/Execution/Failure/Architecture的API、
算法或failure matrix。Implementation Plan使用A/N/INV ID作为attention aid，不把它们变成每个
commit的官僚性清单。

## 33. Definition of Done

- [x] 方法论、正式Owner与Temporary边界明确；
- [x] 抽象准入/停止粒度和maturity定义；
- [x] L0-L4 hierarchy与A0-A27 registry；
- [x] primary parent与typed cross-relation无已知循环；
- [x] 每个core abstraction说明identity、lifecycle、invariant、boundary与non-responsibility；
- [x] N1-N8覆盖construct/publish/derive/transition/failure/invalidate；
- [x] INV-01至INV-19形成Owner/Defense/Evidence proof chain；
- [x] quality responsibility没有全部塞给test；
- [x] I0-I8 fill map与M0-M2 implementation change protocol；
- [x] bad-smell review与防腐边界；
- [x] scheduling/simulation/real-time dispatch三个journey闭环；
- [x] Product Owner授权最终全局一致性审核；
- [x] formal promotion；
- [x] Temporary replacement closure与targeted readiness delta review。

## 34. Formal conclusion

当前正式判断：

- SOMA 的基础架构可以由A0-A27、N1-N8与INV-01至INV-19形成一副完整、可实施、可验证的
  skeleton；
- skeleton固定semantic continuity、Owner、lifecycle、invariant与change discipline，不永久冻结
  internal class decomposition；
- implementation应成为在这些位置建立production defense/evidence的过程，而不是按class list
  机械填充；
- implementation evidence可以按M0/M1审慎修正candidate internal abstraction，但不能静默改写
  正式产品语义；
- 本次最终全局一致性审核关闭了已识别P1语义空洞，没有留下需要implementation自行裁决的
  已知P0/P1；
- 本文完成本身不授权I0；Product Owner 已于 2026-08-03 在本文之外单独授予完整 V1
  implementation authorization，current状态由Conformance拥有。

## 35. Physical Execution Engine M2 responsibility routing

M2是A18 Physical Plan与A20 ExecutionFrame内部责任的M1级修订，不改变Blueprint产品语义：

```text
A18 Physical Plan
    owns Pipeline / Segment / Breaker / Kernel / Morsel decisions
        -> A25 ResourceEstimate owns whole-operation conservative peak
            -> A20 ExecutionFrame owns admitted actual state
                -> N6 parallel work lifecycle preserves canonical semantics
```

Planning是decision Owner，Execution是actual-state/lifecycle Owner，Storage是authoritative data Owner，
Mutation是publication Owner，Reference Interpreter仍直接解释Bound semantics。若实施要求改变public result、
order、callback、resource visibility、failure、mutation atomicity或增加新产品capability，则不再是M1修订，
必须按26.3停止并请求Product Owner裁决。
