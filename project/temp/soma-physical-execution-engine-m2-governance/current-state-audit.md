# SOMA Physical Execution Engine M2 当前状态审计

状态：`COMPLETE / INPUT_TO_FROZEN_CANDIDATE`

日期：2026-08-12

基线：`develop@d106484161d70f8d3f5f344fe06c61308c1bf400`

## 1. 审计方法

本审计以正式Planning、Execution、Architecture、Storage、Failure与Core Owner为上游，逐条追踪
current code中的：

```text
frontend lowering
-> Canonical / Bound / Normalized
-> Physical decision + ResourceEstimate
-> admission + Frame
-> sequential / parallel execution
-> result or mutation publication handoff
```

审计不以class数量判断架构质量，而以每项事实是否具有唯一Owner、是否存在重复decision/state、是否能
解释正确性、资源和性能为标准。

## 2. 已经成立的主链

当前Row source的production主链已经完整：

1. `QueryOperation`只做Java facade lowering；
2. `CanonicalQueryOperation`取得Group guard并在terminal开始时绑定StateRoot；
3. `CanonicalRowPlanner`执行normalization、access-path、parallel与finite vector decision；
4. `CanonicalRowPhysicalPlan`拥有最终decision与`ResourceEstimate`；
5. actual temporary lease成功后才创建`CanonicalRowExecutionFrame`；
6. sequential走`CanonicalRowExecution`或finite Chunk kernel；
7. parallel由`CanonicalParallelWorkScheduler`统一管理caller participation、ordinal、取消、失败和quiescence；
8. Reference从Bound分叉，直接由`ReferenceCanonicalRowInterpreter`解释，不消费Normalized或Physical事实。

因此M2不能推翻M1，也不能恢复old adapter。它应当把这条主链中隐含的物理执行结构显式化。

## 3. 执行family inventory

| Family | Semantic/Bound Owner | Physical decision | Actual state/execution | 当前物理形态 |
|---|---|---|---|---|
| Table / Row | `CanonicalRowOperation` / `BoundCanonicalRowOperation` | `CanonicalRowPlanner` / `CanonicalRowPhysicalPlan` | `CanonicalRowExecutionFrame` / `CanonicalRowExecution` | source + stateless stages + family-local stateful stages |
| Field direct | Row source + `FIELD_PROJECT` | Row planner + finite vector decision | vector kernel或Primitive fallback | Table source的schema-known projection |
| Mapped reference | Canonical Row source + mapped capture | Row physical plan + mapped-local scratch | `MappedQueryOperation` / `ObjectBuffer` | Row segment之后的host callback pipeline |
| Primitive mapped | `CanonicalPrimitiveOperation` | Row plan + primitive/vector decision | `PrimitivePlanOperation` / `LongValueBuffer` | unboxed primitive stages；部分Chunk-native |
| GroupBy | `CanonicalGroupOperation` + Row source | Row physical plan；group scratch family-local附加 | `CanonicalGroupingQueryOperation.GroupState` | streaming Row source + hash aggregation breaker |
| Relation | `CanonicalRelationOperation` / bound relation | `PhysicalRelationPlan` | relation frame + `GeneratedRelation` hot loops | binary build/probe或lookup；可向下游Row输出left locator |
| Selection update/remove | Canonical Row selection | Row physical plan + mutation scratch | locator membership + `SelectionWriteSet`/`SelectionRemovePlan` | query pipeline与atomic publication之间的handoff |
| Point get/add/update/remove | direct Table/Storage path | 无query PhysicalPlan | preflight/prepare/commit + StateRoot publication | 正确地位于physical query engine之外 |

## 4. 已存在但尚未统一命名的物理概念

| 候选概念 | Current executable fact |
|---|---|
| Source | Table scan、Key/Index lookup、IndexSelection、Relation-left source |
| Segment | Row stateless stage loop、Mapped stage loop、Primitive stage loop、finite Chunk kernel |
| Breaker | `IntLocatorBuffer` stateful Row、`ObjectBuffer` mapped sort/distinct、`LongValueBuffer` primitive stateful、`GroupState`、Relation hash/build state |
| Kernel | Row visitor、predicate evaluator、primitive loop、encoded/plain/RLE Chunk handler、Join lookup/hash loop |
| Frame | Row frame、Relation frame及若干family-local state对象 |
| Morsel | Row Chunk-aligned range、finite vector Chunk ordinal、Relation left-side range |
| Deterministic merge | Row range按ordinal合并、Chunk partial按ordinal归并、materialization prefix+disjoint write |

这说明候选模型不是从数据库术语反向套代码，而是为现有事实寻找最小统一语言。

## 5. 主要重复与耦合

### 5.1 Stage traversal重复

Row、Mapped与Primitive分别实现filter/map/skip/limit/stateful边界和callback包装。它们的element shape不同，
不能被一个boxed universal loop替代；但pipeline边界、stage lifecycle与资源liveness可以共享一个物理模型。

### 5.2 Stateful state由family局部拥有

- Row在`CanonicalRowExecution`内部创建locator buffer并执行sort/distinct/top；
- Mapped创建`ObjectBuffer`与distinct/sort结构；
- Primitive创建`LongValueBuffer`与自己的stateful loop；
- GroupBy把完整hash state嵌入`CanonicalGroupingQueryOperation`；
- Relation另有独立PhysicalPlan、Frame与build/probe state。

这些不是五套语义，但现在缺少“Breaker拥有何种state、何时分配、何时释放、怎样恢复canonical order”
的共同合同。

### 5.3 PhysicalPlan表达力不完整

Row PhysicalPlan已经拥有access、normalized stages、parallel prefix、partitions、resource和finite vector
decision，却尚未显式拥有segment/breaker拓扑。GroupBy的最终物理形态甚至表现为“Row physical plan +
extra scratch + family-local GroupState”，使plan无法完整回答一次operation实际会执行哪些operator。

### 5.4 ResourceEstimate仍是family-local常数模型

现有估算在正确性上保持保守，但很多scratch通过`perRow * upperBound`追加。它没有从operator state
liveness推导“哪些buffer同时存活”。M2应让ResourceEstimate与完整PhysicalPlan同源；第一阶段仍可以
保守地一次租借whole-operation peak，不引入动态lease。

### 5.5 finite vector kernel是一座优化岛

当前`CanonicalPrimitiveVectorKernel`已覆盖：

- Table count；
- integral Field sum；
- ordered `long[]` materialization；
- zero/one pure typed integral predicate；
- PLAIN、encoded/RLE与有限overlay handler；
- sequential与Chunk-ordinal parallel。

它证明Chunk-at-a-time、representation-aware、typed fusion可显著降低成本；但eligibility与execution都
封装在单个closed class中，尚不能作为GroupBy或其他segment的统一upstream机制。

### 5.6 parallel lifecycle统一，work preparation仍分裂

`CanonicalParallelWorkScheduler`已经是唯一scheduler lifecycle Owner。Row prefix使用Chunk-aligned range，
finite vector使用Chunk ordinal。M2只需统一“Morsel是有canonical ordinal的bounded work unit”以及merge
合同，不应再建executor、thread pool或scheduler。

### 5.7 Relation-left存在显式物化桥

Relation结果作为下游Row source时，会先物化left locator buffer再交给Row pipeline。这是合法breaker，
但也暴露内存搬运边界。M2应先把它描述为binary pipeline与unary downstream之间的materialization
bridge；是否可以stream/fuse必须由后续profile和语义证明，不在设计阶段预先承诺。

## 6. Owner矩阵

| 事实 | 唯一正式Owner | M2允许做什么 | M2不得做什么 |
|---|---|---|---|
| logical result/order/null/numeric | Planning + Logical + Failure | 保持并验证 | 重定义 |
| Canonical/Bound/Normalized | Planning | 消费其输出 | 建第二套IR |
| final Physical decision/resource estimate | Planning | 增加最小segment/breaker/morsel表达 | 把actual worker/state放入plan |
| guard/lease/frame/scheduler/quiescence | Execution | 统一operator state lifecycle | 把normalization放入Frame |
| Chunk/representation/StateRoot | Storage | 选择handler并借用只读access | 创建第二storage truth |
| Reference oracle | Planning/Conformance | 与新Physical exact differential | 解释PhysicalPlan或作为fallback |
| mutation atomic publication | Storage/Execution | 接收selection membership/write-set | 由query kernel直接改StateRoot |
| public/generated surface | Signature/Logical | 保持不变 | 增加physical API |

## 7. 高价值架构缺口

按价值排序：

1. PhysicalPlan无法完整、统一地描述所有实际operator和state boundary；
2. stateless segment与stateful breaker缺少共同lifecycle/resource/order合同；
3. Mapped/Primitive/Row存在结构重复，但不能以boxing方式粗暴合并；
4. GroupBy/Join的物理state没有完全进入统一Frame模型；
5. finite Chunk kernel难以按证据逐步扩展到其他segment；
6. Morsel与deterministic merge尚未成为所有parallel family共享的计划事实；
7. explain无法稳定地投影“segment/breaker/kernel/morsel strategy”这一层架构信息。

这些缺口足以支持M2治理成立，但不支持一次性重写全部execution code。

## 8. 不构成问题的现状

- Reference与production拥有独立执行代码是正确的防腐边界；
- 不同element shape拥有不同typed kernel是必要specialization，不等于架构重复；
- Point operation不经过Canonical query pipeline是正确边界；
- callback barrier不能被任意融合或跨线程重排；
- unsupported vector case退回现有optimized physical path是正常physical choice，不是失败；
- GroupBy/Join当前性能热点存在，不证明必须引入通用数据库operator DAG。

## 9. 审计结论

当前系统具备M2治理条件：semantic/binding/resource/scheduler地基已经稳定，有限Chunk kernel和
GroupBy/Relation实现提供真实反例与性能事实。正确方向是统一PhysicalPlan与ExecutionFrame的物理语言，
而不是再增加一套执行逻辑。
