# SOMA Java Conformance

类型：Conformance Entry

状态：post-implementation全局完整性审查`PASS`；I0-I8 `COMPLETED`；G1-G10 `PASS`；
Canonical IR/Execution M1 `FORMALLY_PROMOTED / S1-S6_COMPLETED / NO_ACTIVE_IMPLEMENTATION_SLICE`；
Vectorized Pipeline扩展`PASS / FORMALLY_PROMOTED / VP1-VP3_COMPLETED / NO_ACTIVE_IMPLEMENTATION_SLICE`；
Physical Execution Engine M2 `FORMALLY_PROMOTED / IMPLEMENTATION_AUTHORIZED / P1_COMPLETED / P2_ACTIVE`

正式事实源：是

Owner：SOMA Java Blueprint、Design、implementation与evidence的一致性状态

最后审查日期：2026-08-12

## 1. 文档责任

本目录记录当前checkout是否符合正式Blueprint/Design、哪些结论已有evidence、哪些尚未实现。
Conformance不拥有产品语义，也不把readiness/feasibility外推为production/release claim。

权威上游：

- [Blueprint](../blueprint/README.md)
- [Design](../design/README.md)
- [Implementation Plan](../engineering/v1-implementation-plan.md)
- [Canonical IR / Execution Engine Plan](../engineering/canonical-ir-execution-engine-implementation-plan.md)
- [Physical Execution Engine M2 Plan](../engineering/physical-execution-engine-m2-implementation-plan.md)

## 2. Current state

```text
Product Blueprint/Design       ACTIVE V1 BASELINE
Formal promotion              PASS
Final global review           PASS
Implementation readiness      FULFILLED / SUPERSEDED_BY_QUALIFICATION
Core abstraction promotion    PASS
I0-I8 authorization           FULFILLED (granted 2026-08-03)
Production source/reactor      PRESENT (I0-I8 scope)
Generated consumer API         PRESENT (V1 final functional/generated surface)
Active implementation slice   M2_P2
G1                             PASS
G2                             PASS
G3                             PASS
G4                             PASS
G5                             PASS
G6                             PASS
G7                             PASS
G8                             PASS
G9                             PASS
G10                            PASS
Package/release                LOCAL_PACKAGE_QUALIFIED / PUBLICATION_NOT_AUTHORIZED
Repository projection         DELIVERY_CENTERED / GOVERNANCE_PASS
Post-governance global review PASS / FINDINGS_CLOSED
Performance/correctness       MEMORY_ATTRIBUTION_LOW_ALLOCATION_PASS / TYPE_DISTRIBUTION_QUALIFIED /
                              FOUR_DIMENSIONAL_GOVERNANCE_PASS / PERFORMANCE_FRONTIER_QUALIFIED
Vector physical phase 1       PASS / FORMALLY_PROMOTED / BASELINE_INTEGRATED
Vector physical expansion     PASS / VP1-VP3_COMPLETED / TEMPORARY_RETIRED
Structural/Index governance  INT32_STRUCTURAL_INCREMENTAL_INDEX / PASS / FORMALLY_PROMOTED
Scheduling reference        STANDARD_FJSP_100K / PASS / NATURAL_POINT_MUTATION
Scheduling performance      STANDARD_FJSP_100K / FRESH_0.6S / WARM_0.51S / PASS
Canonical IR promotion        PASS / FORMALLY_PROMOTED / BASELINE_FROZEN
Canonical IR implementation   S1-S6_COMPLETED / QUALIFIED
Canonical IR active slice     NONE
Grassing reference            PASS / COMPLETED / HEADLESS_UI_QUALIFIED /
                              APPLICATION_PROFILE_OPTIMIZED
Active bounded topic          PHYSICAL_EXECUTION_ENGINE_M2 / FORMALLY_PROMOTED /
                              IMPLEMENTATION_AUTHORIZED / P1_COMPLETED / P2_ACTIVE
Queued product concept        SOMA_ENGINE / INTENT_ONLY / NOT_ACTIVE
```

I0 [正式资格](i0-build-spine-qualification.md)证明build、generation与artifact spine成立；I1
[正式资格](i1-primitive-keyed-table-qualification.md)证明第一套primitive keyed Table纵向闭环成立；
I2 [正式资格](i2-schema-type-storage-breadth-qualification.md)证明schema/type/storage/Key/Index breadth
成立；I3 [正式资格](i3-query-ir-reference-qualification.md)证明sequential query、typed IR、reference
interpreter与optimized execution成立；I4
[正式资格](i4-selection-mutation-resource-qualification.md)证明Selection mutation、atomic publication与
Group accounting成立；I5 [正式资格](i5-group-relation-qualification.md)证明GroupBy、binary
Equality/Cross Join与G6成立；I6 [正式资格](i6-parallel-execution-qualification.md)证明bounded
parallel execution与G7成立；I7 [正式资格](i7-compression-metadata-qualification.md)证明Chunk
compression、四级metadata、final generated surface与G8成立；I8
[正式资格](i8-product-qualification.md)证明三个reference application、approved百万行performance
threshold、package/SBOM/provenance，以及`develop@a6e8400`的远端CI与non-publishing release
qualification成立。I0-I8与G1-G10 implementation qualification闭合；授权不包含远端artifact
发布、签名或正式release声明。

## 3. Conformance matrix

| Surface | Design Owner | Current executable fact | Status |
|---|---|---|---|
| Cross-Owner abstraction/narrative/proof routing | [Core](../design/core-abstractions-and-narratives.md) | I0-I8 proof chain继续成立；全部query family已进入Canonical/Bound/Physical/Frame主线，旧adapter与迁移桥已退出 | PASS / M1_S1-S6_COMPLETE |
| Schema/compiler/full regeneration | [Schema](../design/schema-and-generation.md) | six annotations、aggregating processor、完整composition/type/symbol preflight、I7 final generated surface与full regeneration已建立 | PASS |
| Group/Table/chunk/Key/Index/compression | [Storage](../design/data-model-and-storage.md) | checked int structural/long cumulative domain、paged PLAIN/encoded/overlay storage、singleton-inline/ordered-int-array Key/Index、point incremental maintenance、atomic StateRoot与Group retained accounting已建立 | PASS |
| Direct/Group/Join logical API | [Logical](../design/logical-api.md) | Table/Index/Field query、Selection mutation、GroupBy、binary Equality/Cross Join、explicit parallel与四级metadata已建立 | PASS |
| Exact Java 8 surface | [Signature](../design/generated-api-signatures.md) | I7 cumulative generated/runtime public surface已由Java 8 consumer与javap边界验证 | PASS |
| IR/optimizer/reference interpreter | [Planning](../design/planning-and-optimization.md) | Row/Field/Mapped/Primitive/Relation/Group已由Canonical/Bound与独立Reference拥有；finite primitive kernel、encoded handler与ordered materialization resource一次进入PhysicalPlan | PASS / M1_S1-S6_COMPLETE / VECTOR_EXPANSION_PROMOTED |
| Binding/mutation/parallel/resource | [Execution](../design/execution-and-concurrency.md) | 全部query family共享guard、binding、admission与Frame主路径；Row range/Chunk aggregate/materialization共享唯一ordinal-work lifecycle | PASS / M1_S1-S6_COMPLETE / VECTOR_EXPANSION_PROMOTED |
| Result/failure | [Failure](../design/results-and-failures.md) | query与point/Selection mutation structured result/failure、zero-publication与no-op成立 | PASS |
| Artifact/internal architecture | [Architecture](../design/implementation-architecture.md) | two-artifact/Java 8/package继续PASS；全部query family的Java-lowering-to-frame seam与replacement closure已成立 | PASS / M1_S1-S6_COMPLETE |
| Performance/scenarios | BP-15 + G9 | 三个reference application、10K/1M/10M frontier matrix、profile优化与fixed-host memory attribution通过 | PASS |
| Security/package/release | G10 | dependency/license/SBOM/checksum/provenance、package consumer、本地与远端non-publishing workflow qualification成立 | PASS |

Documentation不得把同机qualification阈值改写为跨硬件SLA、正式release或一亿行性能承诺。

## 4. Active records

- [Physical Execution Engine M2正式晋升与实施准入](v1-physical-execution-engine-m2-promotion-readiness.md)：
  Candidate fingerprint、Design/Engineering Baseline、P1-P6 readiness、Product Owner授权与publication
  boundary Owner；
- [Physical Execution Engine M2 P1资格](physical-execution-engine-m2-p1-qualification.md)：最小
  Pipeline/Segment/Kernel/Morsel topology、finite Chunk decision replacement、1M防退化与P1 exit Owner；

- [Vectorized Physical Pipeline第一阶段正式晋升](v1-vectorized-physical-pipeline-phase1-promotion.md)：
  finite primitive Chunk kernel、PhysicalPlan单次decision、Chunk-morsel partial、shared parallel lifecycle、
  1M最终回归与Temporary replacement closure Owner；
- [Vectorized Physical Pipeline扩展VP1资格](vectorized-pipeline-expansion-vp1-qualification.md)：
  single final Physical decision、encoded-native integral count/sum/predicate、resource admission与同机
  baseline/candidate性能证据Owner；
- [Vectorized Physical Pipeline扩展VP2资格](vectorized-pipeline-expansion-vp2-qualification.md)：
  ordered `long[]` representation-native materialization、bounded parallel partition、resource与同机
  baseline/candidate性能证据Owner；
- [Vectorized Physical Pipeline扩展治理](v1-vectorized-physical-pipeline-expansion-governance.md)：
  VP1-VP3完整实施、10K/1M/10M、reference applications、正式Design晋升与Temporary replacement closure
  Owner；
- [Selection mutation write-set and in-place commit governance](v1-selection-mutation-write-set-governance.md)：
  PLAIN Selection update/remove从touched-Chunk全leaf copy迁移到columnar write set、dense move plan、
  final-locator sidecar projection与prevalidated final commit的Owner；
- [Post-governance global integrity and regression review](v1-post-governance-global-integrity-regression-review.md)：
  结构/Index与Scheduling大范围优化后的Design↔code、operation provenance、resource admission、
  product boundary与全仓回归Owner；
- [Canonical IR 与执行引擎正式晋升和实施准入审查](v1-canonical-ir-execution-engine-promotion-readiness.md)：
  M1 Owner晋升、Design Baseline fingerprint、S1-S6 readiness、Temporary replacement closure与独立授权边界Owner；

- [Final pre-implementation global consistency review](v1-final-pre-implementation-global-consistency-review.md)：
  readiness、findings closure、core promotion与review-time authorization boundary Owner；当前
  post-review authorization状态由本文第6节拥有；
- [Large-scale engine formal promotion](large-scale-engine-formal-promotion.md)：候选source
  fingerprint、promotion matrix、evidence boundary与Temporary replacement closure；
- [V1 Implementation Gates](v1-implementation-gates.md)：G1-G10最低production evidence。
- [I0 Build Spine Qualification](i0-build-spine-qualification.md)：I0 implementation commit、
  Java 8环境、full-regeneration、linkage、dependency/security与artifact provenance Owner。
- [I1 Primitive Keyed Table Qualification](i1-primitive-keyed-table-qualification.md)：I1
  implementation commit、generated public surface、paged storage/Key、query/update/resource/failure与
  独立审查Owner。
- [I2 Schema、Type 与 Storage Breadth Qualification](i2-schema-type-storage-breadth-qualification.md)：
  I2 implementation commit、complete type/Key/Index matrix、generated breadth、failed-state、规模journey
  与独立审查Owner。
- [I3 Query IR 与 Reference Execution Qualification](i3-query-ir-reference-qualification.md)：
  I3 implementation commit、sequential query surface、logical/physical plan、reference differential、
  materialization、numeric与qualification Owner。
- [I4 Selection Mutation 与 Resource Qualification](i4-selection-mutation-resource-qualification.md)：
  I4 implementation commit、Selection/IndexSelection mutation、atomic publication、resource、Group
  accounting与qualification Owner。
- [I5 GroupBy 与 Relation Qualification](i5-group-relation-qualification.md)：I5 implementation commit、
  GroupBy、binary Equality/Cross Join、typed result、reference differential与G6 Owner。
- [I6 Bounded Parallel Execution Qualification](i6-parallel-execution-qualification.md)：I6
  implementation commit、generated parallel surface、bounded scheduler、custom/common pool、order与G7 Owner。
- [I7 Compression 与 Metadata Qualification](i7-compression-metadata-qualification.md)：I7
  implementation commit、Chunk representation、AUTO/OFF、overlay、四级metadata、explain与G8 Owner。
- [I8 Product Qualification](i8-product-qualification.md)：I8 implementation commit、三个reference
  application、million-row profile、approved G9 threshold、package/SBOM/provenance与G10 local Owner。
- [V1 Delivery-centered Repository Governance](v1-delivery-centered-repository-governance.md)：
  user-first入口、capability-based tests/qualification、build-support与source-delivery迁移Owner。
- [V1 Performance and Correctness Governance](v1-performance-correctness-governance.md)：
  三个reference application长期benchmark、正常路径正确性复核、profile驱动优化、scale/memory边界、
  相对回归ratchet与最佳实践Owner。
- [V1 Ten-million Composed Workload Governance](v1-ten-million-composed-workload-governance.md)：
  三个10M组合场景、正常路径正确性、Key-aware Join资源上界、mutation/accounting优化、AUTO/OFF与
  fixed-host性能/内存边界Owner。
- [V1 Four-dimensional Performance Architecture Governance](v1-four-dimensional-performance-architecture-governance.md)：
  Design、Execution、Memory、CPU因果模型，GroupBy/Relation内部优化、10M before/after、低CPU利用率
  归因与剩余parallel架构边界Owner。
- [V1 Operator × Type × Distribution Performance Qualification](v1-operator-type-distribution-performance-qualification.md)：
  benchmark-only type matrix、数据分布、cost-aware RLE、Bound cardinality、Field materialization、
  1M before/after与10M资格Owner。
- [V1 Memory Attribution and Low-allocation Execution Governance](v1-memory-attribution-low-allocation-governance.md)：
  retained、temporary reservation、participant allocation、heap/RSS边界，正常路径低分配优化、
  controlled A/B与fixed 10M资格Owner。
- [V1 Performance Frontier Qualification](v1-performance-frontier-qualification.md)：Table、Field、
  IndexSelection与mapped/stateful/Group/Relation/mutation矩阵，10K/1M/10M、CPU/allocation profile、
  accepted optimization、固定主机memory attribution、最佳实践与剩余性能边界Owner。
- [V1 Scheduling Performance Governance](v1-scheduling-performance-governance.md)：标准100K FJSP
  phase separation、通用point mutation/low-allocation runtime优化、example访问路径、CPU/allocation
  profile、fresh/warm evidence、最佳实践与300ms剩余边界Owner。
- [V1 32位结构域与即时增量 Key/Index 维护资格](v1-incremental-structural-mutation-governance.md)：
  checked 32/64位数值域、singleton-inline/ordered `int[]`唯一membership、point/Selection
  mutation、failed-state/accounting、detached predecessor A/B、10K/1M/10M与profile Owner。
- [V1 Scheduling Reference Application 治理](v1-scheduling-reference-application-governance.md)：
  标准100K FJSP、四张runtime Table、FCFS + SPT、自然waiting `add/remove`、独立结果校验、
  benchmark解耦与底层Index治理application回归Owner。
- [V1 Grassing Simulation Reference Application 治理](v1-grassing-simulation-reference-application-governance.md)：
  两张runtime Table、五阶段tick、UI/headless、determinism/validation、application JFR/A-B、
  source/package delivery与Selection mutation后续边界Owner。
- [Canonical IR / Execution S1 Qualification](canonical-ir-execution-s1-qualification.md)：compact identity/
  typed literal、最小Row Canonical/Bound/Reference/Physical/Frame闭环、Java 8与10K/1M防退化证据Owner。
- [Canonical IR / Execution S2 Qualification](canonical-ir-execution-s2-qualification.md)：完整普通
  Row/Field/Selection、parallel refinement、safe explain、Java 8与10K/1M防退化证据Owner。
- [Canonical IR / Execution S3 Qualification](canonical-ir-execution-s3-qualification.md)：Mapped/Primitive
  Canonical family、独立reference、unboxed specialized kernel、Java 8与10K/1M防退化证据Owner。
- [Canonical IR / Execution S4 Qualification](canonical-ir-execution-s4-qualification.md)：Relation/Group
  Canonical/Bound/Physical/Frame、two-root lifecycle、独立reference与1M防退化证据Owner。
- [Canonical IR / Execution S5 Qualification](canonical-ir-execution-s5-qualification.md)：relation-left
  canonical source、旧Row adapter退役、layer ownership与replacement closure证据Owner。
- [Canonical IR / Execution S6 Final Qualification](canonical-ir-execution-s6-final-qualification.md)：
  S1-S6最终replacement、全量交付、10K/1M/10M与100K FJSP性能防退化、Owner closure和claim boundary Owner。
- [I1 Field Endpoint Signature Correction](i1-field-endpoint-signature-correction.md)：
  `@SomaField`与generic marker同名反例、Product Owner裁决、targeted evidence与replacement
  closure Owner。

Historical inputs：

- [Previous V1 Implementation Readiness Review](v1-implementation-readiness-review.md)：
  `HISTORICAL_PASS / SUPERSEDED`；
- [Previous formal documentation promotion](formal-documentation-promotion.md)：2026-08-01旧
  baseline的promotion provenance；
- [P2 Java 8 feasibility](p2-generated-api-feasibility.md)：旧baseline selected type/mechanism
  evidence；只有被新promotion record重新采纳的部分仍可作为input。

Historical record不能覆盖current Blueprint/Design/Readiness。当前唯一active bounded Temporary是
[SOMA Physical Execution Engine M2全局架构治理](../temp/soma-physical-execution-engine-m2-governance/README.md)：
current-state audit、Candidate Design、有限验证、过度设计审查与P1-P6计划已经完成并冻结；正式事实已由
Design/Engineering与[M2晋升记录](v1-physical-execution-engine-m2-promotion-readiness.md)接管，Temporary只保留
provenance并等待P6 replacement closure。当前P1已获授权并激活。
Vectorized Physical Pipeline扩展已经由
[正式Conformance记录](v1-vectorized-physical-pipeline-expansion-governance.md)接管并完成VP1-VP3、正式Owner
晋升与replacement closure；Product Owner未授权范围扩张或release。Grassing治理已由
[正式Conformance记录](v1-grassing-simulation-reference-application-governance.md)接管并完成
replacement closure。
Canonical IR/Execution治理已经正式晋升、冻结并完成S1-S6 implementation与qualification；当前没有
active implementation slice。
[SOMA Engine产品构思](../temp/soma-engine-product-concept/README.md)是独立queued intent，不是Design、
active governance或current implementation input。
`docs/`仍是用户文档placeholder。`benchmarks/`的长期non-production性能与正确性证据已由
[正式Conformance记录](v1-performance-correctness-governance.md)完成治理和replacement closure；
千万行组合负载的扩展证据见[对应记录](v1-ten-million-composed-workload-governance.md)；四维性能架构
收口见[对应记录](v1-four-dimensional-performance-architecture-governance.md)；
类型、operator与distribution kernel资格见[对应记录](v1-operator-type-distribution-performance-qualification.md)；
内存归因与低分配执行资格见[对应记录](v1-memory-attribution-low-allocation-governance.md)；
全面source/operator/composition性能前沿资格见[对应记录](v1-performance-frontier-qualification.md)；
交付导向仓库治理见[对应记录](v1-delivery-centered-repository-governance.md)。

## 5. Gate status

| Gate | Status |
|---|---|
| G1 Artifact/build/full regeneration | PASS |
| G2 Schema/generated surface | PASS |
| G3 Storage/Key/Index | PASS |
| G4 Query/IR/optimizer | PASS |
| G5 Mutation/resource/failure | PASS |
| G6 Group/Join | PASS |
| G7 Parallel | PASS |
| G8 Compression/metadata | PASS |
| G9 Scenarios/performance | PASS |
| G10 Security/package/release | PASS |

Gate不能在对应production surface出现前运行或标PASS；历史slice-scoped disposition不能替代当前
完整Gate结果。

## 6. Implementation authorization contract

核心抽象promotion与targeted readiness delta review已经关闭。Product Owner 于 2026-08-03
明确授权 Codex 按当前 Blueprint、九个 Design Owner、I0-I8 Plan 与 G1-G10 自主完成 V1。

授权要求：

- 一次只推进一个active slice；
- 每个slice只有在exit evidence、独立审查、Conformance更新和干净提交完成后才进入下一项；
- 允许自主编写code/test/benchmark/docs并提交推送`develop`；
- 允许创建、修改、提交并验证repository-local GitHub Actions CI与non-publishing release
  qualification workflow；允许由`develop` push触发这些验证；
- 允许运行local/internal benchmark与profile，并将可重放入口、环境、fingerprint和有边界的
  结论写入Conformance；G9 threshold仍按Gate要求由profile proposal与Product Owner批准；
- 允许运行local Maven package qualification，包括source/javadoc、LICENSE/NOTICE、SBOM、
  checksum、provenance与independent consumer smoke；
- 预先准入JUnit Jupiter 5.x作为I0建立的唯一third-party test framework。它及其JUnit Platform
  test runtime必须只处于test scope；I0需固定一个官方支持Java 8的具体版本，并记录dependency
  tree、license、known-vulnerability、Java 8 compatibility与artifact leakage证据；不得引入
  Vintage或把JUnit传递到`soma-runtime`、`soma-processor`及consumer runtime；
- 默认由主Agent沿单一路径推进；Gate需要独立证据时至多使用一个bounded只读subagent，不以重复审查
  替代实施，也不并行修改同一核心surface；
- Blueprint/Design语义变化、stop rule、权限扩张、上述JUnit test stack以外的新dependency、
  第三production artifact、证明链无法闭合或性能与正确性取舍必须暂停等待Product Owner。

授权不包含：

- 上述JUnit test stack与standard Maven/JDK build surface以外的dependency或plugin expansion；
- GitHub Release、GitHub Packages、Maven repository或其他remote artifact publication；
- 未通过G9及claim review的对外性能claim；
- signing与正式release声明。

I0已经以standard Maven/JDK build surface与JUnit Jupiter `5.11.4` test-only stack完成资格；
具体版本、dependency tree、license、2026-08-04时点known-vulnerability、Java 8执行与artifact
隔离证据见[I0记录](i0-build-spine-qualification.md)。I1-I7资格分别见
[I1记录](i1-primitive-keyed-table-qualification.md)、
[I2记录](i2-schema-type-storage-breadth-qualification.md)、
[I3记录](i3-query-ir-reference-qualification.md)和
[I4记录](i4-selection-mutation-resource-qualification.md)和
[I5记录](i5-group-relation-qualification.md)和
[I6记录](i6-parallel-execution-qualification.md)和
[I7记录](i7-compression-metadata-qualification.md)与
[I8记录](i8-product-qualification.md)。I0-I8 implementation slice与G1-G10均已关闭；CI/release
qualification workflow保持non-publishing。GitHub
Release、Package publication、签名和正式发布声明均不在授权内。

上述2026-08-03授权只拥有已经完成的I0-I8实施；Canonical IR/Execution S1-S6由Product Owner于
2026-08-11另行授权，并已经按独立
[Engineering Plan](../engineering/canonical-ir-execution-engine-implementation-plan.md)完成。最终状态与证据见
[S6最终资格](canonical-ir-execution-s6-final-qualification.md)。

## 7. Release claim boundary

只有G1-G10全部PASS、three scenarios达到approved qualification、package/provenance成立且
Product Owner签署release authorization后，才可以声明可用性、兼容性、支持矩阵或release
readiness。正式Design与readiness本身不构成release evidence。
