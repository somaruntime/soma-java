# SOMA Java V1 Schema 与编译生成 Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Schema composition、annotation/type contract、generated identity/object、命名、
diagnostic 与 full-regeneration support

最后审查日期：2026-08-03

## 1. 设计目标

Schema只描述application希望长期持有的Table结构。Processor在Java 8编译期验证完整
composition并生成唯一typed surface；runtime不使用reflection重新发现schema。

本Design不拥有Table运行状态、API operation semantics、planner rewrite或artifact topology。

## 2. Composition declaration

Composition由无参数package-level `@SomaSchema`声明：

```java
@SomaSchema
package com.example.scheduler.soma.schema;
```

规则：

- processor只收集该exact package直接包含的package-private top-level `@SomaTable`；
- 不扫描subpackage、filesystem或dependency classpath；
- package名必须以`.schema`结尾，其父package是generated API namespace；
- generated namespace不能等于或位于`io.github.somaruntime.soma` shared/internal namespace之下，
  也不能位于platform-reserved `java.*`、`javax.*`、`jdk.*`或`sun.*` namespace；违反时使用
  stable composition diagnostic；
- composition至少包含一张Table；同package所有Table自动进入同一个`SomaGroup`；
- `@SomaValue`从Table Field递归发现，不作为root显式列举；
- schema declaration type不进入application public signature；
- 一个generated namespace只能对应一个composition；collision为compile failure。

Child ownership graph已经退出产品，因此没有root/child Table推导；所有Table在Group中平等。

## 3. Annotation inventory

V1只有六个`RetentionPolicy.CLASS` annotation：

```text
@SomaSchema  @SomaTable  @SomaValue
@SomaField   @SomaKey    @SomaIndex
```

六个annotation均使用`@Documented`，不使用`@Inherited`。

| Annotation | Target | 作用 |
|---|---|---|
| `@SomaSchema` | PACKAGE | 声明一个composition |
| `@SomaTable` | TYPE | 声明Table schema与initial capacity hint |
| `@SomaValue` | TYPE | 声明可递归flatten的logical value |
| `@SomaField` | FIELD | 声明普通logical Field/value leaf |
| `@SomaKey` | FIELD | 声明可选唯一point identity，同时隐含Field |
| `@SomaIndex` | FIELD | 声明non-unique exact-match access path，同时隐含Field |

正式annotation：

```java
public @interface SomaTable {
    int defaultCapacity() default 16;
}
```

`defaultCapacity < 0`编译失败。它是initial capacity hint，不是maximum、立即分配承诺或
chunk geometry。

Accessor创建Table facade时`capacity()==0`且不分配payload。第一次positive `reserve(n)`以
`n`为required target；未reserve而第一次`add`时，growth至少满足一行，并可以用
`defaultCapacity`作为initial growth hint。`defaultCapacity==0`合法，表示不额外预留；它不
改变add的可用性。Hint不是reservation requirement：若完整hint不适合current managed budget，
runtime可降低到满足本次add的whole-Chunk target；不能仅因hint过大让一个otherwise feasible add
失败。Explicit `reserve(n)`才要求成功后`capacity>=n`或fail closed。

`@SomaOptional`、`@SomaDefault`、`@SomaIgnore`、`@SomaChild`、`@SomaUnique`、
`SomaSemantic`和`@SomaTable(name=...)`不存在，也不生成compatibility alias。

## 4. Declaration shape

- `@SomaTable/@SomaValue`必须是exact schema package中的package-private top-level final class；
- declaration只有instance Field和annotation，不声明constructor、method、initializer、
  inheritance或generic type parameter；
- 每个`@SomaTable/@SomaValue`至少声明一个有SOMA role的direct instance Field；空Table/Value
  编译失败，不为no-arg/canonical constructor collision建立特殊形态；
- Field不是`static/final/transient/volatile`，没有initializer；
- Field名必须是合法、稳定且不以`_`开头；
- schema package segment、Table/Value simple name与Field name必须是NFC、可见的Java identifier：
  拒绝`$`、identifier-ignorable/Unicode FORMAT或control code point、bidi override/isolate及非NFC
  spelling；中文等可见Unicode identifier合法；processor不静默normalize/rename；
- processor不修改用户class，不依赖Lombok、bytecode weaving或reflection。

## 5. Field role

每个direct Table Field必须且只能有一个role：

| Role | 数量 | 合同 |
|---|---:|---|
| `@SomaKey` | `0..1` | unique point identity；发布后immutable |
| `@SomaIndex` | `0..N` | non-unique exact-match Index |
| `@SomaField` | `0..N` | ordinary payload |

`@SomaKey/@SomaIndex`隐含Field，不能叠加`@SomaField`。Table可以keyless。Composite Key使用
一个完整`@SomaValue`，不把多个direct Field分别标Key。

`@SomaValue`内部的每个direct Field必须且只能使用`@SomaField`；`@SomaKey/@SomaIndex`只允许
出现在direct Table Field。Key/Index不能标在Value或nested leaf上，也不能通过flattening传播。

Index只标direct logical Field，生成`by<FieldName>(value)`；V1不声明tuple、nested-subfield、
range、prefix或secondary unique Index。

## 6. Field type system

Processor在生成任何source前，把每个Field映射为最终public/generated signature type。除会映射为
generated counterpart的`@SomaValue`外，Enum、ordinary Object、array component、parameterized
raw type及其所有bound/type argument必须按Java 8 source access rule从generated parent namespace
可引用；否则composition以stable SOMA diagnostic整体失败。不能把inaccessible `.schema`
package-private helper type留给后续javac报普通access error，也不能擦除成`Object`规避。允许
package-private type的前提是它本来就在generated parent package并可由generated source合法引用；
其application可见性仍沿用Java自身规则。

| 类型 | Schema null | Equality/order capability | Key/Index |
|---|---|---|---|
| 八种primitive | non-null，未提供时零值 | exact primitive；numeric/order按type | float/double不可，其余可 |
| `String` | nullable | content equality / natural order | 可 |
| Enum | nullable | constant identity / declaration order | 可 |
| `@SomaValue` | outer non-null；reference leaf可null | structural equality；无implicit order | 仅recursively keyable Value可 |
| ordinary Object/temporal | nullable | 仅callback/explicit Comparator | 不可 |

float/double equality使用wrapper canonical semantics：NaN canonical equal，`+0.0`与`-0.0`
不同；natural order使用`Float.compare/Double.compare`。它们可filter/distinct/sort，不作为
Key/Index。

Date/Time/Instant没有特殊lowering。Application需要hot scan/order/aggregate/Index时必须
显式编码成带单位primitive或Value；否则作为ordinary reference。

`@SomaValue`要求：

- 可递归包含primitive、String、Enum或其他eligible Value；
- graph必须acyclic；
- ordinary Object不能出现在Value中；
- structural equality/hash由全部leaf按declaration encounter order组成；nullable reference leaf按
  `Objects.equals/hashCode`，因此null leaf与null leaf structural equal；
- outer Value与nested Value non-null；nullable只发生在允许的reference leaf。

“Recursively keyable Value”要求全部leaf最终只属于boolean/byte/short/char/int/long、String或
Enum；任何float/double leaf都会让完整Value仍可存储、filter、distinct与materialize，但不能
标为Key/Index，也不能用作GroupBy key或Equality Join component。把float/double包进Value不能
绕过direct float/double identity限制；需要稳定业务身份时由application显式编码为long或其他
keyable Value。

## 7. Generated identity

`.schema`父package生成：

```text
Soma
SomaGroup
Xxx                         detached application object/value
XxxTable                    Table facade
XxxTable.View / Editor / Stream / Selection / ReadStream / IndexSelection
typed Field endpoints and composition support
```

Identity唯一来自schema declaration simple name：

- `TransportTime` -> `TransportTime`、`TransportTimeTable`、`transportTimeTable()`；
- `@SomaIndex MachineId machineId` -> `byMachineId(MachineId)`；
- no pluralization、name override、suffix guessing或runtime registry。

Exact transformation只处理首个Unicode code point并使用locale-independent
`Character.toLowerCase/toUpperCase(int)`：

```text
Table object       = <SchemaSimpleName>
Table facade       = <SchemaSimpleName> + "Table"
Table accessor     = lowerFirstCodePoint(<SchemaSimpleName>) + "Table"
Index accessor     = "by" + upperFirstCodePoint(<FieldName>)
```

不采用JavaBeans acronym规则；例如`URL`机械得到`URLTable`与`uRLTable()`。Schema名本身以
`Table`结尾时仍机械追加`Table`，不猜测或去重。所有不自然结果都允许application通过选择更自然
的schema/Field名解决；processor不增加override。完整symbol table在写source前拒绝collision。

同composition/ClassLoader只有一个generated `Soma`与`SomaGroup`。

## 7.1 Canonical schema encounter order

每个Table/Value的direct Field按Java source declaration order形成canonical encounter order；Value
flattening按该顺序depth-first递归。Canonical全参constructor参数、generated Field/member顺序、
fetch/materialization、Value structural equality/hash、diagnostic排序与generated source都使用这
一顺序；不能使用hash iteration、filesystem、processor round或classloader order。Processor只
处理完整source composition，并以golden验证javac/qualified build host提供的direct enclosed
source order；无法获得稳定source order时composition整体编译失败。

## 8. Generated application object

### 8.1 Value

`@SomaValue`生成immutable detached object：

- private final fields；
- public canonical全参constructor；
- 同名getter；
- structural `equals/hashCode`；
- 没有default constructor、setter或mutable builder。

### 8.2 Table object

`@SomaTable`生成mutable detached application object：

- private fields；
- public no-arg与canonical全参constructor；
- 同名getter和`void` setter；
- 不生成JavaBean alias或structural equality；
- storage不保存carrier object；`add`复制leaf/reference slot。

No-arg Table object是application-owned mutable carrier，可以处于尚未完整赋值的状态；这不是
SOMA Table中的published record。`add`在读取carrier后、任何publication前验证全部schema
约束：primitive缺省为Java零值，reference payload可null，Key与outer/nested Value必须non-null。
验证失败为structured failure，Table不改变。Processor不把no-arg constructor解释成合法
domain default，也不为Value生成no-arg object。

### 8.3 Borrowed support types

`XxxTable.View`、`Editor`与Value View是callback-scoped reusable borrowed view；constructor
不属于application surface。稳定保存必须`fetch()`为detached object。Generated nested support
type必须使用private constructor，不能依赖Java隐式default/package constructor；generated
application package不是访问控制边界。Top-level Group/Table有效创建必须经过不可伪造的
composition capability token，不能只依赖package-private constructor。

## 9. Reference schema

```java
@SomaValue
final class MachineId {
    @SomaField long value;
}

@SomaValue
final class MachinePairKey {
    @SomaField MachineId fromMachine;
    @SomaField MachineId toMachine;
}

@SomaTable(defaultCapacity = 4_096)
final class TransportTime {
    @SomaKey MachinePairKey machinePair;
    @SomaField long transportMinutes;
}

@SomaTable(defaultCapacity = 65_536)
final class MachineEvent {
    @SomaIndex MachineId machineId;
    @SomaField long eventMinute;
}
```

## 10. Full-regeneration contract

Processor是composition-level aggregating processor。Correctness contract是full regeneration：

1. build host提供完整schema source set；
2. processor option包含`-Asoma.fullSourceSet=true`；
3. generated output在每次compile前被完整替换或clean；
4. processor等待round稳定后一次生成完整composition；
5. schema删除/重命名后旧generated source不能残留；
6. 不支持raw partial javac、Gradle isolating incremental或IDE自有partial processor path。

标准Maven compile与IDE delegated Maven build是V1 support path。未来incremental support必须
另建Temporary并证明完整性，不是processor猜测partial input。

## 11. Naming与reserved surface

Processor先构建完整generated symbol table，再写任何source。Collision涉及：

- Java/Object member；
- `Soma/SomaGroup/Table` direct operation；
- Field child、Index accessor、nested type；
- stream/expression/join/group/metadata/explain member；
- generated file/class和case-folded filesystem name；
- NFC/case-folded logical/generated name；
- generated exact FQN已由当前source set或dependency classpath中的type占用。Processor不扫描
  dependency寻找schema，但必须对自己将要生成的有限FQN集合执行exact lookup。

发生collision时composition整体失败，不加数字/suffix自动消歧。

## 12. Compiler failure boundary

Stable diagnostic使用`[SOMA-xxxx]`前缀，至少覆盖：

- composition/package/full-source-set；
- reserved shared/internal/JVM package namespace；
- annotation target、declaration modifier与Field role；
- empty Table/Value declaration；
- unsupported/cyclic type；
- Key/Index eligibility与数量；
- checked int defaultCapacity range；
- generated name/reserved collision；
- unsafe/invisible/non-NFC/`$`/bidi identifier；
- expression capability/literal mismatch；
- Join `on/and` Field type/composition compatibility；
- late round、duplicate generation、stale output与version handshake。

Diagnostic不得泄漏绝对path、内部stack或nondeterministic round order。Compiler不生成partial
composition；schema input order不改变generated source。

## 13. Artifact/version boundary

Generated source只依赖同版本runtime public/internal linkage。Processor与runtime version不匹配
必须在compile或initial linkage阶段稳定失败；upgrade要求full regenerate/recompile，不承诺
generated class binary compatibility。

Artifact topology由[Implementation Architecture](implementation-architecture.md)拥有，exact
method grammar由[Generated Signature](generated-api-signatures.md)拥有。

## 14. Evidence Gate

Implementation必须证明：

- valid/invalid annotation matrix与stable diagnostic golden；
- late-round、100+Table/Field、name collision、deletion/rename stale cleanup；
- deterministic generated source与provenance marker；
- Java 8 independent consumer、`javap`、constructor visibility；
- processor/runtime mismatch与malformed schema security negatives；
- Maven clean/full build和IDE delegated build。
