# Schema 与编译生成 Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 schema composition、annotation、generated identity/object、编译边界与 schema build contract

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 设计目标

Compiler layer 把声明式 `.schema` source 转换为父 package 中的自然 Java object 和
typed SOMA API。它必须在编译期关闭非法类型、命名和 capability，不修改 existing
application type，也不把 schema declaration 或 runtime reflection 泄漏给 consumer。

本 Design 主要承接 BP-1、BP-2、BP-3 和 BP-10。

## 2. Composition declaration

一个 composition 由专用 `.schema` package 中的无参数 package-level
`@SomaSchema` 声明：

```java
@SomaSchema
package com.example.scheduler.soma.schema;

import io.github.somaruntime.soma.annotation.SomaSchema;
```

Composition contract：

- schema package 必须以 `.schema` 结尾；
- generated namespace 是去掉最后一级 `.schema` 后的父 package；
- 映射结果不能是 default package；
- generated namespace 本身不能再以 `.schema` 结尾，也不能等于同一 compilation 中
  另一 composition 的 schema package；例如 `com.example.schema.schema` 非法，不能把
  generated application API 写回 `com.example.schema` declaration namespace；
- processor 只收集 exact schema package 直接包含的 package-private 顶层
  `@SomaTable`；
- 不扫描 subpackage 或 dependency classpath；
- 同 package 中所有 `@SomaTable` 自动进入 composition，不维护 `rootTables` 清单；
- composition 必须至少包含一张 Table；
- nested/local `@SomaTable`、没有 `@SomaSchema` 的 Table、跨 composition Table 或
  多个 composition 映射到同一 generated package 都编译失败；
- source、filesystem 或 annotation-processing encounter order 没有语义；Table 按
  canonical type identity 规范化。

Composition 内没有 root/child Table category。所有 Table 平等进入 generated
`SomaGroup`，Table 间关系由普通 endpoint Field/Index 表达。

## 3. Annotation inventory 与 retention

V1 只有以下 public schema annotations：

```text
@SomaSchema
@SomaTable
@SomaValue
@SomaField
@SomaKey
@SomaIndex
```

全部显式使用 `RetentionPolicy.CLASS`。它们是 compiler/build metadata，不是
runtime reflection API：

- compiler 和受支持 build integration 可以跨 classfile boundary 识别；
- runtime 不通过 `Class.getAnnotation` 或 metadata interpreter 重建 schema；
- `CLASS` retention 不扩大 Table discovery；
- processor 可以沿已声明 Field type relation 解析 `@SomaValue`，不得全局扫描
  package/classpath。

V1 删除 `@SomaOptional`、`@SomaDefault`、`@SomaIgnore`、`@SomaChild`、
`@SomaUnique` 和 `SomaSemantic`。不得生成兼容 alias 或 legacy diagnostic category。

## 4. Compiler-only declaration shape

Schema declaration 是 package-private 顶层 type，只表达 Field order、role、type 和
Table configuration：

- application 不实例化、持有、导入或在 public signature 中使用 declaration；
- `.schema` package 不放普通 application code；
- declaration 必须是 package-private `final class`，不能是 interface/enum/annotation、
  abstract、nested/local、generic、继承非 `Object` superclass 或实现 interface；
- 不允许显式 constructor、method、initializer block、nested type、static Field 或未标注
  instance Field；
- schema Field 必须是 package-private、non-static、non-final instance Field；Field source
  declaration order 是 generated constructor/Field tree 的 canonical order；
- Table 与 Value 都至少有一个 storage Field；
- `@SomaValue` Field 只能使用 `@SomaField`，Value dependency 必须 acyclic；
- `@SomaValue` 只从 composition Table Field 递归发现；未被引用的 Value 不因此成为
  public generated type；
- reachable `@SomaValue` 必须是同一 exact schema package、同一 full source set 中的
  package-private top-level declaration；不复用 dependency classpath 或另一 composition
  的 Value declaration；
- 任意 Field 不能直接、array component 或 generic type argument 引用 `@SomaTable`
  declaration；`@SomaValue` declaration 只能作为 direct logical Field/nested Value，不能
  包在 array/Collection/generic argument 中伪装 opaque payload；
- ordinary reference Field 的完整 declared type（enclosing/component/type argument）必须
  能合法出现在 generated parent package 的 public signature；inaccessible/local/anonymous
  type、wildcard bound leak 或 unresolved/error type 编译失败；
- ordinary javac 仍可能生成 declaration `.class`；“compiler-only”表示 application
  与 runtime contract 不依赖它，不能虚构 JSR 269 阻止 classfile 生成。

Standard javac 仍会生成 declaration `.class`。V1 packaging 明确允许它出现在 consumer
artifact；它没有 public accessibility，runtime 也不得依赖或反射它。V1 不增加专用
post-compile deletion/plugin 只为隐藏该 classfile。

## 5. Identity 与 naming

固定映射：

```text
com.example.scheduler.soma.schema.MachineId
    -> com.example.scheduler.soma.MachineId

com.example.scheduler.soma.schema.TransportTime
    -> com.example.scheduler.soma.TransportTime
    -> com.example.scheduler.soma.TransportTimeTable
    -> SomaGroup.transportTimeTable()
    -> Soma.transportTimeTable()
```

V1 `@SomaTable` 不接受 `name`，`@SomaSchema` 不接受 `outputPackage`。Canonical schema
declaration type 是唯一 identity source；移动、重命名或改变 type 都是 schema 和
generated API 变化。

`@SomaTable` 只接受 storage/configuration 参数；V1 当前唯一参数是
`defaultCapacity`。Exact declaration 为 `int defaultCapacity() default 16`；合法范围是
`0..Integer.MAX_VALUE`。它是初始 capacity hint，不是 identity、maximum、segment size
或 allocation promise。Negative value 必须在 compiler validation 中失败，合法 value
仍可能在 runtime resource boundary 被拒绝。

Processor 在父 package 为 composition 生成唯一 public `Soma`、`SomaGroup`、
detached application object 与 typed Table/Field/Stream API。所有 generated public
signature 必须把 schema type 映射成父 package generated type，不泄漏 `.schema`
FQCN。

Generated application object 不重新携带 `@SomaValue`/`@SomaTable` 并参与 composition
discovery，否则会形成第二份 schema input。若需要 generation provenance，只能使用不
参与产品 schema 的 standard/internal marker。

Generated name、reserved system namespace、Field/nested path、Index accessor 或
用户已有 type/member 发生 collision 时必须稳定编译失败，不能增加后缀或静默改名。
以 `_` 开头的 generated namespace 保留给 SOMA system surface；V1 当前只有
`_metadata()`。

Schema type/Field identifier 不得以 `_` 开头。Reserved member 由
[Exact Signature Design](generated-api-signatures.md)机械推导：若 schema name 会在同一
generated owner scope 与 system Field/method/nested type 或 inherited `Object` member
重名，即使 Java 语法允许 field/method 同名，也按 semantic collision 失败。Processor
不维护另一份可能漂移的手写 reserved-word list。

完整 diagnostic code/message catalog 与 binary compatibility policy 是 production
compiler surface admission 的 Gate；实现不能因此改变 fail-closed collision 语义。

## 6. Generated Value object

对于：

```java
@SomaValue
final class MachineId {
    @SomaField long value;
}
```

父 package 生成同名 immutable application value。它必须具有：

- `public final` type；
- private final Fields；
- 按 schema source order 的 canonical 全字段 constructor；
- 同名只读 accessor；
- stable structural `equals/hashCode` 与 human-readable `toString`；
- 无默认 constructor；
- nested Value 使用父 package 对应 generated type。

Processor 新建 type，不修改 annotated declaration，不依赖 Lombok、javac internal
AST、reflection、`Unsafe` 或 post-compile bytecode patch。

Generated Value 是 detached immutable object，不是 live row 或 storage Owner。它不
通过 setter/object mutation 复用；需要减少 allocation 时，应避免 materialization
或使用 callback-scoped Value View，而不是削弱 Value invariant。

## 7. Generated Table object

对于 schema `TransportTime`，processor 生成两个不同角色：

```text
schema.TransportTime       compiler-only declaration
TransportTime              mutable detached application object
TransportTimeTable         authoritative live Table facade
```

Generated detached `TransportTime` 必须具有：

- private instance Fields；
- 按 schema source order 的 public 全字段 constructor；
- public 默认无参 constructor；
- 每个 Field 的同名 getter 与 `void` setter；
- 不生成 public mutable Field；
- 不同时生成 JavaBean `getXxx/setXxx`；
- 不生成 structural `equals/hashCode`。

```java
MachinePairKey pair = detached.machinePair();
detached.machinePair(updatedPair);

long minutes = detached.transportMinutes();
detached.transportMinutes(24L);
```

无参 constructor 使用 Java zero initialization。对象只有在 `add` 或 point `update`
boundary 通过完整 schema validation 后才能发布。Key 在 detached object 上仍可写；
immutability 只约束已经发布的 live Record。

Generated Table object 只承担 add/update input 与 point/stream/fetch materialization；
Table 不保存 carrier object。Primitive/Value leaf 按值复制，String/Enum/ordinary
Object 复制 reference slot。

## 8. Field role declaration

每个 direct Table storage Field 必须且只能声明一种角色：

| Annotation | 数量 | Schema 语义 |
|---|---:|---|
| `@SomaKey` | `0..1` | optional unique business identity 与 point path |
| `@SomaIndex` | `0..N` | non-unique exact-match access path |
| `@SomaField` | `0..N` | 无 access structure 的 payload |

`@SomaKey`/`@SomaIndex` 已隐含 storage Field，不能再叠加 `@SomaField` 或彼此重复。
未标注的非-static instance Field 不会隐式进入 schema，必须产生编译 diagnostic。

一个 composite Key 使用一个完整 `@SomaValue` Field；不能把多个 sibling Field 分别
标为 Key。`@SomaIndex` 是无参数 direct-Field marker，生成
`by<FieldName>(value)`，例如 `machineId -> byMachineId(machineId)`。

Nested-subfield、cross-Field tuple、Value 内部 Index、prefix、range 和 secondary
unique 不进入 V1。超出范围的查询通过 scan/filter 或 application-owned OOP structure
表达。

合法 Field type、null、Key/Index equality 和 storage lowering 由
[数据模型与存储 Design](data-model-and-storage.md)拥有；generated endpoint 与
capability 由[逻辑层 API Design](logical-api.md)拥有。

## 9. Generated construction boundary

`SomaGroup` 与 `XxxTable` constructor 不是 public API。有效 instance 只能由 generated
`Soma` factory/navigation 创建：

```java
SomaGroup group = Soma.createGroup();
TransportTimeTable table = group.transportTimeTable();
```

Java 8 top-level package-private 并不能阻止同 package application 调用 signature，
因此 Production generator 使用 package-private、identity-token-gated constructor：
same-package source 可能编译到 constructor，但不能伪造 generated `Soma` 私有持有的
有效 token；null/arbitrary token 必须在分配 Table storage 前以 `INVALID_ARGUMENT`
失败。P2 已证明该 mechanism 可行；exact internal carrier 可以优化，但不能让普通
application 得到有效 instance，也不能公开无 guard constructor：

```java
new SomaGroup();
new TransportTimeTable();
TransportTimeTable.create();
```

这条边界不适用于 detached Table object；`new TransportTime()` 和全字段 constructor
明确是 public application surface。

## 10. Full-regeneration build support contract

Composition correctness 的唯一语义基准是完整 `.schema` source set。Schema
add/remove/rename/move/change 时，受支持 build integration 必须：

1. 识别受影响 composition；
2. 替换或清理该 composition 专属 generated-source/generated-class output；
3. 向 processor 提交完整 `.schema` source set；
4. 显式声明当前 invocation 满足 full-source contract；
5. 生成结果与 clean full compilation 等价且不存在 stale type/member。

责任边界：

| Owner | 责任 |
|---|---|
| Build integration | source-set completeness、schema-change detection、generated output ownership 与 stale cleanup |
| Processor | 验证 host/mode handshake、验证当前 compilation 可见事实、canonical aggregation 与 late-round fail closed |

Handshake 是 build Owner assertion，不是 source-set oracle。Standard JSR 269 processor
不能从 partial invocation 推断未提交 source。缺少 handshake 时 composition generation
必须在发布任何 generated composition source 前失败；错误配置的 host 对 partial
source 伪报完整性属于 build contract violation。

V1 支持边界：

- Maven 与 IDE 最初只承诺 composition-scoped full regeneration；
- raw partial `javac` 不受支持；
- ordinary application-only change 不因此禁止增量编译；
- Gradle incremental 只有通过真实 add/change/delete、stale cleanup 和 clean-full
  equivalence Gate 后，才能成为额外受支持路径；
- `aggregating` descriptor 只是协议注册，不是通过证据；
- processor 不得 best-effort 扫描 filesystem/classpath 猜测 partial source set。

正式 handshake 使用 processor option：

```text
-Asoma.fullSourceSet=true
```

Processor 同时生成 build-only composition manifest 以支持 stale/equivalence evidence；
option 仍只是 build Owner assertion，不是 source-set oracle。P2 的
`-Asoma.p2.fullSourceSet=true` 只是一项 historical witness，不能进入 production。
精确 Maven/IDE 支持矩阵由
[Production Implementation Architecture](implementation-architecture.md)拥有。

## 11. Annotation-processing round boundary

Processor 在一个 compilation 内必须按 aggregating processor 工作，并在 processing
round 结束前形成完整 exact-package composition。如果后续 round 新增相关 Table 而
会使已生成 composition 不完整，必须 stable diagnostic fail closed；不能先发布一份
partial `Soma/SomaGroup` 再静默接受过期 surface。

Generated source 必须通过标准 `Filer` 创建，不覆盖 existing application source，
不依赖 compiler-specific internal API。Input order 不得影响 generated output。

## 12. Compiler failure boundary

以下至少在编译期失败：

- invalid composition/package mapping；
- nested `.schema.schema` 或 generated namespace/schema namespace collision；
- missing/duplicate Field role；
- invalid `@SomaValue` leaf；
- invalid Key/Index type or nullability contract；
- duplicate Key declaration；
- schema/public generated signature leak；
- generated type/member/path/index accessor collision；
- unsupported declaration shape；
- late-round composition incompleteness；
- missing full-source handshake。

Compiler failure 不发布可被 consumer 误用的 partial composition。Diagnostic 必须以
logical schema identity 表达，不暴露本机 path、内部 stack 或 compiler object。

### 12.1 Stable diagnostic catalog

每条 processor error 以 `[SOMA-xxxx]` code 开头；code 是 build/negative-test contract，
message wording 供人阅读但不作 machine parsing。V1 catalog：

| Code | Category |
|---|---|
| `SOMA-1001` | missing/duplicate/invalid `@SomaSchema` package composition |
| `SOMA-1002` | schema package mapping/default package/cross-composition conflict |
| `SOMA-1101` | invalid Table/Value declaration shape or member |
| `SOMA-1102` | empty declaration、unmarked Field or invalid Field modifier |
| `SOMA-1103` | Value dependency cycle |
| `SOMA-1201` | missing/overlapping/invalid Field role |
| `SOMA-1202` | unsupported Field/leaf/null type contract |
| `SOMA-1203` | invalid/duplicate Key declaration or Key type |
| `SOMA-1204` | invalid Index declaration or Index type |
| `SOMA-1301` | generated type/member/reserved-name collision |
| `SOMA-1302` | logical Field path/Index accessor collision |
| `SOMA-1401` | missing/invalid full-source handshake |
| `SOMA-1402` | late-round composition change/incomplete aggregation |
| `SOMA-1403` | generated output ownership/Filer conflict |
| `SOMA-1501` | processor/runtime generated-contract version mismatch detectable at compile time |
| `SOMA-1901` | processor internal invariant failure |

同一 source fact 只报告最具体 code；由一个根因引发的 downstream generation error 不
级联刷屏。`SOMA-1901` 必须包含 sanitized schema identity 和 processor version，但不
把 stack trace 作为唯一 diagnostic；它始终是 implementation defect，不是用户错误。

## 13. 明确排除

- 修改 existing annotated class；
- runtime reflection schema discovery；
- package/classpath Table scan；
- `rootTables`、`name` 或 `outputPackage` 字符串 identity；
- generated compatibility alias；
- `@SomaChild` ownership graph；
- unproven multi-host incremental claim；
- processor 猜测 build host 没有提交的 source；
- schema declaration 进入 application public API。

## 14. Implementation admission Gates

Production compiler surface 出现前必须补齐：

1. 每个 stable diagnostic code 的 positive/negative fixture；
2. deterministic source/golden 与 `javap -v` signature Gate；
3. independent Java 8 consumer 和 compile-negative matrix；
4. Maven/IDE full-regeneration build integration 与 stale cleanup；
5. schema add/remove/rename/move/change 的 clean-full equivalence；
6. 若声明 Gradle incremental，必须另行取得真实 Gate。

当前 P2 evidence 只证明部分 Java 8 type shape、runtime mechanism 和 build boundary
可行，详见[正式 Conformance 记录](../conformance/p2-generated-api-feasibility.md)。
Exact public/generated signature 与 artifact/version policy 已分别由
[Generated Java API Signature](generated-api-signatures.md)和
[Production Implementation Architecture](implementation-architecture.md)关闭。
