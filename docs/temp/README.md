# SOMA Java 临时设计与研究蓝图索引

状态：临时设计索引
正式事实源：否
最后审查日期：2026-07-19

本目录保留用户明确批准的长期研究蓝图，以及尚未完成决策或迁移的普通临时专题。任何实现、Gate、兼容性判断或发布声明仍必须以 [正式设计文档索引](../README.md) 登记的 Owner 为准。

Packed Index / Exact Access / IndexBuffer 专题已于2026-07-17完成Owner迁移、实现、验证与退役；实施evidence见[专题收口报告](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)，临时专题原文不再保留。

## 普通临时专题

| 专题 | 当前边界 | 删除条件 |
|---|---|---|
| [Packed Index / Exact Access 切换后尾项治理](packed-exact-index-post-cutover-tails/) | 记录报告卫生、`IndexSnapshot`安全决策、allocation/retained-memory与可维护性尾项；不重新打开已完成cutover | 稳定决定迁入唯一Owner、分项evidence关闭并形成dated closeout |

## 长期研究蓝图

| 蓝图 | 当前研究边界 |
|---|---|
| [FJSP MachineCandidate frontier](fjsp-machine-candidate-frontier-blueprint.md) | frontier lifecycle、indicator/update 粒度和 claim-grade evidence |
| [VRP runtime frontier](vrp-runtime-frontier-blueprint.md) | dense workspace 与 keyed insertion frontier 的升级条件 |
| [连续仿真 runtime state](simulation-runtime-state-blueprint.md) | state vector、event queue、trace buffer 的 concrete runner 和 evidence |
| [Game runtime frontier](game-runtime-frontier-blueprint.md) | dense action workspace、occupancy lookup 和 game-loop evidence |

这些长期蓝图仍按各自页首说明解释，并已同步到packed/exact v3 baseline；它们继续研究scenario role、hot-loop与evidence，不拥有public/runtime语义。

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
