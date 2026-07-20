# 连续仿真 runtime state 蓝图

类型：Blueprint

状态：候选

Owner：连续仿真目标场景

事实范围：dense 数值状态、事件边界、trace 和导出路径的目标形态

非事实范围：具体积分算法、物理模型、精确 API 和当前性能结论

最后审查日期：2026-07-19

## 1. 目标形态

连续仿真的主循环使用初始化一次、反复原地更新的 dense state vector。SOMA 负责 packed primitive state、只读 lookup facts 和 trace/result buffer；simulator 拥有数值模型、拓扑、事件语义和时间推进。

| 数据 | 目标形态 | 主要路径 |
|---|---|---|
| topology / parameter input | keyed 或 dense read-mostly definitions | 初始化 lookup/scan |
| state vector | dense long-lived authoritative numeric state | primitive scan/update |
| pending events | application min-heap；SOMA 可做 batch projection | ordered pop / projection |
| trace samples | dense append buffer | append、边界 export/sort |

## 2. 主循环

```text
initialize state-vector layout once
  -> pop due events from application heap
  -> apply events
  -> compute derivatives over dense columns
  -> integrate state vector in place
  -> append samples according to policy
  -> materialize/export only at an explicit boundary
```

state vector 是数值状态唯一事实源。entity object/table 可以保存 topology 和 immutable parameter，但不得并行维护可独立修改的 numeric shadow state。

## 3. 边界选择

- event ordering 是 application 数据结构职责，不由 table scan/sort/remove 模拟；
- trace 是历史输出，不反向成为 current state；
- 高频 coefficient lookup 若主导 inner loop，应在初始化或拓扑变化时受控预投影到连续布局；
- active ColumnView、结构变更和写入阶段必须有清楚分界；
- NaN、Infinity、scale 和数值稳定性属于 simulator 业务语义，SOMA 只执行其声明的 key/storage 规则。

## 4. 蓝图需要证明的事项

- Row Pipeline update、ColumnView 和生成 primitive loop 的分界；
- event heap 与可选 Table projection 的独立成本；
- trace append、chunk/export 和显式排序成本；
- lookup 与 dense preprojection 的收益、同步规则和失效成本；
- final-state materialization 与主循环 allocation 的隔离。
