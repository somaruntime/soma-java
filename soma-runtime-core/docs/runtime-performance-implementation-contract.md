# Runtime 性能实现契约

状态：正式设计文档
Owner：`soma-runtime-core`
事实范围：packed/primitive/fused/allocation-bounded runtime kernel、capacity/scratch、primary locator/exact index和stats overhead
非事实范围：跨模块性能模型、public API、benchmark scenario/结果和具体永久阈值
最后审查日期：2026-07-20

## 1. 目标

本文定义 Java 8 runtime-core 与 generated hot path 必须保持的机械效率形状，避免“名义列存，实际由对象分配、装箱、间接访问和隐藏 rebuild 主导”。

本文不声明任何已测得性能优势。跨模块性能北极星、Access Pattern Card 和成本模型以 [Runtime 性能模型](../../docs/runtime-performance-model.md) 为准；benchmark evidence 以 [soma-benchmarks](../../soma-benchmarks/docs/README.md) 为准。

## 2. Contract input 与边界

Implementation 必须消费：

- validated normalized schema 和 static column/selector binding；
- scenario Access Pattern Card；
- public API/lifecycle/correctness contract；
- versioned runtime plan。

Storage、lifecycle、runtime plan、errors/stats 和 public API 分别由 [TableStore 契约](table-store-contract.md)、[Runtime lifecycle 契约](runtime-lifecycle-contract.md)、[Runtime plan 契约](runtime-plan-contract.md)、[Runtime errors 与 diagnostics 契约](runtime-errors-and-diagnostics-contract.md) 和 [Generated Table API 契约](../../docs/generated-table-api-contract.md) 拥有。

性能优化如果改变 schema、public behavior、determinism、ownership、lifecycle 或 error semantics，必须先修改对应 Owner，不能由 implementation 静默决定。

## 3. Packed SoA storage invariants

### 3.1 Packed live-row invariant

每个公开可用的 stable table state 必须满足：

```text
live RowSlot = [0, size)
```

- 默认 scan 不遍历长期 tombstone/free-list hole；
- single delete 使用swap-remove；
- multi-row remove把selected Index写入`IndexBuffer`并排序，再从tail选择未删除survivor填充front hole；
- remove不得分配`boolean[size]`全表mark；成功返回后的table state不得留下hole；
- row move 后primary locator、columns、bitmap、child handle和所有exact indexes必须同步维护；
- dense public row index 与当前 packed `RowSlot` 对应，structural mutation 后失效。

如果 future implementation 需要 segmented/chunked storage，仍必须提供等价的 contiguous/chunk-contiguous scan contract 和 evidence，不能静默退化成逐 row pointer traversal。

### 3.2 Column layout

- primitive leaf 使用对应 primitive array/segment，不通过 boxed wrapper 存储；
- value field 递归 flatten，hot scan 不先构造 Value object；
- optional 使用 bitmap + payload column；
- child field 使用 primitive/compact opaque handle representation，不在 parent column 保存 Java Collection；
- all leaf columns、bitmap 和 RowSpace 对同一 logical capacity/slot mapping 保持一致；
- string/object field 可以使用 reference column，但必须单独计算 indirection、GC retention 和 touched bytes，不能把它当作 primitive locality claim；
- scan kernel 只绑定实际需要的 columns，不因 schema row 很宽而读取所有 columns。

V1 baseline 是 pure SoA。AoSoA、blocked layout、string pool、compression 或 SIMD-specific layout 属于 evidence-backed future/runtime-plan option，不是无条件优化。

## 4. Steady-state allocation contract

以下 non-materializing hot operations 在 capacity/scratch 已准备完成后，必须以 no per-row/per-field allocation 为目标，并由 allocation benchmark 验证：

- default/exact-index/dynamic-sorted Row Pipeline 的 `count`、`anyMatch`、`noneMatch`、`forEach`；
- Row Pipeline `update` 和 `remove` 的 traversal phase；
- Column Pipeline / ColumnView primitive traversal；
- primary-locator `contains/fetch-locate` normal/missing path；
- exact group lookup/traversal；
- optional bitmap scan。

具体约束：

- 不为每个 row 创建 Cursor、Iterator、Tuple、Optional、boxed primitive 或 temporary key；
- generated Cursor 在一个 terminal 内复用或由等价 index-based accessor 替代；
- pipeline stage object、user lambda/callback 可以在 pipeline construction/call boundary 产生，但不得按 row 重建；
- composite key lookup 可以读取 caller-provided immutable Value，但 runtime 内部不得再构造 transient composite key；
- dynamic sort、compaction、key buffer 使用 primitive scratch arrays；不得使用 `Integer[]` 或 materialized row array；
- stats 使用 terminal-local primitive counters，结束时一次 publish；不得逐 row 更新 Map、timer object、atomic counter 或 histogram object。

允许allocation的显式边界：capacity/index growth、rehash、replaceAll fresh-index build、scratch first-growth、public `IndexSnapshot` copy、Batch construction、recursive materialization、external DTO mapping和explicit diagnostic tooling。它们必须分别统计。普通exact read不得触发full-table rebuild或Java object allocation。

## 5. Generated specialization and JIT-friendly execution

“schema-specific generated code + shared runtime kernel”必须同时满足复用性和 hot-loop specialization。

Generated code 必须：

- 静态绑定 normalized leaf 到 concrete primitive/reference column；
- primitive getter/setter 使用 primitive type，不经 `Object`/boxing；
- selector extraction、key/selector leaf equality/hash、dynamic comparator采用generated typed path；
- 在 terminal 开始前绑定所需 arrays/columns、RowSequence 和 callbacks；
- lifecycle/released/epoch/shape 检查在可证明安全时 hoist 到 terminal/bulk boundary；
- generated accessor/cursor call site 保持 monomorphic 或 JVM 可内联形态；
- 不在每个 row/field 调用 metadata interpreter、reflection、dtype switch 或 string field lookup。

Shared runtime kernel 可以使用 internal interface 接受 generated adapter，但不得形成如下逐 cell 路径：

```text
row
  -> generic adapter
  -> getField(columnId)
  -> dtype switch
  -> boxed Object
```

如果某个 shared abstraction 无法被 JVM 内联或导致 megamorphic dispatch，必须通过 generated specialized loop、primitive kernel overload 或其他等价方式消除；最终选择由 bytecode/JIT/profile evidence 决定。

## 6. Row Pipeline execution contract

Row Pipeline intermediate operations 必须尽可能 fuse 到一次 terminal traversal：

```text
AccessPath
  -> candidate RowSequence
  -> fused filter / skip / limit
  -> terminal
```

规则：

- `filter`、`skip`、`limit` 不构造 intermediate Collection/row list；
- `findFirst`、`firstOrThrow`、`limit` 在语义允许时 short-circuit；
- 无 dynamic sort 时，一个 terminal 不重复遍历同一 source；
- dynamic sort 只构造 primitive row-slot/index permutation，不移动 columns、不 materialize schema object；
- comparator 直接读取 candidate columns，不执行 cross-table lookup，不创建 key/value object；
- mutation terminal 在执行前冻结 candidate plan，但不为全部 rows materialize object；
- common no-callback terminals 不得被实现成 generic callback pipeline；
- pipeline consumed/lifecycle checks 不得成为逐 row 分支。

`sorted(...).limit(k)` 是否使用 top-k internal optimization 属于 runtime-plan/benchmark 决策。只要 public semantics、determinism 和 tie-break 完全一致，可以作为内部优化；V1 不因此承诺 public top-k API。

## 7. Primary locator implementation contract

- int/long key使用primitive bucket/key/index arrays；
- composite key normal lookup不创建临时tuple；
- open addressing的empty/deleted state不使用boxed sentinel；
- hash collision必须执行generated full canonical key equality；
- remove/row move后locator必须同步到moved survivor的current Index；
- load factor、probing、delete strategy和rehash threshold属于versioned runtime plan；
- rehash/growth必须在visible mutation前完成或具备rollback-safe staging；
- collision、probe、rehash和capacity必须在低干扰stats中可观察。

V1 concrete hash implementations在rehash时必须先用local primitive arrays完成全部live identity重插与计数校验，再一次发布arrays、used与metrics；allocation、重插、counter overflow或identity校验失败均保留旧映射。`capacity`是当前bucket array长度，`used`是LIVE+DELETED bucket数，probe/collision/rehash是since-reset checked counters；generated append/replace采用staged locator时先通过checked `addMetrics(...)`继承旧instance累计，再与新staging工作量一起发布；`resetMetrics()`只清零这三项累计，不改变locator、tombstone或capacity。

Primitive key path不得以`HashMap<Key, Integer>`作为canonical runtime implementation。String/composite key可以读取reference column，但必须有单独memory/indirection evidence。

## 8. Grouped exact-index contract

### 8.1 Primitive structure

每个`@SomaIndex`/`@SomaUnique`使用`GroupedExactIndex`：

```text
hash bucket -> same-hash group chain -> group head -> row links
row -> group / prev / next
```

- runtime只保存primitive bucket/group/row-link arrays；不保存selector Java object或`Map<Value,List<Integer>>`；
- generated code提供canonical hash以及与representative row的full leaf equality；
- same-hash unequal selector通过group chain区分；
- nonunique group允许0..N rows，unique group最多1 row；
- group与row-link capacity独立checked growth，storage current/high-water可观察；
- row capacity、group capacity与bucket capacity不得互相冒充：table initial capacity和`reserve(expected)`只预留row-link envelope，不得默认推导为`expected`个distinct groups；
- `addBatch`在可见mutation前使用primitive detached分组精确计算每个selector相对current table的新增group数，再做selector-specific capacity preflight；该临时分组属于bulk scratch，不进入steady-state read path；
- `replaceAll`先得到batch真实distinct-group cardinality，再按该基数构建detached fresh index；不得因最坏cardinality直接长期保留`rows`个group slots；
- capacity target使用primitive arithmetic计算；capacity充足的steady-state preflight不创建临时descriptor对象；
- group内row顺序不作公共承诺。

### 8.2 Eager incremental maintenance

- append在发布row前完成uniqueness、真实新增group计数与selector-specific capacity preflight，发布facts后link；计数或扩容失败不得暴露partial row/index state；
- update先对整次terminal final state执行unique validation，再unlink old、publish fields、link new；合法value swap必须成功；
- selector全部来自immutable `@SomaKey` leaves时，generated update/mutator不执行无效的capacity preflight、unlink或relink；mixed selector只维护实际可变的selector；
- remove先unlink removed row；tail-fill move使用`relocate(from,to)`修复per-row links；
- replaceAll/create在detached structure中fresh build并与columns一起原子publish；
- ordinary read只做hash/group lookup与link traversal，不存在dirty state、read-time full rebuild或full-scan fallback；
- mutation expected failure时旧facts与所有exact structures保持一致。

Probe、collision、rehash、entry/group count与storage按[Runtime errors与diagnostics契约](runtime-errors-and-diagnostics-contract.md)聚合观测。Fresh build是显式bulk mutation成本，不得伪装成read成本或沿用rebuild-sidecar语义。

## 9. Capacity、compaction and scratch memory

- table/child initial capacity 是 hint，不是 max size；
- growth 使用 overflow-safe geometric 或 evidence-backed equivalent policy，具体 factor 属于 runtime plan；
- columns、bitmap、RowSpace 和 required locator structures 必须以一致的新 capacity stage；
- `reserve(expected)` 对columns、primary locator与exact-index row links做combined preflight并预留到同一expected row envelope；尚不存在的selector value不预留虚构group/bucket，实际group容量在`addBatch`精确计数后预检；expected resource failure发生在任何capacity publish前；
- resize 的旧/新 arrays 瞬时共存必须进入 allocation/memory estimate；
- `clear()` 默认复用 capacity，不在普通 hot path 自动 shrink；
- object/reference column 在 remove/clear/replacement 后必须清除不再 live 的引用；
- dynamic sort、remove candidate、row sequence、key buffer和snapshot staging应通过table-local `IndexBuffer`等single-owner primitive scratch复用，并有maximum retained bytes/high-water stats；
- 极端 high-water 后的 trim/rebuild 只能是显式 lifecycle/runtime-plan operation，不得在不可预测的普通 terminal 内触发。

V1 不在本文固定 growth factor、load factor、trim threshold 或 scratch maximum；这些参数必须 versioned、可诊断并由 benchmark 校准。

## 10. Optional bitmap kernels

- all-present、all-absent 和 mixed density 使用独立 fast path；
- mixed bitmap 以 64-bit word/chunk 扫描；
- 可以使用 `Long.numberOfTrailingZeros` 等 Java 8 primitive operation 枚举 set bits；
- payload 只在 presence bit 为 `1` 时具有 logical value；
- optional scan 不构造 per-row Optional/wrapper；
- batch import/replace 尽量按 word/chunk 写入；
- optional density、bitmap words scanned、present rows 必须进入 benchmark input/stats。

Branchless、SIMD 或特定 CPU intrinsic 不是 V1 契约；只有在保持 Java 8 portable semantics 且有 evidence 时才进入 runtime plan。

## 11. Child table locality and small-instance overhead

Parent-owned child 的性能收益和成本必须同时建模：

收益：

- parent-key 定位后只扫描该 child instance 的 packed rows；
- child-local exact index不需要重复parent key leaf；
- 不扫描其他 parent rows。

成本：

- child `TableStore`/registry/handle 数量；
- 大量小 primitive arrays/object headers；
- per-child capacity over-reservation；
- 跨很多 child instance 遍历时的 pointer/handle chasing；
- recursive materialization 的 object/List/Map allocation。

永久规则：

- required logical-empty child 不 eager allocate storage；
- optional absent 不创建 child instance；
- `@SomaChild.initialCapacity` 按单个 parent 的 child cardinality 设置；
- parent live storage只保存primitive owner token/handle/generation与optional bitmap；ownership registry使用平行primitive/Object arrays，不得以Java Collection/Entry object graph作为canonical child locator storage；
- child-local hot traversal 使用 live child facade/Row Pipeline，不通过 parent deep materialization；
- child pool/slab/segmented backing 可以作为 internal optimization，但不得改变 exclusive ownership、independent table semantics、lifecycle、handle validity 或 materialization result。

Benchmark 至少覆盖 empty、singleton、small、medium/high child cardinality，以及 child instance count 与 total rows 的交叉规模。Flat-vs-child claim 必须同时记录 locality 与 small-instance memory overhead。

## 12. Runtime stats observability budget

Stats 分三层：

| Level | 默认状态 | 允许成本 |
|---|---|---|
| always-on summary | enabled | terminal/mutation boundary O(1) publish；不得逐 row 分配或使用 atomic |
| terminal-local counters | enabled | primitive local increments，terminal 结束后一次合并 |
| diagnostic detail | explicit opt-in | probe histogram、phase timing、bounded per-index detail、sampling/profile hook |

单线程 aggregate 不需要 lock、atomic 或 concurrent collection。Elapsed timer、histogram 和 profiler hook 不得默认进入最内层 field access。Benchmark 必须同时能运行 summary-only 与 diagnostic mode，避免把 instrumentation overhead 误认为 runtime 固有成本。

Stats 是观测事实，不驱动业务语义。Runtime plan 可以读取历史 summary 做离线/显式 tuning，但 V1 不允许 hot loop 中无界自适应切换数据结构。

## 13. Performance tiers

用户可见访问路径按成本和用途分层：

| Tier | 路径 | 目标 |
|---|---|---|
| bulk | `reserve` / `addBatch` / `replaceAll` | 批量构造、capacity reuse、fresh locator/index build |
| direct/access | key/exact-index source | 通过明确结构减少candidate rows |
| fused row | Row Pipeline + Cursor | typed、object-free row traversal/mutation |
| primitive | Column Pipeline / ColumnView | 单列或少量 primitive columns 的最高吞吐路径 |
| boundary | schema object/`List`/`Map` materialization | 完整 detached observation/export，不作为 hot-loop baseline |

API 易用性不得隐藏 tier 变化。特别是 `fetch`、`findFirst` 和 `firstOrThrow` 会 materialize schema object；需要只定位/读取 primitive field 的 hot path 应使用 Row Pipeline cursor、mutator 或 Column path。

## 14. Runtime-plan decisions

以下属于 versioned runtime plan，不进入 Schema 或 `schema_hash`：

- capacity growth factor、minimum capacity、trim policy；
- primary locator load factor、probing、delete/rehash strategy；
- exact-index bucket/group growth与probe strategy；
- scratch retention limit；
- child pooling/slab threshold；
- string pool/compression；
- AoSoA/block size、SIMD/vectorization candidate；
- stats diagnostic level；
- benchmark-calibrated performance budget。

Runtime plan identity 必须可读取；影响实际 layout/algorithm 的 plan change 必须进入 runtime-plan compatibility/version evidence，但不改变 logical schema hash。

## 15. Evidence obligations

每个核心 kernel 至少需要三类证据：

1. **correctness/invariant**：与 reference model/differential oracle 一致；
2. **allocation/shape**：steady-state allocation、packed rows、primitive storage、no hidden materialization；
3. **performance baseline**：相同语义、相同规模、相同 mutation/read mix 的 primitive/Java baseline。

最低 implementation benchmark lane：

- packed primitive scan：Row Pipeline、Column path、handwritten primitive array baseline；
- optional all-present/all-absent/mixed word scan；
- int/long/composite primary locator normal/missing/collision/rehash；
- exact-index grouped lookup across cardinality/selectivity/collision；
- exact-index incremental append/update/remove/relocate与replaceAll fresh build；
- dynamic sort scratch reuse and comparator count；
- single/batch delete compaction；
- reserve/growth/replaceAll/clear reuse and transient allocation；
- child cardinality/instance-count matrix versus flat baseline；
- summary-only versus diagnostic stats overhead；
- materialization/export as separate boundary。

Claim-grade artifact 至少记录 rows/s、ns/row or ns/op、allocation/op、estimated touched bytes、working-set size、GC observation、JIT warmup/forks、capacity/load factor/selectivity/optional density/mutation ratio 和 known limitations。Hardware cache/branch counters 在可获得时作为 diagnostic evidence，不是所有平台的硬门槛。

## 16. Performance anti-patterns

以下实现属于 V1 性能偏航，必须由 code review、golden、allocation test 或 benchmark gate 阻止：

- hot table 使用 schema object `List`/`Map`、bean/DTO row 或 live proxy 存储；
- primitive field 进入 boxed `Object[]`；
- 每 row 创建 Cursor/Iterator/Optional/tuple/key wrapper；
- Row Pipeline intermediate stage 构造中间 Collection；
- primitive key/index 使用 boxed `HashMap<Key, List<Integer>>` canonical path；
- comparator 内做 cross-table lookup、materialization 或 allocation；
- exact-index read路径隐藏full-table rebuild或full-scan fallback；
- persistent tombstone 让 default scan 逐 row 判断 live/dead；
- stats 在 inner loop 使用 timer object、atomic 或 Map；
- active ColumnView 下为了性能绕过 lifecycle checks；
- 为了局部性复制 authoritative fact 而没有 owner/invalidation；
- 用不同业务语义的 primitive baseline 宣称 SOMA 更快；
- 把 smoke、单次本机结果或无 warmup 结果写成性能保证。

## 17. 非目标

本文不承诺：

- 所有访问模式都优于 Java Collections；
- Row Pipeline 与手写 primitive loop 零开销；
- 特定 CPU cache-line alignment、prefetch、SIMD 或 vector API；
- exact JVM object size/heap accounting；
- parallel scan/sort 或 thread-safe performance；
- native/off-heap layout；
- 自动 join、query optimizer 或无界 adaptive runtime；
- 在无 claim-grade evidence 时给出固定速度、内存或规模保证。
