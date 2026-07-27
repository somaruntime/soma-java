# SOMA Runtime Scale Production Disposition

类型：Temporary

状态：P7 completed candidate

Owner：P7 code/test/doc/evidence disposition

正式事实源：否

事实范围：`d5bdc9c` live implementation 的 RETAIN/MIGRATE/REMOVE、replacement
closure、P8 slice 和验证责任

非事实范围：重新定义正式 Design、声称 production 已迁移、qualification 或 release
readiness

审计基线：`develop@d5bdc9ca6a90731798957a454a465cce121e5dad`

最后审查日期：2026-07-28

## 1. 裁决方法

本处置先从正式 Design 追踪现有责任和全部 consumer，再决定：

- `RETAIN`：现有 abstraction 与 target invariant 一致，只补组合/evidence；
- `MIGRATE`：责任仍需要，但 API、Owner、lifecycle、physical shape 或 protocol 必须
  原子迁移；
- `REMOVE`：目标明确拒绝，或责任已被正式 successor 完整接管；
- `ADD`：正式目标没有 production Owner，必须新增最终 abstraction。

删除必须满足：

```text
Design intent
  -> current producer / consumer / invariant / evidence
  -> final successor
  -> source + generated + protocol + test migration
  -> old symbol/reference/golden absence
  -> narrow Gate
```

本文件不以 LOC、单实现、单调用者、静态 unused 或测试外观裁剪代码。P8 禁止添加
temporary public API、adapter、reflection/Metadata interpreter、parallel legacy
path或“先占位后重写”的 canonical hot path。

## 2. Live footprint 与直接发现

| module | main Java | main LOC | test/fixture Java | test/fixture LOC | P7 结论 |
|---|---:|---:|---:|---:|---|
| `soma-annotations` | 14 | 236 | 0 | 0 | vocabulary RETAIN |
| `soma-processor` | 20 | 9,653 | 1 | 59 | pipeline RETAIN；model/emission MIGRATE |
| `soma-runtime-core` | 68 | 7,966 | 3 | 1,810 | kernel RETAIN；Metadata/Group/layout/resource ADD/MIGRATE |
| `soma-dataflow` | 96 | 14,296 | 0 | 0 | logical model RETAIN；value/delivery/scheduler/relation MIGRATE |
| `soma-testkit` | 1 | 177 | 309 | 15,290 | evidence architecture RETAIN；fixtures MIGRATE |
| `soma-benchmarks` | 31 | 6,608 | 10 | 3,747 | old baselines RETAIN as historical comparison；qualification ADD |

三个 Example 的当前 footprint 是：

| application | production files/LOC | test files/LOC | P7 边界 |
|---|---:|---:|---|
| grassing individual simulation | 32 / 1,846 | 12 / 1,455 | P8 只迁移 mandatory contract |
| industrial dynamic scheduler | 64 / 4,720 | 11 / 1,334 | P8 只迁移 mandatory contract |
| real-time dispatch rule engine | 32 / 2,031 | 9 / 1,228 | P8 只迁移 mandatory contract；Join result保持Eager |

直接 reference scan 还确认：

- generic `ObjectColumn/ObjectExpression/ObjectValueFlow/ObjectColumnResult` value family
  影响 16 个 production 文件；
- legacy DataFlow `borrow(consumer)` 影响 15 个 DataFlow/processor production 文件；
- repository 内 legacy borrow 的直接 consumer 只有
  `DataFlowSliceECheck`、`DataFlowSliceFCheck` 两份 benchmark test；
- 三个 Example 不消费 legacy borrow，因此 callback pilot 不要求装饰性业务迁移；
- String payload、primary key、Delta 与 generated row/materialization已有实现基础，
  但 secondary String selector仍由 `SomaProcessor.resolveSelectorLeaf`明确拒绝；
- optional arbitrary declared reference 当前已因 `TableFieldType.forBoxed`只接受
  boxed primitive而被拒绝；但classifier与diagnostic尚未以四类体系显式建模；
- current storage column全为 flat array，Group、完整 Metadata phase、generated
  SchemaMetadata、hard `maximumRows`、String profile和分级 ledger不存在；
- current Candidate terminal主要以 universal `int[]` selection执行，Group/Join/
  Window barrier和parallel有独立路径，尚不是一个 bounded physical framework。

## 3. S1：Schema、Descriptor 与 closed type protocol

| current surface | disposition | final Owner/successor | closure evidence |
|---|---|---|---|
| 14 个 `@Soma*` annotation | RETAIN | schema vocabulary | public `javap`、compile fixtures |
| `SomaJavacPlugin` / `CompilerProtocol` / JSR 269 handshake | RETAIN | Java 8 compiler boundary | Zulu 8 positive/negative compile |
| normalized schema/hash、Unicode order、artifact staging | RETAIN | processor normalized model | schema/hash golden、clean repeat |
| `TableFieldType` 与 value leaf model | MIGRATE | internal four-kind classifier：primitive/String/flattened/owned-child | positive/negative classifier matrix |
| boxed primitive admission、arbitrary reference rejection | RETAIN/MIGRATE | 纳入显式four-kind classifier与stable arbitrary-object diagnostic；stable ID + sidecar是application边界 | compile positive/negative；classifier evidence |
| String secondary selector rejection fixture | REMOVE | typed String Key/Unique/Index acceptance；optional selector继续拒绝 | compile/golden/runtime collision evidence |
| generic `ObjectColumn<T>` | REMOVE | concrete `StringColumn`；enum/semantic/value仍为primitive/String leaf column | source/protocol absence、GC cleanup |
| `ObjectExpression/ObjectValueFlow/ObjectColumnResult/ObjectValueConsumer` | REMOVE | `StringExpression/StringValueFlow/StringColumnResult/StringValueConsumer`；flattened value由leaf expressions组成 | public/golden/external/differential |
| `DataFlowBinding.objectValue` / generic object parameter | REMOVE | `stringValue` 与 closed typed parameter/binding methods；consumer control parameter不进入value system | protocol fail-closed + old token scan |
| per-Table default-plan emitter | MIGRATE | one schema-scoped generated `SchemaMetadata` companion | deterministic source/javap/external |
| Descriptor types | ADD | immutable `SomaMetadata` root、Schema/Table/Column/Type/Key/Unique/Index/Ownership metadata | construction restriction、canonical projection |

`@SomaValue` 的 detached object carrier、materialized row和ordinary callback/executor
object不是 live value protocol，继续保留；只有 storage/DataFlow value hot path 的
generic Object family被删除。

## 4. S2：Plan、Effective Metadata、resource profile 与 observation envelope

| current surface | disposition | final Owner/successor |
|---|---|---|
| `RuntimePlan/TablePlan/ChildPlan` immutable result与canonical hash | RETAIN/MIGRATE | schema-seeded Plan Builder → immutable Effective Metadata |
| public raw `RuntimePlan.builder(schemaHash, protocol...)` | REMOVE from application surface | generated `SchemaMetadata.newPlan()`；public generated-protocol helper放在 `.runtime.generated` |
| `TablePlan.initialCapacity`、growth与byte limits | RETAIN/MIGRATE | 增加 non-binding `planningRows`、hard `maximumRows`、workload profile和resolved physical identity |
| free-form algorithm/keySpace/access strategy String | REMOVE | versioned closed internal formula/enum；只通过 Effective Metadata观察 |
| `maximumAggregateStorageBytes` / ownership instance bound | MIGRATE | `SomaGroupPlan` hard envelope；implicit single-root Group同协议 |
| current `TableStats/DataFlowStats/DataFlowExplain` | RETAIN/MIGRATE | module-owned Group/Table Observation、DataFlow Explain、Invocation Observation |
| complete Metadata phases | ADD | Descriptor、Plan Builder、Effective、Runtime topology、Observation/Explain |
| `StringResourceProfile` | ADD | caller-declared `PROFILED_UNVERIFIED` estimate与`UNPROFILED`状态 |
| `SomaGroupPlan` + stable member slots | ADD | multi-schema/multi-instance immutable composition plan |

Builder 与所有 child editor在 build 后失效；修改 builder不修改既有 plan/instance。
Schema hash、plan hash、Group/member identity、dataVersion和structural/membership epoch
保持不同事实。

## 5. S3：SomaGroup、ownership 与 ledgers

| current surface | disposition | final Owner/successor |
|---|---|---|
| `ChildOwnershipRegistry` root/child trust、handle、cascade | RETAIN/MIGRATE | 每个 root仍独立拥有；接入 parent Group member与Table attribution |
| `DenseTableState` operation/epoch/fault contract | RETAIN/MIGRATE | 增加 Group membership、dataVersion、explicit release guard |
| `StorageBudget` | REMOVE after migration | `GroupLedger`唯一 structural/transient hard owner；`TableLedger`只 attribution |
| current `Table.create(plan)` | RETAIN convenience/MIGRATE implementation | implicit single-slot Group |
| explicit Group attach | ADD | reserve → private construct → publish-once transaction；failure全 rollback |
| `SomaGroup` state/metadata/release | ADD | ACTIVE/DEGRADED/FAULTED/RELEASED、stable slots、reverse release |
| DataFlow aggregate guard | RETAIN | Invocation按root opaque identity canonical acquire；不要求同 Group |

Group不能变成跨 root transaction、snapshot isolation或guard prerequisite。必须覆盖
same/cross Group、same schema multi-instance、cross schema、self-join和partial
acquire反向释放。

## 6. S4–S5：Storage、locator、String exact access

| current surface | disposition | final Owner/successor |
|---|---|---|
| packed `[0,size)`、swap-remove、typed primitive columns、presence | RETAIN | single logical storage semantics |
| flat typed arrays | RETAIN as `FLAT` | Small/Medium与point-heavy baseline |
| Large column growth | MIGRATE | `FLAT_HEAD_SEGMENTED_TAIL`，atomic segment stage/publish |
| `GeneratedColumn` cold `Object` staging token | RETAIN internal | allocation-boundary token，不是 row/value carrier |
| `ColumnGroup` growth/resource publication | MIGRATE | layout-aware atomic growth + parent ledger lease |
| `IntKeySpace/Hash*KeySpace` authoritative equality contract | RETAIN | compact flat locator baseline |
| automatic segmented locator | REJECT/DO NOT ADD | 只有future production evidence另行裁决 |
| `GroupedExactIndex` / `ExactGroupCounter` | RETAIN/MIGRATE | String hash+authoritative equality、ledger与growth formula |
| locator/exact full-key duplicate | REJECT/DO NOT ADD | authoritative columns保留唯一 key payload |
| String lifecycle | ADD evidence/MIGRATE cleanup | append/reference retention、equal-value no-op、remove/clear/replace/rollback/release/GC |

Large layout只改变physical backing，不改变 row Index、selector、mutation或generated
public语义。任何 growth在发布前完成checked budget与private allocation。

## 7. S6：Candidate、closed values、Invocation ledger 与 callback core

| current surface | disposition | final Owner/successor |
|---|---|---|
| typed `CandidateFlow` logical stages/one-shot semantics | RETAIN | logical Candidate Owner |
| universal `CandidateSelection int[]` terminal path | MIGRATE | closed physical shapes：direct packed range、direct exact group、bounded vector、barrier vector |
| zero-stage/source-only/direct point fast path | RETAIN/EXTEND | no universal output-sized candidate copy |
| primitive scalar/column result families | RETAIN | Eager Detached default |
| generic Object value family | REMOVE | S1 typed String/flattened leaf family |
| `ExecutionFrame` budget counters | MIGRATE | `InvocationLedger` phase leases：shared/worker/output/task/queue current+high-water |
| known output preflight | RETAIN/EXTEND | checked formula；unknown high-expansion bound fail closed before source enumeration/callback |
| `DataFlowDefinition/Template/Invocation` | RETAIN |唯一 callback/eager lifecycle |
| consumer retained in `Definition` identity | REMOVE | consumer only on one-shot delivery Invocation |
| callback core | ADD | `DeliveryResult(deliveredElements, completed)`、early stop、stable failure、non-escape fence |

Callback只允许同步 one-shot read-only。Iterator、pull cursor、Publisher、async push、
mutation/effect streaming与partial detached result不得新增。

## 8. S7–S8：Relation、GroupBy、Window 与 Delta

| current surface | disposition | final Owner/successor |
|---|---|---|
| typed Group/Join/Window logical shapes、ordering与plain-array oracle | RETAIN | logical semantics |
| GroupBy总是构造member arrays | MIGRATE | aggregate-only/member-enumeration两种 specialized plan |
| Join通用 materialized pair barrier | MIGRATE | N:1/semi/anti/exists access fusion；bounded 1:N build/probe preaggregation |
| String `KeyExpression` generic object component | REMOVE | typed String key component + authoritative equality |
| Window全量 frame rebuilding | MIGRATE where present | incremental add/remove state；无法适用时bounded explicit barrier |
| relation/output unchecked或未知 bound | REMOVE | checked finite formula；unknown-unprovable fail closed |
| generated keyed Delta API/atomic apply | RETAIN | single-aggregate safe-point semantics |
| Delta full staging regardless of changed rows | MIGRATE | changed-row staging与deterministic crossover |

所有 physical choice使用versioned deterministic formula并进入 Explain；TV常数不成为
public contract。

## 9. S9：一个 bounded scheduler

| current surface | disposition | final Owner/successor |
|---|---|---|
| sequential oracle | RETAIN |唯一语义基准 |
| `DataFlowContext` managed/borrowed executor ownership | RETAIN | Context继续唯一 executor lifecycle Owner |
| `ExecutionPolicy` adaptive intent | RETAIN/MIGRATE | formula使用cardinality、kernel、scratch与workers |
| `ParallelCandidateExecution` + `ParallelExecution`平行调度事实 | MIGRATE then REMOVE duplicate | single bounded morsel scheduler |
| threshold-only one-size partition | REMOVE | Segment/Morsel/Execution Vector分责；single Segment可拆morsel |
| worker state与merge | MIGRATE | worker-local scratch/partial；logical morsel ordinal fixed merge |
| nested executor/common pool/shared hot atomics | REJECT/DO NOT ADD | explicit Context、bounded task/queue |

Cancel/deadline/failure必须按logical ordinal选择primary，bounded drain后再释放source
guard和lease。

## 10. S10：Generated delivery、observation、Guide 与 benchmark hooks

| current surface | disposition | final Owner/successor |
|---|---|---|
| Candidate generated Cursor fence | RETAIN/MIGRATE | generated Candidate delivery facade |
| primitive/String Value borrow | MIGRATE | typed value delivery facade |
| Group/Join/Window borrow | MIGRATE | shape-specific delivery facade；共同 callback lifetime |
| old `borrow(consumer)` signatures/classes | REMOVE | Definition→Template→Invocation wrapper；consumer不进入identity |
| `DataFlowStats/Explain` flat fields | MIGRATE | module-owned component composition；delivery/current/high-water facts |
| current two Guide files | MIGRATE | quick start、Metadata/Group、String/resource、DataFlow、failure、parallel、delivery、scale、upgrade |
| current component benchmark model/validator | RETAIN/EXTEND | runtime-scale artifact、strict preregistration与claimAllowed=false |

每个 Candidate/Value/Group/Join/Window callback shape必须独立通过 Q-DELIVERY；失败时
保持 Conformance gap，不能恢复 legacy path。

## 11. Test 与 evidence disposition

| current evidence | disposition | P8/P10 action |
|---|---|---|
| compiler positive/negative fixtures、schema/hash golden | RETAIN/MIGRATE | 新四类classifier、String selector、Metadata输出；arbitrary object negative |
| `table-selector-string` negative | REMOVE as target-inconsistent | replacement为positive selector + optional/unsupported object negative |
| public/generated `javap` golden | MIGRATE atomically | protocol/API clean surface；old Object/borrow/raw plan token absence |
| external Maven dense/keyed/access/child/breadth | RETAIN/MIGRATE | simple/advanced/String/Group/callback/diagnostics journey |
| runtime core/phase scripts | RETAIN/EXTEND | Metadata/Group/layout/ledger/String/lifecycle |
| DataFlow Slice A–F + reference differential | RETAIN/MIGRATE | typed String、closed Candidate、relation/scheduler/delivery |
| legacy borrow checks E/F | MIGRATE | invocation-owned callback + Eager equivalence/failure |
| old component/application performance baselines | RETAIN historical | migration non-regression对照；不算P6 qualification |
| production-scale qualification | ADD | Q-SMALL/FAST、MEDIUM、1M、10M、100M single/double/String、EXPANSION、DELIVERY、SOAK |
| `check.sh` | RETAIN/MIGRATE | P8窄 Gate后，P10纳入全部 required production checks |

测试裁剪只删除已由新 invariant/differential/external/qualification evidence接管的旧
protocol断言，不删除独立语义或failure oracle。

## 12. Documentation 与 Example disposition

- 正式 Design在P6已完成，不因P8实现细节反向降低；
- Implementation Map在每个影响实际代码的slice更新到精确candidate；
- Conformance只在对应production evidence通过后关闭；
- Guide在S10按可编译snippet/external consumer绑定，不复制Design；
- Report保留旧baseline为历史provenance，并为new candidate生成独立evidence；
- 三个Example在P8只做generated Plan/protocol/lifecycle必需迁移；
- P9再逐应用审计Metadata/Group/String/delivery和最佳实践：无偏差即RETAIN；
- RTD sorted joined command保持Eager default，除非P9证明callback有真实产品收益；
- Grassing/Scheduler不为展示新API重写算法叙事。

## 13. P8 原子实施顺序

P8按S1→S10推进；每个slice保持reactor可编译，并形成有意义本地commit：

1. S1 schema classifier、Metadata descriptor与typed String protocol；
2. S2 Plan/Effective/observation/resource profile；
3. S3 Group/ownership/ledger；
4. S4 storage layout；
5. S5 String selector/locator；
6. S6 closed Candidate/value/resource/callback core；
7. S7 relation specialization；
8. S8 Delta staging；
9. S9 bounded scheduler；
10. S10 generated callback/observation/Guide/benchmark hook。

若后续slice依赖前一slice的public/protocol identity，可使用一个commit series，但在
任一已提交边界不能留下两个canonical public/generated path。窄验证失败最多原样
重试一次；第二次仍失败必须诊断并改变方法。

## 14. P7 退出判定

P7已给出：

- 所有S1–S10 target的production Owner与live predecessor；
- Object、borrow、raw Plan、StorageBudget和parallel duplicate的明确successor；
- source/generated/protocol/test/golden/external replacement closure；
- benchmark/Guide/Implementation Map/Conformance/Report责任；
- 三个Example的P8/P9边界；
- rejected方向的absence规则。

因此P8可以开始。该判定不表示任何Conformance gap已关闭，也不把Lab/旧Gate外推为
production evidence。
