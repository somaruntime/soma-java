# VRP runtime state 蓝图

类型：Blueprint

状态：候选

Owner：VRP 目标场景

事实范围：VRP 构造解中 route state、局部 workspace 与可选 frontier 的目标使用方式

非事实范围：完整 VRP 算法、精确 schema/API 和当前性能结论

最后审查日期：2026-07-19

## 1. 目标形态

SOMA 承载 customer/vehicle input、current route state、route visits、assignment facts 和构造阶段的候选工作集。VRP constructor 拥有容量、时间窗、插入策略和跨表一致性。

- customer、vehicle、travel cost 使用稳定 identity 和精确 lookup；
- route 是长期 keyed current solution；
- route visits 是 parent-owned dense child，`position` 表达业务顺序但不是 row identity；
- selected route/customer 的 insertion candidates 默认使用可复用 dense workspace；
- 只有候选跨多轮存在且局部增量更新显著优于 rebuild 时，才升级为 keyed frontier。

## 2. 目标流程

```text
select route/customer scope
  -> child-local scan current route visits
  -> build or refresh insertion candidate workspace
  -> filter feasibility
  -> explicit total comparator over candidate Indexes
  -> choose insertion
  -> application commits route rewrite and assignment
  -> refresh only affected workspace/frontier state
```

一次 `.sorted(...)` 只定义当前 terminal 的顺序；route 的长期顺序由 visit `position` 事实表达。dense 删除和 rewrite 不保证物理顺序。

## 3. 事实与 cache

- current route visits 是路线结果的唯一内容事实，不再复制等价 `RouteResult`；
- customer assignment 使用独立 identity 时可以成为独立 result fact；
- unassigned-customer 视图和 insertion candidates 可以重建，不得成为 assignment 的第二事实源；
- 预投影只复制能显著改善 hot loop 的只读或 derived leaf，并明确 invalidation；
- coordinate/route sequence 的专用数据结构可以由 application 持有，不要求全部放入 SOMA。

## 4. 蓝图需要证明的事项

- dense workspace rebuild 与 keyed incremental frontier 的规模分界；
- route child locality、small-child overhead 和 rewrite 成本；
- travel-cost random lookup 与受控 preprojection 的取舍；
- explicit sort scratch、candidate selectivity 和 allocation；
- route/assignment 跨表提交失败时的 application invariant。
