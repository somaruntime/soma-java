# R2.1 Current-state、能力矩阵与证据盘点

类型：Bounded Temporary / Current-state Audit

状态：`COMPLETE / FROZEN_INPUT / SNAPSHOT_AT_E2CE237 / NOT_A_FORMAL_DESIGN`

基线：`e2ce237736cd6042fbce2d90c2522e1a280e7d7c`

日期：2026-08-12

Current route：本盘点已经被[冻结临时设计](design.md)吸收；它只保留implementation前current-state
snapshot，不拥有Design或当前授权状态。

## 1. 审核目的与边界

本盘点回答四个问题：

1. 当前Canonical IR、Physical Plan、Chunk representation与execution lifecycle实际上怎样协作；
2. 第一阶段finite vector kernel已经覆盖什么，哪些shape仍走逐行路径；
3. 继续扩展时，最容易产生第二套planner、executor、resource truth或representation truth的位置在哪里；
4. 哪些正式证据可以复用，哪些事实必须在后续implementation slice重新测量。

本文件描述当前实现，不授予production修改，不把private类名晋升为长期产品合同。正式语义仍由Design
Owner拥有。

权威上游：[Planning](../../design/planning-and-optimization.md)、
[Execution](../../design/execution-and-concurrency.md)、
[Architecture](../../design/implementation-architecture.md)、
[Storage](../../design/data-model-and-storage.md)与
[第一阶段正式晋升](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)。

## 2. 当前端到端执行主线

```text
Generated Java API
    -> Logical*Plan frontend carrier
        -> CanonicalRow/Primitive/Mapped/Relation/Group operation
            -> terminal-start binding
                -> Bound operation
                    -> normalization
                        -> Physical plan + ResourceEstimate
                            -> temporary admission
                                -> ExecutionFrame
                                    -> optimized scalar operator
                                    -> or finite primitive Chunk kernel
                                        -> shared ordinal-work scheduler
                                            -> canonical result

Reference path
    -> same bound semantic input
        -> independent Reference interpreter
```

这里不存在一套可被删除的“旧直接执行”和一套完整“新执行引擎”。当前事实是：

- 所有正常query都经过同一个Canonical lifecycle；
- generic optimized path主要以locator/row为执行粒度；
- 第一阶段finite kernel是Physical Plan选择的专用physical mechanism；
- Reference只作为test-only correctness oracle，不是production fallback。

## 3. Carrier、Owner与生命周期盘点

| 层次 | 当前主要carrier/mechanism | 当前Owner | 生命周期与事实 |
|---|---|---|---|
| Java frontend | `LogicalRowPlan`及generated Table/Field/Stream carrier | generated API + lowering | one-shot public carrier；不拥有physical decision |
| Canonical semantic | `CanonicalRowOperation`、`CanonicalPrimitiveOperation`、`CanonicalMappedOperation`、`CanonicalRelationOperation`、`CanonicalGroupOperation` | Canonical IR | data-only semantic truth；callback以opaque handle保留 |
| Binding | `BoundCanonical*Operation` | query admission/binding | terminal取得Group guard后绑定当前`StateRoot`；不跨terminal缓存 |
| Normalization | `NormalizedCanonicalRow`、relation normalization | Canonical planner | 只做已证明等价的typed filter集合化、pushdown等 |
| Row physical plan | `CanonicalRowPhysicalPlan` | `CanonicalRowPlanner` | access path、parallel prefix、resource与可选vector decision |
| Relation physical plan | `PhysicalRelationPlan` | `CanonicalRelationPlanner` | nested cross、right Index lookup或right hash；独立resource formula |
| Group execution | row physical plan + `GroupState` | grouping operation | 尚无独立group physical decision；hash/group arrays在lease后建立 |
| Vector decision | `CanonicalPrimitiveVectorKernel.Decision` | 当前由terminal refinement调用 | count、integral sum、`long[]` materialization三类finite operation |
| Resource | `ResourceEstimate`、temporary lease | Physical Plan + memory manager | work前checked admission；Frame和operator-local state在lease后创建 |
| Execution frame | `CanonicalRowExecutionFrame`、relation frame | execution lifecycle | operation-local、borrowed、不可逃逸；只消费bound plan |
| Scalar optimized | `CanonicalRowExecution`、primitive/mapped/group/relation operator | execution engine | locator或element loop；stateful形状按正式合同materialize |
| Representation | `TableChunk`、`PlainChunk`、`EncodedChunk`、`OverlayChunk` | storage/representation | authoritative storage；PLAIN持typed arrays，encoded持codec-specific column，overlay合并base与delta |
| Parallel lifecycle | `CanonicalParallelWorkScheduler` | execution/concurrency | bounded caller participation、P-1 drainers、ordinal failure arbitration、quiescence |
| Row parallel preparation | `CanonicalParallelRowScheduler` | row optimized path | typed prefix按Chunk range执行，当前通常形成O(rows) locator buffer |
| Reference oracle | `ReferenceCanonicalRowInterpreter`等 | test-only correctness | canonical scan、原始stage顺序；不使用optimizer、Index或parallel |

## 4. Representation的实际执行能力

### 4.1 PLAIN

`PlainChunk`直接拥有primitive typed array与reference array。第一阶段kernel在operation scope内借用typed
array，并在一个Chunk内完成predicate、projection与sink，不逐元素调用通用visitor。这是当前最成熟的
finite specialization。

### 4.2 ENCODED

`EncodedChunk`按leaf保存codec-specific `PrimitiveColumn`或reference dictionary。当前包含bit boolean、
plain integral、RLE、plain floating与dictionary reference等表示。

现有`PrimitiveColumn.visit(...)`和`RleColumn.visit(...)`仍以logical row为单位调用visitor；RLE即使拥有
run，也会把run展开为逐行callback。第一阶段vector decision可以命中AUTO/encoded Table，但部分encoded
分支仍通过每行raw/accessor或predicate evaluator执行。因此：

> “选择了vector decision”不等于“所有representation都执行representation-native kernel”。

这是本专题最主要的current-state gap。

### 4.3 ENCODED_WITH_OVERLAY

`OverlayChunk`必须合并base与overlay current value。当前primitive traversal也是逐行选择overlay/base。
它的正确性边界清晰，但在没有profile和资源公式前，不适合预建通用decode或overlay vector engine。

### 4.4 Directory与Chunk边界

`TableChunkDirectory`负责locator到Chunk/offset的寻址。scalar row path可能在热循环中反复经历directory、
virtual accessor与stage dispatch；finite kernel则每个Chunk只解析一次typed storage。现有logical Chunk已经是
自然morsel，不需要为了R2再发明page或sub-Chunk storage unit。

## 5. Operation × execution capability矩阵

`Current mechanism`描述现行production事实；`R2 disposition`是下一份Candidate Design的输入，不是
implementation authorization。

| Source/family | Representative shape | Current mechanism | Representation/parallel事实 | R2 disposition |
|---|---|---|---|---|
| Table | unfiltered `count` | finite Chunk count kernel | PLAIN direct；AUTO允许representation fallback；parallel使用O(chunks) partial | KEEP baseline |
| Table | pure typed integral predicate + `count` | compiled finite predicate + Chunk kernel | typed-only；callback不进入；parallel使用shared scheduler | EXTEND encoded-native |
| Table | callback filter/count | scalar optimized row loop | callback是optimization barrier；parallel通常仍按row/prefix执行 | REJECT specialization |
| Table | typed filter + integral Field + `sum` | finite predicate/project/sum kernel | exact signed-128；PLAIN direct；encoded部分逐行 | FIRST PRIORITY |
| Field | direct integral `sum` | finite integral sum kernel | sequential Chunk；parallel O(chunks) partial | FIRST PRIORITY for AUTO |
| Field | primitive callback filter/map/convert | primitive stage loop | callback opaque；AUTO/OFF相近，parallel经常无收益 | REJECT specialization |
| Field | `long[] toArray()` | finite ordered materialization | sequential only；PLAIN direct；encoded部分逐行 | KEEP, then generalize carefully |
| Field | other primitive arrays | generic primitive materialization | upper-bound staging + exact result；typed but未进入finite kernel | DEFER to S2 evidence |
| Field | min/max/average/summary | generic primitive terminal | integral与floating numeric合同不同；floating固定1024 tree | ADMIT integral candidate; DEFER floating |
| IndexSelection | exact count | Index cursor/posting | 1M已约0.3 ms；无需Table scan | DEFER; low expected value |
| IndexSelection | residual typed filter/project | Index source + scalar residual | canonical posting order；source cardinality通常已小 | DEFER pending profile |
| Mapped primitive | callback mapper + aggregate | scalar mapped/primitive loop | callback barrier；parallel在现有1M证据中无明显收益 | REJECT specialization |
| Mapped reference | map/distinct/sort/materialize | reference stateful pipeline | equality/hash/comparator与allocation边界复杂 | REJECT from finite primitive pipeline |
| Stateful | distinct/sort/top/skip/limit | specialized scalar stateful algorithms | canonical order、stable tie与O(rows) scratch是合同 | REJECT initial fusion |
| GroupBy | typed key + aggregate | row source + `GroupState` hash arrays | low/high cardinality差异大；floating保留per-group sequence | DEFER whole family |
| Equality Join | Index lookup/right hash + filters | relation physical plan/operator | output bound、outer semantics、pair order与two-root admission复杂 | DEFER whole family |
| Mutation | point/Selection mutation | separate mutation lifecycle | 已有独立write-set/sidecar治理 | OUT OF SCOPE |

## 6. 当前finite kernel边界

第一阶段`CanonicalPrimitiveVectorKernel`固定三类Operation：

1. `COUNT`；
2. `INTEGRAL_SUM`；
3. `LONG_MATERIALIZATION`。

当前predicate compiler只接收finite pure tree：constant、`and/or/not`与integral
`eq/ne/lt/le/gt/ge/between`。它不解释application callback，也不覆盖任意mapper、stateful stage或
relation/group semantics。

该边界本身合理。需要治理的是decision的承载方式：

- base row plan先生成，terminal随后通过`PhysicalRefinement`调用`planCount`、`planIntegralSum`或
  `planLongMaterialization`；
- vector decision被附加回Physical Plan，同时修正parallel resource；
- execution再根据decision进入finite kernel；
- representation-specific fast/slow path仍在kernel内部决定。

这不是当前正确性缺陷，但如果继续逐terminal扩展，会使“eligibility、representation、resource”分散在
planner、terminal与kernel三处。

## 7. Parallel与morsel盘点

### 7.1 已经正确的基础

- Chunk ordinal已经是稳定、bounded的morsel identity；
- row range与Chunk work共享`CanonicalParallelWorkScheduler`；
- caller参与，最多P-1个外部drainer；
- pool拒绝、interrupt、callback failure与quiescence沿用同一个生命周期；
- integral sum按Chunk ordinal合并exact partial；
- parallel finite aggregate只需O(chunks) scratch。

### 7.2 当前限制

- generic parallel row path会先materialize O(rows) locator prefix；
- `long[]` materialization未进入parallel finite path；
- callback/mapped primitive即使显式parallel，也可能仍受逐元素callback和prefix搬运限制；
- 没有证据证明sub-Chunk morsel值得其额外调度和skew复杂度。

因此R2应扩展“finite typed full traversal”，而不是建立第二个scheduler。

## 8. Relation与GroupBy为何不作为首批目标

### 8.1 GroupBy

当前GroupBy的成本Owner包括：key hash/equality、group cardinality、dynamic group arrays、key
materialization、aggregate state以及floating canonical sequence。它不是简单把row loop换成Chunk loop即可完成；
worker-local GroupBy还需要合并identity、encounter order和完整resource peak设计。

### 8.2 Join

当前Join已经拥有独立Physical Relation Plan，在right Index lookup、right hash与cross nested之间选择；还要
处理pushdown、outer unmatched、semi/anti、完整Cartesian duplicate、two-root admission和output bound。把它
并入本轮finite primitive pipeline会同时改动两个复杂Owner。

正式性能证据显示Join仍是重要hotspot，但“重要”不等于“现在就适合准入”。R2选择先不激活复杂operator，
避免用路线图完整性替代设计证明。

## 9. 识别出的结构坏味道

### 9.1 两阶段physical decision正在接近重复Owner

base plan与terminal refinement当前仍能保持正确，但新增更多terminal后会产生多处eligibility与resource
delta。需要让同一个planner调用看到closed terminal request并一次产出最终plan，而不是新建第二planner。

### 9.2 Representation strategy没有成为显式decision

当前vector plan可在AUTO上命中，但kernel内部才发现某个Chunk必须逐行。这使`_explain()`、cost reasoning和
性能证据难以区分`PLAIN_DIRECT`、`ENCODED_NATIVE`与`ENCODED_SCALAR`。

### 9.3 `visitPrimitive`仍是逐元素抽象

它已经比borrowed View低成本，但不能表达RLE run-level aggregate或一次Chunk typed loop。继续堆叠visitor
special case会形成隐式pipeline。

### 9.4 Current vector class可能膨胀成God object

同一个类已经承担eligibility、predicate compilation、PLAIN/encoded execution、parallel partial、numeric
accumulation与materialization。R2需要按Owner拆分data-only decision与有限kernel mechanism，但不能反向建立
通用class hierarchy。

### 9.5 Generic parallel prefix与finite partial存在成本断层

eligible aggregate是O(chunks)，fallback可能是O(rows)。这不是错误，但必须在Physical Plan解释和资源公式中
可见，不能由execution临时决定。

## 10. 过度设计预警

以下做法没有current consumer或证据，不应进入Candidate Design：

- general vector DAG、operator bytecode、runtime codegen或Java Vector API；
- `VectorBatch` public/internal universal container；
- 为每种primitive/type/terminal生成class cross-product；
- 新scheduler、new thread pool或sub-Chunk work-stealing协议；
- decode-all cache、第二份PLAIN storage truth或跨terminal kernel cache；
- 同时重写GroupBy与Join；
- 为SOMA Engine预建JSON/Workflow node；
- 在当前private vocabulary尚未实现时冻结具体class名为兼容合同。

## 11. 证据分类

| Evidence | 分类 | 可复用结论 | 后续需要刷新 |
|---|---|---|---|
| Canonical IR/Execution S1-S6最终资格 | `REUSABLE` | single lifecycle、Reference independence、resource/parallel Owners | 受影响路径的targeted regression |
| Vectorized Physical Pipeline第一阶段晋升 | `CURRENT` | 三类finite kernel、O(chunks) partial、shared scheduler | 新cell及新representation handler |
| 当前production source at `e2ce237` | `CURRENT` | 本文carrier、branch与fallback事实 | 每个implementation slice后重盘点 |
| Runtime四条vector tests | `CURRENT` | PLAIN/AUTO differential、bounded parallel、resource replacement、pool failure | 新cell cross-product、fallback negative |
| Performance frontier 10K/1M/10M | `REUSABLE` | source/operator热点、AUTO/OFF、allocation与复杂operator优先级 | 绝对数值是dated fixed-host snapshot |
| 2026-08-12 1M R2定向重放 | `CURRENT_BOUNDED` | AUTO encoded gap仍存在；现行fingerprint通过 | 不是stable qualification或profile |
| 既有JFR/async-profiler | `NEEDS_REFRESH_WHEN_IMPLEMENTING` | directory/accessor/encoded traversal曾为hotspot | implementation前后matched profile |
| 三个reference application | `REUSABLE` | 产品场景和正确性路线 | S1-S3最终受影响场景重放 |

## 12. R2.1结论

R2.1结论为`COMPLETE`：

1. 当前Canonical与execution基础足够承载后续finite specialization；
2. 不需要重写Java frontend、Canonical IR、Reference oracle、scheduler或storage topology；
3. 最有价值的第一目标是AUTO/encoded下的pure typed integral scan、predicate与aggregate；
4. 必须先把representation strategy与terminal decision纳入一次Physical Plan选择；
5. GroupBy、Join、callback、stateful与mapped reference不进入首批能力；
6. 这些事实已经被[冻结临时设计](design.md)吸收，后续实施不得从本snapshot扩张冻结capability。
