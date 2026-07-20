# Java-only SOMA V1 G3 runtime core gate report

状态：passed
Gate：G3 — runtime core gate
唯一 Owner：`soma-runtime-core`
执行日期：2026-07-11
执行人：Codex（实现、自动化验证与证据复核）
验证 commit：`060a6df`（`feat: complete phase 5 capability breadth`）
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（继续 active）

> 当前性说明（2026-07-20）：本报告是 commit `060a6df` 的 v2 G3 evidence 快照，其中 SparseInt、sidecar dirty/rebuild 与 maintained-order material 已由 `4b6fa43` 的 packed/exact v3 切换取代。当前 runtime 形态与 fresh Gate 入口见 [2026-07-17 专题收口报告](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)；本文保留当时 Gate 证据，不定义当前实现。

本报告关闭 G3 required evidence。它不关闭 G5、G6、V1.0 RC 或 release readiness，也不支持任何绝对或相对性能优势声明。

## 1. 目标、范围与事实源

G3 验证 annotation-agnostic runtime kernel 与 generated static binding 是否共同满足 packed/primitive storage、KeySpace、AccessStructures、mutation、lifecycle、materialization、resource bound、typed error 和低干扰 stats 义务。

输入正式事实源：

- `docs/validation-gates.md`；
- `docs/implementation-strategy.md` 的 V1 Capability Ledger；
- `docs/runtime-correctness-model.md`、`docs/runtime-performance-model.md`、`docs/materialization-contract.md`；
- `soma-runtime-core/docs/table-store-contract.md`；
- `soma-runtime-core/docs/runtime-lifecycle-contract.md`；
- `soma-runtime-core/docs/runtime-plan-contract.md`；
- `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md`；
- `soma-runtime-core/docs/runtime-performance-implementation-contract.md`。

输入实现与 evidence artifact：

- `soma-runtime-core/src/main/java` 的 handwritten/runtime generated protocol；
- `soma-runtime-core/src/test/java/com/hgtech/soma/runtime/RuntimeCorePhase1Check.java`；
- `soma-runtime-core/src/test/java/com/hgtech/soma/runtime/KeySpacePhase2Check.java`；
- `soma-testkit/src/test/fixtures/external-maven-*` 的 dense、keyed、access、child、Phase 5 breadth consumer；
- `scripts/check-runtime-core-phase1.sh`、`check-keyspace-phase2.sh`、`check-generated-dense-phase1.sh`、`check-generated-keyed-phase2.sh`、`check-access-phase3.sh`、`check-child-phase4.sh`、`check-breadth-phase5.sh` 与仓库总入口 `scripts/check.sh`。

报告是验证快照，不反向拥有或修改 runtime 语义。

## 2. Gate 判定

| G3 required evidence | 结论 | 主要证据 |
|---|---|---|
| table-local 与 ownership-aggregate invariants | passed | `DenseTableState` prepare/commit、operation/materialization exclusion、`ChildOwnershipRegistry` generation handle/owner/path validation、randomized/oracle 与 failure-atomic consumer |
| packed `[0,size)` | passed | primitive/reference columns同长 growth、remove compaction、survivor row/key locator repair、packed survivor order与 structural epoch检查 |
| primitive columns 与 optional sidecars | passed | boolean/byte/short/int/long/float/double typed columns、`PresenceBitmap`、primitive/enum Column Pipeline 与 ColumnView |
| steady-state allocation/fusion shape | passed | Row Pipeline primitive row traversal、terminal-local reusable Cursor、no-sort `count`/`forEach` fused traversal、source/bytecode forbidden-shape checks |
| KeySpace domain/load/collision/rehash | passed | SparseInt bounded-domain kernel；HashInt/HashLong/HashComposite randomized lookup、collision-heavy probe、tombstone、rehash 与 overflow checks |
| compaction/capacity/scratch | passed | `reserve`、overflow-safe staged growth、update/sidecar/row-operation scratch ceiling/current/high-water、remove marks与sort/selection primitive scratch |
| sidecar rebuild/stats overhead shape | passed | dirty/lazy rebuild、clean traversal、rebuild/dirty/scratch counters、retained/high-water reuse、unrelated update不 dirty |
| child handle/cascade/replacement/pin | passed | opaque generation handle、no share/reparent/cycle、required/optional child、cascade、replacement preflight、subtree pin 与 stale/dangling/wrong-owner/released errors |
| RuntimePlan/MaterializationBudget | passed | immutable canonical identity、create-time compatibility、per-table capacity/scratch bounds、default/per-call五维 budget 与 deterministic estimator |
| Row Pipeline/ColumnView | passed | filter/skip/limit/short-circuit/update/remove/materialize、one-shot pipeline、primitive/enum pipeline、borrowed view pin/stale/released lifecycle |
| structured errors/stats | passed | stable code/category/context、success/failure operation snapshot、KeySpace/sidecar/scratch/child/materialization facts、checked counter overflow |

G3 required evidence无失败项、无豁免项、无跳过项。

## 3. Storage、optional 与 hot-path shape

### 3.1 Packed/primitive/static binding

- live rows始终占据 `[0,size)`；structural remove移动 packed survivor并同步更新 column、presence、KeySpace row locator、selector dirty state和epoch；
- primitive scalar/value leaves落入对应 primitive arrays；enum使用 ordinal/int binding；reference/value payload使用typed reference column，但不存在 `List<Row>`、DTO graph、schema object或metadata interpreter live storage；
- generated Direct、Row/Key/Column Pipeline 与 mutation代码静态绑定 concrete columns/KeySpace/sidecar；public generated API不泄漏 row slot、bitmap word、child handle或runtime generated protocol；
- `check-generated-dense-phase1.sh` 和 `check-generated-keyed-phase2.sh` 同时检查 source/bytecode，不允许 Java Stream、`Object[]`/boxed row buffer、Iterator、per-row Cursor construction或intermediate Collection进入 Row Pipeline hot shape。

### 3.2 Optional word kernels

`PresenceBitmap` 保持present count并提供 bounded `wordAt` 给 runtime primitive/enum Column Pipeline。专项验证包括：

- 257-row bitmap、10,000次 deterministic randomized set/clear/copy oracle；
- all-absent lane不读取payload但保留`scanned == size`统计语义；
- all-present/required lane走连续payload traversal；
- mixed lane按64-bit word迭代present bits，覆盖 row `0/63/64/65` 边界；
- callback在mixed word中失败时仍记录实际 attempted scanned/matched，并返回`callback_failed`；
- external breadth consumer覆盖optional enum Column Pipeline和ColumnView在`0/63/64`上的presence/value/absent error。

### 3.3 Fusion 与 allocation boundary

- 无 maintained/dynamic sort 的 `count` 与 `forEach` 直接扫描source并融合filter/skip/limit/terminal，不构造完整selection scratch；sorted lane只使用primitive row permutation/sort scratch；
- Cursor/MutableCursor只在terminal invocation内创建并按row复用，不能跨回调保存；primitive Column Pipeline不为每行boxing；
- `materialize`、`fetch*`、Key export与caller callback中的stable boxed/value result是显式allocation boundary，不被伪装为non-materializing hot path；
- G3证明的是packed/primitive/fused/allocation-bounded结构和可观察scratch，不声称已得到throughput、latency、heap ratio或相对baseline优势；这些运行时数值lane仍由G5 benchmark evidence负责。

## 4. KeySpace、capacity 与 failure atomicity

### 4.1 SparseInt domain

`SparseIntKeySpace` 是self-owned bounded non-negative int kernel，维护fixed sparse domain和packed dense key-by-slot：

- `maximumKey`、`sparseCapacity`、`denseCapacity`可观察；
- out-of-domain lookup返回missing，out-of-domain mutation fail fast；
- remove修复移动key的sparse locator，clear保留已分配capacity；
- `Integer.MAX_VALUE`等不可表示array domain在分配前被拒绝；
- randomized packed-slot oracle验证insert/remove/lookup与`size`一致。

当前 generated scalar key的canonical路径按正式首个binding使用HashInt/HashLong/HashComposite；本报告不声称存在自动SparseInt selection或静默fallback。因而也不存在“Sparse失败后无观测切Hash”的隐藏行为。未来若RuntimePlan引入Sparse阈值/fallback，必须additive进入正式plan identity并新增对应G3/G5证据。

### 4.2 Hash domain、collision、rehash 与 stats

- HashInt、HashLong、HashComposite使用primitive open-addressing arrays；composite/String candidate只保存hash、bucket state与packed row，generated code对同hash candidate执行full static leaf/String equality；
- `Aa`/`BB` String collision consumer与collision-heavy randomized keyspace证明hash collision不是duplicate或missing；
- remove写tombstone，compaction修复survivor row locator；后续insert/growth boundary可重建并清除deleted bucket；
- rehash先在local primitive arrays完成全部live identity重插和计数校验，再一次发布arrays、used和metrics。allocation、counter overflow或校验失败不会发布partial mapping；
- generated `addBatch`/`replaceAll`先完整stage新的KeySpace并checked继承since-reset metrics，随后才提交columns与KeySpace引用；duplicate、invalid key或staging失败保持size、epoch、row facts与旧identity mapping；
- `capacity`、`used`、probe/collision/rehash since-reset counters进入`TableStats`。`resetStats()`只清零累计指标，不改变capacity、used、tombstone或live identity；staged replacement继续携带历史metrics；
- expected size、bucket capacity、peak estimator与metric addition均做overflow/range检查。

### 4.3 Reserve、growth 与 scratch

- generated table正式提供`reserve(int)`；它只改变physical capacity，保持size与logical facts，negative/range/estimated allocation错误在publication前失败；
- column capacity由`ColumnGroup`整体stage后commit，避免一部分column增长成功、一部分仍旧capacity；
- `maximumUpdateScratchBytes`、`maximumSidecarScratchBytes`、`maximumOperationScratchBytes`分别约束update、selector rebuild以及selection/sort/remove aggregate；超过上限返回`memory_limit_exceeded`；
- stats区分current retained bytes与lifetime high-water，`resetStats()`不擦除resource fact；release归零可释放的current，clear按契约保留可复用capacity；
- sidecar rebuild计算old/new arrays瞬时共存peak，operation scratch按pipeline/sort/remove primitive arrays aggregate计费，避免把隐藏growth或rebuild storm当成steady-state成本。

## 5. AccessStructures、child、lifecycle、errors 与 materialization

### 5.1 AccessStructures 与 sidecar

secondary index、unique与maintained order使用primitive `RowPermutationSidecar`、candidate range与stable row tie-break。证据覆盖：

- clean/dirty/lazy rebuild、same-size scratch reuse、retained/high-water bytes；
- only-dependent selector invalidation；unrelated field update不dirty sidecar；
- index/order/grouped source、dynamic sort、unique duplicate preflight；
- append/update/remove/replace failure保持visible facts和selector一致，structural compaction后重建得到正确source；
- sidecar dirty/rebuild/scratch、operation scanned/matched/changed与result delta可观察。

### 5.2 Parent-owned child 与 aggregate lifecycle

- parent row只保存opaque generation handle，registry使用primitive/object parallel arrays与identity open addressing；Java List/Map只存在于Batch input或detached materialization；
- ownership graph是single-owner forest，拒绝share、attach/reparent、duplicate identity、direct/indirect cycle、wrong owner与dangling handle；
- required/optional dense/keyed child区分logical empty、absent与present-empty；delete/clear/release级联，slot reuse提升generation使旧handle失效；
- replacement在publish前完成construction、ownership、budget/aggregate pin preflight；失败保留旧subtree；
- parent/descendant ColumnView pin、materialization reentrancy和release state跨aggregate协调；外部consumer覆盖cascade、replacement、compaction、stale/dangling/released与carrier failure propagation。

### 5.3 Lifecycle 与 structured failures

Runtime state覆盖active operation、materialization、borrowed view pin、structural epoch、owned/released terminal state：

- structural mutation使旧view/mutator/cursor语义按契约失效；view pinned时拒绝会搬移storage的操作；view close后不可继续读取；release幂等且所有data access返回`table_released`；
- callback RuntimeException封装为`callback_failed`并保留cause；`Error`不伪装成recoverable SOMA failure；carrier constructor/initializer RuntimeException与Error按materialization契约原样传播；
- duplicate/missing/invalid null或floating、stale/released/view pinned、allocation/memory/materialization budget、ownership与compatibility等错误使用stable code/category/bounded context；
- operation成功/失败均关闭active state并记录实际attempted scanned/matched、changed、outcome与error code；checked counter overflow进入`internal_invariant_violation`而不是静默回绕。

### 5.4 RuntimePlan 与 materialization

- `RuntimePlan`、`TablePlan`、`ChildPlan`与`MaterializationBudget` immutable；canonical JSON/hash不依赖默认Locale/timezone；effective plan变更改变identity；unknown/missing/duplicate/inapplicable配置fail closed；
- create boundary验证schema hash、generated/runtime/plan/estimator protocol与table/access identity，不把compatibility检查延迟到hot path；
- default/per-call materialization budget覆盖ownership depth、table instances、rows、leaf values、estimated allocation bytes，并以整个invocation为计数域；
- recursive parent/child materialization先进行完整account/path/ownership/budget与controlled allocation preflight，再构造公开carrier/List/Map；任一descendant失败不返回partial graph、不修改table/epoch；
- evidence覆盖single row、whole dense List、whole keyed Map、Row Pipeline sequence、Key export、required/optional child递归、五维boundary/overflow、exact estimator、allocation failure与detached result。

## 6. Validation record

### 6.1 完整命令

首先对Phase 5实现工作树执行完整验证：

```text
./scripts/check.sh
```

该入口实际串行执行：

```text
./scripts/check-docs.sh
./mvnw -B -ntp verify
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-testkit-phase4.sh
./scripts/check-value-modifiers-phase5.sh
./scripts/check-defaults-phase5.sh
./scripts/check-breadth-phase5.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-external-consumer.sh
git diff --check
```

完整检查通过后只新增了KeySpace metric argument自洽校验；对包含该修正、随后形成commit `060a6df`的最终实现再次执行受影响的完整targeted lanes：

```text
./scripts/check-keyspace-phase2.sh
./scripts/check-breadth-phase5.sh
```

环境记录命令：

```text
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
./mvnw -version
sw_vers
uname -srm
```

### 6.2 环境

- JDK vendor/distribution：Azul Systems / Zulu `8.94.0.17-CA-macos-aarch64`；
- Java：OpenJDK `1.8.0_492-b09`，64-Bit Server VM build `25.492-b09`；
- javac：`1.8.0_492`，本机完整JDK 8 compiler；
- Maven Wrapper distribution：Apache Maven `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- OS：macOS `26.5.2` build `25F84` / Darwin `25.5.0`；
- architecture：`arm64`（Maven报告为`aarch64`）。

### 6.3 结果与 artifact

- 完整`./scripts/check.sh`于2026-07-11 11:14:39结束，exit `0`，最终输出`project-check: ok`；
- 完整运行的Phase 5 breadth evidence：`target/phase5-breadth.K7CTTu`；最后KeySpace修正后的targeted breadth evidence：`target/phase5-breadth.YJbpHd`；
- 相关完整运行artifact：`target/phase1-public-api.N2AFix`、`target/phase0-compiler.Snm2Mc`、`target/defaults-phase5.TgBTCY`、`target/value-modifiers-phase5.yjarrf`；
- breadth artifact包含clean/non-clean incremental逐文件等价、locale/timezone repeat、完整generated type manifest/public javap、Java class major 52、schema/hash、dependency tree、runtime classpath与external consumer执行结果；
- `target/*`是可重新生成的本机临时输出。永久可追溯标识是commit `060a6df`、仓库脚本、fixture/golden与本报告；
- final validation失败项：0；G3豁免项：0；G3跳过项：0。

## 7. V1 scope non-regression

受影响 Capability：`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`、`V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-SECURITY-INTEGRITY`、`V1-EVIDENCE-TOOLING`；generated/external consumer仅作为cross-module evidence contributor，不改变其Owner。

| 审计项 | Phase 5 / G1–G4 联合 closeout 前 | Phase 5 / G1–G4 联合 closeout 后 |
|---|---|---|
| 上述Capability整体状态 | `in-progress` | `evidenced`；G3提供runtime维度证据，由Phase 5与G1–G4交叉evidence统一判定，不由本报告单独关闭 |
| G3 | `not-started` | `passed` |
| G5/G6 | `not-started` | 保持`not-started` |
| V1.0 Goal | active | 继续active |

Phase 5 closeout对Capability Ledger的21项Capability作统一审计并全部判定为`evidenced`。本报告只拥有G3 evidence与Gate结论；跨annotation、processor、generated API、consumer/package边界的Capability状态来自G1–G4报告和Phase 5 closeout的联合证据，不能把`evidenced`误读为由G3单报告独占关闭，也不能据此跳过G5/G6。

审计结论：

- 没有Capability被删除、optional化、改为Lite/MVP、移出V1或移动最终Gate；
- 宪法、Capability ID/完整出口、唯一Owner、Gate定义与release claim边界未改变；Owner文档仅additive固化已实现的exact runtime protocol、resource bound与stats语义；
- canonical live storage/hot path仍是packed primitive/reference leaf columns、presence words、primitive KeySpace/sidecar、opaque child handle与generated static traversal；没有`List<Row>`、DTO graph、reflection、metadata interpreter、Java Stream、boxing或per-row allocation作为正式runtime hot path；
- 没有temporary public/generated API、temporary storage、test-only bypass、future consumer migration或达到V1所需的canonical hot-path rewrite；
- 后续工作只能是additive completion或contract-preserving internal refinement。G5 examples/benchmark、G6 release evidence/support matrix不会反向迁移G3公共契约、核心事实或主执行路径；
- 若未来引入generated SparseInt strategy、threshold/fallback或新的plan dimension，必须以additive contract/identity/evidence完成，不能把本报告解释为已存在自动fallback。

## 8. Known limitations 与允许的 claim

- `SOMA_UNSUPPORTED_JAVAC`未设置，因此unsupported-javac negative lane本次为skipped；这是G2/G4 compiler matrix限制，不是G3 runtime required evidence豁免。本机完整JDK 8 positive lane与transformer缺失时的fail-closed lane已通过；
- 本次只验证Azul Zulu `8u492-b09`、macOS `26.5.2`、arm64。不能外推为其他JDK vendor/minor、OS或architecture支持；正式support matrix仍是G6 required evidence，状态为`not-started`；
- G5 benchmark runner、全部scenario/JSONL与claim-grade performance measurement尚未关闭。G3只允许声明结构满足packed/primitive/fused/allocation-bounded与可观察resource shape，不允许声明“更快”“更省内存”或production规模性能；
- License artifact、SCM/contact、signing/provenance、source/javadoc/checksum、正式package/reproducibility/support matrix与release sign-off属于G6，尚未由本报告验证；
- G3通过不代表V1.0 RC或release ready。只有完整V1 scope及G0–G5 evidence可形成RC候选，只有G0–G6全部通过才能完成总Goal与release readiness。

允许引用的结论仅为：commit `060a6df` 的SOMA Java runtime-core在上述单机完整JDK 8环境中通过G3 required correctness与performance-shape evidence；该结论不跨平台外推，不包含G5/G6、RC、release或性能优势声明。
