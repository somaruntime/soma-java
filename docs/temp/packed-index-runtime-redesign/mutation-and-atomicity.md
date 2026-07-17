# Mutation、Swap-remove 与失败原子性设计

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 目标

本文设计 append/addBatch、update、remove、clear、replaceAll 如何同时维护：

- packed columns/presence；
- PrimaryLocator；
- UniqueLocator；
- GroupedExactIndex；
- child owner token/handle；
- size/capacity/epoch；
- resource accounting 与 stats；
- expected failure all-or-nothing。

核心原则：

> 所有可能的 validation、duplicate、capacity、memory、pin、callback 与 child-cascade expected failure 都发生在 visible commit 前；commit phase 不分配、不调用 application callback、不产生 expected failure。

## 2. Mutation phase model

统一 phase：

```text
CHECK
  -> FREEZE / STAGE INPUT
  -> VALIDATE FINAL LOGICAL STATE
  -> PREPARE CAPACITY / REHASH / CHILD CASCADE
  -> COMMIT BASE + ACCESS FACTS
  -> CLEAR DEAD STORAGE
  -> PUBLISH size / epoch / stats
```

### 2.1 CHECK

- table active、single-owner、not reentrant；
- structural operation检查ColumnView/affected child subtree pin；
- argument/count/index范围；
- checked arithmetic与plan protocol；
- key immutability和selector value canonicalization入口。

### 2.2 FREEZE / STAGE

- Pipeline mutation先冻结matched current Index；
- update把可变field/presence写到primitive staging；
- remove把selected Index写到IndexBuffer；
- Batch/replaceAll使用detached primitive batch arrays；
- child replacement/cascade先建立detached plan。

### 2.3 VALIDATE FINAL LOGICAL STATE

- required/optional/default/null/floating rules；
- key/unique duplicate；
- selector group membership；
- child owner/map-key/cycle；
- final row count和resource estimate。

### 2.4 PREPARE

- columns/presence capacity group staging；
- locator/index bucket/group/row-link capacity；
- rehash arrays；
- mutation delta IndexBuffer/hash scratch；
- aggregate/table memory quota；
- child cascade/replacement preflight。

### 2.5 COMMIT

Commit只执行预先证明不会expected-fail的primitive/reference assignments与counter updates。Runtime内部impossible state必须转`internal_invariant_violation`并终止aggregate信任，不能伪装为可恢复invalid input。

## 3. Append / addBatch

### 3.1 Prepare

对新增`B`行：

1. 计算`newSize = oldSize + B`并检查overflow；
2. stage columns/presence/row-link capacity到`newSize`；
3.校验batch内部和existing rows的primary/unique duplicate；
4.计算每个GroupedExactIndex新增distinct group上界；
5. stage locator/index bucket/group growth或rehash；
6. child subtree全部stage/validate；
7.确认table/aggregate storage与bulk scratch budget。

Duplicate校验不能通过临时Java `HashSet<Value>`完成。建议使用primitive mutation-local locator/delta arrays，并复用generated full equality。

### 3.2 Commit

```text
copy B rows to columns[oldSize, newSize)
  -> publish presence/child owner material for new rows
  -> insert PrimaryLocator entries
  -> insert UniqueLocator entries
  -> link GroupedExactIndex memberships
  -> publish child handles/registry entries
  -> commit size=newSize and structuralEpoch+1 once
```

在`size` commit前，new indexes不对public read可见；same-table access被operation scope阻止。Commit前所有insert slots/capacity已准备好，不能在中途rehash或allocation。

Empty addBatch是no-op，不提升epoch。

## 4. replaceAll

`replaceAll`适合直接构建fresh table state：

```text
detached Batch
  -> fresh columns/presence
  -> fresh PrimaryLocator / UniqueLocator / GroupedExactIndex
  -> fresh child subtree/registry plan
  -> full validation and resource admission
  -> one publish swap
  -> release old retained state
```

规则：

- 旧table facts在fresh build完成前保持current；
- duplicate/index group validation基于fresh state；
- 不存在“先把old sidecar标dirty，下一次read重建”；
- 成功后structural epoch提升一次；
- 相同logical facts是否算visible change沿正式correctness/API Owner决定，本专题不顺带改变；
- old child subtree release必须descendants-first且不产生orphan；
- old arrays cleanup不得覆盖new publication或引入expected failure。

Fresh build是明确bulk mutation成本，不是hidden read rebuild。Stats/benchmark应命名为`bulk index build`，不复用`sidecarRebuild`语义。

## 5. Update terminal

### 5.1 Candidate freeze

Terminal开始时按source/stage语义得到matched current Index，写入candidate IndexBuffer。之后：

- callback修改selector后不会让row重新进入/退出本次candidate set；
- 每个current Index最多出现一次；
- IndexBuffer绑定terminal开始的structural epoch；
- callback期间same-table外部access继续被拒绝。

### 5.2 Field staging

对每个matched row维护：

- touched field bits；
- staged primitive/reference leaf；
- optional presence；
- final changed判断。

Callback只观察pre-terminal row facts与当前row自己的staged writes。任一callback失败：

- live columns/presence不变；
- locators/indexes不变；
- epoch不变；
- committed changed=0；
- attempted scanned/matched进入failure stats。

### 5.3 Primary key

`@SomaKey`不生成setter，update不能改变PrimaryLocator membership。Identity change仍为delete+insert。

### 5.4 Unique final-state validation

建议采用最终staged state语义：

```text
live unique mapping
  - old values of all changed rows
  + new values of all changed rows
  -> must be unique
```

因此以下swap合法：

```text
row A: unique 1 -> 2
row B: unique 2 -> 1
```

校验流程：

1. 找出每个UniqueLocator中selector最终值发生变化的rows；
2. 在validation overlay中逻辑移除这些rows的old mapping；
3.按new value插入mutation-local primitive delta locator；
4. 检查与unaffected live rows冲突；
5. 检查changed rows彼此冲突；
6. stage live locator所需capacity/rehash；
7. commit时先remove all old，再insert all new。

逐row“检查新值然后立即更新live locator”会错误拒绝合法swap，并在后续callback失败时需要复杂rollback，因此不采用。

`P-03`仍是设计建议；若用户选择“逐row顺序语义且swap拒绝”，必须在正式API/correctness Owner明确，不能由hash实现偶然决定。

### 5.5 Non-unique index delta

对每个受影响GroupedExactIndex：

1. 找出selector final value变化的rows；
2.计算old group unlink与new group link计划；
3.预估新增distinct groups和tombstone/rehash；
4. stage bucket/group capacity；
5. commit时unlink all old memberships；
6. publish staged selector columns；
7. link all new memberships。

Unchanged selector的index不触碰。一个row多个普通field变化但selector不变，不得产生无意义unlink/link。

### 5.6 Commit order

建议commit：

```text
remove old unique/index memberships for changed selectors
  -> publish all staged columns/presence
  -> insert new unique/index memberships
  -> publish changed counters/stats
```

所有目标bucket/group slots已在prepare阶段确定；commit不调用user equality/hash、不allocation、不rehash。

普通fixed-width update不改变size/Index，因此不提升structural epoch。若formal lifecycle未来增加content epoch，应作为独立Owner决策。

## 6. Single-row swap-remove

删除current Index `r`：

```text
oldSize = N
last = N - 1

if r == last:
    unlink/delete row r access facts
    cascade child row r
    clear dead slot r
else:
    unlink/delete row r access facts
    cascade child row r
    move survivor last -> r
    repair survivor locator/index/child position
    clear dead slot last

size = N - 1
structuralEpoch++ once
```

Moved survivor的business identity、field values和owned child subtree不变，只是current Index改变。

`compacted`定义为实际移动的survivor row数，因此single remove只能为0或1。

## 7. Multi-row tail-fill remove

### 7.1 为什么不使用stable forward compaction

假设`N=100,000`，只删除Index 0。Stable forward compaction会移动99,999个survivor；在“不保证物理顺序”的新contract下，这个成本没有语义收益。

### 7.2 算法

输入：冻结的`selected[0..M)` current Index。

Prepare：

1. 验证`0 <= M <= N`；
2.对selected Index原地primitive sort ascending；
3.检测duplicate/out-of-range；
4. `newSize = N - M`；
5. `holes = selected values < newSize`；
6. tail domain固定为`[newSize, N)`，长度恰好是`M`；
7.在tail中区分selected rows和survivors；survivor数量必然等于holes数量；
8.完成pin、child cascade、access capacity和cleanup preflight。

Commit：

```text
unlink/delete access facts for every selected row
retire/cascade every selected row child subtree

holeCursor = first selected Index < newSize
for tailIndex from N-1 down to newSize:
    if tailIndex is selected:
        continue
    target = next hole
    move survivor tailIndex -> target
    repair all locators/index links/owner material
    compacted++

clear columns/presence/owner material in [newSize, N)
size = newSize
structuralEpoch++ once when M > 0
```

由于tail长度为`M`，不需要扫描整个table，也不需要`boolean[N]`remove marks。selected排序可以使用candidate IndexBuffer本身；remove不承诺保留candidate order。

### 7.3 示例

```text
N=10, selected={0,1}, newSize=8
tail survivors={8,9}
9 -> 0, 8 -> 1
removed=2, compacted=2

N=10, selected={8,9}, newSize=8
no holes below newSize
removed=2, compacted=0

N=10, selected={0,9}, newSize=8
tail selected={9}, tail survivor={8}
8 -> 0
removed=2, compacted=1
```

新建议不变量：

```text
0 <= compacted <= removed == matched <= scanned
```

这会收紧当前`compacted`可能大于`scanned`的旧语义，必须迁入Generated API Owner并更新golden/result tests。

## 8. Row move repair

对每个`from -> to` survivor move：

### 8.1 Base columns

- copy every flattened primitive/reference leaf；
- copy/clear optional presence；
- copy owner token和child handle columns；
- `to`原selected row facts已逻辑删除；
- `from`在最终tail clear中清理dead references。

### 8.2 PrimaryLocator

- locator key保持不变；
- 只把mapped Index从`from`改为`to`；
- composite locator使用prepare阶段找到的exact bucket/slot，不重建key object。

### 8.3 UniqueLocator

- selector value保持不变；
- 只把bucket target Index从`from`改为`to`。

### 8.4 GroupedExactIndex

`relocate(from,to)`：

- copy`rowGroupId/next/previous`到`to`；
- 如果group head是`from`，改为`to`；
- 让previous.next或next.previous从`from`指向`to`；
- 清理`from`membership slot；
- group membership和size不变。

### 8.5 Child ownership

Owner token/handle随row移动，但child registry中的stable owner token/generation不变。已获得的child facade绑定stable token/field/generation，不绑定packed Index，因此不会因parent row move重新指向别的child。

## 9. Direct delete、pipeline remove 与 clear共用kernel

- keyed `delete(key)`：PrimaryLocator locate后调用single-row remove；
- dense direct删除若public API存在：调用single-row remove；
- pipeline `remove()`：调用multi-row tail-fill；
- parent cascade delete：在同一kernel前增加subtree plan；
- `clear()`：相当于删除全部rows，但可以使用specialized clear，不逐row执行swap；
- `replaceAll(empty)`：specialized full clear + fresh empty structures。

共用kernel避免direct/pipeline/child删除形成不同packed/index语义。

## 10. clear

Clear preflight全部affected child subtree pin/cascade后：

- descendants-first release child rows；
- clear live reference columns和presence range；
- Primary/Unique/Grouped structures logical clear；
- `size=0`；
- 有visible rows时structural epoch提升一次；
- 保留column、locator/index和IndexBuffer capacity，除非plan显式支持trim；
- 不留下dirty structure或旧group traversal。

## 11. ColumnView、pipeline 与 epoch

- structural append/remove/clear/replace/reserve遇到conflictingactive view，在commit前返回`view_pinned`；
- ordinaryfixed-width update可在证明array/layout不变时允许；
- mutation terminal执行期间same-table pipeline/view/direct access返回`reentrant_access`；
- remove/append成功后旧Index全部视为epoch-sensitive invalid，即使某些数值碰巧仍指向同一row；
- pipeline construction不捕获epoch，terminal读取current state；
- mutation freeze后的IndexBuffer必须在同一terminal/epoch消费并finally reset；
- root release使all buffers/locators/indexes/columns归零并invalidate views/facades。

## 12. Failure atomicity矩阵

| Failure | 最晚发生phase | Visible state |
|---|---|---|
| invalid key/selector/null/floating | validation | unchanged |
| duplicate key/unique | final-state validation | unchanged |
| callback RuntimeException | field staging | unchanged，wrapped/preserved per error contract |
| memory limit/checked overflow | capacity prepare | unchanged |
| controlled allocation failure/raw OOME during staging | capacity prepare | oldarrays/size/epoch remain published |
| view/descendant pin | CHECK/PREPARE | unchanged |
| child replacement/cascade expected failure | PREPARE | oldsubtree/parent handle unchanged |
| internal invariant during commit | COMMIT | aggregatefail-fast；不得继续正常访问 |
| JVM fatal error | any | 原样传播；不承诺application recovery |

Commit phase不得执行可能返回duplicate/memory/pin/callback expected failure的动作。

## 13. Stats 与 operation result

语义上至少需要观测：

- rows scanned/matched/changed/removed；
- survivors moved；
- primary/unique/index structures affected；
- locator/index probes/collisions/rehashes；
- groups created/deleted；
- bulk fresh build rows/groups；
- mutation delta/index buffer current/high-water/transient bytes；
- failure phase/code。

但当前public `sidecarMaintained/sidecarRebuilt`字段不能直接重解释为上述指标。Exact Java result/stats shape由 [RuntimePlan、stats 与兼容性设计](plan-stats-and-compatibility.md) 中的待决项管理。

## 14. Complexity shape

令`M=removed/matched`，`H=holes/moved survivors`，`A=affected access structures`：

| Mutation | Target shape |
|---|---|
| single remove | O(columns + A locator/link repair)；`H<=1` |
| multi remove | O(M log M + M + H × (columns + A))；`H<=M` |
| update | O(M × touched columns + affected selector delta probes) |
| addBatch B | O(B × written columns/access insert) + explicit growth/rehash |
| replaceAll B | O(B × columns/access build) fresh staging |
| clear | O(live reference cleanup + child cascade + structure logical clear) |

Primitive selected sort的exact algorithm可由benchmark选择；如果引入O(M)mark/bitmap，必须证明其full-table clear/touched-word成本和memory更优，且仍使用统一IndexBuffer语义。

## 15. Differential/invariant场景

至少覆盖：

- remove first/middle/last/none/all；
- selected只在tail、只在head、head+tail、random sparse/dense；
- selected input arbitrary order与duplicate defense；
- key/unique/hashcollision下row move；
- indexgroup head/member/last-member删除；
- moved row同时是多个index的group head；
- selector update old/new same、new group、existing group、last old group；
- unique valid swap、cycle permutation、duplicate final state；
- callback/resource/pin failure no partial state；
- child facade在parent row move后仍指向same subtree；
- clear/release清理dead reference与retained current bytes；
- random operation trace与reference oracle membership/materialization一致。

## 16. 设计审查清单

- [ ] 是否接受multi-row unique按最终state校验并允许swap？
- [ ] 是否接受`compacted <= removed`的新result invariant？
- [ ] 是否同意remove先sort selected Index，再只扫描长度M的tail domain？
- [ ] 是否确认所有access rehash/growth都在mutation prepare而非read path？
- [ ] 是否确认child facade依赖stable token/generation，不依赖physical Index？
- [ ] 是否接受commit中internal impossible failure直接fail-fast，而不是复杂rollback？
