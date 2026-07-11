# Runtime plan 契约

状态：正式设计文档
Owner：`soma-runtime-core`
事实范围：RuntimePlan scope、configuration dimensions、creation/validation、identity/hash、override、immutability 和 diagnostics
非事实范围：logical schema、public generated API naming、具体 algorithm implementation、benchmark calibration result 和 release default claim
最后审查日期：2026-07-10

## 1. 目标

`RuntimePlan` 描述某个 generated schema ownership aggregate 如何实例化和运行；它不是 Schema，也不改变 logical data meaning。

Schema/hash boundary 由 [Schema processing 契约](../../soma-processor/docs/schema-processing-contract.md) 拥有；materialization budget semantics/default profile 由 [Materialization 契约](../../docs/materialization-contract.md) 拥有；本文拥有 runtime configuration lifecycle 和 identity。

## 2. Canonical model

```text
generated schema metadata
  + generated default runtime plan
  + explicit application overrides
  -> validate and canonicalize
  -> immutable effective RuntimePlan
  -> runtimePlanHash
  -> create ownership aggregate
```

一个 effective plan 至少包含：

- schema hash/runtime compatibility target；
- aggregate-level limits/default MaterializationBudget/stats mode；
- 每个 normalized table logical identity 对应的 `TablePlan`；
- allocation estimator version；
- runtime plan protocol version。

每个 root table/ownership aggregate 在 create 时绑定一个 immutable effective plan。不同 instance 可以使用不同 plan，但同一 instance 创建后不能在 hot path 中隐式切换。

首个 dense runtime slice 固化 handwritten Java boundary：`com.hgtech.soma.runtime.RuntimePlan`、`TablePlan`、`StatsMode` 和 `MaterializationBudget`。Generated `XxxTable.defaultRuntimePlan()` 返回完整 immutable plan，`create(RuntimePlan)` 在分配任何 column 前验证并绑定；builder 只用于 create 前构造，runtime 不保存 mutable builder。

Stable public shape：

```text
enum StatsMode { SUMMARY, DIAGNOSTIC }
RuntimePlan.builder(String schemaHash, String runtimeCompatibility,
    String generatedProtocol, String planProtocol, String allocationEstimator)
RuntimePlan.toBuilder() -> RuntimePlan.Builder
RuntimePlan.schemaHash/runtimeCompatibility/generatedProtocol/planProtocol/
    allocationEstimator/runtimePlanHash -> String
RuntimePlan.defaultMaterializationBudget -> MaterializationBudget
RuntimePlan.statsMode -> StatsMode
RuntimePlan.requireTable(String logicalName) -> TablePlan
RuntimePlan.tables -> immutable List<TablePlan> in logical-name order
RuntimePlan.Builder.defaultMaterializationBudget(MaterializationBudget) -> Builder
RuntimePlan.Builder.statsMode(StatsMode) -> Builder
RuntimePlan.Builder.addTable(TablePlan) -> Builder        // duplicate fails
RuntimePlan.Builder.replaceTable(TablePlan) -> Builder    // missing fails
RuntimePlan.Builder.build() -> RuntimePlan
TablePlan.builder(String tableLogicalName, String algorithm)
TablePlan.toBuilder() -> TablePlan.Builder
TablePlan.tableLogicalName/algorithm -> String
TablePlan.initialCapacity/growthNumerator/growthDenominator -> int
TablePlan.maximumUpdateScratchBytes -> long
TablePlan.accessStrategy/sidecarMaintenancePolicy -> String
TablePlan.maximumSidecarScratchBytes -> long
TablePlan.Builder.initialCapacity(int)/growthRatio(int,int)/
    maximumUpdateScratchBytes(long)/accessStrategy(String)/
    sidecarMaintenancePolicy(String)/maximumSidecarScratchBytes(long) -> Builder
TablePlan.Builder.build() -> TablePlan
```

All parameters/getters are non-null. `requireTable` unknown name返回 `invalid_runtime_plan`；`tables()` 不返回 mutable internal map。Initial capacity > 0；growth numerator > denominator >= 1；maximum update scratch > 0；maximum sidecar scratch >= 0。Generated `create` 对 selector table要求已支持的 access/policy identity和 positive sidecar bound，对 no-selector table要求 `none/none/0`。Schema-specific unknown/missing/inapplicable table在 generated `create` validation fail。

V1 初始 identity/baseline：

| Item | Identity/value |
|---|---|
| runtime compatibility | `soma-runtime-java8-v1` |
| generated runtime protocol | `soma-generated-runtime-v1` |
| plan protocol | `soma-runtime-plan-v1` |
| dense algorithm | `dense-soa-v1` |
| materialization estimator | `soma-materialization-estimator-v1` |
| unspecified dense initial capacity | `16` rows |
| dense growth ratio | `3/2` with overflow-safe minimum-required clamp |
| maximum update scratch | `268435456` bytes（256 MiB，checked preflight，可显式覆盖） |
| no-selector access policy | `none` / `none` / `0` sidecar bytes |
| selector access policy | `primitive-sorted-permutation-v1` + `dirty-lazy-rebuild-v1` |
| maximum sidecar scratch | selector table 默认 `268435456` bytes（含 retained arrays 与 rebuild growth peak） |
| default stats mode | `summary` |

这些是 versioned effective plan facts，不是性能优势或永久调优结论。改变 baseline 必须产生新的 plan hash/evidence；改变不兼容 protocol/algorithm semantics 必须提升对应 identity。

## 3. TablePlan dimensions

每个 table plan 可以包含：

- initial/minimum capacity；
- growth、reserve、clear/trim policy；
- SparseInt domain threshold/fallback；
- HashKeySpace load/probe/delete/rehash policy；
- index/unique/order concrete strategy；
- sidecar eager/lazy/hybrid maintenance policy；
- scratch retention/maximum policy；
- child small-instance/pooling candidate；
- string/reference storage policy；
- stats level；
- memory/size/domain guard；
- implementation protocol/algorithm id。

并非每个 table 使用全部维度。Dense table 不接受 KeySpace 配置；没有 order/index 的 table 不接受对应 sidecar override。Unknown/inapplicable option 必须 fail closed，不能静默忽略。

## 4. Schema default 与 runtime default

- annotation `defaultCapacity` 是 generated default plan 的 input/hint，不进入 logical schema hash；
- runtime plan builder 可以显式覆盖 capacity；
- effective capacity 仍受 size/domain/memory validation；
- benchmark 校准可以改变 future default profile，但不能改变现有 table instance；
- default plan 变化必须进入 runtime plan identity、release note 和 performance evidence；
- per-call operation argument 不自动变成 plan fact。

Schema annotation 不声明 growth factor、load factor、sidecar strategy、stats level 或 benchmark profile。

## 5. Ownership aggregate scope

Parent-owned child table 使用同一个 aggregate plan 中对应 child table identity 的 `TablePlan`：

- 每个 child instance 有独立 storage/capacity/stats state；
- effective policy 来自同一 immutable aggregate plan；
- required logical-empty child 不因 plan 存在而 eager allocate；
- optional absent child 不创建 instance；
- child instance 不能附带来自另一 aggregate 的 arbitrary plan/handle；
- replacement subtree 使用 parent aggregate 的 effective plan stage/validate。

未来如允许 child-instance override，必须先定义 identity、ownership、replacement 和 reproducibility 影响；V1 不提供 ad-hoc live child override。

## 6. MaterializationBudget

Runtime plan 持有 default `MaterializationBudget` 和 estimator version。其维度、默认 profile、all-or-nothing semantics 与 typed exceed error 由 [Materialization 契约](../../docs/materialization-contract.md) 拥有。

每次 materialization 可以传 explicit budget：

- override 仅作用于该 invocation；
- 不修改 effective runtime plan；
- 不改变 runtimePlanHash；
- diagnostics 同时记录 plan default、explicit override 和 effective budget identity；
- explicit budget 仍需 protocol/overflow validation。

V1 不把 wall-clock timeout 放入 default budget。

## 7. Statistics mode

有效 stats mode 至少区分：

- `summary`：always-on terminal/mutation boundary counters；
- `diagnostic`：显式 opt-in 的 probe/phase/rebuild detail。

Stats mode 在 create 时确定，进入 plan hash。Runtime 不根据 workload 在 hot loop 中无界自动切换。Snapshot/reset/detail semantics 由 [runtime errors and diagnostics contract](runtime-errors-and-diagnostics-contract.md) 拥有。

## 8. Plan construction and precedence

Precedence 从低到高：

1. runtime protocol built-in safe baseline；
2. generated schema default plan；
3. application explicit aggregate/table override；
4. operation-local override（仅 owner contract 明确允许的字段，例如 per-call materialization budget）。

不读取 global mutable singleton、system property、environment variable 或 thread-local 作为隐式 plan override。Application 若从配置文件/环境变量构造 plan，mapping/validation 由 application adapter 负责，最终传入显式 typed plan。

## 9. Validation

Create 前至少验证：

- schema/runtime compatibility；
- table logical identity 完整且无 unknown duplicate；
- numeric range/overflow；
- capacity 与 estimated bytes；
- SparseInt domain；
- load/probe/rehash parameter legality；
- sidecar strategy 与 declared selector匹配；
- scratch/memory limit；
- child plan completeness；
- MaterializationBudget/estimator protocol；
- stats mode；
- algorithm id supported by current runtime。

Validation failure 不创建 half-initialized aggregate，返回 structured `invalid_runtime_plan` 或更具体 resource/compatibility code。

## 10. Runtime plan identity

```text
runtimePlanHash = lowercase_hex(
  SHA-256("soma-java:v1:runtime-plan\n" + canonicalEffectivePlan)
)
```

Canonical plan 必须：

- 使用 normalized table identity 和 stable field order；
- 包含所有影响 layout/algorithm/default diagnostics 的 effective value；
- 不包含 local path、timestamp、random id、benchmark result 或 object identity；
- 对同一 effective plan 产生相同 hash；
- 区分 absent/inapplicable 与 explicit value；
- 记录 protocol/algorithm/estimator identity。

首个 canonical effective plan 使用 UTF-8 JSON、Unicode code-point object-key order、table logical identity order和无 whitespace形式。Root keys 固定为 `allocationEstimator`、`defaultMaterializationBudget`、`generatedProtocol`、`planProtocol`、`runtimeCompatibility`、`schemaHash`、`statsMode`、`tables`。Dense table entry 固定为 `algorithm`、`accessStrategy`、`growthDenominator`、`growthNumerator`、`initialCapacity`、`maximumUpdateScratchBytes`、`maximumSidecarScratchBytes`、`sidecarMaintenancePolicy`、`table`。Budget object keys固定为 `maximumEstimatedAllocationBytes`、`maximumLeafValues`、`maximumOwnershipDepth`、`maximumRows`、`maximumTableInstances`。Unknown table、duplicate table、missing table和不适用 dimension在 create 前 fail closed。

`MaterializationBudget.identity()` 使用同一 canonical budget object与前缀 `soma-java:v1:materialization-budget\n` 的 lowercase SHA-256。Per-call override因此有稳定 identity但不改变 `runtimePlanHash`。

Runtime plan hash 用于 diagnostics/reproducibility/compatibility，不是 security signature，不进入 schema hash。

## 11. Mutability and lifecycle

- builder/config input 可以是 mutable application object；
- validation 后 runtime 只持有 immutable effective plan；
- table create 后不能 set growth/load/stats/sidecar policy；
- explicit reserve/clear/trim 等 operation 只能在 plan 允许范围内改变 storage state，不改变 plan identity；
- 需要换 plan 时创建新的 ownership aggregate 并显式迁移/import facts；
- SOMA V1 不提供 live plan mutation、automatic migration 或 adaptive replanning。

## 12. Observability

Generated/runtime API 必须能读取：

- runtime plan protocol/version/hash；
- effective table policy identity；
- effective capacity/domain/sidecar/stats mode summary；
- default materialization budget identity；
- estimator version；
- plan validation failure context。

Public summary 不暴露 bucket arrays、private thresholds unsupported by contract 或 mutable internal objects。Diagnostic detail 也不得成为业务逻辑依赖。

## 13. Evidence

至少覆盖：

- same input -> same canonical plan/hash；
- override precedence；
- unknown/inapplicable field rejection；
- capacity/domain/overflow/memory boundary；
- child plan selection；
- create all-or-nothing；
- default vs explicit materialization budget identity；
- summary/diagnostic mode behavior；
- immutable-after-create；
- schema hash unchanged while runtime plan hash changes；
- generated/runtime plan compatibility mismatch。

Default parameter性能只能由 benchmark evidence 校准；contract test 只证明语义和 identity。

## 14. 非目标

除本文已固化的首个 Java construction boundary、dense growth与 update-scratch baseline 外，本文不固定 future KeySpace/sidecar threshold、probe strategy、sort/compaction scratch、pool threshold、config file syntax、environment mapping 或 production tuning value。
