# soma_java 架构设计

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文是 `soma_java` 项目级架构设计的唯一事实源。它定义 Java-only SOMA 原型的产品边界、项目分层、模块责任、跨层数据流、核心架构单元、依赖方向、正确性/性能信心来源、非目标和 release claim 边界。

本文覆盖整个项目，不只覆盖 runtime internal。具体下游设计与模块契约仍由对应 owner 文档定义：

- Java annotation schema：`soma-annotations/docs/annotation-schema-contract.md`；
- processor/codegen：`soma-processor/docs/processor-codegen-contract.md`；
- Row Pipeline / generated API：`docs/row-pipeline-api-contract.md`；
- runtime core：`soma-runtime-core/docs/runtime-core-contract.md`；
- implementation strategy：`docs/implementation-strategy.md`；
- validation gates：`docs/validation-gates.md`；
- cross-context terminology：`docs/domain-glossary.md`。

如果某个文档需要表达项目级架构事实，应引用本文，不应重新定义第二套架构。

## 2. 项目定位

`soma_java` 是 SOMA 的 Java-only 原型路线。它不实现 `.soma` IDL parser、native runtime、C ABI、Python binding 或跨语言 FFI。

SOMA 在本项目中的产品定位是运行时高性能数据容器和 runtime state schema，不是 protobuf、ORM、ECS、对象映射框架或通用 collection library。

```text
protobuf / API DTO = API / wire / persistence schema
soma_java          = Java runtime state / hot layout schema / high-performance data container
OOP application    = workflow orchestration / algorithm strategy
```

`soma_java` 覆盖三类长生命周期 runtime state：

1. entity state，例如 `Job`、`Operation`、`Machine`、`Material`；
2. frequently queried static / imported data，例如 operation-machine processing time、city-to-city distance matrix；
3. derived runtime frontier，例如 FJSP 中 operation release 后可加工的 `(MachineId, OperationKey)` 候选集合；
4. packed row-index data，例如矩阵行、数组型 runtime state、dense solver workspace。

短生命周期 Java 临时对象不是 SOMA 的核心 scope。SOMA 可以提供 dense table 作为可复用 workspace，但不把普通局部变量、一次性 DTO 或对象池管理作为产品目标。

## 3. V1 北极星

V1 的北极星是：

```text
Java 8 用户用 annotation class 定义运行时状态 schema，
annotation processor 生成 table-first Java API，
底层由 TableStore 组合模型、Java primitive column、bitmap、KeySpace、AccessStructures 和 AccessPath 承载 hot runtime state，
并把 table 明确收敛为 keyed table 与 dense table 两类。
```

用户不应在 hot loop 中直接维护：

- `List<DTO>` runtime row storage；
- `Map<Key, DTO>` runtime row storage；
- 手写 scattered index / order / packed scratch arrays；
- reflection-based runtime schema access；
- third-party collection type 作为 public/generated API。

V1 方向：

```text
Java annotation schema
  -> Java 8 annotation processor
  -> normalized schema model / schema hash
  -> generated Table / Batch / Row Pipeline / DTO materialization / ColumnView
  -> Java columnar runtime kernel
  -> examples / benchmark / gate evidence
```

## 4. 架构设计原则

本项目的架构设计遵循以下原则：

| 原则 | 含义 |
|---|---|
| 明确分层 | schema、processor、generated API、runtime、evidence 分层，避免一个模块同时拥有多层事实 |
| 单一事实源 | 项目级架构只在本文定义；模块契约只在 owner 文档定义 |
| 契约先于实现 | public API、schema hash、runtime lifecycle、error path 和 gate evidence 先有契约，再进入实现 |
| 热路径不反射 | runtime hot path 不依赖 reflection、metadata interpreter 或 DTO object graph |
| 组合优先 | runtime internal 采用 `TableStore` 组合模型，不用继承层级表达 public table kind |
| 可验证性优先 | correctness、compatibility、package 和 benchmark claim 必须能被 gate evidence 验证 |
| 不缩水 | 实施阶段可以分期，但不能把分期偷换为 V1 scope shrink |
| Java 8 baseline | 语言、API 和依赖选择必须保持 Java 8 兼容，除非正式设计文档改变 baseline |

## 5. 三明治结构

`soma_java` 的核心结构可以理解为三明治：

```text
Schema Annotation Layer
  declares runtime state schema

Implementation Layers
  processor / normalization / codegen / runtime-core / evidence tooling

Generated API Layer
  exposes table-first Java API to users
```

上层是用户用 Java annotation 声明 schema；下层是用户实际调用的 generated Soma API；中间层负责把 schema declaration 粘合为可执行、可验证、可维护的 runtime state container。

中间层不是单一模块，而是一组协作层：

```text
annotation processing
  -> normalized schema model
  -> deterministic codegen
  -> generated table facade and storage binding
  -> runtime-core TableStore kernel
  -> Row Pipeline execution
  -> DTO / ColumnView / stats / errors
  -> gate and benchmark evidence
```

Evidence layer 不属于用户 API，但它包住三明治结构，为 correctness、compatibility、package 和 release claim 提供可信度。

## 6. 项目级分层

| Layer | Owner | 责任 | 禁止事项 |
|---|---|---|---|
| Product / Scope Layer | root docs | 项目定位、V1 目标、非目标、release claim 边界 | 承诺 Python、C ABI、native runtime 或未验证性能优势 |
| Schema Annotation Layer | `soma-annotations` / `soma-processor` | Java annotation schema、enum/value/table、field/key/index/order 声明 | 把 annotation class 当作 runtime storage |
| Normalization Layer | `soma-processor` | semantic validation、normalized schema model、canonical schema hash | 绕过 validation 直接 codegen |
| Codegen Layer | `soma-processor` | deterministic generated source、metadata、diagnostics、golden output | 暴露 processor internal model 到 public API |
| Generated API Layer | generated source | table-first API、Batch、Row Pipeline、DTO materialization、ColumnView access、mutator | 暴露 sidecar、bitmap word、hash bucket、row pointer |
| Runtime Store Layer | `soma-runtime-core` + generated binding | `TableStore`、`RowSpace`、`KeySpace`、`ColumnStore`、`AccessStructures`、lifecycle | 解释 annotation 或依赖 processor |
| Execution Layer | generated Rows + runtime-core | `AccessPath`、`RowSequence`、filter/sort/limit/update/remove terminal | 生成 `Stream<DTO>` 作为 hot path 主模型 |
| Boundary Layer | generated API + runtime-core | DTO detached copy、ColumnView live readonly view、typed errors、stats | 返回 live row proxy 或 generic runtime exception |
| Evidence Layer | `soma-testkit` / examples / benchmarks / reports | compile/golden/runtime invariant、package smoke、benchmark smoke、gate reports | 把 smoke 写成性能优势声明 |

分层不是为了增加文件数量，而是为了明确事实所有权和跨层契约。任何实现设计如果无法放入上述层之一，应先审查它是否混淆了职责。

## 7. 模块结构

```text
soma_java/
  soma-annotations/
  soma-processor/
  soma-runtime-core/
  soma-testkit/
  soma-examples/
  soma-benchmarks/
```

| Module | Owner 责任 |
|---|---|
| `soma-annotations` | public schema annotation API |
| `soma-processor` | annotation processing、validation、normalized schema model、schema hash、codegen |
| `soma-runtime-core` | Java columnar runtime kernel、`TableStore` 组合模型、primitive columns、bitmap、key space、access structures、order sidecar、lifecycle、runtime errors |
| `soma-testkit` | compile/golden/runtime invariant test helpers |
| `soma-examples` | Java 8 examples and end-to-end smoke scenarios |
| `soma-benchmarks` | benchmark scenarios and structured evidence output |

根项目只负责跨模块架构、正式设计入口、release gate 和文档治理，不拥有模块内部实现细节。

## 8. 依赖方向

V1 采用以下依赖方向：

```text
soma-annotations
  <- soma-processor
  <- user schema source

soma-runtime-core
  <- generated code
  <- soma-examples
  <- soma-benchmarks

soma-testkit
  <- module tests
```

约束：

- `soma-annotations` 不依赖 runtime，也不依赖 processor；
- `soma-runtime-core` 不依赖 processor，也不理解 Java annotation element；
- `soma-processor` 可以依赖 `soma-annotations`，但 generated public API 不能暴露 processor internal model；
- generated code 可以依赖 `soma-runtime-core`，但 public generated API 不暴露 runtime internal structures；
- examples 和 benchmarks 只能消费发布形态或 reactor artifact，不能绕过 generated API 访问 internal sidecar；
- testkit 只能服务测试和 golden evidence，不能成为 runtime 必需依赖。

## 9. 架构单元拆解

项目级复杂性必须拆成可审查的小单元。每个单元都要能回答：它解决什么问题、输入是什么、输出是什么、拥有哪个事实、不能做什么。

| 架构单元 | Owner | 输入 | 输出 | 拥有的事实 / 不变量 | 禁止越界 |
|---|---|---|---|---|---|
| Schema Declaration | `soma-annotations` | Java annotation source | declared enum/value/table/field/key/index/order | 用户声明语义 | 不执行 runtime storage |
| Schema Validation | `soma-processor` | annotation processing model | diagnostics or valid schema | field membership、type、selector、name collision | 不生成无效 source |
| Normalized Schema Model | `soma-processor` | validated schema | canonical model | logical name、leaf order、selector path、schema hash input | 不依赖 source ordering ambiguity |
| Schema Hash / Metadata | `soma-processor` | normalized model | hash and generated metadata | compatibility identity | 不把 capacity hint 当 logical schema hash |
| Codegen | `soma-processor` | normalized model | generated Java source | deterministic output、API names、binding code | 不解释 runtime state |
| Table Facade | generated source | user calls | table-first API | public behavior and lifecycle entry | 不暴露 sidecar/internal arrays |
| Batch | generated source | typed import data | construction/import boundary | row count estimate、typed values | 不绕过 table validation |
| Row Cursor | generated source | `RowSlot` + column binding | typed getter/setter callback object | callback-local access | 不允许 cursor escape |
| DTO Mapper | generated source | `RowSlot` + columns | detached DTO | materialization boundary | 不返回 live row proxy |
| TableStore | generated source + runtime-core | layout + runtime components | table runtime aggregate | component ownership and lifecycle | 不成为 public API |
| RowSpace | runtime-core | mutation requests | row membership / slots | valid `RowSlot` allocation and movement | 不持有 payload |
| KeySpace | runtime-core + generated adapter | row key values | `RowKey -> RowSlot` lookup | stable identity mapping | 不混同 secondary index |
| ColumnStore | runtime-core + generated binding | typed field values | primitive/object columns | equal-length payload and bitmap alignment | 不拥有 key/index/order policy |
| AccessStructures | runtime-core + generated selector | selector values | secondary index / unique / order sidecar | maintained derived access structures | 不拥有 primary key identity |
| AccessPath | runtime-core + generated source method | source method / dynamic plan | `RowSequence` | terminal initial row sequence | 不改变 storage 本体 |
| MutationCoordinator | runtime-core + generated binding | append/update/delete/remove/replaceAll | coordinated mutation result | cross-component consistency and epoch | 不隐藏 side effects in subcomponents |
| LifecycleState | runtime-core | view/mutation/release events | epoch/errors/stats | view_pinned、stale_view、released semantics | 不定义 schema |
| Evidence Reports | testkit/examples/benchmarks/root | commands/artifacts | gate reports | release-claim evidence | 不定义 design facts |

这个拆解是后续 correctness model 和 performance model 的基础。若未来某个设计无法清楚归属到一个单元，应先调整架构拆解，而不是直接实现。

## 10. Table 分类

V1 table 只分为两类：

| Table kind | 定义 | 主要使用场景 |
|---|---|---|
| keyed table | 有 stable logical key | entity state、lookup table、唯一性约束、`fetch(key)`、`mutate(key)` |
| dense table | 无 stable logical key | packed scan、row-index iteration、批量替换、矩阵/数组型 runtime state、solver workspace |

`entity`、`lookup`、`matrix`、`workspace` 是建模场景，不是 schema kind。V1 不引入 `@SomaTableRole`。是否 keyed 只由是否声明 `@SomaKey` 决定。

`runtime frontier` 也是建模场景，不是第三种 table kind。若 frontier row 有稳定 logical identity、需要跨 dispatch 轮次保留、需要按 key 删除或按 secondary index 查找，应建模为 keyed table。例如 FJSP 中的 `MachineCandidate` 以 `(MachineId, OperationKey)` 作为 primary key，按 `MachineId` 支撑当前 machine dispatch，按 `OperationKey` 支撑某个 operation 被选中后的候选清理。

Child table 是 parent-owned ownership shape，不是第三种 table kind。它只适合 parent row 拥有 child table 生命周期的场景；FJSP 中的 operation-machine processing time 应建成独立 keyed lookup table，不应作为 `Operation` 的 child table。

Public table kind 不等同于 runtime internal 继承层级。Runtime 内部按 `TableStore` 组合模型实现 table：

```text
XxxTable
  -> XxxTableStore
       -> TableLayout
       -> RowSpace
            -> KeySpace        // keyed table only
       -> ColumnStore
       -> AccessStructures
       -> AccessPath
       -> MutationCoordinator
       -> LifecycleState
```

映射关系：

- public keyed table：`TableStore + RowSpace + KeySpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`；
- public dense table：`TableStore + RowSpace + ColumnStore + AccessStructures + AccessPath + MutationCoordinator + LifecycleState`，没有 `KeySpace`，但不是缩水版 table。

Sparse Set 只作为 `SparseIntKeySpace` 等 runtime internal 结构的实现材料，不是 table 本体，也不改变 public/generated API 术语。

## 11. Build-time 数据流

Build-time pipeline：

```text
annotated Java source + package-info.java
  -> annotation processing model
  -> semantic validation
  -> normalized schema model
  -> canonical schema hash
  -> deterministic generated Java source
  -> generated metadata and diagnostics
  -> Java 8 compilation
```

关键边界：

- annotation class 是 schema source，不是 runtime row object；
- processor 只能从 validated / normalized schema model 进入 codegen；
- validation 失败时跳过对应 schema 的 codegen，并输出 diagnostics；
- generated source 不输出 timestamp、local path 或 random id；
- generated API shape 由 golden 固化；
- schema hash mismatch 或 runtime compatibility mismatch 应在 table/create 或 metadata verification 阶段失败。

## 12. Runtime 数据流

Runtime create：

```text
XxxTable.create()
  -> generated TableLayout / metadata
  -> XxxTableStore
  -> RowSpace + ColumnStore + optional KeySpace + AccessStructures + LifecycleState
```

Batch import / replace：

```text
XxxBatch
  -> MutationCoordinator
  -> RowSpace allocation / rebuild
  -> generated field binding
  -> ColumnStore writes
  -> KeySpace identity update
  -> AccessStructures update or dirty mark
  -> epoch / stats update
```

Direct keyed access：

```text
RowKey leaf values
  -> KeySpace
  -> RowSlot
  -> ColumnStore
  -> DTO detached copy or Mutator
```

Dense row-index access：

```text
row index
  -> current RowSlot
  -> ColumnStore
  -> DTO detached copy or Mutator
```

Row Pipeline terminal：

```text
AccessPath
  -> RowSequence
  -> filter / sorted / skip / limit
  -> count / forEach / findFirst / firstOrThrow / fetchAll / update / remove
```

ColumnView：

```text
table.xxxColumn()
  -> acquire live readonly view
  -> read primitive/object column
  -> release
```

## 13. Schema source 与 runtime storage 边界

Java annotation DTO class 同时是 schema source 和 materialized DTO contract，但不能成为 runtime row object。

规则：

- processor 在 compile time 读取 annotation class；
- processor 输出 normalized schema model、schema hash 和 generated Java source；
- runtime table 使用 generated storage binding 和 `soma-runtime-core` primitive structures；
- hot loop 不创建 schema source class instance；
- `fetch(key)` / `findFirst()` / `firstOrThrow()` / `fetchAll()` materialize DTO detached copy，不是 live row proxy；
- ColumnView 是 live readonly view，必须有 owner holding 和 lifecycle rule。

## 14. Generated API 用户模型

用户模型采用 table-first API：

```text
OperationTable.create()
OperationBatch builder / importer
OperationTable.addBatch(batch)
OperationTable.fetch(key) -> Operation
OperationTable.filter(...).update(...)
OperationTable.byDispatchOrder().limit(n).fetchAll()
OperationTable.xxxColumn() -> ColumnView
OperationTable.mutate(key).field(...).commit()
```

V1 主 API 采用 Row Pipeline：table facade 本身是默认 row traversal source，参考 Java Stream 的 `filter` / `sorted` / `limit` / terminal 体验，但 pipeline 元素是 generated row cursor，不是 DTO object。Row Pipeline 支持受控原地更新；Java lambda filter 允许作为 row-level scan predicate，但不承诺自动下推到 index。需要 index/order 加速时，用户从 generated `findByXxx(...)` / `byXxx(...)` source method 进入同一套 pipeline。

V1 不提供 ORM query DSL、arbitrary join planner、parallel stream 或 `java.util.stream.Stream` 作为 hot path 主模型。复杂业务策略仍留在 Java solver core / application model 边界。

## 15. Runtime internal 用户不可见边界

以下内容属于 runtime/internal 或 generated internal，不进入 public API：

- bitmap word；
- cached hash；
- hash bucket layout；
- sparse set internal arrays；
- row pointer / row proxy；
- order sidecar object；
- allocator / growth factor policy；
- generated comparator internal class；
- processor normalized model class。

Public/generated API 只能暴露用户需要的 stable abstraction：table、batch、row cursor callback、DTO detached copy、ColumnView、mutator、typed runtime errors 和 stats。

## 16. 正确性信心来源

中间实现层的正确性不能靠架构口号证明，必须由分层不变量和证据建立。

V1 正确性信心来自：

- schema validation：非法 annotation、类型、field membership、selector、name collision 在 processor 阶段失败；
- normalized model：logical name、leaf order、selector path、schema hash canonical；
- generated golden：public API shape、DTO materialization、mutator、Row Pipeline、ColumnView shape 可复现；
- runtime component invariants：`RowSpace`、`ColumnStore`、`KeySpace`、`AccessStructures`、`LifecycleState` 各自可检查；
- cross-component invariants：row move、delete、replaceAll、update/remove terminal 后 key/index/order/bitmap/epoch 一致；
- differential oracle：测试可使用简单 `List/Map DTO` reference model 做 oracle，但 reference model 不进入 runtime 实现；
- gate reports：G1-G6 记录命令、artifact、结果、known limitations 和 release claim 边界。

Runtime correctness 细化以 `docs/runtime-correctness-model.md` 为准。

## 17. 性能信心来源

V1 性能设计的基础不是泛泛声明“columnar”，而是明确 hot path 和 allocation 边界。

性能信心应来自：

- primitive field 使用 primitive column；
- optional 使用 bitmap + payload column；
- hot Row Pipeline 不 materialize DTO；
- callback 使用 generated row cursor；
- batch import 以 `reserve` / `addBatch` / `replaceAll` 为主要边界；
- key lookup 由 `KeySpace` 承载；
- index/order source 由 maintained `AccessStructures` 和 `AccessPath` 承载；
- ColumnView 直接读取 live column storage；
- dynamic sort 使用 row permutation，不移动真实 column storage；
- benchmark smoke 只证明场景可运行；性能优势声明必须另有 baseline、规模、环境、重复次数和统计口径。

Runtime performance 细化以 `docs/runtime-performance-model.md` 为准。

## 18. Evidence 架构

V1 readiness 不是“代码能跑”，而是 gate evidence 闭环。

```text
contract docs
  -> compile/golden/runtime tests
  -> package smoke
  -> examples smoke
  -> benchmark smoke JSONL
  -> gate reports
  -> release readiness report
```

Gate sequence 以 `docs/validation-gates.md` 为准。架构上必须保证：

- G0 冻结 Java-only scope、架构、非目标和 release claim 边界；
- G1-G2 证明 annotation/processor/codegen；
- G3 证明 runtime core；
- G4 证明 generated API/package；
- G5 证明 examples/benchmark smoke；
- G6 汇总 release readiness。

Reports 是证据，不是设计事实源。任何 release claim 都必须能回到正式 docs 和正式 reports。

## 19. Java 8 与无第三方依赖

V1 baseline 是 Java 8。

V1 在正式设计完成前不引入第三方依赖。底层 `ColumnStore`、bitmap、`SparseIntKeySpace`、`HashKeySpace`、`AccessStructures`、`AccessPath` 和 benchmark harness 先由本项目自有实现承载。

后续如果引入第三方库，必须先有正式设计决策，说明：

- 依赖只作为 internal implementation detail；
- public/generated API 不暴露第三方类型；
- 替换或移除依赖不会破坏 schema、generated API 或 release evidence；
- license、Java 8 compatibility 和可复现构建已确认。

## 20. 架构反模式

以下做法与本架构冲突：

- annotation class 直接成为 runtime row storage；
- generated API 返回 live row proxy object graph；
- hot path 使用 `List<DTO>` / `Map<Key, DTO>`；
- runtime 使用 reflection / metadata interpreter 作为主执行机制；
- dense table 被实现成没有 Row Pipeline、ColumnView、order、lifecycle 的普通数组；
- primary key lookup 被当作普通 secondary index；
- mutation 绕过 `MutationCoordinator`，让 subcomponent 各自隐藏 side effect；
- public/generated API 暴露 sidecar、bitmap word、hash bucket、row pointer、allocator policy；
- runtime-core 依赖 processor 或 annotation element；
- benchmark smoke 被写成性能优势声明；
- Java 8 target 失效。

## 21. V1 非目标

V1 不做：

- `.soma` text grammar；
- native runtime；
- C ABI / FFI；
- Python binding；
- persistence format；
- schema migration；
- arbitrary object graph storage；
- short-lived Java temporary object manager；
- ORM / SQL / join planner；
- automatic lambda-to-index query engine；
- built-in thread-safe table；
- parallel scan / parallel sort；
- production-grade all-platform packaging matrix。

## 22. Release claim 边界

V1 可以声明：

- Java 8 annotation schema 可以生成 table-first runtime state API；
- generated table 使用 Java columnar storage 和 sidecar 支持 key/index/unique/order access；
- V1 覆盖明确列出的 DTO materialization、Row Pipeline、ColumnView、lifecycle、错误、package smoke、examples 和 benchmark smoke。

V1 不应声明：

- native hot layout；
- C ABI / Python 复用；
- 比通用 Java collection 更快，除非有基准对照；
- 完整 schema evolution；
- 任意对象映射；
- 生产级并发容器。

## 23. 下游设计文档

本文只定义项目级架构。下游设计文档应在本文约束下继续细化：

- `docs/implementation-strategy.md`：推荐实现方案、落地路线、不推荐方案和实施阶段不缩水约束；
- `docs/runtime-correctness-model.md`：定义 runtime invariants、mutation state machine、differential oracle；
- `docs/runtime-performance-model.md`：定义 hot path、复杂度、allocation、sidecar rebuild 和 benchmark claim；
- `soma-testkit/docs/`：待补，定义 compile/golden/runtime invariant helper contract；
- `soma-benchmarks/docs/`：待补，定义 runner、JSONL evidence schema 和 baseline claim 口径。

下游文档不得重定义项目级架构。若发现本文不足以支撑下游设计，应先修改本文，再修改下游文档。
