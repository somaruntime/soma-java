# RuntimePlan、Stats 与兼容性设计

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 问题定位

当前 public/runtime protocol围绕以下模型固化：

```text
SparseInt/Hash KeySpace
primitive-sorted-permutation sidecar
dirty-lazy-rebuild maintenance
maximumSparseKey / maximumSidecarScratchBytes
sidecar dirty/rebuild stats
UpdateResult/RemoveResult sidecar fields
```

新模型改为：

```text
hash-only primary locator family
eager unique/grouped exact access
no maintained order
table-local reusable IndexBuffer
mutation-boundary growth/rehash/bulk build
```

因此不能只替换internal class而保留旧plan/stats文字。那会让public diagnostics继续描述不存在的生命周期，也会诱导consumer等待“rebuild”。

## 2. Compatibility dimensions

本专题至少影响：

| Dimension | 变化 |
|---|---|
| handwritten annotation API | order annotation/type删除 |
| schema source | old `@SomaOrder` source不再合法 |
| normalized model/hash | order declaration退出；移除annotation后的schema产生新hash |
| generated public API | `byXxx()` order source删除；exact index source顺序contract变化 |
| runtime public API | `TablePlan`、`TableStats`、operation result可能变化 |
| generated-runtime protocol | sidecar/sparse protocol删除，locator/index protocol新增 |
| runtime behavior | index read不rebuild；dense/keyed remove不保序 |
| runtime plan hash | canonical dimensions与strategy identity变化 |
| examples/fixtures | order usage、expected sequence、API manifest/golden变化 |
| evidence artifacts | old dirty/rebuild/sparse lanes被新exact-index/swap-remove lanes替换 |

这些都是正式兼容性变更，不能归类为纯internal optimization。

## 3. Schema 与 processor identity

### 3.1 Old source

包含下列annotation的source在cutover后应compile fail，不能被静默忽略：

```text
@SomaOrder / @SomaOrders / @SomaSort / SomaDirection
```

Consumer migration：

1. 删除order declaration/import；
2.把generated `byXxx()`调用改为packed/exact source + explicit `.sorted(...)`，或application-owned heap；
3.重新生成schema resource/source；
4.审查所有`first/limit/fetchAll`顺序依赖；
5.更新schema hash golden和external consumer。

### 3.2 Schema hash

- order原本是logical schema fact；删除它会改变该schema的canonical model/hash；
- 不含order的其他logical schema，不应仅因internal hash structure变化而改变schema hash；
- key/index/unique canonical equality/floating semantics保持不变时，hash algorithm前缀无需仅为runtime algorithm变化而修改；
- generated/runtime/plan compatibility identity必须变化，以阻止旧generated code与新runtime混用。

## 4. RuntimePlan维度

### 4.1 建议保留

| Current dimension | 建议 | 原因 |
|---|---|---|
| table logical name / algorithm | 保留 | instance policy identity |
| initial capacity / growth ratio | 保留 | columns与all access arrays仍需growth |
| maximum update scratch | 保留 | field staging与unique/index delta |
| maximum operation scratch | 保留 | IndexBuffer/sort/remove scratch |
| maximum bulk scratch | 保留 | Batch/replaceAll/rehash/child staging |
| maximum table/aggregate storage | 保留 | columns+locators+indexes+retained buffers总guard |
| stats mode | 保留 | summary/diagnostic overhead boundary |
| default MaterializationBudget | 保留 | 与本专题正交 |

### 4.2 建议删除或收敛

| Current dimension/value | 建议 | 迁移理由 |
|---|---|---|
| `maximumSparseKey` | 删除 | 不再存在SparseInt策略或bounded domain |
| `sparse-int-v1` | 删除 | key locator只保留hash family |
| `primitive-sorted-permutation-v1` | 删除 | 不再使用sorted permutation exact access |
| `dirty-lazy-rebuild-v1` | 删除 | access structure固定eager/incremental |
| `sidecarMaintenancePolicy` | 删除 | V1 baseline不再暴露多maintenance policy |
| `maximumSidecarScratchBytes` | 删除 | retained index进table storage；growth/build scratch进bulk；sort/index buffers进operation |

### 4.3 建议的新strategy identity

逻辑维度可以收敛为：

```text
keySpaceStrategy:
  none
  hash-int-<next>
  hash-long-<next>
  hash-composite-<next>

accessStrategy:
  none
  primitive-exact-hash-<next>
```

是否把public getter `keySpaceStrategy`改名为`primaryLocatorStrategy`属于额外API命名变更。本专题建议优先避免不必要重命名：保留`keySpaceStrategy`术语但移除sparse value；正式术语可以在Owner中解释为primary key locator strategy。

`accessStrategy`只表达concrete exact access family，不再配套independent maintenance policy。Unique/grouped细分由normalized selector和generated binding确定，无需application逐selector配置。

### 4.4 Budget归属

| Material | Budget |
|---|---|
| current Primary/Unique/Grouped arrays | `maximumTableStorageBytes` + aggregate storage |
| IndexBuffer/sort auxiliary/remove selected | `maximumOperationScratchBytes`，retained current也计table storage |
| update field/unique/index delta | `maximumUpdateScratchBytes`，retained current也计table storage |
| rehash/fresh index/batch build transient | `maximumBulkScratchBytes` + table/aggregate peak admission |

不建议新增`maximumExactIndexScratchBytes`，除非benchmark证明bulk与index staging需要独立调优。先复用已有三个职责清晰的budget可以减少public plan复杂度。

### 4.5 Canonical plan hash

新canonical table entry应删除：

```text
maximumSparseKey
sidecarMaintenancePolicy
maximumSidecarScratchBytes
```

并让`accessStrategy`只接受`none/primitive-exact-hash-<next>`。Canonical key set变化意味着runtime plan protocol与hash变化；旧plan必须在create boundary以`runtime_plan_mismatch/invalid_runtime_plan`失败，不能忽略unknown old field。

## 5. Compatibility identity

当前正式baseline是generated/runtime/plan protocol v2 family。新cutover必须统一提升到下一generation：

```text
generated protocol        -> next generation
runtime compatibility     -> next generation
runtime plan protocol     -> next generation
```

Exact token（例如是否使用`v3`）在正式Owner迁移时一次确定；临时草案不先制造平行正式identity。

必须同步更新：

- generated metadata；
- runtime verification；
- RuntimePlan builder/default plan；
- canonical plan hash；
- public/generated-runtime manifest；
- match/mismatch fixtures；
- external Maven consumers；
- generated source golden；
- reports/evidence schema允许的identity。

Annotation artifact、processor、generated source与runtime必须成对cutover。不能让old order/sparse generated code调用new runtime，也不能让new exact-index generated code落到old sidecar runtime。

## 6. Stats model

### 6.1 继续保留的summary facts

- rows/capacity/structural epoch/released/active views；
- growth count；
- update/operation/bulk scratch current/high-water；
- last operation/outcome/error/scanned/matched/changed；
- child/materialization counters；
- primary hash capacity/used/probe/collision/rehash；
- runtime/schema/plan identity。

### 6.2 退出的sidecar facts

建议删除：

```text
sidecarDirtyCount
sidecarRebuildCount
sidecarRebuildRows
sidecarScratchCurrentBytes
sidecarScratchHighWaterBytes
```

原因：

- exact structures永远current，不存在dirty transition；
- read不rebuild；
- bulk fresh build与mutation rehash是不同事件，不能继续叫sidecar rebuild；
- general dynamic sort scratch已经属于operation/IndexBuffer metrics。

### 6.3 Exact access观测需求

至少在diagnostic/evidence层能够得到：

| Metric family | 用途 |
|---|---|
| locator/index count | table有多少primary/unique/non-unique结构 |
| bucket capacity/used/tombstone | load与rehash触发解释 |
| probes/collisions/rehashes | lookup/mutation cost解释 |
| grouped distinct groups/entries/max group size | cardinality/selectivity/memory解释 |
| link/unlink/relocate count | update/remove maintenance解释 |
| groups created/deleted | selector churn解释 |
| bulk build count/rows/groups | replaceAll/import成本解释 |
| current/high-water retained bytes |内存与GC retention解释 |
| rehash/bulk transient peak bytes | resource admission解释 |

这些指标可以先由diagnostic detail/per-selector evidence API提供，不必全部变成always-on `TableStats` getter。

### 6.4 建议public最小汇总

设计建议而非已决事实：

```text
exactIndexCount
exactIndexEntryCount
exactIndexGroupCount
exactIndexProbeCount
exactIndexCollisionCount
exactIndexRehashCount
exactIndexStorageCurrentBytes
exactIndexStorageHighWaterBytes
```

Primary locator继续使用现有key-space metrics，secondary unique/grouped聚合到exact-index metrics。Per-selector breakdown只在DIAGNOSTIC snapshot中返回immutable bounded detail，避免向`TableStats`无限添加getter。

是否采用这组exact getter属于`O-03`，正式实现前必须决定。

### 6.5 Reset

- reset清零probe/collision/rehash/link/unlink/build event counters；
- 不改变capacity/group membership/rows；
- current bytes不清零；
- high-water继续按现有lifetime policy，除非Owner明确改变；
- reset不得触发rehash、shrink或IndexBuffer trim。

## 7. UpdateResult / RemoveResult

当前shape：

```text
UpdateResult(scanned, matched, changed, sidecarMaintained, sidecarRebuilt)
RemoveResult(scanned, matched, removed, compacted,
             sidecarMaintained, sidecarRebuilt)
```

### 7.1 不可采用：静默重解释

不能把：

- `sidecarMaintained`静默改成“exact access structures maintained”；
- `sidecarRebuilt`静默改成“rehash/bulk build”。

方法名相同但计数单位和事件完全不同，是behavior/API breaking且会误导consumer。

### 7.2 选择A：直接简化（建议）

项目尚未对外发布时，建议operation result只承载logical outcome：

```text
UpdateResult(scanned, matched, changed)
RemoveResult(scanned, matched, removed, compacted)
```

Access maintenance进入TableStats/diagnostic evidence。优点：

- API简单；
- 不会让用户把implementation structure count当business result；
- 未来更换exact index algorithm无需再改operation result；
- `compacted`继续解释physical survivor moves。

缺点：现有consumer/golden需要迁移。

### 7.3 选择B：新增中性字段

```text
accessStructuresMaintained
accessStructuresRehashed
```

它保留operation-local成本提示，但仍需要精确定义primary/unique/index是否计数、一个structure多row只计一次等。字段可能再次绑定internal实现。

### 7.4 选择C：deprecated coexistence

新增新字段并保留旧sidecar getter返回0或deprecated值。项目尚未发布时收益很小，增加长期噪声，不建议。

`O-02`需要用户在A/B/C中明确选择。本草案推荐A。

## 8. Public row index export

当前：

```text
int[] rowIndexes()
```

问题：数组只携带数值，不携带captured structural epoch。Swap-remove后同一int可能指向另一row，runtime无法区分caller传入的是current还是stale locator。

### 8.1 选择A：保留raw `int[]`

- 最小API变化；
- 文档要求caller同时捕获`structuralEpoch()`；
- 无法强制校验，stale misuse仍可能静默读错row。

### 8.2 选择B：epoch-bearing detached result（建议安全方向）

概念shape：

```text
IndexSnapshot
  long structuralEpoch()
  int size()
  int indexAt(int position)
  int[] toArray()
```

Generated table可提供`requireCurrent(IndexSnapshot)`或snapshot方法在使用前检查epoch。它不是live view，也不叫IndexBuffer；`IndexBuffer`继续是internal reusable scratch。

### 8.3 选择C：移除bulk index export

只保留callback-scoped row/column access和direct `fetchAt/mutateAt`。最安全但破坏diagnostic/advanced usage。

`O-01`需要明确选择。本草案倾向B，但不把它视为已批准public API。

如果采用B，需要新增`stale_index_snapshot`或复用合适lifecycle code；error Owner必须先决定。

## 9. Allocation-free first terminal

当前`firstOrThrow()` materialize schema carrier。即使arg-min不分配candidate array，chosen row仍有一个显式object allocation。

可选方案：

| 方案 | 优点 | 风险 |
|---|---|---|
| `firstIndexOrThrow()` | primitive return、容易配ColumnView/mutateAt | public Index stale/误用 |
| `withFirst(RowConsumer)` | callback-scoped、无detached row | reentrant限制、值跨callback保存困难 |
| 保持现状 | API简单、materialization边界清晰 | FJSP每轮chosen row产生carrier/value allocation |

本专题baseline不自行增加。先由allocation benchmark量化chosen materialization占比，再处理`O-05`。

## 10. Error/diagnostic code变化

### 10.1 退出

- runtime `invalid_key_domain`：SparseInt domain消失后不再有触发源；
- order-specific runtime access/rebuild path；
- sidecar dirty/rebuild detail；
- processor order selector/direction diagnostics。

历史error code不能复用为新语义。是否从public enum/table中直接删除，遵守pre-release compatibility决策并更新manifest。

### 10.2 保留

- duplicate/missing key；
- unique constraint；
- invalid selector/floating value；
- row index、optional、lifecycle、view pin；
- memory/allocation/budget；
- callback/internal invariant；
- compatibility mismatch。

### 10.3 可能新增

- `stale_index_snapshot`（仅当选择epoch-bearing public result）；
- exact-index invariant id与bounded diagnostic context，不暴露bucket/group/raw Index。

## 11. Manifest 与generated-runtime protocol

必须从manifest删除：

- order annotation/public types；
- `SparseIntKeySpace` generated-runtime protocol；
- `RowPermutationSidecar` generated-runtime protocol；
- sparse/sidecar `TablePlan` methods；
- obsolete `TableStats`/result methods（按最终选择）。

必须新增或替换：

- primitive primary/unique/grouped exact raw substrate；
- generated typed hash/equality binding；
- link/unlink/relocate/bulk build protocol；
- IndexBuffer/operation scratch内部协议（仅确有跨package需要时）；
- new stats construction protocol；
- new metadata/plan strategy identity。

Generated facade public signature仍不得泄漏runtime generated protocol type、group id、bucket、row link或live IndexBuffer。

## 12. Direct cutover原则

项目未对外发布不等于可以无记录变化。建议采用一个明确breaking cutover：

1. 正式Owner批准new schema/API/runtime semantics；
2. compatibility identity提升；
3. annotation/processor/runtime/generated source同时迁移；
4. external fixtures/examples一次重生成；
5. old sidecar/sparse code和manifest全部删除；
6. migration note记录old->new mapping；
7. 不保留hidden dual mode或runtime fallback。

不允许：

- old plan field被new runtime静默忽略；
- old `byXxx()`生成但退化为每次dynamic sort；
- `@SomaOrder`仍可编译但不生效；
- new exact index失败时fallback full scan而不诊断；
- 旧/new protocol通过reflection猜测兼容。

## 13. Compatibility evidence

至少包含：

- annotation/public/runtime/generated-runtime/generated API manifest diff；
- old order/sparse fixture预期compile failure或migration fixture；
- migrated consumer重新compile/run；
- schema JSON/hash golden前后解释；
- runtime plan canonical JSON/hash golden；
- old generated/new runtime与new generated/old runtime双向mismatch；
- exact index result membership与unordered contract；
- dense/keyed swap-remove order behavior；
- result/stats getter exact API golden；
- error code removal/addition review；
- no obsolete `SomaOrder/SparseInt/RowPermutation/sidecarDirty` production references。

## 14. 待决选择汇总

| ID | 推荐 | 必须由谁决定 |
|---|---|---|
| `O-01` row index export | B：epoch-bearing `IndexSnapshot` | 用户 + Generated API/Public compatibility Owner迁移 |
| `O-02` operation result | A：删除sidecar字段，保留logical outcome | 用户 + Generated API/Error Owner迁移 |
| `O-03` exact stats | 最小aggregate getters + diagnostic per-selector detail | 用户 + Runtime errors/stats Owner迁移 |
| `O-04` plan兼容窗口 | direct cutover，删除sparse/sidecar字段 | 用户 + Runtime plan/Public compatibility Owner迁移 |
| `O-05` allocation-free first | 暂不加入，先测量 | 用户在benchmark evidence后决定 |
| `O-06` selector type breadth | 保持当前范围 | 用户仅在独立schema专题中扩张 |

## 15. 设计审查清单

- [ ] 是否接受直接删除old plan/sidecar stats surface，而不是兼容空壳？
- [ ] operation result选择A/B/C中的哪一个？
- [ ] raw `rowIndexes()`选择A/B/C中的哪一个？
- [ ] exact-index public stats采用何种最小getter？
- [ ]是否先不增加allocation-free first terminal？
- [ ]是否确认selector type breadth保持当前范围？
