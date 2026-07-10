# Code generation 契约

状态：正式设计文档
Owner：`soma-processor`
事实范围：schema-specific Java artifacts、static runtime binding、deterministic output、golden 和 package smoke
非事实范围：public annotation semantics、normalization/hash 算法、public API behavior 和 runtime kernel
最后审查日期：2026-07-10

## 1. 目标

本文定义 processor 从 validated normalized schema 生成什么，以及 generated source 必须如何静态绑定 runtime。Processing、validation、hash 和 diagnostics 由 [Schema processing 契约](schema-processing-contract.md) 拥有。

Generated Table 的用户语义由根级 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 拥有；recursive materialization 由 [Materialization 契约](../../docs/materialization-contract.md) 拥有。本文只规定 codegen 如何满足这些契约。

Generated hot-loop shape 必须遵守 [Runtime 性能实现契约](../../soma-runtime-core/docs/runtime-performance-implementation-contract.md)。Codegen 不能绕过 validated normalized model 重新解释 Java source element。

## 2. Generated artifacts

Processor 至少提供两类 compile-time output。

`@SomaValue` effective type lowering：

- class final；
- annotated fields `public final`；
- canonical all-fields constructor / optional `of(...)` factory；
- canonical value `equals()` / `hashCode()`；
- deterministic `toString()`；
- 无 setter、无 mutable escape hatch。

每个 schema table 至少生成：

- generated `Table`；
- generated `Batch`；
- generated schema-object/`List`/`Map` materializer；
- generated `List`/`Map` child-field live access API when applicable；
- generated `Rows` / row cursor / mutable row cursor；
- generated `ColumnView` wrapper or typed column view access；
- generated `Mutator`；
- generated key/value classes when needed；
- generated `MaterializationBudget` overload binding；
- generated internal storage adapter。

不生成 public `XxxRecord` / `ChildRecords` 第二类型。`@SomaValue` 由 [Compiler integration 契约](compiler-integration-contract.md) 定义的 javac 8 parse-phase plugin lowering；JSR 269 processor 只消费 lowered effective model并生成 companions。Package smoke 必须证明用户源码、processor 和 generated companions 看到同一个 effective type；runtime 不得承担 annotation interpretation。

Generated hot path 还必须满足：

- normalized leaf 静态绑定到 concrete primitive/reference column；
- primitive getter/setter、key leaf、selector leaf 和 comparator 不通过 `Object` boxing；
- composite key lookup 不生成 runtime transient tuple/key wrapper；
- Cursor 在 terminal 内复用或由等价 index-based accessor 替代，不按 row 构造；
- filter/skip/limit/terminal 可以 fuse 到单次 traversal，不生成 intermediate Collection；
- dynamic sort 使用 primitive row-index/slot buffer；
- terminal boundary 可以安全 hoist lifecycle/epoch/column binding checks；
- common no-callback terminal 不通过 generic callback interpreter 执行；
- generated call site 应保持 JVM 可内联的 monomorphic/final/static 形态，具体效果由 allocation/bytecode/JIT benchmark evidence 验证。

Generated source 依赖：

- `soma-runtime-core`；
- JDK 8；
- 不依赖 third-party collection library。

Compiler plugin/processor 是 build-only dependency，不进入 generated runtime dependency graph。IDE code insight、其他 javac family 和 ECJ support 不能由 generated-source compile success 推导。

## 3. Table API binding

Generated artifact 必须完整实现根级 [Generated Table API 契约](../../docs/generated-table-api-contract.md)：

- keyed/dense facade、Batch、Rows/Cursor、Mutator；
- Direct API、Row/Key/Column Pipeline、typed child facade、ColumnView；
- detached materializing API 与 budget overload；
- typed result/error adaptation。

Codegen 只拥有 schema-specific type/name/static binding，不重新定义 public method semantics。任何新增 convenience method 都必须先进入根级 API contract 和 processor golden，不能只在 codegen 文档中成为隐式 public capability。

Keyed table 绑定 single generated key type 与 KeySpace；dense table 不生成 stable key。Dense row index 只绑定当前 packed state。Public API 不暴露 RowSlot、bitmap、sidecar、ChildTableHandle 或 column mutation primitive。

## 4. Batch API binding

Generated batch 是 construction/import boundary。

规则：

- batch builder 不直接写 table；
- batch 可以估算 row count；
- table `addBatch` 可根据 batch size reserve；
- child table import 使用 detached/unattached child batch；batch 不接受 live ChildTable facade/handle，lookup data 也不应默认建成 child table；
- batch 不承担 key uniqueness 的最终事实，table import 时仍需 runtime `KeySpace` / `AccessStructures` validation。

## 5. Materialization and child binding

Generated table 的 `find` / `fetch` / `fetchAt` 和 Row Pipeline 的 `findFirst()` / `firstOrThrow()` 直接返回对应 `@SomaTable` schema class 或 optional wrapper；Row Pipeline `fetchAll()` 返回 `List<R>`。Whole-table `materialize()` 对 dense 返回 `List<R>`，对 keyed 返回 `Map<K,R>`。

### 5.1 Materialized shape

- materializer 构造 caller-owned schema class instance，不返回 live row proxy；
- materializer 从 configured generated package 直接调用 schema class 的 public no-arg constructor，并显式写入每个 public mutable schema field；
- processor 在生成前验证 public、非 abstract、public no-arg construction 与 public mutable field shape；不依赖 `@SomaTable` lowering、runtime reflection 或 schema-package access bridge；
- constructor/field initializer 不提供 schema default；constructor 失败时不返回 partial object，Table 与 epoch 保持不变；
- scalar/value field 复制当前 logical value，不暴露 flattened column detail；immutable value 可以安全共享或重新构造，具体 accounting 必须确定；
- optional absent 映射为 schema field `null`；optional primitive source 使用 boxed type；
- `@SomaChild List<R>` 递归 materialize dense child；`@SomaChild Map<K,R>` 递归 materialize keyed child；
- required empty child 映射为 non-null empty `List`/`Map`；
- optional absent child 与 present-empty child 保持不同；
- ordinary key reference 只保留 key value，不自动 lookup referenced table；
- returned graph 不包含 `RowSlot`、`ChildTableHandle`、bitmap、sidecar、ColumnView 或 Row Cursor；
- 修改、保存或丢弃 schema object/collection 不改变 Table；写入必须重新经过 generated Batch/Mutator/mutation API。

Processor 不为 `@SomaTable` class 生成 structural `equals()` / `hashCode()`；`List`/`Map` 使用 Java Collection contract。`@SomaValue` 由 compile-time lowering 提供 canonical value equality/hash，并可作为 keyed materialization 的 map key。

### 5.2 Recursive materialization and budget overload

所有 materializer 同时提供默认预算与显式预算形态：

```text
find(key) / find(key, budget)
fetch(key) / fetch(key, budget)
fetchAt(rowIndex) / fetchAt(rowIndex, budget)
findFirst() / findFirst(budget)
firstOrThrow() / firstOrThrow(budget)
fetchAll() / fetchAll(budget)
materialize() / materialize(budget)
```

无 budget overload 使用 table runtime plan 默认 `MaterializationBudget`。显式 overload 的 budget 适用于整个 materialization invocation，不按 root row 重置。Codegen 负责把 ownership/materialization path、field/table names 和 schema object/List/Map/entry construction 绑定到 runtime budget tracker。

### 5.3 Generated child-field API

Required `@SomaChild` field 生成按 parent key/row locator 访问的 typed live child table facade，例如 `children(parentKey)`；logical empty child 可以保持 lazy storage。Optional child 至少生成：

```text
childrenPresent(parentKey)
childrenOrThrow(parentKey)
ensureChildren(parentKey)
replaceChildren(parentKey, childBatch)
unsetChildren(parentKey)
```

`children(parentKey).clear()` / `childrenOrThrow(parentKey).clear()` 表示 present-empty；`unsetChildren(parentKey)` 才表示 absent 并 cascade release。Dense parent 使用 row-index locator 的等价 overload。不得生成同时表达 clear-content 和 unset-ownership 的模糊 `clearChildren()`。

Generated API 不接受 arbitrary live child table object，也不把 materialized `List`/`Map` attach 为 live storage，不暴露 child handle/reparent operation。Parent import/replacement 使用 detached child Batch，runtime 完成 stage/validate/handle switch/cascade release。

## 6. Row/Key/Column Pipeline binding

Generated Row Pipeline 是 lazy row traversal plan，不是 Java Stream，也不是 materialized schema object result。

V1 generated Rows 至少覆盖：

- table facade default packed scan source；
- explicit `rows()` source alias；
- generated index / unique source method, named as `findByXxx(...)` by default；
- generated order source method, named as `byXxx(...)` by default；
- Java lambda `filter(predicate)`；
- dynamic `sorted(comparator)`；
- `skip(n)` / `limit(n)`；
- read terminal `count` / `anyMatch` / `noneMatch` / `forEach` / `findFirst` / `fetchAll`；
- dense / diagnostic terminal `rowIndexes`；
- mutation terminal `update(updater)` / `remove()`；
- keyed table `keys()` key pipeline；
- generated column pipeline method when supported。

Row Pipeline construction 不扫描 table、不复制 row、不 acquire ColumnView。Terminal operation 基于执行时 table current state。

Row Pipeline callback 参数是 generated row cursor / mutable row cursor，不是 schema object。Cursor 只在 callback 调用期间有效，不允许逃逸。

V1 支持 arbitrary Java lambda 作为 row-level filter/update callback，但不承诺 lambda predicate 自动下推到 index。需要 index/order 加速时，用户应从 generated source method 进入同一套 Rows pipeline。V1 不生成 `java.util.stream.Stream` bridge、parallel stream、join planner 或 ORM query DSL。

Row Pipeline source/terminal generation 不得为每个 candidate row 创建 Cursor、Iterator、Optional、boxed row index 或 stage result。Pipeline/callback object 可以在 construction/call boundary 产生；steady-state non-materializing terminal 的 allocation target 是 zero per row。Materializing terminal 的 schema object/List/Map allocation必须继续与 traversal stats 分开。

### 6.1 Grouped index/order source

V1 codegen 必须把 normalized selector 转换成稳定的 generated source method。Grouped source 是 generated API convenience，不是新的 schema kind，也不暴露 runtime sidecar。

规则：

- `@SomaIndex` / `@SomaUnique` 的 source method 默认命名为 `findByXxx(...)`，返回该 table 的 generated `XxxRows`；
- `@SomaOrder` 的 source method 默认命名为 `byXxx(...)`，返回同一 generated `XxxRows`；
- grouped source 的参数来自 normalized selector prefix；
- 如果 selector prefix 正好覆盖一个 scalar/value field path 的全部 leaf，generated method 使用该 scalar/value type 作为参数；
- 如果 selector prefix 不能映射为一个 scalar/value field path，generated method 使用 normalized leaf 参数顺序；
- generated method name、参数名、参数顺序和 overload 冲突必须由 golden 固化；
- 命名冲突或 ambiguous overload 必须在 processor validation 阶段失败，不能生成不可编译代码；
- grouped source 只选择 terminal 初始 `RowSequence`，后续仍使用同一套 `filter` / `sorted` / `limit` / terminal API。

Grouped source 必须由 processor golden 覆盖至少以下形状：

- composite/value selector 参数折叠；
- grouped index、unique 和 order source；
- parent-owned dense/keyed child source；
- method/parameter/overload collision diagnostics；
- FJSP、VRP 等 formal example 中声明的 canonical access path。

具体业务 schema 和 usage flow 进入 [soma-examples](../../soma-examples/docs/README.md)，codegen contract 不复制完整场景代码。

### 6.2 Floating access binding

Processor 根据 normalized field role 为 key/index/unique/order 中的 floating leaf 生成统一 validation/canonicalization binding：

- schema default 编译期拒绝 NaN/infinity，negative zero normalized 为 positive zero；
- Batch/import 和 Mutator 写入前检查 finite/canonical zero；
- key lookup 与 generated index/unique/order source 参数使用同一 canonicalization；
- equality/hash/index matching/order comparator 绑定同一 canonical value；
- diagnostics 包含 declaration/selector leaf path，不压缩为 generic invalid input。

Ordinary payload floating leaf 不生成上述 finite rejection。全局 floating semantics 变化必须触发 processor/runtime compatibility version 变化，并由 golden/compatibility tests 固化。

## 7. Mutator binding

Generated mutator 用于 existing row mutation。

规则：

- mutator 不生成 key setter；
- key identity change 必须 delete + insert；
- mutator commit 时更新 affected column；
- 修改 index/unique/order selector 字段时同步更新或标记 dirty；
- active ColumnView 下 structural mutation 映射为 view_pinned；
- mutation error 必须保留 typed runtime exception / error code。

## 8. ColumnView binding

Generated table 可以暴露 typed readonly ColumnView。

规则：

- ColumnView 强持有 owner table；
- ColumnView 有 explicit close/release；
- release idempotent；
- close 后读取返回 released_view 类错误；
- store epoch mismatch 返回 stale_view；
- structural mutation 遇到 active ColumnView 返回 view_pinned；
- ColumnView 不承诺 snapshot isolation。

## 9. Generated package and names

V1 generated package 优先由 `package-info.java` 中的 `@SomaSchema.generatedPackage` 得到。缺省策略如果存在，必须由 processor 配置明确声明，并进入 generated metadata。

Generated names 必须避免冲突：

- table name；
- batch name；
- key/value name；
- index/unique/order access method name；
- optional presence method name；
- materialization/budget overload method name；
- child presence/ensure/replace/unset method name；
- mutator method name。

命名冲突必须在 processor validation 阶段失败，不能生成不可编译代码。

## 10. Schema hash and metadata

Generated output 必须包含：

- schema hash；
- schema version；
- generated target：`java8-columnar`；
- processor version；
- runtime compatibility version；
- runtime plan hash、capacity/KeySpace/access-structure policy identity、stats mode、default MaterializationBudget 和 allocation estimator version；
- schema name；
- generated package；
- table metadata。

Schema hash mismatch 必须在 table/create or generated metadata verification 阶段失败，不能延迟到 hot path。

## 11. Deterministic output

Codegen output 必须可复现：

- 不输出 timestamp；
- 不输出 local path；
- 不输出 random id；
- import order stable；
- method order stable；
- generated source formatter stable；
- same source + same processor/lowering identity + same supported javac 8 toolchain -> same generated source。

Golden output comparison 可以忽略明确声明的 non-semantic whitespace，但不能忽略 public API shape。

## 12. Golden cases

V1 golden cases 至少覆盖：

- simple value；
- keyed table；
- dense table；
- optional boxed schema/materialization field；
- enum field；
- string field；
- value key；
- index/unique/order；
- grouped index source, for example `findByJobSequence(JobId jobId, int sequenceNo)`；
- frontier grouped index source, for example `findByMachine(MachineId machineId)` and `findByOperation(OperationKey operationKey)` on `MachineCandidate`；
- grouped order source for a flat baseline, for example `byRoutePosition(RouteId routeId)`；
- parent-key live child source and child-local order, for example `routes.visits(routeId).byPosition()`；
- invalid selector；
- duplicate key declaration；
- generated mutator without key setter；
- Row Pipeline with table-default `filter` / `sorted` / `limit` / `findFirst` / `fetchAll` / `update` / `remove`；
- generated fused primitive hot-loop shape、cursor reuse、no intermediate collection and no per-row boxing/allocation；
- `@SomaValue` implicit public-final/canonical-construction/equality/hash effective shape；
- recursive schema object / `List` / `Map` materialization shape；
- table-row identity default、Java Collection equality 与 value equality boundary；
- floating access validation/canonicalization binding；
- child ownership cycle diagnostics and child API shape；
- MaterializationBudget overload and error-path binding；
- ColumnView access shape；
- deterministic schema hash；
- deterministic generated source。

## 13. Package smoke

V1 package smoke 必须使用 Maven artifact 或 reactor equivalent，不能只依赖 IDE classpath。

Package smoke 至少验证：

- user schema source can compile with the supported transformer/annotation processor path；
- `@SomaValue` effective modifiers、constructor 和 equality/hash 对 generated companions 与 user code 一致可见；
- transformer 缺失、unsupported compiler 和 compiler identity mismatch fail closed；
- `@SomaSchema` package metadata can be read by the processor；
- generated source can compile under Java 8 target；
- generated table can create runtime storage；
- addBatch / fetch returning schema class / whole-table List-or-Map materialize / Row Pipeline filter/update/remove/fetchAll / index source / ordered source / key pipeline / child API / ColumnView can execute；
- default/explicit MaterializationBudget overload and typed budget error can execute；
- schema hash metadata exists；
- runtime stats can be read；
- no third-party runtime dependency is required。

Package smoke 使用 canonical Maven external consumer；IDE editor/JPS experience 不在没有专用 adapter evidence 时冒充为 supported build claim。

## 14. 非目标

V1 codegen 不做：

- `.soma` source parser；
- native metadata target；
- C ABI metadata target；
- Python facade target；
- runtime reflection query engine；
- `java.util.stream.Stream` 作为 hot path 主 pipeline；
- dynamic schema；
- source-compatible schema migration。
