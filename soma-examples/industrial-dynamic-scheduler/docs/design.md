# 工业动态调度应用 Design

类型：应用 Design

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-23

## 责任方向

依赖方向固定为：

```text
config -> problem -> bootstrap -> runtime -> validation/evidence
```

- `config/` 严格读取 properties、校验全部 key 并输出 canonical text；
- `problem/` 生成和预检 detached input，不 import SOMA runtime/generated code；
- `state/` 只声明 annotation schema；
- `runtime/` 装载 authoritative state，拥有 event queue、resource calendar 和 solve；
- `validation/` 只依赖 detached problem 与 materialized result；
- `evidence/` 编排 correctness、long-run 和 benchmark，不进入 production loop。

Runtime 不反向调用 generator；generator 的可变 `Random` 状态在 problem 构造完成后
即可释放。

## State projection

| 角色 | SOMA table | 主要访问 |
|---|---|---|
| input definition | Job/Operation/Machine/Setup/Transport | key、unique、owned child |
| authoritative state | Machine/Operation/SecondaryResourceState | point read/mutation、column |
| derived frontier | DispatchCandidate | exact group、update、filter/sort、remove |
| result | OperationAssignment | append、key traversal、materialization |

`EligibleMachine` 和 `MaintenanceWindow` 是严格 owned child。Event queue 与
resource lane array 是 application structure，可由 input/assignment 重建，不成为
live SOMA storage 的旁路事实源。

## 调度算法

每次循环：

1. frontier 为空时消费下一个外部事件时刻；
2. release 和 material 两类事件都发布后，首工序进入 frontier；
3. packed update 根据 machine、operation、resource version 刷新全部候选；
4. 按 `setupStart -> completion -> priority -> due -> stable identity` 全序选择；
5. 若尚未消费的事件早于候选 setupStart，先发布事件并重新选择；
6. 重新检查 candidate key 和三个 source version；
7. 按 assignment、machine、resource、operation、frontier 的显式顺序提交；
8. successor 带 predecessor end/machine 发布，其他候选通过 swap-remove 退役。

Machine interval 包含 setup 和 processing，并跳过所有 maintenance window。
Secondary resource 用按可用时间排序的 lane array 找到满足 units 的最早时刻；
assignment 是该约束的最终权威事实。

## Failure 与 lifecycle

- problem 在首次 Table mutation 前完成 identity/reference/range/matrix/event 预检；
- bootstrap 失败会关闭整个尚未发布的 aggregate；
- 单 Table operation 保持 SOMA 失败原子性；
- SOMA V1 没有跨 Table transaction；authoritative write 后失败使 solve fail-stop；
- derived frontier、event projection 与 resource calendar 可以从 input/assignment
  重建；
- `SchedulerRuntime` 是唯一 owner，按 result/derived/lookup/state/definition
  逆序 release；
- callback 不重入同一 aggregate，不产生外部副作用；
- solver 和 pipeline 都是 one-shot。

## Index 与物理顺序

业务 tie-break 始终使用 stable identity。Machine calendar 对 child 的扫描不依赖
physical order；result checksum 在按 operation identity 排序后计算。验证器还会
反转 materialized assignment 清单，证明输出不依赖 packed physical order。

`IndexSnapshot` 只在同步只读批次消费；应用负路径显式验证 mutation 后 stale、
wrong-source 和 release 后访问均被拒绝。
