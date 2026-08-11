# SOMA Canonical Logical IR 与执行引擎 S3 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / S3_CLOSED / S4_READY`

日期：2026-08-11

Owner：Canonical IR/Execution S3 Mapped与Primitive family的implementation fact、exit evidence、
性能边界与S4准入状态

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 结论

S3通过。普通Java mapped-reference与primitive pipeline在terminal边界从Java facade capture一次性
lower为data-only `CanonicalMappedOperation`或`CanonicalPrimitiveOperation`，共享S1/S2的
`CanonicalRowOperation -> BoundCanonicalRowOperation -> PhysicalPlan -> Admission ->
CanonicalRowExecutionFrame`主路径。reference与production消费同一Bound Row语义，但保持独立的
mapped/primitive遍历和stateful算法；primitive physical kernel继续使用unboxed `long`/bit表示和
direct Chunk Field traversal。

本slice没有改变public/generated API、结果类型、encounter order、null/equality、numeric、callback、
failure或resource合同，没有新增dependency、artifact、SPI、JSON/Workflow或SOMA Engine实现。
Relation/Group仍由S4拥有。

## 2. Implementation fact

### 2.1 Semantic ownership

- `MappedPipelineCapture`与`PrimitivePipelineCapture`只拥有尚未terminal的Java facade调用捕获；它们不再
  被命名或使用为semantic Plan；
- `CanonicalMappedOperation/Stage`拥有host-reference shape、mapped stage、terminal与closed callback
  handle；不持有Table facade、StateRoot、cursor、lease、membership或worker；
- `CanonicalPrimitiveOperation/Stage`拥有primitive kind、conversion、root shape、stage、terminal与closed
  callback handle；physical value继续以unboxed `long`或float/double bits表示；
- `HostCallbackHandle`只保留closed callback kind、owner identity与opaque callback，不分析lambda字节码，
  不复制可由operation推导的property；
- mapped/primitive operation以`CanonicalRowOperation(FAMILY_SOURCE)`拥有唯一source semantics，terminal
  start后与普通Row一样完成guard、binding、conservative admission、frame创建和cleanup。

### 2.2 Execution 与 reference

- production mapped kernel消费`CanonicalRowExecutionFrame`，支持streaming filter/map/slice、canonical
  distinct、stable sort、materialization、match/extremum/action及sequential/parallel Row source；
- production primitive kernel消费相同frame，保留direct primitive Field Chunk traversal、specialized
  predicates/conversions、unboxed buffer、deterministic numeric accumulator与stateful kernel；
- `ReferenceMappedInterpreter`与`ReferencePrimitiveInterpreter`直接消费同一Bound semantic operation，
  使用canonical Row scan与独立boxed/list、insertion-sort、distinct和pairwise numeric算法，不读取
  production physical decision；
- explain只投射已经绑定的canonical/physical/resource事实，不执行callback、不创建operation-local
  scratch；
- 原`MappedPlan`与`PrimitivePlan` type及文件退出；Mapped/Primitive terminal不再调用旧
  `QueryOperation`、`RowExecutor`或`BoundRowPlan`。

## 3. Correctness、lifecycle 与 resource evidence

- primitive Java 8 conversion compile matrix、mapped reference array component、parent/Object/empty/all-null、
  invalid component、negative capability与generated ABI均通过processor suite；
- mapped null/equality/distinct hash failure、stable comparator trace、callback barrier/failure、short-circuit及
  one-shot branch/permanent consumption通过runtime suite；
- primitive reference/production differential覆盖filter、map、conversion、distinct、sort、slice、signed
  order、float/double special value、integral overflow与`1023/1024/1025` deterministic block boundary；
- materialization、mapped distinct/sort与primitive buffer在callback/data work前执行checked conservative
  temporary admission；lease与frame在成功或失败前释放；
- sequential与parallel benchmark fingerprint一致，primitive hot path没有boxing、reflection、per-row
  metadata lookup或O(N) node allocation。

## 4. Java 8 与 repository evidence

执行并通过：

```text
mvn -q -pl soma-runtime test
mvn -q -pl soma-processor -am test
mvn -q -pl soma-runtime -DskipTests compile
git diff --check
```

Processor stress保持112 Tables、448 Fields、224 Indexes及Java 8 generated consumer/source/public/verbose
ABI资格。S3只改变runtime internal lowering与execution linkage；public/generated declaration和third-party
dependency没有变化。冻结的Engineering Plan SHA-256保持
`eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d`。

## 5. Fixed-host performance guard

环境沿用正式performance-frontier的Corretto 8、6 GiB SOMA budget、P8；changed family以fresh JVM、
一次外层run、2次inner warmup、5次inner sample取median，全部correctness/fingerprint `PASS`。

| Operation | 正式baseline | S3 representative median | 结论 |
|---|---:|---:|---|
| 10K mapped primitive | 未单列 | 0.212 ms | 固定成本仍为亚毫秒 |
| 10K mapped reference | 未单列 | 1.084 ms | reference materialization维持线性 |
| 1M mapped primitive | 22.144 ms | 19.963–21.480 ms | 改善 |
| 1M mapped reference | 16.174 ms | 15.154–16.507 ms | 等价或改善 |
| 1M primitive Field sum | 5.637 ms | 6.094–6.677 ms | 同一算法/分配等级；观察到单机波动，S6复核 |
| 1M Field distinct / sort / top | 4.546 / 8.977 / 8.730 ms | 4.634 / 8.939 / 8.936 ms | 等价 |

Field sum没有新增per-row allocation、boxing、reflection或复杂度变化；两次结果存在约8%–18%的固定主机
差异，暂记为S6全量性能复核观察项，不据此反复修改架构。S3未改变Relation/Group、10M或FJSP kernel，
因此不重复其重型证据。

## 6. Exit 与 claim boundary

S3 Exit：`PASS`。S4可以激活并迁移Relation/Group family；在S4关闭前不得激活S5。

本记录不证明Relation/Group已经统一，不证明10M、一亿行或跨硬件新性能，不授权Release、Package
publication、签名或正式发布声明。
