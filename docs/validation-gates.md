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
| G0 | Java-only scope freeze | SomaTable 宪法、architecture、glossary、correctness、performance model、runtime performance implementation contract、non-goals、module owner、release claim boundary 已进入正式文档且无临时事实源 |
| G1 | annotation schema gate | schema-backed table class、immutable `@SomaValue`、List/Map child mapping、type/value-state、floating strict-access、ownership graph/cycle、key/access/default、schema hash、breaking diagnostics |
| G2 | processor/codegen gate | `@SomaValue` effective-type golden、normalized/hash golden、deterministic generated Table/materializer/List-Map child API/budget overload、primitive static binding、Cursor reuse/fused-no-boxing hot-loop shape、diagnostic golden |
| G3 | runtime core gate | table-local + ownership-aggregate invariants、packed `[0,size)`、primitive columns/sidecars、steady-state allocation shape、KeySpace domain/load/collision、compaction/capacity/scratch、sidecar rebuild/stats overhead、child handle/cascade/replacement/pin、MaterializationBudget、Row Pipeline、ColumnView、typed errors |
| G4 | generated API/package gate | Java 8 transformer+generated API compile/run、recursive schema-object/List/Map child/budget smoke、schema/runtime/runtime-plan metadata、package smoke |
| G5 | examples/benchmark gate | Java 8 FJSP frontier E2E、Access Pattern Card、kernel shape、child-locality/deep-materialization lane、index/order/dynamic sort/update/remove、stale/released/view_pinned/budget error、benchmark JSONL |
| G6 | release readiness gate | release notes、install instructions、artifact checksums、known limitations、gate report 汇总、回滚/撤回策略 |

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
- 通过项、失败项、豁免项；
- 产物路径、checksum 或等价可验证标识；
- known limitations；
- release claim 允许引用的结论。

缺少 report 的 gate 等同于 `not-started`。

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
- release claim 引用了未进入正式 `reports/` 的证据；
- package smoke 只通过 IDE classpath 或 loose generated source；
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

## 7. Error path coverage

V1 至少覆盖：

- annotation schema validation error；
- semantic validation error；
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

- supported source transformation / annotation processor 可被用户 schema project 触发；
- `@SomaValue` implicit final/public-final/construction/equality/hash 对 user source 与 generated companions 一致可见；
- generated source 编译通过；
- generated table 创建成功；
- batch import、fetch、order source、Row Pipeline filter/update terminal、ColumnView 可执行；
- schema-class single-row、whole-table List/Map、Row Pipeline List materialization 与 required/optional child API 可执行；
- default/explicit MaterializationBudget overload 与 typed budget error 可执行；
- schema hash、runtime compatibility、runtime plan/budget metadata 可读取；
- runtime stats 可读取；
- Java 8 target 生效；
- no third-party runtime dependency。

## 9. Benchmark smoke

Benchmark smoke 只证明：

- benchmark 工具能运行；
- 场景、规模、指标和环境信息能结构化记录；
- release artifact 能完成基本性能路径。

Benchmark smoke 不等于性能优势声明。任何“更快”“更省内存”“适合生产大规模 hot path”的声明，都必须有 baseline、数据规模、机器环境、重复次数、统计口径和可复现命令。

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
- Java 8 package smoke 和 examples smoke。

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
