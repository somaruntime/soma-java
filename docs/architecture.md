# soma_java 架构设计

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 定位

`soma_java` 是 SOMA 的 Java-only 原型路线。它不实现 `.soma` IDL parser、native runtime、C ABI、Python binding 或跨语言 FFI。

本项目的 V1 方向是：

```text
Java annotation schema
  -> Java 8 annotation processor
  -> normalized schema model / schema hash
  -> generated Table / Batch / Row Pipeline / DTO materialization / ColumnView
  -> Java columnar runtime kernel
  -> examples / benchmark / gate evidence
```

SOMA 的产品定位仍然是运行时高性能数据容器和 runtime state schema，不是 protobuf、ORM、ECS、对象映射框架或通用 collection library。

```text
protobuf / API DTO = API / wire / persistence schema
soma_java          = Java runtime state / hot layout schema / high-performance data container
OOP application    = workflow orchestration / algorithm strategy
```

`soma_java` 覆盖三类长生命周期 runtime state：

1. entity state，例如 `Job`、`Operation`、`Machine`、`Material`；
2. frequently queried static / imported data，例如 operation-machine processing time、city-to-city distance matrix；
3. packed row-index data，例如矩阵行、数组型 runtime state、dense solver workspace。

短生命周期 Java 临时对象不是 SOMA 的核心 scope。SOMA 可以提供 dense table 作为可复用 workspace，但不把普通局部变量、一次性 DTO 或对象池管理作为产品目标。

## 2. V1 北极星

V1 的北极星是：

```text
Java 8 用户用 annotation class 定义运行时状态 schema，
annotation processor 生成 table-first Java API，
底层由 Java primitive column、bitmap、index sidecar 和 order sidecar 承载 hot runtime state，
并把 table 明确收敛为 keyed table 与 dense table 两类。
```

用户不应在 hot loop 中直接维护：

- `List<DTO>` runtime row storage；
- `Map<Key, DTO>` runtime row storage；
- 手写 scattered index / order / packed scratch arrays；
- reflection-based runtime schema access；
- third-party collection type 作为 public/generated API。

## 3. Table 分类

V1 table 只分为两类：

| Table kind | 定义 | 主要使用场景 |
|---|---|---|
| keyed table | 有 stable logical key | entity state、lookup table、唯一性约束、`fetch(key)`、`mutate(key)` |
| dense table | 无 stable logical key | packed scan、row-index iteration、批量替换、矩阵/数组型 runtime state、solver workspace |

`entity`、`lookup`、`matrix`、`workspace` 是建模场景，不是 schema kind。V1 不引入 `@SomaTableRole`。是否 keyed 只由是否声明 `@SomaKey` 决定。

Child table 是 parent-owned ownership shape，不是第三种 table kind。它只适合 parent row 拥有 child table 生命周期的场景；FJSP 中的 operation-machine processing time 应建成独立 keyed lookup table，不应作为 `Operation` 的 child table。

## 4. 模块结构

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
| `soma-runtime-core` | Java columnar runtime kernel、primitive columns、bitmap、sparse set、indexes、order sidecar、lifecycle、runtime errors |
| `soma-testkit` | compile/golden/runtime invariant test helpers |
| `soma-examples` | Java 8 examples and end-to-end smoke scenarios |
| `soma-benchmarks` | benchmark scenarios and structured evidence output |

根项目只负责跨模块架构、正式设计入口、release gate 和文档治理，不拥有模块内部实现细节。

## 5. 依赖方向

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
- examples 和 benchmarks 只能消费发布形态或 reactor artifact，不能绕过 generated API 访问 internal sidecar；
- testkit 只能服务测试和 golden evidence，不能成为 runtime 必需依赖。

## 6. Schema source 与 runtime storage 的边界

Java annotation DTO class 同时是 schema source 和 materialized DTO contract，但不能成为 runtime row object。

规则：

- processor 在 compile time 读取 annotation class；
- processor 输出 normalized schema model、schema hash 和 generated Java source；
- runtime table 使用 generated storage class 和 `soma-runtime-core` primitive structures；
- hot loop 不创建 schema source class instance；
- `fetch(key)` / `findFirst()` / `firstOrThrow()` / `fetchAll()` materialize DTO detached copy，不是 live row proxy；
- ColumnView 是 live readonly view，必须有 owner holding 和 lifecycle rule。

## 7. Generated API 用户模型

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

## 8. Runtime kernel 用户不可见边界

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

## 9. Java 8 与无第三方依赖

V1 baseline 是 Java 8。

V1 在正式设计完成前不引入第三方依赖。底层 primitive column、bitmap、sparse set、hash index、order sidecar 和 benchmark harness 先由本项目自有实现承载。

后续如果引入第三方库，必须先有正式设计决策，说明：

- 依赖只作为 internal implementation detail；
- public/generated API 不暴露第三方类型；
- 替换或移除依赖不会破坏 schema、generated API 或 release evidence；
- license、Java 8 compatibility 和可复现构建已确认。

## 10. V1 非目标

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

## 11. Release claim 边界

V1 可以声明：

- Java 8 annotation schema 可以生成 table-first runtime state API；
- generated table 使用 Java columnar storage 和 sidecar 支持 key/index/unique/order access；
- V1 覆盖明确列出的 DTO materialization、lifecycle、错误、package smoke、examples 和 benchmark smoke。

V1 不应声明：

- native hot layout；
- C ABI / Python 复用；
- 比通用 Java collection 更快，除非有基准对照；
- 完整 schema evolution；
- 任意对象映射；
- 生产级并发容器。
