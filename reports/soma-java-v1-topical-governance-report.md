# SOMA Java V1 专题治理报告

状态：completed（专题 Goal 完成；原完整 V1 Goal 仍因 G6 blocked）
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

## 3. 审计期间的 Gate 临时回退与解除

审计已以可复现 source/evidence 反证当前 G2、G3、G5 的若干完成结论。在整改、fresh validation 与独立复审完成前：

- `V1-GENERATED-API`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-PERFORMANCE-SHAPE`、`V1-SCENARIO-BENCHMARK` 暂按 `implemented-unverified` 管理；
- G2、G3、G5 不作为当前专题的已通过结论；
- 既有报告保留为历史 evidence，不作为整改后实现提交的 fresh evidence。

该回退已在实现提交 `aa7a466` 的完整 `./scripts/check.sh`、fresh benchmark artifact和独立复核通过后解除。G0-G5恢复为`passed`；G6保持`blocked`且不在本专题完成范围内。

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

## 6. 最终 evidence matrix

已重放：processor normal/negative/golden、value floating/hash、generated source+javap、mutator overlap、callback same-table/cross-table、key locator、ColumnView/value leaf、SparseInt plan、child recursive failure/retry/release、resource limit/limit+1、batch cleanup、randomized key/index/order oracle、四 formal scenarios、domain/APC diagnostic、benchmark schema/semantic negatives、JDK 8 full compiler、JDK 25 rejection、independent external consumer和`./scripts/check.sh`。未参与主要修改的 reviewer 已对 live diff、generated source、consumer和fresh artifact完成两轮复核，最终结论为 PASS。

## 7. Findings 最终裁决

| Finding | 最终状态 | 设计/实现/evidence 出口 |
|---|---|---|
| `V1G-P0-001` | closed | Value leaf primitive row/column API、collision-safe命名、canonical consumer迁移和source/javap guard已落地；hot loop不再重建Value object chain |
| `V1G-P0-002` | closed | benchmark v3聚合、严格JSON、cross-field validator、measurement allocation v2、per-stage touched/working-set和36条落盘negative path闭环；fresh artifact为`target/benchmark-smoke.PJkPCM` |
| `V1G-P0-003` | closed | Mutator按字段维护touched/assignment state；交错提交不再回写未触及旧值，overlap oracle通过 |
| `V1G-P0-004` | closed | 同table callback scope覆盖所有facade入口，重入稳定返回`reentrant_access`；跨表访问保持合法，SOMA error不重包 |
| `V1G-P0-005` | closed | strict floating只作用于key/selector identity；ordinary Value leaf保真NaN、Infinity和`-0.0` raw bits，完整generated consumer通过 |
| `V1G-P0-006` | closed | V1 Value依赖闭包固定为schema-local；cross-schema引用在任何Filer write前稳定拒绝，schema/hash golden闭合 |
| `V1G-P1-007` | closed | additive scalar row locator与flattened Value-key overload已进入generated API；ColumnView组合不再要求materialize key/carrier |
| `V1G-P1-008` | closed | child cascade采用bounded reusable scratch、recursive preflight和descendants-first commit；pin/epoch/中途quota失败均可无部分状态重试 |
| `V1G-P1-009` | closed | final release清空column/presence/key/scratch/registry current storage，保留high-water diagnostics；旧view为typed failure |
| `V1G-P1-010` | closed | table/aggregate/ownership instance/bulk scratch均有checked deterministic admission；limit/limit-1、rollback和release后retry已验证 |
| `V1G-P1-011` | closed | `GeneratedColumnAccess`隔离Column Pipeline/View构造；application public signature不再泄漏generated concrete constructor surface |
| `V1G-P1-012` | closed | generated RuntimePlan可显式选择eligible SparseInt，maximum sparse key和byte preflight fail closed；无silent fallback |
| `V1G-P1-013` | closed | compilation-wide symbol/admission pass在Filer write前拒绝generated FQN与inherited/final member冲突，`SOMA-GEN-001/002`稳定 |
| `V1G-P1-014` | closed | depth/leaf/member/source/schema-total admission固化，limit/limit+1和256-table完整JDK 8 generated-source compile验证通过 |
| `V1G-P1-015` | closed | plugin diagnostic具source position且单次输出；default grammar/4096-4097边界、bounded echo、Unicode code-point order与isolated surrogate保真闭合 |
| `V1G-P1-016` | closed | Batch late failure清理capacity tail；failed callback的String/object scratch在`finally`清空，identity no-retained-reference oracle通过 |
| `V1G-P1-017` | closed | repeated small keyed batch不做整KeySpace rebuild；stable top-1使用arg-min specialization；sidecar dirty/rebuild/scratch保持可观测且有持续mutation evidence |
| `V1G-P1-018` | closed | 四个正式Access Pattern Card从真实`UpdateResult`、materialization/export结果计数，按table记录hot leaf width和working-set公式 |
| `V1G-P1-019` | closed | Maven dependency plugin版本/坐标与effective-POM锁定；isolated consumer/package脚本不再依赖未pin prefix解析 |
| `V1G-P2-020` | closed | 生命周期已结束的`docs/temp/v1-implementation-design.md`删除，长期蓝图allowlist保持不变 |
| `V1G-P2-021` | closed | VRP `route-rewrite`执行真实non-empty位置重写并由scenario结果核验 |
| `V1G-P2-022` | closed | FJSP dispatch等值比较固定identity tie-break，compaction前后determinism验证通过 |
| `V1G-P2-023` | P2 residual | Processor/generator已抽出`GeneratedSourceOutput`、limits和若干package-private职责，但主generator仍大；这是内部可维护性残余，不改变模块/API/hot path，也不要求未来consumer migration或rewrite |
| `V1G-P2-024` | closed | runtime-plan Owner补齐operation/bulk scratch稳定shape；本报告只绑定fresh命令，不追写历史报告的当时事实 |

最终严重度统计：P0剩余`0`，V1 blocker剩余`0`，required P1剩余`0`；P2 residual`1`，不影响正确性、公共契约、canonical hot path或G0-G5。

## 8. 完整 Capability 状态矩阵

| Capability ID | 状态 | 唯一 Owner / 实现链 | fresh evidence |
|---|---|---|---|
| `V1-ANNOTATION-SCHEMA` | evidenced | `soma-annotations/docs/annotation-schema-contract.md` | compiler/default/value modifier/negative combination lanes |
| `V1-COMPILER-LOWERING` | evidenced | `soma-processor/docs/compiler-integration-contract.md` | full JDK 8 javac lowering；JDK 25 `SOMA-COMP-002` fail-closed；plugin location fixture |
| `V1-PROCESSING-MODEL` | evidenced | `soma-processor/docs/schema-processing-contract.md` | global admission、schema-local Value闭包、diagnostic/golden fixtures |
| `V1-SCHEMA-HASH` | evidenced | `soma-processor/docs/schema-processing-contract.md` | locale/timezone repeat、Unicode code-point/isolated-surrogate golden |
| `V1-PUBLIC-COMPATIBILITY` | evidenced | `docs/public-api-compatibility-contract.md` | `check-public-api.sh`，additive generated-runtime protocol manifest |
| `V1-GENERATED-API` | evidenced | `docs/generated-table-api-contract.md` / codegen Owner | full generated source compile、javap、external consumers |
| `V1-DENSE-STORAGE` | evidenced | `soma-runtime-core/docs/table-store-contract.md` | primitive/object column、presence、capacity/release invariants |
| `V1-ROW-PIPELINE` | evidenced | generated API / runtime performance Owners | fused callback scope、no per-row object/source guards、top-1 specialization |
| `V1-COLUMN-ACCESS` | evidenced | generated API / codegen Owners | typed primitive/value-leaf Pipeline与ColumnView consumer |
| `V1-KEYED-IDENTITY` | evidenced | generated API / TableStore Owners | SparseInt/Hash composite/collision/value-key/scalar locator lanes |
| `V1-ACCESS-STRUCTURES` | evidenced | TableStore / codegen Owners | index、unique、order、dynamic sort、mutation oracle与bulk scratch boundary |
| `V1-MUTATION` | evidenced | generated API / correctness Owners | touched Mutator、Batch no-partial/no-retention、update/remove results |
| `V1-CHILD-OWNERSHIP` | evidenced | annotation / lifecycle Owners | root/child/grandchild quota、pin、recursive release、mid-subtree failure/retry |
| `V1-MATERIALIZATION` | evidenced | `docs/materialization-contract.md` | recursive object/List/Map、budget limit/limit-1、allocation failure、no partial result |
| `V1-RUNTIME-LIFECYCLE` | evidenced | `soma-runtime-core/docs/runtime-lifecycle-contract.md` | epoch/view/release/callback/owned child atomicity |
| `V1-RUNTIME-ERRORS` | evidenced | runtime error Owner | stable code/category/path/context与bounded diagnostic fixtures |
| `V1-RUNTIME-PLAN` | evidenced | runtime-plan Owner | immutable identity、SparseInt选择、table/aggregate/instance/scratch admission |
| `V1-PERFORMANCE-SHAPE` | evidenced | runtime performance implementation Owner | packed primitive arrays、bounded scratch、arg-min、sidecar/rebuild/allocation shape guards |
| `V1-SECURITY-INTEGRITY` | evidenced | `docs/security-model.md` | pre-Filer admission、resource overflow/limit、diagnostic redaction、dependency governance |
| `V1-EVIDENCE-TOOLING` | evidenced | testkit Owner | compile/golden/invariant/external consumer/serialized negative artifact helpers |
| `V1-CONSUMER-PACKAGE` | evidenced | `docs/build-and-dependency-contract.md` | current external Maven consumers + Maven verify；clean package mechanics见§12 |
| `V1-SCENARIO-BENCHMARK` | evidenced | examples scenario Owners / benchmark Owner | 四场景executed-result APC；20 lanes×2；36 negatives |
| `V1-RELEASE-EVIDENCE` | blocked | version/release / validation Owners | G6真实发布事实不足；状态未删除、未optional化、未由本专题完成 |

## 9. 五个强制专项结论

- Sparse Set / KeySpace：SparseInt domain与byte admission、Hash Int/Long/Composite collision/full equality、incremental append、compaction row-slot repair和missing/duplicate语义均闭合；无`HashMap<Key,Integer>` canonical path或silent fallback。
- 列式存储：live storage仍是primitive/packed arrays、PresenceBitmap和受控Object reference column；Value object仅在materialization boundary重建。release清空current storage，quota/scratch/growth均可预检。
- Index / Unique / Order：mutation consistency、unique bulk validation、dirty/lazy rebuild、stable top-1、dynamic sort与持续mutation oracle通过；没有隐藏full sort或rebuild storm作为正常top-1路径。
- API：annotation、compile diagnostic、public API、generated API、error/lifecycle行为和external consumer一致；新增接口均为additive final V1 protocol，不是temporary facade。
- Canonical hot path：Row/Key/Column Pipeline继续静态绑定concrete columns/KeySpace；无reflection、metadata interpreter、Java Stream、boxing tuple、DTO graph或per-row allocation替代。

## 10. 设计、实现、架构与用户体验结果

设计修正先进入唯一 Owner：Value leaf primitive access、scalar locator、schema-local Value闭包、codegen admission、child two-phase preflight、resource plan、ColumnView bridge、SparseInt plan、benchmark aggregate/allocation/touched scope和FJSP tie-break。没有为迁就实现而降低Capability、Gate或non-goal。

主要实现修复包括Mutator touched state、callback reentrancy、ordinary floating Value保真、Unicode canonical order、plugin source location、failed-tail/scratch cleanup、recursive child atomic release、instance/bulk budget、stable arg-min，以及benchmark M=2 nested merge与fail-closed validation。

Clean Architecture结论：annotation → processor normalized model/codegen → generated static protocol → runtime primitive substrate的依赖方向保持；runtime core不解释annotation，schema object/DTO不成为live storage。Clean Code审计没有机械引入对象层；`V1G-P2-023`保留为内部generator规模残余。

用户体验改进：schema author得到精确位置、稳定code和有界default诊断；consumer得到可发现、类型安全的primitive/value-leaf accessor和scalar locator；missing/optional/lifecycle/resource failure均有稳定typed语义；常见场景自然落在generated canonical path。

## 11. G0-G5、环境与命令

| Gate | 最终状态 | 专题 fresh 结论 |
|---|---|---|
| G0 | passed | V1 scope、Java-only边界、Owner/Gate均未缩水 |
| G1 | passed | annotation/processing/schema/hash与negative diagnostics fresh通过 |
| G2 | passed | full JDK 8 generated-source compile、admission、javap/public manifest fresh通过 |
| G3 | passed | runtime/key/access/child/materialization/lifecycle/resource完整回归通过 |
| G4 | passed | current external Maven consumers和artifact graph通过；package mechanics边界见§12 |
| G5 | passed | 四正式场景、APC、20 benchmark lanes×2、36 negative artifact cases通过 |
| G6 | blocked | 未改变；本专题不宣称public RC/release ready |

最终完整命令：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
SOMA_UNSUPPORTED_JAVAC=/opt/homebrew/opt/openjdk/bin/javac \
./scripts/check.sh
```

结果：`project-check: ok`。环境为Azul Zulu OpenJDK/Javac `1.8.0_492-b09`、Maven Wrapper `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64；unsupported compiler为OpenJDK javac `25.0.2`，稳定拒绝为`SOMA-COMP-002`。本机结果不外推为正式support matrix。

最后一次可复核benchmark命令为`JAVA_HOME=... ./scripts/check-benchmark-smoke.sh`，现存目录`target/benchmark-smoke.PJkPCM`包含primary/repeat JSONL、checksums和implementation file list；两份各20 records，`measurementIterations=2`，`claimAllowed=false`，36条invalid fixture全部被独立validator拒绝。

完整check中examples输出200个generated types、711个Java 8 class（major 52），四个scenario/APC和owner breadth全部通过；external Maven consumer、keyed/access/child/breadth consumer均真实编译执行。各脚本的`target/*.XXXXXX`目录是可再生成的临时证据，后续Maven clean会删除；报告不把已删除目录伪装成现存artifact，持久证据是脚本、fixture、golden、serialized schema和本报告绑定的提交。

## 12. Package、external consumer与本次网络边界

- `2490406`在clean worktree上完成过`./scripts/package-smoke.sh`，验证isolated repositories预热、artifact topology和package consumer；之后POM、artifact名称、依赖拓扑及package脚本未再变化。
- `aa7a466`上当前`./mvnw verify`和多组isolated external Maven consumer均在完整check中通过，覆盖本次修改后的实际annotation、processor、runtime和generated API内容。
- 在`aa7a466` clean worktree上再次运行`JAVA_HOME=... ./scripts/package-smoke.sh`时，Maven在项目编译前解析`maven-source-plugin`的`commons-compress:1.25.0`遭遇Maven Central TLS handshake中断；申请沙箱外重试又因Codex当前用量额度被系统拒绝。该失败未执行或反证任何项目代码，不被写成package成功，也不用于G6 claim。

因此G4 package/consumer结论由“未变化的clean package mechanics evidence + 当前commit Maven verify/external consumer evidence”共同支持；fresh package rerun的外部下载失败记录为验证环境限制，不隐藏、不包装为成功。

## 13. V1 scope non-regression 总审计

- Capability：22项功能/架构/evidence capability保持或重新进入`evidenced`；`V1-RELEASE-EVIDENCE`保持`blocked`。
- Owner：只补全或纠正正式语义，没有删除Capability、移动Gate或反向合理化shortcut。
- public/generated API：新增leaf accessor、scalar locator、construction bridge和atomic preflight均为additive final protocol；manifest已锁定。不存在temporary API或未来public migration。
- canonical hot path：保持primitive/packed/static binding/fusion；所有整改为additive completion或contract-preserving internal refinement。
- storage：未引入`List<Row>`、DTO live graph、metadata interpreter、Stream、boxing key tuple或test-only bypass。
- 用户体验：diagnostic、lookup、lifecycle、resource failure和APC事实更精确；没有以性能为由降低正确性或可发现性。
- Gate：G0-G5 fresh通过；G6和release claim未触碰。
- 后续达到公开V1不需要迁移consumer、核心事实或canonical hot path；只需完成原G6真实发布事实。

## 14. Accepted trade-offs 与 known limitations

- benchmark smoke只证明路径、复杂度、allocation/touched/working-set形态和fail-closed artifact，不给绝对性能优势结论；全部record保持`claimAllowed=false`。
- `soma-processor`主generator仍大，是`V1G-P2-023`内部可维护性残余。重新评估触发条件是新增Capability导致明显change blast、JVM code limit压力或同类缺陷重复；任何拆分不得改变normalized model、public/generated API或hot path。
- `target` evidence默认临时可再生成；只有最后一次benchmark目录当前保留。需要长期归档时应由正式evidence policy另行批准，而不是把build output提交进Git。
- 当前只验证本机Zulu JDK 8/macOS arm64；正式vendor/minor/OS/architecture矩阵仍属G6。
- clean package fresh rerun受外部TLS/执行额度限制，边界已在§12如实记录。

## 15. 独立复核、Git与最终未完成项

fresh reviewer第一次复核拒绝关闭benchmark，指出M=2 aggregate、allocation scope、APC/touched和Owner漂移；整改后同一reviewer只读抽查Owner、generated source、consumer和fresh serialized artifact，最终结论为：此前P0/P1全部关闭，未发现剩余V1 blocker，PASS。

专题本地提交：

- `36087bf docs: define V1 governance remediation contracts`
- `9f00be9 fix: harden SOMA Java V1 runtime and evidence`
- `2490406 fix: prewarm isolated package smoke repositories`
- `aa7a466 fix: close SOMA Java V1 governance blockers`

本专题Goal最终状态为`completed`。尚未完成项只有：原完整V1 Goal下的G6真实发布工作、`V1G-P2-023`非阻断内部维护残余，以及§12已记录的当前网络环境fresh package重放限制；不存在P0、required P1、temporary contract、future migration或known V1 rewrite。
