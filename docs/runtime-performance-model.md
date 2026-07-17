# Runtime 性能模型

状态：正式设计文档
Owner：根项目协调层
事实范围：性能北极星、Access Pattern Card、成本模型、性能路径和 claim 边界
非事实范围：具体 runtime 数据结构参数、benchmark scenario、测量结果和 release 性能声明
最后审查日期：2026-07-10

## 1. 目标

本文解释 SOMA Java 为什么可能高效、实现必须关注哪些成本、什么证据才能支持性能判断。

列式存储、packed SoA、primitive hash access和DOD是性能先验，不是性能保证。实现纪律由[runtime性能实现契约](../soma-runtime-core/docs/runtime-performance-implementation-contract.md)拥有，测量和声明由[benchmark evidence contract](../soma-benchmarks/docs/benchmark-evidence-contract.md)拥有。

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
- exact-index maintenance/fresh build、growth、rehash、swap-remove、stats等成本不隐藏。

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
| access source | packed、key、exact index、dynamic sort、child-local |
| read/mutation mix | scan、lookup、update、remove、replace 频率 |
| selectivity | selector 命中比例和 group size |
| optional density | all-present/all-absent/mixed |
| child density | parent count、child rows、small-instance distribution |
| working set | hot bytes 与 cache fit |
| allocation/export | materialization、DTO、scratch 频率 |
| phase boundary | import、compute、index build/maintenance、sort、export是否分开 |

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
- exact-index maintenance/rehash 和 resize transient memory。

## 6. Performance lanes

必须把以下 lane 分开：

| Lane | 主要成本 |
|---|---|
| import/build | validation、Batch、growth、key/index construction |
| direct lookup | key construction、hash、probe、missing |
| packed scan | rows、touched columns/bytes、branch |
| exact index | selector hash/full equality、group traversal、incremental maintenance |
| dynamic sort | K、comparator calls、row-index scratch |
| update/remove | matched rows、exact-index delta、swap-remove/tail-fill |
| child-local | locate parent/child、small-instance overhead、contiguous scan |
| materialization | recursive rows/leaves、object/collection allocation |
| external mapping | DTO/wire conversion |

一个总耗时不能解释这些 lane。

## 7. Import 与 capacity

`create/addBatch/replaceAll` 的成本至少包含：

- initial allocation；
- growth/resize 和 transient double-memory；
- primitive/reference column copy；
- primary locator/exact-index construction；
- optional bitmap；
- child lazy allocation；
- validation 和 failure cleanup。

`defaultCapacity` 是 hint，不是 logical limit。Growth factor、trim、reuse 和 scratch policy 属于 runtime plan。

## 8. Primary locator

Hash primary locator必须报告load factor、probe/collision、rehash、missing/duplicate mix和transient allocation。Composite key hot lookup不应按操作创建tuple/object；V1不使用Key到bounded Entity的Sparse Set映射。

复杂度结论必须限定：

- average vs worst case；
- key distribution；
- capacity/load policy；
- adversarial/collision assumption。

## 9. Exact index 与 unique

Exact access structures可以减少candidate rows，但引入：

- bucket/group/link memory；
- mutation link/unlink/relocate；
- probe/collision/rehash；
- generated full equality；
- group traversal。

V1固定eager/incremental maintenance，不把maintenance policy暴露为runtime plan。任何read-time rebuild/full-scan fallback都是性能与正确性缺陷；stats必须能观察probe、collision、rehash、group、link、unlink和relocate。

业务顺序只由dynamic `sorted(comparator)`或应用层专用priority结构表达，exact-index组内枚举顺序不作承诺。

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
| identity/access | primary locator、exact index、unique |
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
- exact index一定快于packed scan；
- exact index组内枚举等价于business order；
- columnar layout 自动消除 allocation；
- benchmark smoke 证明 production 性能；
- 单场景结论可推广到所有 runtime state。

## 18. 非目标

本文不选择具体 growth factor、hash probing、SIMD/AoSoA、pooling、JVM flags、scale preset 或性能阈值，也不报告任何已测得性能优势。
