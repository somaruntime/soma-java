# SOMA Java V1 逻辑层 API Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Generated hierarchy、direct source、Table operation、pipeline capability、View、
aggregate/Group/Join、materialization与advanced metadata/explain surface

最后审查日期：2026-08-03

## 1. 设计目标

用户通过一条主线完成工作：

```text
Soma / Group / Table / Field / Index
    -> direct reusable source or point operation
        -> lazy typed pipeline
            -> terminal
                -> detached result or Table-local atomic publication
```

API借鉴Java Stream naming，但source是SOMA Table/Field/Index/relation，且Selection可以更新
authoritative Table。Physical Column、Chunk、plan、row position与Executor不进入普通API。

Exact Java declaration由[Generated Signature](generated-api-signatures.md)拥有。

## 2. Generated hierarchy

```text
Soma
    -> SomaGroup
        -> XxxTable
            -> logical Field
                -> nested Field
```

候选普通入口已经正式化：

```java
TransportTimeTable times = Soma.transportTimeTable();

SomaGroup active = Soma.createGroup();
TransportTimeTable activeTimes = active.transportTimeTable();

assert times == Soma.defaultGroup().transportTimeTable();
```

同一Group accessor重复调用返回同一instance；不同Group隔离。没有public Group/Table
constructor或manual release。

## 3. Direct source

Table、Field与IndexSelection直接是reusable source；普通路径不提供`stream()`或
`parallelStream()`。Sequential默认，parallel必须在source/relation pipeline入口显式选择：

```java
long count = times
        .filter(times.transportMinutes.gt(30L))
        .count();

long total = times.transportMinutes
        .filter(value -> value > 30L)
        .sum();

long parallel = times
        .parallel()
        .filter(times.transportMinutes.gt(30L))
        .mapToLong(times.transportMinutes)
        .sum();
```

Source可重复使用；intermediate产生的linked pipeline lazy、one-shot，不能分叉或重复terminal。

## 4. Table direct operation

每张generated Table提供：

```java
long size();
long capacity();
void reserve(long expectedRows);
void add(Xxx value);
```

Keyed Table额外提供：

```java
Optional<Xxx> find(Key key);
Xxx get(Key key);
UpdateResult update(Key key, Consumer<? super Editor> updater);
RemoveResult remove(Key key);
```

合同：

- keyless Table不生成point methods；
- `find` missing为空；`get` missing为structured `MISSING_KEY`；
- `add`复制carrier leaf/reference，不保存object；duplicate Key失败；
- point update只使用Key + Editor，不接受replacement object；Key没有setter；
- point update missing返回`matched == 0 && changed == 0`，不调用callback且不改变version；
- point remove missing返回`removed == 0`；
- `reserve(n <= capacity)`为no-op；negative失败；
- capacity在Table/Group生命周期内只随growth增加；remove不隐式shrink；
- V1无clear/trim/release/Loader/Batch。

Repeated add每次独立atomic；第N次失败不回滚此前成功add。高吞吐Loader只有profile触发新的
surface admission后才可进入。

## 5. Stream、Selection 与 lineage

`XxxTable.Stream`表示携带Table row lineage的query pipeline，本身没有mutation terminal。
`XxxTable.Selection`表示显式形成的Table-local row selection，并继承Stream query operation。

产生Selection：

- `table.selectAll()`；
- Table/Stream的`filter/sorted/sortedBy/skip/limit/top`；
- IndexSelection；
- 对Selection继续执行保留lineage的operation。

只有Selection生成：

```java
UpdateResult update(Consumer<? super Editor> updater);
RemoveResult remove();
```

```java
table.filter(...).update(...);       // legal
table.byMachineId(id).remove();      // legal
table.selectAll().remove();          // legal and explicit

table.remove();                      // absent
table.map(...).update(...);          // absent
table.field.remove();                // absent
table.join(other).on(...).remove();  // absent
```

`selectAll()`是optimizer-visible logical node，不创建恒真callback。Filter/order/slice operation
只选择或重排同一Table的row identity，因此保留mutation lineage；`map`、Field projection、Join、
Group与materialization终止mutation lineage。

## 6. View、Editor 与 Value View

Table callback使用borrowed `XxxTable.View`；mutation callback使用`Editor`。完整Value Field
callback使用borrowed `V.View`。

- View/Editor保存owner、execution token、participant/thread与callback epoch；
- callback结束、foreign Table/Group/thread或terminal结束后访问为
  `CALLBACK_SCOPE_VIOLATION`；
- sequential每participant复用O(1)个View，parallel总量O(P)；
- Java 8不能保证检测同participant后续callback中的旧alias；该alias escape明确unsupported；
- 稳定保存必须`fetch()`为detached application object；
- View identity/equality不是row identity；application不得保存、返回或跨线程使用。

Runtime直接拒绝mapper返回值本身是View/Value.View/JoinPair；它不反射或深扫描application
container、lambda closure或ordinary Object graph。把borrowed value嵌入这些对象同样违反合同，
后续可检测访问为`CALLBACK_SCOPE_VIOLATION`；唯一稳定路径仍是callback内`fetch()`。

Editor只公开当前staged value与non-Key setter；validation/publish完成前不改变Table。

## 7. Field endpoint

Nested logical endpoint全部保留：

```java
times.machinePair
times.machinePair.fromMachine
times.machinePair.fromMachine.value
```

完整Value与leaf endpoint都是logical Field，不是physical Column。Field直接是query source，
没有update/remove terminal。

Field-first从whole Table domain开始；Selection-first projection继承已有Selection。二者lowering到
同一logical Field access，不复制数据。

## 8. Field 与 Relation expression

Field比较产生immutable typed expression：

```text
eq / ne
lt / le / gt / ge
between / in
isNull / isNotNull
and / or / not
asc / desc
```

Capability由type决定：nullable reference才有`isNull`，natural-order Field才有order/
between/asc/desc，intrinsic-equality Field才有in/distinct。Ordinary Object只有null test与
callback/Comparator。

只依赖literal的argument validation在expression construction立即执行：null `eq/ne`、逆
`between`、null varargs/element等直接以QUERY/`INVALID_ARGUMENT`失败，不freeze configuration、
不取得Group guard。`in(...)`复制并按正式equality去重literal array，使expression不受调用方随后
修改原array影响；空array形成immutable constant FALSE。Owner/dependency/current-state/resource
validation仍在pipeline composition或terminal相应阶段完成。

Expression/pipeline node及成功返回时已包含的literal snapshot是application-retained detached Java
object，不属于Group retained或terminal temporary lease；clone/normalize前仍必须检查array
length/byte arithmetic，失败不发布任何Table state，真实OOME按JVM `Error`边界传播。Terminal为
Index/hash/scan形成的operation scratch另行进入managed budget。`in`只作为finite literal
convenience；大规模动态membership应建普通Table + Index/Equality Join，不能用超大literal
绕过relation/resource模型。

```java
pipeline.filter(table.enabled.eq(true));        // typed IR
pipeline.filter(view -> applicationCheck(view)); // opaque callback
```

连续typed filter是顶层AND canonical style：

```java
.filter(A)
.filter(B)
```

`A.and(B)`用于括号/复杂表达式；`or/not`保留。`eq(null)`无效；null selection使用
`isNull()`。Typed expression可重排；callback是optimization barrier。

Behavioral callback contract借鉴Java Stream：predicate、mapper、match callback、Comparator与
arbitrary mapped reference `equals/hashCode`必须non-interfering，并且其结果只由参数与application
稳定事实决定；Comparator还必须满足一致的total-order contract，equals/hashCode必须满足Java
equivalence/hash consistency。Full-traversal parallel stage不保证这些callback的wall-clock顺序；含opaque
callback的short-circuit segment则按canonical caller-thread schedule执行，不进行application
callback speculation。所有opaque Comparator-based sort/top/min/max也使用canonical caller-thread
schedule。显式side effect使用`forEach/forEachOrdered`；
Table state修改只使用Editor mutation terminal。违反non-interference时，logical equivalence不受
支持，但任何已发生的Table publication仍必须遵守zero-partial-state。

边界语义：

- `between(lower, upper)`两端inclusive；`lower > upper`为`INVALID_ARGUMENT`；
- primitive/reference `in()`的空参数匹配nothing；重复literal按集合语义去重，不改变order；
- nullable reference的`in(...)`不接受null element，null selection仍只用`isNull()`；
- nullable natural-order Field的ascending order是null-first，descending是null-last；
- `field.asc().then(other.asc())`形成lexicographic tie-break，owner必须属于同一row/relation
  element；
- empty stream的`anyMatch=false`、`allMatch=true`、`noneMatch=true`，与Java Stream一致。

## 9. Query operation set

Formal naming：

```text
filter
map / mapToInt / mapToLong / mapToDouble
distinct
sorted / sortedBy
skip / limit / top
count / anyMatch / allMatch / noneMatch / findFirst
min / max / sum / average / summaryStatistics
forEach / forEachOrdered
toList / toArray
groupBy
join / crossJoin
```

Rules：

- Table source无`distinct()`；每条published membership独立；需要value distinct先Field/map；
- `filter/map`保序，`distinct`保留first encounter，`sorted`stable；
- mapped reference `distinct`按`Objects.equals/hashCode`，包括至多保留一个null；
- arbitrary mapped reference `distinct`的application `equals/hashCode`按canonical caller-thread
  encounter schedule调用，并作为完整callback boundary适用non-interference、reentrancy、failure
  wrapping与provenance；schema-known String/Enum/Value distinct才可使用specialized parallel path；
- `skip/limit/top`使用long；negative为`INVALID_ARGUMENT`；
- `skip(n)`丢弃前`min(n,count)`个，`limit(n)`保留前`min(n,count)`个；`n==0`分别为identity/
  empty；
- `top(n, order)`语义等价于stable `sortedBy(order).limit(n)`；`n==0`为空，`n>=count`返回全部
  stable-sorted membership；optimizer可以使用bounded heap但必须保持该语义与failure schedule；
- `findFirst/min/max`tie取canonical first；业务依赖first必须显式sort；
- sequential `forEach`按encounter order；parallel `forEach`不保证side-effect order；
- parallel `forEach`的action可在caller/workers执行；某个action失败时，已经开始的其他range可能已
  产生外部side effect，SOMA只cancel/quiesce而不声称回滚；
- `forEachOrdered`把final action固定在calling thread按encounter order串行交付；上游可并行，
  buffer/merge peak先admit；ordinal k action失败后不再向k之后交付；
- predicate、mapper、match与forEach这类element callback对每次logical element/stage invocation
  至多调用一次；successful full traversal对每个到达该stage的element恰好一次，short-circuit或
  failure只覆盖其canonical reached prefix；
- Comparator以及arbitrary mapped reference distinct触发的application `equals/hashCode`是
  comparison/hash callback，不适用“每element一次”；它们可以按canonical algorithm多次调用，
  application不得依赖调用次数或把副作用写入其中；
- 含behavioral callback的short-circuit segment在caller thread按canonical order运行；typed-only
  short-circuit可以内部speculate，但不执行application callback；
- `peek`不存在；diagnostic使用`_explain()`，side effect使用terminal；
- generic `reduce/collect/flatMap`、window、approximate、prepared query不存在。

Capability matrix：

| Capability | Table/Selection | Field | Mapped | Join | Group result |
|---|---|---|---|---|---|
| filter/map/mapTo* | 是 | 是 | 是 | 是 | 否 |
| distinct | membership缺席 | eligible | 是 | Pair缺席；projection后 | 否 |
| sort/skip/limit/top | 是 | eligible | eligible | projection后 | 否 |
| scalar/match | 是 | 是 | 是 | count/match；无findFirst | aggregate决定 |
| materialize | 是 | 是 | 是 | projection后 | 是 |
| update/remove | Selection only | 否 | 否 | 否 | 否 |
| groupBy | Table/Selection | 否 | 否 | 否 | terminal |
| join | Table only | 否 | 否 | binary only | 否 |

Join Pair是borrowed callback carrier，因此不直接排序、截断、`findFirst`或materialize；先用
`map/mapTo*/select`形成detached element，再使用Mapped/primitive stream能力。

## 10. Numeric contract

- byte/short/int sum返回checked long；long sum返回checked long；
- integer accumulator只按最终result range判断overflow；long使用signed 128-bit two-limb；
- float/double sum与average返回double；
- sequential/parallel使用相同canonical 1024-element block + pairwise strictfp tree；
- integer overflow为`ARITHMETIC_OVERFLOW`；callback `Math.addExact`为`CALLBACK_FAILED`；
- floating遵循Java IEEE-754：NaN/Infinity是normal numeric value，aggregate overflow可产生signed
  Infinity而不是`ARITHMETIC_OVERFLOW`；min/max/order使用`Float.compare/Double.compare` total
  order，不使用会改变NaN语义的`Math.min/max`；
- summary至少包含long count、typed min/max、sum与double average。

## 11. Materialization

所有materialized result detached，不保留pipeline、View、row position或backing storage。

- Table/Selection `toArray()`返回generated detached `Xxx[]`；
- schema-known reference Field `toArray()`返回typed reference array；
- primitive Field/mapped stream返回primitive array；
- arbitrary reference `MappedStream<R>`提供`toList()`与`<A> A[] toArray(Class<A>)`；
- reference mapped stream没有no-arg `Object[] toArray()`或array-factory overload；
- ordinary无budget `toList()/toArray()`保留；若结果超过Java container/array或managed budget，
  分配前`RESOURCE_LIMIT_EXCEEDED`。

`toList()`返回新的、application-owned、structurally modifiable container。Table/Value element
已经detached；ordinary Object reference保持原referent identity，不做deep copy；String只保证
content equality，dictionary可改变reference identity；Enum保持constant identity。修改container
或detached element不改变SOMA state。

Reference mapped value可null。若`findFirst/min/max`最终selected reference为null，因为Java 8
`Optional`不能表达nullable present，返回`NULL_VALUE_UNSUPPORTED`。

## 12. GroupBy

```java
GroupedLongResult<MachineId> totals = times
        .groupBy(times.machinePair.fromMachine)
        .sum(times.transportMinutes);
```

- group key必须是keyable Field；nullable String/Enum key的null形成正常group；float/double及包含
  float/double leaf的Value虽有明确filter/distinct equality，但不是Group key；
- group order是key在bound snapshot首次出现order；
- 一个terminal只产生一个aggregate；无dynamic multi-aggregate varargs；
- terminal为`count`及numeric Field的`sum/min/max/average/summaryStatistics`；
- result detached、typed、read-only，不返回`Map<Object,Object>`；
- reference key使用typed Grouped result；primitive key有specialized family与unboxed consumer；
- `toList/toArray`返回typed Entry carrier。

## 13. Equality Join

```java
times.join(states)
        .on(times.machinePair.fromMachine, states.machineId)
        .inner()
        .filter(states.enabled.eq(true));
```

- `on(leftField,rightField)`默认Inner；`.and(...)`增加equality component；
- kind：Inner/Left/Full/Semi/Anti；无Right convenience，交换左右使用Left；
- only same composition、different generated Table type生成overload；runtime只允许same Group；
- V1没有relation alias或self-Join/self-Cross；同一Group每种Table type只有一个instance，不能用
  同type的隐式左右角色制造歧义；
- equality component支持boolean/byte/short/char/int/long、String、Enum与recursively keyable
  Value；float/double及包含float/double leaf的Value不进入Join；null永不match；
- “null永不match”指整个nullable String/Enum Field value为null；outer non-null Value仍按完整
  structural equality比较，其中允许的reference leaf null是Value内容的一部分；
- Ordinary Object Field在Java type层不是equality endpoint，不能作为GroupBy key、Join
  component、Key或Index；
- repeated value产生完整Cartesian matches，不去重；
- Inner/Left按left order，同一left的right match按right order；Full再输出unmatched right；
- Semi对存在至少一个right match的每个left membership恰好输出一次；Anti对不存在任何right
  match的每个left membership恰好输出一次；两者保持left order，right duplicate不放大left；
  nullable Join component因null永不match而使该left进入Anti而非Semi；
- Semi/Anti返回左侧query-only `ReadStream`，无Pair/missing/mutation capability；
- Inner/Left/Full callback接收borrowed `JoinPair<L,R>`，通过`hasLeft/hasRight/left/right`；
- missing side accessor为`MISSING_RELATION_SIDE`；
- Pair不能materialize；先map/select成detached value/Tuple；
- `select(leftField,rightField)`只存在于Inner/Cross matched stream；Outer必须先用callback与
  `hasLeft/hasRight`形成显式missing representation；
- Join result不继续join；V1没有multi-way/range/as-of/interval/arbitrary non-equality Join。

Join filter沿用typed `filter`，不增加`filterLeft/filterRight`。Optimizer负责安全pushdown与
Index substitution；callback永不下推。Outer missing使用TRUE/FALSE/MISSING relation truth，
filter只保留TRUE；missing不是Field null。

## 14. Cross Join

Cross必须显式并携带hard result budget：

```java
table.crossJoin(other, maxOutputRows);
```

Missing condition不能隐式Cross。Checked product超过budget/long/resource时在callback/
materialization前失败。

## 15. Metadata 与 Explain

`_`表示semi-hidden advanced/debug surface。

`Soma / SomaGroup / Table / Field`四级都提供`_metadata()`；`_`表示它是面向高级用户和调试的
semi-hidden surface，不是普通业务路径。Field层级覆盖完整Value Field及每个nested logical
Field endpoint，不覆盖physical Column。

`_metadata()`返回immutable detached snapshot，只固定信息类别：

- composition/schema与logical Table/Field/Key/Index；
- configuration freeze、effective memory budget、AUTO/OFF；
- Table size/capacity/managed-byte estimate；
- Table/Field的plain-equivalent payload estimate、current payload representation bytes、savings与
  是否存在encoded representation；
- Group default identity、global retained/temporary bytes。

Field metadata至少稳定表达logical path、logical type/nullability、Key/Index role与可用能力类别；
完整/nested Field可以聚合或投影上述compression summary，但不得返回flattened leaf编号、array、
codec name/token、per-Chunk layout或mutable runtime handle。Plain-equivalent只估算SOMA-owned
payload representation，不包含ordinary referent body。Exact carrier/getter topology仍按下述I7
evidence admission固定。

Freeze前的global metadata明确表达UNFROZEN，effective budget为absent；读取它不计算policy、
freeze configuration或创建default Group。Exact absence carrier在I7经consumer evidence固定。

Exact carrier在implementation阶段经Java 8 consumer与compatibility evidence固定。Metadata不返回
raw Executor、Chunk、array、address、Class/reflection或mutable statistics。

`_explain()`是消费one-shot pipeline的diagnostic terminal，取得Group guard并完成planning，
但不运行callback/data kernel；返回human-readable text，展示logical stages、predicate
dependency、pushdown/residual、callback barrier、Index/Join/codec choice、order与peak estimate。
Text不稳定，application不得parse驱动业务。

## 16. Explicit absence

API不包含：direct clear/trim/release、Batch/Loader、Field remove、Mapped mutation、Table
distinct、Pair materialization、Right/multi-way/non-equality Join、generic reduce/collect/flatMap、
peek、async/Future、per-stream Executor、public Column/Chunk/row identity、runtime schema或
compatibility alias。

## 17. Evidence Gate

Implementation必须证明：

- direct source与Stream/Selection/ReadStream compile narrowing；
- typed expression/callback overload与wrong-owner negatives；
- View/Editor O(1)/O(P)、scope failure与fetch；
- operation/capability matrix的positive/negative generated source；
- materialization type/null/resource boundary；
- Group/Join kind/null/duplicate/order/cardinality；
- numeric sequential/parallel bit/equality；
- three reference journeys只使用public surface。
