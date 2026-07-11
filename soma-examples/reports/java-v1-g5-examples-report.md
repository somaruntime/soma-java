# Java-only SOMA V1 G5 examples evidence report

状态：examples-evidence-passed；G5 overall 已由 root closeout report 关闭
Gate：G5 examples/benchmark gate（本报告只拥有examples部分）
Owner：`soma-examples`
执行日期：2026-07-11
执行人：Codex
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（G6未通过，当前blocked）

本报告证明四个Java 8 formal scenarios及其Access Pattern Card对应的可运行examples evidence通过。`soma-benchmarks`的runner、required lanes和结构化JSONL由其独立Owner report证明；两者已由`reports/java-v1-g5-examples-benchmark-gate-report.md`联合审查并关闭G5。本报告本身仍不单独拥有G5或release readiness。

## 1. 验证对象与artifact

- implementation commit：`2597c81895ea024867b2c8bb2eafab175e6cea5c feat: complete phase 6 functional rc`；
- artifact：`com.hgtech.soma:soma-examples:0.1.0-SNAPSHOT`；
- 最终Zulu全量artifact SHA-256：`4e93860210d7614780f311b14be7293547ad954ba8eb2cd1024cd0c5efe63334`；该checksum只绑定本次SNAPSHOT validation artifact，不是G6 immutable release provenance；
- 可重放入口：`scripts/check-examples-phase6.sh`；
- 本次最终Zulu全量evidence目录：`target/phase6-examples.Wt58xz`；Corretto全量重放目录：`target/phase6-examples.BxKJHi`；运行目录可删除，长期证据是scenario source、exact golden、检查脚本和本报告；
- Java source：70个；generated顶层Java type：200个；scenario/lowered/generated class：711个，全部classfile major 52。

四个canonical schema identity：

| Scenario | Canonical schema hash |
|---|---|
| FJSP | `2637e877ebd81f680a882a4ef3d1f3f9d8a499d59373998de29e9cd186e1f985` |
| VRP | `a91a785c355d89cfa9a18336b249a94cda709e6f27ceaf4edc6b13b1a3a9d00d` |
| Continuous simulation | `1e072f120f35a7dcc08258016fbf844df8880e375aec2fd1a92f4698136f63b6` |
| Game runtime | `5860c82b313f84e0d8aa9d51b5ac115639ac4d2b19656ab0103b5d232ed6e088` |

## 2. FJSP canonical E2E

`FjspScenario`实现并断言以下完整路径：

```text
request facts
  -> Job/Operation/Material/Machine/SetupTime generated tables
  -> OperationDefinition grouped row facts + candidateMachines live child facade
  -> operation release
  -> MachineCandidate keyed frontier
  -> Machine.byAvailableTime maintained order
  -> findByMachine grouped indicator update
  -> dynamic FCFS + SPT sorted firstOrThrow
  -> application-ordered assignment + machine commit
  -> findByOperation grouped remove/compaction
  -> JobResult / OperationAssignment detached export
```

场景没有用每轮`replaceAll`伪装frontier，也没有把dispatch rule固化为schema order。`MachineCandidate` row存在表示候选有效，选中operation后通过`findByOperation(...).remove()`删除全部候选。operation release由`findByJobSequence(jobId, sequenceNo)`及其owned candidate child驱动，不退化为definition全表扫描。

跨root table的assignment、machine、frontier和job-state提交顺序由scenario controller显式拥有；代码没有暗示SOMA提供跨表transaction或自动compensation。`SetupTime`是required keyed lookup，缺失明确暴露`missing_key`，没有由runtime猜测默认setup业务语义。

第一、第二道operation都完整执行release、indicator update、dynamic sort、assignment、machine mutation与grouped remove；第二道明确通过`SetupTime.fetch`取得positive setup `2`。detached`OperationDefinition + List`只在独立materialization assertion中构造，不进入release hot path。

实际结果：2个assignment；keyed frontier完成两轮child release、grouped update、dynamic sort、grouped remove；sidecar rebuild为5次且可从`TableStats`读取。

## 3. 其余formal scenarios

### 3.1 VRP construction

- `Route.visits`是parent-owned dense child，required empty与live child facade均被执行；
- `Vehicle/VehicleId`、`byVehicleId`、`Route.byVehicle`与`Route.byRouteId`均由scenario真实执行；
- `TravelCost`是required composite-key lookup；
- `UnassignedCustomerRow.byDueThenInput`作为derived frontier执行并在assignment后同步remove；
- `InsertionCandidateRow.byBestDelta()`选择当前workspace最小候选；
- route child replacement、routeVersion/load mutation、Customer state/optional assignment与derived candidate cleanup由application按序提交；
- `RouteTable.fetch(routeId)`递归materialize detached `Route + List<RouteVisitRow>`；hot route traversal使用`routes.visits(routeId)`，没有把materialized List变成live storage。

实际结果：1个route visit、1个owned child instance。

### 3.2 Continuous simulation

- `StateVectorRow`是数值事实源，typed `DoubleColumnView`先读后关闭，再由Row Pipeline update写入；
- `Tank/TankId.byTankId`、`Valve.byFromTank/byToTank` topology access真实执行；Tank/Valve cache只在step boundary从StateVector同步；
- `FlowCoefficient`是required keyed lookup；
- `PendingEventRow.byEventTime()`按event time/sequence消费，event应用后再单独remove/compact；
- `TraceSampleRow`只作为detached trace/export buffer，不反向成为state fact source。

实际结果：2个changed vector rows、2个trace samples。

### 3.3 Game runtime

- `GameUnit.byTurnOrder()`和`findByPlayer()`分别验证maintained order与grouped index；
- `Player` keyed state真实导入并在damage commit后更新score；
- `AbilityCost`是required composite-key lookup；
- `MoveCandidateRow`是selected-unit dense workspace，按`byTotalCost()`选择；
- `GameUnit.position`先提交为authoritative fact，`MapTileRow.occupantUnit`随后作为cache更新；occupancy验证使用fused Row Pipeline，不使用whole-table materialization后的List位置访问；
- `PendingDamageRow`按resolution order消费，写回keyed unit后clear，不被当作history/replay log。

实际结果：2个unit，move/occupancy/damage完整flow通过。

## 4. Generated API、error、lifecycle 与stats evidence

examples直接由annotation schema通过本机full JDK 8 transformer+processor生成API；POM显式配置`-Xplugin:SomaValue`和annotation processor path，`soma-processor`保持provided/build-only。未手写generated facade或把schema object、DTO/Collection graph变成runtime live storage。

实际覆盖：

- `create`、`reserve`、`addBatch`、`replaceAll`、`containsKey`、`fetch`、`find`；
- owned child facade、child replacement与recursive `List` materialization；
- grouped index、maintained order、dynamic `sorted`、`findFirst`/`firstOrThrow`；
- point `mutate(...).commit()`、Row Pipeline `update`/`remove`、dense `clear`；
- typed ColumnView、optional presence predicate、default/explicit materialization boundary；
- `RuntimePlan.schemaHash()`、runtime plan identity和`TableStats`的sidecar/keyspace/child/materialization事实。

实际E2E failure/lifecycle：

| Evidence | 结果 |
|---|---|
| duplicate key | FJSP assignment重复插入得到`duplicate_key` |
| required lookup missing | FJSP SetupTime与VRP/Simulation lookup得到`missing_key` |
| optional lookup empty | FJSP未release candidate的`find`返回empty，不被误报为failure |
| empty required result | empty grouped frontier `firstOrThrow`得到`empty_result` |
| optional presence | generated `lastSetupFamilyAbsent()`与detached null边界一致 |
| view pinned | active machine ColumnView下structural clear得到`view_pinned` |
| released view | close后的ColumnView读取得到`released_view` |
| released table | release后的table访问得到`table_released` |
| schema/runtime/stats | 四个schema hash可读取；plan/stats identity一致；sidecar/keyspace/child counters可读 |

`ScenarioSuite`还输出四条稳定lane marker，检查脚本与
`expected-lane-markers.txt`逐行exact比较，分别覆盖FJSP errors、FJSP
lifecycle、FJSP stats以及VRP/Simulation/Game Owner breadth；缺少或改变任一marker
都会使examples检查失败。

`stale_view`需要绕过active-view structural pin或使用runtime state harness，不适合通过public E2E强造；本报告按FJSP E2E Owner允许的引用边界引用`soma-runtime-core/reports/java-v1-g3-runtime-core-report.md`及`RuntimeCorePhase1Check.testViewLifecycleState`。invalid selector属于compile/processor negative fixture，引用`soma-processor/reports/java-v1-g1-schema-processing-report.md`和`java-v1-g2-code-generation-report.md`，不在valid scenario source中保留无法编译的伪代码。

## 5. Access Pattern Card 对应证据

正式Card仍由以下Owner文档拥有：

- `soma-examples/docs/fjsp-runtime-state-example.md`；
- `soma-examples/docs/vrp-runtime-state-example.md`；
- `soma-examples/docs/simulation-runtime-state-example.md`；
- `soma-examples/docs/game-runtime-state-example.md`。

`ScenarioSuite`为每张Card输出稳定、可检查的correctness evidence摘要：

```text
access-pattern-card scenario=fjsp paths=child-release,keyed-frontier,grouped-update,dynamic-sort,grouped-remove assignments=2 sidecarRebuilds=5
access-pattern-card scenario=vrp paths=route-child,travel-lookup,insertion-order,route-rewrite visits=1 childInstances=1
access-pattern-card scenario=simulation paths=state-vector,event-order,event-remove,trace-export changedRows=2 traceSamples=2
access-pattern-card scenario=game paths=unit-order,ability-lookup,move-workspace,occupancy-cache,damage-buffer units=2 sidecarDirty=0
```

这些小规模结果只证明Card的核心API路径可运行。Card要求的规模、hot columns、touched bytes、selectivity、mutation/read ratio、optional/child density、JIT warmup/forks、stats mode和export frequency仍由`soma-benchmarks`required lanes拥有，本报告不以examples总耗时或上述计数作性能优势声明。

## 6. Validation record

执行命令：

```text
./mvnw -B -ntp -pl soma-examples -am clean verify
$JAVA_HOME/bin/java -cp soma-examples/target/classes:soma-runtime-core/target/classes \
  com.hgtech.soma.examples.ScenarioSuite
./scripts/check-examples-phase6.sh
git diff --check
```

检查脚本同时验证四份checked-in canonical schema JSON/hash exact golden、完整200-type generated manifest、关键public `javap` API facts、711个classfile major 52、processor provided scope、无reflection/Java Stream runtime path和examples JAR checksum。结构guard明确拒绝FJSP release helper调用`definitions.fetch`，并拒绝Game `byGridPosition().fetchAll()`位置式whole-table materialization；error/lifecycle/stats/Owner-breadth lane markers与checked-in expected逐行exact比较。

环境：Azul Systems, Inc. Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17-CA-macos-aarch64`，64-Bit Server VM build `25.492-b09`；`javac 1.8.0_492`；Maven Wrapper / Apache Maven `3.9.16`；macOS `26.5.2` / Darwin `25.5.0`；`arm64`（Maven `aarch64`）。

结果：Zulu与Corretto两套完整JDK 8下的reactor clean verify、四场景ScenarioSuite和`examples-phase6-check`均通过；最终Zulu evidence为`target/phase6-examples.Wt58xz`；失败项0，waived项0。

## 7. V1 scope non-regression

- 本报告形成时`V1-SCENARIO-BENCHMARK`由`not-started`推进为`in-progress`；benchmark contributor和root集中审查随后已将其推进为`evidenced`；
- 四场景直接使用Phase 5最终annotation、generated API、RuntimePlan/error/stats和canonical packed/primitive/static binding路径；后续不需要consumer/public API迁移；
- Owner、Capability Ledger、Gate、non-goal和release claim boundary未变化；只对FJSP E2E Owner做了最小澄清：canonical release使用live child，detached parent+List只用于materialization/export evidence，从而消除同Owner文档间的hot-path表述冲突；
- 没有temporary public/generated API、手写facade、stub/fake、test-only bypass、temporary live storage/hot path、consumer migration或rewrite；
- Java Collection只用于schema child声明、input snapshot或detached materialization/export边界，不是runtime canonical storage；
- `soma-benchmarks`的benchmark runner、structured JSONL、required component lanes和G5集中closeout完整保留；G6全部release breadth完整保留；
- 后续只能additive benchmark/release completion或contract-preserving internal refinement。如果达到G5需要迁移这些scenario公共契约、核心事实或主执行路径，本checkpoint必须标记blocked。

## 8. Known limitations 与状态结论

- 本机结果不能外推为正式JDK vendor/minor、OS或architecture支持矩阵；
- formal scenario fixture以correctness和API/path coverage为目的，不是完整APS/VRP solver、ODE solver或game engine；
- examples不设置或证明throughput、latency、memory ratio、跨平台或相对性能优势；
- invalid selector和stale view按正式边界引用G1/G2/G3 evidence，没有在valid E2E中伪造；
- benchmark smoke、structured JSONL和required performance lanes不由本报告单独关闭；root G5 report已联合关闭；
- G5 overall现已由root closeout report标记`passed`；G6尚未通过，唯一V1 Goal当前blocked，且不得声明public RC artifact或release readiness。

结论：四个Java 8 formal scenarios与Access Pattern Card对应的examples evidence为`passed`；benchmark required evidence与G5集中审查也已完成，最终Capability状态见root G5 closeout report。
