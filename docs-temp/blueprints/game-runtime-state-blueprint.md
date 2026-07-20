# Game runtime state 蓝图

类型：Blueprint

状态：候选

Owner：Game 目标场景

事实范围：entity state、phase-local workspace、grid/cache 和结果投影的目标使用方式

非事实范围：完整 game engine、ECS 调度、网络协议、精确 API 和当前性能结论

最后审查日期：2026-07-19

## 1. 目标形态

SOMA 保存类型化 battle runtime state 和高频工作集；game loop 继续拥有规则、pathfinding、行动调度、rendering、network 和 replay。

- player/unit 使用 keyed long-lived state；
- map definition 使用 read-mostly dense/grid layout；
- occupancy 是由 unit position 派生的 lookup cache，必须可校验和重建；
- selected-unit move candidates 使用 phase-local dense workspace；
- pending damage 使用当前 resolution phase 的 dense buffer，不兼任历史日志；
- final hp/position/score 从最终 state 投影，不复制等价 result table。

## 2. 目标流程

```text
select next unit
  -> build selected-unit candidate workspace
  -> filter and explicitly sort candidate Indexes
  -> application commits movement and occupancy cache update
  -> build/resolve pending damage buffer
  -> mutate authoritative unit/player state
  -> export detached snapshot at a boundary
```

如果候选扩展成跨 tick、全局 AI planning frontier，必须重新评估 identity、lifecycle 和增量维护；不能把当前 dense workspace 无条件放大。

## 3. 边界选择

- unit position 是 occupancy 的事实源；cache drift 时丢弃并重建；
- grid physical traversal order 不是坐标 O(1) lookup 承诺；hot coordinate access 可以使用 keyed table 或 application grid adapter；
- dynamic sort 是本次 action policy，不是 maintained global order；
- damage resolution 跨 table mutation 不受 SOMA transaction 保护；
- SOMA 不是 ECS、pathfinder、renderer 或 network state replication system。

## 4. 蓝图需要证明的事项

- selected-unit workspace 的规模、capacity reuse 和 sort scratch；
- coordinate lookup 的 dense/keyed/external-grid 分界；
- occupancy cache 更新与 rebuild invariant；
- pending damage 的目标 locality、lookup/mutation 和 failure recovery；
- snapshot materialization 与 external DTO/network mapping 的独立成本。
