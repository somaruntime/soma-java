# Java-only SOMA V1 Goal execution status

状态：active
更新日期：2026-07-10
唯一 Codex Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`
Goal thread：`019f4bf2-6fb4-7d71-ad13-72e1abe9ba03`
当前 repository baseline：`b78997c`
当前 checkpoint：Phase 1 dense table complete implementation

本文件是可恢复的执行状态与审计入口，不是设计事实源。产品语义仍只来自 `docs/README.md` 及各 module formal Owner；Capability/Gate 定义仍只来自 implementation strategy/validation gates。

## 1. Checkpoint status

| Checkpoint | 状态 | Exit/evidence |
|---|---|---|
| Phase 0 compiler/build foundation | completed | commits `2346252`、`c5fbfbf`；Phase 0 report |
| Phase 1 dense table闭环 | in-progress | exact contract `b78997c`；当前直接实施runtime、processor/generated facade与完整evidence |
| Phase 2 keyed identity | pending | SparseInt/Hash KeySpace、Key Pipeline |
| Phase 3 access structures | pending | index/unique/order/grouped source/sidecar |
| Phase 4 child ownership | pending | child forest/cascade/replacement/recursive materialization |
| Phase 5 full breadth + G1-G4 | pending | annotation/processor/codegen/runtime/incremental/compatibility closeout |
| Phase 6 examples/benchmark + G5 | pending | formal scenarios、Access Pattern Cards、JSONL benchmark evidence |
| G6 release readiness | pending | License/SCM/contact/provenance/reproducibility/support matrix |
| Final total audit | pending | 23 Capability + G0-G6；仅此时 Goal completed |

Phase 0-6 不是版本、MVP 或独立 Goal。任何 checkpoint completed 都不改变唯一 Goal 的 active 状态，直到 G0-G6 全部通过。

## 2. Capability status

Phase 0 已进入的八项保持 `in-progress`：

- `V1-ANNOTATION-SCHEMA`；
- `V1-COMPILER-LOWERING`；
- `V1-PROCESSING-MODEL`；
- `V1-SCHEMA-HASH`；
- `V1-PUBLIC-COMPATIBILITY`；
- `V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`；
- `V1-CONSUMER-PACKAGE`。

其余 15 项仍为 `not-started`，全部保留原完整出口：

- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`；
- `V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`、`V1-CHILD-OWNERSHIP`；
- `V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`；
- `V1-PERFORMANCE-SHAPE`、`V1-SCENARIO-BENCHMARK`、`V1-RELEASE-EVIDENCE`。

Phase 1 implementation开始后，相关项才从 `not-started` 进入 `in-progress`；设计细化本身不冒充 capability implementation evidence。

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

Slice：P1-S1 generated dense primitive/presence kernel。它不是产品版本，也不代表 Phase 1 或任一 Capability 完整完成。

涉及 Capability：

- Phase 0 八项中的 annotation、processing、hash、public compatibility、security、evidence、consumer；compiler lowering保持既有 foundation；
- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`；
- `V1-COLUMN-ACCESS` 在后续 Phase 1-E补齐，不能由 fetch/materialization替代。

唯一 Owner 链：annotation schema、schema processing、code generation、generated API/materialization、public compatibility、TableStore/lifecycle/plan/errors/performance、security、testkit、build contract。每项行为仍由其中对应的一份唯一 Owner 拥有，不形成联合 Owner。

Slice exit：

- processor 真实生成最终命名的 dense Table/Batch/Rows/Row/MutableRow/Mutator；
- primitive/presence columnar Batch、packed `[0,size)` table、addBatch/replaceAll/clear/release；
- fetchAt/mutateAt、linear one-shot filter/skip/limit/count/forEach/update；
- whole-terminal primitive staging与callback failure atomicity；
- single-row/whole-dense detached materialization及default/explicit budget；
- create-time schema/generated/runtime/plan/estimator compatibility validation；
- structured errors/stats、generated/public/protocol manifests、external Maven consumer与shape evidence。

仍保留的 V1 breadth：其他 primitive/value/string/enum、complete defaults、Column Pipeline/View、all terminals/remove、key/index/order、child ownership、recursive materialization、full diagnostics/incremental、examples/benchmark/package/release/support matrix。

禁止捷径：手写 facade冒充 generated、processor依赖 runtime-core、generic metadata/dtype interpreter、Object/DTO/List<Row> live storage、optional sentinel、Stream/boxing/per-row Cursor、callback直写 live columns、temporary API/protocol/storage、test-only compatibility bypass、本机 smoke冒充 Gate/RC/release。

计划 evidence：annotation/table valid-invalid compile、canonical JSON/hash、generated source/javap、runtime/protocol manifest、packed/presence invariant、dense differential oracle、Batch/replace/clear/fetch/mutate/update/materialization/error/lifecycle、callback failure atomicity、Cursor reuse/fusion、allocation随 row count不线性增长、isolated external Maven consumer、artifact graph/checksum/environment report。

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
- 当前只补齐Phase 1 exact contract与恢复治理，不宣称新增 implementation evidence；
- 当前方案是最终 V1架构的有效子集，后续必须additive completion或contract-preserving internal refinement；
- 尚未引入temporary public/generated API、temporary hot path、migration或rewrite。
