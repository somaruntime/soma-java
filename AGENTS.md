# soma_java Agent Guide

## 当前状态

SOMA Java 已从 clean-slate 起点建立当前正式V1 Blueprint、九个分责Design Owner、
I0-I8 Engineering Plan 与 G1-G10 Conformance Gates。Product Owner 已确认当前 V1 North
Star 为“一亿行以上、编译式、支持关系计算的单进程 Table 引擎”；一亿行以上是架构愿景，
第一阶段以百万行数据的高效、低分配、资源受控操作建立资格证据。

2026-08-03的[实施前最终全局一致性审核](project/conformance/v1-final-pre-implementation-global-consistency-review.md)
为`PASS`；其历史`READY_FOR_IMPLEMENTATION`已经由后续implementation/qualification履行。Product
Owner 已于 2026-08-03 明确授予完整 V1 implementation authorization。I0-I8已完成implementation
与qualification；I0-I7分别通过：
[I0资格](project/conformance/i0-build-spine-qualification.md)、
[I1资格](project/conformance/i1-primitive-keyed-table-qualification.md)、
[I2资格](project/conformance/i2-schema-type-storage-breadth-qualification.md)、
[I3资格](project/conformance/i3-query-ir-reference-qualification.md)、
[I4资格](project/conformance/i4-selection-mutation-resource-qualification.md)、
[I5资格](project/conformance/i5-group-relation-qualification.md)、
[I6资格](project/conformance/i6-parallel-execution-qualification.md)、
[I7资格](project/conformance/i7-compression-metadata-qualification.md)，I8通过
[产品资格与G9 Owner sign-off](project/conformance/i8-product-qualification.md)：G1-G10为`PASS`；
Canonical IR/Execution M1已获Product Owner授权并完成S1-S6 implementation与qualification；当前没有
active implementation slice。
GitHub Release/Package、签名和正式release声明未授权。

Active checkout已包含I8三个reference application、million-row profile、package/SBOM/provenance、CI与
non-publishing release qualification workflow，以及I7 AUTO/OFF compression、PLAIN/encoded/overlay representation、四级typed metadata
与safe explain，以及I6 generated parallel surface、application-owned/common `ForkJoinPool`与bounded
caller-participating scheduler、I5 GroupBy与binary Equality/Cross Join、I4 Selection mutation、I3 typed
IR和I2 schema/type/Key/Index。I8记录拥有对应remote workflow历史证据；当前delivery-centered
repository治理已通过本地`./scripts/check.sh`且没有committed build artifact。没有GitHub
Release/Package、签名或正式release声明。核心抽象候选已经
正式晋升为[核心抽象、叙事与不变量证明链](project/design/core-abstractions-and-narratives.md)，
此前Temporary replacement closure已完成；[32位结构域与即时增量Key/Index维护](project/conformance/v1-incremental-structural-mutation-governance.md)
已正式晋升并`PASS`：Table-local结构域统一为checked `int`，累计域保持checked
`long`，Key/Index只保留singleton-inline / ordered `int[]`一套membership truth，point
mutation即时局部维护；Selection mutation以columnar write set、dense move plan与final-locator
sidecar projection闭合普通PLAIN路径，encoded/indexed fallback保留candidate。
[Scheduling性能治理](project/conformance/v1-scheduling-performance-governance.md)已正式晋升并
`PASS`：标准100K FJSP纯dispatch由约18.7秒降至fresh约0.6秒、warm约0.51秒，且通用runtime
优化已由正式Design承接。
[Canonical Logical IR与执行引擎M1治理](project/conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)
已完成正式Owner晋升、Baseline Freeze与targeted readiness，状态为
`IMPLEMENTATION_FULFILLED / S1-S6_COMPLETED`；[最终S6资格](project/conformance/canonical-ir-execution-s6-final-qualification.md)
已经关闭replacement与性能防退化证据；正式
[S1-S6计划](project/engineering/canonical-ir-execution-engine-implementation-plan.md)保持冻结基线，实际active
状态由Conformance拥有。
[Vectorized Physical Pipeline第一阶段](project/conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)
已正式晋升并`PASS`：当前正式能力只包含Table `count`、integral Field `sum`与ordered
`long[]` materialization三类finite primitive Chunk kernel，Physical Plan一次性拥有kernel/resource
decision，row/chunk两条parallel路径共享同一bounded caller-participating lifecycle；
[后续能力扩展治理](project/conformance/v1-vectorized-physical-pipeline-expansion-governance.md)也已正式晋升并
`PASS`：VP1 encoded-native integral、VP2 ordered `long[]` representation-native/parallel与VP3全局资格、
正式Owner晋升和Temporary replacement closure均已完成，当前没有active implementation slice；
[SOMA Physical Execution Engine M2正式晋升与实施准入](project/conformance/v1-physical-execution-engine-m2-promotion-readiness.md)
已经`PASS`，P1-P6正式计划由
[Engineering Plan](project/engineering/physical-execution-engine-m2-implementation-plan.md)拥有；Product Owner
已于2026-08-12授权完整实施；[P1资格](project/conformance/physical-execution-engine-m2-p1-qualification.md)
、[P2资格](project/conformance/physical-execution-engine-m2-p2-qualification.md)、
[P3资格](project/conformance/physical-execution-engine-m2-p3-qualification.md)与
[P4资格](project/conformance/physical-execution-engine-m2-p4-qualification.md)、
[P5资格](project/conformance/physical-execution-engine-m2-p5-qualification.md)与
[最终P6资格](project/conformance/physical-execution-engine-m2-p6-final-qualification.md)已`PASS`；P1-P6全部完成，
Candidate Temporary已退役，当前没有active implementation slice；
[Selection mutation write-set治理](project/conformance/v1-selection-mutation-write-set-governance.md)
已正式晋升并`PASS`，PLAIN update/remove不再复制全部touched-Chunk leaves；
[SOMA Grassing空间个体仿真Reference Application与性能治理](project/conformance/v1-grassing-simulation-reference-application-governance.md)
已正式晋升并`PASS`，UI/headless、正确性、Application profile优化与source/package delivery已闭合；
[SOMA Engine产品构思](project/temp/soma-engine-product-concept/README.md)仅为queued intent，不是Design或
implementation input；[Post-M2性能优化治理](project/conformance/v1-post-m2-performance-optimization-governance.md)
已`PASS / T1_T4_COMPLETED`：T1删除Index Join重复equality，T2让GroupBy直接读取typed aggregate
Field，T3完成dense remove sidecar projection，T4 construction候选未达收益门槛并撤回；路线Temporary
已退役，当前没有active performance slice；
[Production Core质量检查方法可行性探索](project/conformance/v1-production-core-quality-inspection-method-feasibility.md)
已`PASS / ROUTINE_ADOPTION_REJECTED`，没有建立稳定脚本、CI或质量门。
千万行组合负载治理由
[正式Conformance记录](project/conformance/v1-ten-million-composed-workload-governance.md)拥有；交付导向仓库治理由
[正式Conformance记录](project/conformance/v1-delivery-centered-repository-governance.md)拥有；
[性能与正确性联合治理](project/conformance/v1-performance-correctness-governance.md)已`PASS`，
且[四维性能架构治理](project/conformance/v1-four-dimensional-performance-architecture-governance.md)已
`PASS`；[Operator × Type × Distribution 性能资格](project/conformance/v1-operator-type-distribution-performance-qualification.md)
也已`PASS`，cost-aware RLE、Bound cardinality 与 Field materialization 已闭合；
[内存归因与低分配执行治理](project/conformance/v1-memory-attribution-low-allocation-governance.md)已
`PASS`，retained、temporary reservation、Java allocation、heap/RSS 分层观测与正常路径低分配优化已闭合；
[全面性能前沿资格](project/conformance/v1-performance-frontier-qualification.md)已`PASS`，Table、Field、
IndexSelection与主要派生operation完成10K/1M/10M fixed-host资格及三轮profile-driven优化；`benchmarks/`现为
长期nonproduction性能与场景正确性证据；[Scheduling reference application治理](project/conformance/v1-scheduling-reference-application-governance.md)
已`PASS`，标准100K FJSP通过四张runtime Table与自然waiting point `add/remove`完成，fixed slot和
application frontier workaround已退出。`docs/`仍只保留placeholder。
[大范围优化后全局完整性与回归审查](project/conformance/v1-post-governance-global-integrity-regression-review.md)
已`PASS`：跨operation failure provenance、point Index allocation admission与正式Owner状态漂移已闭合，
未准入application frontier或ordered access path。

开始工作前必须读取：

- [项目状态](project/README.md)；
- [SOMA Java V1 产品蓝图](project/blueprint/README.md)；
- [Design 总览](project/design/README.md)；
- [V1 Implementation Plan](project/engineering/v1-implementation-plan.md)；
- [Conformance 与证据边界](project/conformance/README.md)。

随后只读取与任务直接相关的 Design Owner；涉及 typed IR、optimizer、Join/Group physical
planning 或 reference differential 时读取
[规划与优化 Design](project/design/planning-and-optimization.md)。不要把全部项目事实加载成
每次任务的默认前置。

涉及Canonical IR/Execution后续修改时，还必须读取
[S1-S6正式计划](project/engineering/canonical-ir-execution-engine-implementation-plan.md)与
[晋升/准入记录](project/conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)以及
[最终资格](project/conformance/canonical-ir-execution-s6-final-qualification.md)；不得把已完成的S1-S6重新
解释为active migration或静默恢复旧adapter。

涉及Vectorized Physical Pipeline、representation-aware kernel或morsel-driven execution时，必须读取
[第一阶段正式晋升记录](project/conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)、
[VP1资格](project/conformance/vectorized-pipeline-expansion-vp1-qualification.md)、
[VP2资格](project/conformance/vectorized-pipeline-expansion-vp2-qualification.md)与
[扩展正式治理记录](project/conformance/v1-vectorized-physical-pipeline-expansion-governance.md)。VP1-VP3已
完成，不得重新解释为active migration；任何能力矩阵扩张仍须建立新的bounded Temporary与授权。

涉及Physical Pipeline、Segment、Breaker、Kernel、ExecutionFrame或Morsel全局架构时，还必须读取
[M2正式计划](project/engineering/physical-execution-engine-m2-implementation-plan.md)与
[正式晋升/准入记录](project/conformance/v1-physical-execution-engine-m2-promotion-readiness.md)；Candidate
Temporary只用于provenance，不覆盖正式Owner或current implementation status。

实施slice或跨Design审查还必须读取
[核心抽象与叙事Design](project/design/core-abstractions-and-narratives.md)中与该slice对应的A/N/INV
章节；不要求为局部文档任务默认加载全文。

## 固定身份与边界

- 产品品牌为 SOMA（Scheme-Oriented Memory Architecture）；
- repository 为 `somaruntime/soma-java`；
- copyright owner、publishing identity 和 maintainer 为 ArthurFeng；
- Java package / Maven group baseline 为 `io.github.somaruntime.soma`；
- 当前语言方向为 Java 8；
- License 为 Apache License 2.0；
- 品牌资产和权利边界由 `assets/` 与 `NOTICE` 拥有。

这些身份事实不预先证明 artifact、version、dependency、runtime algorithm 或 release profile。

## Clean-slate 约束

- predecessor 只存在于 Git ref `archive/pre-product-reset-2026-07-31`；
- 不复制、cherry-pick、包装或恢复 predecessor source；
- 不保留 legacy 目录、compatibility layer、双 API、migration adapter 或 `v2` 平行 module；
- 不因为旧 type/test/benchmark 存在就要求新 Design 兼容；
- 需要历史经验时，只提取问题、约束和 evidence，并从当前 Design 重新推导实现；
- predecessor public `DataFlow`、`Transformation`、`Candidate` model、public `Batch`、public physical `Column`、
  `Segment` 和 manual `release()` 不属于 V1；
- ChildTable、`@SomaChild`、ownership graph、per-owner Table、implicit cascade 和
  cross-Table transaction 不属于 V1；1:M/N:M 使用普通 Table、endpoint ID、Index；
- V1 `@SomaTable` 不接受 `name` identity；package-private `.schema` declaration type 决定父
  package generated object、`XxxTable` 和 `xxxTable()`；
- application API 不泄漏 schema declaration type；
- formal production topology 为 exactly `soma-runtime` + `soma-processor`；不得增加第三production
  artifact，也不得为未来 capability 预建 placeholder。

## 正式事实与文档

- [Blueprint](project/blueprint/README.md)拥有产品意图、边界和成功标准；
- [Design](project/design/README.md)按关注点拥有长期规范性合同；
- [核心抽象与叙事](project/design/core-abstractions-and-narratives.md)拥有跨Design skeleton、主叙事、
  proof-chain routing与M0-M2变更协议，不覆盖精确Design；
- code/build-support/tests 在出现后拥有当前 executable fact；
- [Conformance](project/conformance/README.md)记录 implementation gap 与 evidence；
- root/project README 是入口，不复制 Design；
- Manual、White Paper、Examples 在出现后是角色投影，不是 parallel Design；
- `project/temp/`当前没有active bounded topic；Production Core质量方法探索已由
  [正式Conformance记录](project/conformance/v1-production-core-quality-inspection-method-feasibility.md)完成
  Q0-Q2、采用裁决与Temporary closure；Physical Execution Engine M2已由
  [最终P6资格](project/conformance/physical-execution-engine-m2-p6-final-qualification.md)完成P1-P6与
  Temporary replacement closure；Vectorized Physical Pipeline扩展已经由
  [正式Conformance记录](project/conformance/v1-vectorized-physical-pipeline-expansion-governance.md)接管并完成
  Temporary replacement closure；
  Grassing治理由
  [正式Conformance记录](project/conformance/v1-grassing-simulation-reference-application-governance.md)拥有，
  Candidate Temporary已完成replacement closure；
  [SOMA Engine产品构思](project/temp/soma-engine-product-concept/README.md)是明确标记的queued intent，
  不得作为当前Design或implementation input；
  [Post-M2性能优化治理](project/conformance/v1-post-m2-performance-optimization-governance.md)已完成
  T1–T4、资格与Temporary replacement closure；未授权T5–T7没有形成current backlog或Design；
  新的重大长期变化先进入bounded Temporary，经Product Owner裁决和验证后晋升，再删除Temporary；
  不得直接在正式Design中掩盖未裁决变化；
- 历史 Conformance 只保存 provenance，不能覆盖 current Blueprint/Design/readiness。

默认使用中文编写 Design、Report 和代码注释。

## Surface admission

新增 module、production type、public/generated API、dependency、test taxonomy、benchmark、
script、workflow、Process 或正式文档前，必须说明：

1. 独立 capability 与 consumer；
2. Owner、lifecycle 和 failure boundary；
3. 为什么当前 surface 不能承载；
4. 对应的 Blueprint requirement 与 Design Owner；
5. 需要什么 evidence 才能成立。

不要为未来可能性预建空目录、interface、abstraction、module 或 compatibility layer。
Implementation proposal 若需要改变 Blueprint/Design 产品语义，必须停下并请求 Product Owner
裁决。

I0已固定JUnit Jupiter `5.11.4`作为唯一third-party test framework，并证明Java 8
compatibility、license、2026-08-04时点known-vulnerability、dependency tree与无production
artifact leakage。JUnit/JUnit Platform只允许test scope，不准入Vintage；该裁决不授权其他
dependency或plugin expansion。

## 实施准入与推进

- 当前 implementation authorization 已由I0-I8、Canonical IR/Execution S1-S6与Physical
  Execution Engine M2 P1-P6完成履行；当前没有active implementation slice；
- Canonical IR/Execution S1-S6的授权由
  [正式Conformance记录](project/conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)拥有；
  冻结Engineering Plan中的准入快照不作为current authorization状态源；
- 当前授权覆盖repository-local CI与non-publishing release qualification workflow、local/internal
  benchmark与profile、local Maven package qualification，以及上述JUnit test-only stack；精确
  边界由[Conformance authorization contract](project/conformance/README.md#6-implementation-authorization-contract)
  拥有；
- 每个 slice 按 [Implementation Plan](project/engineering/v1-implementation-plan.md)交付
  positive、negative、failed-state、独立审查与 Conformance evidence；
- Canonical IR/Execution后续变更以其独立[S1-S6计划](project/engineering/canonical-ir-execution-engine-implementation-plan.md)
  和最终Conformance为baseline，不把I0-I8 chronology或历史授权直接套用；
- 使用 [G1-G10](project/conformance/v1-implementation-gates.md)更新 current executable fact；
- reference interpreter 先成为 correctness oracle，再准入 optimizer/parallel；
- stop rule 触发时建立 bounded Temporary，不在 code 中静默缩小 scope 或堆叠补丁；
- I0-I8 exit已通过；G9已由Product Owner批准，G10远端workflow evidence已闭合。
- 默认由主Agent沿单一路径直接交付；只有Gate确实需要独立证据时才使用一个bounded只读subagent，
  不以多轮subagent审查替代实施，也不得让多个Agent并行修改同一核心surface；
- Blueprint/Design 语义变化、权限扩张、已准入test stack以外的新dependency、第三production
  artifact、证明链无法闭合或性能与正确性取舍必须停止并等待 Product Owner；
- 每个 slice 只有在 exit evidence、独立审查、Conformance 更新和干净提交完成后才可进入下一项。

## 当前阶段验证

当前是post-V1 implementation qualification阶段。文档与repository-surface变更至少执行：

- 使用 `rg` / `rg --files` 搜索；
- 使用 `apply_patch` 编辑；
- 运行 `git diff --check`；
- 检查 Markdown 相对链接与 current route；
- 检查每项事实的唯一 Owner 和 Blueprint↔Design↔Engineering↔Conformance traceability；
- 确认active checkout没有predecessor code、legacy API、committed build artifact或release claim；
- 记录但不外推本机环境事实。

稳定命令入口为`./scripts/check.sh`、`./scripts/qualify.sh`、`./scripts/benchmark.sh`与
`./scripts/package-local.sh`；能力级qualification和fixtures分别位于`build-support/qualification/`
与`tests/`。

P2 feasibility spike 已退役；只有当前
[正式晋升记录](project/conformance/large-scale-engine-formal-promotion.md)明确重新采纳的结论可
作为 bounded input。不得把 historical evidence 当成可运行 production test、完整 runtime、
performance 或 release evidence。

## Git

- 长期分支只使用 `main`、`develop`、`release`；
- 常规工作在 `develop`；
- 保留用户现有修改；
- 当前授权允许在每个 slice 证据闭合后提交并推送`develop`；
- 当前授权不包含remote artifact publication、签名、正式发布声明或其他分支发布动作；
- destructive 操作必须先解析精确、可恢复 target；
- 不使用 destructive reset/checkout 覆盖用户 worktree。
