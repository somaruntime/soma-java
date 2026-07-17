# Java annotation schema 契约

状态：正式设计文档
Owner：`soma-annotations`
事实范围：public schema annotation、类型系统、field role、optional/default、key/index/unique 和 child declaration
非事实范围：normalization、schema hash、diagnostics、generated API、runtime storage 和示例场景
最后审查日期：2026-07-10

## 1. 目标

Java annotation schema 是 `soma_java` V1 的唯一 schema source。本文只定义用户在 Java source 中声明什么、声明具有什么 logical meaning。

`@SomaTable` class 定义 row schema，并作为 detached single-row materialization carrier；它不兼任 live runtime storage。`@SomaValue` 定义 compiler-supported immutable inline value。Generated API 和 materialization 的跨模块语义分别由 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 与 [Materialization 契约](../../docs/materialization-contract.md) 拥有。

Compiler lowering 由 [compiler integration 契约](../../soma-processor/docs/compiler-integration-contract.md) 拥有；processing、normalized schema、exact hash 和 diagnostics 由 [schema processing 契约](../../soma-processor/docs/schema-processing-contract.md) 拥有。

## 2. Schema declaration

V1 schema declaration 至少包含以下概念：

| 概念 | Java schema source | 语义 |
|---|---|---|
| schema | `package-info.java` + `@SomaSchema` | schema identity、generated package、version policy |
| enum | Java `enum` referenced by SOMA field | fixed enum member set and ordinal storage metadata |
| value | class + `@SomaValue` | compiler-defined immutable inline value，expanded into leaf columns |
| table row | class + `@SomaTable` | keyed/dense SomaTable 的 row schema 与 detached materialization carrier |
| field | `@SomaField` | required field, optional logical name / semantic scalar metadata |
| key | `@SomaKey` | single logical primary key field, optional logical name / semantic scalar metadata |
| child | `@SomaChild` + `List<R>` / `Map<K,R>` | parent-owned dense/keyed child table field + optional per-field initial capacity |
| optional | `@SomaOptional` | field modifier；presence bitmap + payload/handle column，materialized as `null` absence |
| default | `@SomaDefault` | deterministic schema default for required field |
| ignore | `@SomaIgnore` | explicitly excluded declaration helper field |
| index | `@SomaIndex` / `@SomaIndexes` | secondary non-unique access |
| unique | `@SomaUnique` / `@SomaUniques` | secondary unique access |

V1 要求 schema source 显式表达 SOMA 语义。普通 Java field、getter、setter、bean naming 或 Java 字段初始化表达式不能自动成为 schema fact。`@SomaValue` 的 implicit final/public/construction/equality/hash 属于 SOMA compiler semantics，不是普通 Java modifier 推断，也不依赖用户同时标注 Lombok annotation。

用户侧 annotation 使用 `field` 语义，不使用 `column` 命名。`column` 是 value flatten 之后的 runtime 物理概念，不作为 V1 public annotation 名称。

### 2.1 Annotation ergonomics

V1 annotation API 同时追求表达能力和易用性。规则：

- `@SomaTable.name` 缺省时使用 Java class simple name 作为 logical table name；需要稳定 snake case、plural name 或跨 Java 重命名保持 schema name 时，显式填写 `name`；
- `@SomaField.name`、`@SomaKey.name`、`@SomaChild.name` 缺省时使用 Java field name 作为 logical field name；显式 `name` 用于 schema logical name override；`@SomaOptional` 只表达 presence modifier，不另行拥有 logical name；
- selector path 使用 logical field name；如果字段设置了 `name` override，selector 必须使用 override 后的 logical path；
- generated Java API 方法名默认从 Java field name 派生，不因为 schema logical name override 破坏 Java 侧可读性；
- `@SomaIndex.value`、`@SomaUnique.value` 是 `name` 的 alias，但 Java annotation 语法在同时设置其他参数时仍应使用 named form；
- normalized schema model 必须把所有缺省值归一化为显式 logical name 和 semantic，因此 annotation 简写不影响 schema hash 的确定性。

示例：

```java
@SomaTable(defaultCapacity = 4096) // logical table name defaults to Operation
@SomaIndex(name = "by_job", fields = {
    "operationKey.jobId.value"
})
public final class Operation {
    @SomaKey(name = "operation_key")
    public OperationKey operationKey;

    @SomaField(semantic = SomaSemantic.DATE_TIME)
    public long earliestStartMillis;
}
```

### 2.2 Package schema

每个 schema package 必须在 `package-info.java` 中声明 `@SomaSchema`：

```java
@SomaSchema(
    name = "fjsp_runtime_state",
    generatedPackage = "com.example.fjsp.state.generated",
    version = "1"
)
package com.example.fjsp.state;

import com.hgtech.soma.annotation.SomaSchema;
```

`@SomaSchema` 语义：

- `name` 是 schema logical name，进入 normalized schema model 和 `schema_hash`；
- `generatedPackage` 是 generated API package，进入 generated metadata；
- `version` 是用户侧 schema version label，进入 metadata，但不替代 exact `schema_hash`；
- 同一 compilation unit 中属于同一 schema 的 `@SomaValue` / `@SomaTable` / enum 必须可归入一个明确 schema package；
- V1 不支持一个 Java package 中声明多个独立 SOMA schema。

String identity grammar：

- annotation string 使用 source 中的 exact value，不执行 `trim`、case folding、Unicode normalization 或 locale-sensitive conversion；
- `@SomaSchema.name` 必须匹配 ASCII `[A-Za-z][A-Za-z0-9_]*`，长度不超过 128 UTF-16 code units；
- effective `@SomaTable` logical name 必须匹配同一 ASCII grammar，长度不超过 128 UTF-16 code units；explicit 空字符串只表示先派生 Java class simple name，派生结果仍必须通过相同 grammar；同一 schema 内 effective name 必须唯一；
- `@SomaSchema.generatedPackage` 必须是 `SourceVersion.RELEASE_8` 下的合法 dotted Java name，长度不超过 255 UTF-16 code units；
- `@SomaSchema.version` 长度为 1..128，不允许 ISO control character 或首尾 Unicode whitespace/space character；
- explicit field-role logical name 必须是非 keyword Java identifier，长度不超过 128 UTF-16 code units；空字符串只表示使用 Java field name；
- 不满足 grammar 的输入以 structured diagnostic fail closed，不以静默清洗后的值参与 schema hash。

V1 exact public shape：`@SomaTable` 包含 `String name() default ""` 与 `int defaultCapacity() default -1`；`@SomaChild` 包含 `String name() default ""` 与 `int initialCapacity() default -1`；marker `@SomaOptional` 不含 element。

`defaultCapacity = -1` 是稳定的“未指定”sentinel，由 generated default runtime plan 选择 versioned safe baseline；positive value 是显式 hint。`0` 和小于 `-1` 的值非法。Sentinel 只影响 runtime plan input，不进入 logical schema hash。`@SomaChild.initialCapacity` 使用相同的 `-1` sentinel 与正数约束：`-1` 表示继承 child table effective initial capacity，显式值必须大于 `0`；它进入 generated effective child plan/runtime plan hash，不进入 logical schema hash。

### 2.3 Annotation target and retention

V1 annotation API 的 target / retention 基线：

| Annotation | Target | Retention |
|---|---|---|
| `@SomaSchema` | `PACKAGE` | `SOURCE` |
| `@SomaValue` | `TYPE` | `SOURCE` |
| `@SomaTable` | `TYPE` | `SOURCE` |
| `@SomaField` | `FIELD` | `SOURCE` |
| `@SomaKey` | `FIELD` | `SOURCE` |
| `@SomaChild` | `FIELD` | `SOURCE` |
| `@SomaOptional` | `FIELD` | `SOURCE` |
| `@SomaDefault` | `FIELD` | `SOURCE` |
| `@SomaIgnore` | `FIELD` | `SOURCE` |
| `@SomaIndex` / `@SomaIndexes` | `TYPE` | `SOURCE` |
| `@SomaUnique` / `@SomaUniques` | `TYPE` | `SOURCE` |

V1 runtime 不通过 reflection 解释 schema。annotation retention 使用 `SOURCE`，javac 8 parse-phase transformer 负责 `@SomaValue` effective-type lowering，JSR 269 processor 负责 normalized schema model、metadata 和 generated Java source。Transformer 缺失或 compiler unsupported 时必须 fail closed，不得把 transformation 推迟到 runtime或静默退化为 mutable class。

Java enum 不需要额外 enum annotation。被 `@SomaField` 或 `@SomaKey` 引用的 Java enum 自动纳入 schema；enum member order 使用 source declaration order。

## 3. 类型系统

V1 类型分为：

- `boolean`；
- signed integer widths：`byte`、`short`、`int`、`long`；
- floating point：`float`、`double`；
- semantic scalar：date、time、date_time；
- Java enum；
- `String`；
- schema value；
- dense child field：`List<R>`；
- keyed child field：`Map<K,R>`。

`List`/`Map` 只允许作为 `@SomaTable` class 的 direct `@SomaChild` field，不允许进入 `@SomaValue`。Schema source 必须使用 exact `java.util.List` / `java.util.Map` parameterized type；raw collection、wildcard、nested collection、null element、null map key/value 不属于合法 V1 schema。

Java-only V1 不提供 unsigned primitive。业务上的非负数，例如 minute、count、input order、distance，可以使用 `int` 或 `long` 表达；非负约束由 loader、application validation 或后续 validation annotation 承担。V1 不引入 `@SomaRange`，也不把 unsigned range 作为 runtime storage type。

Semantic scalar storage：

| Semantic type | Storage |
|---|---|
| date | `int` epoch day |
| time | `long` nanoseconds since midnight |
| date_time | `long` UTC epoch milliseconds |

V1 不从 Java type name 猜测 semantic scalar。semantic scalar 必须由 annotation metadata 显式声明。`@SomaField` 和 `@SomaKey` 支持 `semantic = SomaSemantic.NONE | DATE | TIME | DATE_TIME`，缺省为 `NONE`；optional semantic scalar 仍在 `@SomaField` 上声明 semantic，并叠加 marker `@SomaOptional`。

`String` 在 Java runtime 中可以使用 object reference column 存储，但 schema 层仍只表达 `String` 语义，不暴露 JVM address 或 object identity。

因为 `@SomaTable` class 直接作为 detached row materialization type，optional primitive 必须在 Java schema source 使用 boxed type（`Boolean`、`Byte`、`Short`、`Integer`、`Long`、`Float`、`Double`）。Runtime 仍使用 primitive column + presence bitmap；boxed type 只服务 schema/materialization absence shape。Required primitive 继续使用 Java primitive。

### 3.1 Floating value domain

V1 采用 ordinary-payload/strict-access 分层策略：

- 不参与 key/index/unique 的 `float` / `double` leaf 允许 Java IEEE-754 `NaN`、positive/negative infinity 和 negative zero；
- `NaN` 不表示 optional absence，absence 只由 presence bitmap 表达；
- 参与 `@SomaKey` 或 `@SomaIndex` / `@SomaUnique` selector 的 floating leaf 必须 finite；
- strict access leaf 的 negative zero 在 schema default、Batch/import、Mutator、lookup 和 generated source parameter boundary canonicalize 为 positive zero；
- equality、hash 和 index matching 使用同一 canonical value；
- V1 不提供 per-field floating-policy annotation。

Processor 从 normalized field role 判断 floating leaf 是否 strict。Shared `@SomaValue` 可以在普通 field 中保留 ordinary semantics，也可以在 outer key/selector path 下获得 strict semantics；不能只根据 Value declaration 自身猜测。

这套全局策略不作为 per-schema 可配 metadata；策略版本由 processor/runtime compatibility identity 管理。具体 field role、selector path 和 default normalized result 仍进入 normalized schema model/schema hash。

## 4. Schema-backed class field membership

V1 采用 explicit field membership 与 orthogonal field modifier：

- `@SomaTable` 中的 instance field 必须显式标注一个 primary role：`@SomaField`、`@SomaKey`、`@SomaChild` 或 `@SomaIgnore`；
- `@SomaValue` 中的每个 instance field 都必须是 `@SomaField` schema state；non-static `@SomaIgnore` field 会引入不参与 canonical equality/hash 的隐藏 instance state，因此 V1 明确拒绝；
- `static` field 不属于 schema；
- `transient` field 不自动成为 schema field，建议显式标注 `@SomaIgnore`；
- unannotated instance field 是 processor error；
- `@SomaField`、`@SomaKey`、`@SomaChild`、`@SomaIgnore` 互斥；
- `@SomaOptional` 是 modifier，只能叠加在 `@SomaField` 或 `@SomaChild`；key/value leaf 不允许 optional；
- `@SomaDefault` 是附加 metadata，只能叠加在允许 default 的 field annotation 上；
- `@SomaTable` class 必须是 top-level、public、非 abstract、可实例化的普通 Java detached carrier；member/local/anonymous class 不属于 V1 table declaration；javac plugin 不对它执行 source lowering；
- `@SomaTable` class 不允许 type parameter；V1 generated table/static binding 不接受 generic row carrier；
- 每个 `@SomaField`、`@SomaKey` 或 `@SomaChild` schema field 必须是 public mutable instance field，不能是 `final`；generated materializer 必须能从 configured generated package 直接赋值；
- `@SomaTable` class 必须保留不声明 checked exception 的 public no-arg constructor；public class 在没有显式 constructor 时获得的 implicit public no-arg constructor 合法；用户可以声明其他 constructor，但不能移除该 construction path；
- Java field initializer 和 constructor body 是普通 Java 行为，不构成 schema default，也不进入 normalized schema/hash；materializer 在构造后显式写入每个 schema field；
- 用户可以修改 caller-owned detached `@SomaTable` object，但该修改不会写回 SomaTable；写入仍必须重新经过 Batch、Mutator 或 generated mutation API；
- inaccessible class/field/constructor、abstract carrier 或缺少 public no-arg constructor 必须在 processor 阶段 fail closed，不允许 runtime reflection 或 schema-package access bridge 补救。

这样可以避免 declaration helper/cache/debug 字段被误纳入 schema，也避免用户以为某个 Java 字段参与 SOMA 存储但 processor 静默忽略。

## 5. Value

`@SomaValue` 是 SOMA compiler-defined immutable inline value，不拥有 table identity，也不是 runtime row boundary。其目标类似 Lombok `@Value` 的 semantic bundle，但 SOMA public shape 默认直接暴露 `public final` fields，而不是要求用户重复写 modifiers 或依赖 getter。

推荐 source shape：

```java
@SomaValue
public class MachineId {
    @SomaField
    long value;
}
```

Compile-time effective shape 等价于：class final、annotated field `public final`、canonical all-fields construction、canonical `equals()` / `hashCode()` 和 deterministic `toString()`。这些成员由 SOMA compiler/processor lowering 提供，不要求用户手写，也不依赖 runtime reflection。

规则：

- value 可以包含 scalar、semantic scalar、enum、string 或 nested value；
- value 内部每个 instance field 必须显式标注 `@SomaField`；`@SomaIgnore` 只服务允许 detached helper state 的 table carrier，不允许绕过 value immutability；
- `@SomaField` instance field 逻辑上 `public final`；显式 `public final` 可以作为冗余兼容写法，但 canonical example 不要求；
- value class 逻辑上 final，不允许 inheritance、non-final escape hatch、setter 或 mutable alias；
- value 内部不允许 `@SomaKey`、`@SomaChild`、`@SomaOptional`、`@SomaIndex` 或 `@SomaUnique`；
- value 内部不允许字段类型为 `@SomaTable`、`List`、`Map`、array 或其他 mutable container；
- value 作为 table field 时按 leaf expansion 展开为 columns；
- value 被 `@SomaKey` 使用时，其 leaf fields 共同构成 composite key；
- value leaf order 进入 normalized schema model 和 schema hash；
- value construction parameter order、equality/hash 和 `toString()` field order 基于 normalized leaf order；
- value 不支持 optional field；canonical constructor 对 String、enum、nested value reference 执行 non-null 检查，并以 Java field name 作为 `NullPointerException` message；
- user-defined `equals()` / `hashCode()` 不得改变 SOMA canonical value semantics；V1 processor 应拒绝冲突实现或以 generated effective shape 覆盖，具体 diagnostic 由 processor contract 固定。

`@SomaValue` 的 floating leaf 使用确定性的 Java wrapper bit semantics：所有 NaN 表示归一到同一 equality/hash，negative zero 与 positive zero 可区分。若该 leaf 通过 outer key 或 index/unique selector 进入 identity/access role，则 generated boundary 必须进一步要求 finite 并把 negative zero canonicalize 为 positive zero。

`@SomaValue` 不表达 cross-table object reference。跨表关系应使用 `MachineId`、`OperationKey` 这类 value/key 表达，然后由 generated table API 做 lookup。

## 6. Table

`@SomaTable` 是 runtime state 的主要边界。V1 table 只分为两类：keyed table 和 dense table。

### 6.1 Keyed table

Keyed table 有 stable logical key。

适用场景：

- entity state，例如 `Job`、`Operation`、`Machine`、`Material`；
- lookup table，例如 `SetupTime`、city pair distance lookup，或确实需要跨 parent 独立 pair identity 的 capability lookup；
- 需要唯一性约束、`containsKey(key)`、`fetch(key)`、`mutate(key)` 或 `delete(key)` 的 runtime data。

规则：

- keyed table 必须有且只有一个 logical key；
- key 可以是 scalar、semantic scalar、enum 或 value；
- 复合业务身份通过 value key 表达；
- primary key lookup 是 keyed table 的基础能力；runtime internal 由 hash primary locator 承载，不作为普通 secondary index 暴露给 schema/API；
- key equality 由 normalized key leaf path 和 storage type 决定。

### 6.2 Dense table

Dense table 没有 stable logical key。

适用场景：

- packed scan；
- row-index iteration；
- matrix / array-like runtime state；
- 批量替换或重建的数据平面；
- solver workspace，例如 VRP 中反复重建的 insertion candidate rows。

Dense table 可以是长生命周期 runtime state，也可以作为长生命周期 table 实例中的 scratch workspace。它不适合表达需要 stable identity、跨轮次 `fetch(key)` 或唯一性约束的数据。

规则：

- dense table 不声明 `@SomaKey`；
- dense row index 只是当前 packed storage 的位置，不是 stable business identity；
- dense table 的物理遍历顺序不稳定；需要业务顺序时由 Row Pipeline 显式 `sorted(...)`；
- dense table 可以使用 `replaceAll(batch)` 批量刷新，同时复用 capacity；
- dense table 单行 materialize 为 schema class，whole-table `materialize()` 返回 `List<R>`，但不暴露 stable key API。

### 6.3 Child table ownership and List/Map mapping

`@SomaChild List<R>` / `@SomaChild Map<K,R>` 是 ownership 关系，不是第三种 table kind。Schema/materialization 层固定映射：

```text
List<R>   <=> dense child SomaTable<R>
Map<K, R> <=> keyed child SomaTable<K, R>
```

规则：

- table 可以包含 `@SomaField`、`@SomaKey`、`@SomaChild`、`@SomaOptional` modifier、`@SomaIgnore`、`@SomaIndex` 和 `@SomaUnique`；
- `@SomaField` 标注的 value typed field 会 flatten；
- `@SomaChild List<R>` 要求 `R` 是没有 `@SomaKey` 的 `@SomaTable` class；
- `@SomaChild Map<K,R>` 要求 `R` 是有且只有一个 logical key 的 `@SomaTable` class，且 `K` 精确等于该 key 的 materialized Java type；primitive/semantic primitive key 使用对应 boxed type，enum、String 与 value key 保持 exact reference type；
- `@SomaChild` field 表示 parent row owns child table instance；
- child table instance 的 ownership 属于 enclosing parent SomaTable aggregate，并且只 attach 到一个 parent row/field slot；
- live child instance 不允许被多个 parent row 共享，也不允许 reparent；
- public/generated mutation boundary 不接受任意 live child facade 或 Java `List`/`Map` 作为 live attachment；只接受 detached child Batch/subtree construction data；
- child table 的 key/index/unique 只作用于该 child table instance；
- cross-table reference 不使用 table typed field，而使用 scalar、enum、semantic scalar 或 value key。

Schema table-ownership dependency graph 必须无环。Processor 必须拒绝直接或间接 ownership cycle，并报告完整 declaration path；同一个 child table type 可以被不同 parent declaration 复用，这不表示 runtime instance 可以共享。

V1 ownership edge必须位于同一个`@SomaSchema` package/aggregate内。跨schema relation使用scalar/enum/semantic/value key reference；V1不把两个独立schema hash/runtime plan隐式合并为一个ownership aggregate。

`@SomaChild` field 只建立 ownership edge。Runtime parent column 保存 internal `ChildTableHandle`，不保存 Java Collection，也不 flatten child columns；handle 不是 schema field value、业务 key 或可序列化 contract。

FJSP candidate-machine input 如果生命周期完全属于 operation、主要访问模式是按 operation 连续遍历，则推荐 `@SomaChild List<CandidateMachine>` dense child；如果需要独立 `(operation,machine)` identity、按 machine 反向查询、跨 operation 生命周期或独立 mutation，则推荐 root keyed lookup table。运行中的可调度候选仍应独立建模为 `MachineCandidate` frontier，不能与 immutable candidate-machine input 混成同一事实。

### 6.4 Default capacity

`@SomaTable` 可以声明 `defaultCapacity`：

```java
@SomaTable(name = "operations", defaultCapacity = 4096)
public final class Operation {
    ...
}
```

`defaultCapacity` 语义：

- 只允许出现在 `@SomaTable`；
- `-1` 表示未指定，由 generated/runtime plan baseline 决定 effective capacity；显式值必须大于 `0`，`0` 或小于 `-1` 非法；
- 表示 initial row capacity hint，不是 max row count；
- 不进入 logical `schema_hash`；
- 可以进入 generated runtime plan、allocation plan 或 `runtimePlanHash`；
- root table 创建时可作为默认初始容量；
- child table instance 第一次创建时可作为默认初始容量；
- child table 必须 lazy allocation，不得因为 parent table capacity 而 eager 创建所有 child storage。

`@SomaChild(initialCapacity = n)` 可以为某个 parent field slot family 覆盖 child type 的默认初始容量。它同样是 runtime-plan hint，不进入 logical schema hash；field override 优先于 child table type `defaultCapacity`。每个 parent row 都可能拥有独立 child instance，因此 child initial capacity 必须按单个 parent 的典型 child row count 设置，不能沿用 root-table 总行数规模。

### 6.5 Schema-backed materialization projection

每个 `@SomaTable` class 自身就是 detached single-row public shape，不再生成独立 `XxxRecord`：

- scalar/enum/string/semantic scalar field 映射回同一个 schema field；
- `@SomaValue` field 映射为对应 immutable value，不暴露 flattened column detail；
- required dense child field 映射为 non-null `List<R>`，logical empty child 映射为空 list；
- required keyed child field 映射为 non-null `Map<K,R>`，logical empty child 映射为空 map；
- optional child absent 映射为 `null`，present-empty 映射为 non-null empty `List`/`Map`；
- optional scalar/value absent 映射为 `null`；optional primitive schema source 必须使用 boxed type；
- ordinary scalar/value key reference 保持 key value，不自动展开 referenced table；
- `ChildTableHandle`、presence bitmap、packed Index、exact-index link 和 runtime stats 不进入 materialized shape；
- materialized schema object/collection 是 caller-owned detached copy，可以被调用方修改，但无 dirty tracking 或 automatic write-back；
- `@SomaTable` class 不生成 structural equality/hash；`List`/`Map` 使用 Java Collection contract，`@SomaValue` 使用 canonical value equality/hash。

Materialization shape 是 schema public contract。改变 field type/optional/ownership、List/Map kind、child projection 或 generated return type 是 breaking change；Materialized Object 不是 wire/persistence format，不提供 schema migration。

## 7. Key

`@SomaKey` 是 keyed table 的 stable logical identity。

规则：

- `@SomaKey` 只能出现在 `@SomaTable` 的 direct field 上；
- key field 必须 required；
- key 可以是 scalar、semantic scalar、enum 或 value；
- 复合业务身份通过 value key 表达；
- key leaf 不生成普通 mutator setter；
- 修改 identity 必须通过 delete + insert 表达；
- key equality 由 normalized key leaf path 和 storage type 决定；
- `@SomaKey` 不允许叠加 `@SomaDefault`；
- key path 上不允许出现带 `@SomaDefault` 的 value leaf。

`@SomaValue` 内部不允许使用 `@SomaKey`。value 是否成为 key，由外层 table field 上的 `@SomaKey` 决定。

## 8. Optional

`@SomaOptional` 表示 presence，不等于默认值。

规则：

- optional 是叠加在 `@SomaField` 或 `@SomaChild` 上的 marker；
- optional 可作用于 boxed scalar、semantic scalar wrapper、enum、string、value 和 child collection field；
- optional primitive schema field 使用 boxed type，例如 `Long`、`Integer`、`Double`、`Boolean`，absent materialize 为 `null`；
- runtime 仍使用 presence bitmap 加 primitive payload column 或 handle column，不因 boxed schema field 改变 hot layout；
- absent 不等于 Java primitive default；
- required `String`、enum 和 `@SomaValue` field 一律 non-null；Batch/import/Mutator 的 null 在 visible mutation 前返回 structured `invalid_null_value`；
- optional reference/value field 的 materialized carrier 或 `Batch.add(row)` 中 `null` 表示 absent；generated mutable cursor/mutator 的 `setXxx(null)` 非法，caller 必须使用 `clearXxx()`，从而不把 null 同时解释为 value 和 control operation；
- generated API 可以暴露 presence predicate / `OrThrow` / `OrDefault` convenience method，但用户不直接维护 bitmap；
- `@SomaOptional` 不允许叠加 `@SomaDefault`。

如果 `@SomaOptional` 作用于 `@SomaChild` field，absent 表示该 parent row/field slot 没有绑定 child instance，materialized field 为 `null`。Optional present-empty 与 absent 是不同 schema state：`child().clear()` 保持 present-empty，只有 `unsetChild()` 或等价 generated API 才进入 absent 并 cascade release 原 subtree。

普通 required child field 始终逻辑存在；尚未分配 storage 时是 logical empty child table，并由 runtime lazy allocation。Required child `clear()` 后仍然 present-empty，不能进入 absent。

## 9. Default

`@SomaDefault` 表示确定性 schema default。它不是 Java 字段初始化值。

示例：

```java
@SomaField
@SomaDefault("0")
public int priority;

@SomaField
@SomaDefault("READY")
public MachineState state;

@SomaField
@SomaDefault("")
public String name;
```

规则：

- `@SomaField` 可以叠加 `@SomaDefault`；
- `@SomaKey` 不允许 default；
- `@SomaOptional` 默认语义是 absent，不允许叠加 default；
- table typed child field 不允许叠加 default；
- Java 字段初始化值不进入 schema default；
- default 必须是确定性、可解析、可归一化的字符串字面量；
- append/new row 时，如果 required field 未显式赋值，则写入 schema default；
- fetch 时返回实际存储值，不重新计算 default；
- `@SomaDefault` 进入 normalized schema model 和 `schema_hash`。

Literal parsing 规则：

| Field type | Default literal |
|---|---|
| integer | 十进制字面量 |
| floating point | 十进制、`NaN`、`Infinity`、`-Infinity` 或 `-0.0`；是否允许由 normalized field role 决定 |
| boolean | `true` / `false` |
| enum | enum member name |
| String | annotation string value |
| date/time/date_time | SOMA 规定的 ISO text form |
| value | V1 不支持整体 default，只支持 leaf default |
| table | 不支持 default |

`@SomaValue` 内部 leaf field 可以声明 `@SomaDefault`。该 default 是 value 类型本身的语义默认值，会影响所有使用该 value 的 table field。只在某一张 table 中成立的默认值，不应放入共享 value 类型。任何被 `@SomaKey` 引用的 value path 都不得依赖 value leaf default。Generated RowBuilder仍以整个value field为assignment boundary：只有一个required value的全部递归leaf都声明default时，outer value未赋值才能由processor静态生成完整canonical value；只覆盖部分leaf时outer value仍是missing required field，不能从Java zero/null或临时partial object猜测其余leaf。Direct `addValues`、carrier `add` 和Mutator传入的value始终是显式值，不重新套用leaf default。

Floating default normalization：

- ordinary payload 可以使用上述 exceptional literal；canonical schema JSON 使用固定 token，不使用 locale/JDK-dependent formatter；
- strict identity/access leaf 拒绝 `NaN` 和 positive/negative infinity；
- strict leaf 的 `-0.0` default normalized result 为 `0.0`；
- invalid floating default 在 processor 阶段失败，不得延后到 runtime create。

## 10. Index / unique

`@SomaIndex` 与 `@SomaUnique` 是 table-level exact-access constraints。

规则：

- 只能放在 `@SomaTable` 类型上；
- Java 8 repeated annotation 必须提供容器 annotation，例如 `@SomaIndexes`、`@SomaUniques`；
- selector 使用 logical field path，例如 `operationKey.jobId.value`、`machineId.value`；
- selector 可以引用 value leaf path；
- selector 不可穿透 child table；
- selector 只能引用 required 且不含 string、table 或 optional leaf 的 field path；
- selector 引用 floating leaf 时，该 leaf 获得 strict access semantics：default/import/mutation/source argument 必须 finite，negative zero canonicalize 为 positive zero；
- index 是 secondary non-unique access；
- unique 是 secondary unique access；
- 同一 table 内 index、unique 名称不能冲突；
- index/unique 只表达完整 selector 的 exact equality，不表达 range、prefix range 或 maintained order；
- exact structure 必须在 mutation 成功返回时已经 current，不能把 dirty/full rebuild 推迟到下一次读取。

Grouped index source 使用完整 selector 表达。一个 generated 参数可以对应 scalar field，也可以对应一个 `@SomaValue` field 的全部 leaf；processor 将它展开为完整 canonical leaf equality，并返回同一套 Row Pipeline。

例如某个采用 flat pair lookup 的场景中，`OperationMachineCapability.findByOperation(operationKey)` 使用 `@SomaIndex` 的完整 selector，它正好对应 `operationMachineKey.operationKey` 的全部 leaf：

```java
@SomaIndex(name = "by_operation", fields = {
    "operationMachineKey.operationKey.jobId.value",
    "operationMachineKey.operationKey.operationId.value"
})
```

Grouped source 是 generated API convenience，不改变 schema kind，也不引入 query DSL。返回集合的组内顺序不作承诺；未排序 terminal 只遵循该次 source sequence。需要业务顺序时必须显式 `sorted(...)`，长期 priority/event queue 由应用层专用结构维护稳定 key。V1 支持 Java lambda 作为 row-level `filter` / `update` callback，但不引入 arbitrary join planner，也不承诺 lambda predicate 自动下推到 index。Selector diagnostics 必须指出出错 path、失败的 path segment、候选字段列表、是否因 optional/string/table leaf 被拒绝，以及对应 Java element location。

## 11. 建模与示例边界

Entity、lookup、frontier、workspace、matrix、input/working/result 是 application modeling role，不是新的 annotation 或 Table kind。Canonical modeling rules 和完整 Java 8 场景进入 [soma-examples](../../soma-examples/docs/README.md)，本契约不复制长篇业务示例。

## 12. 与相邻 Owner 的关系

- Processor 读取本文声明并产生 normalized schema、hash、diagnostics 和 generated artifacts；
- Generated public API 必须满足根级 API/materialization 契约；
- Runtime-core 不解析 annotation；
- Access Pattern Card、capacity/growth strategy 和 benchmark scale 不是 logical schema；
- `defaultCapacity` / `@SomaChild.initialCapacity` 是 declaration 中的 runtime-plan hint，不进入 logical schema hash。

## 13. 非目标

本文不定义 processor internal model、canonical JSON/hash 算法、Java source transformation、runtime layout、TableStore lifecycle、benchmark 结论或 application business validation。
