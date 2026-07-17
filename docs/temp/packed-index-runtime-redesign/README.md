# Packed Index / Exact Access / IndexBuffer 重设计专题

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无；本专题不授权修改正式 Owner、annotation、generated API、runtime 或 consumer
适用范围：SOMA Java V1 packed columnar table、primary/secondary exact access、Row Pipeline、mutation、runtime plan/stats 与迁移证据
最后审查日期：2026-07-17

## 1. 专题定位

这是一次跨 annotation、processor、generated API、runtime-core、examples、testkit、benchmarks 和 Gate 的重大重设计。当前正式契约仍要求 SparseInt/Hash `KeySpace`、index/unique/order sidecar、dirty/lazy rebuild 和既有 public plan/stats surface；在本专题完成独立审查并迁入唯一 Owner 前，这些正式契约继续有效。

本专题只回答：

- 为什么要改变；
- 新模型应当具有什么语义；
- runtime 应采用什么机械结构；
- mutation、failure atomicity、lifecycle 和 ordering 如何闭合；
- 哪些 public/schema/protocol surface 会受影响；
- 应按什么阶段迁移和验证。

本专题不修改代码，不以草案替代正式设计，也不处理对外发布工作。

## 2. 文档职责

| 文档 | 单一职责 |
|---|---|
| [治理章程与决策台账](governance-charter.md) | 意图、目标、范围、术语、已确认决策、推导结论、待审项、Capability 与 Owner 边界 |
| [Schema、API 与顺序语义](schema-api-semantics.md) | keyed/dense、annotation、exact lookup、Row Pipeline source/terminal、materialization 和显式排序语义 |
| [Runtime storage、exact access 与 IndexBuffer](runtime-storage-and-access.md) | packed SoA、PrimaryLocator、UniqueLocator、GroupedExactIndex、AccessPath、pipeline plan 和 scratch 结构 |
| [Mutation、swap-remove 与失败原子性](mutation-and-atomicity.md) | append/update/remove/replaceAll、locator/index 增量维护、child handle、epoch 和 commit protocol |
| [RuntimePlan、stats 与兼容性](plan-stats-and-compatibility.md) | 旧 sidecar/sparse 维度退出、新 plan/stats 观测、public/protocol migration 和未决 API 选择 |
| [迁移阶段与验证计划](migration-and-validation.md) | Owner 迁移矩阵、实施阶段、禁止捷径、evidence、Gate 出口和回滚边界 |

## 3. 一页摘要

建议目标模型是：

```text
@SomaTable columns + presence + packed Index [0, size)
  + @SomaKey      -> PrimaryLocator       -> 0/1 current Index
  + @SomaUnique   -> UniqueLocator        -> 0/1 current Index
  + @SomaIndex    -> GroupedExactIndex    -> 0..K current Index sequence
  + default scan  -> [0, size) current physical sequence
  -> fused filter / skip / limit
  -> explicit dynamic sorted when business order is required
  -> terminal
```

核心变化：

- 保留 `@SomaKey`，它是 primary unique identity，不等同于 secondary unique；
- 保留 `@SomaUnique` 和 `@SomaIndex`，只支持 exact access；
- 不支持 range lookup；范围条件使用列式 scan/filter；
- 删除 Sparse Set/Entity 映射方向，key 直接经 primitive/generated hash locator 定位当前 Index；
- 删除 `@SomaOrder` maintained order；策略顺序使用 `.sorted(...)`，priority/event queue 使用 application-owned heap 等专用结构；
- keyed table、dense table 和 `@SomaIndex` 都不承诺物理或业务遍历顺序；
- keyed/dense 删除都采用 swap-remove/tail-fill，稳定状态始终 packed `[0,size)`；
- exact access 在 mutation boundary eager/incremental 维护，不允许 dirty 后在下一次读取时全表 rebuild；
- `IndexBuffer` 是 table-local、terminal-scoped、primitive、可复用的内部执行材料，不是每个 intermediate stage 的 L1/L2/L3 副本；
- `sorted(...).firstOrThrow()` 可以使用稳定 arg-min，只扫描通过前置阶段的候选，不必完成全量排序；
- 未显式排序的 `firstOrThrow()`、`limit(n)`、`fetchAll()` 只服从本次 source sequence。default source 是当前物理 Index 顺序；non-unique index source 的 group 枚举顺序不构成业务顺序。

## 4. 审查顺序

建议按以下顺序审查：

1. 先审 [治理章程与决策台账](governance-charter.md)，确认哪些是已决定事实、哪些只是建议；
2. 再审 [Schema、API 与顺序语义](schema-api-semantics.md)，尤其是未排序 terminal、dense child `List` 和 raw index 的后果；
3. 审查 [Runtime storage、exact access 与 IndexBuffer](runtime-storage-and-access.md) 与 [Mutation、swap-remove 与失败原子性](mutation-and-atomicity.md) 是否闭合；
4. 对 [RuntimePlan、stats 与兼容性](plan-stats-and-compatibility.md) 中列出的 public 待审项作明确选择；
5. 最后批准或修改 [迁移阶段与验证计划](migration-and-validation.md)。

只有上述审查完成后，下一轮才能先迁移正式 Owner，再开始实现。不能从本专题直接跳到代码。

## 5. 当前实现基线说明

本专题以当前仓库的实际实现为迁移基线：

- generated Row Pipeline 已有 primitive selection scratch 与 `sorted(...).firstOrThrow()` stable arg-min；
- selector 仍由 `RowPermutationSidecar` 在 dirty 后按全表 permutation rebuild/sort；
- multi-remove 当前使用 stable forward compaction；
- `TablePlan` 仍公开 sparse/sidecar policy 与 scratch dimensions；
- `TableStats`、`UpdateResult`、`RemoveResult` 仍公开 sidecar rebuild/maintenance 语义。

这些事实只用于定义迁移起点，不表示本专题已改变它们。
