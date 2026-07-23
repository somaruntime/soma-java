# 复杂度可持续性 Stage 1 结果审查

类型：Temporary

状态：active（等待项目 Owner 审查）

Owner：SOMA Java 复杂度可持续性 Stage 1

事实范围：Stage 1 实际变更、验证证据、范围非回归与残余风险

非事实范围：长期产品语义、正式实现地图、性能 claim、public release readiness

正式事实源：否

实施授权：已完成（本文不授权正式固化或继续实施）

起点：Stage 0 immutable commit `e69eec2`

最后审查日期：2026-07-23

## 1. 审查结论

Stage 1 按批准的三个 slice 完成，没有缩减 Stage 0 的目标，也没有扩大到
public API、annotation Schema、Access Model、runtime 语义或正式文档治理。

- benchmark lane 的循环责任已经拆开；
- processor admission 与 emitter support 的逆向所有权已经修正；
- generated Scan 在既有回归 Gate 之外获得了 artifact/schema 两级诊断；
- runtime 状态机按裁决保持不动；
- 完整 `./scripts/check.sh` 通过，结果为 `project-check: ok`。

因此，本轮结果适合进入项目 Owner 审查。它尚不是正式固化结论，也不构成
Temporary 删除授权。

## 2. 实施切片与提交

| Slice | 提交 | 结果 |
|---|---|---|
| Stage 1 设计 | `6a9d8d0` | 固定职责、边界、验证与停止点 |
| A：benchmark responsibility | `242d83f` | suite 收缩为编排/兼容 facade，契约、workload、evidence、aggregation 分责 |
| B：processor ownership | `79c0a89` | selector public shape 上移至 codegen model，Table source helper 退出 Auxiliary emitter |
| C：generated footprint evidence | `8f685e2` | 保留原 Gate，增加 artifact/schema 归一化诊断 |

每个实施 slice 在独立提交时均可保留，不依赖未来重写才正确。

## 3. 实际责任变化

### 3.1 Benchmark

变更前：

```text
SmokeLaneSuite
  -> manifest + orchestration + workloads
  -> evidence + validation + aggregation

BenchmarkModel <-> SmokeLaneSuite
```

变更后：

```text
SmokeLaneSuite                 compatibility facade + orchestration
  -> SmokeLaneContract         manifest + identity + metadata + validation
  -> SmokeLaneWorkloads        typed workloads + fixtures
  -> SmokeLaneEvidence         observation evidence
  -> SmokeLaneAggregation      repeated-measurement merge

BenchmarkModel -> SmokeLaneContract
```

`SmokeLaneSuite` 从 2,341 行收缩为 46 行。当前较大的
`SmokeLaneWorkloads`（1,265 行）与 `SmokeLaneContract`（843 行）分别保持
workload 和契约内聚；本轮没有为了减少 LOC 再制造一层通用框架。

为了保持现有测试入口及 JSON schema 的
`x-soma-laneBinding=SmokeLaneSuite.validateLaneRecord`，suite 中保留了窄的
兼容 alias/delegate；它们不再拥有对应事实。

### 3.2 Processor

变更前：

```text
SomaProcessor admission -> DenseSelectorSourceSupport
DenseTableSourceEmitter  -> DenseAuxiliarySourceEmitter
```

变更后：

```text
SomaProcessor admission
  -> DenseSelectorCodegenModel

Table / Exact emitters
  -> DenseSelectorCodegenModel
  -> DenseSelectorSourceSupport

DenseTableSourceEmitter
  -> DenseScanExecutionSourceSupport
```

`DenseSelectorCodegenModel` 现在拥有 selector 参数分组和 canonical public
parameter type sequence；source emission 仍由 source support 拥有。
写入 Table artifact 的 Scan terminal executor 由
`DenseScanExecutionSourceSupport` 拥有，不再借道 Auxiliary artifact emitter。

`DenseTableSourceEmitter -> DenseExactIndexSourceEmitter` 被有意保留：现有证据
只支持它是 artifact-internal composition，没有证明它需要拆分。

### 3.3 Generated footprint

原有 33-table fixed candidate、既有基线与 15% ceiling 均未改变。新增：

- `scan-artifact-footprint.tsv`：逐 Scan 的 source、top-level class、nested
  classes 和 class family 规模；
- `schema-footprint.tsv`：逐 schema 的 table、field、physical leaf、selector
  与生成源码规模；
- properties 中的 min/max/average、feature totals、准确的
  `topLevelClassBytes` 与兼容 alias `topClassBytes`；
- TSV 汇总值必须重建原 aggregate 的一致性检查。

当前诊断样本包含 4 个 schema、33 个 table、149 个 field、161 个 physical
leaf 和 8 个 selector。Scan source 合计 785,446 bytes / 3,392 lines；
单 Scan source 为 22,057–28,284 bytes，平均 23,801 bytes；最大 class
family 为 41,866 bytes。两次 clean 执行得到的 TSV 字节一致。

这些数据用于解释增长形状，不是单 feature 因果系数，也不替代 processor
admission limit。

### 3.4 Runtime

`DenseTableState` 与 `ChildOwnershipRegistry` 未修改。Stage 0 的 retain
裁决和再次审查触发条件保持不变：

> 只有未来至少两次独立 metrics/resource 变化持续绕开 mutation
> coordinator，才重新审查 satellite state。

## 4. 非回归证据

### 4.1 生成与场景

- processor 变更前后的 generated Java SHA-256 清单一致；
- schema artifact 与 schema hash 清单一致；
- generated class/public manifest 一致；
- external consumer 与 codegen admission checker 通过；
- 4 个 executable scenario 通过；
- 仍生成 222 个类型和 782 个 Java 8 major-52 class。

### 4.2 Benchmark

- artifact schema 保持 `soma-benchmark-smoke-v4`；
- 20 条 lane record 全部存在且重复执行顺序一致；
- 36 条 negative artifact path 继续被拒绝；
- lane binding、workload identity、measurement 与 aggregation invariant 保持；
- `claimAllowed=false` 保持，没有产生新的性能或发布 claim。

### 4.3 Code-size 与完整 Gate

- 原 fixed-candidate code-size Gate、基线、阈值和 15% ceiling 未修改；
- 新诊断的逐项合计必须与旧 aggregate 相等；
- `git diff --check` 通过；
- Zulu JDK `1.8.0_492`、Maven `3.9.16`、macOS arm64 环境下，
  `./scripts/check.sh` 最终输出 `project-check: ok`；
- FJSP allocation/GC Gate 通过，100,000 operations 样本无 Full GC；
  该单次 Gate 结果仅用于非回归，不作为新的稳定性能结论。

## 5. Scope non-regression

| 边界 | 审查结果 |
|---|---|
| Blueprint / Design | 未修改，目标没有由当前实现反向降低 |
| public/generated API | 未变 |
| annotation Schema / schema hash | 未变 |
| Access Model / Index 生命周期 | 未变 |
| runtime protocol / failure atomicity | 未变 |
| 四场景与 benchmark lane | 未删除、未合并、未弱化 |
| Gate / threshold / baseline | 未放宽 |
| runtime state machine | 未拆分、未改写 |
| dependency | 未新增第三方依赖 |
| 正式 Owner / Report | 未提前修改或形成 |

Stage 1 的实际结果是内部责任澄清与诊断增强，不是产品能力折扣，也没有把
本专题扩大成新的产品设计。

## 6. 残余风险与明确未实施项

1. `SmokeLaneWorkloads` 和 `SmokeLaneContract` 仍较大，但当前分别只有一个
   共同变化原因；只有出现新的独立 co-change 证据时才继续拆分。
2. `BenchmarkModel` 仍约 964 行，承载通用 model/JSON/validation。本轮只消除
   已确认的 lane cycle，不把文件大小本身升级为新的实施授权。
3. suite 的兼容 alias 和旧 schema binding 暂时保留；若未来要改名，需要单独
   治理 artifact contract，而不是在内部重构中顺手删除。
4. Table emitter 到 Exact emitter 的依赖有意保留，等待 artifact ownership
   发生真实分化后再复审。
5. footprint 诊断还不能证明单个 feature 的因果斜率，也没有验证
   256-table/256-leaf compiler envelope；现有数据不得外推为容量承诺。
6. runtime 状态机没有发现足够拆分证据；后续只按已记录的事件触发条件复审。

这些事项均不是本轮遗留的功能缺陷，也不要求在正式固化前继续实施。

## 7. 建议与停止点

建议项目 Owner 接受 Stage 1 实施结果，并在新的明确授权下进行最终固化：

1. 将稳定的责任变化写入唯一 Implementation Map / Engineering Owner；
2. 形成正式 Governance Report，记录本轮证据与边界；
3. 重跑相称 Gate；
4. 删除本 Temporary，并恢复文档入口的无 active-topic 状态。

当前严格停在结果审查处，不执行上述步骤。
