# Runtime Core 地图

类型：Implementation Map

状态：正式

Owner：SOMA runtime-core 实现导航

对应 Design：[Table、存储与访问](../design/table-storage-and-access.md)、[Ownership 与 lifecycle](../design/ownership-and-lifecycle.md)、[Correctness 与 failure](../design/correctness-and-failure.md)

事实范围：当前 handwritten runtime、generated-runtime protocol、hot path 和核心验证入口

最近实现核对基线：`b991f4c`

最后审查日期：2026-07-20

## 1. Handwritten public/runtime types

| 关注点 | 当前入口 |
|---|---|
| runtime plan | [`RuntimePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RuntimePlan.java)、[`TablePlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TablePlan.java)、[`ChildPlan.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/ChildPlan.java) |
| structured failure | [`SomaRuntimeException.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaRuntimeException.java)、[`SomaErrorCategory.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/SomaErrorCategory.java) |
| diagnostics/results | [`TableStats.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TableStats.java)、[`UpdateResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/UpdateResult.java)、[`RemoveResult.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RemoveResult.java) |
| public index snapshot | [`IndexSnapshot.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshot.java)、[`IndexSnapshots.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/IndexSnapshots.java)；empty shared、single-index inline、multi-index detached array |
| column access | [`AbstractColumnView.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnView.java)、[`AbstractColumnPipeline.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnPipeline.java) 及 typed subclasses |
| materialization budget | [`MaterializationBudget.java`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/MaterializationBudget.java) |

## 2. Generated-runtime protocol

Protocol 位于 [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated)，当前主要组合为：

- state/columns：`DenseTableState`、`ColumnGroup`、typed columns、`PresenceBitmap`；
- primary locator：`IntKeySpace`、`HashIntKeySpace`、`HashLongKeySpace`、`HashCompositeKeySpace`；
- secondary exact access：`GroupedExactIndex`，row-link与group capacity独立；
- bulk exact preflight：`ExactGroupCounter`，按selector distinct-group cardinality做primitive计数；
- operation scratch：`IndexBuffer`；
- ownership：`ChildOwnershipRegistry`、`OwnedChildTable`；
- materialization：`MaterializationTracker`、`MaterializationAllocation`；
- compatibility/failure：`GeneratedMetadata`、`RuntimeCompatibility`、`RuntimeFailures`；
- resource accounting：`StorageBudget`。

这些 public Java types 是 generator binding protocol，不是 application SPI。Generated facade 的 public signature 不应泄漏它们。

## 3. 当前 hot path trace

```text
generated table method
  -> DenseTableState begin/preflight
  -> exact group or packed scan fills/reuses IndexBuffer
  -> generated typed filter/sort/update/remove loop
  -> column / locator / GroupedExactIndex delta
  -> DenseTableState success/failure stats and epoch commit
```

Keyed delete 先从 KeySpace 移除目标 key，再对 tail-fill survivor 修复 current Index。Exact index 通过 group/link 增量维护；append/replace按实际distinct groups预检和分配。当前协议已经没有 `SparseIntKeySpace` 或 `RowPermutationSidecar`；`KeySpace`仅是primary-locator兼容性术语。

## 4. 核心检查

- handwritten kernel：[`RuntimeCorePhase1Check.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/RuntimeCorePhase1Check.java)；
- key space：[`KeySpacePhase2Check.java`](../../soma-runtime-core/src/test/java/com/hgtech/soma/runtime/KeySpacePhase2Check.java)；
- generated runtime scripts：[`check-generated-dense-phase1.sh`](../../scripts/check-generated-dense-phase1.sh)、[`check-generated-keyed-phase2.sh`](../../scripts/check-generated-keyed-phase2.sh)、[`check-access-phase3.sh`](../../scripts/check-access-phase3.sh)、[`check-child-phase4.sh`](../../scripts/check-child-phase4.sh)；
- diagnostics：[`check-table-diagnostics-phase1.sh`](../../scripts/check-table-diagnostics-phase1.sh)；
- floating storage/access：[`check-floating-value-storage.sh`](../../scripts/check-floating-value-storage.sh)。

修改 protocol method、storage invariant 或 generated binding 时，需要同时核对 runtime-core 与 processor-generated consumer，单边测试不足以证明 conformance。
