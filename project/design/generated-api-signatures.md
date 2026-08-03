# SOMA Java V1 Generated Java API Signature Design

类型：Design / Exact Signature Owner

状态：Active V1 Baseline

正式事实源：是

Owner：Annotation、shared public types与generated Java 8 exact type/method grammar

最后审查日期：2026-08-03

## 1. 文档责任

本文把已经由Schema、Logical、Planning、Execution与Failure Design确认的capability投影为
Java 8 source shape。它不重新解释语义，也不要求implementation使用public generic universal
engine。

除明确标为“implementation-evidence admission”的metadata carrier成员外，本文是V1 exact
surface基准。Production processor必须用generated source、`javap -v`与independent consumer
证明一致。

## 2. Public package boundary

Maven group / Java package baseline：

```text
io.github.somaruntime.soma
```

Shared public package承担annotation、configuration、expression/function、mapped/primitive
stream、Join/Group result、Result与failure。Application schema父package承担generated
`Soma/SomaGroup/Xxx/XxxTable`。

所有进入generated method/member generic signature的application Enum/Object/array/parameterized
type必须已经通过Schema Design的parent-namespace source-accessibility validation；Signature不能
用raw/`Object` fallback掩盖不可访问类型。

`io.github.somaruntime.soma.internal.*`不属于application compatibility contract。

## 3. Annotation signatures

```java
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface SomaSchema {}

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SomaTable {
    long defaultCapacity() default 16L;
}

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SomaValue {}

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SomaField {}

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SomaKey {}

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface SomaIndex {}
```

没有其他schema annotation或name/fields/unique参数；annotation不使用`@Inherited`。

## 4. Configuration

```java
public enum SomaCompression {
    AUTO,
    OFF
}

public final class SomaConfiguration {
    public static Builder builder();

    public static final class Builder {
        public Builder parallelExecutor(ForkJoinPool executor);
        public Builder memoryBudgetBytes(long bytes);
        public Builder compression(SomaCompression compression);
        public SomaConfiguration build();
    }
}
```

Builder没有parallelism、ExecutorService、codec、Chunk、backend、spill、planner或per-Table
配置。`SomaConfiguration/Builder`都显式声明private constructor，由`builder()`创建；不能留下
Java implicit public constructor。`ForkJoinPool`与configuration lifecycle由Execution Design拥有。

## 5. Shared expression/function types

Typed IR与callback必须是不同Java type：

```java
public interface SomaRelationExpression {
    SomaRelationExpression and(SomaRelationExpression other);
    SomaRelationExpression or(SomaRelationExpression other);
    SomaRelationExpression not();
}

public interface SomaExpression<R> extends SomaRelationExpression {
    SomaExpression<R> and(SomaExpression<R> other);
    SomaExpression<R> or(SomaExpression<R> other);

    @Override
    SomaExpression<R> not();
}

@FunctionalInterface
public interface SomaPredicate<T> {
    boolean test(T value);
}
```

`SomaRelationExpression/SomaExpression`不是functional interface。Same-row composition保持
`SomaExpression<R>`；cross-side composition提升为`SomaRelationExpression`。Lambda只能选择
`SomaPredicate` overload。

Expression与Order shared type是SOMA-issued typed-plan carrier，不是application extension SPI。
Java 8无法seal public interface；application手写实现即使通过javac，也没有hidden
composition/owner/node provenance。所有composition/intermediate/terminal接收点必须在pipeline
claim、configuration freeze与Group admission前拒绝foreign/replayed implementation为
`INVALID_ARGUMENT`，不能cast到internal node后泄漏`ClassCastException`。

Primitive callback family只生成grammar实际需要的Java 8 public `@FunctionalInterface`，按
`SomaIntPredicate/SomaLongPredicate/...`、`SomaToLongFunction<T>`等机械命名；不能退回boxed
`Predicate<Integer>`hot path。

Logical Field shared view：

```java
public interface SomaField<R, V> {
    // logical Field identity/source marker；generated endpoint按V能力投影comparison/order/null
}

public interface SomaKeyableField<R, V> extends SomaField<R, V> {
    // Key/Index/GroupBy/Equality-Join eligible Field marker
}

public interface SomaOrder<R> {
    SomaOrder<R> then(SomaOrder<R> next);
}
```

Application通常不显式书写这些接口；generated endpoint有更窄、typed return。
`SomaKeyableField`只用于在Java类型层收窄`groupBy/on/and`及递归identity eligibility，不表示
该Field已经声明为`@SomaKey/@SomaIndex`，也不表示physical hash。Float/double endpoint仍可拥有
明确`eq/distinct/order`，但不实现该marker；包含float/double leaf的Value endpoint同样不实现。

`SomaField/SomaKeyableField`同样不是SPI。它们必须由generated endpoint携带hidden
composition/Table/logical-path identity；application手写marker、foreign composition endpoint或
replayed owner不能成为合法projection/GroupBy/Join component。Java generic type只承担normal
source-level narrowing，runtime provenance validation承担Java 8开放interface的防伪失败边界。
`SomaOrder.then`机械形成lexicographic tie-break order；null placement由Logical Design拥有。

## 6. Result 与 failure signatures

```java
public final class UpdateResult {
    public long matched();
    public long changed();
}

public final class RemoveResult {
    public long removed();
}

public enum SomaOperation {
    CONFIGURE, RESERVE, ADD, FIND, GET, UPDATE, REMOVE, QUERY
}

public enum SomaFailureCode {
    INVALID_ARGUMENT,
    DUPLICATE_KEY,
    MISSING_KEY,
    MISSING_RELATION_SIDE,
    NULL_VALUE_UNSUPPORTED,
    RESOURCE_LIMIT_EXCEEDED,
    ARITHMETIC_OVERFLOW,
    CONCURRENT_GROUP_OPERATION,
    REENTRANT_GROUP_OPERATION,
    NESTED_PARALLEL_OPERATION,
    CONFIGURATION_FROZEN,
    PARALLEL_EXECUTOR_UNAVAILABLE,
    OPERATION_CANCELLED,
    PIPELINE_ALREADY_CONSUMED,
    CALLBACK_SCOPE_VIOLATION,
    CALLBACK_FAILED
}

public final class SomaOperationException extends RuntimeException {
    public SomaFailureCode code();
    public SomaOperation operation();
    public String context();
    @Override public Throwable getCause();
}

public final class SomaLongSummary {
    public long count();
    public long min();
    public long max();
    public long sum();
    public double average();
}

public final class SomaDoubleSummary {
    public long count();
    public double min();
    public double max();
    public double sum();
    public double average();
}
```

Public constructor/factory不允许application制造SOMA failure code。Exact failure mapping由
Results Design拥有。

`UpdateResult/RemoveResult/SomaOperationException/SomaLongSummary/SomaDoubleSummary`都显式声明
private constructor，并由trusted internal factory构造；省略constructor而得到Java implicit public
no-arg constructor不合法。Exception的trusted provenance仍按Failure Design验证，constructor
visibility本身不是security substitute。

## 7. Generated detached objects

对：

```java
@SomaValue final class MachineId { @SomaField long value; }
```

生成：

```java
public final class MachineId {
    public MachineId(long value);
    public long value();
    @Override public boolean equals(Object other);
    @Override public int hashCode();

    public static final class View {
        public long value();
        public MachineId fetch();
        // private constructor
    }
}
```

没有no-arg constructor或setter。`View`是callback-scoped borrowed projection；它不改变detached
`MachineId`的immutability，稳定保存必须`fetch()`。

对Table schema生成mutable detached object：

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

不生成`getX/setX`alias、builder或structural `equals/hashCode`。

## 8. Generated composition root

```java
public final class Soma {
    public static SomaGroup defaultGroup();
    public static SomaGroup createGroup();

    public static TransportTimeTable transportTimeTable();
    public static MachineStateTable machineStateTable();

    public static void configure(SomaConfiguration configuration);
    public static SomaMetadata _metadata();
}

public final class SomaGroup {
    public TransportTimeTable transportTimeTable();
    public MachineStateTable machineStateTable();
    public GroupMetadata _metadata();
}
```

`Soma`没有可用constructor；`SomaGroup`没有public/protected或package-usable direct constructor。
每张composition Table机械生成accessor；没有schema declaration type、dynamic lookup、string
name或generic `table(Class)`入口。Accessor名称严格使用Schema Design的
`lowerFirstCodePoint(simpleName) + "Table"`规则，不使用JavaBeans acronym或pluralization。

## 9. Representative Table facade

```java
public final class TransportTimeTable {
    public long size();
    public long capacity();
    public void reserve(long expectedRows);
    public void add(TransportTime value);

    public Optional<TransportTime> find(MachinePairKey key);
    public TransportTime get(MachinePairKey key);
    public UpdateResult update(
            MachinePairKey key,
            Consumer<? super Editor> updater);
    public RemoveResult remove(MachinePairKey key);

    public Selection selectAll();
    public Stream parallel();

    public Selection filter(SomaExpression<View> expression);
    public Selection filter(SomaPredicate<? super View> callback);

    // query intermediate/terminal按第11节grammar机械生成

    public final MachinePairField machinePair;
    public final TransportMinutesField transportMinutes;

    public TableMetadata _metadata();

    public static class View {
        public MachinePairKey.View machinePair();
        public long transportMinutes();
        public TransportTime fetch();
        /* private constructor */
    }
    public static final class Editor extends View {
        public void transportMinutes(long value);
        public TransportTime fetch();
        /* no Key setter；private constructor */
    }
    public static class Stream {
        public Stream parallel();
        /* query-only row lineage */
    }
    public static final class Selection extends Stream {
        @Override public Selection parallel();
        public UpdateResult update(Consumer<? super Editor> updater);
        public RemoveResult remove();
    }
    public static final class ReadStream {
        public ReadStream parallel();
        /* relation-derived left query only */
    }
    public static final class IndexSelection {
        public Selection parallel();
        public UpdateResult update(Consumer<? super Editor> updater);
        public RemoveResult remove();
        /* reusable exact-Index selection source + row query grammar */
    }
}
```

Keyless Table缺少find/get/update/remove(Key)。Nested public support type的constructor必须是
`private`，不能只用package-private，因为generated package属于application。Top-level
`SomaGroup/XxxTable`使用private constructor与非公开generated factory/linkage；有效创建要求只由
generated `Soma/SomaGroup`链持有的unforgeable per-composition capability token。Application
同package即使调用可见的non-public bridge并传入null/任意Object，也不能得到有效实例。Token不
进入public signature、metadata、failure或callback；实现不依赖constructor reflection或global
live-instance registry。

这里的“unforgeable”限定在supported Java source/API与未篡改artifact：合法source不能读取真实
token，direct construction在compile time缺席，null/foreign token在publication前失败。SOMA不
声称隔离拥有同进程特权的`setAccessible`、`Unsafe`、instrumentation/agent或hand-written
bytecode；这些能力可以绕过任何Java 8 library的访问控制，属于Blueprint明确排除的sandbox
threat model。

`View`仅为generated `Editor`继承而non-final；application不能subclass，因为private constructor
与runtime scope token不可构造。`Selection`/`ReadStream`的内部继承可以由generated
package-private base承载；上例只固定public capability，不强迫具体class inheritance布局。

每个`@SomaIndex`机械生成一个accessor，例如：

```java
public MachineEventTable.IndexSelection byMachineId(MachineId value);
```

Nullable String/Enum Index允许用`null`选择normal null bucket；non-nullable Value Index的null
argument为`INVALID_ARGUMENT`。`IndexSelection`本身可重复使用；每次direct terminal形成一次
新的operation，继续intermediate则产生one-shot `Selection`。

## 10. Field endpoint grammar

每个logical endpoint是public final Table member或parent Field member：

```java
times.machinePair
times.machinePair.fromMachine
times.machinePair.fromMachine.value
```

Endpoint按type生成：

- direct query source与`parallel()`；
- comparison/null/order methods；
- typed `toArray()`；
- nested Value child；
- typed row-projection marker；
- semi-hidden `FieldMetadata _metadata()`；
- no update/remove；
- Key endpoint无setter；physical leaf/Column不可见。

Endpoint public type按logical path机械嵌套，不按Java value type共享一个universal endpoint：

```text
times.machinePair                       TransportTimeTable.MachinePairField
times.machinePair.fromMachine           MachinePairField.FromMachineField
times.machinePair.fromMachine.value     FromMachineField.ValueField
times.transportMinutes                  TransportTimeTable.TransportMinutesField
```

名字为`upperFirstCodePoint(fieldName) + "Field"`；只转换首个Unicode code point，不做复数、
acronym或locale猜测。Collision由Schema完整symbol-table validation拒绝，不自动加suffix。
Direct endpoint是Table的public final member；nested endpoint是parent endpoint的public final member。
Endpoint class为public final non-static nested class、constructor private且owner不可伪造。每个
endpoint实现`SomaField<View,V>`；只有recursively keyable endpoint额外实现
`SomaKeyableField<View,V>`。Concrete endpoint type拥有该Field精确允许的comparison、order、
source/intermediate/terminal member，因此不存在runtime capability switch。每个endpoint（包括
nested Value endpoint）都提供`FieldMetadata _metadata()`；它返回该logical path的detached
snapshot，不暴露flattened physical leaves。

Field本身是reusable sequential source；`parallel()`或任意intermediate返回该endpoint下的
public final `Stream`，其constructor同样private。完整Value Field的Stream callback element是
`V.View`、materialization是detached `V`；primitive/reference endpoint按第12节返回exact type。

Representative methods：

```java
SomaExpression<R> eq(long value);
SomaExpression<R> ne(long value);
SomaExpression<R> lt(long value);
SomaExpression<R> le(long value);
SomaExpression<R> gt(long value);
SomaExpression<R> ge(long value);
SomaExpression<R> between(long lowerInclusive, long upperInclusive);
SomaExpression<R> in(long... values);
SomaOrder<R> asc();
SomaOrder<R> desc();
```

Reference Field的`eq/in/isNull/isNotNull/order`只在type capability允许时生成。不存在的member
不能以constant、runtime unsupported或raw Object overload代替。

Endpoint family规则：

- primitive endpoint生成exact primitive callback/array与相应`mapTo*`projection overload；
- String、Enum、ordinary Object endpoint生成schema-known reference Field stream；
- complete `@SomaValue` endpoint的callback element是generated `Value.View`，而`toList/toArray`
  materialize detached Value；
- nested Value endpoint继续使用stable logical path，不暴露flattened leaf编号；
- keyable endpoint实现`SomaKeyableField<R,V>`；float/double、含float/double leaf的Value与
  ordinary Object endpoint只实现`SomaField<R,V>`，因此不能传给`groupBy/on/and`。

## 11. Row pipeline grammar

Table/Stream/Selection/ReadStream按capability生成以下subset：

```java
Stream parallel(); // actual covariant return preserves Stream/Selection/ReadStream capability

Selection filter(SomaExpression<View> expression);
Selection filter(SomaPredicate<? super View> callback);

// Processor为当前Table每个schema-known Field endpoint生成concrete overload；例如：
TransportMinutesField.Stream mapToLong(TransportMinutesField field);
MachinePairField.Stream map(MachinePairField field);

<R> MappedStream<R> map(Function<? super View, ? extends R> mapper);
SomaIntStream mapToInt(SomaToIntFunction<? super View> mapper);
SomaLongStream mapToLong(SomaToLongFunction<? super View> mapper);
SomaDoubleStream mapToDouble(SomaToDoubleFunction<? super View> mapper);

Selection sorted(Comparator<? super View> comparator);
Selection sortedBy(SomaOrder<View> order);
Selection skip(long n);
Selection limit(long n);
Selection top(long n, SomaOrder<View> order);

long count();
boolean anyMatch(SomaPredicate<? super View> predicate);
boolean allMatch(SomaPredicate<? super View> predicate);
boolean noneMatch(SomaPredicate<? super View> predicate);
Optional<Xxx> findFirst();
void forEach(Consumer<? super View> action);
void forEachOrdered(Consumer<? super View> action);
List<Xxx> toList();
Xxx[] toArray();
String _explain();
```

Table source无`distinct`；Stream/Selection return type协变保持或终止lineage。ReadStream的
expression owner固定left View，且没有update/remove、relation kind或重新获得Selection的方法。

Exact row-lineage transition：

| Receiver | `parallel` | `filter/sorted/sortedBy/skip/limit/top` | `map/Field projection` |
|---|---|---|---|
| Table | `Stream` | `Selection` | projected stream |
| `Stream` | `Stream` | `Selection` | projected stream |
| `Selection` | `Selection` | `Selection` | projected stream |
| `ReadStream` | `ReadStream` | `ReadStream` | projected stream |

Table另有`selectAll() -> Selection`。只有`Selection`/direct `IndexSelection`拥有mutation terminal；
ReadStream不能通过任何intermediate重新获得Selection。

`parallel()`是idempotent execution-mode intermediate：可以在terminal前任意unconsumed
pipeline stage显式调用，并以covariant return保持当前lineage/capability；不存在`sequential()`，
一旦标记parallel就不能切回。它只记录mode，不创建pool、task或执行work。

Typed Field projection overload与lambda callback overload使用不同non-functional Field type，
Java 8 overload resolution必须无歧义。前者进入typed IR并保留schema-known materialization，
后者进入opaque callback barrier。把`View/Value.View/JoinPair`自身作为mapper result返回必须在
callback boundary以`CALLBACK_SCOPE_VIOLATION`拒绝；`fetch()`后的detached object合法。Runtime
不深扫描application container/closure/object graph；嵌套保存borrowed value仍是illegal usage，
并在后续可检测访问时失败。

## 12. Field、Mapped 与 primitive stream

Reference `MappedStream<R>`：

```java
MappedStream<R> parallel();
MappedStream<R> filter(SomaPredicate<? super R> predicate);
<U> MappedStream<U> map(Function<? super R, ? extends U> mapper);
SomaIntStream mapToInt(SomaToIntFunction<? super R> mapper);
SomaLongStream mapToLong(SomaToLongFunction<? super R> mapper);
SomaDoubleStream mapToDouble(SomaToDoubleFunction<? super R> mapper);
MappedStream<R> distinct();
MappedStream<R> sorted(Comparator<? super R> comparator);
MappedStream<R> top(long n, Comparator<? super R> comparator);
MappedStream<R> skip(long n);
MappedStream<R> limit(long n);
long count();
boolean anyMatch(SomaPredicate<? super R> predicate);
boolean allMatch(SomaPredicate<? super R> predicate);
boolean noneMatch(SomaPredicate<? super R> predicate);
Optional<R> findFirst();
Optional<R> min(Comparator<? super R> comparator);
Optional<R> max(Comparator<? super R> comparator);
void forEach(Consumer<? super R> action);
void forEachOrdered(Consumer<? super R> action);
List<R> toList();
<A> A[] toArray(Class<A> componentType);
String _explain();
```

不提供no-arg `Object[] toArray()`、`IntFunction<A[]>` overload或`map(Class, mapper)`。

`componentType`只用于validated `Array.newInstance(componentType, length)`分配正确runtime
component array；不进行Field/constructor/classpath/schema反射或对象扫描。

Primitive stream有八种schema Field family；mapped specialization至少覆盖int/long/double，
其他primitive Field保留exact primitive source/toArray。Numeric member按type出现，不boxing。

Representative mapped specialization：

```java
public interface SomaLongStream {
    SomaLongStream parallel();
    SomaLongStream filter(SomaLongPredicate predicate);
    SomaLongStream map(SomaLongUnaryOperator mapper);
    SomaIntStream mapToInt(SomaLongToIntFunction mapper);
    SomaDoubleStream mapToDouble(SomaLongToDoubleFunction mapper);
    SomaLongStream distinct();
    SomaLongStream sorted();
    SomaLongStream top(long n);
    SomaLongStream skip(long n);
    SomaLongStream limit(long n);
    long count();
    boolean anyMatch(SomaLongPredicate predicate);
    boolean allMatch(SomaLongPredicate predicate);
    boolean noneMatch(SomaLongPredicate predicate);
    OptionalLong findFirst();
    OptionalLong min();
    OptionalLong max();
    long sum();
    OptionalDouble average();
    SomaLongSummary summaryStatistics();
    void forEach(SomaLongConsumer action);
    void forEachOrdered(SomaLongConsumer action);
    long[] toArray();
    String _explain();
}
```

`SomaInt/DoubleStream`及schema-known boolean/byte/short/char/float Field streams按下表机械投影；
不存在的方法必须从Java type缺席，不以runtime unsupported代替。

Terminal return grammar：

| Element shape | `findFirst` | `min/max` | `sum` | `average` | `summaryStatistics` | `toArray` |
|---|---|---|---|---|---|---|
| boolean Field | `Optional<Boolean>` | absent | absent | absent | absent | `boolean[]` |
| byte/short/char/int | `OptionalInt` | `OptionalInt` | checked `long` | `OptionalDouble` | `SomaLongSummary` | exact primitive array |
| long | `OptionalLong` | `OptionalLong` | checked `long` | `OptionalDouble` | `SomaLongSummary` | `long[]` |
| float/double | `OptionalDouble` | `OptionalDouble` | canonical `double` | `OptionalDouble` | `SomaDoubleSummary` | exact primitive array |
| schema-known reference/Value Field | `Optional<V>` | `Optional<V>` only when natural order exists | absent | absent | absent | schema-known `V[]` |
| arbitrary mapped reference `R` | `Optional<R>` | only Comparator overload | absent | absent | absent | only `toArray(Class<A>)` |

Reference `Optional`在selected value为null时按Logical/Failure合同抛
`NULL_VALUE_UNSUPPORTED`。Empty summary的`count/min/max/sum/average`均为零；`count()==0`是
明确presence flag，不把零解释为真实min/max。Standalone empty `min/max/average`返回empty
Optional family。所有`List`都是新的、application-owned、structurally modifiable container；
修改它或其中detached Table/Value object不影响SOMA。Ordinary reference element仍保持同一Java
referent identity，不做deep copy。

## 13. Group signatures

```java
GroupedLongResult<MachineId> result = times
        .groupBy(times.machinePair.fromMachine)
        .sum(times.transportMinutes);
```

Shared/reference family：

```java
public interface GroupedLongResult<K> {
    long size();
    void forEach(ObjLongConsumer<? super K> consumer);
    List<GroupedLongEntry<K>> toList();
    GroupedLongEntry<K>[] toArray();
}

public interface GroupedLongEntry<K> {
    K key();
    long value();
}
```

`groupBy`只接受`SomaKeyableField`。Processor按key endpoint生成builder overload：reference/
Enum/Value key使用generic key family；boolean/byte/short/char/int/long key使用下述specialized
result/consumer/Entry family，不能退回per-group boxed key。

Shared primitive-key result使用以下exact token grammar：

```text
KeyToken     Boolean | Byte | Short | Char | Int | Long
ValueToken   Int | Long | Double | LongSummary | DoubleSummary

result       <KeyToken>Grouped<ValueToken>Result
entry        <KeyToken>Grouped<ValueToken>Entry
consumer     Soma<KeyToken><ValueToken>Consumer
```

例如`LongGroupedLongResult`、`LongGroupedLongEntry`与`SomaLongLongConsumer`。Processor只引用
实际aggregate所需的shared family，不为composition复制type。Reference/Enum/Value key继续使用
`Grouped<ValueToken>Result<K>`、`Grouped<ValueToken>Entry<K>`和标准/typed object-first consumer；
示例中的`GroupedLongResult<MachineId>`即该grammar。所有Result内部保持columnar/unboxed；只有
显式`toList/toArray`才创建Entry objects。
Ordinary Object Field、float/double Field在Table/Selection上没有`groupBy`可适用overload。

Builder按aggregate Field type机械生成：

| Aggregate | Result value family |
|---|---|
| `count()` | checked long |
| byte/short/char/int/long `sum(field)` | checked long |
| float/double `sum(field)` | canonical double |
| numeric `min/max(field)` | exact/widened primitive family matching第12节 |
| numeric `average(field)` | double |
| integer/floating `summaryStatistics(field)` | `SomaLongSummary` / `SomaDoubleSummary` family |

Generated method参数始终绑定同一row owner `R`；foreign Table Field不能通过Java type或runtime
owner validation。每次builder只允许一个terminal aggregate；没有varargs multi-aggregate、
dynamic record或`Map<Object,Object>`。Result是detached typed columnar carrier；specialized
`forEach`直接读取typed key/value columns，`toList/toArray`返回detached immutable Entry，
container本身可修改。

## 14. Join signatures

每个Table对same composition中每个不同generated Table type生成typed overload；不为自身Table
type生成Join/Cross overload：

```java
SomaJoinOnBuilder<
        TransportTimeTable.View,
        MachineStateTable.View,
        TransportTimeTable.ReadStream>
join(MachineStateTable other);
```

Shared builders：

```java
public interface SomaJoinOnBuilder<L, R, LS> {
    <V> SomaJoinCondition<L, R, LS> on(
            SomaKeyableField<L, V> left,
            SomaKeyableField<R, V> right);
}

public interface SomaPairStream<L, R> {
    SomaPairStream<L, R> parallel();
    SomaPairStream<L, R> filter(SomaRelationExpression expression);
    SomaPairStream<L, R> filter(
            SomaPredicate<? super JoinPair<L, R>> callback);
    <T> MappedStream<T> map(
            Function<? super JoinPair<L, R>, ? extends T> mapper);
    SomaIntStream mapToInt(SomaToIntFunction<? super JoinPair<L, R>> mapper);
    SomaLongStream mapToLong(SomaToLongFunction<? super JoinPair<L, R>> mapper);
    SomaDoubleStream mapToDouble(
            SomaToDoubleFunction<? super JoinPair<L, R>> mapper);
    long count();
    boolean anyMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    boolean allMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    boolean noneMatch(SomaPredicate<? super JoinPair<L, R>> predicate);
    void forEach(Consumer<? super JoinPair<L, R>> action);
    void forEachOrdered(Consumer<? super JoinPair<L, R>> action);
    String _explain();
    // no findFirst/direct Pair materialization
}

public interface SomaMatchedJoinStream<L, R> extends SomaPairStream<L, R> {
    @Override SomaMatchedJoinStream<L, R> parallel();
    @Override SomaMatchedJoinStream<L, R> filter(
            SomaRelationExpression expression);
    @Override SomaMatchedJoinStream<L, R> filter(
            SomaPredicate<? super JoinPair<L, R>> callback);

    <A, B> MappedStream<SomaTuple2<A, B>> select(
            SomaField<L, A> left,
            SomaField<R, B> right);
}

public interface SomaOuterJoinStream<L, R> extends SomaPairStream<L, R> {
    @Override SomaOuterJoinStream<L, R> parallel();
    @Override SomaOuterJoinStream<L, R> filter(
            SomaRelationExpression expression);
    @Override SomaOuterJoinStream<L, R> filter(
            SomaPredicate<? super JoinPair<L, R>> callback);
}

public interface SomaJoinCondition<L, R, LS>
        extends SomaMatchedJoinStream<L, R> {
    <V> SomaJoinCondition<L, R, LS> and(
            SomaKeyableField<L, V> left,
            SomaKeyableField<R, V> right);

    SomaMatchedJoinStream<L, R> inner();
    SomaOuterJoinStream<L, R> left();
    SomaOuterJoinStream<L, R> full();
    LS semi();
    LS anti();

    @Override SomaJoinCondition<L, R, LS> parallel();
}

public final class SomaTuple2<A, B> {
    public A first();
    public B second();
    // immutable detached carrier；constructor non-public
}
```

`on`默认Inner。`and`与kind只存在于`SomaJoinCondition`；第一个normal intermediate返回matched
stream，之后不能再改变condition/kind。`parallel()`是idempotent mode marker，并以covariant
return保留condition/matched/outer capability。`LS`机械绑定left generated `ReadStream`，因此
Semi/Anti在compile time丢失right/mutation capability。

`SomaPairStream`只固定Pair仍可安全表达的query terminal。它没有`findFirst/toList/toArray`，
也没有直接`sortedBy/top`：Pair本身是borrowed carrier，必须先`map/mapTo*/select`成detached
element，随后在mapped stream上排序、截断或materialize。Inner/Cross可以使用typed `select`；
Left/Full必须通过callback显式处理missing side。这样不会为Outer primitive投影制造null或零值
sentinel，也不会让Pair逃逸callback scope。

```java
public interface JoinPair<L, R> {
    boolean hasLeft();
    boolean hasRight();
    L left();
    R right();
}
```

Pair是borrowed callback view，不提供toList/toArray/findFirst。只有Inner/Cross的matched stream
提供typed `select`，因为Outer missing side不能安全投影成普通primitive/reference Tuple；
Left/Full必须通过`hasLeft/hasRight`与callback显式形成application-owned missing representation。
`select`返回detached `SomaTuple2<A,B>`；Value被materialize，ordinary reference保持identity。
Hot numeric path使用mapTo*。

`SomaTuple2`同样显式声明private constructor，由matched projection的trusted internal factory
创建；application不获得public/package-default constructor。

Cross Join使用独立`crossJoin(other, long maxOutputRows)`generated overload，返回
`SomaMatchedJoinStream`，不由missing `on`隐式产生。Equality/Cross的other Table都由same
composition/different-type overload保证；runtime仍验证same Group。V1没有relation alias或
self-Join/self-Cross。

## 15. Metadata carrier boundary

以下root type name进入V1 surface：

```text
SomaMetadata
GroupMetadata
TableMetadata
FieldMetadata
```

其稳定语义类别由Logical API Design拥有。Exact nested carrier、enum/list topology与每个getter
是唯一允许在implementation slice内通过Java 8 consumer/debug evidence完成admission的public
细节；在固定前必须满足：

1. 不泄漏physical mutable object或Class/reflection；
2. 不把Chunk/codec/posting等unstable diagnostic变成compatibility ABI；
3. 与`size/capacity/effective budget/managed bytes`唯一事实同源；
4. 经过API diff与independent consumer后才标记stable。

所有最终metadata carrier都必须immutable且显式private constructor；I7不能以Java implicit
public constructor作为“exact topology choice”。

这不是允许implementation改变metadata类别或语义。

## 16. Compatibility boundary

Pre-release阶段不保留失败草案alias。正式release后：

- annotation/shared public type与generated source grammar受source compatibility policy；
- processor/runtime exact version pairing；upgrade full regenerate/recompile；
- generated class binary compatibility不跨version承诺；
- internal package、`_explain()`text、physical diagnostic不稳定；
- metadata只有明确标记stable的member进入compatibility contract。

## 17. Explicit absence

Generated/shared API中不得出现：

- `stream()/parallelStream()`；
- `Record`命名或live Record result；
- public constructor for Group/Table/View/Editor/Stream/Field；
- `setParallelExecutor`、`setParallelism`、arbitrary ExecutorService；
- Table clear/trim/release、Batch/Loader；
- Field remove、Mapped mutation、Table distinct；
- no-arg mapped reference `toArray()`；
- Right/multi-way/non-equality Join；
- public Column/Chunk/locator/backend；
- runtime unsupported placeholder或predecessor alias。

## 18. Signature evidence Gate

Production evidence必须包含：

1. full generated source golden与`javap -v`；
2. independent Java 8 consumer positive/negative compile；
3. expression-vs-lambda overload、wrong owner与Semi right-side negatives；
4. constructor/access modifier与reserved collision；
5. 全部 primitive/reference/Value/Key/Index capability matrix；
6. Join builder inference与100+Table surface/classfile/IDE profile；
7. metadata admission/API diff；
8. processor/runtime version mismatch与full regeneration。
