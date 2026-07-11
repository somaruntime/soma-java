# FJSP frontier canonical scenario review

状态：历史审查报告；已由`../docs/fjsp-e2e-scenario.md`和`java-v1-g5-examples-report.md`取代
日期：2026-07-07
Owner：`soma-examples`

> 本文记录2026-07-07时间点的设计审查，其中旧schema形态与当时未完成项不是当前事实。当前场景语义只看FJSP正式Owner，当前验证结论只看G5 examples report。

## 1. 审查目标

本报告记录 FJSP 示例场景从“每轮重建候选 workspace”调整为 `MachineCandidate` keyed runtime frontier 后的 canonical scenario review。目标不是评估调度算法优劣，而是确认新的示例路径能继续压测 `soma_java` V1 的正式契约：

```text
schema annotation
  -> processor / codegen
  -> generated table-first API
  -> TableStore runtime internal
  -> Row Pipeline update / dynamic sort / remove
  -> ColumnView / typed errors
  -> gate evidence
```

本报告是审查证据，不是新的设计事实源。若本文与 owner 文档冲突，以 owner 文档为准。

## 2. Canonical scenario boundary

FJSP canonical scenario 使用简化 `FCFS + SPT` dispatch loop：

```text
request boundary
  -> load jobs / operations / materials / machines / processing times / setup times
  -> batch import generated tables
  -> operation release
  -> add feasible MachineCandidate rows
  -> select machine by available time
  -> update candidate indicators for the machine
  -> dynamic sort by FCFS + SPT
  -> mutate operation assignment
  -> mutate machine availability / setup family
  -> remove candidates for selected operation
  -> release next operations
  -> DTO materialization / export boundary
```

场景内 table 分为三组：

| 场景组 | Table | Public kind | Runtime pressure |
|---|---|---|---|
| keyed entity state | `Job`、`Operation`、`Material`、`Machine` | keyed table | stable identity、optional state mutation、order/index sidecar、DTO export |
| keyed lookup table | `ProcessingTime`、`SetupTime` | keyed table | composite key lookup、secondary index、missing lookup semantics |
| keyed runtime frontier | `MachineCandidate` | keyed table | candidate identity、by-machine index、by-operation cleanup、update/remove terminal、dynamic sort |

`ProcessingTime` 和 `SetupTime` 不应建成 `Operation` 的 child table。`MachineCandidate` 也不应作为 `Operation` 的 child table 或每轮临时 workspace。它是 solver loop 增量维护的 runtime frontier：row 存在表示候选有效，失效通过 `remove()` 删除表达。

## 3. Scenario-to-contract matrix

| FJSP action | Annotation / codegen pressure | Generated API pressure | Runtime internal pressure | Evidence pressure |
|---|---|---|---|---|
| load schema and create tables | `@SomaSchema`、`@SomaTable`、value key、optional、index/order selector validation | `create()`、metadata、schema hash、runtime compatibility | `TableLayout`、`LifecycleState` | G1、G2、G4 |
| batch import entity and lookup data | default capacity、batch row count、key uniqueness | `reserve(size)`、`addBatch(batch)` | `RowSpace` allocation、`ColumnStore` writes、`KeySpace` insert、sidecar dirty/update | G3、G4、G5 |
| release operation | `OperationMachineKey` flattening、`ProcessingTime.by_operation` index | `processingTimes.findByOperation(operationKey)`、`machineCandidates.addBatch(batch)` | index source、key insert、by-machine/by-operation index maintenance | G2、G3、G5 |
| select machine | `Machine.by_available_time` order | `machines.byAvailableTime().firstOrThrow()` | order sidecar、lazy rebuild、ordered traversal | G2、G3、G5 |
| update candidate indicators | non-key field mutation | `machineCandidates.findByMachine(machineId).update(...)` | terminal traversal freeze、selector dirty/update stats | G3、G5 |
| dispatch candidate | dynamic row sort | `sorted(dispatchRuleComparator).firstOrThrow()` | temporary row permutation / top-k buffer | G3、G5、benchmark smoke |
| cleanup selected operation | `MachineCandidate.by_operation` index | `machineCandidates.findByOperation(operationKey).remove()` | structural remove、compaction、KeySpace/index cleanup | G3、G5 |
| assign operation | optional field setter、key setter absence | `operations.mutate(operationKey).commit()` | optional bitmap、dirty sidecar、DTO detached copy | G3、G4、G5 |
| update machine | non-key fixed-width mutation | `machines.mutate(machineId).setAvailableFromMinute(...)` | order selector mutation、sidecar dirty/rebuild | G3、G5 |
| read hot column | typed column access | `xxxColumn()` / column pipeline | `ColumnView` acquire/read/release、view errors | G3、G5、benchmark smoke |
| export result | DTO materialization boundary | `fetchAll()` / `fetch(key)` | DTO mapper detached copy | G2、G4、G5 |

## 4. Findings

### F1: 已处理 - FJSP 候选集应是 keyed runtime frontier

`MachineCandidate` 有稳定候选身份，并需要按 machine 查询、按 operation 删除。把它建成 keyed table 比每轮重建 workspace 更符合访问模式，也避免把跨表读取、ready time 计算、builder 构造和 sidecar rebuild 隐藏在一次刷新操作里。

### F2: 已处理 - dispatch rule 不进入 schema order

`FCFS + SPT` 是 solver strategy，不是 table 永久事实。正式 schema 只声明 `MachineCandidate.by_machine` 与 `MachineCandidate.by_operation` 两条稳定访问路径；dispatch 前更新 indicator 字段，再用 Row Pipeline `sorted(comparator)` 选择候选。

### F3: 已处理 - frontier 有效性通过 row lifecycle 表达

`MachineCandidate` 不使用长期 `active` 字段。候选 row 存在即有效；operation 被选中后，`findByOperation(operationKey).remove()` 删除所有相关候选。

### F4: 已处理 - setup 和 missing lookup 语义由 solver 拥有

`SetupTime` 在 canonical FJSP 中是 required setup matrix lookup；缺失 row 表示输入或模型不完整，除非业务规则显式定义默认 `0` 或不可行语义。SOMA runtime 只提供 typed missing / empty result，不解释业务可行性。

### F5: 待后续证据 - frontier diagnostic benchmark 仍需模块级契约

根级 performance model 和 `soma-benchmarks/docs/README.md` 已记录 benchmark lane 缺口。后续 benchmark contract 应单独观察 `MachineCandidate.addBatch`、indicator `update`、dynamic sort、`remove` cleanup 与 dense workspace 变体之间的成本差异，不能用单个 FJSP 总耗时替代。

## 5. Gate mapping

| Gate | FJSP frontier scenario 应提供或引用的证据 |
|---|---|
| G1 | FJSP schema 的 annotation/type/key/index/order/optional cases；invalid selector compile failure |
| G2 | `OperationKey` / `OperationMachineKey` / `SetupTimeKey` normalized golden；`MachineCandidate` by-machine/by-operation source golden；`Machine.byAvailableTime` order source golden |
| G3 | `TableStore` component invariant；batch import；duplicate/missing key；row move；sidecar dirty/rebuild；Row Pipeline update/remove；dynamic sort；ColumnView stale/released/view_pinned |
| G4 | Java 8 package smoke：generated FJSP tables compile/run；schema hash/runtime compatibility metadata readable |
| G5 | FJSP request -> loader -> generated tables -> frontier dispatch loop -> exporter smoke；index/order source；dynamic sort；Row Pipeline update/remove；ColumnView；typed lifecycle errors |
| G6 | release readiness report 引用 G1-G5，不直接把本报告当作 runtime 证据 |

## 6. 结论

FJSP frontier canonical scenario 能作为 V1 设计压力测试的第一条标准场景。当前正式文档已经把候选集建模统一到 `MachineCandidate` keyed runtime frontier，并把 dispatch rule 保持在 solver loop。剩余风险不在 schema/API 口径，而在后续 benchmark evidence contract 和 G2/G5 实现证据。
