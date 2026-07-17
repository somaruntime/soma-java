# 正式迁移阶段与验证计划

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 目标

本文把设计专题转换为后续可执行、可停止、可验证的迁移路线。它不把Phase完成冒充完整V1/release完成，也不在正式Owner批准前授权实现。

## 2. 迁移总原则

```text
temporary design review
  -> user resolves public/open decisions
  -> unique Owner contracts migrate first
  -> compatibility identity and capability ledger align
  -> runtime + processor/generated vertical cutover
  -> consumers/examples migrate
  -> correctness/allocation/benchmark evidence
  -> obsolete code/docs/gates cleanup
```

永久约束：

- 实现服务于新批准设计，不以当前代码便利反向修改目标；
- 不建立old/new两套长期canonical runtime；
- 不生成temporary public facade；
- 不在read path保留hidden rebuild/fallback scan；
- 不把smoke或单机结果写成production/release claim；
- G6发布工作继续排除，但正式V1 release仍不能因本专题跳过G6。

## 3. 正式Owner迁移矩阵

设计获批后，必须按事实Owner修改；下表不是联合Owner。

| 事实 | 唯一Owner文档 | 需要迁移的内容 |
|---|---|---|
| table/key/access/order annotation semantics | [annotation schema契约](../../../soma-annotations/docs/annotation-schema-contract.md) | 删除order类型；保留key/unique/index exact；无range；strict leaf边界 |
| normalized model/hash/diagnostics | [schema processing契约](../../../soma-processor/docs/schema-processing-contract.md) | order退出；new diagnostics；hash/golden migration |
| generated artifacts/static binding | [code generation契约](../../../soma-processor/docs/code-generation-contract.md) | 删除order source；生成exact locator/index binding；IndexBuffer/pipeline shape |
| public source/terminal/order semantics | [Generated Table API契约](../../generated-table-api-contract.md) | source sequence、unordered first/limit/fetch、result/index export选择 |
| detached List/Map order | [Materialization契约](../../materialization-contract.md) | dense physical snapshot、keyed Map unordered、explicit boundary sort |
| cross-component invariants/failure atomicity | [Runtime正确性模型](../../runtime-correctness-model.md) | exact structures always-current、final-state unique、tail-fill remove |
| cross-moduleperformance model | [Runtime性能模型](../../runtime-performance-model.md) | 删除Sparse/order lanes；新增exact/group/IndexBuffer/swap-remove cost |
| storage/access structures | [TableStore契约](../../../soma-runtime-core/docs/table-store-contract.md) | Primary/Unique/Grouped结构；删除Sparse/RowPermutation/dirty lifecycle |
| mutation/epoch/child lifecycle | [Runtime lifecycle契约](../../../soma-runtime-core/docs/runtime-lifecycle-contract.md) | buffer scope、row relocate、child token、epoch/pin/release |
| plan/hash/strategy/budget | [RuntimePlan契约](../../../soma-runtime-core/docs/runtime-plan-contract.md) | 删除sparse/sidecar dimensions；new access identity/protocol |
| stats/result/error | [Runtime errors契约](../../../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) | oldsidecar metrics退出；exact metrics与index snapshot error选择 |
| kernel allocation/shape | [Runtime性能实现契约](../../../soma-runtime-core/docs/runtime-performance-implementation-contract.md) | no rebuild-on-read、primitive group links、IndexBuffer、tail-fill |
| public compatibility/migration | [Public API兼容性契约](../../public-api-compatibility-contract.md) | breaking surface、protocol pairing、manifest/migration note |
| V1 scope/capability phases | [实现策略](../../implementation-strategy.md) | ledger中order/sparse breadth与Phase/Gate出口重写 |
| required evidence | [验证门禁](../../validation-gates.md) | G1-G5 lane从oldsidecar/sparse迁到newexact/swap/GC evidence |
| canonical terms | [领域术语表](../../domain-glossary.md) | Index/IndexBuffer/PrimaryLocator/GroupedExactIndex与no-order边界 |
| architecture composition | [项目架构](../../architecture-design.md) | KeySpace/order sidecar组合改为exact locator/index |
| permanent principles | [设计宪法](../../soma-table-design-constitution.md) | V1 breadth删除Sparse/order，明确packed/no physical order/exact access |

Formal docs修改后才能更新examples、guides、reports摘要。Report不反向拥有设计。

## 4. Capability slice前置记录

后续每个implementation slice开始前必须列出：

### 4.1 涉及Capability

```text
V1-ANNOTATION-SCHEMA
V1-PROCESSING-MODEL
V1-SCHEMA-HASH
V1-PUBLIC-COMPATIBILITY
V1-GENERATED-API
V1-DENSE-STORAGE
V1-ROW-PIPELINE
V1-KEYED-IDENTITY
V1-ACCESS-STRUCTURES
V1-MUTATION
V1-CHILD-OWNERSHIP
V1-MATERIALIZATION
V1-RUNTIME-LIFECYCLE
V1-RUNTIME-ERRORS
V1-RUNTIME-PLAN
V1-PERFORMANCE-SHAPE
V1-EVIDENCE-TOOLING
V1-CONSUMER-PACKAGE
V1-SCENARIO-BENCHMARK
```

### 4.2 故意不实现但必须non-regression的breadth

- parent-owned keyed/dense child完整语义；
- `@SomaValue` lowering/equality/hash；
- optional/default/presence；
- Column Pipeline/ColumnView；
- recursive materialization与budget；
- structured errors与single-owner lifecycle；
- Java 8/full javac integration；
- schema/runtime/plan compatibility；
- consumer package与scenario evidence。

### 4.3 slice出口

每一slice必须是最终新架构的有效子集。不能留下需要下一slice迁移publicconsumer、替换canonicalstorage或重写主执行路径的临时实现。

## 5. Phase A：临时设计独立审查

输入：本专题全部文档。

必须关闭：

- `O-01` public index export；
- `O-02` operation result；
- `O-03` exact stats；
- `O-04` runtime plan兼容窗口；
- `O-05` allocation-free first是否进入本轮；
- `O-06` selector type breadth。

出口：

- 用户明确接受/修改全部已确认、推导和建议项；
- 不存在“实现时再看”的public/schema/runtime语义；
- 确认breaking cutover范围；
- 本Phase仍不改正式Owner或代码。

Stop：任一待决选择会改变public/generated signature或schema breadth时，不进入Phase B。

## 6. Phase B：正式设计与Capability迁移

动作：

1.按第3节逐项修改唯一Owner；
2.先固化annotation/API/materialization/correctness语义；
3.再固化processor/runtime implementation义务；
4.更新architecture/glossary/performance model；
5.修改implementation strategy capability ledger与Phase出口；
6.修改G0-G5 required evidence；
7.记录public compatibility/protocol migration；
8.运行文档门禁与全局语义复审。

出口：

- 正式文档不再要求Sparse Set、maintained order或dirty rebuild；
- 没有两个Owner重复定义Index/order/result semantics；
- Capability未被无目标地dropped/deferred，而是被批准的新V1 scope替换；
- Gate仍覆盖同等或更强的function/correctness/performance evidence；
- 临时专题仍保留用于实施trace，不作为facts。

Stop：正式Owner之间仍冲突，或ledger/Gate仍要求old model时，不进入编码。

## 7. Phase C：Runtime primitive foundation

范围：runtime-core + runtime testkit，不先暴露临时application API。

实现：

- hash-only PrimaryLocator family；
- UniqueLocator raw substrate；
- GroupedExactIndex bucket/group/row-link substrate；
- link/unlink/relocate/clear/release；
- growth/rehash/bulk build staging；
- IndexBuffer/operation buffer lifecycle；
- tail-fill single/multi remove kernel；
- resource accounting/invariants；
- selected stats internal counters。

允许：旧generated code在隔离fixture中继续使用old protocol，直到Phase D atomic cutover；但old/new protocol不能共同作为release/currentcanonical path。

禁止：

- 用Java `Map`/`List<Integer>`快速占位；
- hash-only equality；
- dirty/fallback full scan；
- stable compaction；
- test-only public hook代替正式protocol。

出口evidence：

- primitive unit/property tests；
- collision/full equality；
- group link invariant；
- random append/update/remove/rehash trace；
- resource failure no partial publication；
- allocation shape smoke。

## 8. Phase D：Processor、Generated API 与protocol atomic cutover

实现：

- 删除order annotation/model/diagnostics/emission；
- 删除SparseInt/RowPermutation generated-runtime protocol；
- normalized model/hash migration；
- generated Primary/Unique/Grouped typed hash/equality binding；
- exact `findByXxx` source改为new AccessPath；
- shared one-shot pipeline plan + terminal-specific IndexBuffer；
- stable arg-min/general sort；
- update final-state delta与tail-fill remove binding；
- final RuntimePlan/Stats/Result/Index export选择；
- compatibility identity、metadata与manifest同步提升。

Atomic出口：

- new generated code只依赖new runtime protocol；
- old generated/new runtime和new generated/old runtime都fail fast；
- 没有ignored annotation或hidden compatibility mode；
- generated public API与Owner/golden一致；
- full Java 8 compile/golden/package fixture通过。

## 9. Phase E：Consumers、Examples 与蓝图迁移

### 9.1 当前已知order surface

当前source inventory至少包括：

- FJSP：`JobDefinition`、`Machine`；
- VRP：`Vehicle`、`Route`、`Customer`、`InsertionCandidateRow`、`RouteVisitRow`、`UnassignedCustomerRow`；
- Simulation：`Tank`、`StateVectorRow`、`PendingEventRow`、`TraceSampleRow`；
- Game：`MapTileRow`、`GameUnit`、`MoveCandidateRow`、`PendingDamageRow`；
- compiler/external consumer fixtures、public API fixture、guides、scenario docs和benchmark contracts。

### 9.2 Scenario迁移规则

- FJSP `MachineCandidate.findByMachine(...).filter(...).sorted(...).firstOrThrow()`保留为canonical exact-group示例；
- machine next-available长期priority选择优先评估application min-heap；
- VRP/Game phase-local candidate workspace使用explicit dynamic sort；
- Simulation pending event queue迁到external heap，不把event sequence塞回physical order；
- ordered child/export使用position/time字段 + explicit boundary sort；
- 所有未排序`first/limit/fetchAll`调用逐项审查是否只是任意current member，还是遗漏business order。

### 9.3 Temporary blueprint同步

本目录四份长期研究蓝图中的old order/dirty sidecar描述在正式迁移后必须同步：

- 已失效方案标记为historical/rejected；
- 保留scenario role/hot-loop/evidence研究；
- 不把本专题全文复制进去；
- 新正式事实使用Owner链接+一句局部后果。

出口：

- all examples compile/run；
- scenario output需要determinism处都有total comparator或stable external structure；
- public/package fixtures无old annotation/protocol；
- docs/guides不再建议maintained order或Sparse Set。

## 10. Phase F：Correctness、allocation 与performance evidence

### 10.1 Correctness matrix

| Lane | Required evidence |
|---|---|
| primary locator | normal/missing/duplicate/collision/rehash/remove relocate |
| unique locator | normal/missing/final-state swap/conflict/update/remove |
| grouped index | 0/1/many group、head/member/last remove、group create/delete、rehash |
| packed rows | keyed/dense single/multi remove、random tail-fill、reference cleanup |
| pipeline | source/filter/skip/limit/multi-sort/first/fetch/update/remove sequence oracle |
| child | parent swap-move后facade identity、cascade/pin/release |
| failure | callback/resource/pin/duplicate no partial state |
| compatibility | old/new protocol mismatch、schema/plan hash golden |

### 10.2 Allocation/GC matrix

至少记录：

- allocation/op与allocated bytes/op；
- pipeline construction与terminal execution分离；
- exact lookup/group scansteady-state allocation；
- IndexBufferfirst growth与warm retained path；
- dynamic sort general vs arg-min；
- update delta与remove tail-fill；
- rehash/fresh bulk buildtransient allocation；
- materialized chosen row单独计；
- Young/Full GC count、pause、allocation rate；
- retained current/high-water scratch/index bytes。

### 10.3 Benchmark scales

Component lanes：

- N=small/medium/large packed scan；
- exact group K=0/1/small/10%/high selectivity；
- distinct group cardinality low/high；
- mutation/read ratio read-mostly/balanced/mutation-heavy；
- collision/adversarial hash；
- remove first/middle/last/random 1%、10%、50%、all；
- replaceAll/reserve/growth/rehash；
- summary vs diagnostic stats。

FJSP canonical lane：

```text
table N = 100,000
machine group K ~= 10,000
ready matched M ~= 1,000

findByMachine
  -> filter
  -> sorted(total comparator)
  -> firstOrThrow
```

分开记录：group locate、K traversal、filter、comparator count、arg-min、chosen materialization、commit/remove、allocation和GC。不能只给solve总时间。

### 10.4 Baselines

- current old selector full rebuild path；
- handwritten primitive hash/group arrays；
- full packed scan + filter；
- Java collection diagnostic baseline（只作成本对照，不作为canonical实现）；
- stable compaction old path vs tail-fill new path；
- full sort vs stable arg-min。

所有baseline业务语义必须相同；unordered与ordered结果不能直接比较总耗时。

## 11. Phase G：Cleanup 与全局Gate

删除/更新：

- old annotation classes/imports；
- `SparseIntKeySpace`与sparse plan/test lane；
- `RowPermutationSidecar`与dirty/rebuild code/stats；
- old generated-runtime/public API manifest entries；
- obsolete diagnostics/golden/reports assertion；
- old scenario/order benchmark lane；
- temporary compatibility scaffolding。

Repository搜索必须证明production/current docs无以下old canonical token，历史reports可保留但索引标明已被取代：

```text
SomaOrder
SomaOrders
SomaSort
sparse-int-v1
SparseIntKeySpace
RowPermutationSidecar
dirty-lazy-rebuild-v1
primitive-sorted-permutation-v1
sidecarDirtyCount
sidecarRebuildCount
```

最终运行与变更surface相称的G0-G5 checks和repo-wide `./scripts/check.sh`。G6发布prerequisite不在本专题执行，也不得因G0-G5通过而声明release readiness。

## 12. Gate映射建议

| Gate | 本专题新required evidence |
|---|---|
| G0 | formal Owner/ledger/gate无old model冲突，临时专题不被引用为fact |
| G1 | no-order annotation schema、key/unique/index exact、invalid old source |
| G2 | generated exact binding、pipeline/IndexBuffer shape、no old protocol |
| G3 | packed swap-remove、locators/index always-current、allocation/resource/invariants |
| G4 | migrated external Maven consumers、protocol mismatch、plan/hash/API manifest |
| G5 | FJSP/VRP/Simulation/Game migrated scenarios与structured benchmark lanes |
| G6 | 本专题不执行；正式release仍按原Owner requirement |

## 13. 禁止捷径

任何Phase都禁止：

- 把`@SomaKey`降级成普通unique source；
- 让`@SomaOrder`继续编译但失效；
- 以full scan fallback伪装exact index正确；
- index dirty后read-time rebuild；
- `Map<Key,List<Integer>>`、`Map<Hash,int[]>`作为canonical hot structure；
- hash-only identity/equality；
- per-row Cursor/Iterator/boxed Index/key tuple；
- 每个pipeline stage生成candidate array；
- stable compaction保序但文档宣称swap-remove；
- external heap保存packed Index；
- old plan/stats字段静默重解释；
- temporary public API、reflection/metadata interpreter或test bypass；
- 只改FJSP而遗漏VRP/Simulation/Game/fixtures/docs；
- 用smoke/单次JFR宣称production优势；
- 为通过现有tests反向恢复old正式目标。

## 14. Stop conditions

出现以下任一情况必须停止slice并回到设计：

- `O-01`至`O-06`仍未关闭但实现需要选择；
- exact structure需要改变selector equality/type breadth；
- final-state unique swap语义无法在resource boundary闭合；
- child facade当前实现实际依赖physical Index且无法contract-preserving修复；
- public result/stats迁移需要未批准的coexistence；
- 达到新V1仍需要第二次public/protocol migration；
- benchmark证明group link memory/maintenance不适合目标Access Pattern，需要改变核心structure；
- correctness只能靠fallback full rebuild/scan维持；
- 工作树中的无关用户变更与本slice不可安全分离。

## 15. Rollback边界

- Phase A前：仅删除/修改临时专题，不影响正式事实；
- Phase B后未编码：回退完整正式设计slice，不能让Owner半迁移；
- Phase C internal prototype：可整体丢弃，不进入public manifest；
- Phase D cutover后：rollback必须恢复annotation+processor+runtime+generated protocol+fixtures完整配对版本；
- 不能只恢复old runtime而保留new generated code，或反之；
- 已生成的consumer artifacts必须重新生成，不做runtime automatic migration。

## 16. 每Phase closeout模板

```text
Phase:
Commit/artifact:
Affected Capability IDs:
Owner changes:
Public/schema/protocol changes:
Implemented slice:
Intentionally unimplemented V1 breadth:
Evidence commands/artifacts:
Correctness result:
Allocation/GC result:
Known limitations:
V1 scope non-regression:
Next step is additive/internal refinement or migration/rewrite:
Stop/blockers:
```

若最后一项是migration/rewrite，Phase不能closeout为通过。

## 17. 本设计专题完成出口

本轮设计工作完成只表示：

- intent/goal/scope/phase明确；
- semantic/runtime/mutation/plan/stats/compatibility设计可审查；
- 未决项没有被猜测；
- 正式Owner与evidence迁移路径完整；
- 文档门禁通过。

它不表示：

- 用户已接受全部建议；
- 正式V1 scope已改变；
- 实现已开始或完成；
- 性能已经提升；
- G0-G6或release readiness成立。
