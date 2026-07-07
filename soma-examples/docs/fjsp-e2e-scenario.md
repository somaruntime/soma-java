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
- `Material` keyed table；
- `Machine` keyed table；
- `MachineCandidate` keyed runtime frontier table；
- `ProcessingTime` keyed lookup table；
- `SetupTime` keyed lookup table；
- enum；
- value key；
- nested value；
- optional field；
- table-level index；
- table-level order；
- Row Pipeline lazy terminal；
- ColumnView。

## 4. Dispatch flow

默认示例算法：

1. 从 request boundary 导入 jobs、operations、materials、machines、processing times 和 setup times；
2. batch import 初始化 generated tables；
3. 当 operation release 时，通过 `ProcessingTime.findByOperation(operationKey)` 为可加工 machine 生成 `MachineCandidate` rows；
4. 每轮从 `Machine.byAvailableTime().firstOrThrow()` 选择下一个可用 machine；
5. 对 `MachineCandidate.findByMachine(machineId)` 的候选更新 setup、effective ready time、FCFS value 和 SPT value；
6. 对同一 machine 的候选执行 `sorted(dispatchRuleComparator).firstOrThrow()`，选择下一个 operation；
7. mutation 写回 operation assignment；
8. 更新 machine availability 和 last setup family；
9. 使用 `MachineCandidate.findByOperation(operationKey).remove()` 删除该 operation 的全部候选；
10. release 后续 operation，并重复直到全部 operation assigned；
11. fetch/materialize DTOs；
12. export response boundary。

算法正确性不是 SOMA 的完整 APS 承诺。该示例只用于证明 runtime state API 能支撑典型调度 hot loop。

SOMA V1 只保证单张 table mutation 后的 table 内部不变量。`OperationStateTable` assignment、`MachineStateTable` availability 更新、`MachineCandidateTable` frontier 删除、`JobStateTable` 和 `MaterialStateTable` 推进等跨 table 提交序列，不具备 runtime transaction 语义；其一致性、提交顺序、失败处理和补偿策略由 solver loop 拥有。

`MachineCandidate` 是 keyed runtime frontier。候选 row 存在表示该 `(MachineId, OperationKey)` 组合仍处于可选 frontier；operation 被选中后，solver loop 应通过 `findByOperation(operationKey).remove()` 删除所有相关候选，而不是保留长期 `active` 标志。Dispatch rule 属于 solver 策略，示例不在 `MachineCandidate` schema 上声明 `byMachineDispatchRule` 这类 order。

## 5. Lookup missing semantics

FJSP example 必须明确区分 runtime missing key 与业务不可行：

- `ProcessingTime` 表示 operation-machine pair 是否可加工。通过 `findByOperation(operationKey)` 生成候选时，某台机器没有对应 `ProcessingTime` row 表示该机器不是候选；如果某个 released operation 没有任何 candidate row，solver core 将其解释为当前无可行机器，而不是 SOMA runtime error。
- `MachineCandidate` 表示已经 release 且仍未被分配的候选 frontier。某个 operation 不在 frontier 中，可能表示它尚未 release、已经被分配、或因业务规则暂不可行；具体解释由 solver core 拥有。
- 如果 loader 或 solver core 已经确定某个 `OperationMachineKey` 必须存在，再调用 `processingTimes.fetch(operationMachineKey)` 或 `firstOrThrow()` 时缺失，应作为 typed missing key / empty required result 错误暴露。
- `SetupTime` 在 V1 canonical FJSP 中是 required setup matrix lookup。除第一道工序或机器没有 `lastSetupFamily` 且业务规则定义 setup 为 `0` 的情况外，缺失 `SetupTime` row 表示输入或模型不完整，应通过 typed missing key / required lookup error 暴露。
- 如果未来示例要表达 sparse setup matrix，例如缺失 setup 表示不可行或默认 `0`，必须先修改本契约，不能由 runtime 自行猜测。

SOMA runtime 只提供 `find(...)` / `containsKey(...)` / empty Row Pipeline / typed missing error 等基础语义。候选不可行、输入不完整、默认 setup 等业务解释属于 loader 或 solver core。

## 6. API usage points

示例必须覆盖以下 generated API 使用点：

- `create()`；
- `reserve(size)`；
- `addBatch(batch)`；
- keyed `fetch(key)`；
- keyed `find(key)` or empty Row Pipeline for optional lookup；
- `containsKey(key)`；
- generated grouped index access, for example `findByOperation(operationKey)`；
- generated machine frontier index access, for example `findByMachine(machineId)`；
- generated order access；
- generated optional presence predicate；
- Row Pipeline `findFirst()` / `firstOrThrow()`；
- Row Pipeline `sorted(comparator)` dynamic sort；
- `mutate(key).field(...).commit()`；
- Row Pipeline `update(updater)` for indicator refresh；
- Row Pipeline `remove()` for selected operation candidate cleanup；
- `delete(key)` if scenario includes single-key deletion；
- typed `ColumnView` read；
- DTO materialization for export。

## 7. Error and lifecycle evidence

Examples smoke 至少证明：

- duplicate key 可观察；
- required lookup missing key / empty required result 可观察；
- optional lookup empty result 不被误报为 runtime failure；
- invalid selector 在 compile/processor 阶段失败；
- schema hash metadata 可读取；
- stale view 可观察；
- released view 可观察；
- view pinned 可观察；
- runtime stats 可读取。

如果某项属于 runtime unit gate 而不适合 E2E smoke，应在 G5 report 中引用对应 G3/G4 evidence，不能静默省略。

## 8. Benchmark smoke boundary

Examples 可以作为 benchmark smoke 的基础，但 benchmark smoke 只能证明：

- benchmark runner 能运行；
- scenario、scale、environment、metrics 能结构化记录；
- release artifact 能完成基本 performance path。

Benchmark smoke 不能单独支撑“更快”“更省内存”或“生产级大规模 hot path”声明。

## 9. Non-goals

FJSP example V1 不做：

- 完整 APS 求解器；
- 最优性证明；
- CP-SAT propagation；
- 多目标优化；
- 全业务约束覆盖；
- protobuf library dependency inside SOMA core；
- persistence format；
- UI / service integration。

## 10. G5 report

G5 examples report 应记录：

- 使用的 module versions / commit hash；
- generated schema hash；
- Java 8 smoke command；
- request boundary -> loader -> generated tables -> solver core -> exporter -> response boundary 完整路径；
- ordered access evidence；
- grouped index/order source evidence；
- required lookup missing error 与 optional lookup empty result evidence；
- Row Pipeline lazy terminal evidence；
- ColumnView evidence；
- stale/released/view_pinned evidence；
- runtime stats evidence；
- benchmark smoke 是否执行；
- known limitations。
