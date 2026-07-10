# Runtime 性能模型

状态：正式设计文档
Owner：根项目协调层
事实范围：性能北极星、Access Pattern Card、成本模型、性能路径和 claim 边界
非事实范围：具体 runtime 数据结构参数、benchmark scenario、测量结果和 release 性能声明
最后审查日期：2026-07-10

## 1. 目标

本文解释 SOMA Java 为什么可能高效、实现必须关注哪些成本、什么证据才能支持性能判断。

列式存储、SoA、Sparse Set 和 DOD 是性能先验，不是性能保证。实现纪律由 [runtime 性能实现契约](../soma-runtime-core/docs/runtime-performance-implementation-contract.md) 拥有，测量和声明由 [benchmark evidence contract](../soma-benchmarks/docs/benchmark-evidence-contract.md) 拥有。

## 2. Performance north star

```text
schema-known, long-lived, access-pattern-defined state
  -> packed SoA storage
  -> primitive-specialized access
  -> generated fused execution
  -> explicit allocation boundaries
```

目标：

- hot path 接近同语义 handwritten primitive baseline；
- overhead 可解释、可观察、可 benchmark；
- 读取只触达需要的 columns；
- stable state packed 或 chunk-contiguous；
- non-materializing terminal 不按 row/field 分配对象；
- rebuild、growth、rehash、compaction、stats 等成本不隐藏。

## 3. 五层性能模型

```text
Access Pattern
  -> Logical Table / Ownership
  -> Physical Layout / Access Structures
  -> Generated + Runtime Kernel
  -> Stats / Benchmark / Runtime-plan feedback
```

不能从“列存”直接跳到“快”。每层都可能引入 locality、branch、indirection、allocation 或 maintenance cost。

## 4. Access Pattern Card

正式 examples、blueprints 和 claim-grade benchmark 必须记录：

| Field | 问题 |
|---|---|
| rows/cardinality | Table 和 child instance 的规模分布 |
| hot columns | 每轮真正读取/写入哪些 leaf |
| access source | packed、key、index、order、dynamic sort、child-local |
| read/mutation mix | scan、lookup、update、remove、replace 频率 |
| selectivity | selector 命中比例和 group size |
| optional density | all-present/all-absent/mixed |
| child density | parent count、child rows、small-instance distribution |
| working set | hot bytes 与 cache fit |
| allocation/export | materialization、DTO、scratch 频率 |
| phase boundary | import、compute、rebuild、sort、export 是否分开 |

Card 是 scenario/runtime-plan input，不是 logical Schema，不进入 schema hash。

## 5. 成本记号

| Symbol | 含义 |
|---|---|
| `N` | source rows |
| `M` | matched rows |
| `K` | selected/group rows |
| `T` | touched columns/leaves |
| `D` | touched bytes / working-set bytes |
| `I` | indirection/probe/comparator count |
| `A` | allocation bytes/object count |
| `C` | child table instances |

Big-O 只能说明增长阶，还必须报告：

- bytes touched 和 working set；
- branch/selectivity；
- hash probe/collision；
- cache/indirection；
- allocation/GC；
- JIT/inlining shape；
- sidecar rebuild 和 resize transient memory。

## 6. Performance lanes

必须把以下 lane 分开：

| Lane | 主要成本 |
|---|---|
| import/build | validation、Batch、growth、key/index construction |
| direct lookup | key construction、hash/sparse domain、probe、missing |
| packed scan | rows、touched columns/bytes、branch |
| index/order | selector lookup、group traversal、dirty rebuild |
| dynamic sort | K、comparator calls、row-index scratch |
| update/remove | matched rows、sidecar maintenance、compaction |
| child-local | locate parent/child、small-instance overhead、contiguous scan |
| materialization | recursive rows/leaves、object/collection allocation |
| external mapping | DTO/wire conversion |

一个总耗时不能解释这些 lane。

## 7. Import 与 capacity

`create/addBatch/replaceAll` 的成本至少包含：

- initial allocation；
- growth/resize 和 transient double-memory；
- primitive/reference column copy；
- key/index/order build 或 dirty marking；
- optional bitmap；
- child lazy allocation；
- validation 和 failure cleanup。

`defaultCapacity` 是 hint，不是 logical limit。Growth factor、trim、reuse 和 scratch policy 属于 runtime plan。

## 8. KeySpace

SparseInt 路径必须报告 key domain、dense/sparse capacity 和 fallback；不能仅按 row count 判断内存。

Hash 路径必须报告 load factor、probe/collision、rehash、missing/duplicate mix 和 transient allocation。Composite key hot lookup 不应按操作创建 tuple/object。

复杂度结论必须限定：

- average vs worst case；
- key distribution；
- capacity/load policy；
- adversarial/collision assumption。

## 9. Index、unique 与 order

AccessStructures 可以减少 candidate rows，但引入：

- sidecar memory；
- mutation maintenance；
- dirty/rebuild；
- selector materialization；
- group/permutation traversal。

Eager、lazy 或 hybrid policy 属于 runtime plan。任何隐藏 rebuild storm 都是性能缺陷；stats 必须能区分 clean traversal 与 rebuild。

Maintained order 和 dynamic `sorted(comparator)` 是不同 lane，不能用一个结果替代另一个。

## 10. Row/Key/Column Pipeline

Non-materializing Row Pipeline 目标成本：

```text
O(source candidates + matched action)
```

但必须验证：

- filter/skip/limit/terminal 是否 fused；
- short-circuit 是否提前停止；
- Cursor 是否 terminal-local reuse；
- callback path 是否 boxing/adapter allocation；
- sorted 是否使用 primitive row permutation；
- stats 是否按 row 创建对象。

Key Pipeline 的 stable key value materialization成本必须单独记录。Column Pipeline/ColumnView 应只触达目标 column 和必要 presence words。

## 11. Optional bitmap

Optional density 影响 branch 和 payload read：

- all-present；
- all-absent；
- mixed/random；
- clustered presence。

Runtime 可以使用 word-level fast path，但具体 kernel 属于 implementation contract。Benchmark 必须记录 density，不能只报告平均场景。

## 12. Child locality

Parent-owned child 可能改善 per-parent continuous scan，但同时引入：

- child handle lookup；
- child instance metadata；
- small arrays/bitmap/keyspace；
- lazy allocation；
- many-small-table GC/heap overhead；
- recursive lifecycle。

必须比较：

- parent count；
- child cardinality distribution；
- child instance count；
- flat composite-key + grouped index baseline；
- allocation/retained capacity；
- target hot loop touched bytes。

“child 更 cache-friendly”只能在相同业务语义 benchmark 后成立。

## 13. Materialization 与 allocation

Materialization 是显式 allocation lane。Deep materialization至少报告：

- ownership depth/table instances；
- rows/leaves；
- schema object、`List`、`Map.Entry` 和 key allocation；
- estimated/observed bytes；
- budget failure；
- external DTO mapping 是否另计。

它不能与 columnar compute 合并为一个 hot-loop 性能数字。

## 14. Memory model

Memory evidence至少分为：

| Category | 内容 |
|---|---|
| logical payload | primitive/reference columns |
| presence | bitmap |
| identity/access | keyspace、index、unique、order |
| ownership | child handle、registry、instance metadata |
| transient | growth、rehash、sort/compaction scratch、materialization |
| retained | high-water capacity、object references |
| observability | stats/counters/histograms |

`memoryBytes` 只能是带 estimator version 的诊断估算，不能冒充 JVM heap 精确值。

## 15. Performance tiers

从高层到低层：

1. Batch/replaceAll bulk path；
2. Direct keyed/dense access；
3. Fused Row Pipeline；
4. Primitive Column Pipeline；
5. Borrowed ColumnView / handwritten-equivalent primitive kernel；
6. Materialization/external boundary。

不是所有场景都应选择最低层 API。选择由 Access Pattern Card、正确性和 evidence 共同决定。

## 16. Evidence 与 claim

Evidence 分为：

- smoke：路径可运行；
- diagnostic：解释成本结构；
- claim-grade：有同语义 baseline、环境、规模、重复和统计。

只有 claim-grade 可以支持限定范围内的性能声明。Benchmark 必须比较同语义方案，分离 setup/import、hot loop、rebuild/sort、materialization/export。

具体 artifact 和 scenario lanes 由 [soma-benchmarks](../soma-benchmarks/docs/README.md) 拥有。

## 17. 不允许的推论

不能仅凭设计声明：

- Row Pipeline 一定快于 handwritten loop；
- child 一定快于 flat table；
- SparseInt 一定比 hash 省内存；
- maintained order 等价于 dynamic sort；
- columnar layout 自动消除 allocation；
- benchmark smoke 证明 production 性能；
- 单场景结论可推广到所有 runtime state。

## 18. 非目标

本文不选择具体 growth factor、hash probing、SIMD/AoSoA、pooling、JVM flags、scale preset 或性能阈值，也不报告任何已测得性能优势。
