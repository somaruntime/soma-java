# SOMA Java Conformance

类型：Conformance Entry

状态：最终全局一致性审核`PASS`；Design/Plan `READY_FOR_IMPLEMENTATION`；
Implementation authorization `GRANTED`；I0 `COMPLETED`；I1-I8 `NOT_STARTED`

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
Production source/reactor      PRESENT (I0 scope)
Generated consumer API         INTERNAL I0 CARRIER ONLY; PUBLIC API ABSENT
Active slice                   NONE (next: I1)
G1                             PASS
G2 / G10                       I0_SCOPE_PASS / OVERALL IN_PROGRESS
G3-G9                          NOT_RUN
Package/release                NOT_QUALIFIED
```

I0 [正式资格](i0-build-spine-qualification.md)证明build、generation与artifact spine成立，
不表示I1-I8 runtime capability已经成立。当前授权允许每个slice闭合后的commit与`develop`
push，以及下文第6节限定的内部资格验证；不包含远端artifact发布、签名或正式release声明。

## 3. Conformance matrix

| Surface | Design Owner | Current executable fact | Status |
|---|---|---|---|
| Cross-Owner abstraction/narrative/proof routing | [Core](../design/core-abstractions-and-narratives.md) | formal design only | DESIGN_CLOSED / NOT_IMPLEMENTED |
| Schema/compiler/full regeneration | [Schema](../design/schema-and-generation.md) | six annotations、aggregating processor、internal carrier与full regeneration已建立；公开generated surface待I1-I2 | I0_SCOPE_PASS / IN_PROGRESS |
| Group/Table/chunk/Key/Index/compression | [Storage](../design/data-model-and-storage.md) | no production runtime state | NOT_IMPLEMENTED |
| Direct/Group/Join logical API | [Logical](../design/logical-api.md) | no generated API | NOT_IMPLEMENTED |
| Exact Java 8 surface | [Signature](../design/generated-api-signatures.md) | bounded fixtures only | NOT_IMPLEMENTED |
| IR/optimizer/reference interpreter | [Planning](../design/planning-and-optimization.md) | no production planner/interpreter | NOT_IMPLEMENTED |
| Binding/mutation/parallel/resource | [Execution](../design/execution-and-concurrency.md) | no engine/scheduler/admission | NOT_IMPLEMENTED |
| Result/failure | [Failure](../design/results-and-failures.md) | no production carrier/mapping | NOT_IMPLEMENTED |
| Artifact/internal architecture | [Architecture](../design/implementation-architecture.md) | non-published reactor + exactly runtime/processor artifacts；Java 8 qualification通过 | I0_COMPLETED |
| Performance/scenarios | BP-15 + G9 | paper journeys only | NOT_EVALUABLE |
| Security/package/release | G10 | dependency/license/OSV/legal/checksum/reproducibility baseline成立；workflow与完整package/release待I8 | I0_SCOPE_PASS / IN_PROGRESS |

`NOT_IMPLEMENTED`不是Design contradiction；它是clean-slate implementation gap。Documentation
不得把bounded fixture改写为production capability。

## 4. Active records

- [Final pre-implementation global consistency review](v1-final-pre-implementation-global-consistency-review.md)：
  readiness、findings closure、core promotion与review-time authorization boundary Owner；当前
  post-review authorization状态由本文第6节拥有；
- [Large-scale engine formal promotion](large-scale-engine-formal-promotion.md)：候选source
  fingerprint、promotion matrix、evidence boundary与Temporary replacement closure；
- [V1 Implementation Gates](v1-implementation-gates.md)：G1-G10最低production evidence。
- [I0 Build Spine Qualification](i0-build-spine-qualification.md)：I0 implementation commit、
  Java 8环境、full-regeneration、linkage、dependency/security与artifact provenance Owner。

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
| G2 Schema/generated surface | I0_SCOPE_PASS / IN_PROGRESS |
| G3 Storage/Key/Index | NOT_RUN |
| G4 Query/IR/optimizer | NOT_RUN |
| G5 Mutation/resource/failure | NOT_RUN |
| G6 Group/Join | NOT_RUN |
| G7 Parallel | NOT_RUN |
| G8 Compression/metadata | NOT_RUN |
| G9 Scenarios/performance | NOT_RUN |
| G10 Security/package/release | I0_SCOPE_PASS / IN_PROGRESS |

Gate不能在对应production surface出现前运行或标PASS。`I0_SCOPE_PASS`只关闭该slice拥有的证据，
不等于整个Gate已经完成。

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
- subagent只用于独立分析、测试与审查，不并行修改同一核心surface；
- Blueprint/Design语义变化、stop rule、权限扩张、上述JUnit test stack以外的新dependency、
  第三production artifact、证明链无法闭合或性能与正确性取舍必须暂停等待Product Owner。

授权不包含：

- 上述JUnit test stack与standard Maven/JDK build surface以外的dependency或plugin expansion；
- GitHub Release、GitHub Packages、Maven repository或其他remote artifact publication；
- 未通过G9及claim review的对外性能claim；
- signing与正式release声明。

I0已经以standard Maven/JDK build surface与JUnit Jupiter `5.11.4` test-only stack完成资格；
具体版本、dependency tree、license、2026-08-04时点known-vulnerability、Java 8执行与artifact
隔离证据见[I0记录](i0-build-spine-qualification.md)。下一项只允许从I1开始。CI/release
qualification workflow可以在其所属slice建立和运行，但必须保持non-publishing。GitHub
Release、Package publication、签名和正式发布声明均不在授权内。

## 7. Release claim boundary

只有G1-G10全部PASS、three scenarios达到approved qualification、package/provenance成立且
Product Owner签署release authorization后，才可以声明可用性、兼容性、支持矩阵或release
readiness。正式Design与readiness本身不构成release evidence。
