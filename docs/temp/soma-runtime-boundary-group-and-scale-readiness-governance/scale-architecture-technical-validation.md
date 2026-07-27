# SOMA Scale Architecture 技术假设与验证协议

类型：Temporary

状态：validation complete（TV0–TV9 evidence 已由 P6 正式 Design 消费）

Owner：SOMA scale architecture standalone technical validation

正式事实源：否

实施授权：本文只记录技术验证阶段的历史协议和 claim boundary；当前 production
实施授权以 active Codex Goal、正式 Design 与 Conformance 为准，不能由本文扩大

事实范围：本轮讨论形成的 Small/Medium/Large 与 `100M × 100M` 规模包络、完整
Metadata 验证边界、V1 类型与存储边界、reference-backed String baseline、候选
物理架构、Eager Detached 默认、callback-scoped streaming 试点、SOMA 只读实现
证据、独立 Lab 实验问题、评价方法、证据转移和退出规则

非事实范围：已接受的 Metadata/Result Delivery public/generated API、
Segment/Morsel/Execution Block 大小、任意 String profile 已验证的单表或双表
一亿行能力、正式存储/索引/并行算法、ordinary Iterator、closeable pull cursor、
SOMA Schema/协议修改、production authorization 或 readiness

最后审查日期：2026-07-28

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

设计去向：[SOMA 系统设计、核心抽象与叙事再审视](system-design-and-narrative-governance.md)

下游规模治理：[SOMA 代码与测试规模治理](code-and-test-scale-governance.md)

原始输入仓库基线：`253e383317a84bacdc164bf105a57a7eb1303987`

本轮 Research 复核基线：`6cdc34f673c7bead208173e13df913e7c0a719fe`

## 1. 文档角色

本文件是本专题进入技术验证阶段的协议输入。它解决两个问题：

1. 保存本轮关于完整 Metadata、双 100M Table、存储分段、索引、中间结果、Result
   Delivery 和执行粒度的完整上下文；
2. 在证据不足时阻止高可信技术假设被提前写成正式设计。

本文中的 Metadata 精确类型、`32K Segment`、compact locator、Candidate 多物理
形态、vector、morsel、Group/Join/Window/Delta specialization 均为待验证候选。
后续只有在成本模型、隔离原型、production-shaped Lab evidence 和 standalone
composite qualification 形成闭环后，才能由项目 Owner 决定是否将其作为 SOMA
detailed-design 输入。

本文不裁决 SOMA 最终 canonical narrative、核心抽象或 code/test 去留。Lab 只回答
候选机械方向与成本是否成立；Technical Validation Report 和 Owner decision matrix
形成后，结论先进入配套系统设计治理，再由 accepted Design 驱动 code/test inventory、
replacement closure 与后续实施授权。

本文不修改正式 [设计宪法](../../design/soma-java-design-constitution.md)、
[Table、存储与访问](../../design/table-storage-and-access.md)、
[Transformation Model](../../design/transformation-model.md)或
[DataFlow 执行模型](../../design/dataflow-execution-model.md)。

### 1.1 验证载体与隔离约束

TV0–TV9 全部在 `soma_java` repository/workspace 之外的独立 Lab 中实施。本仓库在
验证阶段仅提供只读问题来源、baseline 形状和语义约束：

```text
SOMA read-only facts
  -> Lab-owned baseline surrogate
  -> Lab-owned candidate
  -> standalone evidence
  -> Owner adoption decision
```

独立 Lab：

- 不依赖 SOMA Maven module、JAR、generated artifact、source set、internal protocol
  或 symlink；
- 不 import `com.hgtech.soma` production/test classes，使用 Lab-owned package；
- 只实现回答一个验证问题所需的最小 primitive structure、kernel 和 harness；
- 可以依据指定 SOMA commit 语义等价地复刻 baseline mechanical shape，但必须记录
  provenance 和有意省略的语义；
- 不承担 SOMA public API、compatibility、migration、release 或长期可维护性；
- 结果只能判定候选技术方向，不形成 SOMA conformance、Gate、readiness 或代码
  合并授权。

Lab 中的 `production-shaped` 只表示内存布局、访问模式、key equality、mutation、
budget 和失败形状足以回答问题，不表示使用 production SOMA code。

Lab 是本次治理的一次性、可删除验证载体，不是长期 repository、prototype 或平行
实现。TV0 必须记录其精确删除根目录；治理收口前先形成自包含 Technical Validation
Report 和 Owner decision matrix，将必要结论/evidence 摘要转移给正式 Owner，再删除
整个 Lab source、build output、generated dataset 和临时 artifact。正式文档、构建和
运行时不得依赖已删除的 Lab。

当前 Lab 与 deletion root 已冻结为：

```text
/Users/arthur/Documents/HGTECH/projects/prototype/soma_runtime_scale_technical_validation_lab
```

Lab 的 TV0–TV7 narrow primitive/numeric 阶段已完成，收口 revision 为
`591bbab505b14a2261d2700b7f0ffdcdde6cfb1f`。这些 evidence 继续有效，但只证明其
明确 workload 和类型边界；TV8 reference-backed String raw evidence 已在 revision
`b2970d2` 收口，不引入新的 String storage backend，也不把 Lab 类型变成 SOMA
contract。TV0–TV8 综合文档 revision 为 `b70e9a2`。TV9 implementation/raw/
decision revision 为 `75fe7a7` / `bbc13e8` / `cf322ab`，并已通过独立只读复核。

## 2. 产品目标与约束

### 2.1 候选规模目标

候选目标采用两层规模包络：

1. 每张 Table 在 Small、Medium、Large 和单表 100M profile 下分别验证；
2. `100M × 100M` Challenge 覆盖两个独立 root Table 同时各有 100M rows 的受约束
   multi-source computation。

这不是对任意宽 Schema、任意数量索引、任意 Join expansion、笛卡尔关系或 global
Sort/Window 的统一延迟承诺，也不是当前能力声明。最终 standalone qualification
必须同时
说明：

- left/right Schema 的 primitive-backed scalar、reference-backed immutable scalar、
  compiler-flattened value 与 owned structured state 组成和 estimated bytes/row；
- 两张 Table 同时驻留的 payload 与 maintained primary/unique/exact access path；
- String 的 UTF-16 code-unit length、value cardinality、distinct object identity、
  sharing ratio、optional/absence ratio、字段角色与三层 memory accounting；
- `1:1`、`N:1`、有界 `1:N` 或受限 `N:M` 关系；
- touched columns、hit rate、selectivity、distinctness、skew、group size、
  maximum multiplicity 和 join expansion；
- terminal/output shape 与可证明 output upper bound；
- Zulu JDK 8、GC、heap、hardware、worker 和 warmup/fork；
- latency、throughput、effective bandwidth、allocation、retained/peak heap 和 GC；
- 哪些结论属于 Small、Medium、Large、single-100M 或 `100M × 100M`
  Qualification。

`100M × 100M` 至少分为以下 relation lanes：

| Lane | 关系与终端边界 |
|---|---|
| unique–unique | `1:1`，bounded complete result 或 aggregate |
| FK–PK | `N:1`，覆盖均匀与倾斜 key |
| low-hit | semi/anti/exists/count，避免 pair materialization |
| bounded expansion | `1:N`，声明 maximum multiplicity/output |
| high expansion | `N:M` 只允许 aggregate/pre-aggregation 或预期 budget rejection |

### 2.2 不可反转的边界

- SOMA 保持 Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8；
- live hot state 不透明 spill 到磁盘，不通过通用压缩换取常驻内存；
- Snapshot/Restore 是显式生命周期能力，不是运行时换页；
- application 负责 MES working-set projection、驱逐、双缓存、同步和 JVM 总预算；
- SOMA 负责 columnar storage、Access、有限 Transformation/DataFlow、单次操作
  correctness 和受控数据并行；
- SOMA 不承担跨 Table transaction、application rule graph 或并发 Table API；
- packed `[0,size)`、swap-remove、Index currentness、failure atomicity 和 detached
  output 边界不得因性能优化改变。
- Candidate/DataFlow intermediate 可以 lazy/fused；Eager Detached 继续是默认
  Result Delivery，完整、bounded 并一次性发布；
- callback-scoped streaming 只作为显式、同步、one-shot、read-only 的可选试点；
  callback value/borrow 不得逃逸，guard/scratch/cancel/cleanup 必须在 terminal
  调用返回前关闭；
- 不新增 ordinary Iterator、closeable pull cursor、Generator、Publisher、异步
  push、partial detached publication 或 terminal 返回后继续持有 source guard 的
  Lazy Output。

### 2.3 Small、Medium 与 Large 同等重要

规模优化不能只服务一亿行。候选性能区域为：

| Profile | 候选规模 | 第一成本 |
|---|---:|---|
| Small | `0 .. 32K` | 固定调用、plan、对象分配、分支和 JIT 代码形状 |
| Medium | `32K .. 1M` | cache locality、候选表示和循环机械形状 |
| Large | `1M .. 100M` | bytes moved、retained/scratch、GC、内存带宽和并行饱和 |
| Challenge | two Tables × `100M` | simultaneous retained state、relation/access、barrier/output peak |

这些边界只是实验分层，不进入 public contract。任何 Large 优化若对 Small/Medium
造成稳定回归，必须提供 operation-boundary specialization 或被拒绝，不能把回归
包装成规模目标的必然代价。

### 2.4 V1 类型边界与 reference-backed String baseline

V1 验证采用以下四类 schema storage 语义：

| 类型类别 | 验证表示 |
|---|---|
| primitive-backed scalar | primitive、enum、date/time、显式 semantic scalar 的 primitive column |
| reference-backed immutable scalar | 白名单仅 `String` 的 typed reference column |
| compiler-flattened value | `@SomaValue` canonical leaf columns，不保存 value object |
| owned structured state | parent-owned child Table 与 opaque handle，不保存 Collection/object graph |

V1 已裁决 String 继续使用 reference-backed baseline。TV8 不比较 dictionary、字符
arena、intern、compression 或其他字符串后端；若某个 String profile 超出预算，
正确结论是限定 profile 或确定性拒绝，而不是在本阶段重新打开表示候选。

物理 `Object[]` 能保存任意引用不构成 schema capability。任意 Java 对象可能通过
外部 alias 或可变子图绕过 mutation、epoch、Key/Unique/Index、ownership、并发和
materialization 边界，因此不准入普通 schema field。确需关联 application object
时，SOMA 保存稳定 ID，对象由 application sidecar/registry 管理。

String 数据语义固定为：保存 caller reference，不复制、不 intern、不 normalize；
required value 非 null，optional absence 与 empty string 分离；Key、Unique、Index、
Group、Join 和 order 使用完整 String value equality/hash/order，不能使用引用
identity。

## 3. 大规模性能的成本模型

单表与双表一亿行场景的主要成本不是抽象的“操作次数”，而是：

```text
retained bytes
  = left rows × left stored bytes/row
  + right rows × right stored bytes/row
  + both primary/unique/exact access
  + ownership/presence/runtime control

operation bytes
  = left/right rows or candidates × touched bytes
  + candidate/scratch/barrier/eager-output bytes

transient peak
  = retained bytes
  + growth/rehash old-and-new structures
  + invocation scratch
  + complete unpublished result
  + JVM/GC headroom

time lower bound
  ≈ actual bytes moved / sustainable memory bandwidth
```

Descriptor、Plan、Effective 和 Observation Metadata 本身应当 bounded；column
payload、presence、locator bucket、row link、group membership、candidate、
scratch 和 output payload 不是 Metadata，必须完整计入 retained/transient/peak。

String profile 还必须并列报告三个不可替代的口径：

```text
SOMA-owned structural bytes
  = reference columns + presence + locator/index + scratch/output structures

SOMA-retained reachable String bytes
  = 从 Table/index/output 可达的 distinct String object 与字符载荷模型

JVM observed heap
  = 指定 JDK/reference width/object layout/GC 下的实际 heap observation
```

structural estimator 不包含 reachable String payload；reachable model 不宣称对象由
SOMA 创建或独占；JVM observed heap 不能反向成为 deterministic admission estimator。
跨行或跨 Table 共享同一 object identity 时，aggregate reachable bytes 必须按
identity 去重；相同 value 但不同 object 不得按 value cardinality 错误去重。三种
口径都记录 current、peak/high-water 和 measurement/estimator identity。

当前实现的量级基线用于建立问题，不构成未来设计：

| 当前结构 | 100M 量级近似值 |
|---|---:|
| 一个完整 `int[100M]` candidate | `0.373 GiB` |
| 左右两个 candidate vector | `0.745 GiB` |
| 一个 current long primary locator | `3.25 GiB` |
| 两张 Table 各一个 locator | `6.50 GiB` |
| generic `100M × 100M` Join 基础 scratch | `2.12 GiB` |
| `1:1` 的 100M joined-index output | `0.84 GiB` |
| high-distinct 100M Group 当前 scratch | `3.98 GiB` |

上述估算尚未包含真实 columns、其他 exact access、对象头、growth/rehash 重叠、
result materialization 和 JVM/GC headroom。任意高 multiplicity `100M × 100M`
关系理论输出可能远超 `int` cardinality 和可用 heap，因此 standalone
qualification 必须先
关闭 relation/output bound。

因此，应优先：

```text
减少 retained bytes
  -> 减少 intermediate bytes
  -> 减少完整数据遍数
  -> 在 terminal 前消除无用 relation/output
  -> 保持连续访问和 locality
  -> 再考虑 branchless、SIMD、unroll 等指令级优化
```

HPC Roofline、MonetDB memory-wall、X100 vectorized execution 和
compiled-vs-vectorized 研究共同说明：低 arithmetic intensity 的列扫描通常首先受
数据搬运限制；全列中间物化会增加内存流量，而 per-element interpreter 又会增加
控制开销。SOMA 的 compiler specialization 适合采用 generated fused kernel 与
小执行批次结合的混合方向，但该方向仍需 Java 8 production-shaped Lab evidence。

研究输入：

- [Berkeley Lab Roofline Model](https://amcr.lbl.gov/departments/computer-science-department/ppan/roofline-performance-model/)
- [Breaking the memory wall in MonetDB](https://ir.cwi.nl/pub/13805)
- [MonetDB/X100](https://ir.cwi.nl/pub/11098)
- [Compiled and Vectorized Queries](https://www.vldb.org/pvldb/vol11/p2209-kersten.pdf)
- [Morsel-driven Parallelism](https://portal.fis.tum.de/en/publications/morsel-driven-parallelism-a-numa-aware-query-evaluation-framework/)

## 4. 三种不得混淆的粒度

### 4.1 Storage Segment

持久存储、capacity growth、GC object size、swap-remove 和物理扫描的分区。它与
ECS Chunk 在“固定规模、packed、增量分配、可作为遍历/并行分区”方面相似，但不
拥有 archetype migration、Entity composition 或 16 KiB unmanaged block 语义。

SOMA Storage Segment 更接近 Arrow `ChunkedArray`：多个物理 primitive array
构成一条逻辑连续列，chunking 是可调的内部实现，不是 API identity。

研究输入：

- [Unity ECS Archetype Chunk](https://docs.unity.cn/Packages/com.unity.entities%401.0/manual/concepts-archetypes.html)
- [Apache Arrow ChunkedArray](https://arrow.apache.org/docs/cpp/api/array.html)

### 4.2 Execution Vector

一次 fused kernel 或 barrier primitive 在 cache 中处理的连续范围。它不必产生
Vector object 或中间数组，可以只是 `[base,end)` 以及复用的 selection/scratch。

DuckDB 默认 2,048-row execution vector、X100 的 cache-resident vector 说明该粒度
通常显著小于持久存储分区。`2,048` 只能作为 SOMA 初始候选，不是移植结论。

### 4.3 Parallel Morsel

提交给 worker 的连续输入范围。一个 morsel 可以包含一个或多个 Storage Segment，
内部继续按 Execution Vector 或 fused range 执行。它用于摊薄 task 开销、动态负载
均衡和 worker-local scratch，不等于 Segment 大小。

Java 8 JVM heap 和移动式 GC 不提供稳定 NUMA placement，因此 SOMA 可以借鉴
morsel-driven scheduling，但首轮验证不承诺 NUMA affinity。

### 4.4 三种粒度的组合规则

Segment、Morsel 和 Execution Vector 不建立一对一关系：

```text
Storage Segment
  -> zero / one / many MorselRange
       -> one or many Execution Block
```

- `segmentCount == 1` 不自动推导 `workers == 1`；若 estimated work 足够大，可以
  在单 Segment 内切分多个 disjoint morsel；
- 多个小 Segment 可以由一个 task 连续处理，避免 one-segment-one-task 的调度税；
- 大 Segment 可以切成多个 morsel，改善负载均衡和 cancellation boundary；
- work unit 可以携带一个或多个 `(segmentOrdinal, from, to)` range，但最终压平到
  一个 bounded scheduler 和一个 worker budget；
- 不建立 Segment-level executor 再嵌套 intra-Segment executor；
- 每个 morsel 有稳定 logical ordinal；worker completion order 不改变 output、
  failure identity 或 merge order；
- Segment size 服务 storage/growth/GC，morsel size 服务 scheduling，Execution
  Block 服务 cache/JIT，三个参数必须分别验证。

并行决策以 deterministic bind-time estimated work 为基础，至少考虑 operator、
cardinality、touched columns、expression/hash cost、segment layout、selectivity、
per-worker scratch、available workers 和 memory-bandwidth ceiling。不能只用 row
count、Segment 数量或 wall-clock 试跑结果选择。

## 5. 候选物理架构

以下全部是待验证 hypothesis。

### H0：完整 Metadata 与物理计划绑定

完整 `SomaMetadata` 是候选 SOMA 设计方向，但 Lab 不实现完整 production hierarchy
或 public API。H0 只用覆盖代表性 Schema/Group/Table/Column/Segment/Access scope
的最小 surrogate 验证以下机械链路：

```text
Descriptor
  -> mutable Plan Builder before create/bind
  -> validated immutable Effective Metadata
  -> generated/layout-specialized hot path
  -> immutable Observation snapshot
```

验证重点：

- 代表性 Schema/Group/Table/Column/Segment/Key/Unique/Index/Ownership descriptor
  的稳定 identity 和 deterministic traversal；
- Storage、Access、Execution、Parallel、Scratch、Output 与 Budget plan 的分层；
- unknown path、incompatible strategy、overflow 和不足预算 fail closed；
- create/bind 后 effective metadata 不可变；
- hot path 不使用 reflection、dynamic registry、string lookup 或 per-row metadata
  dispatch；
- Small lane 单独测量 metadata construction、copy/build/freeze/hash 和 lookup 固定税；
- evidence artifact 记录 effective metadata/plan identity，不记录业务 payload。

现有 `RuntimePlan` / `TablePlan` 仅作为当前实现对照。技术验证不能预设最终是保留、
迁移、重命名或替换，也不能在 Lab 中设计或修改 SOMA public API。

### H1：flat head + fixed tail Storage Segment

候选结构：

```text
Column
  -> head: small flat array，最多增长到 Segment rows
  -> tails: 超过 head 后才分配的固定大小 primitive segments
```

候选作用：

- Small Table 继续只持有一个 flat primitive array；
- head 内部可以保留 bounded geometric growth；
- Large Table 增长只新增 Segment，不复制全部历史列；
- `reserve(large)` 可以直接准备完整 Segment；
- Segment directory 可以几何增长，但数据 Segment 不预分配额外 50% 容量；
- future Snapshot/Restore 可以自然分块，但不因此提前确定持久化格式。

初始 Segment 大小候选：

| Rows | 8-byte column payload | 主要问题 |
|---:|---:|---|
| 16,384 | 128 KiB | 更多 array objects 和边界 |
| 32,768 | 256 KiB | 当前首选实验点，不是设计结论 |
| 65,536 | 512 KiB + header | 在最小 G1 Region 下可能进入 humongous |

Java 8 G1 Region 为 1–32 MiB，超过半个 Region 的对象按 humongous 处理。这只是
选择实验点的证据之一；最终结论不能假设所有应用使用同一 GC 或 Region ergonomics。

关键实现约束：

- 逻辑 Index 继续为全 Table current physical position；
- Segment/offset 不进入 public/generated signature；
- growth 必须先准备所有 columns、presence 和相关结构，再原子 publish；
- swap-remove 可以跨 Segment，但结果仍是 `[0,size)` packed；
- 不能只在每次 `get(index)` 中增加 shift/mask，然后保留逐行 generic getter；
- packed scan 应在外层解析 Segment，在内层运行 primitive generated loop；
- candidate gather 可以按物理 shape 决定是否逐 Index 定位或使用 range/segment cursor。

### H2：compact/sharded primary、unique 与 exact access

Segment 只降低列增长风险，不降低索引 retained bytes。候选方向：

- locator bucket 不重复保存完整 Key，只保存 current row 和 control/fingerprint；
- full equality 回查 authoritative Key columns；
- hash high bits 选择 shard，局部 growth/rehash，避免全 locator 同时复制；
- row-indexed group/link/presence arrays 使用可增长的 segmented primitive storage；
- primary、secondary unique 和 secondary exact group 继续是不同数据结构责任；
- maintained exact access 仍在 mutation 成功时 current，读取不触发 rebuild。

Abseil Swiss Table 的 compact metadata/fingerprint 是研究输入，不是可直接移植方案。
Java 8 没有当前 Vector API，不能直接复制其 SSE 实现；需要比较普通 grouped probing、
control-word/SWAR 和分片 open addressing 的真实收益。

### H3：Candidate 多物理形态

逻辑 `Candidate` 不必总是完整 `int[]`：

```text
PackedRange
SegmentRange
ExactGroupCursor
DenseBitmap
SparseIndexes
```

候选成本：

| 100M candidate shape | 近似 retained/scratch |
|---|---:|
| packed range | `O(1)` |
| one bit per row | 12.5 MB |
| full `int[]` | 400 MB |
| 1% sparse indexes | 4 MB |

物理 shape 不得改变 upstream order、lineage、cardinality、Index lifecycle 或 terminal
failure。只有明确 unordered 的内部步骤才允许重排；逻辑顺序仍由 Transformation
Model 拥有。

shape conversion 只能在语义需要的 barrier 或显著降低后续总成本时发生。Range、
Exact cursor 和 SegmentRange 不应为进入统一 executor 先展开成 full `int[]`；
Bitmap/SparseIndexes 也不能在每个 stage 来回转换。candidate、sort、group、join 和
output scratch 需要统一计算 peak-live，而不是分别只看单个数组。

### H4：fusion、access-path 与 terminal specialization

候选方向：

- filter/project/reduce 在一个 generated loop 中完成；
- `sort + first/best` 降低为 arg-min/max 或 top-k；
- `groupBy().count/sum()` 只保留 group key/aggregate state，不构造完整 members；
- 只有 group traversal/materialization 才建立 member layout；
- primary/unique/exact-assisted Join 直接使用 maintained access path；
- semi/anti/exists/count/aggregate Join 不物化完整 left/right pair；
- generic hash Join 只在无可用 access path 且 budget 允许时建立 scratch；
- 满足代数和 order 前提的 Join 后 Group/Aggregate 验证 Join–Group fusion 或
  build-side pre-aggregation，避免级联两张 hash table；
- Join/Group/Sort/Window barrier 按 terminal 所需信息分配，不统一走最宽物化路径；
- eager detached result 可以内部 segmented build，但全部成功后一次性发布；
- full global Sort/Join expansion 受显式预算和 standalone qualification profile
  约束。

### H5：generated fused kernel + bounded Execution Vector

候选不是“把每一步都物化成 vector”，而是：

```text
Segment outer loop
  -> bounded execution range
  -> generated primitive fused kernel
  -> barrier-local vector/scratch only when required
```

执行范围初始只比较 `512 / 1024 / 2048 / 4096`。Java 8 HotSpot SuperWord 对代码
形状敏感，primitive、monomorphic、低分支循环更有机会自动向量化，但不把自动 SIMD
作为正确性或性能承诺。

### H6：morsel-driven data parallelism

只有顺序 kernel 已经稳定后才验证：

- Segment-aligned 或 intra-Segment contiguous morsel；
- 单个大 Segment 可以 split，多个小 Segment 可以 coalesce；
- 所有 work unit 压平到一个 bounded scheduler，不使用 nested executor；
- worker-local scratch；
- worker-local stats、partial aggregate 和 hash/pre-aggregation state；
- output 使用 disjoint range；partial/counter 布局验证 cache-line false sharing；
- deterministic partition ordinal 与 fixed-order merge；
- simple scan 候选 `64K .. 256K` rows/task；
- expensive expression/hash build 候选 `16K .. 64K` rows/task；
- Small/Medium 根据 estimated work 确定性 sequential fallback，不以只有一个
  Segment 为 fallback 理由；
- task/morsel 可以多于 worker 以改善 skew，但 task objects 和 scheduling tax必须
  bounded；
- 内存带宽饱和后不继续增加 worker；
- 不引入隐式 common pool、并发 Table API 或跨 Invocation worker ownership。

### H7：末端微优化

Branchless、manual unroll、Tree Reduction、radix/partitioned Hash Join 和 Merge Join
只能在上述结构性工作后按 profile 采用：

- branchless 必须证明 misprediction 是主要成本；
- Tree Reduction 只用于声明 associative、deterministic merge 的 reducer；
- radix/partitioned Hash 只用于大型、无 maintained access path 的 barrier；
- Merge Join 只在输入已经具有兼容 total order 时采用，不能为了 Join 先全量排序；
- 不因 Java 8 限制引入第三方 runtime、Unsafe public contract 或 off-heap 旁路。

### H8：Scratch 生命周期与 Result Delivery

候选方向：

- Invocation/worker scratch 采用有界 lease/reuse，并记录 current、peak-live 和
  retained high-water；
- terminal 完成或失败后显式 release，不把一次大任务的全部 capacity 永久绑定到
  Context；
- 同一 operation 避免 candidate、sort、group/join 和 output 多套 cardinality-sized
  arrays 同时存活；
- eager detached result 可以由 fixed-size primitive segments 构建，但只能在完整
  成功后发布；
- Eager Detached 是默认能力；callback-scoped streaming 作为独立 read-only
  Result Delivery 候选，只在调用栈内消费；
- materialize、detached columnar、Access callback-scoped Borrow 和 Result
  streaming 继续是不同边界；
- callback streaming 可以降低 retained output peak 或 early stop，但不能减少逻辑
  cardinality，也不能跳过 output/resource preflight；
- 不验证 ordinary Iterator、closeable pull `ResultCursor`、Generator、Publisher、
  mutation/effect streaming 或 partial detached output delivery。

### H9：大表小 Delta

当前 keyed Delta 路径值得隔离验证：

- duplicate detection 和 target resolution 与 Delta size `D` 近线性；
- 使用 maintained primary/unique access 定位，不对大 Table 按 entry 线性扫描；
- 只 stage affected columns、segments 和 access-path delta；
- 验证 segment-level copy-on-write 或等价 staged publish，避免 small Delta 先
  snapshot/replace 整张大 Table；
- 保持 declaration order、preflight、single-aggregate safe point 和 failure
  atomicity。

### H10：Window 增量 specialization

- rolling sum/count/average 只在相应 algebra 条件成立时使用增量状态；
- min/max 比较 monotonic deque 或其他 bounded structure；
- partition-wide constant 只计算一次；
- 不满足条件的 reducer 回退 reference-order generic Window；
- 不引入 retained cross-Invocation window、watermark 或 automatic view maintenance。

### H11：Small/Medium Invocation 固定税

候选方向：

- generated ordinal slot 替代静态 shape 下的 IdentityHashMap/TreeMap/string lookup；
- Template 预计算 canonical source acquisition order；
- Small sequential fast path 不创建 executor/task/Future；
- 减少 parameter boxing、partial object、captured array 和 callback wrapper；
- Metadata build/freeze、Definition/Template/Invocation 成本分别计量；
- Large 优化若增加固定税，必须提供 operation-boundary specialization。

### H12：reference-backed String 的能力与规模边界

H12 不选择 String backend，只验证已冻结 reference-backed baseline：

- payload scan/filter/order 和 materialization 使用 String value semantics；
- primary Key、Unique、non-unique Index 对 hash collision 做 authoritative full
  equality；
- Group/Join 区分 aggregate-only 与 member/result-consuming 成本，禁止因 String
  自动退回 boxed `Map`/object-graph hot path；
- Small/Medium 分别记录 build、point/exact、scan、Group/Join 和 fixed cost；
- single/double Large lane 同时声明 length、value cardinality、object-identity
  sharing、field role、structural/reachable/observed heap 和 GC；
- mutation/replace/remove/clear/release 必须清除 dead reference，保持 epoch 与
  Key/Unique/Index current；
- high-cardinality single/double 100M 在 preflight 中无法满足 heap/headroom 时
  deterministic reject，不尝试 OOM，也不重新引入 dictionary/arena；
- equality/hash/order differential 覆盖 empty、ASCII、CJK/Unicode、相同 hash 不同
  value、相同 value 不同 object，以及未 normalization 的 canonical-equivalent
  Unicode sequence。

## 6. 当前实现证据

以下事实已在本轮 Research 复核基线重新核对；它们描述当前实现，不是永久规范：

1. [`RuntimePlan`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/RuntimePlan.java)
   和 [`TablePlan`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/TablePlan.java)
   已提供 immutable effective plan、builder、canonical identity 和部分 storage/access
   policy，但尚不是本专题要求的完整 Metadata 层级；
2. [`LongColumn`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/LongColumn.java)
   使用单一 `long[]`，`stageCapacity()` 通过 `Arrays.copyOf` 复制整列；
3. [`ColumnGroup.ensureCapacity`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/ColumnGroup.java)
   会为全部 columns stage 新 capacity，保证失败原子性但造成大规模复制峰值；
4. [`IndexBuffer`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/IndexBuffer.java)
   是可复用但单一的 `int[]`，完整 100M candidate 需要约 400 MB；
5. [`HashLongKeySpace`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/HashLongKeySpace.java)
   保存 `long[] keys + int[] rows + byte[] states`，约 13 bytes/bucket，负载率约不超过
   50%；100M expected keys 对应 268,435,456 buckets，约 3.25 GiB locator；
6. [`GroupedExactIndex`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/GroupedExactIndex.java)
   当前 row links 约 12 bytes/row，尚未包含 group 与 bucket 成本；
7. [`DataFlowInvocation`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowInvocation.java)
   每次 invocation 持有 `IdentityHashMap`、`ArrayList`、canonical `TreeMap` 等对象；
   [`ExecutionFrame`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/DataFlowProgram.java)
   再按 invocation 分配 primitive scratch，Small workload 会看见这些固定税；
8. [`CandidateOperations`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/CandidateOperations.java)
   已有 streaming/adaptive/barrier 分支，但排序或 materialization 等路径仍可能建立
   cardinality-sized selection vector；
9. [`GroupOperations`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/GroupOperations.java)
   当前 prepare path 建立 buckets、group chain、representatives、sizes、
   group-by-position、offsets、write 和 members 等多组数组；
10. [`JoinOperations`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/JoinOperations.java)
    的 generic path 建立 right-side buckets/chain；若终端结果必须先知道大小，部分路径
    会先 enumerate count，再分配并第二次 enumerate fill；
11. [`ExpandOperations`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/ExpandOperations.java)
    的 scalar aggregate 可以单遍执行，materialized result 当前使用 count + allocate +
    fill 两遍路径；
12. [`WindowOperations`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/WindowOperations.java)
    先建立 window starts/ends；重叠 Window 的 sum/min/max 当前会逐窗口重新扫描成员；
13. [`DenseTableSourceEmitter`](../../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceEmitter.java)
    当前 Delta apply 会复制完整 working snapshot、逐项查找和修改，最后
    `replaceAll`；这是大表小 Delta 必须专门验证的成本形状；
14. [`ParallelExecution`](../../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow/ParallelExecution.java)
    当前以 cardinality threshold 决定 eligibility，将逻辑范围切为不超过 worker
    数的 contiguous partitions，`tasks == workers`；尚无独立的 Segment、Morsel 和
    Execution Block 决策；
15. [`StringColumn`](../../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated/StringColumn.java)
    当前以 `String[]` 保存白名单 String reference，clear/remove/replacement 会清除 dead
    reference；`estimatedBytes` / `retainedBytes` 使用 `8 × capacity` 的 structural
    estimator，不包含 reachable String object/character payload，也不等于 JVM
    observed heap。

这些事实只支持建立验证问题：完整 Metadata、Segment、索引、中间结果、barrier
specialization、增量算法、分层并行和 reference-backed String scale 都值得验证；
它们不能直接证明任一候选优于当前实现，也不能授权替换当前实现。

## 7. Evidence 层次

每个技术决策依次需要：

1. **成本模型**：bytes/row、capacity、peak、probe、pass count 和 bandwidth 下界；
2. **Lab isolated prototype**：证明机械形状是否成立，不进入 SOMA
   public/production contract；
3. **Production-shaped Lab evidence**：使用接近 generated primitive kernel、真实
   key equality、mutation 和 budget 的独立代码形状；
4. **Standalone composite candidate**：在 Lab-owned Table/DataFlow surrogate 中
   组合已入选候选，并与 Lab reference differential；
5. **Standalone qualification**：只让最终候选进入 single-100M、`100M × 100M`
   和 long-run，不用规模测试调参。

单次最快 wall-clock、只在 toy array 上成立的 microbenchmark、一次 GC 日志或一个
example 结果都不能独立形成设计决策。上述五层均属于 Lab evidence；即使全部通过，
也只能进入 Owner adoption decision，不能跳过 SOMA detailed design 和后续真实实现
验证。

## 8. 有界技术验证计划

原始验证按 `TV0 -> TV8` 推进并已完成。最新 Result Delivery 决策在其后新增
`TV9`；编号表示证据依赖，不表示已有实现。每个 TV 只能得到进入最终设计的受限
裁决，不能直接得到 SOMA production authorization。

### TV0：Workload、Metadata 与 Evidence Protocol

先冻结共同实验协议：

- 在 `soma_java` 之外建立独立 Lab；其精确 repository/path、JDK、build、dependency
  和 package identity，以及治理结束时允许删除的精确 Lab root 进入 manifest；
- 建立隔离检查：Lab build/classpath/source roots 不包含任何 SOMA module、JAR、
  generated artifact 或 production/test class；
- 分别记录 SOMA read-only source baseline commit 与 Lab experiment commit，不能用
  一个 hash 混合两种事实；
- 为 Small、Medium、Large、single-100M 和 `100M × 100M` 建立 immutable workload
  manifest；
- manifest 必须包含 schema、两侧 row count/row width、relation、selectivity、skew、
  multiplicity、access path、terminal、output bound、budget 和 deterministic seed；
- 依据当前 `RuntimePlan` / `TablePlan` 的只读事实，在 Lab 中建立最小 Metadata
  surrogate，验证 descriptor、plan、effective、observation 四层的 identity、遍历、
  freeze、fail-closed 和 explain；
- Metadata builder 只在 create/bind 前可变；effective metadata 冻结后不得被运行期
  修改；
- generated kernel 只读取已绑定 ordinal/primitive 字段，不允许 reflection、字符串
  lookup 或 per-row Metadata dispatch；
- 分别测量 Metadata build、freeze、bind、explain 与 steady execution，防止完整体系
  把 Small 固定税隐藏到“初始化”之外；
- artifact 记录两个 repository identity、dirty state、JDK vendor/version/build、
  build tool、OS、architecture、JVM flags、heap、GC、machine topology、plan
  identity、命令、最终 evidence transfer 位置和 Lab deletion checklist。

退出条件：同一 workload identity 可复现、预算可预检、结果可 differential，且 Metadata
surrogate 没有被包装为 SOMA API 或依赖。具体 public/generated 类型名不在 Lab 中
裁决。

### TV1：Storage、Segment 与 Growth

先固定一个 provisional `32K` segment，只比较三个架构：

1. current flat array；
2. simple segmented getter；
3. flat head + fixed tails + segment-aware generated outer loop。

规模：

```text
1K -> 32K -> 1M -> 10M
```

操作：

- sequential/random point read/write；
- one-column packed scan；
- four-column fused filter + arg-min；
- incremental append/growth；
- reserve + Batch append；
- swap-remove，包含跨边界 relocation。

只有第三种形态在 Small/Medium 无稳定不可接受回归、在 Large 明显降低 growth/peak
后，才比较 `16K / 32K / 64K`。只有最终候选进入 single-100M standalone
qualification。

正确性至少覆盖：

- `segmentRows - 1 / segmentRows / segmentRows + 1`；
- head 到 tail 的多次增长；
- cross-segment copy/swap-remove；
- optional presence；
- Key/exact relocation；
- staged allocation/resource failure 后旧状态不变；
- release/clear/replace lifecycle。

### TV2：Locator、Unique 与 Exact Access

最多比较：

```text
current full-key locator
compact row + fingerprint
sharded compact locator
```

Primary、Unique 和 grouped Exact 分开核算，不因同为 hash structure 合并结论。固定
hit/miss、distinct/collision、insert/remove/updateRow、skew 和 churn workload，记录：

- bytes/live row、两张 Table 同时 retained bytes 和 peak rehash；
- hit/miss ns/op、probes/collisions、shard skew；
- mutation/relocation maintenance；
- full-key equality、fingerprint false positive 和 randomized reference differential；
- 单 locator 优化是否把内存转移到重复 full-key storage 或 temporary rebuild。

### TV3：Candidate、Scratch 与 Eager Terminal

比较 `ContiguousRange`、`SegmentRange`、Bitmap、Sparse/Segmented Index 和可直接
消费的 exact cursor；selectivity 固定为：

```text
0.01% / 1% / 10% / 50% / 100%
```

终端只选 count、arg-min、snapshot、update/remove 和一个 sort/materialization 代表
路径，同时验证：

- Range/cursor 不为进入统一 executor 被预先展开成 full `int[]`；
- shape conversion 只发生在语义 barrier，且计入 conversion peak；
- scratch 按 phase lease/release，失败路径不泄漏 budget；
- terminal 在完整成功前不发布结果，失败只返回 typed failure，不返回 partial result；
- eager complete output 的 bytes、两遍 count/fill 和 segmented internal builder 都纳入
  成本；TV3 保持原始 eager baseline，不在该历史阶段比较 Result Delivery，
  callback-scoped streaming 由新增 TV9 单独验证；
- 候选选择规则简单、确定性，可由 cardinality、access shape、selectivity estimate 和
  budget 推导，不通过 wall-clock 试跑自调优。

### TV4：Group、Join 与 Join–Group

验证：

- aggregate-only GroupBy 与 member-consuming GroupBy；
- primary/unique/exact-assisted Join 与 generic hash Join fallback；
- `1:1`、uniform/skewed `N:1`、low-hit semi/anti/exists、bounded `1:N`；
- Join 后 count/sum/min/max 的 fusion 或 pre-aggregation；
- high-expansion `N:M` 在没有有限 output bound 时的预算拒绝。

指标包括 passes、bytes read/written、group cardinality、build/probe、scratch/output
peak、output expansion、skew/longest chain 和 failure budget。若 specialization 不能
减少一遍全量处理、消除显著 O(N) scratch 或避免 materialized joined pairs，则不增加
长期复杂度。

### TV5：Delta、Window 与 Small/Medium 固定税

三类问题放在同一阶段，是为了同时检查“大表上的小变化”和“小问题上的固定开销”：

- Table size 固定为 `1K / 64K / 1M / 10M`，Delta 为
  `1 / 16 / 1K / 1%`，比较 full snapshot/replace 与 staged changed-row/index path；
- count/time Window 固定 overlap、width 和 slide，比较逐窗口重扫与 incremental
  count/sum/min/max specialization；
- Definition compile、Template bind、Invocation create/execute 分开测量；
- 对比 current map/list lookup 与 generated ordinal slot/canonical source order；
- Small sequential fast path 不创建 executor、task、Future 或 worker scratch；
- 任一增量候选必须保持 failure atomicity、structural epoch、key/index/ownership
  relocation 和 deterministic publication。

### TV6：Generated Kernel、Execution Block 与分层并行

顺序候选稳定后再比较：

- Execution Block：`512 / 1024 / 2048 / 4096` rows；
- Morsel：`32K / 64K / 128K / 256K` rows；
- workers：`1 / 2 / 4 / 8`，不超过实际 machine 和 budget；
- sequential、managed executor、borrowed executor 的相同语义结果；
- single large Segment 拆成多 morsel；
- many small Segments 合并为少量 morsel；
- uniform、skewed 与 early-empty work；
- worker-local hash/scratch/stats/partial result 和 deterministic ordinal merge。

同一 bounded scheduler 执行扁平 task 集合，不建立两层 executor。记录 task submit/join
固定税、task/worker 数、imbalance、steal/queue 行为（若可观测）、effective bandwidth、
cache miss/LLC/false-sharing evidence（平台支持时）、worker scratch、merge、
p50/p90/p99/max 和 GC。

激活规则按 estimated work，而不是“是否只有一个 Segment”：

```text
estimated work
  = operator weight
  × candidate cardinality
  × touched columns / expression cost
  × selectivity or expected output
  + hash / barrier / merge / scratch cost
```

达到内存带宽平台后停止增加并行度；并行加速非线性不是 correctness failure，但 Small/
Medium 稳定回归、cache line 争用或不受控 task/scratch 放大会淘汰候选。

### TV7：Standalone Composite Scale Validation

只让各阶段 final candidate 进入 Lab-owned composite Table/DataFlow surrogate：

- Small/Medium regression lane；
- Large `1M / 10M` retained、growth、GC 和 long-run lane；
- single-100M narrow Table surrogate lane；
- 两个独立 root Table surrogate 同时各 100M rows 的 `100M × 100M` relation lanes；
- allocation/resource failure、cancellation 和 deterministic reference differential；
- complete eager terminal 的 output bound、publication atomicity 和 source-guard release。

`100M × 100M` 不是任意笛卡尔积承诺。每条 standalone qualification 必须声明
relation/access/selectivity/multiplicity/output bound；无法在 preflight 证明
output/scratch 受预算约束的 workload，正确结果是确定性拒绝，不是尝试分配后 OOM。

TV7 只回答“这些候选机械形状组合后是否仍成立”。它不使用实际 SOMA
Table/DataFlow，不形成 SOMA integrated qualification，也不能证明候选移植后的
generated code、compatibility、lifecycle 或 application workload 已经通过。

### TV8：Reference-backed String Baseline

TV8 只验证一种表示：typed reference column 保存 caller-provided `String`。候选数
为一，不进行 dictionary/arena/backend 性能赛马。

Correctness/contract lane：

- required/null、optional absence、empty 与 non-empty；
- ASCII、CJK/Unicode、canonical-equivalent 但未 normalization 的不同 sequence；
- `equals` / `hashCode` / `compareTo` reference differential；
- same-hash different-value collision 和 same-value different-object；
- String payload、primary Key、Unique、non-unique Index、Group/Join；
- mutation/replace/remove/clear/release 后 dead-reference、epoch 和 access currentness。

Scale/resource lane：

- Small `1K`、Medium `32K / 1M`；
- single-100M shared-reference payload/group profile；
- 两个独立 root Table 各 100M、equal-value but distinct-object pool 的 aggregate
  Group/Join profile；
- high-cardinality String Key single/double 100M preflight；
- 每条结果声明 UTF-16 length、value cardinality、distinct object identity、
  sharing ratio、optional ratio、field role、simultaneously-live tables；
- 分开报告 structural model、reachable String model、JVM observed heap、GC 与
  clear/release 后观测；
- full result 超预算时 deterministic rejection；aggregate terminal 不能冒充
  materialized pair result。

只有 qualification correctness、Small/Medium、single/double scale 或明确 preflight
rejection、GC/lifecycle、claim boundary 和可复现 artifact 全部关闭后，TV8 才能形成
`accepted for detailed design`。TV8 不证明 arbitrary object support、任意 String
profile 100M、SOMA production integration 或 readiness。

TV8 已按上述退出条件完成，裁决为 `accepted for detailed design`，但带明确
profile/resource bounds：

- protocol 覆盖 required/null、empty、Unicode、未 normalization、hash collision、
  same-value/different-object、Key/Unique/Index、Group/Join、mutation/epoch、
  clear/release；
- 3 个 qualification fork workload/checksum 一致；1M unique Key/Unique 与 1M
  shared Exact Index/aggregate Join mechanics 通过；
- single-100M 实际 profile：1 个 Table、UTF-16 length 16、cardinality/object
  identities 1,024、payload/Group；structural model `800,108,128 B`、reachable
  String model `73,728 B`、observed heap after build `400,611,224 B`；
- double-100M 实际 profile：2 个独立 Table 各 100M、UTF-16 length 16、每表
  cardinality 1,024、aggregate object identities 2,048、Group/aggregate Join；
  structural model `1,600,181,392 B`、reachable String model `147,456 B`、
  observed heap after build `800,746,328 B`；
- double lane 的 logical pair count 为 `9,765,625,000,192`，完整 row-pair output
  为 `78,125,000,001,536 B`，因此 materialization 被拒绝；
- high-cardinality unique String Key 的 single/double 100M profile 在 4 GiB heap
  下 modeled required 为 `11,677,813,220 B` / `23,355,626,440 B`，均在
  allocation 前确定性拒绝；
- observed heap 与 operation GC 只属于本机 Zulu 8/G1 observation，不是 SOMA
  benchmark baseline 或对象大小规范。

### TV9：Result Delivery — Eager Detached 与 callback-scoped streaming

TV9 是 TV0–TV8 之后新增的独立问题。它不推翻 eager publication evidence，只判断
是否值得在少量 read-only terminal/interface 上增加 callback-scoped streaming
Capability。

候选严格限定为：

```text
A. Eager Detached baseline
B. callback-scoped streaming
```

共同语义：

- 同一 logical source、order、value semantics、budget 和 failure category；
- one-shot terminal，application 已独占 source aggregate；
- 不允许 callback value/borrow 逃逸；
- callback、cancel、deadline、source lifecycle conflict 后 guard/scratch 全部释放；
- 不发布 partial detached result；
- callback 已产生的 application side effect 不由 SOMA 回滚；
- high-expansion output 仍在 source touch/枚举前进行 cardinality/resource preflight。

Workload：

- Small/Medium fixed-cost lane；
- `1M / 10M` full-consumption lane；
- early stop：first、`1K`、`1%`、`50%`；
- 只让通过前述协议的 final candidate 进入一次 bounded single-100M projection 和
  一次两个 100M root source 的 bounded/early-stop relation qualification，不用
  100M 调参；
- primitive、reference-backed String、typed projection；
- bounded Group/Join output；
- consumer exception、cancel、deadline、mutation/release conflict；
- deterministic order/checksum、allocation、scratch、retained peak、JVM heap 与 GC；
- high-expansion rejection 只做 preflight，不枚举不可接受的完整输出。

明确排除：

- ordinary `Iterator<T>`；
- closeable pull cursor / `ResultCursor`；
- asynchronous Publisher/Generator；
- mutation、Effect、跨 root commit；
- public API 命名与 compatibility migration；
- 以 streaming 为由接受未知或无界 output。

TV9 先形成 experiment brief、workload identity、candidate semantics、最大实验数和
退出条件，再写代码。每个候选一次 feasibility；只有两者都满足 correctness/failure
协议时才进行最多三个独立 JVM fork。到达以下裁决之一立即停止：

```text
accepted for limited read-only pilot
rejected
inconclusive
```

实际裁决：

- callback-scoped streaming：
  `accepted for limited read-only pilot`；
- Eager Detached：继续作为默认 Result Delivery；
- 1M primitive allocation 为 Eager `16,000,240 B` 对 callback `184 B`；
- 10M String allocation 为 Eager `200,000,256 B` 对 callback `184 B`；
- early-1K 为 Eager touch 1M 对 callback touch 1,024；
- `1M → 1,024` bounded Group 与 262,144 bounded relation 通过；
- `2,457,600,000,000 B` high-expansion semantic output 从 canonical relation
  entry 在 source guard/touch 前 typed rejection；
- single/double logical-100M 只是 deterministic delivery surrogate，不是
  production SOMA Table/Join/Group readiness；
- public/generated signature、首批 terminal、production crossover 与真实 GC
  仍由最终设计和 production qualification 裁决。

## 9. Profile 与指标

### 9.1 Schema

- narrow：稳定 Key、少量 primitive runtime state；
- medium：多种 primitive、optional presence 和一个代表 exact access；
- wide：更多 physical leaves，用于测 bytes/row 和 touched-column 差异；
- reference String：只使用白名单 immutable String reference，声明 length/cardinality/
  identity sharing/field role，不外推到 arbitrary object；
- flattened value：按 physical primitive leaves 计量，不保存 value object；
- owned structured state：按 child Table/handle/aggregate 单独核算，不归入 object
  graph。

双表 profile 还必须分别记录 left/right schema footprint、各自 retained index，以及
两表同时 live 时的 aggregate storage budget。100M standalone qualification 首先
只使用 narrow；medium/wide 的最大规模由实际 heap 和明确 working-set 预算决定，
不能用 OOM 后反复降低规模寻找漂亮结果。

### 9.2 Relation 与输出

每个双表 workload 固定：

- left/right cardinality 与 key distinct count；
- `1:1`、`N:1`、semi/anti、bounded `1:N` 或 high-expansion `N:M`；
- uniform/skew distribution、hit rate、最大 fan-out；
- left/right access path 及 build/probe side；
- terminal 是 scalar、group aggregate、index snapshot 还是 projected column；
- expected/max output elements、result bytes 和超限行为。

### 9.3 Workload

- create/reserve/append/replace/clear；
- point/primary/unique/exact；
- packed one/four/eight-column scan；
- filter + count/arg-min/snapshot；
- swap-remove/churn；
- aggregate-only GroupBy；
- index-assisted、semi/anti、bounded one-to-many 与 generic Join；
- Join–Group/pre-aggregation；
- count/time Window 与 overlap；
- small Delta over large Table；
- bounded eager output/materialization 和 over-budget rejection。

### 9.4 Metrics

- latency、throughput、cycles/row 或 equivalent work/row；
- touched/estimated bytes 与 effective bandwidth；
- steady allocation/op；
- retained、transient、peak/high-water bytes；
- SOMA-owned structural bytes、SOMA-retained reachable String bytes 与 JVM
  observed heap；
- Young/Full GC、pause 和 humongous/large-array evidence when applicable；
- probe/collision/rehash；
- candidate/scratch/output bytes；
- Metadata build/freeze/bind/explain、effective plan identity 和 hot-path dispatch；
- Segment count/rows、Morsel count/rows、Execution Block rows；
- parallel tasks/workers/speedup/saturation、task imbalance 和 merge cost；
- cache/LLC miss、memory bandwidth、false sharing 和 write contention（可观测时）；
- eager result publication、source-guard release 和 over-budget failure；
- callback streaming 的 first-value/full-consumption、early-stop、consumer failure、
  guard/scratch release 和 retained-output peak；
- correctness/reference differential 和 failure atomicity。

所有量化结果还必须附带 actual heap、GC、JDK、OS、architecture、CPU/topology、JVM
flags、fork/warmup/measurement 配置和 workload identity；否则只能作为诊断线索。

## 10. 防止性能测试打转

- 每个候选先运行一次单-fork feasibility；
- 每个实验最多保留三个候选；
- TV9 已冻结为两个候选，不得加入 Iterator、cursor 或 Publisher 形成新赛道；
- 只有最后两个候选运行 3 个独立 JVM fork；
- single-100M 与 `100M × 100M` 只运行最终候选，不参与参数筛选；
- 先在 Small/Medium 暴露固定税，再在 `1M / 10M` 校准内存和带宽；禁止直接用 100M
  调 Segment/Morsel/Block、load factor 或 threshold；
- Storage Segment、Morsel、Execution Block、locator 和 candidate shape 分阶段选择，
  不运行它们的无界笛卡尔组合；
- 差异小于稳定噪声时记为 `inconclusive`，不继续堆 fork；
- benchmark 异常先审计 test identity、warmup、GC、output 和 workload，不立即修改
  SOMA；
- 不为了通过 Lab check 缩小问题、删除目标能力、减少正确性约束或改变语义；
- 不以一次 wall-clock、JIT 偶然值或单机峰值作为结论；
- 每个 slice 必须有预先声明的问题、候选、指标、退出条件和最大实验数；
- 到达 decision point 即停止，不在同一问题上无限调参；
- Lab 必须拥有自己的 deterministic correctness/check 命令；不得调用 SOMA
  `./scripts/check.sh` 冒充实验验证，也不得把 Lab 纳入 SOMA reactor；
- standalone composite candidate 和阶段性 immutable candidate 才运行 Lab full
  check；隔离实验优先窄 correctness/benchmark check。

建议的判定语言只有：

```text
accepted for detailed design
rejected
inconclusive
```

Small/Medium 稳定回归超过约 5% 时，候选必须解释并提供 fast path；低于噪声的结果不
声明胜负。该 5% 只是 Lab 的初始筛选线，不是 SOMA performance Gate；后者只能由
后续 production-shape evidence 校准。

## 11. Future Evidence Transfer Map

本表只帮助 Owner 在 Lab 结束后决定后续 SOMA detailed-design scope，不是本轮验证的
实现位置、任务清单或修改授权：

| 未来可能受影响的 SOMA Owner | Lab evidence 可能支持的后续设计问题 |
|---|---|
| `soma-runtime-core` | 完整 Metadata 的 runtime/effective 部分、segmented primitive/reference columns、presence/index/scratch、atomic segment growth、String structural/reachable/observed accounting、stats |
| `soma-processor` | schema/descriptor Metadata 产出、四类 type/storage classification、String whitelist、segment-aware generated loops、schema footprint、ordinal binding/protocol projection |
| `soma-dataflow` | execution Metadata、physical Candidate shape、scratch lease、Eager Detached 默认、callback-scoped Result Delivery、access-path specialization、Morsel/Block scheduler |
| `soma-testkit` | boundary/property/reference differential 与 failure injection |
| `soma-benchmarks` | 后续 SOMA production-shape component lanes 和 evidence artifacts |
| `soma-examples` | 只在 core 候选稳定后验证真实 application 使用，不承担 component 调参 |

TV0–TV9 不得落在上述任何 SOMA module。完整 Metadata 的精确 API、现有
RuntimePlan/TablePlan 的保留或迁移、generated-runtime internal protocol 是否变化，
都只能在 Lab evidence review 和 Owner adoption decision 后，由新的 SOMA
integrated detailed design 单独裁决。

## 12. 下一阶段推荐入口

TV0–TV9 已经完成。后续按以下顺序消费 evidence：

1. 以现有 Technical Validation Report、Owner Decision Matrix 与本 Temporary 中
   已转移的 TV0–TV9 evidence 摘要作为输入，不把 Lab 类型当正式事实；
2. 逐项复核 TV0–TV9 的 profile、语义前提和潜在 Owner；
3. 将裁决和必要 evidence 摘要输入系统设计治理，完成 canonical product/system
   narrative、Metadata、Capability Model、Result Delivery 及 integrated detailed
   design 的 Owner 裁决；
4. 依据 accepted Design 审计 SOMA code/test/doc/evidence；只有 replacement
   closure 和新的实施授权完成后，才设计 production slices、裁剪不必要代码/测试
   并建立真实 qualification；
5. core 稳定后审计三个 Example，必要时按最终最佳实践治理；
6. 将最终决策与必要 evidence 摘要转移到相应正式 Design/Report Owner；
7. 本次治理完成且正式 Owner 不再依赖 Lab path 后，删除整个独立 Lab；随后按本
   专题退役条件删除 Temporary。

后续工作不得以本文件中的推荐值为 production 修改授权，也不得把完成一个 benchmark
slice、单表 100M 或某个峰值结果当作整个 Scale Readiness 治理已经收口。独立 Lab
也不得在治理结束后保留为长期 prototype、平行实现或事实源。
