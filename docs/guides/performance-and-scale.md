# 性能适用性与最佳用法

<!--
类型：Product Projection
状态：当前
Owner：SOMA Java performance guidance
受众：技术选型者、应用开发者与性能工程师
输入事实源：project/reports/current-performance-summary.md 与正式性能 Design
事实范围：面向使用者的性能适用条件、最佳用法和坏味道投影
最后审查日期：2026-07-30
-->

SOMA 的性能证据用于证明明确环境和 workload 下的机械形状与非回归，不构成
10M/100M 行保证、跨平台 SLA 或任意 Schema 的容量承诺。评估真实项目时必须同时
记录字段形状、String payload、索引、表数量、mutation pattern、生命周期、heap、
allocation 和 GC。

## 推荐用法

- 让 SOMA Table 保存 authoritative runtime state；跨轮次候选、优先队列和
  resource calendar 等可重建算法状态使用 application-owned primitive 结构；
- 在明确生命周期内复用 ColumnView，并在 machine group、resource group 等自然
  边界提升不变读取，避免在每个 candidate 上重复访问同一 row；
- 为动态 cardinality update 复用 generated primitive scratch，同时规划
  `maximumUpdateScratchBytes` 和预期 high-water；
- 复用 DataFlow Definition、Template 与 Context；每次执行只创建 one-shot
  Invocation；
- stable sort、ordered merge 等语义 barrier 必须单独测量，不能因为存在 parallel
  入口就假定整条路径会线性加速；
- 优化前后保持 input、result、schema 和 runtime-plan identity，并同时观察 CPU、
  allocation、GC 与 SOMA high-water。

## 常见坏味道

- 把 DTO、`List<Row>`、object graph 或 schema metadata 放入 runtime hot storage；
- 在 hot loop 中使用 reflection、Java Stream、装箱集合或逐行临时对象；
- 为节省少量 retained scratch 而反复精确扩容，造成数组复制和 GC；
- 只看单次 wall time，不验证 checksum、failure、lifecycle 和结果一致性；
- 把本机 baseline 或某个 example 的结果外推到不同 JDK、硬件或业务数据。

正式数据、环境和限制见
[当前性能与规模摘要](../../project/reports/current-performance-summary.md)；
长期性能不变量见
[性能模型](../../project/design/performance-model.md)。
