# SOMA Java V1 规划与优化 Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Typed Logical/Predicate IR、normalization、semantics-preserving rewrite、Index
substitution、Join/Group planning、statistics、reference interpreter与optimizer differential

最后审查日期：2026-08-12

本次冻结：Canonical Logical IR / Execution Engine M1 responsibility baseline；finite primitive
Chunk physical specialization

## 1. 设计目标

SOMA把Java generated frontend表达的typed computation lower为唯一Canonical Logical IR，在
terminal-start绑定authoritative state，再形成normalized与physical decision。Optimization只能改变
成本，不能改变result、order、null/missing、duplicate、callback、failure或publication。

本文不拥有public operation naming、authoritative storage、scheduler执行细节或failure carrier。

## 2. Planner lifecycle

Pipeline构造期只记录schema/owner与Canonical logical nodes，不绑定StateRoot。Terminal流程：

```text
invocation and owner validation
    -> atomic pipeline receiver consumption
        -> Group operation admission
            -> terminal-start StateRoot/statistics binding
                -> BoundOperation
                    +-- Reference Interpreter
                    +-- logical normalization and semantics-preserving rewrite
                            -> NormalizedOperation
                                -> PhysicalPlan + ResourceEstimate
                                    -> resource admission
                                        -> operation-local ExecutionFrame
                                            -> execution / deterministic merge / publish
```

Linked pipeline的合法intermediate先claim predecessor并产生唯一open child；合法terminal在Group
admission前consume receiver。Argument/owner/state validation未通过时不claim；reusable source不
进入claim状态机。

Public expression/Field/Order marker不是planner SPI。Lowering前只接受SOMA-issued、owner与
composition provenance匹配的node/endpoint；application implementation、foreign composition与
replayed node在phase-1 validation稳定失败，不能进入normalization或触发internal cast failure。
Canonical nodes可以随合法lazy pipeline存在；Bound、Normalized、PhysicalPlan与ResourceEstimate只对
本次bound roots有效，terminal后不复用。V1没有public prepared query。

## 3. Canonical Logical IR

### 3.1 责任与生命周期

Java lowering负责generated carrier真伪、composition/Group/Table/Field owner、one-shot与argument
validation，并把合法frontend operation转换为closed Canonical family。Canonical operation是immutable、
data-only semantic root：一个source或bounded binary relation、零个或多个stage、恰好一个terminal，
以及独立的`ExecutionRequest`。它不绑定StateRoot、statistics、physical Index、locator、scratch、
worker或resource lease。

Pipeline claim状态属于Java facade，不进入Canonical node。Direct capacity/point operation、metadata与
runtime configuration继续走Execution Owner的direct operation lifecycle，不为形式统一强行进入IR。

Canonical node至少表达：

- element shape：Row、schema-known Field、mapped reference、primitive、Relation Pair、Group result；
- composition/Table/Field/Index logical identity与dependency set；
- row lineage与mutation capability；
- encounter-order descriptor；
- exact/upper/lower cardinality；
- nullability、relation truth、equality/order capability；
- pure expression或opaque callback barrier；
- required logical leaves、logical access candidate与resource class。

`ExecutionRequest`独立表达`SEQUENTIAL | PARALLEL`，不是filter/map stage，也不在subplan中重复。

Node family：

```text
Source
    TableSource / IndexSelectionSource
Unary
    ExpressionFilter / CallbackFilter
    FieldProject / CallbackMap / PrimitiveMap
    Distinct / Sort / Top / Skip / Limit
Relation
    GroupBy / EqualityJoin / CrossJoin
Terminal
    Scalar / Match / Materialize / Callback / Update / Remove / Explain
```

Internal node/class/serialization不是public surface。

### 3.2 Canonical identity

Logical identity复用现有compiled composition descriptor/capability与generated ordinal空间：

- Table identity = composition + Table ordinal；
- Field identity = Table identity + logical Field ordinal；
- Index identity = Table identity + Index ordinal，indexed Field由compiled descriptor推导。

Identity不持有generated Table facade、Group graph、StateRoot、physical leaf offset、sidecar或storage
address，也不承诺public serialization。Binding按identity解析当前Table/root并重新验证Group与
currentness。实现优先使用compact descriptor/ordinal；不得为概念对称性建立四层wrapper object graph。

### 3.3 Type、shape与literal

`LogicalType`引用compiled schema descriptor，表达primitive、String、Enum、`@SomaValue`、ordinary
reference及其null/equality/order/materialization capability，不以reflection重新发现type。
Arbitrary Java mapper结果使用host reference shape，不伪装成schema `LogicalType`。

`TypedLiteral`拥有expected logical type与immutable canonical leaf snapshot，在construction完成checked
size/byte、null与equality normalization；`in`在此阶段defensive copy并去重。它不持有generated probe、
Table、StateRoot或Index。Physical equality/hash/lookup直接消费其typed leaves，不复制第二个probe。

Java `table.field` direct source只有一个Canonical形态：`TableSource + FieldProject`。Direct leaf或
encoded scan fusion属于physical choice，不能建立第二套Field source semantic truth。

### 3.4 Host-bound callback

Opaque Java callback由最小`HostCallbackHandle`承载：只保存受控callback引用、callback kind与无法由
enclosing operation推导的owner/capability。Input/output shape由相邻node拥有；barrier、scope、
caller-thread/parallel capability与failure policy从callback kind的唯一合同推导，不形成property bag。
它不是serialization hook，未来frontend不能伪造Java callback handle。

### 3.5 Bound、Normalized 与 Physical decision

Group admission后，`BoundOperation`一次性绑定participating logical identity到immutable published
StateRoot、state version、statistics、runtime capability与operation provenance。它不持有locator
buffer、cursor、membership hash、physical posting或worker partition，不回写Canonical IR，也不跨
terminal缓存。

`NormalizedOperation`拥有deterministic semantic rewrite结果：constant/boolean normalization、相邻
typed filter集合、dependency/lineage/barrier/residual、required leaves、checked cardinality bound与
Join/Group等价推导。它只能记录Key/Index eligibility，不能持有physical sidecar或lookup cursor。

`PhysicalPlan`拥有本次bound roots上的access path、kernel、Join/Group algorithm、partition与
deterministic merge decision；`ResourceEstimate`是其checked conservative peak投影。两者都不能
分配O(N) execution storage、调用application callback或读取未绑定的current state。Actual cursor、
membership、sort/hash/materialization buffer、workers与staging由Execution Owner在resource admission
后创建的operation-local ExecutionFrame拥有。

对schema-known、typed-only、streaming segment，Planning可以选择有限的representation-owned
Chunk kernel。Kernel eligibility、compiled predicate/leaf binding、parallel responsibility和对应
temporary peak必须在本次`PhysicalPlan + ResourceEstimate`中一次形成；terminal与execution kernel只
消费该decision，不重新推导第二套资格或scratch事实。当前正式准入的最小集合是Table count、
integral Field sum与ordered `long[]` materialization；不满足条件时使用同一PhysicalPlan Owner下
既有optimized family，不转Reference、不产生unsupported failure。

这种specialization是physical decision，不是新的Canonical node、public Batch/Vector、prepared plan
或跨terminal kernel cache。后续增加representation/type/operator cell必须重新经过profile、
differential、resource与代码规模准入。

Current V1只需要operator chain/tree，不建立general DAG、stable node id、fan-out、prepared/cache或
planner/frontend SPI。Row、Mapped、Primitive、Relation与Group可以保留specialized physical family；
统一Canonical semantics不等于统一成boxed universal executor。

## 4. Predicate IR

`SomaExpression<R>`是owner-bound typed row predicate；`SomaRelationExpression`可包含Join左右
任一或两侧dependency。Expression无side effect、literal stable snapshot、可重用。

Opaque lambda是callback node：optimizer不分析bytecode，不跨callback移动、消除或复制logical
node，也不改变它接收的element/owner。Behavioral callback必须满足Logical Design的non-
interfering contract；full-traversal parallel callback的wall-clock order不是application contract。
含opaque callback的short-circuit segment与所有Comparator-based sort/top/min/max是canonical
sequential barrier。

Terminal binding验证：

- Table/Selection expression只依赖当前Table；
- Join expression只依赖本Join左右Table；
- Group/composition/owner一致；
- third Table、foreign Group/composition为`INVALID_ARGUMENT`。

## 5. Boolean 与 null semantics

Table Field predicate使用two-valued result：

- `eq(nonNull)`对null为false；
- `ne(nonNull)`对null为true；
- order/between/in对null为false；
- `isNull/isNotNull`显式选择reference null；
- `eq(null)`与`in`包含null为`INVALID_ARGUMENT`。

`between(lower,upper)`包含两端，lower > upper在planning前为`INVALID_ARGUMENT`。空`in()`是
constant FALSE；重复literal按集合语义去重。Nullable natural-order Field的ascending是null-
first、descending是null-last；`SomaOrder.then`按声明顺序形成lexicographic tie-break。

Outer Join missing side不是Field null。Relation predicate内部使用：

```text
TRUE / FALSE / MISSING
```

Filter只保留TRUE。Strong three-valued composition：

```text
NOT: TRUE -> FALSE, FALSE -> TRUE, MISSING -> MISSING
AND: FALSE dominates; TRUE identity; MISSING AND MISSING -> MISSING
OR:  TRUE dominates; FALSE identity; MISSING OR MISSING -> MISSING
```

Optimizer不能把missing改写成null bucket或ordinary Field value。

## 6. Canonical filter style

同一logical stage中：

```java
.filter(A)
.filter(B)
```

是top-level AND canonical style，与`filter(A.and(B))`逻辑等价。相邻typed filters可规范化为
predicate set；`and/or/not`保留括号与复杂表达式。

Callback、map、limit/top、mutation或改变element/invocation semantics的node是normalization
边界。任何跨边界移动都需要独立等价规则，不能由“看起来更快”推断。

## 7. Fixed optimization phases

1. structural/owner/type validation；
2. terminal-start root/statistics binding；
3. constant folding、adjacent typed filter集合化、boolean normalization；
4. dependency analysis、predicate rewrite、residual tracking、Index substitution、null/missing
   pruning；
5. leaf pruning；
6. stateless kernel fusion；
7. stateful barrier planning；
8. Join/Group algorithm与build-side selection；
9. representation-aware finite kernel choice；
10. cardinality、managed-memory、container/array与task ResourceEstimate；
11. deterministic partition/merge description；
12. Execution Owner依据ResourceEstimate完成admission并创建ExecutionFrame。

Phase order是Design contract。Implementation可细分internal pass，但不能让resource admission在
不可逆work之后，也不能让physical choice先于logical semantics固定。

## 8. Rewrite legality

Optimizer可以：

- fold pure typed constants；
- combine/reorder pure Field predicates；
- replace eligible equality filter with Key/Index lookup；
- prune unused leaves；
- fuse stateless typed kernels；
- choose hash/lookup/merge/nested-loop Join；
- select compression-aware kernel；
- choose physical build side while preserving logical output order。

Optimizer不能：

- cross、delete或duplicate opaque callback node，或改变其logical input/argument；
- delete callback map because terminal ignores mapped value；
- cross sort/distinct/limit/top/mutation without proven rule；
- replace encounter order with hash/bucket/worker order；
- change floating reduction tree；
- change failure precedence or turn unsupported into fallback；
- treat missing relation side as null；
- remove Outer Join residual without proof。

Optimized `top(n, order)`可以不用完整sort，但oracle固定为stable
`sortedBy(order).limit(n)`；tie、null placement、Comparator schedule、n==0/n>=count与failure必须
等价。

## 9. Index substitution

Typed equality/range eligibility由Field capability决定；V1 Index substitution只针对existing exact
Key/non-unique Index。

Substitution必须保持：

- duplicate cardinality；
- bound StateRoot canonical order；
- nullable Index bucket与Join null-never-match区别；
- same structured failure；
- expression dependency与Group currentness。

没有Index或statistics时使用scan，不产生runtime unsupported。

Mapped reference `distinct`使用`Objects.equals/hashCode`并按encounter order保留first，null至多
保留一个。Optimizer若选择hash/sort representation，必须与这一定义及ordinary referent identity
合同等价。Arbitrary mapped reference的application `equals/hashCode`是opaque caller-thread
barrier；只有schema-known String/Enum/Value equality可以进入parallel/specialized path。

## 10. Equality Join planning

Logical relation固定left/right、equality components、kind与post-Join predicate。Physical planner
可选择：

- compatible Key/Index lookup Join；
- one-side hash build + order-preserving probe；
- proven-order merge Join；
- small-input bounded nested loop。

所有算法产生相同：

- Inner/Left/Full/Semi/Anti semantics；
- null-never-match；
- non-null Value的structural equality（包括允许的null reference leaves）；
- repeated value Cartesian matches；
- left/right encounter order；
- Semi/Anti的left membership existence semantics（每个left最多一次，right duplicate不放大）；
- missing-side truth；
- checked cardinality与primary failure。

V1只有two logical inputs，不做multi-way Join reorder。Physical build side可交换，但logical
left-driven publication不能改变。

Logical inputs必须是same composition/same Group中的两个不同generated Table type。V1不建立
alias identity，因此self-Join/self-Cross在generated surface缺席。

## 11. Join predicate pushdown

Reference semantics是先产生logical Join relation，再按用户filter顺序求值。Optimizer可以从
typed predicate导出input predicate：

```text
normalize
    -> dependency analysis
        -> Join-kind equivalence proof
            -> derived side predicate
                -> Index substitution
                    -> physical Join
                        -> residual predicate
```

- Inner单侧pure predicate通常可完全下推；
- Left/Full/Semi/Anti使用各自证明过的rule；
- Outer可用derived input predicate缩小候选，但在未证明完全等价时保留post-Join residual；
- cross-side OR/complex dependency通常保留在Join后；
- callback永不push、merge、reorder或Index substitution；
- 不提供`filterLeft/filterRight`性能API。

任何新Outer rewrite必须先加入reference differential matrix，再进入optimizer。

## 12. Group planning

GroupBy logical plan固定key equality、bound encounter order与单一aggregate。Planner可以选择
hash/sort/Index-assisted grouping，但必须保持：

- null key形成normal group；
- key首次出现order；
- exact count/numeric result与overflow；
- typed columnar result；
- checked cardinality/hash/materialization budget。

Approximate statistics只影响cost，不进入application result。

## 13. Statistics

Planner只读取与StateRoot一起atomic published的immutable statistics snapshot：

- size/null count；
- Chunk min/max/encoding；
- Index distinct/bucket/posting count；
- internal approximate selectivity。

Statistics missing时采用conservative plan。Metadata query不偷跑full scan或arbitrary Object
inspection。Approximate statistics不变成approximate user aggregate。

## 14. Sequential reference interpreter

Reference interpreter是同一Bound Canonical operation的简单顺序语义实现：

```text
CanonicalOperation + BoundOperation
    +-- Reference Interpreter: correctness-first
    +-- Normalize -> PhysicalPlan -> Optimized Executor
```

它不是public engine choice、debug switch、unsupported fallback或production double execution。

Reference与optimized path共享：

- terminal-start roots与Group admission；
- equality/null/missing/order；
- checked cardinality/numeric；
- Join/Group duplicate/order；
- callback invocation/barrier；
- selection/result/failure；
- mutation staging/Result/zero publication。

Reference path不消费NormalizedOperation或PhysicalPlan，不使用Index substitution、fusion、compression
kernel、cost-model build choice、parallel partition或approximate statistics。它仍使用相同的32位结构域/
64位累计域、checked arithmetic与resource boundary，不能以测试oracle名义使用不成立的int/unbounded
structure，也不能成为optimizer failure fallback。若reference operation需要O(N) storage、materialized
result或callback work，reference interpreter必须独立形成correctness-first conservative estimate并通过
A25 admission；它不能复用production PhysicalPlan/ResourceEstimate来换取表面一致。

Application Comparator operation是opaque stateful barrier。V1对sort/top/min/max使用各自同一
canonical comparison schedule，允许parallel pipeline在该stage退化为caller-thread sequential
execution；typed `sortedBy`/natural primitive operation才可使用已证明等价的parallel/specialized
order kernel。这样Comparator的structured failure不因physical algorithm漂移。

## 15. Differential proof

每个optimizer capability必须验证：

```text
same schema + independent copy of same state + same logical plan
    -> reference result/failure/final state
    -> optimized sequential result/failure/final state
    -> optimized parallel result/failure/final state
    -> exact comparison
```

比较包括detached content、encounter order/tie、floating bit contract、failure code/context、
mutation前后payload/Key/Index/size/capacity/version与resource release。

真实side-effect callback不能在同一production operation执行两次。Test使用pure callback、
recorded invocation trace或独立state copy。

## 16. Explain contract

`_explain()`从同一operation的layer snapshots投影：

- original logical stages；
- typed predicate dependency；
- derived pushdown与retained residual；
- callback/stateful barrier；
- Index substitution；
- Join/Group/codec choice；
- encounter-order preservation与estimated peak。

Explain text不稳定、不可parse驱动业务；它不是plan handle。

## 17. Evidence Gate

Production evidence至少覆盖：

- expression/callback overload与dependency validation；
- boolean/null/missing truth tables；
- adjacent filter normalization与barrier negatives；
- Key/Index substitution的duplicate/order/null/failure；
- every Join kind + pushdown/residual matrix；
- Group algorithm differential；
- reference/optimized sequential/parallel exact differential；
- floating/numeric、checked cardinality与resource failure；
- `_explain()`包含必要decision且不执行callback；
- random plan/property/fuzz corpus与three reference journeys。
- permanent minimal test-only lowering fixture证明Canonical semantics不依赖Java facade object identity；
  该fixture不是production frontend SPI、JSON frontend或第三artifact。
