# Generated Java API Signature Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 shared/generated Java 8 public signature、type family、operation property、metadata carrier 与 generated ABI projection

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 文档责任

本文把[逻辑层 API Design](logical-api.md)中的能力投影为实现者可以直接生成和验证的
Java 8 signature grammar。Logical API 仍拥有行为语义；本文唯一拥有：

- public package/type/member 的精确命名规则；
- generated type hierarchy 与 constructor visibility；
- Stream/Field/primitive functional type family；
- operation stateless/stateful/short-circuit property；
- terminal result type；
- metadata exact carrier；
- signature absence 与 generated ABI contract reference。

本文使用 `Xxx`、`fieldPath` 等 schema placeholder，不复制每张 Table 的机械展开结果。
Production generator 必须用 golden source、`javap -v` 和 independent consumer 固定机械
展开。

## 2. Public package contract

`soma-runtime` artifact 拥有以下 application-visible package：

```text
io.github.somaruntime.soma.annotation
io.github.somaruntime.soma.api
io.github.somaruntime.soma.function
io.github.somaruntime.soma.metadata
```

`io.github.somaruntime.soma.internal.*` 中可能存在 `public` type，只用于 generated code
linkage，不是 application API。Application 对 internal package 的 source/binary dependency
不受兼容性承诺保护。

Composition-generated type 位于 `.schema` 的父 package。Schema declaration type 从所有
generated public signature 中消失。

### 2.1 Generated name grammar

对 schema simple type `Xxx` 与 Field identifier `fieldName`：

```text
detached Value/Table object  Xxx
Table facade                 XxxTable
Group/Soma accessor          Introspector.decapitalize(Xxx) + "Table"
Field endpoint type          upperFirstCodePoint(fieldName) + "Field"
Field endpoint member        fieldName
Index accessor               "by" + upperFirstCodePoint(fieldName)
```

`Introspector.decapitalize` 精确采用 Java 8 `java.beans.Introspector` semantics；
`upperFirstCodePoint` 只用 `Character.toUpperCase` 转换第一个 Unicode code point并保留其余
字符。Processor 不做 pluralization、acronym guess、locale transform 或字符串 `name`
override。任何结果与用户 type/member、system nested type、同 scope Field/path 冲突都以
Schema Design 的 stable diagnostic 失败，不自动加 suffix。

## 3. Annotation signatures

所有 annotation 使用 `RetentionPolicy.CLASS`、`@Documented`，不使用 `@Inherited`：

```java
@Documented
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface SomaSchema {}

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface SomaTable {
    int defaultCapacity() default 16;
}

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface SomaValue {}

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface SomaField {}

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface SomaKey {}

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface SomaIndex {}
```

`defaultCapacity` 允许 `0..Integer.MAX_VALUE`。Negative value 编译失败；具体 array length
能否分配由 runtime checked resource boundary 决定。`0` 表示初始不分配 payload slot，
不是 zero-capacity maximum。

Annotation 不定义 `name`、`fields`、`outputPackage`、`rootTables` 或 compatibility
member。Value declaration 的 instance storage Field 只允许 `@SomaField`；Key/Index 只
允许 Table direct Field。

## 4. Shared Result 与 failure signatures

Package：`io.github.somaruntime.soma.api`。

```java
public final class UpdateResult {
    public UpdateResult(int matched, int changed);
    public int matched();
    public int changed();
}

public final class RemoveResult {
    public RemoveResult(int removed);
    public int removed();
}

public final class SomaFailureContext {
    public SomaFailureContext(String table, String fieldPath);
    public String table();
    public String fieldPath();
}

public final class SomaOperationException extends RuntimeException {
    public SomaOperationException(
            SomaFailureCode code,
            SomaOperationKind operation,
            SomaFailureContext context,
            String message,
            Throwable cause);

    public SomaFailureCode code();
    public SomaOperationKind operation();
    public SomaFailureContext context();
}
```

`SomaFailureContext` 的两个 constructor argument 都必须 non-null；empty String `""`
是“不适用”的唯一 absence representation。Logical Table/Field identity 本身不能是 empty，
因此没有二义性。Runtime 生成的 `table()` 固定为 generated Table facade FQCN，例如
`com.example.scheduler.soma.TransportTimeTable`；`fieldPath()` 固定为 Table-relative
dot-separated logical path，例如 `machinePair.fromMachine.value`。Composition/global
operation 的两个值均为空；Table-level operation 的 `fieldPath()` 为空。

Public carrier constructor validation：`UpdateResult` 要求
`0 <= changed <= matched`，`RemoveResult` 要求 `removed >= 0`；failure constructor 的
`code/operation/context/message` non-null，`cause` 可以 null。Application 直接构造这些
carrier 时违反前置条件抛 `IllegalArgumentException`，因为它不是一个已取得 Table
operation identity/admission 的 SOMA operation，不能伪造 `SomaOperationException`。

`UpdateResult`、`RemoveResult`、failure carrier 与 metadata 不声明 stable Java
serialization format。它们可以被 JVM serialization mechanism 观察，但 V1 不承诺跨
version serialization compatibility。

`SomaFailureCode` 与 `SomaOperationKind` 的 exact enum values 由
[Result 与 Structured Failure Design](results-and-failures.md)拥有，本文不复制第二份
列表。

## 5. Generated application object

### 5.1 Value

Schema `MachineId` 生成父 package `public final class MachineId`：

```java
public final class MachineId {
    public MachineId(long value);
    public long value();

    @Override public boolean equals(Object other);
    @Override public int hashCode();
    @Override public String toString();
}
```

- Field 为 `private final`；
- constructor 按 schema source order；
- constructor 在赋值前拒绝 null nested generated Value，抛 `IllegalArgumentException`；
  String/Enum leaf 的 null 仍是合法 Value state，是否可作为 Key 由 Table operation
  validation 决定；
- 不生成 no-arg constructor、setter 或 JavaBean alias；
- `equals/hashCode` 使用正式 Value structural semantics；
- `toString()` 按 Field source order 生成 human-readable representation，但不是
  serialization、parser 或跨 schema/version machine contract；
- full Value callback 使用 generated nested `MachineId.View`，不是 `MachineId`。

每个 generated Value 同时包含只在 callback signature 中出现的 borrowed View type：

```java
public final class MachineId {
    public static final class View {
        public long value();
        public MachineId fetch();
    }
}
```

`View` constructor 不属于 application surface。普通用户通常只在 lambda inference 中
看到它；stable business value 仍是 `MachineId`。

### 5.2 Detached Table object

Schema `TransportTime` 生成：

```java
public final class TransportTime {
    public TransportTime();
    public TransportTime(MachinePairKey machinePair, long transportMinutes);

    public MachinePairKey machinePair();
    public void machinePair(MachinePairKey value);

    public long transportMinutes();
    public void transportMinutes(long value);
}
```

Field 为 private；不生成 public mutable Field、JavaBean alias 或 structural
`equals/hashCode`。No-arg constructor 使用 Java zero initialization；只有 add/update
validation 成功后，其内容才能进入 Table。

## 6. Generated composition root

每个 composition 的父 package 生成唯一：

```java
public final class Soma {
    public static SomaGroup defaultGroup();
    public static SomaGroup createGroup();
    public static TransportTimeTable transportTimeTable();
    public static void setParallelExecutor(ForkJoinPool executor);
    public static SomaMetadata _metadata();
}

public final class SomaGroup {
    public TransportTimeTable transportTimeTable();
    public SomaGroupMetadata _metadata();
}
```

`Soma` 的 constructor 为 private。`SomaGroup` constructor 为 package-private guarded
constructor，不属于 public API。

Java 8 无法让两个 top-level sibling class 互相构造，同时对同 package application
形成真正的 compile-time friend boundary。因此 exact contract 是：

- other-package consumer 不能调用 constructor；
- same-package code 可能看见 package-private guarded signature；
- constructor 接受由 generated `Soma` 私有持有的 identity token；
- arbitrary/null token 必须在创建任何 Table/array 前以
  `SomaOperationException(INVALID_ARGUMENT, QUERY, ...)` 失败；
- same-package code 不能通过 ordinary Java source/type access 伪造有效 Group；application
  使用 reflection/Unsafe 绕过 access/token 属于 unsupported tampering，不是 SOMA security
  sandbox 承诺。

“direct construction absent”是 application capability absence，不虚构 Java 8 能让
package-private member 对同 package source 不可见。

## 7. Generated Table facade

对每个 Table 生成 `public final class XxxTable`。以 keyed `TransportTimeTable` 为例：

```java
public final class TransportTimeTable {
    public final MachinePairField machinePair;
    public final TransportMinutesField transportMinutes;

    public int size();
    public int capacity();
    public void reserve(int expectedRows);
    public void add(TransportTime value);

    public Optional<TransportTime> find(MachinePairKey key);
    public TransportTime get(MachinePairKey key);
    public UpdateResult update(TransportTime replacement);
    public RemoveResult remove(MachinePairKey key);

    public Stream stream();
    public Stream parallelStream();
    public SomaTableMetadata _metadata();

    public static class Record { ... }
    public static final class Editor extends Record { ... }
    public final class Stream { ... }
    public final class IndexSelection { ... }
}
```

另一张声明了 direct `@SomaIndex MachineId machineId` 的 Table 生成：

```java
public IndexSelection byMachineId(MachineId value);

public final class IndexSelection {
    public Stream stream();
    public Stream parallelStream();
}
```

Rules：

- Key/Index method parameter 使用 schema direct Field 的 exact application-visible logical
  type；primitive 保持 primitive，String/Enum/Value 保持 exact generated/declared type，
  不接受 wrapper/universal `Object` overload；
- keyless Table 缺少 `find/get/update/remove` point methods；
- `by<FieldName>` 只为 direct `@SomaIndex` Field 生成；
- `IndexSelection` 只含 `stream()/parallelStream()`；
- Table 是 generated top-level final class；Record/Editor/Table Stream/IndexSelection 和
  Field/Field Stream 按本文规则成为 public nested type；普通 consumer 不依赖 internal
  superclass；
- Table guarded constructor package-private，必须校验 generated `SomaGroup` 私有 token
  与 owner identity；
- same-package arbitrary constructor call 可以编译但不能得到有效 instance；
- endpoint 是 eagerly constructed `public final` logical Field object，不是 accessor
  method，也不触发 storage allocation。
- `Record` 是 public static non-final（只供 generated `Editor` 继承），`Editor` 是 public
  static final；Table `Stream/IndexSelection`、Field、nested Field 和各 Field `Stream` 都
  是 public final non-static nested type；Value `View` 是 public static final nested type；
- 上述 nested type 的 application-valid constructor 全部缺席或 guarded，不生成 public
  no-arg factory。

## 8. Record、Editor 与 Value View signatures

Generated Record 只暴露 getter 和 `fetch()`：

```java
public static class Record {
    public MachinePairKey.View machinePair();
    public long transportMinutes();
    public TransportTime fetch();
}

public static final class Editor extends Record {
    public void transportMinutes(long value);
    @Override public TransportTime fetch();
}
```

Editor 为每个 non-Key direct Table Field 生成同名 whole-Field setter，接受 stable
declared/generated type；不在 Record callback 中生成 nested setter。Nested leaf update
使用 typed Field projection terminal。Key root/nested getter 在 Editor 中仍无 setter。
Record/Editor constructor 不属于 application surface。

Value View 是 generated Value 的 nested type：

```java
public final class MachinePairKey {
    public static final class View {
        public MachineId.View fromMachine();
        public MachineId.View toMachine();
        public MachinePairKey fetch();
    }
}
```

View 不 override `equals/hashCode/toString` 来伪装 stable Value；需要 structural equality
时比较 leaf，或在 callback 中 `fetch().equals(value)`。把 mutable/reusable View 放入
Map/Set 或跨 callback 保存是 contract violation。

Comparator/min/max/sort 同时需要两个 logical operands。Runtime 因此承诺的是每个
participant 常数个 cursor/View，总量 O(P)，不是所有 operation 都恰好一个 object。

## 9. Field endpoint grammar

每个 direct/nested logical path 生成唯一 endpoint type 和 `public final` member。Endpoint
type 按路径嵌套在 generated Table/parent endpoint 内，不污染 generated package：

```text
table.machinePair                         TransportTimeTable.MachinePairField
table.machinePair.fromMachine             MachinePairField.FromMachineField
table.machinePair.fromMachine.value       FromMachineField.ValueField
```

机械 shape：

```java
public final class TransportTimeTable {
    public final MachinePairField machinePair;
    public final TransportMinutesField transportMinutes;

    public final class MachinePairField {
        public final FromMachineField fromMachine;
        public final ToMachineField toMachine;
        public Stream stream();
        public Stream parallelStream();
        public final class Stream { ... }
    }

    public final class TransportMinutesField {
        public Stream stream();
        public Stream parallelStream();
        public final class Stream { ... }
    }
}
```

Parent endpoint constructor/child endpoint 由 Table 创建并共享同一个 owner token。每个
endpoint 提供：

```java
Stream stream();
Stream parallelStream();
SomaFieldMetadata _metadata();
```

`Table.Stream.select(endpoint)` 为同一 Table 的每个 endpoint 生成一个 exact overload，
不使用会丢失 owner/type 的 universal `select(Field)`：

```java
MachinePairField.Stream select(MachinePairField field);
TransportMinutesField.Stream select(TransportMinutesField field);
```

Foreign Table/Group endpoint 因 Java nominal type 相同而可能编译，runtime owner guard
必须以 `INVALID_ARGUMENT` 拒绝。

## 10. Stream type families

### 10.1 Record Stream

Generated `XxxTable.Stream` 保留 Record membership：

```java
Stream filter(Predicate<? super Record> predicate);
Stream sorted(Comparator<? super Record> comparator);
Stream skip(int n);
Stream limit(int maxSize);

// 这里机械插入第 9 节规定的每个 Field exact select overload

<R> MappedStream<R> map(Function<? super Record, ? extends R> mapper);
SomaIntStream mapToInt(ToIntFunction<? super Record> mapper);
SomaLongStream mapToLong(ToLongFunction<? super Record> mapper);
SomaDoubleStream mapToDouble(ToDoubleFunction<? super Record> mapper);

long count();
boolean anyMatch(Predicate<? super Record> predicate);
boolean allMatch(Predicate<? super Record> predicate);
boolean noneMatch(Predicate<? super Record> predicate);
Optional<Xxx> findFirst();
void forEach(Consumer<? super Record> consumer);
List<Xxx> toList();
Xxx[] toArray();
Optional<Xxx> min(Comparator<? super Record> comparator);
Optional<Xxx> max(Comparator<? super Record> comparator);
UpdateResult update(Consumer<? super Editor> updater);
RemoveResult remove();
```

Record Stream 不提供 no-arg natural `sorted/min/max`、`distinct` 或 generic `reduce`。

### 10.2 Field Stream

Field Stream 是 endpoint nested generated exact type。它保留 Field slot lineage；共同
operation 为：

```text
filter / sorted when legal / skip / limit
map / mapToInt / mapToLong / mapToDouble
count / match / findFirst / forEach / toList / legal toArray
natural or Comparator min/max when legal
distinct when intrinsic equality exists
update when Field root is not Key
```

`distinct()` 返回 query-only mapped primitive/reference stream。Key root 及其全部 nested
Field 的 Stream 不生成 `update`。Field Stream 永远不生成 `remove`。

对 scalar reference Field `T`，processor 从下面 grammar 删除 type-ineligible member；
所有返回 `Stream` 的 operation 保留 Field lineage：

```java
Stream filter(Predicate<? super T> predicate);
Stream sorted();                                      // intrinsic natural order only
Stream sorted(Comparator<? super T> comparator);
Stream skip(int n);
Stream limit(int maxSize);

MappedStream<T> distinct();                           // intrinsic equality only
<R> MappedStream<R> map(Function<? super T, ? extends R> mapper);
SomaIntStream mapToInt(ToIntFunction<? super T> mapper);
SomaLongStream mapToLong(ToLongFunction<? super T> mapper);
SomaDoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

long count();
boolean anyMatch(Predicate<? super T> predicate);
boolean allMatch(Predicate<? super T> predicate);
boolean noneMatch(Predicate<? super T> predicate);
Optional<T> findFirst();
void forEach(Consumer<? super T> consumer);
List<T> toList();
T[] toArray();                                        // reifiable Field only
Optional<T> min();                                    // intrinsic natural order only
Optional<T> max();                                    // intrinsic natural order only
Optional<T> min(Comparator<? super T> comparator);
Optional<T> max(Comparator<? super T> comparator);
UpdateResult update(UnaryOperator<T> updater);         // non-Key root only
```

Ordinary/parameterized Object 不含 no-arg order、intrinsic `distinct`；parameterized
reference 不含 `toArray()`。Comparator operation 和 application mapper 仍可使用。

Full Value Field callback 使用 `V.View`；stable terminal result 使用 `V`。下列是 signature
grammar，processor 将 placeholder 机械展开成 endpoint 自己的 nested `Stream`，不发布
shared `ValueFieldStream`：

```java
Stream filter(Predicate<? super V.View> predicate);
Stream sorted(Comparator<? super V.View> comparator);
Stream skip(int n);
Stream limit(int maxSize);
MappedStream<V> distinct();
<R> MappedStream<R> map(Function<? super V.View, ? extends R> mapper);
SomaIntStream mapToInt(ToIntFunction<? super V.View> mapper);
SomaLongStream mapToLong(ToLongFunction<? super V.View> mapper);
SomaDoubleStream mapToDouble(ToDoubleFunction<? super V.View> mapper);
long count();
boolean anyMatch(Predicate<? super V.View> predicate);
boolean allMatch(Predicate<? super V.View> predicate);
boolean noneMatch(Predicate<? super V.View> predicate);
Optional<V> findFirst();
void forEach(Consumer<? super V.View> consumer);
List<V> toList();
V[] toArray();
Optional<V> min(Comparator<? super V.View> comparator);
Optional<V> max(Comparator<? super V.View> comparator);
UpdateResult update(Function<? super V.View, ? extends V> updater);
```

Value 没有 implicit natural order。Key-root Value Stream 删除 `update`。

### 10.3 Reference Mapped Stream

Shared `io.github.somaruntime.soma.api.MappedStream<R>`：

```java
public interface MappedStream<R> {
    MappedStream<R> filter(Predicate<? super R> predicate);
    MappedStream<R> sorted(Comparator<? super R> comparator);
    MappedStream<R> distinct();
    MappedStream<R> skip(int n);
    MappedStream<R> limit(int maxSize);
    <U> MappedStream<U> map(Function<? super R, ? extends U> mapper);
    SomaIntStream mapToInt(ToIntFunction<? super R> mapper);
    SomaLongStream mapToLong(ToLongFunction<? super R> mapper);
    SomaDoubleStream mapToDouble(ToDoubleFunction<? super R> mapper);

    long count();
    boolean anyMatch(Predicate<? super R> predicate);
    boolean allMatch(Predicate<? super R> predicate);
    boolean noneMatch(Predicate<? super R> predicate);
    Optional<R> findFirst();
    void forEach(Consumer<? super R> consumer);
    List<R> toList();
    <A> A[] toArray(Class<A> componentType);
    Optional<R> min(Comparator<? super R> comparator);
    Optional<R> max(Comparator<? super R> comparator);
}
```

Arbitrary `R` 无 compile-time Comparable guarantee，因此不提供 no-arg natural
`sorted/min/max`。Mapped Stream 不生成 mutation 或 generic `reduce/collect`。

### 10.4 Primitive Streams

Package `io.github.somaruntime.soma.api` 提供下列 public query-only interface；application
不能直接构造 implementation：

```text
SomaBooleanStream
SomaByteStream
SomaShortStream
SomaCharStream
SomaIntStream
SomaLongStream
SomaFloatStream
SomaDoubleStream
```

Field lineage 需要 update 时，generated Field Stream 包含对应 update method，并可
delegate 到 shared query kernel。boolean/byte/short/char/float callback 使用
`io.github.somaruntime.soma.function` 中按下列 grammar 命名的 primitive functional
interface；int/long/double 使用 JDK `java.util.function` specialization。任何类型都不能
退回 wrapper generic：

```text
<Primitive>Predicate
<Primitive>Consumer
<Primitive>UnaryOperator
<Primitive>Function<R>
<Primitive>ToIntFunction
<Primitive>ToLongFunction
<Primitive>ToDoubleFunction
```

Primitive query signature grammar（`S` 为对应 `SomaXxxStream`，`P` 为 primitive，
`B` 为 wrapper，`A` 为对应 primitive array，`F` 为对应 function prefix）：

```java
S filter(FPredicate predicate);
S distinct();
S sorted();                               // boolean 删除
S skip(int n);
S limit(int maxSize);
S map(FUnaryOperator mapper);
<R> MappedStream<R> mapToObj(FFunction<? extends R> mapper);
// boolean/byte/short/char/float: mapToInt/mapToLong/mapToDouble
// int: mapToLong/mapToDouble；long: mapToInt/mapToDouble；double: mapToInt/mapToLong

long count();
boolean anyMatch(FPredicate predicate);
boolean allMatch(FPredicate predicate);
boolean noneMatch(FPredicate predicate);
OptionalCarrier findFirst();
void forEach(FConsumer consumer);
List<B> toList();
A toArray();
OptionalCarrier min();                    // boolean 删除
OptionalCarrier max();                    // boolean 删除
// 按第 12 节矩阵生成 sum/average
```

Cross-primitive exact return/function matrix：

| Source | int target | long target | double target |
|---|---|---|---|
| boolean/byte/short/char/float | `SomaIntStream mapToInt(FToIntFunction)` | `SomaLongStream mapToLong(FToLongFunction)` | `SomaDoubleStream mapToDouble(FToDoubleFunction)` |
| int | same-type `map(IntUnaryOperator)` | `SomaLongStream mapToLong(IntToLongFunction)` | `SomaDoubleStream mapToDouble(IntToDoubleFunction)` |
| long | `SomaIntStream mapToInt(LongToIntFunction)` | same-type `map(LongUnaryOperator)` | `SomaDoubleStream mapToDouble(LongToDoubleFunction)` |
| double | `SomaIntStream mapToInt(DoubleToIntFunction)` | `SomaLongStream mapToLong(DoubleToLongFunction)` | same-type `map(DoubleUnaryOperator)` |

Generated primitive Field `Stream` 对 `filter/sorted/skip/limit` 返回自身以保留 lineage；
`distinct/map/mapToObj/mapToXxx` 返回 shared query-only Stream。Non-Key root 额外生成：

```java
UpdateResult update(FUnaryOperator updater);
```

Function exact single abstract method：

| Family | Method |
|---|---|
| `<Primitive>Predicate` | `boolean test(P value)` |
| `<Primitive>Consumer` | `void accept(P value)` |
| `<Primitive>UnaryOperator` | `P applyAsPrimitive(P value)` |
| `<Primitive>Function<R>` | `R apply(P value)` |
| `<Primitive>ToIntFunction` | `int applyAsInt(P value)` |
| `<Primitive>ToLongFunction` | `long applyAsLong(P value)` |
| `<Primitive>ToDoubleFunction` | `double applyAsDouble(P value)` |

其中 `Primitive/applyAsPrimitive` 机械替换为 `Boolean/applyAsBoolean`、
`Byte/applyAsByte`、`Short/applyAsShort`、`Char/applyAsChar`、`Float/applyAsFloat`。
`int/long/double` 使用 JDK `java.util.function` 对应 type/method；同 primitive mapping 使用
JDK `IntUnaryOperator/LongUnaryOperator/DoubleUnaryOperator`。只发布 V1 grammar 实际引用
的 custom interface，不建立开放 primitive SPI。Lambda inference 使普通调用不需要
显式书写这些类型。所有 custom functional type 都是 `public @FunctionalInterface`，
不含 default method 或 boxing bridge。

## 11. Intermediate operation properties

| Operation | State | Order | Short-circuit | Lineage |
|---|---|---|---|---|
| `filter` | stateless | preserving | 否 | 保留 |
| `select` | stateless projection | preserving | 否 | Record -> Field |
| `map/mapToObj/mapToXxx` | stateless projection | preserving | 否 | 终止 live lineage |
| `distinct` | stateful | first encounter stable | 否 | 终止 Field lineage |
| `sorted` | stateful/full barrier | stable | 否 | 保留已有 kind/lineage |
| `skip` | stateful/order-sensitive | preserving suffix | 否 | 保留 |
| `limit` | stateful/order-sensitive | preserving prefix | 是 | 保留 |

所有 source 都 finite；“short-circuit”表示可少观察后续元素，不表示支持 infinite
source。Implementation 可以 fuse 相邻 stateless stage，但不能越过 stateful/order/
mutation/failure boundary 改变 callback 或 primary failure 语义。

所有 generated/shared Stream 使用 Execution Design 的 Java-Stream-like linked-chain
one-shot contract；intermediate 返回值不是可自由分叉的 immutable query plan。

## 12. Terminal properties 与 result types

| Terminal | Short-circuit | Side effect | Mutation | Result |
|---|---:|---:|---:|---|
| `count` | 否 | 否 | 否 | `long` |
| `anyMatch/allMatch/noneMatch` | 是 | callback only | 否 | `boolean` |
| `findFirst` | 是 | 否 | 否 | 对应 Optional |
| `forEach` | 否 | 是 | 否 | `void` |
| `toList/toArray` | 否 | 否 | 否 | detached materialization |
| `sum/average/min/max` | 否 | 否 | 否 | primitive/reference result |
| `update` | 否 | updater | 是 | `UpdateResult` |
| `remove` | 否 | 否 | 是 | `RemoveResult` |

Primitive terminal carrier：

| Value kind | `findFirst/min/max` | `sum` | `average` | `toArray` |
|---|---|---|---|---|
| boolean | `Optional<Boolean>` | absent | absent | `boolean[]` |
| byte/short/char/int | `OptionalInt` | byte/short/int -> checked `int`; char absent | byte/short/int -> `OptionalDouble`; char absent | exact primitive array |
| long | `OptionalLong` | checked `long` | `OptionalDouble` | `long[]` |
| float | `OptionalDouble` | `float` | `OptionalDouble` | `float[]` |
| double | `OptionalDouble` | `double` | `OptionalDouble` | `double[]` |
| String/Enum/reifiable reference | `Optional<T>` | absent | absent | `T[]` |
| Value | `Optional<V>` | absent | absent | `V[]` |
| parameterized reference | `Optional<T>` | absent | absent | absent；使用 `toList()` |

`byte/short sum` 返回 `int`，与 Java numeric promotion 一致。`float findFirst/min/max`
使用 `OptionalDouble`，结果发生 widening，不引入 `OptionalFloat`。Boxing 只允许发生在
boolean Optional 与 primitive `toList()` result boundary。

Empty primitive `sum()` 返回对应 result primitive zero；empty `average/min/max/findFirst`
返回 empty Optional carrier。Integer sum 检查数学最终值的 result range；floating
sum/average 的 fixed tree、NaN/infinity/signed-zero 语义由
[Production Implementation Architecture](implementation-architecture.md)固定。
所有 reference/primitive `min/max` 在 comparison equal 时保留 canonical first
encounter value。

`skip/limit` 使用 `int`，因为 Table cardinality domain 为 int；negative argument 是
`INVALID_ARGUMENT`。`min/max(Comparator)` 与 Java Stream 命名保持一致，不使用
`minBy/maxBy` alias。

## 13. Metadata exact carrier

Package：`io.github.somaruntime.soma.metadata`。所有 type 为 `public final` immutable
detached snapshot，constructor 不属于 application surface；返回的 List 不可修改。

```java
public final class SomaMetadata {
    public String composition();
    public List<SomaTableSchemaMetadata> tables();
    public SomaParallelMetadata parallel();
}

public final class SomaGroupMetadata {
    public String composition();
    public boolean isDefault();
}

public final class SomaTableMetadata {
    public SomaTableSchemaMetadata schema();
    public SomaTableRuntimeMetadata runtime();
}

public final class SomaTableSchemaMetadata {
    public String declarationType();
    public String objectType();
    public String tableType();
    public int defaultCapacity();
    public List<SomaFieldMetadata> fields();
    public Optional<SomaFieldMetadata> key();
    public List<SomaFieldMetadata> indexes();
}

public final class SomaTableRuntimeMetadata {
    public int size();
    public int capacity();
}

public final class SomaFieldMetadata {
    public String name();
    public String path();
    public String declaredType();
    public SomaFieldRole role();
    public boolean nullable();
    public List<SomaFieldMetadata> fields();
}

public final class SomaParallelMetadata {
    public SomaParallelState state();
    public int parallelism();
    public boolean available();
}
```

Enums：

```text
SomaFieldRole     KEY INDEX FIELD NESTED
SomaParallelState UNINITIALIZED CUSTOM_FIXED COMMON_POOL_FIXED
```

Rules：

- `composition()` 固定返回带 `.schema` 的 canonical schema package name；
  `declarationType()` 返回 `.schema` Table declaration FQCN；`objectType()/tableType()`
  分别返回 parent package detached object/Table facade FQCN；
- Field `declaredType()` 使用 application-visible logical type：primitive keyword、generated
  Value FQCN，或 ordinary declared type 的 canonical generic source form；不返回 schema
  Value FQCN、erasure-only name 或 filesystem path；
- `path` 使用 dot-separated logical Field path；
- `Soma._metadata().tables()` 只返回 schema descriptor，不创建 default Group/Table；
- `tables()` 按 canonical declaration type 排序；`fields()` 保持 schema Field source
  order；`indexes()` 保持其 Table Field source order；
- Table runtime snapshot 通过 shared Read 获得 self-consistent size/capacity；
- Field endpoint `_metadata()` 返回对应 immutable descriptor；
- `parallelism == 0` 只用于 `UNINITIALIZED`；fixed state 使用 effective pool parallelism；
- `available()` 是只读 snapshot hint：`UNINITIALIZED` 和未被观察为 shutdown/terminating
  的 fixed pool 返回 `true`，已知 shutdown/terminating/terminated 的 custom fixed pool
  返回 `false`；它不固定 configuration、不提交 probe task，也不保证随后的 terminal
  一定不会因 race/rejection 失败；
- 不返回 raw pool、Class、Method/Field reflection object、stateVersion、storage bytes、
  Index statistics、schema fingerprint 或 mutable collection。

V1 不增加单独 logical-type descriptor graph；`declaredType()` 已完整表达 processor 看到
的 declared Java type，nested Value shape 由 `fields()` 表达。

## 14. Version、generation 与 compatibility

- artifact pairing/version 由
  [Production Implementation Architecture](implementation-architecture.md)唯一拥有；
  Signature projection 服从其 exact runtime/processor pair；
- generated source/class 是 build output，不是独立发布 artifact；
- runtime/processor upgrade 必须 full regenerate 并重新编译 composition；
- generated code 编译引用一个 internal generated-contract version constant；mismatch 在
  compile 或 class initialization 前 fail closed，不能带着未知 ABI 运行；
- `io.github.somaruntime.soma.api/annotation/function/metadata` 是 application public
  surface，release 后按 semantic versioning 管理；
- `internal` generated linkage 可以随 compatible processor/runtime pair 改变，不对
  application 承诺；
- generated Value/Table/Field accessor 的 add/remove/rename 是 application source/API
  change；V1 不提供旧名 alias；
- V1 不承诺 Java serialization compatibility。

## 15. Explicit absence

除既有 absence contract 外，V1 明确不生成：

- generic `reduce`、`collect`、Collector、natural arbitrary mapped sort；
- `minBy/maxBy` alias；
- `OptionalFloat` 或八套 SOMA Optional container；
- public constructor for valid Group/Table/Record/Editor/View/Field/Stream；
- mutable metadata、schema fingerprint、Class/reflection metadata；
- public runtime SPI、planner、storage/index handle；
- mapped reference no-arg `toArray()` 或 array-factory overload。

## 16. Signature evidence Gate

Production implementation 必须自动生成并验证：

1. annotation/shared API `javap -v` baseline；
2. keyed/keyless、primitive breadth、String/Enum/Value/Object/parameterized Field golden；
3. Record/Editor/View/Field/Stream exact generated source；
4. same-package guarded-construction counterexample 与 runtime rejection；
5. all positive/negative capability probes；
6. metadata carrier/source/immutability/no-side-effect probes；
7. runtime/processor mismatch failure；
8. Java 8 independent consumer lambda inference。
