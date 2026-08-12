# SOMA Physical Execution Engine M2 全局架构治理

类型：Bounded Temporary / Architecture Governance / Candidate Design / Feasibility

状态：`FROZEN_CANDIDATE / DESIGN_COMPLETE / FEASIBILITY_PASS / READY_FOR_PROMOTION_REVIEW / IMPLEMENTATION_NOT_AUTHORIZED`

日期：2026-08-12

Owner：本专题的目标、边界、候选物理执行模型、验证结论、冻结基线与后续实施顺序

> 本专题已经完成设计、有限验证、过度设计审查和候选基线冻结。它不是正式Design Owner，不授权
> production实现，也不覆盖当前已经正式成立的Canonical IR/Execution M1与Vectorized Pipeline
> VP1-VP3。只有Product Owner完成正式晋升与implementation authorization后，才能启动实施计划的P1。

## 1. 为什么需要本专题

SOMA已经完成Canonical Logical IR、binding、normalization、PhysicalPlan、resource admission、
ExecutionFrame、bounded parallel scheduler以及有限的representation-native Chunk kernel。当前系统不是
“没有执行引擎”，也不需要再建立第二套执行器。

剩余问题位于更高一层：现有物理执行事实分别散落在Row、Mapped、Primitive、GroupBy、Relation与
Selection mutation内部。`streaming region`、`stateful boundary`、`typed kernel`、`parallel work unit`
和`operator-local state`已经存在，却没有一套统一的物理架构语言来约束它们。因此：

- 相邻stateless operator能否融合，仍主要由family-local代码决定；
- sort、distinct、top、GroupBy、Join build等stateful边界各自拥有buffer/state与估算方式；
- Row range与Chunk ordinal共享同一scheduler lifecycle，但work shape仍缺少统一的Morsel合同；
- finite primitive Chunk kernel已经证明高吞吐方向成立，却仍像一座封闭的优化岛；
- ResourceEstimate难以从“哪些state在何时同时存活”系统推导；
- 若继续逐operation添加fast path，容易重新形成互不协调的执行机制。

本专题因此从“继续优化某个kernel”升层为：

> 建立SOMA唯一的全局物理执行架构，使全部计算family能用Physical Pipeline、Segment、Breaker、
> Kernel、ExecutionFrame与Morsel解释；同时保持Reference独立、资源先准入、确定性顺序和现有
> specialized hot path。

## 2. 名称中的M2

本专题名称中的`M2`表示“Physical Execution Engine的第二个架构里程碑”，用于区别已完成的
Canonical IR/Execution M1。

它不自动等于[核心抽象变更协议](../../design/core-abstractions-and-narratives.md)中的产品语义M2。
当前候选方案不改变public/generated语义、Canonical语义或storage truth，预期属于internal M1责任调整。
如果实施证明必须改变用户可观察语义、Blueprint、Reference合同或失败边界，必须按Core M2 stop rule
暂停并由Product Owner重新裁决。

## 3. 治理目标

1. 盘点全部现有执行路径、Owner、执行state和重复机制；
2. 建立唯一的Physical Pipeline / Segment / Breaker / Kernel / Frame / Morsel模型；
3. 将Table、Field、Mapped、Primitive、GroupBy、Join与Selection mutation映射到该模型；
4. 用一个stateless纵向切片和一个stateful纵向切片验证模型；
5. 证明该模型兼容Reference differential、resource admission、sequential/parallel等价与当前性能事实；
6. 删除过度设计，仅冻结实施所需的最小合同；
7. 形成可逐slice替换、没有长期双路径的实施计划。

## 4. 边界

本专题不包含：

- production source修改；
- public/generated API或产品语义变化；
- Canonical IR、Predicate、null/order/numeric/failure语义重设计；
- 第三production artifact、新dependency、Java版本变化；
- SOMA Engine、JSON frontend、Workflow、dynamic schema或set-based Batch；
- 通用数据库DAG、public Batch/Vector、runtime code generation、Java Vector API；
- spill、off-heap、mmap、distributed execution或跨Group transaction；
- storage、compression、Index、Join算法本身的重新设计；
- 为所有type × operator × representation预建class或placeholder。

Direct point get/add/update/remove继续由Table/Storage mutation path拥有，不为了架构整齐强行经过
Physical Pipeline。Selection只把“选择membership”交给pipeline，atomic publication仍由Mutation与
Storage Owner负责。

## 5. 交付物

- [当前执行体系与重复机制审计](current-state-audit.md)
- [冻结候选设计](design.md)
- [有限可行性与Profile验证](feasibility-validation.md)
- [P1-P6实施计划](implementation-plan.md)
- [Baseline Freeze与实施准入审查](baseline-freeze-and-readiness.md)

## 6. 最终结论

设计结论是：

```text
Canonical Operation
    -> terminal-start binding
        -> normalization
            -> physical pipeline planning
                source/access path
                + ordered pipeline segments
                + explicit breakers
                + representation-specific kernels
                + morsel strategy
                + one ResourceEstimate
                    -> resource admission
                        -> one ExecutionFrame
                            -> shared ordinal-work scheduler
                                -> segment kernels / breaker state
                                    -> deterministic merge / result / publication handoff
```

这不是建立一套新的executor，而是把current executable fact升格为唯一、可验证、可继续优化的
PhysicalPlan与ExecutionFrame模型。

两项有限验证均为`PASS`：

- stateless：`Table scan -> typed filter -> Field projection -> integral sum / ordered long[]`；
- stateful：`Table scan -> typed filter -> GroupBy hash aggregation`的语义、资源和profile边界成立；
  当前frontier profile同时覆盖未过滤GroupBy的1M热路径，用于确认breaker热点归属。

候选设计已经充分支持正式晋升审查。当前没有active implementation slice；release/publication仍未授权。

## 7. 决策记录

| 日期 | 决策 |
|---|---|
| 2026-08-12 | Product Owner批准以“SOMA Physical Execution Engine M2 全局架构治理”为专题名称 |
| 2026-08-12 | Product Owner批准全局盘点、统一模型、两项有限验证、过度设计审查与冻结范围 |
| 2026-08-12 | Candidate Design采用linear unary pipeline + bounded binary relation，不采用通用DAG |
| 2026-08-12 | Morsel复用唯一ordinal-work scheduler，不建立第二线程池或第二scheduler |
| 2026-08-12 | Reference继续直接解释Bound semantics，不消费Physical Segment/Kernel |
| 2026-08-12 | Candidate Design完成冻结；implementation authorization仍未授予 |
