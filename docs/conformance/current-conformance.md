# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式Blueprint/Design与2026-07-29 Corretto engineering candidate

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
| Java 8 schema/compiler/type system | 一致且 evidenced | Corretto 8 exact toolchain、four-kind classifier、arbitrary-object negative、String selector、`@SomaValue` flatten、owned child、schema/hash repeat与external consumer |
| Metadata control plane | 一致且 evidenced | Descriptor属于完整`SomaMetadata`；schema-seeded mutable builder在创建前freeze为Effective Plan；generated Table/Group投影detached Runtime Metadata；hot path不解释Metadata |
| Group/Table ownership | 一致且 evidenced | explicit/implicit Group、stable slots、multi-schema/multi-instance、atomic attach、GroupLedger、分层fault、all-member preflight与reverse release |
| storage/layout/locator | 一致；当前scale evidence blocked | `FLAT`、`FLAT_HEAD_SEGMENTED_TAIL`、atomic publication、`FLAT_COMPACT` locator及contract通过；100M证据仍绑定历史Zulu candidate |
| Access/Candidate | 一致且 evidenced | point/exact/column保持natural path；Candidate closed range/segment/exact/sparse形态与sequential differential闭合 |
| Transformation/relation | 一致且 evidenced | Group/Join/Window specialized strategy、Delta staging、bounded output及known/overflow/unknown cardinality分配前拒绝 |
| DataFlow execution/parallel | 一致且 evidenced | Definition→Template→one-shot Invocation；一个bounded adaptive morsel scheduler区分Segment/vector/morsel，支持单Segment中型并行和deterministic merge |
| Result Delivery | 一致且 evidenced；scale待重放 | Eager Detached默认；Candidate/Value/Group/Join/Window同步callback-scoped visitor contract通过；历史Delivery scale lane待Corretto重放 |
| String V1 | 一致；当前scale evidence blocked | 唯一reference-backed immutable scalar后端及mutation/lifecycle contract成立；1M/10M/双100M String规模与actual GC仍绑定历史Zulu candidate |
| Resource/failure/observation | 一致且 evidenced | plan hard boundaries、typed preflight、structural/reachable-String/JVM heap分层、stable failure envelope、Table/Group/DataFlow stats/explain |
| component performance | 一致且 evidence有限 | Corretto/macOS/aarch64 Access与DataFlow baseline通过；不外推其他环境 |
| runtime-scale qualification | blocked | 历史Zulu十lane全部passed且保留；当前Corretto authority尚未重跑Small/Medium、1M/10M、single/double100M、String、Expansion、Delivery、Soak |
| reference applications | 一致且 evidenced | 三个独立Java 8 consumer的correctness与Corretto 9 profile baseline通过；相关root由显式SomaGroup拥有 |
| code/test规模 | 一致且 evidenced | replacement closure与footprint Gate保留；测试、benchmark和脚本按Capability/journey/evidence分层，不以治理批次形成平行Owner |
| G0–G4 | passed | Corretto compiler/codegen/runtime/external consumer与工程Full evidence |
| G5 | blocked | component/application已通过，Corretto runtime-scale qualification未完成 |
| G6 selected private-source | blocked | identity/SCM/support/security与Corretto Linux Full已形成；clean package/security provenance、最终matrix sign-off和manual qualification未完成 |
| Codex Cloud development | candidate / qualification-blocked | 产品/工程形状适合隔离Linux开发，setup已退出重复evidence/release预热；尚未完成Corretto fresh-container setup、Fast、Full与clean-worktree验收，不进入支持矩阵 |

## 3. 当前结论

SOMA Java V1仍按“Schema-Defined、Compiler-Specialized、JVM Heap-Resident
runtime-state computing library”理解，并由State/Owner、Metadata/Plan、closed
Capability、Resource/Failure/Observation组成。工程体系已收敛为
Fast→Full→Qualification，使用Maven标准local repository、一次准备多项取证、
最多四路安全并行和fail-closed阶段状态。

JDK authority迁移属于支持与evidence变化，不是产品语义变化。Corretto本机
compiler/runtime/component/application以及Ubuntu x64 build/contract evidence已
形成；Zulu runtime-scale与release qualification保持历史事实，但不再作为当前
passed依据。因此`CF-006`仍保持打开，并由`CF-016`承担Corretto规模重验；
`CF-009`–`CF-015`的实现闭合不因此回退。

当前candidate可继续本地与GitHub Linux CI开发，但不能声明Corretto下的100M
qualification、Linux性能/规模、private-source G6、Codex Cloud ready、
production、public release或Maven Central readiness。

## 4. Evidence 入口

- [当前G0–G6状态](../../reports/java-v1-goal-execution-status.md)
- [工程体系治理报告](../../reports/2026-07-29-soma-v1-engineering-system-governance-report.md)
- [当前性能与规模摘要](../../reports/current-performance-summary.md)
- [Implementation Map](../implementation-map/README.md)
- [Benchmark 治理](../engineering/benchmark-governance.md)
- [Validation Gate治理](../engineering/validation-gates.md)

本结论不授权改变仓库visibility、创建tag、公开发布或上传Maven artifact。
