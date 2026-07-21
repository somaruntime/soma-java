# Blueprint 治理越界影响登记

类型：Temporary

状态：待统一裁决

Owner：SOMA Java Blueprint 内容质量治理

事实范围：本专题发现但不在 Blueprint/Temporary 修改授权内的 Design、实现、测试、Guide、Conformance 或 Report 影响候选

非事实范围：正式偏差结论、修改授权、当前实现能力声明和长期设计

最后审查日期：2026-07-21

## 1. 登记规则

每项候选必须包含来源 Blueprint、观察证据、可能 Owner、建议裁决以及是否阻塞 Blueprint 自身表达。登记只说明需要统一裁决，不等于确认 Design 或代码存在缺陷，也不授权修改。

## 2. 待统一裁决事项

### EXT-001：FJSP 目标与当前可执行场景重新出现差距

- **来源**：[FJSP Blueprint](../../blueprints/fjsp-runtime-state-blueprint.md) 与[产品 Blueprint](../../blueprints/soma-java-product-blueprint.md)；
- **当前证据**：[`OperationDefinition`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/schema/OperationDefinition.java) 仍把 job/sequence 声明为 non-unique index；[`Machine`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/schema/Machine.java) / [`SetupTime`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/schema/SetupTime.java) 仍维护蓝图不消费的 secondary selector；[`FjspCandidateFrontier`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspCandidateFrontier.java) 仍令 FCFS=`operationRelease`、SPT=`processing`，release 期间在 frontier publish 前激活外部 queue，并按调用构造 Batch/Value Object；[`FjspSolver`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspSolver.java) 仍使用 unchecked time arithmetic 和 per-assignment Batch；
- **可能 Owner**：FJSP schema/scenario、processor golden 与 phase-6 fixtures、FJSP invariant/allocation/GC evidence、Conformance；
- **建议裁决**：单独授权 FJSP adoption slice，先固化 indicator/queue/input invariant，再修改 schema、solver、测试和 benchmark；重新判断“FJSP 目标场景一致”的 Conformance 结论；
- **阻塞性**：不阻塞 Blueprint 目标表达；阻塞当前实现一致声明和以旧 FJSP lane 证明新目标。

### EXT-002：VRP canonical model 的差距已超出既有 CF-001

- **来源**：[VRP Blueprint](../../blueprints/vrp-runtime-state-blueprint.md)；
- **当前证据**：current [`Customer`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/Customer.java) 仍混合 input/state/assignment shadow；[`Route.by_vehicle`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/Route.java) 仍为 non-unique index；[`RouteVisitRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/RouteVisitRow.java) 缺少预投影 `LocationId` 且时间字段仍用 minute；[`InsertionCandidateRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/InsertionCandidateRow.java) 仍使用 insert-after/penalty 形态，current [`VrpScenario`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/vrp/VrpScenario.java) 未实现 Blueprint 的完整 ordinal propagation、preflight 与 failure protocol；
- **可能 Owner**：VRP schema/scenario、developer example、golden/fixture/invariant/benchmark、`CF-001`；
- **建议裁决**：把现有 CF-001 扩展为完整 VRP adoption gap，再以有授权专题迁移 schema 与 executable journey；对 flat-vs-child、dense-vs-keyed 的 benchmark 继续使用相同语义，不能把条件式 frontier 当默认目标；
- **阻塞性**：不阻塞 Blueprint；阻塞 VRP current capability、示例最佳实践和场景 evidence 声明。

### EXT-003：Simulation target 需要扩大 CF-002 并迁移 event/time 模型

- **来源**：[连续仿真 Blueprint](../../blueprints/simulation-runtime-state-blueprint.md)；
- **当前证据**：current [`Tank`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/Tank.java) / [`Valve`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/Valve.java) 仍保存可写 numeric shadow；[`PendingEventRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/PendingEventRow.java) / [`TraceSampleRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/TraceSampleRow.java) 使用 millisecond/`DATE_TIME` 形态；executable [`SimulationScenario`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/simulation/SimulationScenario.java) 仍以 Table scan/sort/remove 表达 due event，而不是以 application heap 作为唯一 queue；尚未覆盖完整 derivative staging、relative-nanos boundary 和 trace invariant；
- **可能 Owner**：Simulation schema/scenario、developer example、golden/fixture/numerical invariant/benchmark、`CF-002`；
- **建议裁决**：把 source-of-truth、event heap、relative time unit 和 failure/checkpoint 一起作为一个场景 adoption slice，避免只改字段名而保留双 queue/双状态；
- **阻塞性**：不阻塞 Blueprint；阻塞 Simulation current-conformance 和以现有示例证明新 hot-loop journey。

### EXT-004：Game target 需要扩大 CF-003 为完整场景迁移

- **来源**：[Game Blueprint](../../blueprints/game-runtime-state-blueprint.md)；
- **当前证据**：current [`MapTileRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/game/MapTileRow.java) 仍把 terrain 与 occupant cache 混在 dense row；[`GameUnit`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/game/GameUnit.java) definition/state 未拆分；[`MoveCandidateRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/game/MoveCandidateRow.java) 仍重复 `unitId`；[`PendingDamageRow`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/game/PendingDamageRow.java) 缺少 `sequenceNo`；current [`GameScenario`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/game/GameScenario.java) 没有 keyed coordinate roots、pathing revision/prepared move、occupancy rebuild 和完整 damage staging/fail-stop；
- **可能 Owner**：Game schema/scenario、developer example、golden/fixture/cache/failure/benchmark、`CF-003`；
- **建议裁决**：先裁决 keyed coordinate canonical path 和 definition/state split，再整体迁移 action context、cache recovery 与 damage command semantics；initiative 升序已与 current scenario 一致，无需反向修改；
- **阻塞性**：不阻塞 Blueprint；阻塞 Game current capability、cache correctness evidence 和最佳实践声明。

### EXT-005：正式导航、当前投影和 evidence 必须在采纳后同步

- **来源**：本轮五份 Blueprint 的共同 target 变化；
- **当前证据**：[Current Conformance](../../conformance/current-conformance.md) 仍称 FJSP aligned、只把 VRP/Simulation/Game 三项列为目标差距；[Known Gaps](../../conformance/known-gaps.md) 的 `CF-001..003` 只覆盖旧差距子集；Implementation Map、`soma-examples/docs/`、phase-6 golden/fixtures、G5/性能 Report 均描述或验证 current/旧 target；
- **可能 Owner**：Conformance、Implementation Map、examples developer Report、test/evidence map、current performance/Gate Report，以及 adoption 专题的 Governance Report；
- **建议裁决**：先更新 Conformance 以准确承认新目标差距；每个场景代码采纳后再更新 current Implementation/Guide/fixture，并重跑相称 Gate。历史 Report 保持历史证据，不改写为新目标证明；最终另产出 Governance Report 记录采纳与 evidence；
- **阻塞性**：不阻塞 Blueprint；阻塞“正式文档整体已一致”和任何把旧 Gate/benchmark 外推到新 schema/journey 的声明。

## 3. 已判定无需越界修改

- 当前 canonical Blueprint 路径可由既有 V1 能力族表达：keyed/dense table、primary/secondary exact access、Row Pipeline、IndexSnapshot、ColumnView、typed Batch、parent-owned child、swap-remove 与 application-owned external structure；本轮未识别出必须修改 core Design 或 public API 才能成立的目标。
- FJSP flattened primitive batch writer、Simulation bulk writable primitive path、VRP segment rewrite 等只作为 benchmark 触发的条件式候选；在 evidence 和独立 Design 裁决前不属于当前采纳要求。
- G6 release/publishing 外部事实不属于本专题，Blueprint 没有改变或解除其 blocked 状态。
