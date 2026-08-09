# SOMA Java V1 Production Implementation Plan

类型：Engineering Plan

状态：Active Baseline / I0-I8 COMPLETED / G1-G10 PASS / RELEASE NOT AUTHORIZED

正式事实源：是（实施顺序、slice exit与stop rule）

Owner：SOMA Java V1 production implementation slices、依赖顺序与Definition of Done

最后审查日期：2026-08-09

## 1. 目标与授权边界

本计划把正式Blueprint/Design转化为Java 8 compiler/runtime product。Product Owner 已于
2026-08-03 单独授予完整 V1 implementation authorization。I0-I8已经完成implementation与
qualification；I0-I7分别通过
[I0正式资格](../conformance/i0-build-spine-qualification.md)与
[I1正式资格](../conformance/i1-primitive-keyed-table-qualification.md)、
[I2正式资格](../conformance/i2-schema-type-storage-breadth-qualification.md)、
[I3正式资格](../conformance/i3-query-ir-reference-qualification.md)、
[I4正式资格](../conformance/i4-selection-mutation-resource-qualification.md)、
[I5正式资格](../conformance/i5-group-relation-qualification.md)与
[I6正式资格](../conformance/i6-parallel-execution-qualification.md)与
[I7正式资格](../conformance/i7-compression-metadata-qualification.md)，I8通过
[产品资格与G9/G10 evidence](../conformance/i8-product-qualification.md)。当前没有active
implementation slice；I0-I8与G1-G10 implementation qualification已经闭合。

当前授权同时覆盖repository-local CI/non-publishing release qualification workflow、local/internal
benchmark与profile、local Maven package qualification，以及JUnit Jupiter 5.x test-only stack。
精确边界由[Conformance authorization contract](../conformance/README.md#6-implementation-authorization-contract)
拥有；远端artifact发布、签名和正式release声明仍未授权。

“纵向slice”只表示实施顺序，不缩减V1 scope。禁止先实现flat-int/boxed/reflection MVP，再把
chunk、IR、specialization、failure或relation当成未来补丁。

## 2. Global Definition of Done

V1 production implementation只有同时满足以下条件才完成：

1. two artifact + independent Java 8 consumer clean build/run；
2. schema/full-regeneration/diagnostic/generated exact surface通过；
3. long-domain chunked storage、Key/Index、order/null/materialization通过；
4. typed IR、reference interpreter与optimized sequential/parallel differential通过；
5. point/Selection mutation every fault point zero publication；
6. Group/Equality/Cross Join的kind/null/duplicate/order/cardinality/resource通过；
7. bounded parallel、AUTO/OFF compression、global memory admission通过；
8. three million-row reference scenarios达到profile后批准的qualification target；
9. security/dependency/SBOM/package/license/provenance/release workflow通过；
10. Conformance无undisclosed blocker，documentation/Examples/claim不超过evidence。

## 3. Slice map

每个slice同时使用[核心抽象与叙事Design](../design/core-abstractions-and-narratives.md)的
A/N/INV fill map作为attention/proof routing；精确合同仍以对应分责Design为准。A/N/INV不是
production class名，也不是每个commit的强制清单。

| Slice | Name | Status | Primary Gates |
|---|---|---|---|
| I0 | Build spine、artifact与full-regeneration carrier | COMPLETED | G1、G2、G10 |
| I1 | Primitive keyed Table vertical slice | COMPLETED | G1-G5 |
| I2 | Schema/type/chunk/Key/Index breadth | COMPLETED | G2-G4 |
| I3 | Direct query、Predicate IR与reference interpreter | COMPLETED | G4 |
| I4 | Selection mutation、failure与resource admission | COMPLETED | G5 |
| I5 | GroupBy与binary Equality/Cross Join | COMPLETED | G6 |
| I6 | Bounded ForkJoin parallel execution | COMPLETED | G7 |
| I7 | Compression、metadata/explain与surface closure | COMPLETED | G8 |
| I8 | Reference scenarios、performance、security、package/release qualification | COMPLETED | G9、G10 |

任何slice只有其exit evidence进入Conformance后才能进入下一slice。允许在同一commit交付相邻
mechanism，但Gate不能因实现方便合并消失。

## 4. I0 — Build spine

### Capability

建立standard Maven reactor、`soma-runtime`、`soma-processor`与independent consumer fixture，
使后续代码处在真实Java 8 classpath/processorpath/version/package boundary。

### Surface admission

- root Maven reactor与wrapper policy；
- exactly two production modules；
- Java 8 compiler/toolchain enforcement；
- unit/golden/compile-negative/consumer/integration test taxonomy；
- JUnit Jupiter 5.x test-only stack：I0固定官方支持Java 8的版本，验证license、known
  vulnerability、dependency tree与无production artifact leakage；
- full-source-set handshake、generated manifest与stale cleanup；
- runtime/processor exact version linkage；
- LICENSE/NOTICE/source/javadoc/SBOM/provenance baseline。

### Exit

- clean reactor与independent consumer在qualified Java 8编译运行；
- classpath/processorpath分离；
- empty/invalid schema、version mismatch、partial source set negatives；
- shared configuration owner的class-load/internal observation不freeze，configure-first与首次
  runtime access ordering carrier成立；公开`Soma`/default Group/Table ordering在I1真实纵向
  闭环出现后验证，公开metadata observation在I7 exact carrier admission后验证；
- dependency tree only admitted；
- no third artifact、legacy source或production placeholder package。

### I0 M1 slice-boundary clarification（2026-08-03）

I0负责build host、processor、generation manifest/version/config carrier，但不生成无真实后端的
公开`Soma`/`SomaGroup`/Table facade，也不提前冻结I7 metadata getter topology。这样保持最终
Blueprint/Design语义不变，并关闭两个implementation归属冲突：

- 第一套完整公开composition root与primitive Table vertical slice在I1一次出现；I0只生成不承诺
  用户capability的internal composition/provenance carrier，并由independent schema consumer验证；
- I0通过runtime内部同源证据验证configuration CAS与freeze ordering；公开`Soma.class`、
  `defaultGroup()`/Table-first与`_metadata()` observation证据分别在I1/I7补齐；
- I0的failed-state合同是“所有source先验证并内存render、success manifest最后写；失败输出不可
  形成valid composition，下一次qualified lifecycle先完整清理”，不声称JSR 269 `Filer`能让
  任意I/O失败后的物理目录绝对为空；
- `-Asoma.fullSourceSet=true`是build host assertion，不是processor能够独立证明的filesystem
  oracle；伪造该option的raw partial javac明确不属于support path。

该记录属于核心抽象变更协议的M1定位修正：不改变public/generated signature、V1 capability、
result/order/null/failure或资源语义；若实现反例要求改变这些SEMANTIC_BASELINE，立即升级M2并
等待Product Owner。

## 5. I1 — Primitive keyed Table vertical slice

Journey：

```text
one schema
    -> generated Soma/Group/Table/object/View/Editor/Field
        -> chunked PLAIN StateRoot
            -> reserve/add/find/get
                -> typed filter/count
                    -> point update
                        -> structured failure
```

第一条storage必须已经支持paged Chunk directory、long domain、Group guard、one-shot pipeline、
scope token与atomic publish；不能用single `int[]`/Object[] universal engine。

Exit：generated/javap golden、consumer positive/negative、tiny-Chunk boundary、no reflection/boxing、
add/find/query/update success/failure、point update missing/no-callback、candidate-root与prevalidated
final-commit两条publication mechanism的root/version invariant。

Implementation status：`COMPLETED`；implementation commit
`3ef250b7ca50ebc4e598f0e09a02d5e9d44876bf`；完整evidence与claim boundary见
[I1 Qualification](../conformance/i1-primitive-keyed-table-qualification.md)。

## 6. I2 — Schema/type/storage breadth

覆盖：

- 全部 primitive、String、Enum、Value flattening、ordinary/parameterized Object；
- Enum/Object/array/parameterized type从generated parent namespace的Java 8 source accessibility；
- keyless/keyed、multiple Index、nullable reference、float/double canonical semantics；
- multiple Group/default Group、long reserve/growth/remove compaction/GC；
- default/explicit Group中same-type accessor首次并发的single identity与safe publication；
- generated constructor/naming/collision/full regeneration breadth；
- 1:M/N:M relation Table与双向Index journey。

Exit包括全部 annotation/type diagnostic、cross-Chunk tests、Key/Index collision/null/order/rebuild、
reference clearing、ordinary Object不具Equality/Key/Index/GroupBy/Join capability的compile-negative、
structural bytes/allocation baseline与100+Table generated-surface profile。

Implementation status：`COMPLETED`；implementation commit
`3fa61a0d29d48cf96e66bb18a79739119c9e3d76`；完整evidence与claim boundary见
[I2 Qualification](../conformance/i2-schema-type-storage-breadth-qualification.md)。

## 7. I3 — Query IR 与 reference interpreter

覆盖：

- direct Table/Index/Field source；
- Stream/Selection/ReadStream type-state；
- Field/Relation expression与callback barrier；
- filter/map/mapTo*/distinct/sort/top/skip/limit；
- scalar/match/materialization/numeric；
- typed Logical IR、fixed optimization phases；
- sequential reference interpreter与optimized sequential differential；
- `_explain()`logical-only baseline。

Exit：operation-property compile matrix、expression/callback overload、wrong-owner negative、View
O(1)、mapped `toArray(Class)`、integer/floating contract、normalization/barrier/Index substitution
golden与reference differential。还必须覆盖between逆区间、empty/duplicate/null `in`、nullable
order placement、lexicographic tie-break、empty match、mapped reference distinct/null与detached
modifiable List合同，以及behavioral callback non-interference/canonical short-circuit/Comparator
barrier；`in` defensive snapshot的checked construction/application-retained ownership与terminal
scratch admission边界。

Implementation status：`COMPLETED`；implementation commit `62ad28c`；完整evidence与claim boundary见
[I3 Qualification](../conformance/i3-query-ir-reference-qualification.md)。

## 8. I4 — Selection mutation、failure与resource

覆盖point add/update/remove/reserve与Selection update/remove：

- frozen selection/compaction；
- Editor staging；
- payload/Key/all Index/accounting atomicity；
- small/large selection共享candidate-root path；只有profile证明必要时才重新准入small journal；
- failure phase precedence；
- retained/temporary global admission；
- explicit Group `PhantomReference/ReferenceQueue` retained accounting release；
- automatic/explicit effective memory budget；
- metadata last-published observation。

Exit必须在payload、allocation、Key、each Index、callback、validation、publish前所有可恢复point
注入失败，并证明old root/version完整、worker/lease/guard释放。Callback failure evidence必须区分
current runtime provenance（保留scope/missing/reentry code）与application replay/foreign provenance
（`CALLBACK_FAILED`）。Opaque selection mutation必须证明bound-upper resource admission发生在任何
callback前；point update必须证明existing-row worst-case admission发生在Editor callback前而missing
不admit/callback；GC accounting必须证明无strong-retention cycle与double release。

Repeated add在本slice取得million-row ingestion profile。若其成为dominant bottleneck且internal
优化无法达到合理manual column baseline的approved threshold，停止并建立Loader Temporary；
不得引入 hidden Batch。

## 9. I5 — Group 与 relation

### Group

- all eligible key type/null group；
- count与numeric aggregate；
- typed primitive/reference result；
- key-first order、checked cardinality与resource。

### Join

- same-Group typed `on/and`；
- recursively keyable Field marker与float/double/ordinary Object compile-time exclusion；
- Inner/Left/Full/Semi/Anti/Cross；
- null-never-match、duplicate Cartesian、missing truth；
- Pair/ReadStream/materialization narrowing；
- Predicate IR pushdown/residual/Index substitution；
- lookup/hash baseline与physical-order independence。

Exit：reference-vs-optimized algorithm differential、全部 kind/order/null/cardinality/failure、extreme
checked budget、Join type-state negative（kind after intermediate、Outer select、Pair materialization、
foreign/ordinary-Object Field）、`_explain()` plan与调度journey paper program。

## 10. I6 — Parallel execution

在 optimized sequential correctness 上加入 application-owned `ForkJoinPool`：

- caller + P-1 bounded drainers；
- ordinal range queue/start gate/caller progress；
- deterministic merge/floating tree/failure frontier；
- synchronous cancellation/interrupt/quiescence；
- no hidden fallback/nested parallel。

Opaque callback-bearing short-circuit与Comparator stage按Design在caller thread canonical执行；
typed-only stage才可speculate。

Exit覆盖P=1/2/4/16、small/large、custom/common/shutdown/rejection/saturated/nested/interrupt、
active participant/task bound，以及sequential/parallel result/order/numeric/mutation/non-resource
failure exact equivalence；parallel-specific resource/interrupt failure单独验证fail-closed与no
alternate result。

## 11. I7 — Compression 与 diagnostic closure

实现AUTO/OFF与Chunk representation：

- PLAIN、eligible integer/boolean/Enum encoding、String dictionary；
- sparse overlay与affected-Chunk rebuild；
- generated/specialized kernels；
- Index/compression atomicity；
- forced codec correctness、AUTO cost/peak；
- final Soma/Group/Table/Field `_metadata()` carrier admission与API diff；
- full `_explain()` pushdown/residual/Index/Join/codec/peak；
- complete generated signature/IDE navigation/full regeneration rerun。

Exit时除scenario/performance/package/release外，G1至G8必须PASS。

完成证据：[I7 Compression 与 Metadata Qualification](../conformance/i7-compression-metadata-qualification.md)。

## 12. I8 — Product qualification

### Three scenarios

1. 调度：Job/Machine/Option、Key/双向Index、Join、candidate与compensated publish；
2. 仿真：event order、detached decision、state update与event remove；
3. 实时派工：pending Index、Predicate IR/Join、low-allocation decision loop。

### Profile sequence

```text
correctness
    -> deterministic workload
        -> profile and allocation/memory attribution
            -> narrow owner optimization
                -> reference/parallel equivalence rerun
                    -> scale/saturation
                        -> package/security/release qualification
```

Datasets至少覆盖Narrow、Medium、Reference-mixed million-row；16 core/32 GB是qualification
resource envelope，不是runtime maximum。比较ArrayList+HashMap与合理manual column baseline，
但不以牺牲contract获得benchmark。

Exit：approved G9 performance report与G10 qualification、public API Examples、package consumer smoke、source/javadoc/license/
NOTICE/SBOM/checksum/provenance、CI/release workflow与Owner sign-off。GitHub Release/Package需
单独发布授权。

Implementation status：`COMPLETED`；implementation commit
`cd0d476ad4f3d1e3db978534c7942d49604876a9`；Product Owner已于2026-08-09批准G9 threshold并完成
I8 sign-off；`develop@a6e8400`的远端CI与non-publishing release qualification均通过。完整证据与claim boundary见
[I8 Product Qualification](../conformance/i8-product-qualification.md)。

## 13. Gate traceability

| Blueprint | Primary Design | Slices | Gate |
|---|---|---|---|
| BP-1/BP-2/BP-14 | Schema、Signature、Architecture | I0-I3、I7 | G1、G2、G10 |
| BP-3/BP-4 | Storage、Execution | I1-I2、I4 | G3、G5 |
| BP-5/BP-8 | Logical、Planning | I1、I3、I5 | G4、G6 |
| BP-6/BP-11 | Execution、Failure | I1、I4、I6 | G5、G7 |
| BP-7 | Logical、Planning、Storage | I5 | G6 |
| BP-9/BP-10/BP-12 | Execution、Architecture | I3-I8 | G4、G5、G7-G9 |
| BP-13 | Blueprint、Storage、Execution | I2、I4-I8 | G3、G5-G7、G9 |
| BP-15 | All + Conformance | I0-I8 | G1-G10 |

## 14. Stop rules

立即停止当前slice并建立bounded Temporary，如果：

- public/generated signature需要偏离Design；
- correctness依赖reflection/boxing/unbounded allocation/hidden blocking；
- storage退化到single array/int domain；
- reference与optimized/parallel不能得到相同logical result或non-resource failure semantics；
- mutation failure无法证明zero publication；
- full regeneration/stale cleanup不能重放；
- Join rewrite无法证明Outer/missing/order等价；
- tasks/scratch/memory peak无法bound；
- Loader/off-heap/mmap/third artifact/dependency没有surface admission；
- repeated validation不产生新evidence；
- new public type/operation找不到Blueprint/Owner/lifecycle/failure/evidence。

Stop不等于缩小产品scope。必须记录反例、受影响Owner、候选修正、恢复条件与Product Owner
裁决。

## 15. Change protocol

- Internal mechanism可凭evidence替换，但不改变contract；
- public capability、order/null/failure/lifecycle/resource visibility变化回到Product Owner；
- 每个slice status变化链接Conformance evidence与commit；
- pre-release不保留失败草案compatibility alias；
- 当前Product Owner授权覆盖I0-I8 implementation、每个slice闭合后的commit/`develop` push，
  以及Conformance第6节限定的CI、internal benchmark/profile、local package qualification和
  JUnit Jupiter 5.x test-only stack；remote artifact publication、signing与正式release声明仍需
  独立授权，其他权限不得由本授权推断。
