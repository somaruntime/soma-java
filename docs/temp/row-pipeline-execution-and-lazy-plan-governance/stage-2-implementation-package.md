# SOMA Access Model / Candidate Scan Stage 2 实施包

类型：Temporary

状态：S2.1–S2.6 immutable candidate 完成；S2.7 正式固化待单独授权

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：Stage 2 的模块变更面、原子切换顺序、实际迁移、验证Gate与停止条件

非事实范围：正式Design、正式性能声明或发布计划

输入：[Stage 1 决策](stage-1-decisions.md)、[Pipeline IR](pipeline-ir.md)、[Stage 1 Evidence](stage-1-evidence.md)

最后审查日期：2026-07-23

## 1. 实施目标

在一次治理生命周期中完成：

- Access Model目标generated API的clean cutover；
- Candidate Scan compact plan与source/terminal specialization；
- Key/Column Traversal命名和one-shot lifecycle；
- Unique point family与scalar Index terminal；
- prebound Column diagnostics；
- correctness、compatibility、allocation、code-size和四场景验证；
- 最终正式Owner原子固化并删除Temporary。

用户已授权并完成 Stage 2 的 production/public API/runtime 实施与 immutable
candidate。S2.1–S2.6 固定在 executable commit `fd82eba`，证据见
[Stage 2 Evidence](stage-2-evidence.md)。正式 Owner 固化与 Temporary 删除仍须按
S2.7 获得单独授权并原子执行。

## 2. 模块变更面

| 模块 | 目标变更 | 明确不变 |
|---|---|---|
| `soma-annotations` | 无 | annotation、Schema语义 |
| `soma-processor` | target naming、collision/admission、Unique point、Scan/Traversal生成、compact executor | normalized schema/hash语义、javac8 boundary |
| `soma-runtime-core` | typed ColumnTraversal、prebound diagnostics、protocol v4、必要lifecycle helper | packed columns、KeySpace、GroupedExactIndex、IndexSnapshot contract |
| `soma-testkit` | target javap/golden、external consumer、differential/lifecycle/failure oracle | reference语义不降级 |
| `soma-examples` | 四场景mechanical migration与hot Index best-one | domain模型、checksum/policy |
| `soma-benchmarks` | Access Pattern matrix、new scalar/unique/plan/traversal lanes | artifact claim boundary |
| scripts/docs | checker、Gate、最终Owner promotion | release/G6工作 |

## 3. 实施切片

切片是执行顺序，不是长期阶段或缩水roadmap。最终cutover前允许working tree短暂不可编译，但任何提交/交付点不得暴露双canonical public API。

### S2.1 Test oracle 与目标surface骨架

- 在testkit建立target `javap` manifest和migration fixture；
- 增加Access Pattern reference evaluator；
- 增加scalar Index、Unique point/bridge、old-handle alias、Traversal consumed、overflow 4/5/16 oracle；
- 将benchmark v2 lane固定为target前baseline，保留artifact checksum；
- 先不修改正式文档。

出口：目标API和语义可以由失败测试精确表达，不靠源码字符串猜测。

### S2.2 Runtime typed support 与identity

- `AbstractColumnPipeline`/typed `*ColumnPipeline`替换为Traversal family；
- ColumnTraversal与ColumnView接收generated prebound operation literal；
- 删除`ColumnViewOperations.Cache`动态绑定路径；
- 新增或调整one-shot traversal lifecycle/error；
- generated/runtime compatibility升级v4；plan protocol、dense algorithm和schema identity不变；
- 不引入generic metadata descriptor。

出口：runtime-core public/protocol target surface完整，Java 8 unit/public golden通过。

### S2.3 Generator Access Model cutover

- 更新generated type-name admission：Scan/Cursor/UpdateCursor/KeyTraversal；
- 删除Rows/Row/MutableRow/rows生成；
- Table生成canonical Packed stage/terminal direct executor；
- `@SomaIndex`生成`scanByX`；
- `@SomaUnique`生成point family + `scanByX` bridge；
- primary/current Index词族和`indexSnapshot`切换；
- Key/Column Traversal生成切换；
- schema JSON/hash保持byte identity。

出口：processor unit、generated source compile、name collision和determinism通过；无old public alias。

### S2.4 Candidate Scan compact executor

- 生成plan owner + generation handle；
- Packed首stage、ExactGroup、ExactUnique typed source plan；
- inline 3 + overflow三数组；
- append publish-last和terminal cleanup；
- streaming、barrier、stable full sort、stable arg-min；
- direct packed count/terminal specialization；
- Index/snapshot/materialization/update/remove各自executor；
- logical stats与callback/failure语义迁移。

出口：全部reference/differential/lifecycle/failure oracle通过；没有per-stage node、per-element object或Stream。

### S2.5 Consumer 与场景原子迁移

- external Maven fixture和`javap` golden只保留target surface；
- FJSP用`scanByMachine(...).requireIndex()`替代single snapshot；
- FJSP Unique lookup用point Index API；
- VRP/Simulation/Game使用Table Packed source、`scanByX`、`indexSnapshot`；
- benchmark imports/lane描述改为Access Model词汇；
- 更新development artifact version为`0.2.0-SNAPSHOT`，不做发布配置。

出口：四场景checksum、schema hash和external consumer通过；源码current范围无旧canonical token。

### S2.6 性能调优与Gate

- component allocation按source/stage/terminal拆分；
- JFR确认plan/source/array/diagnostic String allocation；
- FJSP 100k同dataset/JVM参数A/B；
- generated source/class/javac size；
- retained IndexBuffer/sort/update scratch；
- 若miss Gate，只调整internal micro-layout，不回退Access/API目标。

出口：本文件第6节Gate通过或专题明确blocked；不以删除测试/放宽语义收口。

### S2.7 正式固化与Temporary退役

在实现与Gate全部成功后：

```text
Blueprint target experience
  -> Design unique Owners
  -> Implementation Map / Conformance / Engineering
  -> commit-bound Governance + Performance Report
  -> docs/checker/full Gate
  -> delete this Temporary topic
```

正式文档不经历中间状态；promotion和Temporary删除在最后一个原子治理步骤完成。

## 4. 目标 API migration card

| Current | Target |
|---|---|
| `table.rows().sorted(c).rowIndexes()` | `table.sorted(c).indexSnapshot()` |
| `table.filter(p).rowIndexes()` | `table.filter(p).indexSnapshot()` |
| `table.findByGroup(v)` (`@SomaIndex`) | `table.scanByGroup(v)` |
| `table.findByUnique(v).rowIndexes()` | `table.findIndexByUnique(v)`或`requireIndexByUnique(v)` |
| `...sorted(c).limit(1).rowIndexes().indexAt(0)` | `...sorted(c).requireIndex()` |
| `findRowIndex(key)` | `findIndex(key)` |
| `rowIndexOf(key)` | `requireIndex(key)` |
| `*Rows.Predicate` + `*Row` | `*Scan.Predicate` + `*Cursor` |
| `*Rows.Updater` + `*MutableRow` | `*Scan.Updater` + `*UpdateCursor` |
| `*Keys` | `*KeyTraversal` |
| `LongColumnPipeline` | `LongColumnTraversal` |

不生成compatibility shim。Migration fixture证明用户代码可以机械修改并重新生成。

## 5. Correctness Gate

### 5.1 Access/API

- 每个`AP-01..27`有current/target mapping或明确non-Scan路径；
- Unique `0..1` point semantics覆盖hit/miss/collision/update/delete；
- exact group不scan fallback、不重建、不扩回全表；
- current Index、snapshot、materialization返回shape和validity明确；
- old generated names不出现在current javap/consumer/example。

### 5.2 Sequence/callback/lifecycle

- Filter/Skip/Limit声明顺序；
- stable sort与repeated sort；
- arg-min first-on-equal，comparison `M-1`于`M>=1`的单Sort best-one reference lane；
- previous handle、branch、repeat terminal、Traversal repeat全部typed failure；
- intermediate validation/allocation failure不消费旧handle；
- callback escape/reentrancy/failure attribution和cause保持；
- terminal failure后plan callback/reference清理可由retention test证明。

### 5.3 Mutation/resource

- update/remove candidate freeze、unique conflict、exact relocation、swap-remove；
- Batch/replace/child ownership不回退；
- memory/materialization/scratch preflight在callback/publish前；
- failure不改变rows/columns/locator/index/child/epoch；
- IndexSnapshot caller-responsibility契约不变。

### 5.4 Identity

- 同一schema source的schema JSON/hash byte-identical；
- old generated/runtime与v4 mismatch在create前fail closed；
- plan protocol与输入集合不变；compatibility字段值升级到v4，因此plan hash按正式Design确定性变化；
- deterministic generation、Java 8 classfile、external Maven compile/run。

## 6. Performance Gate

### 6.1 必报维度

- ns/op或throughput、sample/fork/warmup；
- allocated B/op与JFR allocation class/stack；
- Young/Full GC count/time；
- operation scratch current/high-water；
- scanned/matched/changed与checksum；
- generated source bytes/lines、class bytes/count、javac wall time；
- environment、commit、命令、artifact/checksum。

### 6.2 Provisional component envelopes

以下是Stage 2诊断目标，不是永久跨环境SLA。若miss必须解释与审查，不能自动放宽：

| Lane | Current B/op | Target envelope |
|---|---:|---:|
| Packed zero-stage count | 83.7216 | `<= 16` |
| Exact zero-stage count | 112.0736 | `<= 89` |
| Exact one-stage filter count | 400.0736 | `<= 193` |
| Exact three-stage count | 560.0736 | `<= 241` |
| Exact five-stage overflow count | 1000.0736 | `<= 465` |
| Exact filter-sort scalar Index | snapshot baseline 648.0960 | `<= 273`且不创建IndexSnapshot |
| long Column traversal | 564.7280 | `<= 160`，无runtime String concat |

Envelope依赖同一Zulu JDK 8/aarch64 ThreadMXBean方法。最终 handle 裁决后，Scan 只保留
plan reference与generation，Table/source state归入typed plan；Exact zero-stage 实现分配为
`88 B/op`，当前 harness 固定带来约 `0.0736 B/op` 观测开销，因此可执行上限收紧为
`89 B/op`。其余受handle布局影响的lane同步收紧，防止多余引用重新进入handle。
更换JDK/architecture时先建立新baseline，不直接套用绝对值。

### 6.3 Integrated/code-size non-regression

- FJSP checksum、assignments、makespan、tardiness与Schema identity不变；plan hash只允许因v4 compatibility输入确定性变化，并须在全部measurement中一致；
- solve median throughput不劣于同机baseline 10%，allocation/op不得回退；
- total allocation/op不得回退超过2%，并单独报告import/solve/export；
- GC只作诊断，不用单次count硬判，但显著回退必须解释；
- 33-table generated source、Rows/Scan family class bytes和nested class count相对baseline增幅均`<=15%`；
- 不出现per-element allocation、boxing tuple、iterator、Stream或dynamic diagnostic String；
- benchmark多fork或分lane运行，不能用当前单进程噪声nanos证明收益。

## 7. Scope non-regression Gate

- `soma-annotations`和Schema semantics无变化；
- packed SoA、swap-remove、exact incremental maintenance、child ownership不变；
- 不新增range/order/join/top-k/reduction；
- 不新增第三方dependency、reflection/metadata interpreter或generic runtime；
- 不触碰release/G6主体与readiness；
- accepted Design/API目标不因实现困难缩水为temporary alias或future TODO。

## 8. 停止条件

遇到以下任一项，停止Stage 2并返回Owner裁决：

- schema hash或normalized schema必须因rename改变；
- 无法同时保持callback/failure/stats与目标优化；
- compact design必须依赖Table-global/ThreadLocal plan或generic Object executor；
- Unique point API与generated name/erasure出现不可解冲突；
- code-size超过15%且无法通过typed helper收敛；
- Packed direct terminal allocation/throughput显著回退；
- 需要temporary public alias、第三方dependency或正式Design范围外能力；
- full Gate出现与本专题相关的unresolved failure。

## 9. 预期文件入口

实施前必须再次live定位，不把本表当固定文件清单：

| 责任 | 当前主要入口 |
|---|---|
| generated orchestration/names | `DenseTableSourceGenerator.java`、`SomaProcessor.java` |
| exact codegen | `DenseExactIndexSourceEmitter.java` |
| runtime compatibility | `RuntimeCompatibility.java` |
| Candidate Scan runtime | `GeneratedScanPlan.java`、`GeneratedScanEvaluation.java` |
| Column traversal/view | `AbstractColumnTraversal.java`、typed Traversal/View、`GeneratedColumnAccess.java` |
| snapshot/stats/failure | `IndexSnapshot.java`、`DenseTableState.java`、`RuntimeFailures.java` |
| external oracle | `external-maven-dense`、`external-maven-access-phase3`、keyed/child/breadth fixtures |
| component evidence | `PostCutoverComponentBenchmark.java` + validator/script |
| integrated evidence | FJSP 100k runner、four-scenario checker |

## 10. 候选出口

Stage 2 production/public 实施、P1/P2、命名、handle、性能与 Gate 已完成，候选为
`fd82eba`。当前只剩 S2.7 正式 Owner 原子固化；该动作必须单独授权，无需重新讨论
已经关闭的命名、Unique cardinality、inline capacity或Access family，除非出现新的
反证或第8节停止条件。
