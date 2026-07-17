# SOMA Java 临时设计与研究蓝图索引

状态：临时设计索引
正式事实源：否
最后审查日期：2026-07-17

本目录保存尚未迁入唯一 Owner 的专题草案与长期研究蓝图。任何实现、Gate、兼容性判断或发布声明仍必须以 [正式设计文档索引](../README.md) 登记的 Owner 为准。

## 当前治理专题

| 专题 | 状态 | 目标 |
|---|---|---|
| [Packed Index / Exact Access / IndexBuffer 重设计](packed-index-runtime-redesign/README.md) | 待独立审查 | 在不进入实现的前提下，完整设计取消 Sparse Set 与 maintained order、引入 eager exact access、统一 `IndexBuffer`、采用 keyed/dense swap-remove 的迁移方案 |

## 长期研究蓝图

| 蓝图 | 当前研究边界 |
|---|---|
| [FJSP MachineCandidate frontier](fjsp-machine-candidate-frontier-blueprint.md) | frontier lifecycle、indicator/update 粒度和 claim-grade evidence |
| [VRP runtime frontier](vrp-runtime-frontier-blueprint.md) | dense workspace 与 keyed insertion frontier 的升级条件 |
| [连续仿真 runtime state](simulation-runtime-state-blueprint.md) | state vector、event queue、trace buffer 的 concrete runner 和 evidence |
| [Game runtime frontier](game-runtime-frontier-blueprint.md) | dense action workspace、occupancy lookup 和 game-loop evidence |

这些长期蓝图仍按各自页首说明解释。若其 `@SomaOrder`、dirty sidecar、Sparse Set 或稳定物理顺序假设与新的重设计专题冲突，在专题尚未正式批准和迁移前，冲突只表示研究方向正在变化；不能任选一份临时文档作为实现事实源。

## 生命周期

普通专题遵循：

```text
临时设计
  -> 独立审查和明确决策
  -> 按唯一 Owner 顺序迁移正式事实
  -> 更新实现、examples、testkit、benchmarks 和 gates
  -> 验证迁移闭环
  -> 删除或归档临时专题
```

存在未决问题时必须保留为待审项，不得在迁移或实现中猜测答案。
