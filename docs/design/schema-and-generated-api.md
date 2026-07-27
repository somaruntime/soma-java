# Schema 与生成 API 设计

类型：Design

状态：正式

Owner：SOMA schema 与 generated contract

设计层次：`D2` 能力设计

主要关注点：Schema 语言、编译期契约与 schema-specific generated API

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：annotation/schema 语义、compiler/codegen 技术路线、normalization/hash、编译期诊断、generated facade/DataFlow binding 语义和公开 IndexSnapshot 消费契约

非事实范围：runtime 存储算法、具体 generator 类结构和 measured performance

最后审查日期：2026-07-28

本 Owner 先定义 schema 与 generated public capability 的长期语义，再约束实现这些语义所必需的 compiler/codegen 机制。当前 generator 类、精确 signature 和 emission 结构不属于本 Design，由 Implementation Map 与 executable surface 记录。

## 1. Schema vocabulary

- `@SomaSchema` 定义一个生成单元和 namespace；
- `@SomaValue` 定义 compiler-owned immutable value type，具有稳定 value equality/hash 和 leaf flattening；
- `@SomaTable` 定义 row schema 及 detached single-row materialization shape；
- `@SomaField`、`@SomaIgnore`、`@SomaSemantic` 描述受控字段语义；
- `@SomaOptional` 与 `@SomaDefault` 明确 absence/default，禁止用任意 sentinel 暗示缺失；
- `@SomaChild` 定义 parent-owned `List<R>` 或 `Map<K,R>` child；
- `@SomaKey` 定义 primary identity；`@SomaUnique`、`@SomaIndex` 定义 secondary exact access。

Schema 不提供 range index、maintained order 或 application priority queue。排序策略由调用点显式给出。

Schema source 必须显式表达 SOMA field role。普通 Java getter/setter、field initializer、bean naming、reflection 可见字段或任意第三方 annotation 都不能自动成为 schema fact。

### 1.1 Declaration 与 carrier

- 一个 schema package 通过 `@SomaSchema` 形成唯一生成单元、logical identity、generated package 和 user version label；
- `@SomaTable` class 是 public、可构造的 detached row carrier，不是 live row；用户修改 materialized carrier 不会回写 table；
- `@SomaValue` 是 compiler-defined immutable inline value，支持 nested value，并以 canonical leaf order 定义 construction/equality/hash；
- table field 必须明确选择 value、key、child 或 ignore role；optional/default 是正交 modifier，不允许隐式 sentinel；
- keyed/dense 由是否存在一个 primary key 决定；root/child 与 input/working/result 不改变 table kind；
- `List<R>` child 表示 parent-owned dense table，`Map<K,R>` child 表示 parent-owned keyed table，live storage 不保存 Java Collection graph。

### 1.2 Type 与 value-state

V1 schema/storage kind 封闭为四类：

| kind | schema semantics | physical representation |
|---|---|---|
| primitive-backed scalar | primitive、enum、date/time、显式 semantic scalar | primitive column；enum 使用 ordinal |
| reference-backed immutable scalar | 白名单仅 `java.lang.String` | typed reference column，保存 caller reference |
| compiler-flattened value | `@SomaValue` immutable value semantics | canonical primitive/String leaf columns |
| owned structured state | parent-owned child `List`/`Map` declaration | child Table handle + ownership registry |

Array、raw/wildcard/nested collection、arbitrary object/DTO graph、table reference和
runtime reflection type不属于 schema storage model。确需关联 application object
时，schema 保存 primitive/String/flattened stable ID，对象由 application
sidecar/registry 管理。

Required、optional absent、schema default、zero、empty string、invalid value、missing key 和 empty result 是不同状态。Ordinary floating payload 可以保存 Java IEEE-754 special value；参与 key/index/unique 的 leaf 必须 finite，并把 negative zero canonicalize 为 positive zero，使 write、lookup、hash 与 full equality 使用同一 identity。

String V1 不复制、不 intern、不 normalize，不引入 dictionary 或 character arena。
required String 非 null；optional 用 presence 表示 absence，present payload 非 null，
空字符串是普通 value。payload、Key、Unique、Exact Index、Group/Join、filter、
order、mutation 与 detached result 使用 Java String value semantics；hash/fingerprint
只缩小候选，最终 equality 回查 authoritative column。equal-value、
different-object mutation 是 logical no-op，保留原 reference且不改变 epoch/access；
append 可以保存 caller 的 equal-value different reference。remove/clear/replace/
rollback/release 必须清理 dead reference。

Annotation element、target/retention、grammar、default constant 和全部当前 Java signature 属于 executable public surface，由[可执行契约地图](../implementation-map/executable-contract-map.md)定位。Design 规定其语义和演进边界，不复制一份容易漂移的签名表。

## 2. 编译期规则

Full JDK 8 javac 是 V1 compiler authority。`@SomaValue` 需要 javac integration 提供 immutable/public-final-field/value semantics，JSR 269 processor 负责发现、验证、规范化、hash 和生成；两者必须以版本化 identity 协同，不能只靠新 JDK 的 `--release 8` 模拟。

Compiler plugin 缺失、unsupported compiler、plugin/processor identity mismatch、late-round 新 declaration 或 lowering 冲突必须 fail closed；不能生成“看似可编译、运行时再解释”的降级产物。Processor 只在完整 collection、validation、symbol/admission plan 和 source generation 成功后发布 product artifact，避免 partial generated output 成为可消费状态。

编译期必须拒绝：

- 不受支持的 field/type/modifier/annotation 组合；
- key/unique/index 的非法目标、重复名称或冲突定义；
- child ownership shape、optional/default 或 value nesting 的歧义；
- 生成名称冲突、保留标识符冲突和无法稳定排序的 schema；
- 超过明确 codegen 资源上限的 schema；
- generated/compiler/runtime protocol identity 不匹配。

诊断必须稳定、可定位、bounded，不能依赖普通 stdout 日志。

## 3. Normalization 与 schema hash

同一语义 schema 必须得到确定的 normalized model、生成顺序和 schema hash。Normalization 使用明确的 Unicode code-point order，不依赖文件系统、locale、reflection iteration 或 JVM hash iteration。

Schema hash 表达 schema contract identity；runtime plan hash 表达运行时策略 identity，两者不得混用。对 hash 输入或生成 manifest 的非兼容变化必须经过 compatibility review。

Normalized model 至少保留 schema/table/value identity、logical field/leaf path、type/value-state、key/child/selector role、default、generated naming input 和 compatibility identity。具体序列化字段是当前 processor artifact surface；改变语义、排序或 hash 输入不能只更新 golden 来迁就实现。

### 3.1 Descriptor Metadata 与生成入口

每个 `generatedPackage` 生成唯一 schema-scoped `SchemaMetadata` companion，投影：

- immutable `SomaMetadata` root 与 `SomaSchemaMetadata`；
- `SomaTableMetadata`、`SomaColumnMetadata`、`SomaKeyMetadata`、
  `SomaUniqueMetadata`、`SomaIndexMetadata`、`SomaOwnershipMetadata` 和
  `SomaTypeMetadata`；
- schema default `RuntimePlan` 与 schema-seeded mutable-before-freeze builder。

Descriptor 类型只允许 processor-populated immutable construction，不开放 arbitrary
application 构造、global registry、reflection 或 resource scanning。每个 generated
Table 提供同一 schema Metadata、自己的 Table Metadata 和 default plan convenience；
raw handwritten plan builder 不作为 canonical application entry。Descriptor field/
type/optional/default/selector/ownership/schema hash 永不可在运行期修改。

## 4. Generated facade

生成 API 至少表达：

- schema-specific table create 与 runtime plan binding；
- generated SchemaMetadata、Table Metadata 与 mutable-before-freeze plan entry；
- typed batch/import；
- keyed fetch/contains/mutate/delete，或 dense packed access；
- Packed source、exact selector source、filter/skip/limit/dynamic sort 和 Candidate terminal；
- typed mutation terminal、result/stats；
- typed ColumnTraversal、primitive ColumnView 和 direct column access；
- keyed KeyTraversal 与 secondary-unique point family；
- parent-owned child facade；
- 每张 Table 一个 schema-specific DataFlow companion，提供 typed Source、column/value expression、point/exact/owned-child binding；
- keyed table 的 typed detached Delta 与 safe-point `applyDelta`；
- detached row/aggregate materialization与预算；
- default Eager result 与复用标准 DataFlow lifecycle 的 generated typed callback
  delivery facade；
- lifecycle、structured errors 和 compatibility identity。

生成 public signature 不暴露 runtime bucket、RowSlot、raw owner token、primitive backing array 或 internal protocol type。

### 4.1 Operation 语义

| 形态 | 长期语义 |
|---|---|
| create/default plan | 在 aggregate 发布前验证 schema/generated/runtime/plan identity |
| Batch/import/replace | detached typed staging；调用后不持有 caller collection/object graph；all-or-nothing publish |
| keyed direct access | key 是 stable identity；missing 与 conflict 使用明确 result/typed failure |
| dense access | current Index 只在当前 table state 有效，不是 stable identity |
| exact source | 从 eager maintained group 产生当前候选，不做 read-time rebuild/scan fallback |
| Candidate Scan | lazy intermediate、one-shot、同步、非重入；stage 只消费前一 candidate set |
| DataFlow companion | 生成 typed Source/Binding/Expression access；不保存 Template/Invocation，不生成 per-operator executor |
| Definition/Template/Invocation | logical/reusable、compiled/reusable、bound/one-shot 三种 lifecycle 不能合并 |
| keyed Delta/apply | detached ordered Insert/Update/Delete；整批 preflight 后在 single-aggregate safe point 原子 publish |
| update/remove | 只作用于当前候选，维护 columns、locator、exact access、ownership 和 epoch 原子一致 |
| ColumnTraversal/View | typed leaf access；Traversal one-shot，live View 有明确 close、pin、epoch 和 stale 语义 |
| materializing terminal | 返回 detached schema object/List/Map，并遵守 aggregate budget |
| IndexSnapshot terminal | 复制 current Index 序列，只供紧接着的同步只读批次 |

精确方法名、overload 和参数顺序由 generated `javap` golden 与 external consumer 拥有当前事实。Blueprint 中的代码只表达目标体验。

完整 Access family、组合合法性、sequence 与 terminal 语义由 [Access Model 与 Candidate Scan](access-model-and-candidate-scan.md)拥有；本 Owner 负责把它们投影为 schema-specific generated contract。

Logical Shape、Operator、Result/Effect 语义由 [Transformation Model](transformation-model.md)拥有；Definition/Template/Invocation、binding 和 protocol 由 [DataFlow 执行模型](dataflow-execution-model.md)拥有。本 Owner 只拥有 schema-specific generated projection，不复制 analyzer 或 executor。

## 5. Handle 与 callback 语义

- materializing terminal 返回 detached `@SomaTable` object；它不是 live view；
- Candidate callback 收到 callback-scoped Cursor/UpdateCursor，不能逃逸、缓存或跨 stage 使用；
- callback-scoped Result Delivery 的 Cursor/guard 同样不得逃逸；String getter 返回的
  immutable String value 可以保留，不因此延长 Cursor/source guard；
- Candidate Scan、KeyTraversal、ColumnTraversal、mutation builder、Definition Builder 和 Invocation 是 one-shot；消费后再次调用必须产生 typed lifecycle error；
- `IndexSnapshot` 是显式复制的 public Index result，只复制数值序列并记录 source table / structural epoch；它不是 stable identity 或 row snapshot；
- caller只在一个同步只读Index消费批次中立即使用，来源Table任意mutation/lifecycle变化后视为失效；`requireCurrent`只作为可选边界防御，不进入强制hot path；
- internal candidate scratch 统一称为 `IndexBuffer`，不进入 application data model；
- callback failure 必须遵守 mutation atomicity，不允许 partially committed row set。
- callback consumer 是 Invocation parameter，不得被 Definition/Template retain 或
  写入其 identity。

## 6. API 演进

Handwritten public、generated public、generated-runtime protocol 与 transformation/kernel protocol 是不同兼容面。任何 public/generated 名称或语义变化必须更新 manifest/golden/external-consumer evidence；internal implementation 可以在不改变这些 surface 的前提下演进。

Generated output 必须 deterministic、byte-stable 于其声明的工具链/identity，不包含 timestamp、local path、random id 或 iteration-order 偶然性。Generated name collision、source size/parameter slot/resource admission 和 emission failure在 product artifact 发布前处理；不能通过临时 public carrier、字段截断、反射或 metadata interpreter 绕过。
