# SOMA Physical Execution Engine M2 冻结候选设计

类型：Candidate Design

状态：`FROZEN_CANDIDATE / NOT_FORMAL_DESIGN / IMPLEMENTATION_NOT_AUTHORIZED`

日期：2026-08-12

上游：

- [Planning and Optimization](../../design/planning-and-optimization.md)
- [Execution, Concurrency and Parallelism](../../design/execution-and-concurrency.md)
- [Implementation Architecture](../../design/implementation-architecture.md)
- [Data Model and Storage](../../design/data-model-and-storage.md)
- [Results and Failures](../../design/results-and-failures.md)
- [Core Abstractions and Narratives](../../design/core-abstractions-and-narratives.md)

> 本文细化internal physical execution architecture，不拥有产品语义。与正式Design冲突时，正式Owner
> 优先；冲突必须触发stop rule，不能由实施代码自行裁决。

## 1. 设计主叙事

SOMA的用户表达logical intent，Canonical/Bound层保存确定语义，optimizer形成最终PhysicalPlan，
Execution只执行已经准入的plan：

```text
Java generated operation
    -> immutable Canonical Operation
        -> terminal-start Bound Operation
            +-- Reference Interpreter
            |
            `-- Normalized Operation
                    -> Physical Pipeline Plan + ResourceEstimate
                        -> resource admission
                            -> operation-local ExecutionFrame
                                -> bounded Morsel execution
                                    -> deterministic result / publication handoff
```

M2新增的不是一层用户抽象，而是PhysicalPlan内部的统一表示：

```text
Physical Pipeline
    = Source
    + one or more ordered Segments
    + zero or more Breakers
    + Terminal Sink
    + Kernel choices
    + Morsel strategy
    + complete ResourceEstimate
```

## 2. 核心抽象

### 2.1 Physical Pipeline

Physical Pipeline是一个terminal的完整物理执行拓扑。第一阶段只允许：

- linear unary pipeline；
- 一个有界binary Relation节点及其left/right input；
- binary输出后的linear downstream；
- Selection末端的mutation handoff。

它不是通用DAG、workflow或public API。相同Canonical语义可以有不同Physical Pipeline；Physical choice
只能改变成本，不能改变结果、顺序、callback、failure、resource或publication合同。

### 2.2 Physical Pipeline Segment

Segment是在两个边界之间可以stream/fuse的最大连续物理区域。边界包括：

- source/access-path切换；
- stateful breaker；
- opaque callback barrier；
- element shape变化且当前kernel不能继续融合；
- binary relation boundary；
- terminal sink。

Segment可以采用Row-at-a-time fallback，也可以采用Chunk-at-a-time typed kernel。两者是同一PhysicalPlan
下的不同kernel choice，不是两套语义或两套executor。

Segment必须记录：

- input/output element shape；
- required leaves与representation capability；
- stage范围与barrier；
- order/cardinality properties；
- selected kernel；
- sequential或parallel Morsel strategy；
- operator-local temporary estimate。

### 2.3 Pipeline Breaker

Breaker必须先拥有state或消费一定输入，才能继续产生输出。正式类别是有限、封闭的：

| 类别 | 典型能力 | 必须保持的合同 |
|---|---|---|
| reorder | stable sort、top | canonical stability与tie order |
| membership | distinct | first encounter wins |
| aggregation | GroupBy | first-key encounter order、typed aggregate/numeric |
| binary build/probe | hash/lookup Join | duplicate Cartesian、join-kind order、null-never-match |
| materialization sink | list/array/locator/value result | detached result、exact length/order |
| mutation handoff | selected locators/write set/remove plan | frozen membership、atomic publication owner不变 |

`skip/limit`本身不是breaker；它们是streaming cardinality operators。`top`是bounded reorder breaker。
Relation-left locator materialization是当前必要bridge breaker，未来只有证据证明可以消除时才能融合。

### 2.4 Physical Kernel

Kernel是一个Segment或Breaker的typed implementation。它可以根据以下事实专门化：

- element shape：row locator、primitive kind、host reference、relation pair；
- storage representation：PLAIN、encoded/RLE、overlay；
- predicate/order/aggregate capability；
- terminal；
- sequential/parallel strategy。

Kernel不是每个组合一个class的要求。Planner按能力选择少量已验证handler；unsupported组合选择已存在的
optimized scalar/row kernel。Reference永远不是production fallback。

第一阶段保留closed typed shapes：

```text
ROW_LOCATOR
PRIMITIVE(boolean/byte/short/char/int/long/float/double raw form)
HOST_REFERENCE
RELATION_PAIR_LOCATORS
GROUP_ENTRY  // breaker输出，不是普通source
```

不建立`Object[] VectorBatch`、universal tuple或public vector container。

### 2.5 ExecutionFrame

ExecutionFrame是一次已准入operation的actual state Owner。它在temporary lease成功后创建，在operation
返回、失败或取消前完成cleanup和quiescence。

Frame拥有：

- bound roots与actual cursors；
- predicate membership和Index cursor；
- segment-local state；
- breaker hash/buffer/heap state；
- Morsel partials与deterministic merge state；
- cancellation、failure和cleanup state。

Frame不拥有Canonical semantics、normalization、algorithm choice或resource estimate。operator state可按
segment/breaker ordinal访问，但不得演化为通用service registry。

### 2.6 Morsel

Morsel是从Physical Segment派生的、有canonical ordinal的bounded work unit。当前允许：

- Chunk ordinal；
- 一个或多个完整Chunk构成的locator range；
- Relation left-side ordinal range；
- breaker merge阶段的bounded partition。

Morsel不是新线程、新线程池或第二scheduler。全部parallel work继续由
`CanonicalParallelWorkScheduler`的caller-participating lifecycle执行。

每个Morsel必须具有：

- stable ordinal；
- bounded input range；
- 独立或明确分区的output/partial state；
- cancellation boundary；
- deterministic failure priority；
- canonical merge rule。

不能让worker完成顺序、hash bucket顺序或Index posting偶然顺序成为结果顺序。

## 3. Physical planning合同

Planner在binding和normalization之后一次性完成：

```text
source/access selection
-> segment boundary discovery
-> stateless typed fusion
-> breaker placement
-> representation handler selection
-> parallel/morsel decision
-> liveness/resource estimation
-> one immutable PhysicalPlan
```

最终PhysicalPlan必须完整回答：

- 从哪里读；
- 按什么element shape传递；
- 哪些operator融合成Segment；
- 哪些位置必须物化/建表/排序；
- 每个Segment/Breaker采用哪个kernel；
- sequential还是parallel、怎样分Morsel；
- 哪些state同时存活、peak temporary与result bytes是多少；
- 最终sink或mutation handoff是什么。

不得在ExecutionFrame创建后重新选择algorithm、representation handler、partition count或scratch shape。
runtime只允许执行plan中已经准入的选择；数据依赖的bounded branch必须作为PhysicalPlan已有handler的
明确分支，并由同一保守resource estimate覆盖。

## 4. Segment构造规则

### 4.1 可以融合

- 相邻pure typed filter；
- schema-known Field projection；
- primitive stateless map/conversion；
- streaming skip/limit；
- count、match、integral sum等支持push accumulator的terminal；
- 经证明可直接消费source representation的typed predicate + projection + terminal。

### 4.2 必须断开

- arbitrary Java callback，除非保持原callback顺序、次数、线程和failure合同；
- sort、distinct、top、GroupBy、hash Join build；
- reference mapper导致不可预测对象创建或application equality/hash；
- outer Join missing-side形态变化；
- element shape转换而没有已验证typed kernel；
- mutation publication；
- 任何可能改变logical observation的rewrite。

Callback barrier不必永远产生物化；它可以是同一streaming pipeline中的独立Segment，但不能跨越它
重排typed operator，也不能让并行改变caller-thread callback合同。

## 5. Resource合同

PhysicalPlan拥有唯一`ResourceEstimate`。估算来自：

```text
source cursor/state
+ simultaneously live segment state
+ breaker state and growth peak
+ parallel Morsel descriptors/partials
+ deterministic merge state
+ detached result or mutation staging
```

第一阶段仍在任何data/callback/allocation之前一次性lease conservative whole-operation peak。M2不引入：

- dynamic lease/release；
- spill；
- optimistic allocation后补记账；
- 运行中因预算不足切换Reference或另一算法。

未来若需要phase-local lease，必须另行证明failure、cleanup和peak accounting，不能由本设计自动授权。

## 6. 顺序、并行与失败

- sequential与parallel执行同一PhysicalPlan语义；parallel只是Morsel refinement；
- source、Segment和Breaker都携带canonical ordinal/order property；
- stable sort用input ordinal做tie-break；distinct保留first encounter；
- GroupBy key按first encounter发布；Join保持正式left/right order；
- short-circuit callback只观察canonical prefix；full traversal callback不重复调用；
- worker异常按最小canonical ordinal确定winner；
- callback异常继续统一包装为`CALLBACK_FAILED`；`Error`传播并best-effort cleanup；
- terminal返回或抛出前所有drainer quiescent、lease和guard释放。

## 7. Reference关系

Reference Interpreter继续：

- 直接消费Bound semantics；
- canonical locator/value traversal；
- 按原logical stage顺序执行；
- 不读取Segment、Breaker、Kernel、Morsel、statistics或Index substitution；
- 不作为production fallback；
- 使用与production独立的state/algorithm。

Differential比较logical result、order、failure code/context和publication state，而不是比较physical trace。

## 8. Operation映射

| Operation family | Candidate Physical Pipeline |
|---|---|
| Table scan/filter/count | Table source -> typed/callback segments -> count sink |
| Field direct | Table source -> Field projection segment -> typed terminal |
| Mapped reference | Row source segment -> callback map segment -> optional membership/reorder breaker -> materialization sink |
| Primitive | Row/Field source -> unboxed stateless segment -> optional primitive breaker -> numeric/materialization sink |
| GroupBy | upstream Row segment -> aggregation breaker -> typed grouped-result sink |
| Equality/Cross Join | left/right source -> relation build/lookup/probe breaker/operator -> pair/left segment -> terminal |
| Selection update | Row selection segments -> locator membership sink -> write-set handoff -> mutation publication |
| Selection remove | Row selection segments -> locator membership sink -> remove-plan handoff -> mutation publication |
| Point operation | 不进入Physical Pipeline；保持direct preflight/prepare/commit |

### 8.1 GroupBy

GroupBy是本设计的首个stateful纵向slice：

```text
Table/Index source
    -> optional typed filter segment
        -> key projection + aggregate-input segment
            -> hash aggregation breaker
                -> first-encounter ordered result sink
```

Group hash state必须进入final PhysicalPlan与admitted Frame；`expectedGroups`等hint只能影响capacity，不能
改变结果。第一阶段不把GroupBy变成通用多aggregate dynamic record，也不新增多aggregate public API。

### 8.2 Relation

Relation保留bounded binary结构：

```text
left input + right input
    -> build/lookup/probe physical operator
        -> relation pair/left shape
            -> downstream linear segments
```

`RIGHT_INDEX_LOOKUP`、`RIGHT_HASH`、`NESTED_CROSS`仍是finite algorithm choices。M2只统一plan/frame/
resource/morsel合同，不准入新Join语义或任意non-equality Join。

### 8.3 Selection mutation

Selection query部分可以复用完整Physical Pipeline，但publication不能成为普通kernel：

```text
Physical selection
    -> frozen locator/write-set/remove-plan handoff
        -> Mutation Owner validation
            -> atomic Table-local publication
```

Planner必须把selection result与mutation staging的共同peak计入同一preflight。zero match和zero change合同
保持不变。

## 9. Explain与观测

`_explain()`可以增加不稳定诊断类别：

- physical source/access path；
- segment count与element shape；
- breaker kind；
- kernel family与representation handler；
- sequential/parallel与Morsel kind/count；
- estimated temporary/result peak；
- fallback reason。

这些是diagnostic text，不固定private class name、codec coefficient、threshold或字符串格式。Application
不得解析explain驱动业务逻辑。

## 10. 过度设计审查

明确拒绝：

1. **通用operator DAG**：current Java frontend只需要linear unary + bounded binary；Workflow属于未来
   SOMA Engine产品；
2. **universal VectorBatch**：会引入boxing、type erasure和另一套数据容器；
3. **public/internal SPI**：没有第三方kernel consumer，不建立扩展点；
4. **每个组合预生成kernel class**：按profile与evidence逐个准入finite handler；
5. **第二scheduler/executor**：Morsel只复用现有ordinal-work lifecycle；
6. **让Reference解释PhysicalPlan**：会破坏独立oracle；
7. **把Point operation纳入query engine**：只会增加固定成本和责任混淆；
8. **动态resource leasing/spill**：超出V1内存引擎边界；
9. **runtime bytecode/codegen/Vector API**：Java 8 baseline且当前证据不足；
10. **同时重写全部family**：采用纵向slice，退出时移除被替换的local mechanism。

## 11. 核心不变量

```text
M2-INV-1  一个terminal只有一个final PhysicalPlan和一个ResourceEstimate。
M2-INV-2  Reference只解释Bound semantics，不消费Physical事实。
M2-INV-3  Actual O(N) state只在resource admission后进入ExecutionFrame。
M2-INV-4  Segment fusion不能跨越semantic/callback/breaker barrier。
M2-INV-5  Parallel只细化同一plan，Morsel completion order不成为result order。
M2-INV-6  Unsupported specialized kernel选择existing optimized kernel，不选择Reference。
M2-INV-7  Group/Join/buffer/heap state有明确Breaker Owner和cleanup lifecycle。
M2-INV-8  Selection publication仍由Mutation/Storage Owner执行。
M2-INV-9  Point operation不为架构整齐承担query pipeline固定成本。
M2-INV-10 public/generated API、result、order、null、numeric、failure与publication保持不变。
M2-INV-11 每个实施slice退出时，被替换family只有一套physical decision/state path。
M2-INV-12 无profile/evidence的未来kernel、backend或DAG不进入production surface。
```

## 12. Stop rules

出现以下任一情况必须暂停并等待Product Owner：

- 需要改变Blueprint、public/generated API或Canonical语义；
- 必须引入general DAG、public Batch/Vector、第三artifact/dependency或新Java版本；
- Reference独立性无法保持；
- conservative resource estimate与目标算法无法同时成立；
- 正确性与性能必须二选一；
- 一个slice无法删除旧decision/state path而形成长期双实现；
- profile证明统一抽象引入稳定性能退化且没有局部修正；
- 需要让SOMA Engine queued intent成为current implementation input；
- release/publication权限扩张。

## 13. 冻结结论

本设计已经完整回答目标、Owner、element shape、segment/breaker/kernel/frame/morsel、资源、Reference、
parallel、operation mapping、过度设计与stop rule。剩余工作是正式Design晋升与实施授权，不是继续扩张
候选模型。
