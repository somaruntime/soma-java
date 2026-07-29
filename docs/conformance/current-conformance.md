# 当前一致性基线

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

核对对象：正式Blueprint/Design与`1.0.0` clean release candidate

事实范围：主要设计能力的一致性判断、当前evidence适用性与直接依据

非事实范围：public release授权、跨环境支持矩阵或任意Schema性能承诺

最后审查日期：2026-07-29

## 1. 判定口径

- **一致且 evidenced**：代码、生成物、测试和当前JDK authority下的适用Gate一致；
- **一致但 evidence 有限**：未发现设计偏差，但测量只在记录环境/profile成立；
- **blocked**：目标仍保留，但当前authority/环境缺少必需evidence；
- **evidence-resolved**：source-side条件已冻结，由本报告所在同一SHA的retained
  artifact解析；全部条件通过才是passed，任一缺失或失败即blocked；
- **waived**：Owner明确接受未取证项及其影响，且相关support claim保持关闭；
- 历史`passed`不自动外推到新JDK authority或新candidate。

## 2. 能力矩阵

| 关注点 | 当前判定 | 直接依据与边界 |
|---|---|---|
| Java 8 schema/compiler/type system | 一致且 evidenced | four-kind storage classifier保持；enum/date/time/instant使用logical type-specific facade，raw primitive保持numeric capability；negative compile、generated golden与external consumer通过 |
| Metadata control plane | 一致且 evidenced | Descriptor属于完整`SomaMetadata`；schema-seeded mutable builder在创建前freeze为Effective Plan；generated Table/Group投影detached Runtime Metadata；hot path不解释Metadata |
| Group/Table ownership | 一致且 evidenced | explicit/implicit Group、stable slots、multi-schema/multi-instance、atomic attach、GroupLedger、分层fault、all-member preflight与reverse release |
| storage/layout/locator | 一致且 evidenced | `FLAT`、`FLAT_HEAD_SEGMENTED_TAIL`、atomic publication、`FLAT_COMPACT` locator及单/双1M qualification通过；10M/100M只保留research/stress，不影响V1 Gate |
| Access/Candidate | 一致且 evidenced | point/exact/column保持natural path；formula-bound primitive low-cardinality Bitmap intersection、link fallback、mutation/relocation、budget与differential contract通过 |
| Transformation/relation | 一致；final evidence-resolved | integral arithmetic已按Owner裁决统一为fail-closed checked semantics，raw/closed、scalar/parallel、prefix、Group、Window与Expanded canonical contract及DataFlow v5 clean 3-fork通过；既有Group/Join/Delta与primitive min/max/Bloom/fallback Join保持，最终reference/scale由同SHA artifact解析 |
| DataFlow execution/parallel | 一致且 evidenced | Definition→Template→one-shot Invocation；一个bounded adaptive morsel scheduler区分Segment/vector/morsel，支持单Segment中型并行和deterministic merge |
| Result Delivery | 一致且 evidenced | Eager Detached默认；Candidate/Value/Group/Join/Window同步callback-scoped visitor contract及Corretto Delivery/Soak lane通过 |
| String V1 | 一致且 evidenced | reference-backed immutable scalar、Key/Unique/Index、Group/Join、不同长度mutation、equal-value no-op、clear/release/actual GC成立；length只为非约束profile |
| Resource/failure/observation | 一致且 evidenced | plan hard boundaries、typed preflight、structural/reachable-String/JVM heap分层、stable failure envelope、Table/Group/DataFlow stats/explain |
| component performance | 一致且 evidence有限 | Corretto/macOS/aarch64 Access v1 baseline保持；DataFlow v5已在clean commit固定3-fork通过且threshold不放宽；不外推其他环境 |
| runtime-scale qualification | 一致且 evidence有限；final evidence-resolved | 最终candidate必须产生同SHA、精确source tree的8条required lane且全部`passed`、`claimAllowed=false`；10M/100M仍仅为非阻塞research/stress |
| reference applications | 一致且 evidenced | 三个独立Java 8 consumer的correctness成立；Corretto下九个profile已各完成5-fork identity calibration且threshold不放宽，相关root由显式SomaGroup拥有；最终同SHA replay由G5 artifact解析 |
| code/test规模 | 一致且 evidenced | replacement closure与footprint Gate保留；测试、benchmark和脚本按Capability/journey/evidence分层，不以治理批次形成平行Owner |
| G0 | passed | Java-only scope、Owner、claim boundary与抽象叙事闭环稳定 |
| G1–G4 | same-SHA evidence-resolved | 本报告所在clean SHA的canonical Full与private CI Full必须通过 |
| G5 | same-SHA evidence-resolved | 新arithmetic/differential contract与DataFlow v5 clean calibration已闭合；最终SHA的三个application与runtime-scale 8-lane required qualification必须通过 |
| G6 selected private-source | conditional Owner sign-off | 当前successor已闭合产品语义与实现；同SHA Ubuntu Full、package/reproducibility、security/provenance、sealed bundle、support matrix、manual qualification与下载checksum全部成功才为passed，否则blocked |
| AI multi-tool behavior evidence | waived for V1 | Codex positive consumer已真实compile/run；negative/anti-pattern与第二宿主验证由Product Owner明确waive，不声明multi-tool support |
| Codex Cloud development | not release-scoped | 当前用户目标不要求Cloud qualification；它不进入支持矩阵，也不替代private-source G6 |

## 3. 当前结论

SOMA Java V1仍按“Schema-Defined、Compiler-Specialized、JVM Heap-Resident
runtime-state computing library”理解，并由State/Owner、Metadata/Plan、closed
Capability、Resource/Failure/Observation组成。工程体系已收敛为
Fast→Full→Qualification，使用Maven标准local repository、一次准备多项取证、
最多四路安全并行和fail-closed阶段状态。

JDK authority迁移属于支持与evidence变化，不是产品语义变化。旧Corretto本机
compiler/runtime/component/application及Ubuntu x64 build/contract只用于定位回归，
不替代最终clean immutable candidate的正式Gate。最终macOS runtime-scale必须绑定
同一clean commit与精确source tree；Zulu evidence只属于历史。

本次冻结前产品目标审计发现的integral overflow policy已由Product Owner裁决并
完成Design/Code/contract replacement closure；successor还包含新的compiler、
DataFlow与workflow修复。Source-side产品一致性已闭合，freeze-ready结果由本报告
所在clean immutable SHA的适用Gate、qualification与retained bundle解析；source
文本不预写动态passed状态。

即使新的candidate最终通过，也不能把本机evidence外推为Linux性能/规模、
production、public release或Maven Central；AI behavior waiver也不能外推为
multi-tool support。

## 4. Evidence 入口

- [V1 release governance](../../reports/java-v1-release-governance-report.md)
- [当前性能与规模摘要](../../reports/current-performance-summary.md)
- [Implementation Map](../implementation-map/README.md)
- [Benchmark 治理](../engineering/benchmark-governance.md)
- [Validation Gate治理](../engineering/validation-gates.md)

本结论不授权改变仓库visibility、创建tag、公开发布或上传Maven artifact。
