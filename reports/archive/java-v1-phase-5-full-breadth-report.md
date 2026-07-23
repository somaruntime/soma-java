# Java-only SOMA V1 Phase 5 full breadth closeout report

归档说明：本报告是特定时间点的历史 checkpoint，保留原结论与术语作为 provenance。

状态：completed
日期：2026-07-11
Checkpoint：Phase 5 processor/codegen hardening 与 full V1 capability breadth
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（继续 active）

本报告关闭 Phase 5 实施 checkpoint，并记录 G1–G4 已通过。它不关闭 Phase 6、G5、G6、V1.0 RC、public release或总 Goal。

## 1. Scope 与 Owner

Phase 5 对 Phase 0–4 已落地的最终架构子集做 breadth completion、compiler/processor/codegen hardening、runtime performance-shape收口和集中验证。21 项非 Phase 6-only Capability 从 `in-progress` 统一推进到 `evidenced`：

1. `V1-ANNOTATION-SCHEMA`
2. `V1-COMPILER-LOWERING`
3. `V1-PROCESSING-MODEL`
4. `V1-SCHEMA-HASH`
5. `V1-PUBLIC-COMPATIBILITY`
6. `V1-GENERATED-API`
7. `V1-DENSE-STORAGE`
8. `V1-ROW-PIPELINE`
9. `V1-COLUMN-ACCESS`
10. `V1-KEYED-IDENTITY`
11. `V1-ACCESS-STRUCTURES`
12. `V1-MUTATION`
13. `V1-CHILD-OWNERSHIP`
14. `V1-MATERIALIZATION`
15. `V1-RUNTIME-LIFECYCLE`
16. `V1-RUNTIME-ERRORS`
17. `V1-RUNTIME-PLAN`
18. `V1-PERFORMANCE-SHAPE`
19. `V1-SECURITY-INTEGRITY`
20. `V1-EVIDENCE-TOOLING`
21. `V1-CONSUMER-PACKAGE`

`V1-SCENARIO-BENCHMARK` 与 `V1-RELEASE-EVIDENCE` 保持原 Phase 6 / G5–G6，不被 Phase 5 completion隐式关闭。

唯一 Owner仍按正式文档分离：annotation schema拥有declaration；compiler integration拥有full JDK 8 lowering/fail-closed identity；schema processing拥有validation/normalization/hash/diagnostics；code generation和Generated API拥有static typed facade；runtime-core各Owner分别拥有packed storage、KeySpace/access/compaction、lifecycle、plan、errors/stats和performance shape；root materialization/build/public compatibility/security contract拥有跨模块边界；testkit只拥有evidence helpers。没有联合 Owner或实现反向拥有产品语义。

## 2. Phase 5 实际实现

### 2.1 Annotation、processing 与 defaults

- 完成 `@SomaDefault` 及 required table field、`@SomaValue` leaf default的声明、validation、normalization和generated Writer application；
- 覆盖 boolean、byte/short/int/long、float/double、String、enum及 DATE/TIME/DATE_TIME semantic defaults；strict floating拒绝非 finite并canonicalize negative zero，ordinary floating保留 NaN/Infinity正式语义；
- 完成 required/optional String、enum、flat/nested value field与scalar value field；optional absence使用presence事实，不使用sentinel；
- parse-phase和processor双层拒绝 `@SomaValue` field上的 key/child/optional非法修饰，FQN、single import与普通同名annotation识别稳定；
- normalized model和canonical schema/hash纳入defaults、reference/enum/value、String keyed child事实；invalid defaults、modifier和schema注入/冲突继续fail closed。

### 2.2 Generated API 与 package breadth

- generated Table/Batch/Writer/Rows/Mutator/Keys支持String/enum/value required/optional读写、clear、materialization和mutation；
- String key使用generated full equality + `HashCompositeKeySpace` raw hash/probe substrate，collision不会被误判为identity，不使用 `HashMap<Key,Integer>`或transient tuple hot lookup；
- Key Pipeline补齐default/explicit `MaterializationBudget` 的 `fetchAll`、`findFirst`、`firstOrThrow`；
- generated Table补齐 `reserve(int)`；所有materializing入口保留两遍preflight/build、共享tracker、budget identity、typed overflow/budget failure与failure atomicity；
- exact generated public manifest覆盖19个顶层类型，public `javap`拒绝runtime internal handle/registry/state泄漏；consumer class与全部lowered/generated companion均为Java 8 classfile major 52。

### 2.3 Runtime、fusion、allocation shape 与可观察性

- optional primitive/enum Column Pipeline按all-present、all-absent和mixed bitmap word lane执行，mixed lane使用word/trailing-zero traversal，覆盖0/63/64边界；
- 无sort Row Pipeline的`count`/`forEach`直接fuse scan，不构造完整selection scratch；operation scratch、update/sort/remove scratch受RuntimePlan上限约束并进入current/high-water stats；
- `reserve`在正式capacity/memory limit内预分配，packed `[0,size)`和事实epoch不被伪修改；
- HashInt/HashLong/HashComposite rehash采用stage-local arrays后一次publish，allocation failure不暴露half-rehashed locator；capacity/used/probe/collision/rehash metrics可读取并可reset，reset保留locator facts；
- SparseInt和Hash KeySpace、access sidecar、compaction、child forest、recursive materialization、RuntimePlan和structured errors/stats保持Phase 1–4语义并纳入集中回归；
- public/generated hot path保持primitive/packed/static binding、Cursor reuse、bounded scratch、no reflection/metadata interpreter/Java Stream/per-row object allocation。

### 2.4 Determinism、incremental 与 external consumer

- external Maven fixture不继承reactor parent，使用独立repository安装artifact，显式激活`-Xplugin:SomaValue`与annotation processor；
- clean compile后对同一source执行non-clean recompilation，classes和generated sources逐文件一致；
- 默认环境与`tr_TR` / `Pacific/Kiritimati`重复构建的generated source、canonical schema、schema hash一致；
- artifact/dependency tree证明processor只在provided/build路径，runtime graph只有annotations/runtime-core且无第三方runtime dependency；
- external consumer运行覆盖defaults、reference/enum/value、optional word lane、String collision/key lifecycle、reserve、plan/stats、budget、String-keyed child和released error。

## 3. 集中 validation 与 evidence

验证对象：implementation commit `060a6df feat: complete phase 5 capability breadth`；reactor artifacts `soma-annotations`、`soma-runtime-core`、`soma-processor`、`soma-testkit` `0.1.0-SNAPSHOT`；external artifact `external-maven-breadth-phase5-consumer-1.0.0-SNAPSHOT.jar`。

集中执行命令：

```text
./scripts/check-value-modifiers-phase5.sh
./scripts/check-defaults-phase5.sh
./scripts/check-breadth-phase5.sh
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-testkit-phase4.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-external-consumer.sh
./mvnw -B -ntp verify
git diff --check
./scripts/check.sh
```

环境：Azul Systems, Inc. Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17-CA-macos-aarch64`，64-Bit Server VM build `25.492-b09`；`javac 1.8.0_492`；Maven Wrapper / Apache Maven `3.9.16`；macOS `26.5.2` build `25F84` / Darwin `25.5.0`；`arm64`（Maven `aarch64`）。

结果：全部专项、Maven reactor verify、docs/public protocol checks、Phase 0–4 regression和完整`check.sh`通过，最终返回`project-check: ok`。完整检查后只增加KeySpace metrics输入自洽保护；implementation commit `060a6df`上重新执行keyspace与breadth targeted validation并通过。失败项`0`，waived项`0`。可选unsupported-javac环境lane因未设置`SOMA_UNSUPPORTED_JAVAC`跳过。

关键 evidence：

- G1：annotation schema、full validation/default/value/child/key/access/type matrix、normalized/hash golden与diagnostic evidence；
- G2：javac 8 lowering/negative matrix、deterministic generated source/resource、exact public/generated manifest、static binding/fusion/no-boxing source/bytecode shape；
- G3：packed/primitive/presence、KeySpace domain/collision/rehash/atomicity/stats、access sidecar、compaction/capacity/scratch、lifecycle/child/materialization、RuntimePlan/budget、structured error/stats与allocation shape；
- G4：独立external Maven consumer、artifact/dependency graph、transformer+processor、Java 8 major 52、schema/hash/runtime plan/stats/materialization/budget、19 generated public types、clean/non-clean incremental与locale/timezone repeat；
- G4完整`check.sh` breadth运行产物：`target/phase5-breadth.K7CTTu`；implementation commit后targeted运行产物：`target/phase5-breadth.YJbpHd`；canonical schema hash `eb4ebee10e77b7f22ada9ad59d9fe064e8a867a7ca03fae105664a3f056dc3ad`；最新consumer JAR SHA-256 `c9ee527700d4b77e2e84b47e2cd9afe0a7ed8d644b43e18e31dc185818209139`。运行目录可删除，长期证据是fixture、expected artifacts、scripts和reports。

Gate状态：G0、G1、G2、G3、G4为`passed`；G5、G6保持`not-started`。G1–G3详细evidence由各自唯一Owner report记录，G4见`reports/java-v1-g4-package-smoke-report.md`。

## 4. Capability 状态变化

| Capability group | Phase 5 前 | Phase 5 后 | 实际 evidence |
|---|---|---|---|
| annotation/compiler/processing/hash | in-progress | evidenced | full declaration/invalid/default/value matrix、javac 8 lowering/identity、canonical schema/hash、clean/incremental/locale repeat |
| public/generated API | in-progress | evidenced | exact manifests、19-type breadth consumer、primitive/reference/value/key/access/child/materialization/budget surface |
| dense/pipeline/column/key/access/mutation | in-progress | evidenced | packed/presence invariant、fusion、word kernels、KeySpace oracle/metrics/atomic rehash、sidecar/dynamic sort/compaction/result stats |
| child/materialization/lifecycle/errors/plan | in-progress | evidenced | owned forest、cascade/replacement/pin、recursive List/Map、budget/path/overflow/failure atomicity、structured stats/errors |
| performance/security/evidence/package | in-progress | evidenced | primitive/static/allocation-bounded shape、resource bounds/integrity diagnostics、full check、independent Maven/package graph/class major |

逐项状态对应第1节21项Capability；没有Capability变成optional、dropped、waived或无目标阶段的deferred。

## 5. Phase 6 保留 breadth

Phase 5 completion不减少以下完整V1工作：

- `V1-SCENARIO-BENCHMARK`：FJSP、VRP、Simulation、Game formal Java 8 scenarios，Access Pattern Cards，frontier/keyed/dense/child-locality/deep-materialization及全部required performance-shape lanes，benchmark runner与结构化JSONL；最终Gate为G5；
- `V1-RELEASE-EVIDENCE`：Apache-2.0正式License artifact、HGTECH/SOMA namespace和SCM/contact、community/security policy、release notes/install、source/javadoc/checksum/signing/provenance、reproducibility、compatibility/support matrix、known limitations、rollback/withdraw与最终gate report；最终Gate为G6；
- G5完成前不能声明V1.0 RC；G6完成前不能声明release ready、公开发布或把本机结果外推为正式支持矩阵。

Phase 6只能在当前最终public/generated contract与canonical hot path上做additive scenario/benchmark/release completion；不得通过替换Phase 5 public API、核心事实、consumer或主执行路径完成。

## 6. V1 scope non-regression

- Capability状态：第1节21项从`in-progress`推进到`evidenced`；两项Phase 6-only Capability保持原Phase/Gate且未删除；
- actual evidence：第3节命令、G1–G4 owner reports、fixture/golden/scripts、external artifact graph、schema/hash、class major、generated manifest与运行结果；
- 尚未实现项及原出口：第5节所有scenario/benchmark项仍在Phase 6/G5，所有release/support matrix项仍在Phase 6/G6；
- Owner、Capability Ledger、Gate、non-goal和release claim boundary未变化；正式Owner文档只在既有V1语义内完成exact validation/runtime protocol，没有反向降低验收标准；
- 后续收敛方式是additive completion或contract-preserving internal refinement；
- 没有temporary public/generated contract、temporary canonical live storage/hot path、test-only bypass、consumer migration、核心事实迁移或rewrite；
- 当前public/generated API已由独立external consumer直接使用，canonical runtime是packed primitive/reference columns、presence words、primitive/hash KeySpace、primitive sidecar、owned child handle和static generated traversal；Java Collection只存在于input snapshot或detached materialization boundary；
- 若Phase 6发现必须迁移上述公共契约、核心事实或主路径，本checkpoint必须回退为`blocked`，不能用后续重写掩盖。

## 7. Known limitations

- validation只证明上述本机Zulu JDK 8、macOS arm64环境通过，不能外推为正式JDK vendor/minor、OS或architecture支持矩阵；G6 support matrix仍为`not-started`；
- artifact仍是`0.1.0-SNAPSHOT`验证产物，不是immutable signed release；
- unsupported-javac可选环境lane本次未执行；
- Phase 6 formal examples、Access Pattern Cards、benchmark runner/JSONL及G5尚未完成；
- License/SCM/contact/source/javadoc/checksum/provenance/reproducibility/support matrix与G6尚未完成；
- 当前RC以correctness为中心且未设置绝对性能硬指标；Phase 5只证明required performance shape和可观察性，不作throughput、latency、memory ratio或跨平台性能优势声明。

因此，Phase 5状态为`completed`，G1–G4为`passed`，但唯一V1 Goal必须继续`active`并进入Phase 6；V1.0 RC、release readiness与Goal completed均尚未成立。
