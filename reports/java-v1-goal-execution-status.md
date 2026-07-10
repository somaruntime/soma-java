# Java-only SOMA V1 Goal execution status

状态：active
更新日期：2026-07-11
唯一 Codex Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`
Goal thread：`019f4bf2-6fb4-7d71-ad13-72e1abe9ba03`
当前 repository baseline：`bcf0966`
当前 checkpoint：Phase 1 dense table完整闭环

本文件是可恢复的执行状态与审计入口，不是设计事实源。产品语义仍只来自 `docs/README.md` 及各 module formal Owner；Capability/Gate 定义仍只来自 implementation strategy/validation gates。

## 1. Checkpoint status

| Checkpoint | 状态 | Exit/evidence |
|---|---|---|
| Phase 0 compiler/build foundation | completed | commits `2346252`、`c5fbfbf`；Phase 0 report |
| Phase 1 dense table闭环 | in-progress | runtime protocol `13179d2`、depth budget `2b2b643`、processor/generated facade `c067dac`、dense read terminals `bcf0966`；继续补齐remove/Column path/shape evidence |
| Phase 2 keyed identity | pending | SparseInt/Hash KeySpace、Key Pipeline |
| Phase 3 access structures | pending | index/unique/order/grouped source/sidecar |
| Phase 4 child ownership | pending | child forest/cascade/replacement/recursive materialization |
| Phase 5 full breadth + G1-G4 | pending | annotation/processor/codegen/runtime/incremental/compatibility closeout |
| Phase 6 examples/benchmark/release + G5-G6 | pending | formal scenarios、Access Pattern Cards、JSONL benchmark、package、License/SCM/contact/provenance/reproducibility/support matrix |
| Final total audit | pending | 23 Capability + G0-G6；仅此时 Goal completed |

Phase 0-6 不是版本、MVP 或独立 Goal。任何 checkpoint completed 都不改变唯一 Goal 的 active 状态，直到 G0-G6 全部通过。

## 2. Capability status

当前 17 项 Capability 为 `in-progress`，没有任何一项因垂直切片而被错误标记 completed。Phase 0 已进入的八项保持 `in-progress`：

- `V1-ANNOTATION-SCHEMA`；
- `V1-COMPILER-LOWERING`；
- `V1-PROCESSING-MODEL`；
- `V1-SCHEMA-HASH`；
- `V1-PUBLIC-COMPATIBILITY`；
- `V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`；
- `V1-CONSUMER-PACKAGE`。

Phase 1 已把以下九项从 `not-started` 推进为 `in-progress`：

- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`；
- `V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`。

其余六项仍为 `not-started`，并全部保留原完整出口：`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-CHILD-OWNERSHIP`、`V1-SCENARIO-BENCHMARK`、`V1-RELEASE-EVIDENCE`。

## 3. Gate status

| Gate | 状态 |
|---|---|
| G0 | passed |
| G1 | not-started |
| G2 | not-started |
| G3 | not-started |
| G4 | not-started |
| G5 | not-started |
| G6 | not-started |

## 4. Current slice declaration

已完成 vertical slice：P1-S1 generated dense primitive/presence kernel（commits `13179d2`、`2b2b643`、`c067dac`）。它不是产品版本，也不代表 Phase 1 或任一 Capability 完整完成。

当前 vertical slice：Phase 1 dense access/terminal/shape completeness。

涉及 Capability：`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-MUTATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`；其他 Capability 状态不回退。

唯一 Owner：generated-table API contract；processor code-generation contract；runtime TableStore/lifecycle/errors/performance contracts；testkit contract分别拥有对应行为，不形成联合 Owner。

Slice exit：补齐 dense primitive 的全部 V1 Row read/mutation terminals、stable sorted semantics、remove/compaction、typed Column Pipeline/ColumnView、borrow lifecycle、dense differential oracle、cursor reuse/fusion/bytecode/allocation-shape evidence，并保持已经接受的 public/generated API additive。

仍保留的 V1 breadth：value/string/enum/default、key/index/order sidecar、child ownership、recursive materialization、formal examples/benchmark/package/release/support matrix，全部仍在原 Phase/Gate。

禁止捷径：generic object pipeline、Stream/boxing/per-row cursor allocation、`List<Row>` live storage、temporary column API、以 fetch/materialize 替代 column path、逐 row live update后回滚、以本机通过冒充 Gate/RC。

计划 evidence：generated javap/source golden、pipeline differential/one-shot/error cases、remove compaction、ColumnView lifecycle/epoch/release、primitive column oracle、allocation/bytecode shape、external Maven consumer、`./scripts/check.sh`。

### 4.1 已完成 P1-S1 记录

涉及 Capability：

- Phase 0 八项中的 annotation、processing、hash、public compatibility、security、evidence、consumer；compiler lowering保持既有 foundation；
- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`；
- `V1-COLUMN-ACCESS` 在当前 Phase 1后续 vertical slice补齐，不能由 fetch/materialization替代。

唯一 Owner 链：annotation schema、schema processing、code generation、generated API/materialization、public compatibility、TableStore/lifecycle/plan/errors/performance、security、testkit、build contract。每项行为仍由其中对应的一份唯一 Owner 拥有，不形成联合 Owner。

Slice exit：

- processor 真实生成最终命名的 dense Table/Batch/Rows/Row/MutableRow/Mutator；
- primitive/presence columnar Batch、packed `[0,size)` table、addBatch/replaceAll/clear/release；
- fetchAt/mutateAt、linear one-shot filter/skip/limit/count/forEach/update；
- whole-terminal primitive staging与callback failure atomicity；
- single-row/whole-dense detached materialization及default/explicit budget；
- create-time schema/generated/runtime/plan/estimator compatibility validation；
- structured errors/stats、generated/public/protocol manifests、external Maven consumer与shape evidence。

仍保留的 V1 breadth：value/string/enum、complete defaults、Column Pipeline/View、all terminals/remove、key/index/order、child ownership、recursive materialization、full diagnostics/incremental、examples/benchmark/package/release/support matrix。全部七种 V1 signed/floating primitive加boolean及其optional boxed presence已由本 slice覆盖。

禁止捷径：手写 facade冒充 generated、processor依赖 runtime-core、generic metadata/dtype interpreter、Object/DTO/List<Row> live storage、optional sentinel、Stream/boxing/per-row Cursor、callback直写 live columns、temporary API/protocol/storage、test-only compatibility bypass、本机 smoke冒充 Gate/RC/release。

实际 evidence：annotation/table valid-invalid compile、canonical JSON/hash golden、generated source跨locale/timezone repeatability、generated javap golden、runtime/protocol manifest、packed/presence randomized invariant、Batch/replace/clear/fetch/mutate/update/materialization/error/lifecycle、callback failure atomicity、stable primitive-index sorted、short-circuit any/none、find/required/fetchAll/rowIndexes、isolated external Maven consumer与artifact runtime graph。Dense differential、remove、Column path、cursor/bytecode/allocation shape继续由当前 slice生成。

## 5. Recovery protocol

会话或上下文切换后按以下顺序恢复：

1. 查询唯一 Codex Goal，必须仍为 active，除非 G0-G6 已全部通过；
2. 读取本报告、Phase/Gate reports、`git status` 与最近 commits；
3. 读取当前 checkpoint涉及的 formal Owners；
4. 恢复最多一个 `in-progress` step，不从 Phase 0 重做；
5. validation/commit/report后更新本报告并自动进入下一项。

如果 UI 计划再次消失，以本报告和 repository facts恢复；不得因此建立新的缩小 Goal、重复已完成工作或丢失未实现 V1 breadth。

## 6. V1 scope non-regression

- 唯一 Goal、23 项 Capability、G0-G6、RC完整性与release boundary未变化；
- Phase 0状态和证据未回退；
- Phase 1 新增了真实 annotations/runtime/processor/generated facade、primitive/presence storage、atomic update、materialization、public/schema golden和external consumer evidence；Capability只推进到 `in-progress`；
- 当前方案是最终 V1架构的有效子集，后续必须additive completion或contract-preserving internal refinement；
- 尚未引入temporary public/generated API、temporary hot path、migration或rewrite。

## 7. Latest validation record

- commit/artifact：`bcf0966`；reactor artifacts `soma-annotations`、`soma-runtime-core`、`soma-processor` `0.1.0-SNAPSHOT`；external artifact `external-maven-dense-consumer-1.0.0-SNAPSHOT.jar`；
- 完整命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check.sh`；
- JDK：Azul Zulu OpenJDK `1.8.0_492-b09`，64-Bit Server VM build `25.492-b09`；
- Maven Wrapper：Apache Maven `3.9.16`；
- OS/architecture：macOS `26.5.2`、`aarch64`；
- 结果：reactor verify、public API、Phase 0 compiler、runtime-core、table diagnostics、generated dense external consumer、value external consumer、docs/scope/diff checks全部通过；generated dense source/schema/hash在默认环境与 Turkish locale/Pacific-Kiritimati timezone byte-identical；
- 跳过：unsupported-javac negative lane（未设置 `SOMA_UNSUPPORTED_JAVAC`）；
- known limitation：该结果只表示上述本机环境通过，不能外推正式 support matrix；G1-G6仍未关闭。
