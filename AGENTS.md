# soma_java Agent Guide

## 当前状态

SOMA Java 已从 clean-slate 起点建立当前正式V1 Blueprint、九个分责Design Owner、
I0-I8 Engineering Plan 与 G1-G10 Conformance Gates。Product Owner 已确认当前 V1 North
Star 为“一亿行以上、编译式、支持关系计算的单进程 Table 引擎”；一亿行以上是架构愿景，
第一阶段以百万行数据的高效、低分配、资源受控操作建立资格证据。

2026-08-03的[实施前最终全局一致性审核](project/conformance/v1-final-pre-implementation-global-consistency-review.md)
为`PASS`，Design/Plan为`READY_FOR_IMPLEMENTATION`。Product Owner 已于 2026-08-03 明确授予
完整 V1 implementation authorization。I0-I8已完成implementation与qualification；I0-I7分别通过：
[I0资格](project/conformance/i0-build-spine-qualification.md)、
[I1资格](project/conformance/i1-primitive-keyed-table-qualification.md)、
[I2资格](project/conformance/i2-schema-type-storage-breadth-qualification.md)、
[I3资格](project/conformance/i3-query-ir-reference-qualification.md)、
[I4资格](project/conformance/i4-selection-mutation-resource-qualification.md)、
[I5资格](project/conformance/i5-group-relation-qualification.md)、
[I6资格](project/conformance/i6-parallel-execution-qualification.md)、
[I7资格](project/conformance/i7-compression-metadata-qualification.md)，I8通过
[产品资格与G9 Owner sign-off](project/conformance/i8-product-qualification.md)：G1-G10为`PASS`；
当前没有active implementation slice。GitHub Release/Package、签名和正式release声明未授权。

Active checkout已包含I8三个reference application、million-row profile、package/SBOM/provenance、CI与
non-publishing release qualification workflow，以及I7 AUTO/OFF compression、PLAIN/encoded/overlay representation、四级typed metadata
与safe explain，以及I6 generated parallel surface、application-owned/common `ForkJoinPool`与bounded
caller-participating scheduler、I5 GroupBy与binary Equality/Cross Join、I4 Selection mutation、I3 typed
IR和I2 schema/type/Key/Index。I8记录拥有对应remote workflow历史证据；当前delivery-centered
repository治理已通过本地`./scripts/check.sh`且没有committed build artifact。没有GitHub
Release/Package、签名或正式release声明。核心抽象候选已经
正式晋升为[核心抽象、叙事与不变量证明链](project/design/core-abstractions-and-narratives.md)，
此前 Temporary replacement closure已完成；当前没有 active bounded topic。千万行组合负载治理由
[正式Conformance记录](project/conformance/v1-ten-million-composed-workload-governance.md)拥有；交付导向仓库治理由
[正式Conformance记录](project/conformance/v1-delivery-centered-repository-governance.md)拥有；
[性能与正确性联合治理](project/conformance/v1-performance-correctness-governance.md)已`PASS`，
且[四维性能架构治理](project/conformance/v1-four-dimensional-performance-architecture-governance.md)已
`PASS`；[Operator × Type × Distribution 性能资格](project/conformance/v1-operator-type-distribution-performance-qualification.md)
也已`PASS`，cost-aware RLE、Bound cardinality 与 Field materialization 已闭合；`benchmarks/`现为
长期nonproduction性能与场景正确性证据，`docs/`仍只保留placeholder。

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

实施slice或跨Design审查还必须读取
[核心抽象与叙事Design](project/design/core-abstractions-and-narratives.md)中与该slice对应的A/N/INV
章节；不要求为局部文档任务默认加载全文。

## 固定身份与边界

- 产品品牌为 SOMA（State-Oriented Memory Architecture）；
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
- `project/temp/`当前没有active topic；新的重大长期变化先进入bounded Temporary，经Product Owner裁决和验证后
  晋升，再删除Temporary；不得直接在正式Design中掩盖未裁决变化；
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

- 当前 implementation authorization 覆盖 I0-I8 的自主实现；I0-I8 slice与G1-G10均已关闭，
  当前没有active implementation slice；
- 当前授权覆盖repository-local CI与non-publishing release qualification workflow、local/internal
  benchmark与profile、local Maven package qualification，以及上述JUnit test-only stack；精确
  边界由[Conformance authorization contract](project/conformance/README.md#6-implementation-authorization-contract)
  拥有；
- 每个 slice 按 [Implementation Plan](project/engineering/v1-implementation-plan.md)交付
  positive、negative、failed-state、独立审查与 Conformance evidence；
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
