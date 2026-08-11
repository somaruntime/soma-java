# SOMA Canonical Logical IR 与执行引擎 S2 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / S2_CLOSED / S3_READY`

日期：2026-08-11

Owner：Canonical IR/Execution S2普通Row、Field、Selection、parallel与explain主路径的
implementation fact、exit evidence、性能边界与S3准入状态

上游：[正式晋升与实施准入](v1-canonical-ir-execution-engine-promotion-readiness.md) ·
[S1-S6实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md) ·
[规划与优化Design](../design/planning-and-optimization.md) ·
[执行Design](../design/execution-and-concurrency.md)

## 1. 结论

S2通过。普通Table/IndexSelection/Field数据源的typed/callback filter、typed/callback order、distinct、
skip/limit/top、完整只读terminal、Selection update/remove、explicit parallel与`_explain()`已经统一进入
S1建立的Canonical -> Bound -> Normalized -> Physical -> Admission -> Frame主路径。

S2没有改变public/generated application API、结果、encounter order、callback、failure、resource或
mutation atomicity合同，没有新增dependency、artifact、SPI、JSON/Workflow或SOMA Engine实现。
Mapped/Primitive与Relation/Group仍分别由S3、S4拥有，不被本记录误称为已迁移。

## 2. Implementation fact

### 2.1 Canonical ownership

- `CanonicalRowOperation`现在是普通Row/Field/Selection operation的唯一不可变语义载体，拥有source、
  closed stage、terminal、callback handle与sequential/parallel request；
- `CanonicalRowStage`只表达typed/callback filter、typed/callback order、distinct Field、Field projection、
  skip与limit；不持有StateRoot、Index、cursor、lease或worker；
- `CanonicalOrder`从generated carrier中只提取Field ordinal与direction；`HostCallbackHandle`只以closed
  kind保存opaque Java callback，不尝试反编译lambda；
- `BoundCanonicalRowOperation`在terminal-start绑定唯一StateRoot、layout、operation与provenance；
- `CanonicalRowPlanner`完成adjacent typed predicate normalization、exact lookup substitution、
  bounded top、resource estimate与parallel typed-prefix partition decision；
- `CanonicalRowExecutionFrame`只在temporary lease成功后创建cursor、membership与parallel source buffer。

### 2.2 Execution 与 reference

- optimized sequential执行scan、Key/Index、typed/callback filter、Field projection、slice、stable order、
  first-distinct与bounded typed top；
- reference interpreter消费同一Bound canonical semantics，但使用canonical scan、独立typed insertion sort与
  reference distinct，不读取optimizer或Index posting order；
- callback comparator为了保持application可观察的调用顺序，optimized/reference共享唯一stable callback
  schedule；这不是共享typed optimizer或physical access decision；
- parallel scheduler只细化Physical Plan的Table-scan typed prefix，使用既有bounded caller-participating
  ForkJoinPool，按range ordinal合并；callback、stateful stage与terminal仍在caller thread执行；
- `_explain()`只投射logical/normalized/physical/resource decision，不创建execution frame、不提交worker。

### 2.3 Query 与 mutation

普通非Relation `QueryOperation`的count/match/find/forEach/materialization/explain/test locator入口均在
terminal边界lower到Canonical operation。普通Selection update/remove在同一operation guard内执行
bind、plan、lease、locator freeze、candidate staging与一次atomic publication；zero-change和failed-state
仍不发布。

旧Row executor/optimizer目前只被尚未迁移的Mapped/Primitive、Relation/Group family与其过渡adapter引用；
这是一条有S3/S4 Owner的迁移边界，不是普通Row/Field production的第二physical truth。

## 3. Correctness、lifecycle 与 resource evidence

- full `GeneratedTableTest`覆盖typed/callback differential、stable callback trace、Key/Index/null lookup、
  callback barrier、top 0/threshold/oversize/tie、parallel等价、Field materialization、Selection
  update/remove generation与failure no-publication；
- one-shot carrier、validation-before-claim、terminal-start binding、same-Group reentrancy、callback failure
  translation与operation provenance保持既有合同；
- materialization、stateful order/distinct/top、parallel root buffer、IN membership及Selection candidate均在
  execution前做checked conservative admission；lease、cursor与frame在返回或失败前释放；
- no-stateful sequential scan保持zero scratch，exact lookup不退化为Table scan；
- Java 8 runtime与processor targeted suites全部通过，generated stress与public ABI没有变化。

## 4. Java 8 与 repository evidence

执行并通过：

```text
mvn -q -pl soma-runtime test
mvn -q -pl soma-processor -am test
git diff --check
```

Processor stress保持112 Tables、448 Fields、224 Indexes及Java 8 generated consumer/source/public/verbose
ABI资格。S2只调整runtime internal classes，没有generated/public surface或third-party dependency delta。

## 5. Fixed-host performance guard

环境沿用正式performance-frontier的Corretto 8、6 GiB SOMA budget、P16、3个fresh JVM；两档
`frontier-source + frontier-stateful`均通过correctness/fingerprint。

| Operation | 正式baseline | S2 median | 结论 |
|---|---:|---:|---|
| 10K Table typed filter | 0.425 ms | 0.450 ms | +0.025 ms固定成本，无复杂度变化 |
| 10K Field sort / top | 0.384 / 0.306 ms | 0.377 / 0.310 ms | 与基线等价 |
| 10K exact Index count | 0.011 ms | 0.018 ms | 微秒区间，无scan回退 |
| 1M Table typed / callback filter | 19.451 / 23.136 ms | 17.226 / 17.343 ms | 正常吞吐改善 |
| 1M Field sum / filter / materialize | 5.637 / 15.568 / 8.043 ms | 5.482 / 15.120 / 8.014 ms | 无退化 |
| 1M Field distinct / sort / top | 4.546 / 8.977 / 8.730 ms | 4.549 / 8.763 / 8.763 ms | 与基线等价 |
| 1M Table top / slice | 15.413 / 2.526 ms | 11.810 / 2.533 ms | top改善、slice等价 |

1M exact Index count为0.290 ms，residual为0.441 ms；exact绝对差处于亚毫秒噪声区间且仍走sidecar，
不构成结构退化。S2未改变Mapped/Primitive、Relation/Group与10M kernel，因此不重复其重型证据。

## 6. Exit 与 claim boundary

S2 Exit：`PASS`。S3可以激活并迁移Mapped/Primitive family；在S3关闭前不得激活S4。

本记录不证明Mapped/Primitive、Relation/Group已经统一，不证明10M或一亿行新性能，不授权Release、
Package publication、签名或正式发布声明。
