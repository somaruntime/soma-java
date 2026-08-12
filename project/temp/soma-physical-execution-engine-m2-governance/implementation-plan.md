# SOMA Physical Execution Engine M2 实施计划

类型：Engineering Candidate / Sequential Slice Plan

状态：`FROZEN_CANDIDATE / READY_FOR_PROMOTION_REVIEW / IMPLEMENTATION_NOT_AUTHORIZED`

日期：2026-08-12

> 本计划不是implementation authorization。正式Design晋升、Baseline审查与Product Owner明确授权完成后，
> 才能激活P1。一次只能有一个active slice；每个slice必须完成replacement、evidence、review、
> Conformance与干净提交后才能进入下一项。

## 1. 实施目标

在不改变public/generated与Canonical语义的前提下，把current family-local物理执行事实收敛为：

```text
one Physical Pipeline Plan
    -> one ResourceEstimate
        -> one admitted ExecutionFrame
            -> specialized Segment / Breaker kernels
                -> one bounded ordinal-work scheduler lifecycle
```

实施不是“所有操作都改成vectorized”，也不是“删除所有family-specific代码”。目标是统一decision、
state lifecycle、resource、morsel与explain；hot kernel继续按typed shape专门化。

## 2. 实施总则

1. **纵向替换**：每个slice必须让选定operation从planning到execution完整闭环；
2. **一个active path**：不先铺全套空接口或parallel hierarchy；
3. **Reference先行**：同一Bound semantics先有独立oracle，再替换production physical path；
4. **Admission先于state**：actual buffer/hash/partial/worker state只在lease后创建；
5. **保留specialization**：不得以boxing、reflection、per-row allocation换取统一；
6. **复用scheduler**：不得增加线程池、scheduler或worker abstraction；
7. **无长期bridge**：slice退出时删除被替代的physical decision/state path；
8. **targeted-first**：只运行与changed surface匹配的证据；没有新信息不重复Profile；
9. **性能是acceptance**：正确性通过但出现无法解释的复杂度/allocation/throughput退化，slice不关闭；
10. **不借机扩产品**：SOMA Engine、new Join、Batch、Loader、DAG、off-heap等保持排除。

## 3. P1 — 最小Physical Pipeline骨架

目标：用已经成立的stateless finite Chunk slice建立最小、可执行、无行为变化的Segment/Kernel/Morsel
plan representation。

工作：

- 在final Row PhysicalPlan内部表达source、一个stateless Segment、terminal sink、kernel与Morsel decision；
- 让existing Table count、integral Field sum、ordered `long[]`使用该plan，而不是另有第二个eligibility truth；
- `CanonicalPrimitiveVectorKernel`可保留实现，但eligibility/resource/handler决定必须由final plan拥有；
- ExecutionFrame按segment ordinal拥有actual state；
- explain增加segment/kernel/morsel的safe诊断投影；
- 删除被替代的finite vector parallel decision或重复resource calculation。

Exit evidence：

- zero/one typed predicate、PLAIN/encoded/RLE/overlay、empty/all/no-match；
- sum overflow与ordered materialization；
- sequential/parallel/Reference exact differential；
- resource/failure/quiescence；
- 1M stateless fixed-host A/B，sub-ms路径无稳定fixed-cost或throughput退化；
- no new public signature/dependency/artifact；
- no parallel eligibility/resource owner。

## 4. P2 — Row、Field、Mapped与Primitive stateless Segments

目标：统一unary stateless pipeline boundary，同时保留Row locator、primitive和host reference三种typed
execution shape。

工作：

- planner显式发现Segment、callback barrier与element shape transition；
- Row typed filter/projection/count、Field direct、primitive map/conversion进入统一plan结构；
- Mapped reference保留host callback kernel，不转换为universal Object batch；
- callback前后Segment不发生非法重排；
- current optimized row/scalar loops作为合法kernel选择，而不是被一次性删除；
- 合并重复的stage-boundary、Morsel与resource decision，保留必要typed loop。

Exit evidence：

- operation/type capability matrix与Java 8 generated consumer；
- callback exactly-once/thread/failure/barrier；
- skip/limit、match、numeric、materialization三路differential；
- required leaves与representation handler；
- 10K fixed cost、1M source/mapped/primitive profile；
- no per-row plan node、boxing、metadata lookup或allocation-class regression。

## 5. P3 — Unary stateful Breakers

目标：让Row、Mapped、Primitive的distinct、stable sort、top与materialization显式成为Breaker/sink，
并由PhysicalPlan/Frame完整拥有state与资源。

工作：

- 建立有限breaker descriptor：membership、reorder、bounded top、materialization；
- 从family executor中抽离algorithm decision和scratch estimate；
- actual locator/object/raw primitive buffer、set、heap只在Frame中创建；
- 保持typed implementation，不创建generic `ObjectBuffer`替代primitive路径；
- 定义breaker input/output order、cardinality与merge contract；
- parallel只在有证明的breaker上增加partition/merge；其余保持sequential breaker并允许parallel upstream。

Exit evidence：

- distinct first、stable tie、top=stable sort+limit、empty/limit边界；
- Row/Mapped/Primitive Reference differential；
- conservative peak、growth与detached result admission；
- 1M frontier-stateful A/B与allocation profile；
- no second breaker truth or double materialization。

## 6. P4 — GroupBy aggregation Breaker

目标：把GroupBy从“Row plan + family-local extra scratch/GroupState”提升为完整Physical Pipeline中的typed
aggregation Breaker。

工作：

- Canonical/Bound语义保持不变；
- planner选择key/value projection、hash aggregation kernel、capacity hint与result sink；
- final plan拥有完整group resource estimate；
- actual buckets、links、representatives、aggregate/floating sequences进入Frame；
- low/high cardinality与nullable reference key保留specialized handler；
- first-encounter order通过input ordinal/representative显式维护；
- 默认先迁移current sequential breaker；partitioned partial aggregation只有profile、资源和deterministic
  merge验证都成立时才作为同slice后半段准入，否则不预建placeholder。

Exit evidence：

- filtered/unfiltered、primitive/reference key、null、all aggregates；
- Reference exact result/order/numeric/failure differential；
- tiny-budget与growth-peak fault evidence；
- 1M low/high cardinality A/B与JFR；
- 若引入partial aggregation，sequential/parallel exact equivalence和资源峰值必须单独闭合；
- no dynamic aggregate record/public API change。

## 7. P5 — Relation与Selection handoff

目标：让binary Relation与Selection mutation完整服从同一PhysicalPlan/Frame/resource/morsel合同，同时
保持各自正式Owner边界。

工作：

- 将Relation source、build/probe/lookup operator、pair/left output与downstream Segment表示为bounded
  binary pipeline；
- `RIGHT_INDEX_LOOKUP`、`RIGHT_HASH`、`NESTED_CROSS`保持finite algorithms；
- relation hash/matched-right/left partition state进入admitted Frame；
- 评估relation-left locator bridge；没有profile证据不消除；
- Selection query输出frozen membership/write-set/remove-plan handoff；
- mutation validation/publication继续由Mutation/Storage Owner执行；
- resource estimate覆盖selection与mutation staging的共同peak。

Exit evidence：

- all Join kinds、null-never-match、duplicate Cartesian、outer missing、canonical order；
- pushdown/residual与Reference independence；
- Relation→Mapped/Primitive/Group downstream；
- Selection zero match/change、callback failure、atomic publication/fault injection；
- 1M relation与selection changed-path A/B；
- no new Join capability或cross-Table transaction。

## 8. P6 — 全局closure、Owner晋升与资格

目标：证明所有计算family已经进入唯一M2 physical architecture，并关闭Temporary replacement。

工作与证据：

- inventory扫描证明没有旧segment/breaker/resource/morsel parallel truth；
- complete Reference/optimized sequential/parallel differential；
- Java 8 deterministic generation、public ABI与negative capability；
- resource/failure/callback/quiescence/mutation publication；
- 10K/1M/10M changed-family性能前沿与三个reference application；
- one bounded independent read-only review；
- 正式更新Planning、Execution、Architecture、Core与Conformance Owner；
- 删除M2 Temporary，保留最小permanent executable evidence；
- G4/G5/G6/G7/G9受影响部分保持`PASS`；
- release/publication仍`NOT_AUTHORIZED`。

## 9. 性能守卫

每个slice采用同commit/host/JDK/JVM/rows/parallelism的baseline/candidate：

- changed hot path至少3个fresh JVM run，独立correctness fingerprint；
- 先看复杂度、allocation class、temporary peak和Profile归因，再看median；
- repository comparator的`15% + 2 ms`继续作为broad regression ratchet；
- 对sub-ms vector hot path，若退化超过约20%且绝对超过0.05 ms，必须重取样并解释，不能因未触发
  broad threshold直接忽略；
- 任何O(N)新增copy、per-row allocation/boxing、double traversal或串行breaker扩大都必须有设计理由；
- 确认噪声后的小波动可记录，不把固定主机数字升级为兼容合同。

如果修正退化需要改变semantic/resource/failure合同，触发stop rule而不是静默接受。

## 10. 证据矩阵

| Contract | P1 | P2 | P3 | P4 | P5 | P6 |
|---|---:|---:|---:|---:|---:|---:|
| public/generated unchanged | targeted | targeted | targeted | targeted | targeted | full |
| Reference differential | stateless | unary | stateful unary | Group | Relation/Selection | full |
| resource before state | vector | segments | breakers | group hash | relation/mutation | full |
| sequential/parallel | Chunk | unary | supported breaker | optional partial | relation | full |
| performance/profile | 1M source | source/mapped | stateful | Group | relation/mutation | 10K/1M/10M/apps |
| replacement scan | vector island | stateless loops | buffers/heaps | GroupState | relation/handoff | all |

## 11. Stop rules

[Candidate Design](design.md#12-stop-rules)全部适用。此外：

- 一个slice需要同时修改两个以上未迁移family才能获得可运行结果；
- plan representation开始复制Canonical/Normalized semantics；
- breaker abstraction只能通过generic Object/boxing成立；
- Morsel需要新executor或改变callback thread semantics；
- resource estimate必须在执行中观察实际state后才能成立；
- performance evidence停止产生新信息却继续重复review/benchmark；

均应暂停、缩小slice或请求Product Owner裁决。

## 12. 估算

这是跨Planning/Execution/Group/Relation/Mutation的中大型internal refactor。实施工作量应按slice风险而非
行数驱动，预计production/test/doc净变化约`4,000-8,000`行，实际可因删除重复family code显著降低。
P1是最小地基；P4/P5风险最高。不得为了满足估算预先创建空抽象或扩大代码量。
