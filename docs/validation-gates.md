# soma_java V1 验证门禁

状态：正式设计文档
Owner：根项目协调层
事实范围：V1 readiness gates、required evidence、report path、blocking 和 release claim
非事实范围：owner contract 语义、具体测试实现和性能结果
最后审查日期：2026-07-10

## 1. 门禁原则

V1 readiness 不能由单元测试通过、示例能跑或本机 demo 成功单独代表。

`soma_java` V1 必须同时满足：

- annotation schema / processor / runtime / generated Java API / examples 的闭环；
- contract、lifecycle、package、错误路径和 smoke 证据；
- benchmark smoke 能运行并产出结构化结果；
- 文档明确声明支持能力、非目标和已知限制。

## 2. Gate sequence

| Gate | 名称 | 必需证据 |
|---|---|---|
| G0 | Java-only scope freeze | SomaTable 宪法、architecture、glossary、build/dependency、public compatibility、security、correctness/performance、compiler integration、runtime plan/error/performance implementation、version/release、non-goals、module owner、release claim boundary 已进入正式文档且无临时事实源 |
| G1 | annotation schema gate | schema-backed table class、immutable `@SomaValue`、List/Map child mapping、type/value-state、floating strict-access、ownership graph/cycle、key/access/default、schema hash、breaking diagnostics |
| G2 | processor/codegen gate | javac 8 transformer activation/negative matrix、`@SomaValue` effective-type/classfile golden、normalized/hash golden、deterministic generated Table/materializer/List-Map child API/budget overload、primitive static binding、Cursor reuse/fused-no-boxing hot-loop shape、diagnostic golden |
| G3 | runtime core gate | table-local + ownership-aggregate invariants、packed `[0,size)`、primitive columns/sidecars、steady-state allocation shape、KeySpace domain/load/collision、compaction/capacity/scratch、sidecar rebuild/stats overhead、child handle/cascade/replacement/pin、RuntimePlan/MaterializationBudget、Row Pipeline、ColumnView、structured errors/stats |
| G4 | generated API/package gate | external Maven Java 8 consumer 激活 transformer+processor 并 compile/run、recursive schema-object/List/Map child/budget smoke、schema/compiler/runtime/runtime-plan metadata、package smoke |
| G5 | examples/benchmark gate | Java 8 FJSP frontier E2E、Access Pattern Card、kernel shape、child-locality/deep-materialization lane、index/order/dynamic sort/update/remove、stale/released/view_pinned/budget error、benchmark JSONL |
| G6 | release readiness gate | license/namespace/SCM/contact、community/security policy、release notes/install、source/javadoc/checksum/provenance、reproducibility、compatibility matrix、known limitations、gate report、回滚/撤回 |

V1 不设置 ABI gate、Python gate 或 native package gate。

## 3. Gate status

Gate 状态只允许：

| 状态 | 含义 |
|---|---|
| `not-started` | 尚未开始，不能被 release 引用 |
| `blocked` | 已开始但有阻塞项，不能通过 |
| `waived` | 有明确豁免理由、影响范围和 owner sign-off |
| `passed` | 必需证据完整，且没有未豁免阻塞项 |
| `informational` | 仅记录非阻塞规划状态，不能替代 V1 required gate |

## 4. Evidence report requirements

每个 gate report 必须包含：

- gate id、owner、执行日期、执行人；
- commit hash 或 artifact version；
- 输入 artifact 和输出 artifact；
- 实际执行命令或人工审查步骤；
- 实际 JDK vendor/version/build、Maven Wrapper/Maven version（适用时）、OS 和 architecture；
- 通过项、失败项、豁免项；
- 产物路径、checksum 或等价可验证标识；
- known limitations；
- release claim 允许引用的结论。

缺少 report 的 gate 等同于 `not-started`。

### 4.1 V1 scope non-regression

每个 phase、checkpoint 和 gate report 必须包含独立的 `V1 scope non-regression` 审计：

- 引用 [实现策略 capability ledger](implementation-strategy.md#83-v1-capability-ledger) 中受影响的 Capability ID；
- 记录每项 capability 的前后状态和对应 evidence；
- 列出当前未实现的 V1 breadth，并确认它们仍保留原完整出口和最终 Gate；
- 证明当前产物是 V1 架构的有效子集，后续通过 additive completion 或 contract-preserving internal refinement 收敛；
- 证明没有 temporary public/generated API、temporary canonical live storage/hot path 或 test-only bypass 被当作正式 capability；
- 记录宪法、Owner contract、capability ledger、Gate 和 release claim 是否变化；
- 如果需要 public migration、核心事实迁移或主执行路径重写，状态必须是 `blocked`，不能通过 phase closeout。

Phase-local success 不能隐式 waive、删除或移出未涉及的 V1 capability。`waived` 只对本 Gate 明确列出的 evidence item 生效，必须包含唯一 Owner 和用户/项目决策者 sign-off；Codex、单个 implementation PR 或临时报告无权自行豁免产品 capability。

### 4.2 实施验证环境与支持矩阵边界

V1 implementation validation 先使用当前开发机的完整 JDK 8 javac、Maven Wrapper 和当前 OS/architecture。每次 validation 必须记录实际 JDK vendor/version/build、OS、architecture 和完整命令；使用 Maven 时同时记录 Wrapper distribution/Maven version。

本机 validation 只证明对应 commit/artifact 在该次记录环境中通过，不能外推为跨 vendor/minor、OS 或 architecture 支持。正式 support matrix 仍是 G6 required evidence；在 public RC/release sign-off 前未确定、未执行或证据不完整时，对应状态必须是 `not-started` 或 `blocked`，不得标记 `passed`、不得从 V1 删除，也不得由“理论兼容”或单机 smoke 替代。

## 5. Gate owner and report paths

| Gate | 唯一 Owner | Evidence contributors | Evidence report |
|---|---|---|---|
| G0 | root | all design owners | `reports/java-v1-g0-scope-freeze-report.md` |
| G1 | `soma-processor` | `soma-annotations`、`soma-testkit` | `soma-processor/reports/java-v1-g1-schema-processing-report.md` |
| G2 | `soma-processor` | `soma-testkit` | `soma-processor/reports/java-v1-g2-code-generation-report.md` |
| G3 | `soma-runtime-core` | `soma-testkit` | `soma-runtime-core/reports/java-v1-g3-runtime-core-report.md` |
| G4 | root | `soma-processor`、`soma-examples` | `reports/java-v1-g4-package-smoke-report.md` |
| G5 | root | `soma-examples`、`soma-benchmarks` | `soma-examples/reports/java-v1-g5-examples-report.md` and `soma-benchmarks/reports/java-v1-benchmark-smoke-report.md` |
| G6 | root | all gate owners | `reports/java-v1-g6-release-readiness-report.md` |

Report 是 evidence，不是设计事实源。可持续技术事实必须进入对应 root/module owner 的 `docs/*.md`。

## 6. Blocking rules

以下情况必须阻塞 V1 release：

- G0-G6 任一 required gate 为 `not-started` 或 `blocked`；
- phase/gate report 缺少 `V1 scope non-regression` 审计，或 capability 状态无法回溯到正式 ledger；
- Phase 被改写成 `v0.x`、MVP、Lite、Basic 等替代产品目标，或未实现 capability 被移入无批准的新版本/indefinite backlog；
- 当前实现需要未来迁移 public/generated consumer、核心事实或 canonical hot path 才能达到正式 V1；
- stub、fake、test-only bypass、temporary API 或 temporary storage 被当作 capability/Gate evidence；
- implementation PR 为合理化既有 shortcut 而反向修改宪法、Owner contract、capability ledger、non-goal 或 Gate；
- release claim 引用了未进入正式 `reports/` 的证据；
- package smoke 只通过 IDE classpath 或 loose generated source；
- transformer 缺失/unsupported compiler 时静默退化，或用 `--release 8` 冒充 javac 8 adapter support；
- schema hash metadata 未生成或不可验证；
- generated API 与 runtime compatibility 未在初始化阶段校验；
- 旧 `XxxRecord`/`ChildRecords` 分离 contract、schema object live-storage 误解或旧临时宪法仍被当作正式事实源；
- duplicate key、invalid floating access、ownership cycle、stale/released/view pinned、materialization budget/allocation 等错误路径不可观察；
- benchmark smoke 被写成性能优势声明；
- generated/runtime hot path 存在 per-row Cursor/boxing/intermediate Collection、persistent tombstone、generic metadata field dispatch 或不可观察的 rebuild storm；
- G3 只有 correctness test，没有 packed/primitive/allocation/fusion/capacity/sidecar/stats performance-shape evidence；
- known limitation 与公开 release claim 冲突；
- public/generated API 暴露 runtime sidecar、bitmap word、hash bucket 或 third-party internal type；
- Java 8 target 失效。
- 本机 validation 被外推为未验证 JDK vendor/minor、OS 或 architecture 的正式支持结论；
- public release 缺少 license、namespace ownership、SCM/contact、安全报告渠道或 external consumer；
- released artifact 可变、不可复现或缺少 checksum/source/javadoc/provenance。

## 7. Error path coverage

V1 至少覆盖：

- annotation schema validation error；
- semantic validation error；
- compiler transformer missing/unsupported/mismatch；
- schema hash mismatch；
- generated/runtime compatibility mismatch；
- invalid enum/value/table declaration；
- invalid/mutable `@SomaValue` lowering；
- invalid List/Map child kind or map-key mismatch；
- invalid key/index/unique/order selector；
- direct/indirect child ownership cycle；
- non-finite floating identity/access default/write/query；
- duplicate key；
- missing key；
- key mutation 被拒绝或不生成 key setter；
- stale access；
- released view；
- view pinned；
- allocation failure；
- memory limit exceeded；
- materialization budget exceeded；
- dangling/wrong-owner/released child path；
- child replacement construction failure with old subtree preserved；
- table released / use-after-release 被安全拒绝。

## 8. Package smoke

V1 package smoke 使用 Maven artifact 或 reactor equivalent。

Smoke 必须验证：

- 不继承 root parent 的 external Maven consumer 可以显式触发 supported transformer / annotation processor；
- `@SomaValue` implicit final/public-final/construction/equality/hash 对 user source 与 generated companions 一致可见；
- transformer 缺失和 unsupported compiler fail closed；
- generated source 编译通过；
- generated table 创建成功；
- batch import、fetch、order source、Row Pipeline filter/update terminal、ColumnView 可执行；
- schema-class single-row、whole-table List/Map、Row Pipeline List materialization 与 required/optional child API 可执行；
- default/explicit MaterializationBudget overload 与 typed budget error 可执行；
- schema hash、runtime compatibility、runtime plan/budget metadata 可读取；
- runtime stats 可读取；
- full JDK 8 compiler/runtime 与 Java 8 target 生效；
- no third-party runtime dependency。

## 9. Benchmark smoke

Benchmark smoke 只证明：

- benchmark 工具能运行；
- 场景、规模、指标和环境信息能结构化记录；
- release artifact 能完成基本性能路径。

Benchmark smoke 不等于性能优势声明。任何“更快”“更省内存”“适合生产大规模 hot path”的声明，都必须有 baseline、数据规模、机器环境、重复次数、统计口径和可复现命令。

V1.0 RC 以正确性为当前验收中心，不设置绝对 throughput、latency、memory ratio 或相对提升硬指标。这不取消 G2/G3 的 primitive static binding、packed/primitive/allocation/fusion 等 performance-shape evidence，也不取消 G5 benchmark runner、场景和结构化 JSONL 的 required evidence。

最小 benchmark smoke 场景：

- optional all-present scan；
- optional all-absent scan；
- optional mixed bitmap chunk scan；
- key lookup normal case；
- key lookup hash collision case；
- batch import with reserve；
- batch import without enough capacity；
- ordered access lazy rebuild；
- keyed runtime frontier add/update/remove + dynamic `firstOrThrow`；
- dense scratch replace + ordered `findFirst` / `firstOrThrow`；
- ColumnView acquire/read/release；
- parent-local child scan versus flat baseline smoke；
- recursive schema-object/List/Map success and per-dimension budget-boundary smoke；
- packed primitive scan versus handwritten primitive-array smoke；
- fused Row Pipeline/Cursor reuse/no-per-row-allocation smoke；
- SparseInt domain guard、HashKeySpace load/collision/rehash smoke；
- single/batch compaction and capacity/scratch reuse smoke；
- clean/dirty/rebuild-storm sidecar smoke；
- summary-only versus diagnostic stats overhead smoke。

## 10. Release claim boundary

V1 可以声明：

- Java annotation schema + generated Java table-first API；
- Java columnar runtime kernel；
- explicit key/index/unique/order access；
- batch import/export、compiler-defined immutable `@SomaValue`、List/dense 与 Map/keyed mapping、recursive Materialized Object、parent-owned child table、Row Pipeline、ColumnView 和 runtime stats；
- 正式 release matrix 已证明的 Java 8 javac/runtime package smoke 和 examples smoke。

V1 不应声明：

- native runtime；
- C ABI；
- Python binding；
- protobuf replacement；
- ORM/ECS/query engine；
- internal thread safety；
- unmeasured performance advantage；
- full schema evolution；
- production-grade all-platform packaging。
