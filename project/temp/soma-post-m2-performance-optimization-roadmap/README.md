# SOMA Post-M2 性能优化路线提案

类型：Temporary / Performance Governance Proposal / Future Topic Router

状态：`ACTIVE / T1_T2_COMPLETED / T3_DENSE_REMOVE_ACTIVE / T4_QUEUED / IMPLEMENTATION_AUTHORIZED`

日期：2026-08-12（2026-08-13由Product Owner激活T1-T4）

Owner：M2完成后的性能现状摘要、候选热点优先级与未来专题启用路径

> 本文不是新的性能承诺，也不覆盖正式Design。Product Owner已授权Codex按T1-T4顺序完成
> 自主设计、审核、实施与本地提交；一次只允许一个active slice。各slice的Candidate Design、
> current baseline、实施结论与资格证据由本目录承接，稳定事实最终晋升至正式Design/Conformance，
> 然后本Temporary完成replacement closure。T5-T7仍是未授权候选。

## 1. 为什么需要这份提案

SOMA已经完成Canonical Logical IR / Execution M1、Vectorized Physical Pipeline和Physical
Execution Engine M2。当前执行主线、Owner和资源生命周期已经统一，继续以零散hot-path patch
推进优化的边际收益和架构风险都开始恶化。

当前真正缺少的不是另一轮无边界“全面调优”，而是一个回答以下问题的候选路线：

1. M2完成后，哪些路径已经足够好，不应继续消耗设计与实现预算；
2. 哪些路径仍然主导正常规模下的wall-clock、CPU、allocation或temporary peak；
3. 哪些问题可以在现有正式Design内优化，哪些必须建立新的bounded治理专题；
4. 后续专题应以什么顺序完成设计、验证、冻结和实施；
5. 怎样防止为了单个benchmark重新制造第二套IR、执行器、scheduler或场景特供路径。

本文的独立消费者是Product Owner和Codex/Agent。现有Conformance分别拥有各轮证据，但不负责
维护跨专题的未来优先级，因此需要这份有期限的Temporary路由。它不覆盖任何正式事实Owner。

## 2. 目标、范围与排除项

### 2.1 目标

- 从全局执行引擎视角整理Post-M2性能状态；
- 识别对百万行、千万行正常路径最有价值的候选优化专题；
- 给出设计先行、证据驱动、一次只激活一个专题的建议顺序；
- 保护Canonical语义、资源受控、deterministic result与Reference differential；
- 为未来“一亿行以上”的North Star保留可优化空间，但不把愿景冒充当前资格。

本提案服务Blueprint中的高性能、低分配、资源受控和可继续扩展目标，特别是BP-8、BP-9、
BP-10、BP-12与BP-15；精确目标仍以[正式Blueprint](../../blueprint/README.md)为准。

### 2.2 当前不做

- 不修改公开/generated API、Blueprint或既有Design语义；允许现有Design范围内的production实现优化；
- 不新增artifact、dependency、scheduler、runtime codegen或public SPI；
- 不重新设计Canonical Logical IR；
- 不准入SOMA Engine、set-based mutation、ordered access或application frontier；
- 不把提高CPU占用率本身当作目标；
- 不声明跨机器SLA、“世界最快”或一亿行资格；
- 不授权GitHub Release、Package、签名或正式release声明。

## 3. 当前状态

### 3.1 已经成立的执行基础

[M2 P6最终资格](../../conformance/physical-execution-engine-m2-p6-final-qualification.md)已经证明：

```text
Canonical / Bound / Normalized
    -> operation-family Physical Pipeline
        -> finite streaming Segment(s)
        -> optional finite Breaker
        -> terminal sink or mutation handoff
        -> one whole-operation ResourceEstimate
            -> admission
                -> one ExecutionFrame
                    -> specialized typed kernels
                    -> bounded caller-participating scheduler
```

当前长期基础包括：

- Row、Field、Mapped、Primitive、GroupBy、Relation和Selection query使用同一Physical执行主线；
- Reference Interpreter直接解释Bound semantics，不依赖production PhysicalPlan；
- operator-local state只在whole-operation resource admission之后由Frame创建；
- Storage继续拥有authoritative data，Mutation继续拥有validation与atomic publication；
- sequential与parallel共享caller-participating lifecycle，不存在第二个scheduler；
- P1-P6已完成，当前没有active implementation slice。

因此下一轮优化应当扩展现有有限Physical topology，而不是重新搭建执行体系。

### 3.2 当前10M资格快照

以下数字来自2026-08-12的M2 P6容量资格；固定主机和工具链边界沿用全面性能前沿记录中的
Apple Silicon / Corretto 8环境。它们用于判断数量级和热点，不是SLA：

| Operation | 10M representative median | 当前判断 |
|---|---:|---|
| Integral Field sum | 2.802 ms | 已经是高效直接kernel，不是当前优先级 |
| Typed Table filter | 33.562 ms | 正常scan路径，继续优化需有明确profile证据 |
| Field top | 92.390 ms | 有限Breaker已成立，当前不是主导热点 |
| Low-cardinality GroupBy | 165.174 ms | 尚有participant-local aggregation空间 |
| High-cardinality GroupBy | 492.188 ms | cardinality、hash state和merge成本明显 |
| Equality Join count | 1.628 s | 当前最重要的只读计算热点 |
| Semi Join | 1.328 s | 与Relation build/probe/lookup成本同源 |
| Selection update | 465.310 ms | 需先区分matching、write-set和publication成本 |
| Selection remove | 2.679 s | 当前最重要的mutation热点 |

全部路径在该资格中保持correctness/fingerprint `PASS`。数字只代表一次固定主机容量快照；后续
专题启动时必须重新记录当前HEAD、JVM、数据分布与fresh-JVM baseline。

### 3.3 已知剩余profile边界

[全面性能前沿资格](../../conformance/v1-performance-frontier-qualification.md)与后续正式治理共同
指向以下剩余成本：

- Relation equality、build/probe/Index lookup与deterministic result merge；
- GroupBy high-cardinality hash state、result construction与parallel merge；
- dense Selection remove的locator move、Key/Index projection和publication；
- repeated atomic add中的Index growth/rehash、Chunk finish/encode与StateRoot publication；
- encoded RLE random decode、locator-to-chunk lookup和nullable/reference equality；
- opaque callback/mapped路径中的per-element Java call、multi-field access和结果materialization；
- conservative ResourceEstimate与实际allocation/temporary之间的精度差。

[Selection mutation write-set治理](../../conformance/v1-selection-mutation-write-set-governance.md)已经
退出PLAIN update/remove的touched-Chunk全leaf copy，因此后续不能把已删除的旧问题继续描述为
current hotspot；当前关注点应当是dense move、sidecar projection和publication。

### 3.4 当前不值得优先深挖的路径

除非新profile推翻现有证据，暂不优先投入：

- direct integral Field count/sum/materialization；
- exact Key/Index point lookup；
- integral natural sort/top；
- 普通Table count与低成本typed scan；
- 已经通过即时增量Key/Index维护闭合的普通point add/remove；
- 为小数据强制parallel；
- 仅为了减少几条guard、admission或generated carrier指令而弱化正确性。

这些路径仍需承担防退化回归，但不应占用下一个架构专题。

## 4. 候选优化专题与建议顺序

以下顺序是当前建议，不是不可变计划。每一项都必须由专题启动时的新profile重新确认。

| 顺序 | 候选专题 | 当前问题 | 候选方向 | 为什么独立治理 |
|---:|---|---|---|---|
| T1 | Relation Physical Execution | 10M Join约1.3–1.6 s，显式parallel收益不足 | participant-local build/probe或lookup state、morsel partition、deterministic merge、cost-based sequential/parallel choice | 涉及双输入资源、Join语义、顺序和parallel lifecycle，风险与潜在收益都最高 |
| T2 | GroupBy Physical Execution | high-cardinality约492 ms | participant-local aggregate state、capacity planning、bounded merge、primitive specialization | 可复用Frame/Morsel原则，但state、merge和failure不同于Relation，不应和T1一次实现 |
| T3 | Dense Selection Remove | 10M约2.679 s | 分解match、move plan、sidecar projection与publication；减少重复locator/Index work | mutation atomicity、zero-publication和failed-state边界不能由query优化顺带处理 |
| T4 | Construction / Index Build | 全面前沿中10M ingest约5.46–5.86 s，需按current HEAD重测 | reserve-aware growth、amortized Index build、Chunk finalization与publication batching的内部优化 | 可能触及bulk construction产品边界；不得静默引入public Loader/Batch |
| T5 | Compression-aware Random Access | RLE random decode与locator-to-chunk仍是profile热点 | codec/access cost model、run lookup辅助结构或representation choice refinement | 需要同时约束retained memory、ingest和scan，不能只优化单个Join benchmark |
| T6 | Callback / Mapped Execution | typed路径之外仍有Java callback和多Field读取成本 | 更准确的leaf demand、稳定shape specialization与低分配result sink | callback是不透明barrier，不能通过字节码分析或重排副作用换性能 |
| T7 | Resource Precision / Target Host | conservative reservation可能限制大结果；当前证据主要来自本机 | operator-specific bound校准、peak attribution、512 GiB目标主机资格 | 它提高容量可用性，不应抢在主要CPU/wall-clock热点之前 |

T1与T2可以共享正式Physical Pipeline词汇和底层工具，但建议保持两个独立implementation slice。
“共享抽象”只有在两者都出现相同、稳定的信息需求时才准入，不预建通用DAG、Exchange、Vector API
或distributed operator框架。

Selection remove的绝对时间高于Join，但T1仍暂列第一：Relation覆盖更广的只读组合能力，同时是
当前parallel参与者利用不足最明确的路径。若专题启动时的current profile表明真实工作负载由密集
remove主导，T3可以提前；排序必须由证据改变，不能由实现便利改变。

## 5. 首选后续专题：Relation Physical Execution

当前建议首先启动Relation专题。它具有最高的只读组合路径延迟和最明显的parallel潜力，同时已经拥有
Canonical/Bound/Physical/Reference边界，适合在不改变public API的条件下形成可测的纵向闭环。

### 5.1 建议目标

- 保持Equality/Cross、Inner/Left/Full/Semi/Anti的现有逻辑语义；
- 降低百万行、千万行Join的wall-clock与CPU work；
- 让`parallel()`只在cost model证明有益时采用participant-local work；
- 让build/probe/lookup、temporary state和merge在执行前可估算；
- 不改变canonical encounter order、duplicate/null合同或structured failure；
- Reference Interpreter继续是独立语义裁判。

### 5.2 设计前必须回答的问题

1. 当前时间分别消耗在binding、source reduction、build、probe/equality、Index lookup、downstream
   filter和result merge的多少比例；
2. 哪些Relation shape适合Index lookup，哪些适合hash build，哪些应保持sequential；
3. participant如何拥有局部state，怎样在resource admission后创建并确定性合并；
4. duplicate、outer missing side、Semi/Anti短路和canonical order怎样保持；
5. data skew、null、high duplicate和极小输入怎样避免parallel放大成本；
6. whole-operation ResourceEstimate怎样覆盖所有同时存活的build/probe/merge状态；
7. 哪一组有限physical variants足够，不把M2重新扩张成通用数据库执行框架。

### 5.3 建议阶段

```text
R0 Current baseline and profile
    -> R1 Candidate Design
        -> R2 finite feasibility validation
            -> R3 consistency / over-design / readiness review
                -> R4 Product Owner freeze and implementation authorization
                    -> R5 bounded implementation slices
                        -> R6 qualification and replacement closure
```

#### R0：当前基线与profile

- 固定current HEAD、JDK、JVM参数、主机、schema、数据分布与fingerprint；
- 覆盖10K、1M、10M中的Join count、typed side filter、Semi、Anti以及至少一种Outer；
- 比较sequential与`parallelism = 1/2/4/8/16`，记录wall-clock、CPU、allocation、temporary/retained和GC；
- 使用CPU/allocation profile区分lookup、equality、decode、cursor、hash state与merge；
- 不在baseline阶段修改production实现。

#### R1：Candidate Design

- 只定义有限Relation Pipeline、Segment、Breaker/merge、Kernel、Frame state和Morsel责任；
- 从实际Information Demand推导partition/build/probe表示；
- 固定sequential/parallel选择、resource admission、deterministic merge与failure cleanup；
- 明确现有Physical Plan是否足够；只有缺少无法表达的正式信息时才提出最小扩展；
- 列出不支持和不做的算法，避免预建未来能力。

#### R2：有限可行性验证

- 首先验证一个普通Equality Join count和一个Semi/Anti路径；
- 与Reference Interpreter、当前production baseline和独立fingerprint差分；
- 至少包含uniform、skew/high-duplicate、empty/no-match和nullable side；
- 验证小规模保持sequential、大规模才采用并行的cost boundary；
- 验证temporary预检先于operator-local allocation，failure后无资源或state泄漏。

Spike代码在Design未冻结前不得成为production第二路径；验证结束后要么被正式实施吸收，要么删除。

#### R3–R4：冻结与准入

- 审查Blueprint↔Design↔code边界、Reference独立性、资源峰值和过度设计；
- 固定Candidate fingerprint、slice map、相对防退化阈值和退出证据；
- 由Product Owner明确完成Baseline Freeze和Implementation Authorization；
- 未授权前不得开始完整production迁移。

#### R5–R6：建议实施切片

1. sequential Relation operator responsibility与profile基线闭环；
2. participant-local build/probe和deterministic merge；
3. Semi/Anti/Outer、typed filter与downstream composition扩展；
4. cost model、resource precision和small/skew fallback；
5. full qualification、正式Owner晋升、Conformance与Temporary replacement closure。

每个切片必须同时关闭功能、代码、架构与工程质量；不得长期保留baseline/candidate双production路径。

## 6. 后续专题的共同证据合同

每个被激活的专题至少建立：

| Claim | 最小证据 |
|---|---|
| Correctness | Reference differential、independent fingerprint、边界分布、sequential/parallel等价 |
| Performance | current baseline、candidate、fresh JVM、median/variance、CPU与wall-clock共同解释 |
| Memory | retained、temporary reservation、participant allocation、heap/RSS分责，不混为“Table内存” |
| Resource | 所有operator-local state在admission后创建；checked arithmetic；失败释放 |
| Architecture | 一个Physical decision Owner、一套scheduler、无第二IR/adapter/fallback |
| Engineering | Java 8、targeted test、受影响benchmark、全仓qualification、clean diff与可恢复检查点 |

既有`15% + 2 ms`相邻A/B守卫可作为默认防退化参考，但专题必须根据changed family、噪声和
收益目标在冻结时确定精确Gate，不能由本文提前固化成跨专题永久阈值。

## 7. 总则与停止规则

1. **先profile，再Design，再实施。** 没有current profile，不根据旧报告直接改代码。
2. **一次只激活一个专题、一个slice。** 不并行改Relation、Group、mutation和compression。
3. **IR语义默认冻结。** 性能问题优先在Physical planning、operator、storage access和resource
   representation中解决；只有证据证明Canonical IR无法表达必要语义时才升级裁决。
4. **不追求虚假的满核。** 目标是更低wall-clock、可控总CPU、allocation与peak memory；小输入或
   skew下sequential可能是正确选择。
5. **不做场景特供。** Scheduling、Simulation或单个synthetic distribution只能暴露问题，不能拥有
   SOMA runtime特例。
6. **不以正确性换性能。** order、null、numeric、callback、failure、resource、atomic publication和
   currentness合同不得弱化。
7. **不预建未来框架。** 没有当前consumer时不增加通用DAG、Exchange、spill、Vector API、off-heap、
   mmap、distributed worker或codec插件体系。
8. **新surface必须重新准入。** public API、dependency、artifact、scheduler或持久状态一旦成为候选，
   停止实施并请求Product Owner裁决。
9. **证据停止变化时停止。** 重复测试、审查或微调不再产生新信息时，回到热点归因和DoD，不原地打转。
10. **资格不等于发布。** 任何优化通过也不自动授权对外性能声明或release。

## 8. 生命周期与启用方式

当前T1已激活，T2-T4按顺序排队；T5-T7仍保持`QUEUED / NOT_AUTHORIZED`。Product Owner于
2026-08-13授权在既有Design范围内完成T1-T4的自主设计、审核、实施、资格与本地提交，但不授权
push、release或对外性能声明。

未来启用某一项时：

1. Product Owner明确选择候选专题；
2. 在`project/temp/`建立该专题唯一的bounded Temporary；
3. 重新核验current HEAD和profile，不复制本文数字作为新baseline；
4. 完成Candidate Design与有限验证；
5. 通过一致性、过度设计与Implementation Readiness审查；
6. Product Owner冻结并明确授权后才进入implementation；
7. 稳定合同晋升到正式Design，事实证据进入Conformance；
8. 删除已完成使命的专题Temporary，并更新或退役本文。

如果Profile证明T1不再是最高价值路径，可以重新排序，但必须记录新证据；不能因为实现方便而
选择低价值专题。

## 9. 当前执行状态

| Slice | 状态 | 唯一专题事实 |
|---|---|---|
| T1 Relation Physical Execution | `PASS / CLOSED` | [T1 Candidate Design与资格](t1-relation-execution-candidate-design.md) |
| T2 GroupBy Physical Execution | `PASS / CLOSED` | [T2 Candidate Design与资格](t2-groupby-execution-candidate-design.md) |
| T3 Dense Selection Remove | `ACTIVE` | T2关闭后建立 |
| T4 Construction / Index Build | `QUEUED` | T3关闭后建立 |
| T5-T7 | `NOT_AUTHORIZED` | 仍只保留本提案中的未来路由 |

## 10. 正式事实入口

- [SOMA Java V1 Blueprint](../../blueprint/README.md)
- [Planning and Optimization Design](../../design/planning-and-optimization.md)
- [Execution and Concurrency Design](../../design/execution-and-concurrency.md)
- [Implementation Architecture Design](../../design/implementation-architecture.md)
- [M2 P6 Final Qualification](../../conformance/physical-execution-engine-m2-p6-final-qualification.md)
- [Performance Frontier Qualification](../../conformance/v1-performance-frontier-qualification.md)
- [Operator × Type × Distribution Qualification](../../conformance/v1-operator-type-distribution-performance-qualification.md)
- [Selection Mutation Write-set Governance](../../conformance/v1-selection-mutation-write-set-governance.md)
- [Scheduling Performance Governance](../../conformance/v1-scheduling-performance-governance.md)

上述正式Owner拥有产品、Design和证据事实；本文只拥有未来选路建议。
