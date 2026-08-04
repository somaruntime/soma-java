# SOMA Java Conformance

类型：Conformance Entry

状态：最终全局一致性审核`PASS`；Design/Plan `READY_FOR_IMPLEMENTATION`；
Implementation authorization `GRANTED`；implementation reset `COMPLETE`；I0-I8 `NOT_STARTED`

正式事实源：是

Owner：SOMA Java Blueprint、Design、implementation与evidence的一致性状态

最后审查日期：2026-08-04

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
Production source/reactor      ABSENT
Generated consumer API         ABSENT
Active slice                   NONE (next: I0)
G1-G10                         NO PASS YET
Package/release                NOT_QUALIFIED
```

`READY_FOR_IMPLEMENTATION`与独立授权共同允许按I0-I8实施，但不表示任何runtime capability已经
成立。当前授权允许每个slice闭合后的commit与`develop` push，不包含release/package/signing。

## 3. Conformance matrix

| Surface | Design Owner | Current executable fact | Status |
|---|---|---|---|
| Cross-Owner abstraction/narrative/proof routing | [Core](../design/core-abstractions-and-narratives.md) | formal design only | DESIGN_CLOSED / NOT_IMPLEMENTED |
| Schema/compiler/full regeneration | [Schema](../design/schema-and-generation.md) | no production processor/build | NOT_IMPLEMENTED |
| Group/Table/chunk/Key/Index/compression | [Storage](../design/data-model-and-storage.md) | no production runtime state | NOT_IMPLEMENTED |
| Direct/Group/Join logical API | [Logical](../design/logical-api.md) | no generated API | NOT_IMPLEMENTED |
| Exact Java 8 surface | [Signature](../design/generated-api-signatures.md) | bounded fixtures only | NOT_IMPLEMENTED |
| IR/optimizer/reference interpreter | [Planning](../design/planning-and-optimization.md) | no production planner/interpreter | NOT_IMPLEMENTED |
| Binding/mutation/parallel/resource | [Execution](../design/execution-and-concurrency.md) | no engine/scheduler/admission | NOT_IMPLEMENTED |
| Result/failure | [Failure](../design/results-and-failures.md) | no production carrier/mapping | NOT_IMPLEMENTED |
| Artifact/internal architecture | [Architecture](../design/implementation-architecture.md) | no Maven reactor/artifact | NOT_IMPLEMENTED |
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
| G1 Artifact/build/full regeneration | NOT_RUN |
| G2 Schema/generated surface | NOT_RUN |
| G3 Storage/Key/Index | NOT_RUN |
| G4 Query/IR/optimizer | NOT_RUN |
| G5 Mutation/resource/failure | NOT_RUN |
| G6 Group/Join | NOT_RUN |
| G7 Parallel | NOT_RUN |
| G8 Compression/metadata | NOT_RUN |
| G9 Scenarios/performance | NOT_RUN |
| G10 Security/package/release | NOT_RUN |

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

下一项 I0 只允许使用Design已准入的standard Maven/JDK build surface；任何新的dependency仍需停下
裁决。GitHub Release、Package、签名和正式发布声明均不在授权内。

## 7. Release claim boundary

只有G1-G10全部PASS、three scenarios达到approved qualification、package/provenance成立且
Product Owner签署release authorization后，才可以声明可用性、兼容性、支持矩阵或release
readiness。正式Design与readiness本身不构成release evidence。
