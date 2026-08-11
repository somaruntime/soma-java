# SOMA Java V1 数据模型与存储 Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Group/Table identity、logical Field、authoritative StateRoot/Chunk、leaf/null、Key/Index、
capacity/order、compression、reference ownership与future backend seam

最后审查日期：2026-08-03

## 1. 设计目标

SOMA把schema-known mutable Tables保存为checked 32位结构域、chunked、data-oriented authoritative
state，同时保持用户面对logical Table/Field/Value。Storage不拥有用户operation naming、
optimizer rewrite、parallel scheduling或failure presentation。

## 2. Group 与 Table identity

```text
Soma
    -> SomaGroup
        -> each generated Table type exactly once
```

- 每个Group对composition中每种Table type恰好一个instance；
- repeated accessor返回同一instance；不同Group状态完全隔离；
- generated accessor不取得Group operation guard，因此首次/重复并发访问也必须安全发布并返回
  同一instance；可以eager构造或使用per-type CAS/lazy holder，但不能形成losing live Table；
- default Group是每composition/ClassLoader singleton；
- `Soma.xxxTable() == Soma.defaultGroup().xxxTable()`；
- `Soma.createGroup()`创建显式Group；没有public Group/Table constructor；
- Group/Table依赖Java reachability与GC，没有close/release/AutoCloseable；
- default Group是ClassLoader-lifetime high-water state；需要整体回收或双缓存时使用显式Group；
- runtime不使用global live-Group registry、后台线程或永久ThreadLocal意外保留Group。

显式Group的retained-byte accounting使用Java 8 `PhantomReference + ReferenceQueue`：global
manager只持有不反向引用Group/Table的accounting token，检测到Group不可达后exactly-once释放
retained reservation。Queue在operation admission、configuration与global metadata读取时同步
drain；不建立cleaner/background thread。GC与queue delivery不承诺即时，因此application不能把
“最后一个强引用消失”当成同步release；需要可预测复用时应保留并重用显式Group。

## 3. Authoritative StateRoot

每张Table只通过一个atomic current state descriptor发布live state：

```text
StateRoot
    PublishedHeader(size, capacity, stateVersion)
    paged ChunkDirectory
        Chunk 0..N
            primitive/reference leaf representations
            null/encoding statistics
    optional Key sidecar
    zero or more Index sidecars
    managed-byte accounting
```

- `size/capacity`使用non-negative checked `int`；`stateVersion`使用checked `long`；
- read/query operation binding后的logical state在terminal/quiescence期间稳定；
- root swap或non-throwing final descriptor/header publish是唯一可见线性化点；
- detached object、View/Editor、logical plan、callback、scratch与temporary result不是
  authoritative state；
- `stateVersion`是internal currentness，不进入ordinary API。

这里的“root”是authoritative facts的原子composition，不要求所有物理array在整个Table生命期
永久immutable。Mutation publication有两种等价mechanism：

1. structural/large mutation构造独立candidate root，最后atomic swap；
2. bounded point/small mutation在exclusive Group guard下，先完成全部可能抛出的callback、
   validation、allocation、hash/codec与journal准备，再执行经证明non-throwing的bounded physical
   writes，最后atomic publish新的immutable header/statistics/accounting descriptor。

普通operation不能在commit window并发读取同一Group；lock-free metadata只读取上一次完整发布
的detached header projection，不读取正在修改的payload。任何operation都不能观察partial
payload/sidecar组合。若某个mechanism在final writes后仍可能产生可恢复failure，它不合法。

## 4. Chunk geometry

- Table logical position、size、capacity与raw locator使用checked `int`；`-1`只作internal
  missing/end sentinel，最大合法locator为`Integer.MAX_VALUE - 1`；
- Stream/Relation/Group count/cardinality、memory byte累计与stateVersion使用checked `long`；
- Chunk ordinal/local offset与directory range使用`int`；directory仍分页，避免单个reference array
  成为更早的物理上限，并为Chunk级representation与未来backend保留seam；
- 同一Table所有Field的同一Chunk具有相同row span，logical row不跨Chunk；
- generated facade/IR不保存Java array identity；kernel每Chunk dispatch，不做per-element
  virtual backend call。

建议初值依据plain row width把single Chunk payload目标设为约2 MiB，row count取
`[4096,65536]`中的2次幂。该值是profile-tuned internal mechanism，不是public config或
compatibility contract；internal tiny-Chunk test必须可强制跨Chunk路径。

## 5. Logical Field 与 leaf storage

```text
Table membership selection
    -> logical Field/Value projection
        -> one or more physical leaf representations
```

只有两类authoritative leaf：

1. exact primitive leaves；
2. ordinary Java reference leaves。

`@SomaValue`递归flatten为leaf，但仍是一个logical Field。Public API不暴露leaf index、Column、
array、Chunk或encoding token。

| Logical type | Physical baseline | Null |
|---|---|---|
| primitive | exact primitive leaf | 不可null，默认零值 |
| String | reference或transparent dictionary leaf | nullable |
| Enum | reference/ordinal representation | nullable |
| `@SomaValue` | recursively flattened leaves | outer/nested non-null；eligible reference leaf nullable |
| ordinary Object/temporal | reference leaf | nullable |

## 6. Equality、order 与 null

- primitive按exact type contract；
- float/double使用wrapper canonical equality与`Float/Double.compare` natural order；
- String按content且不承诺保存input reference identity；dictionary/rebuild可返回任意content-equal
  String referent；Enum按constant identity/declaration order；
- Value按全部leaf structural equality/hash；
- ordinary Object没有SOMA intrinsic equality/order；
- relation missing side不是Field null，二者合同分离；
- reference mapped value可以null，但Key/Value outer按Schema合同non-null。

详细operation eligibility由Logical/Signature Design拥有。

## 7. Key contract

- 每Table `0..1` direct Key；keyless合法；
- Key是stable business identity，zero primitive合法且不作sentinel；
- reference/Value Key non-null；float/double、包含float/double leaf的Value与ordinary Object不可
  作Key；
- Key发布后完全immutable，Editor不生成setter；
- 修改Key必须remove后add；没有rekey；
- duplicate add在publication前稳定失败；
- Key point access为expected O(1)，不生成`byKey(...).stream()`。

Physical baseline是typed sharded open-addressed hash；每个live Key slot内联唯一raw `int` locator，
zero locator是正常值而不是sentinel。Remove立即删除Key mapping，packed remove在同一commit把tail
mapping从`T`调整到`R`。Hash mixing/load factor/shard count是versioned internal mechanism。

## 8. Index contract

- `0..N` non-unique exact-match Index；
- Index声明在direct logical Field；
- Index返回`0..N` selection并按bound StateRoot canonical order规范化；
- repeated value不去重；nullable String/Enum Index有normal null bucket；
- Index不提供range/prefix/tuple/secondary unique；
- ordinary Object、float/double及包含float/double leaf的Value不可Index；
- Index memory计入managed budget，metadata可观察其basic cardinality与memory cost。

Physical baseline是typed sharded exact-value directory。每个exact Bucket只有一套canonical
membership：singleton直接内联一个`int` locator，multi使用严格升序、无duplicate的一个`int[]`；
二者互斥，不维护per-record next link、reverse Index或第二套truth。Point add/update/remove即时只维护
受影响Bucket并与payload一次publish；Selection mutation从最终candidate payload一次重建全部sidecar。
Index表达logical value而不是compression token。

## 9. 关系 Table

SOMA不提供ChildTable。1:M使用many-side Table保存one-side ID；N:M使用composition Table保存
两端ID；按真实访问方向建立Index。

```text
Job(jobId)
Machine(machineId)
ProcessingOption(jobId indexed, machineId indexed, processingMinutes)
```

Storage不验证endpoint存在、不cascade、不提供cross-Table transaction。Application拥有引用
完整性、mutation顺序、补偿与业务lifecycle。Equality Join读取同一Group中多个Table，但不
改变任何Table ownership。

## 10. Capacity 与 growth

- initial accessor不分配payload；首次positive reserve/add才materialize Chunk；
- first add可把schema defaultCapacity作为preferred target，但hint可向下收缩到本次required
  whole-Chunk capacity；explicit reserve target不可静默收缩；
- `capacity()`是不再次growth可容纳的logical record数；
- `reserve(n)`成功保证`capacity >= n`且size/order不变；
- `n <= capacity`为no-op，negative为`INVALID_ARGUMENT`；
- growth按whole Chunk，结构capacity使用checked `int`，所有byte/cumulative arithmetic先widen到
  checked `long`；
- candidate directory/chunk/sidecar全部成功后一次root swap；
- V1没有trim/shrink；payload capacity在Table/Group生命周期内单调不减，structural remove不释放
  尾部Chunk。它必须清空失去logical reachability的reference slot；Key/Index/compression等derived
  sidecar可按new state释放不再需要的internal bytes，但不能借此改变payload capacity。

建议growth target为`max(required, old + old/2)`后向上取整到Chunk boundary；它是internal
mechanism。请求超过`Integer.MAX_VALUE` Table结构上限、managed budget、locator/directory或Java
array representation时以`RESOURCE_LIMIT_EXCEEDED`在publication前fail closed；合法累计
`long`算术溢出使用`ARITHMETIC_OVERFLOW`。

## 11. Canonical encounter order

Current root的internal order是Chunk ordinal + live local slot order：

- add append到末尾；update不改变当前order；
- structural remove使用预先计算的deterministic dense compaction，把末尾survivor填入最早hole；
- 同一bound snapshot上的point/sequential/parallel路径得到相同order；
- structural mutation后order可以改变；Table不承诺insertion/business order；
- IndexSelection是bound order的ordered subsequence；
- 依赖first、tie或发布顺序的业务必须显式sort。

Compaction mapping在authoritative write前冻结；payload、Key、Index与compression overlay重放
同一mapping。Public API不暴露slot、locator、tombstone或order key。

## 12. Reference ownership

Reference leaf保存普通Java reference，读取时是普通reference read；SOMA不复制、不冻结、
深扫描referent。Referent内部mutation不改变Table stateVersion/Index；application维护referent
不变性与线程安全。Remove/root replacement必须清空unreachable reference slot，防止retention。

这一referent-identity合同只属于ordinary Object/reference payload；String是schema-known content
type，transparent dictionary允许canonicalize相同content，application不得依赖String `==`。Enum
始终返回对应constant identity。

Editor/no-op detection对ordinary Object payload只比较reference identity `==`；SOMA不调用其
`equals/hashCode`推断Table mutation。不同referent是logical change，同一referent的内部变化仍是
application side effect。

SOMA-owned structural/index/compression bytes计入budget；referent对象本身与application长期
持有的detached result不计入。

String PLAIN representation保留的input String对象及其内部content body也按external referent处理，
不做不可靠deep-size accounting；若dictionary codec复制content到SOMA-owned byte/char structure，
该复制结构全部计入managed budget。Metadata的payload estimate只声明SOMA-owned representation
范围，不能投影为JVM total retained heap。

## 13. Compression representation

V1 compression policy默认`AUTO`，用户可显式`OFF`，不直接指定codec。AUTO表示允许runtime
按收益选择表示，不保证一定压缩。

Chunk leaf representation seam至少允许：

```text
PLAIN
BIT_PACKED / FRAME_OF_REFERENCE
DELTA
RLE
DICTIONARY
PLAIN_OR_ENCODED + SPARSE_OVERLAY
```

合同：

- active tail/hot Chunk默认PLAIN；full/sealed Chunk在同步operation boundary评估AUTO；
- codec dispatch每Chunk一次；ordinary Object reference只PLAIN；Value按leaf独立表示；
- encoded Chunk sparse update进入bounded overlay；超过internal threshold时在candidate中rebuild；
- add/update/remove只处理本次触及、刚sealed或因本次mutation失效的Chunk，不触发无界whole-
  Table recompression；
- selection mutation可以处理全部affected Chunk，但执行前完成work/resource admission；
- Index表达logical value，与overlay/compression一起atomic publish；
- 无background compressor、隐式线程或async rewrite；
- AUTO可因收益、update rate、Index或peak memory选择PLAIN；
- compression不能改变null、equality、order、callback或failure；
- old + candidate + scratch peak必须纳入budget。

Codec、threshold、sampling与overlay density是profile-driven internal choices；forced codec
correctness与AUTO cost-model必须分别验证。

Last-published compression statistics向metadata提供plain-equivalent payload bytes、current
representation bytes、savings与是否存在encoded representation；它们与managed accounting同源，
但不含ordinary referent body。Codec name、per-Chunk token/layout与threshold不是stable metadata
ABI，只能进入不稳定`_explain()`。

## 14. Future backend seam

V1只有on-heap implementation。Off-heap/mmap没有public configuration、backend interface或
qualification claim。

扩展seam是`Chunk representation + chunk-level specialized kernel + StateRoot directory`。
Generated API、logical IR、Key equality、Result与failure不依赖Java array identity。未来
backend必须单独解决Java 8 lifecycle、cleaner、failure、安全、serialization与performance；
ordinary Object reference不能假装可直接mmap/persist。

## 15. Storage invariants

1. Published header、payload、Key、Index、compression与managed accounting属于同一atomic
   logical state；
2. bound read state在operation期间稳定；mutation只在exclusive final commit窗口修改并一次发布；
3. failure不改变current root/version；
4. no-op mutation不改变version；
5. stale View/locator不能访问新root；
6. all growth/cardinality/byte arithmetic checked；
7. no public array/Chunk/locator identity；
8. reference slot在失去logical reachability后清空；
9. compression/backend选择不改变logical value；
10. Table没有稳定业务顺序。
11. global accounting token不强引用Group/Table，Group GC后retained bytes最终exactly-once释放。
12. payload capacity除growth外不变；remove不隐式shrink。

## 16. Evidence Gate

Implementation必须覆盖：

- 全部 type/null/flatten/unflatten/detached materialization；
- tiny Chunk cross-boundary、million real与near-int/long virtual arithmetic；
- reserve/growth/remove/compaction/GC retention；
- explicit Group reachability、ReferenceQueue accounting release、无strong-retention/double-release；
- Key/Index collision、duplicate、null、move/rebuild与ordered selection；
- primitive no-boxing hot path与structural byte accounting；
- forced codec、AUTO choice、overlay/Index interaction与peak budget；
- backend seam不泄漏array identity；
- no release/trim/ChildTable/secondary unique/public Column surface。
