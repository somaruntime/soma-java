# soma_java 项目架构设计

状态：正式设计文档
Owner：根项目协调层
事实范围：项目边界、系统结构、模块责任、依赖方向和跨层数据流
非事实范围：具体 annotation、API method、runtime 数据结构算法和 benchmark 结果
最后审查日期：2026-07-10

## 1. 目标

本文是 `soma_java` 项目级架构的唯一事实源。它回答：

- Java-only SOMA 解决什么问题；
- build time 与 runtime 如何分层；
- 每个模块拥有哪类事实；
- 依赖必须朝什么方向；
- public/generated、runtime internal 和 evidence 如何隔离。

SomaTable 永久语义以 [设计宪法](soma-table-design-constitution.md) 为准。

## 2. 项目边界

V1 面向 Java 8 进程内 runtime state：

- schema source 使用 Java annotation；
- javac 8 parse-phase plugin 完成 `@SomaValue` effective-type lowering；
- JSR 269 processor 生成类型安全 Table/Batch/API/materializer；
- runtime 使用 Java columnar kernel；
- examples、testkit 和 benchmarks 产生证据。

V1 不承诺 Python、C ABI、native runtime、跨语言 FFI、persistence、distributed execution 或 concurrent table access。

## 3. 架构北极星

```text
Java annotation schema
  -> javac 8 source lowering + compile-time schema processing
  -> normalized schema model + exact schema hash
  -> generated Table / Batch / API / materializer / ColumnView
  -> annotation-agnostic Java TableStore kernel
  -> examples / testkit / benchmark / gate evidence
```

关键分离：

- schema class 描述 logical shape；
- generated code 绑定 schema 与 runtime；
- runtime-core 只处理 schema-agnostic storage material；
- live facts 只存在于 TableStore ownership aggregate；
- materialization 和 external mapping 是显式边界；
- evidence 模块证明契约，不定义契约。

## 4. 架构层次

| 层次 | 责任 | 禁止拥有 |
|---|---|---|
| Application adapter | DTO/API/file 与 Batch/materialized object 映射、业务校验、跨表 orchestration | schema/runtime 内部规则 |
| Public schema | annotation、field role、type、optional/default/access declaration | runtime storage |
| Compile-time processing | javac integration、validation、normalization、hash、diagnostics、codegen | live runtime facts |
| Generated API | schema-specific facade、cursor、mutator、materializer、column binding | generic metadata interpretation policy |
| Runtime core | primitive storage、lookup/sidecar、mutation/lifecycle/error | annotation source 和业务约束 |
| Evidence | compile/golden/invariant/example/benchmark/report | 产品契约 |

依赖和事实都必须从 application/public boundary 指向稳定 core contract，不能让 examples、reports 或 runtime details 反向定义 schema/API。

## 5. 模块责任

### 5.1 `soma-annotations`

拥有 public schema annotation API 和用户可见 schema declaration 语义。

不拥有 normalization/hash 算法、generated source、runtime storage 或 benchmark。

### 5.2 `soma-processor`

拥有 javac 8 parse-phase integration、`@SomaValue` semantic lowering、JSR 269 processing、validation、normalized schema、exact hash、diagnostics 和 code generation。

Generated code 实现根级公共 API 契约，但 processor internal model 不进入 public API。

### 5.3 `soma-runtime-core`

拥有 annotation-agnostic TableStore kernel、primitive columns、bitmap、KeySpace、AccessStructures、AccessPath、mutation、ownership lifecycle、runtime errors 和性能实现纪律。

Runtime 不解析 annotation，不生成 Java source，不拥有 schema logical meaning。

### 5.4 `soma-testkit`

拥有 compile fixture、golden、runtime invariant、materialization comparator 和 performance-shape assertion helper。

Testkit 是 evidence helper，不是 production runtime dependency。

### 5.5 `soma-examples`

拥有 Java 8 正式 usage scenarios、Access Pattern Cards 和 E2E smoke 场景。

Example 可以覆盖 owner contract，但不能修改 owner semantics。

### 5.6 `soma-benchmarks`

拥有 benchmark evidence methodology、scenario lanes、artifact 和 claim discipline。

Benchmark 结果可以反证设计假设，但只有正式设计变更才能修改 API/schema/runtime contract。

## 6. 依赖方向

允许的主依赖：

```text
soma-annotations
      ^
      |
soma-processor

soma-runtime-core
      ^
      |
generated schema-specific code

soma-testkit
  -> annotations / processor / runtime-core

soma-examples
  -> annotations / processor-generated code / runtime-core

soma-benchmarks
  -> runtime-core / generated code / selected examples
```

架构约束：

- `soma-annotations` 不依赖 processor/runtime；
- `soma-runtime-core` 不依赖 annotations/processor/examples；
- processor 可以依赖 annotations，但不能依赖 examples/benchmarks；
- production modules 不依赖 testkit/benchmarks；
- examples 和 benchmarks 位于依赖图外缘；
- 不允许循环模块依赖。

## 7. Build-time 数据流

```text
Java source + annotations
  -> javac parse
  -> @SomaValue lowering before symbol enter
  -> declaration collection by JSR 269 processor
  -> semantic validation
  -> normalized schema
  -> canonical serialization
  -> exact schema hash
  -> generated source/resources
  -> Java 8 compilation
```

Build-time 必须满足：

- 不从 runtime state 反推 schema；
- validation 完成前不生成可用 artifact；
- normalization 和 hash 可复现；
- generated output 只读取 validated normalized model；
- diagnostics 使用稳定 machine-readable code；
- transformer 缺失或 compiler unsupported 时 fail closed；
- source transformation/semantic lowering 的 compiler boundary 明确；
- canonical V1 build 使用 full JDK 8 + Maven，不把 `--release 8` 等同于 javac 8 adapter support。

具体规则由 [compiler integration 契约](../soma-processor/docs/compiler-integration-contract.md)、[schema processing 契约](../soma-processor/docs/schema-processing-contract.md) 和 [code generation 契约](../soma-processor/docs/code-generation-contract.md) 拥有。

## 8. Runtime 数据流

### 8.1 Import

```text
external facts
  -> application validation/mapping
  -> generated Batch
  -> generated table mutation boundary
  -> TableStore authoritative facts
```

只有 mutation 成功后 row 才成为 visible fact。External DTO 不与 TableStore 并行同步。

### 8.2 Hot computation

```text
generated source method
  -> AccessPath / packed rows
  -> fused Row/Key/Column traversal or ColumnView
  -> primitive reads/writes
  -> coordinated sidecar/lifecycle update
```

Hot loop 不以 schema object、Java Collection graph、reflection 或 per-cell metadata interpreter 为基础。

### 8.3 Export

```text
TableStore facts
  -> explicit materialization or primitive projection
  -> caller-owned schema object / List / Map
  -> application mapper
  -> external DTO/result
```

Deep materialization 是有预算的 boundary operation，不是默认 hot path。

## 9. Runtime internal 组合模型

TableStore 由职责明确的组件组合：

| Component | 架构责任 |
|---|---|
| RowSpace | packed live-row domain、size/capacity、compaction |
| KeySpace | keyed table logical key 到 row slot 的定位 |
| ColumnStore | primitive/reference payload 和 presence |
| AccessStructures | secondary index、unique、order 等派生结构 |
| AccessPath | terminal 的候选 row sequence |
| MutationCoordinator | visible mutation、sidecar、epoch 和 pin 冲突协调 |
| LifecycleState | active/released、view/cursor/pipeline 生命周期 |

这些是 runtime internal，不进入 public/generated 用户术语。详细设计分别由 [TableStore 契约](../soma-runtime-core/docs/table-store-contract.md)、[runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md)、[runtime plan 契约](../soma-runtime-core/docs/runtime-plan-contract.md) 和 [runtime errors/diagnostics 契约](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) 拥有。

## 10. 公共边界

用户只看到：

- schema annotation；
- generated keyed/dense table facade；
- Batch、Direct API、Mutator；
- Row/Key/Column Pipeline；
- typed child facade；
- Materialized Object；
- typed ColumnView；
- structured result/error/stats。

用户不应看到：

- RowSlot、ChildTableHandle；
- hash bucket、bitmap word、sidecar node；
- allocator/scratch internal；
- arbitrary internal AccessPath extension；
- processor normalized model implementation class。

公共语义由 [Generated Table API 契约](generated-table-api-contract.md) 和 [Materialization 契约](materialization-contract.md) 拥有；public/internal/compatibility boundary 由 [Public API 与兼容性契约](public-api-compatibility-contract.md) 拥有。

## 11. 正确性、性能与证据

| 关注点 | 设计 Owner | 证据 Owner |
|---|---|---|
| Schema/compatibility | annotations + processor owner contracts | testkit compile/golden |
| Compiler/build consumer | compiler integration + build/dependency contract | external consumer/package smoke |
| Public API shape | root API contract + codegen contract | processor golden/package smoke |
| Runtime correctness | correctness model + runtime lifecycle | runtime invariants/testkit |
| Runtime performance shape | performance model + runtime implementation contract | testkit shape assertions/benchmarks |
| Scenario usability | examples scenario contracts | examples reports |
| Release readiness | validation gates | formal reports |

“设计上应当更快”不能替代 benchmark；“测试通过”不能替代 owner contract；“示例能跑”不能替代 release gate。

## 12. 架构反模式

禁止：

- schema class 或 `List<Row>` 充当 live storage；
- runtime-core 解析 annotation 或业务 metadata；
- generated hot loop 逐 cell 做 reflection/type switch/boxing；
- report、README、guide 或 blueprint 定义新契约；
- 两个模块共同拥有同一 public fact；
- child storage 被共享或 reparent；
- detached object 暗含 write-back；
- application 依赖 hidden sidecar/order 作为业务事实；
- 为开发顺序缩水 V1 release contract。

## 13. 演进边界

Runtime plan 可以演进 capacity、hash strategy、index maintenance、scratch、stats mode 和 materialization default budget，不改变 logical schema。

以下变化必须按 compatibility/breaking change 审查：

- annotation 或 field semantic；
- normalized schema/hash input；
- generated public API；
- ownership、optional/default、key/index/order；
- materialization shape；
- runtime error/lifecycle；
- release gate 和 claim。

Build graph、artifact classification、public compatibility、security trust boundary 和 release lifecycle 分别由根级 owner contract 管理；module implementation 不得自行重新定义。

## 14. 下游正式文档

- [文档治理规则](documentation-governance.md)
- [领域术语表](domain-glossary.md)
- [Build 与依赖契约](build-and-dependency-contract.md)
- [Public API 与兼容性契约](public-api-compatibility-contract.md)
- [Generated Table API 契约](generated-table-api-contract.md)
- [Materialization 契约](materialization-contract.md)
- [Runtime 正确性模型](runtime-correctness-model.md)
- [Runtime 性能模型](runtime-performance-model.md)
- [Security model](security-model.md)
- [实现策略](implementation-strategy.md)
- [V1 验证门禁](validation-gates.md)
- [Versioning 与 release 契约](versioning-and-release-contract.md)
- [annotation schema 契约](../soma-annotations/docs/annotation-schema-contract.md)
- [compiler integration 契约](../soma-processor/docs/compiler-integration-contract.md)
- [schema processing 契约](../soma-processor/docs/schema-processing-contract.md)
- [code generation 契约](../soma-processor/docs/code-generation-contract.md)
- [TableStore 契约](../soma-runtime-core/docs/table-store-contract.md)
- [runtime lifecycle 契约](../soma-runtime-core/docs/runtime-lifecycle-contract.md)
- [runtime plan 契约](../soma-runtime-core/docs/runtime-plan-contract.md)
- [runtime errors 与 diagnostics 契约](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md)
- [runtime 性能实现契约](../soma-runtime-core/docs/runtime-performance-implementation-contract.md)

## 15. 非目标

本文不规定具体 API signature、annotation 参数、hash canonical byte format、column class、probe strategy、growth factor、benchmark scale 或 implementation milestone 日期。
