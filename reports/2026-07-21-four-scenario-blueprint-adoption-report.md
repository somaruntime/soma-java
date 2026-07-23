# 四场景 Blueprint 采纳专题治理报告

类型：Governance Report

状态：历史治理证据；其当时实现、正式事实提升、完整 Gate 与 Temporary 退役均已完成

Owner：SOMA Java 四场景 Blueprint 采纳治理

受众：项目 Owner、场景维护者与 Gate reviewer

实现基线：`a137b109ea42e90d609b98bc850f7a6a74d34d32`

适用版本：commit `a137b10`

事实范围：commit `a137b10` 时 FJSP、VRP、Simulation、Game 的 executable 采纳、fixtures、benchmark 映射、一致性与验证

非事实范围：core Design/public API 重新定义、普遍性能优势、G6 或 public release readiness

输入事实源：`a137b10` 时的 Blueprint/Design、source/fixtures、Implementation Map/Conformance 与该次可重放验证

最后审查日期：2026-07-21

## 1. 结论

> 本报告只保留 `a137b10` 时点的治理 provenance。旧四场景已由 2026-07-23 参考应用边界治理退役，本报告不再拥有 current G5、产品能力或当前示例导航。

项目 Owner 接受的四个新场景目标已完成实现采纳。`a137b10` 不只替换 schema 名称，而是同步迁移了 data role、identity、candidate/order、application-owned structure、failure protocol 和 hot-path 边界。当前 Implementation Map、Conformance、developer current Report、phase-6 fixtures 与 benchmark lanes 已重新绑定到该实现基线。

| 治理面 | 结论 |
|---|---|
| FJSP | 已采纳 unique successor、完整 preflight、FCFS/SPT indicator、publish-before-heap 与 checked/fail-stop commit |
| VRP | 已采纳 definition/assignment/workspace 拆分、全 insertion ordinal、hard-constraint projection、stale guard 与 authoritative-first commit |
| Simulation | 已采纳 definition/vector 拆分、外部 event heap、relative nanoseconds、derivative staging 与 numeric fail-stop |
| Game | 已采纳 definition/state 拆分、keyed tile/occupancy、action revision、cache rebuild 与 damage total order |
| 横向证据 | schema/hash、generated/public facts、scenario markers、benchmark schema/validator 和 current docs 已同步 |
| core / release | 无 core Design/public API 变更；G6 继续 blocked |

## 2. 意图与范围

本专题要消除“Blueprint 已展示新的最佳实践，current executable example 却仍教用户走旧路径”的分裂。授权范围包括四场景 schema/application/test、processor golden、phase-6 fixtures、benchmark、Implementation Map、Conformance、developer current Report 和 current evidence。

本专题没有修改 annotation 或 generated/runtime public API，没有恢复 Sparse Set、maintained order、range index、stable physical Index 或跨 root transaction，也没有处理 SCM、publishing、support matrix 或其他 G6 事项。

## 3. 四场景采纳结果

### 3.1 FJSP

- `OperationDefinition.by_job_sequence` 成为 secondary unique access，删除无消费方 machine/setup selector；
- import 在首次 authoritative mutation 前校验非负值、identity/reference、sequence 完整性、candidate 唯一性、setup matrix 完整性和时间上界；
- indicator 严格使用 FCFS=`effectiveReady`、SPT=`setup+processing`，未 refresh row 不可被选中；
- frontier 先完整 stage/publish，再 refresh application-owned indexed heap；machine Table 仍是事实源；
- solver 复用 Batch 与 primitive scratch，时间运算使用 checked arithmetic，失败后实例 fail-stop。

### 3.2 VRP

- `CustomerDefinition`、`CustomerAssignment`、`UnassignedCustomerRow` 分开 input/result/derived state；
- one-active-route-per-vehicle 使用 `Route.by_vehicle` secondary unique，route visits 仍为 parent-owned dense child；
- candidate 是无 maintained index 的 dense workspace，覆盖 empty/non-empty route 的全 insertion ordinal、容量与时间窗后缀传播；
- total comparator、route-version stale guard 和 required directed travel lookup 均显式化；
- assignment/visits/route 是权威事实，unassigned/candidate 只是可重建 derived workspace。

### 3.3 Simulation

- definition Table 不再保存 mutable numeric shadow，`StateVectorRow` 是唯一 numeric authority；
- Java 8 `PriorityQueue` 按 `(simulationTimeNanos, sequenceNo)` 保存 authoritative pending events，Table event row 只是可选 projection；
- simulation clock 统一为 session-origin relative nanoseconds，不使用 `DATE_TIME` 表达模拟时间；
- event-boundary integration、derivative staging、finite/scale validation、reusable scratch 与 trace batch 已可执行；
- numeric mutation 先完整 stage，失败不发布 partial vector，无法保证时实例 fail-stop。

### 3.4 Game

- player/unit definition 与 mutable state 分离，tile definition 和 occupancy cache 以 `GridPosition` 分别 keyed；
- `GameUnitState.position` 是权威位置，occupancy 可增量更新，也可从 live units 重建；
- selected-unit move workspace 不重复 unit identity，跨 operation 保存 application `action context + generation + pathing revision`，不保存 SOMA Index；
- move 先提交 unit authoritative state，再更新/rebuild occupancy；
- damage 使用 `(resolutionOrder, sequenceNo)` total order，先进入 reusable primitive staging，再执行跨表 mutation，失败时 fail-stop。

## 4. 横向证据重绑

- phase-6 四份 canonical schema JSON/hash 已刷新；当前 hash 为 FJSP `194193e45183a724774828595744dc14bb45365f45f23442c9586a5f3a08632d`、VRP `da1e5bdd4fad6492f6502e3ec4454811934dee2d3b72d7aad5fd0559744df59d`、Simulation `b617064e99e112d9a8865a8f6d0301ea4e915cd5adb2d207bd58657e5d43a616`、Game `e1b47a2a18c0455b5442b2d40fda3a6cee7fc997f2c02a3b5441666bd321b50f`；
- examples 共83个 Java source，manifest 固定222个 generated top-level type，phase-6 Gate 生成782个 Java 8 class；
- public API facts、generated type manifest、lane markers、Access Pattern Card width 与 scenario output 已同步；
- smoke benchmark `generated.exact_index_incremental_lookup` 使用 keyed FJSP `MachineCandidate` exact access；
- `generated.dense_scratch_replace_sort` 使用无 maintained index 的 VRP candidate workspace，不再把 dense sort 写成 exact-index evidence；
- JSONL schema 升级为 v4，strict validator、negative artifacts、hot-column/accounting 与 checksums 已同步。

2026-07-11 的 G5 root/module reports 保留当时的 v2 evidence，只在页首补充 current/superseded 导航；本专题没有改写历史测量或把它们冒充为当前场景证据。

## 5. Conformance 与 scope non-regression

- `CF-001` VRP data-role split、`CF-002` Simulation numeric source 和 `CF-003` Game tile/cache split 已由实现、fixtures、scenario checks、benchmark 映射与 current docs 共同关闭；
- FJSP 目标一致性在重新采纳后通过 phase-6 adoption lane 防回归；
- `CF-005` 继续限制性能 claim，`CF-006` 继续使 G6 blocked；
- `@SomaKey`、`@SomaUnique`、`@SomaIndex`、Index/IndexSnapshot、packed swap-remove、exact-index eager maintenance 与 child ownership 的 core 语义未变；
- 无 temporary public/generated API、无旧 schema 兼容分支、无 reflection/Stream/boxed collection hot storage、无隐藏的跨 Table transaction 声明。

## 6. 验证记录

已通过的窄验证：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check-examples-phase6.sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check-benchmark-smoke.sh
```

结果：

- examples clean build、222 generated types、782 major-52 classes、四场景 schema/public/lane/scenario checks 全部通过；
- benchmark primary/repeat 各20条record，36条serialized negative path 全部 fail closed，v4 strict validator 通过；
- 实际环境为Azul Zulu OpenJDK `1.8.0_492-b09`、Maven Wrapper/Apache Maven `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64。

在当前正式投影和 Temporary 退役后执行了最终完整验证：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check.sh
git diff --check
```

结果为`project-check: ok`。Gate覆盖docs/scope、全模块Maven、build governance、public API、compiler/codegen、runtime/exact index/KeySpace、dense/keyed/access/child/breadth external consumers、diagnostics、四场景、benchmark smoke、component allocation与FJSP allocation/GC。`unsupported-javac` diagnostic lane因未设置额外编译器按脚本约定skip；正式JDK 8 compiler authority已通过。

最终evidence：

- examples：`target/phase6-examples.UcC1Sm`，222 generated types、782 major-52 classes；
- benchmark：`target/benchmark-smoke.57y6bM`，primary/repeat各20 records、36 negative paths，primary SHA-256 `57bbd5d061335c4fc3c82aa808ca95dc9c943d38e4dc759c1f812d34b2ab190c`；
- component：`target/post-cutover-components.OtjbGW`，artifact SHA-256 `6f0220a708e33ab4753ac5ebb83363011d50af3c43d16907d87364346b4b9539`；
- FJSP allocation/GC：`target/fjsp-allocation-gc.acOqnF`，artifact SHA-256 `8d2244454786db6a1b96ce855fb1e07f53b49ffceccf120db04fbfee758afab3`。

`target/` 是可删除的本机重放产物；长期事实依然由 source/fixtures/scripts/正式报告拥有。本机通过不外推为其他JDK/OS/architecture的支持矩阵。

## 7. Temporary 退役与剩余边界

Blueprint 审查专题的长期事实已进入正式 Blueprint；本采纳专题的长期事实已进入代码、Implementation Map、Conformance、developer current Report 与本 Governance Report。`docs/temp/blueprint-practice-governance/` 与 `docs/temp/four-scenario-blueprint-adoption/` 已删除，没有作为历史并行 Owner 保留。

本专题不使 G6 解除 blocked，也不支持“已公开发布就绪”、跨环境 SLA 或通用性能优势声明。旧 FJSP A/B 只能作为 application heap 决策的历史证据；因 dispatch/input/workflow 已变化，不得与当前场景直接比较为 runtime 性能变化。
