# Runtime storage、Exact Access 与 IndexBuffer 设计

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 设计目标与约束

Runtime 继续采用：

> schema-specific generated binding + small annotation-agnostic primitive runtime kernel。

必须同时满足：

- live facts 只在 packed columns/presence/ownership relation 中；
- primary/unique/index 是 derived access structure，不复制 authoritative payload；
- hash 只缩小 candidate，generated full equality 决定相等；
- normal lookup/group traversal 不创建 key tuple、iterator、entry、boxed index 或 per-group object；
- mutation boundary eager 维护 access structures；
- read terminal 不触发 dirty/full-table rebuild；
- generated hot loop 静态绑定 leaf columns，不通过 metadata interpreter；
- all retained/transient primitive arrays受 runtime plan memory guard；
- release 后 runtime 不继续持有大数组。

## 2. 拟议 runtime 组合模型

```text
Generated XxxTable
  -> TableStore
       -> PackedRowSpace               // size/capacity/current Index domain
       -> ColumnStore                  // primitive/reference columns + presence
       -> PrimaryLocator?              // keyed only
       -> UniqueLocator[]              // @SomaUnique
       -> GroupedExactIndex[]          // @SomaIndex
       -> AccessPath                   // scan / exact unique / exact group
       -> OperationBuffers             // reusable IndexBuffer + auxiliary scratch
       -> MutationCoordinator
       -> LifecycleState / stats
```

不再存在：

- `SparseIntKeySpace`；
- stable Entity id/directory；
- `RowPermutationSidecar` 作为 index/order canonical structure；
- dirty/lazy selector lifecycle；
- maintained order access path。

## 3. Packed RowSpace 与 Index

稳定状态：

```text
live Index = [0, size)
0 <= size <= capacity
```

Index 是所有 column、presence、locator target、index row link 和 child handle column 的当前共同坐标。它只在当前 structural epoch 有效。

规则：

- append 在旧 `size` 之后增加 Index；
- clear 令 `size=0` 并清理 live reference/presence/access facts，可保留 capacity；
- remove 使用 tail-fill 恢复 `[0,newSize)`；
- moved survivor 的 columns、presence、primary/unique locator、group row links、owner token 和 child handles 同步 relocate；
- structural commit 只提升一次 epoch；
- 默认 scan只遍历`0..size-1`，没有persistent tombstone/hole。

## 4. PrimaryLocator

### 4.1 语义

PrimaryLocator 实现：

```text
canonical @SomaKey value -> current Index or missing
```

它不生成 Entity id，也不要求 key domain 与 table capacity 成比例。

### 4.2 建议物理材料

按 key shape 使用 primitive open addressing：

| Key shape | Hash/probe material | Full equality |
|---|---|---|
| boolean/byte/short/int/float/enum | primitive int key/hash/state/index arrays | primitive canonical value |
| long/double | primitive long key/hash/state/index arrays | primitive canonical value |
| Value/composite | primitive hash/state/index arrays | generated leaf-by-leaf comparison against columns |
| String | 保持待决 breadth；若支持则保存/读取reference leaf并做`String.equals` | generated null/length/value equality |

Open addressing 至少区分 `EMPTY/LIVE/DELETED`。Remove 可以写 tombstone；rehash 在后续 mutation prepare/growth boundary发生，不能由 read lookup触发。

### 4.3 Collision safety

Composite/value key lookup：

```text
compute canonical hash from query leaves
  -> probe same-hash candidate bucket
  -> read candidate current Index
  -> generated typed comparison(query leaves, column leaves[Index])
  -> equal: found
  -> not equal: continue probe
```

禁止：

- 把 64-bit hash 当唯一 identity；
- 创建 transient `Object[]`/tuple/value wrapper做 lookup；
- 调用 arbitrary user `hashCode/equals` 取代 normalized semantics；
- collision 时错误报告 duplicate/missing。

## 5. UniqueLocator

UniqueLocator 与 PrimaryLocator 可以复用 raw hash substrate，但具有不同 logical owner：

```text
canonical @SomaUnique selector -> current Index or missing
```

差异：

- selector field允许更新；
- 不生成key direct CRUD或Map identity；
- conflict code是`unique_constraint_violation`；
- multi-row update需要基于最终staged state验证；
- 一个table可以有多个UniqueLocator。

物理上每个 LIVE bucket只需保存hash/state/current Index；selector value仍从authoritative columns读取。Rehash按current live rows重新插入locator，不复制selector object。

## 6. GroupedExactIndex

### 6.1 目标

GroupedExactIndex 实现：

```text
canonical @SomaIndex selector -> zero or one group -> 0..K current Index
```

它必须支持：

- expected O(1) group locate；
- O(K) group traversal；
- append时link；
- selector update时unlink old/link new；
- remove时unlink；
- swap-remove时O(1) relocate moved row link；
- group create/delete与hash bucket rehash；
- 不承诺group内顺序。

### 6.2 建议 primitive layout

每个 non-unique index 使用三组平行 primitive arrays。

Hash buckets：

```text
byte[] bucketState        // EMPTY/LIVE/DELETED
long[] bucketHash
int[]  bucketGroupId
```

Stable-within-index group records：

```text
long[] groupHash
int[]  groupHeadIndex
int[]  groupSize
int[]  groupFreeNext
```

Per-current-row membership：

```text
int[] rowGroupId
int[] rowNextIndex
int[] rowPreviousIndex
```

`groupId` 是该 index instance 内部的可回收 slot，不是 public identity，也不跨 release。把 bucket 与 group record 分离的理由：bucket rehash 只重排 `bucketGroupId`，无需改写每个 row 的 `rowGroupId`。

### 6.3 Group equality

每个 live group 至少有一个 head Index。查找同 hash group 时：

```text
query selector leaves
  -> bucket hash candidate
  -> groupHeadIndex
  -> generated full equality(query, selector columns[head])
```

同一 group 的所有 member 在 stable state 中必须拥有相同 canonical selector。Head被unlink时更新head；最后一个member删除时移除bucket并回收groupId。

### 6.4 Link/unlink/relocate

建议 group 使用双向 row link：

- `link(index, groupId)`：写row group/prev/next，更新head/size；
- `unlink(index)`：通过rowGroup/prev/next O(1)移除，必要时更新head/delete group；
- `relocate(oldIndex,newIndex)`：把membership arrays从old搬到new，并让prev/next/head中原来指向old的引用改为new；
- clear/release：逻辑清空或丢弃全部arrays。

Link可以采用head insertion；它会改变group枚举顺序，但该顺序本来就不是contract。禁止为了维持插入顺序额外引入stable compaction或per-group growable object array。

### 6.5 Growth 与 rehash

Group/bucket/row arrays分别受以下量影响：

- row membership capacity：table capacity；
- group record capacity：distinct selector value count；
- bucket capacity：group count/load factor；

Mutation prepare必须：

1. 估算新增rows和distinct groups；
2. stage所需新arrays或rehash buckets；
3.完成duplicate/resource/overflow precheck；
4.一次publish capacity/rehash结果；
5.再执行不会产生expected failure的link commit。

Rehash成本发生在mutation boundary并进入stats/benchmark；它不是“访问时全表重建”。Bulk `replaceAll`可以直接构建一套fresh exact structures，再与fresh columns一起publish。

## 7. Generated code 与 runtime kernel 分工

Runtime kernel拥有：

- bucket/group/link primitive arrays；
- capacity、load、tombstone、rehash、memory accounting；
- raw probe cursor/slot iteration；
- link/unlink/relocate structural primitives；
- stats counter和invariant checks。

Generated code拥有：

- selector leaf hash extraction；
- strict floating validation/canonicalization；
- full leaf equality；
- schema-specific column binding；
- exact source parameter binding；
- mutation时哪些selector受field change影响；
- query/duplicate error path中的logical table/selector name。

禁止把selector metadata、field path string或dtype switch放入per-probe/per-rowinner loop。Generated/runtime protocol可以提供窄的raw substrate，但application public signature不能暴露bucket/group/link type。

## 8. AccessPath 与零复制 group traversal

AccessPath 只抽象“如何获得下一个 current Index”：

```text
PackedScanPath       -> index++ until size
SingleIndexPath      -> 0/1 locator result
GroupedIndexPath     -> groupHead, rowNext, ...
```

Terminal执行期间同table structural mutation/reentrant access被lifecycle阻止，因此path可以直接遍历live primitive arrays，无需先复制整个group。

只有以下情况需要freeze到IndexBuffer：

- dynamic general sort；
- mutation terminal需要固定candidate set；
- `rowIndexes()`需要detached copy；
- 某些materialization/budget preflight需要完整count且不能安全双遍历；
- 未来top-k算法需要bounded heap/buffer。

以下路径可以stream：

- `count/anyMatch/noneMatch/forEach` without sort；
- `first/findFirst` without sort；
- `filter/skip/limit` before terminal；
- `sorted(...).firstOrThrow()` stable arg-min；
- exact index/unique membership traversal。

## 9. IndexBuffer

### 9.1 Logical shape

概念接口：

```text
IndexBuffer
  int[] values
  int size

ensureCapacity(required, budget)
append(index)
get(position)
set(position,index)
sort/compact helpers
reset()            // size = 0 only
release()          // values = empty
```

不要求以一个Java public class暴露；generated table storage可以内联等价字段，只要统一遵守该生命周期和命名。

### 9.2 Ownership

每个table instance拥有自己的operation buffers。建议最小角色：

| Buffer | 用途 | 可复用关系 |
|---|---|---|
| candidate | general sort、mutation freeze、row index export staging | terminal结束reset |
| auxiliary | stable merge sort或top-k辅助 | 不需要时size=0 |
| mutation delta | remove sorted indexes、unique/index delta | 可与candidate在phase不重叠时复用 |

“统一使用IndexBuffer”不表示整个table只能有一个`int[]`。一个sort同时需要input与auxiliary；update field staging、presence scratch和hash bucket staging也不是IndexBuffer。

### 9.3 Lifecycle

```text
terminal begin
  -> acquire table-local role buffers
  -> ensure capacity before user callback/publish
  -> execute
  -> finally reset logical size
table clear
  -> reset size, normally retain arrays
table release
  -> drop retained arrays and storage accounting
```

Single-owner/reentrant rules保证同一table不会并发租用buffer。Table A callback可以访问Table B，二者使用各自buffer；B不得reenter A。

`reset()`不需要逐元素清零primitive int。若buffer暂存reference则不属于IndexBuffer；reference scratch必须清理dead reachability。

### 9.4 Capacity 与 memory guard

- growth使用checked arithmetic；
- new array分配前检查`maximumOperationScratchBytes`和table/aggregate storage；
- current/high-water记录retained arrays与growth transient peak；
- first growth允许allocation，steady-state容量足够时不再分配；
- 极端high-water不在普通terminal内自动shrink；release必须归零。

## 10. Row Pipeline plan allocation

### 10.1 需要保留的public语义

Row Pipeline one-shot且intermediate alias consumed：

```java
Rows a = table.rows();
Rows b = a.filter(...);
// a is consumed; b is current
```

因此直接返回同一个mutable `Rows` instance会让旧alias重新看到新plan，破坏语义。

### 10.2 建议实现

```text
PipelinePlanOwner
  source
  stage storage (small inline / retained arrays)
  stageCount
  generation

Rows wrapper
  owner
  expectedGeneration
```

Intermediate：

1. validate input；
2.校验wrapper generation仍current；
3.向owner追加stage，不复制已有stage arrays；
4.增加generation；
5.返回一个新的小wrapper，旧wrapper因generation不匹配而consumed。

可接受的allocation：每个chain/intermediate一个small wrapper和application lambda。不可接受：每个stage复制`kinds/predicates/comparators/counts/seen`全部arrays，或每个candidate创建stage/cursor对象。

### 10.3 Stage storage

- common small stage count使用inline slots或一次capacity=4的arrays；
- overflow按geometric growth一次stage；
- `seen`计数是terminal-local primitive scratch，不应与immutable stage plan复制；
- wrapper/plan不可跨线程；terminal后owner进入consumed/failed-consumed。

## 11. Terminal执行策略

| Pipeline shape | 推荐执行 |
|---|---|
| no sort + read short-circuit | direct source streaming |
| no sort + `forEach/count` | fused source streaming |
| one sort + first/limit(1) | stable arg-min，保留first equal candidate |
| general sort + fetch/forEach | candidate IndexBuffer + primitive stable sort |
| sorted + limit(k) | baseline full sort；只有exact semantics evidence后才top-k |
| update | freeze matched Index -> field staging ->validate/access prepare -> publish |
| remove | freeze matched Index -> sort selected Index -> tail-fill commit |
| rowIndexes | detached `int[]` copy；public epoch问题仍待决 |

Comparator使用reusable left/right cursor或等价direct column binding，不materialize row/value object。Comparator callback必须处于same-table callback scope，不能reenter table或修改facts。

## 12. 复杂度与内存模型

令：

- `N`=table rows；
- `K`=exact group rows；
- `M`=filter matched rows；
- `U`=unique/index count；
- `G`=一个GroupedExactIndex distinct groups。

| Operation | Expected time | Retained material |
|---|---:|---:|
| primary/unique exact lookup | expected O(1)，collision worst O(bucket probe) | O(bucket capacity) |
| non-unique exact lookup | expected O(1)+O(K) traversal | O(bucket+G+N row links) per index |
| default scan/filter | O(N) | no candidate buffer when streamable |
| group filter | O(K) | no candidate buffer when streamable |
| general dynamic sort | O(M log M) | O(M) candidate + auxiliary high-water |
| sorted first arg-min | O(M) | O(1) selected Index after buffer warmup |
| selector update | expected O(U affected probes/links) per changed row | mutation delta scratch |
| rehash | O(live keys/groups) at mutation boundary | old+new transient arrays |

Worst-case hash behavior仍可能退化；stats/benchmark必须记录load、probe、collision和rehash。设计目标是消除hidden full-table sort，不是声称hash拥有严格worst-case O(1)。

## 13. Allocation 与 GC 边界

Steady-state non-materializing path禁止：

- per-row Cursor/Iterator/Optional/Integer；
- `Map<Key,List<Integer>>`/`Map<Hash,int[]>` group object graph；
- transient composite key/value tuple；
- stage-by-stage candidate array；
- read-time sidecar rebuild arrays；
- per-row stats object/timer/map。

允许且必须单独度量：

- pipeline wrapper/lambda construction；
- IndexBuffer first growth；
- locator/index growth或rehash；
- replaceAll fresh structure build；
- materialized chosen row / Optional / List / Map；
- reference/String column payload本身；
- controlled diagnostic tooling。

`firstOrThrow()`返回schema carrier，本身是显式materialization allocation boundary。是否增加allocation-free first API属于`O-05`，不能靠runtime偷偷改变return semantics。

## 14. 不采用的方案

### 14.1 Key -> bounded Entity -> Sparse Set

不采用。Value/composite key无法自然映射为稳定bounded domain；collision/open-address mapping会形成第二层hash和扩容重映射，最终仍需维护key->entity与entity->Index两层locator。

### 14.2 `Map<HashValue,int[]>`

不作为canonical runtime：

- Java Map/Entry/group arrays造成object/header/GC；
- group append/update需要频繁resize/copy或额外logical size object；
- hash collision不能仅靠HashValue；
- swap-remove后所有group arrays中的Index需要定位和修复；
- 多个index会形成大量小数组和high-water retention。

### 14.3 全表sorted permutation

适合read-mostly ordered access，但不适合本专题 exact lookup + mutation-heavy frontier。它在dirty后需要全表sort，正是要消除的first-access spike。

### 14.4 B+ tree / skip list

它们为range/order付出pointer/object或复杂node维护成本，而range/order已经明确不在baseline。Exact-only使用primitive hash更直接。

### 14.5 每个stage一份IndexBuffer

不采用。它把逻辑序列变换错误实现为L1/L2/L3全复制，增加memory traffic和scratch high-water；fused streaming与terminal-specific materialization更合适。

## 15. Internal invariant

每个stable state至少检查：

- PrimaryLocator live entry与keyed rows一一对应；
- UniqueLocator live entry与unique selector rows一一对应；
- GroupedExactIndex每个live row恰好出现在一个对应group；
- group size/head/prev/next无cycle、无dangling、count一致；
- bucket hash/groupId与head selector full equality一致；
- row move后没有locator/link指向`[size,capacity)` dead Index；
- reference dead slots清空；
- operation buffer不处于leased state，terminal结束logical size为0；
- read access不会触发structure rebuild或allocation（capacity已准备时）。

## 16. 设计审查清单

- [ ] 是否接受primitive group record + per-row双向link的内存开销？
- [ ] 是否同意group order完全internal，以换取O(1) unlink/relocate？
- [ ] 是否接受rehash/full fresh build只发生在mutation/bulk boundary？
- [ ] 是否同意IndexBuffer是角色统一而非单数组唯一？
- [ ] 是否接受pipeline每个intermediate仍可能有一个small wrapper allocation，以保持alias-consumed语义？
- [ ] selector String/reference breadth是否保持当前边界，留待独立决策？
