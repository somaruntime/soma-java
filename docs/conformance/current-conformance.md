# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式Blueprint/Design与`1.0.0` release candidate preparation

事实范围：主要设计能力的一致性判断、当前evidence适用性与直接依据

非事实范围：public release授权、跨环境支持矩阵或任意Schema性能承诺

最后审查日期：2026-07-29

## 1. 判定口径

- **一致且 evidenced**：代码、生成物、测试和当前JDK authority下的适用Gate一致；
- **一致但 evidence 有限**：未发现设计偏差，但测量只在记录环境/profile成立；
- **blocked**：目标仍保留，但当前authority/环境缺少必需evidence；
- 历史`passed`不自动外推到新JDK authority或新candidate。

## 2. 能力矩阵

| 关注点 | 当前判定 | 直接依据与边界 |
|---|---|---|
| Java 8 schema/compiler/type system | 一致且 evidenced | four-kind storage classifier保持；enum/date/time/instant使用logical type-specific facade，raw primitive保持numeric capability；negative compile、generated golden与external consumer通过 |
| Metadata control plane | 一致且 evidenced | Descriptor属于完整`SomaMetadata`；schema-seeded mutable builder在创建前freeze为Effective Plan；generated Table/Group投影detached Runtime Metadata；hot path不解释Metadata |
| Group/Table ownership | 一致且 evidenced | explicit/implicit Group、stable slots、multi-schema/multi-instance、atomic attach、GroupLedger、分层fault、all-member preflight与reverse release |
| storage/layout/locator | 一致且 evidenced | `FLAT`、`FLAT_HEAD_SEGMENTED_TAIL`、atomic publication、`FLAT_COMPACT` locator及单/双1M qualification通过；10M/100M只保留research/stress，不影响V1 Gate |
| Access/Candidate | 一致且 evidenced | point/exact/column保持natural path；formula-bound primitive low-cardinality Bitmap intersection、link fallback、mutation/relocation、budget与differential contract通过 |
| Transformation/relation | 一致且 evidenced | 既有Group/Join/Window/Delta语义保留；numeric closed whole-loop kernel与primitive min/max/Bloom/fallback Join通过reference differential、contract与双1M lane；String filter禁用 |
| DataFlow execution/parallel | 一致且 evidenced | Definition→Template→one-shot Invocation；一个bounded adaptive morsel scheduler区分Segment/vector/morsel，支持单Segment中型并行和deterministic merge |
| Result Delivery | 一致且 evidenced | Eager Detached默认；Candidate/Value/Group/Join/Window同步callback-scoped visitor contract及Corretto Delivery/Soak lane通过 |
| String V1 | 一致且 evidenced | reference-backed immutable scalar、Key/Unique/Index、Group/Join、不同长度mutation、equal-value no-op、clear/release/actual GC成立；length只为非约束profile |
| Resource/failure/observation | 一致且 evidenced | plan hard boundaries、typed preflight、structural/reachable-String/JVM heap分层、stable failure envelope、Table/Group/DataFlow stats/explain |
| component performance | 一致且 evidence有限 | Corretto/macOS/aarch64 Access与DataFlow baseline通过；不外推其他环境 |
| runtime-scale qualification | blocked pending exact candidate | 旧Corretto/macOS/aarch64 v2 artifact的8条required lane可作回归诊断，但recorded commit不是当前clean immutable candidate；10M/100M仍仅为非阻塞research/stress |
| reference applications | 一致且 evidenced | 三个独立Java 8 consumer的correctness与Corretto 9 profile baseline通过；相关root由显式SomaGroup拥有 |
| code/test规模 | 一致且 evidenced | replacement closure与footprint Gate保留；测试、benchmark和脚本按Capability/journey/evidence分层，不以治理批次形成平行Owner |
| G0 | passed | Java-only scope、Owner、claim boundary与抽象叙事闭环稳定 |
| G1–G4 | blocked pending final Full | production Java surface未改变；`1.0.0`最终clean commit仍需canonical Full |
| G5 | blocked pending exact qualification | 需要clean commit的DataFlow 3-fork provenance与8-lane required artifact |
| G6 selected private-source | blocked | 需要同一最终candidate的Full、clean package/security provenance、matrix与Owner sign-off |
| Codex Cloud development | not release-scoped | 当前用户目标不要求Cloud qualification；它不进入支持矩阵，也不替代private-source G6 |

## 3. 当前结论

SOMA Java V1仍按“Schema-Defined、Compiler-Specialized、JVM Heap-Resident
runtime-state computing library”理解，并由State/Owner、Metadata/Plan、closed
Capability、Resource/Failure/Observation组成。工程体系已收敛为
Fast→Full→Qualification，使用Maven标准local repository、一次准备多项取证、
最多四路安全并行和fail-closed阶段状态。

JDK authority迁移属于支持与evidence变化，不是产品语义变化。旧Corretto本机
compiler/runtime/component/application/runtime-scale及Ubuntu x64 build/contract
仍可定位回归，但正式Gate必须绑定新的clean immutable candidate。Zulu evidence
只属于历史。

当前`1.0.0`候选正在形成，不能提前写成G5或private-source G6 passed，也不能把
本机evidence外推为Linux性能/规模、production、public release或Maven Central。

## 4. Evidence 入口

- [V1 release governance](../../reports/java-v1-release-governance-report.md)
- [当前性能与规模摘要](../../reports/current-performance-summary.md)
- [Implementation Map](../implementation-map/README.md)
- [Benchmark 治理](../engineering/benchmark-governance.md)
- [Validation Gate治理](../engineering/validation-gates.md)

本结论不授权改变仓库visibility、创建tag、公开发布或上传Maven artifact。
