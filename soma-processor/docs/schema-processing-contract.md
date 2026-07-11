# Schema processing 契约

状态：正式设计文档
Owner：`soma-processor`
事实范围：compile-time collection、validation、declaration order、normalized schema、exact hash、compatibility identity 和 diagnostics
非事实范围：public annotation semantics、generated API behavior、runtime storage 和 benchmark
最后审查日期：2026-07-10

## 1. 目标

本文定义 processor 如何把 validated Java annotation source 转换为唯一、确定、可兼容检查的 normalized schema。Public declaration 语义以 [annotation schema 契约](../../soma-annotations/docs/annotation-schema-contract.md) 为准。

Processing failure 不得产生可被 runtime 使用的 schema-specific artifact。

## 2. Processing pipeline

```text
annotated Java source + @SomaSchema package metadata
  -> javac 8 parse-phase source transformation / effective type model
  -> declaration collection
  -> semantic validation
  -> normalized schema model
  -> canonical serialization and exact hash
  -> code-generation input
  -> structured diagnostics
```

约束：

- declaration collection 不依赖 runtime reflection；
- `@SomaValue` effective shape 在 normalization 前确定；
- processor 必须验证 compiler/lowering identity，未 lowering 或 unsupported compiler 时 fail closed；
- 所有 selector、logical name、ownership 和 type reference 在 codegen 前解析；
- validation 失败时跳过对应 schema codegen；
- code generation 只能读取 validated normalized model，不能重新解释 source element；
- filesystem path、timestamp、output directory 和 formatter 不进入 normalized facts。

## 3. Declaration order

V1 normalized schema model 必须稳定。

V1 baseline：

- Java enum member order 使用 source declaration order；
- table/value field order 使用 annotation processor 从 lowered javac element model 读取到的 source declaration order；
- field position 隐式来自 source declaration order；
- V1 不要求用户显式声明 `position`；
- index/unique/order declaration order 使用 annotation array order 或 repeated annotation 的 source order；
- processor golden tests 必须证明同一 source 在同一 Java 8 toolchain 下 canonical output 稳定；
- runtime 不得依赖 reflection order。

如果后续需要跨 compiler 的更强稳定性，可以引入 explicit `position`，但它不是 V1 默认要求。

## 4. Normalized schema model

Normalized schema model 至少包含：

- schema version；
- schema name from `@SomaSchema`；
- generated target：`java8-columnar`；
- generated package；
- enum declaration list and member order；
- value declaration list、implicit immutable effective shape and leaf expansion；
- table class list、schema-backed materialization shape 与 derived generated public names；
- table kind：`keyed` or `dense`；
- field / child / optional-modifier list；
- key declaration；
- schema default declaration；
- index / unique / order declaration；
- selector normalized path；
- resolved storage type；
- layout order；
- semantic scalar storage metadata；
- string storage policy；
- optional storage policy；
- `List`/`Map` child kind、row/key type and ownership metadata；
- generated public API names（进入 generated metadata/manifest，不进入 logical schema canonical JSON/hash）；
- canonical serialization input；
- schema hash。

Normalized schema model 不包含：

- local filesystem path；
- timestamp；
- random id；
- generated output directory；
- formatter details；
- runtime benchmark result；
- application/scenario Access Pattern Card；
- `defaultCapacity`；
- runtime allocation plan。

Runtime plan 可以包含：

- `schema_hash`；
- `defaultCapacity`；
- storage hint；
- capacity growth/trim、allocation/scratch strategy；
- SparseInt domain/fallback、HashKeySpace load/probing/rehash strategy；
- secondary index concrete structure and sidecar eager/lazy/hybrid policy；
- stats diagnostic mode；
- default `MaterializationBudget`；
- materialization allocation estimator version；
- runtime plan hash。

Materialization budget dimension 和 all-or-nothing failure semantics 是 runtime contract；default values/estimator version 是 runtime plan。两者都不进入 logical schema hash。Per-call budget override 是 operation input，不改变 table runtime plan hash。

Access Pattern Card 只为 scenario/runtime-plan tuning 和 benchmark scale 提供 rows、hot columns、access/mutation mix、selectivity、optional/child density、working set 与 allocation/export frequency。V1 不引入 `@SomaAccessPattern` 或 `@SomaPerformance` annotation；实际访问模式会随 application phase 和 workload 改变，不能被误写成永久 logical schema fact。

## 5. Canonical schema hash

V1 使用 exact schema hash 作为 compatibility boundary。

```text
schema_hash = lowercase_hex(SHA-256("soma-java:v1:schema\n" + canonical_normalized_schema_model))
```

Canonical form：

- UTF-8 JSON；
- object key 按 Unicode code point 升序排列；
- array 顺序保留 schema 语义顺序；
- string 使用 JSON 标准转义；
- integer 使用十进制文本；
- boolean 使用 `true` / `false`；
- 不输出 null 字段；
- 缺省语义必须归一化为显式字段。

V1 canonical declaration graph 的稳定顺序与引用规则：

- schema root object 同时记录 source `schemaPackage`；schema resource 使用 `META-INF/soma/<schemaPackage>.schema.json` 与 `.schema.sha256`，从而 schema logical name/version/generated package 的 incremental 修改覆盖同一 package-owned artifact；
- enum/value declaration array 按 fully-qualified Java type 排序；value field 和 enum member 保留 source declaration order；
- enum entry 必须记录 fully-qualified Java type、logical simple name 和完整 member order，不能只记录 enum FQN；
- nested value field 使用 `value:<fully-qualified-type>` 引用 canonical value declaration；processor 在输出前解析同 schema declaration graph、拒绝 unresolved reference/cycle，因此完整 leaf expansion 可由 ordered graph 唯一递归导出，不在 JSON 中重复一份可能漂移的 leaf list；
- 已被当前 implementation 接受的 declaration，其 canonical JSON/hash 即进入 non-regression golden；后续 Phase 只能添加此前被 fail-closed 拒绝的 V1 breadth，不能让相同 source/toolchain 的既有 canonical JSON/hash 因内部重构而变化；
- 尚未完整 normalization 的合法 V1 declaration 可以在 capability `in-progress` 时以稳定 diagnostic 拒绝，但不得生成缺失 enum member、value graph、role、selector 或其他 hash fact 的 partial resource。

首个 table-accepting slice 同时固定 table canonical representation；这不是临时 Phase 1 JSON：

- `tables` 按 table fully-qualified Java type 排序；table object key 按 canonical object-key 规则输出 `fields`、`javaType`、`kind`、`logicalName`、`materializedType`；
- `fields` 保留 source declaration order。Field object 固定包含 `javaName`、`leaves`、`logicalName`、`materializedType`、`optional`、`role`、`type`；后续 default/child/key 等已定义 V1 breadth只在该 object additive 增加正式 fact；
- primitive/simple field 的 `leaves` 含一个 object：`leafPath`、`semantic`、`storageType`；value field 后续按 normalized leaf order完整展开，不能只记录 outer value token；
- required primitive `type/materializedType/storageType` 使用 Java primitive token；optional boxed primitive 的 logical `type/storageType` 仍使用对应 primitive token，`materializedType` 使用完整 boxed Java FQN，并以 `optional:true` 表达 presence；
- Phase 1 dense table 使用 `kind:"dense"`、`role:"field"`，`materializedType` 是 source carrier FQN；不生成未实现 key/index/child 的空语义替代；
- child field使用`role:"child"`、`type:"child"`、empty `leaves`，并additive包含`child` object：固定`container`、`rowJavaType`、`tableLogicalName`，Map再含`keyMaterializedType`；top-level `materializedType`是完整List/Map type，`optional`保留三态presence；`@SomaChild.initialCapacity`只进入ChildPlan/runtimePlanHash，不进入logical JSON/hash；
- generated names 是从 source Java type、generated package和 processor naming protocol导出的 compatibility metadata，固定记录到 generated metadata/source golden与 generated API manifest，不进入 logical schema JSON/hash；naming protocol变化提升 processor/runtime pairing identity并触发 generated compatibility review，但不能无 logical schema change地改写 schema hash；
- effective table logical name 在同一 schema 唯一；source Java field name和 effective logical field name 在单 table 内各自唯一；
- `defaultCapacity`、growth、stats、budget、algorithm 和其他 runtime-plan hint 完全排除在 canonical table/schema JSON 之外；它们只进入 generated default plan 与 runtime plan hash；
- 相同 source/toolchain 的既有 value-only schema JSON保持 byte-identical；首次接受 table source 后，其 exact table JSON/hash进入 non-regression golden。

Generated code、runtime metadata、testkit 和 reports 必须引用同一个 schema hash。

## 6. Compatibility 与 breaking change

V1 中以下变化均视为 breaking change：

- 修改 `@SomaSchema.name`；
- 重命名 enum、value、table；
- 重命名或重排 enum member；
- 重命名 key、field、optional、index、unique、order；
- keyed table 与 dense table 之间切换；
- 修改字段类型；
- 修改 semantic scalar storage；
- 修改 key 类型、key leaf structure 或 equality 语义；
- 修改 selector、order direction 或 access name；
- 删除字段；
- 新增改变 layout 的字段；
- 修改 optional / required 语义；
- 修改 default literal 或 default 解析结果；
- 修改 value structure；
- 修改 string storage policy；
- 修改 child field ownership 或 `List`/`Map` kind；
- 修改 schema class materialization、optional absence 或 whole-table container shape；
- 修改 generated public API name or return type。

V1 默认不承诺 additive compatibility。新增字段也会改变 layout、materialization shape、generated API 和 schema hash。

改变 V1 全局 floating strict-access semantics、`@SomaValue` lowering/value equality、table materialization equality policy 或 MaterializationBudget protocol 不一定改变某张 schema 的 normalized model，但必须改变 processor/runtime compatibility identity，并同步 generated API/runtime gate。

以下变化不属于 logical schema breaking change，但可能改变 runtime behavior 或性能，应进入 runtime plan evidence：

- 修改 `defaultCapacity`；
- 修改 storage hint；
- 修改 allocation strategy；
- 修改 default MaterializationBudget 或 estimator version；
- 修改 benchmark-only metadata。

## 7. Diagnostics

Processor diagnostics 至少区分：

- missing or duplicate `@SomaSchema`；
- invalid schema name；
- invalid generated package；
- duplicate schema name；
- unresolved type；
- invalid field type；
- member/local/anonymous、non-public、abstract 或不可实例化的 `@SomaTable` carrier；
- `@SomaTable` 缺少 public no-arg constructor，或该 constructor 声明 checked exception；
- non-public、final 或其他不可由 generated materializer 赋值的 table schema field；
- unannotated instance field；
- mutually exclusive primary field annotations；
- optional marker without `@SomaField` / `@SomaChild`；
- optional primitive declared with Java primitive instead of boxed type；
- multiple key declarations；
- optional key；
- key used inside value；
- default used on key；
- default used on key value leaf path；
- default used on optional；
- default used on child field；
- invalid default literal；
- non-finite default on floating identity/access leaf；
- invalid defaultCapacity；
- invalid index selector；
- invalid unique selector；
- invalid order selector；
- invalid order direction；
- selector path through child table；
- direct or indirect table ownership cycle；
- invalid child ownership declaration；
- raw/wildcard/nested collection child；
- `List` child row declares key；
- `Map` child row missing/multiple key；
- `Map<K,R>` key type mismatch；
- cross-schema child ownership；
- mutable/invalid `@SomaValue` effective shape or conflicting value equality/hash；
- selector path uses Java field name after logical name override；
- duplicate generated access name；
- value contains table/List/Map/mutable field；
- value contains key/index/unique/order declaration；
- unsupported Java language feature；
- optional primitive declared without boxed schema/materialization type；
- unsupported unsigned type expectation；
- schema hash generation failure。

Diagnostics golden comparison 以 diagnostic code、severity、element location、related symbol 和是否阻止 codegen 为稳定字段。message 文本允许优化，但不能改变机器可读 code 语义。

首个 compiler/build slice 固化以下 code family；后续 breadth 在对应 family additive 分配，不复用已有 code 表达不同失败：

| Code | Stable category |
|---|---|
| `SOMA-COMP-001` | processor 未看到 required javac plugin activation |
| `SOMA-COMP-002` | compiler/JDK/processing environment unsupported |
| `SOMA-COMP-003` | compiler lowering identity mismatch |
| `SOMA-COMP-004` | processor element model 缺少 canonical lowered shape |
| `SOMA-COMP-005` | plugin 未看到 required processor activation |
| `SOMA-COMP-006` | parse-phase annotation identity 未使用无歧义 FQN/single-type import |
| `SOMA-VALUE-001` | value declaration/effective class or field shape invalid |
| `SOMA-VALUE-002` | source member 与 canonical generated member 冲突 |
| `SOMA-VALUE-003` | value field role、static annotation 或 hidden ignored state invalid |
| `SOMA-VALUE-004` | value logical field name invalid/duplicate |
| `SOMA-VALUE-005` | value field type unsupported/unresolved |
| `SOMA-VALUE-006` | semantic scalar 与 storage type 不兼容 |
| `SOMA-VALUE-007` | nested value declaration cycle |
| `SOMA-VALUE-008` | nested value reference 不属于同一 schema compilation graph |
| `SOMA-SCHEMA-001` | package schema declaration missing |
| `SOMA-SCHEMA-002` | schema logical name invalid |
| `SOMA-SCHEMA-003` | generated package invalid |
| `SOMA-SCHEMA-004` | schema version label invalid |
| `SOMA-SCHEMA-005` | schema logical name duplicate |
| `SOMA-OUTPUT-001` | deterministic compiler-managed artifact emission failed |

Table/codegen family additive 分配：

| Code | Stable category |
|---|---|
| `SOMA-TABLE-001` | table declaration/carrier shape invalid |
| `SOMA-TABLE-002` | table logical name invalid/duplicate |
| `SOMA-TABLE-003` | field membership/role/modifier invalid |
| `SOMA-TABLE-004` | field logical name invalid/duplicate |
| `SOMA-TABLE-005` | table field type/optional materialized type unsupported |
| `SOMA-TABLE-006` | public no-arg construction or public mutable field shape invalid |
| `SOMA-TABLE-007` | default capacity/runtime-plan hint invalid |
| `SOMA-TABLE-008` | table key cardinality/type/requiredness invalid |
| `SOMA-TABLE-009` | index/unique/order name、selector leaf、direction 或 access binding invalid |
| `SOMA-TABLE-010` | child container/row/key/modifier/ownership declaration invalid |
| `SOMA-TABLE-011` | direct/indirect child ownership cycle |
| `SOMA-GEN-001` | generated public name/signature collision |
| `SOMA-GEN-002` | deterministic generated source emission failed |

## 8. 与 code generation 的边界

本文输出 validated normalized schema 与 compatibility metadata。[Code generation 契约](code-generation-contract.md) 只消费该输出并生成 artifacts；它不能改变 logical name、field order、selector、default、ownership 或 hash。

Generated API 的用户语义由根级 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 和 [Materialization 契约](../../docs/materialization-contract.md) 拥有。

## 9. Evidence

至少需要：

- valid/invalid compile fixtures；
- normalized schema golden；
- exact schema hash golden；
- declaration-order stability；
- structured diagnostic golden；
- processor/runtime compatibility mismatch cases；
- compiler/lowering identity mismatch cases；
- canonical javac 8 toolchain repeatability；
- unsupported JDK/compiler negative fixture。

具体 helper 由 [soma-testkit 契约](../../soma-testkit/docs/testkit-contract.md) 拥有。

## 10. 非目标

本文不规定 compiler lowering mechanics、generated Java class/method body、runtime column/keyspace algorithm、runtime-plan concrete strategy、automatic schema migration 或 additive compatibility。Compiler mechanics 由 [Compiler integration 契约](compiler-integration-contract.md) 拥有。
