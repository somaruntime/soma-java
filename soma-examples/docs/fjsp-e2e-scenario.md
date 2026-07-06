# FJSP E2E 场景契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-examples`

## 1. 目标

本文定义 `soma_java` V1 examples 必须证明的端到端场景和 evidence 边界。

Examples 负责暴露跨模块集成问题，但不拥有 annotation schema、processor、runtime core 或 generated API 的正式技术事实。

## 2. V1 最小场景

V1 最小 E2E 场景是简化 FJSP 的 `FCFS + SPT` dispatch：

```text
request DTO or protobuf adapter
  -> solver API loader
  -> Java annotation schema / generated tables
  -> Java columnar runtime core
  -> FCFS + SPT solver core
  -> solver API exporter
  -> response DTO or protobuf adapter
```

`SOMA` library 本身不依赖 protobuf。示例可以使用 plain Java request/response DTO 模拟 API boundary；真实业务项目可在外层接 protobuf。

## 3. Runtime state coverage

示例 runtime state 至少覆盖：

- `Operation` keyed table；
- `Job` keyed table；
- `Machine` keyed table；
- `ProcessingTime` keyed lookup table；
- `SetupTime` keyed lookup table；
- `ReadyOperationScratch` dense table workspace；
- `CandidateScoreScratch` dense table workspace；
- enum；
- value key；
- nested value；
- optional field；
- dense table；
- table-level index；
- table-level order；
- lazy generated view；
- ColumnView。

## 4. Dispatch flow

默认示例算法：

1. 从 request boundary 导入 jobs、operations、machines、processing times 和 setup times；
2. batch import 初始化 generated tables；
3. 使用 FCFS order 找到 ready operation；
4. 为 ready operation 生成 candidate machine score；
5. 使用 SPT order 选择最小 processing time candidate；
6. mutation 写回 operation assignment；
7. 更新 machine availability；
8. 重复直到无 ready operation 或全部 operation assigned；
9. fetch/materialize DTOs；
10. export response boundary。

算法正确性不是 SOMA 的完整 APS 承诺。该示例只用于证明 runtime state API 能支撑典型调度 hot loop。

`ReadyOperationScratch` 和 `CandidateScoreScratch` 是 dense table workspace：它们可以在一次 solve 生命周期内长期持有，并通过 `replaceAll(batch)` 反复刷新。它们不是短生命周期 Java 临时对象管理器，也不改变 SOMA table 只有 keyed table / dense table 两类的原则。

## 5. API usage points

示例必须覆盖以下 generated API 使用点：

- `create()`；
- `reserve(size)`；
- `addBatch(batch)`；
- keyed `fetch(key)`；
- `containsKey(key)`；
- generated order access；
- generated optional presence predicate；
- lazy view `take` / `fetchFirst`；
- dense table workspace `replaceAll(batch)`；
- `mutate(key).field(...).commit()`；
- `delete(key)` if scenario includes deletion；
- typed `ColumnView` read；
- DTO materialization for export。

## 6. Error and lifecycle evidence

Examples smoke 至少证明：

- duplicate key 可观察；
- invalid selector 在 compile/processor 阶段失败；
- schema hash metadata 可读取；
- stale view 可观察；
- released view 可观察；
- view pinned 可观察；
- runtime stats 可读取。

如果某项属于 runtime unit gate 而不适合 E2E smoke，应在 G5 report 中引用对应 G3/G4 evidence，不能静默省略。

## 7. Benchmark smoke boundary

Examples 可以作为 benchmark smoke 的基础，但 benchmark smoke 只能证明：

- benchmark runner 能运行；
- scenario、scale、environment、metrics 能结构化记录；
- release artifact 能完成基本 performance path。

Benchmark smoke 不能单独支撑“更快”“更省内存”或“生产级大规模 hot path”声明。

## 8. Non-goals

FJSP example V1 不做：

- 完整 APS 求解器；
- 最优性证明；
- CP-SAT propagation；
- 多目标优化；
- 全业务约束覆盖；
- protobuf library dependency inside SOMA core；
- persistence format；
- UI / service integration。

## 9. G5 report

G5 examples report 应记录：

- 使用的 module versions / commit hash；
- generated schema hash；
- Java 8 smoke command；
- request boundary -> loader -> generated tables -> solver core -> exporter -> response boundary 完整路径；
- ordered access evidence；
- lazy terminal evidence；
- ColumnView evidence；
- stale/released/view_pinned evidence；
- runtime stats evidence；
- benchmark smoke 是否执行；
- known limitations。
