# SOMA 32 位结构域与即时增量 Key/Index 维护专题设计

类型：`Bounded Governance Candidate Design`

状态：`ACTIVE / CANDIDATE_B / PRODUCT_OWNER_APPROVED / S1-S3_IMPLEMENTED_AND_QUALIFIED / S4_ACTIVE`

日期：2026-08-11

Owner：本次 Key/Index mutation 治理的当前候选设计、实施边界与验收路线

> 本文是当前 bounded Temporary 中唯一 active candidate，并已获得 Product Owner 实施设计批准。
> SOMA 采用“32 位结构域、64 位累计域、Schema-defined 业务数值域”；Key 与 secondary Index 的
> logical membership 在每次 mutation terminal 内立即维护；第一版 Bucket 使用升序 `int[]`。
> 此前延迟方案已冻结为
> [`CANDIDATE-A-DELAYED-STRUCTURAL-MAINTENANCE.md`](CANDIDATE-A-DELAYED-STRUCTURAL-MAINTENANCE.md)，
> 不再授权 implementation。
>
> 本文仍不是项目正式 Blueprint/Design、executable fact 或 Conformance 结论。它只授权按本文边界
> 开始实施；只有完成正式 Owner delta、实现、正确性与性能 qualification、Conformance 和
> replacement closure 后，稳定结论才能晋升。

后续 frontend-neutral Logical IR 治理继续由
[`FUTURE-LOGICAL-IR-GOVERNANCE.md`](FUTURE-LOGICAL-IR-GOVERNANCE.md)排队拥有，本专题不预建
第二套 IR、planner 或 public physical API。

## 1. 治理意图

100,000 工序 FJSP reference journey 暴露了 mutation-heavy Table 的 sidecar 成本。旧实现曾让
point update/remove 重建完整 Key/Index；dirty worktree 中的立即 linked-posting maintenance 已把
journey 从无法在数分钟内完成推进到约 44–45 秒，但低基数大 Bucket 的 predecessor traversal 仍是
主要热点。

本专题最初选择“延迟失效 + dirty query recheck + threshold cleanup”。继续审查后，Product Owner
重新识别到更简单的事实：

```text
Key
    logical value -> 0..1 int locator

Secondary Index
    logical value -> 0..N int locators
```

只要使用适合 `int locator` 的 primitive structure，普通 mutation 可以直接维护受影响的 Key 和
Bucket，而不必复制或重建整个 sidecar。延迟旧 membership 会增加 locator flags、dirty state、query
filtering、debt threshold 与 cleanup Owner；在证明这些成本必要之前，不应引入。

当前治理目标修正为：

> 先把 SOMA 全部 Table-local 可寻址结构迁移到 32 位结构域，再以一套自研 typed
> open-addressed directory 和升序 primitive `int[]` Bucket 完成 point
> add/update/remove 的即时、局部、原子维护；保持 packed remove、canonical order、zero
> publication 和 managed accounting；用真实 Bucket 分布与 focused mutation profile 判断连续数组
> 是否已经足够。

## 2. 已裁决边界

### 2.1 本候选正式采用

- SOMA 采用“32 位结构域、64 位累计域、Schema-defined 业务数值域”；
- Key 是 `typed logical value -> one raw int locator` 的 Typed Unique Hash Map；
- secondary Index 是 `typed logical value -> ordered raw int locators` 的 Typed Hash-backed
  Inverted Index；
- Key 与 Index 的 logical membership 在 mutation terminal 发布前立即达到最终状态；
- Hash directory 使用 SOMA 自研 primitive parallel arrays，不引入 fastutil 或其他production
  dependency；
- secondary Bucket canonical representation 是 singleton inline 或升序 `int[]`；
- 第一版实现以线性扫描和 `System.arraycopy` 为最小 baseline；这些算法不是长期产品兼容合同；
- 扩容与中间插入使用 `System.arraycopy`；
- ordinary add 的 fresh locator 单调递增，因此走尾部 append fast path；
- structural remove继续采用deterministic packed remove，把tail record移动到hole；
- locator只是非负raw `int`，`-1`继续作为missing/end sentinel；
- point/small mutation使用prevalidated、bounded、non-throwing final commit；
- Selection mutation第一版继续一次构造完整candidate payload/sidecar并一次root swap；
- Hash growth/rehash、Bucket capacity growth和Selection candidate rebuild是同步物理工作，不是延迟
  logical membership。

### 2.2 本候选明确删除

- `REMOVED_FLAG`、`INDEX_DIRTY_FLAG`及全部tagged-locator mask；
- Locator State pages、row tombstone与physical high watermark/live size分离；
- `CLEAN`/`DIRTY` Bucket；
- stale Key/Index membership；
- authoritative Column candidate recheck；
- structural debt、25% threshold与terminal-tail cleanup hook；
- Base/Delta Index、query merge、background cleanup；
- 第二套locator truth、reverse mapping或自适应accelerator；
- linked posting及其per-record next/prev storage；
- fastutil production dependency；
- 为未来优化预建的strategy、SPI、adapter或placeholder。

### 2.3 保持不变的产品语义

- public/generated API按32/64位数值域更新；Logical IR与optimizer不感知physical Index replacement；
- Key immutable，修改Key仍只能remove后add；
- Table可以没有Key；每个Table仍为`0..1` Key、`0..N` secondary Index；
- Key expected `O(1)` point access；IndexSelection返回`0..N` ordered selection；
- canonical encounter order、null、Hash collision和generated typed equality合同不变；
- mutation保持Table-local all-or-nothing与zero partial publication；
- same Group外部串行、different Group可由application并发；
- compression、resource admission、structured failure、reference clearing和metadata snapshot合同不变；
- 不增加third production artifact、public physical API或cross-Table transaction。

## 3. 核心认知模型

```text
Table authoritative state
    -> payload Columns / Chunks
    -> optional Typed Unique Key Map
    -> zero or more Typed Bucket Indexes
    -> compression / statistics / accounting
    -> immutable published header

Typed Unique Key Map
    typed hash + generated equality
        -> raw int locator

Typed Bucket Index
    typed hash + generated equality
        -> Bucket
            -> ordered primitive int locators
```

Column仍是业务值的唯一 authoritative Owner。Key/Index是同一generation内的derived access path，
但它们不是“可能过期的candidate cache”：published state中的每条membership都必须live并与当前Column
logical value一致。

Hash只负责缩小probe domain。Exact identity始终由既有generated typed equality决定；不同logical
value即使Hash collision也必须占用不同directory slot/Bucket。SOMA不缓存ordinary application
Object作为Index identity；ordinary Object本来也不能作Key/Index。

## 4. 正式不变量

任意已发布的Table generation必须同时满足：

1. `0 <= locator < size <= capacity`；
2. live locator domain恰好是dense `[0, size)`，没有removed hole、tombstone row或unpublished tail；
3. Key Map中的每个locator都live，且mapped Key与locator当前authoritative Key相等；
4. 每个live Key最多一个locator；duplicate add在第一次physical write前失败；
5. 每个Index Bucket中的locator严格升序且不重复；
6. 每个Bucket locator都live，且locator当前authoritative indexed Field与Bucket exact identity相等；
7. 每个live Record在每个secondary Index中恰好出现一次；
8. IndexSelection是current Table canonical order的ordered subsequence；
9. payload、Key、全部Index、compression、size/version/statistics与accounting属于同一published
   generation；
10. structured failure时old generation完整不变；
11. remove后失去reachability的reference leaf在同一次commit中清空；
12. physical directory/Bucket identity不进入Logical IR、generated signature或ordinary metadata API。

### 4.1 数值域不变量

SOMA不再把所有规模统一扩大成`long`，而是按语义区分三个数值域：

```text
32-bit structural domain
    Table size/capacity/locator
    Chunk ordinal/local offset
    Key/Index membership
    Selection与Table-local mutation result
    Java array/list materialization length

64-bit cumulative domain
    Stream/Relation/Group count与cardinality
    Relation encounter ordinal和skip/limit
    stateVersion
    memory bytes、budget、retained/temporary peak
    checked byte/cardinality arithmetic

Schema-defined application domain
    ID、time、quantity及其他Field按application声明
```

32位结构域超出明确产品能力时在第一次physical write前以`RESOURCE_LIMIT_EXCEEDED`失败；合法范围
内的累计或allocation arithmetic无法表达时以`ARITHMETIC_OVERFLOW`失败。64位Hash只是内部性能
mechanism，不改变结构域，也不进入public API。

## 5. 32 位结构域与 raw locator

### 5.1 Locator合同

本候选固定：

```text
MAX_TABLE_SIZE    = Integer.MAX_VALUE
valid size        = 0..Integer.MAX_VALUE
valid locator     = 0..size-1
maximum locator   = Integer.MAX_VALUE - 1
missing/end       = -1
```

- 所有合法locator为non-negative `int`；
- Table `size/capacity`、generated `size()/capacity()/reserve(...)`、schema `defaultCapacity`、
  Table-local Selection size和mutation result使用`int`；
- Hash、Bucket、Chunk addressing和canonical comparison直接使用raw locator；
- 不保留flag bits，不需要mask/extract；
- public API仍不暴露locator；
- Table/Field/Index/Relation Stream的`count()`统一返回`long`；Relation/Group cardinality、memory
  accounting、stateVersion与checked cumulative arithmetic继续使用`long`；
- application Field的`int/long`等类型只由Schema与业务语义决定，不随结构域变化；
- `reserve`或`add`超过`MAX_TABLE_SIZE`、Bucket/materialization无法由Java array安全表达时，使用
  `RESOURCE_LIMIT_EXCEEDED`在mutation/publication前fail closed；
- int structural arithmetic必须通过checked helper完成；byte/cardinality计算先widen到`long`再检查。

这项裁决会改变当前正式Blueprint/Storage/Signature/Core invariant中的long-domain Table合同和部分
generated signature；在candidate实现与qualification闭合前只记录delta，不直接修改正式Owner。

### 5.2 Dense packed remove

删除locator `R` 时，令 `T = size - 1`：

```text
R == T
    -> 删除R
    -> size--

R != T
    -> 删除R代表的Record
    -> 将tail Record T移动到R
    -> 将tail Key mapping从T改为R
    -> 将tail在每个Index中的membership从T改为R
    -> 清空tail reference slots
    -> size--
```

因此published root始终dense，不需要`liveSize`、`physicalHighWatermark`或locator reuse policy。
Packed remove可以改变structural mutation后的encounter order；这与正式Storage Design一致，Table不承诺
insertion/business order。

## 6. Typed Unique Key Map

### 6.1 物理职责

Key Map使用sharded/open-addressed primitive parallel arrays，概念上类似：

```text
Shard
    byte[] slotStates
    long[] hashes
    int[] locators
```

精确字段布局、Shard数量、probing、load factor与growth ratio是profile-driven internal mechanism，
不进入长期compatibility合同。Table整体不能因为一个全局Hash array重新形成int domain；directory继续
按安全范围分Shard。

### 6.2 Exact identity

Map不保存boxed locator，也不要求保存Key object：

```text
probe staged/generated Key
    -> generated typed hash
    -> probe candidate slots
    -> 读取slot locator对应authoritative Key
    -> generated typed equality
```

Point add中new locator的payload尚未发布，因此duplicate probe直接使用staged Key与existing locator
比较；只有commit后new slot才以new locator作为稳定identity。String、Enum、primitive和flattened
`@SomaValue`沿用正式Schema/Storage equality。Key outer始终non-null。

### 6.3 即时操作

```text
find/get
    -> expected O(1) exact probe

add
    -> duplicate probe
    -> insert key -> new locator

remove R
    -> delete removed Key entry
    -> R != T时把tail Key entry的locator从T改为R
```

Key immutable，因此不存在indexed-style Key update、dirty Key或stale Key。Remove后重新add相同Key只
看到当前Map；旧entry已经立即删除，不需要特殊removed-candidate规则。

### 6.4 Hash slot删除

第一实现可以继续使用tombstone或经证明正确的backward-shift deletion。若使用tombstone：

- logical entry在remove commit中立即消失；
- tombstone只属于Hash probing metadata，不是row/index stale membership；
- insertion在load/tombstone policy要求时同步构造replacement Shard并commit；
- threshold、load factor与rehash geometry是internal mechanism；
- rehash失败发生在physical write前，不能留下partial Key Map。

## 7. Typed Bucket Index

### 7.1 Directory

每个secondary Index拥有一套typed open-addressed directory：

```text
typed hash + exact generated equality
    -> one Bucket slot
        -> 1..N ordered raw locators
```

Directory可以与Key Map共享经过验证的probing/growth实现，但Key与Index仍是不同semantic Owner：Key
unique且返回`0..1`；Index non-unique且返回ordered `0..N`。

### 7.2 Bucket identity

Published Bucket始终至少包含一个live locator，因此可以用一个representative locator读取
authoritative indexed Field完成exact equality，无需保存完整typed value snapshot：

- singleton时representative就是唯一locator；
- multi Bucket中representative可以固定为第一个locator；
- representative被point update/remove移出时，commit同时选择剩余第一个locator；
- Bucket变空时立即删除directory entry；
- packed remove把representative tail locator `T`移动为`R`时，同一commit更新representative；
- new Bucket在prepare阶段使用staged value比较，commit后由插入locator成为representative。

不同logical value的Hash collision使用不同exact slot/Bucket。Nullable String/Enum Index保留normal
null Bucket；Join的null-never-match合同不因此改变。

### 7.3 Singleton inline

为了避免高基数Index为每个singleton分配`Bucket object + int[1]`，directory slot必须支持inline
singleton：

```text
count == 1
    -> firstLocator保存唯一membership
    -> members == null

count >= 2
    -> members引用升序int[]
    -> firstLocator == members[0]
```

`1 -> 2`时在prepare阶段分配multi array；`2 -> 1`时commit后退回inline singleton并释放array
reachability。它们是同一个Bucket的互斥内部representation，不是两套Index或query merge。

## 8. 升序 `int[]` Locator Bucket

### 8.1 第一版数据形态

概念结构：

```java
final class LocatorBucket {
    private int[] locators;
    private int size;
}
```

实际实现可以把`size/firstLocator/members`内联到directory slot以减少对象，但语义必须等价。`int[]`
只属于runtime internal physical representation，不进入public API、generated source、Logical IR或
metadata compatibility。

单个Bucket使用一个连续`int[]`，不引入paged container或第二套membership structure。任何array
allocation都必须先完成length、bytes、memory budget与JVM capability preflight；无法取得所需连续数组
时以`RESOURCE_LIMIT_EXCEEDED`失败，不静默缩小Table或部分发布。第一版从最小线性扫描开始，未来
内部查找算法只能由profile和qualification驱动，不进入产品兼容合同。

### 8.2 线性查找

Bucket保持严格升序。查找从头顺序扫描，并在`current >= target`时终止：

```text
for each locator in ascending order
    locator == target -> found
    locator > target  -> absent; current position is insertion point
end                   -> absent; append position
```

这是有意选择的第一版baseline：连续primitive array没有boxing、pointer chasing或per-entry object，
线性遍历和`System.arraycopy`可以充分利用cache与memory bandwidth。它的渐进复杂度仍是`O(B)`；
本文不把硬件速度误写成`O(log B)`，也不为未来查找算法预建strategy或placeholder。

### 8.3 Add

普通Table add使用`locator = size`，大于全部existing locator：

```text
empty Bucket      -> inline singleton
singleton Bucket  -> allocate [old, locator]
multi Bucket      -> ensure capacity, append locator
```

fresh add不扫描Bucket，不做membership existence check；locator allocator和每Index恰好一次调用保证
membership唯一。Array capacity不足时，在prepare阶段分配并复制replacement array；commit只替换引用
和count。

Indexed update把历史locator加入new Bucket时：

```text
linear scan
    -> existing: internal invariant failure，不能重复插入
    -> insertion point: ensure capacity + shift suffix + write locator
```

### 8.4 Remove

```text
linear scan locator
    -> found position
    -> shift suffix left by one
    -> decrement count
```

- count变0：删除directory entry；
- count变1：退回inline singleton；
- ordinary remove不因空闲capacity立即shrink multi array；
- array slack计入managed retained bytes；
- full Selection rebuild自然消除slack；
- locator不存在是internal invariant violation，不是普通用户failure。

### 8.5 Packed relocation `T -> R`

Tail locator `T`是当前Table最大locator，因此在其Bucket中也一定是最后一个membership。若`R != T`：

```text
tail Bucket
    -> O(1) remove last T
    -> linear scan insertion point for R
    -> shift suffix right
    -> insert R
```

若removed Record和tail Record的indexed value相同，同一个Bucket执行组合操作：从Bucket删除旧`R`和
`T`，再插入代表tail Record的新`R`，最终count只减少1且不产生duplicate。若值不同，removed Bucket
删除`R`，tail Bucket执行`T -> R`。

## 9. Point mutation合同

### 9.1 两阶段协议

所有point mutation遵循：

```text
PREPARE
    -> argument/carrier/owner validation
    -> Key lookup / duplicate check
    -> callback与staged payload
    -> generated hash/equality
    -> 定位全部affected directory slots和Bucket positions
    -> 完成Hash/Bucket/Chunk growth allocation
    -> 完成compression、prepared mutation descriptor和managed-memory preflight
    -> 完成全部recoverable fault injection

COMMIT
    -> bounded System.arraycopy / primitive writes / reference writes
    -> payload、Key、Index与compression final writes
    -> publish immutable header/statistics/accounting/version
```

第一次physical write之后不得再执行application callback、allocation、hash/equality或其他可恢复
throwing work。`System.arraycopy`参数、array capacity、slot identity和all arithmetic必须在prepare
中证明有效；final commit中的bug/VM Error不伪装成recoverable structured failure。

同一Group guard排除普通并发operation。Lock-free metadata只读取上一次完整detached header
projection，不读取正在commit的payload/Bucket array。该机制直接使用正式Storage Design准入的
prevalidated non-throwing bounded physical writes，不要求为每次point mutation复制整个Table或Index。

### 9.2 Point add

```text
seal application object
    -> validate Key/non-null/value shape
    -> choose locator = current size
    -> duplicate Key probe
    -> locate/create every Index Bucket
    -> preflight payload/Hash/Bucket/codec/accounting capacity
    -> write payload
    -> insert Key -> locator
    -> append locator to every Index Bucket
    -> publish size+1/version/accounting
```

所有Index append均利用fresh monotonic locator，不扫描existing Bucket。Hash Shard或Bucket capacity
growth只在需要时偶发发生，形成摊销成本。

### 9.3 Point update

```text
Key lookup
    -> missing: matched=0, changed=0
    -> callback-scoped Editor stage
    -> compare all logical Fields
        -> no-op: matched=1, changed=0, no publication
        -> changed:
            -> identify changed indexed Fields
            -> locate old/new exact Buckets
            -> prepare remove locator from old Bucket
            -> prepare add locator to new Bucket
            -> prepare payload/compression/accounting
            -> one non-throwing commit
            -> publish one version
```

Non-indexed update不读取或修改任何Index Bucket。多个indexed Field同时变化时，每个Index只处理自己
的old/new Bucket；不存在whole-sidecar rebuild。Key没有setter，也不参与update。

若old/new logical value按generated equality相等，不修改membership。若目标Bucket已经包含同locator，
说明内部状态损坏；不能静默去重掩盖错误。

### 9.4 Point remove

设命中locator为`R`，tail为`T = size - 1`：

```text
PREPARE
    -> freeze R/T payload与全部Key/Index values
    -> prepare removed Key deletion
    -> prepare R从每个removed-value Bucket删除
    -> R != T:
        -> prepare tail Key locator T -> R
        -> prepare每个tail-value Bucket locator T -> R
        -> preparetail payload/compression move
    -> prepare reference clearing、accounting与header

COMMIT
    -> commit Key/Index operations
    -> R != T时tail payload写入R
    -> clear oldtail reference slots
    -> publish size-1/version/accounting
```

同一Index中removed value与tail value相同/不同两种情况都必须有专门组合操作，不能通过两次互相
干扰的普通remove/add临时破坏Bucket invariant。Repeated remove仍由Key lookup得到既有
`removed == 0` normal result。

## 10. Selection mutation合同

`table.filter(...).update(...)`与`table.selectAll().remove()`各自是一次完整Table-local terminal。
Selection不机械重复point prepared descriptor，也不partial publish：

```text
bind + freeze selection
    -> reserve conservative payload/sidecar/codec/scratch peak
    -> execute all callbacks into staging
    -> determine final compacted payload and mapping
    -> build one complete Key Map and all Index directories/Buckets
       from final authoritative candidate payload
    -> validate uniqueness/order/accounting
    -> one root swap
```

这不是延迟Index：在terminal返回前，published candidate已经包含最终、精确、无stale membership的
Key/Index。本专题只保留一条Selection candidate rebuild路径，不预建small-selection替代机制。

Dense Selection remove仍可能昂贵，但它是独立的whole-candidate physical algorithm问题，不应把
point mutation重新拖回full rebuild，也不应通过stale query转嫁成本。

## 11. 既有读取与执行合同的非回归边界

本专题不重新设计Query、Join、Compression、Planner或parallel execution。实现只需保持：

- Key find/get仍返回`0..1` exact current membership；
- IndexSelection按canonical locator order迭代singleton或`int[]` Bucket，`count()`对外仍返回`long`；
- optimizer仍只看到`KEY_LOOKUP`/`INDEX_LOOKUP` capability，不看到Hash slot、array position、
  representative、packed relocation或growth；
- Equality Join继续使用同一logical null/duplicate/order合同，reference interpreter仍是差分oracle；
- PLAIN/encoded/overlay只改变payload representation，Index仍保存logical locator；
- sequential/optimized/parallel绑定同一完整published generation；
- same-Group guard和quiescence继续排除mutation overlap。

这些项目只进入non-regression evidence；本Temporary不增加相关public API、IR node、planner phase、
compression representation或parallel protocol。

## 12. Memory与resource accounting

Managed retained必须覆盖：

- Key/Index Shard container与slot-state arrays；
- hashes、locators、counts、representative和Bucket reference arrays；
- singleton inline fields；
- multi Bucket `int[]`完整capacity，不只logical size；
- rehash replacement和暂时仍reachable的old structure；
- statistics/header对象与alignment/conservative headers。

Temporary admission必须覆盖：

- point mutation需要的replacement Shard/Bucket array、prepared descriptor和codec candidate peak；
- packed remove同时受影响的removed/tail Bucket准备；
- Selection完整candidate payload、Key、全部Index、compression与mapping peak；
- failure时丢弃candidate、释放lease所需边界。

普通remove不立即shrink非空Bucket capacity，避免反复allocate/copy；empty Bucket立即释放logical
reachability，`2 -> 1`退回inline singleton。Array growth factor、Hash load factor和tombstone rehash
policy由profile调节，但必须被managed accounting观察，不能成为unmanaged heap。

## 13. Failure与atomicity

### 13.1 正常结果

- missing point update：`matched=0, changed=0`；
- logical no-op update：`matched=1, changed=0`且version不变；
- missing/repeated remove：`removed=0`；
- successful add/update/remove各自至多发布一个new version；
- empty Bucket查询返回normal zero/empty。

### 13.2 Structured failure

至少保持：

- duplicate Key：`DUPLICATE_KEY`；
- invalid carrier/null/owner/range：`INVALID_ARGUMENT`；
- 请求超过`MAX_TABLE_SIZE`、Java array/materialization capability或retained/temporary budget：
  `RESOURCE_LIMIT_EXCEEDED`；
- 合法范围内的byte、cardinality、accounting或stateVersion checked arithmetic无法由`long`表达：
  `ARITHMETIC_OVERFLOW`；
- callback/hash/equality边界沿用既有stable failure mapping。

### 13.3 Failed-state guarantee

任何recoverable failure返回时必须同时成立：

- size/version/root header不变；
- payload、Key、每个Index和compression仍表达同一old generation；
- 没有partial Bucket shift、duplicate/missing membership或partial packed move；
- managed reservation、temporary lease与Group guard释放；
- callback external side effect不冒充可回滚；
- 没有hidden retry、reference fallback、background repair或full-sidecar silent rebuild。

Fault injection必须位于每个可失败prepare边界；进入first physical write后不再注入recoverable
failure。若某个Array/Map commit path不能证明bounded non-throwing，它不能使用in-place mechanism，必须
退回affected candidate或停止等待裁决。

## 14. 复杂度与性能合同

### 14.1 预期复杂度

| Operation | Candidate B目标 |
|---|---|
| Key find/get/add/remove | expected `O(1)` Hash work |
| fresh Index add | expected `O(1)` directory + amortized `O(1)` tail append |
| Index exact count | expected `O(1)` |
| Index iteration | `O(matches)` sequential primitive traversal |
| historical Index insert | `O(B)` linear position search/array shift |
| Index remove | `O(B)` linear position search/array shift |
| packed relocation | per Index `O(B)`，tail removal本身`O(1)` |
| point mutation | 与affected Index/Bucket相关，不与whole Table/whole sidecar相关 |
| Selection mutation | 一次`O(N × access paths)`candidate rebuild |

`B`是受影响exact Bucket的locator数。本候选有意接受`O(B)`，因为连续`int[]`的常数、cache
locality和实现复杂度预计优于linked predecessor traversal、object tree或双结构。这个判断必须由
真实分布验证，不能只用Big-O宣布成功。

### 14.2 不退化边界

- point add不扫描Bucket；
- point update/remove不扫描whole Table或unaffected Index/Bucket；
- Key point access保持expected `O(1)`；
- Index exact count保持expected `O(1)`；
- query不增加locator flag、dirty filter或authoritative recheck；
- high-cardinality singleton不为每个value分配`int[1]`；
- retained bytes不得因per-Bucket object/array overhead显著放大；
- mutation收益不能以clean query、Join、parallel或ingest显著退化换取；
- fixed-host单项clean family超过稳定噪声带或约5%必须解释并修复；复杂度或内存Owner错误不受噪声
  豁免。

### 14.3 Internal algorithm边界

Linear scan、`System.arraycopy`、Hash deletion policy、load factor、growth ratio和Shard geometry只是
第一版可执行baseline。它们不进入正式Design兼容合同，也不预建可插拔strategy或替代container。
未来若profile发现新的主导Owner，按当时证据直接修改唯一internal implementation并重新qualification。

## 15. Dependency与surface边界

本专题不增加fastutil或其他production dependency，不增加public physical API、container SPI、adapter、
strategy或第三artifact。SOMA已有generated typed hash/equality、managed accounting和atomic publication
合同；`int[]`足以形成最小、可profile的primitive baseline。

## 16. 实施顺序

### S0 — Worktree与事实隔离（`COMPLETE`）

- 将scheduling refactor可恢复地冻结在本专题之外，并恢复稳定example基线；
- 保留本目录两个候选及HANDOFF；
- 移除未完成、未资格化的`LocatorBitmap` experiment；
- 恢复compile/test可运行的立即linked-posting checkpoint；
- 重放Key/Index/clean-query baseline；
- 保留44–45秒FJSP历史checkpoint作为问题来源，不把它设为当前专题的qualification依赖或正式claim。

### S1 — 32位结构域迁移（`COMPLETE`）

- 建立Storage、Execution、Failure、Planning、Core abstraction delta matrix；
- 将Table size/capacity、raw locator、Chunk/Table scan range、Key/Index membership、Selection和
  Table-local mutation result迁移为checked `int`；
- 保留Stream/Relation/Group cardinality、memory、stateVersion和checked cumulative arithmetic为`long`；
- 更新annotation/generated API、runtime、consumer、source/`javap` golden与near-boundary evidence；
- 先保持当前立即linked-posting算法，完成compile/test/signature/full qualification基线；
- 不在同一修改中替换Index container，避免数值域与算法回归互相掩盖。

### S2 — 即时 `int[]` Key/Index维护（`COMPLETE`）

- 从current sharded open addressing提取共享但不泄漏的directory mechanics；
- Key exact probe、duplicate、remove、tail relocation；
- Index exact directory、representative替换、collision/null；
- Hash growth/tombstone cleanup、checked arithmetic和managed accounting；
- 建立singleton inline + multi升序`int[]`的唯一Bucket representation；
- 以linear early-stop search作为第一版baseline；
- fresh append、historical insert、remove、`T -> R` relocation；
- same-Bucket/different-Bucket packed remove；
- non-indexed/indexed/multi-Index update；
- preflight、prepared descriptor、non-throwing commit与fault injection；
- reference clearing、compression和version/accounting closure。

### S3 — Selection与全部读取路径（`COMPLETE`）

- Selection candidate rebuild只生成新Bucket结构；
- Key/Index scan、optimized substitution、Join与parallel读取新structure；
- reference/optimized/parallel differential；
- `_metadata()`/`_explain()`不泄漏physical array或半完成state；
- 删除旧linked posting、next links与bitmap残留。

### S4 — Qualification与晋升

- targeted correctness/failure/accounting tests；
- operator/type/distribution benchmark；
- focused mutation workload与async-profiler；
- clean-family/package/full qualification；
- bounded独立审查；
- 稳定结论晋升正式Design/Conformance；
- 冻结candidate与Temporary完成replacement closure后退役。

## 17. 必须覆盖的验证矩阵

### 17.1 Type与identity

- primitive Key/Index：boolean/byte/short/char/int/long适用形态；
- String、Enum、nested `@SomaValue`；
- nullable String/Enum Index null Bucket；
- generated Hash collision但logical value不同；
- equal Value不同application object instance；
- keyless Table与多个secondary Index；
- invalid ordinary Object/float/double role仍由processor拒绝。

### 17.2 Bucket分布

- empty/missing；
- singleton inline；
- `1 -> 2 -> 1 -> 0` representation transition；
- Bucket size 2、8、64、1K、10K、100K、1M；
- high-cardinality大量singleton；
- low-cardinality少数热点Bucket；
- skew、uniform和FJSP status/assignedMachineId分布；
- capacity-minus-one/growth/rehash/tombstone reuse。

百万locator单Bucket是SOMA接受的正常低基数分布。第一版明确接受其`O(B)`成本；qualification记录
正确性、CPU、allocation和memory baseline，但不因规模本身预先增加新container或search abstraction。
任何size/int/byte arithmetic仍必须fail closed，不能依赖测试未达到边界。

### 17.3 Mutation序列

- add与duplicate Key；
- non-indexed/indexed/no-op update；
- 同一locator在多个Index中变化；
- `READY -> RUNNING -> COMPLETE -> READY`不重复membership；
- remove tail与remove middle；
- removed/tail在同一Index Bucket与不同Bucket；
- removed/tail分别是Bucket representative；
- Key remove后重新add；
- repeated remove；
- random add/update/remove state machine；
- small/large Selection update/remove后与point/reference model一致。

### 17.4 Order与query

- 每个Bucket严格升序且无duplicate；
- packed remove后Table/Index canonical order一致；
- Key find/get、Index count/iteration/residual；
- optimizer Key/Index substitution；
- Join duplicates、null和left/right order；
- sequential/reference/optimized/parallel result、fingerprint和order等价。

### 17.5 Failure与memory

- duplicate、hash collision、array/Shard/Bucket growth；
- payload、Hash、Bucket、compression、accounting、header各prepare fault point；
- first physical write前zero publication；
- commit无recoverable fault point；
- exact managed retained bytes覆盖capacity/slack/singleton/multi；
- temporary peak覆盖replacement structures；
- remove立即清空unreachable reference leaves；
- failed mutation后root/version/Key/Index/payload/accounting完全一致。

### 17.6 Performance

- add吞吐和ingest；
- Key 10K random probes；
- Index exact count/iteration；
- point update/remove across Bucket sizes；
- same/different-Bucket packed remove；
- multiple Index cost随Index count变化；
- high-cardinality retained bytes；
- Selection rebuild基线；
- 10K/1M/10M clean query family；
- focused point/Selection mutation composed workload；
- CPU samples、allocation bytes、retained/temporary和RSS分责归因。

## 18. Stop rules

遇到以下情况必须暂停，不得静默叠加补丁：

- 32位结构域出现unchecked wrap、负locator或超过`MAX_TABLE_SIZE`仍继续执行；
- `int[]` Bucket在allocation/admission失败后产生partial publication；
- linear Bucket使真实mutation journey不能优于可重放checkpoint；
- 保持canonical order必须引入另一套长期truth structure；
- in-place commit不能证明全部recoverable work已前置；
- managed accounting无法覆盖Bucket capacity/slack/rehash peak；
- 需要fastutil或其他新dependency；
- 需要超出本文已批准32/64位数值域的Blueprint/public/generated API变化，或改变Logical IR/failure
  taxonomy；
- point收益造成clean query/Join/parallel显著退化；
- formal Design delta无法闭合或reference differential失败。

触发后只建立新的bounded裁决，不恢复candidate A、不自动叠加第二套Index truth，也不以application
固定槽位掩盖runtime问题。

## 19. Exit标准

只有以下内容共同成立，本专题才可以关闭：

1. Product Owner已批准candidate B最终Design及32位结构域/64位累计域裁决；
2. Blueprint、Storage、Signature、Failure与Core invariant的正式delta闭合，Logical IR语义保持不变；
3. Key只有一套即时Typed Unique Hash Map，published mapping始终exact live；
4. 每个Index只有一套Typed Bucket directory，published Bucket始终exact、live、ordered、无duplicate；
5. singleton inline与multi `int[]`是互斥的同一canonical Bucket representation；
6. 没有第二套locator truth、adaptive container、public strategy或未准入dependency；
7. locator保持raw non-negative `int`，Table structural domain不超过`Integer.MAX_VALUE`，没有flags、
   dirty state、stale membership或debt hook；
8. point add/update/remove不执行whole-Table/whole-Index rebuild；
9. packed remove对payload、Key、全部Index、compression、reference和accounting一次发布；
10. Selection mutation只构造一次完整candidate并一次root swap；
11. fault injection证明全部recoverable failure zero publication；
12. retained/temporary/accounting与array-bound checked arithmetic闭合；
13. reference/optimized/parallel、Key/Index/Join/order differential通过；
14. Bucket distribution与mutation state-machine验证通过；
15. clean 10K/1M/10M family无显著回归；
16. focused mutation workload不慢于可重放的立即linked-posting checkpoint并取得可解释收益；
17. full repository qualification和package boundary通过；
18. bounded独立审查没有P0/P1；
19. 正式Conformance成为current evidence Owner；
20. candidate A、HANDOFF与本Temporary完成replacement closure后退役。

## 20. 当前交接边界

S0已将以下内容固化为可重放基线：

- `a0f1cd9`：已验证的立即linked-posting point maintenance；
- 本Temporary与项目入口更新由后续治理提交拥有。

未资格化`LocatorBitmap` experiment已删除；scheduling refactor已在本专题之外可恢复地冻结，active
checkout恢复为稳定example基线。二者都不属于candidate B implementation。

它们都不是candidate B已经实现的证据。精确checkpoint、profile和用户修改边界由
[`HANDOFF.md`](HANDOFF.md)拥有。

Product Owner已完成本文审核。下一步可以按S0→S1→S2的单一active slice顺序开始，但仍不得：

- 把S1数值域迁移和S2 Index container replacement混入同一未验证提交；
- 继续补完bitmap experiment；
- 将candidate A恢复为active implementation input；
- 修改正式Design掩盖候选状态；
- 添加fastutil dependency；
- 在本专题内恢复或继续scheduling refactor；
- 在implementation/qualification前宣称32位结构域或即时`int[]`方案已经成为executable fact。
