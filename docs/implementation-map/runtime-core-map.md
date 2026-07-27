# Runtime Core 地图

类型：Implementation Map

状态：正式

Owner：SOMA runtime-core 实现导航

对应 Design：[Table、存储与访问](../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../design/correctness-and-failure.md)

事实范围：当前 handwritten runtime、generated-runtime protocol、hot path 和核心验证入口

最近实现核对基线：`3c8d425`

最后审查日期：2026-07-28

## 1. Handwritten public/runtime types

| 关注点 | 当前入口 |
|---|---|
| runtime plan | [`RuntimePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RuntimePlan.java)、[`TablePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TablePlan.java)、[`ChildPlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/ChildPlan.java)；schema-seeded、one-shot builder/editor，`planningRows` 为 hint、`maximumRows` 为 hard boundary |
| Effective Metadata | [`EffectiveMetadataProjection.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/EffectiveMetadataProjection.java) 与 `metadata/SomaEffectiveMetadata`、`SomaTableEffectiveMetadata`；只读投影 closed physical identity |
| String resource profile | [`StringResourceProfile.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/StringResourceProfile.java)、`StringResourceProfileStatus`、`StringResourceRole`；区分 `UNPROFILED` 与 caller-declared `PROFILED_UNVERIFIED` |
| structured failure | [`SomaRuntimeException.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaRuntimeException.java)、[`SomaErrorCategory.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaErrorCategory.java) |
| diagnostics/results | [`TableStats.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TableStats.java)、[`UpdateResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/UpdateResult.java)、[`RemoveResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RemoveResult.java) |
| public index snapshot | [`IndexSnapshot.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshot.java)、[`IndexSnapshots.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshots.java)；empty shared、single-index inline、multi-index detached array |
| column access | [`AbstractColumnView.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnView.java)、[`AbstractColumnTraversal.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnTraversal.java) 及 typed subclasses |
| materialization budget | [`MaterializationBudget.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/MaterializationBudget.java) |
| Descriptor Metadata | [`com.hgtech.soma.runtime.metadata`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/metadata) 中的 `SomaMetadata`、Schema/Table/Column/Type/Key/Unique/Index/Ownership immutable read model |

## 2. Generated-runtime protocol

Protocol 位于 [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated)，当前主要组合为：

- state/columns：`DenseTableState`、`ColumnGroup`、primitive typed columns、
  concrete `StringColumn`、`PresenceBitmap`；
- primary locator：`IntKeySpace`、`HashIntKeySpace`、`HashLongKeySpace`、`HashCompositeKeySpace`；
- secondary exact access：`GroupedExactIndex`，row-link与group capacity独立；
- bulk exact preflight：`ExactGroupCounter`，按selector distinct-group cardinality做primitive计数；
- operation scratch：`IndexBuffer`；
- Candidate plan/evaluation：`GeneratedScanPlan`、`GeneratedScanEvaluation`；typed source 与 executor 由 generator 提供；
- ownership/trust：`ChildOwnershipRegistry`、`OwnedChildTable`；共享 registry
  同时拥有 root/child aggregate 的 first-failure fault state；
- materialization：`MaterializationTracker`、`MaterializationAllocation`；
- compatibility/failure：`GeneratedMetadata`、`RuntimeCompatibility`、`RuntimeFailures`；
- generated Plan bridge：`GeneratedRuntimePlan` + unforgeable
  `GeneratedPlanToken`；供 processor output绑定 raw plan construction，application
  authoring入口是 generated `SchemaMetadata.newPlan()`；
- DataFlow lifecycle bridge：aggregate instance identity、canonical acquire/release guard 和 generated candidate/effect access；
- resource accounting：`StorageBudget`。

这些 public Java types 是 generator binding protocol，不是 application SPI。Generated facade 的 public signature 不应泄漏它们。

## 3. 当前 hot path trace

```text
generated Table/Scan method
  -> DenseTableState begin/preflight
  -> ChildOwnershipRegistry aggregate trust preflight
  -> Packed direct path or exact group typed source binding
  -> compact stage plan + generated fused/barrier terminal executor
  -> IndexBuffer/sort/update scratch only when operation shape requires
  -> column / locator / GroupedExactIndex delta
  -> DenseTableState success/failure stats and epoch commit
```

`DenseTableState` 将 internal code 与 raw unexpected failure 路由到共享 aggregate
fault；`ColumnGroup`、`StorageBudget` 和 ownership registry 在各自 invariant
事实产生处标记。Faulted normal access 由既有 preflight 拒绝，runtime plan、
released state、stats snapshot 与 root release 复用既有 operation 名称和 v6
protocol，不新增 public fault surface。

Keyed delete 先从 KeySpace 移除目标 key，再对 tail-fill survivor 修复 current Index。Exact index 通过 group/link 增量维护；append/replace按实际distinct groups预检和分配。Candidate Scan source在terminal-time读取current group；source-only exact count可直接读取cardinality并保持logical stats。当前 generated/runtime compatibility 为 v6；runtime-core 只提供窄 DataFlow guard/access bridge，Definition/Template/Invocation 不进入本模块。协议已经没有 generic `ObjectColumn`、`SparseIntKeySpace` 或 `RowPermutationSidecar`；`KeySpace`仅是primary-locator兼容性术语。

Runtime plan protocol 为 v4。Application 不能调用 raw builder、`addTable`、
`replaceTable` 或写入 free-form physical strategy；generated companion先播种全部
schema Table，再允许按 `SomaTableMetadata` 编辑。Build 后 root builder 与全部
child editor失效。`DenseTableState` 在 reserve/append/replace preflight 统一执行
hard `maximumRows`，失败使用 stable `row_limit_exceeded` 且不修改 rows/epoch。
`planningRows` 不参与 admission。Effective Metadata 是 plan-owned cold snapshot，
execution hot path不解释 Descriptor/Metadata。

## 4. 核心检查

- handwritten kernel：[`RuntimeCorePhase1Check.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/RuntimeCorePhase1Check.java)；
- key space：[`KeySpacePhase2Check.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/KeySpacePhase2Check.java)；
- generated runtime scripts：[`check-generated-dense-phase1.sh`](../../scripts/check-generated-dense-phase1.sh)、[`check-generated-keyed-phase2.sh`](../../scripts/check-generated-keyed-phase2.sh)、[`check-access-phase3.sh`](../../scripts/check-access-phase3.sh)、[`check-child-phase4.sh`](../../scripts/check-child-phase4.sh)；
- diagnostics：[`check-table-diagnostics-phase1.sh`](../../scripts/check-table-diagnostics-phase1.sh)；
- floating storage/access：[`check-floating-value-storage.sh`](../../scripts/check-floating-value-storage.sh)。
- Candidate allocation/code size：[`check-post-cutover-components.sh`](../../scripts/check-post-cutover-components.sh)、[`check-scan-code-size.sh`](../../scripts/check-scan-code-size.sh)。

修改 protocol method、storage invariant 或 generated binding 时，需要同时核对 runtime-core 与 processor-generated consumer，单边测试不足以证明 conformance。
