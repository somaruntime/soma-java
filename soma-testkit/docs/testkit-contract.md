# soma-testkit 契约

状态：正式设计文档
Owner：`soma-testkit`
事实范围：compile/golden/invariant/materialization/performance-shape assertion helper 语义
非事实范围：被测试模块的 schema/API/runtime behavior 和 benchmark claim
最后审查日期：2026-07-10

## 1. 目标

本文定义 `soma-testkit` 对 compile diagnostics、`@SomaValue` effective-type lowering、normalized/generation golden、runtime invariant 和 Materialized Object assertion 的测试辅助边界。Testkit 帮助 owner module 产生可重复证据，但不是 schema、generated API 或 runtime behavior 的事实源。

本文遵循：

- [SomaTable 设计宪法](../../docs/soma-table-design-constitution.md)；
- [annotation schema 契约](../../soma-annotations/docs/annotation-schema-contract.md)；
- [compiler integration 契约](../../soma-processor/docs/compiler-integration-contract.md)；
- [schema processing 契约](../../soma-processor/docs/schema-processing-contract.md)；
- [code generation 契约](../../soma-processor/docs/code-generation-contract.md)；
- [Runtime 正确性模型](../../docs/runtime-correctness-model.md)；
- [Materialization 契约](../../docs/materialization-contract.md)；
- [Runtime 性能实现契约](../../soma-runtime-core/docs/runtime-performance-implementation-contract.md)；
- [V1 验证门禁](../../docs/validation-gates.md)。

## 2. 模块边界

Testkit 可以提供：

- Java 8 compile fixture runner；
- processor diagnostic assertion；
- normalized schema/schema hash/generated source golden assertion；
- generated package compile/run fixture；
- runtime table-local/ownership-aggregate invariant assertion；
- explicit schema-object/List/Map recursive comparator；
- structured assertion result，供 G1-G4 report 引用。

Testkit 不可以：

- 定义新的 annotation、generated API、runtime error 或 schema compatibility semantics；
- 成为 production runtime dependency；
- 通过 reflection/sidecar mutation 绕过 public/generated contract；
- 把 golden 文本当作唯一 correctness evidence；
- 隐式调用 table-row `equals()` 证明完整内容相等；
- 产生 benchmark 性能 claim。

## 3. Compile fixture contract

Compile fixture 必须显式记录：

- fixture id 和 source files；
- Java source/target/release baseline 为 Java 8；
- supported javac 8 compiler/source-transformation identity；
- processor artifact/version；
- classpath/module artifacts；
- expected success/failure；
- actual generated source/diagnostics location；
- deterministic locale/encoding assumptions。

Compile assertion 至少支持：

```text
assertCompileSuccess(fixture)
assertCompileFailure(fixture)
assertDiagnostic(code, severity, elementPath, relatedSymbol)
assertNoUnexpectedDiagnostics()
```

具体 Java method/class name 可在实现时固定，但不得削弱上述语义。Diagnostic assertion 以 machine-readable code、severity、element path、related symbol 和 blocking status 为主；message prose 只能作为辅助文本。

必须覆盖：

- invalid schema-backed table class；
- invalid/mutable `@SomaValue` effective shape；
- raw/wildcard/List-keyed/Map-dense/key-mismatched child field；
- direct/indirect ownership cycle；
- invalid child ownership field；
- non-finite floating default on key/index/unique/order leaf；
- invalid selector/path/name collision；
- Java 8 generated source compile；
- transformer missing/unsupported compiler/mismatch negative fixture；
- same-unit/cross-unit/classfile effective-type inspection；
- clean/incremental output equivalence；
- external Maven consumer 不继承 root parent/reactor classpath。

## 4. Golden contract

Golden evidence 分为独立 artifact：

| Artifact | 必须稳定的语义 |
|---|---|
| normalized schema | logical names、field/leaf order、table kind、ownership、selector role、default normalized result |
| schema hash | exact lowercase SHA-256 result and canonical input |
| effective/generated source | `@SomaValue` modifiers/construction/equality/hash、public types/methods/signatures、materializer/List-Map child/budget API、metadata |
| diagnostics | code/severity/location/related symbol/blocking status |

Golden comparison 可以忽略 owner contract 明确列出的 non-semantic whitespace，但不得忽略：

- public/generated type or return-type change；
- default/explicit MaterializationBudget overload；
- table-row no-generated-equals/hash、value canonical equality/hash、List/Map shape；
- required/optional child API；
- floating validation/canonicalization binding；
- static primitive column/selector binding、Cursor reuse/fused terminal shape、no boxed row index/intermediate collection contract；
- schema/runtime compatibility metadata。

Golden update 必须由对应 owner contract change 驱动，不能因为 diff 不方便而自动接受。

## 5. Runtime invariant helper contract

Runtime invariant helper 分两层：

### 5.1 Table-local invariant

- live row/slot/capacity/column length alignment and packed `[0,size)`；
- optional bitmap/payload/present count；
- `KeySpace` key-to-slot mapping；
- index/unique/order sidecar clean/dirty/current-row consistency；
- store epoch、active view、released state；
- floating strict leaf finite/canonical value。

### 5.2 Ownership-aggregate invariant

- every child handle resolves to one valid owned instance；
- no shared/reparented/orphan/dangling child；
- required logical-present and optional presence state correct；
- delete/clear/unset/replacement/release cascade result correct；
- subtree `view_pinned` precondition and released-view behavior correct；
- no partial handle switch after expected replacement failure。

Internal invariant hooks 只能作为 test-scoped runtime-core surface。Generated/public package smoke 仍必须只使用用户可见 API，不能把 internal hook 当成 V1 public contract。

## 6. Materialized Object comparator

`@SomaTable` row 不生成 structural equality/hash，而 `List`/`Map` 与 `@SomaValue` 各自有不同 equality contract。Testkit 必须提供显式 schema-aware recursive comparison：

- compare scalar/enum/string/semantic scalar values；
- compare `@SomaValue` by value semantics；
- compare floating ordinary payload by the owner-defined deterministic semantics；
- compare strict access floating leaf after canonicalization；
- distinguish optional absent、present zero、present-empty child；
- recurse only through child ownership field；
- compare dense child `List` in materialization contract order；
- compare keyed child `Map` by canonical key/value content，不依赖 iteration order；
- report first mismatch with schema/ownership path；
- support collecting all mismatches as an optional diagnostic mode；
- never call table-row `equals()` as the complete-graph oracle。

Comparator 是测试 assertion，不进入 application/runtime artifact，也不把 Materialized Object 变成 Value Object。

## 7. Materialization budget assertions

Testkit 至少支持以下 deterministic cases：

- depth limit boundary：equal-to-limit success，next child failure；
- table-instance/row/present-leaf/allocation-estimate each dimension boundary；
- `fetchAll()` budget shared across all root results；
- overflow-safe counter failure；
- `materialization_budget_exceeded` fields：dimension、limit、current/proposed、effective budget、path；
- allocation failure distinct from budget exceed；
- failure returns no partial object graph and preserves table/epoch；
- default runtime-plan budget and explicit override produce the expected effective budget；
- elapsed time is diagnostic only and cannot change pass/fail。

## 8. Runtime performance-shape assertions

Testkit 可以提供不产生性能 claim 的结构性 assertion/helper，用于 G2/G3 阻止实现偏航：

- stable table state live slots packed `[0,size)`，无 persistent tombstone/hole；
- primitive schema leaf 绑定 primitive column，primitive row index/scratch 不被 boxed；
- instrumented terminal 中 Cursor/pipeline stage/intermediate Collection construction count 不随 scanned rows 线性增长；
- `filter/skip/limit/terminal` traversal count 与 fused/short-circuit contract 一致；
- dynamic sort/compaction 使用 primitive row-index buffer；
- composite key normal/missing lookup 不产生 runtime transient tuple；
- summary stats 在 terminal boundary publish，diagnostic mode 可区分；
- clear/remove/replacement 后 object/reference column 不保留 dead reference；
- sidecar dirty/rebuild event 与 terminal stats 一致，同一 terminal 不重复 rebuild 同一 sidecar；
- required empty/optional absent child 不 eager allocate child storage。

这些 assertion 可以使用 test-scoped counters、allocation event hooks、generated-source/bytecode inspection 或受控 fixture；具体 helper API 由实现阶段固定。它们只能证明 implementation shape 和事件边界，不代替 benchmark throughput、latency、GC 或 hardware evidence。

## 9. Structured evidence

Testkit assertion result 至少能记录：

```text
fixtureId
contractArea
status
artifactVersion
schemaHash
processorVersion
runtimeCompatibilityVersion
runtimePlanHash
expected
actual
diagnostics
artifactPaths
```

Testkit result 可以进入 gate report，但 report 才是 release evidence；testkit output 不直接声明 release readiness。

## 10. Non-goals

V1 testkit 不做：

- production assertion runtime；
- third-party test framework public dependency；
- source formatter owner；
- automatic golden acceptance；
- exact JVM heap profiler；
- benchmark runner；
- schema migration framework；
- concurrent stress framework for unsupported shared SomaTable access。
