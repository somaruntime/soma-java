# SOMA Canonical Logical IR 与执行引擎 S5 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / S5_CLOSED / S6_READY`

日期：2026-08-12

Owner：S5执行引擎layer closure、迁移桥退役、replacement evidence与S6准入状态

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 结论

S5通过。Row、Field、Mapped、Primitive、Group与Relation-derived left stream已经共享Canonical
semantic、terminal-start binding、Normalized、PhysicalPlan、admission和ExecutionFrame主线；旧Row
optimizer/executor/reference/parallel adapter及其carrier已经删除。Semi/Anti Join的left `ReadStream`不再
lower回`BoundRowPlan`，而是以`RELATION_LEFT` canonical source在同一个Group operation guard内绑定两张
Table、取得完整temporary lease，再向后续Row/Field/Mapped/Primitive/Group kernel提供admitted locator source。

本slice没有改变Blueprint、public/generated API、operation semantics、encounter order、parallel、resource、
failure或mutation合同，没有新增dependency、artifact、SPI、JSON/Workflow或SOMA Engine实现。

## 2. Replacement closure

### 2.1 四层Owner

- `BoundCanonicalRowOperation`只拥有canonical semantics、Table/layout、terminal-start root、operation和
  provenance；不持有locator buffer、cursor、membership、worker或parallel source；
- `NormalizedCanonicalRow`只拥有规范化stage与predicate集合；不持有Index container、probe、cursor、
  worker或scratch；
- `CanonicalRowPhysicalPlan`统一拥有access path、parallel prefix/partition和`ResourceEstimate`；
- `CanonicalRowExecutionFrame`在lease后拥有membership、Index cursor、relation-left source、parallel source
  和execution-local state；Relation frame同样拥有right hash、matched-right与lookup cursor。

### 2.2 旧路径退出

下列旧实现及其production/test/build引用已删除并通过symbol scan：

- `BoundRowPlan`、`NormalizedRowPlan`；
- `RowOptimizer`、`RowExecutor`、`OptimizedSequentialRowExecutor`；
- `ReferenceRowInterpreter`、`ParallelRowScheduler`；
- `StableTopLocatorHeap`。

`QueryOperation`现在只是Java facade lowering adapter；Selection mutation、mapped、primitive、Group与
relation-left均调用canonical coordinator，不再拥有第二套binding、planning或execution decision。

### 2.3 Relation-left correctness oracle

- production和reference各自从同一two-root Bound Relation语义构造left locator source；
- production继续消费Physical Relation algorithm，reference继续使用独立nested-loop oracle；
- downstream mapped、primitive与GroupBy均显式接收该source，避免静默退化为整张left Table scan；
- 新差分证据覆盖Semi Join left source进入mapped materialization、primitive materialization与GroupBy。

## 3. Evidence

- `mvn -q -pl soma-runtime test`：71 tests，0 failure/error；
- `group-relation.sh`：Java 8 deterministic generation、normal/negative consumer、runtime/processor suite与
  artifact surface `PASS`；processor stress为112 Tables、448 Fields、224 Indexes；
- `parallel-execution.sh`：canonical parallel scheduler与Java 8 consumer `PASS`；
- old-carrier exact-symbol scan、layer ownership scan、`git diff --check`通过；
- qualification中删除了I5遗留的“Table不得存在`parallel()`”陈旧断言；该surface已由I6正式准入，
  Pair materialization等仍有效的negative assertion继续保留；
- 冻结Engineering Plan SHA-256保持
  `eecf4e03d0da4bf131243f3426b8013a76fa7c380ffbdd289ee7ce930bfaa93d`。

## 4. Performance boundary

本slice没有改变Join/Group algorithm或Field/Table kernel，只删除旧adapter并修复relation-left source routing。
按冻结计划的evidence budget，不在输入未变化时重复1M profile；受影响的normal-use relation-left consumer、
optimized/reference differential和parallel qualification已经通过。最终10K/1M/10M与100K FJSP防退化由S6
统一资格拥有。

## 5. Exit 与 claim boundary

S5 Exit：`PASS`。S6可以激活并执行全量non-publishing qualification、package consumer、最终性能前沿、
Owner closure与replacement独立复审。

本记录不证明S6、跨硬件SLA、一亿行性能或Release/Package publication；不授权签名或正式发布声明。
