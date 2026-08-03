# SOMA Java Conformance

类型：Conformance Entry

状态：最终全局一致性审核`PASS`；Design/Plan `READY_FOR_IMPLEMENTATION`；
Implementation authorization `GRANTED`；I0 `COMPLETE`；I1 `COMPLETE (I1_SCOPE)`；I2 `COMPLETE (I2_SCOPE)`；I3 `COMPLETE (I3_SCOPE)`；I4 `COMPLETE (I4_SCOPE)`；I5 `COMPLETE (I5_SCOPE)`；G1 `PASS`

正式事实源：是

Owner：SOMA Java Blueprint、Design、implementation与evidence的一致性状态

最后审查日期：2026-08-03

## 1. 文档责任

本目录记录当前checkout是否符合正式Blueprint/Design、哪些结论已有evidence、哪些尚未实现。
Conformance不拥有产品语义，也不把readiness/feasibility外推为production/release claim。

权威上游：

- [Blueprint](../blueprint/README.md)
- [Design](../design/README.md)
- [Implementation Plan](../engineering/v1-implementation-plan.md)

## 2. Current state

```text
Product Blueprint/Design       ACTIVE V1 BASELINE
Formal promotion              PASS
Final global review           PASS
Implementation readiness      READY_FOR_IMPLEMENTATION
Core abstraction promotion    PASS
Implementation authorization  GRANTED (2026-08-03)
Production source/reactor      I0 QUALIFIED
Generated consumer API         I1 primitive keyed + I2 scalar breadth + I3 direct query + I4 point mutation + I5 GroupBy qualified (bounded fixtures)
Active slice                   NONE (I5 COMPLETE; I6 NOT_STARTED)
G1-G10                         G1 PASS; G2-G6 I5_SCOPE_PASS; G10 IN_PROGRESS; others NOT_RUN
Package/release                NOT_QUALIFIED
```

`READY_FOR_IMPLEMENTATION`与独立授权共同允许按I0-I8实施，但不表示任何runtime capability已经
成立。当前授权允许每个slice闭合后的commit与`develop` push，不包含release/package/signing。

## 3. Conformance matrix

| Surface | Design Owner | Current executable fact | Status |
|---|---|---|---|
| Cross-Owner abstraction/narrative/proof routing | [Core](../design/core-abstractions-and-narratives.md) | formal design only | DESIGN_CLOSED / NOT_IMPLEMENTED |
| Schema/compiler/full regeneration | [Schema](../design/schema-and-generation.md) | I0 aggregating carrier/full-regeneration qualified | I0_SCOPE_PASS |
| Group/Table/chunk/Key/Index/compression | [Storage](../design/data-model-and-storage.md) | I1 primitive backend + I2 scalar typed columns/index semantics；sidecar/compression未实施 | I2_SCOPE_PASS |
| Direct/Group/Join logical API | [Logical](../design/logical-api.md) | I1/I2 generated Table/find/get/filter/update bounded surfaces；Join未实施 | I2_SCOPE_PASS |
| Exact Java 8 surface | [Signature](../design/generated-api-signatures.md) | I1/I2 generated Soma/Group/Table/View/Editor/Field bounded fixtures | I2_SCOPE_PASS |
| IR/optimizer/reference interpreter | [Planning](../design/planning-and-optimization.md) | I1/I2 typed expression carrier/evaluator；planner/oracle未实施 | I2_SCOPE_PASS |
| Binding/mutation/parallel/resource | [Execution](../design/execution-and-concurrency.md) | I1/I2 Group guard/scope/point mutation；parallel/resource admission未实施 | I2_SCOPE_PASS |
| Result/failure | [Failure](../design/results-and-failures.md) | I1/I2 UpdateResult、failure mapping与scope/duplicate/missing/overflow bounded | I2_SCOPE_PASS |
| Artifact/internal architecture | [Architecture](../design/implementation-architecture.md) | I0 two-artifact Maven build qualified | I0_PASS |
| Performance/scenarios | BP-15 + G9 | paper journeys only | NOT_EVALUABLE |
| Security/package/release | G10 | no artifact/workflow | NOT_EVALUABLE |

`NOT_IMPLEMENTED`不是Design contradiction；它是clean-slate implementation gap。Documentation
不得把bounded fixture改写为production capability。

## 4. Active records

- [Final pre-implementation global consistency review](v1-final-pre-implementation-global-consistency-review.md)：
  readiness、findings closure、core promotion与review-time authorization boundary Owner；当前
  post-review authorization状态由本文第6节拥有；
- [Large-scale engine formal promotion](large-scale-engine-formal-promotion.md)：候选source
  fingerprint、promotion matrix、evidence boundary与Temporary replacement closure；
- [V1 Implementation Gates](v1-implementation-gates.md)：G1-G10最低production evidence。
- [I0 Build Spine Qualification](i0-build-spine-qualification.md)：I0 exit、G1 PASS 与 G2/G10
  范围边界。
- [I1 Primitive Keyed Table Qualification](i1-primitive-keyed-table-qualification.md)：I1
  bounded vertical slice、独立审查与 scope-limited G2-G5 evidence。
- [I2 Scalar Type/Storage Qualification](i2-scalar-type-storage-qualification.md)：I2
  bounded scalar breadth slice、Java 8 consumer 与 scope-limited G2-G5 evidence。
- [I3 Direct Query Qualification](i3-direct-query-qualification.md)：I3
  bounded direct query/reference interpreter slice 与 scope-limited G4 evidence。
- [I4 Point Mutation Qualification](i4-point-mutation-qualification.md)：I4
  bounded point-remove slice 与 scope-limited G5 evidence。
- [I5 GroupBy Qualification](i5-groupby-qualification.md)：I5 bounded integer-key GroupBy slice 与
  scope-limited G6 evidence。

Historical inputs：

- [Previous V1 Implementation Readiness Review](v1-implementation-readiness-review.md)：
  `HISTORICAL_PASS / SUPERSEDED`；
- [Previous formal documentation promotion](formal-documentation-promotion.md)：2026-08-01旧
  baseline的promotion provenance；
- [P2 Java 8 feasibility](p2-generated-api-feasibility.md)：旧baseline selected type/mechanism
  evidence；只有被新promotion record重新采纳的部分仍可作为input。

Historical record不能覆盖current Blueprint/Design/Readiness。当前没有active Temporary。

## 5. Gate status

| Gate | Status |
|---|---|
| G1 Artifact/build/full regeneration | PASS |
| G2 Schema/generated surface | IN_PROGRESS — I2_SCOPE_PASS |
| G3 Storage/Key/Index | IN_PROGRESS — I2_SCOPE_PASS |
| G4 Query/IR/optimizer | IN_PROGRESS — I2_SCOPE_PASS |
| G5 Mutation/resource/failure | IN_PROGRESS — I2_SCOPE_PASS |
| G6 Group/Join | NOT_RUN |
| G7 Parallel | NOT_RUN |
| G8 Compression/metadata | NOT_RUN |
| G9 Scenarios/performance | NOT_RUN |
| G10 Security/package/release | IN_PROGRESS — I0_BASELINE_PASS |

Gate不能在对应production surface出现前运行或标PASS。

## 6. Implementation authorization contract

核心抽象promotion与targeted readiness delta review已经关闭。Product Owner 于 2026-08-03
明确授权 Codex 按当前 Blueprint、九个 Design Owner、I0-I8 Plan 与 G1-G10 自主完成 V1。

授权要求：

- 一次只推进一个active slice；
- 每个slice只有在exit evidence、独立审查、Conformance更新和干净提交完成后才进入下一项；
- 允许自主编写code/test/benchmark/docs并提交推送`develop`；
- subagent只用于独立分析、测试与审查，不并行修改同一核心surface；
- Blueprint/Design语义变化、stop rule、权限扩张、新dependency、第三artifact、证明链无法闭合
  或性能与正确性取舍必须暂停等待Product Owner。

授权不包含：

- dependency/download beyond admitted build；
- GitHub workflow/release/package；
- benchmark claim；
- publication/signing。

I0 已在Design准入的standard Maven/JDK build surface闭合；任何新的dependency仍需停下裁决。
GitHub Release、Package、签名和正式发布声明均不在授权内。

## 7. Release claim boundary

只有G1-G10全部PASS、three scenarios达到approved qualification、package/provenance成立且
Product Owner签署release authorization后，才可以声明可用性、兼容性、支持矩阵或release
readiness。正式Design与readiness本身不构成release evidence。
