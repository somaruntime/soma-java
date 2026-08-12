# SOMA Finite Physical Pipeline与Chunk-Morsel Execution 冻结临时设计

类型：Active Bounded Temporary / Frozen Implementation Design

状态：`DESIGN_BASELINE_FROZEN / IMPLEMENTATION_READY / IMPLEMENTATION_NOT_AUTHORIZED`

Owner：本专题实施期间finite physical pipeline、representation-aware integral kernel、Chunk-morsel
execution与fallback/resource边界的唯一临时设计Owner

冻结日期：2026-08-12

正式Owner边界：本文件只在当前bounded topic内作为implementation input；长期合同仍由
[Planning](../../design/planning-and-optimization.md)、
[Execution](../../design/execution-and-concurrency.md)、
[Architecture](../../design/implementation-architecture.md)、
[Storage](../../design/data-model-and-storage.md)与
[核心抽象叙事](../../design/core-abstractions-and-narratives.md)拥有。实施与资格闭合后，稳定增量必须晋升
回这些正式Owner，本Temporary随即退役。

## 1. Design Intent

本冻结设计在不改变Java frontend与Canonical Logical IR语义的前提下，解决一个明确问题：

> 当前generic optimized executor以row/element为主要执行粒度；第一阶段finite kernel已经证明Chunk内融合
> 可以显著降低dispatch与并行准备成本，但terminal、representation和resource decision尚未形成一个可按
> 证据扩展的完整Owner。

目标不是“把SOMA变成通用向量数据库”，而是建立一个有限、可证明、可以逐cell准入的physical mechanism：

```text
one Canonical semantic truth
    -> one final Physical Plan decision
        -> one admitted execution lifecycle
            -> one representation-aware Chunk kernel family
                -> sequential sink or bounded Chunk partial
                    -> canonical result
```

## 2. 不改变的正式合同

本设计必须服从[Planning](../../design/planning-and-optimization.md)、
[Execution](../../design/execution-and-concurrency.md)、
[Architecture](../../design/implementation-architecture.md)、
[Storage](../../design/data-model-and-storage.md)与
[核心抽象叙事](../../design/core-abstractions-and-narratives.md)；它只提出未来可能晋升的M1 mechanism，
不覆盖这些正式Owner。

本设计不得改变：

- generated/public API及其one-shot lifecycle；
- Canonical IR的semantic truth；
- terminal-start binding与同一Group外部串行合同；
- Reference interpreter的独立test-only oracle地位；
- encounter order、stable tie、null、numeric与structured failure合同；
- work前resource admission、callback exactly-once与parallel quiescence；
- exactly two production artifacts、Java 8与零新增production dependency；
- unsupported shape进入现有optimized path，而不是Reference或新的runtime error。

## 3. 冻结职责词汇

这些词只定义职责，不要求production采用同名class，也不构成public/internal SPI。

### 3.1 Physical request

terminal向planner提供的closed、data-only需求：source、normalized stages、terminal family、result shape、
execution mode与正式语义约束。它不是第二套IR，也不保存application callback executable state之外的
新语义。

### 3.2 Finite kernel decision

最终Physical Plan拥有的一次性物理决定，至少包含：

- kernel family；
- source/access path；
- compiled finite typed predicate；
- projection leaf与primitive kind；
- 每种现存Chunk representation的handler choice；
- sequential/parallel strategy与canonical merge；
- complete resource delta；
- explain category与whole-operation fallback原因。

### 3.3 Representation handler

对一个Chunk执行同一finite kernel family的闭集机制：

- `PLAIN_DIRECT`：借用typed array；
- `ENCODED_NATIVE`：本基线直接消费run或codec-owned integral representation；
- `ENCODED_SCALAR`：按codec accessor逐值读取，但不重新规划；
- `OVERLAY_SCALAR`：按current overlay/base值逐行读取；
- `UNAVAILABLE`：该operation shape整体回到现有optimized path。

这些是本专题允许的explain category，不是public enum或长期字符串合同。

### 3.4 Morsel

默认就是一个bound logical Chunk ordinal。它不是新的storage page、batch对象或public API。

## 4. 唯一主叙事与依赖方向

```text
Generated frontend
    -> Canonical lowering
        -> terminal-start bind
            -> normalize
                -> planner receives closed physical request
                    -> chooses access + finite kernel + representation handlers
                    -> computes one ResourceEstimate
                        -> lease succeeds
                            -> create ExecutionFrame
                                -> dispatch once per Chunk
                                    -> representation-owned access
                                        -> fused finite loop
                                            -> sink / partial / output range
                                                -> deterministic merge
```

依赖只能向下：

- Canonical IR不知道kernel；
- Storage不知道query pipeline；
- representation只暴露本身能够保证的closed access；
- scheduler不知道predicate、type或aggregate；
- execution不在lease后重新选择算法或增加未计费scratch；
- Reference不调用production kernel。

## 5. 一次Physical decision

### 5.1 裁决

后续implementation应让planner在一次调用中同时看到normalized semantic input与closed terminal request，
直接返回final Physical Plan。当前“base plan -> terminal refinement -> attach vector decision”只能作为迁移期
内部步骤，不晋升为长期结构。

允许的最小变化是扩展现有planner输入或在同一planner Owner内引入一个closed private request carrier；
不允许新建第二个vector planner。

### 5.2 Planner唯一负责

- source/access selection；
- kernel eligibility；
- representation handler set；
- sequential/parallel strategy；
- resource peak；
- whole-operation fallback与explain reason。

### 5.3 Execution唯一负责

- 验证并消费final plan；
- 在lease后建立operator-local state；
- 按Chunk ordinal调用已选handler；
- failure/interrupt/cancel/quiescence；
- partial/result merge和cleanup。

Execution不得重新检查“这个predicate是否适合vector”，也不得因为实际representation临时创建另一份plan。

## 6. 最小finite pipeline

### 6.1 可融合stage

本冻结基线只允许以下闭合形态：

```text
Table scan
    -> zero or more pure typed integral predicates
        -> optional schema-known primitive Field projection
            -> one admitted streaming/materialization terminal
```

相邻pure typed filters规范化后可以融合。projection只是绑定一个schema-known leaf，不创建中间column。

### 6.2 Pipeline breaker

遇到以下任一条件，整个operation shape回到现有optimized path：

- application callback filter/mapper/action/comparator；
- arbitrary mapped primitive/reference source；
- primitive callback map、convert或filter；
- distinct、sorted、top、skip、limit或其他stateful/order-changing stage；
- multi-leaf Value projection、ordinary reference equality/hash/materialization；
- IndexSelection、relation-derived source或GroupBy；
- mutation；
- 无法在plan时完整计费的decode/cache策略；
- 无法证明canonical order、numeric或failure等价的shape。

breaker不表示SOMA不支持该API，只表示它不进入本finite kernel。

### 6.3 Predicate program

Predicate仍是现有finite data-only tree，不引入universal expression VM。本基线只准入：

- constants；
- `and/or/not`；
- scalar integral `eq/ne/lt/le/gt/ge/between`。

boolean、`IN`、reference、Enum/string、null bucket与complex Value不进入本冻结实施范围；它们继续由
现有optimized predicate evaluator处理。未来若要准入boolean bit kernel，必须用独立Design delta闭合
presence/null lowering与实际profile，不能从本文件自动推断。

## 7. Representation-aware execution

### 7.1 Storage Owner提供什么

Storage/Chunk Owner只提供closed、package-private、operation-scoped access：

- PLAIN primitive typed buffer；
- integral encoded run/segment cursor，能够给出`raw value + logical length`；
- 必要的codec metadata与checked logical bounds。

它不接收Canonical node、terminal或planner对象，不返回可跨operation保存的storage view。

### 7.2 PLAIN

继续使用typed array direct loop。每Chunk只做一次kind/handler dispatch，热循环内不做virtual visitor、boxing或
locator-to-directory查找。

### 7.3 RLE/integral encoded

Encoded-native分为两个有限case：

1. 所有required leaves都能提供direct integral buffer时，按一次Chunk typed loop执行；
2. 计算只依赖一个distinct RLE leaf时，按run执行。

run case为：

```text
predicate(raw)
    -> false: skip run
    -> true:
         count += runLength
         sum += raw * runLength using checked wide accumulator
```

materialization只在需要输出每个logical value时展开run，直接写入已准入的结果range；不得先解压为第二个
Chunk数组。

本基线不设计general multi-leaf run zipper。若predicate与projection依赖run boundary不同的多个RLE
leaves，该Chunk使用`ENCODED_SCALAR`；若预计scalar占比使收益不足，则整个operation走existing optimized
fallback。这样既覆盖当前encoded-plain与single-leaf RLE热点，也避免为未经profile证明的组合提前建立游标
网络和新的scratch模型。

### 7.4 Bit boolean

Bit boolean继续走现有optimized/representation scalar机制。本冻结基线既不要求Storage暴露packed word，
也不准入boolean专用kernel；这避免为了尚无matched evidence的cell提前扩大Storage与numeric surface。

### 7.5 Encoded scalar与overlay

如果同一finite family对某个Chunk只有scalar accessor：

- planner必须把该handler显式记录为representation fallback；
- execution仍按Chunk dispatch一次，不重新规划；
- `_explain()`可报告mixed handler category；
- 如果预计fallback占比使收益不足，planner可以让整个operation回到现有optimized path。

本冻结基线不准入operation-local decode buffer。未来只有profile证明scalar overlay/codec成为主要hotspot且能够给出
`O(participants × Chunk bytes)`保守上界时，才单独设计bounded decode。

### 7.6 Mixed representation

同一bound root可以同时包含PLAIN active tail和sealed encoded Chunk。final plan拥有一个closed handler table，
每个Chunk只按自身representation选择已计划的handler。representation在terminal binding后不可被本operation
观察为另一版本，因此不需要跨terminal cache或version guard。

## 8. 冻结capability matrix

### 8.1 已正式存在并保留

| Cell | Sequential | Parallel | Representation |
|---|---|---|---|
| Table count，zero/simple typed predicate | admitted | Chunk partial | PLAIN direct；encoded/overlay明确handler |
| Integral Field sum，zero/simple typed row predicate | admitted | Chunk signed-128 partial | PLAIN direct；encoded优先native |
| ordered `long[]` materialization | admitted | current sequential only | PLAIN direct；encoded/overlay明确handler |

### 8.2 VP1正式准入：encoded-native integral scan/aggregate

| Cell | Decision | 理由 |
|---|---|---|
| AUTO integral encoded direct sum | `ADMIT` | 当前1M AUTO/OFF差距明确；run/codec Owner可直接消费 |
| AUTO typed integral predicate + projected sum | `ADMIT` | encoded-plain direct；single-leaf RLE native；multi-leaf RLE scalar fallback |
| AUTO typed integral predicate + count | `ADMIT` | encoded-plain direct；single-leaf RLE native；无result materialization |
| overlay scalar handler | `ADMIT_AS_EXPLICIT_FALLBACK` | 保持currentness，不预建decode |

### 8.3 VP2正式准入：ordered `long[]` materialization

| Cell | Decision | 理由 |
|---|---|---|
| encoded-native ordered `long[]` | `ADMIT` | 当前AUTO/OFF差距明确；run可直接写入结果range |
| parallel `long[]`，无predicate | `ADMIT` | output offset可由Chunk logical prefix直接决定 |
| parallel `long[]`，pure typed integral predicate | `ADMIT` | conservative-admitted two-pass可保持order并避免O(N) locator staging |

### 8.4 明确延后，不能由实施自行扩张

| Cell | Decision | 理由 |
|---|---|---|
| boolean equality/count | `DEFER` | presence/null lowering与matched profile尚未闭合 |
| integral min/max | `DEFER` | 没有必要为本轮架构证明扩大terminal矩阵 |
| integral average/summary | `DEFER` | result/numeric/failure surface大于当前证据 |
| byte/short/char/int ordered arrays | `DEFER` | 需要逐kind consumer与code-size证据 |
| float/double direct materialization | `DEFER` | 当前encoded本身是plain floating，预期收益有限，先profile |
| floating sum/average/summary | `DEFER` | 必须逐项证明固定1024 logical block tree的bitwise等价 |
| match/findFirst | `DEFER` | 当前公开terminal多为callback；短路与failure arbitration收益不明确 |
| IndexSelection residual | `DEFER` | exact Index已很快，source小且order边界独立 |

### 8.5 排除或必须另开专题

| Cell | 冻结裁决 |
|---|---|
| callback/mapped reference/stateful pipeline | `REJECT_FROM_THIS_MECHANISM` |
| runtime codegen/general DAG/public Batch/Vector API | `REJECT` |
| GroupBy与Join | `REQUIRE_SEPARATE_GOVERNANCE` |
| mutation | `OUT_OF_SCOPE` |

## 9. Morsel-driven execution

### 9.1 Morsel identity

一个morsel等于一个bound Chunk ordinal及其logical row range。默认不切sub-Chunk；Chunk skew成为真实profile
瓶颈前，不新增range splitting、stealing或dynamic queue。

### 9.2 Scalar aggregate

- sequential：一个accumulator顺序消费Chunk；
- parallel：每Chunk一个partial slot，work按ordinal提交；
- merge：caller按canonical Chunk ordinal合并；
- count使用checked long；integral sum使用signed-128 two-limb state；
- scratch为O(chunks)，不建立locator membership。

### 9.3 Ordered `long[]` materialization

无predicate时，每个Chunk的output offset由bound logical row prefix直接得出，可以一次并行写入互不重叠range。

有pure typed predicate时采用two-pass typed-only strategy：

1. 在已取得conservative lease后，每Chunk统计qualifying count；
2. caller按ordinal做checked prefix，得到exact result length和每Chunk output range；
3. 分配typed detached result；
4. 再次执行同一pure typed predicate并写入独立range；
5. 返回canonical ordered result。

这里允许两次读取，因为没有callback、副作用或外部可变state；它避免O(rows) locator/result staging。若两次scan在
某representation上实测得不偿失，planner选择sequential或existing optimized fallback。

### 9.4 Failure与quiescence

继续使用现有shared ordinal-work lifecycle：

- admission、pool可用性与nested parallel在work前验证；
- caller参与且外部drainer最多P-1；
- failure按最小canonical ordinal裁决；
- interrupt/cancel后等待全部submitted work quiesce；
- result只在全部work成功后发布；
- partial、counts、prefix和Frame在返回或失败前释放。

## 10. Resource模型

符号：

- `N`：bound output upper bound；
- `C`：参与Chunk数；
- `P`：effective participants；
- `W`：result primitive width；
- `A`：accumulator bytes per Chunk；
- `T`：scheduler/task固定保守成本。

| Kernel | Temporary peak公式 | Actual allocation目标 |
|---|---|---|
| sequential count/sum | existing fixed frame + predicate membership | O(1)，不按row分配 |
| parallel scalar aggregate | `checked(C × A + T)` | O(C + P) |
| sequential ordered array | `checked(N × W + fixed)` | detached result主导；不得保留第二个N-sized locator buffer |
| parallel array，无predicate | `checked(N × W + T + ordinalState)` | result + O(C + P)上界；不分配count/prefix数组 |
| parallel array，有typed predicate | `checked(N × W + C × count/prefixBytes + T)` | conservative upper result + O(C + P)；不重复callback |
| encoded native run aggregate | 与对应aggregate相同 | 不整体decode，不按run对象分配 |
| representation scalar fallback | plan中使用其现有resource formula | 不在execution临时增加scratch |

所有乘加使用checked long；Java array length仍受32位结构域限制。资源lease覆盖同时live peak，而benchmark的
allocated bytes单独记录累计traffic，二者不能混同。

## 11. Numeric、order与null

### 11.1 Integral

- `count`使用checked long；
- sum在每个Chunk和merge阶段都使用signed-128 exact accumulation；
- `raw × runLength`先进入wide state，不能先在long中溢出；
- 只有final long result越界时产生正式`ARITHMETIC_OVERFLOW`；

### 11.2 Floating

floating sum/average/summary在证明下列等价前不准入：

- logical encounter sequence不变；
- 1024 logical-element block边界不因Chunk/morsel改变；
- sequential与parallel使用相同deterministic pairwise tree；
- NaN、Infinity、signed zero和`Float/Double.compare`合同不变。

### 11.3 Order

filter与projection保留source canonical order；parallel materialization只能写入按Chunk ordinal计算的固定range。
worker completion order、encoded run order之外的hash/posting order都不能成为结果顺序。

### 11.4 Null

当前finite cells只消费formal non-null integral leaves。nullable reference、Enum/String和multi-leaf Value继续走
existing optimized path；future boolean cell必须先验证presence/null lowering，不能从primitive zero value推断null。

## 12. Fallback合同

### 12.1 Shape fallback

planner发现callback、stateful、unsupported source/type/terminal或无法证明资源/语义时，整个operation进入现有
optimized plan。它不是错误，也不调用Reference。

### 12.2 Representation fallback

shape已经准入，但mixed root中某representation只有scalar handler时，plan可以：

1. 显式选择mixed per-Chunk handler；或
2. 根据bound Chunk distribution选择whole-operation optimized fallback。

选择在lease前完成，不能在热循环中重新做cost planning。

### 12.3 Optimizer failure

unexpected planner/kernel invariant failure不得静默回退Reference或另一算法；按既有failure/Error合同fail closed。

## 13. Diagnostics

`_explain()`可以显示不稳定的高级类别：

- selected finite family；
- shape eligible/fallback reason；
- representation handler summary；
- sequential/parallel strategy；
- resource estimate category。

不冻结class名、codec细节、阈值、字符串或cost constant。业务代码不得解析`_explain()`。

## 14. Owner与证明链

| Invariant | 唯一Owner | Production defense | Required evidence |
|---|---|---|---|
| Canonical语义不因kernel改变 | Canonical IR | kernel只消费bound normalized input | Reference differential |
| 一次physical decision | Planning | closed request -> final plan | planner/golden/explain + no refinement residue |
| Representation不成为第二truth | Storage | operation-scoped closed access | escape/identity review + mutation/currentness regression |
| Admission覆盖完整peak | Physical Plan/Resource | checked conservative formula | tiny-budget pre-work failure + allocation attribution |
| Execution不重新规划 | ExecutionFrame | only consumes final plan | code-shape review + fault injection |
| Parallel有界且quiescent | Scheduler | shared ordinal-work lifecycle | rejection/interrupt/nested/failure tests |
| canonical order | kernel merge | ordinal partial/range | sequential/parallel arrays differential |
| integral exact | numeric kernel | signed-128 partial/merge | boundary/random differential |
| 未准入cell不会被实施静默激活 | 本冻结Design | closed capability matrix | source/capability diff + review |
| unsupported shape仍可用 | existing optimized path | shape fallback | positive fallback matrix |

## 15. 最小Surface Admission

本冻结设计允许实施在代码形状确有需要时提出至多两个package-private closed surface，但不预先固定名字；
若现有carrier能够清晰承载，则不新增。二者都不是public/internal SPI。

### 15.1 Closed physical request/decision carrier

1. Capability/consumer：让planner一次看到terminal并产出final finite decision；consumer是Query lifecycle；
2. Owner/lifecycle：Planning Owner，data-only，单terminal；
3. 现有surface不足：terminal refinement会让eligibility/resource随cell增长而重复；
4. Blueprint trace：高性能、可预测资源、Canonical/physical分层与低分配；
5. Evidence：无第二planner、plan golden、resource differential、现有fallback回归。

### 15.2 Closed representation-native integral access

1. Capability/consumer：按run/typed buffer执行finite integral kernel；consumer是已准入Chunk kernel；
2. Owner/lifecycle：Storage Owner，operation-scoped borrowed access；
3. 现有surface不足：逐元素visitor无法利用RLE run-level physical information；
4. Blueprint trace：schema-known columnar state、低搬运、AUTO透明；
5. Evidence：PLAIN/AUTO/overlay differential、no decode-all、allocation与matched profile。

不准入module、artifact、dependency、public/generated API、SPI或generic vector container。

## 16. 冻结实施边界

精确顺序、exit evidence、性能守卫和恢复点由本专题唯一
[Implementation Plan](implementation-plan.md)拥有。本设计只冻结三段能力边界：

1. **VP1**：single final Physical decision + encoded-native integral count/sum/predicate；
2. **VP2**：ordered `long[]` representation-native与Chunk-morsel parallel materialization；
3. **VP3**：全局qualification、正式Owner晋升与Temporary replacement closure。

不存在primitive cross-product slice，也不存在complex operator slice。GroupBy、Join、boolean、其他primitive
array与新terminal若未来有证据，必须重新建立bounded governance或显式Design delta；实施Agent不得把它们
解释为“顺手补齐”。

## 17. Stop Rules

遇到以下任一情况必须暂停并由Product Owner裁决：

- 需要改变public/generated API或Canonical semantic node；
- 需要第三artifact、新dependency、Java版本或Vector/native API；
- 需要第二planner、executor、scheduler、resource model或storage truth；
- 需要无预算decode/cache或跨terminal physical plan cache；
- Reference、numeric、order、failure或quiescence证明无法闭合；
- 性能收益依赖弱化正确性、关闭AUTO或场景特供；
- VP1-VP2之外要激活其他type/terminal、GroupBy或Join；
- matched evidence落入噪声或造成其他正式normal path实质退化。

## 18. 冻结结论

本设计认为：现有架构足以支持下一阶段，不需要重建IR或执行引擎。正确方向是：

1. 把terminal、representation、parallel与resource收进一次final Physical Plan decision；
2. 只为finite typed shape建立representation-native Chunk kernel；
3. 使用现有Chunk作为morsel和现有scheduler作为唯一parallel lifecycle；
4. 对callback、stateful、Index、GroupBy、Join保持明确fallback/defer；
5. 每个cell以Reference、resource和matched profile共同准入；
6. 当前只实施VP1、VP2，再以VP3完成正式Owner晋升和Temporary退役。

Product Owner已授权本次临时设计固化，因此本文件状态为
`DESIGN_BASELINE_FROZEN / IMPLEMENTATION_READY`。这项授权只冻结implementation input；production
implementation仍为`NOT_AUTHORIZED`，必须等待后续明确授权。
