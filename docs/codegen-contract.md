# Java codegen 契约

状态：正式设计文档
日期：2026-07-06
Owner：`soma-processor`

## 1. 目标

本文定义 annotation processor 的 generated Java API、deterministic output、golden output 和 package smoke 口径。

Codegen 只能从 normalized schema model 读取事实，不能绕过 validation 直接解释 Java source element。

## 2. Pipeline

```text
annotated Java source + `@SomaSchema` package metadata
  -> annotation processing model
  -> semantic validation
  -> normalized schema model
  -> canonical schema hash
  -> generated Java source
  -> diagnostics report
```

如果 validation 失败，processor 必须跳过对应 schema 的 codegen，并输出机器可读 diagnostics。

## 3. Generated artifacts

每个 schema table 至少生成：

- generated `Table`；
- generated `Batch`；
- generated DTO materialization mapper；
- generated `Rows` / row cursor / mutable row cursor；
- generated `ColumnView` wrapper or typed column view access；
- generated `Mutator`；
- generated key/value classes when needed；
- generated internal storage adapter。

Generated source 依赖：

- `soma-runtime-core`；
- JDK 8；
- 不依赖 third-party collection library。

## 4. Table API

Generated table API 采用 table-first model。

最低 common API 形态：

```text
create()
reserve(size)
addBatch(batch)
replaceAll(batch)
clear()
count()
fetchFirst()
fetchAll()
rows()
filter(predicate)
xxxColumn()
```

Keyed table 额外暴露：

```text
containsKey(key)
fetch(key)
mutate(key)
delete(key)
keys()
firstKey()
firstKeyOrThrow()
```

Dense table 额外暴露：

```text
rowIndexes()
fetchAt(rowIndex) or equivalent row-index DTO materialization
```

Keyed table 和 dense table 必须分开：

- keyed table 暴露 single generated key type；
- keyed table 用于 entity state、lookup table、唯一性约束和 key-based mutation；
- dense table 不暴露 stable key；
- dense table 用于 packed scan、row-index iteration、矩阵/数组型 runtime state 和 solver workspace；
- dense row index 只对 terminal execution 的 table state 有效；
- `fetchFirst` / `fetchAll` materialize DTO detached copy，不暴露 live row pointer。

## 5. Batch API

Generated batch 是 construction/import boundary。

规则：

- batch builder 不直接写 table；
- batch 可以估算 row count；
- table `addBatch` 可根据 batch size reserve；
- child table import 使用 unattached child batch，但 lookup data 不应默认建成 child table；
- batch 不承担 key uniqueness 的最终事实，table import 时仍需 runtime sidecar validation。

## 6. DTO materialization API

Annotated DTO 是 V1 public materialized DTO object。Generated table `fetch(key)` 与 Row Pipeline 的 `findFirst()` / `fetchAll()` 返回 DTO detached copy。

规则：

- DTO class 是 schema source，也是 materialized DTO type；
- V1 不生成独立 public `Record` 类型；
- 不提供 `fetchDto()`，keyed table 使用 `fetch(key)` 返回 DTO；
- optional absent 在 DTO 字段中 materialize 为 `null`；
- 修改 materialized DTO 不会写回 table；
- 写回 table 必须通过 generated mutator / batch API；
- materialized DTO 不暴露 live row pointer、bitmap、sidecar 或 runtime internal handle。

## 7. Row Pipeline API

Generated Row Pipeline 是 lazy row traversal plan，不是 Java Stream，不是 materialized DTO result。

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

Row Pipeline callback 参数是 generated row cursor / mutable row cursor，不是 DTO。Cursor 只在 callback 调用期间有效，不允许逃逸。

V1 支持 arbitrary Java lambda 作为 row-level filter/update callback，但不承诺 lambda predicate 自动下推到 index。需要 index/order 加速时，用户应从 generated source method 进入同一套 Rows pipeline。V1 不生成 `java.util.stream.Stream` bridge、parallel stream、join planner 或 ORM query DSL。

## 8. Mutator API

Generated mutator 用于 existing row mutation。

规则：

- mutator 不生成 key setter；
- key identity change 必须 delete + insert；
- mutator commit 时更新 affected column；
- 修改 index/unique/order selector 字段时同步更新或标记 dirty；
- active ColumnView 下 structural mutation 映射为 view_pinned；
- mutation error 必须保留 typed runtime exception / error code。

## 9. ColumnView API

Generated table 可以暴露 typed readonly ColumnView。

规则：

- ColumnView 强持有 owner table；
- ColumnView 有 explicit close/release；
- release idempotent；
- close 后读取返回 released_view 类错误；
- store epoch mismatch 返回 stale_view；
- structural mutation 遇到 active ColumnView 返回 view_pinned；
- ColumnView 不承诺 snapshot isolation。

## 10. Generated package and names

V1 generated package 优先由 `package-info.java` 中的 `@SomaSchema.generatedPackage` 得到。缺省策略如果存在，必须由 processor 配置明确声明，并进入 generated metadata。

Generated names 必须避免冲突：

- table name；
- batch name；
- key/value name；
- index/unique/order access method name；
- optional presence method name；
- DTO materialization method name；
- mutator method name。

命名冲突必须在 processor validation 阶段失败，不能生成不可编译代码。

## 11. Schema hash and metadata

Generated output 必须包含：

- schema hash；
- schema version；
- generated target：`java8-columnar`；
- processor version；
- runtime compatibility version；
- schema name；
- generated package；
- table metadata。

Schema hash mismatch 必须在 table/create or generated metadata verification 阶段失败，不能延迟到 hot path。

## 12. Deterministic output

Codegen output 必须可复现：

- 不输出 timestamp；
- 不输出 local path；
- 不输出 random id；
- import order stable；
- method order stable；
- generated source formatter stable；
- same source + same processor version + same Java 8 toolchain -> same generated source。

Golden output comparison 可以忽略明确声明的 non-semantic whitespace，但不能忽略 public API shape。

## 13. Golden cases

V1 golden cases 至少覆盖：

- simple value；
- keyed table；
- dense table；
- optional boxed DTO field；
- enum field；
- string field；
- value key；
- index/unique/order；
- invalid selector；
- duplicate key declaration；
- generated mutator without key setter；
- Row Pipeline with table-default `filter` / `sorted` / `limit` / `findFirst` / `fetchAll` / `update` / `remove`；
- DTO materialization shape；
- ColumnView access shape；
- deterministic schema hash；
- deterministic generated source。

## 14. Package smoke

V1 package smoke 必须使用 Maven artifact 或 reactor equivalent，不能只依赖 IDE classpath。

Package smoke 至少验证：

- user schema source can compile with annotation processor；
- `@SomaSchema` package metadata can be read by the processor；
- generated source can compile under Java 8 target；
- generated table can create runtime storage；
- addBatch / fetch returning DTO / Row Pipeline filter/update/remove / index source / ordered source / key pipeline / ColumnView can execute；
- schema hash metadata exists；
- runtime stats can be read；
- no third-party runtime dependency is required。

## 15. Non-goals

V1 codegen 不做：

- `.soma` source parser；
- native metadata target；
- C ABI metadata target；
- Python facade target；
- runtime reflection query engine；
- `java.util.stream.Stream` 作为 hot path 主 pipeline；
- dynamic schema；
- source-compatible schema migration。
