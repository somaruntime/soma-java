# SOMA Vectorized Physical Pipeline 与 Morsel-Driven Execution 治理专题

类型：Active Bounded Temporary / Candidate Design Governance

状态：`ACTIVE / R2_CANDIDATE_DESIGN / NOT_FROZEN / IMPLEMENTATION_NOT_AUTHORIZED`

Owner：第一阶段正式基线之上的physical pipeline、representation-aware kernel、morsel-driven
execution扩展问题空间、Candidate Design、Slice Map与专题关闭责任

Product Owner激活：2026-08-12

最后更新：2026-08-12

## 1. 专题结论与当前授权

本专题已经由Product Owner从queued intent正式激活为当前唯一bounded Temporary。它的当前任务不是
立即扩写kernel，而是先形成一套完整、自洽、可实施且不过度设计的Candidate Design：

```text
第一阶段正式基线
    -> current-state与capability inventory
        -> Candidate physical pipeline / morsel design
            -> feasibility、风险与性能假设验证
                -> Baseline Freeze与Implementation Readiness审查
                    -> Product Owner另行决定是否授权implementation
```

当前唯一active slice为`R2 — Candidate Design与Baseline Freeze准备`。本次激活：

- 允许读取正式Owner、production code、tests、benchmark与既有profile evidence；
- 允许修改本Temporary及项目状态入口，允许建立nonproduction、可删除的设计验证证据；
- **不允许**修改production source、generated/public API、正式Design或Conformance结论；
- **不构成**Baseline Freeze、Implementation Readiness、Implementation Authorization、Qualification或
  Release Authorization。

如果Candidate Design需要改变Blueprint、public/generated semantics、numeric/failure/resource
visibility、two-artifact topology、dependency或Java 8方向，必须暂停并等待Product Owner裁决。

## 2. 为什么现在值得治理

[第一阶段正式晋升](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)已经证明：

- PLAIN primitive hot path可以由逐值visitor/stage dispatch下降为once-per-Chunk typed loop；
- typed predicate、Field projection与terminal sink可以在有限physical kernel中融合；
- parallel scalar aggregate可以使用O(Chunk count) partial，避免O(rows) locator prefix；
- 这些收益可以在不改变Canonical semantics、public API与Reference oracle的情况下取得。

第一阶段也刻意只准入Table `count`、integral Field `sum`与ordered `long[]` materialization。继续逐个
增加case会同时触及Planning、Execution、Chunk representation、resource admission、parallel scheduler、
numeric tree和diagnostics。如果缺少完整设计，很容易形成：

- terminal入口、PhysicalPlan与kernel各自判断eligibility的多套事实；
- PLAIN、encoded与parallel各自维护一套executor；
- 一类operation一个class的膨胀层级或boxed universal Batch DAG；
- 为追求局部吞吐而破坏order、numeric、failure、resource与fallback合同；
- benchmark显示更快，但代码规模、维护成本和真实场景反而退化。

因此，下一步不是“全面向量化”，而是先定义一套能够长期、安全、按证据扩展的physical execution
architecture。

## 3. 治理目标

### 3.1 总目标

在保持Java frontend、Canonical Logical IR与用户可观察语义不变的前提下，设计并按后续授权逐步建立：

> 由PhysicalPlan一次选择、由ResourceEstimate完整计费、由ExecutionFrame执行、能够感知Chunk
> representation、以bounded morsel驱动并行、且只对已证明cell进行specialization的finite physical
> pipeline。

目标不是让所有operation都拥有专用kernel，而是让值得专用化的正常路径获得更低dispatch、boxing、
materialization和数据搬运成本，同时让不适合的shape稳定回到现有optimized path。

### 3.2 产品与工程目标

1. **高性能**：减少热循环中的逐元素抽象成本、无意义中间materialization和O(rows)并行准备；
2. **低分配**：优先使用borrowed Chunk representation与operation-local bounded partial；
3. **可预测**：所有selection、scratch、partial、result和task在work前完成checked admission；
4. **正确性不降级**：Reference differential、canonical order、numeric、failure和quiescence合同不变；
5. **可扩展但有限**：用明确capability matrix逐cell准入，不建立为未来能力服务的通用框架；
6. **可维护**：Physical decision、representation access、execution lifecycle与evidence各有唯一Owner；
7. **真实有效**：优化由SOMA benchmark与reference application profile驱动，不以microbenchmark单点胜出
   代替产品收益。

## 4. 范围与明确排除

### 4.1 本专题范围

- current Java frontend已经能够表达的query与read-only terminal；
- Canonical/Bound/Normalized到PhysicalPlan、ResourceEstimate和ExecutionFrame的physical lowering；
- Table、Field、IndexSelection正常读取路径中适合finite specialization的部分；
- PLAIN、encoded与overlay Chunk representation的直接读取或保守fallback边界；
- primitive typed predicate、projection、aggregate和ordered materialization；
- sequential与explicit parallel下的Chunk morsel、worker-local partial和deterministic merge；
- pipeline breaker、callback/stateful barrier、eligibility与fallback合同；
- internal `_explain()` / metadata可观察边界，但不冻结私有诊断字符串；
- 10K、1M、10M与三个reference application中受影响的正确性、CPU、allocation、temporary和吞吐证据；
- 最多一个由profile证明有必要的复杂operator候选。

### 4.2 明确排除

- 新增或修改Library user public/generated API；
- 新的Canonical semantic node、public Batch/Vector、prepared query或workflow语言；
- SOMA Engine、JSON frontend、跨进程服务或第三production artifact；
- runtime bytecode generation、JIT compiler、Java Vector API或native/SIMD dependency；
- off-heap、mmap、spill、distributed execution与异步terminal；
- 通用DAG、arbitrary pipeline cache、plugin/SPI或generic Executor framework；
- point/Selection mutation机制重写；mutation只承担回归验证；
- 同时重写GroupBy和Join；复杂operator最多选择一个，也允许一个都不选；
- 为未来cell预建placeholder、feature flag、兼容层或空抽象；
- 将同机性能证据外推为跨硬件SLA或一亿行release承诺。

## 5. 正式基线与本专题非责任

以下内容已经是正式事实，不在本专题重新定义：

- Canonical IR是唯一semantic truth，Reference interpreter是独立test-only oracle；
- PhysicalPlan是kernel、access、partition和ResourceEstimate的decision Owner；
- ExecutionFrame只在resource admission成功后创建并消费已选择的plan；
- Row range与Chunk morsel共享一个bounded caller-participating ordinal-work lifecycle；
- PLAIN kernel只能operation-scope借用typed arrays，不能形成第二份storage truth；
- callback、stateful barrier、terminal-start binding、order、numeric、failure和quiescence合同；
- unsupported shape进入现有optimized path，不转Reference，也不产生新的runtime unsupported failure；
- production topology仍恰好为`soma-runtime`与`soma-processor`。

本专题若发现这些正式事实无法支持目标，应把问题反馈到唯一Design Owner并等待裁决，不在Temporary中
静默覆盖它们。

## 6. Candidate目标叙事

本专题要验证和细化的候选主线是：

```text
generated Java source
    -> existing Canonical / Bound / Normalized semantics
        -> PhysicalPlan selects one finite pipeline decision
            -> ResourceEstimate admits complete peak
                -> ExecutionFrame binds operation-local state
                    -> representation-owned Chunk access
                        -> fused typed stages within proven boundary
                            -> scalar sink / ordered output / local partial
                                -> canonical merge and detached result
```

其中：

- **Physical pipeline** 是同一个admitted plan内有限、可融合的physical stage sequence，不是第二套IR；
- **Kernel** 是某个已准入`shape × representation × type × terminal`的执行机制；
- **Morsel** 默认是一个现有logical Chunk ordinal，不自动引入sub-Chunk或新storage unit；
- **Pipeline breaker** 是不能安全融合或重排的callback、stateful、order、shape或resource边界；
- **Partial** 是operation-local、checked admitted、按canonical ordinal合并的中间结果；
- **Fallback** 是现有optimized executor，不是Reference interpreter或静默降级语义。

这些术语在R2冻结前都是Candidate vocabulary，不预先要求新增同名production type。

## 7. R2必须裁决的设计问题

### 7.1 Capability matrix与准入单位

建立`Source × Representation × Type × Predicate × Terminal × Mode`矩阵，并对每个cell标记：

- current production mechanism；
- expected consumer与profile provenance；
- semantic/barrier条件；
- candidate kernel与fallback；
- result、order、numeric和null要求；
- retained/temporary/allocation/task成本模型；
- 需要的correctness、resource、failure和performance evidence；
- `ADMIT / DEFER / REJECT`结论。

矩阵是设计和实施准入工具，不生成Cartesian-product代码，也不要求填满所有cell。

### 7.2 最小physical pipeline模型

需要裁决：

- 当前`PhysicalPlan`是否已经足够承载source、predicate、projection、sink、representation与merge decision；
- 哪些stage可以融合，哪些必须成为breaker；
- 如何避免terminal、planner与kernel重复eligibility；
- 如何避免一类operator一个长期抽象；
- sequential与parallel怎样消费同一decision而不复制executor；
- internal explain怎样展示选择与fallback，而不把L4机制冻结成compatibility contract。

默认推荐先扩展现有data-only decision和有限private kernel，不新增通用pipeline object model；只有代码形状
反例证明现有surface无法承载时，才提出最小新抽象并完成Surface Admission。

### 7.3 Representation-aware execution

分别说明PLAIN、encoded和overlay：

- 哪些值可以直接borrow、iterate、run-length aggregate或dictionary evaluate；
- 哪些操作必须保守逐值访问；
- 何时允许operation-local bounded decode；
- 为什么不得无预算整体解压或复制authoritative storage；
- cost model如何在direct encoded kernel与existing fallback之间选择；
- representation transition发生后，旧decision为何不会跨terminal失效。

### 7.4 Morsel、parallel与deterministic merge

- Existing Chunk是默认morsel；只有skew/profile证明必要时才讨论sub-Chunk range；
- scheduler只拥有submission/start/rejection/cancel/interrupt/quiescence；kernel只拥有ordinal work；
- count、integral sum、floating aggregate、match/materialization分别需要什么partial/result策略；
- canonical order、floating tree、first/tie、failure arbitration怎样保持；
- tasks、participants、partials与output如何在执行前checked admission；
- nested parallel、pool unavailable和interrupt怎样沿用现有structured failure。

### 7.5 Resource与低分配合同

设计必须区分：

- retained storage；
- borrowed representation；
- temporary reservation；
- actual Java allocation；
- result memory；
- worker/task/partial peak。

每个候选kernel要给出conservative peak公式和actual allocation验证方法。不能先执行callback/scan以获得
exact size，也不能在admission之后重新规划出未计费scratch。

### 7.6 Complex operator准入

只有R3–R5的证据稳定后，才允许比较：

- GroupBy worker-local partial；
- Equality Join probe-side morsel；
- 本版本不准入复杂operator。

裁决依据是正式benchmark/reference application hotspot、可控资源、代码规模和维护收益，不是路线图的
“完整性”。GroupBy与Join不得同时激活。

## 8. 治理与实施Slice Map

### R2 — Candidate Design与Baseline Freeze准备（当前active）

#### R2.1 Current-state inventory

- 列出当前plan/execution/representation/scheduler carrier与Owner；
- 建立完整operation/capability matrix和现有fallback map；
- 把第一阶段及历史profile按`CURRENT / REUSABLE / NEEDS_REFRESH`分类；
- 识别重复决策、潜在God object、过度抽象与证据空白。

#### R2.2 Candidate architecture

- 完成第7章全部裁决；
- 固定最小抽象、主Narrative、Owner、lifecycle与依赖方向；
- 为关键性质建立`Invariant -> Owner -> Production Defense -> Evidence`证明链；
- 明确每个后续slice的eligible cells、fallback、删除项与Stop Rule。

#### R2.3 Design validation

- 只在必要时建立nonproduction、可删除的feasibility fixture；
- 用现有production shape验证Java 8 type/code-size、resource formula与关键反例；
- 用现有baseline或一次定向profile验证优先级，不在设计阶段进行全面性能优化；
- 审查Candidate Design是否引入第二套IR、planner、executor、representation或resource truth。

#### R2.4 Freeze与Readiness审查

- Product Owner审核未决裁决与风险接受；
- 将稳定合同按唯一Owner映射到Planning、Execution、Architecture、Core及必要的Storage Design；
- 形成精确implementation slice、Gate、恢复点与Temporary closure方案；
- Baseline Freeze与Implementation Authorization分别裁决，不能合并推断。

R2退出前不得修改production source。

### S1 — AUTO encoded-aware aggregate与predicate（待授权）

第一优先候选是encoded Field sum、simple typed predicate及predicate + integral sum。不得通过关闭AUTO、
无预算整体解压或复制PLAIN storage truth获得收益。Slice只实现R2明确准入的cells。

### S2 — Finite primitive kernel matrix（待授权）

按profile逐cell准入primitive type、pure typed predicate、count/match/min/max/sum、能够保持既有numeric
tree的floating aggregate与ordered primitive materialization。没有消费者、稳定收益或清晰resource
formula的cell不进入production。

### S3 — Morsel-driven terminal expansion（待授权）

扩展typed-only full traversal terminal的worker-local partial；保持caller participation、bounded drainers、
canonical ordinal merge与no O(rows) membership materialization。默认不新增sub-Chunk。

### S4 — 至多一个复杂operator（条件式、待授权）

根据届时profile选择GroupBy、Equality Join或不准入。必须作为单独slice，并在开始前重新确认Design、
resource上界与性能收益。

### S5 — 全局资格与Temporary closure（待授权）

覆盖10K/1M/10M、PLAIN/AUTO、sequential/parallel、eligible/fallback、allocation/temporary/CPU、Reference
differential、三个reference application、ABI/two-artifact/package与source delivery。稳定事实晋升后删除
本Temporary。

## 9. 每个实施Slice的四类Claim

| Claim | 必须成立的事实 |
|---|---|
| 功能质量 | eligible与fallback结果、order、numeric、null、failure和currentness与Reference/正式合同一致 |
| 代码质量 | hot path同层可读；没有dead branch、复制eligibility、case hierarchy或场景特供补丁 |
| 架构质量 | Canonical、PhysicalPlan、representation、ExecutionFrame、scheduler与resource Owner保持唯一 |
| 工程质量 | Java 8、two artifacts、build/generation、tests、benchmarks、package和最终源码证据可重放 |

性能是功能外的独立准入条件：每个cell必须在冻结的主机、规模、表示与噪声边界下，相对被替换的现有
optimized path呈现稳定、有实际意义的收益，并且不造成其他正式normal path的实质退化。精确阈值在对应
slice开始前由baseline和噪声确定，不预设跨operation统一百分比。

## 10. 关键不变量

1. Canonical IR与Reference oracle保持独立且不因性能路径改变；
2. PhysicalPlan一次拥有全部physical eligibility、kernel、partition与resource decision；
3. Execution只消费admitted plan，不重新规划或建立第二套ResourceEstimate；
4. Representation access由Chunk/Storage Owner提供，borrowed state不逃逸operation；
5. Row range与morsel共享现有parallel lifecycle，不建立第二个scheduler protocol；
6. callback/stateful breaker不可因优化被重排、重复调用或跳过；
7. order、numeric tree、null、failure precedence、terminal-start binding与quiescence保持不变；
8. O(N) result/selection/hash与O(chunks/participants) partial在work前checked admission；
9. unsupported shape稳定走现有optimized path，不转Reference、不静默改变语义；
10. 每个新增cell同时拥有Reference differential、resource/failure evidence与profile收益；
11. 不因内部specialization扩张public surface、artifact、dependency或产品边界；
12. 一次只有一个active implementation slice，完成replacement closure后才进入下一项。

## 11. 风险、坏味道与Stop Rule

### 11.1 主要风险

| 风险 | 典型坏味道 | 防线 |
|---|---|---|
| 语义漂移 | optimizer/kernel直接解释Java callback或改变order | Canonical + Reference differential + breaker |
| 决策重复 | terminal、planner、kernel各自判断eligibility | PhysicalPlan唯一decision Owner |
| 表示泄漏 | execution直接依赖codec私有状态 | representation-owned bounded access seam |
| 组合爆炸 | type/operator/representation Cartesian classes | finite admitted matrix + private closed kernels |
| 并行分叉 | Row与morsel各有cancel/quiescence协议 | shared ordinal-work lifecycle |
| 资源漏算 | admission后新增decode/partial/output | complete ResourceEstimate before work |
| benchmark过拟合 | microbenchmark更快、journey退化 | matrix + reference journeys + allocation/profile |
| 过早复杂化 | 通用Batch DAG、sub-Chunk、codegen、SPI | evidence-triggered Surface Admission |

### 11.2 必须暂停的条件

出现以下任一情况，停止扩张并请求Product Owner或正式Owner裁决：

- 需要改变Blueprint、public/generated API或Canonical semantics；
- 正确性、资源与性能无法同时成立；
- 必须新增dependency、production artifact、runtime codegen或通用framework；
- Candidate需要第二套planner、executor、representation truth或parallel lifecycle；
- encoded优化只能依赖无预算整体decode或跨terminal cache；
- 单一slice持续扩张到多个复杂operator；
- profile不能证明新增复杂度具有稳定收益；
- 测试和审查持续增加，但Design未决项、DoD或可信Evidence不再改善。

## 12. R2 Definition of Done

R2只有在以下事实全部成立后才能申请Baseline Freeze：

- current carrier/Owner/call-path/fallback inventory完整且与代码一致；
- capability matrix覆盖当前公开query surface，并对候选cell给出`ADMIT / DEFER / REJECT`；
- physical pipeline、representation、morsel、partial、resource和diagnostic合同无关键歧义；
- 每项关键Invariant拥有Owner、Production Defense与计划Evidence；
- 后续S1–S5分别有有限范围、前置条件、四类Claim、Stop Rule和恢复点；
- 设计没有第二套IR、planner、executor、scheduler、storage truth或resource model；
- 所有Product Owner裁决项已经关闭，或被明确排除而不阻止实施；
- Candidate Design能够逐项映射到正式Design Owner，但尚未提前修改正式Design；
- 完成一次整体一致性和过度设计审查；
- Product Owner分别裁决Baseline Freeze、Readiness与Implementation Authorization。

## 13. 完整专题关闭条件

本Temporary只有在获授权的implementation全部结束后才退役：

1. 正式Design接管长期合同；
2. production code只保留一种Physical decision与parallel lifecycle主路径；
3. 被替换路径、实验fixture、adapter、flag和过期benchmark已经关闭；
4. 每个已准入cell的四类Claim与性能条件均有最终源码证据；
5. S5全局qualification通过，Conformance准确记录claim boundary；
6. project/AGENTS/Design/Engineering/Conformance入口一致；
7. 本Temporary删除，不作为平行Design或历史过程档案保留。

## 14. 当前下一步

从`R2.1 Current-state inventory`开始。它只读取和分类当前代码与证据，交付：

- carrier / responsibility / coupling matrix；
- operation capability与fallback matrix；
- 第一阶段之后的主要profile机会与证据新鲜度；
- 值得进入R2.2的真实设计问题，以及应当直接拒绝的过度设计。

R2.1完成前不创建production abstraction，也不预写S1–S4代码。
