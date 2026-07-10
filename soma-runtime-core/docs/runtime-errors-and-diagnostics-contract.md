# Runtime errors 与 diagnostics 契约

状态：正式设计文档
Owner：`soma-runtime-core`
事实范围：runtime exception envelope、stable error code/category/context、callback failure、stats snapshot/reset 和 logging side-effect boundary
非事实范围：schema compile diagnostics、public generated method naming、lifecycle state transition、runtime algorithm 和 application logging policy
最后审查日期：2026-07-11

## 1. 目标

本文定义 runtime failure 如何被稳定识别，以及 diagnostics 如何可观察而不污染 hot path或产生隐藏 I/O。

Lifecycle 状态转移由 [Runtime lifecycle 契约](runtime-lifecycle-contract.md) 拥有；public API 在何时触发 failure 由根级 API/materialization contract 拥有；本文拥有 runtime error envelope、code/context 和 stats behavior。

## 2. Error envelope

Generated/public runtime API 使用 unchecked structured exception envelope。实现期可以固定 Java class name，但必须提供以下稳定语义：

```text
category
code
operation
schema/table/field/selector path
safe context map
cause (when applicable)
```

允许一个公共 exception envelope 携带 stable typed code；禁止只抛 message-only generic exception。Caller 必须能在不解析 message 文本的情况下判断错误类别和恢复策略。

Error object/context 是 immutable snapshot，不暴露 mutable runtime state、RowSlot、bucket、bitmap 或 child handle。

首个 runtime slice 固化 handwritten envelope 为 `com.hgtech.soma.runtime.SomaRuntimeException` 与 `SomaErrorCategory { INVALID_INPUT, LOOKUP, CONFLICT, LIFECYCLE, COMPATIBILITY, RESOURCE, CALLBACK, INTERNAL }`。Exception exact getters：`SomaErrorCategory category()`、non-null `String code()`、non-null `String operation()`、non-null `String path()`、non-null immutable `SortedMap<String,String> context()` 和标准 nullable `Throwable getCause()`；无 path 使用 empty string。Constructor private；public static `create(SomaErrorCategory,String code,String operation,String path,Map<String,String> context,Throwable cause)` 是唯一 construction boundary并重复执行 null/bounds/safe-copy validation，供 protocol与application adapter在确需合成同 envelope时使用。`getMessage()` 只是 bounded safe rendering，caller 不解析它。Context按 key排序，单 value最多256、总 rendered context最多4096，超出以固定 marker截断；runtime不调用任意 payload/user `toString()`。

Generated code 通过 `com.hgtech.soma.runtime.generated.RuntimeFailures` 的 typed factory 构造 envelope；factory 是 generated-runtime protocol，不进入 generated facade public signature。Generated code 不自行拼接 unbounded context/message，也不直接写 stdout/stderr/logger。

Phase 1 factory surface返回 `SomaRuntimeException`：`invalidRowIndex(table,index,size,epoch,operation)`、`optionalAbsent(table,field,operation)`、`invalidNullValue(table,field,operation)`、`missingRequiredField(table,field,operation)`、`tableReleased(table,operation)`、`releasedView(table,operation)`、`staleView(table,capturedEpoch,currentEpoch,operation)`、`viewPinned(table,operation,activeViews)`、`pipelineConsumed(table,operation)`、`mutationConsumed(table,operation)`、`staleMutator(table,capturedEpoch,currentEpoch)`、`reentrantAccess(table,activeOperation,requestedOperation)`、`callbackFailed(table,operation,stage,cause)`、`memoryLimitExceeded(table,operation,limit,proposed)`、`compatibilityMismatch(code,expected,actual,path)`、`invalidRuntimePlan(path,reason)`、`internalInvariant(invariantId,table,operation)`。Phase 2 的 typed key factory 增加 `duplicateKey(table,intKey,operation)` 与 `missingKey(table,intKey,operation)`；int key 是 bounded safe descriptor，后续 string/composite key factory仍必须遵守本 Owner 的 escaping/redaction 规则。String/path由generated logical metadata提供，不接受 arbitrary payload formatter。

## 3. Categories

| Category | 含义 | 默认 recoverability |
|---|---|---|
| `invalid_input` | caller value/index/selector/plan 不合法 | 修正输入后可重试 |
| `lookup` | key/optional/row 不存在 | 由调用语义决定 |
| `conflict` | duplicate、unique、pin 等与当前事实冲突 | 状态/输入改变后可重试 |
| `lifecycle` | stale/released/consumed/reentrant | 当前 handle/operation 不可继续 |
| `compatibility` | schema/generated/runtime/plan 不匹配 | 更换匹配 artifact/plan |
| `resource` | budget/memory/allocation/capacity 失败 | 缩小操作或调整显式资源计划 |
| `callback` | application callback 抛出异常 | application 决定 |
| `internal` | impossible state/invariant violation | aggregate 不再可信，fail fast |

Recoverability 是 library-level hint，不替代 application transaction/compensation policy。

## 4. Stable error codes

V1 code namespace 至少包含：

| Code | Category | Required context |
|---|---|---|
| `duplicate_key` | conflict | table、operation、safe key descriptor |
| `unique_constraint_violation` | conflict | table、selector、conflicting row/key descriptor |
| `missing_key` | lookup | table、operation、safe key descriptor |
| `empty_result` | lookup | table/source、terminal operation |
| `optional_absent` | lookup | table、field/path |
| `invalid_row_index` | invalid_input | table、index、current size/epoch |
| `missing_required_field` | invalid_input | table、field/path、operation |
| `invalid_null_value` | invalid_input | table、field/path、operation |
| `invalid_floating_access_value` | invalid_input | field/selector leaf、value class |
| `invalid_selector` | invalid_input | table、selector/path |
| `field_not_found` | invalid_input | table、field/path |
| `dtype_mismatch` | invalid_input | path、expected、actual |
| `invalid_runtime_plan` | invalid_input | plan path、reason、effective schema/runtime identity |
| `stale_cursor` | lifecycle | cursor kind、captured/current epoch |
| `stale_view` | lifecycle | view/table、captured/current epoch |
| `released_view` | lifecycle | view/table/path |
| `table_released` | lifecycle | table/aggregate |
| `view_pinned` | conflict | blocked operation、pinned path/count |
| `pipeline_consumed` | lifecycle | pipeline/source/terminal |
| `mutation_consumed` | lifecycle | table、mutation kind、operation |
| `stale_mutator` | lifecycle | table、captured/current structural epoch |
| `reentrant_access` | lifecycle | active/current operation |
| `schema_hash_mismatch` | compatibility | expected/actual hash |
| `runtime_compatibility_mismatch` | compatibility | generated/runtime version |
| `compiler_integration_mismatch` | compatibility | generated/lowering identity |
| `runtime_plan_mismatch` | compatibility | expected/actual plan protocol/hash |
| `materialization_budget_exceeded` | resource | dimension、limit、current/proposed、budget identity、path |
| `memory_limit_exceeded` | resource | limit、estimate/current/proposed、operation |
| `allocation_failure` | resource | phase、requested estimate、cause type |
| `child_wrong_owner` | internal | ownership path、owner identities，不暴露 raw handle |
| `child_dangling` | internal | ownership path |
| `child_released` | lifecycle | ownership path |
| `ownership_cycle` | internal | ownership path |
| `callback_failed` | callback | operation、callback stage、cause |
| `internal_invariant_violation` | internal | invariant id、table/path、operation |

Code 使用 lowercase snake_case，发布后不能复用为不同语义。新增 code 必须进入 owner contract、API/error tests 和 compatibility review。

## 5. Context rules

所有错误至少包含：

- stable code/category；
- operation name；
- relevant schema/table/field/selector/ownership path；
- current lifecycle/compatibility identity when relevant。

Context value 必须：

- deterministic、bounded、safe-to-render；
- 对 key/string/payload 做长度限制和 escaping/redaction；
- 不调用 arbitrary user `toString()`；
- 不包含 local absolute path、credential、full row/table dump；
- 使用 logical path，不暴露 RowSlot/bucket/raw child handle。

Message prose 可以优化或本地化，但 code/category/context key 变化按 compatibility 处理。

## 6. Failure and aggregate trust

- `invalid_input`、`lookup`、`conflict`、`resource`、`callback` 是 expected failure，必须遵守 owner contract 的 no-partial-visible-state；
- `lifecycle` 表示当前 access 无效，不自动 release 其他合法 owner；
- `compatibility` 在 create boundary 阻止 aggregate 发布；
- `internal` 表示 invariant 已无法信任，当前 aggregate 必须进入 terminal/fail-fast path，不能继续提供 normal access；
- JVM fatal error（例如 `VirtualMachineError`）不被包装成普通 recoverable SOMA error。

`allocation_failure` 只表示 runtime 能在不捕获 JVM fatal error 的情况下识别的可控 allocator/provider/staging failure，例如显式 memory provider 拒绝、deterministic preflight 后的受控 allocation adapter failure或 test-injected allocator failure。`OutOfMemoryError` 是 `VirtualMachineError`，必须原样传播，不包装为 `allocation_failure`。Capacity/column growth 必须先 stage 后 publish，因此 raw OOME 发生时旧 live facts、size、capacity identity和 epoch仍保持合法；但 runtime不承诺 JVM 在 OOME 后可继续可靠工作。

## 7. Callback failure

Application callback exception：

- 作为 cause 保留，不解析 message；
- generated/runtime boundary 可以包装为 `callback_failed`，但不得误报 internal invariant；
- non-mutating terminal 停止并不修改 table；
- mutating terminal 必须满足 correctness model 的 visible failure atomicity；
- 如果实现不能在 callback 可能失败时保持该语义，则该 mutation shape 不能发布；
- `Error`/fatal JVM failure 不保证 application recovery，但 cleanup 不得主动覆盖原 cause。

## 8. Logging and hidden side effects

Runtime core：

- 不直接写 stdout/stderr；
- 不安装或配置全局 logger；
- 不默认依赖 SLF4J/Log4j/JUL；
- 不在 hot loop 格式化 message、stack、JSON 或 key payload；
- 通过 exception、stats snapshot 和显式 diagnostic API 暴露事实。

Processor 使用 compiler diagnostics/Messager，不打印普通控制台日志。Application 决定如何记录、采样、脱敏和关联 trace。

## 9. Stats model

Stats 是 observation，不是 authoritative business fact。

Scope 至少区分：

- table-local snapshot；
- ownership-aggregate snapshot；
- last-operation detail；
- lifetime/high-water counters。

Snapshot 必须 immutable、self-consistent，并记录：

- schema/runtime/runtime-plan identity；
- captured epoch/time basis；
- stats mode；
- counter units；
- whether detail is sampled/estimated/exact。

首个 dense slice 固化 `OperationOutcome { NONE, SUCCESS, FAILED }` 与 immutable `com.hgtech.soma.runtime.TableStats`，由 generated `XxxTable.statsSnapshot()` 返回。Exact getters：`String schemaHash()`、`runtimeCompatibility()`、`runtimePlanHash()`、`lastOperation()`、`lastErrorCode()`；`StatsMode statsMode()`；`int rows()`、`capacity()`、`activeViews()`；`long structuralEpoch()`、`growthCount()`、`updateScratchCurrentBytes()`、`updateScratchHighWaterBytes()`、`lastScanned()`、`lastMatched()`、`lastChanged()`；`boolean released()`；`OperationOutcome lastOutcome()`。String均 non-null；无 last operation/error使用 empty string。后续 key/sidecar/child/materialization stats additive增加，不重命名或改变单位。

`TableStats` constructor private；public static `create(...)` 按上述 getter顺序接收全部 identity/state/last-operation字段并返回 validated immutable snapshot，供 generated-runtime protocol构造。`UpdateResult` 同样使用 private constructor + public static `create(scanned,matched,changed,sidecarMaintained,sidecarRebuilt)`；negative或不满足 `changed <= matched <= scanned` 的输入 fail fast。

Success terminal记录实际 scanned/matched/committed changed。Callback/runtime failure记录 attempted scanned/matched、`lastChanged=0`、outcome FAILED与 stable error code；expected validation在 traversal前失败时 scanned/matched/changed均为零。`statsSnapshot()`、`runtimePlan()`、`isReleased()` 是 release后的只读 diagnostic exception：仍可调用以观察 terminal state；所有 data/pipeline/mutation/materialization access继续返回 `table_released`。

## 10. Summary and diagnostic mode

Summary mode 至少提供：

- row/capacity/table/child count；
- estimated bytes/high water；
- active view/released state；
- growth/rehash/collision/probe summary；
- sidecar dirty/rebuild count/rows/time summary；
- scratch retained/high water；
- last materialization counters/budget identity；
- allocation/resource failure summary。

Diagnostic mode 可以增加 histogram、phase timing、per-sidecar/probe detail，但必须显式启用并标记 overhead。Stats inner-loop cost 继续遵守 runtime performance implementation contract。

## 11. Snapshot and reset semantics

- `statsSnapshot()` 或等价 API 返回调用时 immutable snapshot；
- snapshot 不随 table 后续 mutation 改变；
- counter 使用 overflow-safe `long`，达到不可表达范围时返回 resource/internal error，不能 wrap；
- time 使用 monotonic elapsed source，仅用于 duration，不与 wall clock timestamp混用；
- reset 只清零 resettable event/last-operation counters；
- row/capacity/active-view/released/current-bytes/high-water identity 不因 reset 伪造；
- reset 是 explicit boundary operation，不在 read 时隐式发生；
- reset 不改变 table facts、epoch 或 runtime plan hash。

Generated table 提供显式 `resetStats()`；Phase 1 只把 lastOperation/lastErrorCode清空、lastOutcome设 NONE、lastScanned/lastMatched/lastChanged归零。它不清零 rows/capacity/epoch/released/activeViews、lifetime `growthCount`、current scratch或scratch high-water。

新增 stats Java method/class 必须先进入本 Owner 与 generated/public manifest，不能只由实现输出决定。

## 12. Estimated memory

Estimated bytes：

- 使用 versioned deterministic estimator；
- 区分 current retained、high water、transient requested estimate；
- 不宣称等于 JVM object layout/profiler；
- child aggregate 避免 double count；
- estimator version 进入 runtime plan/diagnostic identity；
- estimate/budget、可控 allocation provider failure 与 raw JVM fatal allocation error保持不同语义；后者不映射为 recoverable code。

## 13. Evidence

至少覆盖：

- 每个 stable code/category/context；
- message 改变不影响 code-based assertion；
- safe rendering/redaction/bounded size；
- expected failure no-partial state；
- internal violation fail-fast；
- callback success/failure/fatal boundary；
- no stdout/stderr/logging side effect；
- summary/diagnostic overhead shape；
- snapshot immutability/consistency；
- reset semantics、counter overflow、time unit；
- table/aggregate child stats no double count。

## 14. 非目标

本文不定义 distributed tracing、logging backend、metrics exporter、JMX、OpenTelemetry、business audit log、exact heap profiler、localized message compatibility 或 checked-exception API。
