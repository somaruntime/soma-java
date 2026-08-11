# SOMA 延迟结构变更与单一 Index 维护候选 A（冻结）

类型：`Frozen Governance Candidate`

状态：`FROZEN / CANDIDATE / NOT_SELECTED / NOT_IMPLEMENTED / NOT_QUALIFIED`

初始日期：2026-08-10

冻结日期：2026-08-11

Owner：此前延迟维护方案的冻结候选记录；不再拥有当前治理实施合同

> Product Owner 于 2026-08-11裁决：本候选停止演进并冻结保存，不删除、不继续修订，也不作为
> implementation authorization。当前治理的 active candidate 已切换为
> [`DESIGN.md`](DESIGN.md)中的“即时 Key/Index 维护 + 升序 `long[]` Bucket”方案。以下正文保留冻结时
> 的完整设计与历史裁决，仅用于方案比较、风险追溯和 replacement closure；其中任何
> `PRODUCT_OWNER_APPROVED`、正式设计或下一步实施表述均受本冻结声明覆盖。

> 本文是本次 bounded Temporary 内唯一规范性 Design，已由 Product Owner 于 2026-08-10批准；
> 同日完成的最后一轮 Design/code 联合审核进一步批准了本文的成本原则修正：本专题不追求字面意义
> 的“零成本抽象”，允许有预算、有证据的常数成本，但禁止把 mutation 收益转嫁为 clean path
> 复杂度退化或显著的端到端性能、内存退化。
> 它只授权并约束本次治理，不是项目正式 Blueprint/Design、当前 executable fact 或 Conformance
> 结论，也不覆盖当前正式 Design Owner。只有完成 Design delta、实现、正确性证明、性能验证、
> Conformance 与 replacement closure 后，本文中的稳定结论才能晋升为 SOMA 正式事实。

后续 Logical IR 治理意图由独立的
[`FUTURE-LOGICAL-IR-GOVERNANCE.md`](FUTURE-LOGICAL-IR-GOVERNANCE.md)保存。它是下下次专题的
queued intent，不与当前 Key/Index mutation governance 并行实施。

## 1. 问题与触发场景

100,000 工序 FJSP reference journey 暴露了 SOMA 高频 Table-local mutation 的结构性成本：

- point `add` 可以增量写入 Key 和 Index；
- point `update` / `remove` 曾重建完整 Index sidecar；
- 当前未完成工作树已经尝试立即 unlink/insert posting 和 packed remove relocation，显著降低了
  full rebuild 成本；
- 但低基数、大 Bucket 的 predecessor 查找仍成为主要热点；
- application 使用固定槽位只能绕开问题，不能证明 SOMA mutation architecture 合理。

根本问题不是 FJSP waiting queue 建模，而是当前物理 Index 假设一次 mutation 必须立即把
payload、locator 和所有 posting 整理成最终紧凑形态。对于 externally serialized 的
`SomaGroup`，这一要求并非正确性的必要条件。

本次讨论形成的核心判断是：

> 普通 point update/remove 不必立即搬移 Row 或删除旧 Index membership。SOMA 可以用受控的
> 延迟失效，以额外空间和有限查询验证换取低成本 mutation，并在阈值到达后同步整理。

“延迟”的对象是旧 posting 删除、hole compaction、stale membership 清理、完整 sidecar rebuild
和统计整理，不是 Record 的逻辑可见性、Key 唯一性、Index 查询正确性、reference clearing、资源
计费或 atomic publication。每次 mutation terminal 可以承担少量固定指令和受控 metadata，但不能
引入随 Table 总行数或 clean Bucket cardinality 增长的额外工作。

## 2. 选择这一方案的原因

### 2.1 mutation 成本应与受影响内容相关

目标复杂度是：

```text
add
    -> append payload
    -> append Key
    -> append one membership to each Index

indexed update
    -> update authoritative payload
    -> mark locator INDEX_DIRTY
    -> ensure membership in the new-value exact Bucket

remove
    -> mark locator REMOVED
```

普通 mutation 的成本应主要与受影响的 Field、Index 和 Hash Bucket 有关，而不是与 Table 总行数
成正比。以下情况才允许进行全量整理：

- 延迟 mutation 达到内部阈值；
- Hash shard 容量不足，需要偶发 growth/rehash；
- 一次完整 mutation terminal 的 projected debt 达到 cleanup 阈值；
- Chunk/storage representation 发生受控转换；
- 未来另行准入的显式 compact/optimize 操作。

普通 add 分配 cleanup 前从未使用过的 fresh raw locator，因此它向 Key/Index posting 的写入必须是
摊销 `O(1)` append；不得为了证明 locator 唯一而扫描目标 Bucket。Indexed update 重返历史 Bucket
时可以执行 membership existence check，但已准入的大 Bucket 机制必须为 `O(log B)` 或更好，不能
把已知低基数场景重新退化为 `O(B)` 遍历，其中 `B` 是目标 Bucket 的 physical membership 数。

### 2.2 SOMA 已有外部串行边界

同一 `SomaGroup` 由 application 外部串行使用，mutation 又处于独占 operation guard 中。SOMA
不需要为并发旧读者长期保留完整 MVCC version。因此可以先完成 validation、resource admission、
posting capacity 和 journal preparation，再执行 bounded、non-throwing commit。

### 2.3 Index 是派生访问路径，Column 才是权威事实

Index 的职责是缩小 candidate domain，而不是拥有业务值。Hash 只用于定位 probe domain，不能替代
logical equality；不同 logical value 即使 generated hash collision，也必须由 exact typed Bucket
identity 区分为不同 Bucket。这与普通 Hash Map 的“hash 定位、key equality 定义 identity”一致，
避免 clean Key/Index/Join 退化为对 same-hash 全部 candidate 的逐行验证。

Bucket 的 logical identity 是派生 routing snapshot，不是业务事实 Owner。实现可以在 clean Bucket
中使用 live representative locator；当 representative 即将因 update/remove 失效时，可以生成只覆盖
该 indexed Field 的 detached typed snapshot。不得因此复制整行或普通 application Object；String、
Enum 和 `@SomaValue` identity 必须遵守既有 generated equality，并计入 managed retained bytes。

每个 Bucket 显式区分：

- `CLEAN`：全部 membership 都是 live、与 Bucket identity 相等且按 canonical raw-locator order组织；
  `count/head/posting iteration` 是可信快速路径；
- `DIRTY`：允许 removed/stale membership；查询跳过 removed，并对 candidate 读取 authoritative Column
  进行完整 typed equality recheck。

因此 Column 始终决定 candidate 当前是否真实匹配，但 authoritative recheck 只进入 DIRTY Bucket；
CLEAN Bucket 只进行一次 typed Bucket lookup，不把正确性成本无条件扩散到正常查询。

### 2.4 保持单一机制比堆叠补丁更重要

最终方案明确不引入长期并存的 Base/Delta Index，也不增加全局 reverse Index、`prev` link或另一套
并行维护、需要query merge的热点 Bucket container。原因是：

- 第二套 Index 会增加 identity、publication、accounting、failure 和 query merge 的 Owner；
- reverse structure 会增加每行、每 Index 的 retained memory 与维护责任；
- 多套并行 representation 会让 mutation correctness 和长期维护成本快速膨胀；
- 当前应证明一套具有明确复杂度和内存预算的 canonical membership container，而不是叠加补丁。

第一阶段中，每个 logical access path 只使用一套 physical structure：Key 使用一套 unique Hash
Index，每个 secondary Index 使用一套 Hash-backed inverted Index；它们共享同一 raw locator
identity。一个 Bucket 在任一 generation 中只有一个 canonical posting container；singleton inline、
paged sorted blocks 等只是该 container 的内部表示，不构成第二套 Index，也不允许同时维护 linked
posting 与辅助 accelerator 后再在 query 时合并。

### 2.5 成熟数据结构裁决

Product Owner 于 2026-08-10 批准以下专题正式定义：

> Key 定义为 **Typed Unique Hash Index**；secondary Index 定义为
> **Typed Hash-backed Inverted Index**。SOMA 不重新发明新的索引类别，而是利用 schema
> generation、raw locator、authoritative Column recheck、延迟失效和资源受控 cleanup，把两类
> 成熟数据结构适配为单 JVM 大规模 mutable Table engine 的内部访问路径。

成熟结构的对应关系是：

```text
Flat/open-addressed Hash Map
    -> SOMA Typed Unique Hash Index
        typed hash + exact typed Bucket identity -> memberships
        CLEAN direct / DIRTY authoritative recheck -> 0..1 live logical match

Inverted Index
    -> SOMA Typed Hash-backed Inverted Index
        typed hash + exact typed Bucket identity -> ordered postings
        CLEAN direct / DIRTY authoritative recheck -> 0..N live logical matches
```

该裁决借鉴但不直接依赖：

- [Abseil Swiss Table](https://abseil.io/docs/cpp/guides/container) 的 flat/open-addressed、inline
  slot 与 compact control metadata；
- [Apache Lucene postings](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/codecs/lucene103/Lucene103PostingsFormat.html)
  的 term/value dictionary、singleton ID 与 block postings 分责；
- [PostgreSQL Hash Index](https://www.postgresql.org/docs/current/hash-index.html) 的 Hash candidate、
  authoritative row recheck、overflow storage 与 deferred dead-entry cleanup。

这些资料只证明结构家族成熟，不证明其磁盘格式、并发、segment、Vacuum、block size 或 library
implementation 适合直接复制到 SOMA。SOMA 不因此增加第三方 dependency、磁盘 Index、MVCC、
background maintenance 或 public physical API。

应当在正式 Design 中冻结的是：

- Key 是 typed、unique、exact-match Hash access path；
- secondary Index 是 typed、non-unique、exact-match inverted access path；
- Bucket拥有 typed hash、exact typed identity、clean/dirty状态与一个canonical posting container；
- Bucket identity是派生routing snapshot，不取代authoritative Column；只有DIRTY Bucket candidate需要
  authoritative equality recheck；
- 每个 access path 只有一套长期 physical structure，不存在 Base/Delta query merge；
- membership、delayed invalidation、cleanup、atomic publication 与 accounting 合同。

不得冻结为长期 Design compatibility contract 的内容包括：

- Shard 数量；
- probing algorithm 与 load factor；
- hash/fingerprint 位宽；
- Bucket/control array 的精确字段；
- clean representative 与 detached typed snapshot 的精确编码；
- singleton 是否内联；
- posting block size；
- cleanup threshold；
- generated/JIT kernel 的展开方式。

这些属于可由 implementation profile 替换的物理机制。

### 2.6 对后续 Logical IR 治理负责

Product Owner 已明确：Key/Index mutation 是下一次治理；frontend-neutral Logical IR、未来
`JSON -> IR` 与 DAG capability 是下下次独立治理。当前专题不得实施 JSON/DAG，但必须遵守：

- Java generated API 是当前 frontend，不能把 Java facade identity 固化为未来唯一 IR identity；
- Logical IR 只拥有 typed operation semantics，不拥有 Hash Bucket、posting、locator flags 或
  cleanup generation；
- Key/Index substitution 属于 optimizer/physical planner，不能进入用户 predicate semantics；
- `REMOVED` filtering、`INDEX_DIRTY` cleanup-debt accounting与authoritative candidate recheck属于
  storage/executor，不能要求frontend显式表达；
- reference interpreter 继续基于 authoritative live state 作为 correctness oracle；
- 新的 Key/Index mechanism 必须通过 logical access-path capability 绑定，未来替换 physical
  structure 不修改 Java/JSON Canonical IR；
- 不新增 JSON dependency、public serialization、workflow scheduler、DAG placeholder 或第二套 IR。

完整意图、当前初步观察、未来治理问题与成功标准见
[`FUTURE-LOGICAL-IR-GOVERNANCE.md`](FUTURE-LOGICAL-IR-GOVERNANCE.md)。

## 3. 最终认知模型

```text
Authoritative Table State
    -> Columns                      唯一业务事实
    -> Tagged Locator State         唯一 locator 与 lifecycle/debt 状态
    -> Key                         Typed Unique Hash Index
         -> exact typed Bucket [CLEAN | DIRTY]
              -> canonical raw-locator postings
                  -> DIRTY only authoritative Key recheck
                      -> 0..1 live logical match
    -> Index                       Typed Hash-backed Inverted Index
         -> exact typed Bucket [CLEAN | DIRTY]
              -> canonical unique raw-locator postings
                  -> DIRTY only authoritative Field recheck
                      -> 0..N live logical matches
```

约束：

1. 一个 logical Record 只有一个 raw locator；
2. 同一个 Key/Index、同一个 exact typed Bucket 中，同一个 raw locator 最多出现一次；
3. 一个 dirty locator 可以暂时出现在同一 Index 的多个 exact typed Bucket 中；不同logical value
   即使Hash collision也不会合并Bucket；
4. authoritative Column 决定 candidate 是否为当前完整 logical value match；
5. removed locator 在任何 Table、Key、Index 或 relation 正常读取中都不可见；
6. cleanup 前不复用 removed raw locator，避免 ABA 和历史 membership 误命中；
7. remove 的 final commit 立即把对应 reference leaves 置为 `null`；primitive payload 可以保留到
   cleanup；
8. 延迟维护完全是内部机制，不增加 public API，也不改变用户数据模型。

## 4. Locator 编码

Java `long` 是 signed 64-bit。bit 63 始终为零；其后的四位保留给 flags，当前启用前两位：

```text
bit 63       始终为 0
bit 62       REMOVED
bit 61       INDEX_DIRTY
bit 60       RESERVED_1
bit 59       RESERVED_2
bit 58..0    RAW_LOCATOR
```

专题固定常量：

```java
static final long REMOVED_FLAG =
        1L << 62;

static final long INDEX_DIRTY_FLAG =
        1L << 61;

static final long RESERVED_FLAG_1 =
        1L << 60;

static final long RESERVED_FLAG_2 =
        1L << 59;

static final long FLAGS_MASK =
        REMOVED_FLAG
        | INDEX_DIRTY_FLAG
        | RESERVED_FLAG_1
        | RESERVED_FLAG_2;

static final long RAW_LOCATOR_MASK =
        (1L << 59) - 1L;
```

59-bit raw locator 可表达 `0..2^59-1`，约 `5.76e17` 个位置，远超单 JVM in-memory Table 的
现实容量，因此该保留不会形成有效规模边界。

逻辑编码必须区分：

- **raw locator**：Index membership、Chunk addressing、Hash 与 canonical-order 使用的非负位置；
- **tagged locator**：需要在executor/diagnostic内部传递完整状态时，由raw identity与flags合成的值；
- **clean locator**：`(taggedLocator & FLAGS_MASK) == 0`。

tagged locator 是内部value encoding，不要求物理上为每个row永久分配一个`long`。Table-owned
Locator State仍是唯一flags Owner；推荐以lazy sparse paged bit-planes保存`REMOVED`和
`INDEX_DIRTY`：全clean root/page可以没有任何flags allocation，需要时再由`raw locator | flags`
合成tagged locator。这样不是第二套RowState，而是同一tagged-locator合同的紧凑物理编码。

Index 中不能复制并永久拥有row flags，否则改变一个Record状态时需要修改它在所有Bucket中的旧
posting，重新引入反向查找问题。Index membership保存raw locator；查询以raw locator解析
Table-owned Locator State。TableStateRoot同时保存removed/dirty计数，使executor能在terminal或
Chunk边界选择路径：

- 全clean root直接复用现有dense Table/Field/Join/parallel kernel，不逐row读取flags；
- 有removed debt时才进入mask-aware scan；全clean page/word继续走批量fast path；
- Bucket自己的`DIRTY`只决定该access path是否执行candidate recheck，不成为第二个row-state Owner。

bit 63 始终为零，因此所有合法 tagged locator 均为非负值，现有 `-1L` missing/end sentinel 继续
无歧义。实现仍必须在 Chunk addressing、Hash 与 canonical comparison 前提取 raw locator；reserved
flags 未经后续治理准入必须保持为零。

## 5. 单一 Index 的物理形态

### 5.1 为什么需要独立于raw locator的canonical posting container

当前 baseline 近似为：

```text
typed-hash Bucket -> head raw locator
raw locator        -> next raw locator
```

它假设一个 Record 在同一个 Index 中只属于一个 Bucket。延迟 indexed update 会让同一个 raw
locator 暂时存在于旧值和新值 Bucket，因此一个 `next[rawLocator]` 无法同时描述多条 membership
链。

候选结构仍然只有一套Index，但一个raw locator可能暂时出现在多个exact typed Bucket，所以
`next[rawLocator]`不能继续承担唯一posting identity。Bucket必须拥有一个canonical membership
container：

```text
exact typed Bucket
    -> inline singleton locator
    -> or paged sorted raw-locator blocks
```

这只固定能力，不冻结精确block size或directory布局。Container必须满足：

- fresh monotonic locator摊销`O(1)` append；
- canonical raw-locator order iteration；
- locator existence/insert为`O(log B)`或更好，不能在已准入的大Bucket中线性遍历；
- 每个Bucket、每个generation只有一个canonical container；
- retained bytes以一个raw locator/membership加有界block metadata为目标，任何显著额外常驻开销必须
  由profile证明收益；
- internal block/slot不是Row identity、不进入Logical IR或public API。

### 5.2 Exact typed Bucket 与 membership 唯一性

普通add分配fresh raw locator，因此不做membership existence check：

```text
hash + typed logical value
    -> locate/create exact Bucket
        -> append fresh locator
```

Indexed update重返历史Bucket时才执行幂等ensure：

```text
locate exact destination Bucket
    -> canonical container contains(locator)
        -> present: no-op
        -> absent: ordered insert
```

不同logical value的generated hash相同时仍使用typed equality定位不同Bucket。CLEAN Bucket的
membership由该generation不变量证明全部真实；DIRTY Bucket的query/find/duplicate detection在跳过
removed candidate后，对authoritative Column执行完整generated typed equality。

Generated typed hash必须deterministic且与既有equality兼容：logical equal必然产生相同hash；不同值
允许collision。Nullable Index的null使用稳定typed null hash，仍由authoritative null equality裁决；
Join的null-never-match合同不因此改变。

正式不变量：

> 对任意一个 Key/Index，`(exact typed Bucket identity, raw locator)` membership 唯一；重复 ensure
> 是幂等操作。

因此：

```text
READY -> RUNNING -> COMPLETE -> READY
```

可以暂时形成：

```text
READY    -> locator 10
RUNNING  -> locator 10   // stale
COMPLETE -> locator 10   // stale
```

但最后一次回到 `READY` 时，canonical container会发现locator 10已存在，不会产生第二条
`READY -> locator 10`，查询也不会重复返回同一 Record。

若两个状态的generated hash恰好collision，它们仍属于两个exact typed Bucket，不共享posting。

### 5.3 本阶段唯一 membership 机制

本方案明确只允许：

```text
one exact typed Bucket -> one canonical posting container
```

当前阶段不引入：

- 当前未完成的`linked posting + LocatorBitmap accelerator`双结构；
- 同一个Bucket内并行维护list与set/bitmap后query merge；
- global/per-Index reverse Index；
- `prev` posting link；
- Base Index + Delta Index；
- background merge、cleanup 或 asynchronous compaction。

Singleton inline到paged sorted blocks的representation transition属于同一个container的内部状态
转换，可以由profile调整。未来若证据要求bitmap，它只能作为该Bucket唯一canonical container的
替代representation重新准入，不能作为与linked posting长期并行的第二套membership事实。

JIT可以优化生成代码、分支和循环，但不能被当作消除渐进复杂度的证明。任何实现若重新引入
`O(B)` destination membership lookup，必须停止而不能用“常数较小”解释。

## 6. Mutation 合同

### 6.1 live size、physical high watermark 与 capacity

专题固定：

```text
0 <= liveSize <= physicalHighWatermark <= capacity
```

- public `size()`返回 `liveSize`；
- `physicalHighWatermark` 是当前曾分配 raw locator domain 的exclusive upper bound；
- public `capacity()`继续表示不扩充 payload Chunks 时可容纳的logical record capacity；
- cleanup前不复用 removed raw locator；
- 若 `physicalHighWatermark < capacity`，add在high watermark分配新locator；
- 若 `physicalHighWatermark == capacity && liveSize < capacity`，add必须在同一terminal中先把projected
  state同步cleanup，再容纳新Record；
- 若 `liveSize == physicalHighWatermark == capacity`，才扩充Table payload Chunk capacity；相关
  locator/membership page与Hash shard只按各自需求扩充；
- cleanup不得隐式缩小public capacity。

### 6.2 Add

```text
validate carrier and immutable Key
    -> reserve payload/posting capacity
    -> choose a fresh raw locator at physical high watermark
    -> append authoritative Column values
    -> exact Key duplicate lookup
    -> append fresh locator to Key and every Index exact Bucket
    -> preserve all-clean Locator State when no flags are needed
    -> terminal-tail maintenance hook
    -> publish liveSize/highWatermark/version/accounting
```

- cleanup 前不得复用 removed raw locator；
- ordinary add 不扫描完整 Table 或完整 Index；
- Hash shard growth/rehash 是允许的偶发摊销事件；
- fresh locator由allocator不变量保证从未进入任何posting，secondary Index直接append而不扫描Bucket；
- duplicate Key detection先定位exact typed Key Bucket：CLEAN Bucket可直接拒绝，DIRTY Bucket遍历并
  recheck live candidates。

### 6.3 Non-indexed update

如果 update 不改变任何 indexed Field：

```text
validate and stage new payload
    -> update affected Column
    -> preserve Index
    -> publish
```

不设置新的 `INDEX_DIRTY`，也不触发 Index maintenance。若该 locator 因历史 indexed update 已经
dirty，则原状态保持到下一次 cleanup。

### 6.4 Indexed update

以 `status: READY -> RUNNING`、`locator = 10` 为例：

```text
stage and validate RUNNING
    -> reserve any required posting capacity
    -> locate exact typed Bucket for RUNNING
    -> canonical container ensure locator 10 membership
    -> commit authoritative status = RUNNING
    -> set INDEX_DIRTY for locator 10
    -> mark old-value Bucket DIRTY
    -> terminal-tail maintenance hook
    -> publish version/accounting
```

旧的 `hash(READY) -> locator 10` membership 暂时保留。若一次 update 同时改变多个 indexed
Field，每个受影响 Index 都执行相同的 ensure；不同logical value即使Hash collision也由typed equality
进入不同Bucket。一个row-level `INDEX_DIRTY`记录cleanup debt；affected old Bucket的`DIRTY`决定其
query执行candidate recheck。Destination Bucket若原先CLEAN且ordered ensure后没有stale candidate，
可以继续CLEAN；若已存在历史debt则保持DIRTY。

所有可能失败的 validation、hash/equality、allocation、capacity growth 和 journal preparation
必须发生在第一次 physical write 之前。最终 commit 只包含 bounded、non-throwing writes，并使
payload、tagged locator、Index、version 和 accounting 成为一个 logical generation。
受影响的 posting block、Bucket metadata 与 Locator State page 必须在 commit 前作为私有 candidate
完成，或者由已经完整准备的 bounded rollback journal 保护；任何 lock-free metadata 读取都只能看到
最后一次 published generation，不能在 StateRoot 发布前观察到半完成的 dirty state。

### 6.5 Remove

```text
locate live record by Key
    -> locate current exact Key/Index Buckets before clearing references
    -> stage Locator State write
    -> set REMOVED
    -> mark affected Key/Index Buckets DIRTY
    -> clear all reference leaves to null
    -> decrement logical live size
    -> preserve primitive payload and all Key/Index memberships temporarily
    -> terminal-tail maintenance hook
    -> publish version/accounting
```

普通 point remove 不进行 packed/swap remove，不移动 tail Record，也不立即删除 Key/Index
membership。所有访问路径看到 `REMOVED` 后必须把该 Record 视为逻辑不存在：

- `find` 返回 empty；
- `get` 使用既有 missing contract；
- repeated remove 返回既有 `removed == 0`；
- Index、scan、Join、GroupBy、parallel 和 reference execution 均不得返回 removed Record。

Reference leaves在同一次final commit中立即清空，避免removed Row继续持有application object。
如果removed locator同时是某个Bucket的live representative，commit前必须建立该Bucket后续可用的
typed identity（选择另一个live representative或detached Field snapshot），因此reference clearing
不破坏exact Bucket lookup。延迟维护状态不能泄漏为新的public failure。

### 6.6 Key remove 后重新 add

Key 仍然 immutable；修改 Key 必须 remove 后 add。Key lookup 命中 removed locator 时必须按逻辑
missing 处理。cleanup前重新add相同Key时：

```text
hash + typed staged Key
    -> locate exact Key Bucket
        -> CLEAN + live: DUPLICATE_KEY
        -> DIRTY: traverse candidates
            -> removed: skip
            -> live + authoritative Key equal: DUPLICATE_KEY
            -> no live equal candidate: allocate fresh raw locator and append membership
```

旧removed locator不复用，也不阻止新Record；同一exact Key Bucket可以暂时包含removed旧locator和
新live locator，但不同Hash-collision value属于不同Bucket，完整logical Key始终最多一个live match。
Cleanup只从live authoritative Key重建memberships。

### 6.7 Selection mutation

`table.filter(...).update(...)`与`table.selectAll().remove()`各自是一个完整terminal。Executor先冻结
并执行完整Selection，形成一次projected state；maintenance hook只在整个terminal data work完成后
运行一次，不在每个Record后运行。

```text
freeze Selection
    -> stage all row changes
    -> derive projected live/high-watermark/debt/accounting
    -> run one terminal-tail maintenance hook
        -> below threshold: keep delayed invalidation
        -> threshold reached: synchronously cleanup projected state
    -> one non-throwing publication
```

因此小规模Selection可以复用延迟失效；大型Selection仍只把整个terminal产生的debt代入统一25%
公式，不因绝对行数获得特殊规则。两者不是两套长期Index，只是同一Index在一个terminal内是否完成
同步整理的physical choice。无论选择哪条路径，operation都只能发布一次logical generation；
read-only terminal不得触发结构性cleanup。

## 7. Query 合同

Key/Index exact-match查询：

```text
hash + typed probe value
    -> locate exact typed Bucket
        -> CLEAN
            -> trust exact count and canonical postings
        -> DIRTY
            -> traverse unique raw locator memberships
                -> resolve current tagged locator
                -> REMOVED: skip
                -> compare authoritative logical Key/Field with probe
                    -> equal: return candidate
                    -> unequal: skip stale membership
```

只有DIRTY Bucket中的非removed candidate必须进行完整generated typed equality recheck。`INDEX_DIRTY`
只记录row-level cleanup debt；Bucket的`CLEAN`不变量授权clean fast path。Generated/specialized
kernel可以融合locator resolution、Column read和equality，但不能把Hash相等当成logical equality。
查询不得：

- 因为发现 stale membership 向普通用户报错；
- 返回 removed Record；
- 返回与 probe 当前值不一致的 Record；
- 因同一 `(exact typed Bucket identity, raw locator)` 重复 membership 返回重复结果；
- 把 hash collision 当作 equality；
- 改变 nullable Index 与 Join null-never-match 的既有逻辑合同。

Table scan绑定root时先选择一次执行路径：removed count为零时直接复用当前dense kernel；否则通过
Locator State按page/word跳过removed，不能强迫clean path逐row读取flags。Cleanup前的canonical order
固定为raw locator ascending并跳过removed；cleanup使用deterministic mapping形成新的raw locator
order。Table仍不承诺insertion或business order，依赖顺序的application必须显式sort。

## 8. 同步 Cleanup

每个 Table 记录至少以下内部统计：

- removed locator 数量；
- stale Key/Index membership 精确数量；
- physical high watermark 与 logical live size；
- live Key/Index membership 数量；
- 当前retained bytes与一次cleanup的conservative temporary peak；
- Index candidate visited / logical match read amplification只作为观测指标，不参与第一版trigger。

Maintenance hook只在一次mutation terminal的全部data work完成、第一次physical write之前执行一次。
它读取mutation过程已经增量维护的projected counters，而不是逐Record、逐Bucket或逐Index检查。
未触发cleanup的hook必须是`O(1)` fixed decision path，不分配与debt规模成正比的对象：

```text
project mutation result
    -> calculate projected debt and resource state
        -> threshold not reached
            -> publish delayed state once
        -> threshold reached
            -> preflight complete cleanup candidate memory
            -> compact projected live payload
            -> assign deterministic new raw locators
            -> rebuild the same Key/Index structures from authoritative Columns
            -> clear REMOVED / INDEX_DIRTY
            -> reset mutation statistics
            -> publish cleaned state once
```

候选 StateRoot/Index 只是 cleanup operation 的临时构造物，用于 fail-closed 和 atomic publication；
它不是长期并存的第二套查询机制。Cleanup 失败时旧 published state 必须完整可用，不能留下 partial
compaction、partial Index 或错误 accounting。一个terminal不能先publish delayed state再publish cleaned
state；最终Result只对应一个logical generation。

第一版只使用一项统一的内部阈值：

```text
structuralDebt
    = removedRowCount
    + staleKeyAndIndexMembershipCount

cleanStructure
    = liveRowCount
    + liveKeyAndIndexMembershipCount

cleanupThreshold
    = ceil(cleanStructure / 4)

shouldCleanup
    = structuralDebt > 0
    && structuralDebt >= cleanupThreshold
```

当 `structuralDebt > 0 && structuralDebt >= cleanupThreshold` 时，本次 mutation terminal 在同一个
publication之前同步cleanup；否则发布延迟状态。也就是说，默认阈值为clean structure的`25%`。同一个row反复修改
indexed Field时，每个新产生的stale membership都增加debt，不能只按distinct dirty row计数；remove
同时形成row hole和stale memberships时，两类物理债务分别计数。所有加法、乘除和rounding使用checked
long-domain arithmetic。

Point mutation和Selection mutation使用完全相同的公式。Selection只是在一次terminal中可能产生更大
的projected debt，因此自然更容易达到阈值；不再设置Selection专属绝对量、比例或多档policy。

`physicalHighWatermark == capacity && liveSize < capacity`且add需要fresh locator，是必须cleanup的
progress condition。若继续发布延迟状态将使未来cleanup无法通过managed-memory preflight，也必须
立即cleanup；cleanup本身仍无法准入则fail closed。除此以外，第一版不使用query amplification、
workload ratio、表规模分档或动态自适应trigger。`25%`是内部implementation policy，不进入public
API或compatibility ABI；未来只有独立profile evidence才能修改这个单一常量。阈值调整不能改变
logical result/order/failure taxonomy，read-only terminal不得触发cleanup。

不允许 background maintenance 或不可解释的异步状态变化。

## 9. 接受的代价与风险

该方案是明确的、有预算的空间与固定成本换取mutation时间：

| 代价/风险 | 当前处理原则 |
|---|---|
| stale posting 占用内存 | 由 managed accounting 和统一25% cleanup threshold约束 |
| maintenance hook 固定成本 | 每个mutation terminal一次`O(1)`counter/policy判断；不扫描data |
| DIRTY Bucket需要Column recheck | generated typed comparison；只影响dirty path；amplification仅观测 |
| exact Bucket identity snapshot | 只复制indexed Field所需typed leaves；managed accounting；不取代Column Owner |
| posting container metadata | 允许少量inline/block metadata；以复杂度与retained profile证明收益 |
| cleanup 产生延迟峰值 | 同步、资源预检、可观测、失败时零发布 |
| removed hole 使 physical size 与 live size 分离 | 明确 high watermark/live size/capacity Owner，不复用 locator |
| 59-bit raw locator | 现实容量充足；所有 arithmetic/mask 必须 checked |
| Locator State | logical tagged encoding + lazy sparse pages；clean root不承担逐 row `long[]` |
| 当前formal dense-compaction baseline发生变化 | 本专题已按M2由Product Owner批准；完成implementation/evidence后才晋升项目正式Design |

### 9.1 成本接受原则

本专题不要求candidate在机器指令、对象数或retained byte上逐项等于baseline。允许：

- terminal/root/Chunk边界增加少量clean/dirty判断；
- mutation更新debt counters、Bucket state和lazy flags；
- posting container、typed identity与cleanup statistics产生可计量的retained metadata；
- dirty state执行必要的candidate recheck和canonical filtering。

不允许：

- ordinary add因Bucket cardinality增长；
- clean Key/Index count从平均`O(1)`退化为`O(matches)`；
- clean Table/Field/Join/parallel无条件逐row解析Locator State；
- posting/locator metadata出现未被端到端收益证明的显著永久放大；
- mutation微基准变快但主要真实journey或clean query family显著变慢。

最终判断关注复杂度、clean/dirty分层、总体延迟、retained/temporary/allocation和cleanup摊销，而不是
追求不现实的字面“零成本抽象”。

### 9.2 Performance non-regression Gate

在fixed host、同一Java 8/JVM参数、相同schema/data fingerprint下，candidate必须同时满足：

| Family | Gate |
|---|---|
| ordinary add | 摊销`O(Index count)`；fresh locator不执行Bucket membership scan |
| clean Key find/get | 保持平均`O(1)`，无逐candidate recheck |
| clean Index exact count | 保持`O(1)`，使用exact Bucket count |
| clean Index iteration | 保持平均`O(1 + matches)`与canonical order |
| clean Table/Field/Join/parallel | 复用dense/current fast path；不逐row读取flags |
| indexed update | 不扫描whole Table/Index；large-Bucket ensure为`O(log B)`或更好 |
| remove | 不重建whole Table/Index；只标记状态、Bucket debt和reference clearing |
| dirty query | 在统一25%debt边界下量化candidate/match amplification；明显退化则qualification失败 |
| retained/temporary | 全部新增bytes被managed accounting覆盖；无未解释的显著永久放大 |
| cleanup | 一次terminal最多一次；preflight；一个publication；摊销后journey仍更快 |

测量同时对照正式clean baseline与HANDOFF记录的立即维护checkpoint。单项clean family超过稳定噪声带
或约`5%`的退化必须解释并修复；复杂度退化不受噪声豁免。`5%`是本专题qualification ratchet，
不是跨硬件public SLA。100,000-operation FJSP journey必须不慢于可重放的立即维护checkpoint，并以
显著改善为目标；dirty-worktree历史秒数只能在S0重建相同checkpoint后转为正式comparison evidence。

## 10. 明确排除

下一治理的默认范围不包括：

- Delta Index 或双 Index query merge；
- stable/public Row ID；
- public tombstone、locator、compact 或 optimize API；
- Segment、off-heap、mmap 或 spill；
- reverse Index、per-record Index membership directory；
- `prev` posting link；
- 当前未完成的`LocatorBitmap` accelerator实验及任何list+accelerator双写结构；
- 同一Bucket并行持有两个canonical membership container；
- background cleanup、maintenance thread 或 asynchronous publication；
- JSON parser、JSON public API 或 internal IR serialization；
- workflow/query DAG implementation、scheduler、cache 或 persisted plan；
- 为后续 Logical IR 治理预建第二套 IR、placeholder module/interface；
- 改变 Key immutable、structured failure、Table-local atomicity或external Group serialization。

任何排除项只有在第一轮正确实现与 profile 明确证明必要后，才能重新建立独立候选专题；不能作为
本轮实现中的顺手补丁。

## 11. 下一治理专题的实施顺序

### S0 — Worktree 与事实基线

- 保留 scheduling refactor，不覆盖其他用户修改；
- 移除或隔离未完成、未资格化的 `LocatorBitmap` experiment；
- 固定当前立即-maintenance实现的测试与性能基线；
- 证明现有 `per-record next locator` 为什么不能承载多 Bucket membership。

### S1 — Design delta 与不变量

- 审查 Blueprint 是否不变；
- 建立 Storage、Architecture、Execution、Failure 与 Core proof-chain delta matrix，不提前修改正式
  Design正文；
- 将 `Typed Unique Hash Index` 与 `Typed Hash-backed Inverted Index` 固定为本专题结构合同；
- 保持 Key 与 secondary Index 的语义 Owner 分离，但允许共享 internal typed Hash directory
  implementation；
- 对照后续 [Logical IR 治理意图](FUTURE-LOGICAL-IR-GOVERNANCE.md)，证明新的 logical descriptor
  不直接引用 physical Hash/Posting/Locator-state implementation；
- 固定exact typed Bucket identity、clean/dirty fast path、locator encoding与lazy physical state、
  live/high-watermark/capacity、Key re-add、order、cleanup trigger和atomicity；
- 以本专题已批准Design作为implementation输入；稳定事实只在qualification关闭后晋升正式Owner。

### S2 — Key/Index representation

- 为Key建立typed unique Hash path：hash collision由exact typed Bucket identity分离；DIRTY Bucket
  authoritative equality保证`0..1` live logical match；
- 为secondary Index建立exact typed directory -> one canonical posting container的inverted path；
- clean Bucket保留exact count/head/ordered iteration；
- fresh add直接append；historical ensure为`O(log B)`或更好；
- 建立`(exact typed Bucket identity, raw locator)` membership uniqueness；
- Bucket identity只保存Field所需generated typed snapshot，不保存整行或ordinary application Object；
- 证明container retained bytes、clean fast path和large-Bucket复杂度；
- 保持collision、null、Value flatten authoritative equality和managed accounting。

### S3 — 延迟 point update/remove

- non-indexed update fast path；
- indexed update append/ensure + dirty publication；
- remove flag publication与reference leaves立即清空；
- clean root/Bucket复用当前fast path；dirty scan/Key/Index/query/reference/optimized/parallel全路径过滤；
- logical plan保持storage-neutral；removed filtering与authoritative candidate recheck只进入bound
  physical execution；
- cleanup 前禁止 locator reuse。

### S4 — 同步 cleanup 与 failure closure

- terminal-tail hook、统一25% structural-debt threshold与两个强制cleanup条件；
- live-row compaction和同一 Key/Index rebuild；
- complete preflight、fault injection、candidate disposal；
- version、capacity、managed retained/temporary accounting。

### S5 — 正确性与性能资格

- reference-vs-optimized-vs-parallel differential；
- mutation state-machine/property tests；
- high/low cardinality、clean/dirty/threshold-minus-one/post-clean Bucket；
- 10K/1M/10M clean family和100K-operation FJSP composed journey；
- profile mutation cost、query read amplification、stale memory 和 cleanup peak；
- 对照formal clean baseline与可重放立即维护checkpoint执行non-regression Gate；
- 达到 exit 后更新 Conformance、退役 Temporary，再恢复 scheduling 专题。

## 12. 必须覆盖的正确性矩阵

至少包括：

- add 与 duplicate Key；
- indexed/non-indexed/no-op update；
- `READY -> RUNNING -> COMPLETE -> READY`，证明 Bucket membership 不重复；
- 同一locator同时存在于同一Index的多个exact typed Bucket；不同logical value发生same-hash
  collision时仍拥有不同Bucket；
- 一个 Table 多个 secondary Index，仅验证各自 authoritative Field；
- remove 后 find/get/remove/scan/Index/Join/GroupBy 均符合现有 logical absence；
- cleanup 前相同 Key remove 后重新 add；
- removed locator 不复用；
- null hash Bucket、Hash collision、nested Value authoritative equality；
- remove final commit立即清空全部reference leaves，不保留removed application object；
- `liveSize <= physicalHighWatermark <= capacity`、high-watermark exhaustion cleanup与true growth；
- sign bit恒零、flags/raw mask、`-1L` sentinel与raw-onlyaddress/order/hash；
- point与small/large Selection各自只运行一次terminal-tail maintenance hook；
- cleanup 前后 logical result、count、fingerprint 等价；
- CLEAN Bucket count/head/iteration不执行candidate recheck；DIRTY Bucket执行authoritative recheck；
- clean root不分配flags pages且复用dense primitive/row/parallel/Join path；
- canonical order deterministic；
- compression PLAIN/encoded/overlay interaction；
- retained/temporary accounting 与 reference clearing；
- validation、allocation、posting、payload、flags、cleanup、publish fault injection；
- structured failure 时 old generation 完整且无 partial publication。

## 13. Exit 标准

只有以下内容共同成立，专题才可以关闭：

1. 本专题Design、Design delta与Product Owner approval保持闭合；qualification完成后稳定合同晋升
   项目正式Design Owner；
2. runtime Key 只有一套 `Typed Unique Hash Index`；每个 secondary Index 只有一套
   `Typed Hash-backed Inverted Index`；全部访问路径共享唯一 raw locator identity；
3. Bucket以typed hash + exact typed identity区分logical value；identity只是Field-scoped derived routing
   snapshot，不取代Column、不保存整行或ordinary application Object；
4. 每个Bucket只有一个canonical posting container；fresh append为摊销`O(1)`，large-Bucket historical
   ensure为`O(log B)`或更好；不存在list+accelerator双写或Base/Delta query merge；
5. 未触发terminal-tail cleanup的ordinary add/indexed update/remove不执行whole-Table/whole-Index
   rebuild；
6. CLEAN root/Bucket保持current dense/direct fast path；stale/removed membership只让DIRTY路径付出
   filtering/recheck成本，且对全部读取和关系路径透明；removed reference leaves立即清空；
7. cleanup前不复用locator；live/high-watermark/capacity、tagged locator、cleanup后
   result/order/failure合同明确；
8. 每个mutation terminal至多执行一次maintenance hook并只发布一个logical generation；
9. atomic publication、zero-publication failure、resource/accounting 证明闭合；
10. differential/property/fault tests通过；
11. 统一25% structural-debt threshold及capacity/memory强制条件经过边界验证，profile证明mutation
    收益，同时量化query amplification、retained/temporary增量与cleanup peak；
12. 10K/1M/10M clean family通过non-regression Gate；100,000-operation FJSP journey正确完成、不慢于
    可重放立即维护checkpoint并取得显著mutation收益；
13. 新 Key/Index/locator-state implementation 没有把 physical identity 或 maintenance node 注入
    Logical IR，且没有让当前 frontend/runtime coupling 进一步扩大；
14. 正式 Conformance 成为唯一 current evidence Owner；
15. 本 Temporary 与未采纳实验完成 replacement closure 后退役。

## 14. 当前交接边界

当前 worktree 已包含一套未提交的立即 posting maintenance 实现，以及一个未完成、未测试、未资格化
的 `LocatorBitmap` experiment。它们不是本文专题Design已经实现的证据。精确checkpoint、测试、
profile 和 scheduling refactor 边界见 [`HANDOFF.md`](HANDOFF.md)。

下下次 Logical IR 治理的独立意图见
[`FUTURE-LOGICAL-IR-GOVERNANCE.md`](FUTURE-LOGICAL-IR-GOVERNANCE.md)；本文不提前宣告其 Design
或 implementation readiness。

下一专题开始前不得：

- 把当前 dirty implementation 宣称为本文方案；
- 继续补完 bitmap experiment；
- 把本专题Design直接描述成已经实现或已成为项目正式Design；
- 在没有新 evidence 的情况下反复运行完整 qualification；
- 恢复 scheduling journey 并把 application workaround 当成 SOMA 最终最佳实践。
