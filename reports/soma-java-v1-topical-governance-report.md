# SOMA Java V1 专题治理报告

状态：整改进行中
报告日期：2026-07-11
事实截止：本报告只绑定当前专题分支的 live repository、实际命令与后续提交；不替代任何正式设计 Owner
专题 Goal：完成 SOMA Java V1 功能、设计、架构、代码质量与性能形态无缩水审计及整改。

## 1. 范围与裁决边界

本专题覆盖 Java-only SOMA V1 的 annotation、javac 8 lowering、schema processing/hash、generated API、columnar runtime、key/access/child/lifecycle、错误与资源边界、正式四场景、benchmark、独立 consumer、package topology 和 G0-G5 evidence。用户明确排除的 G6 发布运营事实继续留在原 V1 Goal 中，不由本专题完成或删除。

报告只记录审计、整改和证据。公共/generated/runtime 语义必须先进入各自唯一 Owner，不能由本报告反向裁决。

## 2. 基线

- 分支：`develop`；审计起点提交：`0d7c1e0`；起点 worktree clean。
- 本机：macOS 26.5.2 build 25F84，arm64；Azul Zulu JDK/javac 8u492；Maven Wrapper 3.9.16。
- `./scripts/check.sh` 在 untouched baseline 成功，但 unsupported-compiler lane 因未设置环境变量而跳过。
- 补跑 `check-compiler-phase0.sh` 时，以 JDK 25 javac 验证 fail-closed 拒绝、以完整 JDK 8 验证正式 lowering/processor 路径，命令成功。
- 五个只读专项审计分别覆盖 processor/generated API、key/index/order、column/lifecycle/resource、场景/benchmark、架构/evidence；主流程对阻塞项重新读取 live source、generated evidence、fixture 和报告。

## 3. Gate 临时回退

审计已以可复现 source/evidence 反证当前 G2、G3、G5 的若干完成结论。在整改、fresh validation 与独立复审完成前：

- `V1-GENERATED-API`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-PERFORMANCE-SHAPE`、`V1-SCENARIO-BENCHMARK` 暂按 `implemented-unverified` 管理；
- G2、G3、G5 不作为当前专题的已通过结论；
- 既有报告保留为历史 evidence，不作为整改后实现提交的 fresh evidence。

## 4. Unified findings ledger

| ID | Severity | Classification | Capability | Unique Owner | Confirmed defect | Required exit |
|---|---|---|---|---|---|---|
| `V1G-P0-001` | P0 | design-and-implementation | GENERATED-API / ROW-PIPELINE / COLUMN-ACCESS / PERFORMANCE-SHAPE | `docs/generated-table-api-contract.md` | Value-backed cursor getter重建完整对象链，正式 FJSP/Game/VRP hot scan按行分配；Value leaf无 primitive row/column入口 | 固化 collision-safe leaf API，生成实现，迁移 canonical consumer，source/bytecode/allocation evidence |
| `V1G-P0-002` | P0 | evidence-and-implementation | SCENARIO-BENCHMARK / PERFORMANCE-SHAPE / EVIDENCE-TOOLING | `soma-benchmarks/docs/benchmark-evidence-contract.md` | 多个 lane实际 materialize/read/mutate，却默认记录 materialized/allocated/read/mutation 为零并称 deterministic accounting | evidence schema区分 measured/estimated/not-observed；每 lane真实填充；语义矛盾 fail closed；重建 G5 artifact |
| `V1G-P0-003` | P0 | design-and-implementation | MUTATION / ACCESS-STRUCTURES | `docs/generated-table-api-contract.md` | 两个同时创建的 Mutator均复制全部非 key 字段；后提交者会回写未触及旧值，静默撤销先提交事实 | Mutator per-field touched/assignment state；只发布显式 set/clear；overlap oracle |
| `V1G-P0-004` | P0 | implementation | ROW-PIPELINE / COLUMN-ACCESS / RUNTIME-LIFECYCLE / RUNTIME-ERRORS | `soma-runtime-core/docs/runtime-lifecycle-contract.md` | callback 只关闭 Cursor，未建立同 table callback scope；callback可经 facade重入并逃逸 stale Mutator | table callback scope；所有外部 facade入口 fail `reentrant_access`；SOMA error不被重包；跨表合法 fixture |
| `V1G-P0-005` | P0 | implementation | PROCESSING-MODEL / GENERATED-API / KEYED-IDENTITY | `soma-processor/docs/code-generation-contract.md` | 普通 Value 的 float/double leaf被无条件按 access key严格化；合法 NaN/Infinity/-0失败或 single-leaf key hash/storage不一致 | strict floating只由 key/selector叶触发；ordinary/value-key normal/collision oracle |
| `V1G-P0-006` | P0 | design-and-implementation | PROCESSING-MODEL / SCHEMA-HASH / SECURITY-INTEGRITY | `soma-processor/docs/schema-processing-contract.md` | table可引用另一 schema 的 Value，生成行为依赖全局 collected value，但 owning schema JSON/hash不闭包 | V1 schema-local Value闭包；cross-schema引用稳定 diagnostic；hash golden |
| `V1G-P1-007` | P1 | design-and-implementation | GENERATED-API / KEYED-IDENTITY / PERFORMANCE-SHAPE | `docs/generated-table-api-contract.md` | exact composite-key scalar read只能构造 key并 materializing `fetch/find` | additive `findRowIndex/rowIndexOf` 与 value-key flattened leaf overload；ColumnView组合；external consumer和allocation evidence |
| `V1G-P1-008` | P1 | design-and-implementation | RUNTIME-LIFECYCLE / CHILD-OWNERSHIP / PERFORMANCE-SHAPE | `soma-runtime-core/docs/runtime-lifecycle-contract.md` | cascade cleanup按 row×child分配数组；registry过早标记 released；失败不可安全重试 | reusable bounded scratch；descendants-first commit；registry成功后发布；failure/retry oracle |
| `V1G-P1-009` | P1 | design-and-implementation | RUNTIME-LIFECYCLE / DENSE-STORAGE / PERFORMANCE-SHAPE | `soma-runtime-core/docs/runtime-lifecycle-contract.md` | final release保留 column/presence/key/scratch/registry current storage；旧 view可长时间持有数组 | release清空 current storage与quota；stats current为零/high-water保留；stale view typed failure |
| `V1G-P1-010` | P1 | design-and-implementation | RUNTIME-PLAN / SECURITY-INTEGRITY / PERFORMANCE-SHAPE | `soma-runtime-core/docs/runtime-plan-contract.md` | retained table/aggregate、ownership instance和bulk staging缺少 deterministic admission；多处可直接进入 raw allocation failure | plan-bound table/aggregate/bulk/instance budget；checked estimate；no-partial/recovery/stats evidence |
| `V1G-P1-011` | P1 | public-boundary | PUBLIC-COMPATIBILITY / COLUMN-ACCESS | `docs/public-api-compatibility-contract.md` | public Column Pipeline/View constructor暴露 `.runtime.generated` concrete types，违背只由 generated facade构造与签名隔离 | public bridge或等价 Java 8 construction协议；view/pipeline constructor不再是application surface；manifest lock |
| `V1G-P1-012` | P1 | design-and-implementation | KEYED-IDENTITY / RUNTIME-PLAN | `soma-runtime-core/docs/table-store-contract.md` | SparseInt只存在手写 standalone class，generated RuntimePlan无法选择；domain array分配无byte preflight | explicit eligible-table key strategy + maximum sparse key；无 silent fallback；generated normal/miss/reject/limit evidence |
| `V1G-P1-013` | P1 | implementation | PROCESSING-MODEL / GENERATED-API / SECURITY-INTEGRITY | `soma-processor/docs/schema-processing-contract.md` | compilation-wide generated FQN和 inherited/final member冲突未在任何 Filer write前统一拒绝；error code退化 | global symbol/admission pass；`SOMA-GEN-001/002`；跨 schema/generated-existing/object-method fixtures |
| `V1G-P1-014` | P1 | design-and-implementation | PROCESSING-MODEL / GENERATED-API / SECURITY-INTEGRITY | `soma-processor/docs/code-generation-contract.md` | value depth/leaf/member/source/schema-total无稳定 codegen admission；极宽合法输入以 JVM/tool failure退出 | 校准后固化 deterministic budgets与 limit/limit+1 adversarial fixtures；先全量生成/preflight再写 Filer |
| `V1G-P1-015` | P1 | implementation | PROCESSING-MODEL / COMPILER-LOWERING / RUNTIME-ERRORS | processor contracts | plugin丢 source position；processor可能重复/无 Element diagnostic；default grammar过宽且回显无界；surrogate canonicalization不保真；slot guard off-by-one/value constructor无guard | location-stable diagnostic；bounded/redacted input；exact decimal grammar；UTF-16 lossless JSON；slot/resource fixtures |
| `V1G-P1-016` | P1 | implementation | MUTATION / SECURITY-INTEGRITY | `docs/generated-table-api-contract.md` | Batch late failure可在 capacity tail留下 reference/child payload；成功前可能保留不可见对象 | catch cleanup failed tail；size/facts不变；no-retained-reference oracle |
| `V1G-P1-017` | P1 | performance-evidence | ACCESS-STRUCTURES / PERFORMANCE-SHAPE / SCENARIO-BENCHMARK | runtime performance + benchmark Owners | repeated small keyed batch触发整 keyspace rebuild；dynamic top-1全排序；maintained order mutation/read可能形成 rebuild storm，缺场景边界 | bounded scratch与可观测 rebuild；safe stable top-1 internal specialization；正式 diagnostic lanes或明确规模/触发条件 |
| `V1G-P1-018` | P1 | evidence-governance | SCENARIO-BENCHMARK | `soma-benchmarks/docs/runtime-state-benchmark-contract.md` | 四个 Access Pattern Card要求的规模/hot columns/working set/mutation ratio未真实执行；generic marker被报告成 exact evidence | exact APC comparison；领域 diagnostic artifact或准确回退状态；不得以 marker替代量化 Card |
| `V1G-P1-019` | P1 | build-governance | CONSUMER-PACKAGE / SECURITY-INTEGRITY | `docs/build-and-dependency-contract.md` | external consumer/package/security脚本使用未 pin Maven dependency plugin prefix | root pin + exact coordinate/effective-POM assertion；隔离 consumer/package重放 |
| `V1G-P2-020` | P2 | governance | EVIDENCE-TOOLING | `docs/documentation-governance.md` | 普通临时 `docs/temp/v1-implementation-design.md` 已过生命周期 | 确认无剩余决策后删除；temp allowlist check |
| `V1G-P2-021` | P2 | evidence | SCENARIO-BENCHMARK | VRP example Owner | `route-rewrite` marker实际只做 empty→single-row replace | 实现真实非空插入/位置重写，或改名并收窄报告；本专题采用前者 |
| `V1G-P2-022` | P2 | design | SCENARIO-BENCHMARK | FJSP scenario Owner | dispatch等值 comparator的最终 tie-break未正式定义，结果依赖source/insertion order | Owner定义 identity tie-break；实现并做 compaction determinism test |
| `V1G-P2-023` | P2 | maintainability | PROCESSING-MODEL / GENERATED-API | `soma-processor` | Processor与generator职责过度集中且comment仍停留Phase 1 | 只做package-private internal拆分/去重；不改模块/API/metadata architecture；由size/change-blast证据验收 |
| `V1G-P2-024` | P2 | evidence | RUNTIME-PLAN / EVIDENCE-TOOLING | runtime-plan Owner + reports | Owner稳定shape遗漏已实现的 operation scratch；旧 gate报告对 unsupported lane、allocation/release有过强措辞 | 修正文档；fresh报告绑定整改提交和实际命令，不追写旧提交事实 |

## 5. 已接受的边界，不作为缺陷

- Java-only、annotation schema、generated specialization、packed primitive storage、parent-owned child、single-owner aggregate、detached materialization和无跨表 transaction边界保持不变。
- FJSP long-lived keyed frontier、VRP/Game selected-scope dense workspace、Simulation state-vector事实源的建模方向成立；本专题修复的是热访问表达、成本记录与场景证据，不把研究蓝图自动提升为 schema。
- `fetch/findFirst/firstOrThrow` 在显式 boundary materialize是合法语义；缺陷是把它放入 canonical hot loop或把成本写成零。
- public top-k、route move API、event heap、automatic join、parallel/native/FFI仍不是 V1 capability。`sorted(...).limit(1)` 可做不改变公共语义的内部稳定 arg-min specialization。
- 当前 generated class尚未触达 JVM method/code limit；缺陷是缺少 stable admission，而不是当前 artifact不可加载。

## 6. 后续 evidence matrix

整改后至少重放：processor normal/negative/golden、value floating/hash、generated source+javap、mutator overlap、callback same-table/cross-table、key locator、ColumnView/value leaf、SparseInt plan、child recursive failure/retry/release、resource limit/limit+1、batch cleanup、randomized key/index/order oracle、四 formal scenarios、domain/APC diagnostic、benchmark schema/semantic negatives、JDK 8 full compiler、JDK 25 rejection、independent external consumer、package/security scan、`./scripts/check.sh`。最后由未参与整改的 reviewer 对 live diff和 fresh evidence重新审计。
