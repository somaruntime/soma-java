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

本文不固定 growth factor、load factor、probe strategy、scratch bytes、pool threshold、exact class/builder method、config file syntax、environment mapping 或 production tuning value。
