# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式 Blueprint/Design 与 2026-07-28 runtime-scale production candidate

实现身份：base `6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`

事实范围：主要设计能力的一致性判断和直接依据

非事实范围：public release授权、跨环境支持矩阵或任意Schema性能承诺

最后审查日期：2026-07-28

## 1. 判定口径

- **一致且 evidenced**：代码、生成物、测试和适用 Gate 与正式 Design 一致；
- **一致但 evidence 有限**：未发现设计偏差，但测量只在记录环境/profile成立；
- **blocked**：缺少必需外部事实或未获对应授权；
- 本机 `passed` 不自动成为 public claim，受限profile不得外推为任意输入。

## 2. 能力矩阵

| 关注点 | 当前判定 | 直接依据与边界 |
|---|---|---|
| Java 8 schema/compiler/type system | 一致且 evidenced | four-kind classifier、arbitrary-object negative、String selector、`@SomaValue` flatten、owned child、schema/hash repeat与Zulu 8 external consumer |
| Metadata control plane | 一致且 evidenced | Descriptor属于完整`SomaMetadata`；schema-seeded mutable builder在创建前freeze为Effective Plan；generated Table/Group投影detached Table/Segment/locator/Unique/Index Runtime Metadata；hot path不解释Metadata |
| Group/Table ownership | 一致且 evidenced | explicit/implicit Group、stable slots、multi-schema/multi-instance、atomic attach、GroupLedger、分层fault、all-member preflight与reverse release；Group不冒充transaction或root trust |
| storage/layout/locator | 一致且 evidenced；profile有限 | `FLAT`与`FLAT_HEAD_SEGMENTED_TAIL`、atomic column-group publication、`FLAT_COMPACT` locator、current/high-water observation、跨32K relocation和100M窄表qualification |
| Access/Candidate | 一致且 evidenced | point/exact/column保持natural path；Candidate closed range/segment/exact/sparse形态与sequential differential闭合，不再以universal buffer解释全部terminal |
| Transformation/relation | 一致且 evidenced | Group/Join/Window specialized strategy、Delta staging、bounded output及known/overflow/unknown cardinality分配前拒绝 |
| DataFlow execution/parallel | 一致且 evidenced | Definition→Template→one-shot Invocation；一个bounded adaptive morsel scheduler区分Segment/vector/morsel，支持单Segment中型并行和deterministic merge；Invocation phase ledger最终归零 |
| Result Delivery | 一致且 evidenced | Eager Detached继续默认；Candidate/Value/Group/Join/Window只试点同步callback-scoped visitor，覆盖early stop、consumer failure、cancel/deadline、non-escape和cleanup；无Iterator/pull/Publisher/async路径 |
| String V1 | 一致且 evidenced；profile有限 | 唯一reference-backed immutable scalar后端；payload、Key/Unique/Index、Group/Join、presence/null、no-op、mutation/epoch、clear/release、实际GC和三层memory accounting均闭合；无dictionary/arena |
| Resource/failure/observation | 一致且 evidenced | plan hard boundaries、typed preflight、structural/reachable-String/JVM heap分层、stable failure envelope、Table/Group/DataFlow detached stats/explain |
| runtime-scale qualification | 一致且 evidence有限 | Small/Medium、1M、10M、单表100M、两个同时驻留100M root、shared-reference String双100M、Expansion、Delivery、100次Soak十条required lane全部passed；只适用于预注册本机profile，全部`claimAllowed=false` |
| reference applications | 一致且 evidenced | 三个独立Java 8 consumer均审计；相关root迁入显式SomaGroup，领域模型/算法/Result不变；correctness与九个default/large/long-run baseline通过 |
| code/test规模 | 一致且 evidenced | replacement closure删除generic Object、legacy borrow、平行parallel executor等旧Owner；neutral/三应用22个Table companion的footprint Gate通过；不以LOC或单调用者机械删除 |
| G0–G5 | passed | formal Design、compiler/codegen/runtime/external consumer、reference differential、component/application和production-scale证据在综合治理Gate中重放 |
| G6 public release readiness | blocked | 真实SCM/ownership/contact、signing/publishing、clean provenance与完整support matrix仍不足；本专题未获push/release授权 |

## 3. 当前结论

本轮治理没有降低SOMA Java V1目标，而是把“Schema-Defined、
Compiler-Specialized、JVM Heap-Resident runtime-state computing library”落实为
一套可替换但封闭的Capability组合：State/Owner负责事实与生命周期，
Metadata/Plan负责cold control plane，specialized Capability负责operation，
Resource/Failure/Observation负责可预测执行。

`CF-009`–`CF-015`已由production实现、generated/public contract、测试、
qualification、三个Example审计和代码规模审查关闭。仍开放的只有环境限定
`CF-005`与public release事实`CF-006`。这意味着当前candidate可以支持本轮正式
SOMA目标和记录profile的本机工程结论，但不能声称任意String、任意wide schema、
任意高展开、跨环境SLA、production ready或public release ready。

## 4. Evidence 入口

- [Runtime Boundary、Group、Scale Readiness 综合治理报告](../../reports/2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)
- [当前 G0–G6 状态](../../reports/java-v1-goal-execution-status.md)
- [Implementation Map](../implementation-map/README.md)
- [Benchmark 治理](../engineering/benchmark-governance.md)
- [Reference Application Portfolio治理](../../reports/2026-07-27-reference-application-portfolio-and-best-practice-governance-report.md)
- [Transformation/DataFlow治理](../../reports/2026-07-27-transformation-dataflow-governance-report.md)

本结论不扩大到push、PR、publishing或release授权。
