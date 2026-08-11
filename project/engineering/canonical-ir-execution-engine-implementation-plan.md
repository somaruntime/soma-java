# SOMA Canonical Logical IR 与执行引擎实施计划

类型：Engineering / Implementation Plan

状态：`FROZEN BASELINE / READY_FOR_IMPLEMENTATION / IMPLEMENTATION_AUTHORIZATION_NOT_GRANTED`

正式事实源：是

Owner：Canonical Logical IR 与执行引擎M1责任替换的S1-S6顺序、exit evidence、性能守卫与停止条件

日期：2026-08-11

上游：[Design 总览](../design/README.md) ·
[规划与优化](../design/planning-and-optimization.md) ·
[执行、并发与并行](../design/execution-and-concurrency.md) ·
[Implementation Architecture](../design/implementation-architecture.md) ·
[核心抽象与叙事](../design/core-abstractions-and-narratives.md)

准入记录：[Canonical IR 与执行引擎正式晋升和实施准入审查](../conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)

> 本计划已经冻结，但不授权production修改。只有Product Owner另行明确授予本专题implementation
> authorization后，才能激活S1；I0-I8的历史授权不能自动扩张到本计划。

## 1. 目标与边界

目标是在不改变Java public/generated语义的前提下，把现有family-local IR、binding、planning与
execution耦合替换为唯一主线：

```text
Java lowering
    -> CanonicalOperation
        -> BoundOperation
            +-- Reference Interpreter
            +-- NormalizedOperation
                    -> PhysicalPlan + ResourceEstimate
                        -> resource admission
                            -> operation-local ExecutionFrame
                                -> specialized sequential / parallel execution
```

本计划是M1 internal responsibility revision，不新增产品能力、第三production artifact、dependency、
public/internal SPI、JSON/Workflow、dynamic schema、Batch/Loader或SOMA Engine implementation。

## 2. 实施原则

1. 采用纵向slice；同一operation family内同时替换lowering、binding、reference、physical planning、
   resource admission与production execution，不先堆一套不可执行的新IR；
2. 已迁移family在slice退出时只有一套Canonical semantics与一套physical decision path；bridge必须在
   本slice删除；
3. 尚未迁移family可暂时保留old path，但不得反向依赖new family或共享双重事实；
4. reference先消费同一Bound semantics，再准入production PhysicalPlan与parallel refinement；
5. 保留primitive、Field、Relation、Group specialized kernel，不以boxed universal interpreter换统一；
6. PhysicalPlan只拥有decision与ResourceEstimate；actual O(N) state只能在lease后进入ExecutionFrame；
7. 已通过且输入未改变的重型证据不重复运行；验证必须对应本slice新增风险。

## 3. 冻结基线与硬性不变量

实施前必须记录clean source/HEAD、Java 8 public/generated ABI与negative capability、deterministic full
regeneration、reference/optimized/parallel differential、resource/failure/operation provenance及当前
10K/1M/10M/FJSP基线。

Generated public declaration默认不变；generated method body到internal linkage可以改变，但必须由正式
Design解释、保持deterministic regeneration，并形成reviewed before/after delta。

硬性不变量：

- result、encounter order、null/missing、duplicate、numeric、callback、failure与publication等价；
- primitive hot path无boxing、reflection、per-row metadata lookup或O(N) object allocation；
- Canonical construction为`O(stages + literals)`，不随Table rows增长；
- Field direct source只有`TableSource + FieldProject`一种Canonical表示；
- `TypedLiteral`直接供typed leaf read/hash/equality消费，不复制第二个probe；
- reference、optimized sequential、parallel使用independent state exact comparison；
- no allocation/callback/irreversible work before conservative resource admission；
- 10K fixed cost、1M throughput/allocation与final 10M/FJSP没有无法解释的因果退化。

## 4. Slice plan

### S1 — 最小 Row 纵向闭环

目标：以Table scan、typed filter、count与exact Key/Index lookup证明新主线可执行。

工作：

- 复用compiled composition descriptor，建立compact Table/Field/Index logical identity；
- 建立direct typed-leaf `TypedLiteral`，切断Canonical predicate对generated probe/Table的依赖；
- 建立最小`CanonicalOperation + BoundOperation`；
- reference直接解释Bound scan/filter/count；
- production完成normalize、scan/Key/Index PhysicalPlan、ResourceEstimate、admission、ExecutionFrame与
  specialized executor；
- 删除本operation集合的old semantic field、lookup decision与bridge。

Exit evidence：predicate/null/in normalization、Key/Index duplicate/order、foreign owner、literal
failure、reference differential、admission-before-allocation、Java 8 ABI；10K typed filter/Index exact与
1M Table filter无因果退化。

### S2 — 完整 Row、Field、terminal 与 Selection

目标：Row/Field全部pipeline能力进入唯一Canonical→Bound→Physical→Execution主线。

工作：

- Table/Index source、Field projection、Row stage与全部terminal进入closed family；
- typed order改用logical Field identity；
- Field source统一lower为`TableSource + FieldProject`，physical planner保留direct Field kernel；
- one-shot claim保留在facade，execution mode进入ExecutionRequest；
- Selection update/remove复用selection semantics + mutation terminal；
- 先reference，再optimized sequential与parallel；direct point/control operation继续留在IR外。

Exit evidence：operation capability matrix、one-shot/failure precedence、Field fusion、Selection
zero-publication、三路differential、explain layer projection；10K Row/Field与1M
sum/filter/materialize/sort/top/slice无因果退化。

### S3 — Mapped 与 Primitive family

目标：共享Canonical semantic family，同时保留host reference shape与primitive unboxed physical path。

工作：

- arbitrary mapped result使用host reference shape；
- ordinary/relation mapped stage共享mapped semantics；
- primitive kind/conversion成为typed semantic fact；
- HostCallbackHandle只保留callback、kind与不可推导的issuing capability；
- reference与production分别消费同一Bound semantics；
- 退出Mapped/Primitive旧semantic carrier，保留specialized kernel。

Exit evidence：primitive conversion compile matrix、mapped null/equality/array component、callback
barrier/failure、1023/1024/1025 numeric边界与三路differential；10K fixed cost和1M mapped/aggregate/
stateful performance无allocation-class或complexity退化。

### S4 — Relation 与 Group 纵向闭环

目标：拆除Relation/Group facade、semantic、binding、planner与executor混合。

工作：

- facade只负责validation、claim与lowering；
- 建立binary Relation/Group canonical node/terminal与two-root BoundOperation；
- pushdown/residual进入Normalized，algorithm/access/build-side进入PhysicalPlan；
- hash/lookup/group state只存在于admitted ExecutionFrame和specialized operator；
- reference不消费production planning decision；删除relation-local重复Plan。

Exit evidence：所有Join kind、null-never-match、outer missing、duplicate Cartesian、canonical order、
pushdown/residual、Group key/order/aggregate、resource/failure与三路differential；1M Join与GroupBy无
完整pair materialization或算法退化。

### S5 — 执行引擎与 layer closure

目标：全部family共享唯一A20 lifecycle与physical planning入口，删除剩余layer inversion。

工作：

- Bound不持有locator buffer、cursor、parallel source等physical state；
- Normalized不持有probe、membership、Index container或worker fact；
- PhysicalPlan统一拥有access/kernel/algorithm/partition decision与ResourceEstimate；
- ExecutionFrame统一拥有lease后scratch、cursor、membership、worker、staging与cleanup；
- coordinator只协调validate/claim/guard/bind/plan/lease/execute/release，不成为God object；
- sequential/parallel细化同一PhysicalPlan；explain从四层snapshot投影；
- 删除old optimizer/executor adapter与重复decision path。

Exit evidence：binding currentness、no-work-before-admission、Index substitution、barrier、leaf/fusion、
physical differential、resource/fault injection、quiescence与no-old/new-carrier scan。性能只覆盖本slice
实际改变的family；无新信息不重复profile。

### S6 — Final qualification 与 Owner closure

目标：证明一体化治理没有改变产品并完成replacement closure。

工作与Exit：

- Java 8 deterministic full generation、public ABI、negative capability与reviewed generated-body delta；
- complete reference/optimized sequential/parallel differential；
- mutation/resource/failure/operation provenance；
- full non-publishing qualification与package consumer；
- final 10K/1M/10M performance frontier与100K FJSP；
- 一次bounded independent review；
- 更新正式Owner与Conformance，删除migration bridge；
- 保留最小permanent test-only lowering contract；
- G4/G5/G6/G7/G9受影响部分仍`PASS`，无双IR/双physical decision、性能因果退化或新release claim。

## 5. Permanent minimal lowering contract

一个package-private test-only fixture长期证明Canonical semantics不依赖generated facade object identity。
它只覆盖Table filter/count、Field projection/materialize、与Java lowering的structural/property等价以及
同一reference/production path结果等价。

它不解析JSON、不生成runtime schema、不成为production frontend SPI、module或artifact。若必须扩大到
general frontend builder、DAG或serialization才能测试，应触发stop rule。

## 6. Evidence budget

| Contract | S1 | S2 | S3 | S4 | S5 | S6 |
|---|---:|---:|---:|---:|---:|---:|
| Java 8 public/generated ABI | targeted | targeted | targeted | targeted | targeted | full |
| owner/provenance/one-shot | Row | full | mapped/primitive | relation/group | all | full |
| reference differential | minimal Row | Row/Selection | Mapped/Primitive | Relation/Group | all | full corpus |
| physical/parallel equivalence | scan/lookup | Row/Field | Mapped/Primitive | Relation/Group | all | full |
| resource/failure | literal/lookup | terminal/mutation | materialize/numeric | relation/group | frame/planning | full |
| fixed-cost performance | 10K | 10K | 10K | targeted | changed families | full 10K |
| scale performance | selected 1M | selected 1M | selected 1M | selected 1M | risk-triggered | full 1M/10M/FJSP |

重型证据只有source/compiler/runtime/script/machine/claim boundary改变时重取。10M/FJSP默认属于S6；中间
slice仅在algorithm、allocation class、resource peak或hot kernel实质变化时触发。

## 7. 性能回归政策

- correctness、allocation class或algorithmic complexity退化：立即阻断；
- 10K稳定回归：优先排查多余node、virtual dispatch、literal/probe copy与operation allocation；
- 1M稳定回归：排查PhysicalPlan、kernel、allocation与memory traffic；
- 10M/FJSP只确认规模/场景闭环，不为单次噪声反复改结构；
- 正确性/扩展性与性能出现真实产品取舍时暂停，由Product Owner裁决。

不设置跨硬件SLA，不把本机结果写成world-class、release或一亿行性能claim。

## 8. Stop rules

出现以下任一情况立即暂停并请求Product Owner：

- public/generated signature、operation语义、order/null/failure/atomicity需要变化；
- 需要第三production artifact、dependency或public/internal SPI；
- Canonical IR迫使boxing、reflection、per-row metadata lookup或O(N) node object；
- 已迁移family退出时仍需双semantic IR、双physical decision或compatibility adapter；
- reference必须消费optimizer/physical decision才能等价；
- resource admission只能在allocation、callback或不可逆work之后；
- Relation/Group只有缩减current capability才能迁移；
- 执行引擎收口要求重定义Storage、Index、Join/Group语义或parallel产品合同；
- 性能与正确性出现真实取舍；
- 工作扩张到JSON、Workflow、dynamic schema、Batch/set-based mutation或SOMA Engine；
- 重复测试/审查不再产生新evidence。

## 9. 防过度设计检查

每个slice提交前回答：

1. 新type是否拥有独立state、decision或lifecycle，而不只是转发？
2. 当前Java frontend/reference/planner/executor是否有真实consumer？
3. identity能否用现有compiled descriptor + ordinal表达？
4. callback property能否由kind/enclosing operation唯一推导？
5. 删除该抽象是否真的丢失Owner、resource boundary或evidence seam？
6. 是否因未来SOMA Engine预建当前不需要的surface？
7. 是否在semantic统一之外错误统一physical specialization？

只凭“未来也许复用”成立的class/interface/module不准入。

## 10. 激活规则

```text
Design promotion              PASS
Baseline freeze               PASS
Implementation readiness      READY
Implementation authorization  NOT_GRANTED
Active slice                  NONE
```

Product Owner明确授权后，S1成为唯一active slice；一次只推进一个slice。每个slice必须在exit evidence、
一次有界独立审查、Conformance更新与干净提交后才能进入下一项。授权不得自动包含第三artifact、dependency、
GitHub Release/Package、签名或正式发布声明。
