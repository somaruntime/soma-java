# FJSP E2E 场景契约

状态：正式设计文档
Owner：`soma-examples`
事实范围：FJSP release/dispatch/commit flow、lookup/error/lifecycle evidence 和 G5 scenario boundary
非事实范围：schema declaration、solver business transaction contract、runtime implementation 和性能 claim
最后审查日期：2026-07-12

## 1. 场景定位

本文定义 FJSP formal example 必须演示和验证的 end-to-end flow。Runtime-state schema 与 Access Pattern Card 由 [FJSP runtime state schema 示例](fjsp-runtime-state-example.md) 拥有。

FJSP source 的阅读顺序固定为：`FjspScenario` 教学门面 -> `FjspProblem` 输入 ->
`FjspInstanceFactory` 导入 -> `FjspInstance` runtime state -> `FjspSolver` 算法 loop ->
`FjspSolveResult`。`FjspCandidateFrontier` 只封装 keyed frontier 的发布、刷新和选择，
`FcfsSptDispatchRule` 只拥有稳定排序规则。所有 annotation schema 声明集中在单一
`fjsp.schema` package；这是 V1 schema-package ownership 的直接结果，不拆成相互嵌套的
`value`/`table` Java packages。

教学入口不包含 generated table 细节、计时、错误矩阵或 gate 断言；这些进入 test source。
Benchmark 复用同一 `FjspInstanceFactory + FjspSolver`，不复制另一套算法。

Canonical flow 是：

```text
import definitions/state
  -> release operation
  -> publish/update MachineCandidate frontier
  -> choose machine and candidate
  -> application-ordered assignment commit
  -> cleanup frontier and export result
```

SOMA 保证每次 table-local mutation 和 ownership aggregate 的正确性；多个 root tables 的业务一致性、ordering 和 compensation 由 solver/application 负责。

## 2. Dispatch flow

默认示例算法：

1. 从 request boundary 导入 jobs、operation definitions（含 candidate-machine child）、machines 和 setup times；
2. batch import 初始化 generated tables；
3. 当 operation release 时，通过 generated grouped/index row source读取definition scalar，并通过`operationDefinitions.candidateMachines(operationKey)` live child facade局部遍历可加工machine，生成`MachineCandidate` rows；release hot path不递归materialize detached parent + child `List`；
4. 每轮从 `Machine.byAvailableTime().firstOrThrow()` 选择下一个可用 machine；
5. 对 `MachineCandidate.findByMachine(machineId)` 的候选更新 setup、effective ready time、FCFS value 和 SPT value；
6. 对同一 machine 的候选执行 `sorted(dispatchRuleComparator).firstOrThrow()`，选择下一个 operation；
7. 向 `OperationAssignment` result table 写入 assignment；
8. 更新 machine availability 和 last setup family；
9. 使用 `MachineCandidate.findByOperation(operationKey).remove()` 删除该 operation 的全部候选；
10. release 后续 operation，并重复直到全部 operation assigned；
11. fetch/materialize detached schema objects；
12. export response boundary。

`dispatchRuleComparator` 的稳定顺序固定为：`effectiveReadyMinute`、`fcfsValue`、
`sptValue`，随后按 candidate identity 的 `jobId.value`、`operationId.value`、
`machineId.value` 升序完成最终 tie-break。前三项完全相等时不得依赖当前 packed row、
插入顺序或 compaction 后的物理位置；正式 smoke 必须构造等值候选并在删除/compaction
后复核同一 identity 仍被选中。Canonical dispatch hot path 使用 generated leaf getter、
row locator 与 typed ColumnView 读取选中事实，只有 assignment/export 等明确边界才构造
detached schema object；不得为每个比较或候选扫描重建完整 Value/object chain。

算法正确性不是 SOMA 的完整 APS 承诺。该示例只用于证明 runtime state API 能支撑典型调度 hot loop。

`releaseNextOperations(...)` 应依赖 `OperationDefinition.by_job_sequence(jobId, nextSequenceNo)`、`JobRuntimeState.nextSequenceNo` 或 material / predecessor readiness 的明确索引或业务队列，不能退化为全表扫描。operation release 后，再局部遍历该 definition 独占的 `candidateMachines` child，增量加入 `MachineCandidate` frontier。

SOMA V1 只保证单张 table mutation 后的 table 内部不变量。`OperationAssignment` 新增、`Machine` availability 更新、`MachineCandidate` frontier 删除和 `JobRuntimeState` 推进等跨 table 提交序列，不具备 runtime transaction 语义；其一致性和提交顺序由 solver loop 拥有。本示例采用 disposable instance policy：任一步失败即停止求解并由 caller 关闭、丢弃整个 `FjspInstance`，不能假设 SOMA runtime 自动补偿。

`MachineCandidate` 是 keyed runtime frontier。候选 row 存在表示该 `(MachineId, OperationKey)` 组合仍处于可选 frontier；operation 被选中后，solver loop 应通过 `findByOperation(operationKey).remove()` 删除所有相关候选，而不是保留长期 `active` 标志。`indicatorReady` 只是当前 machine snapshot / 当前 dispatch 轮次下的 indicator 计算状态，不能作为长期业务状态或候选有效性事实。Dispatch rule 属于 solver 策略，示例不在 `MachineCandidate` schema 上声明 `byMachineDispatchRule` 这类 order。

## 3. Lookup missing semantics

FJSP example 必须明确区分 runtime missing key 与业务不可行：

- `OperationDefinition.candidateMachines` 表示 operation-machine 可加工关系。required child 为空表示该 operation 没有候选 machine；solver core 将其解释为当前无可行机器，而不是 SOMA runtime error。
- `MachineCandidate` 表示已经 release 且仍未被分配的候选 frontier。某个 operation 不在 frontier 中，可能表示它尚未 release、已经被分配、或因业务规则暂不可行；具体解释由 solver core 拥有。
- 如果 loader 或 solver core 已经确定某个 `OperationDefinition` 必须存在，再调用 `operationDefinitions.fetch(operationKey)` 或 `firstOrThrow()` 时缺失，应作为 typed missing key / empty required result 错误暴露。
- `SetupTime` 在 V1 canonical FJSP 中是 required setup matrix lookup。除第一道工序或机器没有 `lastSetupFamily` 且业务规则定义 setup 为 `0` 的情况外，缺失 `SetupTime` row 表示输入或模型不完整，应通过 typed missing key / required lookup error 暴露。
- 如果未来示例要表达 sparse setup matrix，例如缺失 setup 表示不可行或默认 `0`，必须先修改本契约，不能由 runtime 自行猜测。

SOMA runtime 只提供 `find(...)` / `containsKey(...)` / empty Row Pipeline / typed missing error 等基础语义。候选不可行、输入不完整、默认 setup 等业务解释属于 loader 或 solver core。

## 4. API usage points

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
- schema object / `List` / `Map` materialization for export；外部 DTO 只在 adapter 边界构造。

`operationDefinitions.fetch(operationKey)`递归materialize detached
`OperationDefinition + List<CandidateMachineDefinition>`只用于明确的materialization/export
evidence，不进入canonical operation-release hot path。release必须使用generated live child facade；
这一约束与runtime-state Owner中的child-locality口径一致。

## 5. Error and lifecycle evidence

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

## 6. Benchmark smoke boundary

Examples 可以作为 benchmark smoke 的基础，但 benchmark smoke 只能证明：

- benchmark runner 能运行；
- scenario、scale、environment、metrics 能结构化记录；
- release artifact 能完成基本 performance path。

Benchmark smoke 不能单独支撑“更快”“更省内存”或“生产级大规模 hot path”声明。

`soma-benchmarks` 额外拥有 `fjsp.solve.fcfs_spt_100k` diagnostic preset：1000 jobs、
每 job 100 operations、100 machines、每 operation 3 candidate machines，固定 seed，
使用本契约同一 FCFS + SPT + identity tie-break。该 preset 分离 workload/import、solve
hot loop 和 export 时间；单机结果只回答“本次环境和本次 commit 用时多少”，不自动形成
跨机器或 release 性能承诺。

## 7. 使用方式

- `JobDefinition`、`OperationDefinition` 与其 `candidateMachines` dense child、`SetupTime` 是 input facts；`JobRuntimeState`、`OperationRuntimeState`、`Machine`、`MachineCandidate` 是 working state；`JobResult` 与 `OperationAssignment` 是 result facts；material readiness 作为 operation runtime readiness fact 存储，不保留未被算法使用的独立 `Material` table；
- `SetupTime` 是 keyed lookup data；
- `MachineCandidate` 是 keyed runtime frontier，primary key 是包含 `MachineId` 与 `OperationKey` 的组合；
- `MachineCandidate.by_machine` 支撑当前 machine dispatch，`MachineCandidate.by_operation` 支撑 operation 被选中后的候选清理；
- `MachineCandidate` 不声明 dispatch order，FCFS、SPT 和 setup 相关 indicator 由 solver loop 在 dispatch 前更新，再通过 Row Pipeline `sorted(comparator)` 动态排序；
- `assignedMachine` 是 cross-table key reference，`OperationDefinitionTable.fetch(key)` 递归 materialize `OperationDefinition + List<CandidateMachineDefinition>`；
- 上层 dispatch loop 决定 operation release、indicator 计算、FCFS + SPT 排序和跨 table mutation 顺序，SOMA 不拥有调度策略。

## 8. 非目标

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
- grouped index/order source evidence；
- required lookup missing error 与 optional lookup empty result evidence；
- Row Pipeline lazy terminal evidence；
- ColumnView evidence；
- stale/released/view_pinned evidence；
- runtime stats evidence；
- benchmark smoke 是否执行；
- known limitations。
