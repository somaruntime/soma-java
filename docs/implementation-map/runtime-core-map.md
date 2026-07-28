# Runtime Core 地图

类型：Implementation Map

状态：正式

Owner：SOMA runtime-core 实现导航

对应 Design：[Table、存储与访问](../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../design/correctness-and-failure.md)

事实范围：当前 handwritten runtime、generated-runtime protocol、hot path 和核心验证入口

最近实现核对基线：2026-07-28 runtime-scale working-tree candidate（base
`6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`）

最后审查日期：2026-07-28

## 1. Handwritten public/runtime types

| 关注点 | 当前入口 |
|---|---|
| runtime plan | [`RuntimePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RuntimePlan.java)、[`TablePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TablePlan.java)、[`ChildPlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/ChildPlan.java)；schema-seeded、one-shot builder/editor，`planningRows` 为 hint、`maximumRows` 为 hard boundary，`SomaWorkloadProfile` 与 generated structural row width 进入 versioned layout formula |
| Group composition | [`SomaGroup.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaGroup.java)、[`SomaGroupPlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaGroupPlan.java)、`SomaGroupMemberPlan`；stable frozen slots、multi-schema/multi-instance attach、独立 Group/member lifecycle |
| Effective Metadata | [`EffectiveMetadataProjection.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/EffectiveMetadataProjection.java) 与 `metadata/SomaEffectiveMetadata`、`SomaTableEffectiveMetadata`；只读投影 closed physical identity、workload、formula、row width 与 head/segment rows |
| String resource profile | [`StringResourceProfile.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/StringResourceProfile.java)、`StringResourceProfileStatus`、`StringResourceRole`；区分 `UNPROFILED` 与 caller-declared `PROFILED_UNVERIFIED` |
| structured failure | [`SomaRuntimeException.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaRuntimeException.java)、[`SomaErrorCategory.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaErrorCategory.java) |
| diagnostics/results | [`TableStats.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TableStats.java)、[`UpdateResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/UpdateResult.java)、[`RemoveResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RemoveResult.java) |
| public index snapshot | [`IndexSnapshot.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshot.java)、[`IndexSnapshots.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshots.java)；empty shared、single-index inline、multi-index detached array |
| column access | [`AbstractColumnView.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnView.java)、[`AbstractColumnTraversal.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnTraversal.java) 及 typed subclasses |
| materialization budget | [`MaterializationBudget.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/MaterializationBudget.java) |
| Metadata hierarchy | [`com.hgtech.soma.runtime.metadata`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/metadata) 中的 `SomaMetadata`、Schema/Table/Column/Type/Key/Unique/Index/Ownership、`SomaEffectiveMetadata`、`SomaTableRuntimeMetadata`、`SomaSegmentMetadata`、exact/Unique/Index runtime metadata及 `SomaGroupMetadata`/`SomaGroupMemberMetadata` immutable read model |
| physical formulas | [`DeltaStagingFormula.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/DeltaStagingFormula.java)、[`PrimaryLocatorLayoutFormula.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/PrimaryLocatorLayoutFormula.java) 与 storage layout formula；只在freeze/binding boundary选择closed plan |

## 2. Generated-runtime protocol

Protocol 位于 [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated)，当前主要组合为：

- state/columns：`DenseTableState`、`ColumnGroup`、primitive typed columns、
  concrete `StringColumn`、`PresenceBitmap`；Small/Medium/point-heavy 可绑定
  contiguous `FLAT`，Large scan/growth 可绑定 flat-head + fixed-tail Segments；
  同组 columns/bitmap/owner token/child handle 先完整 stage 再发布 capacity；
- primary locator：`IntKeySpace`、`HashIntKeySpace`、`HashLongKeySpace`、
  `HashCompositeKeySpace`；当前只绑定`FLAT_COMPACT`，并发布current retained、
  lifetime high-water、capacity/probe/growth observation；
- secondary exact access：`GroupedExactIndex`，row-link与group capacity独立；
- bulk exact preflight：`ExactGroupCounter`，按selector distinct-group cardinality做primitive计数；
- operation scratch：`IndexBuffer`；
- Candidate plan/evaluation：`GeneratedScanPlan`、`GeneratedScanEvaluation`；typed source 与 executor 由 generator 提供；
- ownership/trust：`ChildOwnershipRegistry`、`OwnedChildTable` 继续拥有每个
  root/child aggregate 的 first-failure fault state；`GroupMembership`、
  `GeneratedRootFactory`、`GeneratedSomaGroup` 只组合独立 root，不合并 aggregate
  trust；
- resource ownership：`GroupLedger` 是 structural/transient hard owner，
  `TableLedger` 只保留 member attribution；`StorageBudget` 已退出协议；
- materialization：`MaterializationTracker`、`MaterializationAllocation`；
- compatibility/failure：`GeneratedMetadata`、`RuntimeCompatibility`、`RuntimeFailures`；
- runtime observation：`GeneratedRuntimeMetadata`在safe operation boundary组合
  Table/Segment/locator/exact/Unique/Index的detached immutable snapshot；
- generated Plan bridge：`GeneratedRuntimePlan` + unforgeable
  `GeneratedPlanToken`；供 processor output绑定 raw plan construction，application
  authoring入口是 generated `SchemaMetadata.newPlan()`；
- DataFlow lifecycle bridge：aggregate instance identity、canonical acquire/release guard 和 generated candidate/effect access；
- Group lifecycle bridge：atomic attach、stable member slot、implicit single-root
  Group、group/member dataVersion 与 membership epoch、all-member release preflight
  和 reverse release。

这些 public Java types 是 generator binding protocol，不是 application SPI。Generated facade 的 public signature 不应泄漏它们。

## 3. 当前 hot path trace

```text
generated Table/Scan method
  -> DenseTableState begin/preflight
  -> GroupMembership + ChildOwnershipRegistry lifecycle/trust preflight
  -> GroupLedger lease / TableLedger attribution
  -> Packed direct path or exact group typed source binding
  -> compact stage plan + generated fused/barrier terminal executor
  -> IndexBuffer/sort/update scratch only when operation shape requires
  -> column / locator / GroupedExactIndex delta
  -> DenseTableState success/failure stats and epoch commit
```

`DenseTableState` 将 internal code 与 raw unexpected failure 路由到 root aggregate
fault；`ColumnGroup`、`GroupLedger` 和 ownership registry 在各自 invariant
事实产生处标记。Root fault使显式 Group进入 `DEGRADED`；Group cleanup/invariant
failure进入 `FAULTED`，并允许 bounded release retry。Faulted normal access由
既有preflight拒绝，runtime plan、released state、stats snapshot与root/group
release复用既有operation名称和v11 protocol。

Keyed delete 先从 KeySpace 移除目标 key，再对 tail-fill survivor 修复 current Index。
Exact index 通过 group/link 增量维护；append/replace按实际distinct groups预检和
分配。Candidate Scan source在terminal-time读取current group；source-only exact
count可直接读取cardinality并保持logical stats。Packed callback scan按当前物理
Segment使用外层loop，point access保持stable shift/mask mapping。当前
generated/runtime compatibility为v11；runtime-core只提供窄DataFlow
guard/access/delivery binding，Definition/Template/Invocation不进入本模块。协议
已经没有generic `ObjectColumn`、`SparseIntKeySpace`、
`RowPermutationSidecar`或`StorageBudget`；`KeySpace`仅是primary-locator
兼容性术语。

Runtime plan protocol 为 v6，storage与primary-locator layout formula identity分别为
`soma-storage-layout-v1`和`soma-primary-locator-layout-v1`。后者只把无locator
解析为`NONE`、已支持locator解析为`FLAT_COMPACT`，不含自动segmented path。
Application不能调用 raw builder、`addTable`、
`replaceTable` 或写入 free-form physical strategy；generated companion先播种全部
schema Table，再允许按 `SomaTableMetadata` 编辑。Build 后 root builder 与全部
child editor失效。`DenseTableState` 在 reserve/append/replace preflight 统一执行
hard `maximumRows`，失败使用 stable `row_limit_exceeded` 且不修改 rows/epoch。
`planningRows` 不参与 admission，只与workload/structural row width进入cold
layout resolution。Effective Metadata 是 plan-owned cold snapshot，execution
hot path不解释 Descriptor/Metadata。

Generated `Table.runtimeMetadata()` 和 `SomaGroup.metadata()` 返回 detached
snapshot；它们可以在生命周期诊断边界读取，但不成为live mutable control plane。
Group member metadata包含对应root runtime metadata，因而应用可以从一个
composition owner观察stable slot、member/table lifecycle、rows、epoch、
Segment、locator和exact access，而无需反射generated/private storage。

Generated `Table.create(plan)` 通过一个单槽 implicit Group 建立与显式 Group相同的
ledger/lifecycle协议；`Table.attach(group, slot)` 才进入显式composition。Attach
在private construction前完成membership和resource preflight，publish-once，失败
回滚。Group release先做all-member preflight，再按slot逆序释放；显式attach的
member拒绝绕过Group单独release。DataFlow继续按root opaque identity canonical
acquire，因此same/cross Group、same-schema multi-instance、cross-schema与self
alias都不被Group拓扑限制。

## 4. 核心检查

- runtime plan/metadata、Group/ownership、storage/access 与 resource/failure：
  [`check-runtime-contracts.sh`](../../scripts/check-runtime-contracts.sh)，其共享
  case library 为
  [`RuntimeCoreContractCases.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/RuntimeCoreContractCases.java)；
- layout/publication invariant：[`StorageTestProtocol.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/generated/StorageTestProtocol.java)，覆盖全部typed column、presence、跨Segment copy/clear、stage failure rollback与release；
- primary locator：
  [`PrimaryLocatorContractCheck.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/PrimaryLocatorContractCheck.java)；
- generated runtime scripts：[`check-generated-dense-contract.sh`](../../scripts/check-generated-dense-contract.sh)、[`check-generated-keyed-contract.sh`](../../scripts/check-generated-keyed-contract.sh)、[`check-generated-access-contract.sh`](../../scripts/check-generated-access-contract.sh)、[`check-generated-ownership-contract.sh`](../../scripts/check-generated-ownership-contract.sh)；
- diagnostics：[`check-schema-diagnostics-contract.sh`](../../scripts/check-schema-diagnostics-contract.sh)；
- floating storage/access：[`check-floating-value-storage.sh`](../../scripts/check-floating-value-storage.sh)。
- Candidate allocation/code size：[`check-access-performance.sh`](../../scripts/check-access-performance.sh)、[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)。
- Group composition/ledger：runtime-core `GroupTestProtocol`，以及
  `check-generated-dense-contract.sh`、`check-generated-ownership-contract.sh`、
  `check-generated-breadth-contract.sh`、`check-dataflow-contracts.sh` 中的显式/隐式 Group、
  multi-schema/multi-instance、rollback/release 与 canonical acquire evidence。

修改 protocol method、storage invariant 或 generated binding 时，需要同时核对 runtime-core 与 processor-generated consumer，单边测试不足以证明 conformance。
